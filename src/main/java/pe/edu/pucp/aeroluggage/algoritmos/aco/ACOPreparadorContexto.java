package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

final class ACOPreparadorContexto {
    private final ACOConfiguracion configuracion;

    ACOPreparadorContexto(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion;
    }

    Map<String, Double> inicializarFeromonas(final ArrayList<VueloProgramado> vuelos) {
        final Map<String, Double> feromonasIniciales = new HashMap<>();
        if (vuelos == null || vuelos.isEmpty()) {
            return feromonasIniciales;
        }
        for (final VueloProgramado vuelo : vuelos) {
            if (vuelo == null || vuelo.getIdVueloProgramado() == null) {
                continue;
            }
            feromonasIniciales.put(vuelo.getIdVueloProgramado(), configuracion.getTau0());
        }
        return feromonasIniciales;
    }

    boolean noTerminaHorizonteOperacion(final int intervaloActual) {
        return intervaloActual < Math.max(1, configuracion.getNts());
    }

    ArrayList<String> leerEventos(final int intervaloActual) {
        return new ArrayList<>();
    }

    ArrayList<Maleta> actualizarMaletasPendientes(
            final ArrayList<Maleta> maletas,
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

    ArrayList<VueloProgramado> actualizarVuelosDisponibles(
            final ArrayList<VueloProgramado> vuelos,
            final ArrayList<String> eventos
    ) {
        final ArrayList<VueloProgramado> vuelosDisponibles = new ArrayList<>();
        if (vuelos == null || vuelos.isEmpty()) {
            return vuelosDisponibles;
        }
        for (final VueloProgramado vuelo : vuelos) {
            final boolean vueloInvalido = vuelo == null
                    || vuelo.getIdVueloProgramado() == null
                    || vuelo.getHoraSalida() == null
                    || vuelo.getHoraLlegada() == null
                    || vuelo.getAeropuertoOrigen() == null
                    || vuelo.getAeropuertoDestino() == null;
            if (vueloInvalido) {
                continue;
            }
            vuelosDisponibles.add(vuelo);
        }
        vuelosDisponibles.sort(Comparator.comparing(VueloProgramado::getHoraSalida));
        return vuelosDisponibles;
    }

    CapacidadesACO recalcularCapacidades(
            final ArrayList<VueloProgramado> vuelos,
            final ArrayList<Aeropuerto> aeropuertos
    ) {
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>();
        if (vuelos != null) {
            for (final VueloProgramado vuelo : vuelos) {
                if (vuelo == null || vuelo.getIdVueloProgramado() == null) {
                    continue;
                }
                capacidadRestanteVuelo.put(vuelo.getIdVueloProgramado(), Math.max(0, vuelo.getCapacidadMaxima()));
            }
        }

        final Map<String, Integer> capacidadRestanteAlmacen = new HashMap<>();
        if (aeropuertos != null) {
            for (final Aeropuerto aeropuerto : aeropuertos) {
                if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                    continue;
                }
                final int capacidadDisponible = Math.max(
                        0,
                        aeropuerto.getCapacidadAlmacen() - aeropuerto.getMaletasActuales()
                );
                capacidadRestanteAlmacen.put(aeropuerto.getIdAeropuerto(), capacidadDisponible);
            }
        }

        return new CapacidadesACO(capacidadRestanteVuelo, capacidadRestanteAlmacen);
    }

    SubproblemaACO construirSubproblema(
            final ArrayList<Maleta> maletas,
            final ArrayList<VueloProgramado> vuelos,
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

        for (final VueloProgramado vuelo : instancia.getVuelos()) {
            if (vuelo == null) {
                continue;
            }
            tiempoBase = minimoTiempo(tiempoBase, vuelo.getHoraSalida());
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
