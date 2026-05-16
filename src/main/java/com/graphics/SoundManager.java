package com.graphics;

import javax.sound.sampled.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SoundManager — Gestión de audio con {@code javax.sound.sampled}.
 *
 * Genera sonidos sintéticos (sin archivos externos) directamente en PCM,
 * así el proyecto compila y funciona sin recursos de audio adicionales.
 *
 * Sonidos implementados:
 * - {@link #playJump()} → tono ascendente corto (salto del pájaro)
 * - {@link #playScore()} → dos pitidos ascendentes (punto anotado)
 * - {@link #playGameOver()} → tono descendente grave (game over)
 * - {@link #playFlap()} → ruido suave de aleteo
 *
 * Diseño:
 * - Todos los sonidos se reproducen en un pool de hilos daemon para no
 * bloquear el hilo de render.
 * - {@link #setEnabled(boolean)} permite silenciar todo el audio.
 * - {@link #shutdown()} libera el pool al cerrar la ventana.
 *
 * Extensión futura:
 * Para cargar archivos .wav reales, reemplaza {@code generarBuffer*()} por
 * {@code AudioSystem.getAudioInputStream(File)} y usa {@code Clip.open()}.
 */
public class SoundManager {

   private static final float SAMPLE_RATE = 44100f;
   private static final int BITS = 16;
   private static final int CHANNELS = 1;
   private static final boolean SIGNED = true;
   private static final boolean BIG_ENDIAN = false;

   private final AudioFormat formato = new AudioFormat(
         SAMPLE_RATE, BITS, CHANNELS, SIGNED, BIG_ENDIAN);

   /** Pool de 3 hilos daemon para reproducción concurrente. */
   private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
      Thread t = new Thread(r, "SoundPool");
      t.setDaemon(true);
      return t;
   });

   private volatile boolean enabled = true;

   // ── API pública ──────────────────────────────────────────────────────────

   /** Activa o silencia el audio globalmente. */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public boolean isEnabled() {
      return enabled;
   }

   /** Sonido de salto: barrido ascendente 400→900 Hz, 120 ms. */
   public void playJump() {
      play(generarBarrido(400, 900, 0.12f, 0.35f));
   }

   /** Sonido de aleteo: ruido filtrado muy corto, 60 ms. */
   public void playFlap() {
      play(generarRuido(0.06f, 0.18f));
   }

   /** Sonido de punto: dos pitidos 700 Hz + 1050 Hz, 80 ms c/u. */
   public void playScore() {
      play(generarDosPitidos(700, 1050, 0.08f, 0.40f));
   }

   /** Sonido de game over: barrido descendente 600→120 Hz, 600 ms. */
   public void playGameOver() {
      play(generarBarrido(600, 120, 0.60f, 0.55f));
   }

   /** Libera el pool de hilos. Llamar en {@code cleanup()}. */
   public void shutdown() {
      pool.shutdownNow();
   }

   // ── Generadores de audio PCM ─────────────────────────────────────────────

   /**
    * Genera un barrido de frecuencia (sweep) lineal.
    *
    * @param freqInicio Frecuencia inicial en Hz
    * @param freqFin    Frecuencia final en Hz
    * @param duracion   Duración en segundos
    * @param volumen    Amplitud [0..1]
    */
   private byte[] generarBarrido(double freqInicio, double freqFin,
         float duracion, float volumen) {
      int muestras = (int) (SAMPLE_RATE * duracion);
      byte[] buf = new byte[muestras * 2];
      for (int i = 0; i < muestras; i++) {
         double t = i / SAMPLE_RATE;
         double freq = freqInicio + (freqFin - freqInicio) * (i / (double) muestras);
         double fase = 2.0 * Math.PI * freq * t;
         // Envelope: fade-in 10% + fade-out 20%
         double env = envelope(i, muestras, 0.10f, 0.20f);
         short sample = (short) (volumen * env * 32767 * Math.sin(fase));
         buf[i * 2] = (byte) (sample & 0xFF);
         buf[i * 2 + 1] = (byte) (sample >> 8);
      }
      return buf;
   }

   /**
    * Genera dos pitidos cortos consecutivos.
    */
   private byte[] generarDosPitidos(double freq1, double freq2,
         float durPitido, float volumen) {
      int m1 = (int) (SAMPLE_RATE * durPitido);
      int pausa = (int) (SAMPLE_RATE * 0.03f);
      int m2 = (int) (SAMPLE_RATE * durPitido);
      byte[] buf = new byte[(m1 + pausa + m2) * 2];
      escribirSinusoide(buf, 0, m1, freq1, volumen);
      // pausa silenciosa (ya es 0 por defecto en new byte[])
      escribirSinusoide(buf, (m1 + pausa) * 2, m2, freq2, volumen);
      return buf;
   }

   private void escribirSinusoide(byte[] buf, int offset, int muestras,
         double freq, float volumen) {
      for (int i = 0; i < muestras; i++) {
         double t = i / SAMPLE_RATE;
         double env = envelope(i, muestras, 0.05f, 0.15f);
         short s = (short) (volumen * env * 32767 * Math.sin(2 * Math.PI * freq * t));
         buf[offset + i * 2] = (byte) (s & 0xFF);
         buf[offset + i * 2 + 1] = (byte) (s >> 8);
      }
   }

   /**
    * Genera ruido blanco filtrado (promedio de 3 muestras consecutivas).
    */
   private byte[] generarRuido(float duracion, float volumen) {
      int muestras = (int) (SAMPLE_RATE * duracion);
      byte[] buf = new byte[muestras * 2];
      double prev = 0;
      for (int i = 0; i < muestras; i++) {
         double rnd = (Math.random() * 2 - 1);
         double filtrado = (rnd + prev) * 0.5; // suavizado simple
         prev = rnd;
         double env = envelope(i, muestras, 0.02f, 0.40f);
         short s = (short) (volumen * env * 32767 * filtrado);
         buf[i * 2] = (byte) (s & 0xFF);
         buf[i * 2 + 1] = (byte) (s >> 8);
      }
      return buf;
   }

   /**
    * Envelope lineal con fade-in y fade-out.
    *
    * @param i       Muestra actual
    * @param total   Total de muestras
    * @param fadeIn  Fracción de fade-in [0..1]
    * @param fadeOut Fracción de fade-out [0..1]
    */
   private double envelope(int i, int total, float fadeIn, float fadeOut) {
      double t = i / (double) total;
      if (t < fadeIn)
         return t / fadeIn;
      if (t > (1.0 - fadeOut))
         return (1.0 - t) / fadeOut;
      return 1.0;
   }

   // ── Reproducción asíncrona ───────────────────────────────────────────────

   private void play(byte[] pcmData) {
      if (!enabled || pcmData == null)
         return;
      pool.submit(() -> {
         try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            if (!AudioSystem.isLineSupported(info))
               return;

            try (SourceDataLine linea = (SourceDataLine) AudioSystem.getLine(info)) {
               linea.open(formato, pcmData.length);
               linea.start();
               linea.write(pcmData, 0, pcmData.length);
               linea.drain();
            }
         } catch (Exception e) {
            // Audio no crítico — ignorar silenciosamente
         }
      });
   }
}