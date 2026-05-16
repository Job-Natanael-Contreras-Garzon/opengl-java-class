package com.graphics;

public class Bird {
   public float birdX;
   public final float startX;
   public float birdY;
   public float velY;
   public boolean alive;
   public int puntaje;
   public float tiempoAleteo;

   public final float[] colorCuerpo;
   public final float[] colorPanza;
   public final float[] colorAla;
   public final float[] colorPico;
   public final String nombre;

   public Bird(float x, float[] cuerpo, float[] panza, float[] ala, float[] pico, String nombre) {
      this.startX = x;
      this.birdX = x;
      this.colorCuerpo = cuerpo;
      this.colorPanza = panza;
      this.colorAla = ala;
      this.colorPico = pico;
      this.nombre = nombre;
   }

   public void reset() {
      birdX = startX;
      birdY = 0.0f;
      velY = 0.0f;
      alive = true;
      puntaje = 0;
      tiempoAleteo = 0f;
   }
}