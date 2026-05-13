package pe.edu.pucp.aeroluggage.simulacion;

import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.VueloInstanciaResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SimulacionSnapshotService {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public void recalcularEstadoSesion(final SimulacionSesion sesion) {
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        final Map<String, Maleta> maletasPorId = sesion.getMaletas().stream()
                .filter(maleta -> maleta != null && maleta.getIdMaleta() != null)
                .collect(Collectors.toMap(Maleta::getIdMaleta, maleta -> maleta, (left, right) -> left));
        final Map<String, Ruta> rutaPorMaleta = sesion.getRutas().stream()
                .filter(ruta -> ruta != null && ruta.getIdMaleta() != null)
                .collect(Collectors.toMap(Ruta::getIdMaleta, ruta -> ruta, (left, right) -> left));
        final Map<String, Integer> usoPorVuelo = calcularUsoPorVuelo(sesion.getRutas(), maletasPorId, simTimeUtc);
        final Map<String, List<Maleta>> maletasPorPedido = new HashMap<>();
        final Map<String, Integer> maletasPorAeropuerto = new HashMap<>();

        actualizarEstadoVuelos(sesion.getVuelosInstancia(), usoPorVuelo, simTimeUtc);

        for (final Ruta ruta : sesion.getRutas()) {
            actualizarEstadoRuta(ruta, simTimeUtc);
        }

        for (final Maleta maleta : sesion.getMaletas()) {
            if (maleta == null) {
                continue;
            }
            if (maleta.getFechaRegistro() == null || maleta.getFechaRegistro().isAfter(simTimeUtc)) {
                maleta.setEstado(EstadoMaleta.EN_ALMACEN);
                maleta.setFechaLlegada(null);
                continue;
            }
            final Ruta ruta = rutaPorMaleta.get(maleta.getIdMaleta());
            final String ubicacionActual = actualizarEstadoMaleta(maleta, ruta, simTimeUtc);
            if (ubicacionActual != null) {
                maletasPorAeropuerto.merge(ubicacionActual, 1, Integer::sum);
            }
            if (maleta.getPedido() != null && maleta.getPedido().getIdPedido() != null) {
                maletasPorPedido.computeIfAbsent(maleta.getPedido().getIdPedido(), ignored -> new ArrayList<>()).add(maleta);
            }
        }

        for (final Pedido pedido : sesion.getPedidos()) {
            if (pedido == null || pedido.getIdPedido() == null) {
                continue;
            }
            actualizarEstadoPedido(pedido, maletasPorPedido.getOrDefault(pedido.getIdPedido(), List.of()));
        }

        for (final Aeropuerto aeropuerto : sesion.getAeropuertos()) {
            if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                continue;
            }
            aeropuerto.setMaletasActuales(maletasPorAeropuerto.getOrDefault(aeropuerto.getIdAeropuerto(), 0));
        }
    }

    public int contarMaletasEnTransito(final SimulacionSesion sesion) {
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        return (int) sesion.getMaletas().stream()
                .filter(maleta -> maleta != null
                        && maleta.getFechaRegistro() != null
                        && !maleta.getFechaRegistro().isAfter(simTimeUtc)
                        && maleta.getEstado() == EstadoMaleta.EN_TRANSITO)
                .count();
    }

    public int contarMaletasEntregadas(final SimulacionSesion sesion) {
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        return (int) sesion.getMaletas().stream()
                .filter(maleta -> maleta != null
                        && maleta.getFechaRegistro() != null
                        && !maleta.getFechaRegistro().isAfter(simTimeUtc)
                        && maleta.getEstado() == EstadoMaleta.ENTREGADA)
                .count();
    }

    public int contarMaletasSinRuta(final SimulacionSesion sesion) {
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        final Map<String, Maleta> maletasPorId = sesion.getMaletas().stream()
                .filter(maleta -> maleta != null && maleta.getIdMaleta() != null)
                .collect(Collectors.toMap(Maleta::getIdMaleta, maleta -> maleta, (left, right) -> left));
        return (int) sesion.getRutas().stream()
                .filter(ruta -> ruta == null
                        || ruta.getSubrutas() == null
                        || ruta.getSubrutas().isEmpty()
                        || ruta.getEstado() == EstadoRuta.FALLIDA)
                .filter(ruta -> {
                    if (ruta == null || ruta.getIdMaleta() == null) {
                        return true;
                    }
                    final Maleta maleta = maletasPorId.get(ruta.getIdMaleta());
                    return maleta != null
                            && maleta.getFechaRegistro() != null
                            && !maleta.getFechaRegistro().isAfter(simTimeUtc);
                })
                .count();
    }

    public int contarVuelosActivos(final SimulacionSesion sesion) {
        return (int) sesion.getVuelosInstancia().stream()
                .filter(vuelo -> vuelo != null && vuelo.getEstado() == EstadoVuelo.EN_PROGRESO)
                .count();
    }

    public int calcularCapacidadLibrePct(final SimulacionSesion sesion) {
        int capacidadTotal = 0;
        int capacidadDisponible = 0;
        for (final VueloInstancia vuelo : sesion.getVuelosInstancia()) {
            if (vuelo == null) {
                continue;
            }
            capacidadTotal += Math.max(0, vuelo.getCapacidadMaxima());
            capacidadDisponible += Math.max(0, vuelo.getCapacidadDisponible());
        }
        if (capacidadTotal <= 0) {
            return 0;
        }
        return (int) Math.round((capacidadDisponible * 100D) / capacidadTotal);
    }

    public List<AeropuertoResponse> mapearAeropuertos(final List<Aeropuerto> aeropuertos) {
        return aeropuertos.stream()
                .map(aeropuerto -> AeropuertoResponse.builder()
                        .withIdAeropuerto(aeropuerto.getIdAeropuerto())
                        .withCiudad(aeropuerto.getCiudad())
                        .withCapacidadAlmacen(aeropuerto.getCapacidadAlmacen())
                        .withMaletasActuales(aeropuerto.getMaletasActuales())
                        .withLatitud(aeropuerto.getLatitud())
                        .withLongitud(aeropuerto.getLongitud())
                        .withHusoGMT(aeropuerto.getHusoGMT())
                        .build())
                .toList();
    }

    public List<VueloInstanciaResponse> mapearVuelosInstancia(final List<VueloInstancia> vuelosInstancia) {
        return vuelosInstancia.stream()
                .map(vueloInstancia -> {
                    final int capacidadDisponible = vueloInstancia.getCapacidadDisponible();
                    final int capacidadMaxima = vueloInstancia.getCapacidadMaxima();
                    return VueloInstanciaResponse.builder()
                            .withIdVueloInstancia(vueloInstancia.getIdVueloInstancia())
                            .withCodigo(vueloInstancia.getCodigo())
                            .withAeropuertoOrigen(vueloInstancia.getAeropuertoOrigen() != null
                                    ? vueloInstancia.getAeropuertoOrigen().getIdAeropuerto() : null)
                            .withAeropuertoDestino(vueloInstancia.getAeropuertoDestino() != null
                                    ? vueloInstancia.getAeropuertoDestino().getIdAeropuerto() : null)
                            .withFechaSalida(formatDateTime(vueloInstancia.getFechaSalida()))
                            .withFechaLlegada(formatDateTime(vueloInstancia.getFechaLlegada()))
                            .withCapacidadMaxima(capacidadMaxima)
                            .withCapacidadDisponible(capacidadDisponible)
                            .withCapacidadUsada(Math.max(0, capacidadMaxima - capacidadDisponible))
                            .withEstado(vueloInstancia.getEstado() != null ? vueloInstancia.getEstado().name() : null)
                            .build();
                })
                .toList();
    }

    private Map<String, Integer> calcularUsoPorVuelo(final List<Ruta> rutas,
                                                     final Map<String, Maleta> maletasPorId,
                                                     final LocalDateTime simTimeUtc) {
        final Map<String, Integer> usoPorVuelo = new HashMap<>();
        for (final Ruta ruta : rutas) {
            if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final Maleta maleta = ruta.getIdMaleta() != null ? maletasPorId.get(ruta.getIdMaleta()) : null;
            if (maleta == null || maleta.getFechaRegistro() == null || maleta.getFechaRegistro().isAfter(simTimeUtc)) {
                continue;
            }
            for (final VueloInstancia vuelo : ruta.getSubrutas()) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                usoPorVuelo.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
            }
        }
        return usoPorVuelo;
    }

    private void actualizarEstadoVuelos(final List<VueloInstancia> vuelos,
                                        final Map<String, Integer> usoPorVuelo,
                                        final LocalDateTime simTimeUtc) {
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo == null) {
                continue;
            }
            final int uso = usoPorVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
            vuelo.setCapacidadDisponible(Math.max(0, vuelo.getCapacidadMaxima() - uso));
            vuelo.actualizarCapacidad();
            if (vuelo.getEstado() == EstadoVuelo.CANCELADO) {
                continue;
            }
            if (vuelo.getFechaLlegada() != null && !vuelo.getFechaLlegada().isAfter(simTimeUtc)) {
                vuelo.setEstado(EstadoVuelo.FINALIZADO);
            } else if (vuelo.getFechaSalida() != null && !vuelo.getFechaSalida().isAfter(simTimeUtc)) {
                vuelo.setEstado(EstadoVuelo.EN_PROGRESO);
            } else {
                vuelo.setEstado(EstadoVuelo.PROGRAMADO);
            }
        }
    }

    private void actualizarEstadoRuta(final Ruta ruta, final LocalDateTime simTimeUtc) {
        if (ruta == null) {
            return;
        }
        if (ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            ruta.setEstado(EstadoRuta.FALLIDA);
            ruta.setDuracion(0D);
            return;
        }
        final VueloInstancia primero = ruta.getSubrutas().getFirst();
        final VueloInstancia ultimo = ruta.getSubrutas().getLast();
        ruta.calcularPlazo();
        if (ultimo.getFechaLlegada() != null && !simTimeUtc.isBefore(ultimo.getFechaLlegada())) {
            ruta.setEstado(EstadoRuta.COMPLETADA);
        } else if (primero.getFechaSalida() != null && !simTimeUtc.isBefore(primero.getFechaSalida())) {
            ruta.setEstado(EstadoRuta.ACTIVA);
        } else {
            ruta.setEstado(EstadoRuta.PLANIFICADA);
        }
    }

    private String actualizarEstadoMaleta(final Maleta maleta,
                                          final Ruta ruta,
                                          final LocalDateTime simTimeUtc) {
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            maleta.setEstado(EstadoMaleta.EN_ALMACEN);
            maleta.setFechaLlegada(null);
            return maleta.getPedido() != null && maleta.getPedido().getAeropuertoOrigen() != null
                    ? maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto()
                    : null;
        }

        String aeropuertoActual = ruta.getSubrutas().getFirst().getAeropuertoOrigen() != null
                ? ruta.getSubrutas().getFirst().getAeropuertoOrigen().getIdAeropuerto()
                : null;
        for (final VueloInstancia vuelo : ruta.getSubrutas()) {
            if (vuelo == null) {
                continue;
            }
            if (vuelo.getFechaSalida() != null && simTimeUtc.isBefore(vuelo.getFechaSalida())) {
                maleta.setEstado(EstadoMaleta.EN_ALMACEN);
                maleta.setFechaLlegada(null);
                return aeropuertoActual;
            }
            if (vuelo.getFechaLlegada() != null && simTimeUtc.isBefore(vuelo.getFechaLlegada())) {
                maleta.setEstado(EstadoMaleta.EN_TRANSITO);
                maleta.setFechaLlegada(null);
                return null;
            }
            aeropuertoActual = vuelo.getAeropuertoDestino() != null
                    ? vuelo.getAeropuertoDestino().getIdAeropuerto()
                    : aeropuertoActual;
        }

        maleta.setEstado(EstadoMaleta.ENTREGADA);
        maleta.setFechaLlegada(ruta.getSubrutas().getLast().getFechaLlegada());
        return null;
    }

    private void actualizarEstadoPedido(final Pedido pedido, final List<Maleta> maletasPedido) {
        if (maletasPedido.isEmpty()) {
            pedido.setEstado(EstadoPedido.REGISTRADO);
            return;
        }
        final boolean todasEntregadas = maletasPedido.stream()
                .allMatch(maleta -> maleta.getEstado() == EstadoMaleta.ENTREGADA);
        if (todasEntregadas) {
            pedido.setEstado(EstadoPedido.ENTREGADO);
            return;
        }
        final boolean algunaConMovimiento = maletasPedido.stream()
                .anyMatch(maleta -> maleta.getEstado() == EstadoMaleta.EN_TRANSITO
                        || maleta.getEstado() == EstadoMaleta.ENTREGADA);
        pedido.setEstado(algunaConMovimiento ? EstadoPedido.EN_PROCESO : EstadoPedido.REGISTRADO);
    }

    private String formatDateTime(final LocalDateTime value) {
        return value != null ? value.format(ISO_DATE_TIME) : null;
    }
}
