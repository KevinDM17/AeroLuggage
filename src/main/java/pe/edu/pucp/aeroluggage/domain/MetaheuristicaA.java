package pe.edu.pucp.aeroluggage.domain;

public class MetaheuristicaA extends AlgoritmoMetaheuristico {
    public MetaheuristicaA() {
    }

    public MetaheuristicaA(final String nombre) {
        super(nombre);
    }

    @Override
    public void ejecutar() {
    }

    @Override
    public String toString() {
        return "MetaheuristicaA{"
            + "nombre='" + getNombre() + '\''
            + '}';
    }
}
