# 📋 INICIO DE PROYECTO - Flappy Bird OpenGL (Primer Parcial)

## 🎯 Objetivo del Proyecto

Modificar y extender la aplicación base **AppFlappyBird.java** para cumplir con los 4 requerimientos obligatorios del primer parcial de Programación Gráfica. Este documento proporciona una guía completa de implementación basada en el análisis del código existente y las funcionalidades ya disponibles en el proyecto.

---

## 📁 Estructura del Proyecto Actual

```
opengl-java-class/
├── pom.xml                          # Configuración Maven con dependencias LWJGL
├── src/main/java/com/graphics/
│   ├── App.java                     # ✅ Triángulo básico (referencia mínima)
│   ├── AppMovimientoTeclado.java    # ✅ Movimiento con teclado + uniforms
│   ├── AppZoom.java                 # ✅ Zoom con uniforms
│   ├── AppMovimientoZoom.java       # ✅ Movimiento + Zoom combinados
│   ├── App3D.java                   # ✅ Cubo 3D con rotación y transformaciones
│   ├── AppCamara.java               # ✅ Cámara first-person con múltiples objetos
│   ├── AppLaberinto.java            # ✅ Colisiones, múltiples objetos, lógica de juego
│   └── AppFlappyBird.java           # 🎮 BASE DEL PROYECTO (punto de partida)
└── README.md                        # Documentación general del proyecto
```

---

## 🔧 Tecnologías y Librerías Disponibles

### ✅ Ya Configuradas en `pom.xml`

- **Java 17**: Versión del lenguaje
- **Maven 3.9+**: Sistema de construcción
- **LWJGL 3.3.3**: Lightweight Java Game Library
  - `lwjgl-core`: Núcleo de LWJGL
  - `lwjgl-glfw`: Gestión de ventanas y entrada (teclado, mouse)
  - `lwjgl-opengl`: Bindings de OpenGL 3.3 Core Profile
  - `natives-windows`: Bibliotecas nativas para Windows

### 🎨 OpenGL 3.3 Core Profile

- **Vertex Shaders**: Transformación de vértices
- **Fragment Shaders**: Coloreado de píxeles
- **Uniforms**: Variables globales del shader (offset, scale, color, rotación)
- **VAO/VBO**: Gestión de geometría en GPU
- **Primitivas**: `GL_TRIANGLES`, `GL_TRIANGLE_FAN`, `GL_LINES`

---

## 📊 Análisis de AppFlappyBird.java (Base Actual)

### ✅ Funcionalidades Ya Implementadas

| Componente | Descripción | Ubicación en Código |
|------------|-------------|---------------------|
| **Ventana GLFW** | 900x700px, OpenGL 3.3 Core | `init()` |
| **Shaders Básicos** | Vertex + Fragment con uniforms | `crearShaders()` |
| **Quad Reutilizable** | Rectángulo base escalable | `crearQuadBase()` |
| **Física del Pájaro** | Gravedad, impulso, velocidad máxima | `actualizar()` |
| **Input de Teclado** | SPACE (saltar), R (reiniciar), ESC (salir) | `procesarInput()` |
| **Generación de Tuberías** | Spawn automático con gap aleatorio | `spawnTuberia()` |
| **Colisiones AABB** | Detección contra tuberías y bordes | `colisionaConTuberia()` |
| **Sistema de Puntaje** | Incremento al pasar tuberías | `actualizar()` |
| **Game Over** | Detección y reinicio | `resetGame()` |
| **Delta Time** | Movimiento independiente de FPS | `loop()` |

### 🎨 Sistema de Rendering Actual

```java
// Método clave: dibujarRect()
private void dibujarRect(float x, float y, float ancho, float alto, float r, float g, float b) {
    GL20.glUniform2f(uOffsetLocation, x, y);      // Posición
    GL20.glUniform2f(uScaleLocation, ancho, alto); // Tamaño
    GL20.glUniform3f(uColorLocation, r, g, b);     // Color
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);   // Dibujar quad
}
```

