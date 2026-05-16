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
 * Reglas:
 * - Cada pájaro tiene posición, velocidad, estado y puntaje propios.
 * - Las tuberías son compartidas y avanzan para ambos.
 * - Un pájaro muerto queda "congelado" en su posición final.
 * - El juego termina cuando AMBOS pájaros han chocado.
 * - La partida empieza en cuanto cualquiera de los dos salta por
 * primera vez (para sincronizar el spawning de tuberías).
 * - Al reiniciar (SPACE o W tras game-over) se resetea todo.
 *
 * Arquitectura clave:
 * - Clase interna Bird encapsula todo el estado por jugador.
 * - dibujarPajaro() acepta un Bird + tiempo + paleta de colores.
 * - El título muestra los puntajes de ambos en tiempo real.
 * ══════════════════════════════════════════════════════════════════════
 */
public class AppFlappyBird {

    // ── Ventana ────────────────────────────────────────────────────────
    private static final int ANCHO = 900;
    private static final int ALTO = 700;

    // ── Pájaros ────────────────────────────────────────────────────────
    /** Posición X horizontal fija del pájaro 1 (izquierda). */
    private static final float BIRD1_X = -0.55f;
    /** Posición X horizontal fija del pájaro 2 (un poco más a la derecha). */
    private static final float BIRD2_X = -0.30f;
    private static final float BIRD_ANCHO = 0.10f;
    private static final float BIRD_ALTO = 0.10f;

    // ── Física ─────────────────────────────────────────────────────────
    private static final float GRAVEDAD = -1.9f;
    private static final float IMPULSO_SALTO = 0.85f;
    private static final float VELOCIDAD_MAX_CAIDA = -1.8f;

    // ── Tuberías ───────────────────────────────────────────────────────
    private static final float TUBERIA_ANCHO = 0.18f;
    private static final float GAP_ALTO = 0.48f;
    private static final float VELOCIDAD_BASE = 0.62f; // velocidad inicial
    private static final float TIEMPO_BASE = 1.5f; // tiempo inicial entre tuberías
    private static final float VELOCIDAD_TUBERIAS = VELOCIDAD_BASE; // compat
    private static final float TIEMPO_ENTRE_TUBERIAS = TIEMPO_BASE; // compat
    private static final float GAP_MIN_CENTRO = -0.45f;
    private static final float GAP_MAX_CENTRO = 0.45f;

    // ── Sistema de Niveles (2.3 - Incremento Progresivo) ───────────────
    /** Cada 5 puntos acumulados = 1 nivel más */
    private static final int PUNTOS_POR_NIVEL = 5;
    /** Incremento de velocidad por nivel: +0.08 por nivel */
    private static final float INCREMENTO_VELOCIDAD = 0.08f;
    /** Decremento de tiempo entre tubos por nivel: -0.1s por nivel */
    private static final float DECREMENTO_TIEMPO = 0.1f;
    /** Velocidad máxima: no puede exceder para mantener jugabilidad */
    private static final float VELOCIDAD_MAX = 1.40f;
    /** Tiempo mínimo entre tubos: piso inferior */
    private static final float TIEMPO_MIN = 0.8f;

    // ── Recursos OpenGL ────────────────────────────────────────────────
    private long window;
    private int programa;
    private int vao, vbo;
    private int uOffsetLocation, uScaleLocation, uColorLocation;

    // ── Estado global ──────────────────────────────────────────────────
    private float timerSpawn;
    private boolean started; // true en cuanto cualquier pájaro salta
    private boolean gameOver; // true cuando AMBOS pájaros han muerto
    private int maxPuntajeGlobal; // máximo puntaje alcanzado (para calcular nivel)

    private final List<Tuberia> tuberias = new ArrayList<>();
    private final Random random = new Random();

    // ── Jugadores ──────────────────────────────────────────────────────
    private Bird bird1;
    private Bird bird2;

    // ── Teclado (detección de flanco) ──────────────────────────────────
    private boolean prevSpace, prevW, prevUp, prevR;

