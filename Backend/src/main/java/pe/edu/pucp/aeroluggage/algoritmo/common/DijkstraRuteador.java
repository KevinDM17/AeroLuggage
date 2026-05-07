package pe.edu.pucp.aeroluggage.algoritmo.common;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class DijkstraRuteador {

    private DijkstraRuteador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static List<VueloInstancia> rutear(final Aeropuerto origen, final Aeropuerto destino,
                                              final LocalDateTime tListo, final LocalDateTime tLimite,
                                              final GrafoTiempoExpandido grafo,
                                              final long minutosConexion,
                                              final Set<String> vuelosBloqueados) {
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

        final Map<String, LocalDateTime> mejorLlegada = new HashMap<>();
        mejorLlegada.put(icaoOrigen, tListo);

        final PriorityQueue<EstadoBusqueda> frontera = new PriorityQueue<>(
                Comparator.comparing(e -> e.tiempoActual));
        frontera.add(new EstadoBusqueda(icaoOrigen, tListo, List.of()));

        while (!frontera.isEmpty()) {
            final EstadoBusqueda actual = frontera.poll();
            final LocalDateTime mejor = mejorLlegada.get(actual.icao);
            if (mejor != null && actual.tiempoActual.isAfter(mejor)) {
                continue;
            }
            if (actual.icao.equals(icaoDestino)) {
                return actual.camino;
            }
            final LocalDateTime tDesde = actual.tiempoActual.plusMinutes(minutosConexion);
            final List<VueloInstancia> vuelos = grafo.vuelosDesde(actual.icao, tDesde);
            for (final VueloInstancia vuelo : vuelos) {
                if (!esUtilizable(vuelo, vuelosBloqueados, tLimite)) {
                    continue;
                }
                final LocalDateTime llegada = vuelo.getFechaLlegada();
                final String icaoSiguiente = vuelo.getAeropuertoDestino().getIdAeropuerto();
                final LocalDateTime mejorSiguiente = mejorLlegada.get(icaoSiguiente);
                if (mejorSiguiente != null && !llegada.isBefore(mejorSiguiente)) {
                    continue;
                }
                mejorLlegada.put(icaoSiguiente, llegada);
                final List<VueloInstancia> nuevoCamino = new ArrayList<>(actual.camino.size() + 1);
                nuevoCamino.addAll(actual.camino);
                nuevoCamino.add(vuelo);
                frontera.add(new EstadoBusqueda(icaoSiguiente, llegada, Collections.unmodifiableList(nuevoCamino)));
            }
        }
        return null;
    }

    public static List<VueloInstancia> rutear(final Aeropuerto origen, final Aeropuerto destino,
                                              final LocalDateTime tListo, final LocalDateTime tLimite,
                                              final GrafoTiempoExpandido grafo) {
        return rutear(origen, destino, tListo, tLimite, grafo, 0L, Set.of());
    }

    private static boolean esUtilizable(final VueloInstancia vuelo, final Set<String> bloqueados,
                                        final LocalDateTime tLimite) {
        if (vuelo.getEstado() == EstadoVuelo.CANCELADO) {
            return false;
        }
        if (vuelo.getCapacidadDisponible() <= 0) {
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
        private final List<VueloInstancia> camino;

        private EstadoBusqueda(final String icao, final LocalDateTime tiempoActual,
                               final List<VueloInstancia> camino) {
            this.icao = icao;
            this.tiempoActual = tiempoActual;
            this.camino = camino;
        }
    }
}
