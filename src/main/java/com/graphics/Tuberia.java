package com.graphics;

/**
 * Tuberia — Representa un obstáculo rectangular vertical en el juego.
 * 
 * Las tuberías se componen de dos secciones (arriba y abajo) con un gap
 * (hueco) en el centro por donde debe pasar el pájaro sin chocar.
 * 
 * Gráficamente, cada tubería se dibuja con:
 * - Sombra lateral (lado derecho, oscuro para dar profundidad)
 * - Cuerpo principal (verde oscuro)
 * - Borde claro izquierdo (efecto 3D básico)
 * - Tapa en el extremo del gap (zona de entrada)
 */
public class Tuberia {
   // ── Movimiento y posición ────────────────────────────────────────────
   public float x;                 // Posición horizontal de la tubería (avanza de derecha a izquierda)
   
   // ── Geometría del obstáculo ──────────────────────────────────────────
   public float gapCentroY;        // Centro vertical del hueco por donde pasa el pájaro
   
   // ── Control de puntuación ────────────────────────────────────────────
   public boolean puntuada1;       // true = pájaro P1 ya pasó y anotó punto
   public boolean puntuada2;       // true = pájaro P2 ya pasó y anotó punto
   public boolean puntuada3;       // true = pájaro P3 ya pasó y anotó punto
   /**
    * Constructor: crea una tubería en una posición X con un hueco aleatorio.
    * 
    * @param x         Posición horizontal inicial (típicamente 1.2, fuera a la derecha)
    * @param gap       Centro Y del hueco (varía cada tubería para aumentar dificultad)
    */
   public Tuberia(float x, float gap) {
      this.x = x;                  // Posición horizontal
      this.gapCentroY = gap;       // Centro Y del hueco (obstáculo)
   }
}