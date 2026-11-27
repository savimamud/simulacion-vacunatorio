package com.utn.simulacion.vacunatorio.model;

public class RandomDraw {
    public String variable;
    public double value;
    public String usedFor;
    public double eventTime;

    public RandomDraw(String variable, double value, String usedFor, double eventTime) {
        this.variable = variable;
        this.value = value;
        this.usedFor = usedFor;
        this.eventTime = eventTime;
    }
}
