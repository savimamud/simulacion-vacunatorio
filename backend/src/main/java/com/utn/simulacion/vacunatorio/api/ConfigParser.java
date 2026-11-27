package com.utn.simulacion.vacunatorio.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utn.simulacion.vacunatorio.model.SimConfig;

public final class ConfigParser {

    private ConfigParser() {}

    public static SimConfig fromJson(JsonObject data) {
        SimConfig c = new SimConfig();
        c.tiempoMax = dbl(data, "tiempo_max", 480);
        c.maxIteraciones = (int) dbl(data, "max_iteraciones", 100_000);
        c.covidRatePer15min = dbl(data, "covid_rate_per_15min", 4);
        c.covidIntervalMinutes = dbl(data, "covid_interval_minutes", 15);
        c.gripeMeanInterarrivalMin = dbl(data, "gripe_mean_interarrival_min", 5);
        c.groupMin = (int) dbl(data, "group_min", 1);
        c.groupMax = (int) dbl(data, "group_max", 4);
        c.covidBoxSize = (int) dbl(data, "covid_box_size", 5);
        c.covidMinPatientsToOpen = (int) dbl(data, "covid_min_patients_to_open", 5);
        c.gripeBoxSize = (int) dbl(data, "gripe_box_size", 10);
        c.secondsPerPatient = dbl(data, "seconds_per_patient", 22);

        // Nombres nuevos del formulario. Los dbl internos mantienen compatibilidad con nombres anteriores.
        c.odeA = dbl(data, "ode_a", dbl(data, "ode_rhs_constant", 41.4));
        c.odeB = dbl(data, "ode_b", dbl(data, "ode_decay", 0.0575));
        c.odeH = dbl(data, "ode_h", dbl(data, "ode_dt", 0.005));
        c.odeT0 = dbl(data, "ode_t0", 0);
        c.odeR0 = dbl(data, "ode_r0", 0.076);

        c.controlStockMeanInterarrivalMin = dbl(data, "control_stock_mean_interarrival_min", 20);
        c.controlStockDurationMin = dbl(data, "control_stock_duration_min", 2);
        c.controlStockEnabled = bool(data, "control_stock_enabled", true);
        c.iteracionInicio = (int) dbl(data, "iteracion_inicio", 1);
        c.iteracionesMostrar = (int) dbl(data, "iteraciones_mostrar", 13);
        c.usarRndsFijos = bool(data, "usar_rnds_fijos", true);
        c.mostrarSoloFilasOds = bool(data, "mostrar_solo_filas_ods", true);
        c.filasOds = (int) dbl(data, "filas_ods", 13);

        if (data.has("seed") && !data.get("seed").isJsonNull()) {
            JsonElement seedEl = data.get("seed");
            if (seedEl.isJsonPrimitive()) {
                c.seed = seedEl.getAsLong();
            }
        }
        return c;
    }

    private static double dbl(JsonObject o, String key, double def) {
        if (!o.has(key) || o.get(key).isJsonNull()) return def;
        return o.get(key).getAsDouble();
    }

    private static boolean bool(JsonObject o, String key, boolean def) {
        if (!o.has(key) || o.get(key).isJsonNull()) return def;
        return o.get(key).getAsBoolean();
    }
}
