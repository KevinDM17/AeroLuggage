package pe.edu.pucp.aeroluggage.algoritmo.ga;

public class ParametrosGA {

    private int tamanioPoblacion = 120;
    private int maxGeneraciones = 400;
    private int maxSinMejora = 60;
    private long tiempoMaximoMs = 90L * 60L * 1000L;
    private double probCruce = 0.85;
    private double probMutacion = 0.20;
    private double probBusquedaLocal = 0.20;
    private int torneoK = 4;
    private double probTorneo = 0.85;
    private int elites = 6;
    private long semilla = 42L;

    private double pesoNoEnrutadas = 50000.0;
    private double pesoVuelosOverflow = 2000.0;
    private double pesoAeropuertosOverflow = 1500.0;

    private long minutosConexion = 10L;
    private long tiempoRecojo = 10L;

    private double pesoGreedySolomon = 0.8;
    private double pesoAleatorioSolomon = 0.2;

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

    public double getPesoNoEnrutadas() {
        return pesoNoEnrutadas;
    }

    public void setPesoNoEnrutadas(final double pesoNoEnrutadas) {
        this.pesoNoEnrutadas = pesoNoEnrutadas;
    }

    public double getPesoVuelosOverflow() {
        return pesoVuelosOverflow;
    }

    public void setPesoVuelosOverflow(final double pesoVuelosOverflow) {
        this.pesoVuelosOverflow = pesoVuelosOverflow;
    }

    public double getPesoAeropuertosOverflow() {
        return pesoAeropuertosOverflow;
    }

    public void setPesoAeropuertosOverflow(final double pesoAeropuertosOverflow) {
        this.pesoAeropuertosOverflow = pesoAeropuertosOverflow;
    }

    public long getMinutosConexion() {
        return minutosConexion;
    }

    public void setMinutosConexion(final long minutosConexion) {
        this.minutosConexion = minutosConexion;
    }

    public long getTiempoRecojo() {
        return tiempoRecojo;
    }

    public void setTiempoRecojo(final long tiempoRecojo) {
        this.tiempoRecojo = tiempoRecojo;
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
