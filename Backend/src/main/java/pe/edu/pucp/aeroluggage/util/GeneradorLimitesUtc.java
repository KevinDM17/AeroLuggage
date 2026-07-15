package pe.edu.pucp.aeroluggage.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class GeneradorLimitesUtc {

    private static final Path DIRECTORIO_POR_DEFECTO = Paths.get(
            "src", "main", "resources", "datos", "Envios");
    private static final Pattern NOMBRE_ARCHIVO = Pattern.compile("^_envios_([A-Z]{4})_.*\\.txt$");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMATO_SALIDA = DateTimeFormatter.ISO_LOCAL_DATE;

    private static class EstadisticaDia {
        long pedidos;
        long maletas;
    }

    public static void main(final String[] args) {
        final Path directorio = DIRECTORIO_POR_DEFECTO;
        if (!Files.isDirectory(directorio)) {
            System.err.println("Directorio de envios no encontrado: " + directorio.toAbsolutePath());
            System.exit(1);
        }

        final Map<LocalDate, EstadisticaDia> agregado = new TreeMap<>();
        int archivosProcesados = 0;
        long totalLineas = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directorio, "_envios_*.txt")) {
            for (final Path archivo : stream) {
                final String nombre = archivo.getFileName().toString();
                final var matcher = NOMBRE_ARCHIVO.matcher(nombre);
                if (!matcher.matches()) {
                    continue;
                }

                procesarArchivo(archivo, agregado);
                archivosProcesados++;
            }
        } catch (final IOException e) {
            System.err.println("Error al leer el directorio: " + e.getMessage());
            System.exit(1);
        }

        totalLineas = agregado.values().stream().mapToLong(e -> e.pedidos).sum();

        final List<String> lineasSalida = new ArrayList<>();
        lineasSalida.add("FECHA,TOTAL_PEDIDOS,TOTAL_MALETAS");
        for (final Map.Entry<LocalDate, EstadisticaDia> entry : agregado.entrySet()) {
            final EstadisticaDia est = entry.getValue();
            lineasSalida.add(entry.getKey().format(FORMATO_SALIDA) + ", "
                    + est.pedidos + ", " + est.maletas);
        }

        final Path archivoSalida = Paths.get("limites_utc.txt");
        try {
            Files.write(archivoSalida, lineasSalida, StandardCharsets.UTF_8);
        } catch (final IOException e) {
            System.err.println("Error al escribir el archivo de salida: " + e.getMessage());
            System.exit(1);
        }

        System.out.println("Archivos procesados: " + archivosProcesados);
        System.out.println("Total lineas (pedidos): " + totalLineas);
        System.out.println("Dias con pedidos: " + agregado.size());
        System.out.println("Salida: " + archivoSalida.toAbsolutePath());
    }

    private static void procesarArchivo(final Path archivo,
                                        final Map<LocalDate, EstadisticaDia> agregado) {
        try (BufferedReader reader = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                final String lineaTrimmed = linea.trim();
                if (lineaTrimmed.isEmpty()) {
                    continue;
                }
                final String[] partes = lineaTrimmed.split("-");
                if (partes.length < 6) {
                    continue;
                }
                final LocalDate fecha = LocalDate.parse(partes[1].trim(), FORMATO_FECHA);
                final int cantidad = Integer.parseInt(partes[5].trim());

                final EstadisticaDia est = agregado.computeIfAbsent(fecha, k -> new EstadisticaDia());
                est.pedidos++;
                est.maletas += cantidad;
            }
        } catch (final IOException e) {
            System.err.println("Error al leer " + archivo.getFileName() + ": " + e.getMessage());
        }
    }
}
