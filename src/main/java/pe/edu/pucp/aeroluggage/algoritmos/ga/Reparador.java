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
        rescatarRutasNoResueltas(solucion, instancia, params, random);
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
            final int capacidadDisponible = Math.max(0, vuelo.getCapacidadDisponible());
            final int exceso = usan.size() - capacidadDisponible;
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
        final Map<String, Integer> consumoVuelo = new HashMap<>();
        final Map<String, Integer> consumoAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();
        final Set<String> presentes = new HashSet<>();
        if (solucion.getSolucion() != null) {
            for (final Ruta ruta : solucion.getSolucion()) {
                if (ruta != null && ruta.getIdMaleta() != null) {
                    presentes.add(ruta.getIdMaleta());
                }
                acumularConsumoRuta(ruta, consumoVuelo, consumoAeropuerto);
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
                final List<VueloInstancia> camino = rutearConCapacidadDisponible(
                        pedido,
                        grafo,
                        instancia.getMinutosConexion(),
                        instancia.getTiempoRecojo(),
                        consumoVuelo,
                        consumoAeropuerto,
                        aeropuertos
                );
                if (camino == null) {
                    ruta.setSubrutas(new ArrayList<>());
                    ruta.setEstado(EstadoRuta.FALLIDA);
                    ruta.setDuracion(0.0);
                } else {
                    ruta.setSubrutas(new ArrayList<>(camino));
                    ruta.setEstado(camino.isEmpty() ? EstadoRuta.FALLIDA : EstadoRuta.PLANIFICADA);
                    ruta.setDuracion(duracionHoras(camino));
                    acumularConsumoRuta(ruta, consumoVuelo, consumoAeropuerto);
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

    private static List<VueloInstancia> rutearConCapacidadDisponible(final Pedido pedido,
                                                                      final GrafoTiempoExpandido grafo,
                                                                      final long minutosConexion,
                                                                      final long tiempoRecojo,
                                                                      final Map<String, Integer> consumoVuelo,
                                                                      final Map<String, Integer> consumoAeropuerto,
                                                                      final Map<String, Aeropuerto> aeropuertos) {
        final Set<String> bloqueados = new HashSet<>();
        final LocalDateTime tLimite = plazoEfectivo(pedido.getFechaHoraPlazo(), tiempoRecojo);
        for (int intento = 0; intento < 4; intento++) {
            final List<VueloInstancia> camino = GARuteadorCache.rutear(
                    pedido.getAeropuertoOrigen(),
                    pedido.getAeropuertoDestino(),
                    pedido.getFechaRegistro(),
                    tLimite,
                    grafo,
                    minutosConexion,
                    bloqueados,
                    consumoVuelo
            );
            if (camino == null) {
                return null;
            }
            final String vueloSaturado = encontrarVueloSaturado(camino, consumoVuelo);
            if (vueloSaturado != null) {
                bloqueados.add(vueloSaturado);
                continue;
            }
            final String aeropuertoSaturado = encontrarAeropuertoSaturado(camino, consumoAeropuerto, aeropuertos);
            if (aeropuertoSaturado != null) {
                bloqueados.add(aeropuertoSaturado);
                continue;
            }
            return camino;
        }
        return GARuteadorCache.rutear(
                pedido.getAeropuertoOrigen(),
                pedido.getAeropuertoDestino(),
                pedido.getFechaRegistro(),
                tLimite,
                grafo,
                minutosConexion,
                bloqueados,
                consumoVuelo
        );
    }

    private static String encontrarVueloSaturado(final List<VueloInstancia> camino,
                                                 final Map<String, Integer> consumoVuelo) {
        for (final VueloInstancia vueloInstancia : camino) {
            if (vueloInstancia == null || vueloInstancia.getIdVueloInstancia() == null) {
                continue;
            }
            final int usos = consumoVuelo.getOrDefault(vueloInstancia.getIdVueloInstancia(), 0);
            if (usos >= vueloInstancia.getCapacidadDisponible()) {
                return vueloInstancia.getIdVueloInstancia();
            }
        }
        return null;
    }

    private static String encontrarAeropuertoSaturado(final List<VueloInstancia> camino,
                                                      final Map<String, Integer> consumoAeropuerto,
                                                      final Map<String, Aeropuerto> aeropuertos) {
        for (final VueloInstancia vueloInstancia : camino) {
            if (vueloInstancia == null) {
                continue;
            }
            final String idOrigen = vueloInstancia.getAeropuertoOrigen() != null
                    ? vueloInstancia.getAeropuertoOrigen().getIdAeropuerto()
                    : null;
            final String idDestino = vueloInstancia.getAeropuertoDestino() != null
                    ? vueloInstancia.getAeropuertoDestino().getIdAeropuerto()
                    : null;
            for (final String idAeropuerto : new String[]{idOrigen, idDestino}) {
                if (idAeropuerto == null) {
                    continue;
                }
                final Aeropuerto aeropuerto = aeropuertos.get(idAeropuerto);
                if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                    continue;
                }
                final int carga = consumoAeropuerto.getOrDefault(idAeropuerto, 0);
                if (carga >= aeropuerto.getCapacidadAlmacen()) {
                    return idAeropuerto;
                }
            }
        }
        return null;
    }

    private static void acumularConsumoRuta(final Ruta ruta,
                                            final Map<String, Integer> consumoVuelo,
                                            final Map<String, Integer> consumoAeropuerto) {
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            return;
        }
        for (final VueloInstancia vueloInstancia : ruta.getSubrutas()) {
            if (vueloInstancia == null) {
                continue;
            }
            if (vueloInstancia.getIdVueloInstancia() != null) {
                consumoVuelo.merge(vueloInstancia.getIdVueloInstancia(), 1, Integer::sum);
            }
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

    private static void rescatarRutasNoResueltas(final Solucion solucion, final InstanciaProblema instancia,
                                                 final ParametrosGA params, final Random random) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()
                || instancia == null || instancia.getGrafo() == null) {
            return;
        }
        final Map<String, Maleta> maletas = indexarMaletas(instancia);
        final Map<String, Integer> consumoVuelo = new HashMap<>();
        final Map<String, Integer> consumoAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();

        for (final Ruta ruta : solucion.getSolucion()) {
            acumularConsumoRuta(ruta, consumoVuelo, consumoAeropuerto);
        }

        final List<Ruta> candidatas = new ArrayList<>();
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null) {
                continue;
            }
            final Maleta maleta = maletas.get(ruta.getIdMaleta());
            if (requiereRescate(ruta, maleta)) {
                candidatas.add(ruta);
            }
        }
        candidatas.sort(Comparator.comparing(ruta -> claveUrgencia(maletas.get(ruta.getIdMaleta()))));

        for (final Ruta ruta : candidatas) {
            final Maleta maleta = maletas.get(ruta.getIdMaleta());
            if (maleta == null || maleta.getPedido() == null) {
                continue;
            }
            liberarConsumoRuta(ruta, consumoVuelo, consumoAeropuerto);
            final List<VueloInstancia> mejorCamino = construirMejorRutaRescate(
                    ruta,
                    maleta,
                    instancia,
                    params,
                    consumoVuelo,
                    consumoAeropuerto,
                    aeropuertos
            );
            if (mejorCamino == null || mejorCamino.isEmpty()) {
                acumularConsumoRuta(ruta, consumoVuelo, consumoAeropuerto);
                continue;
            }
            ruta.setSubrutas(new ArrayList<>(mejorCamino));
            ruta.setEstado(EstadoRuta.PLANIFICADA);
            ruta.setDuracion(duracionHoras(mejorCamino));
            acumularConsumoRuta(ruta, consumoVuelo, consumoAeropuerto);
        }
    }

    private static List<VueloInstancia> construirMejorRutaRescate(final Ruta ruta,
                                                                  final Maleta maleta,
                                                                  final InstanciaProblema instancia,
                                                                  final ParametrosGA params,
                                                                  final Map<String, Integer> consumoVuelo,
                                                                  final Map<String, Integer> consumoAeropuerto,
                                                                  final Map<String, Aeropuerto> aeropuertos) {
        final Pedido pedido = maleta.getPedido();
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        final List<List<VueloInstancia>> candidatos = new ArrayList<>();
        final List<VueloInstancia> rutaDesdeOrigen = rutearConCapacidadDisponible(
                pedido,
                grafo,
                instancia.getMinutosConexion(),
                instancia.getTiempoRecojo(),
                consumoVuelo,
                consumoAeropuerto,
                aeropuertos
        );
        agregarCaminoSiCompleto(candidatos, rutaDesdeOrigen, pedido);

        final List<VueloInstancia> subrutasActuales = ruta.getSubrutas();
        if (subrutasActuales != null && !subrutasActuales.isEmpty()) {
            for (int i = subrutasActuales.size() - 1; i >= 0; i--) {
                final VueloInstancia ultimoTramoValido = subrutasActuales.get(i);
                if (ultimoTramoValido == null || ultimoTramoValido.getAeropuertoDestino() == null
                        || ultimoTramoValido.getFechaLlegada() == null) {
                    continue;
                }
                final Pedido pedidoRescate = new Pedido(
                        pedido.getIdPedido(),
                        ultimoTramoValido.getAeropuertoDestino(),
                        pedido.getAeropuertoDestino(),
                        ultimoTramoValido.getFechaLlegada(),
                        pedido.getFechaHoraPlazo(),
                        pedido.getCantidadMaletas(),
                        pedido.getEstado()
                );
                final List<VueloInstancia> sufijo = rutearConCapacidadDisponible(
                        pedidoRescate,
                        grafo,
                        instancia.getMinutosConexion(),
                        instancia.getTiempoRecojo(),
                        consumoVuelo,
                        consumoAeropuerto,
                        aeropuertos
                );
                if (sufijo == null || sufijo.isEmpty()) {
                    continue;
                }
                final List<VueloInstancia> combinado = new ArrayList<>(subrutasActuales.subList(0, i + 1));
                combinado.addAll(sufijo);
                agregarCaminoSiCompleto(candidatos, combinado, pedido);
            }
        }

        List<VueloInstancia> mejorCamino = null;
        double mejorDuracion = Double.MAX_VALUE;
        for (final List<VueloInstancia> candidato : candidatos) {
            final double duracion = duracionHoras(candidato);
            if (duracion < mejorDuracion) {
                mejorCamino = candidato;
                mejorDuracion = duracion;
            }
        }
        return mejorCamino;
    }

    private static void agregarCaminoSiCompleto(final List<List<VueloInstancia>> candidatos,
                                                final List<VueloInstancia> camino,
                                                final Pedido pedido) {
        if (camino == null || camino.isEmpty() || pedido == null || pedido.getAeropuertoDestino() == null) {
            return;
        }
        final VueloInstancia ultimoVuelo = camino.get(camino.size() - 1);
        if (ultimoVuelo == null || ultimoVuelo.getAeropuertoDestino() == null) {
            return;
        }
        final String destinoEsperado = pedido.getAeropuertoDestino().getIdAeropuerto();
        final String destinoObtenido = ultimoVuelo.getAeropuertoDestino().getIdAeropuerto();
        if (destinoEsperado == null || !destinoEsperado.equals(destinoObtenido)) {
            return;
        }
        candidatos.add(new ArrayList<>(camino));
    }

    private static boolean requiereRescate(final Ruta ruta, final Maleta maleta) {
        if (ruta == null || maleta == null || maleta.getPedido() == null) {
            return false;
        }
        final List<VueloInstancia> subrutas = ruta.getSubrutas();
        if (ruta.getEstado() == EstadoRuta.FALLIDA || subrutas == null || subrutas.isEmpty()) {
            return true;
        }
        final VueloInstancia ultimoVuelo = subrutas.get(subrutas.size() - 1);
        return ultimoVuelo == null
                || ultimoVuelo.getAeropuertoDestino() == null
                || maleta.getPedido().getAeropuertoDestino() == null
                || maleta.getPedido().getAeropuertoDestino().getIdAeropuerto() == null
                || !maleta.getPedido().getAeropuertoDestino().getIdAeropuerto()
                .equals(ultimoVuelo.getAeropuertoDestino().getIdAeropuerto());
    }

    private static LocalDateTime claveUrgencia(final Maleta maleta) {
        if (maleta == null || maleta.getPedido() == null) {
            return LocalDateTime.MAX;
        }
        final LocalDateTime plazo = maleta.getPedido().getFechaHoraPlazo();
        if (plazo != null) {
            return plazo;
        }
        final LocalDateTime registro = maleta.getPedido().getFechaRegistro();
        return registro != null ? registro : LocalDateTime.MAX;
    }

    private static void liberarConsumoRuta(final Ruta ruta,
                                           final Map<String, Integer> consumoVuelo,
                                           final Map<String, Integer> consumoAeropuerto) {
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            return;
        }
        for (final VueloInstancia vueloInstancia : ruta.getSubrutas()) {
            if (vueloInstancia == null) {
                continue;
            }
            decrementarConsumo(consumoVuelo, vueloInstancia.getIdVueloInstancia());
            if (vueloInstancia.getAeropuertoOrigen() != null) {
                decrementarConsumo(consumoAeropuerto, vueloInstancia.getAeropuertoOrigen().getIdAeropuerto());
            }
            if (vueloInstancia.getAeropuertoDestino() != null) {
                decrementarConsumo(consumoAeropuerto, vueloInstancia.getAeropuertoDestino().getIdAeropuerto());
            }
        }
    }

    private static void decrementarConsumo(final Map<String, Integer> consumo, final String id) {
        if (id == null || !consumo.containsKey(id)) {
            return;
        }
        final int nuevoValor = consumo.get(id) - 1;
        if (nuevoValor <= 0) {
            consumo.remove(id);
            return;
        }
        consumo.put(id, nuevoValor);
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
