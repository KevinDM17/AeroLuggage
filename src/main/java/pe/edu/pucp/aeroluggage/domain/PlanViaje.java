package pe.edu.pucp.aeroluggage.domain;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PlanViaje {
    private String idPlanViaje;
    private String idMaleta;
    private String aeropuertoOrigen;
    private String aeropuertoDestino;
    private List<String> idVuelos;
    private Date fechaInicio;
    private Date fechaFin;
    private String estado;

    public PlanViaje() {
        this.idVuelos = new ArrayList<>();
    }

    public PlanViaje(final String idPlanViaje, final String idMaleta, final String aeropuertoOrigen,
        final String aeropuertoDestino, final List<String> idVuelos, final Date fechaInicio,
        final Date fechaFin, final String estado) {
        this.idPlanViaje = idPlanViaje;
        this.idMaleta = idMaleta;
        this.aeropuertoOrigen = aeropuertoOrigen;
        this.aeropuertoDestino = aeropuertoDestino;
        this.idVuelos = idVuelos == null ? new ArrayList<>() : idVuelos;
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.estado = estado;
    }

    public String getIdPlanViaje() {
        return idPlanViaje;
    }

    public void setIdPlanViaje(final String idPlanViaje) {
        this.idPlanViaje = idPlanViaje;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public String getAeropuertoOrigen() {
        return aeropuertoOrigen;
    }

    public void setAeropuertoOrigen(final String aeropuertoOrigen) {
        this.aeropuertoOrigen = aeropuertoOrigen;
    }

    public String getAeropuertoDestino() {
        return aeropuertoDestino;
    }

    public void setAeropuertoDestino(final String aeropuertoDestino) {
        this.aeropuertoDestino = aeropuertoDestino;
    }

    public List<String> getIdVuelos() {
        return idVuelos;
    }

    public void setIdVuelos(final List<String> idVuelos) {
        this.idVuelos = idVuelos == null ? new ArrayList<>() : idVuelos;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(final String estado) {
        this.estado = estado;
    }

    @Override
    public String toString() {
        return "PlanViaje{"
            + "idPlanViaje='" + idPlanViaje + '\''
            + ", idMaleta='" + idMaleta + '\''
            + ", aeropuertoOrigen='" + aeropuertoOrigen + '\''
            + ", aeropuertoDestino='" + aeropuertoDestino + '\''
            + ", idVuelos=" + idVuelos
            + ", fechaInicio=" + fechaInicio
            + ", fechaFin=" + fechaFin
            + ", estado='" + estado + '\''
            + '}';
    }
}
