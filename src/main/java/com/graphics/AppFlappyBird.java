package com.graphics;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * AppFlappyBird — Modo 2 Jugadores
 * ══════════════════════════════════════════════════════════════════════
 * Dos pájaros comparten la misma pantalla y las mismas tuberías.
 *
 * Jugador 1 → SPACE │ color amarillo/dorado (pato original)
 * Jugador 2 → W o ↑ │ color azul/cian (pato azul)
 *
 * Novedades:
 * - Rotación basada en Vertex Shader (físicas de vuelo visuales).
 * - Animación de aleteo sincronizada con el input de salto.
 * - Barra HUD superior con display digital de 7 segmentos.
 * ══════════════════════════════════════════════════════════════════════
 */
public class AppFlappyBird {

    // ── Ventana ────────────────────────────────────────────────────────
    private static final int ANCHO = 900;
    private static final int ALTO = 700;

    // ── Pájaros ────────────────────────────────────────────────────────
    private static final float BIRD1_X = -0.55f;
    private static final float BIRD2_X = -0.30f;
    private static final float BIRD_ANCHO = 0.10f;
    private static final float BIRD_ALTO = 0.10f;

    // ── Física ─────────────────────────────────────────────────────────
    private static final float GRAVEDAD = -1.9f;
    private static final float IMPULSO_SALTO = 0.85f;
    private static final float VELOCIDAD_MAX_CAIDA = -1.8f;

    // ── Tuberías y Niveles ─────────────────────────────────────────────
    private static final float TUBERIA_ANCHO = 0.18f;
    private static final float GAP_ALTO = 0.48f;
    private static final float VELOCIDAD_BASE = 0.62f;
    private static final float TIEMPO_BASE = 1.5f;
    private static final float GAP_MIN_CENTRO = -0.45f;
    private static final float GAP_MAX_CENTRO = 0.45f;
    private static final int PUNTOS_POR_NIVEL = 5;
    private static final float INCREMENTO_VELOCIDAD = 0.08f;
    private static final float DECREMENTO_TIEMPO = 0.1f;
    private static final float VELOCIDAD_MAX = 1.40f;
    private static final float TIEMPO_MIN = 0.8f;

    // ── Recursos OpenGL ────────────────────────────────────────────────
    private long window;
    private int programa;
    private int vao, vbo;
    // Uniforms extendidos para permitir rotación en el shader
    private int uOffsetLocation, uScaleLocation, uColorLocation;
    private int uAngleLocation, uPivotLocation;

    // ── Estado global del Render (Máquina de estados para dibujo) ──────
    private float estadoAngulo = 0f;
    private float estadoPivotX = 0f;
    private float estadoPivotY = 0f;

    private float timerSpawn;
    private boolean started;
    private boolean gameOver;
    private int maxPuntajeGlobal;

    private final List<Tuberia> tuberias = new ArrayList<>();
    private final Random random = new Random();

    // ── Jugadores y Teclado ────────────────────────────────────────────
    private Bird bird1;
    private Bird bird2;
    private boolean prevSpace, prevW, prevUp, prevR;

    // ══════════════════════════════════════════════════════════════════
    // Clase interna Bird
    // ══════════════════════════════════════════════════════════════════
    private static class Bird {
        float birdX; // ¡QUITAMOS EL 'final' AQUÍ!
        final float startX; // NUEVO: Guardar posición original para el reset
        float birdY;
        float velY;
        boolean alive;
        int puntaje;
        float tiempoAleteo;

        final float[] colorCuerpo;
        final float[] colorPanza;
        final float[] colorAla;
        final float[] colorPico;
        final String nombre;

        Bird(float x, float[] cuerpo, float[] panza, float[] ala, float[] pico, String nombre) {
            this.startX = x;
            this.birdX = x;
            this.colorCuerpo = cuerpo;
            this.colorPanza = panza;
            this.colorAla = ala;
            this.colorPico = pico;
            this.nombre = nombre;
        }

        void reset() {
            birdX = startX; // RESTAURAR X
            birdY = 0.0f;
            velY = 0.0f;
            alive = true;
            puntaje = 0;
            tiempoAleteo = 0f;
        }
    }

    private static class Tuberia {
        float x;
        float gapCentroY;
        boolean puntuada1;
        boolean puntuada2;

