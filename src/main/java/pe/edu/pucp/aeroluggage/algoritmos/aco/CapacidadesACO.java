package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.util.HashMap;
import java.util.Map;

final class CapacidadesACO {
    private final Map<String, Integer> capacidadRestanteVuelo;
    private final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen;

    CapacidadesACO(
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen
    ) {
        this.capacidadRestanteVuelo = new HashMap<>(capacidadRestanteVuelo);
        this.capacidadRestanteAlmacen = new HashMap<>(capacidadRestanteAlmacen);
    }

    Map<String, Integer> getCapacidadRestanteVuelo() {
        return capacidadRestanteVuelo;
    }

    Map<String, CapacidadTemporalAlmacen> getCapacidadRestanteAlmacen() {
        return capacidadRestanteAlmacen;
    }
}
