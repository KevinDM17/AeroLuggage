package pe.edu.pucp.aeroluggage;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACO;
import pe.edu.pucp.aeroluggage.algoritmos.aco.ACOReporte;
import pe.edu.pucp.aeroluggage.algoritmos.ga.GA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.io.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.io.DatosEntrada;

public class AeroLuggageApplication {
    private static final Path CARPETA_DOCUMENTOS = Path.of("Documentos");
    private static final Path ARCHIVO_AEROPUERTOS = CARPETA_DOCUMENTOS.resolve("Aeropuertos.txt");
    private static final Path ARCHIVO_VUELOS = CARPETA_DOCUMENTOS.resolve("planes_vuelo.txt");
    private static final Path CARPETA_ENVIOS = CARPETA_DOCUMENTOS.resolve("Envios");
    private static final int TAMANO_LOTE_PEDIDOS_POR_DEFECTO = 50;
    private static final long SLEEP_MILISEGUNDOS_POR_DEFECTO = 1000L;
    private static final int MAX_LOTES_POR_DEFECTO = 2;
    private static final int RUTAS_DETALLE_MAXIMO = 20;

    public static void main(final String[] args) {
        ejecutarPruebaAlgoritmos(args);
    }

    private static void ejecutarPruebaAlgoritmos(final String[] args) {
        final int tamanoLotePedidos = obtenerEntero(args, 0, TAMANO_LOTE_PEDIDOS_POR_DEFECTO);
        final long sleepMilisegundos = obtenerLong(args, 1, SLEEP_MILISEGUNDOS_POR_DEFECTO);
        final int maxLotes = obtenerEntero(args, 2, MAX_LOTES_POR_DEFECTO);
        final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(ARCHIVO_AEROPUERTOS);
        final Map<String, Aeropuerto> aeropuertosPorCodigo = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
        System.out.println("Aeropuertos cargados: " + aeropuertos.size());

        try (CargadorDatosPrueba.LectorLotesEnvios lector =
                     CargadorDatosPrueba.crearLectorLotesEnvios(CARPETA_ENVIOS, aeropuertosPorCodigo)) {
            DatosEntrada lote = lector.siguienteLote(tamanoLotePedidos);
            if (lote.getMaletas().isEmpty()) {
                System.out.println("No se encontraron envios para procesar.");
                return;
            }

            final LocalDate fechaInicio = lote.getMaletas().get(0).getFechaRegistro().toLocalDate();
            final ArrayList<VueloProgramado> vuelos = CargadorDatosPrueba.cargarVuelosProgramados(
                    ARCHIVO_VUELOS,
                    aeropuertosPorCodigo,
                    fechaInicio,
                    CargadorDatosPrueba.DIAS_VUELOS_REPETIDOS
            );

            int numeroLote = 1;
            while (!lote.getMaletas().isEmpty() && dentroDelLimite(numeroLote, maxLotes)) {
                ejecutarLote(numeroLote, lote, vuelos, aeropuertos);
                if (!lector.tienePendientes() || !dentroDelLimite(numeroLote + 1, maxLotes)) {
                    break;
                }
                dormirEntreLotes(sleepMilisegundos);
                lote = lector.siguienteLote(tamanoLotePedidos);
                numeroLote++;
            }
        }
    }

    private static void ejecutarLote(final int numeroLote, final DatosEntrada datosEntrada,
                                     final ArrayList<VueloProgramado> vuelos,
                                     final ArrayList<Aeropuerto> aeropuertos) {
        final InstanciaProblema instancia = new InstanciaProblema(
                "LOTE-" + numeroLote,
                datosEntrada.getMaletas(),
                vuelos,
                aeropuertos
        );
        System.out.println();
        System.out.println("=== LOTE " + numeroLote + " ===");
        imprimirInstancia(instancia, datosEntrada.getPedidos().size());
        ejecutarACO(instancia);
        ejecutarGA(instancia);
    }

    private static void ejecutarACO(final InstanciaProblema instancia) {
        final ACO aco = new ACO();
        System.out.println();
        System.out.println("=== RESULTADO ACO ===");
        aco.ejecutar(instancia);
        imprimirResultadoACO(aco);
    }

