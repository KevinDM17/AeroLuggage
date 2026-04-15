package pe.edu.pucp.aeroluggage.domain.aco;

public class EvaluadorACO {
    private double pesoTiempoTotal;
    private double pesoIncumplimientosPlazo;
    private double pesoSobrecargaVuelos;
    private double pesoSobrecargaAlmacenes;
    private double pesoReplanificaciones;

    public EvaluadorACO() {
        this.pesoTiempoTotal = 1.0D;
        this.pesoIncumplimientosPlazo = 1.0D;
        this.pesoSobrecargaVuelos = 1.0D;
        this.pesoSobrecargaAlmacenes = 1.0D;
        this.pesoReplanificaciones = 1.0D;
    }

    public EvaluadorACO(final double pesoTiempoTotal, final double pesoIncumplimientosPlazo,
        final double pesoSobrecargaVuelos, final double pesoSobrecargaAlmacenes,
        final double pesoReplanificaciones) {
        this.pesoTiempoTotal = pesoTiempoTotal;
        this.pesoIncumplimientosPlazo = pesoIncumplimientosPlazo;
        this.pesoSobrecargaVuelos = pesoSobrecargaVuelos;
        this.pesoSobrecargaAlmacenes = pesoSobrecargaAlmacenes;
        this.pesoReplanificaciones = pesoReplanificaciones;
    }

    public double getPesoTiempoTotal() {
        return pesoTiempoTotal;
    }

    public void setPesoTiempoTotal(final double pesoTiempoTotal) {
        this.pesoTiempoTotal = pesoTiempoTotal;
    }

    public double getPesoIncumplimientosPlazo() {
        return pesoIncumplimientosPlazo;
    }

    public void setPesoIncumplimientosPlazo(final double pesoIncumplimientosPlazo) {
        this.pesoIncumplimientosPlazo = pesoIncumplimientosPlazo;
    }

    public double getPesoSobrecargaVuelos() {
        return pesoSobrecargaVuelos;
    }

    public void setPesoSobrecargaVuelos(final double pesoSobrecargaVuelos) {
        this.pesoSobrecargaVuelos = pesoSobrecargaVuelos;
    }

    public double getPesoSobrecargaAlmacenes() {
        return pesoSobrecargaAlmacenes;
    }

    public void setPesoSobrecargaAlmacenes(final double pesoSobrecargaAlmacenes) {
        this.pesoSobrecargaAlmacenes = pesoSobrecargaAlmacenes;
    }

    public double getPesoReplanificaciones() {
        return pesoReplanificaciones;
    }

    public void setPesoReplanificaciones(final double pesoReplanificaciones) {
        this.pesoReplanificaciones = pesoReplanificaciones;
    }

    public double evaluar(final SolucionACO solucionACO) {
        if (solucionACO == null) {
            return Double.POSITIVE_INFINITY;
        }

        final double costo =
            (solucionACO.getTiempoTotal() * pesoTiempoTotal)
                + (solucionACO.getIncumplimientosDePlazo() * pesoIncumplimientosPlazo)
                + (solucionACO.getSobrecargaDeVuelos() * pesoSobrecargaVuelos)
                + (solucionACO.getSobrecargaDeAlmacenes() * pesoSobrecargaAlmacenes)
                + (solucionACO.getNumeroDeReplanificaciones() * pesoReplanificaciones);
        solucionACO.setCostoTotal(costo);
        return costo;
    }

    @Override
    public String toString() {
        return "EvaluadorACO{"
            + "pesoTiempoTotal=" + pesoTiempoTotal
            + ", pesoIncumplimientosPlazo=" + pesoIncumplimientosPlazo
            + ", pesoSobrecargaVuelos=" + pesoSobrecargaVuelos
            + ", pesoSobrecargaAlmacenes=" + pesoSobrecargaAlmacenes
            + ", pesoReplanificaciones=" + pesoReplanificaciones
            + '}';
    }
}
