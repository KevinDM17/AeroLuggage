package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import pe.edu.pucp.aeroluggage.algoritmos.common.DijkstraRuteador;
import pe.edu.pucp.aeroluggage.algoritmos.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class GARuteadorCache {
    private static final int MAX_ENTRADAS_CACHE = 20000;
    private static final List<VueloInstancia> RUTA_NO_ENCONTRADA = List.of();
    private static final Map<String, List<VueloInstancia>> RUTAS = Collections.synchronizedMap(
            new LinkedHashMap<String, List<VueloInstancia>>(MAX_ENTRADAS_CACHE, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, List<VueloInstancia>> eldest) {
                    return size() > MAX_ENTRADAS_CACHE;
                }
            }
    );

    private GARuteadorCache() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static List<VueloInstancia> rutear(final Aeropuerto origen,
                                       final Aeropuerto destino,
                                       final LocalDateTime tListo,
                                       final LocalDateTime tLimite,
                                       final GrafoTiempoExpandido grafo,
                                       final long minutosConexion,
                                       final Set<String> vuelosBloqueados) {
        final String clave = construirClave(origen, destino, tListo, tLimite, grafo, minutosConexion, vuelosBloqueados);
        final List<VueloInstancia> rutaCacheada = RUTAS.get(clave);
        if (rutaCacheada != null) {
            return rutaCacheada == RUTA_NO_ENCONTRADA ? null : new ArrayList<>(rutaCacheada);
        }

        final List<VueloInstancia> ruta = DijkstraRuteador.rutear(
                origen,
                destino,
                tListo,
                tLimite,
                grafo,
                minutosConexion,
                vuelosBloqueados
        );
        RUTAS.put(clave, ruta == null ? RUTA_NO_ENCONTRADA : List.copyOf(ruta));
        return ruta == null ? null : new ArrayList<>(ruta);
    }

    private static String construirClave(final Aeropuerto origen,
                                         final Aeropuerto destino,
                                         final LocalDateTime tListo,
                                         final LocalDateTime tLimite,
                                         final GrafoTiempoExpandido grafo,
                                         final long minutosConexion,
                                         final Set<String> vuelosBloqueados) {
        return idAeropuerto(origen)
                + '|'
                + idAeropuerto(destino)
                + '|'
                + tListo
                + '|'
                + tLimite
                + '|'
                + System.identityHashCode(grafo)
                + '|'
                + minutosConexion
                + '|'
                + normalizarBloqueados(vuelosBloqueados);
    }

    private static String normalizarBloqueados(final Set<String> vuelosBloqueados) {
        if (vuelosBloqueados == null || vuelosBloqueados.isEmpty()) {
            return "";
        }
        return String.join(",", new TreeSet<>(vuelosBloqueados));
    }

    private static String idAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto == null ? "" : aeropuerto.getIdAeropuerto();
    }
}
