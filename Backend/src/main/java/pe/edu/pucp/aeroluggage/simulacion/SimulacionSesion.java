package pe.edu.pucp.aeroluggage.simulacion;

import lombok.Getter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

@Getter
public class SimulacionSesion {

    private static final long SIMULATED_DAY_MS = 24L * 60L * 60L * 1000L;

    private final String sessionId;
    private final LocalDate fechaInicio;
    private final int totalDias;
    private final long duracionDiaSimuladoMs;
    private final int windowSizeMinutes;
    private final int windowSpacingMinutes;
    private final LocalDateTime fechaInicioUtc;
    private final LocalDateTime fechaFinUtc;
    private final long startedAtRealMs;
    private final AtomicInteger tickActual = new AtomicInteger(0);
    private final AtomicReference<LocalDateTime> currentSimTimeUtc;
    private final AtomicReference<SimulacionVentana> currentWindow;
    private final AtomicLong planningGeneration = new AtomicLong(1);
    private final AtomicLong stateVersion = new AtomicLong(1);
    private final AtomicBoolean activa = new AtomicBoolean(true);
    private final AtomicBoolean planificacionEnCurso = new AtomicBoolean(false);
    private final AtomicBoolean csvEscrito = new AtomicBoolean(false);
    private final AtomicReference<String> ultimaVentanaPlanificada = new AtomicReference<>("");
    private volatile List<Aeropuerto> aeropuertos = List.of();
    private volatile List<VueloProgramado> vuelosProgramados = List.of();
    private volatile List<VueloInstancia> vuelosInstancia = List.of();
    private volatile List<Pedido> pedidos = List.of();
    private volatile List<Maleta> maletas = List.of();
    private volatile List<Ruta> rutas = List.of();
    private volatile List<ResumenVentanaPlanificacion> resumenesVentana = List.of();
    private volatile Map<String, Maleta> maletasPorId = Map.of();
    private volatile ScheduledFuture<?> tareaScheduled;
    private final ConcurrentHashMap<String, MaletaFallos> evaluacionesMaletas = new ConcurrentHashMap<>();
    private final List<SegmentoReplanificacion> segmentosReplanificacion = new CopyOnWriteArrayList<>();
    private final AtomicBoolean replanificacionPendiente = new AtomicBoolean(false);

    public SimulacionSesion(
            final String sessionId,
            final LocalDate fechaInicio,
            final LocalTime horaInicio,
            final int totalDias,
            final long duracionDiaSimuladoMs,
            final int windowSizeMinutes,
            final int windowSpacingMinutes) {
        this.sessionId = sessionId;
        this.fechaInicio = fechaInicio;
        this.totalDias = totalDias;
        this.duracionDiaSimuladoMs = Math.max(1L, duracionDiaSimuladoMs);
        this.windowSizeMinutes = Math.max(1, windowSizeMinutes);
        this.windowSpacingMinutes = Math.max(1, windowSpacingMinutes);
        this.fechaInicioUtc = LocalDateTime.of(fechaInicio, horaInicio);
        this.fechaFinUtc = fechaInicioUtc.plusDays(Math.max(0L, totalDias));
        this.startedAtRealMs = System.currentTimeMillis();
        this.currentSimTimeUtc = new AtomicReference<>(fechaInicioUtc);
        this.currentWindow = new AtomicReference<>(buildWindowFor(fechaInicioUtc, "ACTIVE"));
    }

    public void setTareaScheduled(final ScheduledFuture<?> tarea) {
        this.tareaScheduled = tarea;
    }

    public void updateCurrentSimTimeUtc() {
        final long elapsedRealMs = Math.max(0L, System.currentTimeMillis() - startedAtRealMs);
        final long simulatedElapsedMs = Math.round((double) elapsedRealMs * SIMULATED_DAY_MS / duracionDiaSimuladoMs);
        final LocalDateTime nextTime = fechaInicioUtc.plus(Duration.ofMillis(simulatedElapsedMs));
        final LocalDateTime boundedTime = nextTime.isAfter(fechaFinUtc) ? fechaFinUtc : nextTime;
        currentSimTimeUtc.set(boundedTime);
    }