**Ventaja**: Un solo VBO/VAO reutilizado para todos los rectángulos mediante uniforms.

---

## 🎯 Requerimientos del Parcial y Estrategia de Implementación


### 📌 Requerimiento 1: Pájaro Compuesto por Figuras Geométricas

#### 🎯 Objetivo
Reemplazar el rectángulo simple del pájaro por un personaje compuesto de múltiples figuras geométricas.

#### ✅ Componentes Obligatorios
- **Cuerpo principal** (rectángulo o círculo)
- **Pico** (triángulo)
- **Ala** (triángulo o polígono, con animación opcional)
- **Cola** (triángulo o trapecio)
- **Ojo** (círculo pequeño + pupila)

#### 🔨 Estrategia de Implementación

**Opción A: Múltiples VAO/VBO (Recomendado para aprender)**
```java
// Crear geometrías específicas
private void crearGeometriaPajaro() {
    crearCuerpoPajaro();    // Círculo con GL_TRIANGLE_FAN
    crearPicoPajaro();      // Triángulo
    crearAlaPajaro();       // Triángulo
    crearColaPajaro();      // Triángulo
    crearOjoPajaro();       // Círculo pequeño
}

// Dibujar pájaro compuesto
private void dibujarPajaro(float x, float y) {
    // Aplicar transformaciones relativas a (x, y)
    dibujarCuerpo(x, y);
    dibujarPico(x + 0.05f, y);
    dibujarAla(x - 0.03f, y + 0.02f, animacionAla);
    dibujarCola(x - 0.08f, y);
    dibujarOjo(x + 0.02f, y + 0.03f);
}
```

**Opción B: Reutilizar Quad Base (Más eficiente)**
```java
// Usar dibujarRect() existente para cada parte
private void dibujarPajaro(float x, float y) {
    // Cuerpo (amarillo)
    dibujarRect(x, y, 0.10f, 0.10f, 0.98f, 0.85f, 0.20f);
    // Pico (naranja) - requiere crear método dibujarTriangulo()
    dibujarTriangulo(x + 0.05f, y, 0.03f, 0.02f, 1.0f, 0.5f, 0.0f);
    // Ala (amarillo oscuro)
    dibujarRect(x - 0.03f, y + 0.02f, 0.04f, 0.06f, 0.85f, 0.70f, 0.15f);
    // Cola
    dibujarTriangulo(x - 0.08f, y, 0.04f, 0.03f, 0.90f, 0.75f, 0.18f);
    // Ojo (blanco + negro)
    dibujarCirculo(x + 0.02f, y + 0.03f, 0.015f, 1.0f, 1.0f, 1.0f);
    dibujarCirculo(x + 0.025f, y + 0.03f, 0.008f, 0.0f, 0.0f, 0.0f);
}
```

#### 📚 Referencias en el Proyecto
- **App3D.java**: Ejemplo de múltiples VAO/VBO para geometría compleja
- **AppCamara.java**: Reutilización de un solo cubo con diferentes transformaciones
- **AppFlappyBird.java**: Método `dibujarRect()` como base

#### 🎨 Animación de Aleteo
```java
// En actualizar()
private float tiempoAnimacion = 0.0f;
private float anguloAla = 0.0f;

tiempoAnimacion += dt;
anguloAla = (float) Math.sin(tiempoAnimacion * 8.0f) * 0.3f; // Oscila entre -0.3 y 0.3

// Al dibujar ala, aplicar rotación o desplazamiento vertical
```

---

### 📌 Requerimiento 2: Modo de Dos Jugadores Simultáneos

#### 🎯 Objetivo
Dos pájaros jugando al mismo tiempo con controles independientes.

#### ✅ Especificaciones
- **Jugador 1**: SPACE para saltar
- **Jugador 2**: W o FLECHA ARRIBA para saltar
- Cada jugador tiene: posición, velocidad, estado vivo/muerto, puntaje
- Tuberías compartidas
- Game over cuando ambos mueren

