package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
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

public final class GAConstructorTemporal {

    private static final int MAX_ESCALAS_RUTA = 6;
    private static final int MAX_CANDIDATOS_POR_MALETA = 6;
    private static final int MAX_EXPANSIONES_BUSQUEDA = 800;
    private static final int MAX_ESTADOS_REOPTIMIZACION = 2000;
    private static final String FASE_CONSTRUCCION = "CONSTRUCCION_TEMPORAL";
    private static final String FASE_REOPTIMIZACION = "REOPTIMIZACION_GRUPO";
    private static final String FASE_MUTACION = "MUTACION_GRUPO";

    private GAConstructorTemporal() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static Solucion construir(final InstanciaProblema instancia,
                                     final ParametrosGA params,
                                     final Random random,
                                     final double nivelAleatoriedad,
                                     final boolean priorizarDificiles) {
        if (instancia == null || instancia.getMaletas() == null || instancia.getMaletas().isEmpty()) {
            return new Solucion();
        }
        final GrafoTiempoExpandido grafo = instancia.getGrafo();
        if (grafo == null) {
            return new Solucion();
        }
        final ContextoCapacitado contexto = crearContextoCapacitado(instancia);
        final List<GrupoConflicto> grupos = agruparMaletas(instancia.getMaletas(), priorizarDificiles);
        final ArrayList<Ruta> rutas = new ArrayList<>(instancia.getMaletas().size());
        int secuencia = 1;

        while (!grupos.isEmpty()) {
            final int indice = seleccionarIndice(grupos.size(), nivelAleatoriedad, random);
            final GrupoConflicto grupo = grupos.remove(indice);
            final ResultadoAsignacion resultado = resolverGrupo(
                    grupo.maletas(),
                    contexto,
                    instancia,
                    random,
                    FASE_CONSTRUCCION,
                    MAX_ESTADOS_REOPTIMIZACION
            );
            secuencia = materializarAsignacion(resultado, grupo, rutas, secuencia);
        }

        final Solucion solucion = new Solucion(rutas);
        solucion.calcularMetricas();
        return solucion;
    }

    public static void reoptimizarGruposConflictivos(final Solucion solucion,
                                                     final InstanciaProblema instancia,
                                                     final ParametrosGA params,
                                                     final Random random) {
        if (solucion == null || solucion.getSolucion() == null || instancia == null) {
            return;
        }
        final Map<String, Maleta> maletasPorId = indexarMaletas(instancia);
        final Map<String, Ruta> rutasPorMaleta = indexarRutas(solucion);
        final Set<String> gruposConflictivos = identificarGruposConflictivos(solucion, instancia, maletasPorId);
        if (gruposConflictivos.isEmpty()) {
            return;
        }

        final ContextoCapacitado contexto = crearContextoCapacitado(instancia);
        reservarRutasNoConflictivas(contexto, solucion, maletasPorId, gruposConflictivos);

        final Map<String, List<Maleta>> maletasPorGrupo = new HashMap<>();
        for (final Maleta maleta : instancia.getMaletas()) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final String claveGrupo = claveGrupo(maleta);
            if (!gruposConflictivos.contains(claveGrupo)) {
                continue;
            }
            maletasPorGrupo.computeIfAbsent(claveGrupo, ignored -> new ArrayList<>()).add(maleta);
        }

        final List<String> clavesOrdenadas = new ArrayList<>(maletasPorGrupo.keySet());
        clavesOrdenadas.sort(Comparator.naturalOrder());
        for (final String claveGrupo : clavesOrdenadas) {
            final List<Maleta> maletasGrupo = maletasPorGrupo.get(claveGrupo);
            if (maletasGrupo == null || maletasGrupo.isEmpty()) {
                continue;
            }
            final ResultadoAsignacion resultado = resolverGrupo(
                    maletasGrupo,
                    contexto,
                    instancia,
                    random,
                    FASE_REOPTIMIZACION,
                    MAX_ESTADOS_REOPTIMIZACION
            );
            actualizarRutasGrupo(resultado, maletasGrupo, rutasPorMaleta);
        }

