package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class SubproblemaACO {
    private final ArrayList<Maleta> maletasPendientes;
    private final ArrayList<VueloInstancia> vuelosDisponibles;
    private final Map<String, Integer> capacidadRestanteVueloBase;
    private final Map<String, Integer> capacidadRestanteAlmacenBase;
    private final Map<String, LocalDateTime> plazoPorMaleta;
    private final Map<String, Maleta> maletasPorId;
    private final int intervaloActual;
    private final LocalDateTime inicioIntervalo;

    SubproblemaACO(
            final ArrayList<Maleta> maletasPendientes,
            final ArrayList<VueloInstancia> vuelosDisponibles,
            final Map<String, Integer> capacidadRestanteVueloBase,
            final Map<String, Integer> capacidadRestanteAlmacenBase,
            final Map<String, LocalDateTime> plazoPorMaleta,
            final Map<String, Maleta> maletasPorId,
            final int intervaloActual,
            final LocalDateTime inicioIntervalo
    ) {
        this.maletasPendientes = maletasPendientes == null ? new ArrayList<>() : new ArrayList<>(maletasPendientes);
        this.vuelosDisponibles = vuelosDisponibles == null ? new ArrayList<>() : new ArrayList<>(vuelosDisponibles);
        this.capacidadRestanteVueloBase = new HashMap<>(capacidadRestanteVueloBase);
        this.capacidadRestanteAlmacenBase = new HashMap<>(capacidadRestanteAlmacenBase);
        this.plazoPorMaleta = new HashMap<>(plazoPorMaleta);
        this.maletasPorId = new HashMap<>(maletasPorId);
        this.intervaloActual = intervaloActual;
        this.inicioIntervalo = inicioIntervalo;
    }

    ArrayList<Maleta> getMaletasPendientes() {
        return maletasPendientes;
    }

    ArrayList<VueloInstancia> getVuelosDisponibles() {
        return vuelosDisponibles;
    }

    Map<String, Integer> getCapacidadRestanteVueloBase() {
        return capacidadRestanteVueloBase;
    }

    Map<String, Integer> getCapacidadRestanteAlmacenBase() {
        return capacidadRestanteAlmacenBase;
    }

    Map<String, LocalDateTime> getPlazoPorMaleta() {
        return plazoPorMaleta;
    }

    int getIntervaloActual() {
        return intervaloActual;
    }

    LocalDateTime getInicioIntervalo() {
        return inicioIntervalo;
    }

    Maleta obtenerMaleta(final String idMaleta) {
        return maletasPorId.get(idMaleta);
    }
}
