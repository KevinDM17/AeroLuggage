package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modela la capacidad de almacén de un aeropuerto de forma temporal.
 * Registra intervalos [llegada, liberacion) por cada maleta asignada.
 * La consulta disponibleEn(t) cuenta solo las maletas que siguen en el
 * aeropuerto en el instante t, liberando automáticamente las que ya salieron.
 */
final class CapacidadTemporalAlmacen {
    private final int capacidadBase;
    private final List<long[]> intervalos;

    CapacidadTemporalAlmacen(final int capacidadBase) {
        this.capacidadBase = capacidadBase;
        this.intervalos = new ArrayList<>();
    }

    private CapacidadTemporalAlmacen(final int capacidadBase, final List<long[]> intervalos) {
        this.capacidadBase = capacidadBase;
        this.intervalos = new ArrayList<>(intervalos);
    }

    int getCapacidadBase() {
        return capacidadBase;
    }

    /** Slots libres en el instante {@code tiempo}. */
    int disponibleEn(final LocalDateTime tiempo) {
        final long t = tiempo.toEpochSecond(ZoneOffset.UTC);
        int ocupados = 0;
        for (final long[] intervalo : intervalos) {
            if (intervalo[0] <= t && t < intervalo[1]) {
                ocupados++;
            }
        }
        return Math.max(0, capacidadBase - ocupados);
    }

    boolean puedeReservar(final LocalDateTime desde, final LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return false;
        }
        if (capacidadBase <= 0) {
            return false;
        }

        final long inicio = desde.toEpochSecond(ZoneOffset.UTC);
        final long fin = hasta.toEpochSecond(ZoneOffset.UTC);
        final List<long[]> eventos = new ArrayList<>(intervalos.size() * 2 + 2);

        for (final long[] intervalo : intervalos) {
            final long inicioSolapado = Math.max(inicio, intervalo[0]);
            final long finSolapado = Math.min(fin, intervalo[1]);
            if (inicioSolapado >= finSolapado) {
                continue;
            }
            eventos.add(new long[]{inicioSolapado, 1});
            eventos.add(new long[]{finSolapado, -1});
        }
        eventos.add(new long[]{inicio, 1});
        eventos.add(new long[]{fin, -1});
        eventos.sort((primero, segundo) -> {
            final int comparacionTiempo = Long.compare(primero[0], segundo[0]);
            if (comparacionTiempo != 0) {
                return comparacionTiempo;
            }
            return Long.compare(primero[1], segundo[1]);
        });

        int ocupados = 0;
        for (final long[] evento : eventos) {
            ocupados += (int) evento[1];
            if (ocupados > capacidadBase) {
                return false;
            }
        }
        return true;
    }

    /** Registra que una maleta ocupa almacén desde {@code desde} hasta {@code hasta}. */
    void reservar(final LocalDateTime desde, final LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return;
        }
        intervalos.add(new long[]{
            desde.toEpochSecond(ZoneOffset.UTC),
            hasta.toEpochSecond(ZoneOffset.UTC)
        });
    }

    /** Copia profunda: misma capacidad base, lista de intervalos propia. */
    CapacidadTemporalAlmacen clonar() {
        return new CapacidadTemporalAlmacen(capacidadBase, intervalos);
    }

    /** Clona un mapa completo de capacidades, creando objetos independientes. */
    static Map<String, CapacidadTemporalAlmacen> clonarMapa(
            final Map<String, CapacidadTemporalAlmacen> original
    ) {
        final Map<String, CapacidadTemporalAlmacen> copia = new HashMap<>(original.size() * 2);
        for (final Map.Entry<String, CapacidadTemporalAlmacen> entrada : original.entrySet()) {
            copia.put(entrada.getKey(), entrada.getValue().clonar());
        }
        return copia;
    }
}
