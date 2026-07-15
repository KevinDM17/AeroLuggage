/**
 * Back -> Front:
 *   { idVueloInstancia, codigo, aeropuertoOrigen, aeropuertoDestino, fechaSalida,
 *     fechaLlegada, capacidadMaxima, capacidadDisponible, capacidadUsada, estado }
 * ->
 *   { id, origin, dest, depTime, arrTime, capacity, used, status }
 */
export const adaptFlightInstance = (f) => ({
  id:       f.idVueloInstancia ?? f.codigo,
  idVueloInstancia: f.idVueloInstancia,
  origin:   typeof f.aeropuertoOrigen === "string" ? f.aeropuertoOrigen : f?.aeropuertoOrigen?.idAeropuerto ?? "",
  dest:     typeof f.aeropuertoDestino === "string" ? f.aeropuertoDestino : f?.aeropuertoDestino?.idAeropuerto ?? "",
  depTime:  f.fechaSalida ?? "",
  arrTime:  f.fechaLlegada ?? "",
  capacity: f.capacidadMaxima ?? 0,
  used:     f.capacidadUsada ?? Math.max(0, (f.capacidadMaxima ?? 0) - (f.capacidadDisponible ?? 0)),
  status:   f.estado ?? "PROGRAMADO",
});
