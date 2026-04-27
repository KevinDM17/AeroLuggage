package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import pe.edu.pucp.aeroluggage.algoritmos.common.DijkstraRuteador;
import pe.edu.pucp.aeroluggage.algoritmos.common.GrafoTiempoExpandido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

final class GARuteadorCache {
    private static final int MAX_ENTRADAS_CACHE = 20000;
    private static final int MAX_ALTERNATIVAS_RUTA = 4;
    private static final int MAX_INTENTOS_ALTERNATIVAS = 32;
    private static final long PENALIZACION_USO_VUELO_MINUTOS = 180L;
    private static final Map<String, List<List<VueloInstancia>>> RUTAS = Collections.synchronizedMap(
            new LinkedHashMap<String, List<List<VueloInstancia>>>(MAX_ENTRADAS_CACHE, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(final Map.Entry<String, List<List<VueloInstancia>>> eldest) {
                    return size() > MAX_ENTRADAS_CACHE;
                }
            }
    );

    private GARuteadorCache() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    static List<VueloInstancia> rutear(final Aeropuerto origen,
                                       final Aeropuerto destino,
                                       final LocalDateTime tListo,
                                       final LocalDateTime tLimite,
                                       final GrafoTiempoExpandido grafo,
                                       final long minutosConexion,
                                       final Set<String> vuelosBloqueados) {
        return rutear(origen, destino, tListo, tLimite, grafo, minutosConexion, vuelosBloqueados, Map.of());
    }

    static List<VueloInstancia> rutear(final Aeropuerto origen,
                                       final Aeropuerto destino,
                                       final LocalDateTime tListo,
                                       final LocalDateTime tLimite,
                                       final GrafoTiempoExpandido grafo,
                                       final long minutosConexion,
                                       final Set<String> vuelosBloqueados,
                                       final Map<String, Integer> consumoVuelo) {
        final String clave = construirClave(origen, destino, tListo, tLimite, grafo, minutosConexion, vuelosBloqueados);
        final List<List<VueloInstancia>> rutasCacheadas = RUTAS.get(clave);
        if (rutasCacheadas != null) {
            return seleccionarMejorRuta(rutasCacheadas, tListo, consumoVuelo);
        }

        final List<List<VueloInstancia>> rutas = construirAlternativas(
                origen,
                destino,
                tListo,
                tLimite,
                grafo,
                minutosConexion,
                vuelosBloqueados
        );
        RUTAS.put(clave, copiarRutasInmutables(rutas));
        return seleccionarMejorRuta(rutas, tListo, consumoVuelo);
    }

    private static List<List<VueloInstancia>> construirAlternativas(final Aeropuerto origen,
                                                                    final Aeropuerto destino,
                                                                    final LocalDateTime tListo,
                                                                    final LocalDateTime tLimite,
                                                                    final GrafoTiempoExpandido grafo,
                                                                    final long minutosConexion,
                                                                    final Set<String> vuelosBloqueados) {
        final List<List<VueloInstancia>> rutas = new ArrayList<>();
        final Set<String> firmas = new HashSet<>();
        final Set<String> bloqueadosBase = vuelosBloqueados == null
                ? Set.of()
                : new HashSet<>(vuelosBloqueados);

        final List<VueloInstancia> rutaInicial = DijkstraRuteador.rutear(
                origen, destino, tListo, tLimite, grafo, minutosConexion, bloqueadosBase);
        agregarRutaSiEsNueva(rutas, firmas, rutaInicial);

        int indiceRuta = 0;
        int intentos = 0;
        while (indiceRuta < rutas.size()
                && rutas.size() < MAX_ALTERNATIVAS_RUTA
                && intentos < MAX_INTENTOS_ALTERNATIVAS) {
            final List<VueloInstancia> rutaBase = rutas.get(indiceRuta);
            for (final VueloInstancia vuelo : rutaBase) {
                if (rutas.size() >= MAX_ALTERNATIVAS_RUTA || intentos >= MAX_INTENTOS_ALTERNATIVAS) {
                    break;
                }
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                intentos++;
                final Set<String> bloqueadosAlternativa = new HashSet<>(bloqueadosBase);
                bloqueadosAlternativa.add(vuelo.getIdVueloInstancia());
                final List<VueloInstancia> alternativa = DijkstraRuteador.rutear(
                        origen, destino, tListo, tLimite, grafo, minutosConexion, bloqueadosAlternativa);
                agregarRutaSiEsNueva(rutas, firmas, alternativa);
            }
            indiceRuta++;
        }

        return rutas;
    }

