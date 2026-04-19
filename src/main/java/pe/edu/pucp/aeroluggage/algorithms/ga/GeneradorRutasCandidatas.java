package pe.edu.pucp.aeroluggage.algorithms.ga;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public final class GeneradorRutasCandidatas {
    private static final int LIMITE_RUTAS_POR_MALETA = 6;
    private static final long MINUTOS_MINIMOS_CONEXION = 60L;
    private static final long MINUTOS_MAXIMOS_CONEXION = 24L * 60L;

    private GeneradorRutasCandidatas() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static Map<String, List<RutaCandidata>> generar(final InstanciaProblema instanciaProblema) {
        if (instanciaProblema == null) {
            return Collections.emptyMap();
        }
        final List<Vuelo> vuelos = instanciaProblema.getVuelos();
        final List<MaletaProcesada> maletas = instanciaProblema.getMaletasProcesadas();
        if (vuelos.isEmpty() || maletas.isEmpty()) {
            return Collections.emptyMap();
        }
        final Map<String, List<Vuelo>> vuelosPorOrigen = indexarPorOrigen(vuelos);
        final Map<String, List<RutaCandidata>> rutasPorMaleta = new HashMap<>();
        for (final MaletaProcesada maleta : maletas) {
            final List<RutaCandidata> rutas = buscarRutas(maleta, vuelosPorOrigen);
            rutasPorMaleta.put(maleta.getIdMaleta(), rutas);
        }
        return rutasPorMaleta;
    }

    private static Map<String, List<Vuelo>> indexarPorOrigen(final List<Vuelo> vuelos) {
        final Map<String, List<Vuelo>> indice = new HashMap<>();
        for (final Vuelo vuelo : vuelos) {
            final String origen = extraerOrigen(vuelo);
            indice.computeIfAbsent(origen, clave -> new ArrayList<>()).add(vuelo);
        }
        for (final List<Vuelo> lista : indice.values()) {
            lista.sort(Comparator.comparing(Vuelo::getFechaSalida));
        }
        return indice;
    }

    private static List<RutaCandidata> buscarRutas(final MaletaProcesada maleta,
        final Map<String, List<Vuelo>> vuelosPorOrigen) {
        final List<RutaCandidata> rutas = new ArrayList<>();
        final String origen = maleta.getIdAeropuertoSalida();
        final String destino = maleta.getIdAeropuertoLlegada();
        final long plazoMinutos = (long) maleta.getPlazoMaximoDias() * 24L * 60L;
        final List<Vuelo> directos = vuelosPorOrigen.getOrDefault(origen, Collections.emptyList());

        for (final Vuelo directo : directos) {
            if (!extraerDestino(directo).equals(destino)) {
                continue;
            }
            final long duracion = minutosEntre(directo.getFechaSalida(), directo.getFechaLlegada());
            final boolean cumple = duracion <= plazoMinutos;
            rutas.add(new RutaCandidata(List.of(directo.getIdVuelo()), duracion, cumple));
            if (rutas.size() >= LIMITE_RUTAS_POR_MALETA) {
                return rutas;
            }
        }

        for (final Vuelo primera : directos) {
            final String intermedio = extraerDestino(primera);
            if (intermedio.equals(destino) || intermedio.equals(origen)) {
                continue;
            }
            final List<Vuelo> siguientes = vuelosPorOrigen.getOrDefault(intermedio, Collections.emptyList());
            for (final Vuelo segunda : siguientes) {
                if (!extraerDestino(segunda).equals(destino)) {
                    continue;
                }
                final long espera = minutosEntre(primera.getFechaLlegada(), segunda.getFechaSalida());
                if (espera < MINUTOS_MINIMOS_CONEXION || espera > MINUTOS_MAXIMOS_CONEXION) {
                    continue;
                }
                final long duracionTotal = minutosEntre(primera.getFechaSalida(), segunda.getFechaLlegada());
                final boolean cumple = duracionTotal <= plazoMinutos;
                rutas.add(new RutaCandidata(List.of(primera.getIdVuelo(), segunda.getIdVuelo()), duracionTotal, cumple));
                if (rutas.size() >= LIMITE_RUTAS_POR_MALETA) {
                    return rutas;
                }
            }
        }
        return rutas;
    }

    private static long minutosEntre(final java.util.Date inicio, final java.util.Date fin) {
        if (inicio == null || fin == null) {
            return Long.MAX_VALUE;
        }
        return (fin.getTime() - inicio.getTime()) / 60_000L;
    }

    private static String extraerOrigen(final Vuelo vuelo) {
        final String[] partes = vuelo.getCodigo().split("-");
        return partes.length > 0 ? partes[0] : "";
    }

    private static String extraerDestino(final Vuelo vuelo) {
        final String[] partes = vuelo.getCodigo().split("-");
        return partes.length > 1 ? partes[1] : "";
    }
}
