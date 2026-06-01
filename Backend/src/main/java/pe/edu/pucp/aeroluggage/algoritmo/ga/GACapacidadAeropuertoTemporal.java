package pe.edu.pucp.aeroluggage.algoritmo.ga;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class GACapacidadAeropuertoTemporal {

    private GACapacidadAeropuertoTemporal() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static void registrarIntervalosRuta(final Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto,
                                        final List<VueloInstancia> ruta,
                                        final Pedido pedido,
                                        final long tiempoRecojo) {
        if (ocupacionPorAeropuerto == null) {
            return;
        }
        for (final IntervaloAeropuerto intervalo : construirIntervalosRuta(ruta, pedido, tiempoRecojo)) {
            ocupacionPorAeropuerto.computeIfAbsent(intervalo.idAeropuerto(), ignored -> new ArrayList<>())
                    .add(intervalo.intervalo());
        }
    }

    static void liberarIntervalosRuta(final Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto,
                                      final List<VueloInstancia> ruta,
                                      final Pedido pedido,
                                      final long tiempoRecojo) {
        if (ocupacionPorAeropuerto == null) {
            return;
        }
        for (final IntervaloAeropuerto intervalo : construirIntervalosRuta(ruta, pedido, tiempoRecojo)) {
            final List<IntervaloOcupacion> ocupaciones = ocupacionPorAeropuerto.get(intervalo.idAeropuerto());
            if (ocupaciones == null || ocupaciones.isEmpty()) {
                continue;
            }
            ocupaciones.remove(intervalo.intervalo());
            if (ocupaciones.isEmpty()) {
                ocupacionPorAeropuerto.remove(intervalo.idAeropuerto());
            }
        }
    }

    static List<IntervaloAeropuerto> construirIntervalosRuta(final List<VueloInstancia> ruta,
                                                             final Pedido pedido,
                                                             final long tiempoRecojo) {
        final List<IntervaloAeropuerto> intervalos = new ArrayList<>();
        if (ruta == null || ruta.isEmpty() || pedido == null) {
            return intervalos;
        }
        final VueloInstancia primerVuelo = ruta.get(0);
        if (primerVuelo != null && pedido.getFechaRegistro() != null && primerVuelo.getFechaSalida() != null) {
            agregarIntervalo(
                    intervalos,
                    obtenerIdAeropuerto(pedido.getAeropuertoOrigen()),
                    pedido.getFechaRegistro(),
                    primerVuelo.getFechaSalida()
            );
        }
        for (int i = 0; i < ruta.size() - 1; i++) {
            final VueloInstancia actual = ruta.get(i);
            final VueloInstancia siguiente = ruta.get(i + 1);
            if (actual == null || siguiente == null) {
                continue;
            }
            agregarIntervalo(
                    intervalos,
                    obtenerIdAeropuerto(actual.getAeropuertoDestino()),
                    actual.getFechaLlegada(),
                    siguiente.getFechaSalida()
            );
        }
        final VueloInstancia ultimoVuelo = ruta.get(ruta.size() - 1);
        if (ultimoVuelo != null && ultimoVuelo.getFechaLlegada() != null) {
            agregarIntervalo(
                    intervalos,
                    obtenerIdAeropuerto(ultimoVuelo.getAeropuertoDestino()),
                    ultimoVuelo.getFechaLlegada(),
                    ultimoVuelo.getFechaLlegada().plusMinutes(Math.max(0L, tiempoRecojo))
            );
        }
        return intervalos;
    }

    static ValidacionRuta validarRuta(final List<VueloInstancia> ruta,
                                      final Pedido pedido,
                                      final long tiempoRecojo,
                                      final Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto,
                                      final Map<String, Aeropuerto> aeropuertos) {
        if (ruta == null || ruta.isEmpty() || pedido == null) {
            return ValidacionRuta.aceptada();
        }
        for (final IntervaloAeropuerto intervalo : construirIntervalosRuta(ruta, pedido, tiempoRecojo)) {
            final OverflowAeropuerto overflow = encontrarPrimerOverflow(
                    intervalo.idAeropuerto(),
                    intervalo.intervalo(),
                    ocupacionPorAeropuerto,
                    aeropuertos
            );
            if (overflow != null) {
                return ValidacionRuta.rechazada(overflow);
            }
        }
        return ValidacionRuta.aceptada();
    }

    static OverflowAeropuerto encontrarPrimerOverflow(final String idAeropuerto,
                                                      final IntervaloOcupacion candidato,
                                                      final Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto,
                                                      final Map<String, Aeropuerto> aeropuertos) {
        if (idAeropuerto == null || candidato == null || ocupacionPorAeropuerto == null || aeropuertos == null) {
            return null;
        }
        final Aeropuerto aeropuerto = aeropuertos.get(idAeropuerto);
        if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
            return null;
        }
        final List<IntervaloOcupacion> existentes = ocupacionPorAeropuerto.getOrDefault(idAeropuerto, List.of());
        final List<EventoOcupacion> eventos = new ArrayList<>(existentes.size() * 2 + 2);
        for (final IntervaloOcupacion intervalo : existentes) {
            if (intervalo == null || !seSolapan(intervalo, candidato)) {
                continue;
            }
            eventos.add(new EventoOcupacion(intervalo.inicio(), 1));
            eventos.add(new EventoOcupacion(intervalo.fin(), -1));
        }
        eventos.add(new EventoOcupacion(candidato.inicio(), 1));
        eventos.add(new EventoOcupacion(candidato.fin(), -1));
        eventos.sort(Comparator.comparing(EventoOcupacion::momento).thenComparing(EventoOcupacion::delta));

        int ocupacion = Math.max(0, aeropuerto.getMaletasActuales());
        for (final EventoOcupacion evento : eventos) {
            ocupacion += evento.delta();
            if (ocupacion > aeropuerto.getCapacidadAlmacen()) {
                return new OverflowAeropuerto(
                        idAeropuerto,
                        candidato,
                        ocupacion,
                        aeropuerto.getCapacidadAlmacen()
                );
            }
        }
        return null;
    }

    static int calcularPicoOcupacion(final List<IntervaloOcupacion> intervalos, final int base) {
        if (intervalos == null || intervalos.isEmpty()) {
            return base;
        }
        final List<EventoOcupacion> eventos = new ArrayList<>(intervalos.size() * 2);
        for (final IntervaloOcupacion intervalo : intervalos) {
            if (intervalo == null || intervalo.inicio() == null || intervalo.fin() == null) {
                continue;
            }
            eventos.add(new EventoOcupacion(intervalo.inicio(), 1));
            eventos.add(new EventoOcupacion(intervalo.fin(), -1));
        }
        eventos.sort(Comparator.comparing(EventoOcupacion::momento).thenComparing(EventoOcupacion::delta));
        int actual = base;
        int maximo = base;
        for (final EventoOcupacion evento : eventos) {
            actual += evento.delta();
            if (actual > maximo) {
                maximo = actual;
            }
        }
        return maximo;
    }

    private static String obtenerIdAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto == null ? null : aeropuerto.getIdAeropuerto();
    }

    private static void agregarIntervalo(final List<IntervaloAeropuerto> intervalos,
                                         final String idAeropuerto,
                                         final LocalDateTime inicio,
                                         final LocalDateTime fin) {
        final boolean intervaloValido = idAeropuerto != null
                && inicio != null
                && fin != null
                && fin.isAfter(inicio);
        if (!intervaloValido) {
            return;
        }
        intervalos.add(new IntervaloAeropuerto(idAeropuerto, new IntervaloOcupacion(inicio, fin)));
    }

    private static boolean seSolapan(final IntervaloOcupacion primero, final IntervaloOcupacion segundo) {
        return primero.inicio().isBefore(segundo.fin()) && segundo.inicio().isBefore(primero.fin());
    }

    record IntervaloOcupacion(LocalDateTime inicio, LocalDateTime fin) {
    }

    record IntervaloAeropuerto(String idAeropuerto, IntervaloOcupacion intervalo) {
    }

    private record EventoOcupacion(LocalDateTime momento, int delta) {
    }

    record OverflowAeropuerto(String idAeropuerto,
                              IntervaloOcupacion intervalo,
                              int ocupacionPico,
                              int capacidad) {
    }

    record ValidacionRuta(boolean esValida, OverflowAeropuerto overflow) {
        static ValidacionRuta aceptada() {
            return new ValidacionRuta(true, null);
        }

        static ValidacionRuta rechazada(final OverflowAeropuerto overflow) {
            return new ValidacionRuta(false, overflow);
        }
    }
}
