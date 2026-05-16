package com.graphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * GameWorld — Modelo completo del estado del juego (lógica pura, sin gráficos).
 *
 * Responsabilidades:
 * - Mantener estado de pájaros, tuberías, puntajes y nivel.
 * - Ejecutar simulación física (gravedad, colisiones) cada frame.
 * - Spawnear tuberías con dificultad progresiva.
 * - Coordinar ParticleSystem (partículas al morir) y SoundManager (audio).
 * - Detectar game over (ambos pájaros muertos).
 * - NO conoce OpenGL ni GLFW — presentación está en Renderer.
 *
 * Arquitectura:
 * - GameWorld es el "modelo" (MVC pattern).
 * - Renderer es la "vista" (lee GameWorld, dibuja).
 * - AppFlappyBird es el "controlador" (input → GameWorld).
 *
 * Ciclo de uso:
 * <pre>
 * world.init();         // Una sola vez al arrancar
 * world.reset();        // Cada nueva partida
 * world.actualizar(dt); // Cada frame (~0.016 a 0.033 segundos)
 * </pre>
 */
public class GameWorld {

   // ════════════════════════════════════════════════════════════════════════
   // ESTADO PÚBLICO (leído por Renderer para dibujar)
   // ════════════════════════════════════════════════════════════════════════
   
   public Bird bird1, bird2;                      // Los dos pájaros jugadores
   public final List<Tuberia> tuberias = new ArrayList<>();  // Obstáculos activos

   public boolean started;                        // true = partida comenzó (primer salto)
   public boolean gameOver;                       // true = ambos pájaros muertos
   public int maxPuntajeGlobal;                   // Máximo puntaje histórico (para nivel)

   // ── Parallax para capas visuales (Renderer desplaza fondos según estos) ──
   public float parallaxSuelo = 0f;               // Offset del suelo (se mueve rápido)
   public float parallaxMontanas = 0f;            // Offset de montañas cercanas
   public float parallaxNubes = 0f;               // Offset de nubes (se mueve lento)

   // ════════════════════════════════════════════════════════════════════════
   // SUBSISTEMAS
   // ════════════════════════════════════════════════════════════════════════
   
   public final ParticleSystem particulas = new ParticleSystem();  // Explosiones al morir
   private final SoundManager sonido = new SoundManager();         // Audio sint​éetico

   // ════════════════════════════════════════════════════════════════════════
   // ESTADO PRIVADO (lógica interna del mundo)
   // ════════════════════════════════════════════════════════════════════════
   
   private float timerSpawn;                      // Contador para spawning de tuberías
   private float flickerTimer1 = 0f;              // Parpadeo del pájaro P1 al morir
   private float flickerTimer2 = 0f;              // Parpadeo del pájaro P2 al morir
   private final Random random = new Random();    // Generador de números aleatorios


   // ════════════════════════════════════════════════════════════════════════
   // API: FLICKER (parpadeo)
   // ════════════════════════════════════════════════════════════════════════
   
   // Exponer timers de parpadeo al Renderer (solo lectura)
   public float getFlickerTimer1() {
      return flickerTimer1;
   }

   public float getFlickerTimer2() {
      return flickerTimer2;
   }

   // ════════════════════════════════════════════════════════════════════════
   // INIT / RESET (CICLO DE VIDA)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Inicializa los pájaros y subsistemas. Llamar UNA sola vez al arrancar.
    * 
    * Crea dos pájaros con colores distintivos:
    * - P1: amarillo (cuerpo) + naranja (ala)
    * - P2: azul (cuerpo) + azul oscuro (ala)
    * Ambos comparten el mismo pico (naranja-rojo)
    */
   public void init() {
      // ── Pájaro 1: Amarillo + Naranja ────────────────────────────────────
      bird1 = new Bird(
            Constants.BIRD1_X,
            new float[] { 0.98f, 0.78f, 0.10f }, // Cuerpo: amarillo brillante
            new float[] { 0.99f, 0.94f, 0.55f }, // Panza: amarillo claro
            new float[] { 0.90f, 0.55f, 0.05f }, // Ala: naranja
            new float[] { 1.00f, 0.42f, 0.10f }, // Pico: naranja-rojo
            "P1");  // Identificador
      
      // ── Pájaro 2: Azul + Azul Oscuro ───────────────────────────────────
      bird2 = new Bird(
            Constants.BIRD2_X,
            new float[] { 0.15f, 0.65f, 0.95f }, // Cuerpo: azul
            new float[] { 0.70f, 0.90f, 1.00f }, // Panza: azul claro (cyan)
            new float[] { 0.05f, 0.40f, 0.80f }, // Ala: azul oscuro
            new float[] { 1.00f, 0.42f, 0.10f }, // Pico: naranja-rojo (igual)
            "P2");  // Identificador
      
      reset();  // Inicializar estado de juego
   }

