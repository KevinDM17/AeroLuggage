package pe.edu.pucp.aeroluggage.algoritmos.common;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class CalculadorFitnessExperimental {

    private CalculadorFitnessExperimental() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static ResultadoFitnessExperimental calcular(final Solucion solucion,
                                                        final InstanciaProblema instancia) {
        return calcular(solucion, instancia, new ConfigFitnessExperimental());
    }

    public static ResultadoFitnessExperimental calcular(final Solucion solucion,
                                                        final InstanciaProblema instancia,
                                                        final ConfigFitnessExperimental config) {
        if (instancia == null) {
            return new ResultadoFitnessExperimental(0D, 0, 0, 0, 0, 0D, 0D, 0D);
        }

        final Map<String, Maleta> maletasPorId = instancia.getMaletasPorId();
        final Map<String, VueloInstancia> vuelosPorId = instancia.getVuelosPorId();
        final Map<String, Aeropuerto> aeropuertosPorId = instancia.indexarAeropuertosPorIcao();
        final Map<String, Integer> usoVuelos = new HashMap<>();
        // airportId -> list of [epochSecond, +1 arrival / -1 departure]
        final Map<String, List<long[]>> eventosAeropuerto = new HashMap<>();

        int noEnrutadas = 0;
        int destinoMal = 0;
        int sumaEscalas = 0;
        double duracionTotal = 0D;
        double tiempoEsperaTotal = 0D;

        if (solucion != null && solucion.getSolucion() != null) {
            for (final Ruta ruta : solucion.getSolucion()) {
                final Maleta maleta = ruta != null ? maletasPorId.get(ruta.getIdMaleta()) : null;
                if (!esRutaValida(ruta, maleta)) {
                    noEnrutadas++;
                    continue;
                }

                final List<VueloInstancia> subrutas = ruta.getSubrutas();
                for (int i = 0; i < subrutas.size(); i++) {
                    final VueloInstancia v = subrutas.get(i);
                    if (v == null) {
                        continue;
                    }

                    if (v.getFechaSalida() != null && v.getFechaLlegada() != null) {
                        duracionTotal += Duration.between(v.getFechaSalida(), v.getFechaLlegada())
                                .toMinutes() / 60.0;
                    }

                    if (v.getIdVueloInstancia() != null) {
                        usoVuelos.merge(v.getIdVueloInstancia(), 1, Integer::sum);
                    }

                    // origin airport: bag is stored from registration until this flight departs
                    if (i == 0 && v.getAeropuertoOrigen() != null
                            && v.getAeropuertoOrigen().getIdAeropuerto() != null
                            && v.getFechaSalida() != null) {
                        final LocalDateTime llegadaAOrigen = maleta.getFechaRegistro() != null
                                ? maleta.getFechaRegistro() : v.getFechaSalida();
                        registrarEvento(eventosAeropuerto, v.getAeropuertoOrigen().getIdAeropuerto(),
                                llegadaAOrigen, v.getFechaSalida());
                    }

                    // destination airport: bag arrives on landing, leaves on next flight's departure (or stays)
                    if (v.getAeropuertoDestino() != null
                            && v.getAeropuertoDestino().getIdAeropuerto() != null
                            && v.getFechaLlegada() != null) {
                        final VueloInstancia siguiente = (i + 1 < subrutas.size()) ? subrutas.get(i + 1) : null;
                        final LocalDateTime salidaSiguiente = siguiente != null ? siguiente.getFechaSalida() : null;
                        registrarEvento(eventosAeropuerto, v.getAeropuertoDestino().getIdAeropuerto(),
                                v.getFechaLlegada(), salidaSiguiente);
                    }

                    if (i > 0) {
                        final VueloInstancia prev = subrutas.get(i - 1);
                        if (prev != null && prev.getFechaLlegada() != null && v.getFechaSalida() != null
                                && v.getFechaSalida().isAfter(prev.getFechaLlegada())) {
                            tiempoEsperaTotal += Duration.between(prev.getFechaLlegada(), v.getFechaSalida())
                                    .toMinutes() / 60.0;
                        }
                    }
                }

                sumaEscalas += subrutas.size() - 1;

                final VueloInstancia ultimo = subrutas.get(subrutas.size() - 1);
                if (ultimo != null && !mismoDestino(ultimo.getAeropuertoDestino(), maleta)) {
                    destinoMal++;
                }
            }
        }

        int overflowVuelos = 0;
        for (final Map.Entry<String, Integer> e : usoVuelos.entrySet()) {
            final VueloInstancia v = vuelosPorId.get(e.getKey());
            if (v != null && v.getCapacidadMaxima() > 0 && e.getValue() > v.getCapacidadMaxima()) {
                overflowVuelos++;
            }
        }

        int overflowAlmacen = 0;
        for (final Map.Entry<String, List<long[]>> entry : eventosAeropuerto.entrySet()) {
            final Aeropuerto a = aeropuertosPorId.get(entry.getKey());
            if (a != null && a.getCapacidadAlmacen() > 0
                    && calcularMaxOcupacion(entry.getValue()) > a.getCapacidadAlmacen()) {
                overflowAlmacen++;
            }
        }

        final int n = Math.max(1, maletasPorId.size());
        final int totalVuelos = Math.max(1, instancia.getVueloInstancias().size());
        final int totalAeropuertos = Math.max(1, instancia.getAeropuertos().size());

        final double duracionNorm = duracionTotal     / (n * 48.0);
        final double escalasNorm  = sumaEscalas       / (n * 5.0);
        final double esperaNorm   = tiempoEsperaTotal / (n * 24.0);

        final double fitness =
                config.getW1NoEnrutadas()     * (noEnrutadas    / (double) n)
              + config.getW2DestinMal()       * (destinoMal     / (double) n)
              + config.getW3OverflowVuelos()  * (overflowVuelos  / (double) totalVuelos)
              + config.getW4OverflowAlmacen() * (overflowAlmacen / (double) totalAeropuertos)
              + config.getW5Duracion()        * duracionNorm
              + config.getW6Escalas()         * escalasNorm
              + config.getW7Espera()          * esperaNorm;

        return new ResultadoFitnessExperimental(
                fitness, noEnrutadas, destinoMal,
                overflowVuelos, overflowAlmacen,
                duracionNorm, escalasNorm, esperaNorm);
    }

    private static void registrarEvento(final Map<String, List<long[]>> eventos,
                                        final String idAeropuerto,
                                        final LocalDateTime llegada,
                                        final LocalDateTime salida) {
        final List<long[]> lista = eventos.computeIfAbsent(idAeropuerto, k -> new ArrayList<>());
        lista.add(new long[]{llegada.toEpochSecond(ZoneOffset.UTC), +1});
        if (salida != null) {
            lista.add(new long[]{salida.toEpochSecond(ZoneOffset.UTC), -1});
        }
    }

    // sweep-line: find peak concurrent occupancy
    private static int calcularMaxOcupacion(final List<long[]> eventos) {
        // sort by time; at same time, process arrivals (+1) before departures (-1)
        eventos.sort(Comparator.comparingLong((long[] e) -> e[0]).thenComparingLong(e -> -e[1]));
        int actual = 0;
        int max = 0;
        for (final long[] e : eventos) {
            actual += (int) e[1];
            if (actual > max) {
                max = actual;
            }
        }
        return max;
    }

    private static boolean esRutaValida(final Ruta ruta, final Maleta maleta) {
        if (ruta == null || maleta == null) {
            return false;
        }
        return ruta.getEstado() != EstadoRuta.FALLIDA
                && ruta.getSubrutas() != null
                && !ruta.getSubrutas().isEmpty();
    }

    private static boolean mismoDestino(final Aeropuerto destinoVuelo, final Maleta maleta) {
        if (destinoVuelo == null || destinoVuelo.getIdAeropuerto() == null) {
            return false;
        }
        final Pedido pedido = maleta.getPedido();
        if (pedido == null || pedido.getAeropuertoDestino() == null) {
            return false;
        }
        return destinoVuelo.getIdAeropuerto().equals(pedido.getAeropuertoDestino().getIdAeropuerto());
    }
}
