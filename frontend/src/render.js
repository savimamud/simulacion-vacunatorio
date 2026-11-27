import { Chart, registerables } from "chart.js";
Chart.register(...registerables);

let rkChart = null;

// --- FUNCIONES QUE PIDE TU MAIN.JS ---

export function parseNumber(value) {
  if (value === "" || value == null) return NaN;
  return Number(String(value).trim().replace(",", "."));
}

export function collectParams(form) {
  const fd = new FormData(form);
  const p = Object.fromEntries(fd.entries());
  p.control_stock_enabled = fd.get("control_stock_enabled") === "on";
  p.usar_rnds_fijos = fd.get("usar_rnds_fijos") === "on";
  p.mostrar_solo_filas_ods = fd.get("mostrar_solo_filas_ods") === "on";
  for (const k of Object.keys(p)) {
    if (["control_stock_enabled", "usar_rnds_fijos", "mostrar_solo_filas_ods"].includes(k) || p[k] === "") continue;
    p[k] = parseNumber(p[k]);
    if (Number.isNaN(p[k])) throw new Error(`Valor inválido en "${k}"`);
  }
  return p;
}

export function showToast(msg, type = "info") {
  const el = document.getElementById("toast");
  el.textContent = msg;
  el.className = `toast toast-${type} show`;
  clearTimeout(el._t);
  el._t = setTimeout(() => el.classList.remove("show"), 3200);
}

export function setLoading(on) {
  document.getElementById("loadingOverlay").classList.toggle("hidden", !on);
}

export function activateTab(name) {
  document.querySelectorAll(".nav-tab").forEach((t) => t.classList.toggle("active", t.dataset.tab === name));
  document.querySelectorAll(".tab-pane").forEach((p) => p.classList.toggle("active", p.id === `pane-${name}`));
}

export function showResults(show) {
  document.getElementById("welcomeState").classList.toggle("hidden", show);
  document.getElementById("resultsPanel").classList.toggle("hidden", !show);
}

export function renderSummary(data) {
  const expMin = data.config ? data.config.expiration_minutes : "—";
  document.getElementById("summaryBar").innerHTML = `
    <div class="summary-chip"><span class="label">Iteraciones</span><span class="val">${data.total_iterations}</span></div>
    <div class="summary-chip"><span class="label">Tiempo final</span><span class="val">${data.simulation_end_time} min</span></div>
    <div class="summary-chip accent"><span class="label">Vencimiento gripe</span><span class="val">${expMin} min</span></div>
    <div class="summary-chip"><span class="label">Ventana j..j+i</span><span class="val">${data.slice_params?.j || "—"} → ${data.state_slice?.length || 0} filas</span></div>`;
}

export function renderStats(stats) {
  const icons = ["💉", "🩹", "⏱️", "⏳", "🗑️", "❌", "📦", "📦"];
  document.getElementById("statsGrid").innerHTML = stats.map((s, i) => `
    <article class="stat-card">
      <div class="stat-icon">${icons[i] || "📊"}</div>
      <div class="stat-value">${s.valor}</div>
      <div class="stat-name">${s.nombre}</div>
      <div class="stat-desc">${s.descripcion}</div>
    </article>`).join("");
}

// --- RUNGE-KUTTA (CON TU GRÁFICO Y ECUACIÓN NUEVA) ---

export function renderRK(rk) {
  if (!rk) return;

  const params = rk.params || {};
  const A = params.A ?? 41.4;
  const B = params.B ?? 0.0575;
  const h = params.h ?? 0.005;
  const t0 = params.t0 ?? 0;
  const R0 = params.R0 ?? 0.076;

  document.getElementById("rkEquation").innerHTML = `
    <span class="eq-badge">dR/dt = ${A}*R - ${B}*R²</span>
    <span class="eq-meta">h = ${h} · t0 = ${t0} · R0 = ${R0}</span>`;

  document.getElementById("rkMax").innerHTML = `Tiempo de vencimiento <strong>X = ${rk.R_max} s</strong>`;

  document.querySelector("#rkTable tbody").innerHTML = rk.steps.slice(0, 148).map((s) => `
    <tr>
      <td>${s.t}</td>
      <td>${s.R}</td>
      <td>${s.k1}</td>
      <td>${s.k2}</td>
      <td>${s.k3}</td>
      <td>${s.k4}</td>
      <td class="highlight">${s.R_next}</td>
    </tr>`).join("");

  const ctx = document.getElementById("rkChart");
  if (rkChart) rkChart.destroy();
  rkChart = new Chart(ctx, {
    type: "line",
    data: {
      labels: rk.steps.map(s => s.t),
      datasets: [{
        label: "R(t)",
        data: rk.steps.map(s => s.R),
        borderColor: "#0d9488",
        backgroundColor: "rgba(13,148,136,0.1)",
        fill: true,
        tension: 0.4,
        pointRadius: 0
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: {
        x: { ticks: { maxTicksLimit: 10 } },
        y: { beginAtZero: true }
      }
    }
  });
}

// --- TABLA VECTOR DE ESTADO COMPLETA ---

let stateColumns = [];

function htmlEscape(value) {
  if (value === null || value === undefined || value === "") return "";
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;");
}

function renderWideStateTable(rows, columns = stateColumns) {
  if (!rows || rows.length === 0) return `<div class="empty-state">No hay datos</div>`;
  const visibleColumns = columns && columns.length
    ? columns
    : Object.keys(rows[0]).filter(k => !["aleatorios", "enfermero", "pacientes", "cajas", "detalle_evento"].includes(k)).map(k => ({ key: k, label: k }));

  return `<div class="wide-table-wrap"><table class="simulation-table wide-state-table">
    <thead><tr>${visibleColumns.map(c => `<th>${htmlEscape(c.label)}</th>`).join("")}</tr></thead>
    <tbody>${rows.map(row => `<tr>${visibleColumns.map(c => {
      const value = row[c.key];
      const klass = c.key === "evento" ? "event-cell" : (String(c.key).includes("rnd") ? "rnd-cell" : "");
      return `<td class="${klass}">${htmlEscape(value)}</td>`;
    }).join("")}</tr>`).join("")}</tbody>
  </table></div>`;
}

export function renderStateSlice(rows, columns) {
  if (columns) stateColumns = columns;
  document.getElementById("stateSlice").innerHTML = renderWideStateTable(rows, stateColumns);
}
export function renderStateLast(row, columns) {
  if (columns) stateColumns = columns;
  document.getElementById("stateLast").innerHTML = row ? renderWideStateTable([row], stateColumns) : "";
}

export function renderFullResults(data) {
  renderSummary(data);
  renderStats(data.statistics);
  renderRK(data.runge_kutta);
  renderStateSlice(data.state_slice, data.state_columns);
  renderStateLast(data.last_row, data.state_columns);
  showResults(true);
  activateTab("stats");
}