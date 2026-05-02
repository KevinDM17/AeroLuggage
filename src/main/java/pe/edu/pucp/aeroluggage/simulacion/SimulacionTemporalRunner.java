package pe.edu.pucp.aeroluggage.simulacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOConfiguracion;
import pe.edu.pucp.aeroluggage.algoritmos.common.CalculadorFitnessExperimental;
import pe.edu.pucp.aeroluggage.algoritmos.common.CalculadorSemaforo;
import pe.edu.pucp.aeroluggage.algoritmos.common.ResultadoFitnessExperimental;
import pe.edu.pucp.aeroluggage.algoritmos.ga.FuncionCosto;
import pe.edu.pucp.aeroluggage.algoritmos.ga.GA;
import pe.edu.pucp.aeroluggage.algoritmos.ga.ParametrosGA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;
import pe.edu.pucp.aeroluggage.dominio.enums.Semaforo;
import pe.edu.pucp.aeroluggage.io.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.io.DatosEntrada;

public final class SimulacionTemporalRunner {

    private static final Path DOCS = Path.of("Documentos");
    private static final Path PARAMS_FILE = Path.of("test_params.txt");
    private static final Path RESULTADOS_DIR = Path.of("target", "resultados-experimentales");
    private static final Path FITNESS_EXPERIMENTAL_CSV = RESULTADOS_DIR.resolve("fitness-experimental.csv");
    private static final String FITNESS_EXPERIMENTAL_CABECERA =
            "Muestra,ACO,ACO Tiempo Ms,Algoritmo Genetico,Algoritmo Genetico Tiempo Ms";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String PREFIJO_DIAGNOSTICO_MALETA = "MAL-SPIM-000010930-";
    private static final long SEMILLA_ALEATORIA = -1L;
    private static final long DEFAULT_MINUTOS_CONEXION = 10L;
    private static final long DEFAULT_TIEMPO_RECOJO = 10L;
    private static final long DEFAULT_SA_SEGUNDOS = 0L;
    private static final long DEFAULT_K = 70L;
    private static final boolean DEFAULT_SLEEP_HABILITADO = true;
    private static Runnable terminadorPrograma = () -> System.exit(0);

    private SimulacionTemporalRunner() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void ejecutarDesdeConfiguracion() {
        final ContextoSimulacion contexto = cargarContexto();
        final boolean ejecutarGa = Boolean.parseBoolean(contexto.params().getProperty("ejecutar.ga", "true"));
        final boolean ejecutarAco = Boolean.parseBoolean(contexto.params().getProperty("ejecutar.aco", "true"));
        if (!ejecutarGa && !ejecutarAco) {
            throw new IllegalStateException("No hay algoritmos habilitados en test_params.txt");
        }
        final ExportadorFitnessExperimental exportador = crearExportadorFitnessExperimental(contexto);
        if (ejecutarGa) {
            final ParametrosGA parametrosGa = construirParametrosGA(contexto.params());
            resolverSemillaGa(parametrosGa);
            reportarSemilla("GA", parametrosGa.getSemilla());
            final GA ga = new GA(parametrosGa);
            final ResultadoSimulacion resultado = ejecutarSimulacion(
                    ga,
                    crearContextoEjecucion(contexto),
                    "GA",
                    contexto
            );
            exportador.registrar("GA", parametrosGa.getSemilla(), resultado);
        }
        if (ejecutarAco) {
            final ACOConfiguracion configuracionAco = construirConfiguracionACO(contexto.params());
            resolverSemillaAco(configuracionAco);
            reportarSemilla("ACO", configuracionAco.getSemilla());
            final ACO aco = new ACO(configuracionAco);
            final ResultadoSimulacion resultado = ejecutarSimulacion(
                    aco,
                    crearContextoEjecucion(contexto),
                    "ACO",
                    contexto
            );
            exportador.registrar("ACO", configuracionAco.getSemilla(), resultado);
        }
        exportador.reportarArchivo();
    }

    public static void ejecutarGa() {
        final ContextoSimulacion contexto = cargarContexto();
        final ExportadorFitnessExperimental exportador = crearExportadorFitnessExperimental(contexto);
        final ParametrosGA parametrosGa = construirParametrosGA(contexto.params());
        resolverSemillaGa(parametrosGa);
        reportarSemilla("GA", parametrosGa.getSemilla());
        final GA ga = new GA(parametrosGa);
        final ResultadoSimulacion resultado = ejecutarSimulacion(
                ga,
                crearContextoEjecucion(contexto),
                "GA",
                contexto
        );
        exportador.registrar("GA", parametrosGa.getSemilla(), resultado);
        exportador.reportarArchivo();
    }

    public static void ejecutarAco() {
        final ContextoSimulacion contexto = cargarContexto();
        final ExportadorFitnessExperimental exportador = crearExportadorFitnessExperimental(contexto);
        final ACOConfiguracion configuracionAco = construirConfiguracionACO(contexto.params());
        resolverSemillaAco(configuracionAco);
        reportarSemilla("ACO", configuracionAco.getSemilla());
        final ACO aco = new ACO(configuracionAco);
        final ResultadoSimulacion resultado = ejecutarSimulacion(
                aco,
                crearContextoEjecucion(contexto),
                "ACO",
                contexto
        );
        exportador.registrar("ACO", configuracionAco.getSemilla(), resultado);
        exportador.reportarArchivo();
    }

    public static void ejecutarExperimento() {
        final ContextoSimulacion contexto = cargarContexto();
        final boolean ejecutarGa = Boolean.parseBoolean(contexto.params().getProperty("ejecutar.ga", "true"));
        final boolean ejecutarAco = Boolean.parseBoolean(contexto.params().getProperty("ejecutar.aco", "true"));
        if (!ejecutarGa && !ejecutarAco) {
            throw new IllegalStateException("No hay algoritmos habilitados en test_params.txt");
        }
        final int iteraciones = intParam(contexto.params(), "simulacion.iteraciones", 30);
        final Random rng = new Random();
        final List<ResultadoIteracion> historial = new ArrayList<>();

        try {
            Files.createDirectories(RESULTADOS_DIR);
            inicializarCsvFitnessExperimental();
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo crear directorio de resultados", exception);
        }

        System.out.printf("%n=== EXPERIMENTO: %d iteraciones [%s -> %s] ===%n",
                iteraciones, contexto.fechaInicio(), contexto.fechaFin());

        for (int i = 1; i <= iteraciones; i++) {
            final long semilla = rng.nextLong();
            System.out.printf("%n-- Iteracion %d/%d  semilla=%d --%n", i, iteraciones, semilla);

            ResultadoSimulacion resGA = null;
            ResultadoSimulacion resACO = null;

            final boolean ejecutarGaPrimero = i % 2 != 0;
            if (ejecutarGaPrimero && ejecutarGa) {
                final ParametrosGA pGA = construirParametrosGA(contexto.params());
                pGA.setSemilla(semilla);
                resGA = ejecutarSimulacion(new GA(pGA), crearContextoEjecucion(contexto), "GA-" + i, contexto);
            }
            if (ejecutarAco) {
                final ACOConfiguracion cACO = construirConfiguracionACO(contexto.params());
                cACO.setSemilla(semilla);
                resACO = ejecutarSimulacion(new ACO(cACO), crearContextoEjecucion(contexto), "ACO-" + i, contexto);
            }
            if (!ejecutarGaPrimero && ejecutarGa) {
                final ParametrosGA pGA = construirParametrosGA(contexto.params());
                pGA.setSemilla(semilla);
                resGA = ejecutarSimulacion(new GA(pGA), crearContextoEjecucion(contexto), "GA-" + i, contexto);
            }

            final ExportadorFitnessExperimental exportador =
                    new ExportadorFitnessExperimental(FITNESS_EXPERIMENTAL_CSV);
            if (resGA != null) {
                exportador.registrar("GA", semilla, resGA);
            }
            if (resACO != null) {
                exportador.registrar("ACO", semilla, resACO);
            }
            exportador.reportarArchivo();

            historial.add(new ResultadoIteracion(i, semilla, resGA, resACO));

            System.out.printf("[ITER %2d] semilla=%-20d", i, semilla);
            if (resGA != null) {
                System.out.printf("  GA: %-10s enr=%-5d fit=%.3f t=%dms",
                        resGA.colapsada ? "COLAPSO" : "OK",
                        resGA.totalMaletasEnrutadas,
                        fitnessExperimentalValor(resGA),
                        resGA.tiempoEjecucionMs);
            }
            if (resACO != null) {
                System.out.printf("  ACO: %-10s enr=%-5d fit=%.3f t=%dms",
                        resACO.colapsada ? "COLAPSO" : "OK",
                        resACO.totalMaletasEnrutadas,
                        fitnessExperimentalValor(resACO),
                        resACO.tiempoEjecucionMs);
            }
            System.out.println();
        }

        reportarExperimento(historial, iteraciones, ejecutarGa, ejecutarAco);
    }

    public static void ejecutarDetallado() {
        final ContextoSimulacion contexto = cargarContexto();
        final boolean ejecutarGa = Boolean.parseBoolean(contexto.params().getProperty("ejecutar.ga", "true"));
        final boolean ejecutarAco = Boolean.parseBoolean(contexto.params().getProperty("ejecutar.aco", "true"));
        if (!ejecutarGa && !ejecutarAco) {
            throw new IllegalStateException("No hay algoritmos habilitados en test_params.txt");
        }
        final int iteraciones = intParam(contexto.params(), "simulacion.iteraciones", 30);
        final Random rng = new Random();
        final List<ResultadoIteracion> historial = new ArrayList<>();
        final ExportadorTrazabilidadDetallada exportadorDetallado = new ExportadorTrazabilidadDetallada(RESULTADOS_DIR);

        try {
            Files.createDirectories(RESULTADOS_DIR);
            inicializarCsvFitnessExperimental();
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo crear directorio de resultados", exception);
        }

        System.out.printf("%n=== DETALLADO: %d iteraciones [%s -> %s] ===%n",
                iteraciones, contexto.fechaInicio(), contexto.fechaFin());

        for (int i = 1; i <= iteraciones; i++) {
            final long semilla = rng.nextLong();
            System.out.printf("%n-- Iteracion detallada %d/%d  semilla=%d --%n", i, iteraciones, semilla);

            ResultadoSimulacion resGA = null;
            ResultadoSimulacion resACO = null;

            final boolean ejecutarGaPrimero = i % 2 != 0;
            if (ejecutarGaPrimero && ejecutarGa) {
                final ParametrosGA pGA = construirParametrosGA(contexto.params());
                pGA.setSemilla(semilla);
                resGA = ejecutarSimulacion(
                        new GA(pGA),
                        crearContextoEjecucion(contexto),
                        "GA-" + i,
                        contexto,
                        true
                );
                exportadorDetallado.exportar("GA", i, semilla, resGA, contexto);
            }
            if (ejecutarAco) {
                final ACOConfiguracion cACO = construirConfiguracionACO(contexto.params());
                cACO.setSemilla(semilla);
                resACO = ejecutarSimulacion(
                        new ACO(cACO),
                        crearContextoEjecucion(contexto),
                        "ACO-" + i,
                        contexto,
                        true
                );
                exportadorDetallado.exportar("ACO", i, semilla, resACO, contexto);
            }
            if (!ejecutarGaPrimero && ejecutarGa) {
                final ParametrosGA pGA = construirParametrosGA(contexto.params());
                pGA.setSemilla(semilla);
                resGA = ejecutarSimulacion(
                        new GA(pGA),
                        crearContextoEjecucion(contexto),
                        "GA-" + i,
                        contexto,
                        true
                );
                exportadorDetallado.exportar("GA", i, semilla, resGA, contexto);
            }

            final ExportadorFitnessExperimental exportador =
                    new ExportadorFitnessExperimental(FITNESS_EXPERIMENTAL_CSV);
            if (resGA != null) {
                exportador.registrar("GA", semilla, resGA);
            }
            if (resACO != null) {
                exportador.registrar("ACO", semilla, resACO);
            }
            exportador.reportarArchivo();

            historial.add(new ResultadoIteracion(i, semilla, resGA, resACO));

            System.out.printf("[ITER %2d] semilla=%-20d", i, semilla);
            if (resGA != null) {
                System.out.printf("  GA: %-10s enr=%-5d fit=%.3f t=%dms",
                        resGA.colapsada ? "COLAPSO" : "OK",
                        resGA.totalMaletasEnrutadas,
                        fitnessExperimentalValor(resGA),
                        resGA.tiempoEjecucionMs);
            }
            if (resACO != null) {
                System.out.printf("  ACO: %-10s enr=%-5d fit=%.3f t=%dms",
                        resACO.colapsada ? "COLAPSO" : "OK",
                        resACO.totalMaletasEnrutadas,
                        fitnessExperimentalValor(resACO),
                        resACO.tiempoEjecucionMs);
            }
            System.out.println();
        }

        reportarExperimento(historial, iteraciones, ejecutarGa, ejecutarAco);
    }

