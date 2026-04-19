package pe.edu.pucp.aeroluggage.data;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;

public final class AeropuertoLoader {
    private static final Pattern LINEA_AEROPUERTO = Pattern.compile(
        "^\\s*\\d{1,3}\\s+" +
        "([A-Z]{4})\\s+" +
        ".+?" +
        "([+-]\\d+)\\s+" +
        "(\\d{3,4})\\s+" +
        "Latitude:\\s*(\\d+)\\D+(\\d+)'\\s*(\\d+)\"?\\s*([NS])\\s+" +
        "Longitude:\\s*(\\d+)\\D+(\\d+)'\\s*(\\d+)\"?\\s*([EW])"
    );

    private static final int DEFAULT_CAPACIDAD = 600;

    private AeropuertoLoader() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<Aeropuerto> cargarDesdeClasspath(final String recurso) {
        final InputStream stream = AeropuertoLoader.class.getClassLoader().getResourceAsStream(recurso);
        if (stream == null) {
            throw new IllegalArgumentException("Recurso no encontrado: " + recurso);
        }
        try (Reader reader = abrirLector(stream)) {
            return parsearTexto(leerTodo(reader));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<Aeropuerto> cargarDesdeArchivo(final Path ruta) {
        if (ruta == null || !Files.exists(ruta)) {
            throw new IllegalArgumentException("Archivo no encontrado: " + ruta);
        }
        try (Reader reader = abrirLector(Files.newInputStream(ruta))) {
            return parsearTexto(leerTodo(reader));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Map<String, Integer> husosHorarios(final String recurso) {
        final Map<String, Integer> husos = new HashMap<>();
        final InputStream stream = AeropuertoLoader.class.getClassLoader().getResourceAsStream(recurso);
        if (stream == null) {
            throw new IllegalArgumentException("Recurso no encontrado: " + recurso);
        }
        try (Reader reader = abrirLector(stream)) {
            final String contenido = leerTodo(reader);
            for (final String linea : contenido.split("\\R")) {
                final Matcher matcher = LINEA_AEROPUERTO.matcher(linea);
                if (!matcher.find()) {
                    continue;
                }
                final String icao = matcher.group(1);
                final int gmt = Integer.parseInt(matcher.group(2));
                husos.put(icao, gmt);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableMap(husos);
    }

    private static Reader abrirLector(final InputStream stream) throws IOException {
        final byte[] primerosBytes = stream.readNBytes(2);
        final boolean esUtf16LE = primerosBytes.length == 2
            && (primerosBytes[0] & 0xFF) == 0xFF
            && (primerosBytes[1] & 0xFF) == 0xFE;
        if (esUtf16LE) {
            return new InputStreamReader(stream, StandardCharsets.UTF_16LE);
        }
        final boolean esUtf16BE = primerosBytes.length == 2
            && (primerosBytes[0] & 0xFF) == 0xFE
            && (primerosBytes[1] & 0xFF) == 0xFF;
        if (esUtf16BE) {
            return new InputStreamReader(stream, StandardCharsets.UTF_16BE);
        }
        return new InputStreamReader(new PrefijoInputStream(primerosBytes, stream), StandardCharsets.UTF_8);
    }

    private static String leerTodo(final Reader reader) throws IOException {
        final StringBuilder buffer = new StringBuilder();
        final char[] temp = new char[4096];
        int leidos;
        while ((leidos = reader.read(temp)) != -1) {
            buffer.append(temp, 0, leidos);
        }
        return buffer.toString();
    }

    private static List<Aeropuerto> parsearTexto(final String contenido) {
        final List<Aeropuerto> aeropuertos = new ArrayList<>();
        int secuencia = 1;
        for (final String linea : contenido.split("\\R")) {
            final Matcher matcher = LINEA_AEROPUERTO.matcher(linea);
            if (!matcher.find()) {
                continue;
            }
            final String icao = matcher.group(1);
            final int capacidad = Integer.parseInt(matcher.group(3));
            final int latGrados = Integer.parseInt(matcher.group(4));
            final int latMinutos = Integer.parseInt(matcher.group(5));
            final int latSegundos = Integer.parseInt(matcher.group(6));
            final String latDireccion = matcher.group(7);
            final int lonGrados = Integer.parseInt(matcher.group(8));
            final int lonMinutos = Integer.parseInt(matcher.group(9));
            final int lonSegundos = Integer.parseInt(matcher.group(10));
            final String lonDireccion = matcher.group(11);
            final float latitud = aGradosDecimales(latGrados, latMinutos, latSegundos, latDireccion);
            final float longitud = aGradosDecimales(lonGrados, lonMinutos, lonSegundos, lonDireccion);
            final int capacidadEfectiva = capacidad > 0 ? Math.max(capacidad, DEFAULT_CAPACIDAD) : DEFAULT_CAPACIDAD;
            final String idCiudad = "CIU-" + String.format("%03d", secuencia);
            aeropuertos.add(new Aeropuerto(icao, idCiudad, icao, capacidadEfectiva, 0, longitud, latitud));
            secuencia++;
        }
        return aeropuertos;
    }

    private static float aGradosDecimales(final int grados, final int minutos, final int segundos,
        final String direccion) {
        final float absoluto = grados + minutos / 60.0f + segundos / 3600.0f;
        final boolean esNegativo = "S".equalsIgnoreCase(direccion) || "W".equalsIgnoreCase(direccion);
        return esNegativo ? -absoluto : absoluto;
    }

    private static final class PrefijoInputStream extends InputStream {
        private final byte[] prefijo;
        private final InputStream delegado;
        private int indice;

        PrefijoInputStream(final byte[] prefijo, final InputStream delegado) {
            this.prefijo = prefijo;
            this.delegado = delegado;
            this.indice = 0;
        }

        @Override
        public int read() throws IOException {
            if (indice < prefijo.length) {
                return prefijo[indice++] & 0xFF;
            }
            return delegado.read();
        }

        @Override
        public void close() throws IOException {
            delegado.close();
        }
    }
}
