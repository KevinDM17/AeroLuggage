package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class SubproblemaACO {
    private final ArrayList<Maleta> maletasPendientes;
    private final ArrayList<VueloInstancia> vuelosDisponibles;
    private final Map<String, ArrayList<VueloInstancia>> vuelosPorOrigen;
    private final Map<String, Integer> capacidadRestanteVueloBase;
    private final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacenBase;
    private final Map<String, LocalDateTime> plazoPorMaleta;
    private final Map<String, Maleta> maletasPorId;
    private final LocalDateTime inicioIntervalo;

    SubproblemaACO(
            final List<Maleta> maletasPendientes,
            final List<VueloInstancia> vuelosDisponibles,
            final Map<String, Integer> capacidadRestanteVueloBase,
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacenBase,
            final Map<String, LocalDateTime> plazoPorMaleta,
            final Map<String, Maleta> maletasPorId,
            final LocalDateTime inicioIntervalo
    ) {
        this.maletasPendientes = maletasPendientes == null ? new ArrayList<>() : new ArrayList<>(maletasPendientes);
        this.vuelosDisponibles = vuelosDisponibles == null ? new ArrayList<>() : new ArrayList<>(vuelosDisponibles);
        this.vuelosPorOrigen = indexarVuelosPorOrigen(this.vuelosDisponibles);
        this.capacidadRestanteVueloBase = new HashMap<>(capacidadRestanteVueloBase);
        // Copia profunda: cada hormiga necesita su propia instancia para no interferir con las demás.
        this.capacidadRestanteAlmacenBase = CapacidadTemporalAlmacen.clonarMapa(capacidadRestanteAlmacenBase);
        this.plazoPorMaleta = new HashMap<>(plazoPorMaleta);
        this.maletasPorId = new HashMap<>(maletasPorId);
        this.inicioIntervalo = inicioIntervalo;
    }

    ArrayList<Maleta> getMaletasPendientes() {
        return maletasPendientes;
    }

    ArrayList<VueloInstancia> getVuelosDisponibles() {
        return vuelosDisponibles;
    }

    ArrayList<VueloInstancia> getVuelosDesde(final String idAeropuertoOrigen) {
        if (idAeropuertoOrigen == null) {
            return new ArrayList<>();
        }
        return vuelosPorOrigen.getOrDefault(idAeropuertoOrigen, new ArrayList<>());
    }

    Map<String, Integer> getCapacidadRestanteVueloBase() {
        return capacidadRestanteVueloBase;
    }

    Map<String, CapacidadTemporalAlmacen> getCapacidadRestanteAlmacenBase() {
        return capacidadRestanteAlmacenBase;
    }

    Map<String, LocalDateTime> getPlazoPorMaleta() {
        return plazoPorMaleta;
    }

    LocalDateTime getInicioIntervalo() {
        return inicioIntervalo;
    }

    Maleta obtenerMaleta(final String idMaleta) {
        return maletasPorId.get(idMaleta);
    }

    private Map<String, ArrayList<VueloInstancia>> indexarVuelosPorOrigen(
            final ArrayList<VueloInstancia> vuelosDisponibles
    ) {
        final Map<String, ArrayList<VueloInstancia>> indice = new HashMap<>();
        for (final VueloInstancia vueloInstancia : vuelosDisponibles) {
            if (vueloInstancia == null || vueloInstancia.getAeropuertoOrigen() == null
                    || vueloInstancia.getAeropuertoOrigen().getIdAeropuerto() == null) {
                continue;
            }
            indice.computeIfAbsent(
                    vueloInstancia.getAeropuertoOrigen().getIdAeropuerto(),
                    key -> new ArrayList<>()
            ).add(vueloInstancia);
        }
        return indice;
    }
}
