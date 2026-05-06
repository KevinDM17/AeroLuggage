package pe.edu.pucp.aeroluggage.tuning;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOConfiguracion;
import pe.edu.pucp.aeroluggage.algoritmos.common.CalculadorFitnessExperimental;
import pe.edu.pucp.aeroluggage.algoritmos.common.ResultadoFitnessExperimental;
import pe.edu.pucp.aeroluggage.algoritmos.ga.GA;
import pe.edu.pucp.aeroluggage.algoritmos.ga.ParametrosGA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.io.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.io.DatosEntrada;

public final class TuningTaguchiRunner {

    public static final Path DOCS = Path.of("Documentos");
    public static final Path GA_TUNING_FILE = Path.of("ga_tuning.txt");
    public static final Path ACO_TUNING_FILE = Path.of("aco_tuning.txt");

    private static final int REPLICAS_POR_FILA = 10;
    private static final int DIAS_VUELOS = 7;
    private static final double EPSILON = 1e-9;

    private TuningTaguchiRunner() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    // -------------------------------------------------------------------------
    // Puntos de entrada públicos
    // -------------------------------------------------------------------------

    public static void ejecutarGA() throws IOException {
        if (!validarEntorno(GA_TUNING_FILE)) {
            return;
        }
        final ConfiguracionTuning config = leerConfiguracion(GA_TUNING_FILE);
        imprimirEncabezado(config);

        final InstanciaProblema instancia =
                cargarInstanciaParaTuning(config, GA_TUNING_FILE.getFileName().toString());
        if (instancia == null) {
            return;
        }

        final List<FilaResultado> resultados = new ArrayList<>();
        final double[] snPorFila = new double[config.filas.size()];

        for (int f = 0; f < config.filas.size(); f++) {
            final double[] valoresFila = config.filas.get(f);
            double sumaCuadrados = 0;
            long tiempoTotalMs = 0;

            System.out.printf("%nFila %2d/%d — parámetros: %s%n",
                    f + 1, config.filas.size(), formatearFila(config.columnas, valoresFila));

            for (int r = 0; r < REPLICAS_POR_FILA; r++) {
                final ParametrosGA params = ParametrosGA.pordefecto();
                params.setSemilla(config.semilla + r);
                aplicarFilaGA(params, config.columnas, valoresFila);

                final GA ga = new GA(params);
                final long t0 = System.currentTimeMillis();
                ga.ejecutar(instancia);
                final long tiempoMs = System.currentTimeMillis() - t0;
                tiempoTotalMs += tiempoMs;

                final ResultadoFitnessExperimental res =
                        CalculadorFitnessExperimental.calcular(ga.getMejorSolucion(), instancia);
                final double yi = res.getFitnessExperimental() == 0.0 ? EPSILON
                        : res.getFitnessExperimental();
                sumaCuadrados += yi * yi;

                System.out.printf("  rep %2d/%d — fitness = %12.2f  noRuteadas = %3d  tiempo = %5d ms%n",
                        r + 1, REPLICAS_POR_FILA, res.getFitnessExperimental(),
                        res.getMaletasNoRuteadas(), tiempoMs);

                resultados.add(new FilaResultado(f + 1, r + 1, config.semilla + r, valoresFila,
                        res.getFitnessExperimental(), res.getMaletasNoRuteadas(),
                        res.getUsoCapacidadVuelos(), res.getUsoCapacidadAeropuertos(),
                        res.getDuracionTotalHoras(), tiempoMs));
            }

            snPorFila[f] = -10.0 * Math.log10(sumaCuadrados / REPLICAS_POR_FILA);
            System.out.printf("Fila %2d/%d — S/N = %9.4f dB  tiempo_total = %5d ms%n",
                    f + 1, config.filas.size(), snPorFila[f], tiempoTotalMs);
        }

        final Path csv = Path.of("resultados_taguchi_" + config.fecha + ".csv");
        guardarCsv(resultados, config.columnas, csv);
        System.out.println("\nResultados guardados en: " + csv.toAbsolutePath());
    }

