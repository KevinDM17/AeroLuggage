package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AlmacenContenidoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaVueloResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionDiaADiaService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
public class SimulacionDiaADiaRestController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SimulacionDiaADiaService service;

    @PostMapping("/iniciar")
    public SimulacionEstadoDTO iniciar() {
        log.info("[AeroLuggage/DiaADiaRest] - API-CALL/iniciar");
        try {
            final String sessionId = service.iniciar();
            return SimulacionEstadoDTO.builder()
                    .withSessionId(sessionId)
                    .withEstado("INICIADA")
                    .withMensaje("Simulacion dia a dia iniciada correctamente")
                    .build();
        } catch (final IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @PostMapping("/{sessionId}/detener")
    public SimulacionEstadoDTO detener(@PathVariable final String sessionId) {
        log.info("[AeroLuggage/DiaADiaRest] - API-CALL/detener: sessionId={}", sessionId);
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        service.detener();
        return SimulacionEstadoDTO.builder()
                .withSessionId(sessionId)
                .withEstado("DETENIDA")
                .withMensaje("Simulacion dia a dia detenida")
                .build();
    }

    @PostMapping("/{sessionId}/pedido")
    public SimulacionEstadoDTO procesarPedido(
            @PathVariable final String sessionId,
            @RequestBody final PedidoRequest request) {
        log.info("[AeroLuggage/DiaADiaRest] - API-CALL/pedido: sessionId={}, origen={}, destino={}",
                sessionId, request.getIdAeropuertoOrigen(), request.getIdAeropuertoDestino());
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        try {
            service.procesarPedido(request);
            return SimulacionEstadoDTO.builder()
                    .withSessionId(sessionId)
                    .withEstado("PEDIDO_PROCESADO")
                    .withMensaje("Pedido procesado correctamente")
                    .build();
        } catch (final IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (final IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @PostMapping("/{sessionId}/pedidos-bulk")
    public Map<String, Object> procesarPedidosBulk(
            @PathVariable final String sessionId,
            @RequestBody final Map<String, Object> body) {
        final String icaoOrigen = (String) body.get("icaoOrigen");
        final String content = (String) body.get("content");
        if (icaoOrigen == null || icaoOrigen.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "icaoOrigen es requerido");
        }
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content es requerido");
        }
        log.info("[AeroLuggage/DiaADiaRest] - API-CALL/pedidos-bulk: sessionId={}, origen={}",
                sessionId, icaoOrigen);
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<String> lineas = List.of(content.split("\\R"));
        try {
            service.procesarPedidosBulk(icaoOrigen, lineas);
            final Map<String, Object> response = new HashMap<>();
            response.put("accepted", lineas.stream().filter(l -> !l.trim().isEmpty()).count());
            response.put("total", lineas.stream().filter(l -> !l.trim().isEmpty()).count());
            return response;
        } catch (final IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (final IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @GetMapping("/{sessionId}/estado")
    public Map<String, Object> estado(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("activa", service.isActiva());
        response.put("simTime", service.getSimTimeActual().format(FORMATO_FECHA_HORA));
        response.put("tick", service.getTickActual());
        response.put("aeropuertos", service.getAeropuertos().size());
        response.put("vuelosInstancia", service.getVuelosInstancia().size());
        response.put("pedidos", service.getPedidos().size());
        response.put("maletas", service.getMaletas().size());
        response.put("rutas", service.getRutas().size());
        return response;
    }

    @GetMapping("/estado-actual")
    public ResponseEntity<Map<String, Object>> estadoActual() {
        log.info("[AeroLuggage/DiaADiaRest] - API-CALL/estado-actual");
        final Map<String, Object> estado = service.obtenerEstadoActual();
        if (estado.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        final List<AeropuertoResponse> aeropuertos = service.getAeropuertos().stream()
                .map(a -> AeropuertoResponse.builder()
                        .withIdAeropuerto(a.getIdAeropuerto())
                        .withCiudad(a.getCiudad())
                        .withCapacidadAlmacen(a.getCapacidadAlmacen())
                        .withMaletasActuales(a.getMaletasActuales())
                        .withLatitud(a.getLatitud())
                        .withLongitud(a.getLongitud())
                        .build())
                .toList();
        final List<VueloInstanciaResponse> vuelos = new ArrayList<>();
        for (final VueloInstancia v : service.getVuelosFrontend()) {
            if (v == null) continue;
            vuelos.add(toVueloResponse(v));
        }
        estado.put("aeropuertos", aeropuertos);
        estado.put("vuelos", vuelos);
        return ResponseEntity.ok(estado);
    }

    @PostMapping("/{sessionId}/confirmar-conexion")
    public SimulacionEstadoDTO confirmarConexion(@PathVariable final String sessionId) {
        log.info("[AeroLuggage/DiaADiaRest] - API-CALL/confirmar-conexion: sessionId={}", sessionId);
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        try {
            service.confirmarConexion();
            return SimulacionEstadoDTO.builder()
                    .withSessionId(sessionId)
                    .withEstado("CONEXION_CONFIRMADA")
                    .withMensaje("Conexion confirmada, ticks iniciados")
                    .build();
        } catch (final IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @GetMapping("/{sessionId}/vuelos")
    public List<VueloInstanciaResponse> vuelos(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<VueloInstanciaResponse> result = new ArrayList<>();
        for (final VueloInstancia v : service.getVuelosFrontend()) {
            if (v == null) continue;
            result.add(toVueloResponse(v));
        }
        log.info("[AeroLuggage/DiaADiaRest] - RESPUESTA/vuelos: {} vuelos (CONFIRMADO+EN_PROGRESO)", result.size());
        return result;
    }

    @GetMapping("/{sessionId}/vuelos-nuevos")
    public List<VueloInstanciaResponse> vuelosNuevos(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<VueloInstanciaResponse> result = new ArrayList<>();
        for (final VueloInstancia v : service.getVuelosNuevos()) {
            if (v == null) continue;
            result.add(toVueloResponse(v));
        }
        log.info("[AeroLuggage/DiaADiaRest] - RESPUESTA/vuelos-nuevos: {} vuelos", result.size());
        return result;
    }

    @GetMapping("/{sessionId}/vuelo/{idVuelo}/manifiesto")
    public Map<String, Object> manifiestoVuelo(
            @PathVariable final String sessionId,
            @PathVariable final String idVuelo) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final Map<String, VueloInstancia> vueloIndex = service.getVueloIndex();
        final VueloInstancia objetivo = vueloIndex.get(idVuelo);
        final String codigoObjetivo = objetivo != null ? objetivo.getCodigo() : null;
        final List<Map<String, String>> maletasDTO = new ArrayList<>();
        for (final Ruta ruta : service.getRutas()) {
            if (ruta == null || ruta.getIdMaleta() == null) continue;
            if (!rutaIncluyeVuelo(ruta, idVuelo, codigoObjetivo, vueloIndex)) continue;
            final Maleta maleta = service.getMaleta(ruta.getIdMaleta());
            final Pedido pedido = maleta != null ? maleta.getPedido() : null;
            final Map<String, String> m = new HashMap<>();
            m.put("idMaleta", ruta.getIdMaleta());
            m.put("idPedido", pedido != null ? pedido.getIdPedido() : null);
            m.put("estado", maleta != null && maleta.getEstado() != null
                    ? maleta.getEstado().name() : null);
            m.put("ubicacion", maleta != null ? maleta.getAeropuertoActual() : null);
            maletasDTO.add(m);
        }
        final Map<String, Object> response = new HashMap<>();
        response.put("idVueloInstancia", objetivo != null ? objetivo.getIdVueloInstancia() : idVuelo);
        response.put("codigo", codigoObjetivo);
        response.put("maletas", maletasDTO);
        response.put("totalMaletas", maletasDTO.size());
        return response;
    }

    @GetMapping("/{sessionId}/maleta/{idMaleta}/ruta")
    public RutaSimulacionResponse rutaMaleta(
            @PathVariable final String sessionId,
            @PathVariable final String idMaleta) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final Ruta ruta = service.getRutaPorMaleta(idMaleta);
        if (ruta == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Ruta no encontrada para la maleta: " + idMaleta);
        }
        return mapearRutaPorIds(ruta, service.getVueloIndex());
    }

    @GetMapping("/{sessionId}/rutas")
    public List<RutaSimulacionResponse> rutas(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<RutaSimulacionResponse> result = new ArrayList<>();
        for (final Ruta r : service.getRutas()) {
            if (r == null) continue;
            final EstadoRuta estado = r.getEstado();
            if (estado != EstadoRuta.PLANIFICADA && estado != EstadoRuta.ACTIVA) continue;
            result.add(RutaSimulacionResponse.builder()
                    .withIdRuta(r.getIdRuta())
                    .withIdMaleta(r.getIdMaleta())
                    .withEstado(estado.name())
                    .build());
        }
        return result;
    }

    @GetMapping("/{sessionId}/almacen/{idAeropuerto}/contenido")
    public AlmacenContenidoResponse contenidoAlmacen(
            @PathVariable final String sessionId,
            @PathVariable final String idAeropuerto) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<MaletaSimulacionResponse> finales = new ArrayList<>();
        final List<MaletaSimulacionResponse> transito = new ArrayList<>();
        int totalFinales = 0;
        int totalTransito = 0;
        for (final Maleta m : service.getMaletas()) {
            if (m == null) continue;
            if (!idAeropuerto.equals(m.getAeropuertoActual())) continue;
            final Pedido p = m.getPedido();
            final boolean destinoFinal = p != null && p.getAeropuertoDestino() != null
                    && idAeropuerto.equals(p.getAeropuertoDestino().getIdAeropuerto());
            final MaletaSimulacionResponse dto = MaletaSimulacionResponse.builder()
                    .withIdMaleta(m.getIdMaleta())
                    .withIdPedido(p != null ? p.getIdPedido() : null)
                    .withEstado(m.getEstado() != null ? m.getEstado().name() : null)
                    .withUbicacionActual(m.getAeropuertoActual())
                    .build();
            if (destinoFinal) {
                totalFinales++;
                finales.add(dto);
            } else {
                totalTransito++;
                transito.add(dto);
            }
        }
        return AlmacenContenidoResponse.builder()
                .withIdAeropuerto(idAeropuerto)
                .withTotalMaletasDestinoFinal(totalFinales)
                .withTotalMaletasEnTransito(totalTransito)
                .withMaletasDestinoFinal(finales)
                .withMaletasEnTransito(transito)
                .withPedidosDestinoFinal(List.of())
                .withPedidosEnTransito(List.of())
                .withTotalMaletasEntran(0)
                .withTotalMaletasSalen(0)
                .withPedidosEntran(List.of())
                .withPedidosSalen(List.of())
                .withMaletasEntran(List.of())
                .withMaletasSalen(List.of())
                .build();
    }

    @GetMapping("/{sessionId}/aeropuertos")
    public List<AeropuertoResponse> aeropuertos(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        return service.getAeropuertos().stream()
                .map(a -> AeropuertoResponse.builder()
                        .withIdAeropuerto(a.getIdAeropuerto())
                        .withCiudad(a.getCiudad())
                        .withCapacidadAlmacen(a.getCapacidadAlmacen())
                        .withMaletasActuales(a.getMaletasActuales())
                        .withLatitud(a.getLatitud())
                        .withLongitud(a.getLongitud())
                        .build())
                .toList();
    }

    @GetMapping("/{sessionId}/pedidos")
    public List<PedidoSimulacionResponse> pedidos(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<PedidoSimulacionResponse> result = new ArrayList<>();
        for (final Pedido p : service.getPedidosNoEntregados()) {
            if (p == null) continue;
            final String fechaRegistro = p.getFechaRegistro() != null
                    ? p.getFechaRegistro().toString() : "";
            final String date = fechaRegistro.length() >= 10
                    ? fechaRegistro.substring(0, 10) : "";
            final String time = fechaRegistro.length() >= 16
                    ? fechaRegistro.substring(11, 16) : "";
            result.add(PedidoSimulacionResponse.builder()
                    .withId(p.getIdPedido())
                    .withOrigin(p.getAeropuertoOrigen() != null
                            ? p.getAeropuertoOrigen().getIdAeropuerto() : null)
                    .withDest(p.getAeropuertoDestino() != null
                            ? p.getAeropuertoDestino().getIdAeropuerto() : null)
                    .withBags(p.getCantidadMaletas())
                    .withDate(date)
                    .withTime(time)
                    .withStatus(p.getEstado() != null ? p.getEstado().name() : null)
                    .build());
        }
        return result;
    }

    @GetMapping("/{sessionId}/maletas")
    public List<MaletaSimulacionResponse> maletas(@PathVariable final String sessionId) {
        if (!sessionId.equals(service.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Sesion expirada o no encontrada: " + sessionId);
        }
        final List<MaletaSimulacionResponse> result = new ArrayList<>();
        for (final Maleta m : service.getMaletas()) {
            if (m == null) continue;
            result.add(MaletaSimulacionResponse.builder()
                    .withIdMaleta(m.getIdMaleta())
                    .withIdPedido(m.getPedido() != null ? m.getPedido().getIdPedido() : null)
                    .withFechaRegistro(m.getFechaRegistro() != null
                            ? m.getFechaRegistro().format(FORMATO_FECHA_HORA) : null)
                    .withFechaLlegada(m.getFechaLlegada() != null
                            ? m.getFechaLlegada().format(FORMATO_FECHA_HORA) : null)
                    .withEstado(m.getEstado() != null ? m.getEstado().name() : null)
                    .withUbicacionActual(m.getAeropuertoActual())
                    .build());
        }
        return result;
    }

    private RutaSimulacionResponse mapearRutaPorIds(
            final Ruta ruta, final Map<String, VueloInstancia> vueloIndex) {
        final List<RutaVueloResponse> vuelos = new ArrayList<>();
        for (final String subId : ruta.getSubrutaIds()) {
            final VueloInstancia leg = vueloIndex.get(subId);
            if (leg == null) continue;
            vuelos.add(RutaVueloResponse.builder()
                    .withIdVueloInstancia(leg.getIdVueloInstancia())
                    .withCodigo(leg.getCodigo())
                    .withFechaSalida(leg.getFechaSalida() != null
                            ? leg.getFechaSalida().format(FORMATO_FECHA_HORA) : null)
                    .withFechaLlegada(leg.getFechaLlegada() != null
                            ? leg.getFechaLlegada().format(FORMATO_FECHA_HORA) : null)
                    .withAeropuertoOrigen(leg.getAeropuertoOrigen() != null
                            ? leg.getAeropuertoOrigen().getIdAeropuerto() : null)
                    .withAeropuertoDestino(leg.getAeropuertoDestino() != null
                            ? leg.getAeropuertoDestino().getIdAeropuerto() : null)
                    .build());
        }
        return RutaSimulacionResponse.builder()
                .withIdRuta(ruta.getIdRuta())
                .withIdMaleta(ruta.getIdMaleta())
                .withPlazoMaximoDias(ruta.getPlazoMaximoDias())
                .withDuracion(ruta.getDuracion())
                .withEstado(ruta.getEstado() != null ? ruta.getEstado().name() : null)
                .withVuelos(vuelos)
                .build();
    }

    private static boolean rutaIncluyeVuelo(
            final Ruta ruta, final String idVuelo, final String codigoObjetivo,
            final Map<String, VueloInstancia> vueloIndex) {
        for (final String subId : ruta.getSubrutaIds()) {
            if (subId == null) continue;
            if (subId.equals(idVuelo)) return true;
            if (codigoObjetivo != null) {
                final VueloInstancia sv = vueloIndex.get(subId);
                if (sv != null && codigoObjetivo.equals(sv.getCodigo())) return true;
            }
        }
        for (final String subId : ruta.getSubrutas()) {
            if (subId == null) continue;
            final VueloInstancia sv = vueloIndex.get(subId);
            if (sv == null) continue;
            if (idVuelo.equals(sv.getIdVueloInstancia()) || idVuelo.equals(sv.getCodigo())) return true;
        }
        return false;
    }

    private static VueloInstanciaResponse toVueloResponse(final VueloInstancia v) {
        return VueloInstanciaResponse.builder()
                .withIdVueloInstancia(v.getIdVueloInstancia())
                .withCodigo(v.getCodigo())
                .withAeropuertoOrigen(v.getAeropuertoOrigen() != null
                        ? v.getAeropuertoOrigen().getIdAeropuerto() : null)
                .withAeropuertoDestino(v.getAeropuertoDestino() != null
                        ? v.getAeropuertoDestino().getIdAeropuerto() : null)
                .withFechaSalida(v.getFechaSalida() != null
                        ? v.getFechaSalida().format(FORMATO_FECHA_HORA) : null)
                .withFechaLlegada(v.getFechaLlegada() != null
                        ? v.getFechaLlegada().format(FORMATO_FECHA_HORA) : null)
                .withCapacidadMaxima(v.getCapacidadMaxima())
                .withCapacidadDisponible(v.getCapacidadDisponible())
                .withCapacidadUsada(Math.max(0, v.getCapacidadMaxima() - v.getCapacidadDisponible()))
                .withEstado(v.getEstado() != null ? v.getEstado().name() : null)
                .build();
    }
}