    // ══════════════════════════════════════════════════════════════════
    // Clase interna Bird — encapsula el estado completo de un jugador
    // ══════════════════════════════════════════════════════════════════
    private static class Bird {
        final float birdX; // posición X fija en NDC
        float birdY;
        float velY;
        boolean alive;
        int puntaje;
        // Paleta de colores — tres capas: principal, panza, ala/cola
        final float[] colorCuerpo;
        final float[] colorPanza;
        final float[] colorAla;
        final float[] colorPico;
        final String nombre;

        Bird(float x, float[] cuerpo, float[] panza, float[] ala, float[] pico, String nombre) {
            this.birdX = x;
            this.colorCuerpo = cuerpo;
            this.colorPanza = panza;
            this.colorAla = ala;
            this.colorPico = pico;
            this.nombre = nombre;
        }

        void reset() {
            birdY = 0.0f;
            velY = 0.0f;
            alive = true;
            puntaje = 0;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Modelo de tubería
    // ══════════════════════════════════════════════════════════════════
    private static class Tuberia {
        float x;
        float gapCentroY;
        // Cada jugador tiene su propio flag para no sumar dos veces.
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

    // ── Inicialización GLFW / OpenGL ───────────────────────────────────
    private void init() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo iniciar GLFW");
        }

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

    // ── Shaders (igual que antes) ──────────────────────────────────────
    private void crearShaders() {
        String vertexSrc = """
                #version 330 core
                layout (location = 0) in vec3 aPos;
                uniform vec2 uOffset;
                uniform vec2 uScale;
                void main() {
                    vec2 finalPos = aPos.xy * uScale + uOffset;
                    gl_Position = vec4(finalPos, aPos.z, 1.0);
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

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
    }

    private void comprobarShader(int shader, String tipo) {
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE)
            throw new RuntimeException(tipo + ": " + GL20.glGetShaderInfoLog(shader));
    }

    // ── Quad base ─────────────────────────────────────────────────────
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

    // ══════════════════════════════════════════════════════════════════
    // Reset completo de la partida
    // ══════════════════════════════════════════════════════════════════
    private void resetGame() {
        // ── Jugador 1: pato amarillo/dorado ───────────────────────────
        bird1 = new Bird(
                BIRD1_X,
                new float[] { 0.98f, 0.78f, 0.10f }, // cuerpo: amarillo
                new float[] { 0.99f, 0.94f, 0.55f }, // panza: amarillo claro
                new float[] { 0.90f, 0.55f, 0.05f }, // ala/cola: naranja
                new float[] { 1.00f, 0.42f, 0.10f }, // pico: naranja rojo
                "P1");
        bird1.reset();

        // ── Jugador 2: pato azul/cian ─────────────────────────────────
        bird2 = new Bird(
                BIRD2_X,
                new float[] { 0.15f, 0.65f, 0.95f }, // cuerpo: azul cielo
                new float[] { 0.70f, 0.90f, 1.00f }, // panza: azul claro
                new float[] { 0.05f, 0.40f, 0.80f }, // ala/cola: azul oscuro
                new float[] { 1.00f, 0.42f, 0.10f }, // pico: naranja (igual)
                "P2");
        bird2.reset();

        tuberias.clear();
        timerSpawn = 0f;
        started = false;
        gameOver = false;

        actualizarTitulo();
    }

    // ══════════════════════════════════════════════════════════════════
    // Input — detección de flanco para ambos jugadores
    // ══════════════════════════════════════════════════════════════════
    private void procesarInput() {
        // Salir con ESC siempre
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS)
            GLFW.glfwSetWindowShouldClose(window, true);

        // ── SPACE → Jugador 1 ─────────────────────────────────────────
        boolean spaceAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (spaceAhora && !prevSpace) {
            if (gameOver) {
                resetGame();
                saltarBird(bird1);
            } else {
                saltarBird(bird1);
            }
        }
        prevSpace = spaceAhora;

        // ── W o ↑ → Jugador 2 ────────────────────────────────────────
        boolean wAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean upAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        boolean p2Jump = (wAhora && !prevW) || (upAhora && !prevUp);
        if (p2Jump) {
            if (gameOver) {
                resetGame();
                saltarBird(bird2);
            } else {
                saltarBird(bird2);
            }
        }
        prevW = wAhora;
        prevUp = upAhora;

        // ── R → reset manual en game over ─────────────────────────────
        boolean rAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (rAhora && !prevR && gameOver)
            resetGame();
        prevR = rAhora;
    }

    /** Aplica impulso al pájaro si está vivo; activa la simulación global. */
    private void saltarBird(Bird b) {
        if (!b.alive)
            return;
        started = true;
        b.velY = IMPULSO_SALTO;
    }

    // ══════════════════════════════════════════════════════════════════
    // Sistema de Niveles Dinámicos (2.3)
    // ══════════════════════════════════════════════════════════════════
    /**
     * Calcula el nivel actual basado en el puntaje máximo alcanzado.
     * Fórmula: 1 + floor(maxPuntajeGlobal / PUNTOS_POR_NIVEL)
     * 
     * Ejemplo:
     * Puntaje 0-4 → Nivel 1
     * Puntaje 5-9 → Nivel 2
     * Puntaje 10+ → Nivel 3, etc.
     */
    private int calcularNivel() {
        return 1 + (maxPuntajeGlobal / PUNTOS_POR_NIVEL);
    }

    /**
     * Calcula la velocidad de las tuberías según el nivel actual.
     * Fórmula: velocidad = min(VELOCIDAD_BASE + (nivel - 1) × INCREMENTO_VELOCIDAD,
     * VELOCIDAD_MAX)
     * 
     * Así se garantiza que no exceda VELOCIDAD_MAX para mantener el juego jugable.
     */
    private float calcularVelocidadTuberias(int nivel) {
        float velocidad = VELOCIDAD_BASE + (nivel - 1) * INCREMENTO_VELOCIDAD;
        return Math.min(velocidad, VELOCIDAD_MAX);
    }

    /**
     * Calcula el tiempo entre spawns de tubería según el nivel actual.
     * Fórmula: tiempo = max(TIEMPO_BASE - (nivel - 1) × DECREMENTO_TIEMPO,
     * TIEMPO_MIN)
     * 
     * A mayor nivel, menor tiempo = aparecen más frecuentemente.
     * El mínimo TIEMPO_MIN asegura que el juego siga siendo jugable.
     */
    private float calcularTiempoSpawn(int nivel) {
        float tiempo = TIEMPO_BASE - (nivel - 1) * DECREMENTO_TIEMPO;
        return Math.max(tiempo, TIEMPO_MIN);
    }

    // ══════════════════════════════════════════════════════════════════
    // Lógica por frame
    // ══════════════════════════════════════════════════════════════════
    private void actualizar(float dt) {
        if (!started || gameOver)
            return;

        actualizarBird(bird1, dt);
        actualizarBird(bird2, dt);

        // ── Obtener nivel actual para escalar dificultad ───────────────
        int nivelActual = calcularNivel();
        float velocidadActual = calcularVelocidadTuberias(nivelActual);
        float tiempoSpawnActual = calcularTiempoSpawn(nivelActual);

        // ── Spawn de tuberías (compartidas) ───────────────────────────
        timerSpawn += dt;
        if (timerSpawn >= tiempoSpawnActual) {
            timerSpawn = 0f;
            spawnTuberia();
        }

        // ── Mover tuberías y puntuar / remover ────────────────────────
        Iterator<Tuberia> it = tuberias.iterator();
        while (it.hasNext()) {
            Tuberia t = it.next();
            t.x -= velocidadActual * dt;

            // Puntaje independiente por jugador
            puntarSiCorresponde(t, bird1, true);
            puntarSiCorresponde(t, bird2, false);

            // Remover si salió de pantalla
            if (t.x + TUBERIA_ANCHO * 0.5f < -1.3f)
                it.remove();
        }

        // ── ¿Ambos muertos? ───────────────────────────────────────────
        if (!bird1.alive && !bird2.alive) {
            gameOver = true;
            actualizarTitulo();
        }
    }

    /** Física + colisión de un único pájaro. */
    private void actualizarBird(Bird b, float dt) {
        if (!b.alive)
            return;

        b.velY += GRAVEDAD * dt;
        if (b.velY < VELOCIDAD_MAX_CAIDA)
            b.velY = VELOCIDAD_MAX_CAIDA;
        b.birdY += b.velY * dt;

        // Colisión con límites verticales de la pantalla
        float top = b.birdY + BIRD_ALTO * 0.5f;
        float bottom = b.birdY - BIRD_ALTO * 0.5f;
        if (top >= 1.0f || bottom <= -1.0f) {
            b.alive = false;
            actualizarTitulo();
            return;
        }

        // Colisión con tuberías
        for (Tuberia t : tuberias) {
            if (colisionaConTuberia(b, t)) {
                b.alive = false;
                actualizarTitulo();
                return;
            }
        }
    }

    /** Suma un punto al jugador cuando la tubería queda atrás de su pájaro. */
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
            // Actualizar máximo puntaje global para calcular el nivel
            int puntajeActual = Math.max(bird1.puntaje, bird2.puntaje);
            if (puntajeActual > maxPuntajeGlobal) {
                maxPuntajeGlobal = puntajeActual;
            }
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
            return false; // sin overlap X

        float gT = t.gapCentroY + GAP_ALTO * 0.5f;
        float gB = t.gapCentroY - GAP_ALTO * 0.5f;
        return bT > gT || bB < gB; // fuera del gap
    }

    // ══════════════════════════════════════════════════════════════════
    // Render
    // ══════════════════════════════════════════════════════════════════
    private void render() {
        // Fondo: cielo azul claro
        GL11.glClearColor(0.52f, 0.80f, 0.92f, 1.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

        GL20.glUseProgram(programa);
        GL30.glBindVertexArray(vao);

        // ── Tuberías (compartidas, misma lógica de siempre) ───────────
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

        float tiempo = (float) GLFW.glfwGetTime();

        // ── Pájaros ───────────────────────────────────────────────────
        // Los muertos se dibujan antes (quedan "debajo" visualmente)
        if (!bird1.alive)
            dibujarPajaroMuerto(bird1, tiempo);
        if (!bird2.alive)
            dibujarPajaroMuerto(bird2, tiempo);
        if (bird1.alive)
            dibujarPajaro(bird1, tiempo);
        if (bird2.alive)
            dibujarPajaro(bird2, tiempo);

        // ── Panel de puntaje lateral ──────────────────────────────────
        dibujarPanelPuntaje();

        // ── Overlay game over ─────────────────────────────────────────
        if (gameOver) {
            dibujarRect(0.0f, 0.0f, 2.0f, 0.22f, 0.10f, 0.12f, 0.15f);
        }
    }

    /**
     * Dibuja el pájaro completo en capas:
     * cola → ala trasera → cuerpo → panza → ala delantera →
     * cabeza → ojo → mejilla → pico
     *
     * @param b      Estado del pájaro (posición, colores).
     * @param tiempo Tiempo GLFW para animar el aleteo.
     */
    private void dibujarPajaro(Bird b, float tiempo) {
        float x = b.birdX;
        float y = b.birdY;
        float W = BIRD_ANCHO;
        float H = BIRD_ALTO;

        float[] c = b.colorCuerpo; // amarillo o azul
        float[] pz = b.colorPanza; // tono claro
        float[] al = b.colorAla; // tono oscuro/acento
        float[] pk = b.colorPico; // pico naranja

        // Aleteo: seno del tiempo, solo si está vivo
        float aleteo = (float) Math.sin(tiempo * 8.0f) * H * 0.20f;

        // 1. Cola — 3 plumas escalonadas
        dibujarRect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, al[0], al[1], al[2]);
        dibujarRect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, al[0] * 0.94f, al[1] * 0.88f, al[2]);
        dibujarRect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, al[0] * 0.88f, al[1] * 0.77f, al[2]);

        // 2. Ala trasera (anima hacia abajo)
        dibujarRect(x - W * 0.15f, y - H * 0.30f + aleteo, W * 0.80f, H * 0.30f, al[0], al[1], al[2]);

        // 3. Cuerpo
        dibujarRect(x, y, W, H, c[0], c[1], c[2]);

        // 4. Panza
        dibujarRect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, pz[0], pz[1], pz[2]);

        // 5. Ala delantera (anima hacia arriba con menor amplitud)
        dibujarRect(x - W * 0.10f, y + H * 0.22f + aleteo * 0.7f, W * 0.70f, H * 0.26f, al[0], al[1], al[2]);

        // 6. Cabeza
        dibujarRect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, c[0], c[1], c[2]);

        // 7. Ojo — blanco + pupila + brillo
        dibujarRect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.28f, 1f, 1f, 1f);
        dibujarRect(x + W * 0.48f, y + H * 0.50f, W * 0.15f, H * 0.18f, 0.08f, 0.08f, 0.18f);
        dibujarRect(x + W * 0.46f, y + H * 0.56f, W * 0.07f, H * 0.07f, 1f, 1f, 1f);

        // 8. Mejilla (color de acento suave)
        dibujarRect(x + W * 0.38f, y + H * 0.35f, W * 0.22f, H * 0.14f, 1f, 0.50f, 0.40f);

        // 9. Pico — mandíbula superior + inferior
        dibujarRect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, pk[0], pk[1], pk[2]);
        dibujarRect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, pk[0] * 0.90f, pk[1] * 0.85f, pk[2] * 0.80f);
    }

    /**
     * Versión "muerto" del pájaro: sin aleteo, color desaturado/oscuro
     * y sin mejilla (deja claro visualmente que está eliminado).
     */
    private void dibujarPajaroMuerto(Bird b, float tiempo) {
        float x = b.birdX;
        float y = b.birdY;
        float W = BIRD_ANCHO;
        float H = BIRD_ALTO;

        // Gris oscuro semi-uniforme para indicar muerte
        float gr = 0.40f;

        dibujarRect(x - W * 0.65f, y + H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
        dibujarRect(x - W * 0.70f, y, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
        dibujarRect(x - W * 0.65f, y - H * 0.18f, W * 0.45f, H * 0.22f, gr * 0.8f, gr * 0.8f, gr * 0.8f);
        dibujarRect(x - W * 0.15f, y - H * 0.30f, W * 0.80f, H * 0.30f, gr * 0.9f, gr * 0.9f, gr * 0.9f);
        dibujarRect(x, y, W, H, gr, gr, gr);
        dibujarRect(x + W * 0.10f, y - H * 0.05f, W * 0.55f, H * 0.58f, gr + 0.15f, gr + 0.15f, gr + 0.15f);// PANZA
        // Ala caída (pegada al cuerpo y apuntando hacia abajo)
        dibujarRect(x - W * 0.05f, y - H * 0.40f, W * 0.70f, H * 0.20f, gr * 0.9f, gr * 0.9f, gr * 0.9f);//ALA
        dibujarRect(x + W * 0.28f, y + H * 0.42f, W * 0.75f, H * 0.70f, gr, gr, gr);

        // Ojo de muerto (Cruz / +)
        // Línea horizontal
        dibujarRect(x + W * 0.42f, y + H * 0.52f, W * 0.28f, H * 0.08f, 0.1f, 0.1f, 0.1f);
        // Línea vertical (centrada sobre la horizontal)
        dibujarRect(x + W * 0.52f, y + H * 0.45f, W * 0.08f, H * 0.22f, 0.1f, 0.1f, 0.1f);
        // Pico triste
        dibujarRect(x + W * 0.72f, y + H * 0.44f, W * 0.38f, H * 0.18f, 0.55f, 0.25f, 0.05f);
        dibujarRect(x + W * 0.68f, y + H * 0.30f, W * 0.32f, H * 0.13f, 0.50f, 0.22f, 0.04f);
    }

    /**
     * Panel lateral izquierdo con indicadores visuales de puntaje.
     *
     * Como no hay renderizado de texto en el framebuffer, el panel
     * usa rectángulos apilados como "barras de puntaje":
     * - Barra de color del jugador que crece con cada punto.
     * - Tope máximo de 20 barras para no salir de pantalla.
     *
     * El título de la ventana también muestra los puntajes exactos.
     */
    private void dibujarPanelPuntaje() {
        // Fondo del panel
        dibujarRect(-0.92f, 0.0f, 0.14f, 2.0f, 0.10f, 0.14f, 0.20f);

        int max = 15; // segmentos máximos visibles
        float segH = 1.8f / max;
        float panelX = -0.92f;

        // ── Jugador 1 (izquierda del panel) ───────────────────────────
        int pts1 = Math.min(bird1.puntaje, max);
        for (int i = 0; i < pts1; i++) {
            float sy = -0.9f + i * segH + segH * 0.5f;
            dibujarRect(panelX - 0.025f, sy, 0.05f, segH * 0.85f,
                    bird1.colorCuerpo[0], bird1.colorCuerpo[1], bird1.colorCuerpo[2]);
        }

        // ── Jugador 2 (derecha del panel) ─────────────────────────────
        int pts2 = Math.min(bird2.puntaje, max);
        for (int i = 0; i < pts2; i++) {
            float sy = -0.9f + i * segH + segH * 0.5f;
            dibujarRect(panelX + 0.025f, sy, 0.05f, segH * 0.85f,
                    bird2.colorCuerpo[0], bird2.colorCuerpo[1], bird2.colorCuerpo[2]);
        }

        // Divisor central del panel
        dibujarRect(panelX, 0.0f, 0.004f, 1.9f, 0.55f, 0.65f, 0.70f);

        // Indicador de estado: punto brillante si está vivo, oscuro si muerto
        float yIndicador = 0.95f;
        // P1
        float[] c1 = bird1.alive
                ? new float[] { bird1.colorCuerpo[0], bird1.colorCuerpo[1], bird1.colorCuerpo[2] }
                : new float[] { 0.3f, 0.3f, 0.3f };
        dibujarRect(panelX - 0.025f, yIndicador, 0.06f, 0.06f, c1[0], c1[1], c1[2]);
        // P2
        float[] c2 = bird2.alive
                ? new float[] { bird2.colorCuerpo[0], bird2.colorCuerpo[1], bird2.colorCuerpo[2] }
                : new float[] { 0.3f, 0.3f, 0.3f };
        dibujarRect(panelX + 0.025f, yIndicador, 0.06f, 0.06f, c2[0], c2[1], c2[2]);
    }

    // ── Helper de dibujo ───────────────────────────────────────────────
    private void dibujarRect(float x, float y, float ancho, float alto,
            float r, float g, float b) {
        GL20.glUniform2f(uOffsetLocation, x, y);
        GL20.glUniform2f(uScaleLocation, ancho, alto);
        GL20.glUniform3f(uColorLocation, r, g, b);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
    }

    // ── Título de ventana ──────────────────────────────────────────────
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

    // ══════════════════════════════════════════════════════════════════
    // Bucle principal
    // ══════════════════════════════════════════════════════════════════
    private void loop() {
        float ultimoTiempo = (float) GLFW.glfwGetTime();
        while (!GLFW.glfwWindowShouldClose(window)) {
            float ahora = (float) GLFW.glfwGetTime();
            float dt = Math.min(ahora - ultimoTiempo, 0.033f); // cap 30 ms
            ultimoTiempo = ahora;

            procesarInput();
            actualizar(dt);
            render();

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    // ── Liberación de recursos ─────────────────────────────────────────
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