#### 🔨 Estrategia de Implementación

**Paso 1: Crear Clase Jugador**
```java
private static class Jugador {
    float y;
    float velY;
    int puntaje;
    boolean vivo;
    float r, g, b; // Color distintivo
    
    Jugador(float colorR, float colorG, float colorB) {
        this.r = colorR;
        this.g = colorG;
        this.b = colorB;
        reset();
    }
    
    void reset() {
        y = 0.0f;
        velY = 0.0f;
        puntaje = 0;
        vivo = true;
    }
}
```

**Paso 2: Modificar Variables Globales**
```java
// Reemplazar variables individuales
private Jugador jugador1;
private Jugador jugador2;

// En init()
jugador1 = new Jugador(0.98f, 0.85f, 0.20f); // Amarillo
jugador2 = new Jugador(0.20f, 0.85f, 0.98f); // Azul
```

**Paso 3: Input Independiente**
```java
private void procesarInput() {
    // Jugador 1: SPACE
    boolean space = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
    if (space && !prevSpace && jugador1.vivo) {
        jugador1.velY = IMPULSO_SALTO;
    }
    prevSpace = space;
    
    // Jugador 2: W
    boolean w = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS;
    if (w && !prevW && jugador2.vivo) {
        jugador2.velY = IMPULSO_SALTO;
    }
    prevW = w;
}
```

**Paso 4: Actualización y Colisiones**
```java
private void actualizar(float dt) {
    if (!started || gameOver) return;
    
    // Actualizar cada jugador vivo
    if (jugador1.vivo) {
        actualizarJugador(jugador1, dt);
    }
    if (jugador2.vivo) {
        actualizarJugador(jugador2, dt);
    }
    
    // Game over cuando ambos mueren
    if (!jugador1.vivo && !jugador2.vivo) {
        gameOver = true;
    }
    
    // Tuberías (compartidas)
    actualizarTuberias(dt);
}

private void actualizarJugador(Jugador j, float dt) {
    j.velY += GRAVEDAD * dt;
    j.velY = Math.max(j.velY, VELOCIDAD_MAX_CAIDA);
    j.y += j.velY * dt;
    
    // Colisiones
    if (colisionaBordes(j) || colisionaTuberias(j)) {
        j.vivo = false;
    }
}
```

**Paso 5: Renderizado**
```java
private void render() {
    // ... fondo y tuberías ...
    
    // Dibujar jugador 1 en posición fija X
    if (jugador1.vivo) {
        dibujarPajaro(BIRD_X - 0.15f, jugador1.y, jugador1.r, jugador1.g, jugador1.b);
    }
    
    // Dibujar jugador 2 en posición fija X diferente
    if (jugador2.vivo) {
        dibujarPajaro(BIRD_X + 0.15f, jugador2.y, jugador2.r, jugador2.g, jugador2.b);
    }
    
    // HUD con puntajes
    dibujarHUD();
}
```

#### 📚 Referencias en el Proyecto
- **AppCamara.java**: Múltiples objetos (cubos) con estado independiente
- **AppLaberinto.java**: Gestión de estado de juego complejo

---

### 📌 Requerimiento 3: Incremento Progresivo de Velocidad

#### 🎯 Objetivo
Aumentar dificultad conforme aumenta el puntaje.

#### ✅ Especificaciones
- Velocidad de tuberías aumenta con el puntaje
- Frecuencia de spawn puede aumentar
- Límite superior razonable
- Mostrar nivel/velocidad en interfaz

#### 🔨 Estrategia de Implementación

**Opción A: Sistema de Niveles**
```java
private int nivelActual = 1;
private static final int PUNTOS_POR_NIVEL = 5;

private void calcularNivel() {
    int puntajeMaximo = Math.max(jugador1.puntaje, jugador2.puntaje);
    nivelActual = 1 + (puntajeMaximo / PUNTOS_POR_NIVEL);
    nivelActual = Math.min(nivelActual, 10); // Límite nivel 10
}

private float getVelocidadTuberias() {
    return VELOCIDAD_TUBERIAS + (nivelActual - 1) * 0.08f;
}

private float getTiempoEntreT uberias() {
    float reduccion = (nivelActual - 1) * 0.08f;
    return Math.max(0.8f, TIEMPO_ENTRE_TUBERIAS - reduccion);
}
```