        Tuberia(float x, float gap) {
            this.x = x;
            this.gapCentroY = gap;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Flujo principal
    // ══════════════════════════════════════════════════════════════════
    public void run() {
        init();
        resetGame();
        loop();
        cleanup();
    }

    private void init() {
        if (!GLFW.glfwInit())
            throw new IllegalStateException("No se pudo iniciar GLFW");

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(ANCHO, ALTO, "", 0, 0);
        if (window == 0)
            throw new RuntimeException("No se pudo crear la ventana");

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);
        GLFW.glfwShowWindow(window);
        GL.createCapabilities();

        crearShaders();
        crearQuadBase();
    }

    // ── Shaders (NUEVO: Lógica de rotación en Vertex Shader) ───────────
    private void crearShaders() {
        String vertexSrc = """
                #version 330 core
                layout (location = 0) in vec3 aPos;

                uniform vec2 uOffset;
                uniform vec2 uScale;
                uniform float uAngle; // Ángulo de rotación en radianes
                uniform vec2 uPivot;  // Centro de rotación (coordenada de la pantalla)

                void main() {
                    // 1. Escalar el rectángulo
                    vec2 pos = aPos.xy * uScale;
                    // 2. Moverlo a su posición en el mundo
                    pos += uOffset;

                    // 3. Lógica de Rotación 2D alrededor de un pivote
                    // Trasladar al origen relativo al pivote
                    pos -= uPivot;

                    // Aplicar matriz de rotación
                    float c = cos(uAngle);
                    float s = sin(uAngle);
                    vec2 rotPos = vec2(
                        pos.x * c - pos.y * s,
                        pos.x * s + pos.y * c
                    );

                    // Devolver a la posición original
                    pos = rotPos + uPivot;

                    gl_Position = vec4(pos, aPos.z, 1.0);
                }
                """;

        String fragmentSrc = """
                #version 330 core
                uniform vec3 uColor;
                out vec4 fragColor;
                void main() {
                    fragColor = vec4(uColor, 1.0);
                }
                """;

        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, vertexSrc);
        GL20.glCompileShader(vs);
        comprobarShader(vs, "Vertex");

        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, fragmentSrc);
        GL20.glCompileShader(fs);
        comprobarShader(fs, "Fragment");

        programa = GL20.glCreateProgram();
        GL20.glAttachShader(programa, vs);
        GL20.glAttachShader(programa, fs);
        GL20.glLinkProgram(programa);
        if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException("Link: " + GL20.glGetProgramInfoLog(programa));

        uOffsetLocation = GL20.glGetUniformLocation(programa, "uOffset");
        uScaleLocation = GL20.glGetUniformLocation(programa, "uScale");
        uColorLocation = GL20.glGetUniformLocation(programa, "uColor");
        uAngleLocation = GL20.glGetUniformLocation(programa, "uAngle");
        uPivotLocation = GL20.glGetUniformLocation(programa, "uPivot");

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private void comprobarShader(int shader, String tipo) {
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException(tipo + ": " + GL20.glGetShaderInfoLog(shader));
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

    private void resetGame() {
        bird1 = new Bird(BIRD1_X, new float[] { 0.98f, 0.78f, 0.10f }, new float[] { 0.99f, 0.94f, 0.55f },
                new float[] { 0.90f, 0.55f, 0.05f }, new float[] { 1.00f, 0.42f, 0.10f }, "P1");
        bird1.reset();

        bird2 = new Bird(BIRD2_X, new float[] { 0.15f, 0.65f, 0.95f }, new float[] { 0.70f, 0.90f, 1.00f },
                new float[] { 0.05f, 0.40f, 0.80f }, new float[] { 1.00f, 0.42f, 0.10f }, "P2");
        bird2.reset();

        tuberias.clear();
        timerSpawn = 0f;
        started = false;
        gameOver = false;
        actualizarTitulo();
    }

    private void procesarInput() {
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS)
            GLFW.glfwSetWindowShouldClose(window, true);

        boolean spaceAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (spaceAhora && !prevSpace) {
            if (gameOver)
                resetGame();
            saltarBird(bird1);
        }
        prevSpace = spaceAhora;

        boolean wAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean upAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        boolean p2Jump = (wAhora && !prevW) || (upAhora && !prevUp);
        if (p2Jump) {
            if (gameOver)
                resetGame();
            saltarBird(bird2);
        }
        prevW = wAhora;
        prevUp = upAhora;

        boolean rAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (rAhora && !prevR && gameOver)
            resetGame();
        prevR = rAhora;
    }

