/**
 * Cliente HTTP delgado para hablar con el back.
 * Centraliza base URL, JSON parsing, manejo de errores.
 */

const BASE_URL = import.meta.env.BACKEND_API_BASE_URL ?? "http://localhost:8080/api";

export const USE_MOCK = import.meta.env.VITE_USE_MOCK === "true";

export class ApiError extends Error {
  constructor(message, { status, body } = {}) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
  }
}

async function request(method, path, body) {
  const url = `${BASE_URL}${path}`;
  const init = {
    method,
    headers: { "Content-Type": "application/json", Accept: "application/json" },
  };
  if (body !== undefined) init.body = JSON.stringify(body);

  let res;
  try {
    res = await fetch(url, init);
  } catch (e) {
    throw new ApiError(`No se pudo conectar con ${url}`, { status: 0 });
  }

  const text = await res.text();
  const data = text ? safeJson(text) : null;

  if (!res.ok) {
    const msg = (data && (data.message || data.error)) || `Error ${res.status}`;
    throw new ApiError(msg, { status: res.status, body: data });
  }
  return data;
}

function safeJson(text) {
  try { return JSON.parse(text); } catch { return text; }
}

export const apiGet    = (path)         => request("GET",    path);
export const apiPost   = (path, body)   => request("POST",   path, body);
export const apiPut    = (path, body)   => request("PUT",    path, body);
export const apiDelete = (path)         => request("DELETE", path);