   /**
    * Reinicia el estado para una nueva partida.
    * Llamado al presionar SPACE/W para comenzar o R para reiniciar.
    * 
    * Resetea:
    * - Pájaros (posición, velocidad, vivos)
    * - Tuberías (limpia lista)
    * - Partículas (limpia explosiones previas)
    * - Estado de juego (started=false, gameOver=false)
    */
   public void reset() {
      bird1.reset();                 // Vuelve a posición inicial
      bird2.reset();                 // Vuelve a posición inicial
      tuberias.clear();              // Borra obstáculos
      particulas.clear();            // Borra explosiones
      timerSpawn = 0f;               // Reinicia contador de spawn
      started = false;               // Partida no comenzada
      gameOver = false;              // No es game over
      flickerTimer1 = 0f;            // Sin parpadeo
      flickerTimer2 = 0f;            // Sin parpadeo
      parallaxSuelo = 0f;            // Reset parallax
      parallaxMontanas = 0f;
      parallaxNubes = 0f;
   }

   // ════════════════════════════════════════════════════════════════════════
   // CONTROLES (llamados por AppFlappyBird desde input)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Hace que P1 salte.
    * Ejecutado cuando el usuario presiona SPACE.
    * 
    * Efecto:
    * - Si es el primer salto, inicia la partida (started = true)
    * - Si el pájaro está muerto, no hacer nada
    * - Asignar velocidad hacia arriba
    * - Iniciar animación de aleteo
    * - Reproducir sonido
    */
   public void saltarP1() {
      if (!bird1.alive)
         return;  // Pájaro muerto no puede saltar
      started = true;               // Primera acción: comienza el juego
      bird1.velY = Constants.IMPULSO_SALTO;  // Impulso hacia arriba
      bird1.tiempoAleteo = 0.25f;   // Animar las alas durante 0.25 seg
      sonido.playJump();            // Sonido de salto
   }

   /**
    * Hace que P2 salte.
    * Ejecutado cuando el usuario presiona W o ARRIBA.
    * Idéntico a saltarP1() pero para el pájaro 2.
    */
   public void saltarP2() {
      if (!bird2.alive)
         return;
      started = true;
      bird2.velY = Constants.IMPULSO_SALTO;
      bird2.tiempoAleteo = 0.25f;
      sonido.playJump();
   }

