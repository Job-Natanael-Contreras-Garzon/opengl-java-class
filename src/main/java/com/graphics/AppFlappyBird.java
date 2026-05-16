package com.graphics;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

/**
 * AppFlappyBird — Entry point y bucle principal del juego.
 *
 * Responsabilidades únicas:
 * - Crear y gestionar ventana GLFW y contexto OpenGL.
 * - Capturar input (SPACE, W, UP, ESC, R) y delegar a {@link GameWorld}.
 * - Mantener el tiempo acumulado (para animaciones del Renderer).
 * - Llamar a {@link Renderer#render(GameWorld, float)} cada frame.
 * - Actualizar título de la ventana con estado (puntajes, nivel, velocidad).
 *
 * Arquitectura:
 * - NO contiene lógica de juego ni de render — solo orquestación.
 * - GameWorld: lógica pura (física, colisiones, spawning).
 * - Renderer: presentación gráfica (dibujo OpenGL).
 * - AppFlappyBird: coordinación de subsistemas.
 *
 * Ciclo de vida:
 * 1. init() — crea ventana OpenGL, subsistemas
 * 2. loop() — procesa input, actualiza mundo, renderiza frames
 * 3. cleanup() — libera recursos
 */
public class AppFlappyBird {

    // ════════════════════════════════════════════════════════════════════════
    // ESTADO OPENGL Y SUBSISTEMAS
    // ════════════════════════════════════════════════════════════════════════
    
    private long window;             // Handle de la ventana GLFW
    private GameWorld world;         // Lógica del juego (pájaros, tuberías, física)
    private Renderer renderer;       // Renderizador OpenGL (dibuja todo)

    // Tiempo acumulado desde el inicio (para animaciones sinusoidales en HUD y pájaros)
    private float tiempo = 0f;

    // ── Detección de flancos de teclado ──────────────────────────────────────
    // Permite detectar transiciones: tecla NO presionada → presionada (flanco)
    // Sin esto, una tecla presionada indefinidamente causaría múltiples saltos
    private boolean prevSpace;       // SPACE presionada en el frame anterior
    private boolean prevW;           // W presionada en el frame anterior
    private boolean prevUp;          // ARRIBA presionada en el frame anterior
    private boolean prevR;           // R presionada en el frame anterior

    // ════════════════════════════════════════════════════════════════════════
    // CICLO DE VIDA
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Punto de entrada: ejecuta ciclo completo.
     * Llamado por main().
     */
    public void run() {
        init();      // Inicializar GLFW, OpenGL, subsistemas
        loop();      // Bucle principal (frames)
        cleanup();   // Liberar recursos
    }

