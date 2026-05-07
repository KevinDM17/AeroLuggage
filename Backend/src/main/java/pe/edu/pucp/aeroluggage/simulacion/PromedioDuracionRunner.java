package pe.edu.pucp.aeroluggage.simulacion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import pe.edu.pucp.aeroluggage.cargador.CargadorDatosPrueba;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

public final class PromedioDuracionRunner {

    private static final Path DOCS = Path.of("Documentos");
    private static final Path ARCHIVO_AEROPUERTOS = DOCS.resolve("Aeropuertos.txt");
    private static final Path ARCHIVO_VUELOS = DOCS.resolve("planes_vuelo.txt");
    private static final Path ARCHIVO_PROMEDIO_DURACION = Path.of("PromedioDuracion.txt");
    private static final LocalDate FECHA_BASE = LocalDate.of(2026, 1, 1);
    private static final int DIAS_REFERENCIA = 1;
    private static final String SALTO_LINEA = System.lineSeparator();
    private static final Locale LOCALE_REPORTE = Locale.US;

    private PromedioDuracionRunner() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void ejecutar() {
        final ArrayList<Aeropuerto> aeropuertos = CargadorDatosPrueba.cargarAeropuertos(ARCHIVO_AEROPUERTOS);
        final Map<String, Aeropuerto> indiceAeropuertos = CargadorDatosPrueba.indexarAeropuertos(aeropuertos);
        final ArrayList<VueloProgramado> vuelosProgramados = CargadorDatosPrueba.cargarVuelosProgramados(
                ARCHIVO_VUELOS,
                indiceAeropuertos,
                FECHA_BASE,
                DIAS_REFERENCIA
        );
        final int totalVuelosProgramados = contarVuelosValidos(vuelosProgramados);
        final double promedioDuracionHoras = CargadorDatosPrueba.calcularPromedioDuracionVuelosHoras(vuelosProgramados);
        final String contenido = construirContenidoArchivo(totalVuelosProgramados, promedioDuracionHoras);
        escribirArchivoPromedioDuracion(contenido);

        System.out.println("=== PROMEDIO DURACION DE VUELOS ===");
        System.out.printf("Archivo generado: %s%n", ARCHIVO_PROMEDIO_DURACION.toAbsolutePath());
    }

    private static int contarVuelosValidos(final ArrayList<VueloProgramado> vuelosProgramados) {
        if (vuelosProgramados == null || vuelosProgramados.isEmpty()) {
            return 0;
        }
        int totalVuelosValidos = 0;
        for (final VueloProgramado vueloProgramado : vuelosProgramados) {
            final boolean vueloValido = vueloProgramado != null
                    && vueloProgramado.getHoraSalida() != null
                    && vueloProgramado.getHoraLlegada() != null;
            if (vueloValido) {
                totalVuelosValidos++;
            }
        }
        return totalVuelosValidos;
    }

    private static String construirContenidoArchivo(final int totalVuelosProgramados,
                                                    final double promedioDuracionHoras) {
        final StringBuilder contenido = new StringBuilder();
        contenido.append("Total de vuelos programados: ")
                .append(totalVuelosProgramados)
                .append(SALTO_LINEA);
        contenido.append("Promedio de duracion de vuelos: ")
                .append(String.format(LOCALE_REPORTE, "%.3f", promedioDuracionHoras))
                .append(" horas")
                .append(SALTO_LINEA);
        return contenido.toString();
    }

    private static void escribirArchivoPromedioDuracion(final String contenido) {
        try {
            Files.writeString(ARCHIVO_PROMEDIO_DURACION, contenido);
        } catch (final IOException exception) {
            throw new IllegalStateException("No se pudo escribir el archivo PromedioDuracion.txt", exception);
        }
    }
}