**Opción B: Crecimiento Continuo**
```java
private float getVelocidadTuberias() {
    int puntajeMaximo = Math.max(jugador1.puntaje, jugador2.puntaje);
    float velocidad = VELOCIDAD_TUBERIAS + (puntajeMaximo * 0.015f);
    return Math.min(velocidad, 1.5f); // Límite superior
}
```

**Actualización del Título**
```java
private void actualizarTitulo() {
    String titulo = String.format(
        "Flappy Bird | P1: %d | P2: %d | Nivel: %d | Vel: %.2f",
        jugador1.puntaje, jugador2.puntaje, nivelActual, getVelocidadTuberias()
    );
    GLFW.glfwSetWindowTitle(window, titulo);
}
```

#### 📚 Referencias en el Proyecto
- **AppFlappyBird.java**: Variables `VELOCIDAD_TUBERIAS` y `TIEMPO_ENTRE_TUBERIAS`

---

### 📌 Requerimiento 4: Mejora de la Interfaz del Juego

#### 🎯 Objetivo
Mejorar presentación visual y experiencia de usuario.

#### ✅ Elementos Sugeridos

**A. Fondo Mejorado**
```java
private void dibujarFondo() {
    // Degradado de cielo (varios rectángulos con colores graduales)
    for (int i = 0; i < 10; i++) {
        float y = 1.0f - (i * 0.2f);
        float intensidad = 0.52f + (i * 0.03f);
        dibujarRect(0.0f, y, 2.0f, 0.2f, intensidad, 0.80f, 0.92f);
    }
    
    // Suelo
    dibujarRect(0.0f, -0.9f, 2.0f, 0.2f, 0.45f, 0.35f, 0.25f);
    
    // Nubes (círculos blancos)
    dibujarNube(-0.6f, 0.7f);
    dibujarNube(0.4f, 0.6f);
}

private void dibujarNube(float x, float y) {
    dibujarCirculo(x, y, 0.08f, 1.0f, 1.0f, 1.0f);
    dibujarCirculo(x + 0.06f, y, 0.06f, 1.0f, 1.0f, 1.0f);
    dibujarCirculo(x - 0.06f, y, 0.06f, 1.0f, 1.0f, 1.0f);
}
```

**B. Pantalla de Inicio**
```java
private enum EstadoJuego { MENU, JUGANDO, GAME_OVER }
private EstadoJuego estado = EstadoJuego.MENU;

private void renderMenu() {
    // Fondo
    dibujarFondo();
    
    // Título (rectángulos formando letras o banner)
    dibujarRect(0.0f, 0.3f, 0.8f, 0.15f, 0.98f, 0.85f, 0.20f);
    
    // Instrucciones (rectángulos de colores)
    dibujarRect(-0.3f, -0.1f, 0.5f, 0.08f, 0.2f, 0.8f, 0.4f);
    dibujarRect(0.3f, -0.1f, 0.5f, 0.08f, 0.4f, 0.6f, 0.9f);
}
```

**C. HUD con Puntajes**
```java
private void dibujarHUD() {
    // Panel jugador 1 (esquina superior izquierda)
    dibujarRect(-0.85f, 0.85f, 0.25f, 0.12f, 0.15f, 0.15f, 0.18f);
    // Indicador de color del jugador
    dibujarRect(-0.92f, 0.85f, 0.05f, 0.05f, jugador1.r, jugador1.g, jugador1.b);
    
    // Panel jugador 2 (esquina superior derecha)
    dibujarRect(0.85f, 0.85f, 0.25f, 0.12f, 0.15f, 0.15f, 0.18f);
    dibujarRect(0.92f, 0.85f, 0.05f, 0.05f, jugador2.r, jugador2.g, jugador2.b);
    
    // Números con rectángulos (estilo 7-segmentos)
    dibujarNumero(jugador1.puntaje, -0.85f, 0.85f);
    dibujarNumero(jugador2.puntaje, 0.85f, 0.85f);
}
```