    private static void ejecutarGA(final InstanciaProblema instancia) {
        final GA ga = new GA();
        System.out.println();
        System.out.println("=== RESULTADO GA ===");
        ga.ejecutar(instancia);
        ga.evaluar();
        System.out.println("GA ejecutado. En esta rama la clase GA todavia no expone una solucion para imprimir.");
    }

    private static void imprimirInstancia(final InstanciaProblema instancia, final int pedidos) {
        System.out.println("=== INSTANCIA CARGADA ===");
        System.out.println("Aeropuertos: " + instancia.getAeropuertos().size());
        System.out.println("Vuelos generados para 7 dias: " + instancia.getVuelos().size());
        System.out.println("Pedidos del lote: " + pedidos);
        System.out.println("Maletas: " + instancia.getMaletas().size());
    }

    private static void imprimirResultadoACO(final ACO aco) {
        final ACOReporte reporte = aco.getUltimoReporte();
        System.out.println("Costo: " + aco.getUltimoCosto());
        System.out.println("Intervalos procesados: " + reporte.getIntervalosProcesados());
        System.out.println("Planes confirmados: " + reporte.getPlanesConfirmados());
        System.out.println("Rutas factibles: " + reporte.getRutasFactibles());
        System.out.println("Rutas no factibles: " + reporte.getRutasNoFactibles());
        System.out.println("Incumplimientos de plazo: " + reporte.getIncumplimientosPlazo());
        System.out.println("Sobrecarga vuelos: " + reporte.getSobrecargaVuelos());
        System.out.println("Sobrecarga almacenes: " + reporte.getSobrecargaAlmacenes());
        System.out.println("Replanificaciones: " + reporte.getNumeroReplanificaciones());
        imprimirDetalleRutas(aco.getUltimaSolucion());
    }

    private static void imprimirDetalleRutas(final Solucion solucion) {
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            System.out.println("No hay rutas para mostrar.");
            return;
        }
        final List<Ruta> rutas = solucion.getSubrutas();
        final int limite = Math.min(RUTAS_DETALLE_MAXIMO, rutas.size());
        System.out.println();
        System.out.println("Detalle de rutas mostradas: " + limite + " de " + rutas.size());
        for (int i = 0; i < limite; i++) {
            final Ruta ruta = rutas.get(i);
            if (ruta == null) {
                continue;
            }
            System.out.println((i + 1) + ". Maleta " + ruta.getIdMaleta()
                    + " | Estado: " + ruta.getEstado()
                    + " | Duracion dias: " + ruta.getDuracion());
            imprimirVuelosRuta(ruta);
        }
    }

    private static void imprimirVuelosRuta(final Ruta ruta) {
        if (ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            System.out.println("  Sin vuelos asignados");
            return;
        }
        for (final VueloInstancia vuelo : ruta.getSubrutas()) {
            if (vuelo == null) {
                continue;
            }
            System.out.println("  "
                    + vuelo.getAeropuertoOrigen().getIdAeropuerto()
                    + " -> "
                    + vuelo.getAeropuertoDestino().getIdAeropuerto()
                    + " | "
                    + vuelo.getIdVueloInstancia()
                    + " | "
                    + vuelo.getFechaSalida()
                    + " -> "
                    + vuelo.getFechaLlegada());
        }
    }

    private static boolean dentroDelLimite(final int numeroLote, final int maxLotes) {
        return maxLotes <= 0 || numeroLote <= maxLotes;
    }

    private static void dormirEntreLotes(final long sleepMilisegundos) {
        if (sleepMilisegundos <= 0) {
            return;
        }
        System.out.println();
        System.out.println("Esperando " + sleepMilisegundos + " ms antes del siguiente lote...");
        try {
            Thread.sleep(sleepMilisegundos);
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("La ejecucion por lotes fue interrumpida", exception);
        }
    }

    private static int obtenerEntero(final String[] args, final int indice, final int valorPorDefecto) {
        if (args == null || args.length <= indice || args[indice] == null || args[indice].isBlank()) {
            return valorPorDefecto;
        }
        return Integer.parseInt(args[indice]);
    }

    private static long obtenerLong(final String[] args, final int indice, final long valorPorDefecto) {
        if (args == null || args.length <= indice || args[indice] == null || args[indice].isBlank()) {
            return valorPorDefecto;
        }
        return Long.parseLong(args[indice]);
    }
}
