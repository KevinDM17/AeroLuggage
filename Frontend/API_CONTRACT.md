# API Contract — AeroLuggage Frontend ↔ Backend

El frontend consume los endpoints listados abajo. Mientras `VITE_USE_MOCK=true` usa data en memoria; cuando se cambia a `false`, hace `fetch` real contra `VITE_API_BASE_URL`.

**Convenciones:**
- Todas las rutas relativas a `VITE_API_BASE_URL` (default `http://localhost:8080/api`).
- JSON request/response. Header `Content-Type: application/json`.
- Errores: status HTTP correspondiente + body `{ "message": "...", "error": "..." }` (cualquiera de los dos campos sirve para mostrar al usuario).

---

## Aeropuertos

### `GET /airports`
**Response 200:** `Airport[]`

```ts
type Airport = {
  iata: string;        // 3 letras mayúsculas
  name: string;
  city: string;
  continent: "Sudamerica" | "Norteamerica" | "Europa" | "Asia";
  gmt: number;         // -12 a +14
  capacity: number;    // 500-800 (PDF)
  used: number;        // ocupación actual
};
```

### `POST /airports`
**Body:** `Omit<Airport, "used">`

**Response 201:** `Airport` creado

**Errores comunes:** `409` si IATA duplicado, `400` si validación falla.

---

## Vuelos

### `GET /flights`
**Response 200:** `Flight[]`

```ts
type Flight = {
  id: string;          // código del vuelo (ej. LA201)
  origin: string;      // IATA
  dest: string;        // IATA
  depTime: string;     // "HH:MM"
  arrTime: string;     // "HH:MM"
  status: "Confirmado" | "En progreso" | "Finalizado" | "Cancelado";
  capacity: number;    // 150-250 mismo continente | 150-400 distinto continente
  used: number;
};
```

### `POST /flights`
**Body:** `{ id, origin, dest, depTime, arrTime, capacity }`

**Response 201:** `Flight` creado

### `DELETE /flights/{id}`
Cancela un vuelo (cambia `status` a `"Cancelado"` y libera capacidad).
Disparará la replanificación de rutas afectadas (esto lo decide el back).

**Response 200:** `Flight` actualizado

### `POST /flights/bulk`
Carga masiva desde archivo `.txt`.

**Body:** `{ "content": "<contenido del archivo>" }`

**Response 200:**
```ts
{
  received: number;
  accepted: number;
  rejected: number;
  errors?: Array<{ line: number; reason: string }>;
}
```

---

## Pedidos / Envíos

### `GET /orders`
**Response 200:** `Order[]`

```ts
type Order = {
  id: string;          // SHP-001
  clientId: string;    // CUST-101
  origin: string;      // IATA
  dest: string;        // IATA
  bags: number;        // >= 1
  date: string;        // "YYYY-MM-DD"
  time: string;        // "HH:MM"
  status: "Pendiente" | "Procesando" | "Enviado";
};
```

### `POST /orders`
**Body:** `Omit<Order, "id" | "status">`

**Response 201:** `Order` creado (back asigna `id` y `status: "Pendiente"`).

---

## Estado en tiempo real (KPIs)

### `GET /status`
Pollea cada 5s desde todas las vistas con mapa.

**Response 200:**
```ts
{
  date: string;             // "DD-MM-YY" para overlay del mapa
  time: string;             // "HH:MM:SS UTC"
  bagsInTransit: number;
  activeFlights: number;
  freeCapacityPct: number;  // 0-100
  activeAlerts: number;
}
```

---

## Simulador por Periodo (5 días fijos)

### `POST /simulator/period/start`
**Body:** `{ "startDate": "YYYY-MM-DD" }`

**Response 200:** `PeriodSimState`

### `POST /simulator/period/stop`
**Response 200:** `PeriodSimState`

### `GET /simulator/period/state`
Pollea cada 1s mientras `status === "running"`.

```ts
type PeriodSimState = {
  status: "idle" | "running" | "done";
  startDate: string | null;
  progress: number;     // 0-100
};
```

---

## Simulador hasta Colapso

### `POST /simulator/collapse/start`
**Body:** `{ "startDate": "YYYY-MM-DD" }`

**Response 200:** `CollapseSimState`

### `POST /simulator/collapse/stop`
**Response 200:** `CollapseSimState`

### `GET /simulator/collapse/state`
Pollea cada 1s mientras `status === "running"`.

```ts
type CollapseSimState = {
  status: "idle" | "running" | "collapsed";
  startDate: string | null;
  elapsedMs: number;   // tiempo simulado acumulado
};
```

---

## Pendientes / Futuro

- **WebSocket** opcional en `/ws/status` y `/ws/sim/{type}` reemplazará el polling cuando el back esté listo.
- **Detalle de plan de ruta** de una maleta: `GET /bags/{id}/route-plan`.
- **Cancelación con confirmación**: el front pedirá un motivo (string) en el body de `DELETE /flights/{id}` cuando se implemente replanificación visible.
- **Configuración de umbrales semáforo** (`GET/PUT /config/thresholds`): el PDF dice que los rangos verde/ámbar/rojo deben ser parámetros.