**D. Efectos Visuales**
```java
// Parallax simple
private float offsetFondo = 0.0f;

private void actualizar(float dt) {
    offsetFondo -= getVelocidadTuberias() * dt * 0.3f; // Más lento que tuberías
    if (offsetFondo < -2.0f) offsetFondo += 2.0f;
}

// Parpadeo al morir
private void dibujarPajaro(Jugador j) {
    if (!j.vivo) {
        // Parpadeo cada 0.2 segundos
        if (((int)(GLFW.glfwGetTime() * 5)) % 2 == 0) {
            return; // No dibujar
        }
    }
    // ... dibujo normal ...
}
```

**E. Sonido (Opcional con javax.sound)**
```java
import javax.sound.sampled.*;

private void reproducirSonido(String archivo) {
    try {
        AudioInputStream audio = AudioSystem.getAudioInputStream(
            new File(archivo)
        );
        Clip clip = AudioSystem.getClip();
        clip.open(audio);
        clip.start();
    } catch (Exception e) {
        System.err.println("Error reproduciendo sonido: " + e.getMessage());
    }
}

// Uso
if (salto) {
    reproducirSonido("salto.wav");
}
```

#### 📚 Referencias en el Proyecto
- **App3D.java**: Múltiples objetos coloreados
- **AppLaberinto.java**: Renderizado de escena compleja con múltiples elementos

---


## 🛠️ Funciones y Métodos Clave Ya Implementados

### 📐 Geometría y Rendering

| Función | Descripción | Parámetros | Uso |
|---------|-------------|------------|-----|
| `crearQuadBase()` | Crea rectángulo unitario reutilizable | - | Base para todos los rectángulos |
| `dibujarRect()` | Dibuja rectángulo con transformación | x, y, ancho, alto, r, g, b | Pájaro, tuberías, UI |
| `GL20.glUniform2f()` | Envía vec2 al shader | location, x, y | Offset, escala |
| `GL20.glUniform3f()` | Envía vec3 al shader | location, x, y, z | Color, rotación |
| `GL11.glDrawArrays()` | Dibuja primitivas | modo, inicio, count | `GL_TRIANGLES`, `GL_TRIANGLE_FAN` |

### 🎮 Física y Lógica

| Función | Descripción | Variables Clave |
|---------|-------------|-----------------|
| `actualizar(dt)` | Loop de física y lógica | `birdY`, `birdVelY`, `timerSpawn` |
| `procesarInput()` | Captura teclado con flancos | `prevSpace`, `prevR` |
| `colisionaConTuberia()` | AABB 2D | Compara bordes del pájaro vs tubería |
| `spawnTuberia()` | Genera obstáculo | `random.nextFloat()` para gap |
| `resetGame()` | Reinicia partida | Limpia tuberías, resetea puntaje |

### ⏱️ Tiempo y Animación

```java
// Delta time (ya implementado en loop())
float ultimoTiempo = (float) GLFW.glfwGetTime();
float dt = ahora - ultimoTiempo;

// Limitar dt para evitar saltos grandes
if (dt > 0.033f) dt = 0.033f;
```

---

## 📝 Plan de Implementación Sugerido

### Fase 1: Preparación (1-2 horas)
1. ✅ Leer y entender `AppFlappyBird.java` completamente
2. ✅ Revisar ejemplos de referencia (`App3D.java`, `AppCamara.java`)
3. ✅ Crear backup del código original
4. ✅ Compilar y ejecutar el proyecto base

