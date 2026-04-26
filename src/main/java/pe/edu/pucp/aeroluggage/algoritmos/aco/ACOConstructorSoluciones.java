package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

final class ACOConstructorSoluciones {
    private static final EstadoRuta ESTADO_PLANIFICADA = EstadoRuta.PLANIFICADA;
    private static final EstadoRuta ESTADO_NO_FACTIBLE = EstadoRuta.FALLIDA;
    private static final EstadoRuta ESTADO_REPLANIFICADA = EstadoRuta.REPLANIFICADA;
    private static final int UNIDAD_MALETA = 1;
    private static final int MAX_ESCALAS_RUTA = 8;
    private static final int MAX_CANDIDATOS_CODICIOSOS = 2;
    private static final int MAX_CANDIDATOS_PROBABILISTICOS = 4;
    private static final int MAX_CONECTIVIDAD_CONSIDERADA = 6;
    private static final int MAX_ESTADOS_BUSQUEDA_TEMPORAL = 120;
    private static final int MAX_VUELOS_POR_EXPANSION = 90;
    private static final double FACTOR_ALEATORIO_MINIMO = 0.85D;
    private static final double RANGO_FACTOR_ALEATORIO = 0.30D;

    private final ACOConfiguracion configuracion;
    private final Random random;

    ACOConstructorSoluciones(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion;
        this.random = new Random(configuracion.getSemilla());
    }

    ArrayList<Solucion> generarPoblacionInicial(
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas
    ) {
        final ArrayList<Solucion> poblacionInicial = new ArrayList<>();
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>(subproblema.getCapacidadRestanteVueloBase());
        final Map<String, Integer> capacidadRestanteAlmacen = new HashMap<>(
                subproblema.getCapacidadRestanteAlmacenBase()
        );
        final ArrayList<Ruta> rutas = new ArrayList<>();

        for (final Maleta maleta : subproblema.getMaletasPendientes()) {
            rutas.add(construirRutaParaMaleta(
                    maleta,
                    subproblema,
                    feromonas,
                    capacidadRestanteVuelo,
                    capacidadRestanteAlmacen,
                    true
            ));
        }

        poblacionInicial.add(new Solucion(rutas));
        return poblacionInicial;
    }

    Solucion construirSolucionHormiga(
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas
    ) {
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>(subproblema.getCapacidadRestanteVueloBase());
        final Map<String, Integer> capacidadRestanteAlmacen = new HashMap<>(
                subproblema.getCapacidadRestanteAlmacenBase()
        );
        final ArrayList<Ruta> rutas = new ArrayList<>();

        for (final Maleta maleta : subproblema.getMaletasPendientes()) {
            rutas.add(construirRutaParaMaleta(
                    maleta,
                    subproblema,
                    feromonas,
                    capacidadRestanteVuelo,
                    capacidadRestanteAlmacen,
                    false
            ));
        }

        return new Solucion(rutas);
    }

    Solucion repararInconsistencias(final Solucion solucion, final SubproblemaACO subproblema) {
        final ArrayList<Ruta> rutasReparadas = new ArrayList<>();
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new Solucion(rutasReparadas);
        }

        for (final Ruta rutaOriginal : solucion.getSubrutas()) {
            final Ruta ruta = clonarRuta(rutaOriginal);
            final ArrayList<VueloInstancia> vuelos = new ArrayList<>(ruta.getSubrutas());
            if (!estaOrdenadoPorSalida(vuelos)) {
                vuelos.sort(Comparator.comparing(VueloInstancia::getFechaSalida));
            }
            ruta.setSubrutas(vuelos);

            if (ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || vuelos.isEmpty()) {
                ruta.setEstado(ESTADO_NO_FACTIBLE);
                ruta.calcularPlazo();
                rutasReparadas.add(ruta);
                continue;
            }

            boolean inconsistente = false;
            for (int i = 1; i < vuelos.size(); i++) {
                final VueloInstancia anterior = vuelos.get(i - 1);
                final VueloInstancia actual = vuelos.get(i);
                final boolean cadenaInvalida = !esMismoAeropuerto(
                        anterior.getAeropuertoDestino(),
                        actual.getAeropuertoOrigen()
                );
                final boolean secuenciaTemporalInvalida = actual.getFechaSalida().isBefore(anterior.getFechaLlegada());
                if (cadenaInvalida || secuenciaTemporalInvalida) {
                    inconsistente = true;
                    break;
                }
            }

            final Maleta maleta = subproblema.obtenerMaleta(ruta.getIdMaleta());
            final Pedido pedido = maleta == null ? null : maleta.getPedido();
            final Aeropuerto destinoEsperado = pedido == null ? null : pedido.getAeropuertoDestino();
            final VueloInstancia ultimoVuelo = vuelos.get(vuelos.size() - 1);
            if (destinoEsperado != null && !esMismoAeropuerto(ultimoVuelo.getAeropuertoDestino(), destinoEsperado)) {
                inconsistente = true;
            }

            if (inconsistente) {
                ruta.setEstado(ESTADO_NO_FACTIBLE);
            }
            ruta.calcularPlazo();
            rutasReparadas.add(ruta);
        }

