package pe.edu.pucp.aeroluggage.simulacion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOConfiguracion;
import pe.edu.pucp.aeroluggage.algoritmos.common.CalculadorFitnessExperimental;
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

class SimulacionTemporalTest {

    private static final Path DOCS = Path.of("Documentos");
    private static final Path PARAMS_FILE = Path.of("test_params.txt");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static Properties params;
    private static ArrayList<Aeropuerto> aeropuertos;
    private static Map<String, Aeropuerto> indiceAeropuertos;
    private static ArrayList<VueloProgramado> todosVuelosProgramados;
    private static ArrayList<VueloInstancia> todosVuelosInstancia;
    private static List<Pedido> pedidosOrdenados;
    private static List<Maleta> todasLasMaletas;
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

        final DatosEntrada datosEntrada = CargadorDatosPrueba.cargarEnviosEnRango(
                carpetaEnvios,
                indice,
                fechaInicio,
                fechaFin
        );
        todasLasMaletas = new ArrayList<>(datosEntrada.getMaletas());
        validarMaletas(todasLasMaletas);
        pedidosOrdenados = new ArrayList<>(datosEntrada.getPedidos());
        pedidosOrdenados.sort(Comparator.comparing(Pedido::getFechaRegistro,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Pedido::getIdPedido));
        for (final Pedido p : pedidosOrdenados) {
            if (p.getFechaHoraPlazo() == null) {
                p.calcularFechaHoraPlazo();
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

    @Test
    void simulacion_experimental() {
        final boolean ejecutarGa = Boolean.parseBoolean(params.getProperty("ejecutar.ga", "true"));
        final boolean ejecutarAco = Boolean.parseBoolean(params.getProperty("ejecutar.aco", "true"));
        assumeTrue(ejecutarGa || ejecutarAco, "Ningun algoritmo habilitado en test_params.txt.");

        final int iteraciones = intParam("simulacion.iteraciones", 30);
        final Random rng = new Random();
        final List<ResultadoIteracion> historialExp = new ArrayList<>();

        System.out.printf("%n=== EXPERIMENTO: %d iteraciones [%s -> %s] ===%n",
                iteraciones, fechaInicio, fechaFin);

        for (int i = 1; i <= iteraciones; i++) {
            final long semilla = rng.nextLong();
            System.out.printf("%n-- Iteracion %d/%d  semilla=%d --%n", i, iteraciones, semilla);

            ResultadoSimulacion resGA = null;
            ResultadoSimulacion resACO = null;

            if (ejecutarGa) {
                final ParametrosGA pGA = construirParametrosGA();
                pGA.setSemilla(semilla);
                resGA = ejecutarSimulacion(new GA(pGA),
                        clonarVuelosInstancia(todosVuelosInstancia), "GA-" + i);
            }
            if (ejecutarAco) {
                final ACOConfiguracion cACO = construirConfiguracionACO();
                cACO.setSemilla(semilla);
                resACO = ejecutarSimulacion(new ACO(cACO),
                        clonarVuelosInstancia(todosVuelosInstancia), "ACO-" + i);
            }

            historialExp.add(new ResultadoIteracion(i, semilla, resGA, resACO));

            System.out.printf("[ITER %2d] semilla=%-20d", i, semilla);
            if (resGA != null) {
                System.out.printf("  GA: %-10s enr=%-5d t=%dms",
                        resGA.colapsada ? "COLAPSO" : "OK",
                        resGA.totalMaletasEnrutadas, resGA.tiempoEjecucionMs);
            }
            if (resACO != null) {
                System.out.printf("  ACO: %-10s enr=%-5d t=%dms",
                        resACO.colapsada ? "COLAPSO" : "OK",
                        resACO.totalMaletasEnrutadas, resACO.tiempoEjecucionMs);
            }
            System.out.println();
        }

        reportarExperimento(historialExp, iteraciones, ejecutarGa, ejecutarAco);
        assertNotNull(historialExp);
    }

    private ResultadoSimulacion ejecutarSimulacion(final Metaheuristico algoritmo,
                                                   final ArrayList<VueloInstancia> copiaVuelos,
                                                   final String nombre) {
        final List<Maleta> pendientes = new ArrayList<>();
        final Map<String, Integer> ocupacion = new HashMap<>();
        final Map<String, VueloInstancia> vuelosPorId = indexarVuelosInstancia(copiaVuelos);
        final List<PasoSimulacion> historial = new ArrayList<>();
        final long inicioEjecucionMs = System.currentTimeMillis();
        int totalEnrutadas = 0;
        final ResultadoSimulacion resultado = new ResultadoSimulacion();
        ResultadoFitnessExperimental fitnessExperimental = new ResultadoFitnessExperimental(0D, 0, 0D, 0D, 0D);

        System.out.printf("%n=== SIMULACION TEMPORAL [%s] %s → %s ===%n", nombre, fechaInicio, fechaFin);

        final Map<String, List<Maleta>> maletasPorPedido = agruparMaletasPorPedido(todasLasMaletas);

        LocalDate diaActual = null;
        int nuevasEnDia = 0;
        int enrutadasEnDia = 0;

        for (final Pedido pedido : pedidosOrdenados) {
            if (pedido.getFechaRegistro() == null) {
                continue;
            }
            final LocalDateTime currentTime = pedido.getFechaRegistro();

            final LocalDate diaPedido = currentTime.toLocalDate();
            if (!diaPedido.equals(diaActual)) {
                if (diaActual != null) {
                    reportarResumenDiario(nombre, diaActual, nuevasEnDia, enrutadasEnDia,
                            pendientes.size(), aeropuertos);
                }
                diaActual = diaPedido;
                nuevasEnDia = 0;
                enrutadasEnDia = 0;
            }

            // 1. Actualizar estados de vuelos hasta currentTime
            actualizarEstadosVuelos(copiaVuelos, currentTime);

            // 2. Agregar maletas del pedido a pendientes y ocupación
            final List<Maleta> nuevas =
                    maletasPorPedido.getOrDefault(pedido.getIdPedido(), List.of());
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

            // 3. Reflejar ocupación en objetos Aeropuerto
            for (final Map.Entry<String, Integer> e : ocupacion.entrySet()) {
                final Aeropuerto ap = indiceAeropuertos.get(e.getKey());
                if (ap != null) {
                    ap.setMaletasActuales(Math.max(0, e.getValue()));
                }
            }

            // 4. Ventana de vuelos: [currentTime, currentTime + ventanaDias]
            final LocalDateTime ventanaFin = currentTime.plusDays(ventanaDias - 1L)
                    .with(LocalTime.MAX);
            final ArrayList<VueloInstancia> vuelosVentana = new ArrayList<>();
            final Set<String> idsProgramadosVentana = new HashSet<>();
            for (final VueloInstancia vi : copiaVuelos) {
                if (vi.getFechaSalida() == null) {
                    continue;
                }
                if (!vi.getFechaSalida().isBefore(currentTime)
                        && !vi.getFechaSalida().isAfter(ventanaFin)) {
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

            // 5. Construir instancia y ejecutar algoritmo
            final DateTimeFormatter fmtMomento = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
            final InstanciaProblema instancia = new InstanciaProblema(
                    "SIM-" + currentTime.format(fmtMomento),
                    new ArrayList<>(pendientes), progVentana, vuelosVentana, aeropuertos);
            algoritmo.ejecutar(instancia);
            final Solucion sol = obtenerSolucion(algoritmo);
            fitnessExperimental = fitnessExperimental.sumar(
                    CalculadorFitnessExperimental.calcular(sol, instancia));
            FuncionCosto.calcularCostoSolucion(sol, instancia, construirParametrosGA());

            // 6. Reducir capacidades y remover maletas enrutadas
            final Set<String> idsMaletasEnrutadas = new HashSet<>();
            if (sol != null) {
                for (final Ruta ruta : sol.getSolucion()) {
                    if (ruta == null || ruta.getEstado() == EstadoRuta.FALLIDA
                            || ruta.getSubrutas().isEmpty()) {
                        continue;
                    }
                    idsMaletasEnrutadas.add(ruta.getIdMaleta());
                    for (final VueloInstancia vi : ruta.getSubrutas()) {
                        final VueloInstancia vueloReal = obtenerVueloReal(vuelosPorId, vi);
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
            final Iterator<Maleta> it = pendientes.iterator();
            while (it.hasNext()) {
                final Maleta m = it.next();
                if (!idsMaletasEnrutadas.contains(m.getIdMaleta())) {
                    continue;
                }
                final String icao = icaoOrigen(m);
                if (icao != null) {
                    ocupacion.merge(icao, -1, Integer::sum);
                }
                it.remove();
            }
            totalEnrutadas += idsMaletasEnrutadas.size();
            nuevasEnDia += nuevas.size();
            enrutadasEnDia += idsMaletasEnrutadas.size();

            // 7. Detección de colapso: maletas sin ruta cuyo plazo ya expiró
            final boolean hayColapso = pendientes.stream().anyMatch(m -> {
                final LocalDateTime plazo = m.getPedido() != null
                        ? m.getPedido().getFechaHoraPlazo() : null;
                return plazo != null && !plazo.isAfter(currentTime);
            });

            // 8. Registrar paso
            final String masCargado = ocupacion.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse("-");
            historial.add(new PasoSimulacion(
                    currentTime, nuevas.size(), idsMaletasEnrutadas.size(),
                    pendientes.size(), sol != null ? sol.getSemaforo() : null, masCargado));

            if (hayColapso) {
                System.out.printf("[COLAPSO] %s colapsó en momento %s — %d maleta(s) sin ruta%n",
                        nombre, currentTime, pendientes.size());
                resultado.colapsada = true;
                resultado.momentoColapso = currentTime;
                break;
            }
        }

        if (diaActual != null) {
            reportarResumenDiario(nombre, diaActual, nuevasEnDia, enrutadasEnDia,
                    pendientes.size(), aeropuertos);
        }

        resultado.totalMaletasEnrutadas = totalEnrutadas;
        resultado.totalMaletasPendientes = pendientes.size();
        resultado.historial = historial;
        resultado.tiempoEjecucionMs = System.currentTimeMillis() - inicioEjecucionMs;
        resultado.fitnessExperimental = fitnessExperimental;
        reportarResumen(nombre, resultado);
        return resultado;
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

    private static void reportarResumenDiario(final String nombre, final LocalDate dia,
                                              final int nuevas, final int enrutadas,
                                              final int pendientes,
                                              final ArrayList<Aeropuerto> listaAeropuertos) {
        System.out.printf("%n=== RESUMEN DIA [%s] [%s] ===%n", nombre, dia);
        System.out.printf("  Maletas nuevas:     %d%n", nuevas);
        System.out.printf("  Maletas enrutadas:  %d%n", enrutadas);
        System.out.printf("  Maletas pendientes: %d%n", pendientes);
        System.out.println();
        System.out.printf("  %-10s %10s %10s %12s%n",
                "Aeropuerto", "Ocupacion", "Capacidad", "Porcentaje");
        for (final Aeropuerto aeropuerto : listaAeropuertos) {
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
        System.out.printf("Total enrutadas: %d%n", resultado.totalMaletasEnrutadas);
        System.out.printf("Pendientes fin:  %d%n", resultado.totalMaletasPendientes);
        System.out.printf("Tiempo ejecucion:%d ms (%.3f s)%n",
                resultado.tiempoEjecucionMs,
                resultado.tiempoEjecucionMs / 1000D
        );
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
            System.out.printf("GA  — completadas: %d/%d (%.1f%%)  enr_prom: %.1f  t_prom: %.0f ms%n",
                    completadas, iteraciones, 100.0 * completadas / iteraciones, avgEnr, avgMs);
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
            System.out.printf("ACO — completadas: %d/%d (%.1f%%)  enr_prom: %.1f  t_prom: %.0f ms%n",
                    completadas, iteraciones, 100.0 * completadas / iteraciones, avgEnr, avgMs);
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
        p.setProbTorneo(doubleParam("ga.probTorneo", p.getProbTorneo()));
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

    private record PasoSimulacion(LocalDateTime momento, int nuevas, int enrutadas, int pendientes,
                                  Semaforo semaforo, String aeropuertoMasCargado) {}

    private record ResultadoIteracion(int iteracion, long semilla,
                                      ResultadoSimulacion resGA, ResultadoSimulacion resACO) {}

    private static final class ResultadoSimulacion {
        boolean colapsada = false;
        LocalDateTime momentoColapso = null;
        int totalMaletasEnrutadas = 0;
        int totalMaletasPendientes = 0;
        long tiempoEjecucionMs = 0L;
        ResultadoFitnessExperimental fitnessExperimental = null;
        List<PasoSimulacion> historial = new ArrayList<>();
    }
}
