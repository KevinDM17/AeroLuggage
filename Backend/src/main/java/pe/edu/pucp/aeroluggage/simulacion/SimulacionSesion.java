package pe.edu.pucp.aeroluggage.simulacion;

import lombok.Getter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

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
    private volatile List<ResumenVentanaPlanificacion> resumenesVentana = List.of();
    private volatile ScheduledFuture<?> tareaScheduled;
    private final ConcurrentHashMap<String, Maleta> maletasPorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Ruta> rutasPorMaleta = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MaletaFallos> evaluacionesMaletas = new ConcurrentHashMap<>();
    private final List<SegmentoReplanificacion> segmentosReplanificacion = new CopyOnWriteArrayList<>();
    private final AtomicBoolean replanificacionPendiente = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, List<Maleta>> maletasPorVentana = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Pedido>> pedidosPorVentana = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<VueloInstancia>> vuelosPorVentana = new ConcurrentHashMap<>();
    private final AtomicLong ultimoIndiceVuelosEnviado = new AtomicLong(0);
    private final Set<LocalDate> fechasVuelosGeneradas = ConcurrentHashMap.newKeySet();

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

    public Collection<Maleta> getMaletas() {
        return maletasPorId.values();
    }

    public Collection<Ruta> getRutas() {
        return rutasPorMaleta.values();
    }

    public synchronized void podarEntidadesAnteriores(final LocalDateTime cutoff) {
        final Set<String> aPodar = new HashSet<>();
        for (final Maleta maleta : maletasPorId.values()) {
            if (maleta.getEstado() != EstadoMaleta.ENTREGADA) {
                continue;
            }
            final Ruta ruta = rutasPorMaleta.get(maleta.getIdMaleta());
            if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final LocalDateTime ultimaLlegada = ruta.getSubrutas().getLast().getFechaLlegada();
            if (ultimaLlegada != null && ultimaLlegada.isBefore(cutoff)) {
                aPodar.add(maleta.getIdMaleta());
            }
        }

        if (aPodar.isEmpty()) {
            return;
        }

        aPodar.forEach(maletasPorId::remove);
        aPodar.forEach(rutasPorMaleta::remove);

        final Set<String> vuelosEnUso = new HashSet<>();
        for (final Ruta ruta : rutasPorMaleta.values()) {
            if (ruta.getSubrutas() == null) {
                continue;
            }
            for (final VueloInstancia vuelo : ruta.getSubrutas()) {
                if (vuelo != null && vuelo.getIdVueloInstancia() != null) {
                    vuelosEnUso.add(vuelo.getIdVueloInstancia());
                }
            }
        }

        this.vuelosInstancia = vuelosInstancia.stream()
                .filter(v -> v.getEstado() != EstadoVuelo.FINALIZADO
                        || vuelosEnUso.contains(v.getIdVueloInstancia()))
                .toList();

        this.stateVersion.incrementAndGet();
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
        final long bucket = parseBucket(ventana.getWindowId());
        final String siguienteVentana = "W" + String.format("%04d", bucket + 1L);
        return !siguienteVentana.equals(ultimaVentanaPlanificada.get());
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
        for (final Ruta ruta : nuevasRutas) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                rutasPorMaleta.put(ruta.getIdMaleta(), ruta);
            }
        }
        this.stateVersion.incrementAndGet();
    }

    public String calcularVentana(final LocalDateTime dateTime) {
        if (dateTime == null) return "";
        final long minutesFromStart = Duration.between(fechaInicioUtc, dateTime).toMinutes();
        final long bucket = Math.max(0L, minutesFromStart) / windowSpacingMinutes;
        return "W" + String.format("%04d", bucket + 1);
    }

    public static long parseBucket(final String windowId) {
        return Long.parseLong(windowId.substring(1));
    }

    public boolean hasSnapshotData() {
        return !aeropuertos.isEmpty()
                || !vuelosProgramados.isEmpty()
                || !vuelosInstancia.isEmpty()
                || !pedidos.isEmpty()
                || !maletasPorId.isEmpty()
                || !rutasPorMaleta.isEmpty();
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
        this.resumenesVentana = List.of();
        this.maletasPorId.clear();
        this.rutasPorMaleta.clear();
        this.maletasPorVentana.clear();
        this.pedidosPorVentana.clear();
        this.vuelosPorVentana.clear();
        this.ultimoIndiceVuelosEnviado.set(0);
        this.fechasVuelosGeneradas.clear();
        if (maletas != null) {
            for (final Maleta m : maletas) {
                if (m != null && m.getIdMaleta() != null) {
                    this.maletasPorId.put(m.getIdMaleta(), m);
                    final String v = calcularVentana(m.getFechaRegistro());
                    this.maletasPorVentana.computeIfAbsent(v, k -> new ArrayList<>()).add(m);
                }
            }
        }
        if (pedidos != null) {
            for (final Pedido p : pedidos) {
                if (p != null && p.getIdPedido() != null) {
                    final String v = calcularVentana(p.getFechaRegistro());
                    this.pedidosPorVentana.computeIfAbsent(v, k -> new ArrayList<>()).add(p);
                }
            }
        }
        if (rutas != null) {
            for (final Ruta r : rutas) {
                if (r != null && r.getIdMaleta() != null) {
                    this.rutasPorMaleta.put(r.getIdMaleta(), r);
                }
            }
        }
        if (vuelosInstancia != null) {
            for (final VueloInstancia v : vuelosInstancia) {
                if (v != null && v.getIdVueloInstancia() != null && v.getFechaSalida() != null) {
                    final String w = calcularVentana(v.getFechaSalida());
                    this.vuelosPorVentana.computeIfAbsent(w, k -> new ArrayList<>()).add(v);
                }
            }
        }
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

    public synchronized void asegurarVuelosParaBanda(final long desdeBucket, final long hastaBucket) {
        final Set<LocalDate> fechasPendientes = new HashSet<>();
        for (long b = desdeBucket; b <= hastaBucket; b++) {
            if (vuelosPorVentana.containsKey("W" + String.format("%04d", b))) {
                continue;
            }
            final LocalDateTime ventanaStart = fechaInicioUtc.plusMinutes((b - 1L) * windowSpacingMinutes);
            final LocalDate fecha = ventanaStart.toLocalDate();
            if (!fecha.isAfter(fechaFinVuelos()) && fechasVuelosGeneradas.add(fecha)) {
                fechasPendientes.add(fecha);
            }
        }
        if (fechasPendientes.isEmpty()) {
            return;
        }

        final List<VueloInstancia> nuevos = new ArrayList<>();
        for (final LocalDate fechaOperacion : fechasPendientes) {
            final long secuenciaBase = fechaOperacion.toEpochDay() * (long) vuelosProgramados.size();
            int secuencia = (int) Math.min(secuenciaBase + 1L, Integer.MAX_VALUE);
            for (final VueloProgramado vp : vuelosProgramados) {
                if (vp == null) continue;
                final int gmtOrigen = vp.getAeropuertoOrigen() != null
                        ? vp.getAeropuertoOrigen().getHusoGMT() : 0;
                final int gmtDestino = vp.getAeropuertoDestino() != null
                        ? vp.getAeropuertoDestino().getHusoGMT() : 0;
                final LocalDateTime salidaLocal = LocalDateTime.of(fechaOperacion, vp.getHoraSalida());
                LocalDateTime llegadaLocal = LocalDateTime.of(fechaOperacion, vp.getHoraLlegada());
                if (!llegadaLocal.isAfter(salidaLocal)) {
                    llegadaLocal = llegadaLocal.plusDays(1);
                }
                final LocalDateTime salidaUtc = salidaLocal.minusHours(gmtOrigen);
                if (salidaUtc.isBefore(fechaInicioUtc)) continue;
                final LocalDateTime llegadaUtc = llegadaLocal.minusHours(gmtDestino);
                final String id = String.format("VI%08d", secuencia++);
                final VueloInstancia vi = new VueloInstancia(
                        id, vp.getCodigo(), salidaUtc, llegadaUtc,
                        vp.getCapacidadMaxima(), vp.getCapacidadMaxima(),
                        vp.getAeropuertoOrigen(), vp.getAeropuertoDestino(),
                        EstadoVuelo.PROGRAMADO
                );
                final String ventana = calcularVentana(salidaUtc);
                vuelosPorVentana.computeIfAbsent(ventana, k -> new ArrayList<>()).add(vi);
                nuevos.add(vi);
            }
        }
        if (!nuevos.isEmpty()) {
            final List<VueloInstancia> combinados = new ArrayList<>(this.vuelosInstancia);
            combinados.addAll(nuevos);
            this.vuelosInstancia = List.copyOf(combinados);
        }
    }

    private LocalDate fechaFinVuelos() {
        return fechaInicioUtc.toLocalDate()
                .plusDays(Math.max(0L, totalDias));
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
