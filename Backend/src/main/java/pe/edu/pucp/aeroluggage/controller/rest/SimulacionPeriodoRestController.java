package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
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
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AlmacenContenidoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.EnvioPanelResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.EnviosPanelResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MovimientoPlanificadoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaVueloResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionBaseResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionIniciarRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionResultadoFinalResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.SimulacionSesionResumenDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloManifiestoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionVentanaDTO;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.servicios.query.SimulacionInicioQueryService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesion;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesionManager;
import pe.edu.pucp.aeroluggage.repositorio.RutaRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloInstanciaRepositorio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/simulacion/periodo")
@RequiredArgsConstructor
public class SimulacionPeriodoRestController {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SimulacionSesionManager sesionManager;
    private final SimpMessagingTemplate broker;
    private final SimulacionInicioQueryService simulacionInicioQueryService;
    private final RutaRepositorio rutaRepositorio;
    private final VueloInstanciaRepositorio vueloInstanciaRepositorio;

    @PostMapping("/iniciar")
    public SimulacionEstadoDTO iniciar(@RequestBody final SimulacionIniciarRequest params) {
        return sesionManager.iniciar(params, broker);
    }

    @GetMapping("/sesiones")
    public List<SimulacionSesionResumenDTO> listarSesiones(
            @RequestParam(defaultValue = "PERIODO") final String tipo) {
        return sesionManager.listarSesionesActivas(tipo);
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
        final var result = construirRespuestaVentana(sesion, windowId);
        return result;
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

    @GetMapping("/{sessionId}/vuelo/{idVuelo}/manifiesto")
    public VueloManifiestoResponse manifiestoVuelo(
            @PathVariable final String sessionId,
            @PathVariable final String idVuelo) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        return construirManifiestoVuelo(sesion, idVuelo);
    }

