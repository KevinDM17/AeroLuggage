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
    private static final int MAX_LOTES_POR_DEFECTO = 0;

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
            final ArrayList<VueloProgramado> vuelosProgramados = CargadorDatosPrueba.cargarVuelosProgramados(
                    ARCHIVO_VUELOS,
                    aeropuertosPorCodigo,
                    fechaInicio,
                    CargadorDatosPrueba.DIAS_VUELOS_REPETIDOS
            );
            final ArrayList<VueloInstancia> vuelosInstancia = CargadorDatosPrueba.cargarVuelosInstancia(
                    ARCHIVO_VUELOS,
                    aeropuertosPorCodigo,
                    fechaInicio,
                    CargadorDatosPrueba.DIAS_VUELOS_REPETIDOS
            );
            System.out.println("Vuelos programados cargados: " + vuelosProgramados.size());
            System.out.println("Vuelos instancia generados: " + vuelosInstancia.size());

            int numeroLote = 1;
            int pedidosLeidos = 0;
            int maletasGeneradas = 0;
            int maletasExitosas = 0;
            int maletasSinRuta = 0;
            while (!lote.getMaletas().isEmpty() && dentroDelLimite(numeroLote, maxLotes)) {
                final ResultadoLote resultado = ejecutarLote(
                        numeroLote,
                        pedidosLeidos + 1,
                        lote,
                        vuelosProgramados,
                        vuelosInstancia,
                        aeropuertos
                );
                pedidosLeidos += resultado.getPedidosProcesados();
                maletasGeneradas += resultado.getMaletasProcesadas();
                maletasExitosas += resultado.getMaletasConRuta();
                maletasSinRuta += resultado.getMaletasSinRuta();
                if (!lector.tienePendientes() || !dentroDelLimite(numeroLote + 1, maxLotes)) {
                    break;
                }
                dormirEntreLotes(sleepMilisegundos);
                lote = lector.siguienteLote(tamanoLotePedidos);
                numeroLote++;
            }
            imprimirResumenGlobal(pedidosLeidos, maletasGeneradas, numeroLote, maletasExitosas, maletasSinRuta);
        }
    }

    private static ResultadoLote ejecutarLote(final int numeroLote, final int indicePrimerPedido,
                                              final DatosEntrada datosEntrada,
                                              final ArrayList<VueloProgramado> vuelosProgramados,
                                              final ArrayList<VueloInstancia> vuelosInstancia,
                                              final ArrayList<Aeropuerto> aeropuertos) {
        final long inicio = System.nanoTime();
        final InstanciaProblema instancia = new InstanciaProblema(
                "LOTE-" + numeroLote,
                datosEntrada.getMaletas(),
                vuelosProgramados,
                vuelosInstancia,
                aeropuertos
        );
        System.out.println();
        System.out.println("=== LOTE " + numeroLote + " ===");
        imprimirInstancia(instancia, indicePrimerPedido, datosEntrada.getPedidos().size());
        final ACO aco = ejecutarACO(instancia);
        ejecutarGA(instancia);
        final ACOReporte reporte = aco.getUltimoReporte();
        final long duracionMs = (System.nanoTime() - inicio) / 1_000_000L;
        imprimirResumenLote(instancia.getMaletas().size(), reporte, duracionMs);
        return new ResultadoLote(
                datosEntrada.getPedidos().size(),
                instancia.getMaletas().size(),
                reporte.getRutasFactibles(),
                Math.max(0, instancia.getMaletas().size() - reporte.getRutasFactibles())
        );
    }

    private static ACO ejecutarACO(final InstanciaProblema instancia) {
        final ACO aco = new ACO();
        System.out.println();
        System.out.println("=== RESULTADO ACO ===");
        aco.ejecutar(instancia);
        imprimirResultadoACO(aco);
        return aco;
    }

    private static void ejecutarGA(final InstanciaProblema instancia) {
        final GA ga = new GA();
        System.out.println();
        System.out.println("=== RESULTADO GA ===");
        ga.ejecutar(instancia);
        ga.evaluar();
        System.out.println("GA ejecutado. En esta rama la clase GA todavia no expone una solucion para imprimir.");
    }

    private static void imprimirInstancia(final InstanciaProblema instancia, final int indicePrimerPedido,
                                          final int pedidos) {
        final int indiceUltimoPedido = indicePrimerPedido + pedidos - 1;
        System.out.println("=== INSTANCIA CARGADA ===");
        System.out.println("Pedidos procesados: " + indicePrimerPedido + " al " + indiceUltimoPedido);
        System.out.println("Aeropuertos: " + instancia.getAeropuertos().size());
        System.out.println("Vuelos programados: " + instancia.getVuelosProgramados().size());
        System.out.println("Vuelos instancia para 7 dias: " + instancia.getVuelosInstancia().size());
        System.out.println("Total de pedidos en lote: " + pedidos);
        System.out.println("Total de maletas a enrutar: " + instancia.getMaletas().size());
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
        System.out.println();
        System.out.println("Detalle de rutas: " + rutas.size());
        for (int i = 0; i < rutas.size(); i++) {
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
        final StringBuilder rutaCompleta = new StringBuilder();
        for (final VueloInstancia vuelo : ruta.getSubrutas()) {
            if (vuelo == null) {
                continue;
            }
            if (rutaCompleta.length() == 0) {
                rutaCompleta.append(vuelo.getAeropuertoOrigen().getIdAeropuerto());
            }
            rutaCompleta.append(" -> ").append(vuelo.getAeropuertoDestino().getIdAeropuerto());
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
        System.out.println("  Ruta completa: " + rutaCompleta);
    }

    private static void imprimirResumenLote(final int maletasProcesadas, final ACOReporte reporte,
                                            final long duracionMs) {
        final int maletasConRuta = reporte.getRutasFactibles();
        final int maletasSinRuta = Math.max(0, maletasProcesadas - maletasConRuta);
        System.out.println();
        System.out.println("=== RESUMEN LOTE ===");
        System.out.println("Total de maletas procesadas: " + maletasProcesadas);
        System.out.println("Maletas con ruta encontrada: " + maletasConRuta);
        System.out.println("Maletas sin ruta encontrada: " + maletasSinRuta);
        System.out.println("Porcentaje de exito del lote: " + formatearPorcentaje(maletasConRuta, maletasProcesadas));
        System.out.println("Tiempo de ejecucion del lote: " + duracionMs + " ms");
    }

    private static void imprimirResumenGlobal(final int pedidosLeidos, final int maletasGeneradas,
                                              final int lotesProcesados, final int maletasExitosas,
                                              final int maletasSinRuta) {
        System.out.println();
        System.out.println("=== RESUMEN GLOBAL ===");
        System.out.println("Total de pedidos leidos: " + pedidosLeidos);
        System.out.println("Total de maletas generadas: " + maletasGeneradas);
        System.out.println("Total de lotes procesados: " + lotesProcesados);
        System.out.println("Total de maletas con ruta exitosa: " + maletasExitosas);
        System.out.println("Total de maletas sin ruta: " + maletasSinRuta);
        System.out.println("Porcentaje global de exito: " + formatearPorcentaje(maletasExitosas, maletasGeneradas));
    }

    private static String formatearPorcentaje(final int exitos, final int total) {
        if (total <= 0) {
            return "0.00%";
        }
        return String.format("%.2f%%", exitos * 100D / total);
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

    private static final class ResultadoLote {
        private final int pedidosProcesados;
        private final int maletasProcesadas;
        private final int maletasConRuta;
        private final int maletasSinRuta;

        private ResultadoLote(final int pedidosProcesados, final int maletasProcesadas,
                              final int maletasConRuta, final int maletasSinRuta) {
            this.pedidosProcesados = pedidosProcesados;
            this.maletasProcesadas = maletasProcesadas;
            this.maletasConRuta = maletasConRuta;
            this.maletasSinRuta = maletasSinRuta;
        }

        int getPedidosProcesados() {
            return pedidosProcesados;
        }

        int getMaletasProcesadas() {
            return maletasProcesadas;
        }

        int getMaletasConRuta() {
            return maletasConRuta;
        }

        int getMaletasSinRuta() {
            return maletasSinRuta;
        }
    }
}
