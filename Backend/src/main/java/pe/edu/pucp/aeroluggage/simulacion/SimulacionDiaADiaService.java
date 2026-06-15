package pe.edu.pucp.aeroluggage.simulacion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNS;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNSUtil;
import pe.edu.pucp.aeroluggage.config.ALNSConfig;
import pe.edu.pucp.aeroluggage.config.SimulacionDiaADiaParams;
import pe.edu.pucp.aeroluggage.config.SistemaConfiguracion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoMaletaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoRutaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoVueloDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.OcupacionAeropuertoDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.SimulacionTickLigeroDTO;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.MaletaRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.PedidoRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.RutaRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloInstanciaRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloProgramadoRepositorio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SimulacionDiaADiaService {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String TOPIC_SIM = "/topic/operations/";
    private static final String TOPIC_ESTADO = "/topic/operations/%s/estado";

    private static final String ESTADO_INICIADA = "INICIADA";
    private static final String ESTADO_DETENIDA = "DETENIDA";
    private static final String ESTADO_PEDIDO_PROCESADO = "PEDIDO_PROCESADO";

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;
    private final PedidoRepositorio pedidoRepositorio;
    private final MaletaRepositorio maletaRepositorio;
    private final RutaRepositorio rutaRepositorio;
    private final VueloInstanciaRepositorio vueloInstanciaRepositorio;
    private final SimulacionDiaADiaParams params;
    private final SistemaConfiguracion sistemaConfiguracion;
    private final ALNSConfig alnsConfig;
    private final SimpMessagingTemplate broker;

    private static final long DEPURA_INTERVALO_MS = 3_600_000L;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> tickTask;
    private final Object lock = new Object();

    private String sessionId;
    private boolean activa;
    private volatile long lastAccessTime;
    private volatile long lastDepuraMs;
    private LocalDate ultimoDiaGenerado;
    private long startedAtRealMs;
    private ConcurrentHashMap<String, Aeropuerto> aeropuertos = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, VueloProgramado> vuelosProgramados = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, VueloInstancia> vuelosInstancia = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Pedido> pedidos = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Maleta> maletasPorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Ruta> rutasPorMaleta = new ConcurrentHashMap<>();
    private final AtomicInteger totalMaletasEntregadas = new AtomicInteger(0);
    private final Set<String> idsEntregadasEnTick = new HashSet<>();
    private final Map<String, String> idsCompletadasEnTick = new HashMap<>();
    private final Set<String> idsVuelosRecienConfirmados = ConcurrentHashMap.newKeySet();
    private final Set<String> clientesConectados = ConcurrentHashMap.newKeySet();

    private NavigableMap<LocalDateTime, List<EventoSim>> eventos;
    private LocalDateTime ultimoTiempoProcesado;
    private final AtomicInteger tickActual = new AtomicInteger(0);
    private final AtomicLong stateVersion = new AtomicLong(1);
    private volatile boolean ticksActivos;

    enum TipoEventoSim {
        MALETA_APARECE,
        VUELO_CONFIRMA,
        VUELO_INICIA,
        VUELO_FINALIZA,
        MALETA_SALE_AEROP,
        MALETA_LLEGA_AEROP,
        MALETA_ENTREGADA,
        RUTA_ACTIVA,
        RUTA_COMPLETA
    }

    record EventoSim(TipoEventoSim tipo, String idEntidad, String idAeropuerto, int delta) {}

    public SimulacionDiaADiaService(final AeropuertoRepositorio aeropuertoRepositorio,
                                    final VueloProgramadoRepositorio vueloProgramadoRepositorio,
                                    final PedidoRepositorio pedidoRepositorio,
                                    final MaletaRepositorio maletaRepositorio,
                                    final RutaRepositorio rutaRepositorio,
                                    final VueloInstanciaRepositorio vueloInstanciaRepositorio,
                                    final SimulacionDiaADiaParams params,
                                    final SistemaConfiguracion sistemaConfiguracion,
                                    final ALNSConfig alnsConfig,
                                    final SimpMessagingTemplate broker) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.vueloProgramadoRepositorio = vueloProgramadoRepositorio;
        this.pedidoRepositorio = pedidoRepositorio;
        this.maletaRepositorio = maletaRepositorio;
        this.rutaRepositorio = rutaRepositorio;
        this.vueloInstanciaRepositorio = vueloInstanciaRepositorio;
        this.params = params;
        this.sistemaConfiguracion = sistemaConfiguracion;
        this.alnsConfig = alnsConfig;
        this.broker = broker;
    }

    public String iniciar() {
        synchronized (lock) {
            if (activa && sessionId != null) {
                lastAccessTime = System.currentTimeMillis();
                log.info("[AeroLuggage/DiaADia] - SESION EXISTENTE: sessionId={}", sessionId);
                return sessionId;
            }

            if (activa) {
                detener();
            }

            sessionId = UUID.randomUUID().toString();
            activa = true;
            startedAtRealMs = System.currentTimeMillis();
            lastAccessTime = System.currentTimeMillis();
            lastDepuraMs = System.currentTimeMillis();
            tickActual.set(0);
            stateVersion.set(1);
            idsEntregadasEnTick.clear();
            idsCompletadasEnTick.clear();
            eventos = new TreeMap<>();
            vuelosInstancia.clear();
            aeropuertos.clear();
            vuelosProgramados.clear();
            final List<Aeropuerto> listaAeropuertos = aeropuertoRepositorio.obtenerTodosConCiudad();
            if (listaAeropuertos.isEmpty()) {
                activa = false;
                throw new IllegalStateException("No se encontraron aeropuertos en la base de datos");
            }
            for (final Aeropuerto a : listaAeropuertos) {
                aeropuertos.put(a.getIdAeropuerto(), a);
            }
            for (final Aeropuerto a : aeropuertos.values()) {
                a.setMaletasActuales(0);
            }
            final List<VueloProgramado> listaVuelos = vueloProgramadoRepositorio.obtenerTodos();
            if (listaVuelos.isEmpty()) {
                activa = false;
                throw new IllegalStateException("No se encontraron vuelos programados en la base de datos");
            }
            for (final VueloProgramado vp : listaVuelos) {
                vuelosProgramados.put(vp.getIdVueloProgramado(), vp);
            }

            final LocalDate hoy = LocalDate.now(ZoneOffset.UTC);
            ultimoDiaGenerado = hoy;
            for (int i = 0; i <= 2; i++) {
                generarVuelosParaFecha(hoy.plusDays(i));
            }

            idsVuelosRecienConfirmados.clear();
            ticksActivos = false;

            cargarPedidosDesdeBD();
            cargarMaletasDesdeBD();

            final List<Ruta> rutasBD = rutaRepositorio.obtenerActivasPlanificadas();
            final Set<String> idsVuelosDeRutas = new HashSet<>();
            for (final Ruta r : rutasBD) {
                rutasPorMaleta.put(r.getIdMaleta(), r);
                idsVuelosDeRutas.addAll(r.getSubrutaIds());
            }

            if (!idsVuelosDeRutas.isEmpty()) {
                final List<VueloInstancia> vuelosRuta = vueloInstanciaRepositorio.obtenerPorIds(
                        new ArrayList<>(idsVuelosDeRutas));
                for (final VueloInstancia vi : vuelosRuta) {
                    if (vi != null) vuelosInstancia.put(vi.getIdVueloInstancia(), vi);
                }
            }

            for (final Ruta r : rutasBD) {
                recrearEventosRuta(r);
            }

            ultimoTiempoProcesado = LocalDateTime.now(ZoneOffset.UTC);
            procesarEventos(ultimoTiempoProcesado, ultimoTiempoProcesado);
            vuelosInstancia.values().removeIf(v -> v.getEstado() == EstadoVuelo.FINALIZADO
                    || v.getEstado() == EstadoVuelo.CANCELADO);
            recalcularOcupacionAeropuertos();
            recalcularOcupacionVuelos();
            guardarEstadosBD();
            pedidos.clear();
            maletasPorId.clear();
            rutasPorMaleta.clear();

            log.info("[AeroLuggage/DiaADia] - INICIADA: sessionId={}, fecha={}, aeropuertos={}, vuelosProgramados={}, vuelosInstancia={}",
                    sessionId, hoy, aeropuertos.size(), vuelosProgramados.size(), vuelosInstancia.size());

            return sessionId;
        }
    }

    public void confirmarConexion() {
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }

            if (ticksActivos && tickTask != null && !tickTask.isCancelled()) {
                lastAccessTime = System.currentTimeMillis();
                log.info("[AeroLuggage/DiaADia] - CONEXION RENOVADA: sessionId={}", sessionId);
                return;
            }

            idsVuelosRecienConfirmados.clear();
            ticksActivos = true;
            final long tickIntervalMs = Math.max(100L, params.getTickIntervalMs());
            tickTask = scheduler.scheduleWithFixedDelay(
                    this::ejecutarTick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);
            log.info("[AeroLuggage/DiaADia] - CONEXION CONFIRMADA: sessionId={}", sessionId);
            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sessionId),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sessionId)
                            .withEstado(ESTADO_INICIADA)
                            .withMensaje("Simulacion dia a dia iniciada")
                            .build());
        }
    }

    public void detener() {
        synchronized (lock) {
            if (!activa) {
                return;
            }
            activa = false;
            ticksActivos = false;
            lastAccessTime = 0;
            if (tickTask != null && !tickTask.isCancelled()) {
                tickTask.cancel(false);
                tickTask = null;
            }
            limpiarEstado();
            log.info("[AeroLuggage/DiaADia] - DETENIDA: sessionId={}", sessionId);
            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sessionId),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sessionId)
                            .withEstado(ESTADO_DETENIDA)
                            .withMensaje("Simulacion dia a dia detenida")
                            .build());
        }
    }

    public void procesarPedido(final PedidoRequest request) {
        touchSession();
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }
            final String icaoOrigen = request.getIdAeropuertoOrigen();
            final String icaoDestino = request.getIdAeropuertoDestino();
            final Aeropuerto origen = aeropuertos.get(icaoOrigen);
            final Aeropuerto destino = aeropuertos.get(icaoDestino);
            if (origen == null || destino == null) {
                throw new IllegalArgumentException("Aeropuerto de origen o destino no encontrado");
            }
            final LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
            final LocalDateTime fechaRegistro = request.getFechaRegistro() != null
                    ? LocalDateTime.parse(request.getFechaRegistro())
                    : ahora;
            final String fechaStr = fechaRegistro.toLocalDate().format(
                    DateTimeFormatter.ofPattern("yyyyMMdd"));
            final int ultimoSeq = pedidoRepositorio.obtenerUltimoSecuencial(icaoOrigen, fechaStr);
            final String idPedido = String.format("PED-%s-%s-%05d",
                    icaoOrigen, fechaStr, ultimoSeq + 1);
            final Pedido pedido = new Pedido(
                    idPedido,
                    origen,
                    destino,
                    fechaRegistro,
                    request.getCantidadMaletas(),
                    EstadoPedido.REGISTRADO.name());
            pedido.calcularFechaHoraPlazo();
            pedidos.put(pedido.getIdPedido(), pedido);

            final List<Maleta> nuevasMaletas = new ArrayList<>();
            for (int i = 0; i < request.getCantidadMaletas(); i++) {
                final String idMaleta = String.format("MAL-%s-%s-%s-B%03d",
                        icaoOrigen, fechaStr,
                        idPedido.substring(idPedido.lastIndexOf('-') + 1),
                        i + 1);
                final Maleta maleta = new Maleta(
                        idMaleta,
                        pedido,
                        fechaRegistro,
                        null,
                        EstadoMaleta.EN_ALMACEN);
                maletasPorId.put(idMaleta, maleta);
                nuevasMaletas.add(maleta);
            }

            log.info("[AeroLuggage/DiaADia] - PEDIDO: id={}, origen={}, destino={}, maletas={}",
                    idPedido, icaoOrigen, icaoDestino, request.getCantidadMaletas());

            planificarPendientes();

            final boolean todasAsignadas = nuevasMaletas.stream()
                    .allMatch(m -> rutasPorMaleta.containsKey(m.getIdMaleta()));

            if (!todasAsignadas) {
                for (final Maleta m : nuevasMaletas) {
                    maletasPorId.remove(m.getIdMaleta());
                    rutasPorMaleta.remove(m.getIdMaleta());
                }
                pedidos.remove(idPedido);
                throw new IllegalStateException(
                        "No se encontró ruta disponible para el pedido " + idPedido);
            }

            pedidoRepositorio.insertar(pedido);
            for (final Maleta bag : nuevasMaletas) {
                maletaRepositorio.insertar(bag);
                final Ruta route = rutasPorMaleta.get(bag.getIdMaleta());
                if (route != null) {
                    rutaRepositorio.insertar(route);
                }
            }

            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sessionId),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sessionId)
                            .withEstado(ESTADO_PEDIDO_PROCESADO)
                            .withMensaje("Pedido " + idPedido + " procesado")
                            .build());
        }
    }

    private void ejecutarTick() {
        if (!activa || !ticksActivos) {
            return;
        }
        synchronized (lock) {
            if (!activa) {
                return;
            }
            if (clientesConectados.isEmpty()
                    && System.currentTimeMillis() - lastAccessTime > params.getTimeoutMs()) {
                log.warn("[AeroLuggage/DiaADia] - TIMEOUT: sesion expirada por inactividad de {}ms", params.getTimeoutMs());
                detener();
                return;
            }
            tickActual.incrementAndGet();
            final LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);

            final LocalDate hoy = ahora.toLocalDate();
            if (ultimoDiaGenerado != null && hoy.isAfter(ultimoDiaGenerado)) {
                generarVuelosParaFecha(hoy);
                ultimoDiaGenerado = hoy;
            planificarPendientes();
            log.info("[AeroLuggage/DiaADia] - NUEVO DIA: fecha={}, vuelosInstancia={}",
                        hoy, vuelosInstancia.size());
            }

            final LocalDateTime desde = ultimoTiempoProcesado;
            procesarEventos(desde, ahora);
            ultimoTiempoProcesado = ahora;

            final int tick = tickActual.get();
            if (tick % 5 == 0) {
                int p = 0, c = 0, e = 0, f = 0, x = 0;
                for (final VueloInstancia v : vuelosInstancia.values()) {
                    if (v == null) continue;
                    switch (v.getEstado()) {
                        case PROGRAMADO -> p++;
                        case CONFIRMADO -> c++;
                        case EN_PROGRESO -> e++;
                        case FINALIZADO -> f++;
                        case CANCELADO -> x++;
                    }
                }
                log.info("[AeroLuggage/DiaADia] - TICK {}: vuelos(P={},C={},E={},F={},X={}), maletas={}, rutas={}, pedidos={}",
                        tick, p, c, e, f, x, maletasPorId.size(), rutasPorMaleta.size(), pedidos.size());
            }

            if (System.currentTimeMillis() - lastDepuraMs >= DEPURA_INTERVALO_MS) {
                depurarVuelos(ahora);
                lastDepuraMs = System.currentTimeMillis();
            }

            final SimulacionTickLigeroDTO tickDTO = construirTickDTO(ahora);
            broker.convertAndSend(TOPIC_SIM + sessionId, tickDTO);
            maletasPorId.clear();
            rutasPorMaleta.clear();
            idsVuelosRecienConfirmados.clear();
            idsEntregadasEnTick.clear();
            idsCompletadasEnTick.clear();
        }
    }

    private SimulacionTickLigeroDTO construirTickDTO(final LocalDateTime ahora) {
        int almacen = 0;
        int enTransito = 0;
        int sinRuta = 0;
        final List<EstadoMaletaDTO> estadosMaletas = new ArrayList<>();
        for (final Maleta m : maletasPorId.values()) {
            if (m == null || m.getFechaRegistro() == null || m.getFechaRegistro().isAfter(ahora)) {
                continue;
            }
            final EstadoMaleta estado = m.getEstado();
            if (estado == EstadoMaleta.EN_ALMACEN) almacen++;
            else if (estado == EstadoMaleta.EN_TRANSITO) enTransito++;
            estadosMaletas.add(EstadoMaletaDTO.builder()
                    .withId(m.getIdMaleta())
                    .withE(estado != null ? estado.ordinal() : 0)
                    .build());
            if (estado == EstadoMaleta.EN_ALMACEN || estado == EstadoMaleta.EN_TRANSITO) {
                final Ruta r = rutasPorMaleta.get(m.getIdMaleta());
                if (r == null || r.getEstado() == EstadoRuta.REPLANIFICADA || r.getSubrutas().isEmpty()) {
                    sinRuta++;
                }
            }
        }

        final List<EstadoRutaDTO> estadosRutas = new ArrayList<>();
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r == null) continue;
            estadosRutas.add(EstadoRutaDTO.builder()
                    .withId(r.getIdRuta())
                    .withE(r.getEstado() != null ? r.getEstado().ordinal() : 0)
                    .build());
        }

        int vuelosActivos = 0;
        int capacidadTotal = 0;
        int capacidadDisponible = 0;
        final List<EstadoVueloDTO> estadosVuelos = new ArrayList<>();
        for (final VueloInstancia v : vuelosInstancia.values()) {
            if (v == null) continue;
            final EstadoVuelo estado = v.getEstado();
            if (estado == EstadoVuelo.PROGRAMADO) continue;
            if (estado == EstadoVuelo.EN_PROGRESO) vuelosActivos++;
            capacidadTotal += Math.max(0, v.getCapacidadMaxima());
            capacidadDisponible += Math.max(0, v.getCapacidadDisponible());
            estadosVuelos.add(EstadoVueloDTO.builder()
                    .withId(v.getIdVueloInstancia())
                    .withE(estado != null ? estado.ordinal() : 0)
                    .withCap(v.getCapacidadDisponible())
                    .build());
        }
        final int capacidadLibrePct = capacidadTotal > 0
                ? (int) Math.round((capacidadDisponible * 100D) / capacidadTotal) : 0;

        final List<OcupacionAeropuertoDTO> aeropuertosDTO = new ArrayList<>();
        for (final Aeropuerto a : aeropuertos.values()) {
            if (a == null) continue;
            aeropuertosDTO.add(OcupacionAeropuertoDTO.builder()
                    .withId(a.getIdAeropuerto())
                    .withOcc(a.getMaletasActuales())
                    .build());
        }

        return SimulacionTickLigeroDTO.builder()
                .withType("TICK_DIAADIA")
                .withTick(tickActual.get())
                .withSimTime(ahora.format(FORMATO_FECHA_HORA))
                .withVentanaActual("DIA_A_DIA")
                .withStateVersion(stateVersion.get())
                .withMaletasEnTransito(enTransito)
                .withMaletasEntregadas(totalMaletasEntregadas.get())
                .withMaletasRetrasadas(0)
                .withMaletasNoAsignadas(sinRuta)
                .withVuelosActivos(vuelosActivos)
                .withCapacidadLibrePct(capacidadLibrePct)
                .withEstadosMaletas(estadosMaletas)
                .withEstadosRutas(estadosRutas)
                .withEstadosVuelos(estadosVuelos)
                .withAeropuertos(aeropuertosDTO)
                .build();
    }

    private void generarVuelosParaFecha(final LocalDate fecha) {
        final String fechaStr = fecha.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int seq = vueloInstanciaRepositorio.obtenerUltimoSecuencial(fechaStr) + 1;
        for (final VueloProgramado vp : vuelosProgramados.values()) {
            if (vp == null || vp.getHoraSalida() == null || vp.getHoraLlegada() == null) continue;
            final int gmtOrigen = vp.getAeropuertoOrigen() != null
                    ? vp.getAeropuertoOrigen().getHusoGMT() : 0;
            final int gmtDestino = vp.getAeropuertoDestino() != null
                    ? vp.getAeropuertoDestino().getHusoGMT() : 0;
            LocalDateTime salidaUtc = LocalDateTime.of(fecha, vp.getHoraSalida()).minusHours(gmtOrigen);
            LocalDate fechaLlegadaLocal = fecha;
            if (vp.getHoraLlegada().isBefore(vp.getHoraSalida())) {
                fechaLlegadaLocal = fecha.plusDays(1);
            }
            LocalDateTime llegadaUtc = LocalDateTime.of(fechaLlegadaLocal, vp.getHoraLlegada()).minusHours(gmtDestino);
            if (!llegadaUtc.isAfter(salidaUtc)) {
                llegadaUtc = llegadaUtc.plusDays(1);
            }
            final String orig = vp.getAeropuertoOrigen() != null
                    ? vp.getAeropuertoOrigen().getIdAeropuerto() : "??";
            final String dest = vp.getAeropuertoDestino() != null
                    ? vp.getAeropuertoDestino().getIdAeropuerto() : "??";
            final String id = String.format("VUE-%s-%s-%s-%06d",
                    orig, dest, fechaStr, seq);
            final VueloInstancia vi = new VueloInstancia(
                    id, vp, fecha, salidaUtc, llegadaUtc,
                    vp.getCapacidadMaxima(), vp.getCapacidadMaxima(),
                    EstadoVuelo.PROGRAMADO);
            vuelosInstancia.put(vi.getIdVueloInstancia(), vi);
            final LocalDateTime tConf = salidaUtc.minusMinutes(
                    sistemaConfiguracion.getUmbralConfirmacionMinutos());
            agregarEvento(tConf, TipoEventoSim.VUELO_CONFIRMA, id, null, 0);
            agregarEvento(salidaUtc, TipoEventoSim.VUELO_INICIA, id, null, 0);
            agregarEvento(llegadaUtc, TipoEventoSim.VUELO_FINALIZA, id, null, 0);
            seq++;
        }
    }

    private void agregarEvento(final LocalDateTime tiempo, final TipoEventoSim tipo,
                               final String idEntidad, final String idAeropuerto, final int delta) {
        if (tiempo == null) return;
        final LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        if (!tiempo.isAfter(ahora)) {
            aplicarEventoAhora(new EventoSim(tipo, idEntidad, idAeropuerto, delta));
            return;
        }
        eventos.computeIfAbsent(tiempo, k -> new ArrayList<>())
                .add(new EventoSim(tipo, idEntidad, idAeropuerto, delta));
    }

    private void procesarEventos(final LocalDateTime desde, final LocalDateTime hasta) {
        if (eventos == null) return;
        if (desde == null || hasta == null || !hasta.isAfter(desde)) return;
        final Collection<List<EventoSim>> loteEventos = eventos
                .subMap(desde, false, hasta, true)
                .values();
        for (final List<EventoSim> lote : loteEventos) {
            for (final EventoSim e : lote) {
                aplicarEventoAhora(e);
            }
        }
        vuelosInstancia.values().removeIf(v -> v.getEstado() == EstadoVuelo.FINALIZADO
                || v.getEstado() == EstadoVuelo.CANCELADO);
    }

    private void depurarVuelos(final LocalDateTime ahora) {
        final LocalDateTime corte = ahora.plusHours(48);
        vuelosInstancia.values().removeIf(v -> v.getEstado() == EstadoVuelo.PROGRAMADO
                && v.getFechaSalida() != null
                && v.getFechaSalida().isAfter(corte));
        log.info("[AeroLuggage/DiaADia] - DEPURA: vuelosInstancia={}", vuelosInstancia.size());
    }

    private void aplicarEventoAhora(final EventoSim e) {
        switch (e.tipo()) {
            case MALETA_APARECE -> aplicarMaletaAparece(e);
            case VUELO_CONFIRMA -> aplicarVueloConfirma(e);
            case VUELO_INICIA -> aplicarVueloInicia(e);
            case VUELO_FINALIZA -> aplicarVueloFinaliza(e);
            case MALETA_SALE_AEROP -> aplicarMaletaSale(e);
            case MALETA_LLEGA_AEROP -> aplicarMaletaLlega(e);
            case MALETA_ENTREGADA -> aplicarMaletaEntregada(e);
            case RUTA_ACTIVA -> aplicarRutaActiva(e);
            case RUTA_COMPLETA -> aplicarRutaCompleta(e);
        }
    }

    private void aplicarMaletaAparece(final EventoSim e) {
        Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) {
            m = maletaRepositorio.obtenerPorId(e.idEntidad()).orElse(null);
            if (m == null) return;
            maletasPorId.put(e.idEntidad(), m);
        }
        m.setEstado(EstadoMaleta.EN_ALMACEN);
        m.setAeropuertoActual(e.idAeropuerto());
        if (e.idAeropuerto() != null) {
            final Aeropuerto a = aeropuertos.get(e.idAeropuerto());
            if (a != null) a.setMaletasActuales(a.getMaletasActuales() + 1);
        }
        maletaRepositorio.actualizar(m);
    }

    private void aplicarMaletaSale(final EventoSim e) {
        Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) {
            m = maletaRepositorio.obtenerPorId(e.idEntidad()).orElse(null);
            if (m == null) return;
            maletasPorId.put(e.idEntidad(), m);
        }
        m.setEstado(EstadoMaleta.EN_TRANSITO);
        m.setAeropuertoActual(null);
        if (e.idAeropuerto() != null) {
            final Aeropuerto a = aeropuertos.get(e.idAeropuerto());
            if (a != null) a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
        }
        maletaRepositorio.actualizar(m);
    }

    private void aplicarMaletaLlega(final EventoSim e) {
        Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) {
            m = maletaRepositorio.obtenerPorId(e.idEntidad()).orElse(null);
            if (m == null) return;
            maletasPorId.put(e.idEntidad(), m);
        }
        m.setEstado(EstadoMaleta.EN_ALMACEN);
        m.setAeropuertoActual(e.idAeropuerto());
        if (e.idAeropuerto() != null) {
            final Aeropuerto a = aeropuertos.get(e.idAeropuerto());
            if (a != null) a.setMaletasActuales(a.getMaletasActuales() + 1);
        }
        maletaRepositorio.actualizar(m);
    }

    private void aplicarMaletaEntregada(final EventoSim e) {
        final Maleta m = maletasPorId.remove(e.idEntidad());
        if (m == null) {
            final Optional<Maleta> opt = maletaRepositorio.obtenerPorId(e.idEntidad());
            if (opt.isEmpty()) return;
            aplicarMaletaEntregadaDirecta(opt.get(), e);
            return;
        }
        aplicarMaletaEntregadaDirecta(m, e);
    }

    private void aplicarMaletaEntregadaDirecta(final Maleta m, final EventoSim e) {
        final LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        totalMaletasEntregadas.incrementAndGet();
        m.setEstado(EstadoMaleta.ENTREGADA);
        m.setFechaLlegada(ahora);
        final String idPedido = m.getPedido() != null ? m.getPedido().getIdPedido() : null;
        if (m.getPedido() != null && m.getPedido().getAeropuertoDestino() != null) {
            m.setAeropuertoActual(m.getPedido().getAeropuertoDestino().getIdAeropuerto());
        }
        final Ruta r = rutasPorMaleta.remove(e.idEntidad());
        if (r != null) {
            r.setEstado(EstadoRuta.COMPLETADA);
            r.setFechaEntrega(ahora);
            rutaRepositorio.actualizar(r);
        }
        idsEntregadasEnTick.add(e.idEntidad());
        if (r != null) idsCompletadasEnTick.put(r.getIdRuta(), e.idEntidad());
        if (e.idAeropuerto() != null) {
            final Aeropuerto a = aeropuertos.get(e.idAeropuerto());
            if (a != null) a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
        }
        maletaRepositorio.actualizar(m);

        if (idPedido != null) {
            final boolean pendientes = maletasPorId.values().stream()
                    .anyMatch(b -> b != null && b.getPedido() != null
                            && idPedido.equals(b.getPedido().getIdPedido()));
            if (!pendientes) {
                pedidoRepositorio.obtenerPorId(idPedido).ifPresent(p -> {
                    p.setEstado(EstadoPedido.ENTREGADO);
                    p.setFechaEntrega(ahora);
                    pedidoRepositorio.actualizar(p);
                });
            }
        }
    }

    private void aplicarVueloConfirma(final EventoSim e) {
        final VueloInstancia v = vuelosInstancia.get(e.idEntidad());
        if (v != null && v.getEstado() == EstadoVuelo.PROGRAMADO) {
            v.setEstado(EstadoVuelo.CONFIRMADO);
            idsVuelosRecienConfirmados.add(e.idEntidad());
        }
    }

    private void aplicarVueloInicia(final EventoSim e) {
        final VueloInstancia v = vuelosInstancia.get(e.idEntidad());
        if (v != null && v.getEstado() != EstadoVuelo.CANCELADO) {
            v.setEstado(EstadoVuelo.EN_PROGRESO);
        }
    }

    private void aplicarVueloFinaliza(final EventoSim e) {
        final VueloInstancia v = vuelosInstancia.get(e.idEntidad());
        if (v != null) {
            v.setEstado(EstadoVuelo.FINALIZADO);
        }
    }

    private void aplicarRutaActiva(final EventoSim e) {
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r != null && e.idEntidad().equals(r.getIdRuta())
                    && r.getEstado() == EstadoRuta.PLANIFICADA) {
                r.setEstado(EstadoRuta.ACTIVA);
                break;
            }
        }
    }

    private void aplicarRutaCompleta(final EventoSim e) {
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r != null && e.idEntidad().equals(r.getIdRuta())
                    && r.getEstado() == EstadoRuta.ACTIVA) {
                r.setEstado(EstadoRuta.COMPLETADA);
                break;
            }
        }
    }

    private void planificarPendientes() {
        if (vuelosInstancia.isEmpty()) {
            log.warn("[AeroLuggage/DiaADia] - PLAN: sin vuelos instancia disponibles");
            return;
        }
        final LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        final List<VueloInstancia> vuelosFuturos = vuelosInstancia.values().stream()
                .filter(v -> v != null && v.getFechaSalida() != null
                        && v.getFechaSalida().isAfter(ahora.minusMinutes(30))
                        && v.getEstado() != EstadoVuelo.FINALIZADO
                        && v.getEstado() != EstadoVuelo.CANCELADO)
                .collect(Collectors.toList());
        if (vuelosFuturos.isEmpty()) {
            log.warn("[AeroLuggage/DiaADia] - PLAN: no hay vuelos futuros disponibles");
            return;
        }
        final Set<String> maletasConRutaValida = new HashSet<>();
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r != null && r.getEstado() != EstadoRuta.REPLANIFICADA
                    && !r.getSubrutas().isEmpty()) {
                maletasConRutaValida.add(r.getIdMaleta());
            }
        }
        final List<Maleta> pendientes = new ArrayList<>();
        for (final Maleta m : maletasPorId.values()) {
            if (m == null || m.getIdMaleta() == null) continue;
            if (maletasConRutaValida.contains(m.getIdMaleta())) continue;
            if (m.getFechaRegistro() == null || m.getFechaRegistro().isAfter(ahora)) continue;
            if (m.getEstado() == EstadoMaleta.ENTREGADA) continue;
            pendientes.add(m);
        }
        if (pendientes.isEmpty()) {
            log.info("[AeroLuggage/DiaADia] - PLAN: sin maletas pendientes");
            return;
        }
        log.info("[AeroLuggage/DiaADia] - PLAN: iniciando planificacion para {} maletas en {} vuelos",
                pendientes.size(), vuelosFuturos.size());
        final ALNS alns = new ALNS(alnsConfig.toParametrosALNS());
        try {
            final List<VueloInstancia> vuelosCopia = vuelosFuturos.stream()
                    .map(v -> new VueloInstancia(
                            v.getIdVueloInstancia(), v.getCodigo(), v.getVueloProgramado(),
                            v.getFechaOperacion(), v.getFechaSalida(), v.getFechaLlegada(),
                            v.getCapacidadMaxima(), v.getCapacidadDisponible(),
                            v.getAeropuertoOrigen(), v.getAeropuertoDestino(), v.getEstado()))
                    .collect(Collectors.toList());
            final Map<String, VueloInstancia> idxVuelos = vuelosCopia.stream()
                    .collect(Collectors.toMap(VueloInstancia::getIdVueloInstancia, v -> v, (a, b) -> a));
            final List<Ruta> rutasComprometidas = new ArrayList<>();
            for (final Ruta r : rutasPorMaleta.values()) {
                if (r != null && r.getEstado() != EstadoRuta.REPLANIFICADA) {
                    rutasComprometidas.add(new Ruta(
                            r.getIdRuta(), r.getIdMaleta(),
                            r.getPlazoMaximoDias(), r.getDuracion(),
                            r.getSubrutaIds(), r.getEstado(), r.getFechaEntrega()));
                }
            }
            final List<Aeropuerto> aeropuertosCopia = aeropuertos.values().stream()
                    .map(a -> new Aeropuerto(
                            a.getIdAeropuerto(), a.getCiudad(),
                            a.getCapacidadAlmacen(), a.getMaletasActuales(),
                            a.getLongitud(), a.getLatitud(), a.getHusoGMT()))
                    .collect(Collectors.toList());
            final Map<String, Aeropuerto> idxAeropuertos = new HashMap<>(aeropuertos);
            final InstanciaProblema instancia = new InstanciaProblema(
                    "DIAADIA-" + sessionId,
                    new ArrayList<>(pendientes),
                    new ArrayList<>(vuelosProgramados.values()),
                    new ArrayList<>(vuelosCopia),
                    new ArrayList<>(aeropuertosCopia));
            instancia.setFechaEvaluacion(ahora);
            instancia.setRutasComprometidas(rutasComprometidas);
            final Map<String, Integer> ocupacionBase = new HashMap<>();
            for (final Aeropuerto a : aeropuertos.values()) {
                if (a != null) {
                    ocupacionBase.put(a.getIdAeropuerto(), Math.max(0, a.getMaletasActuales()));
                }
            }
            instancia.setOcupacionBaseAeropuerto(ocupacionBase);
            final Map<String, Maleta> idxMaletas = new HashMap<>();
            for (final Maleta m : maletasPorId.values()) {
                if (m != null) idxMaletas.put(m.getIdMaleta(), m);
            }
            final Map<String, NavigableMap<LocalDateTime, Integer>> eventosBase =
                    ALNSUtil.construirEventosBase(rutasComprometidas, instancia, idxMaletas);
            instancia.setEventosBaseAeropuerto(eventosBase);
            final InstanciaProblema copia = instancia.deepCopy();
            alns.ejecutar(copia);
            final Solucion solucion = alns.getMejorSolucion();
            if (solucion == null || solucion.getSolucion().isEmpty()) {
                log.warn("[AeroLuggage/DiaADia] - PLAN: ALNS no encontro solucion para {} maletas",
                        pendientes.size());
                return;
            }
            final List<Ruta> nuevasRutas = solucion.getSolucion().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            for (final Ruta r : nuevasRutas) {
                if (r.getIdMaleta() != null) {
                    rutasPorMaleta.put(r.getIdMaleta(), r);
                }
            }
            for (final Ruta r : nuevasRutas) {
                onRutaAgregada(r);
            }
            log.info("[AeroLuggage/DiaADia] - PLAN: {} maletas enrutadas exitosamente",
                    nuevasRutas.size());
        } catch (final Exception exception) {
            log.error("[AeroLuggage/DiaADia] - PLAN: error durante planificacion: {}",
                    exception.getMessage());
        } finally {
            alns.limpiarInstancia();
        }
    }

    private void onRutaAgregada(final Ruta ruta) {
        if (ruta == null || ruta.getIdMaleta() == null) return;
        final Maleta m = maletasPorId.get(ruta.getIdMaleta());
        if (m == null || m.getFechaRegistro() == null) return;
        final List<String> ids = ruta.getSubrutas();
        if (ids.isEmpty()) return;
        final Map<String, VueloInstancia> vueloIndex = getVueloIndex();
        agregarEvento(m.getFechaRegistro(), TipoEventoSim.MALETA_APARECE,
                ruta.getIdMaleta(),
                m.getPedido() != null && m.getPedido().getAeropuertoOrigen() != null
                        ? m.getPedido().getAeropuertoOrigen().getIdAeropuerto() : null,
                -1);
        for (int i = 0; i < ids.size(); i++) {
            final VueloInstancia v = vueloIndex.get(ids.get(i));
            if (v == null) continue;
            final String idAeroOrig = v.getAeropuertoOrigen() != null
                    ? v.getAeropuertoOrigen().getIdAeropuerto() : null;
            final String idAeroDest = v.getAeropuertoDestino() != null
                    ? v.getAeropuertoDestino().getIdAeropuerto() : null;
            final boolean ultimo = (i == ids.size() - 1);
            if (v.getFechaSalida() != null && idAeroOrig != null) {
                agregarEvento(v.getFechaSalida(), TipoEventoSim.MALETA_SALE_AEROP,
                        ruta.getIdMaleta(), idAeroOrig, -1);
            }
            if (v.getFechaLlegada() != null && idAeroDest != null) {
                agregarEvento(v.getFechaLlegada(), TipoEventoSim.MALETA_LLEGA_AEROP,
                        ruta.getIdMaleta(), idAeroDest, 1);
            }
            if (ultimo && v.getFechaLlegada() != null && idAeroDest != null) {
                agregarEvento(v.getFechaLlegada().plusMinutes(10), TipoEventoSim.MALETA_ENTREGADA,
                        ruta.getIdMaleta(), idAeroDest, -1);
            }
        }
        if (!ids.isEmpty()) {
            final VueloInstancia primero = vueloIndex.get(ids.getFirst());
            final VueloInstancia ultimoSub = vueloIndex.get(ids.getLast());
            if (primero != null && primero.getFechaSalida() != null) {
                agregarEvento(primero.getFechaSalida(), TipoEventoSim.RUTA_ACTIVA,
                        ruta.getIdRuta(), null, 0);
            }
            if (ultimoSub != null && ultimoSub.getFechaLlegada() != null) {
                agregarEvento(ultimoSub.getFechaLlegada(), TipoEventoSim.RUTA_COMPLETA,
                        ruta.getIdRuta(), null, 0);
            }
        }
        if (m.getFechaRegistro() != null
                && !m.getFechaRegistro().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            for (final String idVuelo : ids) {
                final VueloInstancia vuelo = vueloIndex.get(idVuelo);
                if (vuelo == null) continue;
                vuelo.setCapacidadDisponible(
                        Math.max(0, vuelo.getCapacidadDisponible() - 1));
            }
        }
        for (final String vid : ids) {
            final VueloInstancia vi = vueloIndex.get(vid);
            if (vi != null) {
                vueloInstanciaRepositorio.insertarOActualizar(vi,
                        vi.getVueloProgramado() != null
                                ? vi.getVueloProgramado().getIdVueloProgramado()
                                : null);
            }
        }
    }

    public void procesarPedidosBulk(final String icaoOrigen, final List<String> lineas) {
        touchSession();
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }
            final Aeropuerto origen = aeropuertos.get(icaoOrigen);
            if (origen == null) {
                throw new IllegalArgumentException("Aeropuerto de origen no encontrado: " + icaoOrigen);
            }
            final String fechaStr = LocalDateTime.now(ZoneOffset.UTC).toLocalDate()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            final int ultimoId = pedidoRepositorio.obtenerUltimoSecuencial(icaoOrigen, fechaStr);
            int siguienteId = ultimoId + 1;
            final List<PedidoRequest> pedidos = new ArrayList<>();
            final List<String> errores = new ArrayList<>();
            for (int i = 0; i < lineas.size(); i++) {
                final String linea = lineas.get(i).trim();
                if (linea.isEmpty()) continue;
                final String[] partes = linea.split("-");
                if (partes.length < 3) {
                    errores.add("Linea " + (i + 1) + ": formato invalido, se esperaba DEST-CANT-CLIENT");
                    continue;
                }
                final String idDestino = partes[0].trim();
                final String cantidadStr = partes[1].trim();
                final String idCliente = partes[2].trim();
                if (idDestino.isEmpty() || cantidadStr.isEmpty() || idCliente.isEmpty()) {
                    errores.add("Linea " + (i + 1) + ": campos vacios");
                    continue;
                }
                final int cantidad;
                try {
                    cantidad = Integer.parseInt(cantidadStr);
                } catch (final NumberFormatException e) {
                    errores.add("Linea " + (i + 1) + ": cantidad invalida - " + cantidadStr);
                    continue;
                }
                if (cantidad < 1) {
                    errores.add("Linea " + (i + 1) + ": cantidad debe ser >= 1");
                    continue;
                }
                final Aeropuerto destino = aeropuertos.get(idDestino);
                if (destino == null) {
                    errores.add("Linea " + (i + 1) + ": aeropuerto destino no encontrado - " + idDestino);
                    continue;
                }
                final String idPedido = String.format("PED-%s-%s-%05d",
                        icaoOrigen, fechaStr, siguienteId);
                final PedidoRequest req = new PedidoRequest();
                req.setIdPedido(idPedido);
                req.setIdAeropuertoOrigen(icaoOrigen);
                req.setIdAeropuertoDestino(idDestino);
                req.setCantidadMaletas(cantidad);
                pedidos.add(req);
                siguienteId++;
            }
            if (!errores.isEmpty()) {
                throw new IllegalArgumentException(
                        "Errores de validacion:\n" + String.join("\n", errores));
            }
            if (pedidos.isEmpty()) {
                throw new IllegalArgumentException("No se encontraron pedidos validos en el archivo");
            }
            for (final PedidoRequest req : pedidos) {
                procesarPedidoBulk(req);
            }
            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sessionId),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sessionId)
                            .withEstado(ESTADO_PEDIDO_PROCESADO)
                            .withMensaje("Pedidos bulk procesados: " + pedidos.size())
                            .build());
        }
    }

    private void procesarPedidoBulk(final PedidoRequest request) {
        final String icaoOrigen = request.getIdAeropuertoOrigen();
        final String icaoDestino = request.getIdAeropuertoDestino();
        final Aeropuerto origen = aeropuertos.get(icaoOrigen);
        final Aeropuerto destino = aeropuertos.get(icaoDestino);
        if (origen == null || destino == null) {
            throw new IllegalArgumentException("Aeropuerto de origen o destino no encontrado");
        }
        final LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC);
        final String fechaStr = ahora.toLocalDate().format(
                DateTimeFormatter.ofPattern("yyyyMMdd"));
        final Pedido pedido = new Pedido(
                request.getIdPedido(),
                origen,
                destino,
                ahora,
                request.getCantidadMaletas(),
                EstadoPedido.REGISTRADO.name());
        pedido.calcularFechaHoraPlazo();
        pedidos.put(pedido.getIdPedido(), pedido);

        final List<Maleta> nuevasMaletas = new ArrayList<>();
        for (int i = 0; i < request.getCantidadMaletas(); i++) {
            final String idMaleta = String.format("MAL-%s-%s-%s-B%03d",
                    icaoOrigen, fechaStr,
                    pedido.getIdPedido().substring(pedido.getIdPedido().lastIndexOf('-') + 1),
                    i + 1);
            final Maleta maleta = new Maleta(
                    idMaleta,
                    pedido,
                    ahora,
                    null,
                    EstadoMaleta.EN_ALMACEN);
            maletasPorId.put(idMaleta, maleta);
            nuevasMaletas.add(maleta);
        }

        planificarPendientes();

        final boolean todasAsignadas = nuevasMaletas.stream()
                .allMatch(m -> rutasPorMaleta.containsKey(m.getIdMaleta()));

        if (!todasAsignadas) {
            for (final Maleta m : nuevasMaletas) {
                maletasPorId.remove(m.getIdMaleta());
                rutasPorMaleta.remove(m.getIdMaleta());
            }
            pedidos.remove(pedido.getIdPedido());
            throw new IllegalStateException(
                    "No se encontro ruta disponible para el pedido " + request.getIdPedido());
        }

        pedidoRepositorio.insertar(pedido);
        for (final Maleta bag : nuevasMaletas) {
            maletaRepositorio.insertar(bag);
            final Ruta route = rutasPorMaleta.get(bag.getIdMaleta());
            if (route != null) {
                rutaRepositorio.insertar(route);
            }
        }
    }

    public void touchSession() {
        if (activa) {
            lastAccessTime = System.currentTimeMillis();
        }
    }

    private void cargarPedidosDesdeBD() {
        final List<Pedido> pedidosBD = pedidoRepositorio.obtenerNoEntregados();
        for (final Pedido p : pedidosBD) {
            if (p == null) continue;
            pedidos.put(p.getIdPedido(), p);
        }
        log.info("[AeroLuggage/DiaADia] - PEDIDOS DESDE BD: {}", pedidos.size());
    }

    private void cargarMaletasDesdeBD() {
        final List<Maleta> maletasBD = maletaRepositorio.obtenerNoEntregadas();
        for (final Maleta m : maletasBD) {
            if (m == null) continue;
            maletasPorId.put(m.getIdMaleta(), m);
        }
        log.info("[AeroLuggage/DiaADia] - MALETAS DESDE BD: {}", maletasBD.size());
    }

    private void recalcularOcupacionAeropuertos() {
        for (final Aeropuerto a : aeropuertos.values()) {
            if (a != null) a.setMaletasActuales(0);
        }
        for (final Maleta m : maletasPorId.values()) {
            if (m == null) continue;
            if (m.getEstado() == EstadoMaleta.EN_ALMACEN && m.getAeropuertoActual() != null) {
                final Aeropuerto a = aeropuertos.get(m.getAeropuertoActual());
                if (a != null) {
                    a.setMaletasActuales(a.getMaletasActuales() + 1);
                }
            }
        }
    }

    private void recalcularOcupacionVuelos() {
        for (final VueloInstancia v : vuelosInstancia.values()) {
            if (v != null) v.setCapacidadDisponible(v.getCapacidadMaxima());
        }
        for (final Map.Entry<String, Ruta> entry : rutasPorMaleta.entrySet()) {
            final Ruta ruta = entry.getValue();
            if (ruta == null) continue;
            final List<String> subrutas = ruta.getSubrutas();
            if (subrutas == null || subrutas.isEmpty()) continue;
            for (final String vid : subrutas) {
                final VueloInstancia vuelo = vuelosInstancia.get(vid);
                if (vuelo != null) {
                    vuelo.setCapacidadDisponible(
                            Math.max(0, vuelo.getCapacidadDisponible() - 1));
                }
            }
        }
    }

    private void recrearEventosRuta(final Ruta ruta) {
        final List<String> subrutas = ruta.getSubrutaIds();
        if (subrutas == null || subrutas.isEmpty()) return;

        final String idMaleta = ruta.getIdMaleta();
        final Maleta maleta = maletasPorId.get(idMaleta);
        if (maleta == null) return;

        for (final String vid : subrutas) {
            if (!vuelosInstancia.containsKey(vid)) {
                vueloInstanciaRepositorio.obtenerPorId(vid)
                        .ifPresent(v -> vuelosInstancia.put(vid, v));
            }
        }

        final VueloInstancia primerVuelo = vuelosInstancia.get(subrutas.get(0));
        if (primerVuelo == null || primerVuelo.getAeropuertoOrigen() == null) return;
        final String aeroOrigen = primerVuelo.getAeropuertoOrigen().getIdAeropuerto();
        final VueloInstancia ultimoVuelo = vuelosInstancia.get(subrutas.get(subrutas.size() - 1));
        if (ultimoVuelo == null || ultimoVuelo.getAeropuertoDestino() == null) return;
        final String aeroDestino = ultimoVuelo.getAeropuertoDestino().getIdAeropuerto();

        agregarEvento(primerVuelo.getFechaSalida().minusMinutes(30),
                TipoEventoSim.MALETA_APARECE, idMaleta, aeroOrigen, 1);

        for (final String vid : subrutas) {
            final VueloInstancia v = vuelosInstancia.get(vid);
            if (v == null) continue;
            final String idOrigen = v.getAeropuertoOrigen() != null
                    ? v.getAeropuertoOrigen().getIdAeropuerto() : null;
            final String idDest = v.getAeropuertoDestino() != null
                    ? v.getAeropuertoDestino().getIdAeropuerto() : null;
            agregarEvento(v.getFechaSalida(), TipoEventoSim.MALETA_SALE_AEROP,
                    idMaleta, idOrigen, -1);
            agregarEvento(v.getFechaLlegada(), TipoEventoSim.MALETA_LLEGA_AEROP,
                    idMaleta, idDest, 1);
        }

        agregarEvento(ultimoVuelo.getFechaLlegada().plusMinutes(10),
                TipoEventoSim.MALETA_ENTREGADA, idMaleta, aeroDestino, -1);
    }

    private void guardarEstadosBD() {
        int maletasPersistidas = 0;
        for (final Maleta m : maletasPorId.values()) {
            if (m == null) continue;
            maletaRepositorio.actualizar(m);
            maletasPersistidas++;
        }
        int pedidosPersistidos = 0;
        for (final Pedido p : pedidos.values()) {
            if (p == null) continue;
            pedidoRepositorio.actualizar(p);
            pedidosPersistidos++;
        }
        int rutasPersistidas = 0;
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r == null) continue;
            rutaRepositorio.actualizar(r);
            rutasPersistidas++;
        }
        log.info("[AeroLuggage/DiaADia] - ESTADOS BD: maletas={}, pedidos={}, rutas={}",
                maletasPersistidas, pedidosPersistidos, rutasPersistidas);
    }

    public void registrarCliente(final String wsSessionId) {
        final boolean nuevo = clientesConectados.add(wsSessionId);
        if (nuevo) {
            lastAccessTime = System.currentTimeMillis();
            log.info("[AeroLuggage/DiaADia] - CLIENTE REGISTRADO: wsSessionId={}, total={}",
                    wsSessionId, clientesConectados.size());
        }
    }

    public void desregistrarCliente(final String wsSessionId) {
        clientesConectados.remove(wsSessionId);
        log.info("[AeroLuggage/DiaADia] - CLIENTE DESREGISTRADO: wsSessionId={}, total={}",
                wsSessionId, clientesConectados.size());
    }

    private void limpiarEstado() {
        sessionId = null;
        aeropuertos.clear();
        vuelosProgramados.clear();
        vuelosInstancia.clear();
        pedidos.clear();
        maletasPorId.clear();
        rutasPorMaleta.clear();
        totalMaletasEntregadas.set(0);
        idsEntregadasEnTick.clear();
        idsCompletadasEnTick.clear();
        eventos = null;
        ultimoTiempoProcesado = null;
        tickActual.set(0);
        stateVersion.set(1);
    }

    public Map<String, VueloInstancia> getVueloIndex() {
        touchSession();
        return new HashMap<>(vuelosInstancia);
    }

    public String getSessionId() { touchSession(); return sessionId; }
    public boolean isActiva() { touchSession(); return activa; }
    public List<Aeropuerto> getAeropuertos() { touchSession(); return new ArrayList<>(aeropuertos.values()); }
    public List<VueloProgramado> getVuelosProgramados() { touchSession(); return new ArrayList<>(vuelosProgramados.values()); }
    public List<VueloInstancia> getVuelosInstancia() { touchSession(); return new ArrayList<>(vuelosInstancia.values()); }
    public Collection<Pedido> getPedidos() { touchSession(); return pedidos.values(); }
    public List<Pedido> getPedidosNoEntregados() { return pedidoRepositorio.obtenerNoEntregados(); }
    public Collection<Maleta> getMaletas() { return maletaRepositorio.obtenerNoEntregadas(); }
    public Collection<Ruta> getRutas() { return rutaRepositorio.obtenerActivasPlanificadas(); }
    public Ruta getRutaPorMaleta(final String idMaleta) {
        touchSession();
        final Ruta r = rutasPorMaleta.get(idMaleta);
        if (r != null) return r;
        return rutaRepositorio.obtenerPorId(idMaleta).orElse(null);
    }
    public Maleta getMaleta(final String idMaleta) { touchSession(); return maletasPorId.get(idMaleta); }
    public int getTickActual() { touchSession(); return tickActual.get(); }
    public List<VueloInstancia> getVuelosCalientes() { touchSession(); return new ArrayList<>(vuelosInstancia.values()); }

    public List<VueloInstancia> getVuelosFrontend() {
        touchSession();
        return vuelosInstancia.values().stream()
                .filter(v -> v != null)
                .filter(v -> v.getEstado() == EstadoVuelo.EN_PROGRESO
                        || v.getEstado() == EstadoVuelo.CONFIRMADO)
                .collect(Collectors.toList());
    }

    public List<VueloInstancia> getVuelosNuevos() {
        touchSession();
        if (idsVuelosRecienConfirmados.isEmpty()) {
            return List.of();
        }
        final List<VueloInstancia> resultado = new ArrayList<>();
        for (final String id : idsVuelosRecienConfirmados) {
            final VueloInstancia v = vuelosInstancia.get(id);
            if (v != null) {
                resultado.add(v);
            }
        }
        idsVuelosRecienConfirmados.clear();
        log.info("[AeroLuggage/DiaADia] - VUELOS_NUEVOS: {} vuelos", resultado.size());
        return resultado;
    }

    public LocalDateTime getSimTimeActual() {
        touchSession();
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public Map<String, Object> obtenerEstadoActual() {
        if (!activa) {
            return Map.of();
        }
        final Map<String, Object> estado = new HashMap<>();
        estado.put("sessionId", sessionId);
        estado.put("activa", activa);
        estado.put("simTime", getSimTimeActual().format(FORMATO_FECHA_HORA));
        estado.put("tick", tickActual.get());
        return estado;
    }
}