    public static void ejecutarACO() throws IOException {
        if (!validarEntorno(ACO_TUNING_FILE)) {
            return;
        }
        final ConfiguracionTuning config = leerConfiguracion(ACO_TUNING_FILE);
        imprimirEncabezado(config);

        final InstanciaProblema instancia =
                cargarInstanciaParaTuning(config, ACO_TUNING_FILE.getFileName().toString());
        if (instancia == null) {
            return;
        }

        final List<FilaResultado> resultados = new ArrayList<>();
        final double[] snPorFila = new double[config.filas.size()];

        for (int f = 0; f < config.filas.size(); f++) {
            final double[] valoresFila = config.filas.get(f);
            double sumaCuadrados = 0;
            long tiempoTotalMs = 0;

            System.out.printf("%nFila %2d/%d — parámetros: %s%n",
                    f + 1, config.filas.size(), formatearFila(config.columnas, valoresFila));

            for (int r = 0; r < REPLICAS_POR_FILA; r++) {
                final ACOConfiguracion acoConfig = new ACOConfiguracion();
                acoConfig.setSemilla(config.semilla + r);
                aplicarFilaACO(acoConfig, config.columnas, valoresFila);

                final ACO aco = new ACO(acoConfig);
                final long t0 = System.currentTimeMillis();
                aco.ejecutar(instancia);
                final long tiempoMs = System.currentTimeMillis() - t0;
                tiempoTotalMs += tiempoMs;

                final ResultadoFitnessExperimental res =
                        CalculadorFitnessExperimental.calcular(aco.getUltimaSolucion(), instancia);
                final double yi = res.getFitnessExperimental() == 0.0 ? EPSILON
                        : res.getFitnessExperimental();
                sumaCuadrados += yi * yi;

                System.out.printf("  rep %2d/%d — fitness = %12.2f  noRuteadas = %3d  tiempo = %5d ms%n",
                        r + 1, REPLICAS_POR_FILA, res.getFitnessExperimental(),
                        res.getMaletasNoRuteadas(), tiempoMs);

                resultados.add(new FilaResultado(f + 1, r + 1, config.semilla + r, valoresFila,
                        res.getFitnessExperimental(), res.getMaletasNoRuteadas(),
                        res.getUsoCapacidadVuelos(), res.getUsoCapacidadAeropuertos(),
                        res.getDuracionTotalHoras(), tiempoMs));
            }

            snPorFila[f] = -10.0 * Math.log10(sumaCuadrados / REPLICAS_POR_FILA);
            System.out.printf("Fila %2d/%d — S/N = %9.4f dB  tiempo_total = %5d ms%n",
                    f + 1, config.filas.size(), snPorFila[f], tiempoTotalMs);
        }

        final Path csv = Path.of("resultados_taguchi_aco_" + config.fecha + ".csv");
        guardarCsv(resultados, config.columnas, csv);
        System.out.println("\nResultados guardados en: " + csv.toAbsolutePath());
    }

    // -------------------------------------------------------------------------
    // Setup compartido
    // -------------------------------------------------------------------------

    private static boolean validarEntorno(final Path archivoTuning) {
        if (!Files.isDirectory(DOCS)) {
            System.err.println("Carpeta '" + DOCS.toAbsolutePath() + "' no encontrada.");
            return false;
        }
        if (!Files.exists(archivoTuning)) {
            System.err.println("Archivo '" + archivoTuning.toAbsolutePath() + "' no encontrado.");
            return false;
        }
        return true;
    }

    private static void imprimirEncabezado(final ConfiguracionTuning config) {
        System.out.printf("Fecha: %s | Semilla base: %d | Columnas: %s%n",
                config.fecha, config.semilla, config.columnas);
        System.out.printf("Filas del arreglo: %d | Total ejecuciones: %d%n%n",
                config.filas.size(), config.filas.size() * REPLICAS_POR_FILA);
    }

    private static InstanciaProblema cargarInstanciaParaTuning(
            final ConfiguracionTuning config, final String nombreArchivo) throws IOException {
        final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(
                DOCS.resolve("Aeropuertos.txt"));
        final Map<String, Aeropuerto> indice = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
        final DatosEntrada todosEnvios = CargadorDatosPrueba.cargarEnvios(DOCS.resolve("Envios"), indice);

        final LocalDateTime limite = config.fecha.atTime(LocalTime.MAX);
        final LocalDateTime desde = limite.minusHours(36);
        final ArrayList<Maleta> maletas = filtrarVentana(todosEnvios.getMaletas(), desde, limite);
        System.out.printf("Maletas en ventana [%s — %s]: %d%n%n", desde, limite, maletas.size());

        if (maletas.isEmpty()) {
            System.err.println("Sin maletas en la ventana de fecha. Ajusta 'fecha' en " + nombreArchivo + ".");
            return null;
        }

        final LocalDate fechaVuelos = config.fecha.minusDays(1);
        final ArrayList<VueloProgramado> programados = CargadorDatosPrueba.cargarVuelosProgramados(
                DOCS.resolve("planes_vuelo.txt"), indice, fechaVuelos, DIAS_VUELOS);
        final ArrayList<VueloInstancia> instancias = CargadorDatosPrueba.cargarVuelosInstancia(
                DOCS.resolve("planes_vuelo.txt"), indice, fechaVuelos, DIAS_VUELOS);
        final InstanciaProblema instancia = new InstanciaProblema(
                "TAGUCHI-" + config.fecha, maletas, programados, instancias, aeropuertos);
        instancia.construirGrafo();
        System.out.printf("Grafo construido. Maletas a rutear: %d%nIniciando experimento Taguchi...%n%n",
                maletas.size());
        return instancia;
    }