    private void saltarBird(Bird b) {
        if (!b.alive)
            return;
        started = true;
        b.velY = IMPULSO_SALTO;
        // Iniciar el timer de aleteo (0.25 segundos de animación)
        b.tiempoAleteo = 0.25f;
    }

    private int calcularNivel() {
        return 1 + (maxPuntajeGlobal / PUNTOS_POR_NIVEL);
    }

    private float calcularVelocidadTuberias(int nivel) {
        return Math.min(VELOCIDAD_BASE + (nivel - 1) * INCREMENTO_VELOCIDAD, VELOCIDAD_MAX);
    }

    private float calcularTiempoSpawn(int nivel) {
        return Math.max(TIEMPO_BASE - (nivel - 1) * DECREMENTO_TIEMPO, TIEMPO_MIN);
    }

    private void actualizar(float dt) {
        if (!started) return;

        int nivelActual = calcularNivel();
        float velocidadActual = calcularVelocidadTuberias(nivelActual);
        float tiempoSpawnActual = calcularTiempoSpawn(nivelActual);

        // Si es Game Over, el escenario se detiene, pero aplicamos la velocidad
        // 0 a los pájaros muertos para que caigan recto.
        float velocidadEntorno = gameOver ? 0f : velocidadActual;

        // Pasamos la velocidad a actualizarBird
        actualizarBird(bird1, dt, velocidadEntorno);
        actualizarBird(bird2, dt, velocidadEntorno);

        if (!gameOver) {
            // Spawn de tuberías
            timerSpawn += dt;
            if (timerSpawn >= tiempoSpawnActual) {
                timerSpawn = 0f;
                spawnTuberia();
            }

            // Mover tuberías y puntuar
            Iterator<Tuberia> it = tuberias.iterator();
            while (it.hasNext()) {
                Tuberia t = it.next();
                t.x -= velocidadActual * dt;

                puntarSiCorresponde(t, bird1, true);
                puntarSiCorresponde(t, bird2, false);

                if (t.x + TUBERIA_ANCHO * 0.5f < -1.3f) it.remove();
            }

            // Revisar condición de Game Over
            if (!bird1.alive && !bird2.alive) {
                gameOver = true;
                actualizarTitulo();
            }
        }
    }

    private void actualizarBird(Bird b, float dt, float velocidadEntorno) {
        // ── SI ESTÁ MUERTO: Cae y es arrastrado ──
        if (!b.alive) {
            b.birdX -= velocidadEntorno * dt; // Se lo lleva el escenario
            b.velY += GRAVEDAD * dt * 1.5f;   // Cae más rápido (peso muerto)
            b.birdY += b.velY * dt;
            return; // Terminamos aquí para no calcular colisiones otra vez
        }

        // ── SI ESTÁ VIVO: Físicas normales ──
        b.velY += GRAVEDAD * dt;
        if (b.velY < VELOCIDAD_MAX_CAIDA) b.velY = VELOCIDAD_MAX_CAIDA;
        b.birdY += b.velY * dt;
        
        // Descontar timer del aleteo
        if (b.tiempoAleteo > 0) {
            b.tiempoAleteo -= dt;
        }

        float top = b.birdY + BIRD_ALTO * 0.5f;
        float bottom = b.birdY - BIRD_ALTO * 0.5f;
        
        // ¡LÍMITE DEL TECHO CORREGIDO! Usa 0.75f para no pisar la barra superior
        if (top >= 0.75f || bottom <= -1.0f) {
            b.alive = false;
            b.velY = 0; // Detener salto al chocar
            actualizarTitulo();
            return;
        }

        for (Tuberia t : tuberias) {
            if (colisionaConTuberia(b, t)) {
                b.alive = false;
                b.velY = 0; // Detener salto
                actualizarTitulo();
                return;
            }
        }
    }

    private void puntarSiCorresponde(Tuberia t, Bird b, boolean esP1) {
        if (!b.alive)
            return;
        boolean yaContada = esP1 ? t.puntuada1 : t.puntuada2;
        if (!yaContada && t.x + TUBERIA_ANCHO * 0.5f < b.birdX) {
            if (esP1)
                t.puntuada1 = true;
            else
                t.puntuada2 = true;
            b.puntaje++;
            int puntajeActual = Math.max(bird1.puntaje, bird2.puntaje);
            if (puntajeActual > maxPuntajeGlobal)
                maxPuntajeGlobal = puntajeActual;
            actualizarTitulo();
        }
    }

