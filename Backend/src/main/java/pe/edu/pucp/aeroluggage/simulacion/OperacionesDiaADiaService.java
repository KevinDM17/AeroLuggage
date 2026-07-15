package pe.edu.pucp.aeroluggage.simulacion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNS;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNSUtil;
import pe.edu.pucp.aeroluggage.config.ALNSConfig;
import pe.edu.pucp.aeroluggage.config.OperacionesDiaADiaParams;
import pe.edu.pucp.aeroluggage.config.SistemaConfiguracion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoRequest;
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
import java.time.LocalTime;
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
public class OperacionesDiaADiaService {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final String TOPIC_SIM = "/topic/operations/";
    private static final String TOPIC_ESTADO = "/topic/operations/%s/estado";

    private static final String ESTADO_INICIADA = "INICIADA";
    private static final String ESTADO_DETENIDA = "DETENIDA";
    private static final String ESTADO_PEDIDO_PROCESADO = "PEDIDO_PROCESADO";

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final JdbcTemplate jdbcTemplate;
    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;
    private final PedidoRepositorio pedidoRepositorio;
    private final MaletaRepositorio maletaRepositorio;
    private final RutaRepositorio rutaRepositorio;
    private final VueloInstanciaRepositorio vueloInstanciaRepositorio;
    private final OperacionesDiaADiaParams params;
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
    // Maletas entregadas en las ultimas 4h, retenidas para el panel "Entregados"
    // (en operaciones las entregadas se quitan del estado vivo). Se purgan por edad.
    private final ConcurrentHashMap<String, MaletaEntregadaReciente> maletasEntregadasRecientes = new ConcurrentHashMap<>();
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
    private volatile boolean autoIniciado;

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

    public OperacionesDiaADiaService(final AeropuertoRepositorio aeropuertoRepositorio,
                                    final JdbcTemplate jdbcTemplate,
                                    final VueloProgramadoRepositorio vueloProgramadoRepositorio,
                                    final PedidoRepositorio pedidoRepositorio,
                                    final MaletaRepositorio maletaRepositorio,
                                    final RutaRepositorio rutaRepositorio,
                                    final VueloInstanciaRepositorio vueloInstanciaRepositorio,
                                    final OperacionesDiaADiaParams params,
                                    final SistemaConfiguracion sistemaConfiguracion,
                                    final ALNSConfig alnsConfig,
                                    final SimpMessagingTemplate broker) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.jdbcTemplate = jdbcTemplate;
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

            for (final VueloInstancia v : vuelosInstancia.values()) {
                if (v == null || v.getFechaSalida() == null || v.getFechaLlegada() == null) continue;
                final EstadoVuelo estado = v.getEstado();
                final String id = v.getIdVueloInstancia();
                final int umbral = sistemaConfiguracion.getUmbralConfirmacionMinutos();
                if (estado == EstadoVuelo.PROGRAMADO) {
                    agregarEvento(v.getFechaSalida().minusMinutes(umbral),
                            TipoEventoSim.VUELO_CONFIRMA, id, null, 0);
                }
                if (estado == EstadoVuelo.PROGRAMADO || estado == EstadoVuelo.CONFIRMADO) {
                    agregarEvento(v.getFechaSalida(), TipoEventoSim.VUELO_INICIA, id, null, 0);
                }
                if (estado != EstadoVuelo.FINALIZADO && estado != EstadoVuelo.CANCELADO) {
                    agregarEvento(v.getFechaLlegada(), TipoEventoSim.VUELO_FINALIZA, id, null, 0);
                }
            }

            recalcularOcupacionAeropuertos();
            recalcularOcupacionVuelos();
            guardarEstadosBD();
            pedidos.clear();
            maletasPorId.clear();
            rutasPorMaleta.clear();
            maletasEntregadasRecientes.clear();