    @GetMapping("/{sessionId}/maleta/{idMaleta}/ruta")
    public RutaSimulacionResponse rutaMaleta(
            @PathVariable final String sessionId,
            @PathVariable final String idMaleta) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        final Ruta ruta = sesion.getRutaPorMaleta(idMaleta);
        if (ruta == null) {
            throw new ResponseStatusException(NOT_FOUND,
                    "Ruta no encontrada para la maleta: " + idMaleta);
        }
        return mapearRutaPorIds(ruta, sesion.getVueloIndex());
    }

    @GetMapping("/{sessionId}/envio/{idPedido}/rutas")
    public List<RutaSimulacionResponse> rutasEnvio(
            @PathVariable final String sessionId,
            @PathVariable final String idPedido) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        final Map<String, VueloInstancia> vueloIndex = sesion.getVueloIndex();
        final List<RutaSimulacionResponse> rutas = new ArrayList<>();
        for (final Maleta m : sesion.getMaletas()) {
            if (m == null || m.getPedido() == null) continue;
            if (!idPedido.equals(m.getPedido().getIdPedido())) continue;
            final Ruta ruta = sesion.getRutaPorMaleta(m.getIdMaleta());
            if (ruta != null) rutas.add(mapearRutaPorIds(ruta, vueloIndex));
        }
        return rutas;
    }

    // Resuelve las escalas (vuelos) de una ruta desde subrutaIds (siempre
    // presentes, caliente o fria) contra el indice global de vuelos.
    private RutaSimulacionResponse mapearRutaPorIds(final Ruta ruta, final Map<String, VueloInstancia> vueloIndex) {
        final List<RutaVueloResponse> vuelos = new ArrayList<>();
        for (final String subId : ruta.getSubrutaIds()) {
            final VueloInstancia leg = vueloIndex.get(subId);
            if (leg == null) continue;
            vuelos.add(RutaVueloResponse.builder()
                    .withIdVueloInstancia(leg.getIdVueloInstancia())
                    .withCodigo(leg.getCodigo())
                    .withFechaSalida(leg.getFechaSalida() != null ? leg.getFechaSalida().format(FORMATO_FECHA_HORA) : null)
                    .withFechaLlegada(leg.getFechaLlegada() != null ? leg.getFechaLlegada().format(FORMATO_FECHA_HORA) : null)
                    .withAeropuertoOrigen(leg.getAeropuertoOrigen() != null ? leg.getAeropuertoOrigen().getIdAeropuerto() : null)
                    .withAeropuertoDestino(leg.getAeropuertoDestino() != null ? leg.getAeropuertoDestino().getIdAeropuerto() : null)
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

    private String obtenerHoraLlegadaEstimada(final String idMaleta,
                                              final Map<String, VueloInstancia> vueloIndex,
                                              final Map<String, Ruta> rutasPorMaleta) {
        final Ruta r = rutasPorMaleta.get(idMaleta);
        if (r == null) return null;
        final List<String> ids = r.getSubrutas();
        if (ids == null || ids.isEmpty()) return null;
        final VueloInstancia ultimo = vueloIndex.get(ids.get(ids.size() - 1));
        if (ultimo == null || ultimo.getFechaLlegada() == null) return null;
        return ultimo.getFechaLlegada().format(FORMATO_FECHA_HORA);
    }

    @GetMapping("/{sessionId}/almacen/{idAeropuerto}/contenido")
    public AlmacenContenidoResponse contenidoAlmacen(
            @PathVariable final String sessionId,
            @PathVariable final String idAeropuerto) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        return construirContenidoAlmacen(sesion, idAeropuerto);
    }

    @GetMapping("/{sessionId}/envios")
    public EnviosPanelResponse envios(@PathVariable final String sessionId) {
        final var sesion = sesionManager.obtenerSesion(sessionId);
        if (sesion == null) {
            throw new ResponseStatusException(NOT_FOUND, "Sesion no encontrada: " + sessionId);
        }
        return construirEnviosPanel(sesion);
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

    private VueloManifiestoResponse construirManifiestoVuelo(
            final SimulacionSesion sesion, final String idVuelo) {
        final Map<String, VueloInstancia> vueloIndex = sesion.getVueloIndex();
        final VueloInstancia objetivo = vueloIndex.get(idVuelo);
        final LocalDateTime ahora = sesion.getUltimoTiempoSim();

        // Indexamos maletas (calientes + frias) para resolver el pedido de cada una.
        final Map<String, Maleta> maletasPorId = new HashMap<>();
        for (final Maleta m : sesion.getMaletas()) {
            if (m != null && m.getIdMaleta() != null) {
                maletasPorId.putIfAbsent(m.getIdMaleta(), m);
            }
        }

        final List<MaletaSimulacionResponse> maletasDTO = new ArrayList<>();
        final LinkedHashMap<String, int[]> bagsPorPedido = new LinkedHashMap<>();
        final Map<String, Pedido> pedidoPorId = new HashMap<>();

        for (final Ruta ruta : sesion.getRutas()) {
            if (ruta == null || ruta.getIdMaleta() == null) continue;

            final String idMaleta = ruta.getIdMaleta();
            final Maleta maleta = maletasPorId.get(idMaleta);
            if (!maletaCorrespondeAlVuelo(ruta, maleta, objetivo, vueloIndex, ahora)) continue;
            final Pedido pedido = maleta != null ? maleta.getPedido() : null;
            final String idPedido = pedido != null ? pedido.getIdPedido() : null;

            maletasDTO.add(MaletaSimulacionResponse.builder()
                    .withIdMaleta(idMaleta)
                    .withIdPedido(idPedido)
                    .withEstado(maleta != null && maleta.getEstado() != null ? maleta.getEstado().name() : null)
                    .withUbicacionActual(maleta != null ? maleta.getAeropuertoActual() : null)
                    .build());

            if (idPedido != null) {
                bagsPorPedido.computeIfAbsent(idPedido, k -> new int[]{0})[0]++;
                pedidoPorId.putIfAbsent(idPedido, pedido);
            }
        }

        final List<PedidoSimulacionResponse> pedidosDTO = new ArrayList<>();
        for (final Map.Entry<String, int[]> entry : bagsPorPedido.entrySet()) {
            final Pedido pedido = pedidoPorId.get(entry.getKey());
            pedidosDTO.add(PedidoSimulacionResponse.builder()
                    .withId(entry.getKey())
                    .withOrigin(pedido != null && pedido.getAeropuertoOrigen() != null
                            ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null)
                    .withDest(pedido != null && pedido.getAeropuertoDestino() != null
                            ? pedido.getAeropuertoDestino().getIdAeropuerto() : null)
                    .withBags(entry.getValue()[0])
                    .build());
        }

        return VueloManifiestoResponse.builder()
                .withIdVueloInstancia(objetivo != null ? objetivo.getIdVueloInstancia() : idVuelo)
                .withCodigo(objetivo != null ? objetivo.getCodigo() : idVuelo)
                .withMaletas(maletasDTO)
                .withPedidos(pedidosDTO)
                .build();
    }

    private boolean maletaCorrespondeAlVuelo(
            final Ruta ruta,
            final Maleta maleta,
            final VueloInstancia objetivo,
            final Map<String, VueloInstancia> vueloIndex,
            final LocalDateTime ahora) {
        if (ruta == null || maleta == null || objetivo == null) return false;
        if (maleta.getEstado() == EstadoMaleta.ENTREGADA) return false;

        final List<String> subrutas = ruta.getSubrutaIds();
        if (subrutas == null || subrutas.isEmpty()) return false;

        if (maleta.getEstado() == EstadoMaleta.EN_TRANSITO) {
            for (final String subId : subrutas) {
                final VueloInstancia vuelo = vueloIndex.get(subId);
                if (vuelo == null) continue;
                final boolean enCursoPorEstado = vuelo.getEstado() == EstadoVuelo.EN_PROGRESO;
                final boolean enCursoPorTiempo = ahora != null
                        && vuelo.getFechaSalida() != null
                        && vuelo.getFechaLlegada() != null
                        && !ahora.isBefore(vuelo.getFechaSalida())
                        && ahora.isBefore(vuelo.getFechaLlegada());
                if (enCursoPorEstado || enCursoPorTiempo) {
                    return mismoVuelo(vuelo, objetivo);
                }
            }
            return false;
        }

        final String ubicacionActual = maleta.getAeropuertoActual();
        if (ubicacionActual == null) return false;

        for (final String subId : subrutas) {
            final VueloInstancia vuelo = vueloIndex.get(subId);
            if (vuelo == null || vuelo.getAeropuertoOrigen() == null) continue;
            if (!ubicacionActual.equals(vuelo.getAeropuertoOrigen().getIdAeropuerto())) continue;
            return mismoVuelo(vuelo, objetivo);
        }
        return false;
    }

    private boolean mismoVuelo(final VueloInstancia vuelo, final VueloInstancia objetivo) {
        if (vuelo == null || objetivo == null) return false;
        if (vuelo.getIdVueloInstancia() != null && vuelo.getIdVueloInstancia().equals(objetivo.getIdVueloInstancia())) {
            return true;
        }
        return vuelo.getCodigo() != null && vuelo.getCodigo().equals(objetivo.getCodigo());
    }

    private static final int MAX_MALETAS_CONTENIDO = 500;
    private static final int MAX_ENVIOS_CATEGORIA = 500;
    private static final int HORAS_ENTREGA_RECIENTE = 4;

    private EnviosPanelResponse construirEnviosPanel(final SimulacionSesion sesion) {
        final LocalDateTime ahora = sesion.getUltimoTiempoSim();
        final LocalDateTime corteEntrega = ahora != null ? ahora.minusHours(HORAS_ENTREGA_RECIENTE) : null;
        final Map<String, VueloInstancia> vueloIndex = sesion.getVueloIndex();

        // Agrupar maletas por pedido (calientes + frias).
        final Map<String, List<Maleta>> maletasPorPedido = new LinkedHashMap<>();
        final Map<String, Pedido> pedidoPorId = new HashMap<>();
        for (final Maleta m : sesion.getMaletas()) {
            if (m == null || m.getPedido() == null || m.getPedido().getIdPedido() == null) continue;
            final String idPedido = m.getPedido().getIdPedido();
            maletasPorPedido.computeIfAbsent(idPedido, k -> new ArrayList<>()).add(m);
            pedidoPorId.putIfAbsent(idPedido, m.getPedido());
        }

        final List<EnvioPanelResponse> planificados = new ArrayList<>();
        final List<EnvioPanelResponse> enVuelos = new ArrayList<>();
        final List<EnvioPanelResponse> entregados = new ArrayList<>();

        for (final Map.Entry<String, List<Maleta>> entry : maletasPorPedido.entrySet()) {
            final List<Maleta> maletas = entry.getValue();

            boolean todasEntregadas = true;
            boolean algunaEnVuelo = false;
            LocalDateTime ultimaEntrega = null;
            for (final Maleta m : maletas) {
                final EstadoMaleta estado = m.getEstado();
                if (estado != EstadoMaleta.ENTREGADA) todasEntregadas = false;
                if (estado == EstadoMaleta.EN_TRANSITO) algunaEnVuelo = true;
                if (estado == EstadoMaleta.ENTREGADA && m.getFechaLlegada() != null
                        && (ultimaEntrega == null || m.getFechaLlegada().isAfter(ultimaEntrega))) {
                    ultimaEntrega = m.getFechaLlegada();
                }
            }

            // Decidir categoria antes de resolver rutas (evita trabajo en entregados viejos).
            final String categoria;
            if (todasEntregadas) {
                if (corteEntrega == null || ultimaEntrega == null || ultimaEntrega.isBefore(corteEntrega)) {
                    continue; // entregado fuera de la ventana de 4 h: no se muestra
                }
                if (entregados.size() >= MAX_ENVIOS_CATEGORIA) continue;
                categoria = "ENTREGADO";
            } else if (algunaEnVuelo) {
                if (enVuelos.size() >= MAX_ENVIOS_CATEGORIA) continue;
                categoria = "EN_VUELO";
            } else {
                if (planificados.size() >= MAX_ENVIOS_CATEGORIA) continue;
                categoria = "PLANIFICADO";
            }

            final Pedido pedido = pedidoPorId.get(entry.getKey());
            final LinkedHashSet<String> uts = new LinkedHashSet<>();
            final LinkedHashSet<String> origenes = new LinkedHashSet<>();
            final LinkedHashSet<String> destinos = new LinkedHashSet<>();
            for (final Maleta m : maletas) {
                final Ruta ruta = sesion.getRutaPorMaleta(m.getIdMaleta());
                if (ruta == null) continue;
                for (final String subId : ruta.getSubrutaIds()) {
                    final VueloInstancia leg = vueloIndex.get(subId);
                    if (leg == null) continue;
                    if (leg.getCodigo() != null) uts.add(leg.getCodigo());
                    if (leg.getAeropuertoOrigen() != null && leg.getAeropuertoOrigen().getIdAeropuerto() != null) {
                        origenes.add(leg.getAeropuertoOrigen().getIdAeropuerto());
                    }
                    if (leg.getAeropuertoDestino() != null && leg.getAeropuertoDestino().getIdAeropuerto() != null) {
                        destinos.add(leg.getAeropuertoDestino().getIdAeropuerto());
                    }
                }
            }
            final String origin = pedido != null && pedido.getAeropuertoOrigen() != null
                    ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null;
            final String dest = pedido != null && pedido.getAeropuertoDestino() != null
                    ? pedido.getAeropuertoDestino().getIdAeropuerto() : null;
            if (origin != null) origenes.add(origin);
            if (dest != null) destinos.add(dest);

            final EnvioPanelResponse envio = EnvioPanelResponse.builder()
                    .withId(entry.getKey())
                    .withOrigin(origin)
                    .withDest(dest)
                    .withBags(maletas.size())
                    .withUts(new ArrayList<>(uts))
                    .withOrigenesRuta(new ArrayList<>(origenes))
                    .withDestinosRuta(new ArrayList<>(destinos))
                    .withHoraEntrega("ENTREGADO".equals(categoria) && ultimaEntrega != null
                            ? ultimaEntrega.format(FORMATO_FECHA_HORA) : null)
                    .build();

            switch (categoria) {
                case "ENTREGADO" -> entregados.add(envio);
                case "EN_VUELO" -> enVuelos.add(envio);
                default -> planificados.add(envio);
            }
        }

        entregados.sort(Comparator.comparing(EnvioPanelResponse::getHoraEntrega,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        return EnviosPanelResponse.builder()
                .withPlanificados(planificados)
                .withEnVuelos(enVuelos)
                .withEntregadosUltimas4h(entregados)
                .build();
    }

    private AlmacenContenidoResponse construirContenidoAlmacen(
            final SimulacionSesion sesion, final String idAeropuerto) {
        // ---------- Estado actual: lo que esta FISICAMENTE en el almacen (req 17/18) ----------
        final List<MaletaSimulacionResponse> maletasFinal = new ArrayList<>();
        final List<MaletaSimulacionResponse> maletasTransito = new ArrayList<>();
        final LinkedHashMap<String, int[]> bagsFinalPorPedido = new LinkedHashMap<>();
        final LinkedHashMap<String, int[]> bagsTransitoPorPedido = new LinkedHashMap<>();
        final Map<String, Pedido> pedidoPorId = new HashMap<>();
        final Map<String, Pedido> pedidoPorMaleta = new HashMap<>();
        int totalFinal = 0;
        int totalTransito = 0;

        for (final Maleta maleta : sesion.getMaletas()) {
            if (maleta == null) continue;
            if (maleta.getIdMaleta() != null) {
                pedidoPorMaleta.put(maleta.getIdMaleta(), maleta.getPedido());
            }
            // getAeropuertoActual() = almacen donde esta fisicamente (null si va en vuelo).
            final String ubicacion = maleta.getAeropuertoActual();
            if (ubicacion == null || !ubicacion.equals(idAeropuerto)) continue;

            final Pedido pedido = maleta.getPedido();
            final String idPedido = pedido != null ? pedido.getIdPedido() : null;
            final String destino = pedido != null && pedido.getAeropuertoDestino() != null
                    ? pedido.getAeropuertoDestino().getIdAeropuerto() : null;
            final boolean destinoFinal = idAeropuerto.equals(destino);

            final MaletaSimulacionResponse dto = MaletaSimulacionResponse.builder()
                    .withIdMaleta(maleta.getIdMaleta())
                    .withIdPedido(idPedido)
                    .withEstado(maleta.getEstado() != null ? maleta.getEstado().name() : null)
                    .withUbicacionActual(ubicacion)
                    .build();

            if (destinoFinal) {
                totalFinal++;
                if (maletasFinal.size() < MAX_MALETAS_CONTENIDO) maletasFinal.add(dto);
                if (idPedido != null) {
                    bagsFinalPorPedido.computeIfAbsent(idPedido, k -> new int[]{0})[0]++;
                    pedidoPorId.putIfAbsent(idPedido, pedido);
                }
            } else {
                totalTransito++;
                if (maletasTransito.size() < MAX_MALETAS_CONTENIDO) maletasTransito.add(dto);
                if (idPedido != null) {
                    bagsTransitoPorPedido.computeIfAbsent(idPedido, k -> new int[]{0})[0]++;
                    pedidoPorId.putIfAbsent(idPedido, pedido);
                }
            }
        }

        // ---------- Planificado: movimientos FUTUROS segun el plan de rutas (req 20-23) ----------
        final LocalDateTime ahora = sesion.getUltimoTiempoSim();
        final Map<String, VueloInstancia> vueloIndex = sesion.getVueloIndex();
        final List<MovimientoPlanificadoResponse> maletasEntran = new ArrayList<>();
        final List<MovimientoPlanificadoResponse> maletasSalen = new ArrayList<>();
        final LinkedHashMap<String, AccPedido> entranPorPedido = new LinkedHashMap<>();
        final LinkedHashMap<String, AccPedido> salenPorPedido = new LinkedHashMap<>();
        int totalEntran = 0;
        int totalSalen = 0;

        for (final Ruta ruta : sesion.getRutas()) {
            if (ruta == null || ruta.getIdMaleta() == null) continue;
            final String idMaleta = ruta.getIdMaleta();
            final Pedido pedido = pedidoPorMaleta.get(idMaleta);
            final String idPedido = pedido != null ? pedido.getIdPedido() : null;

            for (final String subId : ruta.getSubrutaIds()) {
                final VueloInstancia leg = vueloIndex.get(subId);
                if (leg == null) continue;
                final String origen = leg.getAeropuertoOrigen() != null
                        ? leg.getAeropuertoOrigen().getIdAeropuerto() : null;
                final String destino = leg.getAeropuertoDestino() != null
                        ? leg.getAeropuertoDestino().getIdAeropuerto() : null;

                if (idAeropuerto.equals(destino) && noEsPasado(leg.getFechaLlegada(), ahora)) {
                    totalEntran++;
                    maletasEntran.add(movimientoPlanificado(idMaleta, idPedido, leg, leg.getFechaLlegada()));
                    if (idPedido != null) acumularPedido(entranPorPedido, idPedido, pedido, leg.getFechaLlegada());
                }
                if (idAeropuerto.equals(origen) && noEsPasado(leg.getFechaSalida(), ahora)) {
                    totalSalen++;
                    maletasSalen.add(movimientoPlanificado(idMaleta, idPedido, leg, leg.getFechaSalida()));
                    if (idPedido != null) acumularPedido(salenPorPedido, idPedido, pedido, leg.getFechaSalida());
                }
            }
        }

        final Comparator<MovimientoPlanificadoResponse> porHora =
                Comparator.comparing(MovimientoPlanificadoResponse::getHora,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        maletasEntran.sort(porHora);
        maletasSalen.sort(porHora);

        return AlmacenContenidoResponse.builder()
                .withIdAeropuerto(idAeropuerto)
                .withTotalMaletasDestinoFinal(totalFinal)
                .withTotalMaletasEnTransito(totalTransito)
                .withMaletasDestinoFinal(maletasFinal)
                .withMaletasEnTransito(maletasTransito)
                .withPedidosDestinoFinal(construirPedidosContenido(bagsFinalPorPedido, pedidoPorId))
                .withPedidosEnTransito(construirPedidosContenido(bagsTransitoPorPedido, pedidoPorId))
                .withTotalMaletasEntran(totalEntran)
                .withTotalMaletasSalen(totalSalen)
                .withMaletasEntran(limitarMovimientos(maletasEntran))
                .withMaletasSalen(limitarMovimientos(maletasSalen))
                .withPedidosEntran(construirPedidosPlanificados(entranPorPedido))
                .withPedidosSalen(construirPedidosPlanificados(salenPorPedido))
                .build();
    }

    /** No esta en el pasado respecto al reloj de la simulacion (o el reloj aun es null). */
    private static boolean noEsPasado(final LocalDateTime instante, final LocalDateTime ahora) {
        return instante != null && (ahora == null || !instante.isBefore(ahora));
    }

    private MovimientoPlanificadoResponse movimientoPlanificado(
            final String idMaleta, final String idPedido, final VueloInstancia leg, final LocalDateTime hora) {
        return MovimientoPlanificadoResponse.builder()
                .withIdMaleta(idMaleta)
                .withIdPedido(idPedido)
                .withVuelo(leg.getCodigo())
                .withHora(hora != null ? hora.format(FORMATO_FECHA_HORA) : null)
                .build();
    }

    private List<MovimientoPlanificadoResponse> limitarMovimientos(final List<MovimientoPlanificadoResponse> lista) {
        return lista.size() <= MAX_MALETAS_CONTENIDO
                ? lista : new ArrayList<>(lista.subList(0, MAX_MALETAS_CONTENIDO));
    }

    private static final class AccPedido {
        private final Pedido pedido;
        private int bags;
        private LocalDateTime earliest;

        private AccPedido(final Pedido pedido) {
            this.pedido = pedido;
        }
    }

    private void acumularPedido(final LinkedHashMap<String, AccPedido> acc, final String idPedido,
            final Pedido pedido, final LocalDateTime hora) {
        final AccPedido a = acc.computeIfAbsent(idPedido, k -> new AccPedido(pedido));
        a.bags++;
        if (hora != null && (a.earliest == null || hora.isBefore(a.earliest))) {
            a.earliest = hora;
        }
    }

    private List<PedidoSimulacionResponse> construirPedidosPlanificados(final LinkedHashMap<String, AccPedido> acc) {
        final List<Map.Entry<String, AccPedido>> entradas = new ArrayList<>(acc.entrySet());
        entradas.sort(Comparator.comparing(e -> e.getValue().earliest,
                Comparator.nullsLast(Comparator.naturalOrder())));
        final List<PedidoSimulacionResponse> salida = new ArrayList<>();
        for (final Map.Entry<String, AccPedido> entry : entradas) {
            final AccPedido a = entry.getValue();
            final Pedido p = a.pedido;
            final String fecha = a.earliest != null ? a.earliest.format(FORMATO_FECHA_HORA) : "";
            salida.add(PedidoSimulacionResponse.builder()
                    .withId(entry.getKey())
                    .withOrigin(p != null && p.getAeropuertoOrigen() != null
                            ? p.getAeropuertoOrigen().getIdAeropuerto() : null)
                    .withDest(p != null && p.getAeropuertoDestino() != null
                            ? p.getAeropuertoDestino().getIdAeropuerto() : null)
                    .withBags(a.bags)
                    .withDate(fecha.length() >= 10 ? fecha.substring(0, 10) : "")
                    .withTime(fecha.length() >= 16 ? fecha.substring(11, 16) : "")
                    .build());
        }
        return salida;
    }

    private List<PedidoSimulacionResponse> construirPedidosContenido(
            final LinkedHashMap<String, int[]> bagsPorPedido, final Map<String, Pedido> pedidoPorId) {
        final List<PedidoSimulacionResponse> salida = new ArrayList<>();
        for (final Map.Entry<String, int[]> entry : bagsPorPedido.entrySet()) {
            final Pedido pedido = pedidoPorId.get(entry.getKey());
            salida.add(PedidoSimulacionResponse.builder()
                    .withId(entry.getKey())
                    .withOrigin(pedido != null && pedido.getAeropuertoOrigen() != null
                            ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null)
                    .withDest(pedido != null && pedido.getAeropuertoDestino() != null
                            ? pedido.getAeropuertoDestino().getIdAeropuerto() : null)
                    .withBags(entry.getValue()[0])
                    .build());
        }
        return salida;
    }

    private SimulacionVentanaDTO construirRespuestaVentana(
            final SimulacionSesion sesion, final String windowId) {
        final List<MaletaSimulacionResponse> maletasDTO = new ArrayList<>();
        final java.util.LinkedHashMap<String, PedidoSimulacionResponse> pedidosMap = new java.util.LinkedHashMap<>();
        final List<RutaSimulacionResponse> rutasDTO = new ArrayList<>();

        final Map<String, VueloInstancia> vueloIndex = sesion.getVueloIndex();

        final Map<String, Ruta> rutasPorMaleta = new HashMap<>();
        for (final Ruta r : sesion.getRutas()) {
            if (r != null && r.getIdMaleta() != null) {
                rutasPorMaleta.put(r.getIdMaleta(), r);
            }
        }

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
                        .withUbicacionActual(m.getAeropuertoActual())
                        .withHoraLlegadaEstimada(obtenerHoraLlegadaEstimada(m.getIdMaleta(), vueloIndex, rutasPorMaleta))
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
        final var rutas = sesion.getRutas();
        for (final var r : rutas) {
            if (r == null || r.getIdMaleta() == null || !idMaletasVentana.contains(r.getIdMaleta())) continue;
            final List<String> ids = r.getSubrutas();
            final List<RutaVueloResponse> vuelosRuta = ids.stream()
                    .map(vueloIndex::get)
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
