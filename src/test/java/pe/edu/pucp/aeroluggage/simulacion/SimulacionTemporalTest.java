package pe.edu.pucp.aeroluggage.simulacion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOConfiguracion;
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

class SimulacionTemporalTest {

    private static final Path DOCS = Path.of("Documentos");
    private static final Path PARAMS_FILE = Path.of("test_params.txt");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static Properties params;
    private static ArrayList<Aeropuerto> aeropuertos;
    private static Map<String, Aeropuerto> indiceAeropuertos;
    private static ArrayList<VueloProgramado> todosVuelosProgramados;
    private static ArrayList<VueloInstancia> todosVuelosInstancia;
    private static Map<LocalDate, List<Maleta>> maletasPorDia;
    private static LocalDate fechaInicio;
    private static LocalDate fechaFin;
    private static int ventanaDias;

    @BeforeAll
    static void cargarDatos() throws IOException {
        params = new Properties();
        try (final InputStream is = Files.newInputStream(PARAMS_FILE)) {
            params.load(is);
        }
        assumeTrue(Files.isDirectory(DOCS), "Carpeta Documentos no encontrada, se omite el test.");

        fechaInicio = LocalDate.parse(params.getProperty("simulacion.fecha.inicio"), FMT);
        fechaFin = LocalDate.parse(params.getProperty("simulacion.fecha.fin"), FMT);
        ventanaDias = intParam("simulacion.ventana.dias", 3);

        final Path archivoAeropuertos = DOCS.resolve("Aeropuertos.txt");
        final Path archivoVuelos = DOCS.resolve("planes_vuelo.txt");
        final Path carpetaEnvios = DOCS.resolve("Envios");

        aeropuertos = CargadorDatosPrueba.cargarAeropuertos(archivoAeropuertos);
        final Map<String, Aeropuerto> indice = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
        indiceAeropuertos = indice;

        final int totalDias = (int) (fechaFin.toEpochDay() - fechaInicio.toEpochDay()) + 1 + ventanaDias;
        todosVuelosProgramados = CargadorDatosPrueba.cargarVuelosProgramados(
                archivoVuelos, indice, fechaInicio, totalDias);
        todosVuelosInstancia = CargadorDatosPrueba.cargarVuelosInstancia(
                archivoVuelos, indice, fechaInicio, totalDias);

        final DatosEntrada datosEntrada = CargadorDatosPrueba.cargarEnvios(carpetaEnvios, indice);
        maletasPorDia = new HashMap<>();
        for (final Maleta m : datosEntrada.getMaletas()) {
            if (m == null || m.getFechaRegistro() == null) {
                continue;
            }
            final LocalDate dia = m.getFechaRegistro().toLocalDate();
            if (!dia.isBefore(fechaInicio) && !dia.isAfter(fechaFin)) {
                maletasPorDia.computeIfAbsent(dia, k -> new ArrayList<>()).add(m);
            }
        }
    }

    @Test
    void simulacion_temporal_ga() {
        assumeTrue(Boolean.parseBoolean(params.getProperty("ejecutar.ga", "true")),
                "GA deshabilitado en test_params.txt.");
        final GA ga = new GA(construirParametrosGA());
        final ResultadoSimulacion resultado = ejecutarSimulacion(
                ga, clonarVuelosInstancia(todosVuelosInstancia), "GA");
        assertNotNull(resultado);
    }

    @Test
    void simulacion_temporal_aco() {
        assumeTrue(Boolean.parseBoolean(params.getProperty("ejecutar.aco", "true")),
                "ACO deshabilitado en test_params.txt.");
        final ACO aco = new ACO(construirConfiguracionACO());
        final ResultadoSimulacion resultado = ejecutarSimulacion(
                aco, clonarVuelosInstancia(todosVuelosInstancia), "ACO");
        assertNotNull(resultado);
    }

