package com.graphics;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Renderer — TODO EL CÓDIGO DE PRESENTACIÓN OPENGL EN UN ÚNICO LUGAR.
 *
 * Responsabilidad única: convertir estado de GameWorld a píxeles en pantalla.
 * NO contiene lógica de juego — solo lectura de GameWorld y dibujo OpenGL.
 *
 * ════════════════════════════════════════════════════════════════════════════
 * ARQUITECTURA OPENGL
 * ════════════════════════════════════════════════════════════════════════════
 *
 * Shader Program:
 * - Vertex Shader: transforma vértices con offset, escala, rotación y pivot
 * - Fragment Shader: asigna color uniforme a cada píxel
 *
 * Uniforms (parámetros que controlan cómo se dibuja cada objeto):
 * - uOffset: posición central en pantalla (NDC coordinates)
 * - uScale: ancho y alto del rectángulo
 * - uColor: color RGB
 * - uAngle: rotación en radianes
 * - uPivot: punto pivote para rotación
 *
 * Geometría base: Quad (rectángulo 2D)
 * - Almacenado en VAO/VBO de forma permanente
 * - Se reutiliza para dibujar TODOS los rectángulos (escalados y posicionados)
 *
 * ════════════════════════════════════════════════════════════════════════════
 * ORDEN DE RENDER (de atrás hacia adelante)
 * ════════════════════════════════════════════════════════════════════════════
 *
 * 1. Fondo: 8 franjas horizontales de degradado (cielo azul)
 * 2. Montañas lejanas (parallax lento, efecto profundidad)
 * 3. Montañas cercanas (parallax medio, más oscuras)
 * 4. Nubes (parallax muy lento, efecto muy lejano)
 * 5. Tuberías (obstáculos verdes con sombra, borde 3D y tapa)
 * 6. Suelo (tierra + hierba + líneas de movimiento parallax)
 * 7. Partículas (pequeños cuadrados de color que caen y se desvanecen)
 * 8. Pájaros vivos (después de muertos, encima de todo)
 * 9. Pájaros muertos (con parpadeo flickering y rotación -1.5 rad)
 * 10. HUD superior (franja oscura con puntajes, nivel, barra de velocidad)
 * 11. Pantalla de inicio (VS, cuadros de color, teclas, START pulsante)
 * 12. Pantalla de game over (resultado, ganador, puntuaciones, RESET pulsante)
 *
 * NOTA: El orden es crítico. Si dibujas pájaros antes de suelo, se ven detrás.
 *
 * ════════════════════════════════════════════════════════════════════════════
 * PRIMITIVA BASE: rect() — Dibuja un rectángulo
 * ════════════════════════════════════════════════════════════════════════════
 *
 * void rect(float x, float y, float w, float h, float r, float g, float b)
 *
 * Parámetros:
 * - x, y: posición central en NDC [-1, 1]
 * - w, h: ancho y alto en NDC
 * - r, g, b: color RGB [0, 1]
 *
 * Internamente:
 * - Asigna uniforms (posición, tamaño, color)
 * - Dibuja el quad usando glDrawArrays (6 vértices = 2 triángulos)
 *
 * Ejemplos:
 * - rect(0, 0.5, 0.1, 0.1, 1, 0, 0) → cuadrado rojo chico centrado arriba
 * - rect(-0.5, -0.5, 1, 0.2, 0.5, 0.5, 0.5) → rectángulo gris en esquina
 * inferior izq
 */
public class Renderer {

   // ════════════════════════════════════════════════════════════════════════
   // RECURSOS OPENGL (permanentes)
   // ════════════════════════════════════════════════════════════════════════

   private int programa; // Programa OpenGL compilado (vertex + fragment shader)
   private int vao, vbo; // Vertex Array Object, Vertex Buffer Object (geometría)

   // Ubicaciones de uniforms (índices de variables en el shader)
   private int uOffsetLoc, uScaleLoc, uColorLoc, uAngleLoc, uPivotLoc;

   // ── Estado de rotación activo (reset a 0 después de cada dibujarPájaro) ──
   // Estos se usan para rotar el pájaro según su velocidad vertical
   private float estadoAngulo = 0f; // Ángulo de rotación actual
   private float estadoPivotX = 0f; // Punto pivote X para la rotación
   private float estadoPivotY = 0f; // Punto pivote Y para la rotación

   // ════════════════════════════════════════════════════════════════════════
   // INIT / CLEANUP
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Inicializa OpenGL: compila shaders y crea geometría base.
    * Llamado una sola vez en AppFlappyBird.init().
    */
   public void init() {
      crearShaders(); // Compilar vertex + fragment shader, linkear programa
      crearQuadBase(); // Crear quad permanente en VAO/VBO
   }

   /**
    * Libera recursos OpenGL antes de terminar.
    * Llamado en AppFlappyBird.cleanup().
    */
   public void cleanup() {
      GL30.glDeleteVertexArrays(vao); // Borrar VAO
      GL15.glDeleteBuffers(vbo); // Borrar VBO
      GL20.glDeleteProgram(programa); // Borrar programa
   }

   // ════════════════════════════════════════════════════════════════════════
   // SHADERS (programas GPU)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Compila y linkea los shaders (vertex + fragment).
    * * Vertex Shader:
    * - Recibe vértices del quad base [-0.5, 0.5] × [-0.5, 0.5]
    * - Escala por uScale (ancho, alto)
    * - Traslada por uOffset (posición)
    * - Aplica rotación por uAngle alrededor de uPivot
    * - Resultado: vértices transformados en NDC [-1, 1]
    *
    * Fragment Shader:
    * - Todos los píxeles dentro del quad tienen el mismo color (uColor)
    * - Salida: vec4(uColor, 1.0) = opaco con color especificado
    *
    * Uniforms:
    * - uOffset (vec2): posición central
    * - uScale (vec2): escala (ancho, alto)
    * - uColor (vec3): color RGB
    * - uAngle (float): ángulo en radianes
    * - uPivot (vec2): punto de rotación
    */
   private void crearShaders() {
      // ── Vertex Shader ──────────────────────────────────────────────────────
      // Recibe vértices (-0.5,-0.5) a (0.5,0.5) del quad base
      // Transforma a posición y tamaño final
      String vertexSrc = """
            #version 330 core

            // Entrada: vértices del quad
            layout(location = 0) in vec3 aPos;

            // Uniforms que controlan la transformación
            uniform vec2  uOffset;     // Posición central (x, y)
            uniform vec2  uScale;      // Escala (ancho, alto)
            uniform float uAngle;      // Rotación en radianes
            uniform vec2  uPivot;      // Centro de rotación

            void main() {
                // 1. Aplicar escala al quad base
                vec2 pos = aPos.xy * uScale + uOffset;

                // 2. Aplicar rotación alrededor del pivot
                pos -= uPivot;  // Trasladar al origen para rotar
                float c = cos(uAngle), s = sin(uAngle);
                pos = vec2(pos.x*c - pos.y*s, pos.x*s + pos.y*c);  // Matriz 2D rotación
                pos += uPivot;  // Trasladar de vuelta

                // 3. Salida en NDC (coordenadas normalizadas de pantalla)
                gl_Position = vec4(pos, aPos.z, 1.0);
            }
            """;

      // ── Fragment Shader ────────────────────────────────────────────────────
      // Color uniforme para todos los píxeles del quad
      String fragmentSrc = """
            #version 330 core

            // Color a usar para todos los píxeles
            uniform vec3 uColor;

            // Salida: color del píxel
            out vec4 fragColor;

            void main() {
                fragColor = vec4(uColor, 1.0);  // Opaco (alpha=1)
            }
            """;

      // Compilar ambos shaders
      int vs = compilar(vertexSrc, GL20.GL_VERTEX_SHADER, "Vertex");
      int fs = compilar(fragmentSrc, GL20.GL_FRAGMENT_SHADER, "Fragment");

      // Crear programa y linkear
      programa = GL20.glCreateProgram();
      GL20.glAttachShader(programa, vs);
      GL20.glAttachShader(programa, fs);
      GL20.glLinkProgram(programa);
      if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
         throw new RuntimeException("Link: " + GL20.glGetProgramInfoLog(programa));

      // Obtener ubicaciones de uniforms (para setearlos después)
      uOffsetLoc = GL20.glGetUniformLocation(programa, "uOffset");
      uScaleLoc = GL20.glGetUniformLocation(programa, "uScale");
      uColorLoc = GL20.glGetUniformLocation(programa, "uColor");
      uAngleLoc = GL20.glGetUniformLocation(programa, "uAngle");
      uPivotLoc = GL20.glGetUniformLocation(programa, "uPivot");

      // Limpiar shaders individuales (ya están en el programa)
      GL20.glDeleteShader(vs);
      GL20.glDeleteShader(fs);
   }

