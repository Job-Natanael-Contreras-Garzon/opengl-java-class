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
 * - {@link #playJump()} → Tono ascendente (400→900 Hz) cuando el pájaro salta
 * - {@link #playScore()} → Dos pitidos (700 y 1050 Hz) cuando anota un punto
 * - {@link #playGameOver()} → Barrido descendente (600→120 Hz) cuando se game over
 * - {@link #playFlap()} → Ruido suave cuando el pájaro aletea
 *
 * Diseño técnico:
 * - Todos los sonidos se reproducen en un POOL de 3 hilos daemon
 *   para no bloquear el hilo de render (audio asíncrono).
 * - {@link #setEnabled(boolean)} permite silenciar todo el audio globalmente.
 * - {@link #shutdown()} libera el pool al cerrar la ventana.
 *
 * Formato de audio:
 * - Sample rate: 44100 Hz (CD quality)
 * - Bits: 16 (rango: -32768 a 32767)
 * - Canales: 1 (mono)
 * - Formato: PCM signed little-endian (bytes sin comprimir)
 *
 * Extensión futura:
 * Para cargar archivos .wav reales, reemplaza generarBarrido() por
 * AudioSystem.getAudioInputStream(File) y usa Clip.open().
 */
public class SoundManager {

   // ── Configuración de audio PCM ───────────────────────────────────────────
   private static final float SAMPLE_RATE = 44100f;   // Muestras por segundo (Hz)
   private static final int BITS = 16;                 // Bits por muestra (16 bits)
   private static final int CHANNELS = 1;              // Mono (no estéreo)
   private static final boolean SIGNED = true;         // Rango: -32768 a 32767
   private static final boolean BIG_ENDIAN = false;    // Byte order: little-endian

   // Objeto que define el formato de audio
   private final AudioFormat formato = new AudioFormat(
         SAMPLE_RATE, BITS, CHANNELS, SIGNED, BIG_ENDIAN);

   // ── Pool de hilos para reproducción asíncrona ────────────────────────────
   /**
    * Pool de 3 hilos daemon para reproducción concurrente de sonidos.
    * Daemon = muere automáticamente si la app termina.
    * Concurrente = múltiples sonidos pueden reproducirse al mismo tiempo.
    */
   private final ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
      Thread t = new Thread(r, "SoundPool");
      t.setDaemon(true);  // Este hilo no previene que la app cierre
      return t;
   });

   // ── Control de volumen global ────────────────────────────────────────────
   private volatile boolean enabled = true;  // volatile = cambios visibles entre hilos

   // ════════════════════════════════════════════════════════════════════════
   // API PÚBLICA
   // ════════════════════════════════════════════════════════════════════════

   /** 
    * Activa o desactiva el audio globalmente.
    * Si está desactivado, playJump/Score/etc. no hacen nada.
    */
   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   /** Devuelve si el audio está activado. */
   public boolean isEnabled() {
      return enabled;
   }

   /**
    * SONIDO DE SALTO: Barrido ascendente 400→900 Hz en 120 ms.
    * Se reproduce cuando el pájaro salta (presiona SPACE/W).
    * Efecto: pitch ascendente (sirena pequeña).
    */
   public void playJump() {
      // generarBarrido: genera un barrido lineal de frecuencia
      // parámetros: freqInicio=400Hz, freqFin=900Hz, duracion=0.12seg, volumen=0.35
      play(generarBarrido(400, 900, 0.12f, 0.35f));
   }

   /**
    * SONIDO DE ALETEO: Ruido filtrado muy corto (60 ms).
    * Podría usarse cuando el pájaro aletea en caída lenta.
    */
   public void playFlap() {
      play(generarRuido(0.06f, 0.18f));
   }

   /**
    * SONIDO DE PUNTO: Dos pitidos ascendentes.
    * Primer pitido: 700 Hz + Pausa 30ms + Segundo pitido: 1050 Hz
    * Se reproduce cuando el pájaro pasa una tubería sin chocar.
    * Efecto: "ping pong" alegre para feedback positivo.
    */
   public void playScore() {
      // generarDosPitidos: crea dos tonos puros consecutivos
      // parámetros: freq1=700Hz, freq2=1050Hz, duracion=80ms cada, volumen=0.40
      play(generarDosPitidos(700, 1050, 0.08f, 0.40f));
   }

   /**
    * SONIDO DE GAME OVER: Barrido descendente 600→120 Hz en 600 ms.
    * Se reproduce cuando ambos pájaros chocan.
    * Efecto: pitch descendente largo y grave (triste/final).
    */
   public void playGameOver() {
      // generarBarrido: barrido descendente lento (600ms)
      // parámetros: freqInicio=600Hz, freqFin=120Hz, duracion=0.60seg, volumen=0.55
      play(generarBarrido(600, 120, 0.60f, 0.55f));
   }

   /** 
    * Libera el pool de hilos de audio.
    * IMPORTANTE: llamar en AppFlappyBird.cleanup() para evitar memory leaks.
    */
   public void shutdown() {
      pool.shutdownNow();
   }

   // ════════════════════════════════════════════════════════════════════════
   // GENERADORES DE AUDIO PCM (síntesis de sonido)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Genera un barrido de frecuencia lineal (sweep tone).
    * La frecuencia cambia linealmente de freqInicio a freqFin durante la duración.
    * 
    * Cálculo de cada muestra:
    * 1. Calcular frecuencia actual (interpolada linealmente)
    * 2. Calcular fase del seno (integral de 2π*freq*t)
    * 3. Generar muestra de seno: sin(fase) * volumen * envelope
    * 4. Aplicar fade-in/fade-out para evitar chasquidos
    *
    * @param freqInicio Frecuencia inicial en Hz (ej: 400)
    * @param freqFin    Frecuencia final en Hz (ej: 900)
    * @param duracion   Duración en segundos (ej: 0.12)
    * @param volumen    Amplitud [0..1] (0.35 = moderado)
    * 
    * @return array de bytes PCM 16-bit little-endian (2 bytes por muestra)
    */
   private byte[] generarBarrido(double freqInicio, double freqFin,
         float duracion, float volumen) {
      // Calcular número de muestras
      int muestras = (int) (SAMPLE_RATE * duracion);
      byte[] buf = new byte[muestras * 2];  // 2 bytes por muestra (16 bits)
      
      for (int i = 0; i < muestras; i++) {
         // Tiempo actual en segundos
         double t = i / SAMPLE_RATE;
         
         // Frecuencia actual (interpolación lineal)
         // Cuando i=0: freq = freqInicio
         // Cuando i=muestras-1: freq = freqFin
         double freq = freqInicio + (freqFin - freqInicio) * (i / (double) muestras);
         
         // Fase acumulada (ángulo del seno)
         double fase = 2.0 * Math.PI * freq * t;
         
         // Envelope (fade-in 10% + fade-out 20%) para evitar chasquidos
         double env = envelope(i, muestras, 0.10f, 0.20f);
         
         // Generar muestra: seno * volumen * envelope
         // Rango: -32767 a 32767 (16-bit signed)
         short sample = (short) (volumen * env * 32767 * Math.sin(fase));
         
         // Escribir en bytes (little-endian: byte bajo primero)
         buf[i * 2] = (byte) (sample & 0xFF);
         buf[i * 2 + 1] = (byte) (sample >> 8);
      }
      return buf;
   }

   /**
    * Genera dos pitidos puros consecutivos con pausa entre ellos.
    * Pitido 1: freq1 durante durPitido segundos
    * Pausa silenciosa: 30 ms
    * Pitido 2: freq2 durante durPitido segundos
    *
    * @param freq1     Frecuencia del primer pitido (Hz)
    * @param freq2     Frecuencia del segundo pitido (Hz)
    * @param durPitido Duración de cada pitido (segundos)
    * @param volumen   Amplitud [0..1]
    * 
    * @return array de bytes PCM que reproduce "beep beep"
    */
   private byte[] generarDosPitidos(double freq1, double freq2,
         float durPitido, float volumen) {
      // Calcular muestras
      int m1 = (int) (SAMPLE_RATE * durPitido);   // Pitido 1
      int pausa = (int) (SAMPLE_RATE * 0.03f);    // Pausa 30ms (silencio)
      int m2 = (int) (SAMPLE_RATE * durPitido);   // Pitido 2
      
      byte[] buf = new byte[(m1 + pausa + m2) * 2];
      
      // Escribir primer pitido en muestras 0 a m1-1
      escribirSinusoide(buf, 0, m1, freq1, volumen);
      
      // Pausa (muestras m1 a m1+pausa-1) se quedan en 0 (silencio)
      // No es necesario escribir nada (new byte[] inicializa con 0)
      
      // Escribir segundo pitido después de la pausa
      escribirSinusoide(buf, (m1 + pausa) * 2, m2, freq2, volumen);
      
      return buf;
   }

   /**
    * Función auxiliar: escribe una sinusoide pura en un buffer.
    * Usada por generarDosPitidos() para escribir cada uno de los dos pitidos.
    *
    * @param buf      Buffer donde escribir (debe estar preallocated)
    * @param offset   Offset de bytes en el buffer
    * @param muestras Número de muestras a escribir
    * @param freq     Frecuencia del seno (Hz)
    * @param volumen  Amplitud [0..1]
    */
   private void escribirSinusoide(byte[] buf, int offset, int muestras,
         double freq, float volumen) {
      for (int i = 0; i < muestras; i++) {
         double t = i / SAMPLE_RATE;
         
         // Envelope con fade-in suave (5%) y fade-out suave (15%)
         double env = envelope(i, muestras, 0.05f, 0.15f);
         
         // Generar muestra: seno * volumen * envelope
         short s = (short) (volumen * env * 32767 * Math.sin(2 * Math.PI * freq * t));
         
         // Escribir en bytes (little-endian)
         buf[offset + i * 2] = (byte) (s & 0xFF);
         buf[offset + i * 2 + 1] = (byte) (s >> 8);
      }
   }

   /**
    * Genera ruido blanco filtrado (suavizado simple).
    * Ruido blanco = muestras aleatorias, pero se suaviza promediando
    * para evitar sonido demasiado áspero.
    *
    * @param duracion Duración en segundos
    * @param volumen  Amplitud [0..1]
    * 
    * @return array de bytes PCM con ruido filtrado
    */
   private byte[] generarRuido(float duracion, float volumen) {
      int muestras = (int) (SAMPLE_RATE * duracion);
      byte[] buf = new byte[muestras * 2];
      double prev = 0;  // Valor anterior para suavizado
      
      for (int i = 0; i < muestras; i++) {
         // Generar número aleatorio en [-1, 1)
         double rnd = (Math.random() * 2 - 1);
         
         // Suavizado simple: promedio con muestra anterior
         // Esto suaviza transiciones abruptas
         double filtrado = (rnd + prev) * 0.5;
         prev = rnd;
         
         // Envelope: fade-in muy rápido (2%) y fade-out lento (40%)
         double env = envelope(i, muestras, 0.02f, 0.40f);
         
         // Generar muestra
         short s = (short) (volumen * env * 32767 * filtrado);
         
         // Escribir en bytes
         buf[i * 2] = (byte) (s & 0xFF);
         buf[i * 2 + 1] = (byte) (s >> 8);
      }
      return buf;
   }

   /**
    * Calcula el envelope (ganancia) en función de la posición en el sonido.
    * Evita chasquidos (clicks) al inicio/final del sonido aplicando fade-in/fade-out.
    *
    * Fases del envelope:
    * 1. Fade-in (primeras muestras): 0→1 lineal (suave entrada)
    * 2. Sustain (muestras centrales): 1 (volumen completo)
    * 3. Fade-out (últimas muestras): 1→0 lineal (suave salida)
    *
    * @param i       Muestra actual (0 a total-1)
    * @param total   Total de muestras
    * @param fadeIn  Fracción de fade-in [0..1] (ej: 0.10 = primeras 10%)
    * @param fadeOut Fracción de fade-out [0..1] (ej: 0.20 = últimas 20%)
    * 
    * @return ganancia [0..1] a aplicar a la muestra
    */
   private double envelope(int i, int total, float fadeIn, float fadeOut) {
      // Convertir índice a fracción normalizada [0..1]
      double t = i / (double) total;
      
      // Si estamos en la zona de fade-in (primeras muestras)
      if (t < fadeIn)
         return t / fadeIn;  // Aumenta de 0 a 1
      
      // Si estamos en la zona de fade-out (últimas muestras)
      if (t > (1.0 - fadeOut))
         return (1.0 - t) / fadeOut;  // Disminuye de 1 a 0
      
      // En el medio: volumen completo
      return 1.0;
   }

   // ════════════════════════════════════════════════════════════════════════
   // REPRODUCCIÓN ASÍNCRONA (en thread pool)
   // ════════════════════════════════════════════════════════════════════════

   /**
    * Reproduce un buffer PCM de audio de forma asíncrona.
    * No bloquea el hilo de juego, usa el pool de hilos daemon.
    *
    * Proceso:
    * 1. Verificar que el audio esté habilitado
    * 2. Enviar tarea al pool de hilos
    * 3. En el thread del pool:
    *    a. Crear SourceDataLine (dispositivo de audio)
    *    b. Abrir línea con formato de audio
    *    c. Escribir datos PCM
    *    d. Esperar a que se reproduzca (drain)
    *
    * @param pcmData Datos de audio PCM en bytes
    */
   private void play(byte[] pcmData) {
      // Si el audio está deshabilitado o no hay datos, no hacer nada
      if (!enabled || pcmData == null)
         return;
      
      // Enviar tarea al pool de hilos
      pool.submit(() -> {
         try {
            // Obtener información de línea de audio compatible
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, formato);
            if (!AudioSystem.isLineSupported(info))
               return;  // No hay dispositivo compatible

            // Abrir línea de audio
            try (SourceDataLine linea = (SourceDataLine) AudioSystem.getLine(info)) {
               linea.open(formato, pcmData.length);
               linea.start();                              // Comienza a reproducir
               linea.write(pcmData, 0, pcmData.length);    // Escribir datos
               linea.drain();                              // Esperar a que termine
            }
         } catch (Exception e) {
            // Audio no es crítico para el juego — ignorar errores silenciosamente
         }
      });
   }
}