package pe.edu.pucp.aeroluggage.domain.aco;

import java.util.ArrayList;
import java.util.List;

public class SubproblemaACO {
    private List<MaletaPendienteACO> maletasPendientes;
    private List<VueloOperacionACO> vuelosFactibles;
    private EstadoRecursosACO estadoRecursosACO;
    private int intervaloActual;

    public SubproblemaACO() {
        this.maletasPendientes = new ArrayList<>();
        this.vuelosFactibles = new ArrayList<>();
    }

    public SubproblemaACO(final List<MaletaPendienteACO> maletasPendientes,
        final List<VueloOperacionACO> vuelosFactibles, final EstadoRecursosACO estadoRecursosACO,
        final int intervaloActual) {
        this.maletasPendientes = maletasPendientes;
        this.vuelosFactibles = vuelosFactibles;
        this.estadoRecursosACO = estadoRecursosACO;
        this.intervaloActual = intervaloActual;
    }

    public List<MaletaPendienteACO> getMaletasPendientes() {
        return maletasPendientes;
    }

    public void setMaletasPendientes(final List<MaletaPendienteACO> maletasPendientes) {
        this.maletasPendientes = maletasPendientes;
    }

    public List<VueloOperacionACO> getVuelosFactibles() {
        return vuelosFactibles;
    }

    public void setVuelosFactibles(final List<VueloOperacionACO> vuelosFactibles) {
        this.vuelosFactibles = vuelosFactibles;
    }

    public EstadoRecursosACO getEstadoRecursosACO() {
        return estadoRecursosACO;
    }

    public void setEstadoRecursosACO(final EstadoRecursosACO estadoRecursosACO) {
        this.estadoRecursosACO = estadoRecursosACO;
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
            + ", vuelosFactibles=" + vuelosFactibles
            + ", estadoRecursosACO=" + estadoRecursosACO
            + ", intervaloActual=" + intervaloActual
            + '}';
    }
}