    public SimulacionVentana refreshCurrentWindow() {
        final SimulacionVentana previous = currentWindow.get();
        final SimulacionVentana next = buildWindowFor(currentSimTimeUtc.get(), "ACTIVE");
        currentWindow.set(next);
        if (previous == null || !previous.getWindowId().equals(next.getWindowId())) {
            stateVersion.incrementAndGet();
        }
        return next;
    }

    public SimulacionVentana buildNextWindow() {
        final SimulacionVentana active = currentWindow.get();
        if (active == null) {
            return buildWindowFor(currentSimTimeUtc.get(), "PENDING");
        }
        final LocalDateTime nextStart = active.getStartUtc().plusMinutes(windowSpacingMinutes);
        if (!nextStart.isBefore(fechaFinUtc)) {
            return null;
        }
        return buildWindowFor(nextStart, "PENDING");
    }

    public boolean haTerminado() {
        return !currentSimTimeUtc.get().isBefore(fechaFinUtc);
    }

    public boolean marcarCsvEscrito() {
        return csvEscrito.compareAndSet(false, true);
    }

    public boolean necesitaPlanificacion() {
        if (planificacionEnCurso.get()) {
            return false;
        }
        final SimulacionVentana ventana = currentWindow.get();
        if (ventana == null) {
            return false;
        }
        return !ventana.getWindowId().equals(ultimaVentanaPlanificada.get());
    }

    public void marcarVentanaPlanificada(final String windowId) {
        ultimaVentanaPlanificada.set(windowId);
    }

    public record SegmentoReplanificacion(
            String idMaleta,
            String origenActual,
            String destinoOriginal,
            LocalDateTime tDisponible,
            LocalDateTime tLimite
    ) {}

    public void agregarSegmentoReplanificacion(final SegmentoReplanificacion segmento) {
        segmentosReplanificacion.add(segmento);
    }

    public List<SegmentoReplanificacion> obtenerSegmentosReplanificacion() {
        final List<SegmentoReplanificacion> copia = new ArrayList<>(segmentosReplanificacion);
        segmentosReplanificacion.clear();
        return copia;
    }

    public boolean marcarReplanificacionPendiente() {
        return replanificacionPendiente.compareAndSet(false, true);
    }

    public boolean isReplanificacionPendiente() {
        return replanificacionPendiente.get();
    }

    public void limpiarReplanificacionPendiente() {
        replanificacionPendiente.set(false);
    }

    public synchronized void agregarRutas(final List<Ruta> nuevasRutas) {
        if (nuevasRutas == null || nuevasRutas.isEmpty()) {
            return;
        }
        final List<Ruta> combinadas = new ArrayList<>(this.rutas);
        combinadas.addAll(nuevasRutas);
        this.rutas = List.copyOf(combinadas);
        this.stateVersion.incrementAndGet();
    }

    public boolean hasSnapshotData() {
        return !aeropuertos.isEmpty()
                || !vuelosProgramados.isEmpty()
                || !vuelosInstancia.isEmpty()
                || !pedidos.isEmpty()
                || !maletas.isEmpty()
                || !rutas.isEmpty();
    }

    public void setSnapshotData(final List<Aeropuerto> aeropuertos,
                                final List<VueloProgramado> vuelosProgramados,
                                final List<VueloInstancia> vuelosInstancia,
                                final List<Pedido> pedidos,
                                final List<Maleta> maletas,
                                final List<Ruta> rutas) {
        this.aeropuertos = aeropuertos == null ? List.of() : List.copyOf(new ArrayList<>(aeropuertos));
        this.vuelosProgramados = vuelosProgramados == null
                ? List.of()
                : List.copyOf(new ArrayList<>(vuelosProgramados));
        this.vuelosInstancia = vuelosInstancia == null ? List.of() : List.copyOf(new ArrayList<>(vuelosInstancia));
        this.pedidos = pedidos == null ? List.of() : List.copyOf(new ArrayList<>(pedidos));
        this.maletas = maletas == null ? List.of() : List.copyOf(new ArrayList<>(maletas));
        this.rutas = rutas == null ? List.of() : List.copyOf(new ArrayList<>(rutas));
        this.resumenesVentana = List.of();
        this.maletasPorId = this.maletas.stream()
                .filter(m -> m != null && m.getIdMaleta() != null)
                .collect(Collectors.toUnmodifiableMap(Maleta::getIdMaleta, m -> m, (a, b) -> a));
    }

