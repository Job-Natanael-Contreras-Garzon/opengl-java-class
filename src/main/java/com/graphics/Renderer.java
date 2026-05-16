package com.graphics;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Renderer — Todo el código de presentación OpenGL en un único lugar.
 *
 * Capas de render (orden de dibujo, de atrás hacia adelante):
 * 1. Fondo: degradado de cielo en 8 franjas horizontales
 * 2. Montañas lejanas (parallax lento)
 * 3. Montañas cercanas (parallax medio)
 * 4. Nubes (parallax muy lento)
 * 5. Tuberías con tapa, sombra y borde claro
 * 6. Suelo con hierba y líneas de movimiento (parallax rápido)
 * 7. Partículas (sobre el suelo, bajo los pájaros)
 * 8. Pájaros (muertos primero, vivos encima)
 * 9. HUD superior: franja oscura + dígitos 7-segmentos + indicadores
 * 10. Pantalla de inicio (si !started)
 * 11. Pantalla de game over (si gameOver)
 *
 * El Renderer NO tiene estado de juego — sólo recibe {@link GameWorld} y
 * un tiempo acumulado {@code tiempo} para animaciones (aleteo, pulso, etc.).
 *
 * Shader de vértice soporta rotación por uniform {@code uAngle}/{@code uPivot},
 * usada para inclinar el pájaro según su velocidad vertical.
 */
public class Renderer {

   // ── Recursos OpenGL ──────────────────────────────────────────────────────
   private int programa;
   private int vao, vbo;
   private int uOffsetLoc, uScaleLoc, uColorLoc, uAngleLoc, uPivotLoc;

   // Estado de rotación activo (reset a 0 después de cada dibujarPájaro)
   private float estadoAngulo = 0f;
   private float estadoPivotX = 0f;
   private float estadoPivotY = 0f;

   // ── Init / Cleanup ───────────────────────────────────────────────────────

   public void init() {
      crearShaders();
      crearQuadBase();
   }

   public void cleanup() {
      GL30.glDeleteVertexArrays(vao);
      GL15.glDeleteBuffers(vbo);
      GL20.glDeleteProgram(programa);
   }

   // ── Shaders ──────────────────────────────────────────────────────────────

   private void crearShaders() {
      // Vertex con soporte de rotación sobre un pivot arbitrario
      String vertexSrc = """
            #version 330 core
            layout(location = 0) in vec3 aPos;
            uniform vec2  uOffset;
            uniform vec2  uScale;
            uniform float uAngle;
            uniform vec2  uPivot;
            void main() {
                vec2 pos = aPos.xy * uScale + uOffset;
                pos -= uPivot;
                float c = cos(uAngle), s = sin(uAngle);
                pos = vec2(pos.x*c - pos.y*s, pos.x*s + pos.y*c);
                pos += uPivot;
                gl_Position = vec4(pos, aPos.z, 1.0);
            }
            """;

      // Fragment: color sólido uniforme (extensible a textura)
      String fragmentSrc = """
            #version 330 core
            uniform vec3 uColor;
            out vec4 fragColor;
            void main() { fragColor = vec4(uColor, 1.0); }
            """;

      int vs = compilar(vertexSrc, GL20.GL_VERTEX_SHADER, "Vertex");
      int fs = compilar(fragmentSrc, GL20.GL_FRAGMENT_SHADER, "Fragment");

      programa = GL20.glCreateProgram();
      GL20.glAttachShader(programa, vs);
      GL20.glAttachShader(programa, fs);
      GL20.glLinkProgram(programa);
      if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
         throw new RuntimeException("Link: " + GL20.glGetProgramInfoLog(programa));

      uOffsetLoc = GL20.glGetUniformLocation(programa, "uOffset");
      uScaleLoc = GL20.glGetUniformLocation(programa, "uScale");
      uColorLoc = GL20.glGetUniformLocation(programa, "uColor");
      uAngleLoc = GL20.glGetUniformLocation(programa, "uAngle");
      uPivotLoc = GL20.glGetUniformLocation(programa, "uPivot");

      GL20.glDeleteShader(vs);
      GL20.glDeleteShader(fs);
   }

   private int compilar(String src, int tipo, String nombre) {
      int s = GL20.glCreateShader(tipo);
      GL20.glShaderSource(s, src);
      GL20.glCompileShader(s);
      if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
         throw new RuntimeException(nombre + ": " + GL20.glGetShaderInfoLog(s));
      return s;
   }

