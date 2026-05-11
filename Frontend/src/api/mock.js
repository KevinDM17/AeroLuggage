/**
 * Datos en memoria para correr el front sin back.
 * Los rangos coinciden con el enunciado del PDF:
 * - Almacen 500-800 maletas
 * - Vuelos mismo continente 150-250
 * - Vuelos distinto continente 150-400
 */

const delay = (ms = 250) => new Promise((r) => setTimeout(r, ms));

// ---------- AEROPUERTOS ----------
let _airports = [
  { iata: "LIM", name: "Jorge Chavez",      city: "Lima, Peru",         continent: "Sudamerica",    gmt: -5, capacity: 700, used: 480 },
  { iata: "BOG", name: "El Dorado",         city: "Bogota, Colombia",   continent: "Sudamerica",    gmt: -5, capacity: 650, used: 612 },
  { iata: "GRU", name: "Guarulhos",         city: "Sao Paulo, Brasil",  continent: "Sudamerica",    gmt: -3, capacity: 800, used: 530 },
  { iata: "MIA", name: "Miami Intl",        city: "Miami, EEUU",        continent: "Norteamerica",  gmt: -5, capacity: 750, used: 720 },
  { iata: "JFK", name: "John F. Kennedy",   city: "New York, EEUU",     continent: "Norteamerica",  gmt: -5, capacity: 780, used: 410 },
  { iata: "MAD", name: "Barajas",           city: "Madrid, Espana",     continent: "Europa",        gmt:  1, capacity: 720, used: 360 },
  { iata: "LHR", name: "Heathrow",          city: "London, UK",         continent: "Europa",        gmt:  0, capacity: 800, used: 420 },
  { iata: "NRT", name: "Narita",            city: "Tokio, Japon",       continent: "Asia",          gmt:  9, capacity: 760, used: 540 },
  { iata: "HKG", name: "Hong Kong Intl",    city: "Hong Kong",          continent: "Asia",          gmt:  8, capacity: 700, used: 460 },
  { iata: "DXB", name: "Dubai Intl",        city: "Dubai, EAU",         continent: "Asia",          gmt:  4, capacity: 800, used: 615 },
];

export async function mockListAirports() { await delay(); return [..._airports]; }
export async function mockCreateAirport(payload) {
  await delay();
  if (_airports.some(a => a.iata === payload.iata)) {
    throw new Error(`Ya existe un aeropuerto con IATA ${payload.iata}`);
  }
  const created = { used: 0, ...payload };
  _airports = [..._airports, created];
  return created;
}

// ---------- VUELOS ----------
let _flights = [
  { id: "LA201", origin: "LIM", dest: "MIA", depTime: "10:00", arrTime: "16:30", status: "Finalizado", capacity: 250, used: 220 },
  { id: "AV105", origin: "BOG", dest: "MAD", depTime: "14:00", arrTime: "06:00", status: "Cancelado",  capacity: 400, used: 0   },
  { id: "G3102", origin: "GRU", dest: "LIM", depTime: "08:00", arrTime: "11:30", status: "En progreso", capacity: 200, used: 150 },
  { id: "AA908", origin: "MIA", dest: "MAD", depTime: "18:00", arrTime: "08:30", status: "Confirmado", capacity: 350, used: 300 },
  { id: "JL061", origin: "NRT", dest: "HKG", depTime: "11:00", arrTime: "15:00", status: "Confirmado", capacity: 220, used: 180 },
];

export async function mockListFlights() { await delay(); return [..._flights]; }
export async function mockCreateFlight(payload) {
  await delay();
  if (_flights.some(f => f.id === payload.id)) {
    throw new Error(`Ya existe un vuelo con codigo ${payload.id}`);
  }
  const created = { status: "Confirmado", used: 0, ...payload };
  _flights = [..._flights, created];
  return created;
}
export async function mockCancelFlight(id) {
  await delay();
  const idx = _flights.findIndex(f => f.id === id);
  if (idx < 0) throw new Error(`Vuelo ${id} no existe`);
  _flights[idx] = { ..._flights[idx], status: "Cancelado", used: 0 };
  return _flights[idx];
}
export async function mockBulkUploadFlights(text) {
  await delay(600);
  const lines = String(text).split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  return { received: lines.length, accepted: lines.length, rejected: 0 };
}

// ---------- PEDIDOS / ENVIOS ----------
let _orders = [
  { id: "SHP-001", clientId: "CUST-101", origin: "LIM", dest: "MIA", bags: 45,  date: "2026-04-14", time: "10:30", status: "Procesando" },
  { id: "SHP-002", clientId: "CUST-202", origin: "BOG", dest: "MAD", bags: 120, date: "2026-04-14", time: "11:15", status: "Pendiente"  },
  { id: "SHP-003", clientId: "CUST-303", origin: "GRU", dest: "LIM", bags: 30,  date: "2026-04-14", time: "09:00", status: "Enviado"    },
];

export async function mockListOrders() { await delay(); return [..._orders]; }
export async function mockCreateOrder(payload) {
  await delay();
  const id = `SHP-${String(_orders.length + 1).padStart(3, "0")}`;
  const created = { id, status: "Pendiente", ...payload };
  _orders = [..._orders, created];
  return created;
}

// ---------- STATUS / KPIs en tiempo real ----------
export async function mockGetStatus() {
  await delay(120);
  // Pequenas fluctuaciones para que se note el polling en el demo
  const jitter = (n, range) => Math.max(0, Math.round(n + (Math.random() - 0.5) * range));
  return {
    date: "18-03-26",
    time: new Date().toUTCString().slice(17, 25) + " UTC",
    bagsInTransit:    jitter(825, 30),
    activeFlights:    jitter(7, 2),
    freeCapacityPct:  Math.min(100, Math.max(0, jitter(42, 4))),
    activeAlerts:     jitter(2, 2),
  };
}

// ---------- SIMULADORES ----------
const _sim = {
  period:   { status: "idle", startDate: null, progress: 0, startedAt: null },
  collapse: { status: "idle", startDate: null, elapsedMs: 0, startedAt: null },
};

export async function mockStartPeriodSim(startDate) {
  await delay();
  _sim.period = { status: "running", startDate, progress: 0, startedAt: Date.now() };
  return { ..._sim.period };
}
export async function mockStopPeriodSim() {
  await delay();
  _sim.period = { status: "idle", startDate: null, progress: 0, startedAt: null };
  return { ..._sim.period };
}
export async function mockGetPeriodSimState() {
  await delay(60);
  if (_sim.period.status === "running") {
    const elapsed = Date.now() - _sim.period.startedAt;
    const mockTotalMs = 60_000;
    _sim.period.progress = Math.min(100, (elapsed / mockTotalMs) * 100);
    if (_sim.period.progress >= 100) _sim.period.status = "done";
  }
  return { ..._sim.period };
}

export async function mockStartCollapseSim(startDate) {
  await delay();
  _sim.collapse = { status: "running", startDate, elapsedMs: 0, startedAt: Date.now() };
  return { ..._sim.collapse };
}
export async function mockStopCollapseSim() {
  await delay();
  _sim.collapse = { status: "idle", startDate: null, elapsedMs: 0, startedAt: null };
  return { ..._sim.collapse };
}
export async function mockGetCollapseSimState() {
  await delay(60);
  if (_sim.collapse.status === "running") {
    _sim.collapse.elapsedMs = Date.now() - _sim.collapse.startedAt;
    if (_sim.collapse.elapsedMs >= 90_000) _sim.collapse.status = "collapsed";
  }
  return { ..._sim.collapse };
}
