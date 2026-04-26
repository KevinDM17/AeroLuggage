package pe.edu.pucp.aeroluggage.algoritmos.common;

public class ResultadoFitnessExperimental {
    private final double fitnessExperimental;
    private final int maletasNoRuteadas;
    private final double usoCapacidadVuelos;
    private final double usoCapacidadAeropuertos;
    private final double duracionTotalHoras;

    public ResultadoFitnessExperimental(final double fitnessExperimental,
                                        final int maletasNoRuteadas,
                                        final double usoCapacidadVuelos,
                                        final double usoCapacidadAeropuertos,
                                        final double duracionTotalHoras) {
        this.fitnessExperimental = fitnessExperimental;
        this.maletasNoRuteadas = maletasNoRuteadas;
        this.usoCapacidadVuelos = usoCapacidadVuelos;
        this.usoCapacidadAeropuertos = usoCapacidadAeropuertos;
        this.duracionTotalHoras = duracionTotalHoras;
    }

    public ResultadoFitnessExperimental sumar(final ResultadoFitnessExperimental otro) {
        if (otro == null) {
            return this;
        }
        return new ResultadoFitnessExperimental(
                fitnessExperimental + otro.fitnessExperimental,
                maletasNoRuteadas + otro.maletasNoRuteadas,
                usoCapacidadVuelos + otro.usoCapacidadVuelos,
                usoCapacidadAeropuertos + otro.usoCapacidadAeropuertos,
                duracionTotalHoras + otro.duracionTotalHoras
        );
    }

    public double getFitnessExperimental() {
        return fitnessExperimental;
    }

    public int getMaletasNoRuteadas() {
        return maletasNoRuteadas;
    }

    public double getUsoCapacidadVuelos() {
        return usoCapacidadVuelos;
    }

    public double getUsoCapacidadAeropuertos() {
        return usoCapacidadAeropuertos;
    }

    public double getDuracionTotalHoras() {
        return duracionTotalHoras;
    }
}
