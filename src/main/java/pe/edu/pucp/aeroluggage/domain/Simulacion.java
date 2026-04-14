package pe.edu.pucp.aeroluggage.domain;

import java.util.Date;

public class Simulacion {
    private String idSimulacion;
    private String tipoEscenario;
    private Date fechaInicio;
    private Date fechaFin;

    public Simulacion() {
    }

    public Simulacion(final String idSimulacion, final String tipoEscenario, final Date fechaInicio,
        final Date fechaFin) {
        this.idSimulacion = idSimulacion;
        this.tipoEscenario = tipoEscenario;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
    }

    public String getIdSimulacion() {
        return idSimulacion;
    }

    public void setIdSimulacion(final String idSimulacion) {
        this.idSimulacion = idSimulacion;
    }

    public String getTipoEscenario() {
        return tipoEscenario;
    }

    public void setTipoEscenario(final String tipoEscenario) {
        this.tipoEscenario = tipoEscenario;
    }

    public Date getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(final Date fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public Date getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(final Date fechaFin) {
        this.fechaFin = fechaFin;
    }

    public void ejecutar() {
    }

    public void generarResultados() {
    }

    @Override
    public String toString() {
        return "Simulacion{"
            + "idSimulacion='" + idSimulacion + '\''
            + ", tipoEscenario='" + tipoEscenario + '\''
            + ", fechaInicio=" + fechaInicio
            + ", fechaFin=" + fechaFin
            + '}';
    }
}
