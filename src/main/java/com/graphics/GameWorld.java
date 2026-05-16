package com.graphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * GameWorld — Modelo completo del estado del juego.
 *
 * Responsabilidades:
 * - Mantener el estado de los dos pájaros, tuberías, nivel y puntaje.
 * - Ejecutar la simulación física y de colisiones cada frame.
 * - Coordinar el {@link ParticleSystem} y el {@link SoundManager}.
 * - NO conoce OpenGL ni GLFW — toda presentación queda en Renderer.
 *
 * Ciclo de uso:
 * 
 * <pre>
 * world.init(); // una sola vez al arrancar
 * world.reset(); // cada nueva partida
 * world.actualizar(dt); // cada frame
 * </pre>
 */
public class GameWorld {

   // ── Estado público (leído por Renderer) ──────────────────────────────────
   public Bird bird1, bird2;
   public final List<Tuberia> tuberias = new ArrayList<>();

   public boolean started;
   public boolean gameOver;
   public int maxPuntajeGlobal;

   // Offset de parallax — el Renderer los usa para desplazar capas visuales
   public float parallaxSuelo = 0f;
   public float parallaxMontanas = 0f;
   public float parallaxNubes = 0f;

   // ── Subsistemas ──────────────────────────────────────────────────────────
   public final ParticleSystem particulas = new ParticleSystem();
   private final SoundManager sonido = new SoundManager();

   // ── Estado privado ───────────────────────────────────────────────────────
   private float timerSpawn;
   private float flickerTimer1 = 0f; // parpadeo individual por pájaro
   private float flickerTimer2 = 0f;
   private final Random random = new Random();

   // Exponer flickerTimers al Renderer (sólo lectura)
   public float getFlickerTimer1() {
      return flickerTimer1;
   }

   public float getFlickerTimer2() {
      return flickerTimer2;
   }

   // ── Init / Reset ─────────────────────────────────────────────────────────

   /**
    * Inicializa los jugadores y subsistemas. Llamar UNA sola vez al arrancar.
    */
   public void init() {
      bird1 = new Bird(
            Constants.BIRD1_X,
            new float[] { 0.98f, 0.78f, 0.10f }, // cuerpo amarillo
            new float[] { 0.99f, 0.94f, 0.55f }, // panza amarillo claro
            new float[] { 0.90f, 0.55f, 0.05f }, // ala naranja
            new float[] { 1.00f, 0.42f, 0.10f }, // pico naranja-rojo
            "P1");
      bird2 = new Bird(
            Constants.BIRD2_X,
            new float[] { 0.15f, 0.65f, 0.95f }, // cuerpo azul
            new float[] { 0.70f, 0.90f, 1.00f }, // panza azul claro
            new float[] { 0.05f, 0.40f, 0.80f }, // ala azul oscuro
            new float[] { 1.00f, 0.42f, 0.10f }, // pico naranja-rojo
            "P2");
      reset();
   }

   /**
    * Reinicia el estado de la partida sin recriar objetos de subsistemas.
    */
   public void reset() {
      bird1.reset();
      bird2.reset();
      tuberias.clear();
      particulas.clear();
      timerSpawn = 0f;
      started = false;
      gameOver = false;
      flickerTimer1 = 0f;
      flickerTimer2 = 0f;
      parallaxSuelo = 0f;
      parallaxMontanas = 0f;
      parallaxNubes = 0f;
   }

   // ── Controles (llamados por AppFlappyBird) ───────────────────────────────

   public void saltarP1() {
      if (!bird1.alive)
         return;
      started = true;
      bird1.velY = Constants.IMPULSO_SALTO;
      bird1.tiempoAleteo = 0.25f;
      sonido.playJump();
   }

   public void saltarP2() {
      if (!bird2.alive)
         return;
      started = true;
      bird2.velY = Constants.IMPULSO_SALTO;
      bird2.tiempoAleteo = 0.25f;
      sonido.playJump();
   }

   // ── Dificultad ───────────────────────────────────────────────────────────

   /**
    * Nivel actual basado en el puntaje máximo acumulado entre partidas.
    * El nivel sube cada {@link Constants#PUNTOS_POR_NIVEL} puntos.
    */
   public int calcularNivel() {
      return 1 + (maxPuntajeGlobal / Constants.PUNTOS_POR_NIVEL);
   }

   /**
    * Velocidad actual de tuberías, con tope en {@link Constants#VELOCIDAD_MAX}.
    */
   public float velocidadActual() {
      return Math.min(
            Constants.VELOCIDAD_BASE + (calcularNivel() - 1) * Constants.INCREMENTO_VELOCIDAD,
            Constants.VELOCIDAD_MAX);
   }

   /**
    * Tiempo entre spawns de tuberías, con piso en {@link Constants#TIEMPO_MIN}.
    */
   public float tiempoSpawnActual() {
      return Math.max(
            Constants.TIEMPO_BASE - (calcularNivel() - 1) * Constants.DECREMENTO_TIEMPO,
            Constants.TIEMPO_MIN);
   }

   // ── Actualización principal ──────────────────────────────────────────────

