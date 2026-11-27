package com.utn.simulacion.vacunatorio.model;

public class Patient {
    public int id;
    public VaccineType vaccineType;
    public double arrivalTime;
    public int groupId;
    public PatientStatus status = PatientStatus.ESPERANDO;
    public Double vaccinatedAt;

    public Patient(int id, VaccineType vaccineType, double arrivalTime, int groupId) {
        this.id = id;
        this.vaccineType = vaccineType;
        this.arrivalTime = arrivalTime;
        this.groupId = groupId;
    }
}
