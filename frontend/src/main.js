import { simulate, rungeKutta, checkHealth } from "./api.js";
import { collectParams, setLoading, showToast, renderFullResults, renderRK, renderSummary, showResults, activateTab } from "./render.js";
import "./style.css";

const form = document.getElementById("paramsForm");

document.querySelectorAll(".nav-tab").forEach((tab) => {
  tab.addEventListener("click", () => activateTab(tab.dataset.tab));
});

document.querySelectorAll(".accordion-trigger").forEach((btn) => {
  btn.addEventListener("click", () => btn.closest(".accordion").classList.toggle("open"));
});

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  setLoading(true);
  try {
    let params;
    try { params = collectParams(form); } catch (err) { showToast(err.message, "error"); return; }
    renderFullResults(await simulate(params));
    showToast("Simulación completada", "success");
  } catch (err) {
    showToast(err.message || "No se pudo conectar con el backend.", "error");
  } finally {
    setLoading(false);
  }
});

document.getElementById("btnRK").addEventListener("click", async () => {
  setLoading(true);
  try {
    let params;
    try { params = collectParams(form); } catch (err) { showToast(err.message, "error"); return; }
    const rk = await rungeKutta(params);
    showResults(true);
    renderSummary({ total_iterations: "—", simulation_end_time: "—", config: { expiration_seconds: rk.X_expiration_seconds, expiration_minutes: rk.X_expiration_seconds / 60 }, slice_params: { j: "—" }, state_slice: [] });
    document.getElementById("statsGrid").innerHTML = "";
    renderRK(rk);
    activateTab("rk");
    showToast("Runge-Kutta calculado", "success");
  } catch (err) {
    showToast(err.message || "Error al calcular RK", "error");
  } finally {
    setLoading(false);
  }
});

async function init() {
  try {
    await checkHealth();
    document.getElementById("backendStatus").className = "status-dot online";
    document.getElementById("backendLabel").textContent = "Backend conectado";
  } catch {
    document.getElementById("backendStatus").className = "status-dot offline";
    document.getElementById("backendLabel").textContent = "Backend desconectado";
  }
}

init();