package pe.edu.pucp.aeroluggage.algorithms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public class InstanciaProblema {
    private final List<MaletaProcesada> maletasProcesadas;
    private final List<Vuelo> vuelos;
    private final List<Aeropuerto> aeropuertos;

    public InstanciaProblema(final List<MaletaProcesada> maletasProcesadas, final List<Vuelo> vuelos,
        final List<Aeropuerto> aeropuertos) {
        this.maletasProcesadas = copyList(maletasProcesadas);
        this.vuelos = copyList(vuelos);
        this.aeropuertos = copyList(aeropuertos);
    }

    public List<MaletaProcesada> getMaletasProcesadas() {
        return new ArrayList<>(maletasProcesadas);
    }

    public List<Vuelo> getVuelos() {
        return new ArrayList<>(vuelos);
    }

    public List<Aeropuerto> getAeropuertos() {
        return new ArrayList<>(aeropuertos);
    }

    private static <T> List<T> copyList(final List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }
}
