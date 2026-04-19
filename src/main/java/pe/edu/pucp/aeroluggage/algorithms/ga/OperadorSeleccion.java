package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.List;
import java.util.Random;

public final class OperadorSeleccion {
    private final int tamanoTorneo;
    private final Random aleatorio;

    public OperadorSeleccion(final int tamanoTorneo, final Random aleatorio) {
        this.tamanoTorneo = Math.max(2, tamanoTorneo);
        this.aleatorio = aleatorio;
    }

    public CromosomaGenetico seleccionarPorTorneo(final List<CromosomaGenetico> poblacion) {
        if (poblacion == null || poblacion.isEmpty()) {
            return null;
        }
        CromosomaGenetico mejor = null;
        for (int i = 0; i < tamanoTorneo; i++) {
            final int indice = aleatorio.nextInt(poblacion.size());
            final CromosomaGenetico candidato = poblacion.get(indice);
            if (mejor == null || candidato.getFitness() < mejor.getFitness()) {
                mejor = candidato;
            }
        }
        return mejor;
    }
}