        return new Solucion(rutasReparadas);
    }

    Solucion mejoraLocal(final Solucion solucion, final SubproblemaACO subproblema) {
        final ArrayList<Ruta> rutasMejoradas = new ArrayList<>();
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new Solucion(rutasMejoradas);
        }

        for (final Ruta rutaOriginal : solucion.getSubrutas()) {
            final Ruta ruta = clonarRuta(rutaOriginal);
            if (ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || ruta.getSubrutas().size() <= 1) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final Maleta maleta = subproblema.obtenerMaleta(ruta.getIdMaleta());
            final VueloInstancia vueloDirecto = buscarMejorVueloDirecto(maleta, subproblema);
            if (vueloDirecto == null) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final int ultimoIndice = ruta.getSubrutas().size() - 1;
            final LocalDateTime llegadaActual = ruta.getSubrutas().get(ultimoIndice).getFechaLlegada();
            if (llegadaActual != null && !vueloDirecto.getFechaLlegada().isBefore(llegadaActual)) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final ArrayList<VueloInstancia> nuevaSubruta = new ArrayList<>();
            nuevaSubruta.add(clonarVueloInstanciaConCapacidad(
                    vueloDirecto,
                    Math.max(0, vueloDirecto.getCapacidadDisponible() - UNIDAD_MALETA)
            ));
            ruta.setSubrutas(nuevaSubruta);
            ruta.calcularPlazo();
            ruta.setEstado(ESTADO_REPLANIFICADA);
            rutasMejoradas.add(ruta);
        }

        return new Solucion(rutasMejoradas);
    }

    Solucion clonarSolucion(final Solucion solucion) {
        final ArrayList<Ruta> rutas = new ArrayList<>();
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new Solucion(rutas);
        }
        for (final Ruta ruta : solucion.getSubrutas()) {
            rutas.add(clonarRuta(ruta));
        }
        return new Solucion(rutas);
    }

    Ruta clonarRuta(final Ruta ruta) {
        if (ruta == null) {
            return null;
        }
        final ArrayList<VueloInstancia> subrutas = new ArrayList<>();
        if (ruta.getSubrutas() != null) {
            for (final VueloInstancia subruta : ruta.getSubrutas()) {
                subrutas.add(clonarVueloInstancia(subruta));
            }
        }
        return new Ruta(
                ruta.getIdRuta(),
                ruta.getIdMaleta(),
                ruta.getPlazoMaximoDias(),
                ruta.getDuracion(),
                subrutas,
                ruta.getEstado()
        );
    }

    LocalDateTime obtenerPrimeraSalida(final Ruta ruta) {
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            return null;
        }
        return ruta.getSubrutas().get(0).getFechaSalida();
    }

    boolean esRutaFactible(final Ruta ruta) {
        return ruta != null && !ESTADO_NO_FACTIBLE.equals(ruta.getEstado());
    }

    void aplicarActualizacionLocalFeromona(
            final FeromonasACO feromonas,
            final Maleta maleta,
            final VueloInstancia vuelo
    ) {
        if (feromonas == null) {
            return;
        }
        feromonas.actualizarLocal(maleta, vuelo);
    }

    private Ruta construirRutaParaMaleta(
            final Maleta maleta,
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final boolean modoCodicioso
    ) {
        if (maleta == null || maleta.getIdMaleta() == null || maleta.getPedido() == null) {
            return crearRutaNoFactible(maleta, new ArrayList<>(), subproblema);
        }

        final Pedido pedido = maleta.getPedido();
        final Aeropuerto origen = pedido.getAeropuertoOrigen();
        final Aeropuerto destino = pedido.getAeropuertoDestino();
        if (origen == null || destino == null) {
            return crearRutaNoFactible(maleta, new ArrayList<>(), subproblema);
        }

        final Aeropuerto actual = origen;
        final LocalDateTime tiempoActual = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        final Map<String, Integer> capacidadRestanteVueloTemporal = new HashMap<>(capacidadRestanteVuelo);
        final Map<String, Integer> capacidadRestanteAlmacenTemporal = new HashMap<>(capacidadRestanteAlmacen);
        final ArrayList<VueloInstancia> plan = construirPlanTemporal(
                maleta,
                subproblema,
                feromonas,
                actual,
                destino,
                tiempoActual,
                plazo,
                capacidadRestanteVueloTemporal,
                capacidadRestanteAlmacenTemporal,
                modoCodicioso
        );
        if (plan == null || plan.isEmpty()) {
            return crearRutaNoFactible(maleta, new ArrayList<>(), subproblema);
        }
        capacidadRestanteVuelo.putAll(capacidadRestanteVueloTemporal);
        capacidadRestanteAlmacen.putAll(capacidadRestanteAlmacenTemporal);
        return crearRutaFactible(maleta, plan, subproblema, ESTADO_PLANIFICADA);
    }

    private ArrayList<VueloInstancia> construirPlanPorRetroceso(
            final Maleta maleta,
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas,
            final Aeropuerto actual,
            final Aeropuerto destino,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final boolean modoCodicioso
    ) {
        final Set<String> visitados = new HashSet<>();
        if (actual.getIdAeropuerto() != null) {
            visitados.add(actual.getIdAeropuerto());
        }
        return construirPlanRecursivo(
                maleta,
                subproblema,
                feromonas,
                actual,
                destino,
                tiempoActual,
                plazo,
                capacidadRestanteVuelo,
                capacidadRestanteAlmacen,
                visitados,
                modoCodicioso,
                0
        );
    }

    private ArrayList<VueloInstancia> construirPlanTemporal(
            final Maleta maleta,
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas,
            final Aeropuerto origen,
            final Aeropuerto destino,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final boolean modoCodicioso
    ) {
        final String idOrigen = obtenerIdAeropuerto(origen);
        final String idDestino = obtenerIdAeropuerto(destino);
        if (idOrigen == null || idDestino == null || tiempoActual == null || plazo == null) {
            return null;
        }

        final PriorityQueue<EstadoPlanTemporal> frontera = new PriorityQueue<>(
                Comparator.comparingDouble(EstadoPlanTemporal::getCosto)
                        .thenComparing(EstadoPlanTemporal::getTiempoActual)
        );
        final Map<String, LocalDateTime> mejorLlegadaPorAeropuerto = new HashMap<>();
        final Set<String> visitadosIniciales = new HashSet<>();
        visitadosIniciales.add(idOrigen);
        frontera.add(new EstadoPlanTemporal(idOrigen, tiempoActual, 0D, new ArrayList<>(), visitadosIniciales));
        mejorLlegadaPorAeropuerto.put(idOrigen, tiempoActual);

        int estadosEvaluados = 0;
        while (!frontera.isEmpty() && estadosEvaluados < MAX_ESTADOS_BUSQUEDA_TEMPORAL) {
            estadosEvaluados++;
            final EstadoPlanTemporal estado = frontera.poll();
            if (estado.getIdAeropuerto().equals(idDestino)) {
                final ArrayList<VueloInstancia> plan = estado.clonarCamino();
                aplicarConsumoPlan(plan, destino, capacidadRestanteVuelo, capacidadRestanteAlmacen);
                reforzarFeromonaLocal(plan, maleta, feromonas);
                return plan;
            }
            if (estado.getCamino().size() >= MAX_ESCALAS_RUTA) {
                continue;
            }

            final ArrayList<VueloInstancia> vuelos = subproblema.getVuelosDesde(estado.getIdAeropuerto());
            int vuelosEvaluados = 0;
            for (int i = primerIndiceConSalidaNoAnterior(vuelos, estado.getTiempoActual()); i < vuelos.size(); i++) {
                if (vuelosEvaluados >= MAX_VUELOS_POR_EXPANSION) {
                    break;
                }
                vuelosEvaluados++;
                final VueloInstancia vuelo = vuelos.get(i);
                if (!esVueloFactibleParaBusqueda(
                        vuelo,
                        destino,
                        estado,
                        plazo,
                        capacidadRestanteVuelo,
                        capacidadRestanteAlmacen
                )) {
                    continue;
                }

                final String idSiguiente = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
                final LocalDateTime mejorLlegada = mejorLlegadaPorAeropuerto.get(idSiguiente);
                if (mejorLlegada != null && !vuelo.getFechaLlegada().isBefore(mejorLlegada)) {
                    continue;
                }

                mejorLlegadaPorAeropuerto.put(idSiguiente, vuelo.getFechaLlegada());
                frontera.add(estado.avanzar(
                        idSiguiente,
                        vuelo,
                        calcularCostoBusqueda(vuelo, maleta, feromonas, destino, estado.getTiempoActual(),
                                plazo, subproblema, modoCodicioso)
                ));
            }
        }

        return null;
    }

    private boolean esVueloFactibleParaBusqueda(
            final VueloInstancia vuelo,
            final Aeropuerto destinoFinal,
            final EstadoPlanTemporal estado,
            final LocalDateTime plazo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen
    ) {
        if (vuelo == null || vuelo.getFechaSalida() == null || vuelo.getFechaLlegada() == null) {
            return false;
        }
        if (vuelo.getFechaSalida().isBefore(estado.getTiempoActual()) || vuelo.getFechaLlegada().isAfter(plazo)) {
            return false;
        }
        if (capacidadRestanteVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0) < UNIDAD_MALETA) {
            return false;
        }

        final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
        if (idDestino == null || estado.getVisitados().contains(idDestino)) {
            return false;
        }

        final boolean esDestinoFinal = esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
        return esDestinoFinal || capacidadRestanteAlmacen.getOrDefault(idDestino, 0) >= UNIDAD_MALETA;
    }

    private double calcularCostoBusqueda(
            final VueloInstancia vuelo,
            final Maleta maleta,
            final FeromonasACO feromonas,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final SubproblemaACO subproblema,
            final boolean modoCodicioso
    ) {
        final long esperaMinutos = Math.max(0L, Duration.between(tiempoActual, vuelo.getFechaSalida()).toMinutes());
        final long duracionMinutos = Math.max(
                1L,
                Duration.between(vuelo.getFechaSalida(), vuelo.getFechaLlegada()).toMinutes()
        );
        if (modoCodicioso) {
            return esperaMinutos + duracionMinutos;
        }

        final double puntaje = puntajeCandidato(
                vuelo,
                feromonas,
                maleta,
                destinoFinal,
                tiempoActual,
                plazo,
                subproblema
        );
        final double factorAleatorio = FACTOR_ALEATORIO_MINIMO + random.nextDouble() * RANGO_FACTOR_ALEATORIO;
        return (esperaMinutos + duracionMinutos) * factorAleatorio + 1D / Math.max(0.000001D, puntaje);
    }

    private void aplicarConsumoPlan(final List<VueloInstancia> plan,
                                    final Aeropuerto destinoFinal,
                                    final Map<String, Integer> capacidadRestanteVuelo,
                                    final Map<String, Integer> capacidadRestanteAlmacen) {
        for (int i = 0; i < plan.size(); i++) {
            final VueloInstancia vuelo = plan.get(i);
            final int nuevaCapacidad = actualizarEstadoTemporal(vuelo, capacidadRestanteVuelo,
                    capacidadRestanteAlmacen, destinoFinal);
            plan.set(i, clonarVueloInstanciaConCapacidad(vuelo, nuevaCapacidad));
        }
    }

    private void reforzarFeromonaLocal(final List<VueloInstancia> plan,
                                       final Maleta maleta,
                                       final FeromonasACO feromonas) {
        for (final VueloInstancia vuelo : plan) {
            aplicarActualizacionLocalFeromona(feromonas, maleta, vuelo);
        }
    }

    private ArrayList<VueloInstancia> construirPlanRecursivo(
            final Maleta maleta,
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas,
            final Aeropuerto actual,
            final Aeropuerto destino,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final Set<String> visitados,
            final boolean modoCodicioso,
            final int profundidad
    ) {
        if (esMismoAeropuerto(actual, destino)) {
            return new ArrayList<>();
        }
        if (profundidad >= MAX_ESCALAS_RUTA) {
            return null;
        }

        final ArrayList<VueloInstancia> candidatos = vuelosFactibles(
                actual,
                destino,
                tiempoActual,
                subproblema,
                capacidadRestanteVuelo,
                capacidadRestanteAlmacen,
                plazo,
                visitados
        );
        if (candidatos.isEmpty()) {
            return null;
        }

        ordenarCandidatos(candidatos, feromonas, maleta, destino, tiempoActual, plazo, subproblema);
        final int maxCandidatos = modoCodicioso ? MAX_CANDIDATOS_CODICIOSOS : MAX_CANDIDATOS_PROBABILISTICOS;
        final int limite = Math.min(maxCandidatos, candidatos.size());

        for (int i = 0; i < limite; i++) {
            final VueloInstancia siguienteVuelo = candidatos.get(i);
            final Map<String, Integer> capacidadVueloCopia = new HashMap<>(capacidadRestanteVuelo);
            final Map<String, Integer> capacidadAlmacenCopia = new HashMap<>(capacidadRestanteAlmacen);
            final Set<String> visitadosCopia = new HashSet<>(visitados);
            final int nuevaCapacidad = actualizarEstadoTemporal(
                    siguienteVuelo,
                    capacidadVueloCopia,
                    capacidadAlmacenCopia,
                    destino
            );
            if (siguienteVuelo.getAeropuertoDestino() != null
                    && siguienteVuelo.getAeropuertoDestino().getIdAeropuerto() != null) {
                visitadosCopia.add(siguienteVuelo.getAeropuertoDestino().getIdAeropuerto());
            }

            final ArrayList<VueloInstancia> sufijo = construirPlanRecursivo(
                    maleta,
                    subproblema,
                    feromonas,
                    siguienteVuelo.getAeropuertoDestino(),
                    destino,
                    siguienteVuelo.getFechaLlegada(),
                    plazo,
                    capacidadVueloCopia,
                    capacidadAlmacenCopia,
                    visitadosCopia,
                    modoCodicioso,
                    profundidad + 1
            );
            if (sufijo == null) {
                continue;
            }

            capacidadRestanteVuelo.clear();
            capacidadRestanteVuelo.putAll(capacidadVueloCopia);
            capacidadRestanteAlmacen.clear();
            capacidadRestanteAlmacen.putAll(capacidadAlmacenCopia);

            final ArrayList<VueloInstancia> plan = new ArrayList<>(sufijo.size() + 1);
            plan.add(clonarVueloInstanciaConCapacidad(siguienteVuelo, nuevaCapacidad));
            plan.addAll(sufijo);
            aplicarActualizacionLocalFeromona(feromonas, maleta, siguienteVuelo);
            return plan;
        }

        return null;
    }

    private ArrayList<VueloInstancia> vuelosFactibles(
            final Aeropuerto actual,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final SubproblemaACO subproblema,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final LocalDateTime plazo,
            final Set<String> visitados
    ) {
        final ArrayList<VueloInstancia> candidatos = new ArrayList<>();
        if (actual == null || actual.getIdAeropuerto() == null) {
            return candidatos;
        }

        final ArrayList<VueloInstancia> vuelos = subproblema.getVuelosDesde(actual.getIdAeropuerto());
        if (vuelos.isEmpty()) {
            return candidatos;
        }

        for (int i = primerIndiceConSalidaNoAnterior(vuelos, tiempoActual); i < vuelos.size(); i++) {
            final VueloInstancia vuelo = vuelos.get(i);
            if (vuelo.getFechaSalida().isBefore(tiempoActual)) {
                continue;
            }
            if (plazo != null && vuelo.getFechaLlegada().isAfter(plazo)) {
                continue;
            }

            final int capacidadVuelo = capacidadRestanteVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
            if (capacidadVuelo < UNIDAD_MALETA) {
                continue;
            }

            final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            if (idDestino == null || visitados.contains(idDestino)) {
                continue;
            }

            final boolean esDestinoFinal = esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
            if (!esDestinoFinal) {
                final int capacidadAlmacen = capacidadRestanteAlmacen.getOrDefault(idDestino, 0);
                if (capacidadAlmacen < UNIDAD_MALETA) {
                    continue;
                }
            }
            candidatos.add(vuelo);
        }
        return candidatos;
    }

    private VueloInstancia seleccionarCodiciosamente(
            final ArrayList<VueloInstancia> candidatos,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final SubproblemaACO subproblema
    ) {
        VueloInstancia mejorVuelo = null;
        double mejorHeuristica = Double.NEGATIVE_INFINITY;

        for (final VueloInstancia candidato : candidatos) {
            final double heuristica = calcularHeuristica(candidato, destinoFinal, tiempoActual, plazo, subproblema);
            if (heuristica <= mejorHeuristica) {
                continue;
            }
            mejorHeuristica = heuristica;
            mejorVuelo = candidato;
        }
        return mejorVuelo;
    }

    private VueloInstancia seleccionarProbabilisticamente(
            final ArrayList<VueloInstancia> candidatos,
            final FeromonasACO feromonas,
            final Maleta maleta,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final SubproblemaACO subproblema
    ) {
        double sumaPesos = 0D;
        final Map<String, Double> pesos = new HashMap<>();

        for (final VueloInstancia candidato : candidatos) {
            final double feromona = obtenerFeromona(feromonas, maleta, candidato);
            final double heuristica = calcularHeuristica(candidato, destinoFinal, tiempoActual, plazo, subproblema);
            final double peso = Math.pow(feromona, configuracion.getAlpha())
                    * Math.pow(heuristica, configuracion.getBeta());
            pesos.put(candidato.getIdVueloInstancia(), peso);
            sumaPesos += peso;
        }

        if (sumaPesos <= 0D) {
            return seleccionarCodiciosamente(candidatos, destinoFinal, tiempoActual, plazo, subproblema);
        }

        double umbral = random.nextDouble() * sumaPesos;
        for (final VueloInstancia candidato : candidatos) {
            umbral -= pesos.getOrDefault(candidato.getIdVueloInstancia(), 0D);
            if (umbral > 0D) {
                continue;
            }
            return candidato;
        }
        return candidatos.get(candidatos.size() - 1);
    }

    private double calcularHeuristica(
            final VueloInstancia vuelo,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo,
            final SubproblemaACO subproblema
    ) {
        final long esperaMinutos = Math.max(0L, Duration.between(tiempoActual, vuelo.getFechaSalida()).toMinutes());
        final long duracionMinutos = Math.max(
                1L,
                Duration.between(vuelo.getFechaSalida(), vuelo.getFechaLlegada()).toMinutes()
        );
        final boolean vueloDirecto = esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
        final double bonificacionDestino = vueloDirecto ? 6D : 1D;
        final double conectividad = 1D + contarSalidasFuturas(
                vuelo.getAeropuertoDestino(),
                vuelo.getFechaLlegada(),
                subproblema
        )
                / (double) MAX_CONECTIVIDAD_CONSIDERADA;
        double holgura = 1D;

        if (plazo != null && !vuelo.getFechaLlegada().isAfter(plazo)) {
            final long minutosHolgura = Math.max(0L, Duration.between(vuelo.getFechaLlegada(), plazo).toMinutes());
            holgura += Math.min(1440D, minutosHolgura) / 1440D;
        }

        return bonificacionDestino * conectividad * holgura / (1D + esperaMinutos / 60D + duracionMinutos / 60D);
    }

    private int actualizarEstadoTemporal(
            final VueloInstancia vuelo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final Aeropuerto destinoFinal
    ) {
        final String idVuelo = vuelo.getIdVueloInstancia();
        final int capacidadActualVuelo = capacidadRestanteVuelo.getOrDefault(idVuelo, 0);
        final int nuevaCapacidadVuelo = Math.max(0, capacidadActualVuelo - UNIDAD_MALETA);
        capacidadRestanteVuelo.put(idVuelo, nuevaCapacidadVuelo);

        if (!esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal)) {
            final String idAeropuerto = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            final int capacidadActualAlmacen = capacidadRestanteAlmacen.getOrDefault(idAeropuerto, 0);
            final int nuevaCapacidadAlmacen = Math.max(0, capacidadActualAlmacen - UNIDAD_MALETA);
            capacidadRestanteAlmacen.put(idAeropuerto, nuevaCapacidadAlmacen);
        }

        return nuevaCapacidadVuelo;
    }

    private VueloInstancia buscarMejorVueloDirecto(final Maleta maleta, final SubproblemaACO subproblema) {
        if (maleta == null || maleta.getPedido() == null) {
            return null;
        }

        final Pedido pedido = maleta.getPedido();
        final Aeropuerto origen = pedido.getAeropuertoOrigen();
        final Aeropuerto destino = pedido.getAeropuertoDestino();
        if (origen == null || destino == null) {
            return null;
        }

        final LocalDateTime tiempoDisponible = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        VueloInstancia mejorVuelo = null;
        final ArrayList<VueloInstancia> vuelosOrigen = subproblema.getVuelosDesde(obtenerIdAeropuerto(origen));

        for (int i = primerIndiceConSalidaNoAnterior(vuelosOrigen, tiempoDisponible); i < vuelosOrigen.size(); i++) {
            final VueloInstancia vuelo = vuelosOrigen.get(i);
            final boolean esDirecto = esMismoAeropuerto(vuelo.getAeropuertoOrigen(), origen)
                    && esMismoAeropuerto(vuelo.getAeropuertoDestino(), destino);
            if (!esDirecto) {
                continue;
            }
            if (vuelo.getFechaSalida().isBefore(tiempoDisponible)) {
                continue;
            }
            if (plazo != null && vuelo.getFechaLlegada().isAfter(plazo)) {
                continue;
            }
            if (mejorVuelo != null && !vuelo.getFechaLlegada().isBefore(mejorVuelo.getFechaLlegada())) {
                continue;
            }
            mejorVuelo = vuelo;
        }
        return mejorVuelo;
    }

    private void ordenarCandidatos(final ArrayList<VueloInstancia> candidatos, final FeromonasACO feromonas,
                                   final Maleta maleta, final Aeropuerto destinoFinal,
                                   final LocalDateTime tiempoActual, final LocalDateTime plazo,
                                   final SubproblemaACO subproblema) {
        candidatos.sort((primero, segundo) -> Double.compare(
                puntajeCandidato(segundo, feromonas, maleta, destinoFinal, tiempoActual, plazo, subproblema),
                puntajeCandidato(primero, feromonas, maleta, destinoFinal, tiempoActual, plazo, subproblema)
        ));
    }

    private double puntajeCandidato(final VueloInstancia candidato, final FeromonasACO feromonas, final Maleta maleta,
                                    final Aeropuerto destinoFinal, final LocalDateTime tiempoActual,
                                    final LocalDateTime plazo, final SubproblemaACO subproblema) {
        final double feromona = obtenerFeromona(feromonas, maleta, candidato);
        final double heuristica = calcularHeuristica(candidato, destinoFinal, tiempoActual, plazo, subproblema);
        return Math.pow(feromona, configuracion.getAlpha()) * Math.pow(heuristica, configuracion.getBeta());
    }

    private int contarSalidasFuturas(final Aeropuerto aeropuerto, final LocalDateTime tiempoReferencia,
                                     final SubproblemaACO subproblema) {
        final String idAeropuerto = obtenerIdAeropuerto(aeropuerto);
        if (idAeropuerto == null) {
            return 0;
        }
        final ArrayList<VueloInstancia> vuelos = subproblema.getVuelosDesde(idAeropuerto);
        if (vuelos.isEmpty()) {
            return 0;
        }

        final int inicio = primerIndiceConSalidaNoAnterior(vuelos, tiempoReferencia);
        return Math.min(MAX_CONECTIVIDAD_CONSIDERADA, vuelos.size() - inicio);
    }

    private boolean estaOrdenadoPorSalida(final ArrayList<VueloInstancia> vuelos) {
        for (int i = 1; i < vuelos.size(); i++) {
            final VueloInstancia anterior = vuelos.get(i - 1);
            final VueloInstancia actual = vuelos.get(i);
            final boolean fechasInvalidas = anterior == null
                    || actual == null
                    || anterior.getFechaSalida() == null
                    || actual.getFechaSalida() == null;
            if (fechasInvalidas) {
                continue;
            }
            if (actual.getFechaSalida().isBefore(anterior.getFechaSalida())) {
                return false;
            }
        }
        return true;
    }

    private int primerIndiceConSalidaNoAnterior(final ArrayList<VueloInstancia> vuelos,
                                                final LocalDateTime tiempoActual) {
        int izquierda = 0;
        int derecha = vuelos.size();
        while (izquierda < derecha) {
            final int mitad = (izquierda + derecha) >>> 1;
            final VueloInstancia vuelo = vuelos.get(mitad);
            final boolean salidaAnterior = vuelo != null
                    && vuelo.getFechaSalida() != null
                    && vuelo.getFechaSalida().isBefore(tiempoActual);
            if (salidaAnterior) {
                izquierda = mitad + 1;
                continue;
            }
            derecha = mitad;
        }
        return izquierda;
    }

    private Ruta crearRutaFactible(
            final Maleta maleta,
            final ArrayList<VueloInstancia> plan,
            final SubproblemaACO subproblema,
            final EstadoRuta estado
    ) {
        final Ruta ruta = new Ruta(
                "RUTA-" + maleta.getIdMaleta(),
                maleta.getIdMaleta(),
                calcularPlazoMaximoDias(maleta, subproblema),
                0D,
                plan,
                estado
        );
        ruta.calcularPlazo();
        return ruta;
    }

    private Ruta crearRutaNoFactible(
            final Maleta maleta,
            final ArrayList<VueloInstancia> plan,
            final SubproblemaACO subproblema
    ) {
        final String idMaleta = maleta == null ? "SIN_MALETA" : maleta.getIdMaleta();
        final double plazoMaximo = maleta == null ? Double.MAX_VALUE : calcularPlazoMaximoDias(maleta, subproblema);
        final Ruta ruta = new Ruta("RUTA-" + idMaleta, idMaleta, plazoMaximo, 0D, plan, ESTADO_NO_FACTIBLE);
        ruta.calcularPlazo();
        return ruta;
    }

    private double calcularPlazoMaximoDias(final Maleta maleta, final SubproblemaACO subproblema) {
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        final LocalDateTime tiempoDisponible = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        if (plazo == null || tiempoDisponible == null || plazo.isBefore(tiempoDisponible)) {
            return Double.MAX_VALUE;
        }
        return Duration.between(tiempoDisponible, plazo).toMinutes() / (24D * 60D);
    }

    private VueloInstancia clonarVueloInstanciaConCapacidad(final VueloInstancia vuelo, final int capacidadDisponible) {
        return new VueloInstancia(
                vuelo.getIdVueloInstancia(),
                vuelo.getCodigo(),
                vuelo.getVueloProgramado(),
                vuelo.getFechaOperacion(),
                vuelo.getFechaSalida(),
                vuelo.getFechaLlegada(),
                vuelo.getCapacidadMaxima(),
                capacidadDisponible,
                vuelo.getAeropuertoOrigen(),
                vuelo.getAeropuertoDestino(),
                vuelo.getEstado()
        );
    }

    private LocalDateTime obtenerTiempoDisponible(final Maleta maleta, final LocalDateTime inicioIntervalo) {
        final LocalDateTime fechaMaleta = maleta == null ? null : maleta.getFechaRegistro();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        final LocalDateTime fechaPedido = pedido == null ? null : pedido.getFechaRegistro();
        final LocalDateTime base = maximoTiempo(fechaMaleta, fechaPedido);
        return maximoTiempo(base, inicioIntervalo);
    }

    private double obtenerFeromona(final FeromonasACO feromonas, final Maleta maleta, final VueloInstancia vuelo) {
        if (feromonas == null) {
            return configuracion.getTau0();
        }
        return feromonas.obtener(maleta, vuelo);
    }

    private double limitarFeromona(final double valor) {
        final double valorMinimo = Math.max(0D, configuracion.getTauMin());
        final double valorMaximo = Math.max(valorMinimo, configuracion.getTauMax());
        return Math.max(valorMinimo, Math.min(valor, valorMaximo));
    }

    private VueloInstancia clonarVueloInstancia(final VueloInstancia vueloInstancia) {
        if (vueloInstancia == null) {
            return null;
        }
        return new VueloInstancia(
                vueloInstancia.getIdVueloInstancia(),
                vueloInstancia.getCodigo(),
                vueloInstancia.getVueloProgramado(),
                vueloInstancia.getFechaOperacion(),
                vueloInstancia.getFechaSalida(),
                vueloInstancia.getFechaLlegada(),
                vueloInstancia.getCapacidadMaxima(),
                vueloInstancia.getCapacidadDisponible(),
                vueloInstancia.getAeropuertoOrigen(),
                vueloInstancia.getAeropuertoDestino(),
                vueloInstancia.getEstado()
        );
    }

    private String obtenerIdAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto == null ? null : aeropuerto.getIdAeropuerto();
    }

    private boolean esMismoAeropuerto(final Aeropuerto primero, final Aeropuerto segundo) {
        final String idPrimero = obtenerIdAeropuerto(primero);
        final String idSegundo = obtenerIdAeropuerto(segundo);
        if (idPrimero == null || idSegundo == null) {
            return false;
        }
        return idPrimero.equals(idSegundo);
    }

    private LocalDateTime maximoTiempo(final LocalDateTime primero, final LocalDateTime segundo) {
        if (primero == null) {
            return segundo;
        }
        if (segundo == null) {
            return primero;
        }
        return primero.isAfter(segundo) ? primero : segundo;
    }

    private static final class EstadoPlanTemporal {
        private final String idAeropuerto;
        private final LocalDateTime tiempoActual;
        private final double costo;
        private final ArrayList<VueloInstancia> camino;
        private final Set<String> visitados;

        private EstadoPlanTemporal(final String idAeropuerto,
                                   final LocalDateTime tiempoActual,
                                   final double costo,
                                   final ArrayList<VueloInstancia> camino,
                                   final Set<String> visitados) {
            this.idAeropuerto = idAeropuerto;
            this.tiempoActual = tiempoActual;
            this.costo = costo;
            this.camino = camino;
            this.visitados = visitados;
        }

        private EstadoPlanTemporal avanzar(final String idSiguiente,
                                           final VueloInstancia vuelo,
                                           final double costoTramo) {
            final ArrayList<VueloInstancia> nuevoCamino = new ArrayList<>(camino.size() + 1);
            nuevoCamino.addAll(camino);
            nuevoCamino.add(vuelo);

            final Set<String> nuevosVisitados = new HashSet<>(visitados);
            nuevosVisitados.add(idSiguiente);
            return new EstadoPlanTemporal(
                    idSiguiente,
                    vuelo.getFechaLlegada(),
                    costo + costoTramo,
                    nuevoCamino,
                    nuevosVisitados
            );
        }

        private String getIdAeropuerto() {
            return idAeropuerto;
        }

        private LocalDateTime getTiempoActual() {
            return tiempoActual;
        }

        private double getCosto() {
            return costo;
        }

        private ArrayList<VueloInstancia> getCamino() {
            return camino;
        }

        private Set<String> getVisitados() {
            return visitados;
        }

        private ArrayList<VueloInstancia> clonarCamino() {
            return new ArrayList<>(camino);
        }
    }
}

