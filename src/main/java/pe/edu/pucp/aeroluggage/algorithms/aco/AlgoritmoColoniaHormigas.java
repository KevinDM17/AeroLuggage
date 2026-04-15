package pe.edu.pucp.aeroluggage.algorithms.aco;

import pe.edu.pucp.aeroluggage.algorithms.Asignacion;
import pe.edu.pucp.aeroluggage.algorithms.Individuo;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Maleta;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class AlgoritmoColoniaHormigas {
    private static final double VALOR_POR_DEFECTO = 1.0D;
    private static final double PENALIZACION_SIN_VUELO = 1_000.0D;
    private static final int CANTIDAD_MALETAS_POR_DEFECTO = 1;
    private static final int PRIORIDAD_POR_DEFECTO = 1;

    private final InstanciaProblema instanciaProblema;
    private final Random random;
    private final FeromonaACO feromonaACO;
    private final HeuristicaACO heuristicaACO;
    private final EvaluadorACO evaluadorACO;
    private final ContextoACO contextoACO;

    private SolucionACO ultimaSolucionACO;
    private SubproblemaACO ultimoSubproblemaACO;

    public AlgoritmoColoniaHormigas(final InstanciaProblema instanciaProblema, final int numHormigas,
        final int numIteraciones, final double tasaEvaporacion, final double alpha, final double beta,
        final double feromonaInicial, final double depositoFeromona) {
        this.instanciaProblema = instanciaProblema;
        this.random = new Random();
        this.feromonaACO = new FeromonaACO();
        this.heuristicaACO = new HeuristicaACO();
        this.evaluadorACO = new EvaluadorACO();
        this.contextoACO = construirContexto(instanciaProblema, numHormigas, numIteraciones, tasaEvaporacion, alpha,
            beta, depositoFeromona);
        this.feromonaACO.setValorInicial(feromonaInicial > 0.0D ? feromonaInicial : VALOR_POR_DEFECTO);
        this.feromonaACO.setDepositoFeromona(depositoFeromona > 0.0D ? depositoFeromona : VALOR_POR_DEFECTO);
    }

    public Individuo ejecutar() {
        this.ultimoSubproblemaACO = construirSubproblema();
        this.feromonaACO.inicializar(
            this.ultimoSubproblemaACO.getMaletasPendientes(),
            this.ultimoSubproblemaACO.getVuelosDisponibles()
        );

        SolucionACO mejorSolucion = null;
        for (int iteracion = 0; iteracion < Math.max(contextoACO.getMaxIteracionesPorIntervalo(), 1); iteracion++) {
            final List<SolucionACO> solucionesIteracion = new ArrayList<>();
            for (int hormiga = 0; hormiga < Math.max(contextoACO.getNumeroHormigas(), 1); hormiga++) {
                final SolucionACO solucionACO = construirSolucion();
                evaluarSolucion(solucionACO);
                solucionesIteracion.add(solucionACO);
                if (mejorSolucion == null || solucionACO.getCostoTotal() < mejorSolucion.getCostoTotal()) {
                    mejorSolucion = solucionACO;
                }
            }

            this.feromonaACO.evaporar(obtenerRho());
            this.feromonaACO.reforzar(solucionesIteracion);
        }

        this.ultimaSolucionACO = mejorSolucion == null ? new SolucionACO() : mejorSolucion;
        return convertirIndividuo(this.ultimaSolucionACO);
    }

    public Map<String, Map<String, Double>> getFeromonas() {
        final Map<String, Map<String, Double>> copiaFeromonas = new HashMap<>();
        for (final Map.Entry<String, Map<String, Double>> entrada : this.feromonaACO.getMatrizFeromona().entrySet()) {
            copiaFeromonas.put(entrada.getKey(), new HashMap<>(entrada.getValue()));
        }
        return copiaFeromonas;
    }

    public SolucionACO getUltimaSolucionACO() {
        return ultimaSolucionACO;
    }

    public SubproblemaACO getUltimoSubproblemaACO() {
        return ultimoSubproblemaACO;
    }

    public ContextoACO getContextoACO() {
        return contextoACO;
    }

    private ContextoACO construirContexto(final InstanciaProblema instanciaProblema, final int numHormigas,
        final int numIteraciones, final double tasaEvaporacion, final double alpha, final double beta,
        final double depositoFeromona) {
        final ContextoACO nuevoContexto = new ContextoACO();
        nuevoContexto.setAeropuertos(instanciaProblema == null ? new ArrayList<>() : instanciaProblema.getAeropuertos());
        nuevoContexto.setVuelosDisponibles(instanciaProblema == null ? new ArrayList<>() : instanciaProblema.getVuelos());
        nuevoContexto.setNumeroHormigas(Math.max(numHormigas, 1));
        nuevoContexto.setMaxIteracionesPorIntervalo(Math.max(numIteraciones, 1));
        nuevoContexto.setNumeroIntervalosTiempo(1);
        nuevoContexto.setAlpha(alpha > 0.0D ? alpha : VALOR_POR_DEFECTO);
        nuevoContexto.setBeta(beta > 0.0D ? beta : 2.0D);
        nuevoContexto.setRho(tasaEvaporacion > 0.0D ? tasaEvaporacion : 0.1D);
        nuevoContexto.setGamma(tasaEvaporacion > 0.0D ? tasaEvaporacion : 0.1D);
        nuevoContexto.setDepositoFeromona(depositoFeromona > 0.0D ? depositoFeromona : VALOR_POR_DEFECTO);
        return nuevoContexto;
    }

    private SubproblemaACO construirSubproblema() {
        return new SubproblemaACO(
            construirMaletasPendientes(),
            construirVuelosOperacion(),
            0
        );
    }

    private List<MaletaPendienteACO> construirMaletasPendientes() {
        final List<MaletaPendienteACO> maletasPendientes = new ArrayList<>();
        if (this.instanciaProblema == null) {
            return maletasPendientes;
        }

        for (final MaletaProcesada maletaProcesada : this.instanciaProblema.getMaletasProcesadas()) {
            if (maletaProcesada == null) {
                continue;
            }

            final Maleta maleta = new Maleta(maletaProcesada.getIdMaleta(), maletaProcesada.getIdEnvio(), "PENDIENTE");
            final Date ahora = new Date();
            final long plazoMilisegundos = Math.max(maletaProcesada.getPlazoMaximoDias(), 1) * 24L * 60L * 60L * 1000L;
            final Date plazoMaximo = new Date(ahora.getTime() + plazoMilisegundos);
            maletasPendientes.add(new MaletaPendienteACO(
                maleta,
                maletaProcesada.getIdAeropuertoSalida(),
                maletaProcesada.getIdAeropuertoLlegada(),
                ahora,
                plazoMaximo,
                CANTIDAD_MALETAS_POR_DEFECTO,
                PRIORIDAD_POR_DEFECTO
            ));
        }

        return maletasPendientes;
    }

    private List<VueloOperacionACO> construirVuelosOperacion() {
        final List<VueloOperacionACO> vuelosOperacion = new ArrayList<>();
        if (this.instanciaProblema == null) {
            return vuelosOperacion;
        }

        for (final Vuelo vuelo : this.instanciaProblema.getVuelos()) {
            if (vuelo == null) {
                continue;
            }

            final boolean disponible = vuelo.getCapacidadDisponible() > 0
                && (vuelo.getEstado() == null || !"CANCELADO".equalsIgnoreCase(vuelo.getEstado()));
            vuelosOperacion.add(new VueloOperacionACO(vuelo, null, null, disponible, 0));
        }

        return vuelosOperacion;
    }

    private SolucionACO construirSolucion() {
        final SolucionACO solucionACO = new SolucionACO();
        final List<PlanMaletaACO> asignaciones = new ArrayList<>();
        final Map<String, Integer> cargaPorVuelo = new HashMap<>();

        for (final MaletaPendienteACO maletaPendienteACO : this.ultimoSubproblemaACO.getMaletasPendientes()) {
            asignaciones.add(construirAsignacion(maletaPendienteACO, cargaPorVuelo));
        }

        solucionACO.setAsignaciones(asignaciones);
        return solucionACO;
    }

    private PlanMaletaACO construirAsignacion(final MaletaPendienteACO maletaPendienteACO,
        final Map<String, Integer> cargaPorVuelo) {
        final List<VueloOperacionACO> vuelosValidos = obtenerVuelosValidosParaMaleta(maletaPendienteACO, cargaPorVuelo);
        if (vuelosValidos.isEmpty()) {
            return new PlanMaletaACO(
                maletaPendienteACO.getMaleta().getIdMaleta(),
                null,
                null,
                null,
                false,
                PENALIZACION_SIN_VUELO
            );
        }

        final VueloOperacionACO vueloSeleccionado =
            seleccionarVueloProbabilisticamente(maletaPendienteACO, vuelosValidos, cargaPorVuelo);
        if (vueloSeleccionado == null || vueloSeleccionado.getVuelo() == null) {
            return new PlanMaletaACO(
                maletaPendienteACO.getMaleta().getIdMaleta(),
                null,
                null,
                null,
                false,
                PENALIZACION_SIN_VUELO
            );
        }

        final int cantidad = Math.max(maletaPendienteACO.getCantidad(), CANTIDAD_MALETAS_POR_DEFECTO);
        final String idVuelo = vueloSeleccionado.getVuelo().getIdVuelo();
        cargaPorVuelo.put(idVuelo, cargaPorVuelo.getOrDefault(idVuelo, 0) + cantidad);

        final VueloOperacionACO vueloAsignado = new VueloOperacionACO(
            vueloSeleccionado.getVuelo(),
            vueloSeleccionado.getIdAeropuertoOrigen(),
            vueloSeleccionado.getIdAeropuertoDestino(),
            vueloSeleccionado.isDisponible(),
            cantidad
        );
        return new PlanMaletaACO(
            maletaPendienteACO.getMaleta().getIdMaleta(),
            idVuelo,
            null,
            vueloAsignado,
            true,
            calcularCostoAsignacion(maletaPendienteACO, vueloSeleccionado, cargaPorVuelo)
        );
    }

    private List<VueloOperacionACO> obtenerVuelosValidosParaMaleta(final MaletaPendienteACO maletaPendienteACO,
        final Map<String, Integer> cargaPorVuelo) {
        final List<VueloOperacionACO> vuelosValidos = new ArrayList<>();
        for (final VueloOperacionACO vueloOperacionACO : this.ultimoSubproblemaACO.getVuelosDisponibles()) {
            if (!esVueloValido(maletaPendienteACO, vueloOperacionACO, cargaPorVuelo)) {
                continue;
            }

            vuelosValidos.add(vueloOperacionACO);
        }
        return vuelosValidos;
    }

    private boolean esVueloValido(final MaletaPendienteACO maletaPendienteACO, final VueloOperacionACO vueloOperacionACO,
        final Map<String, Integer> cargaPorVuelo) {
        if (maletaPendienteACO == null || vueloOperacionACO == null || vueloOperacionACO.getVuelo() == null) {
            return false;
        }

        if (!vueloOperacionACO.isDisponible()) {
            return false;
        }

        final int cantidad = Math.max(maletaPendienteACO.getCantidad(), CANTIDAD_MALETAS_POR_DEFECTO);
        final int cargaActual = cargaPorVuelo.getOrDefault(vueloOperacionACO.getVuelo().getIdVuelo(), 0);
        final boolean respetaCapacidad = cargaActual + cantidad <= vueloOperacionACO.getVuelo().getCapacidadMaxima();
        if (!respetaCapacidad) {
            return false;
        }

        final boolean respetaSalida =
            maletaPendienteACO.getTiempoDisponible() == null
                || vueloOperacionACO.getVuelo().getFechaSalida() == null
                || !vueloOperacionACO.getVuelo().getFechaSalida().before(maletaPendienteACO.getTiempoDisponible());
        if (!respetaSalida) {
            return false;
        }

        return maletaPendienteACO.getPlazoMaximoEntrega() == null
            || vueloOperacionACO.getVuelo().getFechaLlegada() == null
            || !vueloOperacionACO.getVuelo().getFechaLlegada().after(maletaPendienteACO.getPlazoMaximoEntrega());
    }

    private VueloOperacionACO seleccionarVueloProbabilisticamente(final MaletaPendienteACO maletaPendienteACO,
        final List<VueloOperacionACO> vuelosValidos, final Map<String, Integer> cargaPorVuelo) {
        final Map<VueloOperacionACO, Double> pesos = new LinkedHashMap<>();
        double sumaPesos = 0.0D;
        final String idMaleta = maletaPendienteACO.getMaleta().getIdMaleta();

        for (final VueloOperacionACO vueloOperacionACO : vuelosValidos) {
            final String idVuelo = vueloOperacionACO.getVuelo().getIdVuelo();
            final double tau = this.feromonaACO.obtenerValor(idMaleta, idVuelo);
            final double eta = calcularHeuristica(maletaPendienteACO, vueloOperacionACO, cargaPorVuelo);
            final double peso =
                Math.pow(tau, this.contextoACO.getAlpha()) * Math.pow(eta, this.contextoACO.getBeta());
            pesos.put(vueloOperacionACO, peso);
            sumaPesos += peso;
        }

        if (sumaPesos <= 0.0D) {
            return vuelosValidos.get(this.random.nextInt(vuelosValidos.size()));
        }

        final double umbral = this.random.nextDouble() * sumaPesos;
        double acumulado = 0.0D;
        for (final Map.Entry<VueloOperacionACO, Double> entry : pesos.entrySet()) {
            acumulado += entry.getValue();
            if (acumulado >= umbral) {
                return entry.getKey();
            }
        }

        return vuelosValidos.get(vuelosValidos.size() - 1);
    }

    private double calcularHeuristica(final MaletaPendienteACO maletaPendienteACO,
        final VueloOperacionACO vueloOperacionACO, final Map<String, Integer> cargaPorVuelo) {
        final double tiempoEspera = calcularTiempoEspera(maletaPendienteACO, vueloOperacionACO);
        final double tiempoVuelo = calcularTiempoVuelo(vueloOperacionACO);
        final double capacidadRemanente = calcularCapacidadRemanente(vueloOperacionACO, maletaPendienteACO, cargaPorVuelo);
        final double urgencia = calcularUrgencia(maletaPendienteACO, vueloOperacionACO);
        return this.heuristicaACO.calcularValor(tiempoEspera, tiempoVuelo, capacidadRemanente, urgencia);
    }

    private void evaluarSolucion(final SolucionACO solucionACO) {
        this.evaluadorACO.evaluar(
            solucionACO,
            this.ultimoSubproblemaACO.getVuelosDisponibles(),
            construirCapacidadAlmacen()
        );
    }

    private Individuo convertirIndividuo(final SolucionACO solucionACO) {
        final List<Asignacion> asignaciones = new ArrayList<>();
        if (solucionACO != null && solucionACO.getAsignaciones() != null) {
            for (final PlanMaletaACO planMaletaACO : solucionACO.getAsignaciones()) {
                if (planMaletaACO == null || planMaletaACO.getIdMaleta() == null || planMaletaACO.getIdVueloAsignado() == null) {
                    continue;
                }

                asignaciones.add(new Asignacion(planMaletaACO.getIdMaleta(), planMaletaACO.getIdVueloAsignado()));
            }
        }

        final double fitness = solucionACO == null ? Double.POSITIVE_INFINITY : solucionACO.getCostoTotal();
        return new Individuo(asignaciones, fitness);
    }

    private Map<String, Integer> construirCapacidadAlmacen() {
        final Map<String, Integer> capacidadPorAeropuerto = new HashMap<>();
        if (this.instanciaProblema == null) {
            return capacidadPorAeropuerto;
        }

        for (final Aeropuerto aeropuerto : this.instanciaProblema.getAeropuertos()) {
            if (aeropuerto == null || aeropuerto.getIdAeropuerto() == null) {
                continue;
            }

            capacidadPorAeropuerto.put(
                aeropuerto.getIdAeropuerto(),
                Math.max(aeropuerto.getCapacidadAlmacen() - aeropuerto.getMaletasActuales(), 0)
            );
        }
        return capacidadPorAeropuerto;
    }

    private double obtenerRho() {
        return this.contextoACO.getRho() > 0.0D ? this.contextoACO.getRho() : 0.1D;
    }

    private double calcularTiempoEspera(final MaletaPendienteACO maletaPendienteACO,
        final VueloOperacionACO vueloOperacionACO) {
        if (maletaPendienteACO.getTiempoDisponible() == null || vueloOperacionACO.getVuelo().getFechaSalida() == null) {
            return 0.0D;
        }

        final long diferencia = vueloOperacionACO.getVuelo().getFechaSalida().getTime()
            - maletaPendienteACO.getTiempoDisponible().getTime();
        return Math.max(diferencia, 0L) / 60_000.0D;
    }

    private double calcularTiempoVuelo(final VueloOperacionACO vueloOperacionACO) {
        if (vueloOperacionACO.getVuelo().getFechaSalida() == null || vueloOperacionACO.getVuelo().getFechaLlegada() == null) {
            return 0.0D;
        }

        final long diferencia = vueloOperacionACO.getVuelo().getFechaLlegada().getTime()
            - vueloOperacionACO.getVuelo().getFechaSalida().getTime();
        return Math.max(diferencia, 0L) / 60_000.0D;
    }

    private double calcularCapacidadRemanente(final VueloOperacionACO vueloOperacionACO,
        final MaletaPendienteACO maletaPendienteACO, final Map<String, Integer> cargaPorVuelo) {
        final int cantidad = Math.max(maletaPendienteACO.getCantidad(), CANTIDAD_MALETAS_POR_DEFECTO);
        final int cargaActual = cargaPorVuelo.getOrDefault(vueloOperacionACO.getVuelo().getIdVuelo(), 0);
        final int remanente = vueloOperacionACO.getVuelo().getCapacidadMaxima() - cargaActual - cantidad;
        return Math.max(remanente, 0) / (double) Math.max(vueloOperacionACO.getVuelo().getCapacidadMaxima(), 1);
    }

    private double calcularUrgencia(final MaletaPendienteACO maletaPendienteACO,
        final VueloOperacionACO vueloOperacionACO) {
        if (maletaPendienteACO.getPlazoMaximoEntrega() == null || vueloOperacionACO.getVuelo().getFechaLlegada() == null) {
            return maletaPendienteACO.getPrioridad();
        }

        final long diferencia = maletaPendienteACO.getPlazoMaximoEntrega().getTime()
            - vueloOperacionACO.getVuelo().getFechaLlegada().getTime();
        final double penalizacionPlazo = diferencia < 0L ? Math.abs(diferencia) / 60_000.0D : 0.0D;
        return penalizacionPlazo + maletaPendienteACO.getPrioridad();
    }

    private double calcularCostoAsignacion(final MaletaPendienteACO maletaPendienteACO,
        final VueloOperacionACO vueloOperacionACO, final Map<String, Integer> cargaPorVuelo) {
        return calcularTiempoEspera(maletaPendienteACO, vueloOperacionACO)
            + calcularTiempoVuelo(vueloOperacionACO)
            + calcularUrgencia(maletaPendienteACO, vueloOperacionACO)
            + (1.0D - calcularCapacidadRemanente(vueloOperacionACO, maletaPendienteACO, cargaPorVuelo));
    }
}