### Fase 2: Requerimiento 1 - Pájaro Compuesto (3-4 horas)
1. Crear método `dibujarTriangulo()` similar a `dibujarRect()`
2. Crear método `dibujarCirculo()` usando `GL_TRIANGLE_FAN`
3. Implementar `dibujarPajaro()` con todas las partes
4. Agregar animación de aleteo (opcional pero valorado)
5. Probar coherencia visual al subir/bajar

### Fase 3: Requerimiento 2 - Dos Jugadores (4-5 horas)
1. Crear clase `Jugador` con estado completo
2. Instanciar `jugador1` y `jugador2`
3. Modificar `procesarInput()` para controles independientes
4. Actualizar `actualizar()` para manejar ambos jugadores
5. Modificar `render()` para dibujar ambos pájaros
6. Implementar lógica de game over conjunto
7. Probar colisiones independientes

### Fase 4: Requerimiento 3 - Velocidad Progresiva (2-3 horas)
1. Implementar sistema de niveles o crecimiento continuo
2. Modificar velocidad de tuberías según puntaje
3. Ajustar frecuencia de spawn
4. Definir límites superiores
5. Actualizar título de ventana con información
6. Balancear dificultad (playtesting)

### Fase 5: Requerimiento 4 - Interfaz Mejorada (5-6 horas)
1. Implementar fondo con degradado
2. Agregar nubes o elementos decorativos
3. Crear pantalla de inicio (enum `EstadoJuego`)
4. Implementar HUD con puntajes visuales
5. Agregar efectos (parallax, parpadeo)
6. **(Opcional)** Integrar sonidos con `javax.sound`
7. Pulir estética general

### Fase 6: Testing y Refinamiento (2-3 horas)
1. Probar todos los requerimientos
2. Verificar colisiones precisas
3. Ajustar balance de dificultad
4. Optimizar rendimiento si es necesario
5. Preparar defensa (explicar código en vivo)

**Tiempo Total Estimado**: 17-23 horas

---

## 🚀 Comandos de Compilación y Ejecución

### Compilar el Proyecto
```bash
cd "c:\Users\contr\Desktop\prog grafica\opengl-java-class\opengl-java-class"
mvn compile
```

### Ejecutar AppFlappyBird
```bash
mvn exec:exec -DmainClass=com.graphics.AppFlappyBird
```

### Limpiar y Recompilar
```bash
mvn clean compile exec:exec -DmainClass=com.graphics.AppFlappyBird
```

### Ejecutar Otros Ejemplos (Referencia)
```bash
# Triángulo básico
mvn exec:exec -DmainClass=com.graphics.App

# Movimiento con teclado
mvn exec:exec -DmainClass=com.graphics.AppMovimientoTeclado

# Cubo 3D
mvn exec:exec -DmainClass=com.graphics.App3D

# Cámara first-person
mvn exec:exec -DmainClass=com.graphics.AppCamara

# Laberinto con colisiones
mvn exec:exec -DmainClass=com.graphics.AppLaberinto
```

---

## 📚 Recursos de Aprendizaje

### Conceptos Clave de OpenGL

#### 1. **Uniforms**
Variables globales del shader que se pueden cambiar desde CPU:
```java
// Declaración en shader (GLSL)
uniform vec2 uOffset;
uniform vec3 uColor;

// Uso desde Java
int location = GL20.glGetUniformLocation(programa, "uOffset");
GL20.glUniform2f(location, x, y);
```

#### 2. **Primitivas de OpenGL**
```java
GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);      // Triángulos independientes
GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 32);  // Abanico (círculos)
GL11.glDrawArrays(GL11.GL_LINES, 0, 2);          // Líneas
```

#### 3. **Coordenadas NDC (Normalized Device Coordinates)**
- Rango: -1.0 a 1.0 en X e Y
- Centro: (0, 0)
- Esquinas: (-1, -1) abajo-izquierda, (1, 1) arriba-derecha

#### 4. **VAO/VBO**
- **VBO**: Buffer con datos de vértices en GPU
- **VAO**: Configuración de cómo leer el VBO
- Se crean una vez, se reutilizan muchas veces

### Ejemplos de Código Útiles