   /**
    * Compila un shader individual.
    * * @param src Código fuente GLSL
    * 
    * @param tipo   GL_VERTEX_SHADER o GL_FRAGMENT_SHADER
    * @param nombre Para debugging (ej: "Vertex", "Fragment")
    */
   private int compilar(String src, int tipo, String nombre) {
      int s = GL20.glCreateShader(tipo);
      GL20.glShaderSource(s, src);
      GL20.glCompileShader(s);
      if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
         throw new RuntimeException(nombre + ": " + GL20.glGetShaderInfoLog(s));
      return s;
   }

   /**
    * Crea el quad base (rectángulo 2D unitario).
    * * Geometría:
    * - Vértices en NDC: (-0.5, -0.5) a (0.5, 0.5)
    * - 6 vértices total = 2 triángulos
    * - Z es siempre 0 (2D)
    * * Este quad se reutiliza para dibujar TODOS los rectángulos del juego.
    * Cada quad dibujado se escala y traslada por uniforms.
    */
   private void crearQuadBase() {
      // Vértices del quad base (triángulo 1 + triángulo 2)
      float[] verts = {
            // Triángulo 1
            -0.5f, -0.5f, 0f, // Vértice 1 (abajo-izq)
            0.5f, -0.5f, 0f, // Vértice 2 (abajo-der)
            0.5f, 0.5f, 0f, // Vértice 3 (arriba-der)

            // Triángulo 2 (forma rectángulo)
            -0.5f, -0.5f, 0f, // Vértice 1 (abajo-izq) - repetido
            0.5f, 0.5f, 0f, // Vértice 3 (arriba-der) - repetido
            -0.5f, 0.5f, 0f // Vértice 4 (arriba-izq)
      };

      // Crear VAO (almacena estado de entrada de vértices)
      vao = GL30.glGenVertexArrays();
      GL30.glBindVertexArray(vao);

      // Crear VBO (almacena datos de vértices en GPU)
      vbo = GL15.glGenBuffers();
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

      // Copiar datos de vértices a GPU
      FloatBuffer buf = BufferUtils.createFloatBuffer(verts.length);
      buf.put(verts).flip(); // Preparar buffer
      GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buf, GL15.GL_STATIC_DRAW);

      // Configurar cómo interpretar los datos de vértices
      GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
      GL20.glEnableVertexAttribArray(0);

