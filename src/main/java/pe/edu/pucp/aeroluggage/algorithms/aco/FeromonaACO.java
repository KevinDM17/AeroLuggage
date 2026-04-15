package pe.edu.pucp.aeroluggage.algorithms.aco;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeromonaACO {
    private double valorInicial;
    private double depositoFeromona;
    private Map<String, Map<String, Double>> matrizFeromona;

    public FeromonaACO() {
        this.valorInicial = 1.0D;
        this.depositoFeromona = 1.0D;
        this.matrizFeromona = new HashMap<>();
    }

    public FeromonaACO(final double valorInicial, final double depositoFeromona,
        final Map<String, Map<String, Double>> matrizFeromona) {
        this.valorInicial = valorInicial;
        this.depositoFeromona = depositoFeromona;
        this.matrizFeromona = matrizFeromona;
    }

    public double getValorInicial() {
        return valorInicial;
    }

    public void setValorInicial(final double valorInicial) {
        this.valorInicial = valorInicial;
    }

    public double getDepositoFeromona() {
        return depositoFeromona;
    }

    public void setDepositoFeromona(final double depositoFeromona) {
        this.depositoFeromona = depositoFeromona;
    }

    public Map<String, Map<String, Double>> getMatrizFeromona() {
        return matrizFeromona;
    }

    public void setMatrizFeromona(final Map<String, Map<String, Double>> matrizFeromona) {
        this.matrizFeromona = matrizFeromona;
    }

    public void inicializar(final List<MaletaPendienteACO> maletasPendientes,
        final List<VueloOperacionACO> vuelosDisponibles) {
        this.matrizFeromona = new HashMap<>();
        if (maletasPendientes == null || vuelosDisponibles == null) {
            return;
        }

        for (final MaletaPendienteACO maletaPendienteACO : maletasPendientes) {
            if (maletaPendienteACO == null || maletaPendienteACO.getMaleta() == null) {
                continue;
            }

            final Map<String, Double> feromonasPorVuelo = new HashMap<>();
            for (final VueloOperacionACO vueloOperacionACO : vuelosDisponibles) {
                if (vueloOperacionACO == null || vueloOperacionACO.getVuelo() == null) {
                    continue;
                }

                feromonasPorVuelo.put(vueloOperacionACO.getVuelo().getIdVuelo(), valorInicial);
            }
            matrizFeromona.put(maletaPendienteACO.getMaleta().getIdMaleta(), feromonasPorVuelo);
        }
    }

    public double obtenerValor(final String idMaleta, final String idVuelo) {
        if (idMaleta == null || idVuelo == null) {
            return valorInicial;
        }

        final Map<String, Double> feromonasPorVuelo = matrizFeromona.get(idMaleta);
        if (feromonasPorVuelo == null) {
            return valorInicial;
        }

        return feromonasPorVuelo.getOrDefault(idVuelo, valorInicial);
    }

    public void evaporar(final double rho) {
        if (matrizFeromona == null || matrizFeromona.isEmpty()) {
            return;
        }

        for (final Map.Entry<String, Map<String, Double>> entryMaleta : matrizFeromona.entrySet()) {
            final Map<String, Double> nuevasFeromonas = new HashMap<>();
            for (final Map.Entry<String, Double> entryVuelo : entryMaleta.getValue().entrySet()) {
                nuevasFeromonas.put(entryVuelo.getKey(), (1.0D - rho) * entryVuelo.getValue());
            }
            entryMaleta.setValue(nuevasFeromonas);
        }
    }

    public void reforzar(final List<SolucionACO> soluciones) {
        if (soluciones == null || soluciones.isEmpty()) {
            return;
        }

        for (final SolucionACO solucionACO : soluciones) {
            reforzar(solucionACO);
        }
    }

    public void reforzar(final SolucionACO solucionACO) {
        if (solucionACO == null || solucionACO.getAsignaciones() == null || solucionACO.getAsignaciones().isEmpty()) {
            return;
        }

        final double costoBase = solucionACO.getCostoTotal() <= 0.0D ? 1.0D : solucionACO.getCostoTotal();
        final double incremento = depositoFeromona / costoBase;
        for (final PlanMaletaACO planMaletaACO : solucionACO.getAsignaciones()) {
            if (planMaletaACO == null || planMaletaACO.getIdMaleta() == null || planMaletaACO.getIdVueloAsignado() == null) {
                continue;
            }

            final Map<String, Double> feromonasPorVuelo =
                matrizFeromona.computeIfAbsent(planMaletaACO.getIdMaleta(), key -> new HashMap<>());
            final double valorActual = feromonasPorVuelo.getOrDefault(planMaletaACO.getIdVueloAsignado(), valorInicial);
            feromonasPorVuelo.put(planMaletaACO.getIdVueloAsignado(), valorActual + incremento);
        }
    }

    @Override
    public String toString() {
        return "FeromonaACO{"
            + "valorInicial=" + valorInicial
            + ", depositoFeromona=" + depositoFeromona
            + ", matrizFeromona=" + matrizFeromona
            + '}';
    }
}
