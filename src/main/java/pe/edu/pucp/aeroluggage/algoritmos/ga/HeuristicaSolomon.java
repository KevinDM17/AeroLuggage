package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class HeuristicaSolomon {

    private HeuristicaSolomon() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static Solucion construir(final InstanciaProblema instancia, final ParametrosGA params,
                                     final Random random) {
        return construir(instancia, params, random, 0.0);
    }

    public static Solucion construirAleatorizado(final InstanciaProblema instancia, final ParametrosGA params,
                                                 final Random random) {
        return construir(instancia, params, random, 0.35);
    }

    public static Solucion construirDificilesPrimero(final InstanciaProblema instancia, final ParametrosGA params,
                                                     final Random random) {
        return construirDificilesPrimero(instancia, params, random, 0.15);
    }

    private static Solucion construir(final InstanciaProblema instancia, final ParametrosGA params,
                                      final Random random, final double nivelAleatoriedad) {
        if (instancia == null || instancia.getMaletas() == null || instancia.getMaletas().isEmpty()) {
            return new Solucion();
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            return new Solucion();
        }

        final List<Maleta> pendientes = new ArrayList<>(instancia.getMaletas());
        pendientes.removeIf(m -> m == null || m.getPedido() == null);
        pendientes.sort(Comparator.comparing(HeuristicaSolomon::clavePorUrgencia));

        final Map<String, Integer> consumoVuelo = new HashMap<>();
        final Map<String, Integer> consumoAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();
        final ArrayList<Ruta> rutas = new ArrayList<>(pendientes.size());
        int secuenciaRuta = 1;

        while (!pendientes.isEmpty()) {
            final int indice = seleccionarIndice(pendientes, nivelAleatoriedad, random);
            final Maleta maleta = pendientes.remove(indice);
            final Pedido pedido = maleta.getPedido();

            final LocalDateTime tListo = pedido.getFechaRegistro();
            final LocalDateTime tLimite = pedido.getFechaHoraPlazo();
            final Set<String> bloqueados = new HashSet<>();

            final List<VueloInstancia> camino = rutearConCapacidad(
                    pedido, tListo, tLimite, grafo, params, bloqueados, consumoVuelo,
                    consumoAeropuerto, aeropuertos);

            final Ruta ruta = new Ruta();
            ruta.setIdRuta(String.format("R%08d", secuenciaRuta++));
            ruta.setIdMaleta(maleta.getIdMaleta());
            ruta.setPlazoMaximoDias(Ruta.calcularPlazo(pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino()));

            if (camino == null) {
                ruta.setSubrutas(new ArrayList<>());
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0.0);
            } else {
                ruta.setSubrutas(new ArrayList<>(camino));
                ruta.setEstado(camino.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
                ruta.setDuracion(duracionHoras(camino));
                for (final VueloInstancia v : camino) {
                    consumoVuelo.merge(v.getIdVueloInstancia(), 1, Integer::sum);
                    if (v.getAeropuertoOrigen() != null && v.getAeropuertoOrigen().getIdAeropuerto() != null) {
                        consumoAeropuerto.merge(v.getAeropuertoOrigen().getIdAeropuerto(), 1, Integer::sum);
                    }
                    if (v.getAeropuertoDestino() != null && v.getAeropuertoDestino().getIdAeropuerto() != null) {
                        consumoAeropuerto.merge(v.getAeropuertoDestino().getIdAeropuerto(), 1, Integer::sum);
                    }
                }
            }
            rutas.add(ruta);
        }

        final Solucion solucion = new Solucion(rutas);
        solucion.calcularMetricas();
        return solucion;
    }

    private static Solucion construirDificilesPrimero(final InstanciaProblema instancia, final ParametrosGA params,
                                                      final Random random, final double nivelAleatoriedad) {
        if (instancia == null || instancia.getMaletas() == null || instancia.getMaletas().isEmpty()) {
            return new Solucion();
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            return new Solucion();
        }

        final List<Maleta> pendientes = new ArrayList<>(instancia.getMaletas());
        pendientes.removeIf(m -> m == null || m.getPedido() == null);
        pendientes.sort(Comparator.comparingDouble(HeuristicaSolomon::indiceDificultad).reversed());

        final Map<String, Integer> consumoVuelo = new HashMap<>();
        final Map<String, Integer> consumoAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();
        final ArrayList<Ruta> rutas = new ArrayList<>(pendientes.size());
        int secuenciaRuta = 1;

        while (!pendientes.isEmpty()) {
            final int indice = seleccionarIndice(pendientes, nivelAleatoriedad, random);
            final Maleta maleta = pendientes.remove(indice);
            final Pedido pedido = maleta.getPedido();
            final List<VueloInstancia> camino = rutearConCapacidad(
                    pedido,
                    pedido.getFechaRegistro(),
                    pedido.getFechaHoraPlazo(),
                    grafo,
                    params,
                    new HashSet<>(),
                    consumoVuelo,
                    consumoAeropuerto,
                    aeropuertos
            );

            final Ruta ruta = new Ruta();
            ruta.setIdRuta(String.format("R%08d", secuenciaRuta++));
            ruta.setIdMaleta(maleta.getIdMaleta());
            ruta.setPlazoMaximoDias(Ruta.calcularPlazo(pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino()));
            if (camino == null) {
                ruta.setSubrutas(new ArrayList<>());
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0.0);
            } else {
                ruta.setSubrutas(new ArrayList<>(camino));
                ruta.setEstado(camino.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
                ruta.setDuracion(duracionHoras(camino));
                for (final VueloInstancia vueloInstancia : camino) {
                    consumoVuelo.merge(vueloInstancia.getIdVueloInstancia(), 1, Integer::sum);
                    if (vueloInstancia.getAeropuertoOrigen() != null
                            && vueloInstancia.getAeropuertoOrigen().getIdAeropuerto() != null) {
                        consumoAeropuerto.merge(vueloInstancia.getAeropuertoOrigen().getIdAeropuerto(), 1, Integer::sum);
                    }
                    if (vueloInstancia.getAeropuertoDestino() != null
                            && vueloInstancia.getAeropuertoDestino().getIdAeropuerto() != null) {
                        consumoAeropuerto.merge(vueloInstancia.getAeropuertoDestino().getIdAeropuerto(), 1, Integer::sum);
                    }
                }
            }
            rutas.add(ruta);
        }

        final Solucion solucion = new Solucion(rutas);
        solucion.calcularMetricas();
        return solucion;
    }

    private static List<VueloInstancia> rutearConCapacidad(final Pedido pedido, final LocalDateTime tListo,
                                                           final LocalDateTime tLimite,
                                                           final GrafoTiempoExpandido grafo,
                                                           final ParametrosGA params,
                                                           final Set<String> bloqueados,
                                                           final Map<String, Integer> consumo,
                                                           final Map<String, Integer> consumoAeropuerto,
                                                           final Map<String, Aeropuerto> aeropuertos) {
        for (int intento = 0; intento < 12; intento++) {
            final List<VueloInstancia> camino = GARuteadorCache.rutear(
                    pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino(),
                    tListo, tLimite, grafo, params.getMinutosConexion(), bloqueados, consumo);
            if (camino == null) {
                return null;
            }
            String vueloSaturado = null;
            for (final VueloInstancia v : camino) {
                final int usados = consumo.getOrDefault(v.getIdVueloInstancia(), 0);
                if (usados >= v.getCapacidadDisponible()) {
                    vueloSaturado = v.getIdVueloInstancia();
                    break;
                }
            }
            if (vueloSaturado == null) {
                final String idAeropuertoSaturado = aeropuertoSaturado(camino, consumoAeropuerto, aeropuertos);
                if (idAeropuertoSaturado == null) {
                    return camino;
                }
                bloqueados.add(idAeropuertoSaturado);
                continue;
            }
            bloqueados.add(vueloSaturado);
        }
        return GARuteadorCache.rutear(
                pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino(),
                tListo, tLimite, grafo, params.getMinutosConexion(), bloqueados, consumo);
    }

    private static String aeropuertoSaturado(final List<VueloInstancia> camino,
                                              final Map<String, Integer> consumoAeropuerto,
                                              final Map<String, Aeropuerto> aeropuertos) {
        for (final VueloInstancia v : camino) {
            final String idOrigen = v.getAeropuertoOrigen() != null
                    ? v.getAeropuertoOrigen().getIdAeropuerto() : null;
            final String idDestino = v.getAeropuertoDestino() != null
                    ? v.getAeropuertoDestino().getIdAeropuerto() : null;
            for (final String id : new String[]{idOrigen, idDestino}) {
                if (id == null) {
                    continue;
                }
                final Aeropuerto ap = aeropuertos.get(id);
                if (ap == null || ap.getCapacidadAlmacen() <= 0) {
                    continue;
                }
                if (consumoAeropuerto.getOrDefault(id, 0) >= ap.getCapacidadAlmacen()) {
                    return id;
                }
            }
        }
        return null;
    }

    private static int seleccionarIndice(final List<Maleta> pendientes, final double nivelAleatoriedad,
                                         final Random random) {
        if (nivelAleatoriedad <= 0.0 || pendientes.size() == 1) {
            return 0;
        }
        final int ventana = Math.max(1, (int) Math.ceil(pendientes.size() * nivelAleatoriedad));
        return random.nextInt(Math.min(ventana, pendientes.size()));
    }

    private static LocalDateTime clavePorUrgencia(final Maleta maleta) {
        final Pedido pedido = maleta.getPedido();
        if (pedido != null && pedido.getFechaHoraPlazo() != null) {
            return pedido.getFechaHoraPlazo();
        }
        if (pedido != null && pedido.getFechaRegistro() != null) {
            return pedido.getFechaRegistro();
        }
        return LocalDateTime.MAX;
    }

    private static double indiceDificultad(final Maleta maleta) {
        if (maleta == null || maleta.getPedido() == null) {
            return Double.NEGATIVE_INFINITY;
        }
        final Pedido pedido = maleta.getPedido();
        double puntaje = 0.0;
        final LocalDateTime fechaRegistro = pedido.getFechaRegistro();
        final LocalDateTime plazo = pedido.getFechaHoraPlazo();
        if (fechaRegistro != null && plazo != null) {
            puntaje += 10_000D - Math.max(0L, java.time.Duration.between(fechaRegistro, plazo).toMinutes());
        }
        final Aeropuerto origen = pedido.getAeropuertoOrigen();
        final Aeropuerto destino = pedido.getAeropuertoDestino();
        if (origen != null && destino != null && origen.getCiudad() != null && destino.getCiudad() != null
                && origen.getCiudad().getContinente() != destino.getCiudad().getContinente()) {
            puntaje += 500D;
        }
        return puntaje;
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