      // Desunbind
      GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
      GL30.glBindVertexArray(0);
   }

   // ════════════════════════════════════════════════════════════════════════
   // RENDER PRINCIPAL (entry point)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja un frame completo del juego.
    * Llamado por AppFlappyBird.loop() cada frame.
    *
    * @param world  Estado actual del mundo (leer, NO modificar)
    * @param tiempo Tiempo acumulado en segundos desde el inicio (para animaciones)
    *
    *               Proceso:
    *               1. Limpiar pantalla (fondo negro)
    *               2. Activar programa OpenGL
    *               3. Bindear VAO (geometría)
    *               4. Dibujar capas en orden (fondo → pájaros → HUD)
    *               5. Unbind
    */
   public void render(GameWorld world, float tiempo) {
      // Limpiar pantalla con color negro
      GL11.glClearColor(0f, 0f, 0f, 1f);
      GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

      // Usar programa OpenGL compilado
      GL20.glUseProgram(programa);

      // Bindear VAO (geometría del quad)
      GL30.glBindVertexArray(vao);

      // ─────────────────────────────────────────────────────────────────────
      // CAPAS DE RENDER (en orden de atrás hacia adelante)
      // ─────────────────────────────────────────────────────────────────────

      // Capa 1: Fondo (8 franjas de degradado azul)
      dibujarFondo();

      // Capa 2 & 3: Montañas con parallax (lejanas + cercanas)
      dibujarMontanas(world.parallaxMontanas * Constants.PARALLAX_MONTANAS_LEJOS,
            world.parallaxMontanas, tiempo);

      // Capa 4: Nubes decorativas
      dibujarNubes(world.parallaxNubes);

      // Capa 5: Tuberías (obstáculos verdes con sombra)
      dibujarTuberias(world);

      // Capa 6: Suelo (tierra + hierba + líneas de movimiento)
      dibujarSuelo(world.parallaxSuelo);

      // Capa 7: Partículas (explosiones al morir)
      dibujarParticulas(world);

      // Capa 8 & 9: Pájaros (muertos primero con parpadeo, vivos encima)
      if (!world.bird1.alive)
         dibujarPajaroMuerto(world.bird1, world.getFlickerTimer1(), tiempo);
      if (!world.bird2.alive)
         dibujarPajaroMuerto(world.bird2, world.getFlickerTimer2(), tiempo);
      if (world.bird1.alive)
         dibujarPajaro(world.bird1, tiempo);
      if (world.bird2.alive)
         dibujarPajaro(world.bird2, tiempo);

      // Capa 10: HUD superior (franja oscura con puntajes y nivel)
      dibujarHUD(world, tiempo);

      // Capa 11: Pantalla de inicio (si aún no comenzó)
      if (!world.started && !world.gameOver) {
         dibujarPantallaInicio(world, tiempo);
      }

      // Capa 12: Pantalla de game over (si es game over)
      if (world.gameOver) {
         dibujarPantallaGameOver(world, tiempo);
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // CAPAS DE FONDO
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja el fondo del cielo con un degradado vertical suave.
    *
    * Técnica: 8 franjas horizontales superpuestas de arriba a abajo.
    * Cada franja tiene un color más claro y más azul hacia abajo
    * (efecto de cielo gradual: oscuro → claro).
    *
    * Colores:
    * - Superior (y=1.0): azul oscuro (0.25, 0.45, 0.75)
    * - Inferior (y=-0.5): azul celeste muy claro (0.70, 0.91, 0.97)
    *
    * Formato de cada capa: { yCentro, altoFranja, r, g, b }
    * Las franjas se solapan ligeramente para un degradado suave
    * sin bandas visibles.
    */
   private void dibujarFondo() {
      // { yCentro, alto, r, g, b }
      // Transición suave: azul oscuro (arriba) → azul celeste claro (abajo)
      float[][] capas = {
            { 1.00f, 0.30f, 0.25f, 0.45f, 0.75f }, // Azul oscuro — parte superior
            { 0.72f, 0.26f, 0.35f, 0.60f, 0.88f }, // Azul oscuro-medio
            { 0.48f, 0.22f, 0.45f, 0.72f, 0.93f }, // Azul medio — zona central
            { 0.28f, 0.20f, 0.55f, 0.80f, 0.95f }, // Azul claro-medio
            { 0.10f, 0.20f, 0.62f, 0.85f, 0.96f }, // Azul claro
            { -0.08f, 0.20f, 0.66f, 0.88f, 0.97f }, // Azul muy claro
            { -0.26f, 0.20f, 0.68f, 0.90f, 0.97f }, // Azul celeste claro
            { -0.50f, 0.42f, 0.70f, 0.91f, 0.97f }, // Azul celeste — parte inferior (más alto)
      };

      // Dibujar todas las franjas de arriba a abajo
      // Cada franja es un rectángulo ancho (2.0 = pantalla completa) con su color
      for (float[] c : capas)
         rect(0f, c[0], 2f, c[1], c[2], c[3], c[4]);
   }

   /**
    * Dibuja montañas en DOS CAPAS con parallax diferente.
    *
    * EFECTO DE PROFUNDIDAD:
    * - Montañas LEJANAS: parallax lento (factor 0.18), gris azulado claro
    * Se mueven lentamente, simulan estar muy lejos
    * - Montañas CERCANAS: parallax más rápido (factor 0.90), verde oscuro
    * Se mueven más rápido, simulan estar más cerca
    *
    * ESTRUCTURA DE CADA MONTAÑA:
    * Una montaña se dibuja como un "trapecio" simulado con 3 rectángulos:
    * 1. Base ancha (fondo de la montaña)
    * 2. Cuerpo medio (70% del ancho original)
    * 3. Cima estrecha (40% del ancho, para formar la punta)
    * + NIEVE opcional en la cima (solo en montañas lejanas)
    *
    * PARALLAX:
    * El offset acumulado con wrap() permite que las montañas
    * se corten en los bordes y reaparezcan en el opuesto (efecto infinito).
    *
    * @param offsetLejos Offset acumulado para capa lejana (lento)
    * @param offsetCerca Offset acumulado para capa cercana (rápido)
    * @param tiempo      Tiempo acumulado (no usado para montañas estáticas)
    */
   private void dibujarMontanas(float offsetLejos, float offsetCerca, float tiempo) {
      float baseY = Constants.SUELO_Y + Constants.SUELO_H * 0.5f; // Arrancan desde el suelo

      // ── MONTAÑAS LEJANAS — gris azulado claro (casi cielo) ─────────────────
      // Más lentas, más difusas, simulan estar muy lejos (profundidad)
      for (float[] m : Constants.MONTANAS_LEJOS) {
         // m[0] = posición base X, m[1] = ancho, m[2] = alto
         float mx = wrap(m[0] + offsetLejos, -1.5f, 1.5f); // Wrap infinito
         float mw = m[1];
         float mh = m[2];
         float my = baseY + mh * 0.5f; // Centro Y (anclado en el suelo)

         // NIEVE en la cima (rectángulo blanco pequeño en la punta)
         rect(mx, my + mh * 0.25f, mw * 0.35f, mh * 0.28f, 0.92f, 0.95f, 1.00f);

         // CAPAS DE LA MONTAÑA (base → medio → cima)
         rect(mx, my, mw, mh, 0.60f, 0.68f, 0.78f); // Base (ancha, azul grisáceo)
         rect(mx, my + mh * 0.15f, mw * 0.70f, mh * 0.70f, // Cuerpo (70% ancho)
               0.55f, 0.63f, 0.74f);
         rect(mx, my + mh * 0.32f, mw * 0.40f, mh * 0.38f, // Cima (40% ancho)
               0.50f, 0.58f, 0.70f);
      }

      // ── MONTAÑAS CERCANAS — verde oscuro, más definidas (foreground) ──────
      // Más rápidas (parallax mayor), oscuras, simulan estar en primer plano
      for (float[] m : Constants.MONTANAS_CERCA) {
         float mx = wrap(m[0] + offsetCerca, -1.5f, 1.5f);
         float mw = m[1];
         float mh = m[2];
         float my = baseY + mh * 0.5f;

         // CAPAS DE MONTAÑA CERCANA (sin nieve, verde oscuro)
         rect(mx, my, mw, mh, 0.22f, 0.38f, 0.20f); // Base (verde oscuro)
         rect(mx, my + mh * 0.20f, mw * 0.65f, mh * 0.60f, // Cuerpo (65% ancho)
               0.18f, 0.33f, 0.16f);
         rect(mx, my + mh * 0.38f, mw * 0.35f, mh * 0.28f, // Cima (35% ancho)
               0.15f, 0.28f, 0.13f);
      }
   }

   /**
    * Dibuja nubes con parallax muy lento (factor 0.05).
    *
    * Efecto visual:
    * - Nubes parecen estar muy lejos (arriba del cielo)
    * - Se mueven muy lentamente, apenas perceptible
    * - Crean profundidad atmosférica
    *
    * ESTRUCTURA DE CADA NUBE:
    * Una nube se dibuja con 4 rectángulos (para forma orgánica):
    * 1. SOMBRA: rectángulo desplazado ligeramente abajo-derecha (gris oscuro)
    * Simula una sombra suave, efecto de profundidad
    * 2. CUERPO PRINCIPAL: rectángulo ancho y bajo (blanco puro)
    * La forma base de la nube
    * 3. PROTUBERANCIA IZQUIERDA: pequeño rectángulo redondeado a la izquierda
    * Detalles esponjosos de la nube
    * 4. PROTUBERANCIA DERECHA: pequeño rectángulo redondeado a la derecha
    * Simetría y forma más orgánica
    *
    * PARALLAX INFINITO:
    * Las nubes usan wrap() para reciclar posiciones,
    * creando la ilusión de un cielo infinito.
    *
    * @param offset Offset acumulado para parallax muy lento (0.05 factor)
    */
   private void dibujarNubes(float offset) {
      for (float[] n : Constants.NUBES) {
         // n[0] = posición base X, n[1] = posición Y, n[2] = ancho
         float nx = wrap(n[0] + offset, -1.6f, 1.6f); // Wrap infinito (más ancho para transición suave)
         float ny = n[1];
         float nw = n[2];

         // 1. SOMBRA (gris oscuro, ligeramente desplazada)
         // Desplazamiento: +0.005 X (derecha), -0.004 Y (abajo)
         rect(nx + 0.005f, ny - 0.004f, nw, 0.060f, 0.82f, 0.88f, 0.93f);

         // 2. CUERPO PRINCIPAL (blanco puro)
         // Es el elemento visual más importante de la nube
         rect(nx, ny, nw, 0.070f, 1.00f, 1.00f, 1.00f);

         // 3. PROTUBERANCIA IZQUIERDA (forma esponjosa)
         // Desplazada 26% del ancho a la izquierda, levemente arriba
         rect(nx - nw * 0.26f, ny + 0.024f, nw * 0.48f, 0.058f, 1.00f, 1.00f, 1.00f);

         // 4. PROTUBERANCIA DERECHA (forma simétrica)
         // Desplazada 24% del ancho a la derecha, levemente arriba
         rect(nx + nw * 0.24f, ny + 0.020f, nw * 0.42f, 0.050f, 1.00f, 1.00f, 1.00f);
      }
   }

   /**
    * Dibuja el suelo con textura visual y efecto de movimiento parallax.
    *
    * El suelo consta de TRES ELEMENTOS visuales:
    *
    * 1. TIERRA BASE (marrón):
    * Franja ancha de color marrón oscuro que va de izq a der
    * Color: (0.52, 0.35, 0.14) = marrón terroso
    *
    * 2. FRANJA DE HIERBA (verde + borde oscuro):
    * Dos rectángulos en el tope del suelo:
    * - Hierba principal (verde): (0.28, 0.65, 0.20)
    * - Borde oscuro debajo (sombra de profundidad): más oscuro
    * Simulan una capa de hierba sobre la tierra
    *
    * 3. LÍNEAS DE MOVIMIENTO (parallax):
    * 8 segmentos verticales que avanzan y hacen wrap
    * Efecto de "carretilla" que gira infinitamente
    * Simula movimiento del suelo cuando el pájaro está quieto
    *
    * COORDENADAS:
    * - Y del suelo: Constants.SUELO_Y (normalmente -0.80)
    * - Alto del suelo: Constants.SUELO_H (normalmente 0.20)
    * - Borde superior (hierba): Constants.SUELO_BORDE_TOP
    *
    * PARALLAX DE LÍNEAS:
    * Las líneas usan un desplazamiento modulo (%) 0.25
    * para crear un efecto de carretilla que gira continuamente.
    *
    * @param offsetSuelo Offset acumulado del suelo (parallax factor 0.90 = rápido)
    */
   private void dibujarSuelo(float offsetSuelo) {
      float sy = Constants.SUELO_Y; // Posición Y del suelo
      float sh = Constants.SUELO_H; // Alto del suelo
      float bordTop = Constants.SUELO_BORDE_TOP; // Tope del borde de hierba

      // ── TIERRA BASE ──────────────────────────────────────────────────────
      // Rectángulo ancho que cubre toda la pantalla horizontalmente
      rect(0f, sy, 2f, sh, 0.52f, 0.35f, 0.14f);

      // ── FRANJA DE HIERBA ─────────────────────────────────────────────────
      // Franja delgada de hierba verde en el tope
      rect(0f, bordTop - 0.012f, 2f, 0.030f, 0.28f, 0.65f, 0.20f);

      // Borde oscuro debajo de la hierba (efecto de sombra/profundidad)
      rect(0f, bordTop - 0.028f, 2f, 0.008f, 0.20f, 0.50f, 0.15f);

      // ── LÍNEAS DE MOVIMIENTO (carretilla parallax) ─────────────────────────
      // 8 líneas verticales que avanzan y hacen wrap
      // Crea efecto visual de "pista de tren" o "carretilla" girando
      for (int i = 0; i < 8; i++) {
         // Calcular posición X de cada línea
         // Espaciadas 0.25 de distancia, con offset modulo para wrap infinito
         float lx = -1.0f + (i * 0.25f) + offsetSuelo % 0.25f;

         // Si sale del lado derecho, wrappear al lado izquierdo
         if (lx > 1.0f)
            lx -= 2.0f;

         // Dibujar línea vertical delgada (ancho 0.008, alto aprox 45% del suelo)
         rect(lx, sy + 0.02f, 0.008f, sh * 0.45f, 0.42f, 0.28f, 0.10f);
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // TUBERÍAS
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja todas las tuberías (obstáculos) del juego.
    *
    * Cada tubería se compone de DOS SECCIONES (superior e inferior):
    * - Superior: desde el gap hacia arriba
    * - Inferior: desde el gap hacia abajo
    *
    * Efectos visuales por sección:
    * 1. Sombra (lado derecho) — gris oscuro para profundidad
    * 2. Cuerpo (verde oscuro) — color principal
    * 3. Borde claro (lado izquierdo) — efecto 3D (luz desde la izquierda)
    * 4. Tapa (en el extremo del gap) — borde redondeado visual
    *
    * Gap (hueco):
    * - Centro: t.gapCentroY
    * - Altura: Constants.GAP_ALTO
    * - El pájaro debe pasar por aquí sin tocar nada
    */
   private void dibujarTuberias(GameWorld world) {
      for (Tuberia t : world.tuberias) {
         // Límites del hueco (gap)
         float gTop = t.gapCentroY + Constants.GAP_ALTO * 0.5f; // Techo del gap
         float gBot = t.gapCentroY - Constants.GAP_ALTO * 0.5f; // Piso del gap

         // ── TUBERÍA SUPERIOR ──────────────────────────────────────────
         // Desde el gap hacia arriba hasta el techo de la pantalla
         float hSup = 1.0f - gTop; // Altura de esta sección
         if (hSup > 0f) {
            float cy = gTop + hSup * 0.5f; // Centro Y de la sección

            // 1. Sombra lateral (lado derecho, desplazado)
            rect(t.x + 0.012f, cy, Constants.TUBERIA_ANCHO, hSup, 0.08f, 0.42f, 0.10f);

            // 2. Cuerpo principal (verde oscuro)
            rect(t.x, cy, Constants.TUBERIA_ANCHO, hSup, 0.18f, 0.70f, 0.22f);

            // 3. Borde claro izquierdo (efecto 3D)
            rect(t.x - Constants.TUBERIA_ANCHO * 0.36f, cy,
                  Constants.TUBERIA_ANCHO * 0.12f, hSup, 0.30f, 0.85f, 0.32f);

            // 4. Tapa (borde redondeado en el extremo del gap)
            rect(t.x, gTop + 0.028f, Constants.TUBERIA_ANCHO + 0.042f, 0.052f, 0.14f, 0.62f, 0.17f);
            rect(t.x, gTop + 0.028f, Constants.TUBERIA_ANCHO + 0.040f, 0.044f, 0.20f, 0.75f, 0.24f);
         }

         // ── TUBERÍA INFERIOR ──────────────────────────────────────────
         // Desde el gap hacia abajo hasta el suelo
         float hInf = gBot + 1.0f; // Altura de esta sección (gBot es negativo)
         if (hInf > 0f) {
            float cy = -1.0f + hInf * 0.5f; // Centro Y de la sección

            // 1. Sombra
            rect(t.x + 0.012f, cy, Constants.TUBERIA_ANCHO, hInf, 0.08f, 0.42f, 0.10f);

            // 2. Cuerpo
            rect(t.x, cy, Constants.TUBERIA_ANCHO, hInf, 0.18f, 0.70f, 0.22f);

            // 3. Borde claro
            rect(t.x - Constants.TUBERIA_ANCHO * 0.36f, cy,
                  Constants.TUBERIA_ANCHO * 0.12f, hInf, 0.30f, 0.85f, 0.32f);

            // 4. Tapa (en el otro extremo del gap)
            rect(t.x, gBot - 0.028f, Constants.TUBERIA_ANCHO + 0.042f, 0.052f, 0.14f, 0.62f, 0.17f);
            rect(t.x, gBot - 0.028f, Constants.TUBERIA_ANCHO + 0.040f, 0.044f, 0.20f, 0.75f, 0.24f);
         }
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // PARTÍCULAS
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja partículas de explosión cuando un pájaro muere.
    *
    * EFECTO VISUAL:
    * Cuando un pájaro colisiona con una tubería o el suelo, se emite
    * una ráfaga de pequeños cuadrados de color que:
    * - Salen disparados en direcciones aleatorias
    * - Caen hacia abajo por gravedad
    * - Se desvanecen gradualmente (fade out)
    * - Encogen según se desvanecen
    *
    * PROPIEDADES DE CADA PARTÍCULA:
    * - Posición (x, y): se actualiza cada frame con velocidad + gravedad
    * - Color (r, g, b): heredado del pájaro que murió (amarillo/azul)
    * - Tamaño inicial (size): ~0.015
    * - Tiempo de vida (lifeTime): ~0.8 segundos
    * - Alpha (transparencia): declina de 1.0 a 0.0 según tiempo de vida
    *
    * RENDERING:
    * Para cada partícula viva:
    * 1. Calcular alpha (transparencia) según tiempo de vida restante
    * 2. Encogimiento: size * alpha (más transparente = más chico)
    * 3. Solo dibujar si size > 0.002 (umbral mínimo para visibilidad)
    * 4. Usar rect() para dibujar cuadrado de color con el tamaño ajustado
    *
    * @param world Mundo (para acceder a la lista de partículas activas)
    **/
   private void dibujarParticulas(GameWorld world) {
      for (ParticleSystem.Particle p : world.particulas.getParticles()) {
         // Calcular alpha (transparencia) actual de la partícula
         // Comienza en 1.0 (opaca) y baja a 0.0 (invisible) según lifeTime
         float a = p.alpha();

         // Encogimiento progresivo: partículas se empequeñecen según se desvanecen
         // Efecto: "explotan y se dispersan"
         float size = p.size * a;

         // Solo dibujar si la partícula es visible (tamaño > umbral mínimo)
         if (size > 0.002f)
            rect(p.x, p.y, size, size, p.r, p.g, p.b);
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // PÁJAROS
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja un pájaro VIVO con todos sus detalles gráficos.
    *
    * Estructura del pájaro (de atrás hacia adelante):
    * 1. Cola (trasera del pájaro) — 3 rectángulos escalonados
    * 2. Ala trasera — rectángulo que anima hacia arriba/abajo
    * 3. Cuerpo — rectángulo principal (color base del pájaro)
    * 4. Panza — rectángulo claro en la zona abdominal
    * 5. Ala delantera — rectángulo animado (imitación de movimiento)
    * 6. Cabeza — rectángulo para la parte superior
    * 7. Ojo — blanco (base) + pupila negra + brillo blanco
    * 8. Mejilla — rectángulo rosado para dar expresión
    * 9. Pico — mandíbula superior + inferior (2 rectángulos)
    *
    * Animaciones:
    * - Inclinación (rotate): más veloz hacia abajo = inclinado, más lento = recto
    * - Aleteo: después de saltar, alas suben/bajan rápidamente
    *
    * Parámetros:
    * 
    * @param b      Pájaro a dibujar (contiene posición, colores, estado)
    * @param tiempo Tiempo acumulado (para aleteo sinusoidal en caída lenta)
    */
   private void dibujarPajaro(Bird b, float tiempo) {
      float x = b.birdX, y = b.birdY;
      float W = Constants.BIRD_ANCHO, H = Constants.BIRD_ALTO;
      float[] c = b.colorCuerpo, pz = b.colorPanza,
            al = b.colorAla, pk = b.colorPico;

      // ── Configurar inclinación (rotación) según velocidad vertical ───────
      // Hacia arriba = recto (angle ≈ 0), hacia abajo = inclinado (angle < 0)
      estadoAngulo = Math.max(-1.2f, Math.min(0.55f, b.velY * 0.44f));
      estadoPivotX = x;
      estadoPivotY = y;

      // ── Aleteo: animación post-salto y en caída lenta ──────────────────
      // Cuando tiempoAleteo > 0: alas se mueven rápido (post-salto)
      // Cuando tiempoAleteo = 0: alas se mueven lentamente (caída)
      float aleteo = (b.tiempoAleteo > 0)
            ? (float) Math.sin((0.25f - b.tiempoAleteo) * Math.PI * 8.0f) * H * 0.35f
            : (float) Math.sin(tiempo * 6.0f) * H * 0.12f;

      // ── COLA — 3 rectángulos escalonados (efecto de plumas) ──────────────
      // Rectángulo 1 de cola (arriba)
      rect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, al[0], al[1], al[2]);
      // Rectángulo 2 de cola (centro)
      rect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, al[0] * 0.94f, al[1] * 0.88f, al[2]);
      // Rectángulo 3 de cola (abajo)
      rect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, al[0] * 0.88f, al[1] * 0.77f, al[2]);

      // ── ALA TRASERA — animada hacia abajo (durante aleteo) ──────────────
      // Esta ala se mueve más en la animación (aleteo más visible)
      rect(x - W * 0.15f, y - H * 0.30f + aleteo, W * 0.80f, H * 0.30f, al[0], al[1], al[2]);

      // ── CUERPO PRINCIPAL — rectángulo grande ────────────────────────────
      // Este es el cuerpo base del pájaro (color dominante)
      rect(x, y, W, H, c[0], c[1], c[2]);

      // ── PANZA — vientre claro ──────────────────────────────────────────
      // Rectángulo en la parte inferior-delantera para dar volumen
      rect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, pz[0], pz[1], pz[2]);

      // ── ALA DELANTERA — animada hacia arriba (con menor amplitud) ─────
      // Ala que sale hacia adelante, se mueve menos que la trasera
      rect(x - W * 0.10f, y + H * 0.22f + aleteo * 0.7f, W * 0.70f, H * 0.26f, al[0], al[1], al[2]);

      // ── CABEZA — parte superior del cuerpo ──────────────────────────────
      // Rectángulo para la cabeza (donde van ojos)
      rect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, c[0], c[1], c[2]);

      // ── OJO (blanco, pupila, brillo) ────────────────────────────────────
      // Blanco del ojo
      rect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.28f, 1f, 1f, 1f);
      // Pupila (negra)
      rect(x + W * 0.48f, y + H * 0.50f, W * 0.15f, H * 0.18f, 0.08f, 0.08f, 0.18f);
      // Brillo (blanco pequeño)
      rect(x + W * 0.46f, y + H * 0.56f, W * 0.07f, H * 0.07f, 1f, 1f, 1f);

      // ── MEJILLA — rosado para expresión ─────────────────────────────────
      rect(x + W * 0.38f, y + H * 0.35f, W * 0.22f, H * 0.14f, 1f, 0.50f, 0.40f);

      // ── PICO — mandíbulas superior e inferior ────────────────────────────
      // Mandíbula superior (naranja-rojo)
      rect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, pk[0], pk[1], pk[2]);
      // Mandíbula inferior (naranja-rojo más oscuro)
      rect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, pk[0] * 0.90f, pk[1] * 0.85f, pk[2] * 0.80f);

      // Reset inclinación para otros objetos
      estadoAngulo = 0f;
   }

   /**
    * Dibuja un pájaro MUERTO con efectos visuales especiales.
    *
    * Cambios respecto a vivo:
    * - Rotación fija a -1.5 radianes (boca abajo, dramático)
    * - Colores desaturados completamente (gris)
    * - Ojo cerrado (línea horizontal + línea vertical = X)
    * - Sin aleteo (alas caídas)
    * - Parpadeo (flicker): aparece/desaparece intermitentemente
    *
    * El pájaro muerto sigue todos los pasos visuales de uno vivo,
    * pero con colores grises y ojo especial.
    *
    * @param b            Pájaro muerto
    * @param flickerTimer Tiempo restante de parpadeo (segundos)
    *                     Si > 0: efecto flicker (aparece/desaparece)
    * @param tiempo       Tiempo acumulado (no usado para muertos)
    */
   private void dibujarPajaroMuerto(Bird b, float flickerTimer, float tiempo) {
      // ── Efecto de parpadeo (flicker) ────────────────────────────────────
      // Mientras flickerTimer > 0, el pájaro parpadea (aparece/desaparece)
      // Alterna cada FLICKER_INTERVALO segundos
      if (flickerTimer > 0f) {
         int fase = (int) (flickerTimer / Constants.FLICKER_INTERVALO);
         if (fase % 2 == 0)
            return; // Frame invisible — no dibujar nada
      }

      float x = b.birdX, y = b.birdY;
      float W = Constants.BIRD_ANCHO, H = Constants.BIRD_ALTO;

      // ── Rotación fija a -1.5 radianes (boca abajo) ───────────────────
      estadoAngulo = -1.5f; // Inclina el pájaro dramáticamente
      estadoPivotX = x;
      estadoPivotY = y;

      // Color gris desaturado para todos los elementos
      float gr = 0.40f; // Valor gris neutro [0..1]

      // ── COLA (gris oscuro) ──────────────────────────────────────────
      rect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
      rect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
      rect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);

      // ── ALA TRASERA (gris) ──────────────────────────────────────────
      rect(x - W * 0.15f, y - H * 0.30f, W * 0.80f, H * 0.30f, gr * 0.9f, gr * 0.9f, gr * 0.9f);

      // ── CUERPO (gris) ──────────────────────────────────────────────
      rect(x, y, W, H, gr, gr, gr);

      // ── PANZA (gris claro) ──────────────────────────────────────────
      rect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, gr + 0.15f, gr + 0.15f, gr + 0.15f);

      // ── ALA DELANTERA CAÍDA ─────────────────────────────────────────
      // Diferente a la viva: no anima, simplemente cae
      rect(x - W * 0.05f, y - H * 0.40f, W * 0.70f, H * 0.20f, gr * 0.9f, gr * 0.9f, gr * 0.9f);

      // ── CABEZA (gris) ──────────────────────────────────────────────
      rect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, gr, gr, gr);

      // ── OJO CERRADO (X: línea horizontal + línea vertical) ────────
      // Línea horizontal (párpado cerrado)
      rect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.07f, 0.1f, 0.1f, 0.1f);
      // Línea vertical (formar X)
      rect(x + W * 0.52f, y + H * 0.45f, W * 0.07f, H * 0.22f, 0.1f, 0.1f, 0.1f);

      // ── PICO (color pájaro muerto = opaco marrón) ──────────────────
      rect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, 0.55f, 0.25f, 0.05f);
      rect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, 0.50f, 0.22f, 0.04f);

      // Reset rotación
      estadoAngulo = 0f;
   }

   // ════════════════════════════════════════════════════════════════════════
   // HUD SUPERIOR
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Dibuja el HUD (Heads-Up Display) superior del juego.
    *
    * Elementos mostrados:
    * 1. Franja oscura semitransparente de fondo
    * 2. Línea separadora inferior
    * 3. Jugador 1 (izquierda):
    * - Cuadrado de color del pájaro (amarillo para P1)
    * - Puntaje actual en dígitos 7-segmentos
    * 4. Nivel centrado:
    * - Número del nivel actual (1, 2, 3, ...)
    * - Barra de progreso de velocidad (verde → amarillo → rojo)
    * - Líneas decorativas a los lados
    * 5. Jugador 2 (derecha):
    * - Puntaje actual
    * - Cuadrado de color (azul para P2)
    *
    * @param world  Mundo (para leer puntajes y nivel)
    * @param tiempo Tiempo acumulado (no usado en HUD)
    */
   private void dibujarHUD(GameWorld world, float tiempo) {
      float hy = Constants.HUD_TOP_Y; // Centro Y de la franja
      float hh = Constants.HUD_TOP_H; // Alto de la franja

      // ── Franja de fondo (oscura) ────────────────────────────────────────
      rect(0f, hy, 2f, hh, 0.10f, 0.13f, 0.18f);

      // ── Línea separadora inferior ───────────────────────────────────────
      rect(0f, Constants.HUD_SEP_Y, 2f, 0.012f, 0.25f, 0.32f, 0.40f);

      // ── JUGADOR 1 (izquierda) ───────────────────────────────────────────
      // Cuadrado de color del pájaro (para identificación rápida)
      float[] c1 = world.bird1.alive ? world.bird1.colorCuerpo : new float[] { 0.3f, 0.3f, 0.3f };
      rect(-0.82f, hy, 0.075f, 0.075f, c1[0], c1[1], c1[2]);

      // Puntaje de P1 en dígitos 7-segmentos (blanco)
      dibujarNumero(world.bird1.puntaje, -0.60f, hy,
            Constants.DIGIT_W, Constants.DIGIT_H, 1f, 1f, 1f);

      // ── NIVEL CENTRADO ──────────────────────────────────────────────────
      int nivel = world.calcularNivel();

      // Líneas decorativas (4 líneas pequeñas alrededor del nivel)
      rect(-0.18f, hy + 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      rect(0.18f, hy + 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      rect(-0.18f, hy - 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);
      rect(0.18f, hy - 0.055f, 0.14f, 0.010f, 1.0f, 0.80f, 0.10f);

      // Número del nivel (tamaño reducido, color amarillo-dorado)
      dibujarNumero(nivel, 0f, hy + 0.010f,
            Constants.DIGIT_W * 0.85f, Constants.DIGIT_H * 0.80f,
            1.0f, 0.80f, 0.10f);

      // ── BARRA DE PROGRESO DE VELOCIDAD ──────────────────────────────────
      // Representa el progreso hacia la velocidad máxima
      // Verde (lenta) → Amarillo (media) → Rojo (rápida)
      float velPct = (world.velocidadActual() - Constants.VELOCIDAD_BASE)
            / (Constants.VELOCIDAD_MAX - Constants.VELOCIDAD_BASE);
      velPct = Math.max(0f, Math.min(1f, velPct)); // Clamp [0, 1]

      // Fondo de la barra (oscuro)
      rect(0f, hy - 0.065f, 0.28f, 0.014f, 0.18f, 0.22f, 0.28f);

      // Barra llena con color dinámico (si velocidad > 0)
      if (velPct > 0f) {
         float bw = 0.28f * velPct; // Ancho de la barra rellena
         float bx = -0.14f + bw * 0.5f; // Centro X

         // Color: verde → amarillo → rojo según progreso
         float r = Math.min(1f, velPct * 2f); // 0→1: nada→rojo completo
         float g = Math.min(1f, (1f - velPct) * 2f); // 1→0: verde completo→nada
         rect(bx, hy - 0.065f, bw, 0.012f, r, g, 0.05f);
      }

      // ── JUGADOR 2 (derecha) ─────────────────────────────────────────────
      // Puntaje de P2 (blanco)
      dibujarNumero(world.bird2.puntaje, 0.60f, hy,
            Constants.DIGIT_W, Constants.DIGIT_H, 1f, 1f, 1f);

      // Cuadrado de color (azul para P2, gris si está muerto)
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
    * Pantalla de INICIO (antes de empezar el juego).
    *
    * ELEMENTOS VISUALES:
    *
    * 1. MARCO EXTERIOR (3 rectángulos concéntricos):
    * - Marco más oscuro (borde externo)
    * - Marco principal (color medio)
    * - Separador horizontal brillante (línea divisoria)
    *
    * 2. TÍTULO CENTRAL "VS":
    * Letras grandes de "VS" (Versus) en blanco puro
    * Centro de la zona superior del marco
    *
    * 3. JUGADOR 1 (IZQUIERDA):
    * - Cuadrado de color del Pájaro 1 (amarillo = 0.98, 0.78, 0.10)
    * - Botón de acción debajo: "SPACE" en gris
    * Identifica a P1 con su color característico
    *
    * 4. JUGADOR 2 (DERECHA):
    * - Cuadrado de color del Pájaro 2 (azul = 0.15, 0.65, 0.95)
    * - Botón de acción debajo: "W" en gris
    * Identifica a P2 con su color característico
    *
    * 5. BOTÓN "START" (PULSANTE):
    * Texto grande que parpadea suavemente (pulso sinusoidal)
    * Color amarillo-dorado (1.0, 0.8, 0.1)
    * Invita al jugador a presionar una tecla para comenzar
    *
    * ANIMACIONES:
    * - El botón "START" parpadea con Math.sin(tiempo * 3.0)
    * Efecto pulsante: escala + intensidad varían continuamente
    *
    * @param world  Mundo (para acceder a colores de pájaros)
    * @param tiempo Tiempo acumulado (para animar el botón START)
    */
   private void dibujarPantallaInicio(GameWorld world, float tiempo) {
      // ── MARCO EXTERIOR ───────────────────────────────────────────────────
      // Borde más oscuro (sombra)
      rect(0f, 0.18f, 1.28f, 0.62f, 0.05f, 0.08f, 0.12f);

      // Borde principal (color medio)
      rect(0f, 0.18f, 1.24f, 0.58f, 0.10f, 0.14f, 0.20f);

      // Línea separadora horizontal brillante (divide superior de inferior)
      rect(0f, 0.46f, 1.24f, 0.014f, 0.28f, 0.68f, 0.95f);

      // ── TÍTULO "VS" (blanco) ───────────────────────────────────────────
      dibujarTexto("VS", 0f, 0.28f, 0.08f, 0.12f, 1f, 1f, 1f);

      // ── JUGADOR 1 (IZQUIERDA) ──────────────────────────────────────────
      // Cuadrado de color (amarillo = P1)
      float[] c1 = world.bird1.colorCuerpo;
      rect(-0.35f, 0.28f, 0.13f, 0.13f, c1[0], c1[1], c1[2]);

      // Tecla de acción (SPACE)
      dibujarTexto("SPACE", -0.35f, 0.12f, 0.035f, 0.06f, 0.8f, 0.8f, 0.8f);

      // ── JUGADOR 2 (DERECHA) ────────────────────────────────────────────
      // Cuadrado de color (azul = P2)
      float[] c2 = world.bird2.colorCuerpo;
      rect(0.35f, 0.28f, 0.13f, 0.13f, c2[0], c2[1], c2[2]);

      // Tecla de acción (W)
      dibujarTexto("W", 0.35f, 0.12f, 0.04f, 0.07f, 0.8f, 0.8f, 0.8f);

      // ── BOTÓN "START" PULSANTE ─────────────────────────────────────────
      // Anima tamaño + intensidad: oscila entre 0.8x y 1.0x
      float pulso = 0.8f + 0.2f * Math.abs((float) Math.sin(tiempo * 3.0f));
      dibujarTexto("START", 0f, -0.15f, 0.06f * pulso, 0.10f * pulso,
            0.28f, 0.68f, 0.95f); // Amarillo-dorado
   }

   // ════════════════════════════════════════════════════════════════════════
   // PANTALLA DE GAME OVER
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Pantalla de GAME OVER (final del juego).
    *
    * ESTRUCTURA:
    *
    * 1. OSCURECIMIENTO DE FONDO:
    * Rectángulo 2x2 de color oscuro que cubre toda la pantalla
    * Efecto: "velo oscuro" que oscurece el juego de fondo
    *
    * 2. PANEL CENTRAL:
    * Tres rectángulos concéntricos que forman un marco elegante
    * - Marco externo oscuro (sombra)
    * - Marco principal (color gris)
    * - Separador horizontal (línea roja brillante)
    * El separador divide la zona superior (resultado) de la inferior (puntajes)
    *
    * 3. TÍTULO "GAME OVER":
    * Texto rojo grande en la zona superior del panel
    * Indica claramente el final del juego
    *
    * 4. RESULTADO (con animación):
    * EMPATE: texto gris si ambos tienen igual puntaje
    * O
    * GANADOR: el jugador con mayor puntaje
    * - Texto en el color del ganador (amarillo P1 o azul P2)
    * - CORONA: 4 rectángulos pequeños forma una corona visual
    * (base horizontal + 3 picos)
    * - Posicionada encima del nombre del ganador
    *
    * 5. CUADROS DE PUNTAJE (final):
    * Lado izquierdo (P1):
    * - Cuadrado amarillo (0.98, 0.78, 0.10)
    * - Número del puntaje final en blanco
    * * Lado derecho (P2):
    * - Cuadrado azul (0.15, 0.65, 0.95)
    * - Número del puntaje final en blanco
    *
    * 6. SEPARADOR CENTRAL:
    * Línea vertical delgada entre los dos puntajes
    * Color gris-azul (0.32, 0.42, 0.55)
    *
    * 7. BOTÓN "RESET" PULSANTE:
    * Texto grande que parpadea invitando al jugador a reiniciar
    * Color amarillo-dorado (0.28, 0.68, 0.95)
    * Animación pulsante: escala entre 0.88x y 1.0x
    *
    * @param world  Mundo (para leer puntajes finales y comparar ganador)
    * @param tiempo Tiempo acumulado (para animar botón RESET)
    */
   private void dibujarPantallaGameOver(GameWorld world, float tiempo) {
      // ── OSCURECIMIENTO DE PANTALLA ───────────────────────────────────────
      // Rectángulo que cubre toda la pantalla con color oscuro
      rect(0f, 0f, 2f, 2f, 0.04f, 0.05f, 0.08f);

      // ── PANEL CENTRAL ────────────────────────────────────────────────────
      // Marco exterior (sombra oscura)
      rect(0f, 0.12f, 1.38f, 0.82f, 0.08f, 0.10f, 0.14f);

      // Marco principal (gris claro)
      rect(0f, 0.12f, 1.34f, 0.78f, 0.12f, 0.16f, 0.22f);

      // Separador horizontal (línea roja brillante)
      // Divide la zona de resultado (arriba) de la zona de puntajes (abajo)
      rect(0f, 0.50f, 1.34f, 0.016f, 0.88f, 0.18f, 0.18f);

      // ── TÍTULO ──────────────────────────────────────────────────────────
      // "GAME OVER" en rojo oscuro
      dibujarTexto("GAME OVER", 0f, 0.38f, 0.05f, 0.09f, 0.9f, 0.2f, 0.2f);

      // ── RESULTADO (Ganador o Empate) ────────────────────────────────────
      boolean p1gana = world.bird1.puntaje > world.bird2.puntaje;
      boolean empate = world.bird1.puntaje == world.bird2.puntaje;

      if (empate) {
         // EMPATE: mostrar "EMPATE" en gris
         dibujarTexto("EMPATE", 0f, 0.20f, 0.05f, 0.08f, 0.8f, 0.8f, 0.8f);
      } else {
         // GANADOR: mostrar "P1 GANA" o "P2 GANA" en color del ganador
         String ganador = p1gana ? "P1 GANA" : "P2 GANA";
         float[] gc = p1gana ? world.bird1.colorCuerpo : world.bird2.colorCuerpo;
         dibujarTexto(ganador, 0f, 0.20f, 0.05f, 0.08f, gc[0], gc[1], gc[2]);

         // ── CORONA (símbolo de victoria) ─────────────────────────────────
         // 4 rectángulos pequeños: 1 base + 3 picos = forma de corona
         float gx = p1gana ? -0.24f : 0.24f; // Posición según el ganador

         // Base horizontal de la corona
         rect(gx, 0.11f, 0.10f, 0.02f, 1.0f, 0.85f, 0.10f);

         // Pico izquierdo
         rect(gx - 0.04f, 0.14f, 0.02f, 0.04f, 1.0f, 0.85f, 0.10f);

         // Pico central (el más alto)
         rect(gx, 0.15f, 0.02f, 0.05f, 1.0f, 0.85f, 0.10f);

         // Pico derecho
         rect(gx + 0.04f, 0.14f, 0.02f, 0.04f, 1.0f, 0.85f, 0.10f);
      }

      // ── PUNTAJES FINALES (P1 Izquierda, P2 Derecha) ──────────────────────
      // JUGADOR 1 (izquierda)
      float[] c1 = world.bird1.colorCuerpo;
      rect(-0.24f, 0.00f, 0.08f, 0.08f, c1[0], c1[1], c1[2]); // Cuadrado color P1
      dibujarNumero(world.bird1.puntaje, -0.12f, 0.00f, 0.04f, 0.07f,
            1f, 1f, 1f); // Número puntaje en blanco

      // JUGADOR 2 (derecha)
      float[] c2 = world.bird2.colorCuerpo;
      rect(0.24f, 0.00f, 0.08f, 0.08f, c2[0], c2[1], c2[2]); // Cuadrado color P2
      dibujarNumero(world.bird2.puntaje, 0.36f, 0.00f, 0.04f, 0.07f,
            1f, 1f, 1f); // Número puntaje en blanco

      // ── SEPARADOR CENTRAL ───────────────────────────────────────────────
      // Línea vertical delgada entre los dos puntajes
      rect(0f, 0.00f, 0.006f, 0.15f, 0.32f, 0.42f, 0.55f);

      // ── BOTÓN "RESET" PULSANTE ─────────────────────────────────────────
      // Anima tamaño: oscila entre 0.88x y 1.0x para efecto pulsante
      float pulso = 0.88f + 0.12f * Math.abs((float) Math.sin(tiempo * 4.0f));
      dibujarTexto("RESET", 0f, -0.18f, 0.045f * pulso, 0.08f * pulso,
            0.28f, 0.68f, 0.95f); // Amarillo-dorado
   }

   // ════════════════════════════════════════════════════════════════════════
   // PRIMITIVA BASE: rect() — Dibuja un rectángulo coloreado
   // ════════════════════════════════════════════════════════════════════════

   /**
    * * Dibuja un rectángulo en pantalla.
    *
    * ENTRADA (parámetros):
    * - x, y: posición central en coordenadas NDC [-1, 1]
    * Ejemplo: x=-0.5, y=0.5 = mitad izquierda, arriba
    * - w, h: ancho y alto (también en NDC)
    * - r, g, b: color RGB [0..1]
    * Ejemplo: 1,0,0 = rojo puro, 0,1,0 = verde puro, 1,1,1 = blanco
    *
    * INTERNAMENTE:
    * 1. Asignar uniforms al shader:
    * - uOffset = (x, y) — posición
    * - uScale = (w, h) — escala
    * - uColor = (r, g, b) — color
    * - uAngle = estadoAngulo — rotación (si está seteado)
    * - uPivot = (estadoPivotX, estadoPivotY) — centro de rotación
    * 2. Dibujar el quad base (6 vértices = 2 triángulos)
    *
    * REUTILIZACIÓN:
    * El quad base (-0.5,-0.5) a (0.5,0.5) se dibuja cientos de veces
    * cada frame con diferentes uniforms. Esto es muy eficiente porque
    * solo hay 1 VAO/VBO en GPU.
    *
    * EJEMPLOS:
    * - rect(0, 0, 0.1, 0.1, 1, 0, 0) → cuadrado rojo pequeño en centro
    * - rect(-0.5, 0.5, 1, 0.2, 0, 1, 0) → rectángulo verde ancho a la izquierda
    * - rect(0.3, -0.3, 0.05, 0.08, 0.2, 0.5, 0.9) → rectángulo azul pequeño
    */
   private void rect(float x, float y, float w, float h, float r, float g, float b) {
      // Asignar uniforms del shader
      GL20.glUniform2f(uOffsetLoc, x, y); // Posición
      GL20.glUniform2f(uScaleLoc, w, h); // Escala (tamaño)
      GL20.glUniform3f(uColorLoc, r, g, b); // Color
      GL20.glUniform1f(uAngleLoc, estadoAngulo); // Rotación actual
      GL20.glUniform2f(uPivotLoc, estadoPivotX, estadoPivotY); // Pivot de rotación

      // Dibujar el quad (6 vértices = 2 triángulos)
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