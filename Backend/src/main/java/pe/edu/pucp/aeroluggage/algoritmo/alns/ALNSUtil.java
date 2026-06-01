package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

final class ALNSUtil {
    private ALNSUtil() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static List<IntervaloAeropuerto> construirIntervalosRuta(final Ruta ruta,
                                                             final InstanciaProblema instancia,
                                                             final Map<String, Maleta> maletasPorId) {
        if (ruta == null || maletasPorId == null) {
            return List.of();
        }
        return construirIntervalosRuta(ruta.getIdMaleta(), ruta.getSubrutas(), instancia, maletasPorId);
    }

    static List<IntervaloAeropuerto> construirIntervalosRuta(final String idMaleta,
                                                             final List<VueloInstancia> camino,
                                                             final InstanciaProblema instancia,
                                                             final Map<String, Maleta> maletasPorId) {
        final List<IntervaloAeropuerto> intervalos = new ArrayList<>();
        if (idMaleta == null || camino == null || camino.isEmpty() || instancia == null || maletasPorId == null) {
            return intervalos;
        }
        final Maleta maleta = maletasPorId.get(idMaleta);
        if (maleta == null || maleta.getPedido() == null) {
            return intervalos;
        }
        final Pedido pedido = maleta.getPedido();
        final LocalDateTime referencia = max(
                instancia.getFechaEvaluacion(),
                pedido.getFechaRegistro() != null ? pedido.getFechaRegistro() : maleta.getFechaRegistro()
        );
        final VueloInstancia primerVuelo = camino.get(0);
        if (primerVuelo != null) {
            agregarIntervalo(
                    intervalos,
                    pedido.getAeropuertoOrigen(),
                    referencia,
                    primerVuelo.getFechaSalida()
            );
        }
        for (int i = 0; i < camino.size() - 1; i++) {
            final VueloInstancia actual = camino.get(i);
            final VueloInstancia siguiente = camino.get(i + 1);
            if (actual == null || siguiente == null) {
                continue;
            }
            agregarIntervalo(intervalos, actual.getAeropuertoDestino(), actual.getFechaLlegada(), siguiente.getFechaSalida());
        }
        final VueloInstancia ultimoVuelo = camino.get(camino.size() - 1);
        if (ultimoVuelo != null) {
            agregarIntervalo(
                    intervalos,
                    ultimoVuelo.getAeropuertoDestino(),
                    ultimoVuelo.getFechaLlegada(),
                    ultimoVuelo.getFechaLlegada() == null
                            ? null
                            : ultimoVuelo.getFechaLlegada().plusMinutes(Math.max(0L, instancia.getTiempoRecojo()))
            );
        }
        return intervalos;
    }

    static void agregarIntervalo(final List<IntervaloAeropuerto> intervalos,
                                 final Aeropuerto aeropuerto,
                                 final LocalDateTime inicio,
                                 final LocalDateTime fin) {
        if (intervalos == null || aeropuerto == null || aeropuerto.getIdAeropuerto() == null
                || inicio == null || fin == null || !fin.isAfter(inicio)) {
            return;
        }
        intervalos.add(new IntervaloAeropuerto(aeropuerto.getIdAeropuerto(), inicio, fin));
    }

    static LocalDateTime llegadaFinal(final Ruta ruta) {
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            return null;
        }
        return ruta.getSubrutas().get(ruta.getSubrutas().size() - 1).getFechaLlegada();
    }

    static double duracionDias(final List<VueloInstancia> camino) {
        if (camino == null || camino.isEmpty()) {
            return 0.0D;
        }
        final LocalDateTime inicio = camino.get(0).getFechaSalida();
        final LocalDateTime fin = camino.get(camino.size() - 1).getFechaLlegada();
        if (inicio == null || fin == null || fin.isBefore(inicio)) {
            return 0.0D;
        }
        return Duration.between(inicio, fin).toMinutes() / (24D * 60D);
    }

    static Ruta crearRuta(final String idRuta, final Maleta maleta, final List<VueloInstancia> camino,
                          final EstadoRuta estado) {
        final Ruta ruta = new Ruta();
        ruta.setIdRuta(idRuta);
        ruta.setIdMaleta(maleta == null ? null : maleta.getIdMaleta());
        ruta.setSubrutas(camino == null ? new ArrayList<>() : new ArrayList<>(camino));
        if (maleta != null && maleta.getPedido() != null) {
            ruta.setPlazoMaximoDias(Ruta.calcularPlazo(
                    maleta.getPedido().getAeropuertoOrigen(),
                    maleta.getPedido().getAeropuertoDestino()));
        }
        ruta.setDuracion(duracionDias(ruta.getSubrutas()));
        ruta.setEstado(camino == null || camino.isEmpty() ? EstadoRuta.FALLIDA : estado);
        return ruta;
    }

    static String siguienteIdRuta(final int secuencia) {
        return String.format("R%08d", secuencia);
    }

    static Map<String, Maleta> indexarMaletas(final List<Maleta> maletas) {
        final Map<String, Maleta> indice = new HashMap<>();
        if (maletas == null) {
            return indice;
        }
        for (final Maleta maleta : maletas) {
            if (maleta != null && maleta.getIdMaleta() != null) {
                indice.put(maleta.getIdMaleta(), maleta);
            }
        }
        return indice;
    }

    static Map<String, NavigableMap<LocalDateTime, Integer>> clonarEventos(
            final Map<String, NavigableMap<LocalDateTime, Integer>> originales) {
        final Map<String, NavigableMap<LocalDateTime, Integer>> copia = new HashMap<>();
        if (originales == null) {
            return copia;
        }
        for (final Map.Entry<String, NavigableMap<LocalDateTime, Integer>> entry : originales.entrySet()) {
            copia.put(entry.getKey(), entry.getValue() == null ? new TreeMap<>() : new TreeMap<>(entry.getValue()));
        }
        return copia;
    }

    static LocalDateTime max(final LocalDateTime primero, final LocalDateTime segundo) {
        if (primero == null) {
            return segundo;
        }
        if (segundo == null) {
            return primero;
        }
        return primero.isAfter(segundo) ? primero : segundo;
    }

    static Comparator<Maleta> comparadorUrgenciaInicial(final LocalDateTime fechaEvaluacion) {
        return Comparator
                .comparingLong((Maleta maleta) -> holguraInicialMinutos(maleta, fechaEvaluacion))
                .thenComparing(maleta -> maleta != null && maleta.getPedido() != null
                        ? maleta.getPedido().getFechaHoraPlazo()
                        : null, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Maleta::getFechaRegistro, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Maleta::getIdMaleta, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static long holguraInicialMinutos(final Maleta maleta, final LocalDateTime fechaEvaluacion) {
        if (maleta == null || maleta.getPedido() == null || maleta.getPedido().getFechaHoraPlazo() == null
                || fechaEvaluacion == null) {
            return Long.MAX_VALUE;
        }
        return Duration.between(fechaEvaluacion, maleta.getPedido().getFechaHoraPlazo()).toMinutes();
    }

    static long tiempoRestanteMinutos(final Maleta maleta, final LocalDateTime fechaEvaluacion) {
        return holguraInicialMinutos(maleta, fechaEvaluacion);
    }

    record IntervaloAeropuerto(String idAeropuerto, LocalDateTime inicio, LocalDateTime fin) {
    }
}
