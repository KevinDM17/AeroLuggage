package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class ALNSDestruidor {
    static final String OPERADOR_WORST = "WORST_REMOVAL";
    static final String OPERADOR_CRITICAL = "CRITICAL_CAPACITY_REMOVAL";

    private ALNSDestruidor() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static List<Maleta> destruir(final ALNSEstado estado,
                                 final String operador,
                                 final int q,
                                 final ParametrosALNS parametros,
                                 final Random random) {
        if (OPERADOR_CRITICAL.equals(operador)) {
            return criticalCapacityRemoval(estado, Math.min(q, parametros.getQCritical()), parametros);
        }
        return worstRemoval(estado, q, parametros);
    }

    static List<Maleta> worstRemoval(final ALNSEstado estado, final int q, final ParametrosALNS parametros) {
        final List<Ruta> rutas = new ArrayList<>(estado.rutasNoComprometidas());
        rutas.sort(Comparator.comparingDouble((Ruta ruta) -> puntuacionRemocion(estado, ruta)).reversed());
        final List<Maleta> removidas = new ArrayList<>();
        for (int i = 0; i < Math.min(q, rutas.size()); i++) {
            final Ruta ruta = rutas.get(i);
            final Ruta eliminada = estado.removerRuta(ruta.getIdMaleta());
            if (eliminada != null) {
                final Maleta maleta = estado.getMaletasPorId().get(eliminada.getIdMaleta());
                if (maleta != null) {
                    removidas.add(maleta);
                }
            }
        }
        return removidas;
    }

    static List<Maleta> criticalCapacityRemoval(final ALNSEstado estado,
                                                final int qCritical,
                                                final ParametrosALNS parametros) {
        final Set<String> aeropuertosCriticos = new HashSet<>();
        final Map<String, Aeropuerto> aeropuertos = estado.getInstancia().indexarAeropuertosPorIcao();
        final double umbral = Math.max(0.0D, parametros.getUmbralCriticoAeropuerto());
        for (final Map.Entry<String, Aeropuerto> entry : aeropuertos.entrySet()) {
            final Aeropuerto aeropuerto = entry.getValue();
            if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                continue;
            }
            final int pico = picoAeropuerto(estado, entry.getKey(), aeropuerto);
            final double ratio = pico / (double) aeropuerto.getCapacidadAlmacen();
            if (ratio >= umbral) {
                aeropuertosCriticos.add(entry.getKey());
            }
        }
        final List<Ruta> candidatas = new ArrayList<>();
        for (final Ruta ruta : estado.rutasNoComprometidas()) {
            for (final ALNSUtil.IntervaloAeropuerto intervalo
                    : ALNSUtil.construirIntervalosRuta(ruta, estado.getInstancia(), estado.getMaletasPorId())) {
                if (aeropuertosCriticos.contains(intervalo.idAeropuerto())) {
                    candidatas.add(ruta);
                    break;
                }
            }
        }
        candidatas.sort(Comparator.comparingDouble((Ruta ruta) -> puntuacionRemocion(estado, ruta)).reversed());
        final List<Maleta> removidas = new ArrayList<>();
        for (int i = 0; i < Math.min(qCritical, candidatas.size()); i++) {
            final Ruta ruta = candidatas.get(i);
            final Ruta eliminada = estado.removerRuta(ruta.getIdMaleta());
            if (eliminada != null) {
                final Maleta maleta = estado.getMaletasPorId().get(eliminada.getIdMaleta());
                if (maleta != null) {
                    removidas.add(maleta);
                }
            }
        }
        return removidas;
    }

    private static int picoAeropuerto(final ALNSEstado estado,
                                      final String idAeropuerto,
                                      final Aeropuerto aeropuerto) {
        int actual = estado.getOcupacionBaseAeropuerto().getOrDefault(idAeropuerto, aeropuerto.getMaletasActuales());
        int maximo = actual;
        final var eventos = estado.getEventosAeropuerto().get(idAeropuerto);
        if (eventos == null) {
            return maximo;
        }
        for (final int delta : eventos.values()) {
            actual += delta;
            maximo = Math.max(maximo, actual);
        }
        return maximo;
    }

    private static double puntuacionRemocion(final ALNSEstado estado, final Ruta ruta) {
        if (ruta == null) {
            return Double.NEGATIVE_INFINITY;
        }
        final Maleta maleta = estado.getMaletasPorId().get(ruta.getIdMaleta());
        if (maleta == null || maleta.getPedido() == null) {
            return Double.POSITIVE_INFINITY;
        }
        double puntaje = 0.0D;
        final var llegada = ALNSUtil.llegadaFinal(ruta);
        if (llegada != null && maleta.getPedido().getFechaHoraPlazo() != null) {
            puntaje += Math.max(0L, java.time.Duration.between(maleta.getPedido().getFechaHoraPlazo(), llegada).toMinutes()) * 10.0D;
            puntaje -= java.time.Duration.between(llegada, maleta.getPedido().getFechaHoraPlazo()).toMinutes() / 100.0D;
        }
        if (ruta.getSubrutas() != null) {
            for (final VueloInstancia vuelo : ruta.getSubrutas()) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                final int uso = estado.getUsoPorVuelo().getOrDefault(vuelo.getIdVueloInstancia(), 0);
                puntaje += uso;
            }
        }
        puntaje += Math.max(0, ruta.getSubrutas() == null ? 0 : ruta.getSubrutas().size() - 1) * 2.0D;
        return puntaje;
    }
}
