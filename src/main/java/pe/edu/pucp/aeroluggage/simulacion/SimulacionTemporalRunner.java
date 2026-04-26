package pe.edu.pucp.aeroluggage.simulacion;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final long SEMILLA_ALEATORIA = -1L;

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
                    clonarVuelosInstancia(contexto.todosVuelosInstancia()),
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
                    clonarVuelosInstancia(contexto.todosVuelosInstancia()),
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
                clonarVuelosInstancia(contexto.todosVuelosInstancia()),
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
                clonarVuelosInstancia(contexto.todosVuelosInstancia()),
                "ACO",
                contexto
        );
        exportador.registrar("ACO", configuracionAco.getSemilla(), resultado);
        exportador.reportarArchivo();
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
        final Map<LocalDate, List<Maleta>> maletasPorDia = agruparMaletasPorDia(
                datosEntrada, fechaInicio, fechaFin);
        reportarCargaInicial(
                datosEntrada,
                maletasPorDia,
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
                maletasPorDia,
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

    private static Map<LocalDate, List<Maleta>> agruparMaletasPorDia(final DatosEntrada datosEntrada,
                                                                      final LocalDate fechaInicio,
                                                                      final LocalDate fechaFin) {
        final Map<LocalDate, List<Maleta>> maletasPorDia = new HashMap<>();
        for (final Maleta maleta : datosEntrada.getMaletas()) {
            final boolean maletaInvalida = maleta == null || maleta.getFechaRegistro() == null;
            if (maletaInvalida) {
                continue;
            }
            final LocalDate dia = maleta.getFechaRegistro().toLocalDate();
            final boolean diaDentroDeRango = !dia.isBefore(fechaInicio) && !dia.isAfter(fechaFin);
            if (diaDentroDeRango) {
                maletasPorDia.computeIfAbsent(dia, key -> new ArrayList<>()).add(maleta);
            }
        }
        return maletasPorDia;
    }

    private static void reportarCargaInicial(final DatosEntrada datosEntrada,
                                             final Map<LocalDate, List<Maleta>> maletasPorDia,
                                             final LocalDate fechaInicio,
                                             final LocalDate fechaFin,
                                             final int totalDias,
                                             final long tiempoCargaMs) {
        final int pedidos = datosEntrada.getPedidos().size();
        final int maletas = datosEntrada.getMaletas().size();
        final int diasConPedidos = maletasPorDia.size();
        System.out.printf(
                "Carga simulacion: pedidos=%d maletas=%d rango=%s..%s diasVuelos=%d diasConPedidos=%d tiempo=%d ms%n",
                pedidos,
                maletas,
                fechaInicio,
                fechaFin,
                totalDias,
                diasConPedidos,
                tiempoCargaMs
        );
    }

    private static ResultadoSimulacion ejecutarSimulacion(final Metaheuristico algoritmo,
                                                          final ArrayList<VueloInstancia> copiaVuelos,
                                                          final String nombre,
                                                          final ContextoSimulacion contexto) {
        final List<Maleta> pendientes = new ArrayList<>();
        final Map<String, Integer> ocupacion = new HashMap<>();
        final Map<String, VueloInstancia> vuelosPorId = indexarVuelosInstancia(copiaVuelos);
        final List<PasoSimulacion> historial = new ArrayList<>();
        final long inicioEjecucionMs = System.currentTimeMillis();
        int totalEnrutadas = 0;
        final ResultadoSimulacion resultado = new ResultadoSimulacion();
        ResultadoFitnessExperimental fitnessExperimental = new ResultadoFitnessExperimental(0D, 0, 0D, 0D, 0D);

        System.out.printf("%n=== SIMULACION TEMPORAL [%s] %s -> %s ===%n",
                nombre, contexto.fechaInicio(), contexto.fechaFin());

        for (LocalDate dia = contexto.fechaInicio(); !dia.isAfter(contexto.fechaFin()); dia = dia.plusDays(1)) {
            final List<Maleta> nuevas = contexto.maletasPorDia().getOrDefault(dia, List.of());
            for (final Maleta maleta : nuevas) {
                final String icao = icaoOrigen(maleta);
                if (icao != null) {
                    ocupacion.merge(icao, 1, Integer::sum);
                }
            }
            pendientes.addAll(nuevas);

            if (pendientes.isEmpty()) {
                continue;
            }

            for (final Map.Entry<String, Integer> entry : ocupacion.entrySet()) {
                final Aeropuerto aeropuerto = contexto.indiceAeropuertos().get(entry.getKey());
                if (aeropuerto != null) {
                    aeropuerto.setMaletasActuales(Math.max(0, entry.getValue()));
                }
            }

            final LocalDate ventanaFin = dia.plusDays(contexto.ventanaDias() - 1L);
            final ArrayList<VueloInstancia> vuelosVentana = new ArrayList<>();
            final Set<String> idsProgramadosVentana = new HashSet<>();
            for (final VueloInstancia vueloInstancia : copiaVuelos) {
                if (vueloInstancia.getFechaSalida() == null) {
                    continue;
                }
                final LocalDate fechaSalida = vueloInstancia.getFechaSalida().toLocalDate();
                final boolean vueloDentroVentana = !fechaSalida.isBefore(dia) && !fechaSalida.isAfter(ventanaFin);
                if (vueloDentroVentana) {
                    vuelosVentana.add(vueloInstancia);
                    if (vueloInstancia.getVueloProgramado() != null) {
                        idsProgramadosVentana.add(vueloInstancia.getVueloProgramado().getIdVueloProgramado());
                    }
                }
            }

            final ArrayList<VueloProgramado> programadosVentana = new ArrayList<>();
            for (final VueloProgramado vueloProgramado : contexto.todosVuelosProgramados()) {
                if (idsProgramadosVentana.contains(vueloProgramado.getIdVueloProgramado())) {
                    programadosVentana.add(vueloProgramado);
                }
            }

            final InstanciaProblema instancia = new InstanciaProblema(
                    "SIM-" + dia,
                    new ArrayList<>(pendientes),
                    programadosVentana,
                    vuelosVentana,
                    contexto.aeropuertos()
            );
            final ParametrosGA parametrosGa = construirParametrosGA(contexto.params());
            instancia.construirGrafo();

            algoritmo.ejecutar(instancia);
            final Solucion solucion = obtenerSolucion(algoritmo);
            postprocesarSolucion(solucion, instancia, parametrosGa);
            fitnessExperimental = fitnessExperimental.sumar(
                    CalculadorFitnessExperimental.calcular(solucion, instancia)
            );

            final Set<String> idsMaletasEnrutadas = new HashSet<>();
            if (solucion != null) {
                for (final Ruta ruta : solucion.getSolucion()) {
                    final boolean rutaFallida = ruta == null
                            || ruta.getEstado() == EstadoRuta.FALLIDA
                            || ruta.getSubrutas().isEmpty();
                    if (rutaFallida) {
                        continue;
                    }
                    idsMaletasEnrutadas.add(ruta.getIdMaleta());
                    for (final VueloInstancia vueloInstancia : ruta.getSubrutas()) {
                        final VueloInstancia vueloReal = obtenerVueloReal(vuelosPorId, vueloInstancia);
                        final boolean vueloConCapacidad = vueloReal != null
                                && vueloReal.getCapacidadDisponible() > 0;
                        if (!vueloConCapacidad) {
                            continue;
                        }
                        try {
                            vueloReal.actualizarCapacidad(1);
                        } catch (final IllegalStateException ignored) {
                            // no-op
                        }
                    }
                }
            }

            final Iterator<Maleta> iterator = pendientes.iterator();
            while (iterator.hasNext()) {
                final Maleta maleta = iterator.next();
                if (!idsMaletasEnrutadas.contains(maleta.getIdMaleta())) {
                    continue;
                }
                final String icao = icaoOrigen(maleta);
                if (icao != null) {
                    ocupacion.merge(icao, -1, Integer::sum);
                }
                iterator.remove();
            }
            totalEnrutadas += idsMaletasEnrutadas.size();

            final LocalDateTime finDia = dia.atTime(23, 59);
            for (final VueloInstancia vueloInstancia : copiaVuelos) {
                if (vueloInstancia.getEstado() == EstadoVuelo.CANCELADO) {
                    continue;
                }
                if (vueloInstancia.getFechaLlegada() != null && !vueloInstancia.getFechaLlegada().isAfter(finDia)) {
                    vueloInstancia.setEstado(EstadoVuelo.FINALIZADO);
                    continue;
                }
                if (vueloInstancia.getFechaSalida() != null && !vueloInstancia.getFechaSalida().isAfter(finDia)) {
                    vueloInstancia.setEstado(EstadoVuelo.EN_PROGRESO);
                }
            }

            final String aeropuertoMasCargado = ocupacion.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("-");
            historial.add(new PasoSimulacion(
                    dia,
                    nuevas.size(),
                    idsMaletasEnrutadas.size(),
                    pendientes.size(),
                    solucion != null ? solucion.getSemaforo() : null,
                    aeropuertoMasCargado
            ));
            reportarPaso(nombre, dia, nuevas.size(), idsMaletasEnrutadas.size(), pendientes.size(), solucion);

            final LocalDate diaActual = dia;
            final boolean hayCarryOver = pendientes.stream()
                    .anyMatch(maleta -> maleta.getFechaRegistro() != null
                            && maleta.getFechaRegistro().toLocalDate().isBefore(diaActual));
            if (hayCarryOver) {
                System.out.printf("[COLAPSO] %s colapso en dia %s -> %d maleta(s) sin ruta%n",
                        nombre, dia, pendientes.size());
                resultado.colapsada = true;
                resultado.diaColapso = dia;
                break;
            }
        }

        resultado.totalMaletasEnrutadas = totalEnrutadas;
        resultado.totalMaletasPendientes = pendientes.size();
        resultado.historial = historial;
        resultado.tiempoEjecucionMs = System.currentTimeMillis() - inicioEjecucionMs;
        resultado.fitnessExperimental = fitnessExperimental;
        reportarResumen(nombre, resultado);
        return resultado;
    }

    private static String icaoOrigen(final Maleta maleta) {
        final boolean origenInvalido = maleta.getPedido() == null || maleta.getPedido().getAeropuertoOrigen() == null;
        if (origenInvalido) {
            return null;
        }
        return maleta.getPedido().getAeropuertoOrigen().getIdAeropuerto();
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

    private static ArrayList<VueloInstancia> clonarVuelosInstancia(final ArrayList<VueloInstancia> originales) {
        final ArrayList<VueloInstancia> clon = new ArrayList<>(originales.size());
        for (final VueloInstancia vueloInstancia : originales) {
            clon.add(new VueloInstancia(
                    vueloInstancia.getIdVueloInstancia(),
                    vueloInstancia.getVueloProgramado(),
                    vueloInstancia.getFechaOperacion(),
                    vueloInstancia.getFechaSalida(),
                    vueloInstancia.getFechaLlegada(),
                    vueloInstancia.getCapacidadMaxima(),
                    vueloInstancia.getCapacidadDisponible(),
                    vueloInstancia.getEstado()
            ));
        }
        return clon;
    }

    private static void reportarPaso(final String nombre, final LocalDate dia, final int nuevas,
                                     final int enrutadas, final int pendientes, final Solucion solucion) {
        final String semaforo = solucion != null && solucion.getSemaforo() != null
                ? solucion.getSemaforo().name()
                : "N/A";
        System.out.printf("[%s][DIA %s] nuevas=%d enrutadas=%d pendientes=%d semaforo=%s%n",
                nombre, dia, nuevas, enrutadas, pendientes, semaforo);
    }

    private static void reportarResumen(final String nombre, final ResultadoSimulacion resultado) {
        System.out.printf("%n--- RESUMEN [%s] ---%n", nombre);
        System.out.printf("Estado:          %s%n",
                resultado.colapsada
                        ? "COLAPSADA (dia " + resultado.diaColapso + ")"
                        : "COMPLETADA");
        System.out.printf("Total enrutadas: %d%n", resultado.totalMaletasEnrutadas);
        System.out.printf("Pendientes fin:  %d%n", resultado.totalMaletasPendientes);
        System.out.printf("Pasos:           %d%n", resultado.historial.size());
        System.out.printf("Tiempo ejecucion:%d ms (%.3f s)%n",
                resultado.tiempoEjecucionMs,
                resultado.tiempoEjecucionMs / 1000D
        );
        reportarFitnessExperimental(resultado.fitnessExperimental);
        System.out.println();
        System.out.printf("%-12s %8s %10s %10s %10s %18s%n",
                "Dia", "Nuevas", "Enrutadas", "Pendientes", "Semaforo", "AeropuertoCargado");
        for (final PasoSimulacion paso : resultado.historial) {
            final String semaforo = paso.semaforo() != null ? paso.semaforo().name() : "N/A";
            System.out.printf("%-12s %8d %10d %10d %10s %18s%n",
                    paso.dia(), paso.nuevas(), paso.enrutadas(), paso.pendientes(),
                    semaforo, paso.aeropuertoMasCargado());
        }
        System.out.println();
    }

    private static void reportarFitnessExperimental(final ResultadoFitnessExperimental fitnessExperimental) {
        if (fitnessExperimental == null) {
            return;
        }
        System.out.printf("Fitness experimental: %.3f%n", fitnessExperimental.getFitnessExperimental());
        System.out.printf("Maletas no ruteadas acumuladas: %d%n", fitnessExperimental.getMaletasNoRuteadas());
        System.out.printf("Uso capacidad vuelos: %.3f%n", fitnessExperimental.getUsoCapacidadVuelos());
        System.out.printf("Uso capacidad aeropuertos: %.3f%n",
                fitnessExperimental.getUsoCapacidadAeropuertos()
        );
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
        final String cabecera = "Muestra,ACO,Algoritmo Genetico";
        if (!Files.exists(FITNESS_EXPERIMENTAL_CSV)) {
            Files.write(FITNESS_EXPERIMENTAL_CSV, List.of(cabecera));
            return;
        }
        final List<String> lineas = Files.readAllLines(FITNESS_EXPERIMENTAL_CSV);
        if (!lineas.isEmpty() && cabecera.equals(lineas.get(0))) {
            return;
        }
        Files.write(FITNESS_EXPERIMENTAL_CSV, List.of(cabecera));
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
        parametros.setElites(intParam(params, "ga.elites", parametros.getElites()));
        parametros.setSemilla(longParam(params, "ga.semilla", parametros.getSemilla()));
        parametros.setMinutosConexion(longParam(params, "ga.minutosConexion", parametros.getMinutosConexion()));
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
        configuracion.setNts(Math.max(configuracion.getNts(), intParam(params, "simulacion.ventana.dias", 3)));
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

    private record PasoSimulacion(LocalDate dia, int nuevas, int enrutadas, int pendientes,
                                  Semaforo semaforo, String aeropuertoMasCargado) {
    }

    private record ContextoSimulacion(Properties params, ArrayList<Aeropuerto> aeropuertos,
                                      Map<String, Aeropuerto> indiceAeropuertos,
                                      ArrayList<VueloProgramado> todosVuelosProgramados,
                                      ArrayList<VueloInstancia> todosVuelosInstancia,
                                      Map<LocalDate, List<Maleta>> maletasPorDia, LocalDate fechaInicio,
                                      LocalDate fechaFin, int ventanaDias) {
    }

    private static final class ResultadoSimulacion {
        private boolean colapsada;
        private LocalDate diaColapso;
        private int totalMaletasEnrutadas;
        private int totalMaletasPendientes;
        private long tiempoEjecucionMs;
        private ResultadoFitnessExperimental fitnessExperimental;
        private List<PasoSimulacion> historial = new ArrayList<>();
    }

    private static final class ExportadorFitnessExperimental {
        private final Path archivo;
        private Double fitnessAco;
        private Double fitnessGa;

        private ExportadorFitnessExperimental(final Path archivo) {
            this.archivo = archivo;
        }

        private void registrar(final String algoritmo, final long semilla, final ResultadoSimulacion resultado) {
            if (resultado == null || resultado.fitnessExperimental == null) {
                return;
            }
            if ("ACO".equals(algoritmo)) {
                fitnessAco = resultado.fitnessExperimental.getFitnessExperimental();
                return;
            }
            if ("GA".equals(algoritmo)) {
                fitnessGa = resultado.fitnessExperimental.getFitnessExperimental();
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
                    "%d,%s,%s",
                    muestra,
                    formatearFitness(fitnessAco),
                    formatearFitness(fitnessGa)
            );
            lineas.add(linea);
            Files.write(archivo, lineas);
        }

        private boolean completarMuestraPendiente(final List<String> lineas, final boolean completarAco) {
            for (int i = 1; i < lineas.size(); i++) {
                final String[] columnas = lineas.get(i).split(",", -1);
                if (columnas.length != 3) {
                    continue;
                }
                final boolean columnaAcoVacia = columnas[1].isBlank();
                final boolean columnaGaVacia = columnas[2].isBlank();
                if (completarAco && columnaAcoVacia && !columnaGaVacia) {
                    columnas[1] = formatearFitness(fitnessAco);
                    lineas.set(i, String.join(",", columnas));
                    return true;
                }
                if (!completarAco && columnaGaVacia && !columnaAcoVacia) {
                    columnas[2] = formatearFitness(fitnessGa);
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
    }
}
