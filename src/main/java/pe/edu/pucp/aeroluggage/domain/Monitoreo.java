package pe.edu.pucp.aeroluggage.domain;

import java.util.Date;

public class Monitoreo {
    private String idMonitoreo;
    private Date fechaHoraActualizacion;
    private String ubicacionActual;

    public Monitoreo() {
    }

    public Monitoreo(final String idMonitoreo, final Date fechaHoraActualizacion, final String ubicacionActual) {
        this.idMonitoreo = idMonitoreo;
        this.fechaHoraActualizacion = fechaHoraActualizacion;
        this.ubicacionActual = ubicacionActual;
    }

    public String getIdMonitoreo() {
        return idMonitoreo;
    }

    public void setIdMonitoreo(final String idMonitoreo) {
        this.idMonitoreo = idMonitoreo;
    }

    public Date getFechaHoraActualizacion() {
        return fechaHoraActualizacion;
    }

    public void setFechaHoraActualizacion(final Date fechaHoraActualizacion) {
        this.fechaHoraActualizacion = fechaHoraActualizacion;
    }

    public String getUbicacionActual() {
        return ubicacionActual;
    }

    public void setUbicacionActual(final String ubicacionActual) {
        this.ubicacionActual = ubicacionActual;
    }

    public void actualizarUbicacion() {
    }

    public void generarReporte() {
    }

    @Override
    public String toString() {
        return "Monitoreo{"
            + "idMonitoreo='" + idMonitoreo + '\''
            + ", fechaHoraActualizacion=" + fechaHoraActualizacion
            + ", ubicacionActual='" + ubicacionActual + '\''
            + '}';
    }
}
