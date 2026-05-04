package pe.edu.pucp.aeroluggage.algoritmos.common;

public class ResultadoFitnessExperimental {

    private final double fitnessExperimental;
    private final int noEnrutadas;
    private final int destinoMal;
    private final int overflowVuelos;
    private final int overflowAlmacen;
    private final double duracionNorm;
    private final double escalasNorm;
    private final double esperaNorm;

    public ResultadoFitnessExperimental(final double fitnessExperimental,
                                        final int noEnrutadas,
                                        final int destinoMal,
                                        final int overflowVuelos,
                                        final int overflowAlmacen,
                                        final double duracionNorm,
                                        final double escalasNorm,
                                        final double esperaNorm) {
        this.fitnessExperimental = fitnessExperimental;
        this.noEnrutadas = noEnrutadas;
        this.destinoMal = destinoMal;
        this.overflowVuelos = overflowVuelos;
        this.overflowAlmacen = overflowAlmacen;
        this.duracionNorm = duracionNorm;
        this.escalasNorm = escalasNorm;
        this.esperaNorm = esperaNorm;
    }

    public ResultadoFitnessExperimental sumar(final ResultadoFitnessExperimental otro) {
        if (otro == null) {
            return this;
        }
        return new ResultadoFitnessExperimental(
                fitnessExperimental + otro.fitnessExperimental,
                noEnrutadas + otro.noEnrutadas,
                destinoMal + otro.destinoMal,
                overflowVuelos + otro.overflowVuelos,
                overflowAlmacen + otro.overflowAlmacen,
                duracionNorm + otro.duracionNorm,
                escalasNorm + otro.escalasNorm,
                esperaNorm + otro.esperaNorm
        );
    }

    public double getFitnessExperimental() {
        return fitnessExperimental;
    }

    public int getNoEnrutadas() {
        return noEnrutadas;
    }

    public int getDestinoMal() {
        return destinoMal;
    }

    public int getOverflowVuelos() {
        return overflowVuelos;
    }

    public int getOverflowAlmacen() {
        return overflowAlmacen;
    }

    public double getDuracionNorm() {
        return duracionNorm;
    }

    public double getEscalasNorm() {
        return escalasNorm;
    }

    public double getEsperaNorm() {
        return esperaNorm;
    }
}
