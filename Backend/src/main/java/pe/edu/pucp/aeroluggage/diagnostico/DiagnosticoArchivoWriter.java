package pe.edu.pucp.aeroluggage.diagnostico;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DiagnosticoArchivoWriter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path ARCHIVO = Path.of(System.getProperty("user.dir"), "diagnostico-planificador.txt");

    private DiagnosticoArchivoWriter() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static synchronized void append(final String origen, final String mensaje) {
        if (mensaje == null || mensaje.isBlank()) {
            return;
        }
        final String etiqueta = origen == null || origen.isBlank() ? "GENERAL" : origen;
        final String linea = String.format(
                "[%s] [%s] %s%n",
                LocalDateTime.now().format(TIMESTAMP),
                etiqueta,
                mensaje
        );
        try {
            final Path directorio = ARCHIVO.getParent();
            if (directorio != null) {
                Files.createDirectories(directorio);
            }
            Files.writeString(
                    ARCHIVO,
                    linea,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (final IOException ignored) {
            // Best-effort diagnostics writer: never fail the simulation due to logging.
        }
    }

    public static Path getArchivo() {
        return ARCHIVO;
    }
}
