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

import pe.edu.pucp.aeroluggage.cargador.CargadorEnvios;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoMaletaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoRutaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoVueloDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.OcupacionAeropuertoDTO;

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
    private volatile List<VueloInstancia> vuelosCalientes = List.of();
    private volatile List<VueloInstancia> vuelosFrios = List.of();
    private final ConcurrentHashMap<String, Pedido> pedidosCalientes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Pedido> pedidosFrios = new ConcurrentHashMap<>();
    private volatile List<ResumenVentanaPlanificacion> resumenesVentana = List.of();
    private volatile ScheduledFuture<?> tareaScheduled;
    private final ConcurrentHashMap<String, Maleta> maletasPorId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Ruta> rutasPorMaleta = new ConcurrentHashMap<>();

    record ColdEntry(Maleta maleta, Ruta ruta, LocalDateTime entrega) {}
    private final ConcurrentHashMap<String, MaletaFallos> evaluacionesMaletas = new ConcurrentHashMap<>();
    private final AtomicInteger totalMaletasEntregadas = new AtomicInteger(0);
    private final ConcurrentHashMap<String, ColdEntry> maletasFrias = new ConcurrentHashMap<>();
    private final Set<String> idsEntregadasEnTick = new HashSet<>();
    private final Map<String, String> idsCompletadasEnTick = new HashMap<>();

    private final List<SegmentoReplanificacion> segmentosReplanificacion = new CopyOnWriteArrayList<>();

    private final ConcurrentHashMap<String, List<Maleta>> maletasPorVentana = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Pedido>> pedidosPorVentana = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<VueloInstancia>> vuelosPorVentana = new ConcurrentHashMap<>();
    private final AtomicLong ultimoIndiceVuelosEnviado = new AtomicLong(0);
    private final Set<String> vuelosGenerados = ConcurrentHashMap.newKeySet();
    private volatile int umbralConfirmacionMinutos;
    private CargadorEnvios.LectorLotesEnvios lectorEnvios;
    private LocalDateTime ultimaCargaPedidos;

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

    public void setLectorEnvios(final CargadorEnvios.LectorLotesEnvios lectorEnvios) {
        this.lectorEnvios = lectorEnvios;
    }

    public void setUltimaCargaPedidos(final LocalDateTime ultimaCargaPedidos) {
        this.ultimaCargaPedidos = ultimaCargaPedidos;
    }

    public void asegurarPedidosParaVentana(final LocalDateTime finVentana) {
        if (lectorEnvios == null || !lectorEnvios.tienePendientes()) {
            return;
        }
        if (ultimaCargaPedidos != null && !finVentana.isAfter(ultimaCargaPedidos)) {
            return;
        }
        final DatosEntrada lote = lectorEnvios.siguienteLoteHasta(finVentana);
        for (final Pedido p : lote.getPedidos()) {
            if (p == null || p.getIdPedido() == null) continue;
            if (p.getFechaRegistro() != null && p.getFechaRegistro().isBefore(fechaInicioUtc)) continue;
            pedidosCalientes.put(p.getIdPedido(), p);
            final String v = calcularVentana(p.getFechaRegistro());
            pedidosPorVentana.computeIfAbsent(v, k -> new ArrayList<>()).add(p);
        }
        for (final Maleta m : lote.getMaletas()) {
            if (m == null || m.getIdMaleta() == null) continue;
            if (m.getFechaRegistro() != null && m.getFechaRegistro().isBefore(fechaInicioUtc)) continue;
            maletasPorId.put(m.getIdMaleta(), m);
            final String v = calcularVentana(m.getFechaRegistro());
            maletasPorVentana.computeIfAbsent(v, k -> new ArrayList<>()).add(m);
        }
        ultimaCargaPedidos = finVentana;
    }

    public void construirIndiceEventos(final int umbralConfirmacionMinutos) {
        eventosSimulacion = new TreeMap<>();
        final Map<String, Aeropuerto> aeropuertosPorId = new HashMap<>();
        for (final Aeropuerto a : aeropuertos) {
            if (a != null && a.getIdAeropuerto() != null) {
                aeropuertosPorId.put(a.getIdAeropuerto(), a);
            }
        }

        for (final VueloInstancia v : vuelosCalientes) {
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

        final Map<String, VueloInstancia> vueloIndex = getVueloIndex();
        for (final Ruta r : rutasPorMaleta.values()) {
            if (r == null) continue;
            final Maleta m = maletasPorId.get(r.getIdMaleta());
            if (m == null || m.getFechaRegistro() == null) continue;

            final List<String> ids = r.getSubrutas();
            if (ids.isEmpty()) continue;

            agregarEvento(m.getFechaRegistro(), TipoEventoSim.MALETA_APARECE,
                    r.getIdMaleta(),
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

            if (!ids.isEmpty()) {
                final VueloInstancia primero = vueloIndex.get(ids.getFirst());
                final VueloInstancia ultimoSub = vueloIndex.get(ids.getLast());
                if (primero != null && primero.getFechaSalida() != null) {
                    agregarEvento(primero.getFechaSalida(), TipoEventoSim.RUTA_ACTIVA,
                            r.getIdRuta(), null, 0);
                }
                if (ultimoSub != null && ultimoSub.getFechaLlegada() != null) {
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
        final EventoSim e = new EventoSim(tipo, idEntidad, idAeropuerto, delta);
        switch (tipo) {
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
                aplicarEventoAhora(e.tipo(), e.idEntidad(), e.idAeropuerto(), e.delta());
            }
        }

        final List<VueloInstancia> calientesRestantes = new ArrayList<>();
        final List<VueloInstancia> nuevosFrios = new ArrayList<>(this.vuelosFrios);
        boolean huboMovimiento = false;
        for (final VueloInstancia v : this.vuelosCalientes) {
            if (v.getEstado() == EstadoVuelo.FINALIZADO
                    || v.getEstado() == EstadoVuelo.CANCELADO) {
                nuevosFrios.add(v);
                huboMovimiento = true;
            } else {
                calientesRestantes.add(v);
            }
        }
        if (huboMovimiento) {
            this.vuelosCalientes = List.copyOf(calientesRestantes);
            this.vuelosFrios = List.copyOf(nuevosFrios);
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
            final Map<String, VueloInstancia> idx = getVueloIndex();
            final VueloInstancia v1 = idx.get(r.getSubrutas().getFirst());
            if (v1 != null) {
                v1.setCapacidadDisponible(Math.max(0, v1.getCapacidadDisponible() - 1));
            }
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
        final Maleta m = maletasPorId.remove(e.idEntidad());
        if (m == null) return;
        final LocalDateTime entrega = currentSimTimeUtc.get();
        totalMaletasEntregadas.incrementAndGet();
        m.setEstado(EstadoMaleta.ENTREGADA);
        m.setFechaLlegada(entrega);
        if (m.getPedido() != null && m.getPedido().getAeropuertoDestino() != null) {
            m.setAeropuertoActual(m.getPedido().getAeropuertoDestino().getIdAeropuerto());
        }
        final Ruta r = rutasPorMaleta.remove(e.idEntidad());
        if (r != null) {
            r.setEstado(EstadoRuta.COMPLETADA);
            r.setFechaEntrega(entrega);
        }
        maletasFrias.put(e.idEntidad(), new ColdEntry(m, r, entrega));
        idsEntregadasEnTick.add(e.idEntidad());
        if (r != null) idsCompletadasEnTick.put(r.getIdRuta(), e.idEntidad());
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
        for (final VueloInstancia v : vuelosCalientes) {
            if (v != null && v.getIdVueloInstancia() != null
                    && v.getIdVueloInstancia().equals(e.idEntidad())
                    && v.getEstado() == EstadoVuelo.PROGRAMADO) {
                v.setEstado(EstadoVuelo.CONFIRMADO);
                break;
            }
        }
    }

    private void aplicarVueloInicia(final EventoSim e) {
        for (final VueloInstancia v : vuelosCalientes) {
            if (v != null && v.getIdVueloInstancia() != null
                    && v.getIdVueloInstancia().equals(e.idEntidad())
                    && v.getEstado() != EstadoVuelo.CANCELADO) {
                v.setEstado(EstadoVuelo.EN_PROGRESO);
                break;
            }
        }
    }

    private void aplicarVueloFinaliza(final EventoSim e) {
        for (final VueloInstancia v : vuelosCalientes) {
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

        if (m.getFechaRegistro() != null && !m.getFechaRegistro().isAfter(currentSimTimeUtc.get())) {
            final VueloInstancia primerVuelo = !ids.isEmpty()
                    ? vueloIndex.get(ids.getFirst()) : null;
            if (primerVuelo != null) {
                primerVuelo.setCapacidadDisponible(
                        Math.max(0, primerVuelo.getCapacidadDisponible() - 1));
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
            final Map<String, VueloInstancia> idx = getVueloIndex();
            for (final String idVuelo : rutaAntigua.getSubrutas()) {
                final VueloInstancia v = idx.get(idVuelo);
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
                            if (r != null) {
                                final List<String> idsRuta = r.getSubrutas();
                                if (!idsRuta.isEmpty()) {
                                    final VueloInstancia v1 = vuelosIndex.get(idsRuta.getFirst());
                                    if (v1 != null) {
                                        v1.setCapacidadDisponible(Math.max(0, v1.getCapacidadDisponible() - 1));
                                    }
                                }
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
                        final String idRuta = rutaPorMaleta.get(e.idEntidad());
                        if (idRuta != null) {
                            final Ruta r = rutasIndex.get(idRuta);
                            if (r != null) {
                                r.setEstado(EstadoRuta.COMPLETADA);
                                final List<String> idsRuta = r.getSubrutas();
                                if (!idsRuta.isEmpty()) {
                                    final VueloInstancia lastVuelo = vuelosIndex.get(idsRuta.getLast());
                                    if (lastVuelo != null && lastVuelo.getFechaLlegada() != null) {
                                        r.setFechaEntrega(lastVuelo.getFechaLlegada().plusMinutes(10));
                                    }
                                }
                            }
                        }
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
                            final List<String> idsRuta = r.getSubrutas();
                            if (!idsRuta.isEmpty()) {
                                final VueloInstancia lastVuelo = vuelosIndex.get(idsRuta.getLast());
                                if (lastVuelo != null) {
                                    r.setFechaEntrega(lastVuelo.getFechaLlegada());
                                }
                            }
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
        this(sessionId, fechaInicio, horaInicio, totalDias,
                duracionDiaSimuladoMs, windowSizeMinutes, windowSpacingMinutes, 0);
    }

    public SimulacionSesion(
            final String sessionId,
            final LocalDate fechaInicio,
            final LocalTime horaInicio,
            final int totalDias,
            final long duracionDiaSimuladoMs,
            final int windowSizeMinutes,
            final int windowSpacingMinutes,
            final Integer husoGMT) {
        this.sessionId = sessionId;
        this.fechaInicio = fechaInicio;
        this.totalDias = totalDias;
        this.duracionDiaSimuladoMs = Math.max(1L, duracionDiaSimuladoMs);
        this.windowSizeMinutes = Math.max(1, windowSizeMinutes);
        this.windowSpacingMinutes = Math.max(1, windowSpacingMinutes);
        final int offset = husoGMT != null ? husoGMT : 0;
        this.fechaInicioUtc = LocalDateTime.of(fechaInicio, horaInicio).minusHours(offset);
        this.fechaFinUtc = fechaInicioUtc.plusDays(Math.max(0L, totalDias));
        this.startedAtRealMs = System.currentTimeMillis();
        this.currentSimTimeUtc = new AtomicReference<>(fechaInicioUtc);
        this.currentWindow = new AtomicReference<>(buildWindowFor(fechaInicioUtc, "ACTIVE"));
    }

    public void setTareaScheduled(final ScheduledFuture<?> tarea) {
        this.tareaScheduled = tarea;
    }

    public ScheduledFuture<?> getTareaScheduled() {
        return tareaScheduled;
    }

    public void limpiarDatos() {
        if (lectorEnvios != null) {
            try { lectorEnvios.close(); } catch (final Exception ignored) {}
            lectorEnvios = null;
        }
        ultimaCargaPedidos = null;
        this.aeropuertos = List.of();
        this.vuelosProgramados = List.of();
        this.vuelosCalientes = List.of();
        this.vuelosFrios = List.of();
        this.pedidosCalientes.clear();
        this.pedidosFrios.clear();
        this.resumenesVentana = List.of();
        this.maletasPorId.clear();
        this.rutasPorMaleta.clear();
        this.maletasFrias.clear();
        this.idsEntregadasEnTick.clear();
        this.idsCompletadasEnTick.clear();
        this.evaluacionesMaletas.clear();
        this.segmentosReplanificacion.clear();
        this.maletasPorVentana.clear();
        this.pedidosPorVentana.clear();
        this.vuelosPorVentana.clear();
        this.vuelosGenerados.clear();
        this.eventosSimulacion = null;
        this.ultimoTiempoSim = null;
        this.tareaScheduled = null;
        this.tickActual.set(0);
        this.totalMaletasEntregadas.set(0);
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
        final List<Maleta> todas = new ArrayList<>(maletasPorId.values());
        for (final ColdEntry entry : maletasFrias.values()) {
            if (entry.maleta() != null) {
                todas.add(entry.maleta());
            }
        }
        return todas;
    }

    public Collection<Maleta> getMaletasCalientes() {
        return maletasPorId.values();
    }

    public Collection<ColdEntry> getMaletasFrias() {
        return maletasFrias.values();
    }

    public Collection<Pedido> getPedidosCalientes() {
        return pedidosCalientes.values();
    }

    public Collection<Pedido> getPedidosFrios() {
        return pedidosFrios.values();
    }

    public List<Pedido> getPedidos() {
        final List<Pedido> todos = new ArrayList<>(
                pedidosCalientes.size() + pedidosFrios.size());
        todos.addAll(pedidosCalientes.values());
        todos.addAll(pedidosFrios.values());
        return todos;
    }

    public void moverPedidoAFrio(final String pedidoId) {
        final Pedido pedido = pedidosCalientes.remove(pedidoId);
        if (pedido != null) {
            pedidosFrios.put(pedidoId, pedido);
        }
    }

    public List<VueloInstancia> getVuelosInstancia() {
        if (vuelosFrios.isEmpty()) {
            return vuelosCalientes;
        }
        final List<VueloInstancia> todos = new ArrayList<>(
                vuelosCalientes.size() + vuelosFrios.size());
        todos.addAll(vuelosCalientes);
        todos.addAll(vuelosFrios);
        return todos;
    }

    public List<VueloInstancia> getVuelosCalientes() {
        return vuelosCalientes;
    }

    public List<VueloInstancia> getVuelosFrios() {
        return vuelosFrios;
    }

    public void moverVueloAFrio(final String idVuelo) {
        for (final VueloInstancia v : vuelosCalientes) {
            if (v.getIdVueloInstancia() != null && v.getIdVueloInstancia().equals(idVuelo)) {
                final List<VueloInstancia> calientes = new ArrayList<>(vuelosCalientes);
                calientes.remove(v);
                final List<VueloInstancia> frios = new ArrayList<>(vuelosFrios);
                frios.add(v);
                this.vuelosCalientes = List.copyOf(calientes);
                this.vuelosFrios = List.copyOf(frios);
                return;
            }
        }
    }

    public Collection<Ruta> getRutas() {
        final List<Ruta> todas = new ArrayList<>(rutasPorMaleta.values());
        for (final ColdEntry entry : maletasFrias.values()) {
            if (entry.ruta() != null) {
                todas.add(entry.ruta());
            }
        }
        return todas;
    }

    public Ruta getRutaPorMaleta(final String idMaleta) {
        final Ruta r = rutasPorMaleta.get(idMaleta);
        if (r != null) {
            return r;
        }
        final ColdEntry entry = maletasFrias.get(idMaleta);
        return entry == null ? null : entry.ruta();
    }

    public int getTotalMaletasEntregadas() {
        return totalMaletasEntregadas.get();
    }

    public Set<String> consumirIdsEntregadasEnTick() {
        final Set<String> ids = new HashSet<>(idsEntregadasEnTick);
        idsEntregadasEnTick.clear();
        return ids;
    }

    public Map<String, String> consumirIdsCompletadasEnTick() {
        final Map<String, String> ids = new HashMap<>(idsCompletadasEnTick);
        idsCompletadasEnTick.clear();
        return ids;
    }

    public synchronized void podarEntidadesAnteriores(final LocalDateTime simTimeUtc, final Duration retencionPedidos,
                                                      final Duration retencionVuelos) {
        if (simTimeUtc == null) {
            return;
        }

        // ─── FASE 1: PEDIDOS (solo fríos, calientes no tienen fechaEntrega) ────
        final Set<String> pedidosPodables = new HashSet<>();
        for (final Pedido pedido : pedidosFrios.values()) {
            if (pedido == null || pedido.getIdPedido() == null || pedido.getFechaEntrega() == null) {
                continue;
            }
            if (!pedido.getFechaEntrega().plus(retencionPedidos).isAfter(simTimeUtc)) {
                pedidosPodables.add(pedido.getIdPedido());
            }
        }

        if (!pedidosPodables.isEmpty()) {
            final Set<String> maletasPodables = new HashSet<>();
            for (final Maleta maleta : maletasPorId.values()) {
                if (maleta == null || maleta.getIdMaleta() == null || maleta.getPedido() == null) continue;
                final String idPedido = maleta.getPedido().getIdPedido();
                if (idPedido != null && pedidosPodables.contains(idPedido)) {
                    maletasPodables.add(maleta.getIdMaleta());
                }
            }
            for (final Map.Entry<String, ColdEntry> entry : maletasFrias.entrySet()) {
                final ColdEntry coldEntry = entry.getValue();
                final Maleta maleta = coldEntry != null ? coldEntry.maleta() : null;
                if (maleta == null || maleta.getIdMaleta() == null || maleta.getPedido() == null) continue;
                final String idPedido = maleta.getPedido().getIdPedido();
                if (idPedido != null && pedidosPodables.contains(idPedido)) {
                    maletasPodables.add(maleta.getIdMaleta());
                }
            }

            if (!maletasPodables.isEmpty()) {
                for (final String idMaleta : maletasPodables) {
                    maletasPorId.remove(idMaleta);
                    rutasPorMaleta.remove(idMaleta);
                    maletasFrias.remove(idMaleta);
                    idsEntregadasEnTick.remove(idMaleta);
                }
                idsCompletadasEnTick.entrySet().removeIf(entry -> maletasPodables.contains(entry.getValue()));
                evaluacionesMaletas.entrySet().removeIf(entry -> maletasPodables.contains(entry.getKey()));
                maletasPorVentana.values().forEach(lista ->
                        lista.removeIf(m -> m != null && m.getIdMaleta() != null
                                && maletasPodables.contains(m.getIdMaleta())));
            }

            for (final String id : pedidosPodables) {
                pedidosFrios.remove(id);
            }
            pedidosPorVentana.values().forEach(lista ->
                    lista.removeIf(p -> p != null && p.getIdPedido() != null
                            && pedidosPodables.contains(p.getIdPedido())));
        }

        // ─── FASE 2: VUELOS (SIEMPRE, desacoplada) ───────────────────────────
        final Set<String> vuelosEnUso = new HashSet<>();
        for (final Ruta ruta : rutasPorMaleta.values()) {
            if (ruta == null) continue;
            for (final String idVuelo : ruta.getSubrutaIds()) {
                if (idVuelo != null) vuelosEnUso.add(idVuelo);
            }
        }
        for (final ColdEntry entry : maletasFrias.values()) {
            if (entry != null && entry.ruta() != null) {
                for (final String idVuelo : entry.ruta().getSubrutaIds()) {
                    if (idVuelo != null) vuelosEnUso.add(idVuelo);
                }
            }
        }

        this.vuelosFrios = vuelosFrios.stream()
                .filter(v -> {
                    if (v.getIdVueloInstancia() == null) return false;
                    if (vuelosEnUso.contains(v.getIdVueloInstancia())) return true;
                    if (v.getFechaLlegada() != null
                            && !v.getFechaLlegada().plus(retencionVuelos).isBefore(simTimeUtc)) return true;
                    return false;
                })
                .toList();

        final Set<String> idsVuelosEnMemoria = new HashSet<>();
        for (final VueloInstancia v : vuelosCalientes) {
            if (v.getIdVueloInstancia() != null) idsVuelosEnMemoria.add(v.getIdVueloInstancia());
        }
        for (final VueloInstancia v : vuelosFrios) {
            if (v.getIdVueloInstancia() != null) idsVuelosEnMemoria.add(v.getIdVueloInstancia());
        }
        vuelosPorVentana.values().forEach(lista ->
                lista.removeIf(v -> v != null && v.getIdVueloInstancia() != null
                        && !idsVuelosEnMemoria.contains(v.getIdVueloInstancia())));

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
        for (final VueloInstancia v : vuelosCalientes) {
            if (v != null && v.getIdVueloInstancia() != null) {
                index.put(v.getIdVueloInstancia(), v);
            }
        }
        for (final VueloInstancia v : vuelosFrios) {
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
                || !vuelosCalientes.isEmpty() || !vuelosFrios.isEmpty()
                || !pedidosCalientes.isEmpty() || !pedidosFrios.isEmpty()
                || !maletasPorId.isEmpty()
                || !rutasPorMaleta.isEmpty();
    }

    public void setSnapshotData(final List<Aeropuerto> aeropuertos,
                                final List<VueloProgramado> vuelosProgramados,
                                final List<VueloInstancia> vuelosInstancia,
                                final List<Pedido> pedidos,
                                final List<Maleta> maletas,
                                final List<Ruta> rutas) {
        this.aeropuertos = aeropuertos == null ? List.of() : List.copyOf(aeropuertos);
        this.vuelosProgramados = vuelosProgramados == null
                ? List.of()
                : List.copyOf(vuelosProgramados);
        this.resumenesVentana = List.of();
        this.vuelosCalientes = List.of();
        this.vuelosFrios = List.of();
        this.pedidosCalientes.clear();
        this.pedidosFrios.clear();
        this.maletasPorId.clear();
        this.rutasPorMaleta.clear();
        this.maletasPorVentana.clear();
        this.pedidosPorVentana.clear();
        this.vuelosPorVentana.clear();
        this.ultimoIndiceVuelosEnviado.set(0);
        this.vuelosGenerados.clear();
        if (vuelosInstancia != null) {
            final List<VueloInstancia> calientes = new ArrayList<>();
            final List<VueloInstancia> frios = new ArrayList<>();
            for (final VueloInstancia v : vuelosInstancia) {
                if (v == null) continue;
                if (v.getEstado() == EstadoVuelo.FINALIZADO
                        || v.getEstado() == EstadoVuelo.CANCELADO) {
                    frios.add(v);
                } else {
                    calientes.add(v);
                }
            }
            this.vuelosCalientes = List.copyOf(calientes);
            this.vuelosFrios = List.copyOf(frios);
        for (final VueloInstancia v : getVuelosInstancia()) {
                if (v != null && v.getIdVueloInstancia() != null && v.getFechaSalida() != null) {
                    final String w = calcularVentana(v.getFechaSalida());
                    this.vuelosPorVentana.computeIfAbsent(w, k -> new ArrayList<>()).add(v);
                }
            }
        }
        if (maletas != null) {
            for (final Maleta m : maletas) {
                if (m != null && m.getIdMaleta() != null) {
                    if (m.getFechaRegistro() != null && m.getFechaRegistro().isBefore(fechaInicioUtc)) continue;
                    this.maletasPorId.put(m.getIdMaleta(), m);
                    final String v = calcularVentana(m.getFechaRegistro());
                    this.maletasPorVentana.computeIfAbsent(v, k -> new ArrayList<>()).add(m);
                }
            }
        }
        if (pedidos != null) {
            for (final Pedido p : pedidos) {
                if (p != null && p.getIdPedido() != null) {
                    if (p.getFechaRegistro() != null && p.getFechaRegistro().isBefore(fechaInicioUtc)) continue;
                    this.pedidosCalientes.put(p.getIdPedido(), p);
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

    public record TickSnapshot(
            int almacen,
            int enTransito,
            int entregadas,
            int sinRuta,
            int vuelosActivos,
            int capacidadLibrePct,
            List<EstadoMaletaDTO> estadosMaletas,
            List<EstadoRutaDTO> estadosRutas,
            List<EstadoVueloDTO> estadosVuelos,
            List<OcupacionAeropuertoDTO> aeropuertos
    ) {}

    public TickSnapshot consolidar(final LocalDateTime simTimeUtc) {
        int almacen = 0;
        int enTransito = 0;
        int sinRuta = 0;
        final List<EstadoMaletaDTO> estadosMaletas = new ArrayList<>();

        for (final Maleta m : maletasPorId.values()) {
            if (m == null || m.getFechaRegistro() == null
                    || m.getFechaRegistro().isAfter(simTimeUtc)) continue;
            final EstadoMaleta estado = m.getEstado();
            if (estado == EstadoMaleta.EN_ALMACEN) almacen++;
            else if (estado == EstadoMaleta.EN_TRANSITO) enTransito++;
            estadosMaletas.add(EstadoMaletaDTO.builder()
                    .withId(m.getIdMaleta())
                    .withE(estado != null ? estado.ordinal() : 0)
                    .build());
            if (estado == EstadoMaleta.EN_ALMACEN || estado == EstadoMaleta.EN_TRANSITO) {
                final Ruta r = rutasPorMaleta.get(m.getIdMaleta());
                if (r == null || r.getEstado() == EstadoRuta.REPLANIFICADA
                        || r.getSubrutas().isEmpty()) {
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
        for (final VueloInstancia v : getVuelosInstancia()) {
            if (v == null) continue;
            final EstadoVuelo estado = v.getEstado();
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
        for (final Aeropuerto a : aeropuertos) {
            if (a == null) continue;
            aeropuertosDTO.add(OcupacionAeropuertoDTO.builder()
                    .withId(a.getIdAeropuerto())
                    .withOcc(a.getMaletasActuales())
                    .build());
        }

        return new TickSnapshot(almacen, enTransito, totalMaletasEntregadas.get(), sinRuta,
                vuelosActivos, capacidadLibrePct, estadosMaletas, estadosRutas,
                estadosVuelos, aeropuertosDTO);
    }

    public synchronized void asegurarVuelosParaBanda(final long desdeBucket, final long hastaBucket) {
        final long rangoBuckets = hastaBucket - desdeBucket + 1L;
        final LocalDateTime bucketStart = fechaInicioUtc
                .plusMinutes((desdeBucket - 1L) * windowSpacingMinutes);
        final LocalDateTime bucketEnd = bucketStart
                .plusMinutes(rangoBuckets * windowSpacingMinutes);

        final List<VueloInstancia> nuevos = new ArrayList<>();
        for (int idxVp = 0; idxVp < vuelosProgramados.size(); idxVp++) {
            final VueloProgramado vp = vuelosProgramados.get(idxVp);
            if (vp == null) continue;

            final int gmtOrigen = vp.getAeropuertoOrigen() != null
                    ? vp.getAeropuertoOrigen().getHusoGMT() : 0;
            final int gmtDestino = vp.getAeropuertoDestino() != null
                    ? vp.getAeropuertoDestino().getHusoGMT() : 0;

            LocalDate opDate = bucketStart.plusHours(gmtOrigen).toLocalDate();
            LocalDateTime salidaUtc = LocalDateTime.of(opDate, vp.getHoraSalida())
                    .minusHours(gmtOrigen);

            while (salidaUtc.isBefore(bucketStart)) {
                opDate = opDate.plusDays(1);
                salidaUtc = salidaUtc.plusDays(1);
            }

            while (salidaUtc.isBefore(bucketEnd)) {
                if (salidaUtc.isBefore(fechaInicioUtc)) {
                    opDate = opDate.plusDays(1);
                    salidaUtc = salidaUtc.plusDays(1);
                    continue;
                }

                final String key = vp.getIdVueloProgramado() + "@" + opDate;
                if (vuelosGenerados.add(key)) {
                    final LocalDate fechaDestino = salidaUtc.plusHours(gmtDestino).toLocalDate();
                    LocalDateTime llegadaUtc = LocalDateTime.of(fechaDestino, vp.getHoraLlegada())
                            .minusHours(gmtDestino);
                    while (!llegadaUtc.isAfter(salidaUtc)) {
                        llegadaUtc = llegadaUtc.plusDays(1);
                    }

                    final long secuenciaBase = opDate.toEpochDay() * (long) vuelosProgramados.size() + idxVp;
                    final String id = String.format("VI%08d", secuenciaBase + 1L);

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

                opDate = opDate.plusDays(1);
                salidaUtc = salidaUtc.plusDays(1);
            }
        }

        if (!nuevos.isEmpty()) {
            final List<VueloInstancia> calientes = new ArrayList<>(vuelosCalientes);
            calientes.addAll(nuevos);
            this.vuelosCalientes = List.copyOf(calientes);
        }
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
