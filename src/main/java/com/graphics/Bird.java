package com.graphics;

/**
 * Bird — Representa un pájaro jugador con propiedades gráficas y físicas.
 * 
 * Esta clase es un DTO que almacena:
 * - Posición (X, Y) y velocidad vertical del pájaro
 * - Estado del pájaro (vivo/muerto) y puntaje
 * - Colores RGB personalizados para cada parte: cuerpo, panza, ala, pico
 * - Animación del aleteo (tiempoAleteo se usa para animar las alas post-salto)
 * 
 * El Renderer lee estos datos para dibujar el pájaro cada frame.
 */
public class Bird {
   // ── Posición y movimiento ────────────────────────────────────────────
   public float birdX;              // Posición actual X en el eje horizontal
   public final float startX;       // Posición inicial X (para resetear)
   public float birdY;              // Posición actual Y en el eje vertical
   public float velY;               // Velocidad vertical (positiva = arriba, negativa = caída)
   
   // ── Estado del pájaro ────────────────────────────────────────────────
   public boolean alive;            // true = vivo y jugando, false = chocó
   public int puntaje;              // Puntos anotados en esta partida
   public float tiempoAleteo;       // Contador para animar las alas tras saltar (>0 = aleteo activo)

   // ── Colores gráficos (RGB 0..1 cada componente) ──────────────────────
   public final float[] colorCuerpo; // Color del cuerpo principal
   public final float[] colorPanza;  // Color del vientre (zona baja)
   public final float[] colorAla;    // Color de las alas (trasera y delantera)
   public final float[] colorPico;   // Color del pico
   public final String nombre;       // Nombre del jugador ("P1" o "P2")

   /**
    * Constructor: inicializa un pájaro con posición y colores personalizados.
    * 
    * @param x        Posición inicial X
    * @param cuerpo   Color RGB del cuerpo [r, g, b]
    * @param panza    Color RGB de la panza [r, g, b]
    * @param ala      Color RGB del ala [r, g, b]
    * @param pico     Color RGB del pico [r, g, b]
    * @param nombre   Identificador ("P1", "P2", etc.)
    */
   public Bird(float x, float[] cuerpo, float[] panza, float[] ala, float[] pico, String nombre) {
      this.startX = x;
      this.birdX = x;
      this.colorCuerpo = cuerpo;
      this.colorPanza = panza;
      this.colorAla = ala;
      this.colorPico = pico;
      this.nombre = nombre;
   }

   /**
    * Reinicia el estado del pájaro para una nueva partida:
    * - Vuelve a la posición inicial
    * - Resetea velocidad y animación
    * - Marca como vivo
    * - Borra el puntaje
    */
   public void reset() {
      birdX = startX;              // Vuelve a la posición inicial X
      birdY = 0.0f;                // Posición vertical neutra (centro)
      velY = 0.0f;                 // Sin velocidad vertical
      alive = true;                // Marca como vivo
      puntaje = 0;                 // Reinicia puntaje
      tiempoAleteo = 0f;           // Sin animación de aleteo
   }
}