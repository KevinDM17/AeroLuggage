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
 *
 * Adicionalmente, soporta "liberaciones": momentos en que una maleta que ya
 * estaba físicamente en el aeropuerto (y por tanto reducía capacidadBase)
 * parte en un vuelo, devolviendo 1 slot de forma permanente desde ese instante.
 */
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

    /** Slots libres en el instante {@code tiempo}. */
    int disponibleEn(final LocalDateTime tiempo) {
        final long t = tiempo.toEpochSecond(ZoneOffset.UTC);
        int ocupados = 0;
        for (final long[] intervalo : intervalos) {
            if (intervalo[0] <= t && t < intervalo[1]) {
                ocupados++;
            }
        }
        for (final long libTime : liberaciones) {
            if (libTime <= t) {
                ocupados--;
            }
        }
        return Math.max(0, capacidadBase - ocupados);
    }

    boolean puedeReservar(final LocalDateTime desde, final LocalDateTime hasta) {
        if (desde == null || hasta == null || !hasta.isAfter(desde)) {
            return false;
        }
        if (capacidadBase <= 0 && liberaciones.isEmpty()) {
            return false;
        }

        final long inicio = desde.toEpochSecond(ZoneOffset.UTC);
        final long fin = hasta.toEpochSecond(ZoneOffset.UTC);
        final List<long[]> eventos = new ArrayList<>(intervalos.size() * 2 + liberaciones.size() + 2);

        for (final long[] intervalo : intervalos) {
            final long inicioSolapado = Math.max(inicio, intervalo[0]);
            final long finSolapado = Math.min(fin, intervalo[1]);
            if (inicioSolapado >= finSolapado) {
                continue;
            }
            eventos.add(new long[]{inicioSolapado, 1});
            eventos.add(new long[]{finSolapado, -1});
        }

        // Liberaciones: cada una reduce el conteo neto de ocupados desde ese instante.
        // Las anteriores al inicio afectan todo el ventana, así que se anclan al inicio.
        for (final long libTime : liberaciones) {
            if (libTime <= inicio) {
                eventos.add(new long[]{inicio, -1});
            } else if (libTime < fin) {
                eventos.add(new long[]{libTime, -1});
            }
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

    /**
     * Registra que una maleta que ya estaba físicamente en el aeropuerto
     * (contada en maletasActuales y por tanto en capacidadBase) parte en
     * un vuelo en {@code desde}, devolviendo 1 slot de almacén desde ese instante.
     */
    void liberar(final LocalDateTime desde) {
        if (desde == null) {
            return;
        }
        liberaciones.add(desde.toEpochSecond(ZoneOffset.UTC));
    }

    /** Copia profunda: misma capacidad base, listas de intervalos y liberaciones propias. */
    CapacidadTemporalAlmacen clonar() {
        return new CapacidadTemporalAlmacen(capacidadBase, intervalos, liberaciones);
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