            return sessionId;
        }
    }

    public String autoStart() {
        synchronized (lock) {
            final String sid = iniciar();
            if (!ticksActivos || tickTask == null || tickTask.isCancelled()) {
                idsVuelosRecienConfirmados.clear();
                ticksActivos = true;
                autoIniciado = true;
                final long tickIntervalMs = Math.max(100L, params.getTickIntervalMs());
                tickTask = scheduler.scheduleWithFixedDelay(
                        this::ejecutarTick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);

            }
            return sid;
        }
    }

    public void confirmarConexion() {
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }

            if (ticksActivos && tickTask != null && !tickTask.isCancelled()) {
                lastAccessTime = System.currentTimeMillis();

                return;
            }

            idsVuelosRecienConfirmados.clear();
            ticksActivos = true;
            final long tickIntervalMs = Math.max(100L, params.getTickIntervalMs());
            tickTask = scheduler.scheduleWithFixedDelay(
                    this::ejecutarTick, tickIntervalMs, tickIntervalMs, TimeUnit.MILLISECONDS);

            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sessionId),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sessionId)
                            .withEstado(ESTADO_INICIADA)
                            .withMensaje("Operaciones dia a dia iniciada")
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
            broker.convertAndSend(
                    String.format(TOPIC_ESTADO, sessionId),
                    SimulacionEstadoDTO.builder()
                            .withSessionId(sessionId)
                            .withEstado(ESTADO_DETENIDA)
                            .withMensaje("Operaciones dia a dia detenida")
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
            if (!autoIniciado && clientesConectados.isEmpty()
                    && System.currentTimeMillis() - lastAccessTime > params.getTimeoutMs()) {
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
            }

            if (System.currentTimeMillis() - lastDepuraMs >= DEPURA_INTERVALO_MS) {
                depurarVuelos(ahora);
                lastDepuraMs = System.currentTimeMillis();
            }

            final SimulacionTickLigeroDTO tickDTO = construirTickDTO(ahora);
            broker.convertAndSend(TOPIC_SIM + sessionId, tickDTO);
            maletasPorId.clear();
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
            final String ubicacion = obtenerUbicacionMaleta(m, estado);
            estadosMaletas.add(EstadoMaletaDTO.builder()
                    .withId(m.getIdMaleta())
                    .withE(estado != null ? estado.ordinal() : 0)
                    .withU(ubicacion)
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

        final Map<String, Integer> ocupacionPorVuelo = new HashMap<>();
        if (rutasPorMaleta.isEmpty()) {
            for (final Ruta r : rutaRepositorio.obtenerActivasPlanificadas()) {
                if (r != null && r.getIdMaleta() != null) {
                    rutasPorMaleta.put(r.getIdMaleta(), r);
                }
            }
        }
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r == null || r.getSubrutaIds() == null) continue;
            for (final String idVuelo : r.getSubrutaIds()) {
                if (idVuelo == null) continue;
                ocupacionPorVuelo.merge(idVuelo, 1, Integer::sum);
            }
        }

        final List<EstadoVueloDTO> estadosVuelos = new ArrayList<>();
        for (final VueloInstancia v : vuelosInstancia.values()) {
            if (v == null) continue;
            final EstadoVuelo estado = v.getEstado();
            if (estado == EstadoVuelo.PROGRAMADO) continue;
            if (estado == EstadoVuelo.EN_PROGRESO) vuelosActivos++;
            final int maxCap = Math.max(0, v.getCapacidadMaxima());
            capacidadTotal += maxCap;
            final int usado = ocupacionPorVuelo.getOrDefault(v.getIdVueloInstancia(), 0);
            final int capDisp = Math.max(0, maxCap - usado);
            capacidadDisponible += capDisp;
            estadosVuelos.add(EstadoVueloDTO.builder()
                    .withId(v.getIdVueloInstancia())
                    .withE(estado != null ? estado.ordinal() : 0)
                    .withCap(capDisp)
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

    private String obtenerUbicacionMaleta(final Maleta m, final EstadoMaleta estado) {
        if (estado == EstadoMaleta.EN_ALMACEN || estado == EstadoMaleta.ENTREGADA) {
            return m.getAeropuertoActual();
        }
        if (estado == EstadoMaleta.EN_TRANSITO) {
            Ruta r = rutasPorMaleta.get(m.getIdMaleta());
            if (r == null) {
                r = rutaRepositorio.obtenerPorId(m.getIdMaleta()).orElse(null);
                if (r != null) rutasPorMaleta.put(m.getIdMaleta(), r);
            }
            if (r != null && r.getSubrutaIds() != null) {
                VueloInstancia candidata = null;
                for (final String subId : r.getSubrutaIds()) {
                    final VueloInstancia v = vuelosInstancia.get(subId);
                    if (v == null) continue;
                    if (v.getEstado() == pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo.EN_PROGRESO) {
                        return v.getCodigo();
                    }
                    if (candidata == null) candidata = v;
                }
                if (candidata != null) return candidata.getCodigo();
            }
        }
        return m.getAeropuertoActual();
    }

    private void generarVuelosParaFecha(final LocalDate fecha) {
        final String fechaStr = fecha.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        final LocalDateTime inicioVentanaUtc = fecha.atStartOfDay();
        final LocalDateTime finVentanaUtc = inicioVentanaUtc.plusDays(1);
        int seq = vueloInstanciaRepositorio.obtenerUltimoSecuencial(fechaStr) + 1;
        for (final VueloProgramado vp : vuelosProgramados.values()) {
            if (vp == null || vp.getHoraSalida() == null || vp.getHoraLlegada() == null) continue;
            final int gmtOrigen = vp.getAeropuertoOrigen() != null
                    ? vp.getAeropuertoOrigen().getHusoGMT() : 0;
            final int gmtDestino = vp.getAeropuertoDestino() != null
                    ? vp.getAeropuertoDestino().getHusoGMT() : 0;

            LocalDate fechaLocalOrigen = fecha;
            LocalDateTime salidaUtc = LocalDateTime.of(fechaLocalOrigen, vp.getHoraSalida())
                    .minusHours(gmtOrigen);
            if (!salidaUtc.isBefore(finVentanaUtc)) {
                fechaLocalOrigen = fecha.minusDays(1);
            } else if (salidaUtc.isBefore(inicioVentanaUtc)) {
                fechaLocalOrigen = fecha.plusDays(1);
            }
            salidaUtc = LocalDateTime.of(fechaLocalOrigen, vp.getHoraSalida()).minusHours(gmtOrigen);

            LocalDate fechaLlegadaLocal = fechaLocalOrigen;
            if (vp.getHoraLlegada().isBefore(vp.getHoraSalida())) {
                fechaLlegadaLocal = fechaLlegadaLocal.plusDays(1);
            }
            LocalDateTime llegadaUtc = LocalDateTime.of(fechaLlegadaLocal, vp.getHoraLlegada())
                    .minusHours(gmtDestino);
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
        // Solo se retiran los FINALIZADO (cumplieron su ciclo). Los CANCELADO se
        // CONSERVAN en el set vivo para que se sigan emitiendo al frontend (con
        // estado CANCELADO) y puedan verse/filtrarse en la lista de vuelos. No
        // aparecen en el mapa porque este solo dibuja vuelos EN_PROGRESO.
        vuelosInstancia.values().removeIf(v -> v.getEstado() == EstadoVuelo.FINALIZADO);
    }

    private void depurarVuelos(final LocalDateTime ahora) {
        final LocalDateTime corte = ahora.plusHours(48);
        vuelosInstancia.values().removeIf(v -> v.getEstado() == EstadoVuelo.PROGRAMADO
                && v.getFechaSalida() != null
                && v.getFechaSalida().isAfter(corte));
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

        // Retener la maleta entregada (con su ruta resuelta) para el panel
        // "Entregados (ultimas 4h)". Se captura aqui porque la ruta se acaba de
        // remover y los vuelos podrian limpiarse despues.
        if (idPedido != null) {
            final String origenPedido = m.getPedido() != null && m.getPedido().getAeropuertoOrigen() != null
                    ? m.getPedido().getAeropuertoOrigen().getIdAeropuerto() : null;
            final String destinoPedido = m.getPedido() != null && m.getPedido().getAeropuertoDestino() != null
                    ? m.getPedido().getAeropuertoDestino().getIdAeropuerto() : null;
            final List<String> utsEntrega = new ArrayList<>();
            final List<String> origenesEntrega = new ArrayList<>();
            final List<String> destinosEntrega = new ArrayList<>();
            if (r != null) {
                for (final String subId : r.getSubrutaIds()) {
                    final VueloInstancia v = vuelosInstancia.get(subId);
                    if (v == null) continue;
                    if (v.getCodigo() != null) utsEntrega.add(v.getCodigo());
                    if (v.getAeropuertoOrigen() != null) origenesEntrega.add(v.getAeropuertoOrigen().getIdAeropuerto());
                    if (v.getAeropuertoDestino() != null) destinosEntrega.add(v.getAeropuertoDestino().getIdAeropuerto());
                }
            }
            maletasEntregadasRecientes.put(m.getIdMaleta(), new MaletaEntregadaReciente(
                    m.getIdMaleta(), idPedido, origenPedido, destinoPedido,
                    utsEntrega, origenesEntrega, destinosEntrega, ahora));
        }

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
            log.warn("[AeroLuggage/OperacionesDiaADia] - PLAN: sin vuelos instancia disponibles");
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
            log.warn("[AeroLuggage/OperacionesDiaADia] - PLAN: no hay vuelos futuros disponibles");
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
            log.info("[AeroLuggage/OperacionesDiaADia] - PLAN: sin maletas pendientes");
            return;
        }
        log.info("[AeroLuggage/OperacionesDiaADia] - PLAN: iniciando planificacion para {} maletas en {} vuelos",
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
                log.warn("[AeroLuggage/OperacionesDiaADia] - PLAN: ALNS no encontro solucion para {} maletas",
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
            log.info("[AeroLuggage/OperacionesDiaADia] - PLAN: {} maletas enrutadas exitosamente",
                    nuevasRutas.size());
        } catch (final Exception exception) {
            log.error("[AeroLuggage/OperacionesDiaADia] - PLAN: error durante planificacion: {}",
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

    public SimulacionEstadoDTO cancelarVueloProgramado(final String idVueloProgramado) {
        touchSession();
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }
            VueloProgramado vp = vuelosProgramados.get(idVueloProgramado);
            if (vp == null) {
                vp = vueloProgramadoRepositorio.obtenerPorId(idVueloProgramado).orElse(null);
                if (vp == null) {
                    vp = vueloProgramadoRepositorio.obtenerPorIdSimple(idVueloProgramado).orElse(null);
                    if (vp != null) {
                        if (vp.getAeropuertoOrigen() != null
                                && vp.getAeropuertoOrigen().getIdAeropuerto() != null) {
                            final Aeropuerto ao = aeropuertos.get(
                                    vp.getAeropuertoOrigen().getIdAeropuerto());
                            if (ao != null) {
                                vp.setAeropuertoOrigen(ao);
                            }
                        }
                        if (vp.getAeropuertoDestino() != null
                                && vp.getAeropuertoDestino().getIdAeropuerto() != null) {
                            final Aeropuerto ad = aeropuertos.get(
                                    vp.getAeropuertoDestino().getIdAeropuerto());
                            if (ad != null) {
                                vp.setAeropuertoDestino(ad);
                            }
                        }
                    }
                }
                if (vp != null) {
                    vuelosProgramados.put(idVueloProgramado, vp);
                }
            }
            if (vp == null) {
                vp = reconstruirVueloProgramadoFaltante(idVueloProgramado);
                if (vp != null) {
                    vueloProgramadoRepositorio.insertar(vp);
                    vuelosProgramados.put(idVueloProgramado, vp);
                }
            }
            if (vp == null) {
                throw new IllegalArgumentException(
                        "Vuelo programado no encontrado y no se pudo reconstruir: " + idVueloProgramado);
            }
            if (vp.getHoraSalida() == null) {
                throw new IllegalArgumentException(
                        "El vuelo programado no tiene hora de salida");
            }
            final LocalDateTime simTime = LocalDateTime.now(ZoneOffset.UTC);
            final int gmtOrigen = vp.getAeropuertoOrigen() != null
                    ? vp.getAeropuertoOrigen().getHusoGMT() : 0;
            final LocalDateTime simTimeOrigen = simTime.plusHours(gmtOrigen);
            final LocalDateTime salidaOrigenHoy = LocalDateTime.of(
                    simTimeOrigen.toLocalDate(), vp.getHoraSalida());
            final LocalDateTime cutoffOrigen = salidaOrigenHoy.minusHours(1);
            final LocalDate fechaOperacion = !simTimeOrigen.isAfter(cutoffOrigen)
                    ? simTimeOrigen.toLocalDate()
                    : simTimeOrigen.toLocalDate().plusDays(1);

            VueloInstancia instancia = null;
            for (final VueloInstancia vi : vuelosInstancia.values()) {
                if (vi == null || vi.getVueloProgramado() == null) continue;
                if (idVueloProgramado.equals(vi.getVueloProgramado().getIdVueloProgramado())
                        && fechaOperacion.equals(vi.getFechaOperacion())) {
                    instancia = vi;
                    break;
                }
            }
            if (instancia == null) {
                final VueloInstancia cancelada = crearInstanciaCancelada(vp, fechaOperacion);
                vuelosInstancia.put(cancelada.getIdVueloInstancia(), cancelada);
                vueloInstanciaRepositorio.insertarOActualizar(cancelada, idVueloProgramado);
                return SimulacionEstadoDTO.builder()
                        .withSessionId(sessionId)
                        .withEstado("VUELO_PROGRAMADO_CANCELADO")
                        .withMensaje("Se cancelo el vuelo programado " + vp.getCodigo()
                                + " para la fecha operativa " + fechaOperacion
                                + ". No habia ocurrencia activa; 0 maletas afectadas.")
                        .withSimTime(simTime.format(FORMATO_FECHA_HORA))
                        .build();
            }
            final EstadoVuelo estado = instancia.getEstado();
            if (estado == EstadoVuelo.EN_PROGRESO || estado == EstadoVuelo.FINALIZADO
                    || estado == EstadoVuelo.CANCELADO) {
                throw new IllegalStateException(
                        "La ocurrencia " + instancia.getIdVueloInstancia()
                                + " no puede cancelarse por su estado actual: " + estado);
            }

            final String idVuelo = instancia.getIdVueloInstancia();
            final String codigoVuelo = instancia.getCodigo();

            instancia.cancelar();
            vueloInstanciaRepositorio.actualizar(instancia);

            if (eventos != null) {
                eventos.values().forEach(list ->
                        list.removeIf(e -> idVuelo.equals(e.idEntidad())));
            }

            final Map<String, VueloInstancia> vueloIndex = getVueloIndex();
            final List<String> maletasAfectadas = new ArrayList<>();
            final Map<String, Aeropuerto> origenesOriginales = new HashMap<>();

            for (final Ruta ruta : new ArrayList<>(rutasPorMaleta.values())) {
                if (ruta == null) continue;
                final EstadoRuta estadoRuta = ruta.getEstado();
                if (estadoRuta == EstadoRuta.COMPLETADA
                        || estadoRuta == EstadoRuta.REPLANIFICADA) continue;
                final List<String> ids = ruta.getSubrutaIds();
                if (ids == null || !ids.contains(idVuelo)) continue;

                final String idMaleta = ruta.getIdMaleta();
                final Maleta maleta = maletasPorId.get(idMaleta);
                if (maleta == null || maleta.getPedido() == null) continue;

                for (final String subId : ids) {
                    final VueloInstancia v = vueloIndex.get(subId);
                    if (v == null || v.getEstado() == EstadoVuelo.CANCELADO
                            || v.getEstado() == EstadoVuelo.FINALIZADO) continue;
                    v.setCapacidadDisponible(v.getCapacidadDisponible() + 1);
                }

                ruta.setEstado(EstadoRuta.REPLANIFICADA);
                rutaRepositorio.actualizar(ruta);

                final int idx = ids.indexOf(idVuelo);
                String ubicacionActual = null;
                if (idx >= 0 && idx < ids.size()) {
                    final VueloInstancia vueloCancelado = vueloIndex.get(idVuelo);
                    if (vueloCancelado != null && vueloCancelado.getAeropuertoOrigen() != null) {
                        ubicacionActual = vueloCancelado.getAeropuertoOrigen().getIdAeropuerto();
                    }
                }
                if (ubicacionActual == null) {
                    ubicacionActual = maleta.getAeropuertoActual();
                }

                final Aeropuerto aeropuertoActual = aeropuertos.get(ubicacionActual);
                if (aeropuertoActual != null && maleta.getPedido().getAeropuertoOrigen() != null) {
                    origenesOriginales.put(idMaleta, maleta.getPedido().getAeropuertoOrigen());
                    maleta.getPedido().setAeropuertoOrigen(aeropuertoActual);
                }

                maleta.setAeropuertoActual(ubicacionActual);
                maleta.setEstado(EstadoMaleta.EN_ALMACEN);
                maletaRepositorio.actualizar(maleta);

                maletasAfectadas.add(idMaleta);
                rutasPorMaleta.remove(idMaleta);
            }


            if (!maletasAfectadas.isEmpty()) {
                planificarPendientes();

                int nuevasPersistidas = 0;
                for (final String idMaleta : maletasAfectadas) {
                    final Ruta nuevaRuta = rutasPorMaleta.get(idMaleta);
                    if (nuevaRuta != null) {
                        rutaRepositorio.insertar(nuevaRuta);
                        nuevasPersistidas++;
                    }
                    final Aeropuerto original = origenesOriginales.get(idMaleta);
                    if (original != null) {
                        final Maleta m = maletasPorId.get(idMaleta);
                        if (m != null && m.getPedido() != null) {
                            m.getPedido().setAeropuertoOrigen(original);
                        }
                    }
                }

            }

            return SimulacionEstadoDTO.builder()
                    .withSessionId(sessionId)
                    .withEstado("VUELO_PROGRAMADO_CANCELADO")
                    .withMensaje("Se cancelo el vuelo programado " + codigoVuelo
                            + " para la fecha operativa " + fechaOperacion
                            + ". " + maletasAfectadas.size() + " maletas replanificadas.")
                    .withSimTime(simTime.format(FORMATO_FECHA_HORA))
                    .build();
        }
    }

    public void onAeropuertoActualizado(final String iata, final int nuevaCapacidad) {
        synchronized (lock) {
            if (!activa) {
                return;
            }

            final Aeropuerto aeropuerto = aeropuertos.get(iata);
            if (aeropuerto == null) {
                return;
            }

            aeropuerto.setCapacidadAlmacen(nuevaCapacidad);
            recalcularOcupacionAeropuertos();

            final Map<String, VueloInstancia> vueloIndex = getVueloIndex();
            final List<String> maletasAfectadas = new ArrayList<>();

            for (final Maleta maleta : maletasPorId.values()) {
                if (!iata.equals(maleta.getAeropuertoActual())) {
                    continue;
                }
                if (maleta.getEstado() != EstadoMaleta.EN_ALMACEN) {
                    continue;
                }

                final String idMaleta = maleta.getIdMaleta();
                final Ruta ruta = rutasPorMaleta.remove(idMaleta);
                if (ruta != null && ruta.getSubrutaIds() != null) {
                    for (final String subId : ruta.getSubrutaIds()) {
                        final VueloInstancia v = vueloIndex.get(subId);
                        if (v == null || v.getEstado() == EstadoVuelo.CANCELADO
                                || v.getEstado() == EstadoVuelo.FINALIZADO) {
                            continue;
                        }
                        v.setCapacidadDisponible(v.getCapacidadDisponible() + 1);
                    }
                    ruta.setEstado(EstadoRuta.REPLANIFICADA);
                    rutaRepositorio.actualizar(ruta);
                }

                maletasAfectadas.add(idMaleta);
            }

            if (!maletasAfectadas.isEmpty()) {
                planificarPendientes();

                for (final String idMaleta : maletasAfectadas) {
                    final Ruta nuevaRuta = rutasPorMaleta.get(idMaleta);
                    if (nuevaRuta != null) {
                        rutaRepositorio.insertar(nuevaRuta);
                    }
                }
            }

            final SimulacionTickLigeroDTO tickDTO = construirTickDTO(
                    LocalDateTime.now(ZoneOffset.UTC));
            broker.convertAndSend(TOPIC_SIM + sessionId, tickDTO);
        }
    }

    private VueloInstancia crearInstanciaCancelada(final VueloProgramado vp, final LocalDate fechaOperacion) {
        final String fechaStr = fechaOperacion.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        final int gmtOrigen = vp.getAeropuertoOrigen() != null
                ? vp.getAeropuertoOrigen().getHusoGMT() : 0;
        final int gmtDestino = vp.getAeropuertoDestino() != null
                ? vp.getAeropuertoDestino().getHusoGMT() : 0;
        LocalDate fechaLocalOrigen = fechaOperacion;
        LocalDateTime salidaUtc = LocalDateTime.of(fechaLocalOrigen, vp.getHoraSalida())
                .minusHours(gmtOrigen);
        if (salidaUtc.isAfter(fechaOperacion.plusDays(1).atStartOfDay())) {
            fechaLocalOrigen = fechaOperacion.minusDays(1);
        } else if (salidaUtc.isBefore(fechaOperacion.atStartOfDay())) {
            fechaLocalOrigen = fechaOperacion.plusDays(1);
        }
        salidaUtc = LocalDateTime.of(fechaLocalOrigen, vp.getHoraSalida()).minusHours(gmtOrigen);
        LocalDate fechaLlegadaLocal = fechaLocalOrigen;
        if (vp.getHoraLlegada() != null && vp.getHoraLlegada().isBefore(vp.getHoraSalida())) {
            fechaLlegadaLocal = fechaLlegadaLocal.plusDays(1);
        }
        LocalDateTime llegadaUtc = vp.getHoraLlegada() != null
                ? LocalDateTime.of(fechaLlegadaLocal, vp.getHoraLlegada()).minusHours(gmtDestino)
                : salidaUtc.plusHours(1);
        if (!llegadaUtc.isAfter(salidaUtc)) {
            llegadaUtc = llegadaUtc.plusDays(1);
        }
        final String orig = vp.getAeropuertoOrigen() != null
                ? vp.getAeropuertoOrigen().getIdAeropuerto() : "??";
        final String dest = vp.getAeropuertoDestino() != null
                ? vp.getAeropuertoDestino().getIdAeropuerto() : "??";
        final int seq = vueloInstanciaRepositorio.obtenerUltimoSecuencial(fechaStr);
        final String id = String.format("VUE-%s-%s-%s-%06d", orig, dest, fechaStr, seq + 1);
        final VueloInstancia vi = new VueloInstancia(
                id, vp, fechaOperacion, salidaUtc, llegadaUtc,
                vp.getCapacidadMaxima(), vp.getCapacidadMaxima(),
                EstadoVuelo.CANCELADO);
        return vi;
    }

    private VueloProgramado reconstruirVueloProgramadoFaltante(final String idVueloProgramado) {
        if (idVueloProgramado == null || idVueloProgramado.isBlank()) {
            return null;
        }
        final String[] partes = idVueloProgramado.trim().split("-", 3);
        if (partes.length != 3) {
            return null;
        }
        final String origenId = partes[0].trim();
        final String destinoId = partes[1].trim();
        final String horaSalidaRaw = partes[2].trim();
        final Aeropuerto origen = aeropuertos.get(origenId);
        final Aeropuerto destino = aeropuertos.get(destinoId);
        if (origen == null || destino == null) {
            return null;
        }

        final LocalTime horaSalida;
        try {
            horaSalida = LocalTime.parse(horaSalidaRaw);
        } catch (final Exception ignored) {
            return null;
        }

        final VueloProgramado referencia = vuelosProgramados.values().stream()
                .filter(Objects::nonNull)
                .filter(v -> v.getAeropuertoOrigen() != null
                        && v.getAeropuertoDestino() != null
                        && origenId.equals(v.getAeropuertoOrigen().getIdAeropuerto())
                        && destinoId.equals(v.getAeropuertoDestino().getIdAeropuerto()))
                .findFirst()
                .orElse(null);

        final LocalTime horaLlegada = referencia != null && referencia.getHoraLlegada() != null
                ? ajustarHoraLlegadaReferencia(referencia, horaSalida)
                : horaSalida.plusHours(2);
        final int capacidadBase = referencia != null ? referencia.getCapacidadMaxima() : 0;

        final VueloProgramado reconstruido = new VueloProgramado(
                idVueloProgramado,
                idVueloProgramado,
                horaSalida,
                horaLlegada,
                capacidadBase,
                origen,
                destino
        );
        reconstruido.setActivo(true);
        return reconstruido;
    }

    private LocalTime ajustarHoraLlegadaReferencia(final VueloProgramado referencia, final LocalTime horaSalida) {
        if (referencia.getHoraSalida() == null || referencia.getHoraLlegada() == null) {
            return horaSalida.plusHours(2);
        }
        final int salidaRefMin = referencia.getHoraSalida().toSecondOfDay() / 60;
        int llegadaRefMin = referencia.getHoraLlegada().toSecondOfDay() / 60;
        if (!referencia.getHoraLlegada().isAfter(referencia.getHoraSalida())) {
            llegadaRefMin += 24 * 60;
        }
        int duracionMin = llegadaRefMin - salidaRefMin;
        if (duracionMin <= 0) {
            duracionMin = 120;
        }
        return horaSalida.plusMinutes(duracionMin);
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
    }

    private void cargarMaletasDesdeBD() {
        final List<Maleta> maletasBD = maletaRepositorio.obtenerNoEntregadas();
        for (final Maleta m : maletasBD) {
            if (m == null) continue;
            maletasPorId.put(m.getIdMaleta(), m);
        }
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
    }

    public void registrarCliente(final String wsSessionId) {
        final boolean nuevo = clientesConectados.add(wsSessionId);
        if (nuevo) {
            lastAccessTime = System.currentTimeMillis();
        }
    }

    public void desregistrarCliente(final String wsSessionId) {
        clientesConectados.remove(wsSessionId);
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

    /** Info minima de una maleta entregada, retenida para el panel "Entregados". */
    public record MaletaEntregadaReciente(String idMaleta, String idPedido, String origin, String dest,
            List<String> uts, List<String> origenesRuta, List<String> destinosRuta,
            LocalDateTime fechaEntrega) {}

    /** Maletas entregadas en las ultimas 4h (purga las mas antiguas en cada consulta). */
    public List<MaletaEntregadaReciente> getMaletasEntregadasRecientes() {
        final LocalDateTime corte = LocalDateTime.now(ZoneOffset.UTC).minusHours(4);
        maletasEntregadasRecientes.values().removeIf(
                r -> r.fechaEntrega() == null || r.fechaEntrega().isBefore(corte));
        return new ArrayList<>(maletasEntregadasRecientes.values());
    }
    public Collection<Ruta> getRutas() { return rutaRepositorio.obtenerActivasPlanificadas(); }
    public Ruta getRutaPorMaleta(final String idMaleta) {
        touchSession();
        final Ruta r = rutasPorMaleta.get(idMaleta);
        if (r != null) return r;
        return rutaRepositorio.obtenerPorId(idMaleta).orElse(null);
    }
    public Maleta getMaleta(final String idMaleta) {
        touchSession();
        final Maleta m = maletasPorId.get(idMaleta);
        if (m != null) return m;
        return maletaRepositorio.obtenerPorId(idMaleta).orElse(null);
    }

    public int getOcupacionVuelo(final String idVueloInstancia) {
        touchSession();
        if (rutasPorMaleta.isEmpty()) {
            for (final Ruta r : rutaRepositorio.obtenerActivasPlanificadas()) {
                if (r != null && r.getIdMaleta() != null) {
                    rutasPorMaleta.put(r.getIdMaleta(), r);
                }
            }
        }
        return (int) rutasPorMaleta.values().stream()
                .filter(r -> r != null && r.getSubrutaIds() != null)
                .filter(r -> r.getSubrutaIds().contains(idVueloInstancia))
                .count();
    }

    public int getTickActual() { touchSession(); return tickActual.get(); }
    public List<VueloInstancia> getVuelosCalientes() { touchSession(); return new ArrayList<>(vuelosInstancia.values()); }

    public List<VueloInstancia> getVuelosFrontend() {
        touchSession();
        return vuelosInstancia.values().stream()
                .filter(v -> v != null)
                .filter(v -> v.getEstado() == EstadoVuelo.EN_PROGRESO
                        || v.getEstado() == EstadoVuelo.CONFIRMADO
                        || v.getEstado() == EstadoVuelo.CANCELADO)
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

    public Aeropuerto crearAeropuerto(final AeropuertoRequest request) {
        touchSession();
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }
            final String idAeropuerto = request.getIdAeropuerto();
            if (idAeropuerto == null || idAeropuerto.isBlank()) {
                throw new IllegalArgumentException("idAeropuerto es requerido");
            }
            if (aeropuertos.containsKey(idAeropuerto)) {
                throw new IllegalArgumentException("Ya existe un aeropuerto con IATA " + idAeropuerto);
            }
            final String idCiudad = String.valueOf(siguienteIdCiudad());
            final String nombreCiudad = request.getNombreCiudad() != null
                    ? request.getNombreCiudad().trim() : "";
            final Continente continente = parseContinente(request.getContinente());
            final Ciudad ciudadObj = new Ciudad(idCiudad, nombreCiudad, continente);
            jdbcTemplate.update(
                    "INSERT OR IGNORE INTO ciudad (id_ciudad, nombre, continente) VALUES (?, ?, ?)",
                    idCiudad, nombreCiudad, continente != null ? continente.name() : null);
            final Aeropuerto aeropuerto = new Aeropuerto();
            aeropuerto.setIdAeropuerto(idAeropuerto);
            aeropuerto.setCiudad(ciudadObj);
            aeropuerto.setCapacidadAlmacen(request.getCapacidadAlmacen());
            aeropuerto.setMaletasActuales(0);
            aeropuerto.setLatitud(request.getLatitud());
            aeropuerto.setLongitud(request.getLongitud());
            aeropuerto.setHusoGMT(request.getHusoGMT());
            aeropuerto.setActivo(true);
            aeropuertoRepositorio.insertar(aeropuerto);
            aeropuertos.put(idAeropuerto, aeropuerto);
            return aeropuerto;
        }
    }

    public Aeropuerto actualizarAeropuerto(final String iata, final AeropuertoRequest request) {
        touchSession();
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }
            final Aeropuerto existente = aeropuertos.get(iata);
            if (existente == null) {
                throw new IllegalArgumentException("Aeropuerto no encontrado: " + iata);
            }
            final String idCiudad = existente.getCiudad() != null && existente.getCiudad().getIdCiudad() != null
                    ? existente.getCiudad().getIdCiudad()
                    : String.valueOf(siguienteIdCiudad());
            final String nombreCiudad = request.getNombreCiudad() != null
                    ? request.getNombreCiudad().trim() : "";
            final Continente continente = parseContinente(request.getContinente());
            final Ciudad ciudadObj = new Ciudad(idCiudad, nombreCiudad, continente);
            jdbcTemplate.update(
                    "INSERT OR IGNORE INTO ciudad (id_ciudad, nombre, continente) VALUES (?, ?, ?)",
                    idCiudad, nombreCiudad, continente != null ? continente.name() : null);
            existente.setCiudad(ciudadObj);
            existente.setCapacidadAlmacen(request.getCapacidadAlmacen());
            existente.setLatitud(request.getLatitud());
            existente.setLongitud(request.getLongitud());
            existente.setHusoGMT(request.getHusoGMT());
            aeropuertoRepositorio.actualizar(existente);
            return existente;
        }
    }

    public void eliminarAeropuerto(final String iata) {
        touchSession();
        synchronized (lock) {
            if (!activa) {
                throw new IllegalStateException("La simulacion no esta activa");
            }
            final Aeropuerto existente = aeropuertos.get(iata);
            if (existente == null) {
                throw new IllegalArgumentException("Aeropuerto no encontrado: " + iata);
            }
            aeropuertoRepositorio.eliminar(iata);
            aeropuertos.remove(iata);
        }
    }

    private int siguienteIdCiudad() {
        final Integer max = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(CAST(id_ciudad AS INTEGER)), 0) FROM ciudad",
                Integer.class);
        return (max != null ? max : 0) + 1;
    }

    private Continente parseContinente(final String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        final String normalizado = raw.trim().toUpperCase().replace(" ", "_");
        try {
            return Continente.valueOf(normalizado);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

}