    public synchronized void registrarResumenVentana(final ResumenVentanaPlanificacion resumen) {
        if (resumen == null || resumen.windowId() == null) {
            return;
        }
        final List<ResumenVentanaPlanificacion> actualizados = new ArrayList<>(this.resumenesVentana);
        actualizados.removeIf(item -> resumen.windowId().equals(item.windowId()));
        actualizados.add(resumen);
        actualizados.sort((primero, segundo) -> primero.startUtc().compareTo(segundo.startUtc()));
        this.resumenesVentana = List.copyOf(actualizados);
    }

    public record ResumenVentanaPlanificacion(String windowId,
                                              LocalDateTime startUtc,
                                              LocalDateTime endUtc,
                                              int maletasEvaluadas,
                                              int maletasEnrutadas,
                                              int maletasSinRuta,
                                              long tiempoPlanificacionMs) {
    }

    private static class MaletaFallos {
        private final String codigoOrigen;
        private final String fechaRegistroUtc;
        private final List<String> ventanas = new CopyOnWriteArrayList<>();

        MaletaFallos(final String codigoOrigen, final String fechaRegistroUtc) {
            this.codigoOrigen = codigoOrigen;
            this.fechaRegistroUtc = fechaRegistroUtc;
        }

        void agregarVentana(final String windowId, final String motivo) {
            ventanas.add(windowId + "," + motivo);
        }

        void toCsvLines(final String idMaleta, final List<String> lineas) {
            lineas.add(idMaleta + "," + codigoOrigen + "," + fechaRegistroUtc);
            for (final String ventana : ventanas) {
                lineas.add("\t" + ventana);
            }
        }
    }

    public void registrarFalloEnVentana(final String idMaleta, final String codigoOrigen,
                                        final String fechaRegistroUtc, final String windowId,
                                        final String motivo) {
        evaluacionesMaletas.computeIfAbsent(idMaleta,
                k -> new MaletaFallos(codigoOrigen, fechaRegistroUtc))
                .agregarVentana(windowId, motivo);
    }

    public List<String> getNoEnrutadasParaCsv() {
        final List<String> lineas = new ArrayList<>();
        lineas.add("id_maleta,aeropuerto_origen,fecha_registro_utc\tventana,motivo");
        for (final Map.Entry<String, MaletaFallos> entry : evaluacionesMaletas.entrySet()) {
            entry.getValue().toCsvLines(entry.getKey(), lineas);
        }
        return lineas;
    }

    private SimulacionVentana buildWindowFor(final LocalDateTime dateTime, final String status) {
        final long minutesFromStart = Duration.between(fechaInicioUtc, dateTime).toMinutes();
        final long safeMinutesFromStart = Math.max(0L, minutesFromStart);
        final long bucket = safeMinutesFromStart / windowSpacingMinutes;
        final LocalDateTime windowStart = fechaInicioUtc.plusMinutes(bucket * windowSpacingMinutes);
        LocalDateTime windowEnd = windowStart.plusMinutes(windowSizeMinutes);
        if (windowEnd.isAfter(fechaFinUtc)) {
            windowEnd = fechaFinUtc;
        }
        final String windowId = "W" + String.format("%04d", bucket + 1);
        return new SimulacionVentana(
                windowId,
                windowStart,
                windowEnd,
                status,
                planningGeneration.get()
        );
    }
}
