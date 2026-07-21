package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
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

    static void forzarAsignacionFinal(final ALNSEstado estado, final ParametrosALNS parametros) {
        if (estado == null) {
            return;
        }
        final List<Maleta> pendientes = new ArrayList<>(estado.getMaletasSinRuta());
        pendientes.sort(Comparator.comparingLong(
                maleta -> ALNSUtil.tiempoRestanteMinutos(maleta, estado.getInstancia().getFechaEvaluacion())));
        for (final Maleta maleta : pendientes) {
            final Ruta ruta = encontrarInsercionForzada(estado, maleta, parametros);
            if (ruta != null) {
                estado.registrarFalloMaleta(maleta.getIdMaleta(), null);
                estado.reemplazarRuta(ruta);
                estado.limpiarRazonFallo(maleta.getIdMaleta());
            }
        }
    }

    static void repararGreedy(final ALNSEstado estado,
                              final List<Maleta> pendientes,
                              final ParametrosALNS parametros) {
        final int total = pendientes.size();
        pendientes.sort(Comparator.comparingLong(
                maleta -> ALNSUtil.tiempoRestanteMinutos(maleta, estado.getInstancia().getFechaEvaluacion())));
        int secuencia = estado.siguienteSecuenciaRuta();
        int procesadas = 0;
        for (final Maleta maleta : pendientes) {
            final Ruta ruta = encontrarInsercionValida(estado, maleta, parametros);
            if (ruta != null) {
                estado.registrarFalloMaleta(maleta.getIdMaleta(), null);
                estado.reemplazarRuta(ruta);
            }
            secuencia++;
            procesadas++;
        }
    }    static void repararRegret2(final ALNSEstado estado,
                               final List<Maleta> pendientes,
                               final ParametrosALNS parametros,
                               final Random random) {
        final List<Maleta> restantes = new ArrayList<>(pendientes);
        final int totalInicial = restantes.size();
        int secuencia = estado.siguienteSecuenciaRuta();
        int iteracion = 0;
        while (!restantes.isEmpty()) {
            iteracion++;
            Maleta mejorMaleta = null;
            Ruta mejorRuta = null;
            double mejorRegret = Double.NEGATIVE_INFINITY;
            for (final Maleta maleta : restantes) {
                final EvaluacionInsercion evaluacion = evaluarRegret(estado, maleta, parametros);
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
                for (final Maleta restante : restantes) {
                    if (!estado.getRazonesFallo().containsKey(restante.getIdMaleta())) {
                        estado.registrarFalloMaleta(restante.getIdMaleta(), "sin_ruta_en_grafo");
                    }
                }
                break;
            }
            if (mejorRuta != null) {
                estado.registrarFalloMaleta(mejorMaleta.getIdMaleta(), null);
                estado.reemplazarRuta(mejorRuta);
            }
            restantes.remove(mejorMaleta);
            secuencia++;
        }
    }    static Ruta encontrarMejorInsercion(final ALNSEstado estado,
                                        final Maleta maleta,
                                        final ParametrosALNS parametros) {
        final InstanciaProblema instancia = estado.getInstancia();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        final String idMaleta = maleta == null ? null : maleta.getIdMaleta();
        if (pedido == null || pedido.getAeropuertoOrigen() == null || pedido.getAeropuertoDestino() == null) {
            estado.registrarFalloMaleta(idMaleta, "pedido_invalido");
            return null;
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            estado.registrarFalloMaleta(idMaleta, "grafo_no_disponible");
            return null;
        }
        final LocalDateTime listo = ALNSUtil.max(instancia.getFechaEvaluacion(), pedido.getFechaRegistro());
        final LocalDateTime tLimite = pedido.getFechaHoraPlazo();
        if (tLimite != null && !listo.isBefore(tLimite)) {
            estado.registrarFalloMaleta(idMaleta, "maleta_vencida");
            return null;
        }
        final Set<String> bloqueados = new HashSet<>();
        final String icaoOrigen = pedido.getAeropuertoOrigen().getIdAeropuerto();
        final String icaoDestino = pedido.getAeropuertoDestino().getIdAeropuerto();
        if (icaoOrigen != null && icaoOrigen.equals(icaoDestino)) {
            estado.registrarFalloMaleta(idMaleta, "origen_igual_destino");
            return null;
        }
        Ruta mejor = null;
        double mejorCosto = Double.POSITIVE_INFINITY;
        boolean dijkstraExitoso = false;
        String ultimaRazon = null;
        String ultimoVuelo = null;
        for (int intento = 0; intento <= Math.max(1, parametros.getMaxReintentosRuteo()); intento++) {
            final List<VueloInstancia> camino = DijkstraRuteador.rutear(
                    pedido.getAeropuertoOrigen(),
                    pedido.getAeropuertoDestino(),
                    listo,
                    tLimite,
                    grafo,
                    parametros.getMinutosConexion(),
                    bloqueados
            );
            if (camino == null) {
                if (!dijkstraExitoso) {
                    ultimaRazon = "sin_ruta_en_grafo";
                }
                break;
            }
            dijkstraExitoso = true;
            final Ruta candidata = ALNSUtil.crearRuta(maleta, camino, EstadoRuta.PLANIFICADA);
            final String conflictoVuelo = primerVueloSaturado(camino, estado.getUsoPorVuelo());
            if (conflictoVuelo != null) {
                ultimaRazon = "vuelo_saturado";
                ultimoVuelo = conflictoVuelo;
                bloqueados.add(conflictoVuelo);
                continue;
            }
            final boolean aeropuertoValido = validarAeropuertos(candidata, estado);
            if (!aeropuertoValido) {
                ultimaRazon = "aeropuerto_saturado";
                final VueloInstancia vueloConflicto = camino.isEmpty()
                        ? null
                        : camino.get(Math.min(camino.size() - 1, intento % camino.size()));
                if (vueloConflicto != null && vueloConflicto.getIdVueloInstancia() != null) {
                    ultimoVuelo = vueloConflicto.getIdVueloInstancia();
                    bloqueados.add(ultimoVuelo);
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
        if (mejor == null && idMaleta != null) {
            final String razonFinal = ultimoVuelo != null
                    ? ultimaRazon + ":" + ultimoVuelo
                    : ultimaRazon;
            estado.registrarFalloMaleta(idMaleta, razonFinal);
        }
        return mejor;
    }

    static Ruta encontrarInsercionValida(final ALNSEstado estado,
                                         final Maleta maleta,
                                         final ParametrosALNS parametros) {
        final InstanciaProblema instancia = estado.getInstancia();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        final String idMaleta = maleta == null ? null : maleta.getIdMaleta();
        if (pedido == null || pedido.getAeropuertoOrigen() == null || pedido.getAeropuertoDestino() == null) {
            estado.registrarFalloMaleta(idMaleta, "pedido_invalido");
            return null;
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            estado.registrarFalloMaleta(idMaleta, "grafo_no_disponible");
            return null;
        }
        final LocalDateTime listo = ALNSUtil.max(instancia.getFechaEvaluacion(), pedido.getFechaRegistro());
        final LocalDateTime tLimite = pedido.getFechaHoraPlazo();
        if (tLimite != null && !listo.isBefore(tLimite)) {
            estado.registrarFalloMaleta(idMaleta, "maleta_vencida");
            return null;
        }
        final Set<String> bloqueados = new HashSet<>();
        final String icaoOrigen = pedido.getAeropuertoOrigen().getIdAeropuerto();
        final String icaoDestino = pedido.getAeropuertoDestino().getIdAeropuerto();
        if (icaoOrigen != null && icaoOrigen.equals(icaoDestino)) {
            estado.registrarFalloMaleta(idMaleta, "origen_igual_destino");
            return null;
        }
        String ultimaRazon = null;
        String ultimoVuelo = null;
        for (int intento = 0; intento <= Math.max(1, parametros.getMaxReintentosRuteo()); intento++) {
            final List<VueloInstancia> camino = DijkstraRuteador.rutear(
                    pedido.getAeropuertoOrigen(),
                    pedido.getAeropuertoDestino(),
                    listo,
                    tLimite,
                    grafo,
                    parametros.getMinutosConexion(),
                    bloqueados
            );
            if (camino == null) {
                break;
            }
            final Ruta candidata = ALNSUtil.crearRuta(maleta, camino, EstadoRuta.PLANIFICADA);
            final String conflictoVuelo = primerVueloSaturado(camino, estado.getUsoPorVuelo());
            if (conflictoVuelo != null) {
                ultimaRazon = "vuelo_saturado";
                ultimoVuelo = conflictoVuelo;
                bloqueados.add(conflictoVuelo);
                continue;
            }
            final boolean aeropuertoValido = validarAeropuertos(candidata, estado);
            if (!aeropuertoValido) {
                ultimaRazon = "aeropuerto_saturado";
                final VueloInstancia vueloConflicto = camino.isEmpty()
                        ? null
                        : camino.get(Math.min(camino.size() - 1, intento % camino.size()));
                if (vueloConflicto != null && vueloConflicto.getIdVueloInstancia() != null) {
                    ultimoVuelo = vueloConflicto.getIdVueloInstancia();
                    bloqueados.add(ultimoVuelo);
                }
                continue;
            }
            return candidata;
        }
        if (idMaleta != null) {
            final String razonFinal = ultimoVuelo != null
                    ? ultimaRazon + ":" + ultimoVuelo
                    : ultimaRazon;
            estado.registrarFalloMaleta(idMaleta, razonFinal);
        }
        return null;
    }

    static Ruta encontrarInsercionForzada(final ALNSEstado estado,
                                          final Maleta maleta,
                                          final ParametrosALNS parametros) {
        final InstanciaProblema instancia = estado.getInstancia();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        final String idMaleta = maleta == null ? null : maleta.getIdMaleta();
        if (pedido == null || pedido.getAeropuertoOrigen() == null || pedido.getAeropuertoDestino() == null) {
            estado.registrarFalloMaleta(idMaleta, "pedido_invalido");
            return null;
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            estado.registrarFalloMaleta(idMaleta, "grafo_no_disponible");
            return null;
        }
        final LocalDateTime listo = ALNSUtil.max(instancia.getFechaEvaluacion(), pedido.getFechaRegistro());
        final LocalDateTime tLimite = pedido.getFechaHoraPlazo();
        if (tLimite != null && !listo.isBefore(tLimite)) {
            estado.registrarFalloMaleta(idMaleta, "maleta_vencida");
            return null;
        }
        final String icaoOrigen = pedido.getAeropuertoOrigen().getIdAeropuerto();
        final String icaoDestino = pedido.getAeropuertoDestino().getIdAeropuerto();
        if (icaoOrigen != null && icaoOrigen.equals(icaoDestino)) {
            estado.registrarFalloMaleta(idMaleta, "origen_igual_destino");
            return null;
        }
        final List<VueloInstancia> camino = DijkstraRuteador.rutearPermitiendoOverflow(
                pedido.getAeropuertoOrigen(),
                pedido.getAeropuertoDestino(),
                listo,
                tLimite,
                grafo,
                parametros.getMinutosConexion(),
                Set.of()
        );
        if (camino == null || camino.isEmpty()) {
            estado.registrarFalloMaleta(idMaleta, "sin_ruta_en_grafo");
            return null;
        }
        return ALNSUtil.crearRuta(maleta, camino, EstadoRuta.PLANIFICADA);
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

    private static int calcularPico(final NavigableMap<LocalDateTime, Integer> eventos,
                                    final ALNSUtil.IntervaloAeropuerto candidato,
                                    final int base) {
        if (eventos == null || eventos.isEmpty()) {
            return Math.max(base, base + 1);
        }
        int actual = base;
        int maximo = base;

        for (final Map.Entry<LocalDateTime, Integer> entry : eventos.headMap(candidato.inicio(), false).entrySet()) {
            actual += entry.getValue();
            maximo = Math.max(maximo, actual);
        }

        final Integer deltaInicio = eventos.get(candidato.inicio());
        actual += 1 + (deltaInicio != null ? deltaInicio : 0);
        maximo = Math.max(maximo, actual);

        for (final Map.Entry<LocalDateTime, Integer> entry
                : eventos.subMap(candidato.inicio(), false, candidato.fin(), false).entrySet()) {
            actual += entry.getValue();
            maximo = Math.max(maximo, actual);
        }

        final Integer deltaFin = eventos.get(candidato.fin());
        actual += -1 + (deltaFin != null ? deltaFin : 0);
        maximo = Math.max(maximo, actual);

        for (final Map.Entry<LocalDateTime, Integer> entry : eventos.tailMap(candidato.fin(), false).entrySet()) {
            actual += entry.getValue();
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
                                                     final ParametrosALNS parametros) {
        final Ruta r1 = encontrarMejorInsercion(estado, maleta, parametros);
        final List<String> idsR1 = r1 != null ? r1.getSubrutaIds() : List.of();
        if (r1 == null || idsR1.isEmpty()) {
            return new EvaluacionInsercion(null, Double.MAX_VALUE);
        }
        final Set<String> bloqueados = new HashSet<>();
        if (!idsR1.isEmpty()) {
            bloqueados.add(idsR1.get(0));
        }
        final Ruta r2 = encontrarAlternativa(estado, maleta, parametros, bloqueados);
        final List<String> idsR2 = r2 != null ? r2.getSubrutaIds() : List.of();
        if (r2 == null || idsR2.isEmpty()) {
            return new EvaluacionInsercion(r1, Double.MAX_VALUE);
        }
        final LocalDateTime llegada1 = ALNSUtil.llegadaFinal(r1, estado.getInstancia().getVuelosPorId());
        final LocalDateTime llegada2 = ALNSUtil.llegadaFinal(r2, estado.getInstancia().getVuelosPorId());
        final double costo1 = llegada1 == null ? Double.MAX_VALUE : llegada1.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
        final double costo2 = llegada2 == null ? Double.MAX_VALUE : llegada2.atZone(java.time.ZoneOffset.UTC).toEpochSecond();
        return new EvaluacionInsercion(r1, costo2 - costo1);
    }

    private static Ruta encontrarAlternativa(final ALNSEstado estado,
                                             final Maleta maleta,
                                             final ParametrosALNS parametros,
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
            final Ruta ruta = ALNSUtil.crearRuta(maleta, camino, EstadoRuta.PLANIFICADA);
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
