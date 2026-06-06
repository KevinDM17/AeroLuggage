package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaVueloResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionBaseResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionResultadoFinalResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionVentanaDTO;
import pe.edu.pucp.aeroluggage.servicios.query.SimulacionInicioQueryService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesion;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Slf4j
@RestController
@RequestMapping("/api/simulacion/periodo")
@RequiredArgsConstructor
public class SimulacionPeriodoRestController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;
    private final SimulacionInicioQueryService simulacionInicioQueryService;

    @PostMapping("/iniciar")
    public SimulacionEstadoDTO iniciar(@RequestBody final SimulacionIniciarRequest params) {
        log.info("[AeroLuggage/SimulacionRest] - API-CALL/iniciar: fechaInicio: {}, totalDias: {}",
                params.getFechaInicio(), params.getTotalDias());
        return sesionManager.iniciar(params, broker);
    }

    @GetMapping("/{sessionId}/base")
    public SimulacionBaseResponse base(@PathVariable final String sessionId) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion de simulacion no encontrada: " + sessionId);
        }
        final List<AeropuertoResponse> aeropuertos = sesion.getAeropuertos().stream()
                .map(a -> AeropuertoResponse.builder()
                        .withIdAeropuerto(a.getIdAeropuerto())
                        .withCiudad(a.getCiudad())
                        .withCapacidadAlmacen(a.getCapacidadAlmacen())
                        .withMaletasActuales(a.getMaletasActuales())
                        .withLatitud(a.getLatitud())
                        .withLongitud(a.getLongitud())
                        .build())
                .toList();
        final String primeraVentana = sesion.getCurrentWindow().get() != null
                ? sesion.getCurrentWindow().get().getWindowId() : "W0001";
        return SimulacionBaseResponse.builder()
                .withSessionId(sessionId)
                .withFechaInicio(sesion.getFechaInicio().toString())
                .withTotalDias(sesion.getTotalDias())
                .withWindowSizeMinutes(sesion.getWindowSizeMinutes())
                .withWindowSpacingMinutes(sesion.getWindowSpacingMinutes())
                .withDuracionDiaSimuladoMs(sesion.getDuracionDiaSimuladoMs())
                .withPrimeraVentana(primeraVentana)
                .withAeropuertos(aeropuertos)
                .build();
    }

    @GetMapping("/{sessionId}/ventana/{windowId}")
    public SimulacionVentanaDTO ventana(
            @PathVariable final String sessionId,
            @PathVariable final String windowId) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        return construirRespuestaVentana(sesion, windowId);
    }

    @GetMapping("/{sessionId}/vuelos")
    public List<VueloInstanciaResponse> vuelos(
            @PathVariable final String sessionId,
            @RequestParam("desde") final String desde,
            @RequestParam("hasta") final String hasta) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        final long desdeBucket = SimulacionSesion.parseBucket(desde);
        final long hastaBucket = SimulacionSesion.parseBucket(hasta);
        sesion.asegurarVuelosParaBanda(desdeBucket, hastaBucket);
        final List<VueloInstanciaResponse> vuelos = new ArrayList<>();
        for (long i = desdeBucket; i <= hastaBucket; i++) {
            final String w = "W" + String.format("%04d", i);
            final var ventanaVuelos = sesion.getVuelosPorVentana().get(w);
            if (ventanaVuelos != null) {
                for (final var v : ventanaVuelos) {
                    if (v == null) continue;
                    vuelos.add(VueloInstanciaResponse.builder()
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
                            .build());
                }
            }
        }
        return vuelos;
    }

    @GetMapping("/{sessionId}/snapshot")
    public SimulacionInicioResponse snapshot(@PathVariable final String sessionId) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion de simulacion no encontrada: " + sessionId);
        }
        return simulacionInicioQueryService.construirRespuestaInicio(
                SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado("ACTUALIZADA")
                        .withMensaje("Snapshot de simulacion actualizado")
                        .build(),
                sesion
        );
    }

    @GetMapping("/{sessionId}/resultado-final")
    public SimulacionResultadoFinalResponse resultadoFinal(@PathVariable final String sessionId) {
        final var sesion = sesionManager.obtenerSesionFinalizada(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Resultado final de simulacion no encontrado: " + sessionId);
        }
        return simulacionInicioQueryService.construirResultadoFinal(
                "FINALIZADA",
                "Resultado final de simulacion",
                sesion
        );
    }

    private SimulacionVentanaDTO construirRespuestaVentana(
            final SimulacionSesion sesion, final String windowId) {
        final List<MaletaSimulacionResponse> maletasDTO = new ArrayList<>();
        final java.util.LinkedHashMap<String, PedidoSimulacionResponse> pedidosMap = new java.util.LinkedHashMap<>();
        final List<RutaSimulacionResponse> rutasDTO = new ArrayList<>();

        final var maletas = sesion.getMaletasPorVentana().get(windowId);
        if (maletas != null) {
            for (final var m : maletas) {
                if (m == null) continue;
                maletasDTO.add(MaletaSimulacionResponse.builder()
                        .withIdMaleta(m.getIdMaleta())
                        .withIdPedido(m.getPedido() != null ? m.getPedido().getIdPedido() : null)
                        .withFechaRegistro(m.getFechaRegistro() != null
                                ? m.getFechaRegistro().format(FORMATO_FECHA_HORA) : null)
                        .withEstado(m.getEstado() != null ? m.getEstado().name() : null)
                        .build());
                if (m.getPedido() != null && !pedidosMap.containsKey(m.getPedido().getIdPedido())) {
                    final var p = m.getPedido();
                    final String fecha = p.getFechaRegistro() != null ? p.getFechaRegistro().toString() : "";
                    final String date = fecha.length() >= 10 ? fecha.substring(0, 10) : "";
                    final String time = fecha.length() >= 16 ? fecha.substring(11, 16) : "";
                    pedidosMap.put(p.getIdPedido(), PedidoSimulacionResponse.builder()
                            .withId(p.getIdPedido())
                            .withClientId("")
                            .withOrigin(p.getAeropuertoOrigen() != null
                                    ? p.getAeropuertoOrigen().getIdAeropuerto() : null)
                            .withDest(p.getAeropuertoDestino() != null
                                    ? p.getAeropuertoDestino().getIdAeropuerto() : null)
                            .withBags(p.getCantidadMaletas())
                            .withDate(date)
                            .withTime(time)
                            .build());
                }
            }
        }
        final List<PedidoSimulacionResponse> pedidosDTO = new ArrayList<>(pedidosMap.values());

        final java.util.Set<String> idMaletasVentana = maletas != null
                ? maletas.stream()
                        .map(pe.edu.pucp.aeroluggage.dominio.entidades.Maleta::getIdMaleta)
                        .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
                : java.util.Set.of();
        final var rutas = sesion.getRutasPorMaleta().values();
        for (final var r : rutas) {
            if (r == null || r.getIdMaleta() == null || !idMaletasVentana.contains(r.getIdMaleta())) continue;
            final List<RutaVueloResponse> vuelosRuta = r.getSubrutas() == null ? List.of()
                    : r.getSubrutas().stream()
                    .filter(Objects::nonNull)
                    .map(v -> RutaVueloResponse.builder()
                            .withIdVueloInstancia(v.getIdVueloInstancia())
                            .withCodigo(v.getCodigo())
                            .withFechaSalida(v.getFechaSalida() != null
                                    ? v.getFechaSalida().format(FORMATO_FECHA_HORA) : null)
                            .withFechaLlegada(v.getFechaLlegada() != null
                                    ? v.getFechaLlegada().format(FORMATO_FECHA_HORA) : null)
                            .withAeropuertoOrigen(v.getAeropuertoOrigen() != null
                                    ? v.getAeropuertoOrigen().getIdAeropuerto() : null)
                            .withAeropuertoDestino(v.getAeropuertoDestino() != null
                                    ? v.getAeropuertoDestino().getIdAeropuerto() : null)
                            .build())
                    .toList();
            rutasDTO.add(RutaSimulacionResponse.builder()
                    .withIdRuta(r.getIdRuta())
                    .withIdMaleta(r.getIdMaleta())
                    .withPlazoMaximoDias(r.getPlazoMaximoDias())
                    .withDuracion(r.getDuracion())
                    .withEstado(r.getEstado() != null ? r.getEstado().name() : null)
                    .withVuelos(vuelosRuta)
                    .build());
        }

        return SimulacionVentanaDTO.builder()
                .withType("VENTANA")
                .withVentana(windowId)
                .withMaletas(maletasDTO)
                .withPedidos(pedidosDTO)
                .withRutas(rutasDTO)
                .build();
    }
}
