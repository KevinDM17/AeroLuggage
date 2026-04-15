package pe.edu.pucp.aeroluggage.domain.aco;

import pe.edu.pucp.aeroluggage.domain.PlanViaje;

import java.util.ArrayList;
import java.util.List;

public class PlanMaletaACO {
    private String idMaleta;
    private PlanViaje planViaje;
    private List<VueloOperacionACO> vuelosSeleccionados;
    private List<ArcoACO> arcosRecorridos;
    private boolean factible;
    private double penalizacion;

    public PlanMaletaACO() {
        this.vuelosSeleccionados = new ArrayList<>();
        this.arcosRecorridos = new ArrayList<>();
    }

    public PlanMaletaACO(final String idMaleta, final PlanViaje planViaje,
        final List<VueloOperacionACO> vuelosSeleccionados, final List<ArcoACO> arcosRecorridos,
        final boolean factible, final double penalizacion) {
        this.idMaleta = idMaleta;
        this.planViaje = planViaje;
        this.vuelosSeleccionados = vuelosSeleccionados;
        this.arcosRecorridos = arcosRecorridos;
        this.factible = factible;
        this.penalizacion = penalizacion;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public PlanViaje getPlanViaje() {
        return planViaje;
    }

    public void setPlanViaje(final PlanViaje planViaje) {
        this.planViaje = planViaje;
    }

    public List<VueloOperacionACO> getVuelosSeleccionados() {
        return vuelosSeleccionados;
    }

    public void setVuelosSeleccionados(final List<VueloOperacionACO> vuelosSeleccionados) {
        this.vuelosSeleccionados = vuelosSeleccionados;
    }

    public List<ArcoACO> getArcosRecorridos() {
        return arcosRecorridos;
    }

    public void setArcosRecorridos(final List<ArcoACO> arcosRecorridos) {
        this.arcosRecorridos = arcosRecorridos;
    }

    public boolean isFactible() {
        return factible;
    }

    public void setFactible(final boolean factible) {
        this.factible = factible;
    }

    public double getPenalizacion() {
        return penalizacion;
    }

    public void setPenalizacion(final double penalizacion) {
        this.penalizacion = penalizacion;
    }

    @Override
    public String toString() {
        return "PlanMaletaACO{"
            + "idMaleta='" + idMaleta + '\''
            + ", planViaje=" + planViaje
            + ", vuelosSeleccionados=" + vuelosSeleccionados
            + ", arcosRecorridos=" + arcosRecorridos
            + ", factible=" + factible
            + ", penalizacion=" + penalizacion
            + '}';
    }
}
