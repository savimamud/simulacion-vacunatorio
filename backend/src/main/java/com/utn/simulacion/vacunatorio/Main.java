package com.utn.simulacion.vacunatorio;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.utn.simulacion.vacunatorio.api.ConfigParser;
import com.utn.simulacion.vacunatorio.model.SimConfig;
import com.utn.simulacion.vacunatorio.simulation.RungeKutta;
import com.utn.simulacion.vacunatorio.simulation.VacunatorioSimulator;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
  private static final Gson GSON = new GsonBuilder().serializeNulls().create();
  private static Path frontendDist;

  public static void main(String[] args) {
    int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    frontendDist = resolveFrontendDist();
    Javalin app = Javalin.create(config -> config.showJavalinBanner = false);

    app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

    app.post("/api/runge-kutta", ctx -> {
      JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
      SimConfig config = ConfigParser.fromJson(body);
      ctx.json(RungeKutta.solveExpirationOde(
          config.odeA,
          config.odeB,
          config.odeR0,
          config.odeT0,
          config.odeH
      ));
    });

    app.post("/api/simulate", ctx -> {
      try {
        JsonObject body = GSON.fromJson(ctx.body(), JsonObject.class);
        SimConfig config = ConfigParser.fromJson(body);
        VacunatorioSimulator sim = new VacunatorioSimulator(config);
        Map<String, Object> result = sim.run();
        result.put("state_slice", sim.sliceStateVector(config.iteracionInicio, config.iteracionesMostrar));
        Map<String, Object> sliceParams = new LinkedHashMap<>();
        sliceParams.put("j", config.iteracionInicio);
        sliceParams.put("i", config.iteracionesMostrar);
        result.put("slice_params", sliceParams);
        ctx.json(result);
      } catch (Exception ex) { ctx.status(500).json(Map.of("error", ex.getMessage())); }
    });

    app.get("/*", Main::serveStatic);
    app.start(port);
  }

  private static void serveStatic(Context ctx) throws Exception {
    String path = ctx.path();
    if (path.startsWith("/api")) return;
    String relative = path.equals("/") ? "index.html" : path.substring(1);
    Path file = frontendDist != null ? frontendDist.resolve(relative).normalize() : null;

    if (file != null && Files.isRegularFile(file)) {
      if (relative.endsWith(".html")) ctx.contentType("text/html; charset=utf-8");
      else if (relative.endsWith(".js")) ctx.contentType("application/javascript");
      else if (relative.endsWith(".css")) ctx.contentType("text/css");
      ctx.result(Files.readAllBytes(file));
    } else if (frontendDist != null && Files.isRegularFile(frontendDist.resolve("index.html"))) {
      ctx.contentType("text/html; charset=utf-8");
      ctx.result(Files.readAllBytes(frontendDist.resolve("index.html")));
    }
  }

  static Path resolveFrontendDist() {
    Path cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    Path[] candidates = { cwd.resolve("frontend/dist"), cwd.resolve("../frontend/dist") };
    for (Path p : candidates) if (Files.isRegularFile(p.resolve("index.html"))) return p;
    return null;
  }
}