   /**
    * Avanza la simulación un tick.
    *
    * @param dt Delta time en segundos (capeado externamente a 0.033 s).
    */
   public void actualizar(float dt) {
      if (!started)
         return;

      float vel = gameOver ? 0f : velocidadActual();

      // Parallax — siempre avanza mientras el juego está iniciado
      if (!gameOver) {
         parallaxSuelo = (parallaxSuelo - vel * dt * Constants.PARALLAX_SUELO) % 1.0f;
         parallaxMontanas = (parallaxMontanas - vel * dt * Constants.PARALLAX_MONTANAS_CERCA) % 2.0f;
         parallaxNubes = (parallaxNubes - vel * dt * Constants.PARALLAX_NUBES) % 2.0f;
      }

      // Física de pájaros
      actualizarBird(bird1, dt, vel, 1);
      actualizarBird(bird2, dt, vel, 2);

      // Partículas
      particulas.actualizar(dt);

      // Parpadeo (flicker) — cuenta regresiva independiente por pájaro
      if (flickerTimer1 > 0)
         flickerTimer1 = Math.max(0, flickerTimer1 - dt);
      if (flickerTimer2 > 0)
         flickerTimer2 = Math.max(0, flickerTimer2 - dt);

      if (gameOver)
         return;

      // Spawn de tuberías
      timerSpawn += dt;
      if (timerSpawn >= tiempoSpawnActual()) {
         timerSpawn = 0f;
         float gap = Constants.GAP_MIN_CENTRO
               + random.nextFloat() * (Constants.GAP_MAX_CENTRO - Constants.GAP_MIN_CENTRO);
         tuberias.add(new Tuberia(1.2f, gap));
      }

      // Mover y puntuar tuberías
      Iterator<Tuberia> it = tuberias.iterator();
      while (it.hasNext()) {
         Tuberia t = it.next();
         t.x -= vel * dt;
         puntarSiCorresponde(t, bird1, true);
         puntarSiCorresponde(t, bird2, false);
         if (t.x + Constants.TUBERIA_ANCHO * 0.5f < -1.3f)
            it.remove();
      }

      // ¿Ambos muertos?
      if (!bird1.alive && !bird2.alive) {
         gameOver = true;
         sonido.playGameOver();
      }
   }

   // ── Física individual de pájaro ──────────────────────────────────────────

   private void actualizarBird(Bird b, float dt, float vel, int id) {
      if (!b.alive) {
         // Pájaro muerto cae libremente (animación de caída)
         b.velY += Constants.GRAVEDAD * dt * 1.5f;
         b.birdY += b.velY * dt;
         return;
      }

      // Gravedad e integración
      b.velY += Constants.GRAVEDAD * dt;
      if (b.velY < Constants.VELOCIDAD_MAX_CAIDA)
         b.velY = Constants.VELOCIDAD_MAX_CAIDA;
      b.birdY += b.velY * dt;

      // Timer de aleteo (animación post-salto)
      if (b.tiempoAleteo > 0)
         b.tiempoAleteo -= dt;

      // Colisión con límites de pantalla
      float top = b.birdY + Constants.BIRD_ALTO * 0.5f;
      float bottom = b.birdY - Constants.BIRD_ALTO * 0.5f;
      if (top >= Constants.LIMITE_TECHO || bottom <= Constants.LIMITE_SUELO) {
         matarBird(b, id);
         return;
      }

      // Colisión con tuberías
      for (Tuberia t : tuberias) {
         if (colisionaConTuberia(b, t)) {
            matarBird(b, id);
            return;
         }
      }
   }

   private void matarBird(Bird b, int id) {
      b.alive = false;
      b.velY = 0.2f; // pequeño impulso hacia arriba al morir (rebote)

      // Emitir partículas del color del pájaro
      float[] c = b.colorCuerpo;
      particulas.emitirMuerte(b.birdX, b.birdY, c[0], c[1], c[2]);

      // Iniciar parpadeo
      if (id == 1)
         flickerTimer1 = Constants.FLICKER_DURACION;
      else
         flickerTimer2 = Constants.FLICKER_DURACION;
   }

   private void puntarSiCorresponde(Tuberia t, Bird b, boolean esP1) {
      if (!b.alive)
         return;
      boolean yaContada = esP1 ? t.puntuada1 : t.puntuada2;
      if (!yaContada && t.x + Constants.TUBERIA_ANCHO * 0.5f < b.birdX) {
         if (esP1)
            t.puntuada1 = true;
         else
            t.puntuada2 = true;
         b.puntaje++;

         int maxActual = Math.max(bird1.puntaje, bird2.puntaje);
         if (maxActual > maxPuntajeGlobal)
            maxPuntajeGlobal = maxActual;

         sonido.playScore();
      }
   }

   private boolean colisionaConTuberia(Bird b, Tuberia t) {
      float bL = b.birdX - Constants.BIRD_ANCHO * 0.5f;
      float bR = b.birdX + Constants.BIRD_ANCHO * 0.5f;
      float bB = b.birdY - Constants.BIRD_ALTO * 0.5f;
      float bT = b.birdY + Constants.BIRD_ALTO * 0.5f;
      float pL = t.x - Constants.TUBERIA_ANCHO * 0.5f;
      float pR = t.x + Constants.TUBERIA_ANCHO * 0.5f;

      if (bR <= pL || bL >= pR)
         return false;

      float gT = t.gapCentroY + Constants.GAP_ALTO * 0.5f;
      float gB = t.gapCentroY - Constants.GAP_ALTO * 0.5f;
      return bT > gT || bB < gB;
   }

   // ── Limpieza ─────────────────────────────────────────────────────────────

   public void shutdown() {
      sonido.shutdown();
   }
}