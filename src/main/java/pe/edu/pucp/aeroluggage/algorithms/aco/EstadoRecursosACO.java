package pe.edu.pucp.aeroluggage.algorithms.aco;

import java.util.HashMap;
import java.util.Map;

public class EstadoRecursosACO {
    private Map<String, Integer> capacidadRestanteVuelo;
    private Map<String, Integer> capacidadRestanteAlmacen;

    public EstadoRecursosACO() {
        this.capacidadRestanteVuelo = new HashMap<>();
        this.capacidadRestanteAlmacen = new HashMap<>();
    }

    public EstadoRecursosACO(final Map<String, Integer> capacidadRestanteVuelo,
        final Map<String, Integer> capacidadRestanteAlmacen) {
        this.capacidadRestanteVuelo = capacidadRestanteVuelo;
        this.capacidadRestanteAlmacen = capacidadRestanteAlmacen;
    }

    public Map<String, Integer> getCapacidadRestanteVuelo() {
        return capacidadRestanteVuelo;
    }

    public void setCapacidadRestanteVuelo(final Map<String, Integer> capacidadRestanteVuelo) {
        this.capacidadRestanteVuelo = capacidadRestanteVuelo;
    }

    public Map<String, Integer> getCapacidadRestanteAlmacen() {
        return capacidadRestanteAlmacen;
    }

    public void setCapacidadRestanteAlmacen(final Map<String, Integer> capacidadRestanteAlmacen) {
        this.capacidadRestanteAlmacen = capacidadRestanteAlmacen;
    }

    public Integer obtenerCapacidadVuelo(final String idVuelo) {
        if (idVuelo == null) {
            return null;
        }

        return capacidadRestanteVuelo.get(idVuelo);
    }

    public Integer obtenerCapacidadAlmacen(final String idAeropuerto) {
        if (idAeropuerto == null) {
            return null;
        }

        return capacidadRestanteAlmacen.get(idAeropuerto);
    }

    @Override
    public String toString() {
        return "EstadoRecursosACO{"
            + "capacidadRestanteVuelo=" + capacidadRestanteVuelo
            + ", capacidadRestanteAlmacen=" + capacidadRestanteAlmacen
            + '}';
    }
}