        final ArrayList<Ruta> rutasOrdenadas = new ArrayList<>(rutasPorMaleta.values());
        rutasOrdenadas.sort(Comparator.comparing(Ruta::getIdMaleta, Comparator.nullsLast(String::compareTo)));
        int secuencia = 1;
        for (final Ruta ruta : rutasOrdenadas) {
            if (ruta == null) {
                continue;
            }
            ruta.setIdRuta(String.format(Locale.US, "R%08d", secuencia++));
        }
        solucion.setSolucion(rutasOrdenadas);
    }

    public static void reoptimizarPedidoMaleta(final Solucion solucion,
                                               final InstanciaProblema instancia,
                                               final ParametrosGA params,
                                               final Random random,
                                               final String idMaleta) {
        if (solucion == null || solucion.getSolucion() == null || instancia == null || idMaleta == null) {
            return;
        }
        final Map<String, Maleta> maletasPorId = indexarMaletas(instancia);
        final Maleta maletaObjetivo = maletasPorId.get(idMaleta);
        if (maletaObjetivo == null) {
            return;
        }
        final String grupoObjetivo = claveGrupo(maletaObjetivo);
        final ContextoCapacitado contexto = crearContextoCapacitado(instancia);
        final Map<String, Ruta> rutasPorMaleta = indexarRutas(solucion);
        final Set<String> gruposConflictivos = Set.of(grupoObjetivo);
        reservarRutasNoConflictivas(contexto, solucion, maletasPorId, gruposConflictivos);

        final List<Maleta> maletasGrupo = new ArrayList<>();
        for (final Maleta maleta : instancia.getMaletas()) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            if (grupoObjetivo.equals(claveGrupo(maleta))) {
                maletasGrupo.add(maleta);
            }
        }
        final ResultadoAsignacion resultado = resolverGrupo(
                maletasGrupo,
                contexto,
                instancia,
                random,
                FASE_MUTACION,
                MAX_ESTADOS_REOPTIMIZACION
        );
        actualizarRutasGrupo(resultado, maletasGrupo, rutasPorMaleta);
        final ArrayList<Ruta> rutasOrdenadas = new ArrayList<>(rutasPorMaleta.values());
        rutasOrdenadas.sort(Comparator.comparing(Ruta::getIdMaleta, Comparator.nullsLast(String::compareTo)));
        solucion.setSolucion(rutasOrdenadas);
    }

    public static DiagnosticoOcupacion estimarOcupacionTemporal(final Solucion solucion,
                                                                final InstanciaProblema instancia) {
        final Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia != null
                ? instancia.indexarAeropuertosPorIcao()
                : new HashMap<>();
        final Map<String, Maleta> maletasPorId = indexarMaletas(instancia);
        final long tiempoRecojo = instancia != null ? instancia.getTiempoRecojo() : 0L;
        if (solucion == null || solucion.getSolucion() == null) {
            return new DiagnosticoOcupacion(0.0, 0.0, 0.0);
        }
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getIdMaleta() == null) {
                continue;
            }
            final Maleta maleta = maletasPorId.get(ruta.getIdMaleta());
            if (maleta == null || maleta.getPedido() == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            registrarIntervalosRuta(ocupacionPorAeropuerto, ruta.getSubrutas(), maleta.getPedido(), tiempoRecojo);
        }

        double overflowTotal = 0.0;
        double ocupacionNormalizada = 0.0;
        int aeropuertosContados = 0;
        for (final Map.Entry<String, List<IntervaloOcupacion>> entry : ocupacionPorAeropuerto.entrySet()) {
            final Aeropuerto aeropuerto = aeropuertos.get(entry.getKey());
            if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                continue;
            }
            final int base = Math.max(0, aeropuerto.getMaletasActuales());
            final int pico = calcularPicoOcupacion(entry.getValue(), base);
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            if (pico > capacidad) {
                overflowTotal += pico - capacidad;
            }
            ocupacionNormalizada += Math.min(1.0, pico / (double) capacidad);
            aeropuertosContados++;
        }
        final double promedio = aeropuertosContados > 0 ? ocupacionNormalizada / aeropuertosContados : 0.0;
        return new DiagnosticoOcupacion(overflowTotal, ocupacionNormalizada, promedio);
    }

    public static boolean terminaEnDestino(final List<VueloInstancia> ruta, final Pedido pedido) {
        if (ruta == null || ruta.isEmpty() || pedido == null || pedido.getAeropuertoDestino() == null) {
            return false;
        }
        final VueloInstancia ultimoVuelo = ruta.get(ruta.size() - 1);
        return ultimoVuelo != null
                && ultimoVuelo.getAeropuertoDestino() != null
                && pedido.getAeropuertoDestino().getIdAeropuerto() != null
                && pedido.getAeropuertoDestino().getIdAeropuerto()
                .equals(ultimoVuelo.getAeropuertoDestino().getIdAeropuerto());
    }

    private static int materializarAsignacion(final ResultadoAsignacion resultado,
                                              final GrupoConflicto grupo,
                                              final ArrayList<Ruta> rutas,
                                              final int secuenciaInicial) {
        int secuencia = secuenciaInicial;
        final Map<String, List<VueloInstancia>> asignadas = resultado.rutasPorMaleta();
        for (final Maleta maleta : grupo.maletas()) {
            if (maleta == null || maleta.getPedido() == null) {
                continue;
            }
            final List<VueloInstancia> vuelos = asignadas.get(maleta.getIdMaleta());
            rutas.add(crearRuta(maleta, vuelos, secuencia++));
        }
        return secuencia;
    }

    private static void actualizarRutasGrupo(final ResultadoAsignacion resultado,
                                             final List<Maleta> maletasGrupo,
                                             final Map<String, Ruta> rutasPorMaleta) {
        for (final Maleta maleta : maletasGrupo) {
            if (maleta == null || maleta.getPedido() == null || maleta.getIdMaleta() == null) {
                continue;
            }
            final List<VueloInstancia> vuelos = resultado.rutasPorMaleta().get(maleta.getIdMaleta());
            rutasPorMaleta.put(maleta.getIdMaleta(), crearRuta(maleta, vuelos, 0));
        }
    }

    private static Ruta crearRuta(final Maleta maleta, final List<VueloInstancia> vuelos, final int secuencia) {
        final Ruta ruta = new Ruta();
        if (secuencia > 0) {
            ruta.setIdRuta(String.format(Locale.US, "R%08d", secuencia));
        }
        ruta.setIdMaleta(maleta.getIdMaleta());
        ruta.setPlazoMaximoDias(Ruta.calcularPlazo(
                maleta.getPedido().getAeropuertoOrigen(),
                maleta.getPedido().getAeropuertoDestino()
        ));
        if (vuelos == null || vuelos.isEmpty()) {
            ruta.setSubrutas(new ArrayList<>());
            ruta.setEstado(EstadoRuta.FALLIDA);
            ruta.setDuracion(0.0);
            return ruta;
        }
        ruta.setSubrutas(new ArrayList<>(vuelos));
        ruta.setEstado(terminaEnDestino(vuelos, maleta.getPedido()) ? EstadoRuta.PLANIFICADA : EstadoRuta.FALLIDA);
        ruta.setDuracion(duracionHoras(vuelos));
        return ruta;
    }

    private static ResultadoAsignacion resolverGrupo(final List<Maleta> maletasGrupo,
                                                     final ContextoCapacitado contexto,
                                                     final InstanciaProblema instancia,
                                                     final Random random,
                                                     final String fase,
                                                     final int presupuestoEstados) {
        final List<Maleta> pendientes = new ArrayList<>();
        for (final Maleta maleta : maletasGrupo) {
            if (maleta == null || maleta.getPedido() == null || maleta.getIdMaleta() == null) {
                continue;
            }
            pendientes.add(maleta);
        }
        pendientes.sort(Comparator.comparing(GAConstructorTemporal::claveUrgencia));
        final int[] presupuesto = {Math.max(1, presupuestoEstados)};
        return resolverGrupoRecursivo(pendientes, contexto, instancia, random, fase, presupuesto);
    }

    private static ResultadoAsignacion resolverGrupoRecursivo(final List<Maleta> pendientes,
                                                              final ContextoCapacitado contexto,
                                                              final InstanciaProblema instancia,
                                                              final Random random,
                                                              final String fase,
                                                              final int[] presupuesto) {
        if (pendientes.isEmpty()) {
            return new ResultadoAsignacion(new HashMap<>(), 0, 0.0);
        }
        if (presupuesto[0]-- <= 0) {
            return resultadoSinAsignacion(pendientes, fase, "presupuesto de busqueda agotado");
        }

        Maleta siguiente = null;
        List<RutaCandidata> candidatosSiguiente = List.of();
        int menorCantidadCandidatos = Integer.MAX_VALUE;
        for (final Maleta maleta : pendientes) {
            final List<RutaCandidata> candidatos = buscarCandidatos(maleta, contexto, instancia, random, fase);
            if (candidatos.size() < menorCantidadCandidatos) {
                menorCantidadCandidatos = candidatos.size();
                siguiente = maleta;
                candidatosSiguiente = candidatos;
            }
            if (menorCantidadCandidatos == 0) {
                break;
            }
        }
        if (siguiente == null) {
            return new ResultadoAsignacion(new HashMap<>(), 0, 0.0);
        }

        final List<Maleta> resto = new ArrayList<>(pendientes);
        resto.remove(siguiente);
        ResultadoAsignacion mejor = resultadoSinAsignacion(resto, fase, "sin ruta factible residual");

        for (final RutaCandidata candidata : candidatosSiguiente) {
            if (!contexto.reservarRuta(siguiente, candidata.vuelos())) {
                continue;
            }
            final ResultadoAsignacion sufijo = resolverGrupoRecursivo(resto, contexto, instancia, random, fase, presupuesto);
            contexto.liberarRuta(siguiente, candidata.vuelos());
            final Map<String, List<VueloInstancia>> rutas = new HashMap<>(sufijo.rutasPorMaleta());
            rutas.put(siguiente.getIdMaleta(), candidata.vuelos());
            final ResultadoAsignacion actual = new ResultadoAsignacion(
                    rutas,
                    sufijo.maletasCubiertas() + 1,
                    sufijo.duracionTotalHoras() + duracionHoras(candidata.vuelos())
            );
            if (esMejorAsignacion(actual, mejor)) {
                mejor = actual;
            }
        }

        final ResultadoAsignacion omitirActual = resolverGrupoRecursivo(resto, contexto, instancia, random, fase, presupuesto);
        final Map<String, List<VueloInstancia>> rutasOmitidas = new HashMap<>(omitirActual.rutasPorMaleta());
        rutasOmitidas.put(siguiente.getIdMaleta(), List.of());
        final ResultadoAsignacion actualOmitida = new ResultadoAsignacion(
                rutasOmitidas,
                omitirActual.maletasCubiertas(),
                omitirActual.duracionTotalHoras()
        );
        if (esMejorAsignacion(actualOmitida, mejor)) {
            mejor = actualOmitida;
        }

        final List<VueloInstancia> vuelosAsignados = mejor.rutasPorMaleta().get(siguiente.getIdMaleta());
        if (vuelosAsignados == null || vuelosAsignados.isEmpty()) {
            GADiagnosticoRutaRegistry.registrar(
                    siguiente.getIdMaleta(),
                    claveGrupo(siguiente),
                    fase,
                    "sin ruta factible residual"
            );
        } else {
            GADiagnosticoRutaRegistry.registrar(
                    siguiente.getIdMaleta(),
                    claveGrupo(siguiente),
                    fase,
                    "ruta reconstruida con capacidad residual"
            );
        }
        return mejor;
    }

    private static ResultadoAsignacion resultadoSinAsignacion(final List<Maleta> maletas,
                                                              final String fase,
                                                              final String motivo) {
        final Map<String, List<VueloInstancia>> rutas = new HashMap<>();
        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getIdMaleta() == null) {
                continue;
            }
            rutas.put(maleta.getIdMaleta(), List.of());
            GADiagnosticoRutaRegistry.registrar(maleta.getIdMaleta(), claveGrupo(maleta), fase, motivo);
        }
        return new ResultadoAsignacion(rutas, 0, 0.0);
    }

    private static boolean esMejorAsignacion(final ResultadoAsignacion candidato,
                                             final ResultadoAsignacion referencia) {
        if (candidato == null) {
            return false;
        }
        if (referencia == null) {
            return true;
        }
        if (candidato.maletasCubiertas() != referencia.maletasCubiertas()) {
            return candidato.maletasCubiertas() > referencia.maletasCubiertas();
        }
        return candidato.duracionTotalHoras() < referencia.duracionTotalHoras();
    }

    private static List<RutaCandidata> buscarCandidatos(final Maleta maleta,
                                                        final ContextoCapacitado contexto,
                                                        final InstanciaProblema instancia,
                                                        final Random random,
                                                        final String fase) {
        final List<RutaCandidata> candidatos = new ArrayList<>();
        if (maleta == null || maleta.getPedido() == null || instancia == null || instancia.getGrafo() == null) {
            return candidatos;
        }
        final Pedido pedido = maleta.getPedido();
        final String origen = obtenerIdAeropuerto(pedido.getAeropuertoOrigen());
        final String destino = obtenerIdAeropuerto(pedido.getAeropuertoDestino());
        final LocalDateTime inicio = pedido.getFechaRegistro();
        final LocalDateTime plazo = pedido.getFechaHoraPlazo();
        if (origen == null || destino == null || inicio == null || plazo == null) {
            GADiagnosticoRutaRegistry.registrar(
                    maleta.getIdMaleta(),
                    claveGrupo(maleta),
                    fase,
                    "pedido incompleto para construir ruta"
            );
            return candidatos;
        }

        final PriorityQueue<EstadoBusqueda> cola = new PriorityQueue<>(Comparator
                .comparingDouble(EstadoBusqueda::costo)
                .thenComparing(EstadoBusqueda::tiempoActual));
        final Set<String> rutasUnicas = new HashSet<>();
        final Set<String> visitadosEstado = new HashSet<>();
        cola.add(new EstadoBusqueda(origen, inicio, new ArrayList<>(), new HashSet<>(Set.of(origen)), 0.0));
        int expansiones = 0;

        while (!cola.isEmpty() && candidatos.size() < MAX_CANDIDATOS_POR_MALETA && expansiones < MAX_EXPANSIONES_BUSQUEDA) {
            final EstadoBusqueda estado = cola.poll();
            final String firmaEstado = firmarEstado(estado);
            if (!visitadosEstado.add(firmaEstado)) {
                continue;
            }
            expansiones++;
            final LocalDateTime minimoDespegue = estado.vuelos().isEmpty()
                    ? estado.tiempoActual()
                    : estado.tiempoActual().plusMinutes(instancia.getMinutosConexion());
            final List<VueloInstancia> salidas = instancia.getGrafo().vuelosDesde(estado.aeropuertoActual(), minimoDespegue);
            for (final VueloInstancia vuelo : salidas) {
                if (!esVueloFactible(vuelo, destino, plazo, contexto, estado.visitados())) {
                    continue;
                }
                final List<VueloInstancia> nuevoCamino = new ArrayList<>(estado.vuelos().size() + 1);
                nuevoCamino.addAll(estado.vuelos());
                nuevoCamino.add(vuelo);
                final Set<String> visitados = new HashSet<>(estado.visitados());
                final String idDestinoVuelo = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
                if (idDestinoVuelo != null) {
                    visitados.add(idDestinoVuelo);
                }
                final double nuevoCosto = estado.costo() + costoIncremental(vuelo, contexto, estado.vuelos().isEmpty());
                if (destino.equals(idDestinoVuelo)) {
                    if (!contexto.puedeReservarRuta(maleta, nuevoCamino)) {
                        continue;
                    }
                    final String firmaRuta = firmarRuta(nuevoCamino);
                    if (rutasUnicas.add(firmaRuta)) {
                        candidatos.add(new RutaCandidata(nuevoCamino, nuevoCosto));
                    }
                    continue;
                }
                if (nuevoCamino.size() >= MAX_ESCALAS_RUTA) {
                    continue;
                }
                cola.add(new EstadoBusqueda(
                        idDestinoVuelo,
                        vuelo.getFechaLlegada(),
                        nuevoCamino,
                        visitados,
                        nuevoCosto
                ));
            }
        }

        candidatos.sort(Comparator.comparingDouble(RutaCandidata::costo));
        if (candidatos.isEmpty()) {
            GADiagnosticoRutaRegistry.registrar(
                    maleta.getIdMaleta(),
                    claveGrupo(maleta),
                    fase,
                    "no existio ruta factible con capacidad residual"
            );
        }
        return candidatos;
    }

    private static boolean esVueloFactible(final VueloInstancia vuelo,
                                           final String destinoFinal,
                                           final LocalDateTime plazo,
                                           final ContextoCapacitado contexto,
                                           final Set<String> visitados) {
        if (vuelo == null || vuelo.getIdVueloInstancia() == null || vuelo.getFechaSalida() == null
                || vuelo.getFechaLlegada() == null || vuelo.getAeropuertoDestino() == null) {
            return false;
        }
        if (vuelo.getEstado() == EstadoVuelo.CANCELADO) {
            return false;
        }
        if (plazo != null && vuelo.getFechaLlegada().isAfter(plazo)) {
            return false;
        }
        final Integer capacidadResidual = contexto.capacidadResidualVuelo().get(vuelo.getIdVueloInstancia());
        if (capacidadResidual == null || capacidadResidual <= 0) {
            return false;
        }
        final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
        if (idDestino == null) {
            return false;
        }
        final boolean esDestinoFinal = idDestino.equals(destinoFinal);
        return esDestinoFinal || !visitados.contains(idDestino);
    }

    private static double costoIncremental(final VueloInstancia vuelo,
                                           final ContextoCapacitado contexto,
                                           final boolean primerVuelo) {
        final long minutosVuelo = vuelo.getFechaSalida() != null && vuelo.getFechaLlegada() != null
                ? Math.max(0L, Duration.between(vuelo.getFechaSalida(), vuelo.getFechaLlegada()).toMinutes())
                : 0L;
        final int capacidadInicial = Math.max(1, vuelo.getCapacidadDisponible());
        final int capacidadResidual = Math.max(0, contexto.capacidadResidualVuelo()
                .getOrDefault(vuelo.getIdVueloInstancia(), 0));
        final double cargaRelativa = 1.0 - (capacidadResidual / (double) capacidadInicial);
        final double penalizacionConexion = primerVuelo ? 0.0 : 15.0;
        return minutosVuelo + penalizacionConexion + (cargaRelativa * 120.0);
    }

    private static Set<String> identificarGruposConflictivos(final Solucion solucion,
                                                             final InstanciaProblema instancia,
                                                             final Map<String, Maleta> maletasPorId) {
        final Set<String> gruposConflictivos = new HashSet<>();
        final Map<String, Integer> usoPorVuelo = new HashMap<>();
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null) {
                continue;
            }
            for (final VueloInstancia vuelo : ruta.getSubrutas()) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                usoPorVuelo.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
            }
        }
        final Map<String, Integer> capacidadVuelo = new HashMap<>();
        for (final VueloInstancia vuelo : instancia.getVueloInstancias()) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            capacidadVuelo.put(vuelo.getIdVueloInstancia(), Math.max(0, vuelo.getCapacidadDisponible()));
        }
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getIdMaleta() == null) {
                continue;
            }
            final Maleta maleta = maletasPorId.get(ruta.getIdMaleta());
            if (maleta == null) {
                continue;
            }
            final String claveGrupo = claveGrupo(maleta);
            if (ruta.getEstado() == EstadoRuta.FALLIDA || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                gruposConflictivos.add(claveGrupo);
                continue;
            }
            if (!terminaEnDestino(ruta.getSubrutas(), maleta.getPedido())) {
                gruposConflictivos.add(claveGrupo);
                continue;
            }
            for (final VueloInstancia vuelo : ruta.getSubrutas()) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                final int usos = usoPorVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
                final int capacidad = capacidadVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
                if (usos > capacidad) {
                    gruposConflictivos.add(claveGrupo);
                    break;
                }
            }
        }
        return gruposConflictivos;
    }

    private static void reservarRutasNoConflictivas(final ContextoCapacitado contexto,
                                                    final Solucion solucion,
                                                    final Map<String, Maleta> maletasPorId,
                                                    final Set<String> gruposConflictivos) {
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getIdMaleta() == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final Maleta maleta = maletasPorId.get(ruta.getIdMaleta());
            if (maleta == null || gruposConflictivos.contains(claveGrupo(maleta))) {
                continue;
            }
            contexto.reservarRuta(maleta, ruta.getSubrutas());
        }
    }

    private static List<GrupoConflicto> agruparMaletas(final List<Maleta> maletas,
                                                        final boolean priorizarDificiles) {
        final Map<String, List<Maleta>> grupos = new HashMap<>();
        for (final Maleta maleta : maletas) {
            if (maleta == null || maleta.getIdMaleta() == null || maleta.getPedido() == null) {
                continue;
            }
            grupos.computeIfAbsent(claveGrupo(maleta), ignored -> new ArrayList<>()).add(maleta);
        }
        final List<GrupoConflicto> resultado = new ArrayList<>(grupos.size());
        for (final Map.Entry<String, List<Maleta>> entry : grupos.entrySet()) {
            final List<Maleta> maletasGrupo = entry.getValue();
            if (priorizarDificiles) {
                maletasGrupo.sort(Comparator.comparingDouble(GAConstructorTemporal::indiceDificultad).reversed());
            } else {
                maletasGrupo.sort(Comparator.comparing(GAConstructorTemporal::claveUrgencia));
            }
            resultado.add(new GrupoConflicto(entry.getKey(), maletasGrupo));
        }
        resultado.sort(Comparator
                .comparing((GrupoConflicto grupo) -> claveUrgencia(grupo.maletas().get(0)))
                .thenComparing((GrupoConflicto grupo) -> -grupo.maletas().size())
                .thenComparing(GrupoConflicto::clave));
        return resultado;
    }

    private static ContextoCapacitado crearContextoCapacitado(final InstanciaProblema instancia) {
        final Map<String, Integer> capacidadResidualVuelo = new HashMap<>();
        for (final VueloInstancia vuelo : instancia.getVueloInstancias()) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            capacidadResidualVuelo.put(vuelo.getIdVueloInstancia(), Math.max(0, vuelo.getCapacidadDisponible()));
        }
        return new ContextoCapacitado(
                capacidadResidualVuelo,
                new HashMap<>(),
                instancia.indexarAeropuertosPorIcao(),
                instancia.getTiempoRecojo()
        );
    }

    private static int seleccionarIndice(final int cantidad,
                                         final double nivelAleatoriedad,
                                         final Random random) {
        if (cantidad <= 1 || nivelAleatoriedad <= 0.0) {
            return 0;
        }
        final int ventana = Math.max(1, (int) Math.ceil(cantidad * nivelAleatoriedad));
        return random.nextInt(Math.min(ventana, cantidad));
    }

    private static String claveGrupo(final Maleta maleta) {
        if (maleta == null || maleta.getPedido() == null) {
            return "SIN_GRUPO";
        }
        final Pedido pedido = maleta.getPedido();
        if (pedido.getIdPedido() != null && !pedido.getIdPedido().isBlank()) {
            return "PEDIDO:" + pedido.getIdPedido();
        }
        final String origen = obtenerIdAeropuerto(pedido.getAeropuertoOrigen());
        final String destino = obtenerIdAeropuerto(pedido.getAeropuertoDestino());
        final LocalDateTime plazo = pedido.getFechaHoraPlazo();
        return String.format(
                Locale.US,
                "OD:%s->%s|%s",
                origen != null ? origen : "-",
                destino != null ? destino : "-",
                plazo != null ? plazo.truncatedTo(java.time.temporal.ChronoUnit.HOURS) : "-"
        );
    }

    private static LocalDateTime claveUrgencia(final Maleta maleta) {
        if (maleta == null || maleta.getPedido() == null) {
            return LocalDateTime.MAX;
        }
        final Pedido pedido = maleta.getPedido();
        if (pedido.getFechaHoraPlazo() != null) {
            return pedido.getFechaHoraPlazo();
        }
        if (pedido.getFechaRegistro() != null) {
            return pedido.getFechaRegistro();
        }
        return LocalDateTime.MAX;
    }

    private static double indiceDificultad(final Maleta maleta) {
        if (maleta == null || maleta.getPedido() == null) {
            return Double.NEGATIVE_INFINITY;
        }
        final Pedido pedido = maleta.getPedido();
        final LocalDateTime registro = pedido.getFechaRegistro();
        final LocalDateTime plazo = pedido.getFechaHoraPlazo();
        double puntaje = 0.0;
        if (registro != null && plazo != null) {
            puntaje += 10_000D - Math.max(0L, Duration.between(registro, plazo).toMinutes());
        }
        if (pedido.getAeropuertoOrigen() != null
                && pedido.getAeropuertoDestino() != null
                && pedido.getAeropuertoOrigen().getCiudad() != null
                && pedido.getAeropuertoDestino().getCiudad() != null
                && pedido.getAeropuertoOrigen().getCiudad().getContinente()
                != pedido.getAeropuertoDestino().getCiudad().getContinente()) {
            puntaje += 500D;
        }
        return puntaje;
    }

    private static Map<String, Maleta> indexarMaletas(final InstanciaProblema instancia) {
        final Map<String, Maleta> indice = new HashMap<>();
        if (instancia == null || instancia.getMaletas() == null) {
            return indice;
        }
        for (final Maleta maleta : instancia.getMaletas()) {
            if (maleta != null && maleta.getIdMaleta() != null) {
                indice.put(maleta.getIdMaleta(), maleta);
            }
        }
        return indice;
    }

    private static Map<String, Ruta> indexarRutas(final Solucion solucion) {
        final Map<String, Ruta> indice = new HashMap<>();
        if (solucion == null || solucion.getSolucion() == null) {
            return indice;
        }
        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta != null && ruta.getIdMaleta() != null) {
                indice.put(ruta.getIdMaleta(), ruta);
            }
        }
        return indice;
    }

    private static String obtenerIdAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto != null ? aeropuerto.getIdAeropuerto() : null;
    }

    private static String firmarRuta(final List<VueloInstancia> vuelos) {
        final StringBuilder builder = new StringBuilder();
        for (final VueloInstancia vuelo : vuelos) {
            if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('|');
            }
            builder.append(vuelo.getIdVueloInstancia());
        }
        return builder.toString();
    }

    private static String firmarEstado(final EstadoBusqueda estado) {
        return estado.aeropuertoActual() + "|" + estado.tiempoActual() + "|" + firmarRuta(estado.vuelos());
    }

    private static double duracionHoras(final List<VueloInstancia> vuelos) {
        if (vuelos == null || vuelos.isEmpty()) {
            return 0.0;
        }
        final LocalDateTime salida = vuelos.get(0).getFechaSalida();
        final LocalDateTime llegada = vuelos.get(vuelos.size() - 1).getFechaLlegada();
        if (salida == null || llegada == null) {
            return 0.0;
        }
        return Duration.between(salida, llegada).toMinutes() / 60.0;
    }

    private static void registrarIntervalosRuta(final Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto,
                                                final List<VueloInstancia> ruta,
                                                final Pedido pedido,
                                                final long tiempoRecojo) {
        final List<IntervaloAeropuerto> intervalos = construirIntervalosRuta(ruta, pedido, tiempoRecojo);
        for (final IntervaloAeropuerto intervalo : intervalos) {
            ocupacionPorAeropuerto.computeIfAbsent(intervalo.idAeropuerto(), ignored -> new ArrayList<>())
                    .add(intervalo.intervalo());
        }
    }

    private static List<IntervaloAeropuerto> construirIntervalosRuta(final List<VueloInstancia> ruta,
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

    private static int calcularPicoOcupacion(final List<IntervaloOcupacion> intervalos, final int base) {
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
        eventos.sort(Comparator
                .comparing(EventoOcupacion::momento)
                .thenComparing(EventoOcupacion::delta));
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

    private record GrupoConflicto(String clave, List<Maleta> maletas) {
    }

    private record ResultadoAsignacion(Map<String, List<VueloInstancia>> rutasPorMaleta,
                                       int maletasCubiertas,
                                       double duracionTotalHoras) {
    }

    private record RutaCandidata(List<VueloInstancia> vuelos, double costo) {
    }

    private record EstadoBusqueda(String aeropuertoActual,
                                  LocalDateTime tiempoActual,
                                  List<VueloInstancia> vuelos,
                                  Set<String> visitados,
                                  double costo) {
    }

    private record IntervaloOcupacion(LocalDateTime inicio, LocalDateTime fin) {
    }

    private record IntervaloAeropuerto(String idAeropuerto, IntervaloOcupacion intervalo) {
    }

    private record EventoOcupacion(LocalDateTime momento, int delta) {
    }

    public record DiagnosticoOcupacion(double overflowTotal,
                                       double ocupacionAcumulada,
                                       double ocupacionPromedio) {
    }

    private record ContextoCapacitado(Map<String, Integer> capacidadResidualVuelo,
                                      Map<String, List<IntervaloOcupacion>> ocupacionPorAeropuerto,
                                      Map<String, Aeropuerto> aeropuertos,
                                      long tiempoRecojo) {

        private boolean puedeReservarRuta(final Maleta maleta, final List<VueloInstancia> ruta) {
            if (maleta == null || maleta.getPedido() == null || ruta == null || ruta.isEmpty()) {
                return false;
            }
            for (final VueloInstancia vuelo : ruta) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    return false;
                }
                final int capacidad = capacidadResidualVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
                if (capacidad <= 0) {
                    return false;
                }
            }
            final List<IntervaloAeropuerto> intervalos =
                    construirIntervalosRuta(ruta, maleta.getPedido(), tiempoRecojo);
            for (final IntervaloAeropuerto intervalo : intervalos) {
                if (superaCapacidadAeropuerto(intervalo.idAeropuerto(), intervalo.intervalo())) {
                    return false;
                }
            }
            return true;
        }

        private boolean reservarRuta(final Maleta maleta, final List<VueloInstancia> ruta) {
            if (!puedeReservarRuta(maleta, ruta)) {
                return false;
            }
            for (final VueloInstancia vuelo : ruta) {
                final String idVuelo = vuelo.getIdVueloInstancia();
                capacidadResidualVuelo.put(idVuelo, capacidadResidualVuelo.get(idVuelo) - 1);
            }
            final List<IntervaloAeropuerto> intervalos =
                    construirIntervalosRuta(ruta, maleta.getPedido(), tiempoRecojo);
            for (final IntervaloAeropuerto intervalo : intervalos) {
                ocupacionPorAeropuerto
                        .computeIfAbsent(intervalo.idAeropuerto(), ignored -> new ArrayList<>())
                        .add(intervalo.intervalo());
            }
            return true;
        }

        private void liberarRuta(final Maleta maleta, final List<VueloInstancia> ruta) {
            if (maleta == null || maleta.getPedido() == null || ruta == null || ruta.isEmpty()) {
                return;
            }
            for (final VueloInstancia vuelo : ruta) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                capacidadResidualVuelo.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
            }
            final List<IntervaloAeropuerto> intervalos =
                    construirIntervalosRuta(ruta, maleta.getPedido(), tiempoRecojo);
            for (final IntervaloAeropuerto intervalo : intervalos) {
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

        private boolean superaCapacidadAeropuerto(final String idAeropuerto, final IntervaloOcupacion candidato) {
            final Aeropuerto aeropuerto = aeropuertos.get(idAeropuerto);
            if (aeropuerto == null || aeropuerto.getCapacidadAlmacen() <= 0) {
                return false;
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
            eventos.sort(Comparator
                    .comparing(EventoOcupacion::momento)
                    .thenComparing(EventoOcupacion::delta));
            int ocupacion = Math.max(0, aeropuerto.getMaletasActuales());
            for (final EventoOcupacion evento : eventos) {
                ocupacion += evento.delta();
                if (ocupacion > aeropuerto.getCapacidadAlmacen()) {
                    return true;
                }
            }
            return false;
        }

        private boolean seSolapan(final IntervaloOcupacion primero, final IntervaloOcupacion segundo) {
            return primero.inicio().isBefore(segundo.fin()) && segundo.inicio().isBefore(primero.fin());
        }
    }
}
