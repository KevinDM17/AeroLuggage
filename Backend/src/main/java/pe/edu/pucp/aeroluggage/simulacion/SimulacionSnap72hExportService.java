package pe.edu.pucp.aeroluggage.simulacion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SimulacionSnap72hExportService {

    private static final DateTimeFormatter FORMATO_FECHA_CARPETA = DateTimeFormatter.ofPattern("dd_MM_yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_ARCHIVO = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final Path BASE_PATH = Path.of("documentos", "Resultados", "Snapshots");
    private static final String SEPARADOR = ";";

    private record SnapData(
            String fechaCarpeta,
            String fechaArchivo,
            List<String> vuelosCsv,
            List<String> pedidosCsv,
            List<String> maletasCsv,
            List<String> aeropuertosCsv,
            List<String> rutasCsv,
            List<String> infoGeneralTxt
    ) {
    }

    public void generarSnapshot72h(final SimulacionSesion sesion) {
        final SnapData data = sesion.withEventosLiveReadLock(() -> capturarDatos(sesion));
        escribirCSVs(data);
    }

    private SnapData capturarDatos(final SimulacionSesion sesion) {
        final LocalDateTime simTime = sesion.getCurrentSimTimeUtc().get();
        final String fechaCarpeta = simTime.format(FORMATO_FECHA_CARPETA);
        final String fechaArchivo = simTime.format(FORMATO_FECHA_ARCHIVO);

        final Map<String, VueloInstancia> vueloIndex = buildVueloIndex(sesion);

        final Map<String, String> maletaUbicacion = new HashMap<>();
        final Map<String, List<Maleta>> vueloMaletas = new HashMap<>();
        final Map<String, Set<Pedido>> vueloPedidos = new HashMap<>();
        final Map<String, List<Maleta>> aeropuertoMaletas = new HashMap<>();
        final Map<String, Set<Pedido>> aeropuertoPedidos = new HashMap<>();
        final Map<String, List<Maleta>> pedidoMaletas = new HashMap<>();
        final List<Maleta> maletasVisibles = new ArrayList<>();

        for (final Maleta m : sesion.getMaletasCalientes()) {
            if (m == null || m.getFechaRegistro() == null
                    || m.getFechaRegistro().isAfter(simTime)) {
                continue;
            }
            final EstadoMaleta estado = m.getEstado();
            if (estado == EstadoMaleta.ENTREGADA) {
                continue;
            }
            maletasVisibles.add(m);

            final String ubicacion = determinarUbicacion(m, estado, sesion, vueloIndex);
            maletaUbicacion.put(m.getIdMaleta(), ubicacion);

            if (estado == EstadoMaleta.EN_ALMACEN) {
                final String codigoAeropuerto = m.getAeropuertoActual();
                final String claveAeropuerto = codigoAeropuerto != null ? codigoAeropuerto : "";
                aeropuertoMaletas.computeIfAbsent(claveAeropuerto, k -> new ArrayList<>()).add(m);
                if (m.getPedido() != null) {
                    aeropuertoPedidos.computeIfAbsent(claveAeropuerto, k -> new HashSet<>()).add(m.getPedido());
                }
            }

            if (m.getPedido() != null && m.getPedido().getIdPedido() != null) {
                pedidoMaletas.computeIfAbsent(m.getPedido().getIdPedido(), k -> new ArrayList<>()).add(m);
            }
        }

        for (final Maleta m : maletasVisibles) {
            final Ruta r = sesion.getRutaPorMaleta(m.getIdMaleta());
            if (r == null || r.getSubrutaIds() == null || r.getSubrutaIds().isEmpty()) {
                continue;
            }
            for (final String idVuelo : r.getSubrutaIds()) {
                if (idVuelo == null) {
                    continue;
                }
                vueloMaletas.computeIfAbsent(idVuelo, k -> new ArrayList<>()).add(m);
                if (m.getPedido() != null) {
                    vueloPedidos.computeIfAbsent(idVuelo, k -> new HashSet<>()).add(m.getPedido());
                }
            }
        }

        final List<String> vuelosCsv = construirVuelosCsv(vueloIndex, vueloMaletas, vueloPedidos);
        final List<String> pedidosCsv = construirPedidosCsv(sesion, pedidoMaletas);
        final List<String> maletasCsv = construirMaletasCsv(sesion, maletasVisibles, maletaUbicacion);
        final List<String> aeropuertosCsv = construirAeropuertosCsv(sesion, aeropuertoMaletas, aeropuertoPedidos);
        final List<String> rutasCsv = construirRutasCsv(sesion, maletasVisibles, vueloIndex);
        final List<String> infoGeneralTxt = construirInfoGeneralTxt(sesion, maletasVisibles, vueloIndex, vueloMaletas,
                simTime);

        return new SnapData(fechaCarpeta, fechaArchivo, vuelosCsv, pedidosCsv, maletasCsv, aeropuertosCsv, rutasCsv,
                infoGeneralTxt);
    }

    private Map<String, VueloInstancia> buildVueloIndex(final SimulacionSesion sesion) {
        final Map<String, VueloInstancia> index = new HashMap<>();
        for (final VueloInstancia v : sesion.getVuelosInstancia()) {
            if (v != null && v.getIdVueloInstancia() != null) {
                index.put(v.getIdVueloInstancia(), v);
            }
        }
        return index;
    }

    private String determinarUbicacion(final Maleta m, final EstadoMaleta estado,
                                       final SimulacionSesion sesion,
                                       final Map<String, VueloInstancia> vueloIndex) {
        if (estado == EstadoMaleta.EN_ALMACEN) {
            final String codigo = m.getAeropuertoActual();
            return codigo != null ? codigo : "";
        }
        if (estado == EstadoMaleta.EN_TRANSITO || estado == EstadoMaleta.REPLANIFICANDO) {
            final Ruta r = sesion.getRutaPorMaleta(m.getIdMaleta());
            if (r != null && r.getSubrutaIds() != null) {
                for (final String idVuelo : r.getSubrutaIds()) {
                    final VueloInstancia v = vueloIndex.get(idVuelo);
                    if (v != null && v.getEstado() == EstadoVuelo.EN_PROGRESO) {
                        return v.getCodigo() != null ? v.getCodigo() : idVuelo;
                    }
                }
            }
        }
        return "";
    }

    private List<String> construirVuelosCsv(final Map<String, VueloInstancia> vueloIndex,
                                            final Map<String, List<Maleta>> vueloMaletas,
                                            final Map<String, Set<Pedido>> vueloPedidos) {
        final List<String> lineas = new ArrayList<>();
        lineas.add("codigo,origen,fecha_hora_salida,destino,fecha_hora_llegada,estado,ocupacion,capacidad_maxima,ids_pedidos,ids_maletas");
        for (final VueloInstancia v : vueloIndex.values()) {
            final EstadoVuelo estado = v.getEstado();
            if (estado != EstadoVuelo.CONFIRMADO && estado != EstadoVuelo.EN_PROGRESO) {
                continue;
            }
            final String origen = v.getAeropuertoOrigen() != null
                    ? v.getAeropuertoOrigen().getIdAeropuerto() : "";
            final String fechaSalida = v.getFechaSalida() != null
                    ? v.getFechaSalida().format(FORMATO_FECHA_HORA) : "";
            final String destino = v.getAeropuertoDestino() != null
                    ? v.getAeropuertoDestino().getIdAeropuerto() : "";
            final String fechaLlegada = v.getFechaLlegada() != null
                    ? v.getFechaLlegada().format(FORMATO_FECHA_HORA) : "";
            final List<Maleta> maletas = vueloMaletas.getOrDefault(v.getIdVueloInstancia(), List.of());
            final Set<Pedido> pedidos = vueloPedidos.getOrDefault(v.getIdVueloInstancia(), Set.of());
            final String idsMaletas = maletas.stream()
                    .map(Maleta::getIdMaleta)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(SEPARADOR));
            final String idsPedidos = pedidos.stream()
                    .map(Pedido::getIdPedido)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(SEPARADOR));
            lineas.add(String.format("%s,%s,%s,%s,%s,%s,%d,%d,%s,%s",
                    v.getIdVueloInstancia(), origen, fechaSalida, destino, fechaLlegada,
                    estado.name(), maletas.size(), v.getCapacidadMaxima(), idsPedidos, idsMaletas));
        }
        return lineas;
    }

    private List<String> construirPedidosCsv(final SimulacionSesion sesion,
                                             final Map<String, List<Maleta>> pedidoMaletas) {
        final List<String> lineas = new ArrayList<>();
        lineas.add("id,origen,destino,fecha_registro,fecha_plazo,estado,cantidad_maletas,ids_maletas,"
                + "hora_registro_local");
        final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (final Pedido p : sesion.getPedidosCalientes()) {
            if (p == null) {
                continue;
            }
            final EstadoPedido estado = p.getEstado();
            if (estado != EstadoPedido.REGISTRADO && estado != EstadoPedido.EN_PROCESO) {
                continue;
            }
            final String origen = p.getAeropuertoOrigen() != null
                    ? p.getAeropuertoOrigen().getIdAeropuerto() : "";
            final String destino = p.getAeropuertoDestino() != null
                    ? p.getAeropuertoDestino().getIdAeropuerto() : "";
            final String fechaRegistro = p.getFechaRegistro() != null
                    ? p.getFechaRegistro().format(FORMATO_FECHA_HORA) : "";
            final String fechaPlazo = p.getFechaHoraPlazo() != null
                    ? p.getFechaHoraPlazo().format(FORMATO_FECHA_HORA) : "";
            final List<Maleta> maletas = pedidoMaletas.getOrDefault(p.getIdPedido(), List.of());
            final String idsMaletas = maletas.stream()
                    .map(Maleta::getIdMaleta)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(SEPARADOR));
            String horaRegistroLocal = "";
            if (p.getFechaRegistro() != null && p.getAeropuertoOrigen() != null) {
                final int husoGMT = p.getAeropuertoOrigen().getHusoGMT();
                final LocalDateTime horaLocal = p.getFechaRegistro().plusHours(husoGMT);
                horaRegistroLocal = horaLocal.format(formatoHora);
            }
            lineas.add(String.format("%s,%s,%s,%s,%s,%s,%d,%s,%s",
                    p.getIdPedido(), origen, destino, fechaRegistro, fechaPlazo,
                    estado.name(), p.getCantidadMaletas(), idsMaletas, horaRegistroLocal));
        }
        return lineas;
    }

    private List<String> construirMaletasCsv(final SimulacionSesion sesion,
                                             final List<Maleta> maletasVisibles,
                                             final Map<String, String> maletaUbicacion) {
        final List<String> lineas = new ArrayList<>();
        lineas.add("id,id_pedido,estado,ubicacion,fecha_registro,hora_registro,id_ruta,hora_registro_local");
        final DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (final Maleta m : maletasVisibles) {
            final String idPedido = m.getPedido() != null ? m.getPedido().getIdPedido() : "";
            final String estado = m.getEstado() != null ? m.getEstado().name() : "";
            final String ubicacion = maletaUbicacion.getOrDefault(m.getIdMaleta(), "");
            final String fechaRegistro = m.getFechaRegistro() != null
                    ? m.getFechaRegistro().format(FORMATO_FECHA_HORA) : "";
            final String horaRegistro = m.getFechaRegistro() != null
                    ? m.getFechaRegistro().format(formatoHora) : "";
            final Ruta ruta = sesion.getRutaPorMaleta(m.getIdMaleta());
            final String idRuta = ruta != null ? ruta.getIdRuta() : "";
            String horaRegistroLocal = "";
            if (m.getFechaRegistro() != null && m.getPedido() != null
                    && m.getPedido().getAeropuertoOrigen() != null) {
                final int husoGMT = m.getPedido().getAeropuertoOrigen().getHusoGMT();
                final LocalDateTime horaLocal = m.getFechaRegistro().plusHours(husoGMT);
                horaRegistroLocal = horaLocal.format(formatoHora);
            }
            lineas.add(String.format("%s,%s,%s,%s,%s,%s,%s,%s",
                    m.getIdMaleta(), idPedido, estado, ubicacion, fechaRegistro, horaRegistro, idRuta,
                    horaRegistroLocal));
        }
        return lineas;
    }

    private List<String> construirAeropuertosCsv(final SimulacionSesion sesion,
                                                  final Map<String, List<Maleta>> aeropuertoMaletas,
                                                  final Map<String, Set<Pedido>> aeropuertoPedidos) {
        final List<String> lineas = new ArrayList<>();
        lineas.add("codigo,ciudad,ocupacion,capacidad_maxima,ids_pedidos,ids_maletas");
        if (sesion.getAeropuertos() == null) {
            return lineas;
        }
        for (final Aeropuerto a : sesion.getAeropuertos()) {
            if (a == null || a.getIdAeropuerto() == null) {
                continue;
            }
            final String ciudad = a.getCiudad() != null ? a.getCiudad().getNombre() : "";
            final List<Maleta> maletas = aeropuertoMaletas.getOrDefault(a.getIdAeropuerto(), List.of());
            final Set<Pedido> pedidos = aeropuertoPedidos.getOrDefault(a.getIdAeropuerto(), Set.of());
            final String idsMaletas = maletas.stream()
                    .map(Maleta::getIdMaleta)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(SEPARADOR));
            final String idsPedidos = pedidos.stream()
                    .map(Pedido::getIdPedido)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(SEPARADOR));
            lineas.add(String.format("%s,%s,%d,%d,%s,%s",
                    a.getIdAeropuerto(), ciudad, a.getMaletasActuales(),
                    a.getCapacidadAlmacen(), idsPedidos, idsMaletas));
        }
        return lineas;
    }

    private List<String> construirRutasCsv(final SimulacionSesion sesion,
                                           final List<Maleta> maletasVisibles,
                                           final Map<String, VueloInstancia> vueloIndex) {
        final List<String> lineas = new ArrayList<>();
        lineas.add("codigo,id_maleta,ids_vuelos,hora_salida_primer_vuelo,hora_llegada_ultimo_vuelo,aeropuerto_inicio,aeropuerto_fin,estado");
        for (final Maleta m : maletasVisibles) {
            final Ruta r = sesion.getRutaPorMaleta(m.getIdMaleta());
            if (r == null) {
                continue;
            }
            final String idsVuelos = r.getSubrutaIds() != null
                    ? String.join(SEPARADOR, r.getSubrutaIds()) : "";
            final String estado = r.getEstado() != null ? r.getEstado().name() : "";
            String horaSalidaPrimero = "";
            String horaLlegadaUltimo = "";
            String aeropuertoInicio = "";
            String aeropuertoFin = "";
            if (r.getSubrutaIds() != null && !r.getSubrutaIds().isEmpty()) {
                final VueloInstancia primero = vueloIndex.get(r.getSubrutaIds().get(0));
                final VueloInstancia ultimo = vueloIndex.get(r.getSubrutaIds().get(r.getSubrutaIds().size() - 1));
                horaSalidaPrimero = primero != null && primero.getFechaSalida() != null
                        ? primero.getFechaSalida().format(FORMATO_FECHA_HORA) : "";
                horaLlegadaUltimo = ultimo != null && ultimo.getFechaLlegada() != null
                        ? ultimo.getFechaLlegada().format(FORMATO_FECHA_HORA) : "";
                aeropuertoInicio = primero != null && primero.getAeropuertoOrigen() != null
                        ? primero.getAeropuertoOrigen().getIdAeropuerto() : "";
                aeropuertoFin = ultimo != null && ultimo.getAeropuertoDestino() != null
                        ? ultimo.getAeropuertoDestino().getIdAeropuerto() : "";
            }
            lineas.add(String.format("%s,%s,%s,%s,%s,%s,%s,%s",
                    r.getIdRuta(), r.getIdMaleta(), idsVuelos,
                    horaSalidaPrimero, horaLlegadaUltimo, aeropuertoInicio, aeropuertoFin, estado));
        }
        return lineas;
    }

    private List<String> construirInfoGeneralTxt(final SimulacionSesion sesion,
                                                   final List<Maleta> maletasVisibles,
                                                   final Map<String, VueloInstancia> vueloIndex,
                                                   final Map<String, List<Maleta>> vueloMaletas,
                                                   final LocalDateTime simTime) {
        final List<String> lineas = new ArrayList<>();
        lineas.add("=== SNAPSHOT A 72h SIMULADAS ===");
        lineas.add("Fecha y hora simulada: " + simTime.format(FORMATO_FECHA_HORA));

        int maletasEnTransito = 0;
        int maletasEnAlmacen = 0;
        for (final Maleta m : maletasVisibles) {
            final EstadoMaleta estado = m.getEstado();
            if (estado == EstadoMaleta.EN_TRANSITO || estado == EstadoMaleta.REPLANIFICANDO) {
                maletasEnTransito++;
            } else if (estado == EstadoMaleta.EN_ALMACEN) {
                maletasEnAlmacen++;
            }
        }
        lineas.add("Maletas en transito: " + maletasEnTransito);
        lineas.add("Maletas en almacenes: " + maletasEnAlmacen);

        int vuelosEnProgreso = 0;
        int ocupacionTotalVuelos = 0;
        int capacidadTotalVuelos = 0;
        for (final VueloInstancia v : vueloIndex.values()) {
            if (v.getEstado() == EstadoVuelo.EN_PROGRESO) {
                vuelosEnProgreso++;
                final int ocupadas = vueloMaletas.getOrDefault(v.getIdVueloInstancia(), List.of()).size();
                ocupacionTotalVuelos += ocupadas;
                capacidadTotalVuelos += Math.max(0, v.getCapacidadMaxima());
            }
        }
        lineas.add("Vuelos en progreso: " + vuelosEnProgreso);
        final String ocupacionVuelosPct = capacidadTotalVuelos > 0
                ? String.format(Locale.ROOT, "%.2f%%", ocupacionTotalVuelos * 100.0 / capacidadTotalVuelos)
                : "0.00%";
        lineas.add("Ocupacion vuelos: " + ocupacionTotalVuelos + " / " + capacidadTotalVuelos
                + " (" + ocupacionVuelosPct + ")");

        int ocupacionTotalAeropuertos = 0;
        int capacidadTotalAeropuertos = 0;
        if (sesion.getAeropuertos() != null) {
            for (final Aeropuerto a : sesion.getAeropuertos()) {
                if (a == null) {
                    continue;
                }
                ocupacionTotalAeropuertos += Math.max(0, a.getMaletasActuales());
                capacidadTotalAeropuertos += Math.max(0, a.getCapacidadAlmacen());
            }
        }
        final String ocupacionAeropuertosPct = capacidadTotalAeropuertos > 0
                ? String.format(Locale.ROOT, "%.2f%%",
                        ocupacionTotalAeropuertos * 100.0 / capacidadTotalAeropuertos)
                : "0.00%";
        lineas.add("Ocupacion aeropuertos: " + ocupacionTotalAeropuertos + " / "
                + capacidadTotalAeropuertos + " (" + ocupacionAeropuertosPct + ")");

        return lineas;
    }

    private void escribirCSVs(final SnapData data) {
        final Path carpeta = BASE_PATH.resolve(data.fechaCarpeta());
        try {
            Files.createDirectories(carpeta);
            Files.write(carpeta.resolve("vuelos_" + data.fechaArchivo() + ".csv"),
                    data.vuelosCsv(), StandardCharsets.UTF_8);
            Files.write(carpeta.resolve("pedidos_" + data.fechaArchivo() + ".csv"),
                    data.pedidosCsv(), StandardCharsets.UTF_8);
            Files.write(carpeta.resolve("maletas_" + data.fechaArchivo() + ".csv"),
                    data.maletasCsv(), StandardCharsets.UTF_8);
            Files.write(carpeta.resolve("aeropuertos_" + data.fechaArchivo() + ".csv"),
                    data.aeropuertosCsv(), StandardCharsets.UTF_8);
            Files.write(carpeta.resolve("rutas_" + data.fechaArchivo() + ".csv"),
                    data.rutasCsv(), StandardCharsets.UTF_8);
            Files.write(carpeta.resolve("info_general.txt"),
                    data.infoGeneralTxt(), StandardCharsets.UTF_8);
            log.info("[AeroLuggage/Snapshot72h] - Snapshot generado: {} ({} vuelos, {} pedidos, {} maletas, {} aeropuertos, {} rutas)",
                    carpeta.toAbsolutePath(),
                    data.vuelosCsv().size() - 1,
                    data.pedidosCsv().size() - 1,
                    data.maletasCsv().size() - 1,
                    data.aeropuertosCsv().size() - 1,
                    data.rutasCsv().size() - 1);
        } catch (final IOException exception) {
            log.error("[AeroLuggage/Snapshot72h] - Error al escribir CSV: {}", exception.getMessage());
        }
    }
}
