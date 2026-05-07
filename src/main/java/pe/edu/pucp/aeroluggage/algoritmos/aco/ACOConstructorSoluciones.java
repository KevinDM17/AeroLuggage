package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final int MAX_VUELOS_EXPLORADOS_POR_ESTADO = 48;
    private static final int MAX_CONECTIVIDAD_CONSIDERADA = 6;
    private static final int UMBRAL_MALETAS_MEJORA_LOCAL = 4000;
    private static final double BONIFICACION_DESTINO_DIRECTO = 2D;
    private static final double BONIFICACION_DESTINO_NO_DIRECTO = 1D;
    private static final double FACTOR_PRESION_ALMACEN_MINIMO = 0.15D;
    private static final double FACTOR_ALEATORIO_MINIMO = 0.85D;
    private static final double RANGO_FACTOR_ALEATORIO = 0.30D;

    private final ACOConfiguracion configuracion;
    private final Random random;
    private long minutosConexion = 0L;
    private long tiempoRecojo = 0L;

    ACOConstructorSoluciones(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion;
        this.random = new Random(configuracion.getSemilla());
    }

    void setMinutosConexion(final long minutosConexion) {
        this.minutosConexion = minutosConexion;
    }

    void setTiempoRecojo(final long tiempoRecojo) {
        this.tiempoRecojo = tiempoRecojo;
    }

    ArrayList<Solucion> generarPoblacionInicial(
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas
    ) {
        final ArrayList<Solucion> poblacionInicial = new ArrayList<>();
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>(subproblema.getCapacidadRestanteVueloBase());
        final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen = CapacidadTemporalAlmacen.clonarMapa(
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
        final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen = CapacidadTemporalAlmacen.clonarMapa(
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
        if (subproblema.getMaletasPendientes().size() > UMBRAL_MALETAS_MEJORA_LOCAL) {
            return clonarSolucion(solucion);
        }

        final long inicioReconstruccion = System.nanoTime();
        final CapacidadesACO comprometida = reconstruirCapacidadesComprometidas(solucion, subproblema);
        registrarReconstruirCapacidades(System.nanoTime() - inicioReconstruccion);

        for (final Ruta rutaOriginal : solucion.getSubrutas()) {
            final Ruta ruta = clonarRuta(rutaOriginal);
            if (ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || ruta.getSubrutas().size() <= 1) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final Maleta maleta = subproblema.obtenerMaleta(ruta.getIdMaleta());
            final VueloInstancia vueloDirecto = buscarMejorVueloDirecto(
                    maleta,
                    subproblema,
                    comprometida.getCapacidadRestanteVuelo(),
                    comprometida.getCapacidadRestanteAlmacen()
            );
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

            final int capacidadComprometidaVuelo = comprometida.getCapacidadRestanteVuelo()
                    .getOrDefault(vueloDirecto.getIdVueloInstancia(), 0);
            final ArrayList<VueloInstancia> nuevaSubruta = new ArrayList<>();
            nuevaSubruta.add(clonarVueloInstanciaConCapacidad(
                    vueloDirecto,
                    Math.max(0, capacidadComprometidaVuelo - UNIDAD_MALETA)
            ));
            ruta.setSubrutas(nuevaSubruta);
            ruta.calcularPlazo();
            ruta.setEstado(ESTADO_REPLANIFICADA);
            rutasMejoradas.add(ruta);
        }

        return new Solucion(rutasMejoradas);
    }

    private CapacidadesACO reconstruirCapacidadesComprometidas(
            final Solucion solucion,
            final SubproblemaACO subproblema
    ) {
        final Map<String, Integer> capacidadVuelo = new HashMap<>(subproblema.getCapacidadRestanteVueloBase());
        final Map<String, CapacidadTemporalAlmacen> capacidadAlmacen =
                CapacidadTemporalAlmacen.clonarMapa(subproblema.getCapacidadRestanteAlmacenBase());
        if (solucion == null) {
            return new CapacidadesACO(capacidadVuelo, capacidadAlmacen);
        }
        for (final Ruta ruta : solucion.getSubrutas()) {
            if (ruta == null || ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final List<VueloInstancia> vuelos = ruta.getSubrutas();
            final String idOrigen = obtenerIdAeropuerto(vuelos.get(0).getAeropuertoOrigen());
            final CapacidadTemporalAlmacen capOrigen = capacidadAlmacen.get(idOrigen);
            if (capOrigen != null) {
                capOrigen.liberar(vuelos.get(0).getFechaSalida());
            }
            for (int i = 0; i < vuelos.size(); i++) {
                final VueloInstancia vuelo = vuelos.get(i);
                if (vuelo == null) {
                    continue;
                }
                final String idVuelo = vuelo.getIdVueloInstancia();
                if (idVuelo != null) {
                    capacidadVuelo.put(idVuelo,
                            Math.max(0, capacidadVuelo.getOrDefault(idVuelo, 0) - UNIDAD_MALETA));
                }
                if (vuelo.getFechaLlegada() == null) {
                    continue;
                }
                final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
                if (idDestino == null) {
                    continue;
                }
                final LocalDateTime llegada = vuelo.getFechaLlegada();
                final LocalDateTime liberacion = (i < vuelos.size() - 1)
                        ? vuelos.get(i + 1).getFechaSalida()
                        : llegada.plusMinutes(tiempoRecojo);
                final CapacidadTemporalAlmacen cap = capacidadAlmacen.get(idDestino);
                if (cap != null) {
                    cap.reservar(llegada, liberacion);
                }
            }
        }
        return new CapacidadesACO(capacidadVuelo, capacidadAlmacen);
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
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen,
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
        final LocalDateTime plazoBase = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        final LocalDateTime plazo = plazoBase;
        // Se pasan los mapas directamente: construirPlanTemporal solo escribe en ellos
        // cuando encuentra un camino (vía aplicarConsumoPlan). Si falla, devuelve null
        // sin haber modificado nada, por lo que la copia defensiva es innecesaria.
        final ArrayList<VueloInstancia> plan = construirPlanTemporal(
                maleta,
                subproblema,
                feromonas,
                actual,
                destino,
                tiempoActual,
                plazo,
                capacidadRestanteVuelo,
                capacidadRestanteAlmacen,
                modoCodicioso
        );
        if (plan == null || plan.isEmpty()) {
            return crearRutaNoFactible(maleta, new ArrayList<>(), subproblema);
        }
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
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen,
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
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen,
            final boolean modoCodicioso
    ) {
        final long inicioBusqueda = System.nanoTime();
        final String idOrigen = obtenerIdAeropuerto(origen);
        final String idDestino = obtenerIdAeropuerto(destino);
        if (idOrigen == null || idDestino == null || tiempoActual == null || plazo == null) {
            return null;
        }

        final PriorityQueue<EstadoPlanTemporal> frontera = new PriorityQueue<>(
                Comparator.comparingDouble(EstadoPlanTemporal::getCosto)
                        .thenComparing(EstadoPlanTemporal::getTiempoActual)
        );
        final Map<String, ArrayList<LocalDateTime>> mejoresLlegadasPorAeropuerto = new HashMap<>();
        frontera.add(new EstadoPlanTemporal(idOrigen, tiempoActual, 0D, null, null, 0));
        registrarLlegadaCompetitiva(mejoresLlegadasPorAeropuerto, idOrigen, tiempoActual);

        int estadosEvaluados = 0;
        long vuelosInspeccionados = 0L;
        while (!frontera.isEmpty() && estadosEvaluados < configuracion.getMaxEstadosBusquedaTemporal()) {
            estadosEvaluados++;
            final EstadoPlanTemporal estado = frontera.poll();
            if (estado.getIdAeropuerto().equals(idDestino)) {
                final ArrayList<VueloInstancia> plan = estado.reconstruirCamino();
                if (!planRespetaCapacidadAlmacen(plan, destino, capacidadRestanteAlmacen)) {
                    continue;
                }
                aplicarConsumoPlan(plan, destino, capacidadRestanteVuelo, capacidadRestanteAlmacen);
                reforzarFeromonaLocal(plan, maleta, feromonas);
                registrarConstruirPlanTemporal(System.nanoTime() - inicioBusqueda, estadosEvaluados, vuelosInspeccionados);
                return plan;
            }
            if (estado.getProfundidad() >= MAX_ESCALAS_RUTA) {
                continue;
            }

            final ArrayList<VueloInstancia> vuelos = subproblema.getVuelosDesde(estado.getIdAeropuerto());
            int vuelosExplorados = 0;
            for (int i = primerIndiceConSalidaNoAnterior(vuelos, estado.getTiempoActual().plusMinutes(minutosConexion));
                 i < vuelos.size() && vuelosExplorados < MAX_VUELOS_EXPLORADOS_POR_ESTADO;
                 i++) {
                final VueloInstancia vuelo = vuelos.get(i);
                vuelosExplorados++;
                vuelosInspeccionados++;
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
                if (!aceptaLlegadaCompetitiva(mejoresLlegadasPorAeropuerto, idSiguiente, vuelo.getFechaLlegada())) {
                    continue;
                }

                registrarLlegadaCompetitiva(mejoresLlegadasPorAeropuerto, idSiguiente, vuelo.getFechaLlegada());
                frontera.add(estado.avanzar(
                        idSiguiente,
                        vuelo,
                        calcularCostoBusqueda(vuelo, maleta, feromonas, destino, estado.getTiempoActual(),
                                plazo, subproblema, modoCodicioso)
                ));
            }
        }

        registrarConstruirPlanTemporal(System.nanoTime() - inicioBusqueda, estadosEvaluados, vuelosInspeccionados);
        return null;
    }

    private boolean aceptaLlegadaCompetitiva(
            final Map<String, ArrayList<LocalDateTime>> mejoresLlegadasPorAeropuerto,
            final String idAeropuerto,
            final LocalDateTime llegada
    ) {
        if (idAeropuerto == null || llegada == null) {
            return false;
        }
        final ArrayList<LocalDateTime> llegadas = mejoresLlegadasPorAeropuerto.get(idAeropuerto);
        if (llegadas == null || llegadas.size() < configuracion.getMaxLlegadasCompetitivasPorAeropuerto()) {
            return true;
        }
        final LocalDateTime peorLlegadaAceptada = llegadas.get(llegadas.size() - 1);
        return llegada.isBefore(peorLlegadaAceptada);
    }

    private void registrarLlegadaCompetitiva(
            final Map<String, ArrayList<LocalDateTime>> mejoresLlegadasPorAeropuerto,
            final String idAeropuerto,
            final LocalDateTime llegada
    ) {
        if (idAeropuerto == null || llegada == null) {
            return;
        }
        final ArrayList<LocalDateTime> llegadas = mejoresLlegadasPorAeropuerto.computeIfAbsent(
                idAeropuerto,
                key -> new ArrayList<>()
        );
        llegadas.add(llegada);
        llegadas.sort(LocalDateTime::compareTo);
        while (llegadas.size() > configuracion.getMaxLlegadasCompetitivasPorAeropuerto()) {
            llegadas.remove(llegadas.size() - 1);
        }
    }

    private boolean esVueloFactibleParaBusqueda(
            final VueloInstancia vuelo,
            final Aeropuerto destinoFinal,
            final EstadoPlanTemporal estado,
            final LocalDateTime plazo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen
    ) {
        if (vuelo == null || vuelo.getFechaSalida() == null || vuelo.getFechaLlegada() == null) {
            return false;
        }
        if (vuelo.getFechaSalida().isBefore(estado.getTiempoActual().plusMinutes(minutosConexion))
                || vuelo.getFechaLlegada().isAfter(plazo)) {
            return false;
        }
        if (capacidadRestanteVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0) < UNIDAD_MALETA) {
            registrarRechazoCapacidadVuelo();
            return false;
        }

        final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
        if (idDestino == null || estado.contieneAeropuerto(idDestino)) {
            return false;
        }

        // Verificar capacidad temporal: tanto en escalas como en destino final (tiempoRecojo).
        // Sin info del aeropuerto: permitir solo si es destino final.
        final CapacidadTemporalAlmacen cap = capacidadRestanteAlmacen.get(idDestino);
        if (cap == null) {
            return esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
        }
        if (esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal)) {
            final boolean destinoDisponible = cap.puedeReservar(
                    vuelo.getFechaLlegada(),
                    vuelo.getFechaLlegada().plusMinutes(tiempoRecojo)
            );
            if (!destinoDisponible) {
                registrarRechazoAlmacenDestino(idDestino);
            }
            return destinoDisponible;
        }
        // Escala intermedia: verificar que haya al menos 1 slot disponible a la llegada.
        // La duración exacta de la escala no se conoce aún; planRespetaCapacidadAlmacen()
        // hace la verificación completa con el intervalo real antes de confirmar el plan.
        final boolean disponibleEscala = cap.disponibleEn(vuelo.getFechaLlegada()) >= UNIDAD_MALETA;
        if (!disponibleEscala) {
            registrarRechazoAlmacenEscala(idDestino);
        }
        return disponibleEscala;
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
                subproblema,
                null
        );
        final double factorAleatorio = FACTOR_ALEATORIO_MINIMO + random.nextDouble() * RANGO_FACTOR_ALEATORIO;
        return (esperaMinutos + duracionMinutos) * factorAleatorio + 1D / Math.max(0.000001D, puntaje);
    }

    private void aplicarConsumoPlan(final List<VueloInstancia> plan,
                                    final Aeropuerto destinoFinal,
                                    final Map<String, Integer> capacidadRestanteVuelo,
                                    final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen) {
        // Liberar el aeropuerto de origen: la maleta estaba físicamente allí (contada en
        // maletasActuales → capacidadBase) y parte en el primer vuelo, devolviendo 1 slot.
        if (!plan.isEmpty()) {
            final String idOrigen = obtenerIdAeropuerto(plan.get(0).getAeropuertoOrigen());
            final CapacidadTemporalAlmacen capOrigen = capacidadRestanteAlmacen.get(idOrigen);
            if (capOrigen != null) {
                capOrigen.liberar(plan.get(0).getFechaSalida());
            }
        }
        for (int i = 0; i < plan.size(); i++) {
            final VueloInstancia vuelo = plan.get(i);

            // Descuentar capacidad del vuelo.
            final String idVuelo = vuelo.getIdVueloInstancia();
            final int nuevaCapacidadVuelo = Math.max(
                    0, capacidadRestanteVuelo.getOrDefault(idVuelo, 0) - UNIDAD_MALETA
            );
            capacidadRestanteVuelo.put(idVuelo, nuevaCapacidadVuelo);
            plan.set(i, clonarVueloInstanciaConCapacidad(vuelo, nuevaCapacidadVuelo));

            // Registrar intervalo de ocupación en el aeropuerto destino.
            final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            if (idDestino == null) {
                continue;
            }
            final LocalDateTime llegada = vuelo.getFechaLlegada();
            final LocalDateTime liberacion;
            if (i < plan.size() - 1) {
                // Escala intermedia: ocupa almacén desde llegada hasta que sale el siguiente vuelo.
                liberacion = plan.get(i + 1).getFechaSalida();
            } else {
                // Destino final: ocupa almacén hasta que el cliente recoge la maleta.
                liberacion = llegada == null ? null : llegada.plusMinutes(tiempoRecojo);
            }
            final CapacidadTemporalAlmacen cap = capacidadRestanteAlmacen.get(idDestino);
            if (cap != null) {
                cap.reservar(llegada, liberacion);
            }
            registrarRutaAceptadaAeropuerto(idDestino);
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
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen,
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

        ordenarCandidatos(
                candidatos,
                feromonas,
                maleta,
                destino,
                tiempoActual,
                plazo,
                subproblema,
                capacidadRestanteAlmacen
        );
        final int maxCandidatos = modoCodicioso ? MAX_CANDIDATOS_CODICIOSOS : MAX_CANDIDATOS_PROBABILISTICOS;
        final int limite = Math.min(maxCandidatos, candidatos.size());

        for (int i = 0; i < limite; i++) {
            final VueloInstancia siguienteVuelo = candidatos.get(i);
            final Map<String, Integer> capacidadVueloCopia = new HashMap<>(capacidadRestanteVuelo);
            // Copia superficial: CapacidadTemporalAlmacen no se escribe en la recursión,
            // solo se lee (disponibleEn). Si se encontrase un camino se registrarán
            // los intervalos al final vía aplicarConsumoPlan.
            final Map<String, CapacidadTemporalAlmacen> capacidadAlmacenCopia =
                    CapacidadTemporalAlmacen.clonarMapa(capacidadRestanteAlmacen);
            final Set<String> visitadosCopia = new HashSet<>(visitados);

            // Solo descuenta capacidad del vuelo; los intervalos de almacén se registran
            // en aplicarConsumoPlan cuando se conoce el camino completo.
            final String idVueloCandidato = siguienteVuelo.getIdVueloInstancia();
            final int nuevaCapacidad = Math.max(
                    0, capacidadVueloCopia.getOrDefault(idVueloCandidato, 0) - UNIDAD_MALETA
            );
            capacidadVueloCopia.put(idVueloCandidato, nuevaCapacidad);

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

            final ArrayList<VueloInstancia> plan = new ArrayList<>(sufijo.size() + 1);
            plan.add(clonarVueloInstanciaConCapacidad(siguienteVuelo, nuevaCapacidad));
            plan.addAll(sufijo);
            if (!reservarCapacidadAlmacenPlan(plan, destino, capacidadAlmacenCopia)) {
                continue;
            }

            capacidadRestanteVuelo.clear();
            capacidadRestanteVuelo.putAll(capacidadVueloCopia);
            capacidadRestanteAlmacen.clear();
            capacidadRestanteAlmacen.putAll(capacidadAlmacenCopia);
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
            final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen,
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

        final LocalDateTime tiempoMinSalida = tiempoActual.plusMinutes(minutosConexion);
        for (int i = primerIndiceConSalidaNoAnterior(vuelos, tiempoMinSalida);
             i < vuelos.size() && candidatos.size() < MAX_VUELOS_EXPLORADOS_POR_ESTADO;
             i++) {
            final VueloInstancia vuelo = vuelos.get(i);
            if (vuelo.getFechaSalida().isBefore(tiempoMinSalida)) {
                continue;
            }
            if (plazo != null && vuelo.getFechaLlegada().isAfter(plazo)) {
                continue;
            }

            final int capacidadVuelo = capacidadRestanteVuelo.getOrDefault(vuelo.getIdVueloInstancia(), 0);
            if (capacidadVuelo < UNIDAD_MALETA) {
                registrarRechazoCapacidadVuelo();
                continue;
            }

            final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            if (idDestino == null || visitados.contains(idDestino)) {
                continue;
            }

            final boolean esDestinoFinal = esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
            final CapacidadTemporalAlmacen cap = capacidadRestanteAlmacen.get(idDestino);
            if (cap == null) {
                if (!esDestinoFinal) {
                    continue;
                }
            } else if (esDestinoFinal) {
                final boolean destinoFinalDisponible = cap.puedeReservar(
                        vuelo.getFechaLlegada(),
                        vuelo.getFechaLlegada().plusMinutes(tiempoRecojo)
                );
                if (!destinoFinalDisponible) {
                    registrarRechazoAlmacenDestino(idDestino);
                    continue;
                }
            } else if (cap.disponibleEn(vuelo.getFechaLlegada()) < 1) {
                registrarRechazoAlmacenEscala(idDestino);
                continue;
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
        final double bonificacionDestino = vueloDirecto
                ? BONIFICACION_DESTINO_DIRECTO
                : BONIFICACION_DESTINO_NO_DIRECTO;
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


    private VueloInstancia buscarMejorVueloDirecto(
            final Maleta maleta,
            final SubproblemaACO subproblema,
            final Map<String, Integer> capacidadVueloComprometida,
            final Map<String, CapacidadTemporalAlmacen> capacidadAlmacenComprometida
    ) {
        final long inicioBusqueda = System.nanoTime();
        if (maleta == null || maleta.getPedido() == null) {
            registrarBuscarVueloDirecto(System.nanoTime() - inicioBusqueda);
            return null;
        }

        final Pedido pedido = maleta.getPedido();
        final Aeropuerto origen = pedido.getAeropuertoOrigen();
        final Aeropuerto destino = pedido.getAeropuertoDestino();
        if (origen == null || destino == null) {
            registrarBuscarVueloDirecto(System.nanoTime() - inicioBusqueda);
            return null;
        }

        final LocalDateTime tiempoDisponible = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        VueloInstancia mejorVuelo = null;
        final ArrayList<VueloInstancia> vuelosOrigen = subproblema.getVuelosDirectos(
                obtenerIdAeropuerto(origen),
                obtenerIdAeropuerto(destino)
        );

        for (int i = primerIndiceConSalidaNoAnterior(vuelosOrigen, tiempoDisponible); i < vuelosOrigen.size(); i++) {
            final VueloInstancia vuelo = vuelosOrigen.get(i);
            if (vuelo.getFechaSalida().isBefore(tiempoDisponible)) {
                continue;
            }
            if (plazo != null && vuelo.getFechaLlegada().isAfter(plazo)) {
                continue;
            }
            final int capVuelo = capacidadVueloComprometida.getOrDefault(vuelo.getIdVueloInstancia(), 0);
            if (capVuelo < UNIDAD_MALETA) {
                registrarRechazoCapacidadVuelo();
                continue;
            }
            final CapacidadTemporalAlmacen cap = capacidadAlmacenComprometida.get(
                    obtenerIdAeropuerto(vuelo.getAeropuertoDestino())
            );
            if (cap != null) {
                final boolean destinoDisponible = cap.puedeReservar(
                        vuelo.getFechaLlegada(),
                        vuelo.getFechaLlegada().plusMinutes(tiempoRecojo)
                );
                if (!destinoDisponible) {
                    registrarRechazoAlmacenDestino(obtenerIdAeropuerto(vuelo.getAeropuertoDestino()));
                    continue;
                }
            }
            if (mejorVuelo != null && !vuelo.getFechaLlegada().isBefore(mejorVuelo.getFechaLlegada())) {
                continue;
            }
            mejorVuelo = vuelo;
        }
        registrarBuscarVueloDirecto(System.nanoTime() - inicioBusqueda);
        return mejorVuelo;
    }

    private boolean planRespetaCapacidadAlmacen(final List<VueloInstancia> plan,
                                                final Aeropuerto destinoFinal,
                                                final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen) {
        final long inicioValidacion = System.nanoTime();
        if (plan == null || plan.isEmpty()) {
            registrarPlanRespetaCapacidad(System.nanoTime() - inicioValidacion);
            return false;
        }
        for (int i = 0; i < plan.size(); i++) {
            final VueloInstancia vuelo = plan.get(i);
            if (vuelo == null || vuelo.getAeropuertoDestino() == null || vuelo.getFechaLlegada() == null) {
                continue;
            }
            final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            if (idDestino == null) {
                continue;
            }
            final CapacidadTemporalAlmacen cap = capacidadRestanteAlmacen.get(idDestino);
            if (cap == null) {
                if (!esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal)) {
                    registrarPlanRespetaCapacidad(System.nanoTime() - inicioValidacion);
                    return false;
                }
                continue;
            }
            final LocalDateTime liberacion = i < plan.size() - 1
                    ? plan.get(i + 1).getFechaSalida()
                    : vuelo.getFechaLlegada().plusMinutes(tiempoRecojo);
            if (!cap.puedeReservar(vuelo.getFechaLlegada(), liberacion)) {
                if (i == plan.size() - 1) {
                    registrarRechazoAlmacenDestino(idDestino);
                } else {
                    registrarRechazoAlmacenEscala(idDestino);
                }
                registrarPlanRespetaCapacidad(System.nanoTime() - inicioValidacion);
                return false;
            }
        }
        registrarPlanRespetaCapacidad(System.nanoTime() - inicioValidacion);
        return true;
    }

    private boolean reservarCapacidadAlmacenPlan(final List<VueloInstancia> plan,
                                                 final Aeropuerto destinoFinal,
                                                 final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen) {
        for (int i = 0; i < plan.size(); i++) {
            final VueloInstancia vuelo = plan.get(i);
            if (vuelo == null || vuelo.getAeropuertoDestino() == null || vuelo.getFechaLlegada() == null) {
                continue;
            }
            final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            if (idDestino == null) {
                continue;
            }
            final CapacidadTemporalAlmacen cap = capacidadRestanteAlmacen.get(idDestino);
            if (cap == null) {
                if (!esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal)) {
                    return false;
                }
                continue;
            }
            final LocalDateTime liberacion;
            if (i < plan.size() - 1) {
                liberacion = plan.get(i + 1).getFechaSalida();
            } else {
                liberacion = vuelo.getFechaLlegada().plusMinutes(tiempoRecojo);
            }
            if (!cap.puedeReservar(vuelo.getFechaLlegada(), liberacion)) {
                return false;
            }
            cap.reservar(vuelo.getFechaLlegada(), liberacion);
        }
        return true;
    }

    private void ordenarCandidatos(final ArrayList<VueloInstancia> candidatos, final FeromonasACO feromonas,
                                   final Maleta maleta, final Aeropuerto destinoFinal,
                                   final LocalDateTime tiempoActual, final LocalDateTime plazo,
                                   final SubproblemaACO subproblema,
                                   final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen) {
        final Map<String, Double> puntajes = new HashMap<>(candidatos.size() * 2);
        for (final VueloInstancia candidato : candidatos) {
            puntajes.put(
                    candidato.getIdVueloInstancia(),
                    puntajeCandidato(
                            candidato,
                            feromonas,
                            maleta,
                            destinoFinal,
                            tiempoActual,
                            plazo,
                            subproblema,
                            capacidadRestanteAlmacen
                    )
            );
        }
        candidatos.sort((primero, segundo) -> Double.compare(
                puntajes.get(segundo.getIdVueloInstancia()),
                puntajes.get(primero.getIdVueloInstancia())
        ));
    }

    private double puntajeCandidato(final VueloInstancia candidato, final FeromonasACO feromonas, final Maleta maleta,
                                    final Aeropuerto destinoFinal, final LocalDateTime tiempoActual,
                                    final LocalDateTime plazo, final SubproblemaACO subproblema,
                                    final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen) {
        final double feromona = obtenerFeromona(feromonas, maleta, candidato);
        final double heuristica = calcularHeuristica(candidato, destinoFinal, tiempoActual, plazo, subproblema);
        return Math.pow(feromona, configuracion.getAlpha())
                * Math.pow(heuristica, configuracion.getBeta())
                * calcularFactorPresionAlmacen(candidato, capacidadRestanteAlmacen);
    }

    private double calcularFactorPresionAlmacen(final VueloInstancia vuelo,
                                                final Map<String, CapacidadTemporalAlmacen> capacidadRestanteAlmacen) {
        if (vuelo == null || vuelo.getAeropuertoDestino() == null || vuelo.getFechaLlegada() == null
                || capacidadRestanteAlmacen == null) {
            return 1D;
        }
        final CapacidadTemporalAlmacen capacidad = capacidadRestanteAlmacen.get(
                obtenerIdAeropuerto(vuelo.getAeropuertoDestino())
        );
        if (capacidad == null || capacidad.getCapacidadBase() <= 0) {
            return 1D;
        }
        final int disponible = capacidad.disponibleEn(vuelo.getFechaLlegada());
        final double proporcion = disponible / (double) capacidad.getCapacidadBase();
        return Math.max(FACTOR_PRESION_ALMACEN_MINIMO, Math.min(1D, proporcion));
    }

    private void registrarConstruirPlanTemporal(final long nanos, final long estadosEvaluados, final long vuelos) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarConstruirPlanTemporal(nanos, estadosEvaluados, vuelos);
        }
    }

    private void registrarPlanRespetaCapacidad(final long nanos) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarPlanRespetaCapacidad(nanos);
        }
    }

    private void registrarReconstruirCapacidades(final long nanos) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarReconstruirCapacidades(nanos);
        }
    }

    private void registrarBuscarVueloDirecto(final long nanos) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarBuscarVueloDirecto(nanos);
        }
    }

    private void registrarRechazoCapacidadVuelo() {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarRechazoCapacidadVuelo();
        }
    }

    private void registrarRechazoAlmacenDestino(final String idAeropuerto) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarRechazoAlmacenDestino(idAeropuerto);
        }
    }

    private void registrarRechazoAlmacenEscala(final String idAeropuerto) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarRechazoAlmacenEscala(idAeropuerto);
        }
    }

    private void registrarRutaAceptadaAeropuerto(final String idAeropuerto) {
        final ACOMetricas metricas = ACOMetricasContexto.obtener();
        if (metricas != null) {
            metricas.registrarRutaAceptadaAeropuerto(idAeropuerto);
        }
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
        final LocalDateTime plazoBase = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        final LocalDateTime plazo = plazoBase;
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
        private final EstadoPlanTemporal padre;
        private final VueloInstancia vueloTomado;
        private final int profundidad;

        private EstadoPlanTemporal(final String idAeropuerto,
                                   final LocalDateTime tiempoActual,
                                   final double costo,
                                   final EstadoPlanTemporal padre,
                                   final VueloInstancia vueloTomado,
                                   final int profundidad) {
            this.idAeropuerto = idAeropuerto;
            this.tiempoActual = tiempoActual;
            this.costo = costo;
            this.padre = padre;
            this.vueloTomado = vueloTomado;
            this.profundidad = profundidad;
        }

        private EstadoPlanTemporal avanzar(final String idSiguiente,
                                           final VueloInstancia vuelo,
                                           final double costoTramo) {
            return new EstadoPlanTemporal(
                    idSiguiente,
                    vuelo.getFechaLlegada(),
                    costo + costoTramo,
                    this,
                    vuelo,
                    profundidad + 1
            );
        }

        // Recorre la cadena de padres para detectar ciclos sin copiar conjuntos.
        // El camino máximo es MAX_ESCALAS_RUTA (8 nodos), por lo que el coste es O(1) acotado.
        private boolean contieneAeropuerto(final String id) {
            EstadoPlanTemporal cursor = this;
            while (cursor != null) {
                if (cursor.idAeropuerto.equals(id)) {
                    return true;
                }
                cursor = cursor.padre;
            }
            return false;
        }

        private ArrayList<VueloInstancia> reconstruirCamino() {
            final ArrayList<VueloInstancia> camino = new ArrayList<>(profundidad);
            EstadoPlanTemporal cursor = this;
            while (cursor.vueloTomado != null) {
                camino.add(cursor.vueloTomado);
                cursor = cursor.padre;
            }
            Collections.reverse(camino);
            return camino;
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

        private int getProfundidad() {
            return profundidad;
        }
    }
}