    private void spawnTuberia() {
        float gap = GAP_MIN_CENTRO + random.nextFloat() * (GAP_MAX_CENTRO - GAP_MIN_CENTRO);
        tuberias.add(new Tuberia(1.2f, gap));
    }

    private boolean colisionaConTuberia(Bird b, Tuberia t) {
        float bL = b.birdX - BIRD_ANCHO * 0.5f, bR = b.birdX + BIRD_ANCHO * 0.5f;
        float bB = b.birdY - BIRD_ALTO * 0.5f, bT = b.birdY + BIRD_ALTO * 0.5f;
        float pL = t.x - TUBERIA_ANCHO * 0.5f, pR = t.x + TUBERIA_ANCHO * 0.5f;

        if (bR <= pL || bL >= pR)
            return false;
        float gT = t.gapCentroY + GAP_ALTO * 0.5f;
        float gB = t.gapCentroY - GAP_ALTO * 0.5f;
        return bT > gT || bB < gB;
    }

    private void render() {
        GL11.glClearColor(0.52f, 0.80f, 0.92f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(programa);
        GL30.glBindVertexArray(vao);

        for (Tuberia t : tuberias) {
            float gTop = t.gapCentroY + GAP_ALTO * 0.5f;
            float gBot = t.gapCentroY - GAP_ALTO * 0.5f;

            float altoSup = 1.0f - gTop;
            if (altoSup > 0f)
                dibujarRect(t.x, gTop + altoSup * 0.5f, TUBERIA_ANCHO, altoSup, 0.18f, 0.70f, 0.25f);

            float altoInf = gBot + 1.0f;
            if (altoInf > 0f)
                dibujarRect(t.x, -1.0f + altoInf * 0.5f, TUBERIA_ANCHO, altoInf, 0.18f, 0.70f, 0.25f);
        }

        if (!bird1.alive)
            dibujarPajaroMuerto(bird1);
        if (!bird2.alive)
            dibujarPajaroMuerto(bird2);
        if (bird1.alive)
            dibujarPajaro(bird1);
        if (bird2.alive)
            dibujarPajaro(bird2);

        dibujarInterfazSuperior();

        if (gameOver) {
            dibujarRect(0.0f, 0.0f, 2.0f, 0.22f, 0.10f, 0.12f, 0.15f);
        }
    }

    /**
     * Dibuja el pájaro con rotación y aleteo sincronizado.
     */
    private void dibujarPajaro(Bird b) {
        float x = b.birdX;
        float y = b.birdY;
        float W = BIRD_ANCHO;
        float H = BIRD_ALTO;

        float[] c = b.colorCuerpo;
        float[] pz = b.colorPanza;
        float[] al = b.colorAla;
        float[] pk = b.colorPico;

        // ── FISICAS DE ROTACIÓN ──
        // El ángulo depende directamente de la velocidad (velY)
        float anguloDestino = b.velY * 0.45f;
        // Limitamos para evitar que gire demasiado y quede de cabeza
        estadoAngulo = Math.max(-1.3f, Math.min(0.6f, anguloDestino));
        estadoPivotX = x; // Centro del pájaro en X
        estadoPivotY = y; // Centro del pájaro en Y

        // ── SINCRONIZACIÓN DE ALETEO ──
        float aleteo;
        if (b.tiempoAleteo > 0) {
            // Animación activa (0.25s). Se calcula la onda basada en el progreso del salto.
            float progreso = 0.25f - b.tiempoAleteo;
            aleteo = (float) Math.sin(progreso * Math.PI * 8.0f) * H * 0.35f;
        } else {
            // Planeo: si está cayendo y no presiona salto, las alas se quedan fijas arriba
            aleteo = H * 0.20f;
        }

        // 1. Cola
        dibujarRect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, al[0], al[1], al[2]);
        dibujarRect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, al[0] * 0.94f, al[1] * 0.88f, al[2]);
        dibujarRect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, al[0] * 0.88f, al[1] * 0.77f, al[2]);

        // 2. Ala trasera
        dibujarRect(x - W * 0.15f, y - H * 0.30f + aleteo, W * 0.80f, H * 0.30f, al[0], al[1], al[2]);

        // 3. Cuerpo
        dibujarRect(x, y, W, H, c[0], c[1], c[2]);

        // 4. Panza
        dibujarRect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, pz[0], pz[1], pz[2]);

        // 5. Ala delantera
        dibujarRect(x - W * 0.10f, y + H * 0.22f + aleteo * 0.7f, W * 0.70f, H * 0.26f, al[0], al[1], al[2]);

        // 6. Cabeza
        dibujarRect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, c[0], c[1], c[2]);

        // 7. Ojo
        dibujarRect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.28f, 1f, 1f, 1f);
        dibujarRect(x + W * 0.48f, y + H * 0.50f, W * 0.15f, H * 0.18f, 0.08f, 0.08f, 0.18f);
        dibujarRect(x + W * 0.46f, y + H * 0.56f, W * 0.07f, H * 0.07f, 1f, 1f, 1f);

        // 8. Mejilla
        dibujarRect(x + W * 0.38f, y + H * 0.35f, W * 0.22f, H * 0.14f, 1f, 0.50f, 0.40f);

        // 9. Pico
        dibujarRect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, pk[0], pk[1], pk[2]);
        dibujarRect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, pk[0] * 0.90f, pk[1] * 0.85f, pk[2] * 0.80f);

        // RESTAURAR ESTADO DE ROTACIÓN a 0 para que no afecte tuberías ni interfaz
        estadoAngulo = 0f;
    }

    /**
     * Dibuja el pájaro muerto (caída en picada).
     */
    private void dibujarPajaroMuerto(Bird b) {
        float x = b.birdX;
        float y = b.birdY;
        float W = BIRD_ANCHO;
        float H = BIRD_ALTO;

        // Picada profunda dramática (casi 90 grados apuntando al suelo)
        estadoAngulo = -1.5f;
        estadoPivotX = x;
        estadoPivotY = y;

        float gr = 0.40f;

        dibujarRect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
        dibujarRect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
        dibujarRect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
        dibujarRect(x - W * 0.15f, y - H * 0.30f, W * 0.80f, H * 0.30f, gr * 0.9f, gr * 0.9f, gr * 0.9f);
        dibujarRect(x, y, W, H, gr, gr, gr);
        dibujarRect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, gr + 0.15f, gr + 0.15f, gr + 0.15f);
        dibujarRect(x - W * 0.05f, y - H * 0.40f, W * 0.70f, H * 0.20f, gr * 0.9f, gr * 0.9f, gr * 0.9f);
        dibujarRect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, gr, gr, gr);

        dibujarRect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.08f, 0.1f, 0.1f, 0.1f);
        dibujarRect(x + W * 0.52f, y + H * 0.45f, W * 0.08f, H * 0.22f, 0.1f, 0.1f, 0.1f);

        dibujarRect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, 0.55f, 0.25f, 0.05f);
        dibujarRect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, 0.50f, 0.22f, 0.04f);

        estadoAngulo = 0f;
    }

    private void dibujarNumero(int numero, float cx, float cy, float w, float h, float r, float g, float b) {
        String numStr = String.valueOf(numero);
        float espacio = w * 1.8f;
        float startX = cx - (numStr.length() - 1) * espacio / 2f;
        for (int i = 0; i < numStr.length(); i++) {
            int digito = numStr.charAt(i) - '0';
            dibujarDigito7Segmentos(digito, startX + i * espacio, cy, w, h, r, g, b);
        }
    }

    private void dibujarDigito7Segmentos(int digito, float cx, float cy, float w, float h, float r, float g, float b) {
        float t = 0.025f;
        float w2 = w / 2f;
        float h2 = h / 2f;

        int[][] segmentos = {
                { 0, 1, 2, 3, 4, 5 }, { 1, 2 }, { 0, 1, 6, 4, 3 }, { 0, 1, 6, 2, 3 }, { 5, 6, 1, 2 },
                { 0, 5, 6, 2, 3 }, { 0, 5, 6, 4, 2, 3 }, { 0, 1, 2 }, { 0, 1, 2, 3, 4, 5, 6 }, { 0, 1, 2, 3, 5, 6 }
        };

        if (digito < 0 || digito > 9)
            return;

        for (int seg : segmentos[digito]) {
            switch (seg) {
                case 0:
                    dibujarRect(cx, cy + h2, w + t, t, r, g, b);
                    break;
                case 1:
                    dibujarRect(cx + w2, cy + h2 / 2, t, h2, r, g, b);
                    break;
                case 2:
                    dibujarRect(cx + w2, cy - h2 / 2, t, h2, r, g, b);
                    break;
                case 3:
                    dibujarRect(cx, cy - h2, w + t, t, r, g, b);
                    break;
                case 4:
                    dibujarRect(cx - w2, cy - h2 / 2, t, h2, r, g, b);
                    break;
                case 5:
                    dibujarRect(cx - w2, cy + h2 / 2, t, h2, r, g, b);
                    break;
                case 6:
                    dibujarRect(cx, cy, w + t, t, r, g, b);
                    break;
            }
        }
    }

    private void dibujarInterfazSuperior() {
        dibujarRect(0.0f, 0.88f, 2.0f, 0.24f, 0.15f, 0.18f, 0.22f);
        dibujarRect(0.0f, 0.76f, 2.0f, 0.015f, 0.3f, 0.35f, 0.4f);

        float[] c1 = bird1.alive ? bird1.colorCuerpo : new float[] { 0.3f, 0.3f, 0.3f };
        dibujarRect(-0.85f, 0.88f, 0.08f, 0.08f, c1[0], c1[1], c1[2]);
        dibujarNumero(bird1.puntaje, -0.65f, 0.88f, 0.05f, 0.09f, 1f, 1f, 1f);

        dibujarRect(0.0f, 0.94f, 0.12f, 0.015f, 1.0f, 0.8f, 0.1f);
        dibujarRect(0.0f, 0.81f, 0.12f, 0.015f, 1.0f, 0.8f, 0.1f);
        dibujarNumero(calcularNivel(), 0.0f, 0.875f, 0.04f, 0.07f, 1.0f, 0.8f, 0.1f);

        dibujarNumero(bird2.puntaje, 0.65f, 0.88f, 0.05f, 0.09f, 1f, 1f, 1f);
        float[] c2 = bird2.alive ? bird2.colorCuerpo : new float[] { 0.3f, 0.3f, 0.3f };
        dibujarRect(0.85f, 0.88f, 0.08f, 0.08f, c2[0], c2[1], c2[2]);
    }

    // ── Helper de dibujo (Ahora pasa Ángulo y Pivote) ──────────────────
    private void dibujarRect(float x, float y, float ancho, float alto,
            float r, float g, float b) {
        GL20.glUniform2f(uOffsetLocation, x, y);
        GL20.glUniform2f(uScaleLocation, ancho, alto);
        GL20.glUniform3f(uColorLocation, r, g, b);

        // Variables de estado de rotación (si son 0, se dibuja normal)
        GL20.glUniform1f(uAngleLocation, estadoAngulo);
        GL20.glUniform2f(uPivotLocation, estadoPivotX, estadoPivotY);

        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    private void actualizarTitulo() {
        int nivelActual = calcularNivel();
        String p1 = bird1.nombre + ": " + bird1.puntaje + (bird1.alive ? "" : " ✗");
        String p2 = bird2.nombre + ": " + bird2.puntaje + (bird2.alive ? "" : " ✗");
        String base = "Flappy Bird 2P  |  Nivel " + nivelActual + "  |  " + p1 + "   " + p2;

        if (!started)
            GLFW.glfwSetWindowTitle(window, base + "  |  SPACE / W para empezar");
        else if (gameOver)
            GLFW.glfwSetWindowTitle(window, base + "  |  GAME OVER — SPACE/W/R para reiniciar");
        else
            GLFW.glfwSetWindowTitle(window, base);
    }

    private void loop() {
        float ultimoTiempo = (float) GLFW.glfwGetTime();
        while (!GLFW.glfwWindowShouldClose(window)) {
            float ahora = (float) GLFW.glfwGetTime();
            float dt = Math.min(ahora - ultimoTiempo, 0.033f);
            ultimoTiempo = ahora;

            procesarInput();
            actualizar(dt);
            render();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    private void cleanup() {
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vbo);
        GL20.glDeleteProgram(programa);
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    public static void main(String[] args) {
        new AppFlappyBird().run();
    }
}