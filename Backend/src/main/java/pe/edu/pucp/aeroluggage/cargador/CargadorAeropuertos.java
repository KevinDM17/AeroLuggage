package pe.edu.pucp.aeroluggage.cargador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

public final class CargadorAeropuertos {

    private static final Pattern LINEA_AEROPUERTO = Pattern.compile(
            "^\\s*(\\d{1,3})\\s+"
                    + "([A-Z]{4})\\s+"
                    + "(.+?)\\s{2,}"
                    + "(.+?)\\s{2,}"
                    + "(\\w{3,4})\\s+"
                    + "([+-]?\\d+)\\s+"
                    + "(\\d+)\\s+"
                    + "Latitude\\s*:\\s*(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+([NS])\\s+"
                    + "Longitude\\s*:\\s*(\\d+)\\D+(\\d+)\\D+(\\d+)\\D+([EW]).*$");

    private CargadorAeropuertos() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<Aeropuerto> cargar(final Path archivo) {
        final List<String> lineas = leerLineas(archivo);
        final List<Aeropuerto> aeropuertos = new ArrayList<>();
        Continente continenteActual = null;

        for (final String lineaCruda : lineas) {
            final String linea = normalizar(lineaCruda);
            if (linea.isEmpty()) {
                continue;
            }
            final Continente detectado = detectarContinente(linea);
            if (detectado != null) {
                continenteActual = detectado;
                continue;
            }
            final Matcher matcher = LINEA_AEROPUERTO.matcher(linea);
            if (!matcher.matches()) {
                continue;
            }
            aeropuertos.add(construirAeropuerto(matcher, continenteActual));
        }
        return aeropuertos;
    }

    private static Aeropuerto construirAeropuerto(final Matcher matcher, final Continente continente) {
        final String indice = matcher.group(1);
        final String icao = matcher.group(2);
        final String nombreCiudad = matcher.group(3).trim();
        final int gmt = Integer.parseInt(matcher.group(6));
        final int capacidad = Integer.parseInt(matcher.group(7));
        final float latitud = dmsADecimal(
                Integer.parseInt(matcher.group(8)),
                Integer.parseInt(matcher.group(9)),
                Integer.parseInt(matcher.group(10)),
                matcher.group(11));
        final float longitud = dmsADecimal(
                Integer.parseInt(matcher.group(12)),
                Integer.parseInt(matcher.group(13)),
                Integer.parseInt(matcher.group(14)),
                matcher.group(15));

        final Ciudad ciudad = new Ciudad(indice, nombreCiudad, continente);
        return new Aeropuerto(icao, ciudad, capacidad, 0, longitud, latitud, gmt);
    }

    private static float dmsADecimal(final int grados, final int minutos, final int segundos, final String hemisferio) {
        final float decimal = grados + (minutos / 60.0f) + (segundos / 3600.0f);
        final boolean negativo = "S".equals(hemisferio) || "W".equals(hemisferio);
        return negativo ? -decimal : decimal;
    }

    private static Continente detectarContinente(final String linea) {
        final String normalizada = linea.toLowerCase();
        if (normalizada.contains("america del sur")) {
            return Continente.AMERICA_DEL_SUR;
        }
        if (normalizada.contains("america del norte")) {
            return Continente.AMERICA_DEL_NORTE;
        }
        if (normalizada.contains("centro america")) {
            return Continente.CENTRO_AMERICA;
        }
        if (normalizada.contains("europa")) {
            return Continente.EUROPA;
        }
        if (normalizada.contains("asia")) {
            return Continente.ASIA;
        }
        if (normalizada.contains("africa")) {
            return Continente.AFRICA;
        }
        if (normalizada.contains("oceania")) {
            return Continente.OCEANIA;
        }
        return null;
    }

    private static List<String> leerLineas(final Path archivo) {
        try {
            final byte[] bytes = Files.readAllBytes(archivo);
            final String contenido = decodificar(bytes);
            return List.of(contenido.split("\\r?\\n"));
        } catch (final IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo de aeropuertos: " + archivo, ex);
        }
    }

    private static String decodificar(final byte[] bytes) {
        if (bytes.length >= 2) {
            final int b0 = bytes[0] & 0xFF;
            final int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String normalizar(final String linea) {
        return linea
                .replace('\u00A0', ' ')
                .replace('\t', ' ')
                .replaceAll("[\\p{C}&&[^\\n\\r\\t]]", " ")
                .trim();
    }
}
