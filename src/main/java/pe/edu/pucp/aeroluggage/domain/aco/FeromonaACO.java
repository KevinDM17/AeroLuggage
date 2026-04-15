package pe.edu.pucp.aeroluggage.domain.aco;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeromonaACO {
    private double valorInicial;
    private Map<String, Double> nivelesFeromona;

    public FeromonaACO() {
        this.valorInicial = 1.0D;
        this.nivelesFeromona = new HashMap<>();
    }

    public FeromonaACO(final double valorInicial, final Map<String, Double> nivelesFeromona) {
        this.valorInicial = valorInicial;
        this.nivelesFeromona = nivelesFeromona;
    }

    public double getValorInicial() {
        return valorInicial;
    }

    public void setValorInicial(final double valorInicial) {
        this.valorInicial = valorInicial;
    }

    public Map<String, Double> getNivelesFeromona() {
        return nivelesFeromona;
    }

    public void setNivelesFeromona(final Map<String, Double> nivelesFeromona) {
        this.nivelesFeromona = nivelesFeromona;
    }

    public void inicializar(final List<ArcoACO> arcos) {
        if (arcos == null || arcos.isEmpty()) {
            return;
        }

        for (final ArcoACO arco : arcos) {
            if (arco == null) {
                continue;
            }

            nivelesFeromona.put(generarClave(arco), valorInicial);
        }
    }

    public double obtenerValor(final ArcoACO arcoACO) {
        if (arcoACO == null) {
            return valorInicial;
        }

        final String claveArco = generarClave(arcoACO);
        return nivelesFeromona.getOrDefault(claveArco, valorInicial);
    }

    public void actualizarLocalmente(final ArcoACO arcoACO, final double rho) {
        if (arcoACO == null) {
            return;
        }

        final String claveArco = generarClave(arcoACO);
        final double valorActual = obtenerValor(arcoACO);
        final double nuevoValor = ((1.0D - rho) * valorActual) + (rho * valorInicial);
        nivelesFeromona.put(claveArco, nuevoValor);
    }

    public void actualizarGlobalmente(final SolucionACO solucionACO, final double rho) {
        if (solucionACO == null || solucionACO.getPlanesMaleta() == null || solucionACO.getPlanesMaleta().isEmpty()) {
            return;
        }

        final double deposito = calcularDeposito(solucionACO);
        for (final PlanMaletaACO planMaletaACO : solucionACO.getPlanesMaleta()) {
            if (planMaletaACO == null || planMaletaACO.getArcosRecorridos() == null) {
                continue;
            }

            for (final ArcoACO arcoACO : planMaletaACO.getArcosRecorridos()) {
                final String claveArco = generarClave(arcoACO);
                final double valorActual = obtenerValor(arcoACO);
                final double nuevoValor = ((1.0D - rho) * valorActual) + deposito;
                nivelesFeromona.put(claveArco, nuevoValor);
            }
        }
    }

    public void evaporar(final double rho) {
        if (nivelesFeromona == null || nivelesFeromona.isEmpty()) {
            return;
        }

        final Map<String, Double> nuevosNiveles = new HashMap<>();
        for (final Map.Entry<String, Double> entry : nivelesFeromona.entrySet()) {
            final double nuevoValor = (1.0D - rho) * entry.getValue();
            nuevosNiveles.put(entry.getKey(), nuevoValor);
        }
        nivelesFeromona = nuevosNiveles;
    }

    private String generarClave(final ArcoACO arcoACO) {
        final String origen = arcoACO.getIdAeropuertoOrigen() == null ? "" : arcoACO.getIdAeropuertoOrigen();
        final String destino = arcoACO.getIdAeropuertoDestino() == null ? "" : arcoACO.getIdAeropuertoDestino();
        final String vuelo = arcoACO.getIdVuelo() == null ? "" : arcoACO.getIdVuelo();
        return origen + "|" + destino + "|" + vuelo;
    }

    private double calcularDeposito(final SolucionACO solucionACO) {
        final double costoTotal = solucionACO.getCostoTotal();
        if (costoTotal <= 0.0D) {
            return 1.0D;
        }

        return 1.0D / costoTotal;
    }

    @Override
    public String toString() {
        return "FeromonaACO{"
            + "valorInicial=" + valorInicial
            + ", nivelesFeromona=" + nivelesFeromona
            + '}';
    }
}
