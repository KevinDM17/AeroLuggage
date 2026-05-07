package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CapacidadTemporalAlmacen {
    private final int capacidadBase;
    private final List<long[]> intervalos;
    private final List<Long> liberaciones;

    CapacidadTemporalAlmacen(final int capacidadBase) {
        this.capacidadBase = capacidadBase;
        this.intervalos = new ArrayList<>();
        this.liberaciones = new ArrayList<>();
    }

    private CapacidadTemporalAlmacen(final int capacidadBase,
                                     final List<long[]> intervalos,
                                     final List<Long> liberaciones) {
        this.capacidadBase = capacidadBase;
        this.intervalos = new ArrayList<>(intervalos);
        this.liberaciones = new ArrayList<>(liberaciones);
    }

    int getCapacidadBase() {
        return capacidadBase;
    }

    int disponibleEn(final LocalDateTime tiempo) {
        final long inicio = System.nanoTime();
        final int disponible = Math.max(0, capacidadBase - ocupacionSinCandidato(
                tiempo.toEpochSecond(ZoneOffset.UTC)
        ));
        registrarDisponibleEn(System.nanoTime() - inicio);
        return disponible;
    }

    boolean puedeReservar(final LocalDateTime desde, final LocalDateTime hasta) {
        final long inicioMedicion = System.nanoTime();
        final boolean puedeReservar = puedeReservarInterno(desde, hasta);
        registrarPuedeReservar(System.nanoTime() - inicioMedicion);
        return puedeReservar;
    }

    void reservar(final LocalDateTime desde, final LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return;
        }
        intervalos.add(new long[]{
            desde.toEpochSecond(ZoneOffset.UTC),
            hasta.toEpochSecond(ZoneOffset.UTC)
        });
    }

    void liberar(final LocalDateTime desde) {
        if (desde == null) {
            return;
        }
        liberaciones.add(desde.toEpochSecond(ZoneOffset.UTC));
    }

    CapacidadTemporalAlmacen clonar() {
        return new CapacidadTemporalAlmacen(capacidadBase, intervalos, liberaciones);
    }

    static Map<String, CapacidadTemporalAlmacen> clonarMapa(
            final Map<String, CapacidadTemporalAlmacen> original
    ) {
        final Map<String, CapacidadTemporalAlmacen> copia = new HashMap<>(original.size() * 2);
        for (final Map.Entry<String, CapacidadTemporalAlmacen> entrada : original.entrySet()) {
            copia.put(entrada.getKey(), entrada.getValue().clonar());
        }
        return copia;
    }

    private boolean puedeReservarInterno(final LocalDateTime desde, final LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return false;
        }
        if (capacidadBase <= 0 && liberaciones.isEmpty()) {
            return false;
        }

        final long inicio = desde.toEpochSecond(ZoneOffset.UTC);
        final long fin = hasta.toEpochSecond(ZoneOffset.UTC);
        if (ocupacionConCandidato(inicio, inicio, fin) > capacidadBase) {
            return false;
        }

        for (final long[] intervalo : intervalos) {
            if (intervalo[0] >= inicio && intervalo[0] < fin
                    && ocupacionConCandidato(intervalo[0], inicio, fin) > capacidadBase) {
                return false;
            }
            if (intervalo[1] >= inicio && intervalo[1] < fin
                    && ocupacionConCandidato(intervalo[1], inicio, fin) > capacidadBase) {
                return false;
            }
        }

        for (final long liberacion : liberaciones) {
            if (liberacion >= inicio && liberacion < fin
                    && ocupacionConCandidato(liberacion, inicio, fin) > capacidadBase) {
                return false;
            }
        }

        return true;
    }

    private int ocupacionSinCandidato(final long tiempo) {
        int ocupados = 0;
        for (final long[] intervalo : intervalos) {
            if (intervalo[0] <= tiempo && tiempo < intervalo[1]) {
                ocupados++;
            }
        }
        for (final long liberacion : liberaciones) {
            if (liberacion <= tiempo) {
                ocupados--;
            }
        }
        return ocupados;
    }

    private int ocupacionConCandidato(final long tiempo, final long inicioCandidato, final long finCandidato) {
        int ocupados = ocupacionSinCandidato(tiempo);
        if (inicioCandidato <= tiempo && tiempo < finCandidato) {
            ocupados++;
        }
        return ocupados;
    }

    private void registrarPuedeReservar(final long nanos) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarPuedeReservar(nanos);
        }
    }

    private void registrarDisponibleEn(final long nanos) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarDisponibleEn(nanos);
        }
    }
}
