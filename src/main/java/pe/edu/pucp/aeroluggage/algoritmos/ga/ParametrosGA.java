package pe.edu.pucp.aeroluggage.algoritmos.ga;

public class ParametrosGA {

    private int tamanioPoblacion = 80;
    private int maxGeneraciones = 300;
    private int maxSinMejora = 40;
    private long tiempoMaximoMs = 90L * 60L * 1000L;
    private double probCruce = 0.85;
    private double probMutacion = 0.15;
    private double probBusquedaLocal = 0.20;
    private int torneoK = 3;
    private double probTorneo = 0.85;
    private int elites = 4;
    private long semilla = 42L;
    private long minutosConexion = 60L;

    private double w1MaletasIncumplidas = 10000.0;
    private double w2ExcesoHorasPlazo = 100.0;
    private double w3OverflowVuelo = 500.0;
    private double w4OverflowAlmacen = 500.0;
    private double w5TransitoPromedio = 1.0;

    private double penalizacionRutaVacia = 5000.0;
    private double penalizacionSinDestino = 8000.0;
    private double penalizacionRutaInvalida = 3000.0;

    private double pesoGreedySolomon = 0.6;
    private double pesoAleatorioSolomon = 0.4;

    private double umbralSemaforoVerde = 0.70;
    private double umbralSemaforoAmbar = 0.90;
    private double toleranciaIncumplimientoVerde = 0.0;
    private double toleranciaIncumplimientoAmbar = 0.05;

    public ParametrosGA() {
    }

    public static ParametrosGA pordefecto() {
        return new ParametrosGA();
    }

    public int getTamanioPoblacion() {
        return tamanioPoblacion;
    }

    public void setTamanioPoblacion(final int tamanioPoblacion) {
        this.tamanioPoblacion = tamanioPoblacion;
    }

    public int getMaxGeneraciones() {
        return maxGeneraciones;
    }

    public void setMaxGeneraciones(final int maxGeneraciones) {
        this.maxGeneraciones = maxGeneraciones;
    }

    public int getMaxSinMejora() {
        return maxSinMejora;
    }

    public void setMaxSinMejora(final int maxSinMejora) {
        this.maxSinMejora = maxSinMejora;
    }

    public long getTiempoMaximoMs() {
        return tiempoMaximoMs;
    }

    public void setTiempoMaximoMs(final long tiempoMaximoMs) {
        this.tiempoMaximoMs = tiempoMaximoMs;
    }

    public double getProbCruce() {
        return probCruce;
    }

    public void setProbCruce(final double probCruce) {
        this.probCruce = probCruce;
    }

    public double getProbMutacion() {
        return probMutacion;
    }

    public void setProbMutacion(final double probMutacion) {
        this.probMutacion = probMutacion;
    }

    public double getProbBusquedaLocal() {
        return probBusquedaLocal;
    }

    public void setProbBusquedaLocal(final double probBusquedaLocal) {
        this.probBusquedaLocal = probBusquedaLocal;
    }

    public int getTorneoK() {
        return torneoK;
    }

    public void setTorneoK(final int torneoK) {
        this.torneoK = torneoK;
    }

    public double getProbTorneo() {
        return probTorneo;
    }

    public void setProbTorneo(final double probTorneo) {
        this.probTorneo = probTorneo;
    }

    public int getElites() {
        return elites;
    }

    public void setElites(final int elites) {
        this.elites = elites;
    }

    public long getSemilla() {
        return semilla;
    }

    public void setSemilla(final long semilla) {
        this.semilla = semilla;
    }

    public long getMinutosConexion() {
        return minutosConexion;
    }

    public void setMinutosConexion(final long minutosConexion) {
        this.minutosConexion = minutosConexion;
    }

    public double getW1MaletasIncumplidas() {
        return w1MaletasIncumplidas;
    }

    public void setW1MaletasIncumplidas(final double w1MaletasIncumplidas) {
        this.w1MaletasIncumplidas = w1MaletasIncumplidas;
    }

    public double getW2ExcesoHorasPlazo() {
        return w2ExcesoHorasPlazo;
    }

    public void setW2ExcesoHorasPlazo(final double w2ExcesoHorasPlazo) {
        this.w2ExcesoHorasPlazo = w2ExcesoHorasPlazo;
    }

    public double getW3OverflowVuelo() {
        return w3OverflowVuelo;
    }

    public void setW3OverflowVuelo(final double w3OverflowVuelo) {
        this.w3OverflowVuelo = w3OverflowVuelo;
    }

    public double getW4OverflowAlmacen() {
        return w4OverflowAlmacen;
    }

    public void setW4OverflowAlmacen(final double w4OverflowAlmacen) {
        this.w4OverflowAlmacen = w4OverflowAlmacen;
    }

    public double getW5TransitoPromedio() {
        return w5TransitoPromedio;
    }

    public void setW5TransitoPromedio(final double w5TransitoPromedio) {
        this.w5TransitoPromedio = w5TransitoPromedio;
    }

    public double getPenalizacionRutaVacia() {
        return penalizacionRutaVacia;
    }

    public void setPenalizacionRutaVacia(final double penalizacionRutaVacia) {
        this.penalizacionRutaVacia = penalizacionRutaVacia;
    }

    public double getPenalizacionSinDestino() {
        return penalizacionSinDestino;
    }

    public void setPenalizacionSinDestino(final double penalizacionSinDestino) {
        this.penalizacionSinDestino = penalizacionSinDestino;
    }

    public double getPenalizacionRutaInvalida() {
        return penalizacionRutaInvalida;
    }

    public void setPenalizacionRutaInvalida(final double penalizacionRutaInvalida) {
        this.penalizacionRutaInvalida = penalizacionRutaInvalida;
    }

    public double getPesoGreedySolomon() {
        return pesoGreedySolomon;
    }

    public void setPesoGreedySolomon(final double pesoGreedySolomon) {
        this.pesoGreedySolomon = pesoGreedySolomon;
    }

    public double getPesoAleatorioSolomon() {
        return pesoAleatorioSolomon;
    }

    public void setPesoAleatorioSolomon(final double pesoAleatorioSolomon) {
        this.pesoAleatorioSolomon = pesoAleatorioSolomon;
    }

    public double getUmbralSemaforoVerde() {
        return umbralSemaforoVerde;
    }

    public void setUmbralSemaforoVerde(final double umbralSemaforoVerde) {
        this.umbralSemaforoVerde = umbralSemaforoVerde;
    }

    public double getUmbralSemaforoAmbar() {
        return umbralSemaforoAmbar;
    }

    public void setUmbralSemaforoAmbar(final double umbralSemaforoAmbar) {
        this.umbralSemaforoAmbar = umbralSemaforoAmbar;
    }

    public double getToleranciaIncumplimientoVerde() {
        return toleranciaIncumplimientoVerde;
    }

    public void setToleranciaIncumplimientoVerde(final double toleranciaIncumplimientoVerde) {
        this.toleranciaIncumplimientoVerde = toleranciaIncumplimientoVerde;
    }

    public double getToleranciaIncumplimientoAmbar() {
        return toleranciaIncumplimientoAmbar;
    }

    public void setToleranciaIncumplimientoAmbar(final double toleranciaIncumplimientoAmbar) {
        this.toleranciaIncumplimientoAmbar = toleranciaIncumplimientoAmbar;
    }
}
