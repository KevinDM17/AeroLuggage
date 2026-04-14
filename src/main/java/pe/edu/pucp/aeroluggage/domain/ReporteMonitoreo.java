package pe.edu.pucp.aeroluggage.domain;

import java.util.Date;

public class ReporteMonitoreo {
    private String idReporte;
    private Date fechaSolicitud;
    private String contenido;

    public ReporteMonitoreo() {
    }

    public ReporteMonitoreo(final String idReporte, final Date fechaSolicitud, final String contenido) {
        this.idReporte = idReporte;
        this.fechaSolicitud = fechaSolicitud;
        this.contenido = contenido;
    }

    public String getIdReporte() {
        return idReporte;
    }

    public void setIdReporte(final String idReporte) {
        this.idReporte = idReporte;
    }

    public Date getFechaSolicitud() {
        return fechaSolicitud;
    }

    public void setFechaSolicitud(final Date fechaSolicitud) {
        this.fechaSolicitud = fechaSolicitud;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(final String contenido) {
        this.contenido = contenido;
    }

    @Override
    public String toString() {
        return "ReporteMonitoreo{"
            + "idReporte='" + idReporte + '\''
            + ", fechaSolicitud=" + fechaSolicitud
            + ", contenido='" + contenido + '\''
            + '}';
    }
}
