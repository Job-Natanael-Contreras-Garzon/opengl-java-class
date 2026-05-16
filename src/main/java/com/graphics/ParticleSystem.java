package com.graphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * ParticleSystem — Sistema de partículas desacoplado del renderer y del mundo.
 *
 * Responsabilidades:
 * - Emitir ráfagas de partículas en una posición dada (ej: muerte de pájaro).
 * - Actualizar física de cada partícula (posición, gravedad, fade).
 * - Exponer la lista de partículas activas al Renderer para que las dibuje.
 *
 * Diseño:
 * - Clase interna {@link Particle} es un DTO puro (sólo datos, sin lógica).
 * - ParticleSystem no conoce OpenGL; el Renderer lee {@link #getParticles()}.
 */
public class ParticleSystem {

    // ── Partícula individual ─────────────────────────────────────────────────
    public static class Particle {
        public float x, y; // posición actual
        public float vx, vy; // velocidad
        public float vida; // vida restante (segundos)
        public float maxVida; // vida inicial (para calcular alpha)
        public float size; // tamaño base
        public float r, g, b; // color

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

        /** Fracción de vida restante [1→0]. Útil para fade y escala. */
        public float alpha() {
            return vida / maxVida;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    private static final float GRAVEDAD_PARTICULA = -0.90f;

    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    // ── API pública ──────────────────────────────────────────────────────────

    /**
     * Emite una ráfaga de partículas del color del pájaro en su posición.
     *
     * @param x  Centro X de emisión (NDC)
     * @param y  Centro Y de emisión (NDC)
     * @param cr Color R base
     * @param cg Color G base
     * @param cb Color B base
     */
    public void emitirMuerte(float x, float y, float cr, float cg, float cb) {
        for (int i = 0; i < Constants.PARTICULAS_AL_MORIR; i++) {
            float angulo = (float) (rng.nextFloat() * Math.PI * 2.0);
            float spd = Constants.PARTICULA_VEL_MIN
                    + rng.nextFloat() * (Constants.PARTICULA_VEL_MAX - Constants.PARTICULA_VEL_MIN);
            float vida = Constants.PARTICULA_VIDA_MIN
                    + rng.nextFloat() * (Constants.PARTICULA_VIDA_MAX - Constants.PARTICULA_VIDA_MIN);
            float size = Constants.PARTICULA_SIZE_MIN
                    + rng.nextFloat() * (Constants.PARTICULA_SIZE_MAX - Constants.PARTICULA_SIZE_MIN);

            // Variación de color para que no sean todas idénticas
            float vr = clamp(cr + rng.nextFloat() * 0.20f - 0.10f);
            float vg = clamp(cg + rng.nextFloat() * 0.20f - 0.10f);
            float vb = clamp(cb + rng.nextFloat() * 0.20f - 0.10f);

            particles.add(new Particle(
                    x, y,
                    (float) Math.cos(angulo) * spd,
                    (float) Math.sin(angulo) * spd,
                    vida, size, vr, vg, vb));
        }
    }

    /**
     * Actualiza la física de todas las partículas activas y elimina las muertas.
     *
     * @param dt Delta time en segundos.
     */
    public void actualizar(float dt) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vy += GRAVEDAD_PARTICULA * dt; // caída suave
            p.vida -= dt;
            if (p.vida <= 0f)
                it.remove();
        }
    }

    /** Devuelve la lista de partículas activas (sólo lectura para el Renderer). */
    public List<Particle> getParticles() {
        return particles;
    }

    /** Elimina todas las partículas (al reiniciar la partida). */
    public void clear() {
        particles.clear();
    }

    // ────────────────────────────────────────────────────────────────────────
    private float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}