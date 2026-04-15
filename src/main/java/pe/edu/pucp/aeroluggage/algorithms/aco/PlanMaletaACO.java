package pe.edu.pucp.aeroluggage.algorithms.aco;

import pe.edu.pucp.aeroluggage.domain.PlanViaje;

public class PlanMaletaACO {
    private String idMaleta;
    private String idVueloAsignado;
    private PlanViaje planViaje;
    private VueloOperacionACO vueloAsignado;
    private boolean factible;
    private double costoAsignacion;

    public PlanMaletaACO() {
    }

    public PlanMaletaACO(final String idMaleta, final String idVueloAsignado, final PlanViaje planViaje,
        final VueloOperacionACO vueloAsignado, final boolean factible, final double costoAsignacion) {
        this.idMaleta = idMaleta;
        this.idVueloAsignado = idVueloAsignado;
        this.planViaje = planViaje;
        this.vueloAsignado = vueloAsignado;
        this.factible = factible;
        this.costoAsignacion = costoAsignacion;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public String getIdVueloAsignado() {
        return idVueloAsignado;
    }

    public void setIdVueloAsignado(final String idVueloAsignado) {
        this.idVueloAsignado = idVueloAsignado;
    }

    public PlanViaje getPlanViaje() {
        return planViaje;
    }

    public void setPlanViaje(final PlanViaje planViaje) {
        this.planViaje = planViaje;
    }

    public VueloOperacionACO getVueloAsignado() {
        return vueloAsignado;
    }

    public void setVueloAsignado(final VueloOperacionACO vueloAsignado) {
        this.vueloAsignado = vueloAsignado;
    }

    public boolean isFactible() {
        return factible;
    }

    public void setFactible(final boolean factible) {
        this.factible = factible;
    }

    public double getCostoAsignacion() {
        return costoAsignacion;
    }

    public void setCostoAsignacion(final double costoAsignacion) {
        this.costoAsignacion = costoAsignacion;
    }

    @Override
    public String toString() {
        return "PlanMaletaACO{"
            + "idMaleta='" + idMaleta + '\''
            + ", idVueloAsignado='" + idVueloAsignado + '\''
            + ", planViaje=" + planViaje
            + ", vueloAsignado=" + vueloAsignado
            + ", factible=" + factible
            + ", costoAsignacion=" + costoAsignacion
            + '}';
    }
}
