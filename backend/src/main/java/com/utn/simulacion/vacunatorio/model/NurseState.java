package com.utn.simulacion.vacunatorio.model;

public class NurseState {
    public boolean busy;
    public double busyUntil;
    public VaccineType currentVaccine;
    public int batchSize;
    public VaccineType lastVaccine;
    public boolean interrupted;

    public NurseState copy() {
        NurseState n = new NurseState();
        n.busy = busy;
        n.busyUntil = busyUntil;
        n.currentVaccine = currentVaccine;
        n.batchSize = batchSize;
        n.lastVaccine = lastVaccine;
        n.interrupted = interrupted;
        return n;
    }
}
