package com.utn.simulacion.vacunatorio.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class SimConfig {
    // El vector de estado del .ods está planteado en MINUTOS.
    public double tiempoMax = 480;
    public int maxIteraciones = 100_000;

    public double covidRatePer15min = 4;
    public double covidIntervalMinutes = 15;
    public double gripeMeanInterarrivalMin = 5;
    public double controlStockMeanInterarrivalMin = 20;

    public int groupMin = 1;
    public int groupMax = 4;
    public int covidBoxSize = 5;
    public int covidMinPatientsToOpen = 5;
    public int gripeBoxSize = 10;

    // Se mantiene como parámetro editable del enunciado. Para reproducir el .ods se usa
    // TRUNC(seconds/60;2) por paciente, que con 22 s da 0,37 min/persona.
    public double secondsPerPatient = 22;

    // Parámetros de Runge-Kutta para dR/dt = A.R - B.R^2
    public double odeA = 41.4;
    public double odeB = 0.0575;
    public double odeH = 0.005;
    public double odeT0 = 0;
    public double odeR0 = 0.076;

    public double controlStockDurationMin = 2;
    public boolean controlStockEnabled = true;

    public Long seed = 42L;
    public int iteracionInicio = 1;
    public int iteracionesMostrar = 13;

    // Etapa de control: reproduce los RND usados en PlanteoVectorEstado.ods.
    // Cuando quieran pasar a simulaciones nuevas, desactivar usarRndsFijos.
    public boolean usarRndsFijos = true;
    public boolean mostrarSoloFilasOds = true;
    public int filasOds = 13;

    public Map<String, Object> toMap(double expirationMinutes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tiempo_max", tiempoMax);
        m.put("max_iteraciones", maxIteraciones);
        m.put("covid_rate_per_15min", covidRatePer15min);
        m.put("covid_interval_minutes", covidIntervalMinutes);
        m.put("gripe_mean_interarrival_min", gripeMeanInterarrivalMin);
        m.put("control_stock_mean_interarrival_min", controlStockMeanInterarrivalMin);
        m.put("group_min", groupMin);
        m.put("group_max", groupMax);
        m.put("covid_box_size", covidBoxSize);
        m.put("covid_min_patients_to_open", covidMinPatientsToOpen);
        m.put("gripe_box_size", gripeBoxSize);
        m.put("seconds_per_patient", secondsPerPatient);
        m.put("ode_a", odeA);
        m.put("ode_b", odeB);
        m.put("ode_h", odeH);
        m.put("ode_t0", odeT0);
        m.put("ode_r0", odeR0);
        m.put("expiration_minutes", expirationMinutes);
        m.put("control_stock_duration_min", controlStockDurationMin);
        m.put("control_stock_enabled", controlStockEnabled);
        m.put("seed", seed);
        m.put("usar_rnds_fijos", usarRndsFijos);
        m.put("mostrar_solo_filas_ods", mostrarSoloFilasOds);
        return m;
    }
}
