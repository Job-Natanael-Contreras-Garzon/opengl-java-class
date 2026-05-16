package com.graphics;

/**
 * Constants — Todas las constantes del juego en un único lugar.
 *
 * Organización:
 * 1. Ventana (resolución)
 * 2. Pájaros (posiciones iniciales, tamaño)
 * 3. Física (gravedad, salto, límites de pantalla)
 * 4. Tuberías y niveles de dificultad (velocidad, spawning)
 * 5. Entorno visual (suelo, nubes, montañas, parallax)
 * 6. Partículas (efectos al morir)
 * 7. HUD / UI (interfaz superior)
 */
public final class Constants {

      private Constants() {
      } // Clase utilitaria — no instanciar

      // ══════════════════════════════════════════════════════════════════════
      // 1. VENTANA
      // ══════════════════════════════════════════════════════════════════════
      public static final int ANCHO = 900; // Ancho en píxeles de la ventana
      public static final int ALTO = 700; // Alto en píxeles de la ventana

      // ══════════════════════════════════════════════════════════════════════
      // 2. PÁJAROS
      // ══════════════════════════════════════════════════════════════════════
      public static final float BIRD1_X = -0.55f; // Posición X inicial de P1 (a la izquierda)
      public static final float BIRD2_X = -0.30f; // Posición X inicial de P2 (más a la derecha que P1)
      public static final float BIRD3_X = -0.15f; // Posición X inicial de P3 es el pajaro verde
      public static final float BIRD_ANCHO = 0.10f; // Ancho del pájaro en coordenadas de pantalla [-1, 1]
      public static final float BIRD_ALTO = 0.10f; // Alto del pájaro (cuadrado aproximadamente)

      // ══════════════════════════════════════════════════════════════════════
      // 3. FÍSICA
      // ══════════════════════════════════════════════════════════════════════
      public static final float GRAVEDAD = -1.9f; // Aceleración hacia abajo (-Y) cada segundo
      public static final float IMPULSO_SALTO = 0.85f; // Velocidad vertical al saltar (hacia arriba)
      public static final float VELOCIDAD_MAX_CAIDA = -1.8f; // Máxima velocidad de caída permitida (límite)

      // ── Límites de colisión con bordes de pantalla ─────────────────────
      public static final float LIMITE_TECHO = 0.73f; // Si birdY > esto = choque con techo
      public static final float LIMITE_SUELO = -0.75f; // Si birdY < esto = choque con suelo (game over)

      // ══════════════════════════════════════════════════════════════════════
      // 4. TUBERÍAS Y NIVELES (DIFICULTAD PROGRESIVA)
      // ══════════════════════════════════════════════════════════════════════

      // ── Dimensiones del obstáculo ────────────────────────────────────────
      public static final float TUBERIA_ANCHO = 0.18f; // Ancho del tubo (verde oscuro dibujado)
      public static final float GAP_ALTO = 0.48f; // Alto del hueco por donde pasa el pájaro

      // ── Rango del hueco (varía entre tuberías para mayor dificultad) ─────
      public static final float GAP_MIN_CENTRO = -0.38f; // Mínimo Y del centro del hueco
      public static final float GAP_MAX_CENTRO = 0.38f; // Máximo Y del centro del hueco

      // ── Dificultad progresiva (aumenta según el puntaje global) ─────────
      public static final int PUNTOS_POR_NIVEL = 5; // Cada 5 puntos sube el nivel
      public static final float VELOCIDAD_BASE = 0.62f; // Velocidad inicial de tuberías (nv1)
      public static final float INCREMENTO_VELOCIDAD = 0.08f; // Velocidad extra por nivel (+0.08 por nivel)
      public static final float VELOCIDAD_MAX = 1.40f; // Velocidad máxima (tope para no ser imposible)

      public static final float TIEMPO_BASE = 1.50f; // Segundos entre spawns de tuberías (nv1)
      public static final float DECREMENTO_TIEMPO = 0.10f; // Cada nivel: tiempo -= 0.10 (tuberías más frecuentes)
      public static final float TIEMPO_MIN = 0.75f; // Tiempo mínimo entre spawns (tope)

      // ══════════════════════════════════════════════════════════════════════
      // 5. ENTORNO VISUAL
      // ══════════════════════════════════════════════════════════════════════