    private static void agregarRutaSiEsNueva(final List<List<VueloInstancia>> rutas,
                                             final Set<String> firmas,
                                             final List<VueloInstancia> ruta) {
        if (ruta == null) {
            return;
        }
        final String firma = firmarRuta(ruta);
        if (!firmas.add(firma)) {
            return;
        }
        rutas.add(List.copyOf(ruta));
    }

    private static List<List<VueloInstancia>> copiarRutasInmutables(final List<List<VueloInstancia>> rutas) {
        if (rutas == null || rutas.isEmpty()) {
            return List.of();
        }
        final List<List<VueloInstancia>> copia = new ArrayList<>(rutas.size());
        for (final List<VueloInstancia> ruta : rutas) {
            copia.add(List.copyOf(ruta));
        }
        return List.copyOf(copia);
    }

    private static List<VueloInstancia> seleccionarMejorRuta(final List<List<VueloInstancia>> rutas,
                                                             final LocalDateTime tListo,
                                                             final Map<String, Integer> consumoVuelo) {
        if (rutas == null || rutas.isEmpty()) {
            return null;
        }
        List<VueloInstancia> mejorRuta = null;
        long mejorCosto = Long.MAX_VALUE;

        for (final List<VueloInstancia> ruta : rutas) {
            final long costo = calcularCostoDinamico(ruta, tListo, consumoVuelo);
            if (mejorRuta != null && costo >= mejorCosto) {
                continue;
            }
            mejorRuta = ruta;
            mejorCosto = costo;
        }

        return mejorRuta == null ? null : new ArrayList<>(mejorRuta);
    }

    private static long calcularCostoDinamico(final List<VueloInstancia> ruta,
                                              final LocalDateTime tListo,
                                              final Map<String, Integer> consumoVuelo) {
        if (ruta == null || ruta.isEmpty()) {
            return 0L;
        }
        final VueloInstancia ultimoVuelo = ruta.get(ruta.size() - 1);
        final LocalDateTime llegada = ultimoVuelo.getFechaLlegada();
        final long tiempoRuta = tListo == null || llegada == null
                ? 0L
                : Math.max(0L, Duration.between(tListo, llegada).toMinutes());
        long penalizacionUso = 0L;
        for (final VueloInstancia vuelo : ruta) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            final int usos = consumoVuelo == null ? 0 : consumoVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
            penalizacionUso += usos * PENALIZACION_USO_VUELO_MINUTOS;
        }
        return tiempoRuta + penalizacionUso;
    }

    private static String firmarRuta(final List<VueloInstancia> ruta) {
        if (ruta == null || ruta.isEmpty()) {
            return "";
        }
        final List<String> ids = new ArrayList<>(ruta.size());
        for (final VueloInstancia vuelo : ruta) {
            ids.add(vuelo == null || vuelo.getIdVueloInstancia() == null ? "" : vuelo.getIdVueloInstancia());
        }
        return String.join(",", ids);
    }

    private static String construirClave(final Aeropuerto origen,
                                         final Aeropuerto destino,
                                         final LocalDateTime tListo,
                                         final LocalDateTime tLimite,
                                         final GrafoTiempoExpandido grafo,
                                         final long minutosConexion,
                                         final Set<String> vuelosBloqueados) {
        return idAeropuerto(origen)
                + '|'
                + idAeropuerto(destino)
                + '|'
                + tListo
                + '|'
                + tLimite
                + '|'
                + System.identityHashCode(grafo)
                + '|'
                + minutosConexion
                + '|'
                + normalizarBloqueados(vuelosBloqueados);
    }

    private static String normalizarBloqueados(final Set<String> vuelosBloqueados) {
        if (vuelosBloqueados == null || vuelosBloqueados.isEmpty()) {
            return "";
        }
        return String.join(",", new TreeSet<>(vuelosBloqueados));
    }

    private static String idAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto == null ? "" : aeropuerto.getIdAeropuerto();
    }
}
