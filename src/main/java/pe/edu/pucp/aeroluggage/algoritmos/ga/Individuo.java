package pe.edu.pucp.aeroluggage.algoritmos.ga;

import pe.edu.pucp.aeroluggage.algoritmos.Solucion;

public class Individuo {

    private Solucion solucion;
    private double fitness;

    public Individuo() {
    }

    public Individuo(final Solucion solucion, final double fitness) {
        this.solucion = solucion;
        this.fitness = fitness;
    }

    public Individuo(final Solucion solucion) {
        this.solucion = solucion;
        this.fitness = 0.0;
    }

    public Individuo clonarProfundo() {
        final Solucion copia = solucion != null ? solucion.clonarProfundo() : null;
        return new Individuo(copia, fitness);
    }

    public Solucion getSolucion() {
        return solucion;
    }

    public void setSolucion(final Solucion solucion) {
        this.solucion = solucion;
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(final double fitness) {
        this.fitness = fitness;
    }

    @Override
    public String toString() {
        return "Individuo{fitness=" + fitness + ", solucion=" + solucion + '}';
    }
}
