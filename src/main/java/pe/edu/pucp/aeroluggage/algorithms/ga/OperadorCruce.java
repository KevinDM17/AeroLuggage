package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public final class OperadorCruce {
    private final double tasaCruce;
    private final Random aleatorio;

    public OperadorCruce(final double tasaCruce, final Random aleatorio) {
        this.tasaCruce = tasaCruce;
        this.aleatorio = aleatorio;
    }

    public CromosomaGenetico[] cruzar(final CromosomaGenetico padreA, final CromosomaGenetico padreB) {
        if (padreA == null || padreB == null) {
            return new CromosomaGenetico[] {padreA, padreB};
        }
        if (aleatorio.nextDouble() > tasaCruce) {
            return new CromosomaGenetico[] {new CromosomaGenetico(padreA), new CromosomaGenetico(padreB)};
        }
        final Map<String, Integer> genesA = padreA.copiaInterna();
        final Map<String, Integer> genesB = padreB.copiaInterna();
        final Map<String, Integer> genesHijoA = new LinkedHashMap<>();
        final Map<String, Integer> genesHijoB = new LinkedHashMap<>();
        for (final Map.Entry<String, Integer> entrada : genesA.entrySet()) {
            final String idMaleta = entrada.getKey();
            final int valorA = entrada.getValue();
            final int valorB = genesB.getOrDefault(idMaleta, valorA);
            if (aleatorio.nextBoolean()) {
                genesHijoA.put(idMaleta, valorA);
                genesHijoB.put(idMaleta, valorB);
                continue;
            }
            genesHijoA.put(idMaleta, valorB);
            genesHijoB.put(idMaleta, valorA);
        }
        return new CromosomaGenetico[] {new CromosomaGenetico(genesHijoA), new CromosomaGenetico(genesHijoB)};
    }
}