    private static ContextoSimulacion cargarContexto() {
        final long inicioCargaMs = System.currentTimeMillis();
        final Properties params = cargarParametros();
        validarEntradas();

        final LocalDate fechaInicio = LocalDate.parse(params.getProperty("simulacion.fecha.inicio"), FMT);
        final LocalDate fechaFin = LocalDate.parse(params.getProperty("simulacion.fecha.fin"), FMT);
        final int ventanaDias = intParam(params, "simulacion.ventana.dias", 3);

        final Path archivoAeropuertos = DOCS.resolve("Aeropuertos.txt");
        final Path archivoVuelos = DOCS.resolve("planes_vuelo.txt");
        final Path carpetaEnvios = DOCS.resolve("Envios");

        final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(archivoAeropuertos);
        final Map<String, Aeropuerto> indiceAeropuertos = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
        final int totalDias = (int) (fechaFin.toEpochDay() - fechaInicio.toEpochDay()) + 1 + ventanaDias;
        final ArrayList<VueloProgramado> todosVuelosProgramados = CargadorDatosPrueba.cargarVuelosProgramados(
                archivoVuelos, indiceAeropuertos, fechaInicio, totalDias);
        final ArrayList<VueloInstancia> todosVuelosInstancia = CargadorDatosPrueba.cargarVuelosInstancia(
                archivoVuelos, indiceAeropuertos, fechaInicio, totalDias);
        final DatosEntrada datosEntrada = CargadorDatosPrueba.cargarEnviosEnRango(
                carpetaEnvios,
                indiceAeropuertos,
                fechaInicio,
                fechaFin
        );
        final List<Maleta> todasLasMaletas = new ArrayList<>(datosEntrada.getMaletas());
        validarMaletas(todasLasMaletas);
        final List<Pedido> pedidosOrdenados = ordenarPedidosCronologicamente(datosEntrada);
        reportarCargaInicial(
                datosEntrada,
                pedidosOrdenados,
                fechaInicio,
                fechaFin,
                totalDias,
                System.currentTimeMillis() - inicioCargaMs
        );

        return new ContextoSimulacion(
                params,
                aeropuertos,
                indiceAeropuertos,
                todosVuelosProgramados,
                todosVuelosInstancia,
                pedidosOrdenados,
                todasLasMaletas,
                fechaInicio,
                fechaFin,
                ventanaDias
        );
    }

    private static Properties cargarParametros() {
        final Properties params = new Properties();
        try (InputStream is = Files.newInputStream(PARAMS_FILE)) {
            params.load(is);
            return params;
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo leer test_params.txt", exception);
        }
    }

    private static void validarEntradas() {
        if (!Files.isDirectory(DOCS)) {
            throw new IllegalStateException("No existe la carpeta Documentos");
        }
        if (!Files.exists(PARAMS_FILE)) {
            throw new IllegalStateException("No existe el archivo test_params.txt");
        }
    }

    private static void validarMaletas(final List<Maleta> maletas) {
        for (final Maleta maleta : maletas) {
            final String id = maleta != null ? maleta.getIdMaleta() : "(null)";
            if (maleta == null) {
                throw new IllegalStateException("Maleta nula encontrada en los datos de entrada");
            }
            if (maleta.getPedido() == null) {
                throw new IllegalStateException("Maleta " + id + " no tiene pedido asignado");
            }
            if (maleta.getFechaRegistro() == null) {
                throw new IllegalStateException("Maleta " + id + " no tiene fechaRegistro");
            }
            if (maleta.getPedido().getFechaHoraPlazo() == null) {
                maleta.getPedido().calcularFechaHoraPlazo();
            }
            if (maleta.getPedido().getFechaHoraPlazo() == null) {
                throw new IllegalStateException(
                        "Maleta " + id + " (pedido " + maleta.getPedido().getIdPedido()
                        + ") no tiene fechaHoraPlazo y no se pudo calcular"
                        + " (aeropuerto origen/destino o continente podria ser null)");
            }
        }
    }

    private static List<Pedido> ordenarPedidosCronologicamente(final DatosEntrada datosEntrada) {
        final List<Pedido> pedidos = new ArrayList<>(datosEntrada.getPedidos());
        pedidos.sort(Comparator.comparing(Pedido::getFechaRegistro,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Pedido::getIdPedido));
        return pedidos;
    }

