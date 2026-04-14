package pe.edu.pucp.aeroluggage.domain;

public class Visualizador {
    private String idVisualizador;

    public Visualizador() {
    }

    public Visualizador(final String idVisualizador) {
        this.idVisualizador = idVisualizador;
    }

    public String getIdVisualizador() {
        return idVisualizador;
    }

    public void setIdVisualizador(final String idVisualizador) {
        this.idVisualizador = idVisualizador;
    }

    public void mostrarMapa() {
    }

    public void mostrarMonitoreo() {
    }

    public void mostrarIndicadores() {
    }

    @Override
    public String toString() {
        return "Visualizador{"
            + "idVisualizador='" + idVisualizador + '\''
            + '}';
    }
}