    /**
     * Inicialización: ventana, contexto OpenGL y subsistemas.
     * 
     * Pasos:
     * 1. Iniciar GLFW
     * 2. Configurar hints (OpenGL 3.3 core, ventana redimensionable)
     * 3. Crear ventana
     * 4. Establecer contexto OpenGL actual
     * 5. Crear GameWorld (lógica) y Renderer (gráficos)
     * 6. Actualizar título con estado inicial
     */
    private void init() {
        // Iniciar GLFW (necesario antes de cualquier operación GLFW)
        if (!GLFW.glfwInit())
            throw new IllegalStateException("No se pudo iniciar GLFW");

        // Configurar hints (propiedades de la ventana antes de crearla)
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);     // Crear oculta, mostrar después
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);    // Permite redimensionar

        // Solicitar OpenGL 3.3 core profile (sin funciones deprecated)
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);     // Versión 3.x
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);     // Versión 3.3
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        // Crear la ventana
        window = GLFW.glfwCreateWindow(
                Constants.ANCHO, Constants.ALTO,
                "Flappy Bird 3P", 0, 0);
        if (window == 0)
            throw new RuntimeException("No se pudo crear la ventana");

        // Hacer el contexto OpenGL actual en este hilo
        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1);  // VSync: limita a 60 FPS (1 frame por refresh monitor)
        GLFW.glfwShowWindow(window);  // Mostrar ventana
        GL.createCapabilities();  // Cargar funciones OpenGL

        // Instanciar subsistemas
        world = new GameWorld();
        world.init();  // Inicializar pájaros, colores, etc.

        renderer = new Renderer();
        renderer.init();  // Compilar shaders, crear buffers

        // Mostrar estado inicial
        actualizarTitulo();
    }

    // ════════════════════════════════════════════════════════════════════════
    // INPUT (LECTURA DE TECLADO)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Procesa entrada de teclado cada frame.
     * 
     * Detecta flancos (transiciones de no-presionado a presionado)
     * para eventos de un solo disparo (jump, reset).
     * 
     * Controles:
     * - ESC: cerrar ventana
     * - SPACE: P1 salta (o comienza partida si está en inicio)
     * - W / UP: P2 salta (o comienza partida)
     * - R: reiniciar si está en game over
     */
    private void procesarInput() {
        // ── ESC → Cerrar ventana ────────────────────────────────────────────
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS)
            GLFW.glfwSetWindowShouldClose(window, true);

        // ── SPACE → P1 Salta ────────────────────────────────────────────────
        // Detecta transición: SPACE no presionada → presionada (flanco)
        boolean spaceAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (spaceAhora && !prevSpace) {  // Flanco ascendente de SPACE
            if (world.gameOver)
                world.reset();  // En game over, reinicia
            world.saltarP1();   // En juego, P1 salta
        }
        prevSpace = spaceAhora;  // Guardar estado para próximo frame

        // ── W / ↑ → P2 Salta ────────────────────────────────────────────────
        // Detecta si W o ARRIBA fueron presionadas
        boolean wAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean upAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        if ((wAhora && !prevW) ) {  // Flanco de W o UP
            if (world.gameOver)
                world.reset();
            world.saltarP2();
        }
        if ( (upAhora && !prevUp)) {  // Flanco de W o UP
            if (world.gameOver)
                world.reset();
            world.saltarP3();
        }
        prevW = wAhora;
        prevUp = upAhora;

        // ── R → Reiniciar en game over ──────────────────────────────────────
        boolean rAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (rAhora && !prevR && world.gameOver)
            world.reset();
        prevR = rAhora;
    }

    // ════════════════════════════════════════════════════════════════════════
    // BUCLE PRINCIPAL
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Bucle de juego principal (frame loop).
     * 
     * Cada iteración:
     * 1. Calcular delta time (dt) — segundos desde el último frame
     * 2. Procesar input
     * 3. Actualizar mundo (física, colisiones, spawning)
     * 4. Renderizar frame
     * 5. Intercambiar buffers (mostrar en pantalla)
     * 6. Procesar eventos (si la ventana fue cerrada, termina)
     */
    private void loop() {
        // Tiempo del frame anterior (para calcular dt)
        float ultimoTiempo = (float) GLFW.glfwGetTime();

        while (!GLFW.glfwWindowShouldClose(window)) {
            // ── Calcular delta time ──────────────────────────────────────────
            float ahora = (float) GLFW.glfwGetTime();
            float dt = Math.min(ahora - ultimoTiempo, 0.033f);  // Cap a 33ms (30 FPS mínimo)
            ultimoTiempo = ahora;
            tiempo += dt;  // Tiempo acumulado (para animaciones sinusoidales)

            // ── Procesar entrada ─────────────────────────────────────────────
            procesarInput();

            // ── Guardar estado anterior (para detectar cambios de título) ────
            // Permite actualizar título solo cuando hay cambios (no cada frame)
            boolean prevGameOver = world.gameOver;
            int prevPuntaje = world.maxPuntajeGlobal;
            int prevNivel = world.calcularNivel();

            // ── Actualizar mundo ─────────────────────────────────────────────
            // Física, colisiones, spawning de tuberías, partículas
            world.actualizar(dt);

            // ── Actualizar título si cambió estado ───────────────────────────
            if (world.gameOver != prevGameOver
                    || world.maxPuntajeGlobal != prevPuntaje
                    || world.calcularNivel() != prevNivel) {
                actualizarTitulo();
            }

            // ── Renderizar frame ─────────────────────────────────────────────
            // Dibuja fondo, montañas, pájaros, HUD, todo
            renderer.render(world, tiempo);

            // ── Intercambiar buffers y procesar eventos ──────────────────────
            GLFW.glfwSwapBuffers(window);  // Mostrar lo que se dibujó
            GLFW.glfwPollEvents();          // Procesar eventos (cerrar ventana, etc.)
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TÍTULO DE VENTANA (INFORMACIÓN DE ESTADO)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Actualiza el título de la ventana con información de estado.
     * 
     * Muestra:
     * - P1: <nombre>:<puntaje> y estado (✗ si muerto)
     * - P2: <nombre>:<puntaje> y estado (✗ si muerto)
     * - Nivel actual y velocidad de tuberías
     * - Mensaje contextual (SPACE para empezar, SPACE/W/R para reiniciar, etc.)
     */
    private void actualizarTitulo() {
        // Información de P1
        String p1 = world.bird1.nombre + ":" + world.bird1.puntaje
                + (world.bird1.alive ? "" : " ✗");  // Marca ✗ si está muerto
        
        // Información de P2
        String p2 = world.bird2.nombre + ":" + world.bird2.puntaje
                + (world.bird2.alive ? "" : " ✗");
        // Informacion de P3
        String p3 = world.bird3.nombre + ":" + world.bird3.puntaje
                    + (world.bird3.alive ? "" : " ✗");
        
        // Información de nivel y velocidad actual
        String nivel = "Nv." + world.calcularNivel()
                + " [vel:" + String.format("%.2f", world.velocidadActual()) + "]";
        
        // Construir título base
        String base = "Flappy Bird 2P  |  " + p1 + "   " + p2 + "  " + p3 + "  |  " + nivel;

        // Añadir mensaje contextual según estado
        if (!world.started)
            // En pantalla de inicio
            GLFW.glfwSetWindowTitle(window, base + "  |  SPACE / W / ARRIBA para empezar");
        else if (world.gameOver)
            // En pantalla de game over
            GLFW.glfwSetWindowTitle(window, base + "  |  GAME OVER — SPACE/W/R para reiniciar");
        else
            // En juego
            GLFW.glfwSetWindowTitle(window, base);
    }

    // ════════════════════════════════════════════════════════════════════════
    // CLEANUP (LIBERAR RECURSOS)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Libera todos los recursos antes de terminar.
     * 
     * Orden importante:
     * 1. Cleanup Renderer (libera VAO, VBO, programa OpenGL)
     * 2. Shutdown GameWorld (cierra pool de audio del SoundManager)
     * 3. Destruir ventana GLFW
     * 4. Terminar GLFW
     */
    private void cleanup() {
        renderer.cleanup();        // Libera OpenGL resources
        world.shutdown();          // Cierra audio
        GLFW.glfwDestroyWindow(window);  // Destruir ventana
        GLFW.glfwTerminate();      // Terminar GLFW
    }

    // ════════════════════════════════════════════════════════════════════════
    // ENTRADA (MAIN)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Punto de entrada del programa.
     */
    public static void main(String[] args) {
        new AppFlappyBird().run();
    }
}