package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.common.DijkstraRuteador;
import pe.edu.pucp.aeroluggage.algoritmo.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

final class ALNSReparador {
    static final String OPERADOR_GREEDY = "GREEDY_REPAIR";
    static final String OPERADOR_REGRET_2 = "REGRET_2_REPAIR";

    private ALNSReparador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static void reparar(final ALNSEstado estado,
                        final List<Maleta> pendientes,
                        final String operador,
                        final ParametrosALNS parametros,
                        final Random random) {
        if (pendientes == null || pendientes.isEmpty()) {
            return;
        }
        if (OPERADOR_REGRET_2.equals(operador)) {
            repararRegret2(estado, pendientes, parametros, random);
            return;
        }
        repararGreedy(estado, pendientes, parametros);
    }

    static void repararGreedy(final ALNSEstado estado,
                              final List<Maleta> pendientes,
                              final ParametrosALNS parametros) {
        pendientes.sort(Comparator.comparingLong(
                maleta -> ALNSUtil.tiempoRestanteMinutos(maleta, estado.getInstancia().getFechaEvaluacion())));
        int secuencia = estado.siguienteSecuenciaRuta();
        for (final Maleta maleta : pendientes) {
            final Ruta ruta = encontrarMejorInsercion(estado, maleta, parametros, ALNSUtil.siguienteIdRuta(secuencia));
            if (ruta != null && ruta.getEstado() != EstadoRuta.FALLIDA) {
                estado.reemplazarRuta(ruta);
            }
            secuencia++;
        }
    }

    static void repararRegret2(final ALNSEstado estado,
                               final List<Maleta> pendientes,
                               final ParametrosALNS parametros,
                               final Random random) {
        final List<Maleta> restantes = new ArrayList<>(pendientes);
        int secuencia = estado.siguienteSecuenciaRuta();
        while (!restantes.isEmpty()) {
            Maleta mejorMaleta = null;
            Ruta mejorRuta = null;
            double mejorRegret = Double.NEGATIVE_INFINITY;
            for (final Maleta maleta : restantes) {
                final EvaluacionInsercion evaluacion = evaluarRegret(estado, maleta, parametros,
                        ALNSUtil.siguienteIdRuta(secuencia));
                if (evaluacion == null) {
                    continue;
                }
                if (evaluacion.regret() > mejorRegret) {
                    mejorRegret = evaluacion.regret();
                    mejorMaleta = maleta;
                    mejorRuta = evaluacion.mejorRuta();
                }
            }
            if (mejorMaleta == null) {
                break;
            }
            if (mejorRuta != null && mejorRuta.getEstado() != EstadoRuta.FALLIDA) {
                estado.reemplazarRuta(mejorRuta);
            }
            restantes.remove(mejorMaleta);
            secuencia++;
        }
    }

    static Ruta encontrarMejorInsercion(final ALNSEstado estado,
                                        final Maleta maleta,
                                        final ParametrosALNS parametros,
                                        final String idRuta) {
        final InstanciaProblema instancia = estado.getInstancia();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        if (pedido == null || pedido.getAeropuertoOrigen() == null || pedido.getAeropuertoDestino() == null) {
            return null;
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            return null;
        }
        final LocalDateTime listo = ALNSUtil.max(instancia.getFechaEvaluacion(), pedido.getFechaRegistro());
        final Set<String> bloqueados = new HashSet<>();
        Ruta mejor = null;
        double mejorCosto = Double.POSITIVE_INFINITY;
        for (int intento = 0; intento <= Math.max(1, parametros.getMaxReintentosRuteo()); intento++) {
            final List<VueloInstancia> camino = DijkstraRuteador.rutear(
                    pedido.getAeropuertoOrigen(),
                    pedido.getAeropuertoDestino(),
                    listo,
                    pedido.getFechaHoraPlazo(),
                    grafo,
                    parametros.getMinutosConexion(),
                    bloqueados
            );
            if (camino == null) {
                break;
            }
            final Ruta candidata = ALNSUtil.crearRuta(idRuta, maleta, camino, EstadoRuta.PLANIFICADA);
            final String conflictoVuelo = primerVueloSaturado(camino, estado.getUsoPorVuelo());
            if (conflictoVuelo != null) {
                bloqueados.add(conflictoVuelo);
                continue;
            }
            final boolean aeropuertoValido = validarAeropuertos(candidata, estado);
            if (!aeropuertoValido) {
                final VueloInstancia vueloConflicto = camino.isEmpty() ? null : camino.get(Math.min(camino.size() - 1, intento % camino.size()));
                if (vueloConflicto != null && vueloConflicto.getIdVueloInstancia() != null) {
                    bloqueados.add(vueloConflicto.getIdVueloInstancia());
                }
                continue;
            }
            final double costo = ALNSFitness.costoIncremental(estado, candidata, parametros);
            if (costo < mejorCosto) {
                mejorCosto = costo;
                mejor = candidata;
            }
            if (mejor != null) {
                break;
            }
        }
        return mejor;
    }