    // -------------------------------------------------------------------------
    // Parseo del archivo de configuración
    // -------------------------------------------------------------------------

    static ConfiguracionTuning leerConfiguracion(final Path archivo) throws IOException {
        LocalDate fecha = null;
        long semilla = 42L;
        List<String> columnas = new ArrayList<>();
        final List<double[]> filas = new ArrayList<>();

        for (final String linea : Files.readAllLines(archivo, StandardCharsets.UTF_8)) {
            final String t = linea.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            if (t.contains("=")) {
                final String[] partes = t.split("=", 2);
                final String clave = partes[0].trim();
                final String valor = partes[1].trim();
                if ("fecha".equals(clave)) {
                    fecha = LocalDate.parse(valor, DateTimeFormatter.ISO_LOCAL_DATE);
                } else if ("semilla".equals(clave)) {
                    semilla = Long.parseLong(valor);
                } else if ("columnas".equals(clave)) {
                    columnas = new ArrayList<>();
                    for (final String c : valor.split(",")) {
                        columnas.add(c.trim());
                    }
                }
            } else {
                final String[] tokens = t.split(",");
                final double[] valores = new double[tokens.length];
                for (int i = 0; i < tokens.length; i++) {
                    valores[i] = Double.parseDouble(tokens[i].trim());
                }
                filas.add(valores);
            }
        }

        if (fecha == null) {
            throw new IllegalArgumentException(archivo.getFileName() + ": falta la propiedad 'fecha'");
        }
        if (columnas.isEmpty()) {
            throw new IllegalArgumentException(archivo.getFileName() + ": falta la propiedad 'columnas'");
        }
        return new ConfiguracionTuning(fecha, semilla, columnas, filas);
    }

    // -------------------------------------------------------------------------
    // Filtrado de maletas por ventana de tiempo
    // -------------------------------------------------------------------------

    private static ArrayList<Maleta> filtrarVentana(final ArrayList<Maleta> todas,
            final LocalDateTime desde, final LocalDateTime limite) {
        final ArrayList<Maleta> resultado = new ArrayList<>();
        for (final Maleta m : todas) {
            if (m == null || m.getFechaRegistro() == null) {
                continue;
            }
            final LocalDateTime fr = m.getFechaRegistro();
            if (!fr.isBefore(desde) && !fr.isAfter(limite)) {
                resultado.add(m);
            }
        }
        return resultado;
    }

    // -------------------------------------------------------------------------
    // Aplicar fila del arreglo a ParametrosGA
    // -------------------------------------------------------------------------

