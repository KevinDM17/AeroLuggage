package pe.edu.pucp.aeroluggage.simulacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ALNS;
import pe.edu.pucp.aeroluggage.algoritmo.alns.ParametrosALNS;
import pe.edu.pucp.aeroluggage.cargador.CargadorAeropuertos;
import pe.edu.pucp.aeroluggage.cargador.CargadorEnvios;
import pe.edu.pucp.aeroluggage.cargador.CargadorPlanesVuelo;
import pe.edu.pucp.aeroluggage.cargador.DatosEntrada;
import pe.edu.pucp.aeroluggage.servicios.GeneradorVuelosInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class SimulacionTemporalALNSRunner {

    private static final Path DOCS = Path.of("src", "main", "resources", "datos");
    private static final Path PARAMS_FILE = Path.of("experimental", "parametros", "test_params.txt");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter FORMATO_MOMENTO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private SimulacionTemporalALNSRunner() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void main(final String[] args) throws IOException {
        ejecutar();
    }

    public static void ejecutar() throws IOException {
        final Properties params = cargarParametros();
        if (!Boolean.parseBoolean(params.getProperty("ejecutar.alns", "true"))) {
            System.out.println("ALNS está deshabilitado en experimental/parametros/test_params.txt");
            return;
        }
        if (!Files.isDirectory(DOCS)) {
            System.out.printf("No se encontró la carpeta de datos: %s%n", DOCS.toAbsolutePath());
            return;
        }

        final LocalDate fechaInicio = LocalDate.parse(params.getProperty("simulacion.fecha.inicio"), FORMATO_FECHA);
        final LocalDate fechaFin = LocalDate.parse(params.getProperty("simulacion.fecha.fin"), FORMATO_FECHA);
        final int ventanaDias = intParam(params, "simulacion.ventana.dias", 3);
        final int kMinutos = intParam(params, "simulacion.programada.k", 180);
        final int saSegundos = intParam(params, "simulacion.programada.sa.segundos", 0);
        final boolean sleepHabilitado = Boolean.parseBoolean(params.getProperty("simulacion.programada.sleep.habilitado", "false"));

        final Path archivoAeropuertos = DOCS.resolve("Aeropuertos.txt");
        final Path archivoVuelos = DOCS.resolve("planes_vuelo.txt");
        final Path carpetaEnvios = DOCS.resolve("Envios");

        final ArrayList<Aeropuerto> aeropuertos = new ArrayList<>(CargadorAeropuertos.cargar(archivoAeropuertos));
        final Map<String, Aeropuerto> indiceAeropuertos = new HashMap<>();
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto != null && aeropuerto.getIdAeropuerto() != null) {
                indiceAeropuertos.put(aeropuerto.getIdAeropuerto(), aeropuerto);
            }
        }
        final int totalDias = (int) (fechaFin.toEpochDay() - fechaInicio.toEpochDay()) + 1 + ventanaDias;
        final ArrayList<VueloProgramado> todosVuelosProgramados = new ArrayList<>(
                CargadorPlanesVuelo.cargar(archivoVuelos, indiceAeropuertos));
        final ArrayList<VueloInstancia> todosVuelosInstancia = GeneradorVuelosInstancia.generar(
                new ArrayList<>(todosVuelosProgramados), fechaInicio, totalDias);

        final DatosEntrada datosEntrada = CargadorEnvios.cargarEnviosEnRango(
                carpetaEnvios, indiceAeropuertos, fechaInicio, fechaFin);
        final List<Maleta> todasLasMaletas = new ArrayList<>(datosEntrada.getMaletas());
        final List<Pedido> pedidosOrdenados = new ArrayList<>(datosEntrada.getPedidos());
        pedidosOrdenados.sort(Comparator.comparing(Pedido::getFechaRegistro,
                Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Pedido::getIdPedido));
        for (final Pedido pedido : pedidosOrdenados) {
            if (pedido.getFechaHoraPlazo() == null) {
                pedido.calcularFechaHoraPlazo();
            }
        }

        final ParametrosALNS parametrosALNS = construirParametrosALNS(params);
        final ResultadoConsola resultado = ejecutarSimulacion(
                parametrosALNS,
                clonarVuelosInstancia(todosVuelosInstancia),
                todosVuelosProgramados,
                aeropuertos,
                indiceAeropuertos,
                todasLasMaletas,
                pedidosOrdenados,
                ventanaDias,
                kMinutos,
                saSegundos,
                sleepHabilitado
        );
        imprimirResumenFinal(resultado);
    }

    private static ResultadoConsola ejecutarSimulacion(final ParametrosALNS parametros,
                                                       final ArrayList<VueloInstancia> copiaVuelos,
                                                       final ArrayList<VueloProgramado> todosVuelosProgramados,
                                                       final ArrayList<Aeropuerto> aeropuertos,
                                                       final Map<String, Aeropuerto> indiceAeropuertos,
                                                       final List<Maleta> todasLasMaletas,
                                                       final List<Pedido> pedidosOrdenados,
                                                       final int ventanaDias,
                                                       final int kMinutos,
                                                       final int saSegundos,
                                                       final boolean sleepHabilitado) {
        final List<Maleta> pendientes = new ArrayList<>();
        final List<Ruta> rutasComprometidas = new ArrayList<>();
        final Map<String, Integer> ocupacion = new HashMap<>();
        final Map<String, VueloInstancia> vuelosPorId = indexarVuelosInstancia(copiaVuelos);
        final Map<String, List<Maleta>> maletasPorPedido = agruparMaletasPorPedido(todasLasMaletas);
        final ResultadoConsola resultado = new ResultadoConsola();
        final LocalDateTime inicioSimulacion = pedidosOrdenados.isEmpty()
                ? LocalDate.now().atStartOfDay()
                : pedidosOrdenados.get(0).getFechaRegistro().toLocalDate().atStartOfDay();
        final LocalDateTime finSimulacionExclusivo = pedidosOrdenados.isEmpty()
                ? inicioSimulacion.plusMinutes(Math.max(1, kMinutos))
                : pedidosOrdenados.get(pedidosOrdenados.size() - 1).getFechaRegistro().toLocalDate().plusDays(1).atStartOfDay();
        int indicePedido = 0;

        System.out.printf("%n=== RUNNER ALNS [%s -> %s] ===%n",
                pedidosOrdenados.isEmpty() ? "-" : pedidosOrdenados.get(0).getFechaRegistro().toLocalDate(),
                pedidosOrdenados.isEmpty() ? "-" : pedidosOrdenados.get(pedidosOrdenados.size() - 1).getFechaRegistro().toLocalDate());
        System.out.printf("Ventana programada K: %d minuto(s)%n", kMinutos);
        System.out.printf("Sleep SA entre ventanas: %d segundo(s) [%s]%n",
                saSegundos, sleepHabilitado ? "habilitado" : "deshabilitado");

        for (LocalDateTime inicioVentana = inicioSimulacion;
             inicioVentana.isBefore(finSimulacionExclusivo);
             inicioVentana = inicioVentana.plusMinutes(Math.max(1, kMinutos))) {
            final LocalDateTime finVentana = min(inicioVentana.plusMinutes(Math.max(1, kMinutos)), finSimulacionExclusivo);
            final LocalDateTime currentTime = finVentana.minusSeconds(1);
            actualizarEstadosVuelos(copiaVuelos, currentTime);

            final List<Maleta> nuevas = new ArrayList<>();
            while (indicePedido < pedidosOrdenados.size()) {
                final Pedido pedido = pedidosOrdenados.get(indicePedido);
                if (pedido == null || pedido.getFechaRegistro() == null) {
                    indicePedido++;
                    continue;
                }
                if (pedido.getFechaRegistro().isBefore(inicioVentana)) {
                    indicePedido++;
                    continue;
                }
                if (!pedido.getFechaRegistro().isBefore(finVentana)) {
                    break;
                }
                nuevas.addAll(maletasPorPedido.getOrDefault(pedido.getIdPedido(), List.of()));
                indicePedido++;
            }
            for (final Maleta maleta : nuevas) {
                final String idAeropuerto = icaoOrigen(maleta);
                if (idAeropuerto != null) {
                    ocupacion.merge(idAeropuerto, 1, Integer::sum);
                }
            }
            pendientes.addAll(nuevas);
            if (pendientes.isEmpty() && nuevas.isEmpty()) {
                continue;
            }

            for (final Map.Entry<String, Integer> ocupacionEntry : ocupacion.entrySet()) {
                final Aeropuerto aeropuerto = indiceAeropuertos.get(ocupacionEntry.getKey());
                if (aeropuerto != null) {
                    aeropuerto.setMaletasActuales(Math.max(0, ocupacionEntry.getValue()));
                }
            }

            final LocalDateTime ventanaFin = currentTime.plusDays(ventanaDias - 1L).with(LocalTime.MAX);
            final ArrayList<VueloInstancia> vuelosVentana = new ArrayList<>();
            final Set<String> idsProgramadosVentana = new HashSet<>();
            for (final VueloInstancia vuelo : copiaVuelos) {
                if (vuelo.getFechaSalida() == null) {
                    continue;
                }
                if (!vuelo.getFechaSalida().isBefore(currentTime) && !vuelo.getFechaSalida().isAfter(ventanaFin)) {
                    vuelosVentana.add(vuelo);
                    if (vuelo.getVueloProgramado() != null) {
                        idsProgramadosVentana.add(vuelo.getVueloProgramado().getIdVueloProgramado());
                    }
                }
            }
            final ArrayList<VueloProgramado> programadosVentana = new ArrayList<>();
            for (final VueloProgramado vueloProgramado : todosVuelosProgramados) {
                if (idsProgramadosVentana.contains(vueloProgramado.getIdVueloProgramado())) {
                    programadosVentana.add(vueloProgramado);
                }
            }

            final InstanciaProblema instancia = new InstanciaProblema(
                    "ALNS-" + currentTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")),
                    new ArrayList<>(pendientes),
                    programadosVentana,
                    vuelosVentana,
                    new ArrayList<>(aeropuertos)
            );
            instancia.setFechaEvaluacion(currentTime);
            instancia.setRutasComprometidas(new ArrayList<>(rutasComprometidas));
            instancia.setOcupacionBaseAeropuerto(new HashMap<>(ocupacion));
            instancia.construirGrafo();

            final ALNS alns = new ALNS(parametros);
            alns.ejecutar(instancia);
            final Solucion solucion = alns.getMejorSolucion();

            final Set<String> idsEnrutadasEnVentana = new HashSet<>();
            final List<String> idsSinRuta = new ArrayList<>();
            if (solucion != null) {
                for (final Ruta ruta : solucion.getSolucion()) {
                    if (ruta == null || ruta.getIdMaleta() == null) {
                        continue;
                    }
                    if (ruta.getEstado() == EstadoRuta.FALLIDA || ruta.getSubrutas().isEmpty()) {
                        idsSinRuta.add(ruta.getIdMaleta());
                        continue;
                    }
                    idsEnrutadasEnVentana.add(ruta.getIdMaleta());
                    rutasComprometidas.add(ruta);
                    for (final VueloInstancia vueloRuta : ruta.getSubrutas()) {
                        final VueloInstancia vueloReal = vuelosPorId.get(vueloRuta.getIdVueloInstancia());
                        if (vueloReal != null && vueloReal.getCapacidadDisponible() > 0) {
                            try {
                                vueloReal.actualizarCapacidad(1);
                            } catch (final IllegalStateException ignored) {
                                // no-op
                            }
                        }
                    }
                }
            }

            final int pendientesAntes = pendientes.size();
            final Iterator<Maleta> iterator = pendientes.iterator();
            while (iterator.hasNext()) {
                final Maleta maleta = iterator.next();
                if (!idsEnrutadasEnVentana.contains(maleta.getIdMaleta())) {
                    continue;
                }
                final String idAeropuerto = icaoOrigen(maleta);
                if (idAeropuerto != null) {
                    ocupacion.merge(idAeropuerto, -1, Integer::sum);
                }
                iterator.remove();
            }

            final int totalVentana = pendientesAntes;
            final int enrutadasVentana = idsEnrutadasEnVentana.size();
            final int sinRutaVentana = totalVentana - enrutadasVentana;
            final double porcentaje = totalVentana > 0 ? (100.0D * enrutadasVentana / totalVentana) : 100.0D;

            resultado.totalVentanas++;
            resultado.totalMaletasEvaluadas += totalVentana;
            resultado.totalMaletasEnrutadas += enrutadasVentana;
            resultado.totalMaletasSinRuta += sinRutaVentana;
            if (sinRutaVentana == 0) {
                resultado.ventanasCompletas++;
            }

            System.out.printf("%n[VENTANA %02d] %s%n", resultado.totalVentanas, currentTime.format(FORMATO_MOMENTO));
            System.out.printf("  Intervalo datos: %s -> %s%n",
                    inicioVentana.format(FORMATO_MOMENTO),
                    finVentana.minusSeconds(1).format(FORMATO_MOMENTO));
            System.out.printf("  Maletas recibidas en pedido: %d%n", nuevas.size());
            System.out.printf("  Maletas evaluadas en ventana: %d%n", totalVentana);
            System.out.printf("  Rutas comprometidas previas: %d%n", Math.max(0, rutasComprometidas.size() - enrutadasVentana));
            System.out.printf("  Enrutadas por ALNS: %d%n", enrutadasVentana);
            System.out.printf("  Sin ruta en esta ventana: %d%n", sinRutaVentana);
            System.out.printf("  Porcentaje enrutado: %.1f%%%n", porcentaje);
            System.out.printf("  Tiempo planificador (ms): %d%n", alns.getTiempoEjecucionMs());
            System.out.printf("  Pendientes tras ventana: %d%n", pendientes.size());
            if (solucion != null) {
                System.out.printf("  Fitness: %.4f%n", solucion.getFitness());
                System.out.printf("  Incumplidas reportadas: %d%n", solucion.getMaletasIncumplidas());
                System.out.printf("  Ocupacion promedio vuelos: %.4f%n", solucion.getOcupacionPromedioVuelos());
                System.out.printf("  Ocupacion promedio aeropuertos: %.4f%n", solucion.getOcupacionPromedioAlmacenes());
            }
            if (!idsSinRuta.isEmpty()) {
                System.out.printf("  IDs sin ruta: %s%n", idsSinRuta.stream().limit(10).toList());
            }
            if (sleepHabilitado && saSegundos > 0) {
                dormir(saSegundos);
            }
        }

        resultado.pendientesFinales = pendientes.size();
        return resultado;
    }

    private static Properties cargarParametros() throws IOException {
        final Properties params = new Properties();
        try (InputStream is = Files.newInputStream(PARAMS_FILE)) {
            params.load(is);
        }
        return params;
    }

    private static ParametrosALNS construirParametrosALNS(final Properties params) {
        final ParametrosALNS parametros = ParametrosALNS.porDefecto();
        parametros.setMaxIteraciones(intParam(params, "alns.maxIteraciones", parametros.getMaxIteraciones()));
        parametros.setMaxIteracionesSinMejora(
                intParam(params, "alns.maxIteracionesSinMejora", parametros.getMaxIteracionesSinMejora()));
        parametros.setTiempoMaximoMs(longParam(params, "alns.tiempoMaximoMs", parametros.getTiempoMaximoMs()));
        parametros.setQMin(intParam(params, "alns.qMin", parametros.getQMin()));
        parametros.setQMax(intParam(params, "alns.qMax", parametros.getQMax()));
        parametros.setQCritical(intParam(params, "alns.qCritical", parametros.getQCritical()));
        parametros.setMaxReintentosRuteo(intParam(params, "alns.maxReintentosRuteo", parametros.getMaxReintentosRuteo()));
        parametros.setMinutosConexion(longParam(params, "alns.minutosConexion", parametros.getMinutosConexion()));
        parametros.setTiempoRecojo(longParam(params, "alns.tiempoRecojo", parametros.getTiempoRecojo()));
        parametros.setUmbralCriticoAeropuerto(
                doubleParam(params, "alns.umbralCriticoAeropuerto", parametros.getUmbralCriticoAeropuerto()));
        parametros.setTemperaturaInicial(
                doubleParam(params, "alns.temperaturaInicial", parametros.getTemperaturaInicial()));
        parametros.setFactorEnfriamiento(
                doubleParam(params, "alns.factorEnfriamiento", parametros.getFactorEnfriamiento()));
        parametros.setSemilla(longParam(params, "alns.semilla", parametros.getSemilla()));
        parametros.setPesoMaletasNoEnrutadas(
                doubleParam(params, "alns.pesoMaletasNoEnrutadas", parametros.getPesoMaletasNoEnrutadas()));
        parametros.setPesoMaletasFueraDePlazo(
                doubleParam(params, "alns.pesoMaletasFueraDePlazo", parametros.getPesoMaletasFueraDePlazo()));
        parametros.setPesoOverflowVuelos(
                doubleParam(params, "alns.pesoOverflowVuelos", parametros.getPesoOverflowVuelos()));
        parametros.setPesoOverflowAeropuertos(
                doubleParam(params, "alns.pesoOverflowAeropuertos", parametros.getPesoOverflowAeropuertos()));
        parametros.setPesoOcupacionPromedioVuelos(
                doubleParam(params, "alns.pesoOcupacionPromedioVuelos", parametros.getPesoOcupacionPromedioVuelos()));
        parametros.setPesoOcupacionPromedioAeropuertos(
                doubleParam(params, "alns.pesoOcupacionPromedioAeropuertos", parametros.getPesoOcupacionPromedioAeropuertos()));
        parametros.setPesoHolgura(doubleParam(params, "alns.pesoHolgura", parametros.getPesoHolgura()));
        parametros.setSegmentoIteraciones(
                intParam(params, "alns.segmentoIteraciones", parametros.getSegmentoIteraciones()));
        parametros.setSigma1(doubleParam(params, "alns.sigma1", parametros.getSigma1()));
        parametros.setSigma2(doubleParam(params, "alns.sigma2", parametros.getSigma2()));
        parametros.setSigma3(doubleParam(params, "alns.sigma3", parametros.getSigma3()));
        parametros.setSigma4(doubleParam(params, "alns.sigma4", parametros.getSigma4()));
        parametros.setRho(doubleParam(params, "alns.rho", parametros.getRho()));
        parametros.setPesoMinimoOperador(
                doubleParam(params, "alns.pesoMinimoOperador", parametros.getPesoMinimoOperador()));
        return parametros;
    }

    private static Map<String, List<Maleta>> agruparMaletasPorPedido(final List<Maleta> maletas) {
        final Map<String, List<Maleta>> indice = new HashMap<>();
        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getPedido() == null || maleta.getPedido().getIdPedido() == null) {
                continue;
            }
            indice.computeIfAbsent(maleta.getPedido().getIdPedido(), ignored -> new ArrayList<>()).add(maleta);
        }
        return indice;
    }

    private static void actualizarEstadosVuelos(final List<VueloInstancia> vuelos, final LocalDateTime currentTime) {
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo.getEstado() == EstadoVuelo.CANCELADO) {
                continue;
            }
            if (vuelo.getFechaLlegada() != null && !vuelo.getFechaLlegada().isAfter(currentTime)) {
                vuelo.setEstado(EstadoVuelo.FINALIZADO);
            } else if (vuelo.getFechaSalida() != null && !vuelo.getFechaSalida().isAfter(currentTime)) {
                vuelo.setEstado(EstadoVuelo.EN_PROGRESO);
            }
        }
    }

    private static String icaoOrigen(final Maleta maleta) {
        if (maleta == null || maleta.getPedido() == null || maleta.getPedido().getAeropuertoOrigen() == null) {
            return null;
        }
        return maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto();
    }

    private static Map<String, VueloInstancia> indexarVuelosInstancia(final ArrayList<VueloInstancia> vuelos) {
        final Map<String, VueloInstancia> indice = new HashMap<>();
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo != null && vuelo.getIdVueloInstancia() != null) {
                indice.put(vuelo.getIdVueloInstancia(), vuelo);
            }
        }
        return indice;
    }

    private static ArrayList<VueloInstancia> clonarVuelosInstancia(final ArrayList<VueloInstancia> originales) {
        final ArrayList<VueloInstancia> clon = new ArrayList<>(originales.size());
        for (final VueloInstancia vuelo : originales) {
            clon.add(new VueloInstancia(
                    vuelo.getIdVueloInstancia(),
                    vuelo.getVueloProgramado(),
                    vuelo.getFechaOperacion(),
                    vuelo.getFechaSalida(),
                    vuelo.getFechaLlegada(),
                    vuelo.getCapacidadMaxima(),
                    vuelo.getCapacidadDisponible(),
                    vuelo.getEstado()
            ));
        }
        return clon;
    }

    private static void imprimirResumenFinal(final ResultadoConsola resultado) {
        System.out.printf("%n=== RESUMEN FINAL ALNS ===%n");
        System.out.printf("Ventanas evaluadas: %d%n", resultado.totalVentanas);
        System.out.printf("Ventanas con 100%% enruteo: %d%n", resultado.ventanasCompletas);
        System.out.printf("Maletas evaluadas: %d%n", resultado.totalMaletasEvaluadas);
        System.out.printf("Maletas enrutadas: %d%n", resultado.totalMaletasEnrutadas);
        System.out.printf("Maletas sin ruta: %d%n", resultado.totalMaletasSinRuta);
        final double tasaGlobal = resultado.totalMaletasEvaluadas > 0
                ? 100.0D * resultado.totalMaletasEnrutadas / resultado.totalMaletasEvaluadas
                : 100.0D;
        System.out.printf("Tasa global de enruteo: %.1f%%%n", tasaGlobal);
        System.out.printf("Pendientes finales: %d%n", resultado.pendientesFinales);
    }

    private static void dormir(final int saSegundos) {
        try {
            Thread.sleep(Math.max(0L, saSegundos) * 1000L);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static LocalDateTime min(final LocalDateTime primero, final LocalDateTime segundo) {
        return primero.isBefore(segundo) ? primero : segundo;
    }

    private static int intParam(final Properties params, final String key, final int def) {
        final String val = params.getProperty(key);
        return val != null ? Integer.parseInt(val.trim()) : def;
    }

    private static long longParam(final Properties params, final String key, final long def) {
        final String val = params.getProperty(key);
        return val != null ? Long.parseLong(val.trim()) : def;
    }

    private static double doubleParam(final Properties params, final String key, final double def) {
        final String val = params.getProperty(key);
        return val != null ? Double.parseDouble(val.trim()) : def;
    }

    private static final class ResultadoConsola {
        private int totalVentanas;
        private int ventanasCompletas;
        private int totalMaletasEvaluadas;
        private int totalMaletasEnrutadas;
        private int totalMaletasSinRuta;
        private int pendientesFinales;
    }
}
