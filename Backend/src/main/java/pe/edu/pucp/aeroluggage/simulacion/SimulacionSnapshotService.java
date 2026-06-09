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
import pe.edu.pucp.aeroluggage.config.SistemaConfiguracion;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.RutaVueloResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.VueloInstanciaResponse;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoMaletaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoRutaDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.EstadoVueloDTO;
import pe.edu.pucp.aeroluggage.dto.simulacion.ws.OcupacionAeropuertoDTO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SimulacionSnapshotService {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SistemaConfiguracion sistemaConfiguracion;

    public SimulacionSnapshotService(final SistemaConfiguracion sistemaConfiguracion) {
        this.sistemaConfiguracion = sistemaConfiguracion;
    }

    public void recalcularEstadoSesion(final SimulacionSesion sesion) {
        final LocalDateTime simTimeAnterior = sesion.getUltimoTiempoSim();
        final LocalDateTime simTimeActual = sesion.getCurrentSimTimeUtc().get();

        sesion.procesarEventos(simTimeAnterior, simTimeActual,
                sistemaConfiguracion.getUmbralConfirmacionMinutos());

        final Map<String, List<Maleta>> maletasPorPedido = new HashMap<>();
        for (final Maleta m : sesion.getMaletas()) {
            if (m == null || m.getPedido() == null || m.getPedido().getIdPedido() == null) continue;
            maletasPorPedido
                    .computeIfAbsent(m.getPedido().getIdPedido(), k -> new ArrayList<>())
                    .add(m);
        }
        for (final Pedido pedido : sesion.getPedidos()) {
            if (pedido == null || pedido.getIdPedido() == null) continue;
            actualizarEstadoPedido(pedido,
                    maletasPorPedido.getOrDefault(pedido.getIdPedido(), List.of()));
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
        return sesion.getTotalMaletasEntregadas();
    }

    public int contarMaletasSinRuta(final SimulacionSesion sesion) {
        final LocalDateTime simTimeUtc = sesion.getCurrentSimTimeUtc().get();
        int sinRuta = 0;
        for (final Maleta m : sesion.getMaletas()) {
            if (m == null || m.getFechaRegistro() == null
                    || m.getFechaRegistro().isAfter(simTimeUtc)) continue;
            final Ruta r = sesion.getRutaPorMaleta(m.getIdMaleta());
            if (r == null
                    || r.getEstado() == EstadoRuta.REPLANIFICADA
                    || r.getSubrutas() == null
                    || r.getSubrutas().isEmpty()) {
                sinRuta++;
            }
        }
        return sinRuta;
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
                .map(this::mapearVueloInstancia)
                .toList();
    }

    public List<VueloInstanciaResponse> mapearVuelosInstanciaActivos(final List<VueloInstancia> vuelosInstancia,
                                                                       final LocalDateTime simTimeUtc,
                                                                       final int windowMinutes,
                                                                       final LocalDateTime fechaInicioUtc) {
        final LocalDateTime limiteVentana = simTimeUtc.plusMinutes(windowMinutes);
        final List<VueloInstanciaResponse> resultado = new ArrayList<>();
        for (final VueloInstancia vuelo : vuelosInstancia) {
            if (vuelo == null) {
                continue;
            }
            final boolean enProgreso = vuelo.getEstado() == EstadoVuelo.EN_PROGRESO
                    && vuelo.getFechaSalida() != null
                    && !vuelo.getFechaSalida().isBefore(fechaInicioUtc);
            final boolean proximoEnVentana = (vuelo.getEstado() == EstadoVuelo.PROGRAMADO
                    || vuelo.getEstado() == EstadoVuelo.CONFIRMADO)
                    && vuelo.getFechaSalida() != null
                    && !vuelo.getFechaSalida().isAfter(limiteVentana);
            if (!enProgreso && !proximoEnVentana) {
                continue;
            }
            resultado.add(mapearVueloInstancia(vuelo));
        }
        return resultado;
    }

    private VueloInstanciaResponse mapearVueloInstancia(final VueloInstancia vueloInstancia) {
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

    public record EntidadesVisibles(
            List<RutaSimulacionResponse> rutas,
            List<MaletaSimulacionResponse> maletas,
            List<PedidoSimulacionResponse> pedidos
    ) {}

    public EntidadesVisibles mapearEntidadesVisibles(
            final Collection<Ruta> rutas,
            final Collection<Maleta> maletas,
            final LocalDateTime simTimeUtc) {
        final Map<String, Ruta> rutaPorMaleta = new HashMap<>();
        for (final Ruta ruta : rutas) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                rutaPorMaleta.putIfAbsent(ruta.getIdMaleta(), ruta);
            }
        }
        final List<RutaSimulacionResponse> rutasMapeadas = new ArrayList<>();
        final Map<String, MaletaSimulacionResponse> maletasMapeadas = new LinkedHashMap<>();
        final Map<String, PedidoSimulacionResponse> pedidosMapeados = new LinkedHashMap<>();
        int entregadasIncluidas = 0;

        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getFechaRegistro() == null
                    || maleta.getFechaRegistro().isAfter(simTimeUtc)) {
                continue;
            }
            if (maleta.getEstado() == EstadoMaleta.ENTREGADA) {
                entregadasIncluidas++;
                if (entregadasIncluidas > 500) {
                    continue;
                }
            }
            maletasMapeadas.putIfAbsent(maleta.getIdMaleta(), mapearMaleta(maleta));
            if (maleta.getPedido() != null && maleta.getPedido().getIdPedido() != null) {
                pedidosMapeados.putIfAbsent(maleta.getPedido().getIdPedido(), mapearPedido(maleta.getPedido()));
            }
            final Ruta ruta = rutaPorMaleta.get(maleta.getIdMaleta());
            if (ruta != null) {
                rutasMapeadas.add(mapearRuta(ruta));
            }
        }

        final List<MaletaSimulacionResponse> maletasOrdenadas = maletasMapeadas.values().stream()
                .sorted(Comparator.comparing(MaletaSimulacionResponse::getFechaRegistro,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(MaletaSimulacionResponse::getIdMaleta))
                .toList();
        final List<PedidoSimulacionResponse> pedidosOrdenados = pedidosMapeados.values().stream()
                .sorted(Comparator.comparing(PedidoSimulacionResponse::getDate,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(PedidoSimulacionResponse::getId))
                .toList();

        return new EntidadesVisibles(rutasMapeadas, maletasOrdenadas, pedidosOrdenados);
    }

    private RutaSimulacionResponse mapearRuta(final Ruta ruta) {
        final List<RutaVueloResponse> vuelos = ruta.getSubrutas() == null
                ? List.of()
                : ruta.getSubrutas().stream().map(this::mapearRutaVuelo).toList();
        return RutaSimulacionResponse.builder()
                .withIdRuta(ruta.getIdRuta())
                .withIdMaleta(ruta.getIdMaleta())
                .withPlazoMaximoDias(ruta.getPlazoMaximoDias())
                .withDuracion(ruta.getDuracion())
                .withEstado(ruta.getEstado() != null ? ruta.getEstado().name() : null)
                .withVuelos(vuelos)
                .build();
    }

    private RutaVueloResponse mapearRutaVuelo(final VueloInstancia vuelo) {
        return RutaVueloResponse.builder()
                .withIdVueloInstancia(vuelo.getIdVueloInstancia())
                .withCodigo(vuelo.getCodigo())
                .withFechaSalida(formatDateTime(vuelo.getFechaSalida()))
                .withFechaLlegada(formatDateTime(vuelo.getFechaLlegada()))
                .withAeropuertoOrigen(vuelo.getAeropuertoOrigen() != null
                        ? vuelo.getAeropuertoOrigen().getIdAeropuerto() : null)
                .withAeropuertoDestino(vuelo.getAeropuertoDestino() != null
                        ? vuelo.getAeropuertoDestino().getIdAeropuerto() : null)
                .build();
    }

    private MaletaSimulacionResponse mapearMaleta(final Maleta maleta) {
        return MaletaSimulacionResponse.builder()
                .withIdMaleta(maleta.getIdMaleta())
                .withIdPedido(maleta.getPedido() != null ? maleta.getPedido().getIdPedido() : null)
                .withFechaRegistro(formatDateTime(maleta.getFechaRegistro()))
                .withFechaLlegada(formatDateTime(maleta.getFechaLlegada()))
                .withEstado(maleta.getEstado() != null ? maleta.getEstado().name() : null)
                .withUbicacionActual(resolveUbicacionActual(maleta))
                .build();
    }

    private PedidoSimulacionResponse mapearPedido(final Pedido pedido) {
        final String fechaRegistro = formatDateTime(pedido.getFechaRegistro());
        final String date = fechaRegistro != null ? fechaRegistro.substring(0, 10) : "";
        final String time = fechaRegistro != null && fechaRegistro.length() >= 16
                ? fechaRegistro.substring(11, 16) : "";
        return PedidoSimulacionResponse.builder()
                .withId(pedido.getIdPedido())
                .withClientId("")
                .withOrigin(pedido.getAeropuertoOrigen() != null
                        ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null)
                .withDest(pedido.getAeropuertoDestino() != null
                        ? pedido.getAeropuertoDestino().getIdAeropuerto() : null)
                .withBags(pedido.getCantidadMaletas())
                .withDate(date)
                .withTime(time)
                .withStatus(pedido.getEstado() != null ? pedido.getEstado().name() : null)
                .build();
    }

    private String resolveUbicacionActual(final Maleta maleta) {
        return maleta.getAeropuertoActual();
    }

    private String formatDateTime(final LocalDateTime value) {
        return value != null ? value.format(ISO_DATE_TIME) : null;
    }

    public List<EstadoMaletaDTO> mapearEstadosMaletas(final Collection<Maleta> maletas,
                                                       final LocalDateTime simTimeUtc) {
        final List<EstadoMaletaDTO> estados = new ArrayList<>();
        for (final Maleta m : maletas) {
            if (m == null || m.getFechaRegistro() == null
                    || m.getFechaRegistro().isAfter(simTimeUtc)) continue;
            estados.add(EstadoMaletaDTO.builder()
                    .withId(m.getIdMaleta())
                    .withE(m.getEstado() != null ? m.getEstado().ordinal() : 0)
                    .build());
        }
        return estados;
    }

    public List<EstadoRutaDTO> mapearEstadosRutas(final Collection<Ruta> rutas,
                                                   final LocalDateTime simTimeUtc,
                                                   final Map<String, Maleta> maletasPorId) {
        final List<EstadoRutaDTO> estados = new ArrayList<>();
        for (final Ruta r : rutas) {
            if (r == null) continue;
            final Maleta m = maletasPorId.get(r.getIdMaleta());
            if (m == null || m.getFechaRegistro() == null
                    || m.getFechaRegistro().isAfter(simTimeUtc)) continue;
            estados.add(EstadoRutaDTO.builder()
                    .withId(r.getIdRuta())
                    .withE(r.getEstado() != null ? r.getEstado().ordinal() : 0)
                    .withIdMaleta(r.getIdMaleta())
                    .build());
        }
        return estados;
    }

    public List<EstadoVueloDTO> mapearEstadosVuelos(final List<VueloInstancia> vuelos,
                                                       final LocalDateTime simTimeUtc) {
        final LocalDateTime limiteInferior = simTimeUtc.minusDays(1);
        final LocalDateTime limiteSuperior = simTimeUtc.plusDays(2);
        final List<EstadoVueloDTO> estados = new ArrayList<>();
        for (final VueloInstancia v : vuelos) {
            if (v == null) continue;
            if (v.getFechaSalida() != null
                    && (v.getFechaSalida().isBefore(limiteInferior)
                        || v.getFechaSalida().isAfter(limiteSuperior))) continue;
            estados.add(EstadoVueloDTO.builder()
                    .withId(v.getIdVueloInstancia())
                    .withE(v.getEstado() != null ? v.getEstado().ordinal() : 0)
                    .withCap(v.getCapacidadDisponible())
                    .build());
        }
        return estados;
    }

    public List<OcupacionAeropuertoDTO> mapearOcupacionAeropuertos(final List<Aeropuerto> aeropuertos) {
        final List<OcupacionAeropuertoDTO> ocupacion = new ArrayList<>();
        for (final Aeropuerto a : aeropuertos) {
            if (a == null) continue;
            ocupacion.add(OcupacionAeropuertoDTO.builder()
                    .withId(a.getIdAeropuerto())
                    .withOcc(a.getMaletasActuales())
                    .build());
        }
        return ocupacion;
    }

}
