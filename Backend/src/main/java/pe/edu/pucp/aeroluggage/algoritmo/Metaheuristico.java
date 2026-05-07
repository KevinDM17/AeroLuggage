package pe.edu.pucp.aeroluggage.algoritmo;

public abstract class Metaheuristico {
    public Metaheuristico() {
    }

    public abstract void ejecutar(final InstanciaProblema instancia);

    public abstract void evaluar();
}