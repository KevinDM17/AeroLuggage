package pe.edu.pucp.aeroluggage.simulacion;

import lombok.Getter;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
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
    private final AtomicBoolean planValido = new AtomicBoolean(false);
    private final AtomicBoolean planificando = new AtomicBoolean(false);
    private final AtomicBoolean replanPendiente = new AtomicBoolean(false);
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

    private final ConcurrentHashMap<String, List<Maleta>> maletasPorVentana = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Pedido>> pedidosPorVentana = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<VueloInstancia>> vuelosPorVentana = new ConcurrentHashMap<>();
    private final AtomicLong ultimoIndiceVuelosEnviado = new AtomicLong(0);
    private final Set<LocalDate> fechasVuelosGeneradas = ConcurrentHashMap.newKeySet();
    private volatile int umbralConfirmacionMinutos;

    // ====== MAPA DE EVENTOS INCREMENTAL ======

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

    private NavigableMap<LocalDateTime, List<EventoSim>> eventosSimulacion;
    private volatile LocalDateTime ultimoTiempoSim;

    public void setUltimoTiempoSim(final LocalDateTime t) {
        this.ultimoTiempoSim = t;
    }

    public LocalDateTime getUltimoTiempoSim() {
        return ultimoTiempoSim;
    }

    public void setUmbralConfirmacionMinutos(final int umbral) {
        this.umbralConfirmacionMinutos = umbral;
    }

    public void construirIndiceEventos(final int umbralConfirmacionMinutos) {
        eventosSimulacion = new TreeMap<>();
        final Map<String, Aeropuerto> aeropuertosPorId = new HashMap<>();
        for (final Aeropuerto a : aeropuertos) {
            if (a != null && a.getIdAeropuerto() != null) {
                aeropuertosPorId.put(a.getIdAeropuerto(), a);
            }
        }

        for (final VueloInstancia v : vuelosInstancia) {
            if (v == null) continue;
            if (v.getFechaSalida() != null) {
                final LocalDateTime tConf = v.getFechaSalida().minusMinutes(umbralConfirmacionMinutos);
                agregarEvento(tConf, TipoEventoSim.VUELO_CONFIRMA, v.getIdVueloInstancia(), null, 0);
                agregarEvento(v.getFechaSalida(), TipoEventoSim.VUELO_INICIA, v.getIdVueloInstancia(), null, 0);
            }
            if (v.getFechaLlegada() != null) {
                agregarEvento(v.getFechaLlegada(), TipoEventoSim.VUELO_FINALIZA, v.getIdVueloInstancia(), null, 0);
            }
        }

        for (final Ruta r : rutasPorMaleta.values()) {
            if (r == null) continue;
            final Maleta m = maletasPorId.get(r.getIdMaleta());
            if (m == null || m.getFechaRegistro() == null) continue;

            final List<VueloInstancia> subs = r.getSubrutas();
            if (subs == null || subs.isEmpty()) continue;

            agregarEvento(m.getFechaRegistro(), TipoEventoSim.MALETA_APARECE,
                    r.getIdMaleta(),
                    m.getPedido() != null && m.getPedido().getAeropuertoOrigen() != null
                            ? m.getPedido().getAeropuertoOrigen().getIdAeropuerto() : null,
                    -1);

            for (int i = 0; i < subs.size(); i++) {
                final VueloInstancia v = subs.get(i);
                if (v == null) continue;
                final String idAeroOrig = v.getAeropuertoOrigen() != null
                        ? v.getAeropuertoOrigen().getIdAeropuerto() : null;
                final String idAeroDest = v.getAeropuertoDestino() != null
                        ? v.getAeropuertoDestino().getIdAeropuerto() : null;
                final boolean ultimo = (i == subs.size() - 1);

                if (v.getFechaSalida() != null && idAeroOrig != null) {
                    agregarEvento(v.getFechaSalida(), TipoEventoSim.MALETA_SALE_AEROP,
                            r.getIdMaleta(), idAeroOrig, -1);
                }
                if (v.getFechaLlegada() != null && idAeroDest != null) {
                    agregarEvento(v.getFechaLlegada(), TipoEventoSim.MALETA_LLEGA_AEROP,
                            r.getIdMaleta(), idAeroDest, 1);
                }
                if (ultimo && v.getFechaLlegada() != null && idAeroDest != null) {
                    agregarEvento(v.getFechaLlegada().plusMinutes(10), TipoEventoSim.MALETA_ENTREGADA,
                            r.getIdMaleta(), idAeroDest, -1);
                }
            }

            if (!subs.isEmpty()) {
                final VueloInstancia primero = subs.getFirst();
                final VueloInstancia ultimoSub = subs.getLast();
                if (primero.getFechaSalida() != null) {
                    agregarEvento(primero.getFechaSalida(), TipoEventoSim.RUTA_ACTIVA,
                            r.getIdRuta(), null, 0);
                }
                if (ultimoSub.getFechaLlegada() != null) {
                    agregarEvento(ultimoSub.getFechaLlegada(), TipoEventoSim.RUTA_COMPLETA,
                            r.getIdRuta(), null, 0);
                }
            }
        }

        this.ultimoTiempoSim = currentSimTimeUtc.get();
    }

    private void agregarEvento(final LocalDateTime tiempo, final TipoEventoSim tipo,
                               final String idEntidad, final String idAeropuerto, final int delta) {
        if (tiempo == null) return;
        if (!tiempo.isAfter(currentSimTimeUtc.get())) {
            aplicarEventoAhora(tipo, idEntidad, idAeropuerto, delta);
            return;
        }
        eventosSimulacion
                .computeIfAbsent(tiempo, k -> new ArrayList<>())
                .add(new EventoSim(tipo, idEntidad, idAeropuerto, delta));
    }

    private void aplicarEventoAhora(final TipoEventoSim tipo,
                                     final String idEntidad, final String idAeropuerto, final int delta) {
        switch (tipo) {
            case MALETA_APARECE -> {
                final Maleta m = maletasPorId.get(idEntidad);
                if (m == null) return;
                m.setEstado(EstadoMaleta.EN_ALMACEN);
                m.setAeropuertoActual(idAeropuerto);
                final Ruta r = rutasPorMaleta.get(m.getIdMaleta());
                if (r != null && !r.getSubrutas().isEmpty() && r.getSubrutas().getFirst() != null) {
                    r.getSubrutas().getFirst().setCapacidadDisponible(
                            Math.max(0, r.getSubrutas().getFirst().getCapacidadDisponible() - 1));
                }
                if (idAeropuerto != null) {
                    for (final Aeropuerto a : aeropuertos) {
                        if (a != null && a.getIdAeropuerto() != null
                                && a.getIdAeropuerto().equals(idAeropuerto)) {
                            a.setMaletasActuales(a.getMaletasActuales() + 1);
                            break;
                        }
                    }
                }
            }
            case VUELO_CONFIRMA -> {
                for (final VueloInstancia v : vuelosInstancia) {
                    if (v != null && v.getIdVueloInstancia() != null
                            && v.getIdVueloInstancia().equals(idEntidad)
                            && v.getEstado() == EstadoVuelo.PROGRAMADO) {
                        v.setEstado(EstadoVuelo.CONFIRMADO);
                        break;
                    }
                }
            }
            case VUELO_INICIA -> {
                for (final VueloInstancia v : vuelosInstancia) {
                    if (v != null && v.getIdVueloInstancia() != null
                            && v.getIdVueloInstancia().equals(idEntidad)
                            && v.getEstado() != EstadoVuelo.CANCELADO) {
                        v.setEstado(EstadoVuelo.EN_PROGRESO);
                        break;
                    }
                }
            }
            case VUELO_FINALIZA -> {
                for (final VueloInstancia v : vuelosInstancia) {
                    if (v != null && v.getIdVueloInstancia() != null
                            && v.getIdVueloInstancia().equals(idEntidad)) {
                        v.setEstado(EstadoVuelo.FINALIZADO);
                        break;
                    }
                }
            }
            case MALETA_SALE_AEROP -> {
                if (idAeropuerto != null) {
                    for (final Aeropuerto a : aeropuertos) {
                        if (a != null && a.getIdAeropuerto() != null
                                && a.getIdAeropuerto().equals(idAeropuerto)) {
                            a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
                            break;
                        }
                    }
                }
            }
            case MALETA_LLEGA_AEROP -> {
                if (idAeropuerto != null) {
                    for (final Aeropuerto a : aeropuertos) {
                        if (a != null && a.getIdAeropuerto() != null
                                && a.getIdAeropuerto().equals(idAeropuerto)) {
                            a.setMaletasActuales(a.getMaletasActuales() + 1);
                            break;
                        }
                    }
                }
            }
            case MALETA_ENTREGADA -> {
                final Maleta m = maletasPorId.get(idEntidad);
                if (m != null) {
                    m.setEstado(EstadoMaleta.ENTREGADA);
                    m.setFechaLlegada(currentSimTimeUtc.get());
                    if (m.getPedido() != null && m.getPedido().getAeropuertoDestino() != null) {
                        m.setAeropuertoActual(m.getPedido().getAeropuertoDestino().getIdAeropuerto());
                    }
                }
                if (idAeropuerto != null) {
                    for (final Aeropuerto a : aeropuertos) {
                        if (a != null && a.getIdAeropuerto() != null
                                && a.getIdAeropuerto().equals(idAeropuerto)) {
                            a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
                            break;
                        }
                    }
                }
            }
            case RUTA_ACTIVA -> {
                for (final Ruta r : rutasPorMaleta.values()) {
                    if (r != null && r.getIdRuta() != null && r.getIdRuta().equals(idEntidad)
                            && r.getEstado() == EstadoRuta.PLANIFICADA) {
                        r.setEstado(EstadoRuta.ACTIVA);
                        break;
                    }
                }
            }
            case RUTA_COMPLETA -> {
                for (final Ruta r : rutasPorMaleta.values()) {
                    if (r != null && r.getIdRuta() != null && r.getIdRuta().equals(idEntidad)
                            && r.getEstado() == EstadoRuta.ACTIVA) {
                        r.setEstado(EstadoRuta.COMPLETADA);
                        break;
                    }
                }
            }
        }
    }

    public void procesarEventos(final LocalDateTime simTimeAnterior, final LocalDateTime simTimeActual,
                                final int umbralConfirmacionMinutos) {
        if (eventosSimulacion == null) {
            construirIndiceEventos(umbralConfirmacionMinutos);
        }
        if (simTimeAnterior == null) {
            this.ultimoTiempoSim = simTimeActual;
            return;
        }
        final Collection<List<EventoSim>> eventos = eventosSimulacion
                .subMap(simTimeAnterior, false, simTimeActual, true)
                .values();
        for (final List<EventoSim> lote : eventos) {
            for (final EventoSim e : lote) {
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
        }
        this.ultimoTiempoSim = simTimeActual;
    }

    private void aplicarMaletaAparece(final EventoSim e) {
        final Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) return;
        m.setEstado(EstadoMaleta.EN_ALMACEN);
        m.setAeropuertoActual(e.idAeropuerto());
        final Ruta r = rutasPorMaleta.get(m.getIdMaleta());
        if (r != null && !r.getSubrutas().isEmpty()) {
            final VueloInstancia v1 = r.getSubrutas().getFirst();
            v1.setCapacidadDisponible(Math.max(0, v1.getCapacidadDisponible() - 1));
        }
        if (e.idAeropuerto() != null) {
            for (final Aeropuerto a : aeropuertos) {
                if (a != null && a.getIdAeropuerto() != null
                        && a.getIdAeropuerto().equals(e.idAeropuerto())) {
                    a.setMaletasActuales(a.getMaletasActuales() + 1);
                    break;
                }
            }
        }
    }

    private void aplicarMaletaSale(final EventoSim e) {
        final Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) return;
        m.setEstado(EstadoMaleta.EN_TRANSITO);
        m.setAeropuertoActual(null);
        if (e.idAeropuerto() != null) {
            for (final Aeropuerto a : aeropuertos) {
                if (a != null && a.getIdAeropuerto() != null
                        && a.getIdAeropuerto().equals(e.idAeropuerto())) {
                    a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
                    break;
                }
            }
        }
    }

    private void aplicarMaletaLlega(final EventoSim e) {
        final Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) return;
        m.setEstado(EstadoMaleta.EN_ALMACEN);
        m.setAeropuertoActual(e.idAeropuerto());
        if (e.idAeropuerto() != null) {
            for (final Aeropuerto a : aeropuertos) {
                if (a != null && a.getIdAeropuerto() != null
                        && a.getIdAeropuerto().equals(e.idAeropuerto())) {
                    a.setMaletasActuales(a.getMaletasActuales() + 1);
                    break;
                }
            }
        }
    }

    private void aplicarMaletaEntregada(final EventoSim e) {
        final Maleta m = maletasPorId.get(e.idEntidad());
        if (m == null) return;
        m.setEstado(EstadoMaleta.ENTREGADA);
        m.setFechaLlegada(ultimoTiempoSim);
        if (m.getPedido() != null && m.getPedido().getAeropuertoDestino() != null) {
            m.setAeropuertoActual(m.getPedido().getAeropuertoDestino().getIdAeropuerto());
        }
        if (e.idAeropuerto() != null) {
            for (final Aeropuerto a : aeropuertos) {
                if (a != null && a.getIdAeropuerto() != null
                        && a.getIdAeropuerto().equals(e.idAeropuerto())) {
                    a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
                    break;
                }
            }
        }
    }

    private void aplicarVueloConfirma(final EventoSim e) {
        for (final VueloInstancia v : vuelosInstancia) {
            if (v != null && v.getIdVueloInstancia() != null
                    && v.getIdVueloInstancia().equals(e.idEntidad())
                    && v.getEstado() == EstadoVuelo.PROGRAMADO) {
                v.setEstado(EstadoVuelo.CONFIRMADO);
                break;
            }
        }
    }

    private void aplicarVueloInicia(final EventoSim e) {
        for (final VueloInstancia v : vuelosInstancia) {
            if (v != null && v.getIdVueloInstancia() != null
                    && v.getIdVueloInstancia().equals(e.idEntidad())
                    && v.getEstado() != EstadoVuelo.CANCELADO) {
                v.setEstado(EstadoVuelo.EN_PROGRESO);
                break;
            }
        }
    }

    private void aplicarVueloFinaliza(final EventoSim e) {
        for (final VueloInstancia v : vuelosInstancia) {
            if (v != null && v.getIdVueloInstancia() != null
                    && v.getIdVueloInstancia().equals(e.idEntidad())) {
                v.setEstado(EstadoVuelo.FINALIZADO);
                break;
            }
        }
    }

    private void aplicarRutaActiva(final EventoSim e) {
        final Ruta r = rutasPorMaleta.values().stream()
                .filter(rt -> rt != null && e.idEntidad().equals(rt.getIdRuta()))
                .findFirst().orElse(null);
        if (r != null && r.getEstado() == EstadoRuta.PLANIFICADA) {
            r.setEstado(EstadoRuta.ACTIVA);
        }
    }

    private void aplicarRutaCompleta(final EventoSim e) {
        final Ruta r = rutasPorMaleta.values().stream()
                .filter(rt -> rt != null && e.idEntidad().equals(rt.getIdRuta()))
                .findFirst().orElse(null);
        if (r != null && r.getEstado() == EstadoRuta.ACTIVA) {
            r.setEstado(EstadoRuta.COMPLETADA);
        }
    }

    public void onRutaAgregada(final Ruta ruta, final int umbralConfirmacionMinutos) {
        if (ruta == null || ruta.getIdMaleta() == null) return;
        final Maleta m = maletasPorId.get(ruta.getIdMaleta());
        if (m == null || m.getFechaRegistro() == null) return;
        final List<VueloInstancia> subs = ruta.getSubrutas();
        if (subs == null || subs.isEmpty()) return;

        agregarEvento(m.getFechaRegistro(), TipoEventoSim.MALETA_APARECE,
                ruta.getIdMaleta(),
                m.getPedido() != null && m.getPedido().getAeropuertoOrigen() != null
                        ? m.getPedido().getAeropuertoOrigen().getIdAeropuerto() : null,
                -1);

        for (int i = 0; i < subs.size(); i++) {
            final VueloInstancia v = subs.get(i);
            if (v == null) continue;
            final String idAeroOrig = v.getAeropuertoOrigen() != null
                    ? v.getAeropuertoOrigen().getIdAeropuerto() : null;
            final String idAeroDest = v.getAeropuertoDestino() != null
                    ? v.getAeropuertoDestino().getIdAeropuerto() : null;
            final boolean ultimo = (i == subs.size() - 1);

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

        if (!subs.isEmpty()) {
            final VueloInstancia primero = subs.getFirst();
            final VueloInstancia ultimoSub = subs.getLast();
            if (primero.getFechaSalida() != null) {
                agregarEvento(primero.getFechaSalida(), TipoEventoSim.RUTA_ACTIVA,
                        ruta.getIdRuta(), null, 0);
            }
            if (ultimoSub.getFechaLlegada() != null) {
                agregarEvento(ultimoSub.getFechaLlegada(), TipoEventoSim.RUTA_COMPLETA,
                        ruta.getIdRuta(), null, 0);
            }
        }

        if (m.getFechaRegistro() != null && !m.getFechaRegistro().isAfter(currentSimTimeUtc.get())) {
            if (!subs.isEmpty() && subs.getFirst() != null) {
                subs.getFirst().setCapacidadDisponible(
                        Math.max(0, subs.getFirst().getCapacidadDisponible() - 1));
            }
            if (m.getPedido() != null && m.getPedido().getAeropuertoOrigen() != null) {
                final String idAero = m.getPedido().getAeropuertoOrigen().getIdAeropuerto();
                for (final Aeropuerto a : aeropuertos) {
                    if (a != null && a.getIdAeropuerto() != null
                            && a.getIdAeropuerto().equals(idAero)) {
                        a.setMaletasActuales(a.getMaletasActuales() + 1);
                        break;
                    }
                }
            }
        }
    }

    public void onRutaCancelada(final Ruta rutaAntigua) {
        if (rutaAntigua == null) return;
        final Maleta m = maletasPorId.get(rutaAntigua.getIdMaleta());
        if (m == null) return;
        if (m.getFechaRegistro() != null && !m.getFechaRegistro().isAfter(currentSimTimeUtc.get())) {
            for (final VueloInstancia v : rutaAntigua.getSubrutas()) {
                if (v != null) {
                    v.setCapacidadDisponible(v.getCapacidadDisponible() + 1);
                }
            }
        }
        rutaAntigua.setEstado(EstadoRuta.REPLANIFICADA);
    }

    public void podarEventosPasados(final LocalDateTime cutoff) {
        if (eventosSimulacion != null) {
            eventosSimulacion.headMap(cutoff, false).clear();
        }
    }

    public void proyectarEstadoEnCopias(
            final Map<String, VueloInstancia> vuelosIndex,
            final Map<String, Ruta> rutasIndex,
            final Map<String, Aeropuerto> aeropuertosIndex,
            final LocalDateTime desde,
            final LocalDateTime hasta,
            final int umbralConfirmacionMinutos) {
        if (eventosSimulacion == null || desde == null || hasta == null || !desde.isBefore(hasta)) {
            return;
        }
        final Map<String, String> rutaPorMaleta = new HashMap<>();
        for (final Ruta r : this.rutasPorMaleta.values()) {
            if (r != null && r.getIdMaleta() != null) {
                rutaPorMaleta.put(r.getIdMaleta(), r.getIdRuta());
            }
        }
        final Collection<List<EventoSim>> eventos = eventosSimulacion
                .subMap(desde, false, hasta, true)
                .values();
        for (final List<EventoSim> lote : eventos) {
            for (final EventoSim e : lote) {
                switch (e.tipo()) {
                    case MALETA_APARECE -> {
                        final String idRuta = rutaPorMaleta.get(e.idEntidad());
                        if (idRuta != null) {
                            final Ruta r = rutasIndex.get(idRuta);
                            if (r != null && !r.getSubrutas().isEmpty() && r.getSubrutas().getFirst() != null) {
                                final VueloInstancia v1 = r.getSubrutas().getFirst();
                                v1.setCapacidadDisponible(Math.max(0, v1.getCapacidadDisponible() - 1));
                            }
                        }
                        if (e.idAeropuerto() != null) {
                            final Aeropuerto a = aeropuertosIndex.get(e.idAeropuerto());
                            if (a != null) a.setMaletasActuales(a.getMaletasActuales() + 1);
                        }
                    }
                    case MALETA_SALE_AEROP -> {
                        if (e.idAeropuerto() != null) {
                            final Aeropuerto a = aeropuertosIndex.get(e.idAeropuerto());
                            if (a != null) a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
                        }
                    }
                    case MALETA_LLEGA_AEROP -> {
                        if (e.idAeropuerto() != null) {
                            final Aeropuerto a = aeropuertosIndex.get(e.idAeropuerto());
                            if (a != null) a.setMaletasActuales(a.getMaletasActuales() + 1);
                        }
                    }
                    case MALETA_ENTREGADA -> {
                        if (e.idAeropuerto() != null) {
                            final Aeropuerto a = aeropuertosIndex.get(e.idAeropuerto());
                            if (a != null) a.setMaletasActuales(Math.max(0, a.getMaletasActuales() - 1));
                        }
                    }
                    case VUELO_CONFIRMA -> {
                        final VueloInstancia v = vuelosIndex.get(e.idEntidad());
                        if (v != null && v.getEstado() == EstadoVuelo.PROGRAMADO) {
                            v.setEstado(EstadoVuelo.CONFIRMADO);
                        }
                    }
                    case VUELO_INICIA -> {
                        final VueloInstancia v = vuelosIndex.get(e.idEntidad());
                        if (v != null && v.getEstado() != EstadoVuelo.CANCELADO) {
                            v.setEstado(EstadoVuelo.EN_PROGRESO);
                        }
                    }
                    case VUELO_FINALIZA -> {
                        final VueloInstancia v = vuelosIndex.get(e.idEntidad());
                        if (v != null) v.setEstado(EstadoVuelo.FINALIZADO);
                    }
                    case RUTA_ACTIVA -> {
                        final Ruta r = rutasIndex.get(e.idEntidad());
                        if (r != null && r.getEstado() == EstadoRuta.PLANIFICADA) {
                            r.setEstado(EstadoRuta.ACTIVA);
                        }
                    }
                    case RUTA_COMPLETA -> {
                        final Ruta r = rutasIndex.get(e.idEntidad());
                        if (r != null && r.getEstado() == EstadoRuta.ACTIVA) {
                            r.setEstado(EstadoRuta.COMPLETADA);
                        }
                    }
                }
            }
        }
    }

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

    public void limpiarDatos() {
        this.aeropuertos = List.of();
        this.vuelosProgramados = List.of();
        this.vuelosInstancia = List.of();
        this.pedidos = List.of();
        this.resumenesVentana = List.of();
        this.maletasPorId.clear();
        this.rutasPorMaleta.clear();
        this.evaluacionesMaletas.clear();
        this.segmentosReplanificacion.clear();
        this.maletasPorVentana.clear();
        this.pedidosPorVentana.clear();
        this.vuelosPorVentana.clear();
        this.fechasVuelosGeneradas.clear();
        this.eventosSimulacion = null;
        this.ultimoTiempoSim = null;
        this.tareaScheduled = null;
        this.tickActual.set(0);
        this.currentSimTimeUtc.set(fechaInicioUtc);
        this.currentWindow.set(null);
        this.ultimoIndiceVuelosEnviado.set(0);
        this.planningGeneration.set(1);
        this.stateVersion.set(1);
        this.csvEscrito.set(false);
        this.planValido.set(false);
        this.planificando.set(false);
        this.replanPendiente.set(false);
        this.ultimaVentanaPlanificada.set("");
    }

    public Collection<Maleta> getMaletas() {
        return maletasPorId.values();
    }

    public Collection<Ruta> getRutas() {
        return rutasPorMaleta.values();
    }

    public Ruta getRutaPorMaleta(final String idMaleta) {
        return rutasPorMaleta.get(idMaleta);
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
        if (planificando.get()) {
            return false;
        }
        if (replanPendiente.get()) {
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

    public boolean marcarPlanInvalido() {
        return planValido.compareAndSet(true, false);
    }

    public boolean estaPlanValido() {
        return planValido.get();
    }

    public void marcarPlanValido() {
        planValido.set(true);
    }

    public boolean iniciarPlanificacion() {
        return planificando.compareAndSet(false, true);
    }

    public void finalizarPlanificacion() {
        planificando.set(false);
    }

    public boolean estaPlanificando() {
        return planificando.get();
    }

    public boolean solicitarReplan() {
        return replanPendiente.compareAndSet(false, true);
    }

    public boolean hayReplanPendiente() {
        return replanPendiente.get();
    }

    public void limpiarReplanPendiente() {
        replanPendiente.set(false);
    }

    public Map<String, VueloInstancia> getVueloIndex() {
        final Map<String, VueloInstancia> index = new HashMap<>();
        for (final VueloInstancia v : vuelosInstancia) {
            if (v != null && v.getIdVueloInstancia() != null) {
                index.put(v.getIdVueloInstancia(), v);
            }
        }
        return index;
    }

    public synchronized void agregarRutas(final List<Ruta> nuevasRutas) {
        if (nuevasRutas == null || nuevasRutas.isEmpty()) {
            return;
        }
        final Map<String, VueloInstancia> index = getVueloIndex();
        for (final Ruta ruta : nuevasRutas) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                final List<VueloInstancia> subrutasResueltas = ruta.getSubrutaIds().stream()
                        .map(index::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
                ruta.setSubrutas(subrutasResueltas);
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
            final Map<String, VueloInstancia> index = getVueloIndex();
            for (final Ruta r : rutas) {
                if (r != null && r.getIdMaleta() != null) {
                    final List<VueloInstancia> subrutasResueltas = r.getSubrutaIds().stream()
                            .map(index::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                    r.setSubrutas(subrutasResueltas);
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
                final LocalDateTime tConf = salidaUtc.minusMinutes(umbralConfirmacionMinutos);
                agregarEvento(tConf, TipoEventoSim.VUELO_CONFIRMA, id, null, 0);
                agregarEvento(salidaUtc, TipoEventoSim.VUELO_INICIA, id, null, 0);
                if (llegadaUtc != null) {
                    agregarEvento(llegadaUtc, TipoEventoSim.VUELO_FINALIZA, id, null, 0);
                }
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
