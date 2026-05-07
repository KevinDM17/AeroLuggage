package pe.edu.pucp.aeroluggage.algoritmo.ga;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class OperadorMutacion {

    public enum Tipo {
        REROUTE_MALETA, SWAP_MALETAS, CAMBIO_TRAMO, RESCATE_FALLIDA
    }

    private OperadorMutacion() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static Solucion mutar(final Solucion original, final InstanciaProblema instancia,
                                 final ParametrosGA params, final Random random) {
        if (original == null || original.getSolucion() == null || original.getSolucion().isEmpty()) {
            return original;
        }
        final Tipo tipo = elegirTipo(random);
        switch (tipo) {
            case REROUTE_MALETA -> rerouteMaleta(original, instancia, params, random);
            case SWAP_MALETAS -> swapMaletas(original, random);
            case CAMBIO_TRAMO -> cambiarTramo(original, instancia, params, random);
            case RESCATE_FALLIDA -> rescatarRutaFallida(original, instancia, params, random);
            default -> {
                // no-op
            }
        }
        original.calcularMetricas();
        return original;
    }

    private static Tipo elegirTipo(final Random random) {
        final double p = random.nextDouble();
        if (p < 0.5) {
            return Tipo.RESCATE_FALLIDA;
        }
        if (p < 0.8) {
            return Tipo.REROUTE_MALETA;
        }
        if (p < 0.92) {
            return Tipo.CAMBIO_TRAMO;
        }
        return Tipo.SWAP_MALETAS;
    }

    private static void rerouteMaleta(final Solucion solucion, final InstanciaProblema instancia,
                                      final ParametrosGA params, final Random random) {
        final List<Ruta> rutas = solucion.getSolucion();
        if (rutas.isEmpty() || instancia.getGrafo() == null) {
            return;
        }
        final Ruta ruta = seleccionarRutaDebil(rutas, random);
        if (ruta == null) {
            return;
        }
        final Maleta maleta = buscarMaleta(instancia, ruta.getIdMaleta());
        if (maleta == null || maleta.getPedido() == null) {
            return;
        }
        final Pedido pedido = maleta.getPedido();
        final Set<String> bloqueados = new HashSet<>();
        if (ruta.getSubrutas() != null && !ruta.getSubrutas().isEmpty()) {
            final VueloInstancia vueloAzar = ruta.getSubrutas().get(random.nextInt(ruta.getSubrutas().size()));
            if (vueloAzar != null && vueloAzar.getIdVueloInstancia() != null) {
                bloqueados.add(vueloAzar.getIdVueloInstancia());
            }
        }
        final List<VueloInstancia> nuevoCamino = GARuteadorCache.rutear(
                pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino(),
                pedido.getFechaRegistro(), pedido.getFechaHoraPlazo(),
                instancia.getGrafo(), instancia.getMinutosConexion(), bloqueados);
        if (nuevoCamino == null) {
            return;
        }
        final boolean mejoraCobertura = ruta.getEstado() == EstadoRuta.FALLIDA
                || ruta.getSubrutas() == null
                || ruta.getSubrutas().isEmpty();
        if (!mejoraCobertura && ruta.getDuracion() > 0.0 && duracionHoras(nuevoCamino) >= ruta.getDuracion()) {
            return;
        }
        ruta.setSubrutas(new ArrayList<>(nuevoCamino));
        ruta.setEstado(nuevoCamino.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
        ruta.setDuracion(duracionHoras(nuevoCamino));
    }

    private static void swapMaletas(final Solucion solucion, final Random random) {
        final List<Ruta> rutas = solucion.getSolucion();
        if (rutas.size() < 2) {
            return;
        }
        final int i = random.nextInt(rutas.size());
        int j = random.nextInt(rutas.size());
        if (i == j) {
            j = (j + 1) % rutas.size();
        }
        final Ruta rutaA = rutas.get(i);
        final Ruta rutaB = rutas.get(j);
        if (rutaA == null || rutaB == null) {
            return;
        }
        final List<VueloInstancia> subrutasA = rutaA.getSubrutas();
        final List<VueloInstancia> subrutasB = rutaB.getSubrutas();
        final double duracionA = rutaA.getDuracion();
        final double duracionB = rutaB.getDuracion();
        final EstadoRuta estadoA = rutaA.getEstado();
        final EstadoRuta estadoB = rutaB.getEstado();

        rutaA.setSubrutas(subrutasB != null ? new ArrayList<>(subrutasB) : new ArrayList<>());
        rutaA.setDuracion(duracionB);
        rutaA.setEstado(estadoB);

        rutaB.setSubrutas(subrutasA != null ? new ArrayList<>(subrutasA) : new ArrayList<>());
        rutaB.setDuracion(duracionA);
        rutaB.setEstado(estadoA);
    }

    private static void cambiarTramo(final Solucion solucion, final InstanciaProblema instancia,
                                     final ParametrosGA params, final Random random) {
        final List<Ruta> rutas = solucion.getSolucion();
        if (rutas.isEmpty() || instancia.getGrafo() == null) {
            return;
        }
        final Ruta ruta = seleccionarRutaDebil(rutas, random);
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().size() < 2) {
            rerouteMaleta(solucion, instancia, params, random);
            return;
        }
        final List<VueloInstancia> actual = ruta.getSubrutas();
        final int idxCorte = random.nextInt(actual.size() - 1) + 1;
        final VueloInstancia previo = actual.get(idxCorte - 1);
        if (previo == null || previo.getAeropuertoDestino() == null) {
            return;
        }
        final Maleta maleta = buscarMaleta(instancia, ruta.getIdMaleta());
        if (maleta == null || maleta.getPedido() == null) {
            return;
        }
        final Pedido pedido = maleta.getPedido();
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        final LocalDateTime tDesde = previo.getFechaLlegada();
        final Set<String> bloqueados = new HashSet<>();
        final VueloInstancia siguienteActual = actual.get(idxCorte);
        if (siguienteActual != null && siguienteActual.getIdVueloInstancia() != null) {
            bloqueados.add(siguienteActual.getIdVueloInstancia());
        }
        final List<VueloInstancia> suffix = GARuteadorCache.rutear(
                previo.getAeropuertoDestino(), pedido.getAeropuertoDestino(),
                tDesde, pedido.getFechaHoraPlazo(),
                grafo, instancia.getMinutosConexion(), bloqueados);
        if (suffix == null) {
            return;
        }
        final List<VueloInstancia> nuevo = new ArrayList<>(idxCorte + suffix.size());
        nuevo.addAll(actual.subList(0, idxCorte));
        nuevo.addAll(suffix);
        if (!terminaEnDestino(nuevo, pedido)) {
            return;
        }
        if (ruta.getDuracion() > 0.0 && duracionHoras(nuevo) >= ruta.getDuracion() && ruta.getEstado() != EstadoRuta.FALLIDA) {
            return;
        }
        ruta.setSubrutas(nuevo);
        ruta.setEstado(nuevo.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
        ruta.setDuracion(duracionHoras(nuevo));
    }

    private static void rescatarRutaFallida(final Solucion solucion, final InstanciaProblema instancia,
                                            final ParametrosGA params, final Random random) {
        final List<Ruta> rutas = solucion.getSolucion();
        if (rutas.isEmpty() || instancia.getGrafo() == null) {
            return;
        }
        final List<Ruta> candidatas = new ArrayList<>();
        for (final Ruta ruta : rutas) {
            if (ruta == null) {
                continue;
            }
            final boolean sinCobertura = ruta.getEstado() == EstadoRuta.FALLIDA
                    || ruta.getSubrutas() == null
                    || ruta.getSubrutas().isEmpty();
            if (sinCobertura) {
                candidatas.add(ruta);
            }
        }
        if (candidatas.isEmpty()) {
            rerouteMaleta(solucion, instancia, params, random);
            return;
        }
        final Ruta ruta = candidatas.get(random.nextInt(candidatas.size()));
        final Maleta maleta = buscarMaleta(instancia, ruta.getIdMaleta());
        if (maleta == null || maleta.getPedido() == null) {
            return;
        }
        final Pedido pedido = maleta.getPedido();
        final List<VueloInstancia> nuevoCamino = GARuteadorCache.rutear(
                pedido.getAeropuertoOrigen(),
                pedido.getAeropuertoDestino(),
                pedido.getFechaRegistro(),
                pedido.getFechaHoraPlazo(),
                instancia.getGrafo(),
                instancia.getMinutosConexion(),
                new HashSet<>()
        );
        if (nuevoCamino == null || nuevoCamino.isEmpty()) {
            return;
        }
        if (!terminaEnDestino(nuevoCamino, pedido)) {
            return;
        }
        ruta.setSubrutas(new ArrayList<>(nuevoCamino));
        ruta.setEstado(EstadoRuta.PLANIFICADA);
        ruta.setDuracion(duracionHoras(nuevoCamino));
    }

    private static Ruta seleccionarRutaDebil(final List<Ruta> rutas, final Random random) {
        final List<Ruta> debiles = new ArrayList<>();
        final List<Ruta> largas = new ArrayList<>();
        for (final Ruta ruta : rutas) {
            if (ruta == null) {
                continue;
            }
            final boolean fallida = ruta.getEstado() == EstadoRuta.FALLIDA
                    || ruta.getSubrutas() == null
                    || ruta.getSubrutas().isEmpty();
            if (fallida) {
                debiles.add(ruta);
                continue;
            }
            if (ruta.getDuracion() > 0.75) {
                largas.add(ruta);
            }
        }
        if (!debiles.isEmpty()) {
            return debiles.get(random.nextInt(debiles.size()));
        }
        if (!largas.isEmpty()) {
            return largas.get(random.nextInt(largas.size()));
        }
        return rutas.get(random.nextInt(rutas.size()));
    }

    private static boolean terminaEnDestino(final List<VueloInstancia> vuelos, final Pedido pedido) {
        if (vuelos == null || vuelos.isEmpty() || pedido == null || pedido.getAeropuertoDestino() == null) {
            return false;
        }
        final VueloInstancia ultimoVuelo = vuelos.get(vuelos.size() - 1);
        return ultimoVuelo != null
                && ultimoVuelo.getAeropuertoDestino() != null
                && pedido.getAeropuertoDestino().getIdAeropuerto() != null
                && pedido.getAeropuertoDestino().getIdAeropuerto()
                .equals(ultimoVuelo.getAeropuertoDestino().getIdAeropuerto());
    }

    private static Maleta buscarMaleta(final InstanciaProblema instancia, final String idMaleta) {
        if (instancia == null || instancia.getMaletas() == null || idMaleta == null) {
            return null;
        }
        final Map<String, Maleta> indice = new HashMap<>();
        for (final Maleta m : instancia.getMaletas()) {
            if (m != null && m.getIdMaleta() != null) {
                indice.put(m.getIdMaleta(), m);
            }
        }
        return indice.get(idMaleta);
    }

    private static double duracionHoras(final List<VueloInstancia> camino) {
        if (camino == null || camino.isEmpty()) {
            return 0.0;
        }
        final LocalDateTime inicio = camino.get(0).getFechaSalida();
        final LocalDateTime fin = camino.get(camino.size() - 1).getFechaLlegada();
        if (inicio == null || fin == null) {
            return 0.0;
        }
        return java.time.Duration.between(inicio, fin).toMinutes() / 60.0;
    }
}
