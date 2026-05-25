package pe.edu.pucp.aeroluggage.simulacion;

import lombok.Getter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private static final int DEFAULT_WINDOW_SIZE_MINUTES = 180;
    private static final long SIMULATED_DAY_MS = 24L * 60L * 60L * 1000L;

    private final String sessionId;
    private final LocalDate fechaInicio;
    private final int totalDias;
    private final long duracionDiaSimuladoMs;
    private final int windowSizeMinutes;
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
    private final AtomicReference<String> ultimaVentanaPlanificada = new AtomicReference<>("");
    private volatile List<Aeropuerto> aeropuertos = List.of();
    private volatile List<VueloProgramado> vuelosProgramados = List.of();
    private volatile List<VueloInstancia> vuelosInstancia = List.of();
    private volatile List<Pedido> pedidos = List.of();
    private volatile List<Maleta> maletas = List.of();
    private volatile List<Ruta> rutas = List.of();
    private volatile Map<String, Maleta> maletasPorId = Map.of();
    private volatile ScheduledFuture<?> tareaScheduled;

    public SimulacionSesion(
            final String sessionId,
            final LocalDate fechaInicio,
            final int totalDias,
            final long duracionDiaSimuladoMs) {
        this.sessionId = sessionId;
        this.fechaInicio = fechaInicio;
        this.totalDias = totalDias;
        this.duracionDiaSimuladoMs = Math.max(1L, duracionDiaSimuladoMs);
        this.windowSizeMinutes = DEFAULT_WINDOW_SIZE_MINUTES;
        this.fechaInicioUtc = fechaInicio.atStartOfDay();
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
        final LocalDateTime nextStart = active.getEndUtc();
        if (!nextStart.isBefore(fechaFinUtc)) {
            return null;
        }
        return buildWindowFor(nextStart, "PENDING");
    }

    public boolean haTerminado() {
        return !currentSimTimeUtc.get().isBefore(fechaFinUtc);
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
        this.maletasPorId = this.maletas.stream()
                .filter(m -> m != null && m.getIdMaleta() != null)
                .collect(Collectors.toUnmodifiableMap(Maleta::getIdMaleta, m -> m, (a, b) -> a));
    }

    private SimulacionVentana buildWindowFor(final LocalDateTime dateTime, final String status) {
        final long minutesFromStart = Duration.between(fechaInicioUtc, dateTime).toMinutes();
        final long safeMinutesFromStart = Math.max(0L, minutesFromStart);
        final long bucket = safeMinutesFromStart / windowSizeMinutes;
        final LocalDateTime windowStart = fechaInicioUtc.plusMinutes(bucket * windowSizeMinutes);
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