   // ════════════════════════════════════════════════════════════════════════
   // DIFICULTAD (progresión según puntaje)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Calcula el nivel actual basado en puntaje máximo histórico.
    * Sube un nivel cada {@link Constants#PUNTOS_POR_NIVEL} puntos.
    * 
    * Fórmula: nivel = 1 + (maxPuntajeGlobal / PUNTOS_POR_NIVEL)
    * 
    * Ejemplos:
    * - maxPuntaje = 0-4  → nivel 1
    * - maxPuntaje = 5-9  → nivel 2
    * - maxPuntaje = 10-14 → nivel 3
    */
   public int calcularNivel() {
      return 1 + (maxPuntajeGlobal / Constants.PUNTOS_POR_NIVEL);
   }

   /**
    * Calcula la velocidad actual de tuberías según el nivel.
    * Aumenta {@link Constants#INCREMENTO_VELOCIDAD} por cada nivel.
    * Tope en {@link Constants#VELOCIDAD_MAX} para no ser imposible.
    * 
    * Fórmula: vel = VELOCIDAD_BASE + (nivel-1) * INCREMENTO_VELOCIDAD
    * Limitada a: VELOCIDAD_MAX
    */
   public float velocidadActual() {
      return Math.min(
            Constants.VELOCIDAD_BASE + (calcularNivel() - 1) * Constants.INCREMENTO_VELOCIDAD,
            Constants.VELOCIDAD_MAX);
   }

   /**
    * Calcula tiempo entre spawns de tuberías según el nivel.
    * Disminuye {@link Constants#DECREMENTO_TIEMPO} por cada nivel (más frecuentes).
    * Piso en {@link Constants#TIEMPO_MIN} para no ser imposible.
    * 
    * Fórmula: tiempo = TIEMPO_BASE - (nivel-1) * DECREMENTO_TIEMPO
    * Limitada a: TIEMPO_MIN (piso)
    */
   public float tiempoSpawnActual() {
      return Math.max(
            Constants.TIEMPO_BASE - (calcularNivel() - 1) * Constants.DECREMENTO_TIEMPO,
            Constants.TIEMPO_MIN);
   }

   // ════════════════════════════════════════════════════════════════════════
   // ACTUALIZACIÓN PRINCIPAL (física, colisiones, spawning)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Avanza la simulación un tick (un frame).
    * Llamado por AppFlappyBird.loop() cada frame con dt ≈ 0.016-0.033 segundos.
    * 
    * Operaciones cada frame:
    * 1. Si no started: solo actualizar parpadeos (esperar primer salto)
    * 2. Actualizar parallax (fondos se mueven)
    * 3. Actualizar física de cada pájaro
    * 4. Actualizar partículas
    * 5. Actualizar timers de parpadeo
    * 6. Si no gameOver:
    *    - Spawnear tuberías
    *    - Mover tuberías y chequear puntuación
    *    - Detectar colisiones
    * 7. Si ambos muertos: game over
    *
    * @param dt Delta time en segundos (capeado externamente a 0.033 s)
    */
   public void actualizar(float dt) {
      // Si la partida no ha comenzado (esperando primer salto), nada que actualizar
      if (!started)
         return;

      // ── Velocidad actual según nivel ─────────────────────────────────────
      float vel = gameOver ? 0f : velocidadActual();

      // ── Actualizar parallax (desplazamiento de fondos) ───────────────────
      if (!gameOver) {
         // Suelo se mueve rápido (parallax factor 0.90 = 90% de la velocidad)
         parallaxSuelo = (parallaxSuelo - vel * dt * Constants.PARALLAX_SUELO) % 1.0f;
         
         // Montañas cercanas se mueven a velocidad media
         parallaxMontanas = (parallaxMontanas - vel * dt * Constants.PARALLAX_MONTANAS_CERCA) % 2.0f;
         
         // Nubes se mueven muy lentamente (parallax factor 0.05 = 5%)
         parallaxNubes = (parallaxNubes - vel * dt * Constants.PARALLAX_NUBES) % 2.0f;
      }

      // ── Física de pájaros ────────────────────────────────────────────────
      actualizarBird(bird1, dt, vel, 1);  // P1 con id=1
      actualizarBird(bird2, dt, vel, 2);  // P2 con id=2

      // ── Actualizar partículas (caída por gravedad, desvanecimiento) ─────
      particulas.actualizar(dt);

      // ── Actualizar timers de parpadeo (flicker) ──────────────────────────
      // Cuentan regresivamente hacia 0 (cuando llegan a 0, el pájaro dejan de parpadear)
      if (flickerTimer1 > 0)
         flickerTimer1 = Math.max(0, flickerTimer1 - dt);
      if (flickerTimer2 > 0)
         flickerTimer2 = Math.max(0, flickerTimer2 - dt);

      // Si ya es game over, no continuar con lógica de juego
      if (gameOver)
         return;

      // ── Spawnear tuberías con dificultad progresiva ───────────────────────
      timerSpawn += dt;
      if (timerSpawn >= tiempoSpawnActual()) {
         timerSpawn = 0f;  // Reiniciar contador
         
         // Generación de altura aleatoria del gap (hueco)
         float gap = Constants.GAP_MIN_CENTRO
               + random.nextFloat() * (Constants.GAP_MAX_CENTRO - Constants.GAP_MIN_CENTRO);
         
         // Crear tubería en el lado derecho de la pantalla
         tuberias.add(new Tuberia(1.2f, gap));
      }

      // ── Mover tuberías y detectar puntuación ───────────────────────────────
      Iterator<Tuberia> it = tuberias.iterator();
      while (it.hasNext()) {
         Tuberia t = it.next();
         t.x -= vel * dt;  // Mover tubería hacia la izquierda
         
         // Chequear si P1 y P2 puntúan al pasar esta tubería
         puntarSiCorresponde(t, bird1, true);   // true = es P1
         puntarSiCorresponde(t, bird2, false);  // false = es P2
         
         // Eliminar tuberías que salieron de pantalla (a la izquierda)
         if (t.x + Constants.TUBERIA_ANCHO * 0.5f < -1.3f)
            it.remove();
      }

      // ── Chequear fin del juego ──────────────────────────────────────────
      if (!bird1.alive && !bird2.alive) {
         gameOver = true;  // Ambos pájaros muertos = fin
         sonido.playGameOver();  // Sonido final
      }
   }

   // ════════════════════════════════════════════════════════════════════════
   // FÍSICA DE PÁJARO INDIVIDUAL
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Actualiza la física de un pájaro individual cada frame.
    * 
    * Pasos:
    * 1. Si está muerto: cae libremente (animación post-mortem)
    * 2. Si está vivo:
    *    a. Aplicar gravedad: velY += GRAVEDAD * dt
    *    b. Limitar velocidad máxima de caída (no caer infinitamente rápido)
    *    c. Integrar posición: birdY += velY * dt
    *    d. Actualizar timer de aleteo
    *    e. Chequear colisiones con límites de pantalla (techo/suelo)
    *    f. Chequear colisiones con tuberías
    *
    * @param b    Pájaro a actualizar
    * @param dt   Delta time
    * @param vel  Velocidad actual de tuberías (para contexto)
    * @param id   Identificador (1 = bird1, 2 = bird2) para flickering
    */
   private void actualizarBird(Bird b, float dt, float vel, int id) {
      // ── Pájaro muerto: cae libremente ────────────────────────────────────
      if (!b.alive) {
         // Gravedad amplificada (1.5x) para caída más rápida post-mortem
         b.velY += Constants.GRAVEDAD * dt * 1.5f;
         b.birdY += b.velY * dt;
         return;  // Nada más que hacer
      }

      // ── Pájaro vivo: física normal ───────────────────────────────────────
      
      // Aplicar gravedad (acelera hacia abajo)
      b.velY += Constants.GRAVEDAD * dt;
      
      // Limitar velocidad máxima de caída (terminal velocity)
      // Sin esto, el pájaro cae muy rápido si está mucho tiempo sin saltar
      if (b.velY < Constants.VELOCIDAD_MAX_CAIDA)
         b.velY = Constants.VELOCIDAD_MAX_CAIDA;
      
      // Integrar velocidad → posición
      b.birdY += b.velY * dt;

      // Actualizar timer de aleteo (animación post-salto)
      // Cuando es > 0: alas se animan, cuando llega a 0 se detiene la animación
      if (b.tiempoAleteo > 0)
         b.tiempoAleteo -= dt;

      // ── Colisión con límites de pantalla ─────────────────────────────────
      // Pájaro tiene tamaño BIRD_ALTO × BIRD_ANCHO
      float top = b.birdY + Constants.BIRD_ALTO * 0.5f;      // Borde superior del pájaro
      float bottom = b.birdY - Constants.BIRD_ALTO * 0.5f;   // Borde inferior del pájaro
      
      // Si toca techo o suelo → muere
      if (top >= Constants.LIMITE_TECHO || bottom <= Constants.LIMITE_SUELO) {
         matarBird(b, id);
         return;  // No chequear más colisiones (ya está muerto)
      }

      // ── Colisión con tuberías ───────────────────────────────────────────
      for (Tuberia t : tuberias) {
         if (colisionaConTuberia(b, t)) {
            matarBird(b, id);
            return;  // Ya está muerto
         }
      }
   }

   /**
    * Mata un pájaro (lo marca como dead, emite partículas, inicia parpadeo).
    * 
    * Efectos visuales:
    * - El pájaro toma un pequeño impulso hacia arriba (rebote)
    * - Emite partículas del color del pájaro (explosión)
    * - Inicia efecto de parpadeo (flicker) por 1 segundo
    *
    * @param b  Pájaro a matar
    * @param id Identificador (1 o 2) para asociar timer de parpadeo
    */
   private void matarBird(Bird b, int id) {
      b.alive = false;
      b.velY = 0.2f;  // Pequeño impulso hacia arriba al morir (rebote visual)

      // Emitir partículas del color del pájaro (su cuerpo)
      float[] c = b.colorCuerpo;  // Obtener color del cuerpo
      particulas.emitirMuerte(b.birdX, b.birdY, c[0], c[1], c[2]);

      // Iniciar parpadeo (flicker) — el pájaro muerto parpadeará 1 segundo
      // En Renderer, si flickerTimer > 0, el pájaro se dibuja intermitentemente
      if (id == 1)
         flickerTimer1 = Constants.FLICKER_DURACION;
      else
         flickerTimer2 = Constants.FLICKER_DURACION;
   }

   /**
    * Chequea si un pájaro acaba de pasar una tubería y debe anotar punto.
    * 
    * Lógica:
    * - Solo si el pájaro está vivo
    * - Solo si la tubería no ha sido puntuada por este pájaro aún
    * - Punto se anota cuando el pájaro PASA el borde derecho de la tubería
    * - Actualizar máximo puntaje global (para calcular nivel)
    * - Reproducir sonido de punto
    *
    * @param t   Tubería a chequear
    * @param b   Pájaro
    * @param esP1 true si es P1, false si es P2
    */
   private void puntarSiCorresponde(Tuberia t, Bird b, boolean esP1) {
      // Si el pájaro está muerto, no puede anotar
      if (!b.alive)
         return;
      
      // Obtener flag de si esta tubería ya fue puntuada por este pájaro
      boolean yaContada = esP1 ? t.puntuada1 : t.puntuada2;
      if (!yaContada && t.x + Constants.TUBERIA_ANCHO * 0.5f < b.birdX) {
         // Marcar como puntuada
         if (esP1)
            t.puntuada1 = true;
         else
            t.puntuada2 = true;
         
         // Incrementar puntaje
         b.puntaje++;

         // Actualizar máximo puntaje global (para calcular nivel)
         int maxActual = Math.max(bird1.puntaje, bird2.puntaje);
         if (maxActual > maxPuntajeGlobal)
            maxPuntajeGlobal = maxActual;

         // Sonido de punto
         sonido.playScore();
      }
   }

   /**
    * Chequea si un pájaro colisiona con una tubería.
    * 
    * Colisión AABB (Axis-Aligned Bounding Box):
    * 1. Obtener bounding box del pájaro (izq, der, arriba, abajo)
    * 2. Obtener bounding box de la tubería
    * 3. Si no solapan en X → no hay colisión
    * 4. Si solapan en X:
    *    - Si el pájaro está en el gap (hueco) → sin colisión
    *    - Si toca tubería superior o inferior → colisión
    *
    * @param b Pájaro a chequear
    * @param t Tubería
    * @return true si hay colisión, false si no
    */
   private boolean colisionaConTuberia(Bird b, Tuberia t) {
      // Bounding box del pájaro
      float bL = b.birdX - Constants.BIRD_ANCHO * 0.5f;    // Izquierda
      float bR = b.birdX + Constants.BIRD_ANCHO * 0.5f;    // Derecha
      float bB = b.birdY - Constants.BIRD_ALTO * 0.5f;     // Abajo
      float bT = b.birdY + Constants.BIRD_ALTO * 0.5f;     // Arriba
      
      // Bounding box de la tubería
      float pL = t.x - Constants.TUBERIA_ANCHO * 0.5f;     // Izquierda
      float pR = t.x + Constants.TUBERIA_ANCHO * 0.5f;     // Derecha

      // Chequeo en eje X: si no solapan en X, no hay colisión
      if (bR <= pL || bL >= pR)
         return false;

      // Solapan en X, ahora chequear gap (hueco de paso)
      float gT = t.gapCentroY + Constants.GAP_ALTO * 0.5f;  // Techo del gap
      float gB = t.gapCentroY - Constants.GAP_ALTO * 0.5f;  // Piso del gap
      
      // Si el pájaro está completamente dentro del gap, sin colisión
      // Si toca tubería superior o inferior → colisión
      return bT > gT || bB < gB;
   }

   // ════════════════════════════════════════════════════════════════════════
   // LIMPIEZA
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Libera recursos (cerrar audio, etc.).
    * Llamado en AppFlappyBird.cleanup() antes de terminar.
    */
   public void shutdown() {
      sonido.shutdown();  // Cerrar pool de audio
   }
}