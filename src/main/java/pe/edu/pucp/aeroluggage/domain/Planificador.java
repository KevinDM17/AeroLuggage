package pe.edu.pucp.aeroluggage.domain;

public class Planificador {
    private String idPlanificador;

    public Planificador() {
    }

    public Planificador(final String idPlanificador) {
        this.idPlanificador = idPlanificador;
    }

    public String getIdPlanificador() {
        return idPlanificador;
    }

    public void setIdPlanificador(final String idPlanificador) {
        this.idPlanificador = idPlanificador;
    }

    public void planificarRutas() {
    }

    public void replanificarRutas() {
    }

    public void evaluarEscenario() {
    }

    @Override
    public String toString() {
        return "Planificador{"
            + "idPlanificador='" + idPlanificador + '\''
            + '}';
    }
}
