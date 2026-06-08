package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;

import lombok.extern.slf4j.Slf4j;
import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

@Slf4j

final class ALNSFitness {
    private ALNSFitness() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static Resultado evaluar(final ALNSEstado estado, final ParametrosALNS parametros) {
        final InstanciaProblema instancia = estado.getInstancia();

        final long t0 = System.nanoTime();
        final List<Maleta> maletas = estado.getMaletasNoComprometidas();
        final int totalMaletas = Math.max(1, maletas.size());
        final Map<String, Maleta> maletasPorId = estado.getMaletasPorId();

        int noEnrutadas = 0;
        int fueraDePlazo = 0;
        double sumaHolguraNormalizada = 0.0D;
        int holgurasContadas = 0;

        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final Ruta ruta = estado.obtenerRuta(maleta.getIdMaleta());
            if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                noEnrutadas++;
                continue;
            }
            final LocalDateTime llegadaFinal = ALNSUtil.llegadaFinal(ruta);
            final LocalDateTime fechaLimite = maleta.getPedido() != null ? maleta.getPedido().getFechaHoraPlazo() : null;
            if (llegadaFinal == null || fechaLimite == null || llegadaFinal.isAfter(fechaLimite)) {
                fueraDePlazo++;
            } else {
                final long holguraMinutos = Duration.between(llegadaFinal, fechaLimite).toMinutes();
                final long ventanaMinutos = Math.max(1L, Duration.between(
                        maleta.getPedido().getFechaRegistro(),
                        fechaLimite).toMinutes());
                final double holguraNormalizada = Math.min(1.0D, Math.max(0.0D, holguraMinutos / (double) ventanaMinutos));
                sumaHolguraNormalizada += holguraNormalizada;
                holgurasContadas++;
            }
        }
        final long tMaletas = System.nanoTime();

        final LocalDateTime fechaEval = instancia.getFechaEvaluacion();
        final LocalDateTime corteVuelos = fechaEval != null ? fechaEval.plusDays(2) : null;

        double overflowVuelos = 0.0D;
        double ocupacionPromedioVuelos = 0.0D;
        int vuelosContados = 0;
        for (final VueloInstancia vuelo : estado.getVuelosInstancia()) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null || vuelo.getCapacidadDisponible() < 0) {
                continue;
            }
            if (vuelo.getFechaSalida() != null) {
                if (corteVuelos != null && vuelo.getFechaSalida().isAfter(corteVuelos)) {
                    continue;
                }
                if (fechaEval != null && vuelo.getFechaSalida().isBefore(fechaEval)) {
                    continue;
                }
            }
            final int uso = estado.getUsoPorVuelo().getOrDefault(vuelo.getIdVueloInstancia(), 0);
            final int capacidadBase = Math.max(0, vuelo.getCapacidadDisponible());
            if (capacidadBase == 0) {
                if (uso > 0) {
                    overflowVuelos += uso;
                }
                continue;
            }
            if (uso > capacidadBase) {
                overflowVuelos += uso - capacidadBase;
            }
            ocupacionPromedioVuelos += Math.min(1.0D, uso / (double) capacidadBase);
            vuelosContados++;
        }
        ocupacionPromedioVuelos = vuelosContados > 0 ? ocupacionPromedioVuelos / vuelosContados : 0.0D;
        final long tVuelos = System.nanoTime();

        double overflowAeropuertos = 0.0D;
        double ocupacionPromedioAeropuertos = 0.0D;
        int aeropuertosContados = 0;
        for (final Aeropuerto aeropuerto : estado.getAeropuertos()) {
            if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                continue;
            }
            final MetricasAeropuerto metricasAeropuerto = evaluarAeropuerto(
                    aeropuerto,
                    estado.getEventosAeropuerto().get(aeropuerto.getIdAeropuerto()),
                    estado.getOcupacionBaseAeropuerto().getOrDefault(aeropuerto.getIdAeropuerto(), aeropuerto.getMaletasActuales())
            );
            overflowAeropuertos += metricasAeropuerto.overflow();
            ocupacionPromedioAeropuertos += metricasAeropuerto.promedio();
            aeropuertosContados++;
        }
        ocupacionPromedioAeropuertos = aeropuertosContados > 0
                ? ocupacionPromedioAeropuertos / aeropuertosContados
                : 0.0D;
        final long tAeropuertos = System.nanoTime();

        if (log.isTraceEnabled()) {
            log.trace("[ALNS-EVAL] maletas={}ms vuelos={}ms aeropuertos={}ms total={}ms",
                    (tMaletas - t0) / 1_000_000L,
                    (tVuelos - tMaletas) / 1_000_000L,
                    (tAeropuertos - tVuelos) / 1_000_000L,
                    (tAeropuertos - t0) / 1_000_000L);
        }

        final double proporcionNoEnrutadas = noEnrutadas / (double) totalMaletas;
        final double proporcionFueraDePlazo = fueraDePlazo / (double) totalMaletas;
        final double proporcionOverflowVuelos = overflowVuelos / totalMaletas;
        final double proporcionOverflowAeropuertos = overflowAeropuertos / totalMaletas;
        final double penalizacionHolgura = holgurasContadas > 0
                ? 1.0D - (sumaHolguraNormalizada / holgurasContadas)
                : 1.0D;

        final double fitness = parametros.getPesoMaletasNoEnrutadas() * proporcionNoEnrutadas
                + parametros.getPesoMaletasFueraDePlazo() * proporcionFueraDePlazo
                + parametros.getPesoOverflowVuelos() * proporcionOverflowVuelos
                + parametros.getPesoOverflowAeropuertos() * proporcionOverflowAeropuertos
                + parametros.getPesoOcupacionPromedioVuelos() * ocupacionPromedioVuelos
                + parametros.getPesoOcupacionPromedioAeropuertos() * ocupacionPromedioAeropuertos
                + parametros.getPesoHolgura() * penalizacionHolgura;

        final Solucion solucion = estado.getSolucionActual();
        solucion.setFitness(fitness);
        solucion.setCostoTotal(fitness);
        solucion.setMaletasEntregadasATiempo(totalMaletas - noEnrutadas - fueraDePlazo);
        solucion.setMaletasIncumplidas(noEnrutadas + fueraDePlazo);
        solucion.setOcupacionPromedioVuelos(ocupacionPromedioVuelos);
        solucion.setOcupacionPromedioAlmacenes(ocupacionPromedioAeropuertos);
        solucion.setOverflowAlmacenes(overflowAeropuertos);
        solucion.setFactible(noEnrutadas == 0 && fueraDePlazo == 0 && overflowVuelos == 0.0D && overflowAeropuertos == 0.0D);

        return new Resultado(
                fitness,
                noEnrutadas,
                fueraDePlazo,
                overflowVuelos,
                overflowAeropuertos,
                ocupacionPromedioVuelos,
                ocupacionPromedioAeropuertos,
                penalizacionHolgura,
                solucion.isFactible()
        );
    }

    static double costoIncremental(final ALNSEstado estado, final Ruta candidata, final ParametrosALNS parametros) {
        final String idMaleta = candidata == null ? null : candidata.getIdMaleta();
        if (idMaleta == null) {
            return evaluar(estado, parametros).fitness();
        }
        final Ruta anterior = estado.obtenerRuta(idMaleta);
        final boolean tieneRuta = anterior != null && !estado.esComprometida(idMaleta);

        if (tieneRuta) {
            estado.removerRuta(idMaleta);
        }
        estado.agregarRuta(candidata);
        try {
            return evaluar(estado, parametros).fitness();
        } finally {
            estado.removerRuta(idMaleta);
            if (tieneRuta) {
                estado.agregarRuta(anterior);
            }
        }
    }

    private static MetricasAeropuerto evaluarAeropuerto(final Aeropuerto aeropuerto,
                                                        final NavigableMap<LocalDateTime, Integer> eventos,
                                                        final int base) {
        if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
            return new MetricasAeropuerto(0.0D, 0.0D, base);
        }
        if (eventos == null || eventos.isEmpty()) {
            final double relativo = Math.min(1.0D, Math.max(0.0D, base / (double) aeropuerto.getCapacidadAlmacen()));
            final double overflow = Math.max(0.0D, base - aeropuerto.getCapacidadAlmacen());
            return new MetricasAeropuerto(overflow, relativo, base);
        }
        int actual = base;
        int maximo = base;
        double sumaRelativa = 0.0D;
        int muestras = 0;
        for (final Map.Entry<LocalDateTime, Integer> evento : eventos.entrySet()) {
            actual += evento.getValue();
            maximo = Math.max(maximo, actual);
            sumaRelativa += Math.min(1.0D, Math.max(0.0D, actual / (double) aeropuerto.getCapacidadAlmacen()));
            muestras++;
        }
        final double overflow = Math.max(0.0D, maximo - aeropuerto.getCapacidadAlmacen());
        final double promedio = muestras > 0 ? sumaRelativa / muestras : 0.0D;
        return new MetricasAeropuerto(overflow, promedio, maximo);
    }

    record Resultado(double fitness,
                     int noEnrutadas,
                     int fueraDePlazo,
                     double overflowVuelos,
                     double overflowAeropuertos,
                     double ocupacionPromedioVuelos,
                     double ocupacionPromedioAeropuertos,
                     double penalizacionHolgura,
                     boolean factible) {
    }

    private record MetricasAeropuerto(double overflow, double promedio, int pico) {
    }
}
