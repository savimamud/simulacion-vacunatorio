const API = "";

export async function simulate(params) {
  const res = await fetch(`${API}/api/simulate`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || `Error del servidor (${res.status})`);
  }
  return res.json();
}

export async function rungeKutta(params) {
  const res = await fetch(`${API}/api/runge-kutta`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(params),
  });
  if (!res.ok) throw new Error(`Error del servidor (${res.status})`);
  return res.json();
}

export async function checkHealth() {
  const res = await fetch(`${API}/api/health`);
  if (!res.ok) throw new Error("Backend no responde");
  return res.json();
}
