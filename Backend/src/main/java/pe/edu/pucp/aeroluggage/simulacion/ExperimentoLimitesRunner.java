package pe.edu.pucp.aeroluggage.simulacion;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

import pe.edu.pucp.aeroluggage.cargador.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;

public final class ExperimentoLimitesRunner {

    private static final Path DOCS = Path.of("Documentos");
    private static final Path ARCHIVO_AEROPUERTOS = DOCS.resolve("Aeropuertos.txt");
    private static final Path CARPETA_ENVIOS = DOCS.resolve("Envios");
    private static final Path ARCHIVO_LIMITES = Path.of("Limites.txt");
    private static final String SALTO_LINEA = System.lineSeparator();

    private ExperimentoLimitesRunner() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void ejecutar() {
        final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(ARCHIVO_AEROPUERTOS);
        final Map<String, Aeropuerto> indiceAeropuertos = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
        final int limiteMaximoMaletas = calcularLimiteMaximoMaletas(aeropuertos);
        final Map<LocalDate, Integer> demandaPorDiaOrdenada =
                CargadorDatosPrueba.ordenarDiasPorDemandaDescendente(CARPETA_ENVIOS, indiceAeropuertos);
        final String contenido = construirContenidoArchivo(limiteMaximoMaletas, demandaPorDiaOrdenada);
        escribirArchivoLimites(contenido);

        System.out.println("=== EXPERIMENTO LIMITES ===");
        System.out.printf("Archivo generado: %s%n", ARCHIVO_LIMITES.toAbsolutePath());
    }

    public static int calcularLimiteMaximoMaletas(final ArrayList<Aeropuerto> aeropuertos) {
        if (aeropuertos == null || aeropuertos.isEmpty()) {
            return 0;
        }
        int limiteMaximoMaletas = 0;
        for (final Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto == null) {
                continue;
            }
            limiteMaximoMaletas += aeropuerto.getCapacidadAlmacen();
        }
        return limiteMaximoMaletas;
    }

    private static String construirContenidoArchivo(final int limiteMaximoMaletas,
                                                    final Map<LocalDate, Integer> demandaPorDiaOrdenada) {
        final StringBuilder contenido = new StringBuilder();
        contenido.append("Capacidad total de los aeropuertos: ")
                .append(limiteMaximoMaletas)
                .append(SALTO_LINEA)
                .append(SALTO_LINEA)
                .append("Dias ordenados por demanda de maletas (descendente):")
                .append(SALTO_LINEA);
        if (demandaPorDiaOrdenada == null || demandaPorDiaOrdenada.isEmpty()) {
            contenido.append("No se encontraron pedidos validos.").append(SALTO_LINEA);
            return contenido.toString();
        }
        for (final Map.Entry<LocalDate, Integer> entry : demandaPorDiaOrdenada.entrySet()) {
            contenido.append(entry.getKey())
                    .append(" -> ")
                    .append(entry.getValue())
                    .append(" maletas")
                    .append(SALTO_LINEA);
        }
        return contenido.toString();
    }

    private static void escribirArchivoLimites(final String contenido) {
        try {
            java.nio.file.Files.writeString(ARCHIVO_LIMITES, contenido);
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo escribir el archivo Limites.txt", exception);
        }
    }
}
