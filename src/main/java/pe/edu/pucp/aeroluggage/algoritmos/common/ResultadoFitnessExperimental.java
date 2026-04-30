package pe.edu.pucp.aeroluggage.algoritmos.common;

public class ResultadoFitnessExperimental {
    private final double fitnessExperimental;
    private final int maletasNoRuteadas;
    private final double usoCapacidadVuelos;
    private final double usoCapacidadAeropuertos;
    private final double duracionTotalHoras;
    private final double maxPorcentajeLlenadoVuelos;
    private final double maxPorcentajeLlenadoAeropuertos;

    public ResultadoFitnessExperimental(final double fitnessExperimental,
                                        final int maletasNoRuteadas,
                                        final double usoCapacidadVuelos,
                                        final double usoCapacidadAeropuertos,
                                        final double duracionTotalHoras,
                                        final double maxPorcentajeLlenadoVuelos,
                                        final double maxPorcentajeLlenadoAeropuertos) {
        this.fitnessExperimental = fitnessExperimental;
        this.maletasNoRuteadas = maletasNoRuteadas;
        this.usoCapacidadVuelos = usoCapacidadVuelos;
        this.usoCapacidadAeropuertos = usoCapacidadAeropuertos;
        this.duracionTotalHoras = duracionTotalHoras;
        this.maxPorcentajeLlenadoVuelos = maxPorcentajeLlenadoVuelos;
        this.maxPorcentajeLlenadoAeropuertos = maxPorcentajeLlenadoAeropuertos;
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
                duracionTotalHoras + otro.duracionTotalHoras,
                Math.max(maxPorcentajeLlenadoVuelos, otro.maxPorcentajeLlenadoVuelos),
                Math.max(maxPorcentajeLlenadoAeropuertos, otro.maxPorcentajeLlenadoAeropuertos)
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

    public double getMaxPorcentajeLlenadoVuelos() {
        return maxPorcentajeLlenadoVuelos;
    }

    public double getMaxPorcentajeLlenadoAeropuertos() {
        return maxPorcentajeLlenadoAeropuertos;
    }
}
