package pe.edu.pucp.aeroluggage.domain;

public class MetaheuristicaB extends AlgoritmoMetaheuristico {
    public MetaheuristicaB() {
    }

    public MetaheuristicaB(final String nombre) {
        super(nombre);
    }

    @Override
    public void ejecutar() {
    }

    @Override
    public String toString() {
        return "MetaheuristicaB{"
            + "nombre='" + getNombre() + '\''
            + '}';
    }
}
