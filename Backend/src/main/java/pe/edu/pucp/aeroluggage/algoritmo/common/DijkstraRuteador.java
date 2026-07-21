package pe.edu.pucp.aeroluggage.algoritmo.common;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

@Slf4j
public final class DijkstraRuteador {

    private DijkstraRuteador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<VueloInstancia> rutear(final Aeropuerto origen, final Aeropuerto destino,
                                              final LocalDateTime tListo, final LocalDateTime tLimite,
                                              final GrafoTiempoExpandido grafo,
                                              final long minutosConexion,
                                              final Set<String> vuelosBloqueados) {
        return rutear(origen, destino, tListo, tLimite, grafo, minutosConexion, vuelosBloqueados, false);
    }

    public static List<VueloInstancia> rutearPermitiendoOverflow(final Aeropuerto origen, final Aeropuerto destino,
                                                                 final LocalDateTime tListo,
                                                                 final LocalDateTime tLimite,
                                                                 final GrafoTiempoExpandido grafo,
                                                                 final long minutosConexion,
                                                                 final Set<String> vuelosBloqueados) {
        return rutear(origen, destino, tListo, tLimite, grafo, minutosConexion, vuelosBloqueados, true);
    }

    private static List<VueloInstancia> rutear(final Aeropuerto origen, final Aeropuerto destino,
                                               final LocalDateTime tListo, final LocalDateTime tLimite,
                                               final GrafoTiempoExpandido grafo,
                                               final long minutosConexion,
                                               final Set<String> vuelosBloqueados,
                                               final boolean permitirOverflow) {
        if (origen == null || destino == null || tListo == null || tLimite == null || grafo == null) {
            return null;
        }
        final String icaoOrigen = origen.getIdAeropuerto();
        final String icaoDestino = destino.getIdAeropuerto();
        if (icaoOrigen == null || icaoDestino == null) {
            return null;
        }
        if (icaoOrigen.equals(icaoDestino)) {
            return List.of();
        }
        if (!tListo.isBefore(tLimite)) {
            return null;
        }

        log.debug("[Dijkstra/DIAG] RUTEAR_START: {} -> {}, tListo={}, tLimite={}",
                icaoOrigen, icaoDestino, tListo, tLimite);

        final Map<String, LocalDateTime> mejorLlegada = new HashMap<>();
        final Map<String, String> predecesorIcao = new HashMap<>();
        final Map<String, VueloInstancia> vueloUsado = new HashMap<>();

        mejorLlegada.put(icaoOrigen, tListo);
        predecesorIcao.put(icaoOrigen, null);

        final PriorityQueue<EstadoBusqueda> frontera = new PriorityQueue<>(
                Comparator.comparing(e -> e.tiempoActual));
        frontera.add(new EstadoBusqueda(icaoOrigen, tListo));

        while (!frontera.isEmpty()) {
            final EstadoBusqueda actual = frontera.poll();
            final LocalDateTime mejor = mejorLlegada.get(actual.icao);
            if (mejor != null && actual.tiempoActual.isAfter(mejor)) {
                continue;
            }
            if (actual.icao.equals(icaoDestino)) {
                final List<VueloInstancia> camino = reconstruirCamino(icaoDestino, icaoOrigen, predecesorIcao, vueloUsado);
                log.debug("[Dijkstra/DIAG] RUTEAR_END: {} -> {}, encontrado={}, largo={}",
                        icaoOrigen, icaoDestino, camino != null, camino != null ? camino.size() : 0);
                return camino;
            }
            final LocalDateTime tDesde = actual.icao.equals(icaoOrigen)
                    ? actual.tiempoActual
                    : actual.tiempoActual.plusMinutes(minutosConexion);
            final List<VueloInstancia> vuelos = grafo.vuelosDesde(actual.icao, tDesde);
            for (final VueloInstancia vuelo : vuelos) {
                if (!esUtilizable(vuelo, vuelosBloqueados, tLimite, permitirOverflow)) {
                    continue;
                }
                final LocalDateTime llegada = vuelo.getFechaLlegada();
                final String icaoSiguiente = vuelo.getAeropuertoDestino().getIdAeropuerto();
                final LocalDateTime mejorSiguiente = mejorLlegada.get(icaoSiguiente);
                if (mejorSiguiente != null && !llegada.isBefore(mejorSiguiente)) {
                    continue;
                }
                mejorLlegada.put(icaoSiguiente, llegada);
                predecesorIcao.put(icaoSiguiente, actual.icao);
                vueloUsado.put(icaoSiguiente, vuelo);
                frontera.add(new EstadoBusqueda(icaoSiguiente, llegada));
            }
        }
        log.debug("[Dijkstra/DIAG] RUTEAR_END: {} -> {}, encontrado=false (sin ruta)",
                icaoOrigen, icaoDestino);
        return null;
    }

    public static List<VueloInstancia> rutear(final Aeropuerto origen, final Aeropuerto destino,
                                              final LocalDateTime tListo, final LocalDateTime tLimite,
                                              final GrafoTiempoExpandido grafo) {
        return rutear(origen, destino, tListo, tLimite, grafo, 0L, Set.of());
    }

    private static List<VueloInstancia> reconstruirCamino(
            final String icaoDestino,
            final String icaoOrigen,
            final Map<String, String> predecesorIcao,
            final Map<String, VueloInstancia> vueloUsado) {
        final LinkedList<VueloInstancia> camino = new LinkedList<>();
        final int maxSteps = predecesorIcao.size() + 1;
        int steps = 0;
        String actual = icaoDestino;
        while (actual != null && !actual.equals(icaoOrigen)) {
            if (++steps > maxSteps) {
                log.error("[Dijkstra/DIAG] CYCLE_DETECTED: origen={}, destino={}, pasos={}, maxSteps={}",
                        icaoOrigen, icaoDestino, steps, maxSteps);
                return null;
            }
            final VueloInstancia vuelo = vueloUsado.get(actual);
            if (vuelo == null) {
                return null;
            }
            camino.addFirst(vuelo);
            actual = predecesorIcao.get(actual);
        }
        return new ArrayList<>(camino);
    }

    private static boolean esUtilizable(final VueloInstancia vuelo, final Set<String> bloqueados,
                                        final LocalDateTime tLimite,
                                        final boolean permitirOverflow) {
        if (vuelo.getEstado() == EstadoVuelo.CANCELADO) {
            return false;
        }
        if (!permitirOverflow && vuelo.getCapacidadDisponible() <= 0) {
            return false;
        }
        if (vuelo.getFechaLlegada().isAfter(tLimite)) {
            return false;
        }
        if (bloqueados != null && !bloqueados.isEmpty() && bloqueados.contains(vuelo.getIdVueloInstancia())) {
            return false;
        }
        return true;
    }

    private static final class EstadoBusqueda {
        private final String icao;
        private final LocalDateTime tiempoActual;

        private EstadoBusqueda(final String icao, final LocalDateTime tiempoActual) {
            this.icao = icao;
            this.tiempoActual = tiempoActual;
        }
    }
}
