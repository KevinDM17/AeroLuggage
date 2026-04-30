package pe.edu.pucp.aeroluggage.algoritmos.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class CalculadorFitnessExperimental {
    private static final double PESO_MALETA_NO_RUTEADA = 1_000_000D;
    private static final double PESO_USO_CAPACIDAD_VUELO = 1_000D;
    private static final double PESO_USO_CAPACIDAD_AEROPUERTO = 1_000D;
    private static final double PESO_DURACION_HORAS = 10D;
    private static final double PENALIZACION_CAPACIDAD_INVALIDA = 1_000D;

    private CalculadorFitnessExperimental() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static ResultadoFitnessExperimental calcular(final Solucion solucion,
                                                        final InstanciaProblema instancia) {
        if (instancia == null) {
            return new ResultadoFitnessExperimental(0D, 0, 0D, 0D, 0D, 0D, 0D);
        }

        final Map<String, Maleta> maletasPorId = indexarMaletas(instancia);
        final Map<String, VueloInstancia> vuelosPorId = indexarVuelos(instancia);
        final Map<String, Aeropuerto> aeropuertosPorId = instancia.indexarAeropuertosPorIcao();
        final Map<String, Integer> usoVuelos = new HashMap<>();
        final Map<String, Integer> usoAeropuertos = new HashMap<>();
        final Set<String> maletasRuteadas = new HashSet<>();
        double duracionTotalHoras = 0D;

        if (solucion != null && solucion.getSolucion() != null) {
            for (final Ruta ruta : solucion.getSolucion()) {
                if (!esRutaValida(ruta, maletasPorId)) {
                    continue;
                }
                maletasRuteadas.add(ruta.getIdMaleta());
                acumularUsoVuelos(ruta.getSubrutas(), usoVuelos);
                acumularUsoAeropuertos(ruta.getSubrutas(), usoAeropuertos);
                duracionTotalHoras += calcularDuracionHoras(ruta.getSubrutas());
            }
        }

        final int maletasNoRuteadas = Math.max(0, maletasPorId.size() - maletasRuteadas.size());
        final double usoCapacidadVuelos = calcularUsoCapacidadVuelos(usoVuelos, vuelosPorId);
        final double usoCapacidadAeropuertos = calcularUsoCapacidadAeropuertos(
                usoAeropuertos,
                aeropuertosPorId
        );
        final double maxPorcentajeLlenadoVuelos = calcularMaxPorcentajeVuelos(usoVuelos, vuelosPorId);
        final double maxPorcentajeLlenadoAeropuertos = calcularMaxPorcentajeAeropuertos(
                usoAeropuertos,
                aeropuertosPorId
        );
        final double fitnessExperimental = maletasNoRuteadas * PESO_MALETA_NO_RUTEADA
                + usoCapacidadVuelos * PESO_USO_CAPACIDAD_VUELO
                + usoCapacidadAeropuertos * PESO_USO_CAPACIDAD_AEROPUERTO
                + duracionTotalHoras * PESO_DURACION_HORAS;

        return new ResultadoFitnessExperimental(
                fitnessExperimental,
                maletasNoRuteadas,
                usoCapacidadVuelos,
                usoCapacidadAeropuertos,
                duracionTotalHoras,
                maxPorcentajeLlenadoVuelos,
                maxPorcentajeLlenadoAeropuertos
        );
    }

    private static boolean esRutaValida(final Ruta ruta, final Map<String, Maleta> maletasPorId) {
        if (ruta == null || ruta.getIdMaleta() == null || !maletasPorId.containsKey(ruta.getIdMaleta())) {
            return false;
        }
        return ruta.getEstado() != EstadoRuta.FALLIDA
                && ruta.getSubrutas() != null
                && !ruta.getSubrutas().isEmpty();
    }

    private static void acumularUsoVuelos(final List<VueloInstancia> vuelos,
                                          final Map<String, Integer> usoVuelos) {
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            usoVuelos.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
        }
    }

    private static void acumularUsoAeropuertos(final List<VueloInstancia> vuelos,
                                               final Map<String, Integer> usoAeropuertos) {
        if (vuelos.isEmpty()) {
            return;
        }
        final VueloInstancia primerVuelo = vuelos.get(0);
        acumularAeropuerto(primerVuelo == null ? null : primerVuelo.getAeropuertoOrigen(), usoAeropuertos);
        for (final VueloInstancia vuelo : vuelos) {
            acumularAeropuerto(vuelo == null ? null : vuelo.getAeropuertoDestino(), usoAeropuertos);
        }
    }

    private static void acumularAeropuerto(final Aeropuerto aeropuerto,
                                           final Map<String, Integer> usoAeropuertos) {
        if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
            return;
        }
        usoAeropuertos.merge(aeropuerto.getIdAeropuerto(), 1, Integer::sum);
    }

    private static double calcularUsoCapacidadVuelos(final Map<String, Integer> usoVuelos,
                                                     final Map<String, VueloInstancia> vuelosPorId) {
        double usoCapacidad = 0D;
        for (final Map.Entry<String, Integer> entry : usoVuelos.entrySet()) {
            final VueloInstancia vuelo = vuelosPorId.get(entry.getKey());
            if (vuelo == null || vuelo.getCapacidadMaxima() <= 0) {
                usoCapacidad += entry.getValue() * PENALIZACION_CAPACIDAD_INVALIDA;
                continue;
            }
            usoCapacidad += entry.getValue() / (double) vuelo.getCapacidadMaxima();
        }
        return usoCapacidad;
    }

    private static double calcularUsoCapacidadAeropuertos(final Map<String, Integer> usoAeropuertos,
                                                          final Map<String, Aeropuerto> aeropuertosPorId) {
        double usoCapacidad = 0D;
        for (final Map.Entry<String, Integer> entry : usoAeropuertos.entrySet()) {
            final Aeropuerto aeropuerto = aeropuertosPorId.get(entry.getKey());
            if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                usoCapacidad += entry.getValue() * PENALIZACION_CAPACIDAD_INVALIDA;
                continue;
            }
            usoCapacidad += entry.getValue() / (double) aeropuerto.getCapacidadAlmacen();
        }
        return usoCapacidad;
    }

    private static double calcularMaxPorcentajeVuelos(final Map<String, Integer> usoVuelos,
                                                      final Map<String, VueloInstancia> vuelosPorId) {
        double max = 0D;
        for (final Map.Entry<String, Integer> entry : usoVuelos.entrySet()) {
            final VueloInstancia vuelo = vuelosPorId.get(entry.getKey());
            if (vuelo == null || vuelo.getCapacidadMaxima() <= 0) {
                continue;
            }
            final double ratio = entry.getValue() / (double) vuelo.getCapacidadMaxima();
            if (ratio > max) {
                max = ratio;
            }
        }
        return max * 100.0;
    }

    private static double calcularMaxPorcentajeAeropuertos(final Map<String, Integer> usoAeropuertos,
                                                           final Map<String, Aeropuerto> aeropuertosPorId) {
        double max = 0D;
        for (final Map.Entry<String, Integer> entry : usoAeropuertos.entrySet()) {
            final Aeropuerto aeropuerto = aeropuertosPorId.get(entry.getKey());
            if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                continue;
            }
            final double ratio = entry.getValue() / (double) aeropuerto.getCapacidadAlmacen();
            if (ratio > max) {
                max = ratio;
            }
        }
        return max * 100.0;
    }

    private static double calcularDuracionHoras(final List<VueloInstancia> vuelos) {
        if (vuelos == null || vuelos.isEmpty()) {
            return 0D;
        }
        final VueloInstancia primerVuelo = vuelos.get(0);
        final VueloInstancia ultimoVuelo = vuelos.get(vuelos.size() - 1);
        final LocalDateTime salida = primerVuelo == null ? null : primerVuelo.getFechaSalida();
        final LocalDateTime llegada = ultimoVuelo == null ? null : ultimoVuelo.getFechaLlegada();
        if (salida == null || llegada == null || llegada.isBefore(salida)) {
            return 0D;
        }
        return Duration.between(salida, llegada).toMinutes() / 60D;
    }

    private static Map<String, Maleta> indexarMaletas(final InstanciaProblema instancia) {
        final Map<String, Maleta> indice = new HashMap<>();
        for (final Maleta maleta : instancia.getMaletas()) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            indice.put(maleta.getIdMaleta(), maleta);
        }
        return indice;
    }

    private static Map<String, VueloInstancia> indexarVuelos(final InstanciaProblema instancia) {
        final Map<String, VueloInstancia> indice = new HashMap<>();
        for (final VueloInstancia vuelo : instancia.getVueloInstancias()) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            indice.put(vuelo.getIdVueloInstancia(), vuelo);
        }
        return indice;
    }
}
