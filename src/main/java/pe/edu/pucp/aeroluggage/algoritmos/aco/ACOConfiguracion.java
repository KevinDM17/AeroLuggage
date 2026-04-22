package pe.edu.pucp.aeroluggage.algoritmos.aco;

public class ACOConfiguracion {
    private static final int NTS_POR_DEFECTO = 1;
    private static final int MAX_ITER_POR_DEFECTO = 10;
    private static final int N_ANTS_POR_DEFECTO = 8;
    private static final double ALPHA_POR_DEFECTO = 1D;
    private static final double BETA_POR_DEFECTO = 2D;
    private static final double RHO_POR_DEFECTO = 0.15D;
    private static final double GAMMA_POR_DEFECTO = 0.05D;
    private static final double TAU0_POR_DEFECTO = 1D;
    private static final double TAU_MIN_POR_DEFECTO = 0.01D;
    private static final double TAU_MAX_POR_DEFECTO = 25D;
    private static final double PENALIZACION_NO_FACTIBLE_POR_DEFECTO = 1000D;
    private static final double PENALIZACION_INCUMPLIMIENTO_POR_DEFECTO = 300D;
    private static final double PENALIZACION_SOBRECARGA_VUELO_POR_DEFECTO = 200D;
    private static final double PENALIZACION_SOBRECARGA_ALMACEN_POR_DEFECTO = 120D;
    private static final double PENALIZACION_REPLANIFICACION_POR_DEFECTO = 25D;
    private static final int HORAS_POR_INTERVALO_POR_DEFECTO = 24;
    private static final long SEMILLA_POR_DEFECTO = 42L;

    private int nts;
    private int maxIter;
    private int nAnts;
    private double alpha;
    private double beta;
    private double rho;
    private double gamma;
    private double tau0;
    private double tauMin;
    private double tauMax;
    private double penalizacionNoFactible;
    private double penalizacionIncumplimiento;
    private double penalizacionSobrecargaVuelo;
    private double penalizacionSobrecargaAlmacen;
    private double penalizacionReplanificacion;
    private int horasPorIntervalo;
    private long semilla;

    public ACOConfiguracion() {
        nts = NTS_POR_DEFECTO;
        maxIter = MAX_ITER_POR_DEFECTO;
        nAnts = N_ANTS_POR_DEFECTO;
        alpha = ALPHA_POR_DEFECTO;
        beta = BETA_POR_DEFECTO;
        rho = RHO_POR_DEFECTO;
        gamma = GAMMA_POR_DEFECTO;
        tau0 = TAU0_POR_DEFECTO;
        tauMin = TAU_MIN_POR_DEFECTO;
        tauMax = TAU_MAX_POR_DEFECTO;
        penalizacionNoFactible = PENALIZACION_NO_FACTIBLE_POR_DEFECTO;
        penalizacionIncumplimiento = PENALIZACION_INCUMPLIMIENTO_POR_DEFECTO;
        penalizacionSobrecargaVuelo = PENALIZACION_SOBRECARGA_VUELO_POR_DEFECTO;
        penalizacionSobrecargaAlmacen = PENALIZACION_SOBRECARGA_ALMACEN_POR_DEFECTO;
        penalizacionReplanificacion = PENALIZACION_REPLANIFICACION_POR_DEFECTO;
        horasPorIntervalo = HORAS_POR_INTERVALO_POR_DEFECTO;
        semilla = SEMILLA_POR_DEFECTO;
    }

    public int getNts() {
        return nts;
    }

    public void setNts(final int nts) {
        this.nts = nts;
    }

    public int getMaxIter() {
        return maxIter;
    }

    public void setMaxIter(final int maxIter) {
        this.maxIter = maxIter;
    }

    public int getNAnts() {
        return nAnts;
    }

    public void setNAnts(final int nAnts) {
        this.nAnts = nAnts;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(final double alpha) {
        this.alpha = alpha;
    }

    public double getBeta() {
        return beta;
    }

    public void setBeta(final double beta) {
        this.beta = beta;
    }

    public double getRho() {
        return rho;
    }

    public void setRho(final double rho) {
        this.rho = rho;
    }

    public double getGamma() {
        return gamma;
    }

    public void setGamma(final double gamma) {
        this.gamma = gamma;
    }

    public double getTau0() {
        return tau0;
    }

    public void setTau0(final double tau0) {
        this.tau0 = tau0;
    }

    public double getTauMin() {
        return tauMin;
    }

    public void setTauMin(final double tauMin) {
        this.tauMin = tauMin;
    }

    public double getTauMax() {
        return tauMax;
    }

    public void setTauMax(final double tauMax) {
        this.tauMax = tauMax;
    }

    public double getPenalizacionNoFactible() {
        return penalizacionNoFactible;
    }

    public void setPenalizacionNoFactible(final double penalizacionNoFactible) {
        this.penalizacionNoFactible = penalizacionNoFactible;
    }

    public double getPenalizacionIncumplimiento() {
        return penalizacionIncumplimiento;
    }

    public void setPenalizacionIncumplimiento(final double penalizacionIncumplimiento) {
        this.penalizacionIncumplimiento = penalizacionIncumplimiento;
    }

    public double getPenalizacionSobrecargaVuelo() {
        return penalizacionSobrecargaVuelo;
    }

    public void setPenalizacionSobrecargaVuelo(final double penalizacionSobrecargaVuelo) {
        this.penalizacionSobrecargaVuelo = penalizacionSobrecargaVuelo;
    }

    public double getPenalizacionSobrecargaAlmacen() {
        return penalizacionSobrecargaAlmacen;
    }

    public void setPenalizacionSobrecargaAlmacen(final double penalizacionSobrecargaAlmacen) {
        this.penalizacionSobrecargaAlmacen = penalizacionSobrecargaAlmacen;
    }

    public double getPenalizacionReplanificacion() {
        return penalizacionReplanificacion;
    }

    public void setPenalizacionReplanificacion(final double penalizacionReplanificacion) {
        this.penalizacionReplanificacion = penalizacionReplanificacion;
    }

    public int getHorasPorIntervalo() {
        return horasPorIntervalo;
    }

    public void setHorasPorIntervalo(final int horasPorIntervalo) {
        this.horasPorIntervalo = horasPorIntervalo;
    }

    public long getSemilla() {
        return semilla;
    }

    public void setSemilla(final long semilla) {
        this.semilla = semilla;
    }
}
