package com.graphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * ParticleSystem — Sistema de partículas desacoplado del renderer y del mundo.
 *
 * Responsabilidades:
 * - Emitir ráfagas de partículas en una posición (ej: muerte de pájaro).
 * - Actualizar física de cada partícula (posición, gravedad, fade).
 * - Exponer la lista de partículas activas al Renderer para que las dibuje.
 *
 * Diseño:
 * - Clase interna {@link Particle} es un DTO puro (sólo datos, sin lógica).
 * - ParticleSystem no conoce OpenGL; el Renderer lee {@link #getParticles()}.
 * - Cada partícula cae por gravedad y se desvanece gradualmente (fade).
 */
public class ParticleSystem {

    // ════════════════════════════════════════════════════════════════════════
    // CLASE INTERNA: PARTÍCULA INDIVIDUAL
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Particle — Representa una pequeña partícula volante (fragmento del pájaro al chocar).
     * 
     * Cada partícula contiene:
     * - Posición (x, y) y velocidad (vx, vy)
     * - Duración de vida (se desvanece gradualmente)
     * - Tamaño y color (RGB)
     */
    public static class Particle {
        public float x, y;          // Posición actual en pantalla
        public float vx, vy;        // Velocidad (se reduce por gravedad)
        public float vida;          // Vida restante en segundos
        public float maxVida;       // Vida inicial (para calcular fade)
        public float size;          // Tamaño de la partícula
        public float r, g, b;       // Color RGB de la partícula

        /**
         * Constructor: crea una partícula con posición, velocidad, duración y color.
         * 
         * @param x     Posición inicial X
         * @param y     Posición inicial Y
         * @param vx    Velocidad horizontal (expansión radial)
         * @param vy    Velocidad vertical (expansión radial)
         * @param vida  Duración en segundos
         * @param size  Tamaño de la partícula
         * @param r     Color rojo [0..1]
         * @param g     Color verde [0..1]
         * @param b     Color azul [0..1]
         */
        Particle(float x, float y, float vx, float vy,
                float vida, float size, float r, float g, float b) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.vida = vida;
            this.maxVida = vida;
            this.size = size;
            this.r = r;
            this.g = g;
            this.b = b;
        }

        /** 
         * Calcula el alpha (opacidad) actual de la partícula.
         * Devuelve fracción [1→0]: partícula nueva = opaca, partícula vieja = transparente.
         * Se usa para desvanecimiento gradual (fade out).
         */
        public float alpha() {
            return vida / maxVida;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ESTADO DEL SISTEMA
    // ════════════════════════════════════════════════════════════════════════
    
    // Gravedad que afecta a las partículas (caen lentamente)
    private static final float GRAVEDAD_PARTICULA = -0.90f;

    // Lista de partículas activas (el Renderer la lee para dibujarlas)
    private final List<Particle> particles = new ArrayList<>();
    
    // Generador de números aleatorios para variación
    private final Random rng = new Random();

    // ════════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Emite una explosión de partículas en la posición de muerte del pájaro.
     * Crea {@link Constants#PARTICULAS_AL_MORIR} partículas en todas direcciones.
     *
     * Parámetros:
     * @param x  Centro X de emisión (donde estaba el pájaro)
     * @param y  Centro Y de emisión
     * @param cr Color rojo base de las partículas
     * @param cg Color verde base de las partículas
     * @param cb Color azul base de las partículas
     * 
     * Las partículas salen en direcciones aleatorias con velocidades variadas
     * y cada una tiene su propia duración de vida, creando un efecto natural.
     */
    public void emitirMuerte(float x, float y, float cr, float cg, float cb) {
        // Crear N partículas (Constants.PARTICULAS_AL_MORIR = 14 típicamente)
        for (int i = 0; i < Constants.PARTICULAS_AL_MORIR; i++) {
            // Ángulo aleatorio (0 → 2π radianes = 360°)
            float angulo = (float) (rng.nextFloat() * Math.PI * 2.0);
            
            // Velocidad aleatoria (rango: PARTICULA_VEL_MIN → PARTICULA_VEL_MAX)
            float spd = Constants.PARTICULA_VEL_MIN
                    + rng.nextFloat() * (Constants.PARTICULA_VEL_MAX - Constants.PARTICULA_VEL_MIN);
            
            // Duración aleatoria (rango: PARTICULA_VIDA_MIN → PARTICULA_VIDA_MAX)
            float vida = Constants.PARTICULA_VIDA_MIN
                    + rng.nextFloat() * (Constants.PARTICULA_VIDA_MAX - Constants.PARTICULA_VIDA_MIN);
            
            // Tamaño aleatorio (rango: PARTICULA_SIZE_MIN → PARTICULA_SIZE_MAX)
            float size = Constants.PARTICULA_SIZE_MIN
                    + rng.nextFloat() * (Constants.PARTICULA_SIZE_MAX - Constants.PARTICULA_SIZE_MIN);

            // Variación de color — añade aleatoriedad para que no sean idénticas
            // Cada componente RGB varía ±10% respecto al color original
            float vr = clamp(cr + rng.nextFloat() * 0.20f - 0.10f);
            float vg = clamp(cg + rng.nextFloat() * 0.20f - 0.10f);
            float vb = clamp(cb + rng.nextFloat() * 0.20f - 0.10f);

            // Convertir ángulo y velocidad en componentes vx, vy
            // cos(ángulo) = X, sin(ángulo) = Y
            particles.add(new Particle(
                    x, y,  // Centro de emisión
                    (float) Math.cos(angulo) * spd,  // Velocidad X (hacia los lados)
                    (float) Math.sin(angulo) * spd,  // Velocidad Y (hacia arriba/abajo)
                    vida, size, vr, vg, vb));        // Duración y color
        }
    }

    /**
     * Actualiza la física de todas las partículas activas cada frame.
     * 
     * Para cada partícula:
     * 1. Integración: suma velocidad a posición (x += vx*dt, y += vy*dt)
     * 2. Gravedad: acelera hacia abajo (vy += gravedad*dt)
     * 3. Desvanecimiento: resta duración (vida -= dt)
     * 4. Limpieza: elimina partículas que han expirado (vida <= 0)
     *
     * @param dt Delta time en segundos (típicamente ~0.016 a 0.033 segundos)
     */
    public void actualizar(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            
            // 1. Actualizar posición según velocidad
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            
            // 2. Aplicar gravedad (caída lenta)
            p.vy += GRAVEDAD_PARTICULA * dt;
            
            // 3. Restar tiempo de vida
            p.vida -= dt;
            
            // 4. Si expiró, eliminar de la lista
            if (p.vida <= 0f)
                it.remove();
        }
    }

    /** 
     * Devuelve la lista de partículas activas (para que Renderer las dibuje).
     * El Renderer itera esta lista y dibuja cada partícula como un pequeño cuadrado.
     */
    public List<Particle> getParticles() {
        return particles;
    }

    /** 
     * Elimina todas las partículas (al reiniciar la partida).
     * Necesario para limpiar estado anterior.
     */
    public void clear() {
        particles.clear();
    }

    // ════════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ════════════════════════════════════════════════════════════════════════
    
    /**
     * Limita un valor al rango [0, 1] (para colores RGB válidos).
     * Si value < 0 → devuelve 0
     * Si value > 1 → devuelve 1
     * Si 0 <= value <= 1 → devuelve value sin cambios
     */
    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}