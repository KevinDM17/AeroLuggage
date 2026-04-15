package pe.edu.pucp.aeroluggage.algorithms.aco;

import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Maleta;
import pe.edu.pucp.aeroluggage.domain.PlanViaje;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContextoACO {
    private List<Maleta> maletasPendientes;
    private List<Aeropuerto> aeropuertos;
    private List<Vuelo> vuelosDisponibles;
    private List<EventoOperacionACO> eventos;
    private List<PlanViaje> planesConfirmados;
    private Map<String, Integer> tiemposOrigenDestino;
    private int numeroIntervalosTiempo;
    private int maxIteracionesPorIntervalo;
    private int numeroHormigas;
    private double alpha;
    private double beta;
    private double rho;
    private double gamma;
    private double depositoFeromona;

    public ContextoACO() {
        this.maletasPendientes = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
        this.vuelosDisponibles = new ArrayList<>();
        this.eventos = new ArrayList<>();
        this.planesConfirmados = new ArrayList<>();
    }

    public ContextoACO(final List<Maleta> maletasPendientes, final List<Aeropuerto> aeropuertos,
        final List<Vuelo> vuelosDisponibles, final List<EventoOperacionACO> eventos,
        final List<PlanViaje> planesConfirmados, final Map<String, Integer> tiemposOrigenDestino,
        final int numeroIntervalosTiempo, final int maxIteracionesPorIntervalo, final int numeroHormigas,
        final double alpha, final double beta, final double rho, final double gamma) {
        this.maletasPendientes = maletasPendientes;
        this.aeropuertos = aeropuertos;
        this.vuelosDisponibles = vuelosDisponibles;
        this.eventos = eventos;
        this.planesConfirmados = planesConfirmados;
        this.tiemposOrigenDestino = tiemposOrigenDestino;
        this.numeroIntervalosTiempo = numeroIntervalosTiempo;
        this.maxIteracionesPorIntervalo = maxIteracionesPorIntervalo;
        this.numeroHormigas = numeroHormigas;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.gamma = gamma;
        this.depositoFeromona = 1.0D;
    }

    public List<Maleta> getMaletasPendientes() {
        return maletasPendientes;
    }

    public void setMaletasPendientes(final List<Maleta> maletasPendientes) {
        this.maletasPendientes = maletasPendientes;
    }

    public List<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public void setAeropuertos(final List<Aeropuerto> aeropuertos) {
        this.aeropuertos = aeropuertos;
    }

    public List<Vuelo> getVuelosDisponibles() {
        return vuelosDisponibles;
    }

    public void setVuelosDisponibles(final List<Vuelo> vuelosDisponibles) {
        this.vuelosDisponibles = vuelosDisponibles;
    }

    public List<EventoOperacionACO> getEventos() {
        return eventos;
    }

    public void setEventos(final List<EventoOperacionACO> eventos) {
        this.eventos = eventos;
    }

    public List<PlanViaje> getPlanesConfirmados() {
        return planesConfirmados;
    }

    public void setPlanesConfirmados(final List<PlanViaje> planesConfirmados) {
        this.planesConfirmados = planesConfirmados;
    }

    public Map<String, Integer> getTiemposOrigenDestino() {
        return tiemposOrigenDestino;
    }

    public void setTiemposOrigenDestino(final Map<String, Integer> tiemposOrigenDestino) {
        this.tiemposOrigenDestino = tiemposOrigenDestino;
    }

    public int getNumeroIntervalosTiempo() {
        return numeroIntervalosTiempo;
    }

    public void setNumeroIntervalosTiempo(final int numeroIntervalosTiempo) {
        this.numeroIntervalosTiempo = numeroIntervalosTiempo;
    }

    public int getMaxIteracionesPorIntervalo() {
        return maxIteracionesPorIntervalo;
    }

    public void setMaxIteracionesPorIntervalo(final int maxIteracionesPorIntervalo) {
        this.maxIteracionesPorIntervalo = maxIteracionesPorIntervalo;
    }

    public int getNumeroHormigas() {
        return numeroHormigas;
    }

    public void setNumeroHormigas(final int numeroHormigas) {
        this.numeroHormigas = numeroHormigas;
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

    public double getDepositoFeromona() {
        return depositoFeromona;
    }

    public void setDepositoFeromona(final double depositoFeromona) {
        this.depositoFeromona = depositoFeromona;
    }

    @Override
    public String toString() {
        return "ContextoACO{"
            + "maletasPendientes=" + maletasPendientes
            + ", aeropuertos=" + aeropuertos
            + ", vuelosDisponibles=" + vuelosDisponibles
            + ", eventos=" + eventos
            + ", planesConfirmados=" + planesConfirmados
            + ", tiemposOrigenDestino=" + tiemposOrigenDestino
            + ", numeroIntervalosTiempo=" + numeroIntervalosTiempo
            + ", maxIteracionesPorIntervalo=" + maxIteracionesPorIntervalo
            + ", numeroHormigas=" + numeroHormigas
            + ", alpha=" + alpha
            + ", beta=" + beta
            + ", rho=" + rho
            + ", gamma=" + gamma
            + ", depositoFeromona=" + depositoFeromona
            + '}';
    }
}