    private static Map<String, List<Maleta>> agruparMaletasPorPedido(final List<Maleta> maletas) {
        final Map<String, List<Maleta>> indice = new HashMap<>();
        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getPedido() == null
                    || maleta.getPedido().getIdPedido() == null) {
                continue;
            }
            indice.computeIfAbsent(maleta.getPedido().getIdPedido(), k -> new ArrayList<>())
                    .add(maleta);
        }
        return indice;
    }

    private static void actualizarEstadosVuelos(final List<VueloInstancia> todos,
                                                final LocalDateTime currentTime) {
        for (final VueloInstancia v : todos) {
            if (v.getEstado() == EstadoVuelo.CANCELADO) {
                continue;
            }
            if (v.getFechaLlegada() != null && !v.getFechaLlegada().isAfter(currentTime)) {
                v.setEstado(EstadoVuelo.FINALIZADO);
            } else if (v.getFechaSalida() != null && !v.getFechaSalida().isAfter(currentTime)) {
                v.setEstado(EstadoVuelo.EN_PROGRESO);
            }
        }
    }

    private static void reportarCargaInicial(final DatosEntrada datosEntrada,
                                             final List<Pedido> pedidosOrdenados,
                                             final LocalDate fechaInicio,
                                             final LocalDate fechaFin,
                                             final int totalDias,
                                             final long tiempoCargaMs) {
        final int pedidos = datosEntrada.getPedidos().size();
        final int maletas = datosEntrada.getMaletas().size();
        System.out.printf(
                "Carga simulacion: pedidos=%d maletas=%d rango=%s..%s diasVuelos=%d totalPedidos=%d tiempo=%d ms%n",
                pedidos,
                maletas,
                fechaInicio,
                fechaFin,
                totalDias,
                pedidosOrdenados.size(),
                tiempoCargaMs
        );
    }

    private static ResultadoSimulacion ejecutarSimulacion(final Metaheuristico algoritmo,
                                                          final ContextoEjecucion contextoEjecucion,
                                                          final String nombre,
                                                          final ContextoSimulacion contexto) {
        return ejecutarSimulacion(algoritmo, contextoEjecucion, nombre, contexto, false);
    }

    private static ResultadoSimulacion ejecutarSimulacion(final Metaheuristico algoritmo,
                                                          final ContextoEjecucion contextoEjecucion,
                                                          final String nombre,
                                                          final ContextoSimulacion contexto,
                                                          final boolean capturarTrazabilidad) {
        final List<Maleta> registradas = new ArrayList<>();
        final Map<String, Integer> ocupacion = new HashMap<>();
        final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta = new HashMap<>();
        final Map<String, VueloInstancia> vuelosPorId = indexarVuelosInstancia(
                contextoEjecucion.todosVuelosInstancia()
        );
        final List<PasoSimulacion> historial = new ArrayList<>();
        final long inicioEjecucionMs = System.currentTimeMillis();
        final ConfiguracionProgramada configuracionProgramada = construirConfiguracionProgramada(contexto.params());
        final long tiempoRecojo = longParam(contexto.params(), "tiempoRecojo", DEFAULT_TIEMPO_RECOJO);
        final boolean esGa = algoritmo instanceof GA;
        final Set<String> maletasEnrutadas = new HashSet<>();
        final ResultadoSimulacion resultado = new ResultadoSimulacion();
        ResultadoFitnessExperimental fitnessExperimental = new ResultadoFitnessExperimental(0D, 0, 0D, 0D, 0D);

        System.out.printf("%n=== SIMULACION PROGRAMADA [%s] %s -> %s ===%n",
                nombre, contexto.fechaInicio(), contexto.fechaFin());
        System.out.printf(
                "Configuracion programada: SA=%d s, K=%d, SC=%d min, sleep=%s%n",
                configuracionProgramada.saMs() / 1000L,
                configuracionProgramada.k(),
                configuracionProgramada.sc().toMinutes(),
                configuracionProgramada.sleepHabilitado()
        );

        final Map<String, List<Maleta>> maletasPorPedido =
                agruparMaletasPorPedido(contexto.todasLasMaletas());
        final LocalDateTime primerMomento = obtenerPrimerMomentoPedidos(contexto.pedidosOrdenados());
        if (primerMomento == null) {
            resultado.totalMaletasPendientes = 0;
            resultado.tiempoEjecucionMs = System.currentTimeMillis() - inicioEjecucionMs;
            resultado.fitnessExperimental = fitnessExperimental;
            reportarResumen(nombre, resultado);
            return resultado;
        }

        LocalDateTime limiteProcesado = primerMomento.minusNanos(1L);
        final LocalDateTime finSimulacion = contexto.fechaFin().atTime(LocalTime.MAX);
        int indicePedido = 0;
        int numeroCiclo = 0;
        long maxTaMs = 0L;
        double maxOcupacionVuelos = 0.0;
        double maxOcupacionAeropuertos = 0.0;

        while (limiteProcesado.isBefore(finSimulacion)
                && (indicePedido < contexto.pedidosOrdenados().size()
                || existenMaletasNoFinalizadas(
                        registradas,
                        rutasProgramadasPorMaleta,
                        limiteProcesado,
                        tiempoRecojo
                ))) {
            numeroCiclo++;
            final LocalDateTime inicioCicloDatos = limiteProcesado.plusNanos(1L);
            final LocalDateTime finCicloPropuesto = inicioCicloDatos.plus(configuracionProgramada.sc());
            final LocalDateTime finCicloDatos = finCicloPropuesto.isAfter(finSimulacion)
                    ? finSimulacion
                    : finCicloPropuesto;
            final long inicioCicloRealMs = System.currentTimeMillis();
            final int registradasAntes = registradas.size();

            indicePedido = incorporarPedidosHastaMomento(
                    contexto.pedidosOrdenados(),
                    indicePedido,
                    finCicloDatos,
                    maletasPorPedido,
                    registradas
            );

            actualizarEstadosVuelos(contextoEjecucion.todosVuelosInstancia(), finCicloDatos);
            final Map<String, EstadoMaletaTemporal> estadosPorMaleta = evaluarEstadosMaletas(
                    registradas,
                    rutasProgramadasPorMaleta,
                    finCicloDatos,
                    tiempoRecojo
            );
            recalcularOcupacionAeropuertos(
                    ocupacion,
                    contextoEjecucion.aeropuertos(),
                    registradas,
                    rutasProgramadasPorMaleta,
                    finCicloDatos,
                    tiempoRecojo
            );

            int rutasConfirmadasEnCiclo = 0;
            final int nuevasEnCiclo = registradas.size() - registradasAntes;
            Solucion solucion = null;
            double fitnessAlgoritmoCiclo = 0D;
            ResultadoFitnessExperimental fitnessCiclo = new ResultadoFitnessExperimental(0D, 0, 0D, 0D, 0D);
            final LocalDateTime ventanaCompromisoGa = esGa
                    ? finCicloDatos.plus(configuracionProgramada.sc())
                    : null;
            final List<Maleta> maletasReplanificables = construirMaletasReplanificables(
                    registradas,
                    estadosPorMaleta,
                    contextoEjecucion.indiceAeropuertos(),
                    finCicloDatos,
                    esGa,
                    ventanaCompromisoGa
            );

            if (!maletasReplanificables.isEmpty()) {
                liberarCapacidadFuturaReplanificable(
                        estadosPorMaleta,
                        rutasProgramadasPorMaleta,
                        finCicloDatos,
                        esGa,
                        ventanaCompromisoGa
                );
                final VentanaVuelos ventana = construirVentanaVuelos(
                        contextoEjecucion.todosVuelosInstancia(),
                        contextoEjecucion.todosVuelosProgramados(),
                        finCicloDatos,
                        contexto.ventanaDias()
                );
                final DateTimeFormatter fmtMomento = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
                final InstanciaProblema instancia = new InstanciaProblema(
                        "SIM-" + finCicloDatos.format(fmtMomento),
                        new ArrayList<>(maletasReplanificables),
                        ventana.vuelosProgramados(),
                        ventana.vuelosInstancia(),
                        contextoEjecucion.aeropuertos()
                );
                final ParametrosGA parametrosGa = construirParametrosGA(contexto.params());
                instancia.construirGrafo();
                instancia.setMinutosConexion(longParam(contexto.params(), "minutosConexion", DEFAULT_MINUTOS_CONEXION));
                instancia.setTiempoRecojo(longParam(contexto.params(), "tiempoRecojo", DEFAULT_TIEMPO_RECOJO));

                algoritmo.ejecutar(instancia);
                solucion = obtenerSolucion(algoritmo);
                postprocesarSolucion(solucion, instancia, parametrosGa);
                fitnessAlgoritmoCiclo = solucion != null ? solucion.getFitness() : 0D;
                fitnessCiclo = CalculadorFitnessExperimental.calcular(solucion, instancia);
                fitnessExperimental = fitnessExperimental.sumar(fitnessCiclo);

                final Map<String, List<VueloInstancia>> nuevasRutas = extraerRutasConfirmadas(
                        solucion,
                        vuelosPorId
                );
                aplicarRutasReplanificadas(
                        estadosPorMaleta,
                        rutasProgramadasPorMaleta,
                        nuevasRutas,
                        maletasEnrutadas,
                        finCicloDatos,
                        esGa,
                        ventanaCompromisoGa
                );
                reservarCapacidadRutas(nuevasRutas);
                recalcularOcupacionAeropuertos(
                        ocupacion,
                        contextoEjecucion.aeropuertos(),
                        registradas,
                        rutasProgramadasPorMaleta,
                        finCicloDatos,
                        tiempoRecojo
                );
                rutasConfirmadasEnCiclo = nuevasRutas.size();
            }

            final Map<String, EstadoMaletaTemporal> estadosActualizados = evaluarEstadosMaletas(
                    registradas,
                    rutasProgramadasPorMaleta,
                    finCicloDatos,
                    tiempoRecojo
            );
            final List<MaletaNoRuteada> noRuteadasCiclo = identificarNoRuteadas(estadosActualizados, finCicloDatos);
            final int pendientesSinRuta = noRuteadasCiclo.size();
            final int enrutadasVigentes = Math.max(0, registradas.size() - pendientesSinRuta);
            final DetalleColapso detalleColapso = detectarDetalleColapso(
                    estadosActualizados,
                    contextoEjecucion.aeropuertos(),
                    contextoEjecucion.todosVuelosInstancia(),
                    inicioCicloDatos,
                    finCicloDatos
            );
            final boolean hayColapso = detalleColapso != null;

            final String aeropuertoMasCargado = ocupacion.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("-");
            historial.add(new PasoSimulacion(
                    finCicloDatos,
                    nuevasEnCiclo,
                    enrutadasVigentes,
                    pendientesSinRuta,
                    solucion != null ? solucion.getSemaforo() : null,
                    aeropuertoMasCargado
            ));
            if (capturarTrazabilidad) {
                resultado.trazabilidadCiclos.add(construirTrazabilidadCiclo(
                        numeroCiclo,
                        inicioCicloDatos,
                        finCicloDatos,
                        nuevasEnCiclo,
                        enrutadasVigentes,
                        rutasConfirmadasEnCiclo,
                        pendientesSinRuta,
                        noRuteadasCiclo,
                        estadosPorMaleta,
                        estadosActualizados,
                        rutasProgramadasPorMaleta
                ));
            }

            final long taMs = System.currentTimeMillis() - inicioCicloRealMs;
            if (taMs > maxTaMs) {
                maxTaMs = taMs;
            }
            for (final VueloInstancia vuelo : contextoEjecucion.todosVuelosInstancia()) {
                if (vuelo == null || vuelo.getCapacidadMaxima() <= 0) {
                    continue;
                }
                final double pct = 100.0 * (vuelo.getCapacidadMaxima() - vuelo.getCapacidadDisponible())
                        / vuelo.getCapacidadMaxima();
                if (pct > maxOcupacionVuelos) {
                    maxOcupacionVuelos = pct;
                }
            }
            for (final Aeropuerto aeropuerto : contextoEjecucion.aeropuertos()) {
                if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                    continue;
                }
                final double pct = 100.0 * aeropuerto.getMaletasActuales() / aeropuerto.getCapacidadAlmacen();
                if (pct > maxOcupacionAeropuertos) {
                    maxOcupacionAeropuertos = pct;
                }
            }
            reportarResumenCiclo(
                    nombre,
                    numeroCiclo,
                    inicioCicloDatos,
                    finCicloDatos,
                    nuevasEnCiclo,
                    enrutadasVigentes,
                    rutasConfirmadasEnCiclo,
                    pendientesSinRuta,
                    fitnessAlgoritmoCiclo,
                    fitnessCiclo.getFitnessExperimental(),
                    taMs,
                    configuracionProgramada.saMs()
            );
            //reportarCapacidadAeropuertosCiclo(nombre, numeroCiclo, contextoEjecucion.aeropuertos());
            if (configuracionProgramada.saMs() > 0L && taMs > configuracionProgramada.saMs()) {
                terminarPorTiempoExcedido(taMs, configuracionProgramada.saMs());
            }

            if (hayColapso) {
                System.out.printf("[COLAPSO] %s colapso en momento %s -> %s%n",
                        nombre, finCicloDatos, detalleColapso.motivo());
                reportarDiagnosticoColapso(detalleColapso);
                resultado.colapsada = true;
                resultado.momentoColapso = finCicloDatos;
                break;
            }

            limiteProcesado = finCicloDatos;
            dormirHastaSiguienteCiclo(configuracionProgramada, taMs);
        }

        final LocalDateTime momentoFinal = resultado.colapsada && resultado.momentoColapso != null
                ? resultado.momentoColapso
                : limiteProcesado;
        final Map<String, EstadoMaletaTemporal> estadosFinales =
                evaluarEstadosMaletas(registradas, rutasProgramadasPorMaleta, momentoFinal, tiempoRecojo);
        resultado.totalMaletasEnrutadas = contarMaletasRuteadasFinales(estadosFinales);
        resultado.detalleNoRuteadas = identificarNoRuteadas(estadosFinales, momentoFinal);
        resultado.totalMaletasPendientes = resultado.detalleNoRuteadas.size();
        resultado.historial = historial;
        resultado.tiempoEjecucionMs = System.currentTimeMillis() - inicioEjecucionMs;
        resultado.maxTaMs = maxTaMs;
        resultado.maxOcupacionVuelos = maxOcupacionVuelos;
        resultado.maxOcupacionAeropuertos = maxOcupacionAeropuertos;
        resultado.fitnessExperimental = fitnessExperimental;
        reportarDiagnosticoMaletasNoRuteadasFinal(nombre, resultado.detalleNoRuteadas, estadosFinales);
        reportarResumen(nombre, resultado);
        return resultado;
    }

    private static void recalcularOcupacionAeropuertos(final Map<String, Integer> ocupacion,
                                                       final ArrayList<Aeropuerto> aeropuertos,
                                                       final List<Maleta> registradas,
                                                       final Map<String, RutaMaletaProgramada> rutasConfirmadasPorMaleta,
                                                       final LocalDateTime currentTime,
                                                       final long tiempoRecojo) {
        ocupacion.clear();
        for (final Maleta maleta : registradas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final RutaMaletaProgramada rutaProgramada = rutasConfirmadasPorMaleta.get(maleta.getIdMaleta());
            final String aeropuertoActual = resolverAeropuertoOcupacion(
                    maleta,
                    rutaProgramada,
                    currentTime,
                    tiempoRecojo
            );
            if (aeropuertoActual == null) {
                continue;
            }
            ocupacion.merge(aeropuertoActual, 1, Integer::sum);
        }
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null) {
                continue;
            }
            final int maletasActuales = ocupacion.getOrDefault(aeropuerto.getIdAeropuerto(), 0);
            aeropuerto.setMaletasActuales(Math.max(0, maletasActuales));
        }
    }

    private static String resolverAeropuertoOcupacion(final Maleta maleta,
                                                      final RutaMaletaProgramada rutaConfirmada,
                                                      final LocalDateTime currentTime,
                                                      final long tiempoRecojo) {
        final EstadoMaletaTemporal estado = evaluarEstadoMaleta(maleta, rutaConfirmada, currentTime, tiempoRecojo);
        return estado.aeropuertoActual();
    }

    private static ConfiguracionProgramada construirConfiguracionProgramada(final Properties params) {
        final long saSegundos = longParam(params, "simulacion.programada.sa.segundos", DEFAULT_SA_SEGUNDOS);
        final long k = longParam(params, "simulacion.programada.k", DEFAULT_K);
        final boolean sleepHabilitado = Boolean.parseBoolean(
                params.getProperty("simulacion.programada.sleep.habilitado", String.valueOf(DEFAULT_SLEEP_HABILITADO))
        );
        if (k <= 0L) {
            throw new IllegalStateException("simulacion.programada.k debe ser mayor a 0");
        }
        if (saSegundos <= 0L) {
            final Duration sc = Duration.ofSeconds(k * 15L);
            return new ConfiguracionProgramada(0L, k, sc, sleepHabilitado);
        }
        final long saMs = saSegundos * 1000L;
        final Duration sc = Duration.ofSeconds(saSegundos).multipliedBy(k);
        return new ConfiguracionProgramada(saMs, k, sc, sleepHabilitado);
    }

    static long calcularSaltoPlanificacionMs(final Properties params) {
        return construirConfiguracionProgramada(params).saMs();
    }

    static Duration calcularSaltoConsumoDatos(final Properties params) {
        return construirConfiguracionProgramada(params).sc();
    }

    static boolean excedeVentanaPlanificacion(final long taMs, final long saMs) {
        return taMs > saMs;
    }

    static LocalDateTime obtenerPrimerMomentoPedidos(final List<Pedido> pedidos) {
        for (final Pedido pedido : pedidos) {
            if (pedido != null && pedido.getFechaRegistro() != null) {
                return pedido.getFechaRegistro();
            }
        }
        return null;
    }

    static int incorporarPedidosHastaMomento(final List<Pedido> pedidosOrdenados,
                                             final int indiceInicial,
                                             final LocalDateTime finCicloDatos,
                                             final Map<String, List<Maleta>> maletasPorPedido,
                                             final List<Maleta> registradas) {
        int indiceActual = indiceInicial;
        while (indiceActual < pedidosOrdenados.size()) {
            final Pedido pedido = pedidosOrdenados.get(indiceActual);
            if (pedido == null) {
                indiceActual++;
                continue;
            }
            final LocalDateTime fechaRegistro = pedido.getFechaRegistro();
            if (fechaRegistro == null) {
                indiceActual++;
                continue;
            }
            if (fechaRegistro.isAfter(finCicloDatos)) {
                break;
            }
            final List<Maleta> nuevas = maletasPorPedido.getOrDefault(pedido.getIdPedido(), List.of());
            registradas.addAll(nuevas);
            indiceActual++;
        }
        return indiceActual;
    }

    private static boolean existenMaletasNoFinalizadas(final List<Maleta> registradas,
                                                       final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta,
                                                       final LocalDateTime currentTime,
                                                       final long tiempoRecojo) {
        for (final Maleta maleta : registradas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final EstadoMaletaTemporal estado = evaluarEstadoMaleta(
                    maleta,
                    rutasProgramadasPorMaleta.get(maleta.getIdMaleta()),
                    currentTime,
                    tiempoRecojo
            );
            if (estado.estado() != EstadoOperacionMaleta.FINALIZADA) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, EstadoMaletaTemporal> evaluarEstadosMaletas(
            final List<Maleta> registradas,
            final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta,
            final LocalDateTime currentTime,
            final long tiempoRecojo
    ) {
        final Map<String, EstadoMaletaTemporal> estados = new HashMap<>();
        for (final Maleta maleta : registradas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            estados.put(
                    maleta.getIdMaleta(),
                    evaluarEstadoMaleta(
                            maleta,
                            rutasProgramadasPorMaleta.get(maleta.getIdMaleta()),
                            currentTime,
                            tiempoRecojo
                    )
            );
        }
        return estados;
    }

    static EstadoMaletaTemporal evaluarEstadoMaleta(final Maleta maleta,
                                                    final RutaMaletaProgramada rutaProgramada,
                                                    final LocalDateTime currentTime,
                                                    final long tiempoRecojo) {
        final String aeropuertoOrigen = icaoOrigen(maleta);
        final String destinoFinal = maleta != null && maleta.getPedido() != null
                ? idAeropuerto(maleta.getPedido().getAeropuertoDestino())
                : null;
        if (rutaProgramada == null) {
            return new EstadoMaletaTemporal(
                    maleta,
                    maleta != null ? maleta.getIdMaleta() : null,
                    EstadoOperacionMaleta.SIN_RUTA,
                    aeropuertoOrigen,
                    List.of(),
                    List.of(),
                    true,
                    false
            );
        }
        final List<VueloInstancia> vuelos = rutaProgramada.vuelos();
        if (rutaProgramada.aeropuertoEspera() != null && (vuelos == null || vuelos.isEmpty())) {
            return new EstadoMaletaTemporal(
                    maleta,
                    maleta != null ? maleta.getIdMaleta() : null,
                    EstadoOperacionMaleta.SIN_RUTA,
                    rutaProgramada.aeropuertoEspera(),
                    List.of(),
                    List.of(),
                    true,
                    false
            );
        }
        String aeropuertoActual = rutaProgramada.aeropuertoEspera() != null
                ? rutaProgramada.aeropuertoEspera()
                : aeropuertoOrigen;
        final List<VueloInstancia> vuelosEjecutados = new ArrayList<>();
        if (vuelos == null || vuelos.isEmpty()) {
            return new EstadoMaletaTemporal(
                    maleta,
                    maleta != null ? maleta.getIdMaleta() : null,
                    EstadoOperacionMaleta.SIN_RUTA,
                    aeropuertoActual,
                    vuelosEjecutados,
                    List.of(),
                    true,
                    false
            );
        }
        for (int i = 0; i < vuelos.size(); i++) {
            final VueloInstancia vueloInstancia = vuelos.get(i);
            if (vueloInstancia == null) {
                continue;
            }
            final LocalDateTime fechaSalida = vueloInstancia.getFechaSalida();
            final LocalDateTime fechaLlegada = vueloInstancia.getFechaLlegada();
            if (fechaSalida != null && currentTime.isBefore(fechaSalida)) {
                final EstadoOperacionMaleta estado = vuelosEjecutados.isEmpty()
                        ? EstadoOperacionMaleta.ESPERANDO_ORIGEN
                        : EstadoOperacionMaleta.ESPERANDO_CONEXION;
                return new EstadoMaletaTemporal(
                        maleta,
                        maleta != null ? maleta.getIdMaleta() : null,
                        estado,
                        aeropuertoActual,
                        vuelosEjecutados,
                        new ArrayList<>(vuelos.subList(i, vuelos.size())),
                        true,
                        false
                );
            }
            final boolean vueloEnCurso = fechaSalida != null
                    && !currentTime.isBefore(fechaSalida)
                    && (fechaLlegada == null || currentTime.isBefore(fechaLlegada));
            if (vueloEnCurso) {
                return new EstadoMaletaTemporal(
                        maleta,
                        maleta != null ? maleta.getIdMaleta() : null,
                        EstadoOperacionMaleta.EN_TRANSITO,
                        null,
                        vuelosEjecutados,
                        new ArrayList<>(vuelos.subList(i, vuelos.size())),
                        false,
                        true
                );
            }
            if (fechaLlegada != null && !currentTime.isBefore(fechaLlegada)) {
                vuelosEjecutados.add(vueloInstancia);
                final String aeropuertoDestino = idAeropuerto(vueloInstancia.getAeropuertoDestino());
                if (aeropuertoDestino != null) {
                    aeropuertoActual = aeropuertoDestino;
                }
            }
        }
        if (rutaProgramada.aeropuertoEspera() != null && !rutaProgramada.aeropuertoEspera().equals(destinoFinal)) {
            return new EstadoMaletaTemporal(
                    maleta,
                    maleta != null ? maleta.getIdMaleta() : null,
                    EstadoOperacionMaleta.ESPERANDO_CONEXION,
                    rutaProgramada.aeropuertoEspera(),
                    vuelosEjecutados,
                    List.of(),
                    true,
                    false
            );
        }
        final boolean llegoDestinoFinal = destinoFinal == null || destinoFinal.equals(aeropuertoActual);
        if (llegoDestinoFinal) {
            final LocalDateTime salidaSistemaDestino = calcularSalidaSistemaDestino(vuelosEjecutados, tiempoRecojo);
            final boolean permaneceEnDestinoFinal = salidaSistemaDestino == null
                    || currentTime.isBefore(salidaSistemaDestino);
            if (permaneceEnDestinoFinal) {
                return new EstadoMaletaTemporal(
                        maleta,
                        maleta != null ? maleta.getIdMaleta() : null,
                        EstadoOperacionMaleta.EN_DESTINO_FINAL,
                        aeropuertoActual,
                        vuelosEjecutados,
                        List.of(),
                        false,
                        false
                );
            }
            return new EstadoMaletaTemporal(
                    maleta,
                    maleta != null ? maleta.getIdMaleta() : null,
                    EstadoOperacionMaleta.FINALIZADA,
                    aeropuertoActual,
                    vuelosEjecutados,
                    List.of(),
                    false,
                    false
            );
        }
        return new EstadoMaletaTemporal(
                maleta,
                maleta != null ? maleta.getIdMaleta() : null,
                EstadoOperacionMaleta.ESPERANDO_CONEXION,
                aeropuertoActual,
                vuelosEjecutados,
                List.of(),
                true,
                false
        );
    }

    private static LocalDateTime calcularSalidaSistemaDestino(final List<VueloInstancia> vuelosEjecutados,
                                                              final long tiempoRecojo) {
        if (vuelosEjecutados == null || vuelosEjecutados.isEmpty()) {
            return null;
        }
        final VueloInstancia ultimoVuelo = vuelosEjecutados.get(vuelosEjecutados.size() - 1);
        if (ultimoVuelo == null || ultimoVuelo.getFechaLlegada() == null) {
            return null;
        }
        return ultimoVuelo.getFechaLlegada().plusMinutes(Math.max(0L, tiempoRecojo));
    }

    private static List<Maleta> construirMaletasReplanificables(
            final List<Maleta> registradas,
            final Map<String, EstadoMaletaTemporal> estadosPorMaleta,
            final Map<String, Aeropuerto> indiceAeropuertos,
            final LocalDateTime currentTime,
            final boolean esGa,
            final LocalDateTime ventanaCompromisoGa
    ) {
        final List<Maleta> maletasReplanificables = new ArrayList<>();
        for (final Maleta maleta : registradas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final EstadoMaletaTemporal estado = estadosPorMaleta.get(maleta.getIdMaleta());
            if (!esReplanificableSegunAlgoritmo(estado, currentTime, esGa, ventanaCompromisoGa)) {
                continue;
            }
            maletasReplanificables.add(clonarMaletaReplanificable(maleta, estado, indiceAeropuertos, currentTime));
        }
        return maletasReplanificables;
    }

    private static Maleta clonarMaletaReplanificable(final Maleta maleta,
                                                     final EstadoMaletaTemporal estado,
                                                     final Map<String, Aeropuerto> indiceAeropuertos,
                                                     final LocalDateTime currentTime) {
        final Pedido pedidoOriginal = maleta.getPedido();
        final Aeropuerto origenActual = indiceAeropuertos.get(estado.aeropuertoActual());
        final Pedido pedidoClonado = new Pedido(
                pedidoOriginal != null ? pedidoOriginal.getIdPedido() : null,
                origenActual,
                pedidoOriginal != null ? pedidoOriginal.getAeropuertoDestino() : null,
                currentTime,
                pedidoOriginal != null ? pedidoOriginal.getFechaHoraPlazo() : null,
                pedidoOriginal != null ? pedidoOriginal.getCantidadMaletas() : 1,
                pedidoOriginal != null ? pedidoOriginal.getEstado() : null
        );
        return new Maleta(
                maleta.getIdMaleta(),
                pedidoClonado,
                currentTime,
                maleta.getFechaLlegada(),
                maleta.getEstado()
        );
    }

    private static void liberarCapacidadFuturaReplanificable(
            final Map<String, EstadoMaletaTemporal> estadosPorMaleta,
            final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta,
            final LocalDateTime currentTime,
            final boolean esGa,
            final LocalDateTime ventanaCompromisoGa
    ) {
        for (final Map.Entry<String, EstadoMaletaTemporal> entry : estadosPorMaleta.entrySet()) {
            final EstadoMaletaTemporal estado = entry.getValue();
            if (!esReplanificableSegunAlgoritmo(estado, currentTime, esGa, ventanaCompromisoGa)) {
                continue;
            }
            final RutaMaletaProgramada rutaProgramada = rutasProgramadasPorMaleta.get(entry.getKey());
            if (rutaProgramada == null) {
                continue;
            }
            for (final VueloInstancia vueloInstancia : estado.vuelosFuturos()) {
                liberarCapacidadVuelo(vueloInstancia);
            }
        }
    }

    private static void liberarCapacidadVuelo(final VueloInstancia vueloInstancia) {
        if (vueloInstancia == null) {
            return;
        }
        final int nuevaCapacidad = Math.min(
                vueloInstancia.getCapacidadMaxima(),
                vueloInstancia.getCapacidadDisponible() + 1
        );
        vueloInstancia.setCapacidadDisponible(nuevaCapacidad);
    }

    private static VentanaVuelos construirVentanaVuelos(final ArrayList<VueloInstancia> vuelosInstancia,
                                                        final ArrayList<VueloProgramado> vuelosProgramados,
                                                        final LocalDateTime currentTime,
                                                        final int ventanaDias) {
        final LocalDateTime ventanaFin = currentTime.plusDays(ventanaDias - 1L).with(LocalTime.MAX);
        final ArrayList<VueloInstancia> vuelosVentana = new ArrayList<>();
        final Set<String> idsProgramadosVentana = new HashSet<>();
        for (final VueloInstancia vueloInstancia : vuelosInstancia) {
            if (vueloInstancia.getFechaSalida() == null) {
                continue;
            }
            final boolean vueloDentroVentana =
                    !vueloInstancia.getFechaSalida().isBefore(currentTime)
                    && !vueloInstancia.getFechaSalida().isAfter(ventanaFin);
            if (!vueloDentroVentana) {
                continue;
            }
            vuelosVentana.add(vueloInstancia);
            if (vueloInstancia.getVueloProgramado() != null) {
                idsProgramadosVentana.add(vueloInstancia.getVueloProgramado().getIdVueloProgramado());
            }
        }

        final ArrayList<VueloProgramado> programadosVentana = new ArrayList<>();
        for (final VueloProgramado vueloProgramado : vuelosProgramados) {
            if (idsProgramadosVentana.contains(vueloProgramado.getIdVueloProgramado())) {
                programadosVentana.add(vueloProgramado);
            }
        }
        return new VentanaVuelos(vuelosVentana, programadosVentana);
    }

    private static Map<String, List<VueloInstancia>> extraerRutasConfirmadas(final Solucion solucion,
                                                                             final Map<String, VueloInstancia> vuelosPorId) {
        final Map<String, List<VueloInstancia>> rutasConfirmadas = new HashMap<>();
        if (solucion == null || solucion.getSolucion() == null) {
            return rutasConfirmadas;
        }
        for (final Ruta ruta : solucion.getSolucion()) {
            final boolean rutaFallida = ruta == null
                    || ruta.getEstado() == EstadoRuta.FALLIDA
                    || ruta.getSubrutas().isEmpty();
            if (rutaFallida) {
                continue;
            }
            final List<VueloInstancia> vuelosConfirmados = new ArrayList<>();
            for (final VueloInstancia vueloInstancia : ruta.getSubrutas()) {
                final VueloInstancia vueloReal = obtenerVueloReal(vuelosPorId, vueloInstancia);
                if (vueloReal == null) {
                    continue;
                }
                vuelosConfirmados.add(vueloReal);
            }
            if (!vuelosConfirmados.isEmpty() && ruta.getIdMaleta() != null) {
                rutasConfirmadas.put(ruta.getIdMaleta(), vuelosConfirmados);
            }
        }
        return rutasConfirmadas;
    }

    private static void aplicarRutasReplanificadas(
            final Map<String, EstadoMaletaTemporal> estadosPorMaleta,
            final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta,
            final Map<String, List<VueloInstancia>> nuevasRutas,
            final Set<String> maletasEnrutadas,
            final LocalDateTime currentTime,
            final boolean esGa,
            final LocalDateTime ventanaCompromisoGa
    ) {
        for (final Map.Entry<String, EstadoMaletaTemporal> entry : estadosPorMaleta.entrySet()) {
            final String idMaleta = entry.getKey();
            final EstadoMaletaTemporal estado = entry.getValue();
            if (!esReplanificableSegunAlgoritmo(estado, currentTime, esGa, ventanaCompromisoGa)) {
                continue;
            }
            final List<VueloInstancia> nuevaRuta = nuevasRutas.get(idMaleta);
            if (nuevaRuta == null || nuevaRuta.isEmpty()) {
                actualizarRutaSinSolucion(rutasProgramadasPorMaleta, estado, esGa);
                continue;
            }
            final List<VueloInstancia> rutaFusionada = new ArrayList<>(estado.vuelosEjecutados());
            rutaFusionada.addAll(nuevaRuta);
            rutasProgramadasPorMaleta.put(idMaleta, new RutaMaletaProgramada(rutaFusionada, null));
            maletasEnrutadas.add(idMaleta);
        }
    }

    private static void actualizarRutaSinSolucion(
            final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta,
            final EstadoMaletaTemporal estado,
            final boolean esGa
    ) {
        if (esGa && rutaProgramadaSigueSiendoCoherente(estado)) {
            return;
        }
        if (estado.vuelosEjecutados().isEmpty()) {
            if (estado.estado() == EstadoOperacionMaleta.ESPERANDO_CONEXION) {
                rutasProgramadasPorMaleta.put(
                        estado.idMaleta(),
                        new RutaMaletaProgramada(List.of(), estado.aeropuertoActual())
                );
                return;
            }
            rutasProgramadasPorMaleta.remove(estado.idMaleta());
            return;
        }
        rutasProgramadasPorMaleta.put(
                estado.idMaleta(),
                new RutaMaletaProgramada(new ArrayList<>(estado.vuelosEjecutados()), estado.aeropuertoActual())
        );
    }

    private static boolean esReplanificableSegunAlgoritmo(final EstadoMaletaTemporal estado,
                                                           final LocalDateTime currentTime,
                                                           final boolean esGa,
                                                           final LocalDateTime ventanaCompromisoGa) {
        if (estado == null || !estado.replanificable()) {
            return false;
        }
        if (!esGa) {
            return true;
        }
        return !tieneRutaComprometidaGa(estado, currentTime, ventanaCompromisoGa);
    }

    private static boolean tieneRutaComprometidaGa(final EstadoMaletaTemporal estado,
                                                   final LocalDateTime currentTime,
                                                   final LocalDateTime ventanaCompromisoGa) {
        if (estado == null || estado.vuelosFuturos() == null || estado.vuelosFuturos().isEmpty()) {
            return false;
        }
        if (currentTime == null || ventanaCompromisoGa == null) {
            return false;
        }
        final VueloInstancia siguienteVuelo = estado.vuelosFuturos().get(0);
        if (siguienteVuelo == null || siguienteVuelo.getFechaSalida() == null) {
            return false;
        }
        final LocalDateTime primeraSalida = siguienteVuelo.getFechaSalida();
        return !primeraSalida.isBefore(currentTime) && !primeraSalida.isAfter(ventanaCompromisoGa);
    }

    private static boolean rutaProgramadaSigueSiendoCoherente(final EstadoMaletaTemporal estado) {
        if (estado == null) {
            return false;
        }
        final List<VueloInstancia> rutaCompleta = new ArrayList<>();
        rutaCompleta.addAll(estado.vuelosEjecutados());
        rutaCompleta.addAll(estado.vuelosFuturos());
        if (rutaCompleta.isEmpty()) {
            return false;
        }
        for (int i = 0; i < rutaCompleta.size(); i++) {
            final VueloInstancia actual = rutaCompleta.get(i);
            if (actual == null || actual.getEstado() == EstadoVuelo.CANCELADO) {
                return false;
            }
            if (i == rutaCompleta.size() - 1) {
                continue;
            }
            final VueloInstancia siguiente = rutaCompleta.get(i + 1);
            if (siguiente == null || actual.getAeropuertoDestino() == null || siguiente.getAeropuertoOrigen() == null) {
                return false;
            }
            final String destinoActual = actual.getAeropuertoDestino().getIdAeropuerto();
            final String origenSiguiente = siguiente.getAeropuertoOrigen().getIdAeropuerto();
            if (destinoActual == null || !destinoActual.equals(origenSiguiente)) {
                return false;
            }
            if (actual.getFechaLlegada() != null
                    && siguiente.getFechaSalida() != null
                    && actual.getFechaLlegada().isAfter(siguiente.getFechaSalida())) {
                return false;
            }
        }
        return true;
    }

    private static boolean esMaletaSeguimiento(final String idMaleta) {
        return idMaleta != null && idMaleta.startsWith(PREFIJO_DIAGNOSTICO_MALETA);
    }

    private static void reportarDiagnosticoMaletasNoRuteadasFinal(final String nombre,
                                                                  final List<MaletaNoRuteada> noRuteadas,
                                                                  final Map<String, EstadoMaletaTemporal> estadosFinales) {
        if (noRuteadas == null || noRuteadas.isEmpty() || estadosFinales == null || estadosFinales.isEmpty()) {
            return;
        }
        for (final MaletaNoRuteada maletaNoRuteada : noRuteadas) {
            if (maletaNoRuteada == null || !esMaletaSeguimiento(maletaNoRuteada.idMaleta())) {
                continue;
            }
            final EstadoMaletaTemporal estado = estadosFinales.get(maletaNoRuteada.idMaleta());
            if (estado == null) {
                continue;
            }
            final String aeropuertoActual = estado.aeropuertoActual() != null ? estado.aeropuertoActual() : "-";
            System.out.printf(
                    "[DIAG FINAL %s] maleta=%s motivo=%s estado=%s aeropuerto=%s ejecutados=%d futuros=%d replanificable=%s plazo=%s%n",
                    nombre,
                    maletaNoRuteada.idMaleta(),
                    maletaNoRuteada.motivo(),
                    estado.estado(),
                    aeropuertoActual,
                    estado.vuelosEjecutados().size(),
                    estado.vuelosFuturos().size(),
                    estado.replanificable(),
                    maletaNoRuteada.plazo()
            );
        }
    }

    private static void reservarCapacidadRutas(final Map<String, List<VueloInstancia>> nuevasRutas) {
        for (final List<VueloInstancia> ruta : nuevasRutas.values()) {
            for (final VueloInstancia vueloInstancia : ruta) {
                if (vueloInstancia == null || vueloInstancia.getCapacidadDisponible() <= 0) {
                    continue;
                }
                try {
                    vueloInstancia.actualizarCapacidad(1);
                } catch (final IllegalStateException ignored) {
                    // no-op
                }
            }
        }
    }

    private static List<MaletaNoRuteada> identificarNoRuteadas(
            final Map<String, EstadoMaletaTemporal> estadosPorMaleta,
            final LocalDateTime momentoFinal) {
        final List<MaletaNoRuteada> noRuteadas = new ArrayList<>();
        for (final EstadoMaletaTemporal estado : estadosPorMaleta.values()) {
            if (estado == null) {
                continue;
            }
            if (estado.estado() == EstadoOperacionMaleta.FINALIZADA
                    || estado.estado() == EstadoOperacionMaleta.EN_DESTINO_FINAL
                    || estado.estado() == EstadoOperacionMaleta.EN_TRANSITO) {
                continue;
            }
            if (!estado.vuelosFuturos().isEmpty()) {
                continue;
            }
            final Pedido pedido = estado.maleta() != null ? estado.maleta().getPedido() : null;
            final LocalDateTime plazo = pedido != null ? pedido.getFechaHoraPlazo() : null;
            final String destino = pedido != null ? idAeropuerto(pedido.getAeropuertoDestino()) : null;
            final MotivoNoRuteada motivo;
            if (plazo != null && !plazo.isAfter(momentoFinal)) {
                motivo = MotivoNoRuteada.PLAZO_VENCIDO;
            } else if (!estado.vuelosEjecutados().isEmpty()) {
                motivo = MotivoNoRuteada.RUTA_INCOMPLETA;
            } else {
                motivo = MotivoNoRuteada.SIN_RUTA;
            }
            noRuteadas.add(new MaletaNoRuteada(estado.idMaleta(), estado.aeropuertoActual(), destino, plazo, motivo));
        }
        return noRuteadas;
    }

    private static TrazabilidadCiclo construirTrazabilidadCiclo(final int numeroCiclo,
                                                                final LocalDateTime inicioCicloDatos,
                                                                final LocalDateTime finCicloDatos,
                                                                final int nuevasEnCiclo,
                                                                final int enrutadasVigentes,
                                                                final int rutasConfirmadasEnCiclo,
                                                                final int pendientesSinRuta,
                                                                final List<MaletaNoRuteada> noRuteadasCiclo,
                                                                final Map<String, EstadoMaletaTemporal> estadosAntes,
                                                                final Map<String, EstadoMaletaTemporal> estadosDespues,
                                                                final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta) {
        final List<TrazabilidadMaletaPendiente> maletas = new ArrayList<>(noRuteadasCiclo.size());
        for (final MaletaNoRuteada noRuteada : noRuteadasCiclo) {
            if (noRuteada == null || noRuteada.idMaleta() == null) {
                continue;
            }
            final EstadoMaletaTemporal estadoAntes = estadosAntes.get(noRuteada.idMaleta());
            final EstadoMaletaTemporal estadoDespues = estadosDespues.get(noRuteada.idMaleta());
            final String rutaPrevia = estadoAntes != null
                    ? combinarRutas(estadoAntes.vuelosEjecutados(), estadoAntes.vuelosFuturos())
                    : "[]";
            final String rutaFuturaPerdida = estadoAntes != null
                    ? formatearRutaVuelos(estadoAntes.vuelosFuturos())
                    : "[]";
            final String rutaActual = estadoDespues != null
                    ? combinarRutas(estadoDespues.vuelosEjecutados(), estadoDespues.vuelosFuturos())
                    : "[]";
            final String maletasBeneficiadas = identificarMaletasQueUsanRutaPrevia(
                    noRuteada.idMaleta(),
                    estadoAntes,
                    rutasProgramadasPorMaleta
            );
            maletas.add(new TrazabilidadMaletaPendiente(
                    noRuteada.idMaleta(),
                    noRuteada.aeropuertoActual(),
                    noRuteada.aeropuertoDestino(),
                    noRuteada.plazo(),
                    noRuteada.motivo(),
                    estadoAntes != null ? estadoAntes.estado() : null,
                    estadoDespues != null ? estadoDespues.estado() : null,
                    rutaPrevia,
                    rutaFuturaPerdida,
                    rutaActual,
                    maletasBeneficiadas
            ));
        }
        return new TrazabilidadCiclo(
                numeroCiclo,
                inicioCicloDatos,
                finCicloDatos,
                nuevasEnCiclo,
                enrutadasVigentes,
                rutasConfirmadasEnCiclo,
                pendientesSinRuta,
                maletas
        );
    }

    private static String combinarRutas(final List<VueloInstancia> ejecutados, final List<VueloInstancia> futuros) {
        final List<VueloInstancia> ruta = new ArrayList<>();
        if (ejecutados != null) {
            ruta.addAll(ejecutados);
        }
        if (futuros != null) {
            ruta.addAll(futuros);
        }
        return formatearRutaVuelos(ruta);
    }

    private static String identificarMaletasQueUsanRutaPrevia(final String idMaletaPendiente,
                                                              final EstadoMaletaTemporal estadoAntes,
                                                              final Map<String, RutaMaletaProgramada> rutasProgramadasPorMaleta) {
        if (estadoAntes == null || estadoAntes.vuelosFuturos() == null || estadoAntes.vuelosFuturos().isEmpty()) {
            return "sin ruta previa futura";
        }
        final Set<String> vuelosPrevios = new HashSet<>();
        for (final VueloInstancia vueloInstancia : estadoAntes.vuelosFuturos()) {
            if (vueloInstancia != null && vueloInstancia.getIdVueloInstancia() != null) {
                vuelosPrevios.add(vueloInstancia.getIdVueloInstancia());
            }
        }
        if (vuelosPrevios.isEmpty()) {
            return "sin ruta previa futura";
        }
        final List<String> maletasBeneficiadas = new ArrayList<>();
        for (final Map.Entry<String, RutaMaletaProgramada> entry : rutasProgramadasPorMaleta.entrySet()) {
            if (entry.getKey() == null || entry.getKey().equals(idMaletaPendiente) || entry.getValue() == null) {
                continue;
            }
            final List<VueloInstancia> vuelos = entry.getValue().vuelos();
            if (vuelos == null || vuelos.isEmpty()) {
                continue;
            }
            boolean comparteVuelo = false;
            for (final VueloInstancia vueloInstancia : vuelos) {
                if (vueloInstancia != null
                        && vueloInstancia.getIdVueloInstancia() != null
                        && vuelosPrevios.contains(vueloInstancia.getIdVueloInstancia())) {
                    comparteVuelo = true;
                    break;
                }
            }
            if (comparteVuelo) {
                maletasBeneficiadas.add(entry.getKey());
            }
        }
        if (maletasBeneficiadas.isEmpty()) {
            return "sin evidencia de ruta robada por otra maleta";
        }
        maletasBeneficiadas.sort(String::compareTo);
        return String.join(", ", maletasBeneficiadas);
    }

    private static int contarMaletasRuteadasFinales(final Map<String, EstadoMaletaTemporal> estadosPorMaleta) {
        if (estadosPorMaleta == null || estadosPorMaleta.isEmpty()) {
            return 0;
        }
        int totalRuteadas = 0;
        for (final EstadoMaletaTemporal estado : estadosPorMaleta.values()) {
            if (estado == null) {
                continue;
            }
            final boolean tieneRutaActivaOFinalizada = estado.estado() == EstadoOperacionMaleta.FINALIZADA
                    || estado.estado() == EstadoOperacionMaleta.EN_DESTINO_FINAL
                    || estado.estado() == EstadoOperacionMaleta.EN_TRANSITO
                    || !estado.vuelosFuturos().isEmpty()
                    || !estado.vuelosEjecutados().isEmpty();
            if (tieneRutaActivaOFinalizada) {
                totalRuteadas++;
            }
        }
        return totalRuteadas;
    }

    private static DetalleColapso detectarDetalleColapso(final Map<String, EstadoMaletaTemporal> estadosPorMaleta,
                                                         final ArrayList<Aeropuerto> aeropuertos,
                                                         final ArrayList<VueloInstancia> vuelosInstancia,
                                                         final LocalDateTime inicioCicloDatos,
                                                         final LocalDateTime currentTime) {
        final String colapsoCapacidadAeropuerto = detectarColapsoCapacidadAeropuertos(aeropuertos);
        if (colapsoCapacidadAeropuerto != null) {
            return new DetalleColapso(colapsoCapacidadAeropuerto, null, null);
        }
        final String colapsoCapacidadVuelo = detectarColapsoCapacidadVuelos(vuelosInstancia);
        if (colapsoCapacidadVuelo != null) {
            return new DetalleColapso(colapsoCapacidadVuelo, null, null);
        }
        for (final EstadoMaletaTemporal estado : estadosPorMaleta.values()) {
            if (estado == null || estado.maleta() == null) {
                continue;
            }
            if (estado.estado() == EstadoOperacionMaleta.FINALIZADA
                    || estado.estado() == EstadoOperacionMaleta.EN_DESTINO_FINAL
                    || estado.estado() == EstadoOperacionMaleta.EN_TRANSITO) {
                continue;
            }
            if (!estado.vuelosFuturos().isEmpty()) {
                continue;
            }
            final Pedido pedido = estado.maleta().getPedido();
            final LocalDateTime plazo = pedido != null ? pedido.getFechaHoraPlazo() : null;
            final boolean plazoVencidoAntesDelCiclo = plazo != null && !plazo.isAfter(inicioCicloDatos);
            if (plazoVencidoAntesDelCiclo) {
                return new DetalleColapso("plazo vencido para maleta " + estado.idMaleta(), estado, currentTime);
            }
        }
        return null;
    }

    private static String detectarColapsoCapacidadAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null) {
                continue;
            }
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            final int ocupacion = aeropuerto.getMaletasActuales();
            final boolean excedeCapacidad = capacidad >= 0 && ocupacion > capacidad;
            if (excedeCapacidad) {
                return "capacidad de aeropuerto excedida en "
                        + aeropuerto.getIdAeropuerto()
                        + " (ocupacion=" + ocupacion + ", capacidad=" + capacidad + ")";
            }
        }
        return null;
    }

    private static String detectarColapsoCapacidadVuelos(final ArrayList<VueloInstancia> vuelosInstancia) {
        for (final VueloInstancia vueloInstancia : vuelosInstancia) {
            if (vueloInstancia == null) {
                continue;
            }
            final int capacidadDisponible = vueloInstancia.getCapacidadDisponible();
            final int capacidadMaxima = vueloInstancia.getCapacidadMaxima();
            final boolean capacidadInvalida = capacidadDisponible < 0 || capacidadDisponible > capacidadMaxima;
            if (capacidadInvalida) {
                return "capacidad de vuelo invalida en "
                        + vueloInstancia.getIdVueloInstancia()
                        + " (disponible=" + capacidadDisponible + ", maxima=" + capacidadMaxima + ")";
            }
        }
        return null;
    }

    private static void reportarResumenCiclo(final String nombre,
                                             final int numeroCiclo,
                                             final LocalDateTime inicioCicloDatos,
                                             final LocalDateTime finCicloDatos,
                                             final int nuevasEnCiclo,
                                             final int enrutadasAcumuladas,
                                             final int rutasConfirmadasEnCiclo,
                                             final int pendientes,
                                             final double fitnessAlgoritmoCiclo,
                                             final double fitnessCiclo,
                                             final long taMs,
                                             final long saMs) {
        System.out.printf(
                "[CICLO %s #%d] datos=%s -> %s nuevas=%d enrutadas=%d rutasCiclo=%d pendientes=%d fitAlg=%.6f fitExp=%.3f TA=%d ms SA=%d ms%n",
                nombre,
                numeroCiclo,
                inicioCicloDatos.truncatedTo(ChronoUnit.SECONDS),
                finCicloDatos.truncatedTo(ChronoUnit.SECONDS),
                nuevasEnCiclo,
                enrutadasAcumuladas,
                rutasConfirmadasEnCiclo,
                pendientes,
                fitnessAlgoritmoCiclo,
                fitnessCiclo,
                taMs,
                saMs
        );
    }

    private static void reportarCapacidadAeropuertosCiclo(final String nombre,
                                                          final int numeroCiclo,
                                                          final ArrayList<Aeropuerto> aeropuertos) {
        System.out.printf("Capacidad aeropuertos despues del ciclo [%s #%d]:%n", nombre, numeroCiclo);
        System.out.printf("  %-10s %10s %10s %12s%n",
                "Aeropuerto", "Ocupacion", "Capacidad", "Porcentaje");
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null) {
                continue;
            }
            final int ocupacionAeropuerto = aeropuerto.getMaletasActuales();
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            final double porcentaje = capacidad > 0 ? 100.0 * ocupacionAeropuerto / capacidad : 0.0;
            System.out.printf("  %-10s %10d %10d %11.1f%%%n",
                    aeropuerto.getIdAeropuerto(),
                    ocupacionAeropuerto,
                    capacidad,
                    porcentaje);
        }
    }

    private static void reportarDiagnosticoColapso(final DetalleColapso detalleColapso) {
        if (detalleColapso == null || detalleColapso.estadoMaleta() == null || detalleColapso.estadoMaleta().maleta() == null) {
            return;
        }
        final EstadoMaletaTemporal estado = detalleColapso.estadoMaleta();
        final Maleta maleta = estado.maleta();
        final Pedido pedido = maleta.getPedido();
        final String origen = pedido != null && pedido.getAeropuertoOrigen() != null
                ? pedido.getAeropuertoOrigen().getIdAeropuerto()
                : "-";
        final String destino = pedido != null && pedido.getAeropuertoDestino() != null
                ? pedido.getAeropuertoDestino().getIdAeropuerto()
                : "-";
        final LocalDateTime fechaRegistro = maleta.getFechaRegistro();
        final LocalDateTime fechaHoraPlazo = pedido != null ? pedido.getFechaHoraPlazo() : null;

        System.out.println("Diagnostico de maleta colapsada:");
        System.out.printf("  Maleta: %s%n", maleta.getIdMaleta());
        System.out.printf("  Origen: %s%n", origen);
        System.out.printf("  Destino: %s%n", destino);
        System.out.printf("  FechaRegistro: %s%n", fechaRegistro);
        System.out.printf("  FechaHoraPlazo: %s%n", fechaHoraPlazo);
        System.out.printf("  Aeropuerto actual: %s%n", estado.aeropuertoActual() != null ? estado.aeropuertoActual() : "-");
        System.out.printf("  Estado temporal: %s%n", estado.estado());
        System.out.printf("  Ruta parcial que tenia: %s%n", formatearRutaVuelos(estado.vuelosEjecutados()));
        System.out.printf("  Vuelos futuros que intento usar: %s%n", formatearRutaVuelos(estado.vuelosFuturos()));
        if (detalleColapso.momento() != null) {
            System.out.printf("  Momento del colapso: %s%n", detalleColapso.momento());
        }
    }

    private static String formatearRutaVuelos(final List<VueloInstancia> vuelos) {
        if (vuelos == null || vuelos.isEmpty()) {
            return "[]";
        }
        final List<String> segmentos = new ArrayList<>();
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo == null) {
                continue;
            }
            final String codigo = vuelo.getCodigo() != null ? vuelo.getCodigo() : vuelo.getIdVueloInstancia();
            final String origen = idAeropuerto(vuelo.getAeropuertoOrigen());
            final String destino = idAeropuerto(vuelo.getAeropuertoDestino());
            segmentos.add(String.format(
                    "%s(%s->%s %s/%s)",
                    codigo,
                    origen != null ? origen : "-",
                    destino != null ? destino : "-",
                    vuelo.getFechaSalida(),
                    vuelo.getFechaLlegada()
            ));
        }
        return segmentos.toString();
    }

    private static void terminarPorTiempoExcedido(final long taMs, final long saMs) {
        System.out.printf("[COLAPSO] TA excedio SA. TA=%d ms (%.3f s), SA=%d ms (%.3f s). Finalizando simulacion.%n",
                taMs, taMs / 1000D, saMs, saMs / 1000D);
        terminadorPrograma.run();
    }

    private static void dormirHastaSiguienteCiclo(final ConfiguracionProgramada configuracionProgramada,
                                                  final long taMs) {
        if (!configuracionProgramada.sleepHabilitado() || taMs >= configuracionProgramada.saMs()) {
            return;
        }
        final long tiempoDormirMs = configuracionProgramada.saMs() - taMs;
        try {
            Thread.sleep(tiempoDormirMs);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La simulacion programada fue interrumpida", exception);
        }
    }

    static void setTerminadorPrograma(final Runnable nuevoTerminador) {
        terminadorPrograma = nuevoTerminador != null ? nuevoTerminador : () -> System.exit(0);
    }

    static void restaurarTerminadorPrograma() {
        terminadorPrograma = () -> System.exit(0);
    }

    private static String icaoOrigen(final Maleta maleta) {
        final boolean origenInvalido = maleta.getPedido() == null || maleta.getPedido().getAeropuertoOrigen() == null;
        if (origenInvalido) {
            return null;
        }
        return maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto();
    }

    private static String idAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto == null ? null : aeropuerto.getIdAeropuerto();
    }

    private static Solucion obtenerSolucion(final Metaheuristico algoritmo) {
        if (algoritmo instanceof GA) {
            return ((GA) algoritmo).getMejorSolucion();
        }
        if (algoritmo instanceof ACO) {
            return ((ACO) algoritmo).getUltimaSolucion();
        }
        return null;
    }

    private static Map<String, VueloInstancia> indexarVuelosInstancia(final ArrayList<VueloInstancia> vuelos) {
        final Map<String, VueloInstancia> vuelosPorId = new HashMap<>();
        if (vuelos == null || vuelos.isEmpty()) {
            return vuelosPorId;
        }
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            vuelosPorId.put(vuelo.getIdVueloInstancia(), vuelo);
        }
        return vuelosPorId;
    }

    private static VueloInstancia obtenerVueloReal(final Map<String, VueloInstancia> vuelosPorId,
                                                   final VueloInstancia vueloRuta) {
        if (vueloRuta == null || vueloRuta.getIdVueloInstancia() == null) {
            return null;
        }
        return vuelosPorId.get(vueloRuta.getIdVueloInstancia());
    }

    private static void postprocesarSolucion(final Solucion solucion, final InstanciaProblema instancia,
                                             final ParametrosGA parametrosGa) {
        if (solucion == null || instancia == null || parametrosGa == null) {
            return;
        }
        FuncionCosto.calcularCostoSolucion(solucion, instancia, parametrosGa);
        solucion.setSemaforo(CalculadorSemaforo.clasificarGlobal(solucion, instancia, parametrosGa));
    }

    private static ContextoEjecucion crearContextoEjecucion(final ContextoSimulacion contexto) {
        final ArrayList<Aeropuerto> aeropuertos = clonarAeropuertos(contexto.aeropuertos());
        final Map<String, Aeropuerto> indiceAeropuertos = indexarAeropuertos(aeropuertos);
        final ArrayList<VueloProgramado> vuelosProgramados = clonarVuelosProgramados(
                contexto.todosVuelosProgramados(),
                indiceAeropuertos
        );
        final Map<String, VueloProgramado> vuelosProgramadosPorId = indexarVuelosProgramados(vuelosProgramados);
        final ArrayList<VueloInstancia> vuelosInstancia = clonarVuelosInstancia(
                contexto.todosVuelosInstancia(),
                indiceAeropuertos,
                vuelosProgramadosPorId
        );
        return new ContextoEjecucion(aeropuertos, indiceAeropuertos, vuelosProgramados, vuelosInstancia);
    }

    private static ArrayList<Aeropuerto> clonarAeropuertos(final ArrayList<Aeropuerto> originales) {
        final ArrayList<Aeropuerto> clon = new ArrayList<>(originales.size());
        for (final Aeropuerto aeropuerto : originales) {
            if (aeropuerto == null) {
                continue;
            }
            clon.add(new Aeropuerto(
                    aeropuerto.getIdAeropuerto(),
                    aeropuerto.getCiudad(),
                    aeropuerto.getCapacidadAlmacen(),
                    aeropuerto.getMaletasActuales(),
                    aeropuerto.getLongitud(),
                    aeropuerto.getLatitud(),
                    aeropuerto.getHusoGMT()
            ));
        }
        return clon;
    }

    private static Map<String, Aeropuerto> indexarAeropuertos(final ArrayList<Aeropuerto> aeropuertos) {
        final Map<String, Aeropuerto> indice = new HashMap<>();
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                continue;
            }
            indice.put(aeropuerto.getIdAeropuerto(), aeropuerto);
        }
        return indice;
    }

    private static ArrayList<VueloProgramado> clonarVuelosProgramados(
            final ArrayList<VueloProgramado> originales,
            final Map<String, Aeropuerto> indiceAeropuertos
    ) {
        final ArrayList<VueloProgramado> clon = new ArrayList<>(originales.size());
        for (final VueloProgramado vueloProgramado : originales) {
            if (vueloProgramado == null) {
                continue;
            }
            clon.add(new VueloProgramado(
                    vueloProgramado.getIdVueloProgramado(),
                    vueloProgramado.getCodigo(),
                    vueloProgramado.getHoraSalida(),
                    vueloProgramado.getHoraLlegada(),
                    vueloProgramado.getCapacidadBase(),
                    obtenerAeropuertoClonado(indiceAeropuertos, vueloProgramado.getAeropuertoOrigen()),
                    obtenerAeropuertoClonado(indiceAeropuertos, vueloProgramado.getAeropuertoDestino())
            ));
        }
        return clon;
    }

    private static Map<String, VueloProgramado> indexarVuelosProgramados(
            final ArrayList<VueloProgramado> vuelosProgramados
    ) {
        final Map<String, VueloProgramado> indice = new HashMap<>();
        for (final VueloProgramado vueloProgramado : vuelosProgramados) {
            if (vueloProgramado == null || vueloProgramado.getIdVueloProgramado() == null) {
                continue;
            }
            indice.put(vueloProgramado.getIdVueloProgramado(), vueloProgramado);
        }
        return indice;
    }

    private static ArrayList<VueloInstancia> clonarVuelosInstancia(
            final ArrayList<VueloInstancia> originales,
            final Map<String, Aeropuerto> indiceAeropuertos,
            final Map<String, VueloProgramado> vuelosProgramadosPorId
    ) {
        final ArrayList<VueloInstancia> clon = new ArrayList<>(originales.size());
        for (final VueloInstancia vueloInstancia : originales) {
            if (vueloInstancia == null) {
                continue;
            }
            final VueloProgramado vueloProgramado = obtenerVueloProgramadoClonado(
                    vuelosProgramadosPorId,
                    vueloInstancia.getVueloProgramado()
            );
            clon.add(new VueloInstancia(
                    vueloInstancia.getIdVueloInstancia(),
                    vueloInstancia.getCodigo(),
                    vueloProgramado,
                    vueloInstancia.getFechaOperacion(),
                    vueloInstancia.getFechaSalida(),
                    vueloInstancia.getFechaLlegada(),
                    vueloInstancia.getCapacidadMaxima(),
                    vueloInstancia.getCapacidadDisponible(),
                    obtenerAeropuertoClonado(indiceAeropuertos, vueloInstancia.getAeropuertoOrigen()),
                    obtenerAeropuertoClonado(indiceAeropuertos, vueloInstancia.getAeropuertoDestino()),
                    vueloInstancia.getEstado()
            ));
        }
        return clon;
    }

    private static Aeropuerto obtenerAeropuertoClonado(final Map<String, Aeropuerto> indiceAeropuertos,
                                                       final Aeropuerto aeropuertoOriginal) {
        if (aeropuertoOriginal == null || aeropuertoOriginal.getIdAeropuerto() == null) {
            return null;
        }
        return indiceAeropuertos.get(aeropuertoOriginal.getIdAeropuerto());
    }

    private static VueloProgramado obtenerVueloProgramadoClonado(
            final Map<String, VueloProgramado> vuelosProgramadosPorId,
            final VueloProgramado vueloProgramadoOriginal
    ) {
        if (vueloProgramadoOriginal == null || vueloProgramadoOriginal.getIdVueloProgramado() == null) {
            return null;
        }
        return vuelosProgramadosPorId.get(vueloProgramadoOriginal.getIdVueloProgramado());
    }

    private static void reportarResumenDiario(final String nombre, final LocalDate dia,
                                              final int nuevas, final int enrutadas,
                                              final int pendientes,
                                              final ArrayList<Aeropuerto> aeropuertos) {
        System.out.printf("%n=== RESUMEN DIA [%s] [%s] ===%n", nombre, dia);
        System.out.printf("  Maletas nuevas:     %d%n", nuevas);
        System.out.printf("  Maletas enrutadas:  %d%n", enrutadas);
        System.out.printf("  Maletas pendientes: %d%n", pendientes);
        System.out.println();
        System.out.printf("  %-10s %10s %10s %12s%n",
                "Aeropuerto", "Ocupacion", "Capacidad", "Porcentaje");
        for (final Aeropuerto aeropuerto : aeropuertos) {
            final int ocupacionAeropuerto = aeropuerto.getMaletasActuales();
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            final double porcentaje = capacidad > 0 ? 100.0 * ocupacionAeropuerto / capacidad : 0.0;
            System.out.printf("  %-10s %10d %10d %11.1f%%%n",
                    aeropuerto.getIdAeropuerto(), ocupacionAeropuerto, capacidad, porcentaje);
        }
        System.out.println();
    }

    private static void reportarResumen(final String nombre, final ResultadoSimulacion resultado) {
        System.out.printf("%n--- RESUMEN [%s] ---%n", nombre);
        System.out.printf("Estado:          %s%n",
                resultado.colapsada
                        ? "COLAPSADA (momento " + resultado.momentoColapso + ")"
                        : "COMPLETADA");
        System.out.printf("Total enrutadas:      %d%n", resultado.totalMaletasEnrutadas);
        System.out.printf("No ruteadas al final: %d%n", resultado.totalMaletasPendientes);
        reportarDetalleNoRuteadas(resultado.detalleNoRuteadas);
        System.out.printf("Tiempo ejecucion:%d ms (%.3f s)%n",
                resultado.tiempoEjecucionMs,
                resultado.tiempoEjecucionMs / 1000D
        );
        System.out.printf("TA maximo:       %d ms (%.3f s)%n",
                resultado.maxTaMs,
                resultado.maxTaMs / 1000D
        );
        System.out.printf("Porcentaje maximo llenado vuelos:      %.1f%%%n", resultado.maxOcupacionVuelos);
        System.out.printf("Porcentaje maximo llenado aeropuertos: %.1f%%%n", resultado.maxOcupacionAeropuertos);
        reportarFitnessExperimental(resultado.fitnessExperimental);
        System.out.println();
    }

    private static void reportarExperimento(final List<ResultadoIteracion> historial,
                                             final int iteraciones,
                                             final boolean conGA,
                                             final boolean conACO) {
        System.out.printf("%n=== RESUMEN EXPERIMENTO (%d iteraciones) ===%n", iteraciones);
        if (conGA) {
            final long completadas = historial.stream()
                    .filter(r -> r.resGA() != null && !r.resGA().colapsada).count();
            final double avgEnr = historial.stream()
                    .filter(r -> r.resGA() != null)
                    .mapToInt(r -> r.resGA().totalMaletasEnrutadas).average().orElse(0);
            final double avgMs = historial.stream()
                    .filter(r -> r.resGA() != null)
                    .mapToLong(r -> r.resGA().tiempoEjecucionMs).average().orElse(0);
            final long maxTaGa = historial.stream()
                    .filter(r -> r.resGA() != null)
                    .mapToLong(r -> r.resGA().maxTaMs)
                    .max().orElse(0L);
            final double maxVuelosGa = historial.stream()
                    .filter(r -> r.resGA() != null)
                    .mapToDouble(r -> r.resGA().maxOcupacionVuelos)
                    .max().orElse(0.0);
            final double maxAeropuertosGa = historial.stream()
                    .filter(r -> r.resGA() != null)
                    .mapToDouble(r -> r.resGA().maxOcupacionAeropuertos)
                    .max().orElse(0.0);
            System.out.printf("GA  — completadas: %d/%d (%.1f%%)  enr_prom: %.1f  t_prom: %.0f ms%n",
                    completadas, iteraciones, 100.0 * completadas / iteraciones, avgEnr, avgMs);
            System.out.printf("GA  — TA maximo: %d ms (%.3f s)%n", maxTaGa, maxTaGa / 1000D);
            System.out.printf("GA  — Porcentaje maximo llenado vuelos: %.1f%%  aeropuertos: %.1f%%%n",
                    maxVuelosGa, maxAeropuertosGa);
        }
        if (conACO) {
            final long completadas = historial.stream()
                    .filter(r -> r.resACO() != null && !r.resACO().colapsada).count();
            final double avgEnr = historial.stream()
                    .filter(r -> r.resACO() != null)
                    .mapToInt(r -> r.resACO().totalMaletasEnrutadas).average().orElse(0);
            final double avgMs = historial.stream()
                    .filter(r -> r.resACO() != null)
                    .mapToLong(r -> r.resACO().tiempoEjecucionMs).average().orElse(0);
            final long maxTaAco = historial.stream()
                    .filter(r -> r.resACO() != null)
                    .mapToLong(r -> r.resACO().maxTaMs)
                    .max().orElse(0L);
            final double maxVuelosAco = historial.stream()
                    .filter(r -> r.resACO() != null)
                    .mapToDouble(r -> r.resACO().maxOcupacionVuelos)
                    .max().orElse(0.0);
            final double maxAeropuertosAco = historial.stream()
                    .filter(r -> r.resACO() != null)
                    .mapToDouble(r -> r.resACO().maxOcupacionAeropuertos)
                    .max().orElse(0.0);
            System.out.printf("ACO — completadas: %d/%d (%.1f%%)  enr_prom: %.1f  t_prom: %.0f ms%n",
                    completadas, iteraciones, 100.0 * completadas / iteraciones, avgEnr, avgMs);
            System.out.printf("ACO — TA maximo: %d ms (%.3f s)%n", maxTaAco, maxTaAco / 1000D);
            System.out.printf("ACO — Porcentaje maximo llenado vuelos: %.1f%%  aeropuertos: %.1f%%%n",
                    maxVuelosAco, maxAeropuertosAco);
        }
        System.out.println();
        System.out.printf("%-5s %-22s", "Iter", "Semilla");
        if (conGA)  System.out.printf(" %-12s %-6s %-8s", "GA-Estado", "GA-Enr", "GA-ms");
        if (conACO) System.out.printf(" %-12s %-6s %-8s", "ACO-Estado", "ACO-Enr", "ACO-ms");
        System.out.println();
        for (final ResultadoIteracion r : historial) {
            System.out.printf("%-5d %-22d", r.iteracion(), r.semilla());
            if (conGA && r.resGA() != null) {
                System.out.printf(" %-12s %-6d %-8d",
                        r.resGA().colapsada ? "COLAPSO" : "COMPLETADA",
                        r.resGA().totalMaletasEnrutadas, r.resGA().tiempoEjecucionMs);
            }
            if (conACO && r.resACO() != null) {
                System.out.printf(" %-12s %-6d %-8d",
                        r.resACO().colapsada ? "COLAPSO" : "COMPLETADA",
                        r.resACO().totalMaletasEnrutadas, r.resACO().tiempoEjecucionMs);
            }
            System.out.println();
        }
        System.out.println();
    }

    private static double fitnessExperimentalValor(final ResultadoSimulacion resultadoSimulacion) {
        if (resultadoSimulacion == null || resultadoSimulacion.fitnessExperimental == null) {
            return 0.0;
        }
        return resultadoSimulacion.fitnessExperimental.getFitnessExperimental();
    }

    private static void reportarDetalleNoRuteadas(final List<MaletaNoRuteada> noRuteadas) {
        if (noRuteadas == null || noRuteadas.isEmpty()) {
            return;
        }
        for (final MotivoNoRuteada motivo : MotivoNoRuteada.values()) {
            final List<MaletaNoRuteada> grupo = new ArrayList<>();
            for (final MaletaNoRuteada m : noRuteadas) {
                if (m.motivo() == motivo) {
                    grupo.add(m);
                }
            }
            if (grupo.isEmpty()) {
                continue;
            }
            System.out.printf("  %-18s (%d):%n", motivo, grupo.size());
            for (final MaletaNoRuteada m : grupo) {
                System.out.printf("    %-15s [%s -> %s, plazo: %s]%n",
                        m.idMaleta() != null ? m.idMaleta() : "-",
                        m.aeropuertoActual() != null ? m.aeropuertoActual() : "-",
                        m.aeropuertoDestino() != null ? m.aeropuertoDestino() : "-",
                        m.plazo() != null ? m.plazo().truncatedTo(ChronoUnit.MINUTES) : "-");
            }
        }
    }

    private static void reportarFitnessExperimental(final ResultadoFitnessExperimental fitnessExperimental) {
        if (fitnessExperimental == null) {
            return;
        }
        System.out.printf("Fitness experimental: %.3f%n", fitnessExperimental.getFitnessExperimental());
        System.out.printf("Maletas no ruteadas acumuladas: %d%n", fitnessExperimental.getMaletasNoRuteadas());
        System.out.printf("Duracion total horas: %.3f%n", fitnessExperimental.getDuracionTotalHoras());
    }

    private static ExportadorFitnessExperimental crearExportadorFitnessExperimental(final ContextoSimulacion contexto) {
        try {
            Files.createDirectories(RESULTADOS_DIR);
            inicializarCsvFitnessExperimental();
            return new ExportadorFitnessExperimental(FITNESS_EXPERIMENTAL_CSV);
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo crear archivo CSV de fitness experimental", exception);
        }
    }

    private static void inicializarCsvFitnessExperimental() throws IOException {
        if (!Files.exists(FITNESS_EXPERIMENTAL_CSV)) {
            Files.write(FITNESS_EXPERIMENTAL_CSV, List.of(FITNESS_EXPERIMENTAL_CABECERA));
            return;
        }
        final List<String> lineas = Files.readAllLines(FITNESS_EXPERIMENTAL_CSV);
        if (!lineas.isEmpty() && FITNESS_EXPERIMENTAL_CABECERA.equals(lineas.get(0))) {
            return;
        }
        Files.write(FITNESS_EXPERIMENTAL_CSV, List.of(FITNESS_EXPERIMENTAL_CABECERA));
    }

    private static void resolverSemillaGa(final ParametrosGA parametrosGa) {
        if (parametrosGa.getSemilla() != SEMILLA_ALEATORIA) {
            return;
        }
        parametrosGa.setSemilla(generarSemilla());
    }

    private static void resolverSemillaAco(final ACOConfiguracion configuracionAco) {
        if (configuracionAco.getSemilla() != SEMILLA_ALEATORIA) {
            return;
        }
        configuracionAco.setSemilla(generarSemilla());
    }

    private static long generarSemilla() {
        return Math.abs(System.nanoTime() ^ System.currentTimeMillis());
    }

    private static void reportarSemilla(final String algoritmo, final long semilla) {
        System.out.printf("Semilla %s usada: %d%n", algoritmo, semilla);
    }

    private static ParametrosGA construirParametrosGA(final Properties params) {
        final ParametrosGA parametros = new ParametrosGA();
        parametros.setTamanioPoblacion(intParam(params, "ga.tamanioPoblacion", parametros.getTamanioPoblacion()));
        parametros.setMaxGeneraciones(intParam(params, "ga.maxGeneraciones", parametros.getMaxGeneraciones()));
        parametros.setMaxSinMejora(intParam(params, "ga.maxSinMejora", parametros.getMaxSinMejora()));
        parametros.setTiempoMaximoMs(longParam(params, "ga.tiempoMaximoMs", parametros.getTiempoMaximoMs()));
        parametros.setProbCruce(doubleParam(params, "ga.probCruce", parametros.getProbCruce()));
        parametros.setProbMutacion(doubleParam(params, "ga.probMutacion", parametros.getProbMutacion()));
        parametros.setTorneoK(intParam(params, "ga.torneoK", parametros.getTorneoK()));
        parametros.setProbTorneo(doubleParam(params, "ga.probTorneo", parametros.getProbTorneo()));
        parametros.setElites(intParam(params, "ga.elites", parametros.getElites()));
        parametros.setSemilla(longParam(params, "ga.semilla", parametros.getSemilla()));
        parametros.setW1MaletasIncumplidas(
                doubleParam(params, "ga.w1MaletasIncumplidas", parametros.getW1MaletasIncumplidas()));
        parametros.setW2ExcesoHorasPlazo(
                doubleParam(params, "ga.w2ExcesoHorasPlazo", parametros.getW2ExcesoHorasPlazo()));
        parametros.setW3OverflowVuelo(doubleParam(params, "ga.w3OverflowVuelo", parametros.getW3OverflowVuelo()));
        parametros.setW4OverflowAlmacen(
                doubleParam(params, "ga.w4OverflowAlmacen", parametros.getW4OverflowAlmacen()));
        parametros.setW5TransitoPromedio(
                doubleParam(params, "ga.w5TransitoPromedio", parametros.getW5TransitoPromedio()));
        parametros.setPenalizacionRutaVacia(
                doubleParam(params, "ga.penalizacionRutaVacia", parametros.getPenalizacionRutaVacia()));
        parametros.setPenalizacionSinDestino(
                doubleParam(params, "ga.penalizacionSinDestino", parametros.getPenalizacionSinDestino()));
        parametros.setPenalizacionRutaInvalida(
                doubleParam(params, "ga.penalizacionRutaInvalida", parametros.getPenalizacionRutaInvalida()));
        parametros.setPesoGreedySolomon(
                doubleParam(params, "ga.pesoGreedySolomon", parametros.getPesoGreedySolomon()));
        parametros.setMinutosConexion(longParam(params, "minutosConexion", parametros.getMinutosConexion()));
        parametros.setTiempoRecojo(longParam(params, "tiempoRecojo", parametros.getTiempoRecojo()));
        return parametros;
    }

    private static ACOConfiguracion construirConfiguracionACO(final Properties params) {
        final ACOConfiguracion configuracion = new ACOConfiguracion();
        configuracion.setNts(intParam(params, "aco.nts", configuracion.getNts()));
        configuracion.setMaxIter(intParam(params, "aco.maxIter", configuracion.getMaxIter()));
        configuracion.setNAnts(intParam(params, "aco.nAnts", configuracion.getNAnts()));
        configuracion.setAlpha(doubleParam(params, "aco.alpha", configuracion.getAlpha()));
        configuracion.setBeta(doubleParam(params, "aco.beta", configuracion.getBeta()));
        configuracion.setRho(doubleParam(params, "aco.rho", configuracion.getRho()));
        configuracion.setGamma(doubleParam(params, "aco.gamma", configuracion.getGamma()));
        configuracion.setTau0(doubleParam(params, "aco.tau0", configuracion.getTau0()));
        configuracion.setTauMin(doubleParam(params, "aco.tauMin", configuracion.getTauMin()));
        configuracion.setTauMax(doubleParam(params, "aco.tauMax", configuracion.getTauMax()));
        configuracion.setSemilla(longParam(params, "aco.semilla", configuracion.getSemilla()));
        configuracion.setHorasPorIntervalo(
                intParam(params, "aco.horasPorIntervalo", configuracion.getHorasPorIntervalo())
        );
        configuracion.setPenalizacionNoFactible(
                doubleParam(params, "aco.penalizacionNoFactible", configuracion.getPenalizacionNoFactible()));
        configuracion.setPenalizacionIncumplimiento(
                doubleParam(params, "aco.penalizacionIncumplimiento", configuracion.getPenalizacionIncumplimiento()));
        configuracion.setPenalizacionSobrecargaVuelo(
                doubleParam(params, "aco.penalizacionSobrecargaVuelo", configuracion.getPenalizacionSobrecargaVuelo()));
        configuracion.setPenalizacionSobrecargaAlmacen(
                doubleParam(
                        params,
                        "aco.penalizacionSobrecargaAlmacen",
                        configuracion.getPenalizacionSobrecargaAlmacen()
                )
        );
        configuracion.setPenalizacionReplanificacion(
                doubleParam(params, "aco.penalizacionReplanificacion", configuracion.getPenalizacionReplanificacion()));
        return configuracion;
    }

    private static int intParam(final Properties params, final String key, final int defaultValue) {
        final String value = params.getProperty(key);
        return value != null ? Integer.parseInt(value.trim()) : defaultValue;
    }

    private static long longParam(final Properties params, final String key, final long defaultValue) {
        final String value = params.getProperty(key);
        return value != null ? Long.parseLong(value.trim()) : defaultValue;
    }

    private static double doubleParam(final Properties params, final String key, final double defaultValue) {
        final String value = params.getProperty(key);
        return value != null ? Double.parseDouble(value.trim()) : defaultValue;
    }

    private record PasoSimulacion(LocalDateTime momento, int nuevas, int enrutadas, int pendientes,
                                  Semaforo semaforo, String aeropuertoMasCargado) {
    }

    private record TrazabilidadCiclo(int numeroCiclo,
                                     LocalDateTime inicioCicloDatos,
                                     LocalDateTime finCicloDatos,
                                     int nuevasEnCiclo,
                                     int enrutadasVigentes,
                                     int rutasConfirmadasEnCiclo,
                                     int pendientesSinRuta,
                                     List<TrazabilidadMaletaPendiente> maletasPendientes) {
    }

    private record TrazabilidadMaletaPendiente(String idMaleta,
                                               String aeropuertoActual,
                                               String destino,
                                               LocalDateTime plazo,
                                               MotivoNoRuteada motivo,
                                               EstadoOperacionMaleta estadoAntes,
                                               EstadoOperacionMaleta estadoDespues,
                                               String rutaPrevia,
                                               String rutaFuturaPerdida,
                                               String rutaActual,
                                               String maletasBeneficiadas) {
    }

    private record ResultadoIteracion(int iteracion, long semilla,
                                      ResultadoSimulacion resGA, ResultadoSimulacion resACO) {
    }

    private record ContextoSimulacion(Properties params, ArrayList<Aeropuerto> aeropuertos,
                                      Map<String, Aeropuerto> indiceAeropuertos,
                                      ArrayList<VueloProgramado> todosVuelosProgramados,
                                      ArrayList<VueloInstancia> todosVuelosInstancia,
                                      List<Pedido> pedidosOrdenados,
                                      List<Maleta> todasLasMaletas,
                                      LocalDate fechaInicio,
                                      LocalDate fechaFin, int ventanaDias) {
    }

    private record ContextoEjecucion(ArrayList<Aeropuerto> aeropuertos,
                                     Map<String, Aeropuerto> indiceAeropuertos,
                                     ArrayList<VueloProgramado> todosVuelosProgramados,
                                     ArrayList<VueloInstancia> todosVuelosInstancia) {
    }

    private record ConfiguracionProgramada(long saMs, long k, Duration sc, boolean sleepHabilitado) {
    }

    private record VentanaVuelos(ArrayList<VueloInstancia> vuelosInstancia,
                                 ArrayList<VueloProgramado> vuelosProgramados) {
    }

    private enum EstadoOperacionMaleta {
        SIN_RUTA,
        ESPERANDO_ORIGEN,
        EN_TRANSITO,
        ESPERANDO_CONEXION,
        EN_DESTINO_FINAL,
        FINALIZADA
    }

    private enum MotivoNoRuteada {
        SIN_RUTA,
        RUTA_INCOMPLETA,
        PLAZO_VENCIDO
    }

    private record RutaMaletaProgramada(List<VueloInstancia> vuelos, String aeropuertoEspera) {
    }

    private record MaletaNoRuteada(String idMaleta, String aeropuertoActual, String aeropuertoDestino,
                                   LocalDateTime plazo, MotivoNoRuteada motivo) {
    }

    private record EstadoMaletaTemporal(Maleta maleta,
                                        String idMaleta,
                                        EstadoOperacionMaleta estado,
                                        String aeropuertoActual,
                                        List<VueloInstancia> vuelosEjecutados,
                                        List<VueloInstancia> vuelosFuturos,
                                        boolean replanificable,
                                        boolean enTransito) {
    }

    private record DetalleColapso(String motivo,
                                  EstadoMaletaTemporal estadoMaleta,
                                  LocalDateTime momento) {
    }

    private static final class ResultadoSimulacion {
        private boolean colapsada;
        private LocalDateTime momentoColapso;
        private int totalMaletasEnrutadas;
        private int totalMaletasPendientes;
        private long tiempoEjecucionMs;
        private long maxTaMs;
        private double maxOcupacionVuelos;
        private double maxOcupacionAeropuertos;
        private ResultadoFitnessExperimental fitnessExperimental;
        private List<PasoSimulacion> historial = new ArrayList<>();
        private List<MaletaNoRuteada> detalleNoRuteadas = new ArrayList<>();
        private List<TrazabilidadCiclo> trazabilidadCiclos = new ArrayList<>();
    }

    private static final class ExportadorFitnessExperimental {
        private final Path archivo;
        private Double fitnessAco;
        private Long tiempoAcoMs;
        private Double fitnessGa;
        private Long tiempoGaMs;

        private ExportadorFitnessExperimental(final Path archivo) {
            this.archivo = archivo;
        }

        private void registrar(final String algoritmo, final long semilla, final ResultadoSimulacion resultado) {
            if (resultado == null || resultado.fitnessExperimental == null) {
                return;
            }
            if ("ACO".equals(algoritmo)) {
                fitnessAco = resultado.fitnessExperimental.getFitnessExperimental();
                tiempoAcoMs = resultado.tiempoEjecucionMs;
                return;
            }
            if ("GA".equals(algoritmo)) {
                fitnessGa = resultado.fitnessExperimental.getFitnessExperimental();
                tiempoGaMs = resultado.tiempoEjecucionMs;
            }
        }

        private void reportarArchivo() {
            try {
                escribirMuestra();
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo escribir resultado experimental en CSV", exception);
            }
            System.out.printf("CSV fitness experimental: %s%n", archivo.toAbsolutePath());
        }

        private void escribirMuestra() throws IOException {
            if (fitnessAco == null && fitnessGa == null) {
                return;
            }
            final List<String> lineas = new ArrayList<>(Files.readAllLines(archivo));
            final boolean registroUnicoAco = fitnessAco != null && fitnessGa == null;
            final boolean registroUnicoGa = fitnessAco == null && fitnessGa != null;
            if (registroUnicoAco && completarMuestraPendiente(lineas, true)) {
                Files.write(archivo, lineas);
                return;
            }
            if (registroUnicoGa && completarMuestraPendiente(lineas, false)) {
                Files.write(archivo, lineas);
                return;
            }

            final int muestra = Math.max(1, lineas.size());
            final String linea = String.format(
                    java.util.Locale.US,
                    "%d,%s,%s,%s,%s",
                    muestra,
                    formatearFitness(fitnessAco),
                    formatearTiempo(tiempoAcoMs),
                    formatearFitness(fitnessGa),
                    formatearTiempo(tiempoGaMs)
            );
            lineas.add(linea);
            Files.write(archivo, lineas);
        }

        private boolean completarMuestraPendiente(final List<String> lineas, final boolean completarAco) {
            for (int i = 1; i < lineas.size(); i++) {
                final String[] columnas = lineas.get(i).split(",", -1);
                if (columnas.length != 5) {
                    continue;
                }
                final boolean columnaAcoVacia = columnas[1].isBlank();
                final boolean columnaGaVacia = columnas[3].isBlank();
                if (completarAco && columnaAcoVacia && !columnaGaVacia) {
                    columnas[1] = formatearFitness(fitnessAco);
                    columnas[2] = formatearTiempo(tiempoAcoMs);
                    lineas.set(i, String.join(",", columnas));
                    return true;
                }
                if (!completarAco && columnaGaVacia && !columnaAcoVacia) {
                    columnas[3] = formatearFitness(fitnessGa);
                    columnas[4] = formatearTiempo(tiempoGaMs);
                    lineas.set(i, String.join(",", columnas));
                    return true;
                }
            }
            return false;
        }

        private String formatearFitness(final Double fitness) {
            if (fitness == null) {
                return "";
            }
            return String.format(java.util.Locale.US, "%.3f", fitness);
        }

        private String formatearTiempo(final Long tiempoMs) {
            if (tiempoMs == null) {
                return "";
            }
            return String.valueOf(tiempoMs);
        }
    }

    private static final class ExportadorTrazabilidadDetallada {
        private final Path directorioBase;

        private ExportadorTrazabilidadDetallada(final Path directorioBase) {
            this.directorioBase = directorioBase;
        }

        private void exportar(final String algoritmo,
                              final int iteracion,
                              final long semilla,
                              final ResultadoSimulacion resultado,
                              final ContextoSimulacion contexto) {
            if (resultado == null) {
                return;
            }
            final String nombreArchivo = String.format(
                    "trazabilidad-%s-iter-%02d-semilla-%d.txt",
                    algoritmo.toLowerCase(java.util.Locale.ROOT),
                    iteracion,
                    Math.abs(semilla)
            );
            final Path archivo = directorioBase.resolve(nombreArchivo);
            final List<String> lineas = new ArrayList<>();
            lineas.add(String.format("TRAZABILIDAD DETALLADA - %s", algoritmo));
            lineas.add(String.format("Iteracion: %d", iteracion));
            lineas.add(String.format("Semilla: %d", semilla));
            lineas.add(String.format("Rango fechas: %s -> %s", contexto.fechaInicio(), contexto.fechaFin()));
            lineas.add(String.format("Total enrutadas final: %d", resultado.totalMaletasEnrutadas));
            lineas.add(String.format("No ruteadas final: %d", resultado.totalMaletasPendientes));
            lineas.add(String.format("Tiempo ejecucion ms: %d", resultado.tiempoEjecucionMs));
            if (resultado.fitnessExperimental != null) {
                lineas.add(String.format(
                        java.util.Locale.US,
                        "Fitness experimental: %.3f",
                        resultado.fitnessExperimental.getFitnessExperimental()
                ));
            }
            lineas.add("");

            for (final TrazabilidadCiclo ciclo : resultado.trazabilidadCiclos) {
                lineas.add(String.format(
                        "=== CICLO #%d | datos=%s -> %s | nuevas=%d | enrutadas=%d | rutasCiclo=%d | pendientes=%d ===",
                        ciclo.numeroCiclo(),
                        ciclo.inicioCicloDatos().truncatedTo(ChronoUnit.SECONDS),
                        ciclo.finCicloDatos().truncatedTo(ChronoUnit.SECONDS),
                        ciclo.nuevasEnCiclo(),
                        ciclo.enrutadasVigentes(),
                        ciclo.rutasConfirmadasEnCiclo(),
                        ciclo.pendientesSinRuta()
                ));
                if (ciclo.maletasPendientes().isEmpty()) {
                    lineas.add("sin maletas pendientes en este ciclo");
                    lineas.add("");
                    continue;
                }
                for (final TrazabilidadMaletaPendiente maleta : ciclo.maletasPendientes()) {
                    lineas.add(String.format(
                            "maleta=%s destino=%s aeropuertoActual=%s plazo=%s motivo=%s estadoAntes=%s estadoDespues=%s",
                            valorTexto(maleta.idMaleta()),
                            valorTexto(maleta.destino()),
                            valorTexto(maleta.aeropuertoActual()),
                            maleta.plazo() != null ? maleta.plazo().truncatedTo(ChronoUnit.MINUTES) : "-",
                            maleta.motivo(),
                            maleta.estadoAntes(),
                            maleta.estadoDespues()
                    ));
                    lineas.add("  rutaPrevia=" + maleta.rutaPrevia());
                    lineas.add("  rutaFuturaPerdida=" + maleta.rutaFuturaPerdida());
                    lineas.add("  rutaActual=" + maleta.rutaActual());
                    lineas.add("  maletasQueUsanSuRutaPrevia=" + maleta.maletasBeneficiadas());
                }
                lineas.add("");
            }

            try {
                Files.createDirectories(directorioBase);
                Files.write(archivo, lineas);
            } catch (final IOException exception) {
                throw new IllegalStateException("No se pudo escribir trazabilidad detallada en TXT", exception);
            }
            System.out.printf("TXT trazabilidad detallada: %s%n", archivo.toAbsolutePath());
        }

        private String valorTexto(final String valor) {
            return valor == null || valor.isBlank() ? "-" : valor;
        }
    }
}
