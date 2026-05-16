package com.graphics;

/**
 * Constants — Todas las constantes del juego en un único lugar.
 *
 * Organización:
 * 1. Ventana
 * 2. Pájaros
 * 3. Física
 * 4. Tuberías y niveles de dificultad
 * 5. Entorno visual (suelo, nubes, parallax)
 * 6. Partículas
 * 7. HUD / UI
 */
public final class Constants {

   private Constants() {
   } // Clase utilitaria — no instanciar

   // ── 1. Ventana ──────────────────────────────────────────────────────────
   public static final int ANCHO = 900;
   public static final int ALTO = 700;

   // ── 2. Pájaros ──────────────────────────────────────────────────────────
   public static final float BIRD1_X = -0.55f;
   public static final float BIRD2_X = -0.30f;
   public static final float BIRD_ANCHO = 0.10f;
   public static final float BIRD_ALTO = 0.10f;

   // ── 3. Física ───────────────────────────────────────────────────────────
   public static final float GRAVEDAD = -1.9f;
   public static final float IMPULSO_SALTO = 0.85f;
   public static final float VELOCIDAD_MAX_CAIDA = -1.8f;

   // Límites de colisión con bordes de pantalla
   public static final float LIMITE_TECHO = 0.73f; // justo debajo del HUD superior
   public static final float LIMITE_SUELO = -0.75f; // justo encima del suelo visual

   // ── 4. Tuberías y niveles ───────────────────────────────────────────────
   public static final float TUBERIA_ANCHO = 0.18f;
   public static final float GAP_ALTO = 0.48f;
   public static final float GAP_MIN_CENTRO = -0.38f;
   public static final float GAP_MAX_CENTRO = 0.38f;

   // Dificultad progresiva
   public static final int PUNTOS_POR_NIVEL = 5;
   public static final float VELOCIDAD_BASE = 0.62f;
   public static final float INCREMENTO_VELOCIDAD = 0.08f;
   public static final float VELOCIDAD_MAX = 1.40f;
   public static final float TIEMPO_BASE = 1.50f;
   public static final float DECREMENTO_TIEMPO = 0.10f;
   public static final float TIEMPO_MIN = 0.75f;

   // ── 5. Entorno visual ───────────────────────────────────────────────────

   // Suelo
   public static final float SUELO_Y = -0.82f; // centro Y del suelo
   public static final float SUELO_H = 0.36f; // alto del suelo
   // Borde superior del suelo (para colisión y render de hierba)
   public static final float SUELO_BORDE_TOP = SUELO_Y + SUELO_H * 0.5f;

   // Montañas (2 capas de profundidad)
   // Capa lejana — 5 montañas grandes, parallax lento
   public static final float[][] MONTANAS_LEJOS = {
         // { xCentro, ancho, alto }
         { -0.80f, 0.50f, 0.30f },
         { -0.30f, 0.40f, 0.25f },
         { 0.15f, 0.55f, 0.28f },
         { 0.60f, 0.45f, 0.22f },
         { 0.95f, 0.38f, 0.20f },
   };
   public static final float PARALLAX_MONTANAS_LEJOS = 0.06f; // factor de velocidad

   // Capa cercana — colinas más oscuras, parallax más rápido
   public static final float[][] MONTANAS_CERCA = {
         { -0.70f, 0.35f, 0.18f },
         { -0.20f, 0.28f, 0.14f },
         { 0.20f, 0.40f, 0.16f },
         { 0.65f, 0.32f, 0.12f },
         { 1.00f, 0.30f, 0.15f },
   };
   public static final float PARALLAX_MONTANAS_CERCA = 0.18f;

   // Nubes — { xInicial, y, ancho }
   public static final float[][] NUBES = {
         { -0.75f, 0.58f, 0.28f },
         { -0.20f, 0.65f, 0.20f },
         { 0.30f, 0.60f, 0.24f },
         { 0.75f, 0.68f, 0.18f },
         { 1.10f, 0.55f, 0.22f },
   };
   public static final float PARALLAX_NUBES = 0.05f; // muy lento (lejos)
   public static final float PARALLAX_SUELO = 0.90f; // rápido (cerca)

   // ── 6. Partículas ───────────────────────────────────────────────────────
   public static final int PARTICULAS_AL_MORIR = 14;
   public static final float PARTICULA_VEL_MIN = 0.30f;
   public static final float PARTICULA_VEL_MAX = 0.70f;
   public static final float PARTICULA_VIDA_MIN = 0.40f;
   public static final float PARTICULA_VIDA_MAX = 0.80f;
   public static final float PARTICULA_SIZE_MIN = 0.018f;
   public static final float PARTICULA_SIZE_MAX = 0.038f;

   // Duración del parpadeo del pájaro al morir (segundos)
   public static final float FLICKER_DURACION = 1.0f;
   public static final float FLICKER_INTERVALO = 0.07f; // cada cuánto alterna

   // ── 7. HUD / UI ─────────────────────────────────────────────────────────
   public static final float HUD_TOP_Y = 0.88f; // centro Y de la franja superior
   public static final float HUD_TOP_H = 0.24f; // alto de la franja
   public static final float HUD_SEP_Y = 0.76f; // línea separadora

   // Dígitos 7 segmentos — tamaño base
   public static final float DIGIT_W = 0.05f;
   public static final float DIGIT_H = 0.09f;
}