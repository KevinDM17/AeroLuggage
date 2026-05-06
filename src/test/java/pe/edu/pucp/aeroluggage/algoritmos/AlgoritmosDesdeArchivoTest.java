package pe.edu.pucp.aeroluggage.algoritmos;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOConfiguracion;
import pe.edu.pucp.aeroluggage.algoritmos.ga.GA;
import pe.edu.pucp.aeroluggage.algoritmos.ga.ParametrosGA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.io.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.io.DatosEntrada;

class AlgoritmosDesdeArchivoTest {

    private static final Path DOCS = Path.of("Documentos");
    private static final Path PARAMS_FILE = Path.of("test_params.txt");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static Properties params;
    private static InstanciaProblema instancia;

    @BeforeAll
    static void cargarDatos() throws IOException {
        // given
        params = new Properties();
        try (final InputStream is = Files.newInputStream(PARAMS_FILE)) {
            params.load(is);
        }
        assumeTrue(Files.isDirectory(DOCS), "Carpeta Documentos no encontrada, se omite el test.");

        // when
        final Path archivoAeropuertos = DOCS.resolve("Aeropuertos.txt");
        final Path archivoVuelos = DOCS.resolve("planes_vuelo.txt");
        final Path carpetaEnvios = DOCS.resolve("Envios");

        final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(archivoAeropuertos);
        final Map<String, Aeropuerto> indice = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);

        final LocalDate fechaInicio = LocalDate.parse(params.getProperty("simulacion.fecha.inicio"), FMT);
        final LocalDate fechaFin = LocalDate.parse(params.getProperty("simulacion.fecha.fin"), FMT);
        final int dias = (int) (fechaFin.toEpochDay() - fechaInicio.toEpochDay()) + 1;

        final DatosEntrada datosEntrada = CargadorDatosPrueba.cargarEnvios(carpetaEnvios, indice);
        final ArrayList<Maleta> maletas = filtrarPorFecha(datosEntrada.getMaletas(), fechaInicio, fechaFin);

        final ArrayList<VueloProgramado> vuelosProgramados =
                CargadorDatosPrueba.cargarVuelosProgramados(archivoVuelos, indice, fechaInicio, dias);
        final ArrayList<VueloInstancia> vuelosInstancia =
                CargadorDatosPrueba.cargarVuelosInstancia(archivoVuelos, indice, fechaInicio, dias);

        instancia = new InstanciaProblema(
                "TEST-ARCHIVO", maletas, vuelosProgramados, vuelosInstancia, aeropuertos);
    }

    @Test
    void ga_deberia_encontrar_solucion() {
        // given
        assumeTrue(Boolean.parseBoolean(params.getProperty("ejecutar.ga", "true")),
                "GA deshabilitado en test_params.txt.");

        // when
        final GA ga = new GA(construirParametrosGA());
        ga.ejecutar(instancia);

        // then
        assertNotNull(ga.getMejorSolucion());
        assertTrue(Double.isFinite(ga.getMejorSolucion().getFitness()));
    }

    @Test
    void aco_deberia_encontrar_solucion() {
        // given
        assumeTrue(Boolean.parseBoolean(params.getProperty("ejecutar.aco", "true")),
                "ACO deshabilitado en test_params.txt.");

        // when
        final ACO aco = new ACO(construirConfiguracionACO());
        aco.ejecutar(instancia);

        // then
        assertNotNull(aco.getUltimaSolucion());
        assertTrue(Double.isFinite(aco.getUltimoCosto()));
    }

    private static ArrayList<Maleta> filtrarPorFecha(final ArrayList<Maleta> todas,
            final LocalDate inicio, final LocalDate fin) {
        final ArrayList<Maleta> filtradas = new ArrayList<>();
        for (final Maleta m : todas) {
            if (m == null || m.getFechaRegistro() == null) {
                continue;
            }
            final LocalDate fecha = m.getFechaRegistro().toLocalDate();
            final boolean dentroDelRango = !fecha.isBefore(inicio) && !fecha.isAfter(fin);
            if (dentroDelRango) {
                filtradas.add(m);
            }
        }
        return filtradas;
    }

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
        p.setPesoNoEnrutadas(doubleParam("ga.pesoNoEnrutadas", p.getPesoNoEnrutadas()));
        p.setPesoVuelosOverflow(doubleParam("ga.pesoVuelosOverflow", p.getPesoVuelosOverflow()));
        p.setPesoAeropuertosOverflow(doubleParam("ga.pesoAeropuertosOverflow", p.getPesoAeropuertosOverflow()));
        p.setPesoGreedySolomon(doubleParam("ga.pesoGreedySolomon", p.getPesoGreedySolomon()));
        return p;
    }

    private static ACOConfiguracion construirConfiguracionACO() {
        final ACOConfiguracion c = new ACOConfiguracion();
        c.setMaxEstadosBusquedaTemporal(
                intParam("aco.maxEstadosBusquedaTemporal", c.getMaxEstadosBusquedaTemporal()));
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
        c.setPenalizacionNoFactible(doubleParam("aco.penalizacionNoFactible", c.getPenalizacionNoFactible()));
        c.setPenalizacionIncumplimiento(doubleParam("aco.penalizacionIncumplimiento", c.getPenalizacionIncumplimiento()));
        c.setPenalizacionSobrecargaVuelo(doubleParam("aco.penalizacionSobrecargaVuelo", c.getPenalizacionSobrecargaVuelo()));
        c.setPenalizacionSobrecargaAlmacen(doubleParam("aco.penalizacionSobrecargaAlmacen", c.getPenalizacionSobrecargaAlmacen()));
        c.setPenalizacionReplanificacion(doubleParam("aco.penalizacionReplanificacion", c.getPenalizacionReplanificacion()));
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
}
