package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class OperadorMutacion {
    private final double tasaMutacion;
    private final Random aleatorio;

    public OperadorMutacion(final double tasaMutacion, final Random aleatorio) {
        this.tasaMutacion = tasaMutacion;
        this.aleatorio = aleatorio;
    }

    public void mutar(final CromosomaGenetico cromosoma, final Map<String, List<RutaCandidata>> rutasPorMaleta) {
        if (cromosoma == null || rutasPorMaleta == null) {
            return;
        }
        for (final Map.Entry<String, Integer> entrada : cromosoma.getSeleccionRutaPorMaleta().entrySet()) {
            if (aleatorio.nextDouble() > tasaMutacion) {
                continue;
            }
            final List<RutaCandidata> rutas = rutasPorMaleta.get(entrada.getKey());
            if (rutas == null || rutas.isEmpty()) {
                continue;
            }
            final int nuevoIndice = aleatorio.nextInt(rutas.size());
            cromosoma.asignarRuta(entrada.getKey(), nuevoIndice);
        }
    }
}
