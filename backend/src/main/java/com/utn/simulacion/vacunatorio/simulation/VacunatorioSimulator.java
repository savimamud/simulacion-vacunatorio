package com.utn.simulacion.vacunatorio.simulation;

import com.utn.simulacion.vacunatorio.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class VacunatorioSimulator {
    private static final List<Map<String, String>> STATE_COLUMNS = buildColumns();
    private static final int DEFAULT_ODS_ROWS = 13;

    private final SimConfig config;
    private final Random rng;
    private final FixedRndProvider fixedRndProvider = new FixedRndProvider();

    // RK se mantiene intacto. La simulación discreta trabaja en minutos.
    private final double expirationMinutes;

    private double time;
    private double lastEventTime;
    private int iteration;
    private int eventSeq;

    private final PriorityQueue<SimEvent> events = new PriorityQueue<>();
    private final List<Patient> activePatients = new ArrayList<>();
    private final Map<Integer, Integer> patientDisplayIndex = new HashMap<>();
    private final List<VaccineBox> openGripeBoxes = new ArrayList<>();
    private VaccineBox openCovidBox;

    // Se guarda solo la ventana que se muestra y la última fila.
    // Las estadísticas se calculan sobre TODA la corrida, no sobre la ventana j..i.
    private final List<Map<String, Object>> stateHistory = new ArrayList<>();
    private Map<String, Object> lastRow;
    private final StatsAccumulator stats = new StatsAccumulator();

    private String nurseState = "Libre";
    private VaccineType lastVaccine;
    private ActiveService activeService;
    private int activeServiceSeq = 0;
    private int controlStockQueue = 0;
    private boolean controlInProgress = false;

    private Double nextCovidArrival;
    private Double nextGripeArrival;
    private Double nextControlArrival;
    private Double nextApplicationEnd;
    private Double nextGripeExpiration;
    private Double nextControlEnd;

    public VacunatorioSimulator(SimConfig config) {
        this.config = config;
        this.rng = config.seed != null ? new Random(config.seed) : new Random();
        Map<String, Object> rk = RungeKutta.solveExpirationOde(config.odeA, config.odeB, config.odeR0, config.odeT0, config.odeH);
        double expirationSeconds = ((Number) rk.get("X_expiration_seconds")).doubleValue();
        this.expirationMinutes = trunc(expirationSeconds / 60.0, 2);
    }

    public Map<String, Object> run() {
        RowContext init = new RowContext();
        nextCovidArrival = scheduleArrival(VaccineType.COVID, init);
        nextGripeArrival = scheduleArrival(VaccineType.GRIPE, init);
        if (config.controlStockEnabled) nextControlArrival = scheduleControlArrival(init);
        recordState("inicializacion", init);

        while (!events.isEmpty() && iteration < config.maxIteraciones) {
            SimEvent event = events.poll();
            if (event.time > config.tiempoMax) break;
            if (shouldIgnore(event)) continue;

            advanceBusyTime(event.time);
            time = trunc(event.time, 2);
            RowContext ctx = new RowContext();

            switch (event.eventType) {
                case "LLEGADA_COVID" -> handleArrival(VaccineType.COVID, ctx);
                case "LLEGADA_GRIPE" -> handleArrival(VaccineType.GRIPE, ctx);
                case "FIN_APLICACION" -> handleApplicationEnd(event, ctx);
                case "VENCIMIENTO_GRIPE" -> handleGripeExpiration(event, ctx);
                case "LLEGADA_CONTROL" -> handleControlArrival(ctx);
                case "FIN_CONTROL" -> handleControlEnd(event, ctx);
                default -> ctx.detail = "Evento no reconocido";
            }
            recordState(eventNameForRow(event), ctx);
            lastEventTime = time;
        }

        return buildResult();
    }

    public List<Map<String, Object>> sliceStateVector(int j, int i) {
        return stateHistory;
    }

    private void handleArrival(VaccineType type, RowContext ctx) {
        if (type == VaccineType.COVID) nextCovidArrival = scheduleArrival(VaccineType.COVID, ctx);
        else nextGripeArrival = scheduleArrival(VaccineType.GRIPE, ctx);

        double rndGroup = nextRnd("personas_" + type.name().toLowerCase(), ctx, "RND n.º personas");
        int groupSize = groupSizeFromRnd(rndGroup);
        ctx.rndGroup = rndGroup;
        ctx.groupSize = groupSize;

        int groupId = stats.nextGroupId++;
        for (int i = 0; i < groupSize; i++) {
            Patient p = new Patient(stats.nextPatientId++, type, time, groupId);
            activePatients.add(p);
            int displayIndex = type == VaccineType.COVID ? stats.nextCovidDisplayId++ : stats.nextGripeDisplayId++;
            patientDisplayIndex.put(p.id, displayIndex);
        }

        if (type == VaccineType.COVID) stats.totalArrivalsCovid += groupSize;
        else stats.totalArrivalsGripe += groupSize;

        maybeStartNextActivity(ctx);
    }

    private void handleApplicationEnd(SimEvent event, RowContext ctx) {
        if (activeService == null || !Objects.equals(event.payload.get("service_id"), activeService.serviceId)) return;

        VaccineType finishedType = activeService.type;
        Set<Integer> ids = new LinkedHashSet<>(activeService.patientIds);
        activePatients.removeIf(p -> ids.contains(p.id));

        if (finishedType == VaccineType.COVID && openCovidBox != null && openCovidBox.dosesRemaining <= 0) {
            openCovidBox.isOpen = false;
        }

        activeService = null;
        nextApplicationEnd = null;
        nurseState = "Libre";
        maybeStartNextActivity(ctx);
    }

    private void handleGripeExpiration(SimEvent event, RowContext ctx) {
        int boxId = (int) event.payload.get("box_id");
        VaccineBox box = openGripeBoxes.stream()
                .filter(b -> b.id == boxId && b.isOpen)
                .findFirst()
                .orElse(null);
        if (box != null) {
            stats.totalWastedGripeDoses += box.dosesRemaining;
            box.dosesRemaining = 0;
            box.isOpen = false;
            ctx.detail = "Vencen dosis de gripe";
        }
        refreshNextGripeExpiration();
        maybeStartNextActivity(ctx);
    }

    private void handleControlArrival(RowContext ctx) {
        nextControlArrival = scheduleControlArrival(ctx);

        if (!config.controlStockEnabled) return;
        if (controlInProgress || controlStockQueue > 0) {
            ctx.detail = "Control descartado por control pendiente/en curso";
            return;
        }

        if (activeService != null && activeService.type == VaccineType.COVID) {
            controlStockQueue = 1;
            ctx.detail = "Control queda en cola hasta finalizar COVID";
        } else if (activeService != null && activeService.type == VaccineType.GRIPE) {
            interruptGripeForControl(ctx);
        } else {
            startControlStock(ctx);
        }
    }

    private void handleControlEnd(SimEvent event, RowContext ctx) {
        if (!Objects.equals(event.payload.get("control_id"), stats.currentControlId)) return;
        controlInProgress = false;
        nextControlEnd = null;
        stats.controlsCompleted++;
        stats.currentControlId = null;

        if (activeService != null && activeService.pausedByControl) {
            resumeInterruptedGripe(ctx);
        } else {
            nurseState = "Libre";
            maybeStartNextActivity(ctx);
        }
    }

    private void maybeStartNextActivity(RowContext ctx) {
        if (activeService != null || controlInProgress) return;

        if (controlStockQueue > 0) {
            startControlStock(ctx);
            return;
        }

        VaccineType preferred = lastVaccine == VaccineType.COVID ? VaccineType.GRIPE : VaccineType.COVID;
        VaccineType other = preferred == VaccineType.COVID ? VaccineType.GRIPE : VaccineType.COVID;
        for (VaccineType type : List.of(preferred, other)) {
            int batchSize = feasibleBatchSize(type);
            if (batchSize > 0) {
                startVaccination(type, batchSize, ctx);
                return;
            }
        }
        nurseState = "Libre";
    }

    private int feasibleBatchSize(VaccineType type) {
        long waiting = activePatients.stream()
                .filter(p -> p.vaccineType == type && p.status == PatientStatus.ESPERANDO)
                .count();
        if (type == VaccineType.COVID) {
            if (waiting < config.covidMinPatientsToOpen) return 0;
            if (openCovidBox == null || !openCovidBox.isOpen || openCovidBox.dosesRemaining <= 0) {
                return Math.min((int) waiting, config.covidBoxSize);
            }
            return Math.min((int) waiting, openCovidBox.dosesRemaining);
        }
        if (waiting <= 0) return 0;
        VaccineBox box = firstOpenGripeBoxWithDoses();
        int available = box != null ? box.dosesRemaining : config.gripeBoxSize;
        return Math.min((int) waiting, available);
    }

    private void startVaccination(VaccineType type, int batchSize, RowContext ctx) {
        List<Patient> batch = activePatients.stream()
                .filter(p -> p.vaccineType == type && p.status == PatientStatus.ESPERANDO)
                .limit(batchSize)
                .collect(Collectors.toList());

        VaccineBox box;
        if (type == VaccineType.COVID) {
            if (openCovidBox == null || !openCovidBox.isOpen || openCovidBox.dosesRemaining <= 0) {
                openCovidBox = new VaccineBox(stats.nextBoxId++, type, config.covidBoxSize, config.covidBoxSize, time, null, true);
                stats.covidBoxesOpened++;
            }
            box = openCovidBox;
        } else {
            box = firstOpenGripeBoxWithDoses();
            if (box == null) {
                double expiresAt = trunc(time + expirationMinutes, 2);
                box = new VaccineBox(stats.nextBoxId++, type, config.gripeBoxSize, config.gripeBoxSize, time, expiresAt, true);
                openGripeBoxes.add(box);
                stats.gripeBoxesOpened++;
                schedule(expiresAt, "VENCIMIENTO_GRIPE", Map.of("box_id", box.id));
                nextGripeExpiration = expiresAt;
                ctx.expirationDuration = expirationMinutes;
            }
        }

        for (Patient p : batch) {
            p.status = PatientStatus.VACUNANDO;
            if (type == VaccineType.COVID) {
                stats.covidWaitTimeAccum += trunc(time - p.arrivalTime, 2);
                stats.covidPatientsWithAccess++;
            }
        }

        box.dosesRemaining -= batchSize;
        double duration = applicationDurationMinutes(batchSize);
        double finishTime = trunc(time + duration, 2);
        int serviceId = ++activeServiceSeq;
        activeService = new ActiveService(serviceId, type, batchSize,
                batch.stream().map(p -> p.id).collect(Collectors.toList()), finishTime, duration);
        nextApplicationEnd = finishTime;
        nurseState = type == VaccineType.COVID ? "AplicandoC" : "AplicandoG";
        lastVaccine = type;

        ctx.applicationPeople = batchSize;
        ctx.applicationDuration = duration;
        schedule(finishTime, "FIN_APLICACION", Map.of("service_id", serviceId, "vaccine", type.name()));
    }

    private void interruptGripeForControl(RowContext ctx) {
        if (activeService == null) return;
        activeService.remainingDuration = trunc(activeService.finishTime - time, 2);
        activeService.pausedByControl = true;
        nextApplicationEnd = null;
        startControlStock(ctx);
        ctx.remainingAttention = activeService.remainingDuration;
    }

    private void resumeInterruptedGripe(RowContext ctx) {
        if (activeService == null) return;
        activeService.pausedByControl = false;
        activeService.finishTime = trunc(time + activeService.remainingDuration, 2);
        nextApplicationEnd = activeService.finishTime;
        nurseState = "AplicandoG";
        schedule(activeService.finishTime, "FIN_APLICACION", Map.of("service_id", activeService.serviceId, "vaccine", activeService.type.name()));
        ctx.applicationPeople = activeService.batchSize;
        ctx.applicationDuration = activeService.remainingDuration;
    }

    private void startControlStock(RowContext ctx) {
        controlStockQueue = 0;
        controlInProgress = true;
        nurseState = "ControlStock";
        int controlId = ++stats.nextControlId;
        stats.currentControlId = controlId;
        nextControlEnd = trunc(time + config.controlStockDurationMin, 2);
        ctx.controlFinish = nextControlEnd;
        schedule(nextControlEnd, "FIN_CONTROL", Map.of("control_id", controlId));
    }

    private Double scheduleArrival(VaccineType type, RowContext ctx) {
        String key = type == VaccineType.COVID ? "llegada_covid" : "llegada_gripe";
        double mean = type == VaccineType.COVID
                ? config.covidIntervalMinutes / config.covidRatePer15min
                : config.gripeMeanInterarrivalMin;
        double rnd = nextRnd(key, ctx, "RND llegada " + type.name().toLowerCase());
        double delta = -mean * Math.log(1.0 - rnd);
        double arrivalTime = trunc(time + delta, 2);
        schedule(arrivalTime, type == VaccineType.COVID ? "LLEGADA_COVID" : "LLEGADA_GRIPE", Map.of());

        if (type == VaccineType.COVID) {
            ctx.rndCovidArrival = rnd;
            ctx.deltaCovidArrival = delta;
        } else {
            ctx.rndGripeArrival = rnd;
            ctx.deltaGripeArrival = delta;
        }
        return arrivalTime;
    }

    private Double scheduleControlArrival(RowContext ctx) {
        double rnd = nextRnd("llegada_control", ctx, "RND llegada control stock");
        double delta = trunc(-config.controlStockMeanInterarrivalMin * Math.log(1.0 - rnd), 2);
        double arrivalTime = trunc(time + delta, 2);
        schedule(arrivalTime, "LLEGADA_CONTROL", Map.of());
        ctx.rndControlArrival = rnd;
        ctx.deltaControlArrival = delta;
        return arrivalTime;
    }

    private double nextRnd(String key, RowContext ctx, String label) {
    double rnd = config.usarRndsFijos ? fixedRndProvider.next(key, rng) : rng.nextDouble();
    rnd = trunc(rnd, 2);   // truncar antes de devolverlo, para qu eel calculo coincida
    ctx.usedRandoms.add(Map.of("variable", label, "valor", fmt(rnd), "uso", key));
    return rnd;
    }

    private int groupSizeFromRnd(double rnd) {
        int range = Math.max(1, config.groupMax - config.groupMin + 1);
        int offset = (int) Math.floor(rnd * range);
        if (offset >= range) offset = range - 1;
        return config.groupMin + offset;
    }

    private double applicationDurationMinutes(int people) {
        return trunc(round(config.secondsPerPatient / 60.0, 2) * people, 2);
    }

    private void advanceBusyTime(double eventTime) {
        double delta = trunc(eventTime - lastEventTime, 2);
        if (delta > 0 && (activeService != null || controlInProgress)) {
            stats.nurseBusyTimeAccum = trunc(stats.nurseBusyTimeAccum + delta, 2);
        }
    }

    private void recordState(String eventName, RowContext ctx) {
        iteration++;
        Map<String, Object> row = baseWideRow(eventName, ctx);
        addLegacyFields(row, eventName, ctx);
        lastRow = row;
        if (shouldKeepRowForDisplay(iteration)) {
            stateHistory.add(row);
        }
    }

    private boolean shouldKeepRowForDisplay(int rowIteration) {
        int from = Math.max(1, config.iteracionInicio);
        int to = from + Math.max(0, config.iteracionesMostrar) - 1;
        if (config.mostrarSoloFilasOds) {
            to = Math.min(to, Math.max(1, config.filasOds));
        }
        return rowIteration >= from && rowIteration <= to;
    }

    private Map<String, Object> baseWideRow(String eventName, RowContext ctx) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Map<String, String> col : STATE_COLUMNS) row.put(col.get("key"), null);

        row.put("iteracion", iteration);
        row.put("evento", eventName);
        row.put("reloj", fmt(time));

        put(row, "rnd_llegada_covid", ctx.rndCovidArrival);
        put(row, "tiempo_entre_llegadas_covid", ctx.deltaCovidArrival);
        put(row, "proxima_llegada_covid", nextCovidArrival);
        put(row, "rnd_llegada_gripe", ctx.rndGripeArrival);
        put(row, "tiempo_entre_llegadas_gripe", ctx.deltaGripeArrival);
        put(row, "proxima_llegada_gripe", nextGripeArrival);
        put(row, "rnd_personas", ctx.rndGroup);
        if (ctx.groupSize != null) row.put("personas_que_asisten", ctx.groupSize);

        if (ctx.applicationPeople != null) row.put("cantidad_personas_aplicacion", ctx.applicationPeople);
        put(row, "tiempo_aplicacion", ctx.applicationDuration);
        put(row, "fin_aplicacion", nextApplicationEnd);
        put(row, "tiempo_x_vencimiento", ctx.expirationDuration);
        put(row, "vencimiento_dosis", nextGripeExpiration);

        put(row, "rnd_llegada_control_stock", ctx.rndControlArrival);
        put(row, "tiempo_entre_llegadas_control_stock", ctx.deltaControlArrival);
        put(row, "proxima_llegada_control_stock", nextControlArrival);
        put(row, "fin_control_stock", ctx.controlFinish != null ? ctx.controlFinish : nextControlEnd);

        row.put("enfermero_estado", nurseState);
        row.put("cola_covid", countWaiting(VaccineType.COVID));
        row.put("cola_gripe", countWaiting(VaccineType.GRIPE));
        put(row, "tiempo_remanente_atencion", ctx.remainingAttention);
        row.put("cola_control_stock", controlStockQueue);
        row.put("caja_covid_estado", openCovidBox != null && openCovidBox.isOpen ? "Abierta" : "Cerrada");
        row.put("caja_gripe_estado", hasOpenGripeBox() ? "Abierta" : "Cerrada");
        row.put("dosis_remanentes_gripe", gripeDosesRemaining());

        row.put("cont_llegadas_covid", stats.totalArrivalsCovid);
        row.put("cont_llegadas_gripe", stats.totalArrivalsGripe);
        row.put("ac_tiempo_ocupado_enfermero", fmt(stats.nurseBusyTimeAccum));
        row.put("ac_tiempo_espera_pac_covid", fmt(stats.covidWaitTimeAccum));
        row.put("cont_pac_con_acceso_covid", stats.covidPatientsWithAccess);
        row.put("cont_veces_control_stock", stats.controlsCompleted);
        row.put("cont_dosis_vencidas_gripe", stats.totalWastedGripeDoses);
        row.put("cont_cajas_covid_abiertas", stats.covidBoxesOpened);
        row.put("cont_cajas_gripe_abiertas", stats.gripeBoxesOpened);

        fillPatientColumns(row, VaccineType.COVID, 14, "paciente_covid_");
        fillPatientColumns(row, VaccineType.GRIPE, 6, "paciente_gripe_");
        fillControlColumns(row);

        return row;
    }

    private void addLegacyFields(Map<String, Object> row, String eventName, RowContext ctx) {
        row.put("tiempo", row.get("reloj"));
        row.put("detalle_evento", ctx.detail == null ? "" : ctx.detail);
        row.put("aleatorios", new ArrayList<>(ctx.usedRandoms));
        Map<String, Object> enfermero = new LinkedHashMap<>();
        enfermero.put("estado", nurseState);
        enfermero.put("ocupado", activeService != null || controlInProgress);
        enfermero.put("vacuna_actual", activeService != null ? activeService.type : null);
        enfermero.put("lote", activeService != null ? activeService.batchSize : 0);
        enfermero.put("libre_en", nextApplicationEnd != null ? nextApplicationEnd : nextControlEnd);
        enfermero.put("interrumpido", controlInProgress && activeService != null && activeService.pausedByControl);
        row.put("enfermero", enfermero);
        row.put("pacientes", activePatients.stream().map(this::patientToMap).collect(Collectors.toList()));
        row.put("cajas", currentBoxes().stream().map(this::boxToMap).collect(Collectors.toList()));
    }

    private void fillPatientColumns(Map<String, Object> row, VaccineType type, int max, String prefix) {
        List<Patient> patients = activePatients.stream()
                .filter(p -> p.vaccineType == type)
                .sorted(Comparator.comparingInt(p -> patientDisplayIndex.getOrDefault(p.id, p.id)))
                .collect(Collectors.toList());
        for (Patient p : patients) {
            int idx = patientDisplayIndex.getOrDefault(p.id, p.id);
            if (idx < 1 || idx > max) continue;
            row.put(prefix + idx + "_estado", p.status == PatientStatus.VACUNANDO ? "SV" : "Esperando");
            if (type == VaccineType.COVID && p.status == PatientStatus.ESPERANDO) {
                row.put(prefix + idx + "_hora_inicio_espera", fmt(p.arrivalTime));
            }
        }
    }

    private void fillControlColumns(Map<String, Object> row) {
        if (controlStockQueue > 0) row.put("control_stock_1_estado", "Esperando");
        else if (controlInProgress) row.put("control_stock_1_estado", "Controlando");
    }

    private void put(Map<String, Object> row, String key, Double value) {
        if (value != null) row.put(key, fmt(value));
    }

    private long countWaiting(VaccineType type) {
        return activePatients.stream().filter(p -> p.vaccineType == type && p.status == PatientStatus.ESPERANDO).count();
    }

    private boolean hasOpenGripeBox() {
        return openGripeBoxes.stream().anyMatch(b -> b.isOpen && b.dosesRemaining > 0);
    }

    private int gripeDosesRemaining() {
        return openGripeBoxes.stream().filter(b -> b.isOpen).mapToInt(b -> b.dosesRemaining).sum();
    }

    private VaccineBox firstOpenGripeBoxWithDoses() {
        return openGripeBoxes.stream().filter(b -> b.isOpen && b.dosesRemaining > 0).findFirst().orElse(null);
    }

    private List<VaccineBox> currentBoxes() {
        List<VaccineBox> boxes = new ArrayList<>();
        if (openCovidBox != null && openCovidBox.isOpen) boxes.add(openCovidBox);
        openGripeBoxes.stream().filter(b -> b.isOpen).forEach(boxes::add);
        return boxes;
    }

    private void refreshNextGripeExpiration() {
        nextGripeExpiration = openGripeBoxes.stream()
                .filter(b -> b.isOpen && b.expiresAt != null)
                .map(b -> b.expiresAt)
                .min(Double::compareTo)
                .orElse(null);
    }

    private boolean shouldIgnore(SimEvent event) {
        if (event.eventType.equals("FIN_APLICACION")) {
            return activeService == null || !Objects.equals(event.payload.get("service_id"), activeService.serviceId);
        }
        if (event.eventType.equals("FIN_CONTROL")) {
            return !Objects.equals(event.payload.get("control_id"), stats.currentControlId);
        }
        return false;
    }

    private void schedule(double eventTime, String type, Map<String, Object> payload) {
        if (eventTime <= config.tiempoMax) events.add(new SimEvent(trunc(eventTime, 2), eventSeq++, type, payload));
    }

    private String eventNameForRow(SimEvent event) {
        String eventType = event.eventType;
        return switch (eventType) {
            case "LLEGADA_COVID" -> "llegada_covid";
            case "LLEGADA_GRIPE" -> "llegada_gripe";
            case "FIN_APLICACION" -> "GRIPE".equals(String.valueOf(event.payload.get("vaccine"))) ? "fin_aplicacionG" : "fin_aplicacionC";
            case "VENCIMIENTO_GRIPE" -> "vencimiento_dosis";
            case "LLEGADA_CONTROL" -> "llegada_control";
            case "FIN_CONTROL" -> "fin_control_stock";
            default -> eventType.toLowerCase(Locale.ROOT);
        };
    }

    private Map<String, Object> buildResult() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total_iterations", iteration);
        res.put("simulation_end_time", fmt(time));
        res.put("state_columns", STATE_COLUMNS);
        res.put("state_slice", stateHistory);
        res.put("last_row", lastRow);
        res.put("statistics", buildStatistics());
        res.put("runge_kutta", RungeKutta.solveExpirationOde(config.odeA, config.odeB, config.odeR0, config.odeT0, config.odeH));
        res.put("config", config.toMap(expirationMinutes));
        res.put("notas", List.of(
                "El reloj del vector de estado se expresa en minutos, igual que el .ods.",
                "El vencimiento de gripe usa el resultado del RK sin modificarlo: 720 s = 12 min.",
                "Los RND fijos reproducen las filas manuales del PlanteoVectorEstado.ods."
        ));
        return res;
    }

    private List<Map<String, Object>> buildStatistics() {
        double avgWaitCovid = stats.covidPatientsWithAccess == 0 ? 0 : trunc(stats.covidWaitTimeAccum / stats.covidPatientsWithAccess, 2);
        double pctBusy = time <= 0 ? 0 : trunc(100 * stats.nurseBusyTimeAccum / time, 2);
        return List.of(
                stat("Cantidad pacientes que llegan para COVID", stats.totalArrivalsCovid, "Acumulador de llegadas COVID."),
                stat("Cantidad pacientes que llegan para Gripe", stats.totalArrivalsGripe, "Acumulador de llegadas Gripe."),
                stat("% tiempo ocupado enfermero", pctBusy, "Incluye aplicación y control de stock."),
                stat("Tiempo promedio espera cola COVID", avgWaitCovid, "Solo pacientes COVID que accedieron al servicio."),
                stat("Cantidad veces que controla stock", stats.controlsCompleted, "Se incrementa al finalizar el control."),
                stat("Cantidad dosis vencidas gripe", stats.totalWastedGripeDoses, "Dosis remanentes vencidas."),
                stat("Contador cajas COVID abiertas", stats.covidBoxesOpened, "Cajas de 5 dosis."),
                stat("Contador cajas Gripe abiertas", stats.gripeBoxesOpened, "Cajas de 10 dosis.")
        );
    }

    private Map<String, Object> stat(String name, Object value, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", name);
        m.put("valor", value);
        m.put("descripcion", desc);
        return m;
    }

    private Map<String, Object> patientToMap(Patient p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id);
        m.put("vacuna", p.vaccineType.name());
        m.put("llegada", fmt(p.arrivalTime));
        m.put("grupo", p.groupId);
        m.put("estado", p.status == PatientStatus.VACUNANDO ? "SV" : "Esperando");
        return m;
    }

    private Map<String, Object> boxToMap(VaccineBox b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.id);
        m.put("vacuna", b.vaccineType.name());
        m.put("capacidad", b.capacity);
        m.put("dosis_restantes", b.dosesRemaining);
        m.put("abierta_en", fmt(b.openedAt));
        m.put("vence_en", b.expiresAt != null ? fmt(b.expiresAt) : null);
        return m;
    }

    private static double round(double value, int places) {
        double p = Math.pow(10, places);
        return Math.round(value * p) / p;
    }

    private static double trunc(double value, int places) {
        double p = Math.pow(10, places);
        return Math.floor((value + 1e-12) * p) / p;
    }

    private static String fmt(Double value) {
        if (value == null) return "";
        return fmt(value.doubleValue());
    }

    private static String fmt(double value) {
        double v = trunc(value, 2);
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.US, "%.2f", v);
    }

    private static List<Map<String, String>> buildColumns() {
        List<Map<String, String>> cols = new ArrayList<>();
        add(cols, "iteracion", "i");
        add(cols, "evento", "evento");
        add(cols, "reloj", "reloj");
        add(cols, "rnd_llegada_covid", "RND llegada COVID");
        add(cols, "tiempo_entre_llegadas_covid", "t. entre llegadas COVID");
        add(cols, "proxima_llegada_covid", "próx. llegada COVID");
        add(cols, "rnd_llegada_gripe", "RND llegada Gripe");
        add(cols, "tiempo_entre_llegadas_gripe", "t. entre llegadas Gripe");
        add(cols, "proxima_llegada_gripe", "próx. llegada Gripe");
        add(cols, "rnd_personas", "RND n.º personas");
        add(cols, "personas_que_asisten", "n.º personas que asisten");
        add(cols, "cantidad_personas_aplicacion", "cant. personas aplicación");
        add(cols, "tiempo_aplicacion", "tiempo aplicación");
        add(cols, "fin_aplicacion", "fin aplicación");
        add(cols, "tiempo_x_vencimiento", "tiempo X vencimiento");
        add(cols, "vencimiento_dosis", "vencimiento dosis");
        add(cols, "rnd_llegada_control_stock", "RND llegada control stock");
        add(cols, "tiempo_entre_llegadas_control_stock", "t. entre llegadas control");
        add(cols, "proxima_llegada_control_stock", "próx. llegada control");
        add(cols, "fin_control_stock", "fin control stock");
        add(cols, "enfermero_estado", "Enfermero estado");
        add(cols, "cola_covid", "Cola COVID");
        add(cols, "cola_gripe", "Cola Gripe");
        add(cols, "tiempo_remanente_atencion", "t. remanente atención");
        add(cols, "cola_control_stock", "Cola control stock");
        add(cols, "caja_covid_estado", "Caja COVID estado");
        add(cols, "caja_gripe_estado", "Caja Gripe estado");
        add(cols, "dosis_remanentes_gripe", "Dosis remanentes gripe");
        add(cols, "cont_llegadas_covid", "Cont llegadas COVID");
        add(cols, "cont_llegadas_gripe", "Cont llegadas Gripe");
        add(cols, "ac_tiempo_ocupado_enfermero", "AC tiempo ocupado enfermero");
        add(cols, "ac_tiempo_espera_pac_covid", "AC tiempo espera pac COVID");
        add(cols, "cont_pac_con_acceso_covid", "Cont pac con acceso COVID");
        add(cols, "cont_veces_control_stock", "Cont veces control stock");
        add(cols, "cont_dosis_vencidas_gripe", "Cont dosis vencidas gripe");
        add(cols, "cont_cajas_covid_abiertas", "Cont cajas COVID abiertas");
        add(cols, "cont_cajas_gripe_abiertas", "Cont cajas Gripe abiertas");
        for (int i = 1; i <= 14; i++) {
            add(cols, "paciente_covid_" + i + "_estado", "Paciente COVID " + i + " estado");
            add(cols, "paciente_covid_" + i + "_hora_inicio_espera", "Paciente COVID " + i + " hora inicio espera");
        }
        for (int i = 1; i <= 6; i++) add(cols, "paciente_gripe_" + i + "_estado", "Paciente Gripe " + i + " estado");
        add(cols, "control_stock_1_estado", "Control stock 1 estado");
        return Collections.unmodifiableList(cols);
    }

    private static void add(List<Map<String, String>> cols, String key, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        cols.add(m);
    }

    private static class RowContext {
        Double rndCovidArrival, deltaCovidArrival;
        Double rndGripeArrival, deltaGripeArrival;
        Double rndControlArrival, deltaControlArrival;
        Double rndGroup;
        Integer groupSize;
        Integer applicationPeople;
        Double applicationDuration;
        Double expirationDuration;
        Double controlFinish;
        Double remainingAttention;
        String detail;
        final List<Map<String, Object>> usedRandoms = new ArrayList<>();
    }

    private static class ActiveService {
        final int serviceId;
        final VaccineType type;
        final int batchSize;
        final List<Integer> patientIds;
        double finishTime;
        double remainingDuration;
        boolean pausedByControl;

        ActiveService(int serviceId, VaccineType type, int batchSize, List<Integer> patientIds, double finishTime, double duration) {
            this.serviceId = serviceId;
            this.type = type;
            this.batchSize = batchSize;
            this.patientIds = patientIds;
            this.finishTime = finishTime;
            this.remainingDuration = duration;
        }
    }

    private record SimEvent(double time, int seq, String eventType, Map<String, Object> payload) implements Comparable<SimEvent> {
        @Override public int compareTo(SimEvent other) {
            int cmp = Double.compare(time, other.time);
            return cmp != 0 ? cmp : Integer.compare(seq, other.seq);
        }
    }

    private static class StatsAccumulator {
        int totalArrivalsCovid;
        int totalArrivalsGripe;
        int covidPatientsWithAccess;
        int controlsCompleted;
        int totalWastedGripeDoses;
        int covidBoxesOpened;
        int gripeBoxesOpened;
        int nextPatientId = 1;
        int nextCovidDisplayId = 1;
        int nextGripeDisplayId = 1;
        int nextGroupId = 1;
        int nextBoxId = 1;
        int nextControlId = 0;
        Integer currentControlId;
        double covidWaitTimeAccum;
        double nurseBusyTimeAccum;
    }

    private static class FixedRndProvider {
        private final Map<String, Queue<Double>> fixed = new HashMap<>();

        FixedRndProvider() {
            fixed.put("llegada_covid", queue(0.22, 0.45, 0.68, 0.31, 0.55, 0.12));
            fixed.put("llegada_gripe", queue(0.33, 0.71, 0.18, 0.56));
            fixed.put("llegada_control", queue(0.45, 0.72));
            fixed.put("personas_covid", queue(0.10, 0.62, 0.87, 0.49, 0.91));
            fixed.put("personas_gripe", queue(0.38, 0.22, 0.73));
        }

        double next(String key, Random fallback) {
            Queue<Double> q = fixed.get(key);
            if (q != null && !q.isEmpty()) return q.poll();
            return fallback.nextDouble();
        }

        private static Queue<Double> queue(double... values) {
            Queue<Double> q = new ArrayDeque<>();
            for (double v : values) q.add(v);
            return q;
        }
    }
}
