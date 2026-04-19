package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.List;
import pe.edu.pucp.aeroluggage.algorithms.Individuo;

public final class ResultadoGenetico {
    private final Individuo mejorIndividuo;
    private final MetricasSolucion metricas;
    private final int generacionesEjecutadas;
    private final long tiempoEjecucionMs;
    private final List<Double> historialFitness;

    public ResultadoGenetico(final Individuo mejorIndividuo, final MetricasSolucion metricas,
        final int generacionesEjecutadas, final long tiempoEjecucionMs, final List<Double> historialFitness) {
        this.mejorIndividuo = mejorIndividuo;
        this.metricas = metricas;
        this.generacionesEjecutadas = generacionesEjecutadas;
        this.tiempoEjecucionMs = tiempoEjecucionMs;
        this.historialFitness = historialFitness == null ? List.of() : List.copyOf(historialFitness);
    }

    public Individuo getMejorIndividuo() {
        return mejorIndividuo;
    }

    public MetricasSolucion getMetricas() {
        return metricas;
    }

    public int getGeneracionesEjecutadas() {
        return generacionesEjecutadas;
    }

    public long getTiempoEjecucionMs() {
        return tiempoEjecucionMs;
    }

    public List<Double> getHistorialFitness() {
        return historialFitness;
    }

    public boolean esSolucionFactible() {
        return metricas != null
            && metricas.getMaletasSinRuta() == 0
            && metricas.getViolacionesPlazo() == 0
            && metricas.getVuelosSobrecargados() == 0;
    }

    @Override
    public String toString() {
        return "ResultadoGenetico{"
            + "generaciones=" + generacionesEjecutadas
            + ", tiempoMs=" + tiempoEjecucionMs
            + ", fitness=" + (mejorIndividuo == null ? Double.NaN : mejorIndividuo.getFitness())
            + ", metricas=" + metricas
            + '}';
    }
}
