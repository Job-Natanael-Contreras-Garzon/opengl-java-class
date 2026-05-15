package com.graphics;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class AppMovimientoTeclado {

    private long window;
    private int programa;
    private int vao;
    private int vbo;

    // uOffsetLocation almacena la ubicación (ID interno) del uniform "uOffset" dentro del shader.
    // Este valor es obtenido desde el programa compilado en la GPU mediante glGetUniformLocation.
    // Se utiliza posteriormente para enviar datos (offsetX, offsetY) desde la CPU al shader con glUniform2f,
    // permitiendo desplazar el triángulo de forma uniforme en cada frame sin modificar los vértices originales.
    private int uOffsetLocation;

    private static final int ANCHO = 800;
    private static final int ALTO = 600;

    // Offset del triángulo; se manda al shader en cada frame para moverlo.
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    // Velocidad en unidades OpenGL por segundo (combinada con deltaTime).
    private static final float VELOCIDAD = 1.2f;
    // Límite del desplazamiento para mantener el triángulo en zona visible.
    private static final float LIMITE = 0.9f;

    public void run() {
        init();
        loop();
        cleanup();
    }

    private void init() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo iniciar GLFW");
        }

       // Restablece todos los hints a sus valores por defecto antes de configurar los nuevos.
        GLFW.glfwDefaultWindowHints();
        // Oculta la ventana al crearla; se mostrará explícitamente después de la configuración.
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        // Permite redimensionar la ventana por el usuario.
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        // Solicita un contexto OpenGL 3.3.
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        // Usa el perfil Core, que excluye funciones obsoletas de OpenGL.
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        // Requerido en macOS para que el contexto Core Profile funcione correctamente.
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        window = GLFW.glfwCreateWindow(ANCHO, ALTO, "OpenGL - Triangulo Movible", 0, 0);
        if (window == 0) {
            throw new RuntimeException("No se pudo crear la ventana");
        }

        // Atajo nuevo: cerrar ventana al presionar ESC.
        GLFW.glfwSetKeyCallback(window, (w, key, scancode, action, mods) -> {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                GLFW.glfwSetWindowShouldClose(w, true);
            }
        });

        // Asocia el contexto OpenGL de la ventana al hilo actual.
        // Es necesario antes de cualquier llamada a OpenGL.
        GLFW.glfwMakeContextCurrent(window);
        // Activa la sincronización vertical (VSync): limita los frames al refresco del monitor.
        // El valor 1 significa "esperar 1 refresco antes de intercambiar buffers".
        GLFW.glfwSwapInterval(1);
        // Hace visible la ventana ahora que el contexto está configurado.
        GLFW.glfwShowWindow(window);
        // Inicializa las capacidades de OpenGL para el contexto actual.
        // Debe llamarse después de glfwMakeContextCurrent para que LWJGL
        // pueda detectar y exponer las funciones disponibles en el driver.
        GL.createCapabilities();

        // Compila y enlaza los shaders, y obtiene la ubicación del uniform uOffset.
        crearShaders();
        // Crea el VAO y el VBO con los vértices del triángulo.
        crearTriangulo();
    }

    private void crearShaders() {
        // Vertex shader:
        // - Recibe posición original "aPos" del VBO.
        // - Recibe "uOffset" (uniform) para desplazar el triángulo completo.
        // - Construye posición final sumando offset a XY.
        String vertexSrc = """
            #version 330 core
            layout (location = 0) in vec3 aPos;
            uniform vec2 uOffset;
            void main() {
                vec3 pos = vec3(aPos.xy + uOffset, aPos.z);
                gl_Position = vec4(pos, 1.0);
            }
            """;

       // Fragment shader: produce un color verde fijo para todos los fragmentos del triángulo.
        String fragmentSrc = """
            #version 330 core
            out vec4 fragColor;
            void main() { fragColor = vec4(0.2, 0.8, 0.4, 1.0); }
            """;

        // Crea, carga el código fuente y compila el vertex shader en la GPU.
        int vertexShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vertexShader, vertexSrc);
        GL20.glCompileShader(vertexShader);
        // Verifica que la compilación fue exitosa; lanza excepción si hubo errores.
        comprobarShader(vertexShader, "Vertex");

        // Crea, carga el código fuente y compila el fragment shader en la GPU.
        int fragmentShader = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fragmentShader, fragmentSrc);
        GL20.glCompileShader(fragmentShader);
        // Verifica que la compilación fue exitosa; lanza excepción si hubo errores.
        comprobarShader(fragmentShader, "Fragment");

        // Crea el programa de shaders, adjunta ambos shaders compilados y los enlaza
        // en un único programa ejecutable que la GPU usará durante el renderizado.
        programa = GL20.glCreateProgram();

        
        GL20.glAttachShader(programa, vertexShader);
        GL20.glAttachShader(programa, fragmentShader);
        GL20.glLinkProgram(programa);

        if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("Error al enlazar programa: " + GL20.glGetProgramInfoLog(programa));
        }

        // Buscamos la dirección del uniform "uOffset".
        // Esta dirección se usa cada frame con glUniform2f para actualizar la posición.
        uOffsetLocation = GL20.glGetUniformLocation(programa, "uOffset");
        if (uOffsetLocation == -1) {
            throw new RuntimeException("No se encontro el uniform uOffset");
        }

        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    private void comprobarShader(int shader, String tipo) {
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException(tipo + " shader: " + GL20.glGetShaderInfoLog(shader));
        }
    }

    private void crearTriangulo() {
        float[] vertices = {
             0.0f,  0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
             0.5f, -0.5f, 0.0f
        };

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private void procesarInput(float deltaTime) {
        // Distancia recorrida este frame:
        // velocidad (unidades/s) * tiempo del frame (s) = unidades/frame.
        float paso = VELOCIDAD * deltaTime;

        // Movimiento horizontal: izquierda con flecha izquierda o tecla A.
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
            offsetX -= paso;
        }
        // Movimiento horizontal: derecha con flecha derecha o tecla D.
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
            offsetX += paso;
        }
        // Movimiento vertical: arriba con flecha arriba o tecla W.
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
            offsetY += paso;
        }
        // Movimiento vertical: abajo con flecha abajo o tecla S.
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
            offsetY -= paso;
        }

        // Clamping de seguridad para mantener el triángulo dentro de una zona visible.
        // Evita acumulación infinita de offset si una tecla se mantiene mucho tiempo.
        offsetX = Math.max(-LIMITE, Math.min(LIMITE, offsetX));
        offsetY = Math.max(-LIMITE, Math.min(LIMITE, offsetY));
    }

    private void loop() {
        // Nuevo: referencia de tiempo para calcular deltaTime entre frames.
        float ultimoTiempo = (float) GLFW.glfwGetTime();

        while (!GLFW.glfwWindowShouldClose(window)) {
            // Nuevo: movimiento desacoplado de FPS usando deltaTime.
            float tiempoActual = (float) GLFW.glfwGetTime();
            float deltaTime = tiempoActual - ultimoTiempo;
            ultimoTiempo = tiempoActual;

            // Actualiza offsetX/offsetY según teclado.
            procesarInput(deltaTime);

            GL11.glClearColor(0.08f, 0.08f, 0.12f, 1.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            GL20.glUseProgram(programa);
            // Enviamos el offset actualizado al shader antes de dibujar.
            // A partir de este valor, el vertex shader desplaza cada vértice.
            GL20.glUniform2f(uOffsetLocation, offsetX, offsetY);
            GL30.glBindVertexArray(vao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

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
        new AppMovimientoTeclado().run();
    }
}