      // ── Suelo ────────────────────────────────────────────────────────────
      public static final float SUELO_Y = -0.82f; // Centro Y del suelo (abajo de la pantalla)
      public static final float SUELO_H = 0.36f; // Alto total del suelo
      // Borde superior del suelo (para colisión y render de hierba decorativa)
      public static final float SUELO_BORDE_TOP = SUELO_Y + SUELO_H * 0.5f;

      // ── Montañas (2 capas de profundidad con parallax) ──────────────────
      // Capa lejana — 5 montañas grandes, se mueven lentamente (parallax = 0.06)
      public static final float[][] MONTANAS_LEJOS = {
                  // { xCentro, ancho, alto }
                  { -0.80f, 0.50f, 0.30f }, // Montaña 1 (izquierda)
                  { -0.30f, 0.40f, 0.25f }, // Montaña 2
                  { 0.15f, 0.55f, 0.28f }, // Montaña 3 (centro)
                  { 0.60f, 0.45f, 0.22f }, // Montaña 4
                  { 0.95f, 0.38f, 0.20f }, // Montaña 5 (derecha)
      };
      public static final float PARALLAX_MONTANAS_LEJOS = 0.06f; // Factor de velocidad (lento = lejos)

      // Capa cercana — colinas más oscuras (paralell más rápido = 0.18)
      public static final float[][] MONTANAS_CERCA = {
                  { -0.70f, 0.35f, 0.18f },
                  { -0.20f, 0.28f, 0.14f },
                  { 0.20f, 0.40f, 0.16f },
                  { 0.65f, 0.32f, 0.12f },
                  { 1.00f, 0.30f, 0.15f },
      };
      public static final float PARALLAX_MONTANAS_CERCA = 0.18f;

      // ── Nubes (decorativas, efecto de profundidad) ──────────────────────
      // { xInicial, y, ancho }
      public static final float[][] NUBES = {
                  { -0.75f, 0.58f, 0.28f },
                  { -0.20f, 0.65f, 0.20f },
                  { 0.30f, 0.60f, 0.24f },
                  { 0.75f, 0.68f, 0.18f },
                  { 1.10f, 0.55f, 0.22f },
      };
      public static final float PARALLAX_NUBES = 0.05f; // Muy lento (parecen muy lejanas)
      public static final float PARALLAX_SUELO = 0.90f; // Rápido (está cerca, se mueve mucho)

      // ══════════════════════════════════════════════════════════════════════
      // 6. PARTÍCULAS (EFECTOS VISUALES AL MORIR)
      // ══════════════════════════════════════════════════════════════════════
      public static final int PARTICULAS_AL_MORIR = 14; // Número de partículas al choque
      public static final float PARTICULA_VEL_MIN = 0.30f; // Velocidad mínima de expansión
      public static final float PARTICULA_VEL_MAX = 0.70f; // Velocidad máxima de expansión
      public static final float PARTICULA_VIDA_MIN = 0.40f; // Duración mínima (segundos)
      public static final float PARTICULA_VIDA_MAX = 0.80f; // Duración máxima (segundos)
      public static final float PARTICULA_SIZE_MIN = 0.018f; // Tamaño mínimo
      public static final float PARTICULA_SIZE_MAX = 0.038f; // Tamaño máximo

      // ── Parpadeo (flicker) cuando el pájaro muere ──────────────────────
      public static final float FLICKER_DURACION = 1.0f; // Duración total del efecto de parpadeo
      public static final float FLICKER_INTERVALO = 0.07f; // Velocidad de parpadeo (cada 0.07 segundos)

      // ══════════════════════════════════════════════════════════════════════
      // 7. HUD / UI (INTERFAZ GRÁFICA)
      // ══════════════════════════════════════════════════════════════════════
      public static final float HUD_TOP_Y = 0.88f; // Centro Y de la franja superior (donde va el puntaje)
      public static final float HUD_TOP_H = 0.24f; // Alto de la franja del HUD
      public static final float HUD_SEP_Y = 0.76f; // Línea divisoria entre HUD y área de juego

      // ── Dígitos 7 segmentos (puntajes digitales) ──────────────────────
      public static final float DIGIT_W = 0.05f; // Ancho base de cada dígito
      public static final float DIGIT_H = 0.09f; // Alto base de cada dígito

      public static final int Puntaje_Maximo = 10; //morir al llegar a los 10 puntos
}