   private void crearQuadBase() {
      float[] verts = {
            -0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f,
            -0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f
      };
      vao = GL30.glGenVertexArrays();
      GL30.glBindVertexArray(vao);
      vbo = GL15.glGenBuffers();
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
      FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
      buf.put(verts).flip();
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);
      GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
      GL20.glEnableVertexAttribArray(0);
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
      GL30.glBindVertexArray(0);
   }

   // ════════════════════════════════════════════════════════════════════════
   // RENDER PRINCIPAL
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja un frame completo del juego.
    *
    * @param world  Estado actual del mundo (leer, no modificar).
    * @param tiempo Tiempo acumulado en segundos desde el inicio.
    */
   public void render(GameWorld world, float tiempo) {
      GL11.glClearColor(0f, 0f, 0f, 1f);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
      GL20.glUseProgram(programa);
      GL30.glBindVertexArray(vao);

      // ── Capa 1: Fondo degradado ───────────────────────────────────
      dibujarFondo();

      // ── Capa 2 & 3: Montañas con parallax ───────────────────────
      dibujarMontanas(world.parallaxMontanas * Constants.PARALLAX_MONTANAS_LEJOS,
            world.parallaxMontanas, tiempo);

      // ── Capa 4: Nubes ────────────────────────────────────────────
      dibujarNubes(world.parallaxNubes);

      // ── Capa 5: Tuberías ─────────────────────────────────────────
      dibujarTuberias(world);

      // ── Capa 6: Suelo ────────────────────────────────────────────
      dibujarSuelo(world.parallaxSuelo);

      // ── Capa 7: Partículas ───────────────────────────────────────
      dibujarParticulas(world);

      // ── Capa 8: Pájaros ──────────────────────────────────────────
      if (!world.bird1.alive)
         dibujarPajaroMuerto(world.bird1, world.getFlickerTimer1(), tiempo);
      if (!world.bird2.alive)
         dibujarPajaroMuerto(world.bird2, world.getFlickerTimer2(), tiempo);
      if (world.bird1.alive)
         dibujarPajaro(world.bird1, tiempo);
      if (world.bird2.alive)
         dibujarPajaro(world.bird2, tiempo);

      // ── Capa 9: HUD superior ─────────────────────────────────────
      dibujarHUD(world, tiempo);

      // ── Capa 10: Pantalla de inicio ──────────────────────────────
      if (!world.started && !world.gameOver) {
         dibujarPantallaInicio(world, tiempo);
      }

      // ── Capa 11: Pantalla de game over ───────────────────────────
      if (world.gameOver) {
         dibujarPantallaGameOver(world, tiempo);
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // CAPAS DE FONDO
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Fondo: 8 franjas horizontales que crean un degradado de cielo.
    * Azul oscuro arriba → azul claro / celeste abajo.
    */
   private void dibujarFondo() {
      // { yCentro, alto, r, g, b }
      float[][] capas = {
            { 1.00f, 0.30f, 0.25f, 0.45f, 0.75f },
            { 0.72f, 0.26f, 0.35f, 0.60f, 0.88f },
            { 0.48f, 0.22f, 0.45f, 0.72f, 0.93f },
            { 0.28f, 0.20f, 0.55f, 0.80f, 0.95f },
            { 0.10f, 0.20f, 0.62f, 0.85f, 0.96f },
            { -0.08f, 0.20f, 0.66f, 0.88f, 0.97f },
            { -0.26f, 0.20f, 0.68f, 0.90f, 0.97f },
            { -0.50f, 0.42f, 0.70f, 0.91f, 0.97f },
      };
      for (float[] c : capas)
         rect(0f, c[0], 2f, c[1], c[2], c[3], c[4]);
   }

   /**
    * Montañas en dos capas de profundidad con parallax diferente.
    * Cada montaña se dibuja como un "trapecio" simulado con 3 rectángulos
    * superpuestos de ancho decreciente (base grande, punta pequeña).
    *
    * @param offsetLejos Offset acumulado para capa lejana
    * @param offsetCerca Offset acumulado para capa cercana
    */
   private void dibujarMontanas(float offsetLejos, float offsetCerca, float tiempo) {
      float baseY = Constants.SUELO_Y + Constants.SUELO_H * 0.5f; // arrancan desde el suelo

      // ── Montañas lejanas — gris azulado claro ─────────────────────
      for (float[] m : Constants.MONTANAS_LEJOS) {
         float mx = wrap(m[0] + offsetLejos, -1.5f, 1.5f);
         float mw = m[1];
         float mh = m[2];
         float my = baseY + mh * 0.5f;
         // Nieve en la cima (rectángulo blanco pequeño)
         rect(mx, my + mh * 0.25f, mw * 0.35f, mh * 0.28f, 0.92f, 0.95f, 1.00f);
         // Capas de la montaña (base más ancha → cima más estrecha)
         rect(mx, my, mw, mh, 0.60f, 0.68f, 0.78f);
         rect(mx, my + mh * 0.15f, mw * 0.70f, mh * 0.70f, 0.55f, 0.63f, 0.74f);
         rect(mx, my + mh * 0.32f, mw * 0.40f, mh * 0.38f, 0.50f, 0.58f, 0.70f);
      }

      // ── Montañas cercanas — verde oscuro, más definidas ───────────
      for (float[] m : Constants.MONTANAS_CERCA) {
         float mx = wrap(m[0] + offsetCerca, -1.5f, 1.5f);
         float mw = m[1];
         float mh = m[2];
         float my = baseY + mh * 0.5f;
         rect(mx, my, mw, mh, 0.22f, 0.38f, 0.20f);
         rect(mx, my + mh * 0.20f, mw * 0.65f, mh * 0.60f, 0.18f, 0.33f, 0.16f);
         rect(mx, my + mh * 0.38f, mw * 0.35f, mh * 0.28f, 0.15f, 0.28f, 0.13f);
      }
   }

   /**
    * Nubes — parallax muy lento (están "lejos").
    * Cada nube = sombra + cuerpo principal + 2 protuberancias laterales.
    */
   private void dibujarNubes(float offset) {
      for (float[] n : Constants.NUBES) {
         float nx = wrap(n[0] + offset, -1.6f, 1.6f);
         float ny = n[1];
         float nw = n[2];

         rect(nx + 0.005f, ny - 0.004f, nw, 0.060f, 0.82f, 0.88f, 0.93f); // sombra
         rect(nx, ny, nw, 0.070f, 1.00f, 1.00f, 1.00f); // cuerpo
         rect(nx - nw * 0.26f, ny + 0.024f, nw * 0.48f, 0.058f, 1.00f, 1.00f, 1.00f); // protuberancia izq
         rect(nx + nw * 0.24f, ny + 0.020f, nw * 0.42f, 0.050f, 1.00f, 1.00f, 1.00f); // protuberancia der
      }
   }

   /**
    * Suelo con tres elementos:
    * - Franja de tierra (marrón)
    * - Franja de hierba (verde)
    * - Líneas de movimiento con parallax (efecto de velocidad)
    */
   private void dibujarSuelo(float offsetSuelo) {
      float sy = Constants.SUELO_Y;
      float sh = Constants.SUELO_H;
      float bordTop = Constants.SUELO_BORDE_TOP;

      // Tierra base
      rect(0f, sy, 2f, sh, 0.52f, 0.35f, 0.14f);

      // Franja de hierba
      rect(0f, bordTop - 0.012f, 2f, 0.030f, 0.28f, 0.65f, 0.20f);
      // Borde oscuro de la hierba
      rect(0f, bordTop - 0.028f, 2f, 0.008f, 0.20f, 0.50f, 0.15f);

      // Líneas de movimiento parallax (8 segmentos que avanzan y hacen wrap)
      for (int i = 0; i < 8; i++) {
         float lx = -1.0f + (i * 0.25f) + offsetSuelo % 0.25f;
         if (lx > 1.0f)
            lx -= 2.0f;
         rect(lx, sy + 0.02f, 0.008f, sh * 0.45f, 0.42f, 0.28f, 0.10f);
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // TUBERÍAS
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Tuberías mejoradas con:
    * - Sombra lateral derecha
    * - Borde claro izquierdo (3D básico)
    * - Tapa más ancha en el extremo del gap
    */
   private void dibujarTuberias(GameWorld world) {
      for (Tuberia t : world.tuberias) {
         float gTop = t.gapCentroY + Constants.GAP_ALTO * 0.5f;
         float gBot = t.gapCentroY - Constants.GAP_ALTO * 0.5f;

         // Tubería superior
         float hSup = 1.0f - gTop;
         if (hSup > 0f) {
            float cy = gTop + hSup * 0.5f;
            rect(t.x + 0.012f, cy, Constants.TUBERIA_ANCHO, hSup, 0.08f, 0.42f, 0.10f); // sombra
            rect(t.x, cy, Constants.TUBERIA_ANCHO, hSup, 0.18f, 0.70f, 0.22f); // cuerpo
            rect(t.x - Constants.TUBERIA_ANCHO * 0.36f, cy,
                  Constants.TUBERIA_ANCHO * 0.12f, hSup, 0.30f, 0.85f, 0.32f); // borde claro
            // Tapa
            rect(t.x, gTop + 0.028f, Constants.TUBERIA_ANCHO + 0.042f, 0.052f, 0.14f, 0.62f, 0.17f);
            rect(t.x, gTop + 0.028f, Constants.TUBERIA_ANCHO + 0.040f, 0.044f, 0.20f, 0.75f, 0.24f);
         }

         // Tubería inferior
         float hInf = gBot + 1.0f;
         if (hInf > 0f) {
            float cy = -1.0f + hInf * 0.5f;
            rect(t.x + 0.012f, cy, Constants.TUBERIA_ANCHO, hInf, 0.08f, 0.42f, 0.10f);
            rect(t.x, cy, Constants.TUBERIA_ANCHO, hInf, 0.18f, 0.70f, 0.22f);
            rect(t.x - Constants.TUBERIA_ANCHO * 0.36f, cy,
                  Constants.TUBERIA_ANCHO * 0.12f, hInf, 0.30f, 0.85f, 0.32f);
            // Tapa
            rect(t.x, gBot - 0.028f, Constants.TUBERIA_ANCHO + 0.042f, 0.052f, 0.14f, 0.62f, 0.17f);
            rect(t.x, gBot - 0.028f, Constants.TUBERIA_ANCHO + 0.040f, 0.044f, 0.20f, 0.75f, 0.24f);
         }
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // PARTÍCULAS
   // ════════════════════════════════════════════════════════════════════════

   private void dibujarParticulas(GameWorld world) {
      for (ParticleSystem.Particle p : world.particulas.getParticles()) {
         float a = p.alpha();
         float size = p.size * a; // encoge al disiparse
         if (size > 0.002f)
            rect(p.x, p.y, size, size, p.r, p.g, p.b);
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // PÁJAROS
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Pájaro vivo con:
    * - Inclinación por velocidad (shader uAngle)
    * - Aleteo animado post-salto
    * - Cola 3 plumas, ala trasera/delantera, cuerpo, panza, cabeza,
    * ojo (3 capas), mejilla, pico (2 piezas)
    */
   private void dibujarPajaro(Bird b, float tiempo) {
      float x = b.birdX, y = b.birdY;
      float W = Constants.BIRD_ANCHO, H = Constants.BIRD_ALTO;
      float[] c = b.colorCuerpo, pz = b.colorPanza,
            al = b.colorAla, pk = b.colorPico;

      // Inclinación según velocidad vertical
      estadoAngulo = Math.max(-1.2f, Math.min(0.55f, b.velY * 0.44f));
      estadoPivotX = x;
      estadoPivotY = y;

      // Aleteo: sinusoide viva inmediatamente tras el salto, lenta en caída
      float aleteo = (b.tiempoAleteo > 0)
            ? (float) Math.sin((0.25f - b.tiempoAleteo) * Math.PI * 8.0f) * H * 0.35f
            : (float) Math.sin(tiempo * 6.0f) * H * 0.12f;

      // Cola — 3 rectángulos escalonados
      rect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, al[0], al[1], al[2]);
      rect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, al[0] * 0.94f, al[1] * 0.88f, al[2]);
      rect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, al[0] * 0.88f, al[1] * 0.77f, al[2]);

      // Ala trasera (animada hacia abajo)
      rect(x - W * 0.15f, y - H * 0.30f + aleteo, W * 0.80f, H * 0.30f, al[0], al[1], al[2]);

      // Cuerpo
      rect(x, y, W, H, c[0], c[1], c[2]);

      // Panza
      rect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, pz[0], pz[1], pz[2]);

      // Ala delantera (animada hacia arriba, menor amplitud)
      rect(x - W * 0.10f, y + H * 0.22f + aleteo * 0.7f, W * 0.70f, H * 0.26f, al[0], al[1], al[2]);

      // Cabeza
      rect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, c[0], c[1], c[2]);

      // Ojo: blanco + pupila + brillo
      rect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.28f, 1f, 1f, 1f);
      rect(x + W * 0.48f, y + H * 0.50f, W * 0.15f, H * 0.18f, 0.08f, 0.08f, 0.18f);
      rect(x + W * 0.46f, y + H * 0.56f, W * 0.07f, H * 0.07f, 1f, 1f, 1f);

      // Mejilla
      rect(x + W * 0.38f, y + H * 0.35f, W * 0.22f, H * 0.14f, 1f, 0.50f, 0.40f);

      // Pico — mandíbula sup + inf
      rect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, pk[0], pk[1], pk[2]);
      rect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, pk[0] * 0.90f, pk[1] * 0.85f, pk[2] * 0.80f);

      estadoAngulo = 0f;
   }

   /**
    * Pájaro muerto con:
    * - Inclinación fija de -1.5 rad (boca abajo)
    * - Desaturación completa (gris)
    * - Ojo cerrado (línea horizontal)
    * - Efecto de parpadeo (flicker) mientras flickerTimer > 0
    *
    * @param flickerTimer Tiempo restante de parpadeo (segundos). 0 = sin parpadeo.
    */
   private void dibujarPajaroMuerto(Bird b, float flickerTimer, float tiempo) {
      // Parpadeo: omitir frame en intervalos alternos mientras dure el timer
      if (flickerTimer > 0f) {
         int fase = (int) (flickerTimer / Constants.FLICKER_INTERVALO);
         if (fase % 2 == 0)
            return; // frame invisible
      }

      float x = b.birdX, y = b.birdY;
      float W = Constants.BIRD_ANCHO, H = Constants.BIRD_ALTO;

      estadoAngulo = -1.5f;
      estadoPivotX = x;
      estadoPivotY = y;
      float gr = 0.40f;

      // Cola
      rect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
      rect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
      rect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);

      // Ala trasera
      rect(x - W * 0.15f, y - H * 0.30f, W * 0.80f, H * 0.30f, gr * 0.9f, gr * 0.9f, gr * 0.9f);

      // Cuerpo
      rect(x, y, W, H, gr, gr, gr);

      // Panza
      rect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, gr + 0.15f, gr + 0.15f, gr + 0.15f);

      // Ala delantera caída
      rect(x - W * 0.05f, y - H * 0.40f, W * 0.70f, H * 0.20f, gr * 0.9f, gr * 0.9f, gr * 0.9f);

      // Cabeza
      rect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, gr, gr, gr);

      // Ojo cerrado (X) — línea horizontal + línea vertical
      rect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.07f, 0.1f, 0.1f, 0.1f);
      rect(x + W * 0.52f, y + H * 0.45f, W * 0.07f, H * 0.22f, 0.1f, 0.1f, 0.1f);

      // Pico
      rect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, 0.55f, 0.25f, 0.05f);
      rect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, 0.50f, 0.22f, 0.04f);

      estadoAngulo = 0f;
   }

   // ════════════════════════════════════════════════════════════════════════
   // HUD SUPERIOR
   // ════════════════════════════════════════════════════════════════════════

   /**
    * HUD superior con:
    * - Franja oscura semitransparente
    * - Dígitos 7-segmentos para puntajes (P1 izq, P2 der)
    * - Cuadrado de color del jugador (amarillo / azul)
    * - Indicador de nivel centrado
    * - Barra de progreso de velocidad
    */
   private void dibujarHUD(GameWorld world, float tiempo) {
      float hy = Constants.HUD_TOP_Y;
      float hh = Constants.HUD_TOP_H;

      // Franja de fondo
      rect(0f, hy, 2f, hh, 0.10f, 0.13f, 0.18f);
      // Línea separadora inferior
      rect(0f, Constants.HUD_SEP_Y, 2f, 0.012f, 0.25f, 0.32f, 0.40f);

      // ── Jugador 1 (izquierda) ──────────────────────────────────────
      float[] c1 = world.bird1.alive ? world.bird1.colorCuerpo : new float[] { 0.3f, 0.3f, 0.3f };
      rect(-0.82f, hy, 0.075f, 0.075f, c1[0], c1[1], c1[2]); // cuadro de color
      dibujarNumero(world.bird1.puntaje, -0.60f, hy,
            Constants.DIGIT_W, Constants.DIGIT_H, 1f, 1f, 1f);

      // ── Nivel centrado ─────────────────────────────────────────────
      int nivel = world.calcularNivel();
      // Dos líneas decorativas a los lados del nivel
      rect(-0.18f, hy + 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      rect(0.18f, hy + 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      rect(-0.18f, hy - 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      rect(0.18f, hy - 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      dibujarNumero(nivel, 0f, hy + 0.010f,
            Constants.DIGIT_W * 0.85f, Constants.DIGIT_H * 0.80f,
            1.0f, 0.80f, 0.10f);

      // Barra de velocidad (progreso del nivel)
      float velPct = (world.velocidadActual() - Constants.VELOCIDAD_BASE)
            / (Constants.VELOCIDAD_MAX - Constants.VELOCIDAD_BASE);
      velPct = Math.max(0f, Math.min(1f, velPct));
      rect(0f, hy - 0.065f, 0.28f, 0.014f, 0.18f, 0.22f, 0.28f); // fondo barra
      if (velPct > 0f) {
         float bw = 0.28f * velPct;
         float bx = -0.14f + bw * 0.5f;
         // Color: verde → amarillo → rojo según progreso
         float r = Math.min(1f, velPct * 2f);
         float g = Math.min(1f, (1f - velPct) * 2f);
         rect(bx, hy - 0.065f, bw, 0.012f, r, g, 0.05f);
      }

      // ── Jugador 2 (derecha) ────────────────────────────────────────
      dibujarNumero(world.bird2.puntaje, 0.60f, hy,
            Constants.DIGIT_W, Constants.DIGIT_H, 1f, 1f, 1f);
      float[] c2 = world.bird2.alive ? world.bird2.colorCuerpo : new float[] { 0.3f, 0.3f, 0.3f };
      rect(0.82f, hy, 0.075f, 0.075f, c2[0], c2[1], c2[2]);
   }

   // ════════════════════════════════════════════════════════════════════════
   // DÍGITOS 7 SEGMENTOS
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Renderiza un número entero como dígitos 7-segmentos centrado en (cx, cy).
    * Cada dígito se separa {@code w * 1.8f} en X.
    */
   private void dibujarNumero(int numero, float cx, float cy,
         float w, float h, float r, float g, float b) {
      String s = String.valueOf(numero);
      float sp = w * 1.85f;
      float sx = cx - (s.length() - 1) * sp * 0.5f;
      for (int i = 0; i < s.length(); i++)
         dibujarDigito(s.charAt(i) - '0', sx + i * sp, cy, w, h, r, g, b);
   }

   /**
    * Dibuja un único dígito (0-9) con 7 segmentos.
    * Segmentos: 0=top, 1=top-right, 2=bot-right, 3=bot, 4=bot-left, 5=top-left,
    * 6=mid
    */
   private void dibujarDigito(int d, float cx, float cy,
         float w, float h, float r, float g, float b) {
      if (d < 0 || d > 9)
         return;
      // Mapa de segmentos activos por dígito
      boolean[][] mapa = {
            { true, true, true, true, true, true, false }, // 0
            { false, true, true, false, false, false, false }, // 1
            { true, true, false, true, true, false, true }, // 2
            { true, true, true, true, false, false, true }, // 3
            { false, true, true, false, false, true, true }, // 4
            { true, false, true, true, false, true, true }, // 5
            { true, false, true, true, true, true, true }, // 6
            { true, true, true, false, false, false, false }, // 7
            { true, true, true, true, true, true, true }, // 8
            { true, true, true, true, false, true, true }, // 9
      };
      float t = 0.022f; // grosor del segmento
      float w2 = w * 0.5f;
      float h2 = h * 0.5f;
      boolean[] segs = mapa[d];

      if (segs[0])
         rect(cx, cy + h2, w + t, t, r, g, b); // top
      if (segs[1])
         rect(cx + w2, cy + h2 * 0.5f, t, h2, r, g, b); // top-right
      if (segs[2])
         rect(cx + w2, cy - h2 * 0.5f, t, h2, r, g, b); // bot-right
      if (segs[3])
         rect(cx, cy - h2, w + t, t, r, g, b); // bottom
      if (segs[4])
         rect(cx - w2, cy - h2 * 0.5f, t, h2, r, g, b); // bot-left
      if (segs[5])
         rect(cx - w2, cy + h2 * 0.5f, t, h2, r, g, b); // top-left
      if (segs[6])
         rect(cx, cy, w + t, t, r, g, b); // middle
   }

   // ════════════════════════════════════════════════════════════════════════
   // PANTALLA DE INICIO
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Panel central con:
    * - Marco oscuro + borde de acento azul
    * - Cuadros de color de cada jugador con "tecla" visual debajo
    * - Separador central
    * - Rectángulo pulsante ("presiona para empezar")
    */
   private void dibujarPantallaInicio(GameWorld world, float tiempo) {
      // Marco exterior
      rect(0f, 0.18f, 1.28f, 0.62f, 0.05f, 0.08f, 0.12f);
      rect(0f, 0.18f, 1.24f, 0.58f, 0.10f, 0.14f, 0.20f);
      rect(0f, 0.46f, 1.24f, 0.014f, 0.28f, 0.68f, 0.95f);

      // Título central
      dibujarTexto("VS", 0f, 0.28f, 0.08f, 0.12f, 1f, 1f, 1f);

      // P1: cuadro amarillo + Tecla
      float[] c1 = world.bird1.colorCuerpo;
      rect(-0.35f, 0.28f, 0.13f, 0.13f, c1[0], c1[1], c1[2]);
      dibujarTexto("SPACE", -0.35f, 0.12f, 0.035f, 0.06f, 0.8f, 0.8f, 0.8f);

      // P2: cuadro azul + Tecla
      float[] c2 = world.bird2.colorCuerpo;
      rect(0.35f, 0.28f, 0.13f, 0.13f, c2[0], c2[1], c2[2]);
      dibujarTexto("W", 0.35f, 0.12f, 0.04f, 0.07f, 0.8f, 0.8f, 0.8f);

      // Texto START pulsante
      float pulso = 0.8f + 0.2f * Math.abs((float) Math.sin(tiempo * 3.0f));
      dibujarTexto("START", 0f, -0.15f, 0.06f * pulso, 0.10f * pulso, 0.28f, 0.68f, 0.95f);
   }

   // ════════════════════════════════════════════════════════════════════════
   // PANTALLA DE GAME OVER
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Pantalla de game over con:
    * - Oscurecimiento del fondo
    * - Panel central con borde rojo
    * - Destaque del ganador con halo de color + corona de rectángulos
    * - Cuadros de color + barras de puntaje final por jugador
    * - Separador central
    * - Barra pulsante de "reiniciar"
    */
   private void dibujarPantallaGameOver(GameWorld world, float tiempo) {
      rect(0f, 0f, 2f, 2f, 0.04f, 0.05f, 0.08f);

      // Panel central
      rect(0f, 0.12f, 1.38f, 0.82f, 0.08f, 0.10f, 0.14f);
      rect(0f, 0.12f, 1.34f, 0.78f, 0.12f, 0.16f, 0.22f);
      rect(0f, 0.50f, 1.34f, 0.016f, 0.88f, 0.18f, 0.18f);

      // Título
      dibujarTexto("GAME OVER", 0f, 0.38f, 0.05f, 0.09f, 0.9f, 0.2f, 0.2f);

      boolean p1gana = world.bird1.puntaje > world.bird2.puntaje;
      boolean empate = world.bird1.puntaje == world.bird2.puntaje;

      if (empate) {
         dibujarTexto("EMPATE", 0f, 0.20f, 0.05f, 0.08f, 0.8f, 0.8f, 0.8f);
      } else {
         String ganador = p1gana ? "P1 GANA" : "P2 GANA";
         float[] gc = p1gana ? world.bird1.colorCuerpo : world.bird2.colorCuerpo;
         dibujarTexto(ganador, 0f, 0.20f, 0.05f, 0.08f, gc[0], gc[1], gc[2]);

         // Corona pequeña sobre el ganador
         float gx = p1gana ? -0.24f : 0.24f;
         rect(gx, 0.11f, 0.10f, 0.02f, 1.0f, 0.85f, 0.10f);
         rect(gx - 0.04f, 0.14f, 0.02f, 0.04f, 1.0f, 0.85f, 0.10f);
         rect(gx, 0.15f, 0.02f, 0.05f, 1.0f, 0.85f, 0.10f);
         rect(gx + 0.04f, 0.14f, 0.02f, 0.04f, 1.0f, 0.85f, 0.10f);
      }

      // Mostrar puntaje numérico final P1
      float[] c1 = world.bird1.colorCuerpo;
      rect(-0.24f, 0.00f, 0.08f, 0.08f, c1[0], c1[1], c1[2]);
      dibujarNumero(world.bird1.puntaje, -0.12f, 0.00f, 0.04f, 0.07f, 1f, 1f, 1f);

      // Mostrar puntaje numérico final P2
      float[] c2 = world.bird2.colorCuerpo;
      rect(0.24f, 0.00f, 0.08f, 0.08f, c2[0], c2[1], c2[2]);
      dibujarNumero(world.bird2.puntaje, 0.36f, 0.00f, 0.04f, 0.07f, 1f, 1f, 1f);

      // Separador central
      rect(0f, 0.00f, 0.006f, 0.15f, 0.32f, 0.42f, 0.55f);

      // Texto RESET pulsante
      float pulso = 0.88f + 0.12f * Math.abs((float) Math.sin(tiempo * 4.0f));
      dibujarTexto("RESET", 0f, -0.18f, 0.045f * pulso, 0.08f * pulso, 0.28f, 0.68f, 0.95f);
   }

   // ════════════════════════════════════════════════════════════════════════
   // PRIMITIVAS BASE
   // ════════════════════════════════════════════════════════════════════════

   /** Dibuja un rectángulo con color uniforme, sin rotación. */
   private void rect(float x, float y, float w, float h, float r, float g, float b) {
      GL20.glUniform2f(uOffsetLoc, x, y);
      GL20.glUniform2f(uScaleLoc, w, h);
      GL20.glUniform3f(uColorLoc, r, g, b);
      GL20.glUniform1f(uAngleLoc, estadoAngulo);
      GL20.glUniform2f(uPivotLoc, estadoPivotX, estadoPivotY);
      GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
   }

   // ════════════════════════════════════════════════════════════════════════
   // UTILIDADES
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Wrap de valor en un rango [min, max).
    * Usado para el parallax infinito de nubes y montañas.
    */
   private float wrap(float val, float min, float max) {
      float rng = max - min;
      while (val < min)
         val += rng;
      while (val >= max)
         val -= rng;
      return val;
   }

   /**
    * Dibuja una letra utilizando lógica de 7 segmentos.
    * Mapeo: 0=top, 1=top-right, 2=bot-right, 3=bottom, 4=bot-left, 5=top-left,
    * 6=middle
    */
   private void dibujarLetra(char letra, float cx, float cy, float w, float h, float r, float g, float b) {
      float t = 0.022f;
      float w2 = w * 0.5f;
      float h2 = h * 0.5f;
      boolean[] segs;

      switch (Character.toUpperCase(letra)) {
         case 'A':
            segs = new boolean[] { true, true, true, false, true, true, true };
            break;
         case 'B':
            segs = new boolean[] { false, false, true, true, true, true, true };
            break; // 'b' minúscula
         case 'C':
            segs = new boolean[] { true, false, false, true, true, true, false };
            break;
         case 'D':
            segs = new boolean[] { false, true, true, true, true, false, true };
            break; // 'd' minúscula
         case 'E':
            segs = new boolean[] { true, false, false, true, true, true, true };
            break;
         case 'F':
            segs = new boolean[] { true, false, false, false, true, true, true };
            break;
         case 'G':
            segs = new boolean[] { true, false, true, true, true, true, false };
            break;
         case 'I':
            segs = new boolean[] { false, true, true, false, false, false, false };
            break; // Lado derecho
         case 'L':
            segs = new boolean[] { false, false, false, true, true, true, false };
            break;
         case 'M':
            segs = new boolean[] { true, true, true, false, true, true, false };
            break; // U invertida alta
         case 'N':
            segs = new boolean[] { false, false, true, false, true, false, true };
            break; // 'n' minúscula
         case 'O':
            segs = new boolean[] { true, true, true, true, true, true, false };
            break;
         case 'P':
            segs = new boolean[] { true, true, false, false, true, true, true };
            break;
         case 'R':
            segs = new boolean[] { false, false, false, false, true, false, true };
            break; // 'r' minúscula
         case 'S':
            segs = new boolean[] { true, false, true, true, false, true, true };
            break; // Igual al 5
         case 'T':
            segs = new boolean[] { false, false, false, true, true, true, true };
            break; // 't' minúscula
         case 'V':
            segs = new boolean[] { false, false, true, true, true, false, false };
            break; // 'u' baja
         case 'W':
            segs = new boolean[] { false, true, true, true, true, true, false };
            break; // U alta
         case 'Y':
            segs = new boolean[] { false, true, true, true, false, true, true };
            break;
         default:
            return; // Espacios o caracteres no mapeados no dibujan nada
      }

      if (segs[0])
         rect(cx, cy + h2, w + t, t, r, g, b);
      if (segs[1])
         rect(cx + w2, cy + h2 * 0.5f, t, h2, r, g, b);
      if (segs[2])
         rect(cx + w2, cy - h2 * 0.5f, t, h2, r, g, b);
      if (segs[3])
         rect(cx, cy - h2, w + t, t, r, g, b);
      if (segs[4])
         rect(cx - w2, cy - h2 * 0.5f, t, h2, r, g, b);
      if (segs[5])
         rect(cx - w2, cy + h2 * 0.5f, t, h2, r, g, b);
      if (segs[6])
         rect(cx, cy, w + t, t, r, g, b);
   }

   /** Escribe una cadena de texto mezclando letras y números */
   private void dibujarTexto(String texto, float cx, float cy, float w, float h, float r, float g, float b) {
      float sp = w * 1.85f;
      float sx = cx - (texto.length() - 1) * sp * 0.5f;
      for (int i = 0; i < texto.length(); i++) {
         char c = texto.charAt(i);
         if (c == ' ')
            continue;

         if (Character.isDigit(c)) {
            dibujarDigito(c - '0', sx + i * sp, cy, w, h, r, g, b);
         } else {
            dibujarLetra(c, sx + i * sp, cy, w, h, r, g, b);
         }
      }
   }
}