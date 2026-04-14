package pe.edu.pucp.aeroluggage.domain;

public abstract class AlgoritmoMetaheuristico {
    private String nombre;

    public AlgoritmoMetaheuristico() {
    }

    public AlgoritmoMetaheuristico(final String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(final String nombre) {
        this.nombre = nombre;
    }

    public void ejecutar() {
    }

    public void evaluar() {
    }

    @Override
    public String toString() {
        return "AlgoritmoMetaheuristico{"
            + "nombre='" + nombre + '\''
            + '}';
    }
}