    private static void aplicarFilaGA(final ParametrosGA p, final List<String> columnas,
            final double[] valores) {
        for (int i = 0; i < columnas.size(); i++) {
            final double val = valores[i];
            final String col = columnas.get(i);
            if ("tamanioPoblacion".equals(col)) {
                p.setTamanioPoblacion((int) val);
            } else if ("maxGeneraciones".equals(col)) {
                p.setMaxGeneraciones((int) val);
            } else if ("maxSinMejora".equals(col)) {
                p.setMaxSinMejora((int) val);
            } else if ("probCruce".equals(col)) {
                p.setProbCruce(val);
            } else if ("probMutacion".equals(col)) {
                p.setProbMutacion(val);
            } else if ("probBusquedaLocal".equals(col)) {
                p.setProbBusquedaLocal(val);
            } else if ("torneoK".equals(col)) {
                p.setTorneoK((int) val);
            } else if ("probTorneo".equals(col)) {
                p.setProbTorneo(val);
            } else if ("elites".equals(col)) {
                p.setElites((int) val);
            } else if ("minutosConexion".equals(col)) {
                p.setMinutosConexion((long) val);
            } else if ("pesoGreedySolomon".equals(col)) {
                p.setPesoGreedySolomon(val);
            } else if ("pesoAleatorioSolomon".equals(col)) {
                p.setPesoAleatorioSolomon(val);
            } else {
                System.out.println("Advertencia GA: columna desconocida '" + col + "' — ignorada.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Aplicar fila del arreglo a ACOConfiguracion
    // -------------------------------------------------------------------------

    private static void aplicarFilaACO(final ACOConfiguracion p, final List<String> columnas,
            final double[] valores) {
        for (int i = 0; i < columnas.size(); i++) {
            final double val = valores[i];
            final String col = columnas.get(i);
            if ("maxIter".equals(col)) {
                p.setMaxIter((int) val);
            } else if ("nAnts".equals(col)) {
                p.setNAnts((int) val);
            } else if ("alpha".equals(col)) {
                p.setAlpha(val);
            } else if ("beta".equals(col)) {
                p.setBeta(val);
            } else if ("rho".equals(col)) {
                p.setRho(val);
            } else if ("gamma".equals(col)) {
                p.setGamma(val);
            } else if ("tau0".equals(col)) {
                p.setTau0(val);
            } else if ("tauMin".equals(col)) {
                p.setTauMin(val);
            } else if ("tauMax".equals(col)) {
                p.setTauMax(val);
            } else if ("penalizacionNoFactible".equals(col)) {
                p.setPenalizacionNoFactible(val);
            } else if ("penalizacionIncumplimiento".equals(col)) {
                p.setPenalizacionIncumplimiento(val);
            } else if ("penalizacionSobrecargaVuelo".equals(col)) {
                p.setPenalizacionSobrecargaVuelo(val);
            } else if ("penalizacionSobrecargaAlmacen".equals(col)) {
                p.setPenalizacionSobrecargaAlmacen(val);
            } else if ("penalizacionReplanificacion".equals(col)) {
                p.setPenalizacionReplanificacion(val);
            } else {
                System.out.println("Advertencia ACO: columna desconocida '" + col + "' — ignorada.");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Formato de salida
    // -------------------------------------------------------------------------

    private static String formatearFila(final List<String> columnas, final double[] valores) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columnas.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(columnas.get(i)).append('=').append(formatearNivel(valores[i]));
        }
        return sb.toString();
    }

    static String formatearNivel(final double valor) {
        if (valor == Math.floor(valor) && !Double.isInfinite(valor)) {
            return String.valueOf((long) valor);
        }
        return String.valueOf(valor);
    }

    // -------------------------------------------------------------------------
    // CSV detallado
    // -------------------------------------------------------------------------

    private static void guardarCsv(final List<FilaResultado> resultados,
            final List<String> columnas, final Path csv) throws IOException {
        try (final BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            w.write("fila,replica,semilla");
            for (final String col : columnas) {
                w.write("," + col);
            }
            w.write(",fitnessExp,maletasNoRuteadas,usoVuelos,usoAeropuertos,duracionHoras,tiempoMs");
            w.newLine();
            for (final FilaResultado r : resultados) {
                w.write(r.fila + "," + r.replica + "," + r.semilla);
                for (final double val : r.valoresFila) {
                    w.write("," + formatearNivel(val));
                }
                w.write(String.format(",%f,%d,%f,%f,%f,%d",
                        r.fitnessExp, r.maletasNoRuteadas, r.usoVuelos,
                        r.usoAeropuertos, r.duracionHoras, r.tiempoMs));
                w.newLine();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Clases de datos internas (package-private para el test)
    // -------------------------------------------------------------------------

    static final class ConfiguracionTuning {
        final LocalDate fecha;
        final long semilla;
        final List<String> columnas;
        final List<double[]> filas;

        ConfiguracionTuning(final LocalDate fecha, final long semilla,
                final List<String> columnas, final List<double[]> filas) {
            this.fecha = fecha;
            this.semilla = semilla;
            this.columnas = columnas;
            this.filas = filas;
        }
    }

    private static final class FilaResultado {
        final int fila;
        final int replica;
        final long semilla;
        final double[] valoresFila;
        final double fitnessExp;
        final int maletasNoRuteadas;
        final double usoVuelos;
        final double usoAeropuertos;
        final double duracionHoras;
        final long tiempoMs;

        FilaResultado(final int fila, final int replica, final long semilla,
                final double[] valoresFila, final double fitnessExp, final int maletasNoRuteadas,
                final double usoVuelos, final double usoAeropuertos,
                final double duracionHoras, final long tiempoMs) {
            this.fila = fila;
            this.replica = replica;
            this.semilla = semilla;
            this.valoresFila = valoresFila;
            this.fitnessExp = fitnessExp;
            this.maletasNoRuteadas = maletasNoRuteadas;
            this.usoVuelos = usoVuelos;
            this.usoAeropuertos = usoAeropuertos;
            this.duracionHoras = duracionHoras;
            this.tiempoMs = tiempoMs;
        }
    }
}
