package com.graphics;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;

/**
 * AppFlappyBird — Entry point y bucle principal.
 *
 * Responsabilidades únicas:
 * - Crear ventana GLFW y contexto OpenGL.
 * - Capturar input y delegar a {@link GameWorld}.
 * - Mantener el delta time y el tiempo acumulado.
 * - Llamar a {@link Renderer#render(GameWorld, float)} cada frame.
 * - Actualizar el título de la ventana cuando cambia el estado.
 *
 * NO contiene lógica de juego ni de render — sólo orquestación.
 */
public class AppFlappyBird {

    private long window;
    private GameWorld world;
    private Renderer renderer;

    // Tiempo acumulado desde el inicio de la ventana (para animaciones)
    private float tiempo = 0f;

    // Detección de flancos de teclado
    private boolean prevSpace, prevW, prevUp, prevR;

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    public void run() {
        init();
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

        window = GLFW.glfwCreateWindow(
                Constants.ANCHO, Constants.ALTO,
                "Flappy Bird 2P", 0, 0);
        if (window == 0)
            throw new RuntimeException("No se pudo crear la ventana");

        GLFW.glfwMakeContextCurrent(window);
        GLFW.glfwSwapInterval(1); // VSync
        GLFW.glfwShowWindow(window);
        GL.createCapabilities();

        // Instanciar subsistemas
        world = new GameWorld();
        world.init();

        renderer = new Renderer();
        renderer.init();

        actualizarTitulo();
    }

    // ── Input ────────────────────────────────────────────────────────────────

    private void procesarInput() {
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS)
            GLFW.glfwSetWindowShouldClose(window, true);

        // ── SPACE → P1 ────────────────────────────────────────────────
        boolean spaceAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        if (spaceAhora && !prevSpace) {
            if (world.gameOver)
                world.reset();
            world.saltarP1();
        }
        prevSpace = spaceAhora;

        // ── W / ↑ → P2 ────────────────────────────────────────────────
        boolean wAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
        boolean upAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS;
        if ((wAhora && !prevW) || (upAhora && !prevUp)) {
            if (world.gameOver)
                world.reset();
            world.saltarP2();
        }
        prevW = wAhora;
        prevUp = upAhora;

        // ── R → reiniciar en game over ────────────────────────────────
        boolean rAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (rAhora && !prevR && world.gameOver)
            world.reset();
        prevR = rAhora;
    }

    // ── Bucle principal ──────────────────────────────────────────────────────

    private void loop() {
        float ultimoTiempo = (float) GLFW.glfwGetTime();

        while (!GLFW.glfwWindowShouldClose(window)) {
            float ahora = (float) GLFW.glfwGetTime();
            float dt = Math.min(ahora - ultimoTiempo, 0.033f); // cap 33 ms
            ultimoTiempo = ahora;
            tiempo += dt;

            procesarInput();

            // Snapshot para detectar cambios y actualizar título sólo si hay novedades
            boolean prevGameOver = world.gameOver;
            int prevPuntaje = world.maxPuntajeGlobal;
            int prevNivel = world.calcularNivel();

            world.actualizar(dt);

            if (world.gameOver != prevGameOver
                    || world.maxPuntajeGlobal != prevPuntaje
                    || world.calcularNivel() != prevNivel) {
                actualizarTitulo();
            }

            renderer.render(world, tiempo);

            GLFW.glfwSwapBuffers(window);
            GLFW.glfwPollEvents();
        }
    }

    // ── Título de ventana ────────────────────────────────────────────────────

    private void actualizarTitulo() {
        String p1 = world.bird1.nombre + ":" + world.bird1.puntaje
                + (world.bird1.alive ? "" : " ✗");
        String p2 = world.bird2.nombre + ":" + world.bird2.puntaje
                + (world.bird2.alive ? "" : " ✗");
        String nivel = "Nv." + world.calcularNivel()
                + " [vel:" + String.format("%.2f", world.velocidadActual()) + "]";
        String base = "Flappy Bird 2P  |  " + p1 + "   " + p2 + "  |  " + nivel;

        if (!world.started)
            GLFW.glfwSetWindowTitle(window, base + "  |  SPACE / W para empezar");
        else if (world.gameOver)
            GLFW.glfwSetWindowTitle(window, base + "  |  GAME OVER — SPACE/W/R para reiniciar");
        else
            GLFW.glfwSetWindowTitle(window, base);
    }

    // ── Cleanup ──────────────────────────────────────────────────────────────

    private void cleanup() {
        renderer.cleanup();
        world.shutdown(); // cierra pool de audio
        GLFW.glfwDestroyWindow(window);
        GLFW.glfwTerminate();
    }

    public static void main(String[] args) {
        new AppFlappyBird().run();
    }
}