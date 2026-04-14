package pe.edu.pucp.aeroluggage.domain;

import java.util.Date;

public class PlanViaje {
    private String idPlan;
    private Date fechaCreacion;
    private String estado;

    public PlanViaje() {
    }

    public PlanViaje(final String idPlan, final Date fechaCreacion, final String estado) {
        this.idPlan = idPlan;
        this.fechaCreacion = fechaCreacion;
        this.estado = estado;
    }

    public String getIdPlan() {
        return idPlan;
    }

    public void setIdPlan(final String idPlan) {
        this.idPlan = idPlan;
    }

    public Date getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(final Date fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    public void generarPlan() {
    }

    @Override
    public String toString() {
        return "PlanViaje{"
            + "idPlan='" + idPlan + '\''
            + ", fechaCreacion=" + fechaCreacion
            + ", estado='" + estado + '\''
            + '}';
    }
}
