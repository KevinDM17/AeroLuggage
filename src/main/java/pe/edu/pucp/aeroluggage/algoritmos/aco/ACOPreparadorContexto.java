package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class ACOPreparadorContexto {
    private final ACOConfiguracion configuracion;

    ACOPreparadorContexto(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion;
    }

    boolean noTerminaHorizonteOperacion(final int intervaloActual) {
        return intervaloActual < Math.max(1, configuracion.getNts());
    }

    ArrayList<String> leerEventos(final int intervaloActual) {
        return new ArrayList<>();
    }

    ArrayList<Maleta> actualizarMaletasPendientes(
            final List<Maleta> maletas,
            final ArrayList<String> eventos,
            final ArrayList<Ruta> planesConfirmados
    ) {
        final Set<String> maletasConfirmadas = new HashSet<>();
        for (final Ruta ruta : planesConfirmados) {
            if (ruta == null || ruta.getIdMaleta() == null) {
                continue;
            }
            maletasConfirmadas.add(ruta.getIdMaleta());
        }

        final ArrayList<Maleta> maletasPendientes = new ArrayList<>();
        if (maletas == null || maletas.isEmpty()) {
            return maletasPendientes;
        }
        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            if (maletasConfirmadas.contains(maleta.getIdMaleta())) {
                continue;
            }
            maletasPendientes.add(maleta);
        }
        return maletasPendientes;
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
        if (aeropuertos != null) {
            for (final Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                    continue;
                }
                final int capacidadDisponible = Math.max(
                        0,
                        aeropuerto.getCapacidadAlmacen() - aeropuerto.getMaletasActuales()
                );
                capacidadRestanteAlmacen.put(
                        aeropuerto.getIdAeropuerto(),
                        new CapacidadTemporalAlmacen(capacidadDisponible)
                );
            }
        }

        return new CapacidadesACO(capacidadRestanteVuelo, capacidadRestanteAlmacen);
    }

    SubproblemaACO construirSubproblema(
            final List<Maleta> maletas,
            final List<VueloInstancia> vuelos,
            final CapacidadesACO capacidades,
            final int intervaloActual,
            final LocalDateTime tiempoBase
    ) {
        final Map<String, LocalDateTime> plazosPorMaleta = new HashMap<>();
        final Map<String, Maleta> maletasPorId = new HashMap<>();
        final LocalDateTime inicioIntervalo = tiempoBase == null
                ? null
                : tiempoBase.plusHours((long) intervaloActual * configuracion.getHorasPorIntervalo());

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
                intervaloActual,
                inicioIntervalo
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

    LocalDateTime siguienteIntervalo(final LocalDateTime inicioIntervalo) {
        if (inicioIntervalo == null) {
            return null;
        }
        return inicioIntervalo.plusHours(configuracion.getHorasPorIntervalo());
    }

    int avanzarIntervalo(final int intervaloActual) {
        return intervaloActual + 1;
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
}
