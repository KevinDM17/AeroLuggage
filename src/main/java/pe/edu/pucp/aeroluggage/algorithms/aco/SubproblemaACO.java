package pe.edu.pucp.aeroluggage.algorithms.aco;

import java.util.ArrayList;
import java.util.List;

public class SubproblemaACO {
    private List<MaletaPendienteACO> maletasPendientes;
    private List<VueloOperacionACO> vuelosDisponibles;
    private int intervaloActual;

    public SubproblemaACO() {
        this.maletasPendientes = new ArrayList<>();
        this.vuelosDisponibles = new ArrayList<>();
    }

    public SubproblemaACO(final List<MaletaPendienteACO> maletasPendientes,
        final List<VueloOperacionACO> vuelosDisponibles, final int intervaloActual) {
        this.maletasPendientes = maletasPendientes;
        this.vuelosDisponibles = vuelosDisponibles;
        this.intervaloActual = intervaloActual;
    }

    public List<MaletaPendienteACO> getMaletasPendientes() {
        return maletasPendientes;
    }

    public void setMaletasPendientes(final List<MaletaPendienteACO> maletasPendientes) {
        this.maletasPendientes = maletasPendientes;
    }

    public List<VueloOperacionACO> getVuelosDisponibles() {
        return vuelosDisponibles;
    }

    public void setVuelosDisponibles(final List<VueloOperacionACO> vuelosDisponibles) {
        this.vuelosDisponibles = vuelosDisponibles;
    }

    public int getIntervaloActual() {
        return intervaloActual;
    }

    public void setIntervaloActual(final int intervaloActual) {
        this.intervaloActual = intervaloActual;
    }

    @Override
    public String toString() {
        return "SubproblemaACO{"
            + "maletasPendientes=" + maletasPendientes
            + ", vuelosDisponibles=" + vuelosDisponibles
            + ", intervaloActual=" + intervaloActual
            + '}';
    }
}
