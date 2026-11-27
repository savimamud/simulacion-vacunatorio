package com.utn.simulacion.vacunatorio.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StateRow {
    public int iteration;
    public double time;
    public String eventType;
    public String eventDetail;
    public List<Patient> patients;
    public List<VaccineBox> boxes;
    public NurseState nurse;
    public int queuesCovid;
    public int queuesGripe;
    public int closedCovidBoxes;
    public int closedGripeBoxes;
    public List<RandomDraw> randomDraws;
    public List<Map<String, Object>> tempObjects;
    public Map<String, Object> metricsSnapshot;

    public StateRow(int iteration, double time, String eventType, String eventDetail,
                    List<Patient> patients, List<VaccineBox> boxes, NurseState nurse,
                    int queuesCovid, int queuesGripe, int closedCovidBoxes, int closedGripeBoxes,
                    List<RandomDraw> randomDraws, List<Map<String, Object>> tempObjects,
                    Map<String, Object> metricsSnapshot) {
        this.iteration = iteration;
        this.time = time;
        this.eventType = eventType;
        this.eventDetail = eventDetail;
        this.patients = patients;
        this.boxes = boxes;
        this.nurse = nurse;
        this.queuesCovid = queuesCovid;
        this.queuesGripe = queuesGripe;
        this.closedCovidBoxes = closedCovidBoxes;
        this.closedGripeBoxes = closedGripeBoxes;
        this.randomDraws = randomDraws;
        this.tempObjects = tempObjects;
        this.metricsSnapshot = metricsSnapshot;
    }

    public Map<String, Object> toMap(boolean includeTemp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("iteracion", iteration);
        m.put("tiempo", round(time, 4));
        m.put("evento", eventType);
        m.put("detalle_evento", eventDetail);
        m.put("cola_covid", queuesCovid);
        m.put("cola_gripe", queuesGripe);
        m.put("cajas_covid_cerradas", closedCovidBoxes);
        m.put("cajas_gripe_cerradas", closedGripeBoxes);
        m.put("enfermero", nurseToMap(nurse));
        m.put("pacientes", patients.stream().map(StateRow::patientToMap).collect(Collectors.toList()));
        m.put("cajas", boxes.stream().map(StateRow::boxToMap).collect(Collectors.toList()));
        m.put("aleatorios", randomDraws.stream().map(StateRow::drawToMap).collect(Collectors.toList()));
        m.put("metricas_parciales", metricsSnapshot);
        if (includeTemp) m.put("objetos_temporales", tempObjects);
        return m;
    }

    private static Map<String, Object> patientToMap(Patient p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("vacuna", p.vaccineType.name());
        m.put("llegada", round(p.arrivalTime, 4));
        m.put("grupo", p.groupId);
        m.put("estado", p.status.name());
        m.put("vacunado_en", p.vaccinatedAt != null ? round(p.vaccinatedAt, 4) : null);
        return m;
    }

    private static Map<String, Object> boxToMap(VaccineBox b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id);
        m.put("vacuna", b.vaccineType.name());
        m.put("capacidad", b.capacity);
        m.put("dosis_restantes", b.dosesRemaining);
        m.put("abierta_en", round(b.openedAt, 4));
        m.put("vence_en", b.expiresAt != null ? round(b.expiresAt, 4) : null);
        m.put("abierta", b.isOpen);
        return m;
    }

    private static Map<String, Object> nurseToMap(NurseState n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ocupado", n.busy);
        m.put("libre_en", round(n.busyUntil, 4));
        m.put("vacuna_actual", n.currentVaccine != null ? n.currentVaccine.name() : null);
        m.put("lote", n.batchSize);
        m.put("ultima_vacuna", n.lastVaccine != null ? n.lastVaccine.name() : null);
        m.put("interrumpido", n.interrupted);
        return m;
    }

    private static Map<String, Object> drawToMap(RandomDraw d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("variable", d.variable);
        m.put("valor", round(d.value, 6));
        m.put("uso", d.usedFor);
        m.put("tiempo_evento", round(d.eventTime, 4));
        return m;
    }

    private static double round(double v, int places) {
        double p = Math.pow(10, places);
        return Math.round(v * p) / p;
    }
}
