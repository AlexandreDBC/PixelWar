package util;

import java.io.Serializable;

public class Pixel implements Serializable {
    private static final long serialVersionUID = 1L;
    private int x;
    private int y;
    private String couleur;
    private boolean estReserve;
    private long tempsReservationMs; // Stocke le timestamp de réservation

    public Pixel(int x, int y, String couleur) {
        this.x = x;
        this.y = y;
        this.couleur = couleur;
        this.estReserve = false;
        this.tempsReservationMs = 0;
    }

    // Getters et setters existants
    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public String getCouleur() {
        return couleur;
    }

    // Nouveaux getters et setters pour la réservation
    public boolean estReserve() {
        return estReserve;
    }

    public void reserver() {
        this.estReserve = true;
        this.tempsReservationMs = System.currentTimeMillis();
    }

    public void liberer() {
        this.estReserve = false;
    }

    public long getTempsReservationMs() {
        return tempsReservationMs;
    }

    // Vérifie si la période d'embargo est passée (1 minute)
    public boolean estEmbargoTermine() {
        if (!estReserve) return true;
        return (System.currentTimeMillis() - tempsReservationMs) >= 60000; // 60000ms = 1 minute
    }
}