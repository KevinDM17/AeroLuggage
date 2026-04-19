package pe.edu.pucp.aeroluggage.escenarios;

import java.util.List;
import pe.edu.pucp.aeroluggage.algorithms.ga.MetricasSolucion;
import pe.edu.pucp.aeroluggage.algorithms.ga.ResultadoGenetico;

public final class ReporteEscenario {
    private final TipoEscenario tipo;
    private final int totalEnviosProcesados;
    private final int totalMaletasProcesadas;
    private final long tiempoTotalMs;
    private final List<ResultadoGenetico> resultadosPorLote;
    private final MetricasSolucion metricasAgregadas;
    private final boolean colapsoDetectado;
    private final int loteColapso;

    public ReporteEscenario(final TipoEscenario tipo, final int totalEnviosProcesados,
        final int totalMaletasProcesadas, final long tiempoTotalMs,
        final List<ResultadoGenetico> resultadosPorLote, final MetricasSolucion metricasAgregadas,
        final boolean colapsoDetectado, final int loteColapso) {
        this.tipo = tipo;
        this.totalEnviosProcesados = totalEnviosProcesados;
        this.totalMaletasProcesadas = totalMaletasProcesadas;
        this.tiempoTotalMs = tiempoTotalMs;
        this.resultadosPorLote = resultadosPorLote == null ? List.of() : List.copyOf(resultadosPorLote);
        this.metricasAgregadas = metricasAgregadas;
        this.colapsoDetectado = colapsoDetectado;
        this.loteColapso = loteColapso;
    }

    public TipoEscenario getTipo() {
        return tipo;
    }

    public int getTotalEnviosProcesados() {
        return totalEnviosProcesados;
    }

    public int getTotalMaletasProcesadas() {
        return totalMaletasProcesadas;
    }

    public long getTiempoTotalMs() {
        return tiempoTotalMs;
    }

    public List<ResultadoGenetico> getResultadosPorLote() {
        return resultadosPorLote;
    }

    public MetricasSolucion getMetricasAgregadas() {
        return metricasAgregadas;
    }

    public boolean isColapsoDetectado() {
        return colapsoDetectado;
    }

    public int getLoteColapso() {
        return loteColapso;
    }

    @Override
    public String toString() {
        return "ReporteEscenario{"
            + "tipo=" + tipo
            + ", enviosProcesados=" + totalEnviosProcesados
            + ", maletasProcesadas=" + totalMaletasProcesadas
            + ", tiempoMs=" + tiempoTotalMs
            + ", metricas=" + metricasAgregadas
            + ", colapso=" + colapsoDetectado
            + ", loteColapso=" + loteColapso
            + '}';
    }
}
