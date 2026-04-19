package pe.edu.pucp.aeroluggage.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import pe.edu.pucp.aeroluggage.domain.Envio;

public final class EnvioLoader {
    private static final int CAMPOS_ESPERADOS = 7;

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private EnvioLoader() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<Envio> cargar(final String recurso, final String origen) {
        return cargar(recurso, origen, Integer.MAX_VALUE, envio -> true);
    }

    public static List<Envio> cargar(final String recurso, final String origen, final int limite) {
        return cargar(recurso, origen, limite, envio -> true);
    }

    public static List<Envio> cargar(final String recurso, final String origen, final int limite,
        final Predicate<Envio> filtro) {
        final InputStream stream = EnvioLoader.class.getClassLoader().getResourceAsStream(recurso);
        if (stream == null) {
            throw new IllegalArgumentException("Recurso no encontrado: " + recurso);
        }
        return leerEnvios(stream, origen, limite, filtro);
    }

    public static List<Envio> cargarDesdeArchivo(final Path ruta, final String origen, final int limite) {
        if (ruta == null || !Files.exists(ruta)) {
            throw new IllegalArgumentException("Archivo no encontrado: " + ruta);
        }
        try {
            return leerEnvios(Files.newInputStream(ruta), origen, limite, envio -> true);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Envio> leerEnvios(final InputStream stream, final String origen, final int limite,
        final Predicate<Envio> filtro) {
        final List<Envio> envios = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                if (envios.size() >= limite) {
                    break;
                }
                final String lineaLimpia = linea.trim();
                if (lineaLimpia.isEmpty()) {
                    continue;
                }
                final Envio envio = parsearLinea(lineaLimpia, origen);
                if (envio != null && filtro.test(envio)) {
                    envios.add(envio);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return envios;
    }

    private static Envio parsearLinea(final String linea, final String origen) {
        final String[] campos = linea.split("-");
        if (campos.length < CAMPOS_ESPERADOS) {
            return null;
        }
        final String idEnvio = "ENV-" + campos[0].trim();
        final String fechaTexto = campos[1].trim();
        final int hora = Integer.parseInt(campos[2].trim());
        final int minuto = Integer.parseInt(campos[3].trim());
        final String destino = campos[4].trim();
        final int cantidadMaletas = Integer.parseInt(campos[5].trim());
        final LocalDateTime fechaRegistro = LocalDateTime.of(
            java.time.LocalDate.parse(fechaTexto, FORMATO_FECHA),
            java.time.LocalTime.of(hora, minuto)
        );
        final Date fecha = Date.from(fechaRegistro.toInstant(ZoneOffset.UTC));
        return new Envio(idEnvio, origen, destino, fecha, cantidadMaletas, "REGISTRADO");
    }
}
