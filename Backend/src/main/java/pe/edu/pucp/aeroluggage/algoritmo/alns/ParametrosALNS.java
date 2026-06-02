package pe.edu.pucp.aeroluggage.algoritmo.alns;

public class ParametrosALNS {
    private int maxIteraciones = 150;
    private int maxIteracionesSinMejora = 40;
    private long tiempoMaximoMs = 30_000L;
    private int qMin = 1;
    private int qMax = 6;
    private int qCritical = 4;
    private int maxReintentosRuteo = 40;
    private long minutosConexion = 10L;
    private long tiempoRecojo = 10L;
    private double umbralCriticoAeropuerto = 0.25D;
    private double temperaturaInicial = 5.0D;
    private double factorEnfriamiento = 0.98D;
    private long semilla = 42L;

    private double pesoMaletasNoEnrutadas = 1_000D;
    private double pesoMaletasFueraDePlazo = 800D;
    private double pesoOverflowVuelos = 700D;
    private double pesoOverflowAeropuertos = 700D;
    private double pesoOcupacionPromedioVuelos = 5D;
    private double pesoOcupacionPromedioAeropuertos = 5D;
    private double pesoHolgura = 3D;

    private int segmentoIteraciones = 15;
    private double sigma1 = 10D;
    private double sigma2 = 6D;
    private double sigma3 = 2D;
    private double sigma4 = 0D;
    private double rho = 0.2D;
    private double pesoMinimoOperador = 0.1D;

    public static ParametrosALNS porDefecto() {
        return new ParametrosALNS();
    }

    public int getMaxIteraciones() {
        return maxIteraciones;
    }

    public void setMaxIteraciones(final int maxIteraciones) {
        this.maxIteraciones = maxIteraciones;
    }

    public int getMaxIteracionesSinMejora() {
        return maxIteracionesSinMejora;
    }

    public void setMaxIteracionesSinMejora(final int maxIteracionesSinMejora) {
        this.maxIteracionesSinMejora = maxIteracionesSinMejora;
    }

    public long getTiempoMaximoMs() {
        return tiempoMaximoMs;
    }

    public void setTiempoMaximoMs(final long tiempoMaximoMs) {
        this.tiempoMaximoMs = tiempoMaximoMs;
    }

    public int getQMin() {
        return qMin;
    }

    public void setQMin(final int qMin) {
        this.qMin = qMin;
    }

    public int getQMax() {
        return qMax;
    }

    public void setQMax(final int qMax) {
        this.qMax = qMax;
    }

    public int getQCritical() {
        return qCritical;
    }

    public void setQCritical(final int qCritical) {
        this.qCritical = qCritical;
    }

    public int getMaxReintentosRuteo() {
        return maxReintentosRuteo;
    }

    public void setMaxReintentosRuteo(final int maxReintentosRuteo) {
        this.maxReintentosRuteo = maxReintentosRuteo;
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

    public double getUmbralCriticoAeropuerto() {
        return umbralCriticoAeropuerto;
    }

    public void setUmbralCriticoAeropuerto(final double umbralCriticoAeropuerto) {
        this.umbralCriticoAeropuerto = umbralCriticoAeropuerto;
    }

    public double getTemperaturaInicial() {
        return temperaturaInicial;
    }

    public void setTemperaturaInicial(final double temperaturaInicial) {
        this.temperaturaInicial = temperaturaInicial;
    }

    public double getFactorEnfriamiento() {
        return factorEnfriamiento;
    }

    public void setFactorEnfriamiento(final double factorEnfriamiento) {
        this.factorEnfriamiento = factorEnfriamiento;
    }

    public long getSemilla() {
        return semilla;
    }

    public void setSemilla(final long semilla) {
        this.semilla = semilla;
    }

    public double getPesoMaletasNoEnrutadas() {
        return pesoMaletasNoEnrutadas;
    }

    public void setPesoMaletasNoEnrutadas(final double pesoMaletasNoEnrutadas) {
        this.pesoMaletasNoEnrutadas = pesoMaletasNoEnrutadas;
    }

    public double getPesoMaletasFueraDePlazo() {
        return pesoMaletasFueraDePlazo;
    }

    public void setPesoMaletasFueraDePlazo(final double pesoMaletasFueraDePlazo) {
        this.pesoMaletasFueraDePlazo = pesoMaletasFueraDePlazo;
    }

    public double getPesoOverflowVuelos() {
        return pesoOverflowVuelos;
    }

    public void setPesoOverflowVuelos(final double pesoOverflowVuelos) {
        this.pesoOverflowVuelos = pesoOverflowVuelos;
    }

    public double getPesoOverflowAeropuertos() {
        return pesoOverflowAeropuertos;
    }

    public void setPesoOverflowAeropuertos(final double pesoOverflowAeropuertos) {
        this.pesoOverflowAeropuertos = pesoOverflowAeropuertos;
    }

    public double getPesoOcupacionPromedioVuelos() {
        return pesoOcupacionPromedioVuelos;
    }

    public void setPesoOcupacionPromedioVuelos(final double pesoOcupacionPromedioVuelos) {
        this.pesoOcupacionPromedioVuelos = pesoOcupacionPromedioVuelos;
    }

    public double getPesoOcupacionPromedioAeropuertos() {
        return pesoOcupacionPromedioAeropuertos;
    }

    public void setPesoOcupacionPromedioAeropuertos(final double pesoOcupacionPromedioAeropuertos) {
        this.pesoOcupacionPromedioAeropuertos = pesoOcupacionPromedioAeropuertos;
    }

    public double getPesoHolgura() {
        return pesoHolgura;
    }

    public void setPesoHolgura(final double pesoHolgura) {
        this.pesoHolgura = pesoHolgura;
    }

    public int getSegmentoIteraciones() {
        return segmentoIteraciones;
    }

    public void setSegmentoIteraciones(final int segmentoIteraciones) {
        this.segmentoIteraciones = segmentoIteraciones;
    }

    public double getSigma1() {
        return sigma1;
    }

    public void setSigma1(final double sigma1) {
        this.sigma1 = sigma1;
    }

    public double getSigma2() {
        return sigma2;
    }

    public void setSigma2(final double sigma2) {
        this.sigma2 = sigma2;
    }

    public double getSigma3() {
        return sigma3;
    }

    public void setSigma3(final double sigma3) {
        this.sigma3 = sigma3;
    }

    public double getSigma4() {
        return sigma4;
    }

    public void setSigma4(final double sigma4) {
        this.sigma4 = sigma4;
    }

    public double getRho() {
        return rho;
    }

    public void setRho(final double rho) {
        this.rho = rho;
    }

    public double getPesoMinimoOperador() {
        return pesoMinimoOperador;
    }

    public void setPesoMinimoOperador(final double pesoMinimoOperador) {
        this.pesoMinimoOperador = pesoMinimoOperador;
    }
}
