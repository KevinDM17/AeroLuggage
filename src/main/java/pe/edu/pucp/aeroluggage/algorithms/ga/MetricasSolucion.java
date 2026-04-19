package pe.edu.pucp.aeroluggage.algorithms.ga;

public final class MetricasSolucion {
    private final int totalMaletas;
    private final int maletasAsignadas;
    private final int maletasSinRuta;
    private final int violacionesPlazo;
    private final int vuelosSobrecargados;
    private final int sumaSobrecarga;
    private final int totalSegmentosRuta;
    private final double penalizacionTotal;

    public MetricasSolucion(final int totalMaletas, final int maletasAsignadas, final int maletasSinRuta,
        final int violacionesPlazo, final int vuelosSobrecargados, final int sumaSobrecarga,
        final int totalSegmentosRuta, final double penalizacionTotal) {
        this.totalMaletas = totalMaletas;
        this.maletasAsignadas = maletasAsignadas;
        this.maletasSinRuta = maletasSinRuta;
        this.violacionesPlazo = violacionesPlazo;
        this.vuelosSobrecargados = vuelosSobrecargados;
        this.sumaSobrecarga = sumaSobrecarga;
        this.totalSegmentosRuta = totalSegmentosRuta;
        this.penalizacionTotal = penalizacionTotal;
    }

    public int getTotalMaletas() {
        return totalMaletas;
    }

    public int getMaletasAsignadas() {
        return maletasAsignadas;
    }

    public int getMaletasSinRuta() {
        return maletasSinRuta;
    }

    public int getViolacionesPlazo() {
        return violacionesPlazo;
    }

    public int getVuelosSobrecargados() {
        return vuelosSobrecargados;
    }

    public int getSumaSobrecarga() {
        return sumaSobrecarga;
    }

    public int getTotalSegmentosRuta() {
        return totalSegmentosRuta;
    }

    public double getPenalizacionTotal() {
        return penalizacionTotal;
    }

    public double porcentajeAsignacion() {
        if (totalMaletas == 0) {
            return 0.0;
        }
        return (maletasAsignadas * 100.0) / totalMaletas;
    }

    public String colorSemaforo() {
        if (violacionesPlazo == 0 && vuelosSobrecargados == 0 && maletasSinRuta == 0) {
            return "VERDE";
        }
        final double porcentajeProblemas = (double) (violacionesPlazo + vuelosSobrecargados + maletasSinRuta)
            / Math.max(1, totalMaletas);
        if (porcentajeProblemas <= 0.1) {
            return "AMBAR";
        }
        return "ROJO";
    }

    @Override
    public String toString() {
        return "MetricasSolucion{"
            + "totalMaletas=" + totalMaletas
            + ", maletasAsignadas=" + maletasAsignadas
            + ", maletasSinRuta=" + maletasSinRuta
            + ", violacionesPlazo=" + violacionesPlazo
            + ", vuelosSobrecargados=" + vuelosSobrecargados
            + ", sumaSobrecarga=" + sumaSobrecarga
            + ", totalSegmentosRuta=" + totalSegmentosRuta
            + ", penalizacionTotal=" + penalizacionTotal
            + ", porcentajeAsignacion=" + porcentajeAsignacion()
            + ", color=" + colorSemaforo()
            + '}';
    }
}
