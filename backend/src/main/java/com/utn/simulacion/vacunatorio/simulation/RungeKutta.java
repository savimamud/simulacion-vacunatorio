package com.utn.simulacion.vacunatorio.simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RungeKutta {
    private RungeKutta() {}

    // Ecuación general: dR/dt = A.R - B.R^2
    private static double drDt(double a, double b, double r) {
        return (a * r) - (b * r * r);
    }

    private static double round(double v, int places) {
        double p = Math.pow(10, places);
        return Math.round(v * p) / p;
    }

    public static Map<String, Object> solveExpirationOde(double a, double b, double r0, double t0, double h) {
        List<Map<String, Object>> steps = new ArrayList<>();

        double t = t0;
        double r = r0;
        double asymptote = a / b;

        // Se dejan 148 pasos para mantener la misma visualización que ya venía mostrando el TP.
        for (int i = 0; i < 148; i++) {
            double k1 = drDt(a, b, r);
            double k2 = drDt(a, b, r + 0.5 * h * k1);
            double k3 = drDt(a, b, r + 0.5 * h * k2);
            double k4 = drDt(a, b, r + h * k3);

            double rNext = r + (h / 6.0) * (k1 + 2 * k2 + 2 * k3 + k4);

            // Limitamos R para que no supere la asíntota por errores de redondeo.
            if (rNext > asymptote) {
                rNext = asymptote;
            }

            Map<String, Object> step = new LinkedHashMap<>();
            step.put("t", round(t, 4));
            step.put("R", round(r, 6));
            step.put("k1", round(k1, 6));
            step.put("k2", round(k2, 6));
            step.put("k3", round(k3, 6));
            step.put("k4", round(k4, 6));
            step.put("R_next", round(rNext, 6));
            steps.add(step);

            r = rNext;
            t += h;
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("A", a);
        params.put("B", b);
        params.put("h", h);
        params.put("t0", t0);
        params.put("R0", r0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("steps", steps);
        result.put("params", params);
        result.put("R_max", round(asymptote, 2));
        result.put("X_expiration_seconds", round(asymptote, 2));

        return result;
    }

    // Compatibilidad con llamadas viejas.
    public static Map<String, Object> solveExpirationOde(double r0, double h, double tMax) {
        return solveExpirationOde(41.4, 0.0575, r0, 0, h);
    }
}
