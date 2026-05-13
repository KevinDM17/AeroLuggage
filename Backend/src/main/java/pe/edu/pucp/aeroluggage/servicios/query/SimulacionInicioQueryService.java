package pe.edu.pucp.aeroluggage.servicios.query;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.ga.GA;
import pe.edu.pucp.aeroluggage.algoritmo.ga.ParametrosGA;
import pe.edu.pucp.aeroluggage.cargador.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.cargador.GeneradorVueloInstancias;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.MaletaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.PedidoSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.RutaSimulacionResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.RutaVueloResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.SimulacionInicioResponse;
import pe.edu.pucp.aeroluggage.dto.rest.simulacion.SimulacionVentanaResponse;
import pe.edu.pucp.aeroluggage.simulacion.DataTransferObject.SimulacionEstadoDTO;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSesion;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionSnapshotService;
import pe.edu.pucp.aeroluggage.simulacion.SimulacionVentana;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SimulacionInicioQueryService {

    private static final String AEROPUERTO_FILE_KEY = "AEROPUERTO_FILE_PATH";
    private static final String PLANES_VUELO_FILE_KEY = "PLANES_VUELO_FILE_PATH";
    private static final String ENVIOS_FOLDER_KEY = "ENVIOS_FOLDER_PATH";
    private static final String DEFAULT_AEROPUERTOS_PATH = "datos/Aeropuertos.txt";
    private static final String DEFAULT_PLANES_VUELO_PATH = "datos/planes_vuelo.txt";
    private static final String DEFAULT_ENVIOS_PATH = "datos/Envios";
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final long MINUTOS_CONEXION = 10L;
    private static final long TIEMPO_RECOJO = 10L;

    private final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
    private final SimulacionSnapshotService snapshotService;

    public SimulacionInicioQueryService(final SimulacionSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public SimulacionInicioResponse construirRespuestaInicio(final SimulacionEstadoDTO estado,
                                                             final SimulacionSesion sesion) {
        ensureSnapshotLoaded(sesion);
        snapshotService.recalcularEstadoSesion(sesion);

        final SimulacionVentana currentWindow = sesion.getCurrentWindow().get();
        final LocalDateTime visibleUntilUtc = currentWindow.getEndUtc();
        final List<Pedido> pedidosVisibles = sesion.getPedidos().stream()
                .filter(pedido -> pedido.getFechaRegistro() != null && !pedido.getFechaRegistro().isAfter(visibleUntilUtc))
                .sorted(Comparator.comparing(Pedido::getFechaRegistro).thenComparing(Pedido::getIdPedido))
                .toList();
        final List<Maleta> maletasVisibles = sesion.getMaletas().stream()
                .filter(maleta -> maleta.getFechaRegistro() != null && !maleta.getFechaRegistro().isAfter(visibleUntilUtc))
                .sorted(Comparator.comparing(Maleta::getFechaRegistro).thenComparing(Maleta::getIdMaleta))
                .toList();
        final Set<String> maletaIdsVisibles = maletasVisibles.stream()
                .map(Maleta::getIdMaleta)
                .collect(Collectors.toSet());
        final List<Ruta> rutasVisibles = sesion.getRutas().stream()
                .filter(ruta -> maletaIdsVisibles.contains(ruta.getIdMaleta()))
                .sorted(Comparator.comparing(Ruta::getIdRuta))
                .toList();

        return SimulacionInicioResponse.builder()
                .withSessionId(estado.getSessionId())
                .withEstado(estado.getEstado())
                .withMensaje(estado.getMensaje())
                .withFechaInicio(sesion.getFechaInicio().toString())
                .withTotalDias(sesion.getTotalDias())
                .withCurrentSimTimeUtc(formatDateTime(sesion.getCurrentSimTimeUtc().get()))
                .withDuracionDiaSimuladoMs(sesion.getDuracionDiaSimuladoMs())
                .withWindowSizeMinutes(sesion.getWindowSizeMinutes())
                .withStateVersion(sesion.getStateVersion().get())
                .withCurrentWindow(mapearVentana(currentWindow))
                .withNextWindow(mapearVentana(sesion.buildNextWindow()))
                .withAeropuertos(snapshotService.mapearAeropuertos(sesion.getAeropuertos()))
                .withVuelosInstancia(snapshotService.mapearVuelosInstancia(sesion.getVuelosInstancia()))
                .withPedidos(pedidosVisibles.stream().map(this::mapearPedido).toList())
                .withMaletas(maletasVisibles.stream().map(this::mapearMaleta).toList())
                .withRutas(rutasVisibles.stream().map(this::mapearRuta).toList())
                .build();
    }

    private void ensureSnapshotLoaded(final SimulacionSesion sesion) {
        if (sesion.hasSnapshotData()) {
            return;
        }
        synchronized (sesion) {
            if (sesion.hasSnapshotData()) {
                return;
            }

            final Path aeropuertosPath = resolveResourcePath(AEROPUERTO_FILE_KEY, DEFAULT_AEROPUERTOS_PATH);
            final Path vuelosPath = resolveResourcePath(PLANES_VUELO_FILE_KEY, DEFAULT_PLANES_VUELO_PATH);
            final Path enviosPath = resolveEnviosPath();

            final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(aeropuertosPath);
            final Map<String, Aeropuerto> indiceAeropuertos = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
            final LocalDate fechaInicio = sesion.getFechaInicio();
            final LocalDate fechaFin = fechaInicio.plusDays(Math.max(0L, sesion.getTotalDias() - 1L));
            final ArrayList<VueloProgramado> vuelosProgramados = CargadorDatosPrueba.cargarVuelosProgramados(
                    vuelosPath,
                    indiceAeropuertos,
                    fechaInicio,
                    sesion.getTotalDias()
            );
            final ArrayList<VueloInstancia> vuelosInstancia = new ArrayList<>(GeneradorVueloInstancias.generar(
                    vuelosProgramados,
                    fechaInicio,
                    fechaFin
            ));
            final DatosEntrada datosEntrada = CargadorDatosPrueba.cargarEnviosEnRango(
                    enviosPath,
                    indiceAeropuertos,
                    fechaInicio,
                    fechaFin
            );
            final ArrayList<Pedido> pedidos = new ArrayList<>(datosEntrada.getPedidos());
            pedidos.sort(Comparator.comparing(Pedido::getFechaRegistro).thenComparing(Pedido::getIdPedido));
            final ArrayList<Maleta> maletas = new ArrayList<>(datosEntrada.getMaletas());
            maletas.sort(Comparator.comparing(Maleta::getFechaRegistro).thenComparing(Maleta::getIdMaleta));

            final ArrayList<Ruta> rutas = ejecutarPlanificadorPrimeraVentana(
                    sesion,
                    aeropuertos,
                    vuelosProgramados,
                    vuelosInstancia,
                    maletas
            );

            sesion.setSnapshotData(aeropuertos, vuelosProgramados, vuelosInstancia, pedidos, maletas, rutas);
            snapshotService.recalcularEstadoSesion(sesion);
            sesion.getStateVersion().incrementAndGet();
        }
    }

    private ArrayList<Ruta> ejecutarPlanificadorPrimeraVentana(final SimulacionSesion sesion,
                                                               final ArrayList<Aeropuerto> aeropuertos,
                                                               final ArrayList<VueloProgramado> vuelosProgramados,
                                                               final ArrayList<VueloInstancia> vuelosInstancia,
                                                               final ArrayList<Maleta> todasLasMaletas) {
        final LocalDateTime limiteVentana = sesion.getCurrentWindow().get().getEndUtc();
        final ArrayList<Maleta> maletasElegibles = todasLasMaletas.stream()
                .filter(maleta -> maleta.getFechaRegistro() != null && !maleta.getFechaRegistro().isAfter(limiteVentana))
                .collect(Collectors.toCollection(ArrayList::new));
        if (maletasElegibles.isEmpty()) {
            return new ArrayList<>();
        }

        final InstanciaProblema instancia = new InstanciaProblema(
                "SIM-" + sesion.getSessionId(),
                maletasElegibles,
                vuelosProgramados,
                new ArrayList<>(vuelosInstancia),
                aeropuertos
        );
        instancia.setMinutosConexion(MINUTOS_CONEXION);
        instancia.setTiempoRecojo(TIEMPO_RECOJO);
        instancia.construirGrafo();

        final GA ga = new GA(buildInteractiveGAParams(maletasElegibles.size()));
        ga.ejecutar(instancia);
        final Solucion mejorSolucion = ga.getMejorSolucion();
        if (mejorSolucion == null || mejorSolucion.getSolucion() == null) {
            return new ArrayList<>();
        }

        final ArrayList<Ruta> rutas = new ArrayList<>(mejorSolucion.getSolucion());
        for (final Ruta ruta : rutas) {
            if (ruta == null) {
                continue;
            }
            if (ruta.getSubrutas() == null) {
                ruta.setSubrutas(new ArrayList<>());
            }
            if (ruta.getSubrutas().isEmpty()) {
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0D);
                continue;
            }
            ruta.calcularPlazo();
            if (ruta.getEstado() == null) {
                ruta.setEstado(EstadoRuta.PLANIFICADA);
            }
        }
        return rutas;
    }

    private ParametrosGA buildInteractiveGAParams(final int totalMaletas) {
        final ParametrosGA parametros = ParametrosGA.pordefecto();
        if (totalMaletas <= 40) {
            parametros.setTamanioPoblacion(36);
            parametros.setMaxGeneraciones(60);
            parametros.setMaxSinMejora(12);
        } else if (totalMaletas <= 150) {
            parametros.setTamanioPoblacion(24);
            parametros.setMaxGeneraciones(35);
            parametros.setMaxSinMejora(8);
        } else {
            parametros.setTamanioPoblacion(16);
            parametros.setMaxGeneraciones(20);
            parametros.setMaxSinMejora(5);
        }
        parametros.setElites(Math.min(4, parametros.getTamanioPoblacion()));
        parametros.setSemilla(42L);
        return parametros;
    }

    private Path resolveEnviosPath() {
        final String configured = dotenv.get(ENVIOS_FOLDER_KEY, DEFAULT_ENVIOS_PATH);
        return resolveAnyResourcePath(
                configured,
                configured.replace("envÃƒÂ­os", "Envios").replace("envÃ­os", "Envios").replace("envios", "Envios"),
                DEFAULT_ENVIOS_PATH
        );
    }

    private Path resolveResourcePath(final String envKey, final String defaultPath) {
        return resolveAnyResourcePath(dotenv.get(envKey, defaultPath), defaultPath);
    }

    private Path resolveAnyResourcePath(final String... candidates) {
        for (final String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            final ClassPathResource resource = new ClassPathResource(candidate);
            if (!resource.exists()) {
                continue;
            }
            try {
                return resource.getFile().toPath();
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo resolver el recurso: " + candidate, exception);
            }
        }
        throw new IllegalStateException("No se encontro ningun recurso valido para la simulacion");
    }

    private PedidoSimulacionResponse mapearPedido(final Pedido pedido) {
        final String[] dateTime = splitDateTime(pedido.getFechaRegistro());
        return PedidoSimulacionResponse.builder()
                .withId(pedido.getIdPedido())
                .withClientId("")
                .withOrigin(pedido.getAeropuertoOrigen() != null ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null)
                .withDest(pedido.getAeropuertoDestino() != null ? pedido.getAeropuertoDestino().getIdAeropuerto() : null)
                .withBags(pedido.getCantidadMaletas())
                .withDate(dateTime[0])
                .withTime(dateTime[1])
                .withStatus(pedido.getEstado() != null ? pedido.getEstado().name() : null)
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

    private RutaSimulacionResponse mapearRuta(final Ruta ruta) {
        return RutaSimulacionResponse.builder()
                .withIdRuta(ruta.getIdRuta())
                .withIdMaleta(ruta.getIdMaleta())
                .withPlazoMaximoDias(ruta.getPlazoMaximoDias())
                .withDuracion(ruta.getDuracion())
                .withEstado(ruta.getEstado() != null ? ruta.getEstado().name() : null)
                .withVuelos(ruta.getSubrutas().stream().map(this::mapearRutaVuelo).toList())
                .build();
    }

    private RutaVueloResponse mapearRutaVuelo(final VueloInstancia vuelo) {
        return RutaVueloResponse.builder()
                .withIdVueloInstancia(vuelo.getIdVueloInstancia())
                .withCodigo(vuelo.getCodigo())
                .withFechaSalida(formatDateTime(vuelo.getFechaSalida()))
                .withFechaLlegada(formatDateTime(vuelo.getFechaLlegada()))
                .withAeropuertoOrigen(vuelo.getAeropuertoOrigen() != null ? vuelo.getAeropuertoOrigen().getIdAeropuerto() : null)
                .withAeropuertoDestino(vuelo.getAeropuertoDestino() != null ? vuelo.getAeropuertoDestino().getIdAeropuerto() : null)
                .build();
    }

    private SimulacionVentanaResponse mapearVentana(final SimulacionVentana ventana) {
        if (ventana == null) {
            return null;
        }
        return SimulacionVentanaResponse.builder()
                .withWindowId(ventana.getWindowId())
                .withStartUtc(formatDateTime(ventana.getStartUtc()))
                .withEndUtc(formatDateTime(ventana.getEndUtc()))
                .withStatus(ventana.getStatus())
                .withGeneration(ventana.getGeneration())
                .build();
    }

    private String resolveUbicacionActual(final Maleta maleta) {
        if (maleta.getPedido() == null) {
            return null;
        }
        return switch (maleta.getEstado()) {
            case ENTREGADA -> maleta.getPedido().getAeropuertoDestino() != null
                    ? maleta.getPedido().getAeropuertoDestino().getIdAeropuerto() : null;
            case EN_TRANSITO -> null;
            default -> maleta.getPedido().getAeropuertoOrigen() != null
                    ? maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto() : null;
        };
    }

    private String formatDateTime(final LocalDateTime value) {
        return value != null ? value.format(ISO_DATE_TIME) : null;
    }

    private String[] splitDateTime(final LocalDateTime value) {
        if (value == null) {
            return new String[]{"", ""};
        }
        return new String[]{
                value.toLocalDate().toString(),
                value.toLocalTime().toString().substring(0, 5)
        };
    }
}