    private ResultadoSimulacion ejecutarSimulacion(final Metaheuristico algoritmo,
                                                   final ArrayList<VueloInstancia> copiaVuelos,
                                                   final String nombre) {
        final List<Maleta> pendientes = new ArrayList<>();
        final Map<String, Integer> ocupacion = new HashMap<>();
        final List<PasoSimulacion> historial = new ArrayList<>();
        int totalEnrutadas = 0;
        final ResultadoSimulacion resultado = new ResultadoSimulacion();

        System.out.printf("%n=== SIMULACION TEMPORAL [%s] %s → %s ===%n", nombre, fechaInicio, fechaFin);

        for (LocalDate dia = fechaInicio; !dia.isAfter(fechaFin); dia = dia.plusDays(1)) {

            // 1. Register new orders arriving today
            final List<Maleta> nuevas = maletasPorDia.getOrDefault(dia, List.of());
            for (final Maleta m : nuevas) {
                final String icao = icaoOrigen(m);
                if (icao != null) {
                    ocupacion.merge(icao, 1, Integer::sum);
                }
            }
            pendientes.addAll(nuevas);

            if (pendientes.isEmpty()) {
                continue;
            }

            // 2. Reflect current occupancy on shared airport objects
            for (final Map.Entry<String, Integer> e : ocupacion.entrySet()) {
                final Aeropuerto ap = indiceAeropuertos.get(e.getKey());
                if (ap != null) {
                    ap.setMaletasActuales(Math.max(0, e.getValue()));
                }
            }

            // 3. Build InstanciaProblema for this step (flights in [dia, dia + ventana))
            final LocalDate ventanaFin = dia.plusDays(ventanaDias - 1);
            final ArrayList<VueloInstancia> vuelosVentana = new ArrayList<>();
            final Set<String> idsProgramadosVentana = new HashSet<>();
            for (final VueloInstancia vi : copiaVuelos) {
                if (vi.getFechaSalida() == null) {
                    continue;
                }
                final LocalDate fechaSalida = vi.getFechaSalida().toLocalDate();
                if (!fechaSalida.isBefore(dia) && !fechaSalida.isAfter(ventanaFin)) {
                    vuelosVentana.add(vi);
                    if (vi.getVueloProgramado() != null) {
                        idsProgramadosVentana.add(vi.getVueloProgramado().getIdVueloProgramado());
                    }
                }
            }
            final ArrayList<VueloProgramado> progVentana = new ArrayList<>();
            for (final VueloProgramado vp : todosVuelosProgramados) {
                if (idsProgramadosVentana.contains(vp.getIdVueloProgramado())) {
                    progVentana.add(vp);
                }
            }

            final InstanciaProblema instancia = new InstanciaProblema(
                    "SIM-" + dia, new ArrayList<>(pendientes), progVentana, vuelosVentana, aeropuertos);

            // 4. Run algorithm
            algoritmo.ejecutar(instancia);
            final Solucion sol = obtenerSolucion(algoritmo);

            // 5. Commit successful routes — decrement flight capacity on this algo's copy
            final Set<String> idsMaletasEnrutadas = new HashSet<>();
            if (sol != null) {
                for (final Ruta ruta : sol.getSolucion()) {
                    if (ruta == null || ruta.getEstado() == EstadoRuta.FALLIDA
                            || ruta.getSubrutas().isEmpty()) {
                        continue;
                    }
                    idsMaletasEnrutadas.add(ruta.getIdMaleta());
                    for (final VueloInstancia vi : ruta.getSubrutas()) {
                        if (vi != null && vi.getCapacidadDisponible() > 0) {
                            try {
                                vi.actualizarCapacidad(1);
                            } catch (final IllegalStateException ignored) {
                                // over-committed flight; skip silently
                            }
                        }
                    }
                }
            }

            // 6. Remove routed maletas from pending; update origin airport occupancy
            final Iterator<Maleta> it = pendientes.iterator();
            while (it.hasNext()) {
                final Maleta m = it.next();
                if (idsMaletasEnrutadas.contains(m.getIdMaleta())) {
                    final String icao = icaoOrigen(m);
                    if (icao != null) {
                        ocupacion.merge(icao, -1, Integer::sum);
                    }
                    it.remove();
                }
            }
            totalEnrutadas += idsMaletasEnrutadas.size();

            // 7. Advance flight states for today
            final LocalDateTime finDia = dia.atTime(23, 59);
            for (final VueloInstancia vi : copiaVuelos) {
                if (vi.getEstado() == EstadoVuelo.CANCELADO) {
                    continue;
                }
                if (vi.getFechaLlegada() != null && !vi.getFechaLlegada().isAfter(finDia)) {
                    vi.setEstado(EstadoVuelo.FINALIZADO);
                } else if (vi.getFechaSalida() != null && !vi.getFechaSalida().isAfter(finDia)) {
                    vi.setEstado(EstadoVuelo.EN_PROGRESO);
                }
            }

            // 8. Record step metrics
            final String masCargado = ocupacion.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("-");
            historial.add(new PasoSimulacion(dia, nuevas.size(), idsMaletasEnrutadas.size(),
                    pendientes.size(), sol != null ? sol.getSemaforo() : null, masCargado));
            reportarPaso(nombre, dia, nuevas.size(), idsMaletasEnrutadas.size(), pendientes.size(), sol);

            // 9. Collapse check: any carry-over maleta (registered before today) still unrouted?
            final LocalDate diaActual = dia;
            final boolean hayCarryOver = pendientes.stream()
                    .anyMatch(m -> m.getFechaRegistro() != null
                            && m.getFechaRegistro().toLocalDate().isBefore(diaActual));
            if (hayCarryOver) {
                System.out.printf("[COLAPSO] %s colapsó en día %s — %d maleta(s) sin ruta%n",
                        nombre, dia, pendientes.size());
                resultado.colapsada = true;
                resultado.diaColapso = dia;
                break;
            }
        }

        resultado.totalMaletasEnrutadas = totalEnrutadas;
        resultado.totalMaletasPendientes = pendientes.size();
        resultado.historial = historial;
        reportarResumen(nombre, resultado);
        return resultado;
    }