#### Crear Círculo con Triangle Fan
```java
private void crearCirculo(int segmentos) {
    float[] vertices = new float[(segmentos + 2) * 3];
    vertices[0] = 0.0f; // Centro X
    vertices[1] = 0.0f; // Centro Y
    vertices[2] = 0.0f; // Centro Z
    
    for (int i = 0; i <= segmentos; i++) {
        float angulo = (float) (2.0 * Math.PI * i / segmentos);
        vertices[(i + 1) * 3 + 0] = (float) Math.cos(angulo) * 0.5f;
        vertices[(i + 1) * 3 + 1] = (float) Math.sin(angulo) * 0.5f;
        vertices[(i + 1) * 3 + 2] = 0.0f;
    }
    
    // Subir a VBO y dibujar con GL_TRIANGLE_FAN
}
```

#### Detección de Flancos (Edge Detection)
```java
// Evita múltiples saltos con una sola pulsación
boolean teclaAhora = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
if (teclaAhora && !teclaPrev) {
    // Acción solo en el momento de presionar
    saltar();
}
teclaPrev = teclaAhora;
```

#### Colisión AABB 2D
```java
boolean colisiona(float x1, float y1, float w1, float h1,
                  float x2, float y2, float w2, float h2) {
    float left1 = x1 - w1/2, right1 = x1 + w1/2;
    float bottom1 = y1 - h1/2, top1 = y1 + h1/2;
    float left2 = x2 - w2/2, right2 = x2 + w2/2;
    float bottom2 = y2 - h2/2, top2 = y2 + h2/2;
    
    return right1 > left2 && left1 < right2 &&
           top1 > bottom2 && bottom1 < top2;
}
```

---

## ⚠️ Consideraciones Importantes

### 🔴 Errores Comunes a Evitar

1. **No usar Delta Time**
   - ❌ `posicion += velocidad;`
   - ✅ `posicion += velocidad * deltaTime;`

2. **Olvidar Activar Programa/VAO**
   ```java
   GL20.glUseProgram(programa);  // Siempre antes de uniforms
   GL30.glBindVertexArray(vao);  // Siempre antes de draw
   ```

3. **Uniforms No Encontrados**
   - Verificar que el nombre coincida exactamente con el shader
   - Verificar que el uniform se use en el shader (si no, se optimiza)

4. **Colisiones Imprecisas**
   - Usar márgenes de error pequeños
   - Considerar el centro vs las esquinas de los objetos

5. **Rendimiento**
   - No crear VAO/VBO en cada frame
   - Reutilizar geometría con uniforms

### 🟢 Buenas Prácticas

1. **Organización del Código**
   ```java
   // Agrupar por funcionalidad
   // ===== GEOMETRÍA =====
   private void crearQuadBase() { }
   private void crearCirculo() { }
   
   // ===== RENDERING =====
   private void dibujarPajaro() { }
   private void dibujarTuberia() { }
   
   // ===== LÓGICA =====
   private void actualizar() { }
   private void procesarInput() { }
   ```

2. **Constantes Configurables**
   ```java
   private static final float GRAVEDAD = -1.9f;
   private static final float IMPULSO_SALTO = 0.85f;
   // Fácil de ajustar para balanceo
   ```

3. **Comentarios Útiles**
   ```java
   // Explicar el "por qué", no el "qué"
   // Limitar dt para evitar túnel de colisión en frames lentos
   if (dt > 0.033f) dt = 0.033f;
   ```

4. **Testing Incremental**
   - Implementar un requerimiento a la vez
   - Probar después de cada cambio significativo
   - Usar `System.out.println()` para debug

---

## 🎓 Preparación para la Defensa

### Conceptos que Debes Dominar

1. **Pipeline Gráfico**
   - Vertex Shader → Rasterización → Fragment Shader
   - Qué hace cada etapa

2. **Uniforms vs Atributos**
   - Uniforms: iguales para todos los vértices
   - Atributos: diferentes por vértice

