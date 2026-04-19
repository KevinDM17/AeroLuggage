package pe.edu.pucp.aeroluggage.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Envio;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public final class CargadorDatos {
    public static final String RECURSO_AEROPUERTOS = "data/aeropuertos.txt";
    public static final String RECURSO_PLANES_VUELO = "data/planes_vuelo.txt";
    public static final String RECURSO_ENVIOS_EBCI = "data/envios_EBCI.txt";

    private static final Set<String> CODIGOS_AMERICA_SUR = new HashSet<>(Arrays.asList(
        "SKBO", "SEQM", "SVMI", "SBBR", "SPIM", "SLLP", "SCEL", "SABE", "SGAS", "SUAA"
    ));
    private static final Set<String> CODIGOS_EUROPA = new HashSet<>(Arrays.asList(
        "LATI", "EDDI", "LOWW", "EBCI", "UMMS", "LBSF", "LKPR", "LDZA", "EKCH", "EHAM"
    ));

    private CargadorDatos() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static InstanciaProblema cargarInstanciaCompleta(final LocalDate fechaInicio, final int cantidadDias,
        final int limiteEnvios) {
        final List<Aeropuerto> aeropuertos = AeropuertoLoader.cargarDesdeClasspath(RECURSO_AEROPUERTOS);
        final Map<String, Integer> husosHorarios = AeropuertoLoader.husosHorarios(RECURSO_AEROPUERTOS);
        final List<PlanDeVuelo> planes = VueloLoader.cargarPlanes(RECURSO_PLANES_VUELO);
        final List<Vuelo> vuelos = VueloLoader.expandirVuelos(planes, fechaInicio, cantidadDias, husosHorarios);
        final List<Envio> envios = EnvioLoader.cargar(RECURSO_ENVIOS_EBCI, "EBCI", limiteEnvios);
        final List<MaletaProcesada> maletasProcesadas = expandirMaletas(envios);
        return new InstanciaProblema(maletasProcesadas, vuelos, aeropuertos);
    }

    public static List<MaletaProcesada> expandirMaletas(final List<Envio> envios) {
        if (envios == null || envios.isEmpty()) {
            return new ArrayList<>();
        }
        final List<MaletaProcesada> maletas = new ArrayList<>();
        int secuenciaGlobal = 1;
        for (final Envio envio : envios) {
            final int cantidad = Math.max(1, envio.getCantidadMaletas());
            final int plazoDias = calcularPlazoDias(envio.getIdAeropuertoSalida(), envio.getIdAeropuertoLlegada());
            for (int i = 0; i < cantidad; i++) {
                final String idMaleta = "MAL-" + String.format("%09d", secuenciaGlobal);
                maletas.add(new MaletaProcesada(
                    idMaleta,
                    envio.getIdEnvio(),
                    envio.getIdAeropuertoSalida(),
                    envio.getIdAeropuertoLlegada(),
                    plazoDias
                ));
                secuenciaGlobal++;
            }
        }
        return maletas;
    }

    public static int calcularPlazoDias(final String origen, final String destino) {
        final boolean mismoContinente = estaEnMismoContinente(origen, destino);
        return mismoContinente ? 1 : 2;
    }

    private static boolean estaEnMismoContinente(final String origen, final String destino) {
        if (origen == null || destino == null) {
            return false;
        }
        final boolean ambosAmerica = CODIGOS_AMERICA_SUR.contains(origen) && CODIGOS_AMERICA_SUR.contains(destino);
        final boolean ambosEuropa = CODIGOS_EUROPA.contains(origen) && CODIGOS_EUROPA.contains(destino);
        if (ambosAmerica || ambosEuropa) {
            return true;
        }
        final boolean ambosAsia = !CODIGOS_AMERICA_SUR.contains(origen)
            && !CODIGOS_AMERICA_SUR.contains(destino)
            && !CODIGOS_EUROPA.contains(origen)
            && !CODIGOS_EUROPA.contains(destino);
        return ambosAsia;
    }
}
