package com.utn.simulacion.vacunatorio.model;

public class VaccineBox {
    public int id;
    public VaccineType vaccineType;
    public int capacity;
    public int dosesRemaining;
    public double openedAt;
    public Double expiresAt;
    public boolean isOpen;

    public VaccineBox(int id, VaccineType vaccineType, int capacity, int dosesRemaining,
                      double openedAt, Double expiresAt, boolean isOpen) {
        this.id = id;
        this.vaccineType = vaccineType;
        this.capacity = capacity;
        this.dosesRemaining = dosesRemaining;
        this.openedAt = openedAt;
        this.expiresAt = expiresAt;
        this.isOpen = isOpen;
    }
}