    private static String icaoOrigen(final Maleta m) {
        if (m.getPedido() == null || m.getPedido().getAeropuertoOrigen() == null) {
            return null;
        }
        return m.getPedido().getAeropuertoOrigen().getIdAeropuerto();
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

    private static ArrayList<VueloInstancia> clonarVuelosInstancia(
            final ArrayList<VueloInstancia> originales) {
        final ArrayList<VueloInstancia> clon = new ArrayList<>(originales.size());
        for (final VueloInstancia vi : originales) {
            clon.add(new VueloInstancia(
                    vi.getIdVueloInstancia(),
                    vi.getVueloProgramado(),
                    vi.getFechaOperacion(),
                    vi.getFechaSalida(),
                    vi.getFechaLlegada(),
                    vi.getCapacidadMaxima(),
                    vi.getCapacidadDisponible(),
                    vi.getEstado()
            ));
        }
        return clon;
    }

    private static void reportarPaso(final String nombre, final LocalDate dia, final int nuevas,
                                     final int enrutadas, final int pendientes, final Solucion sol) {
        final String semaforo = sol != null && sol.getSemaforo() != null
                ? sol.getSemaforo().name() : "N/A";
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
        System.out.println();
        System.out.printf("%-12s %8s %10s %10s %10s%n",
                "Dia", "Nuevas", "Enrutadas", "Pendientes", "Semaforo");
        for (final PasoSimulacion paso : resultado.historial) {
            final String sem = paso.semaforo() != null ? paso.semaforo().name() : "N/A";
            System.out.printf("%-12s %8d %10d %10d %10s%n",
                    paso.dia(), paso.nuevas(), paso.enrutadas(), paso.pendientes(), sem);
        }
        System.out.println();
    }

    // ---- parameter helpers (identical to AlgoritmosDesdeArchivoTest) ----

    private static ParametrosGA construirParametrosGA() {
        final ParametrosGA p = new ParametrosGA();
        p.setTamanioPoblacion(intParam("ga.tamanioPoblacion", p.getTamanioPoblacion()));
        p.setMaxGeneraciones(intParam("ga.maxGeneraciones", p.getMaxGeneraciones()));
        p.setMaxSinMejora(intParam("ga.maxSinMejora", p.getMaxSinMejora()));
        p.setTiempoMaximoMs(longParam("ga.tiempoMaximoMs", p.getTiempoMaximoMs()));
        p.setProbCruce(doubleParam("ga.probCruce", p.getProbCruce()));
        p.setProbMutacion(doubleParam("ga.probMutacion", p.getProbMutacion()));
        p.setTorneoK(intParam("ga.torneoK", p.getTorneoK()));
        p.setElites(intParam("ga.elites", p.getElites()));
        p.setSemilla(longParam("ga.semilla", p.getSemilla()));
        p.setMinutosConexion(longParam("ga.minutosConexion", p.getMinutosConexion()));
        p.setW1MaletasIncumplidas(doubleParam("ga.w1MaletasIncumplidas", p.getW1MaletasIncumplidas()));
        p.setW2ExcesoHorasPlazo(doubleParam("ga.w2ExcesoHorasPlazo", p.getW2ExcesoHorasPlazo()));
        p.setW3OverflowVuelo(doubleParam("ga.w3OverflowVuelo", p.getW3OverflowVuelo()));
        p.setW4OverflowAlmacen(doubleParam("ga.w4OverflowAlmacen", p.getW4OverflowAlmacen()));
        p.setW5TransitoPromedio(doubleParam("ga.w5TransitoPromedio", p.getW5TransitoPromedio()));
        p.setPenalizacionRutaVacia(doubleParam("ga.penalizacionRutaVacia", p.getPenalizacionRutaVacia()));
        p.setPenalizacionSinDestino(doubleParam("ga.penalizacionSinDestino", p.getPenalizacionSinDestino()));
        p.setPenalizacionRutaInvalida(doubleParam("ga.penalizacionRutaInvalida", p.getPenalizacionRutaInvalida()));
        p.setPesoGreedySolomon(doubleParam("ga.pesoGreedySolomon", p.getPesoGreedySolomon()));
        return p;
    }

    private static ACOConfiguracion construirConfiguracionACO() {
        final ACOConfiguracion c = new ACOConfiguracion();
        c.setNts(intParam("aco.nts", c.getNts()));
        c.setMaxIter(intParam("aco.maxIter", c.getMaxIter()));
        c.setNAnts(intParam("aco.nAnts", c.getNAnts()));
        c.setAlpha(doubleParam("aco.alpha", c.getAlpha()));
        c.setBeta(doubleParam("aco.beta", c.getBeta()));
        c.setRho(doubleParam("aco.rho", c.getRho()));
        c.setGamma(doubleParam("aco.gamma", c.getGamma()));
        c.setTau0(doubleParam("aco.tau0", c.getTau0()));
        c.setTauMin(doubleParam("aco.tauMin", c.getTauMin()));
        c.setTauMax(doubleParam("aco.tauMax", c.getTauMax()));
        c.setSemilla(longParam("aco.semilla", c.getSemilla()));
        c.setHorasPorIntervalo(intParam("aco.horasPorIntervalo", c.getHorasPorIntervalo()));
        c.setPenalizacionNoFactible(doubleParam("aco.penalizacionNoFactible", c.getPenalizacionNoFactible()));
        c.setPenalizacionIncumplimiento(
                doubleParam("aco.penalizacionIncumplimiento", c.getPenalizacionIncumplimiento()));
        c.setPenalizacionSobrecargaVuelo(
                doubleParam("aco.penalizacionSobrecargaVuelo", c.getPenalizacionSobrecargaVuelo()));
        c.setPenalizacionSobrecargaAlmacen(
                doubleParam("aco.penalizacionSobrecargaAlmacen", c.getPenalizacionSobrecargaAlmacen()));
        c.setPenalizacionReplanificacion(
                doubleParam("aco.penalizacionReplanificacion", c.getPenalizacionReplanificacion()));
        return c;
    }

    private static int intParam(final String key, final int def) {
        final String val = params.getProperty(key);
        return val != null ? Integer.parseInt(val.trim()) : def;
    }

    private static long longParam(final String key, final long def) {
        final String val = params.getProperty(key);
        return val != null ? Long.parseLong(val.trim()) : def;
    }

    private static double doubleParam(final String key, final double def) {
        final String val = params.getProperty(key);
        return val != null ? Double.parseDouble(val.trim()) : def;
    }

    // ---- inner types ----

    private record PasoSimulacion(LocalDate dia, int nuevas, int enrutadas, int pendientes,
                                  Semaforo semaforo, String aeropuertoMasCargado) {}

    private static final class ResultadoSimulacion {
        boolean colapsada = false;
        LocalDate diaColapso = null;
        int totalMaletasEnrutadas = 0;
        int totalMaletasPendientes = 0;
        List<PasoSimulacion> historial = new ArrayList<>();
    }
}
