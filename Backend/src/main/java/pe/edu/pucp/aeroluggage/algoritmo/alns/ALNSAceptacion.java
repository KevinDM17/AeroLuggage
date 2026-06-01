package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.util.Random;

final class ALNSAceptacion {
    private ALNSAceptacion() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static boolean aceptar(final double fitnessActual,
                           final double fitnessNuevo,
                           final double temperatura,
                           final Random random) {
        if (fitnessNuevo < fitnessActual) {
            return true;
        }
        if (temperatura <= 0.0D) {
            return false;
        }
        final double probabilidad = Math.exp(-((fitnessNuevo - fitnessActual) / temperatura));
        return random.nextDouble() < probabilidad;
    }
}
