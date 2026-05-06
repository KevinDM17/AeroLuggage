package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema.IntervaloOcupacionAeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class ACOPreparadorContexto {
    ACOPreparadorContexto(final ACOConfiguracion configuracion) {
    }

    ArrayList<VueloInstancia> actualizarVuelosDisponibles(
            final List<VueloInstancia> vuelos,
            final ArrayList<String> eventos
    ) {
        final ArrayList<VueloInstancia> vuelosDisponibles = new ArrayList<>();
        if (vuelos == null || vuelos.isEmpty()) {
            return vuelosDisponibles;
        }
        for (final VueloInstancia vuelo : vuelos) {
            final boolean vueloInvalido = vuelo == null
                    || vuelo.getIdVueloInstancia() == null
                    || vuelo.getFechaSalida() == null
                    || vuelo.getFechaLlegada() == null
                    || vuelo.getAeropuertoOrigen() == null
                    || vuelo.getAeropuertoDestino() == null;
            if (vueloInvalido) {
                continue;
            }
            vuelosDisponibles.add(vuelo);
        }
        vuelosDisponibles.sort(Comparator.comparing(VueloInstancia::getFechaSalida));
        return vuelosDisponibles;
    }

    CapacidadesACO recalcularCapacidades(
            final InstanciaProblema instancia,
            final List<VueloInstancia> vuelos,
            final List<Aeropuerto> aeropuertos
    ) {
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>();
        if (vuelos != null) {
            for (final VueloInstancia vuelo : vuelos) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                capacidadRestanteVuelo.put(vuelo.getIdVueloInstancia(), Math.max(0, vuelo.getCapacidadDisponible()));
            }
        }

        final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen = new HashMap<>();
        final Map<String, List<IntervaloOcupacionAeropuerto>> ocupacionFuturaAeropuertos = instancia == null
                ? Map.of()
                : instancia.getOcupacionFuturaAeropuertos();
        if (aeropuertos != null) {
            for (final Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                    continue;
                }
                final int capacidadDisponible = Math.max(
                        0,
                        aeropuerto.getCapacidadAlmacen() - aeropuerto.getMaletasActuales()
                );
                final CapacidadTemporalAlmacen capacidadTemporalAlmacen =
                        new CapacidadTemporalAlmacen(capacidadDisponible);
                precargarOcupacionFutura(
                        capacidadTemporalAlmacen,
                        ocupacionFuturaAeropuertos.get(aeropuerto.getIdAeropuerto())
                );
                capacidadRestanteAlmacen.put(aeropuerto.getIdAeropuerto(), capacidadTemporalAlmacen);
            }
        }

        return new CapacidadesACO(capacidadRestanteVuelo, capacidadRestanteAlmacen);
    }

    SubproblemaACO construirSubproblema(
            final List<Maleta> maletas,
            final List<VueloInstancia> vuelos,
            final CapacidadesACO capacidades,
            final LocalDateTime tiempoBase
    ) {
        final Map<String, LocalDateTime> plazosPorMaleta = new HashMap<>();
        final Map<String, Maleta> maletasPorId = new HashMap<>();

        if (maletas != null) {
            for (final Maleta maleta : maletas) {
                if (maleta == null || maleta.getIdMaleta() == null) {
                    continue;
                }
                maletasPorId.put(maleta.getIdMaleta(), maleta);
                final Pedido pedido = maleta.getPedido();
                if (pedido == null) {
                    continue;
                }
                LocalDateTime plazo = pedido.getFechaHoraPlazo();
                if (plazo == null) {
                    plazo = pedido.calcularFechaHoraPlazo();
                }
                plazosPorMaleta.put(maleta.getIdMaleta(), plazo);
            }
        }

        return new SubproblemaACO(
                maletas,
                vuelos,
                capacidades.getCapacidadRestanteVuelo(),
                capacidades.getCapacidadRestanteAlmacen(),
                plazosPorMaleta,
                maletasPorId,
                tiempoBase
        );
    }

    LocalDateTime obtenerTiempoBase(final InstanciaProblema instancia) {
        LocalDateTime tiempoBase = null;
        if (instancia == null) {
            return null;
        }

        for (final Maleta maleta : instancia.getMaletas()) {
            if (maleta == null) {
                continue;
            }
            tiempoBase = minimoTiempo(tiempoBase, maleta.getFechaRegistro());
            final Pedido pedido = maleta.getPedido();
            if (pedido == null) {
                continue;
            }
            tiempoBase = minimoTiempo(tiempoBase, pedido.getFechaRegistro());
        }

        for (final VueloInstancia vuelo : instancia.getVuelosInstancia()) {
            if (vuelo == null) {
                continue;
            }
            tiempoBase = minimoTiempo(tiempoBase, vuelo.getFechaSalida());
        }

        return tiempoBase;
    }

    private LocalDateTime minimoTiempo(final LocalDateTime primero, final LocalDateTime segundo) {
        if (primero == null) {
            return segundo;
        }
        if (segundo == null) {
            return primero;
        }
        return primero.isBefore(segundo) ? primero : segundo;
    }

    private void precargarOcupacionFutura(
            final CapacidadTemporalAlmacen capacidadTemporalAlmacen,
            final List<IntervaloOcupacionAeropuerto> intervalos
    ) {
        if (capacidadTemporalAlmacen == null || intervalos == null || intervalos.isEmpty()) {
            return;
        }
        for (final IntervaloOcupacionAeropuerto intervalo : intervalos) {
            if (intervalo == null) {
                continue;
            }
            capacidadTemporalAlmacen.reservar(intervalo.desde(), intervalo.hasta());
        }
    }
}