3. **Transformaciones**
   - Traslación: `posicion + offset`
   - Escala: `posicion * escala`
   - Rotación: matrices de rotación (si implementas)

4. **Gestión de Estado**
   - Cómo se maneja el estado de cada jugador
   - Cuándo termina el juego

5. **Colisiones**
   - Explicar AABB
   - Por qué funciona para este juego

### Modificaciones en Vivo Posibles

- Cambiar color de un jugador
- Ajustar velocidad de tuberías
- Modificar tamaño del gap
- Agregar una nueva parte al pájaro
- Cambiar controles de teclado
- Modificar física (gravedad, impulso)

### Preguntas Frecuentes

**P: ¿Por qué usar un solo quad base?**
R: Eficiencia. Un VBO/VAO reutilizado con uniforms es más rápido que crear múltiples buffers.

**P: ¿Qué es NDC?**
R: Normalized Device Coordinates. Sistema de coordenadas de -1 a 1 que OpenGL usa internamente.

**P: ¿Por qué necesitamos delta time?**
R: Para que el movimiento sea independiente de los FPS. Sin él, el juego iría más rápido en PCs potentes.

**P: ¿Cómo funciona la colisión AABB?**
R: Compara los bordes de dos rectángulos. Si hay overlap en X Y en Y simultáneamente, hay colisión.

---

## 📞 Recursos Adicionales

### Documentación Oficial
- [LWJGL Documentation](https://www.lwjgl.org/guide)
- [OpenGL Reference](https://www.khronos.org/opengl/wiki/)
- [GLSL Reference](https://www.khronos.org/opengl/wiki/OpenGL_Shading_Language)

### Tutoriales Recomendados
- [LearnOpenGL](https://learnopengl.com/) - Excelente para conceptos
- [LWJGL Tutorial](https://github.com/LWJGL/lwjgl3-wiki/wiki/2.1.-The-Basics) - Específico para Java

### Herramientas de Debug
```java
// Verificar errores de OpenGL
int error = GL11.glGetError();
if (error != GL11.GL_NO_ERROR) {
    System.err.println("OpenGL Error: " + error);
}

// Imprimir info de shader
String log = GL20.glGetShaderInfoLog(shader);
System.out.println("Shader Log: " + log);
```

---

## ✅ Checklist Final Antes de Entregar

### Funcionalidad
- [ ] Pájaro tiene al menos 5 partes geométricas distintas
- [ ] Animación de aleteo visible
- [ ] Dos jugadores con controles independientes
- [ ] Puntajes individuales visibles
- [ ] Game over cuando ambos mueren
- [ ] Velocidad aumenta progresivamente
- [ ] Nivel/velocidad mostrado en interfaz
- [ ] Fondo mejorado (degradado, nubes, etc.)
- [ ] Pantalla de inicio o game over
- [ ] HUD con información clara

### Código
- [ ] Código compilable sin errores
- [ ] Comentarios en secciones clave
- [ ] Constantes bien nombradas
- [ ] Sin código comentado innecesario
- [ ] Organización lógica de métodos

### Defensa
- [ ] Puedo explicar el pipeline gráfico
- [ ] Entiendo qué hacen los shaders
- [ ] Puedo modificar colores en vivo
- [ ] Puedo ajustar física en vivo
- [ ] Entiendo el sistema de colisiones

---

## 🎯 Conclusión

Este proyecto es una excelente oportunidad para demostrar comprensión de:
- **OpenGL moderno** (Core Profile 3.3)
- **Arquitectura de shaders** (Vertex + Fragment)
- **Gestión de estado de juego**
- **Física básica 2D**
- **Entrada de usuario**
- **Organización de código**

La base de `AppFlappyBird.java` ya proporciona un framework sólido. Los requerimientos son extensiones lógicas que demuestran dominio progresivo de conceptos gráficos.

**¡Éxito en tu parcial!** 🚀

---

*Documento creado para el Primer Parcial de Programación Gráfica*  
*Basado en análisis completo del proyecto opengl-java-class*  
*Fecha: 2026*
