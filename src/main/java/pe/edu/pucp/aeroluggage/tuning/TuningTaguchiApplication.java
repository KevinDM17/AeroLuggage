package pe.edu.pucp.aeroluggage.tuning;

import java.io.IOException;

public final class TuningTaguchiApplication {

    private static final String MODO_GA = "ga";
    private static final String MODO_ACO = "aco";

    private TuningTaguchiApplication() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void main(final String[] args) {
        final String modo = obtenerModo(args);
        try {
            if (MODO_ACO.equals(modo)) {
                TuningTaguchiRunner.ejecutarACO();
            } else {
                TuningTaguchiRunner.ejecutarGA();
            }
        } catch (final IOException e) {
            System.err.println("Error de E/S al ejecutar el tuning: " + e.getMessage());
        } catch (final IllegalArgumentException e) {
            System.err.println("Error en el archivo de configuración: " + e.getMessage());
        }
    }

    private static String obtenerModo(final String[] args) {
        if (args == null || args.length == 0 || args[0] == null || args[0].isBlank()) {
            return MODO_GA;
        }
        return args[0].trim().toLowerCase();
    }
}
