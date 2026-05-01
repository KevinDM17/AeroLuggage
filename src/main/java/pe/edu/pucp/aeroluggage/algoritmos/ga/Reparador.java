package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.Duration;
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
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class Reparador {

    private Reparador() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void reparar(final Solucion solucion, final InstanciaProblema instancia,
                               final ParametrosGA params, final Random random) {
        if (solucion == null || instancia == null) {
            return;
        }
        eliminarRutasDuplicadas(solucion);
        eliminarRutasInvalidas(solucion, instancia);
        repararCapacidad(solucion, instancia, params, random);
        repararCapacidadAlmacenes(solucion, instancia, params, random);
        insertarMaletasFaltantes(solucion, instancia, params, random);
        recalcularCamposRuta(solucion);
        solucion.calcularMetricas();
    }

    public static void eliminarRutasDuplicadas(final Solucion solucion) {
        if (solucion == null || solucion.getSolucion() == null) {
            return;
        }
        final Set<String> vistos = new HashSet<>();
        final List<Ruta> rutas = solucion.getSolucion();
        final List<Ruta> filtradas = new ArrayList<>(rutas.size());
        for (final Ruta ruta : rutas) {
            if (ruta == null) {
                continue;
            }
            if (ruta.getIdMaleta() == null) {
                filtradas.add(ruta);
                continue;
            }
            if (vistos.add(ruta.getIdMaleta())) {
                filtradas.add(ruta);
            }
        }
        rutas.clear();
        rutas.addAll(filtradas);
    }

    public static void eliminarRutasInvalidas(final Solucion solucion, final InstanciaProblema instancia) {
        if (solucion == null || solucion.getSolucion() == null) {
            return;
        }
        final Map<String, Maleta> maletas = indexarMaletas(instancia);
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null) {
                continue;
            }
            final List<VueloInstancia> subrutas = ruta.getSubrutas();
            if (subrutas == null || subrutas.isEmpty()) {
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0.0);
                continue;
            }
            final Maleta maleta = maletas.get(ruta.getIdMaleta());
            if (maleta == null || maleta.getPedido() == null) {
                ruta.setSubrutas(new ArrayList<>());
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0.0);
                continue;
            }
            final Pedido pedido = maleta.getPedido();
            if (contieneCanceladoONoFactible(subrutas)
                    || !primerVueloCoherente(subrutas, pedido.getAeropuertoOrigen())
                    || !ultimoVueloCoherente(subrutas, pedido.getAeropuertoDestino())
                    || !conexionesCoherentes(subrutas)) {
                ruta.setSubrutas(new ArrayList<>());
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0.0);
            }
        }
    }

    public static void repararCapacidad(final Solucion solucion, final InstanciaProblema instancia,
                                        final ParametrosGA params, final Random random) {
        if (solucion == null || solucion.getSolucion() == null || instancia.getGrafo() == null) {
            return;
        }
        final Map<String, List<Ruta>> rutasPorVuelo = new HashMap<>();
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null) {
                continue;
            }
            for (final VueloInstancia v : ruta.getSubrutas()) {
                if (v == null || v.getIdVueloInstancia() == null) {
                    continue;
                }
                rutasPorVuelo.computeIfAbsent(v.getIdVueloInstancia(), k -> new ArrayList<>()).add(ruta);
            }
        }
        final Map<String, VueloInstancia> vuelos = indexarVuelos(instancia);
        final Map<String, Maleta> maletas = indexarMaletas(instancia);

        for (final Map.Entry<String, List<Ruta>> entry : rutasPorVuelo.entrySet()) {
            final VueloInstancia vuelo = vuelos.get(entry.getKey());
            if (vuelo == null) {
                continue;
            }
            final List<Ruta> usan = entry.getValue();
            final int exceso = usan.size() - vuelo.getCapacidadMaxima();
            if (exceso <= 0) {
                continue;
            }
            usan.sort(Comparator.comparingDouble(Reparador::holguraNegativa));
            for (int i = 0; i < exceso && !usan.isEmpty(); i++) {
                final Ruta victima = usan.get(usan.size() - 1 - i);
                final Maleta maleta = maletas.get(victima.getIdMaleta());
                rerutearBloqueando(victima, maleta, entry.getKey(), instancia.getGrafo(),
                        instancia.getMinutosConexion(), instancia.getTiempoRecojo());
            }
        }
    }

    public static void insertarMaletasFaltantes(final Solucion solucion, final InstanciaProblema instancia,
                                                final ParametrosGA params, final Random random) {
        if (solucion == null || instancia == null || instancia.getMaletas() == null) {
            return;
        }
        final Set<String> presentes = new HashSet<>();
        if (solucion.getSolucion() != null) {
            for (final Ruta ruta : solucion.getSolucion()) {
                if (ruta != null && ruta.getIdMaleta() != null) {
                    presentes.add(ruta.getIdMaleta());
                }
            }
        } else {
            solucion.setSolucion(new ArrayList<>());
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        int secuencia = solucion.getSolucion().size() + 1;
        for (final Maleta maleta : instancia.getMaletas()) {
            if (maleta == null || maleta.getIdMaleta() == null || maleta.getPedido() == null) {
                continue;
            }
            if (presentes.contains(maleta.getIdMaleta())) {
                continue;
            }
            final Pedido pedido = maleta.getPedido();
            final Ruta ruta = new Ruta();
            ruta.setIdRuta(String.format("R%08d", secuencia++));
            ruta.setIdMaleta(maleta.getIdMaleta());
            ruta.setPlazoMaximoDias(Ruta.calcularPlazo(pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino()));
            if (grafo == null) {
                ruta.setSubrutas(new ArrayList<>());
                ruta.setEstado(EstadoRuta.FALLIDA);
                ruta.setDuracion(0.0);
            } else {
                final List<VueloInstancia> camino = GARuteadorCache.rutear(
                        pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino(),
                        pedido.getFechaRegistro(),
                        plazoEfectivo(pedido.getFechaHoraPlazo(), instancia.getTiempoRecojo()),
                        grafo, instancia.getMinutosConexion(), new HashSet<>());
                if (camino == null) {
                    ruta.setSubrutas(new ArrayList<>());
                    ruta.setEstado(EstadoRuta.FALLIDA);
                    ruta.setDuracion(0.0);
                } else {
                    ruta.setSubrutas(new ArrayList<>(camino));
                    ruta.setEstado(camino.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
                    ruta.setDuracion(duracionHoras(camino));
                }
            }
            solucion.getSolucion().add(ruta);
        }
    }

    public static void recalcularCamposRuta(final Solucion solucion) {
        if (solucion == null || solucion.getSolucion() == null) {
            return;
        }
        int secuencia = 1;
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null) {
                continue;
            }
            ruta.setIdRuta(String.format("R%08d", secuencia++));
            final List<VueloInstancia> subrutas = ruta.getSubrutas();
            if (subrutas == null || subrutas.isEmpty()) {
                ruta.setDuracion(0.0);
                ruta.setEstado(EstadoRuta.FALLIDA);
            } else {
                ruta.setDuracion(duracionHoras(subrutas));
                if (ruta.getEstado() == null) {
                    ruta.setEstado(EstadoRuta.PLANIFICADA);
                }
            }
        }
    }

    private static void rerutearBloqueando(final Ruta ruta, final Maleta maleta, final String idVueloBloqueado,
                                           final GrafoTiempoExpandido grafo,
                                           final long minutosConexion, final long tiempoRecojo) {
        if (maleta == null || maleta.getPedido() == null) {
            ruta.setSubrutas(new ArrayList<>());
            ruta.setEstado(EstadoRuta.FALLIDA);
            ruta.setDuracion(0.0);
            return;
        }
        final Pedido pedido = maleta.getPedido();
        final Set<String> bloqueados = new HashSet<>();
        bloqueados.add(idVueloBloqueado);
        final List<VueloInstancia> nuevo = GARuteadorCache.rutear(
                pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino(),
                pedido.getFechaRegistro(), plazoEfectivo(pedido.getFechaHoraPlazo(), tiempoRecojo),
                grafo, minutosConexion, bloqueados);
        if (nuevo == null) {
            ruta.setSubrutas(new ArrayList<>());
            ruta.setEstado(EstadoRuta.FALLIDA);
            ruta.setDuracion(0.0);
            return;
        }
        ruta.setSubrutas(new ArrayList<>(nuevo));
        ruta.setEstado(nuevo.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
        ruta.setDuracion(duracionHoras(nuevo));
    }

    private static double holguraNegativa(final Ruta ruta) {
        if (ruta == null) {
            return Double.NEGATIVE_INFINITY;
        }
        return -ruta.getDuracion();
    }

    private static boolean contieneCanceladoONoFactible(final List<VueloInstancia> subrutas) {
        for (final VueloInstancia v : subrutas) {
            if (v == null || v.getEstado() == EstadoVuelo.CANCELADO) {
                return true;
            }
        }
        return false;
    }

    private static boolean primerVueloCoherente(final List<VueloInstancia> subrutas, final Aeropuerto origen) {
        if (subrutas.isEmpty() || origen == null) {
            return false;
        }
        final Aeropuerto inicio = subrutas.get(0).getAeropuertoOrigen();
        return inicio != null && inicio.getIdAeropuerto() != null
                && inicio.getIdAeropuerto().equals(origen.getIdAeropuerto());
    }

    private static boolean ultimoVueloCoherente(final List<VueloInstancia> subrutas, final Aeropuerto destino) {
        if (subrutas.isEmpty() || destino == null) {
            return false;
        }
        final Aeropuerto fin = subrutas.get(subrutas.size() - 1).getAeropuertoDestino();
        return fin != null && fin.getIdAeropuerto() != null
                && fin.getIdAeropuerto().equals(destino.getIdAeropuerto());
    }

    private static boolean conexionesCoherentes(final List<VueloInstancia> subrutas) {
        for (int i = 0; i < subrutas.size() - 1; i++) {
            final VueloInstancia actual = subrutas.get(i);
            final VueloInstancia siguiente = subrutas.get(i + 1);
            if (actual == null || siguiente == null) {
                return false;
            }
            if (actual.getAeropuertoDestino() == null || siguiente.getAeropuertoOrigen() == null) {
                return false;
            }
            if (!actual.getAeropuertoDestino().getIdAeropuerto()
                    .equals(siguiente.getAeropuertoOrigen().getIdAeropuerto())) {
                return false;
            }
            if (actual.getFechaLlegada() != null && siguiente.getFechaSalida() != null
                    && actual.getFechaLlegada().isAfter(siguiente.getFechaSalida())) {
                return false;
            }
        }
        return true;
    }

    private static LocalDateTime plazoEfectivo(final LocalDateTime plazo, final long tiempoRecojo) {
        if (plazo == null || tiempoRecojo <= 0) {
            return plazo;
        }
        return plazo.minusMinutes(tiempoRecojo);
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
        return Duration.between(inicio, fin).toMinutes() / 60.0;
    }

    public static void repararCapacidadAlmacenes(final Solucion solucion, final InstanciaProblema instancia,
                                             final ParametrosGA params, final Random random) {
        if (solucion == null || solucion.getSolucion() == null || instancia.getGrafo() == null) {
            return;
        }
        final Map<String, Integer> cargaPorAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();

        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null) {
                continue;
            }
            for (final VueloInstancia v : ruta.getSubrutas()) {
                if (v == null) {
                    continue;
                }
                if (v.getAeropuertoOrigen() != null && v.getAeropuertoOrigen().getIdAeropuerto() != null) {
                    cargaPorAeropuerto.merge(v.getAeropuertoOrigen().getIdAeropuerto(), 1, Integer::sum);
                }
                if (v.getAeropuertoDestino() != null && v.getAeropuertoDestino().getIdAeropuerto() != null) {
                    cargaPorAeropuerto.merge(v.getAeropuertoDestino().getIdAeropuerto(), 1, Integer::sum);
                }
            }
        }

        final Map<String, List<Ruta>> rutasPorAeropuerto = new HashMap<>();
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null) {
                continue;
            }
            for (final VueloInstancia v : ruta.getSubrutas()) {
                if (v == null) {
                    continue;
                }
                final String idOrigen = v.getAeropuertoOrigen() != null
                        ? v.getAeropuertoOrigen().getIdAeropuerto() : null;
                final String idDestino = v.getAeropuertoDestino() != null
                        ? v.getAeropuertoDestino().getIdAeropuerto() : null;
                if (idOrigen != null) {
                    rutasPorAeropuerto.computeIfAbsent(idOrigen, k -> new ArrayList<>()).add(ruta);
                }
                if (idDestino != null && !idDestino.equals(idOrigen)) {
                    rutasPorAeropuerto.computeIfAbsent(idDestino, k -> new ArrayList<>()).add(ruta);
                }
            }
        }

        final Map<String, Maleta> maletas = indexarMaletas(instancia);
        for (final Map.Entry<String, Integer> entry : cargaPorAeropuerto.entrySet()) {
            final String idAeropuerto = entry.getKey();
            final Aeropuerto aeropuerto = aeropuertos.get(idAeropuerto);
            if (aeropuerto == null) {
                continue;
            }
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            if (capacidad <= 0) {
                continue;
            }
            final int carga = entry.getValue();
            final int exceso = carga - capacidad;
            if (exceso <= 0) {
                continue;
            }
            final List<Ruta> rutasEnAeropuerto = rutasPorAeropuerto.get(idAeropuerto);
            if (rutasEnAeropuerto == null || rutasEnAeropuerto.isEmpty()) {
                continue;
            }
            rutasEnAeropuerto.sort(Comparator.comparingDouble(Reparador::holguraNegativa));
            for (int i = 0; i < exceso && !rutasEnAeropuerto.isEmpty(); i++) {
                final Ruta victima = rutasEnAeropuerto.get(rutasEnAeropuerto.size() - 1 - i);
                rerutearBloqueandoAeropuerto(victima, maletas.get(victima.getIdMaleta()),
                        idAeropuerto, instancia.getGrafo(),
                        instancia.getMinutosConexion(), instancia.getTiempoRecojo());
            }
        }
    }

    private static void rerutearBloqueandoAeropuerto(final Ruta ruta,
                                                     final Maleta maleta,
                                                     final String idAeropuertoBloqueado,
                                                     final GrafoTiempoExpandido grafo,
                                                     final long minutosConexion,
                                                     final long tiempoRecojo) {
        if (maleta == null || maleta.getPedido() == null) {
            ruta.setSubrutas(new ArrayList<>());
            ruta.setEstado(EstadoRuta.FALLIDA);
            ruta.setDuracion(0.0);
            return;
        }
        final Pedido pedido = maleta.getPedido();
        final Set<String> bloqueados = new HashSet<>();
        bloqueados.add(idAeropuertoBloqueado);
        final List<VueloInstancia> nuevo = GARuteadorCache.rutear(
                pedido.getAeropuertoOrigen(), pedido.getAeropuertoDestino(),
                pedido.getFechaRegistro(), plazoEfectivo(pedido.getFechaHoraPlazo(), tiempoRecojo),
                grafo, minutosConexion, bloqueados);
        if (nuevo == null) {
            ruta.setSubrutas(new ArrayList<>());
            ruta.setEstado(EstadoRuta.FALLIDA);
            ruta.setDuracion(0.0);
            return;
        }
        ruta.setSubrutas(new ArrayList<>(nuevo));
        ruta.setEstado(nuevo.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
        ruta.setDuracion(duracionHoras(nuevo));
    }

    private static Map<String, Maleta> indexarMaletas(final InstanciaProblema instancia) {
        final Map<String, Maleta> indice = new HashMap<>();
        if (instancia == null || instancia.getMaletas() == null) {
            return indice;
        }
        for (final Maleta m : instancia.getMaletas()) {
            if (m != null && m.getIdMaleta() != null) {
                indice.put(m.getIdMaleta(), m);
            }
        }
        return indice;
    }

    private static Map<String, VueloInstancia> indexarVuelos(final InstanciaProblema instancia) {
        final Map<String, VueloInstancia> indice = new HashMap<>();
        if (instancia == null || instancia.getVueloInstancias() == null) {
            return indice;
        }
        for (final VueloInstancia v : instancia.getVueloInstancias()) {
            if (v != null && v.getIdVueloInstancia() != null) {
                indice.put(v.getIdVueloInstancia(), v);
            }
        }
        return indice;
    }
}