    static boolean validarAeropuertos(final Ruta ruta, final ALNSEstado estado) {
        final Map<String, Aeropuerto> aeropuertos = estado.getInstancia().indexarAeropuertosPorIcao();
        for (final ALNSUtil.IntervaloAeropuerto intervalo
                : ALNSUtil.construirIntervalosRuta(ruta, estado.getInstancia(), estado.getMaletasPorId())) {
            final Aeropuerto aeropuerto = aeropuertos.get(intervalo.idAeropuerto());
            if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                continue;
            }
            final int pico = calcularPico(
                    estado.getEventosAeropuerto().get(intervalo.idAeropuerto()),
                    intervalo,
                    estado.getOcupacionBaseAeropuerto().getOrDefault(intervalo.idAeropuerto(), aeropuerto.getMaletasActuales())
            );
            if (pico > aeropuerto.getCapacidadAlmacen()) {
                return false;
            }
        }
        return true;
    }

    private static int calcularPico(final Map<LocalDateTime, Integer> eventos,
                                    final ALNSUtil.IntervaloAeropuerto candidato,
                                    final int base) {
        final java.util.NavigableMap<LocalDateTime, Integer> temporales = new java.util.TreeMap<>();
        if (eventos != null) {
            temporales.putAll(eventos);
        }
        temporales.merge(candidato.inicio(), 1, Integer::sum);
        temporales.merge(candidato.fin(), -1, Integer::sum);
        int actual = base;
        int maximo = base;
        for (final Map.Entry<LocalDateTime, Integer> evento : temporales.entrySet()) {
            actual += evento.getValue();
            maximo = Math.max(maximo, actual);
        }
        return maximo;
    }

    private static String primerVueloSaturado(final List<VueloInstancia> camino,
                                              final Map<String, Integer> usoPorVuelo) {
        if (camino == null) {
            return null;
        }
        for (final VueloInstancia vuelo : camino) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            if (usoPorVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0) + 1 > Math.max(0, vuelo.getCapacidadDisponible())) {
                return vuelo.getIdVueloInstancia();
            }
        }
        return null;
    }

    private static EvaluacionInsercion evaluarRegret(final ALNSEstado estado,
                                                     final Maleta maleta,
                                                     final ParametrosALNS parametros,
                                                     final String idRuta) {
        final Ruta r1 = encontrarMejorInsercion(estado, maleta, parametros, idRuta);
        if (r1 == null || r1.getSubrutas().isEmpty()) {
            return new EvaluacionInsercion(null, Double.MAX_VALUE);
        }
        final Set<String> bloqueados = new HashSet<>();
        bloqueados.add(r1.getSubrutas().get(0).getIdVueloInstancia());
        final Ruta r2 = encontrarAlternativa(estado, maleta, parametros, idRuta, bloqueados);
        if (r2 == null || r2.getSubrutas().isEmpty()) {
            return new EvaluacionInsercion(r1, Double.MAX_VALUE);
        }
        final LocalDateTime llegada1 = ALNSUtil.llegadaFinal(r1);
        final LocalDateTime llegada2 = ALNSUtil.llegadaFinal(r2);
        final double costo1 = llegada1 == null ? Double.MAX_VALUE : llegada1.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
        final double costo2 = llegada2 == null ? Double.MAX_VALUE : llegada2.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
        return new EvaluacionInsercion(r1, costo2 - costo1);
    }

    private static Ruta encontrarAlternativa(final ALNSEstado estado,
                                             final Maleta maleta,
                                             final ParametrosALNS parametros,
                                             final String idRuta,
                                             final Set<String> bloqueadosIniciales) {
        final InstanciaProblema instancia = estado.getInstancia();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        if (pedido == null || instancia.getGrafo() == null) {
            return null;
        }
        final Set<String> bloqueados = new HashSet<>(bloqueadosIniciales);
        final LocalDateTime listo = ALNSUtil.max(instancia.getFechaEvaluacion(), pedido.getFechaRegistro());
        for (int intento = 0; intento <= Math.max(1, parametros.getMaxReintentosRuteo()); intento++) {
            final List<VueloInstancia> camino = DijkstraRuteador.rutear(
                    pedido.getAeropuertoOrigen(),
                    pedido.getAeropuertoDestino(),
                    listo,
                    pedido.getFechaHoraPlazo(),
                    instancia.getGrafo(),
                    parametros.getMinutosConexion(),
                    bloqueados
            );
            if (camino == null) {
                return null;
            }
            final Ruta ruta = ALNSUtil.crearRuta(idRuta, maleta, camino, EstadoRuta.PLANIFICADA);
            if (primerVueloSaturado(camino, estado.getUsoPorVuelo()) == null && validarAeropuertos(ruta, estado)) {
                return ruta;
            }
            if (!camino.isEmpty()) {
                bloqueados.add(camino.get(0).getIdVueloInstancia());
            }
        }
        return null;
    }

    private record EvaluacionInsercion(Ruta mejorRuta, double regret) {
    }
}
