package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;

public class ACO extends Metaheuristico {
    private final ACOConfiguracion configuracion;
    private final ACOPreparadorContexto preparadorContexto;
    private final ACOConstructorSoluciones constructorSoluciones;
    private final ACOEvaluador evaluador;
    private InstanciaProblema ultimaInstancia;
    private Solucion ultimaSolucion;
    private ACOReporte ultimoReporte;
    private double ultimoCosto;
    private FeromonasACO feromonas;

    public ACO() {
        this(new ACOConfiguracion());
    }

    public ACO(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion == null ? new ACOConfiguracion() : configuracion;
        this.preparadorContexto = new ACOPreparadorContexto(this.configuracion);
        this.constructorSoluciones = new ACOConstructorSoluciones(this.configuracion);
        this.evaluador = new ACOEvaluador(this.configuracion);
        this.ultimaSolucion = new Solucion();
        this.ultimoReporte = new ACOReporte();
        this.ultimoCosto = Double.POSITIVE_INFINITY;
        this.feromonas = new FeromonasACO(this.configuracion);
    }

    @Override
    public void ejecutar(final InstanciaProblema instancia) {
        ultimaInstancia = instancia;
        ultimaSolucion = new Solucion();
        ultimoReporte = new ACOReporte();
        ultimoCosto = Double.POSITIVE_INFINITY;
        feromonas = new FeromonasACO(configuracion);
        if (instancia == null || instancia.getMaletas().isEmpty()) {
            return;
        }

        final ArrayList<Ruta> planesConfirmados = new ArrayList<>();
        final LocalDateTime tiempoBase = preparadorContexto.obtenerTiempoBase(instancia);

        for (int t = 0; preparadorContexto.noTerminaHorizonteOperacion(t); t = preparadorContexto.avanzarIntervalo(t)) {
            final ArrayList<String> eventos = preparadorContexto.leerEventos(t);
            final SubproblemaACO subproblema = prepararSubproblema(
                    instancia,
                    planesConfirmados,
                    tiempoBase,
                    t,
                    eventos
            );
            if (subproblema.getMaletasPendientes().isEmpty()) {
                ultimoReporte.setIntervalosProcesados(t + 1);
                ultimoReporte.setPlanesConfirmados(planesConfirmados.size());
                continue;
            }

            Solucion mejorSolucionIntervalo = seleccionarMejorSolucionInicial(subproblema);
            EvaluacionACO mejorEvaluacionIntervalo = evaluador.evaluarSolucion(mejorSolucionIntervalo, subproblema);

            for (int iter = 1; iter <= configuracion.getMaxIter(); iter++) {
                for (int hormiga = 1; hormiga <= configuracion.getNAnts(); hormiga++) {
                    final Solucion solucionHormiga = constructorSoluciones.construirSolucionHormiga(
                            subproblema,
                            feromonas
                    );
                    final Solucion solucionReparada = constructorSoluciones.repararInconsistencias(
                            solucionHormiga,
                            subproblema
                    );
                    final Solucion solucionMejorada = constructorSoluciones.mejoraLocal(solucionReparada, subproblema);
                    final EvaluacionACO evaluacion = evaluador.evaluarSolucion(solucionMejorada, subproblema);

                    if (evaluador.esMejorEvaluacion(evaluacion, mejorEvaluacionIntervalo)) {
                        mejorSolucionIntervalo = constructorSoluciones.clonarSolucion(solucionMejorada);
                        mejorEvaluacionIntervalo = evaluacion;
                    }
                }
                evaluador.aplicarActualizacionGlobalFeromona(
                        feromonas,
                        mejorSolucionIntervalo,
                        mejorEvaluacionIntervalo
                );
            }

            final LocalDateTime ventanaCompromiso = preparadorContexto.siguienteIntervalo(
                    subproblema.getInicioIntervalo()
            );
            confirmarDecisionesProximas(planesConfirmados, mejorSolucionIntervalo, ventanaCompromiso);
            evaluador.actualizarIndicadores(ultimoReporte, planesConfirmados, t, mejorEvaluacionIntervalo);
            feromonas = evaluador.conservarYAdaptarFeromonas(
                    feromonas,
                    mejorSolucionIntervalo,
                    mejorEvaluacionIntervalo,
                    eventos
            );

            final EvaluacionACO evaluacionActual = Double.isFinite(ultimoCosto)
                    ? new EvaluacionACO(ultimoCosto, 0D, 0, 0, 0, 0, 0, 0)
                    : null;
            if (evaluador.esMejorEvaluacion(mejorEvaluacionIntervalo, evaluacionActual)) {
                ultimaSolucion = constructorSoluciones.clonarSolucion(mejorSolucionIntervalo);
                ultimoCosto = mejorEvaluacionIntervalo.getCosto();
            }
        }

        ultimaSolucion = completarRutasFaltantes(instancia, consolidarPlanes(planesConfirmados, ultimaSolucion));
        evaluar();
    }

    @Override
    public void evaluar() {
        if (ultimaInstancia == null) {
            return;
        }

        final ArrayList<VueloInstancia> vuelosDisponibles = preparadorContexto.actualizarVuelosDisponibles(
                ultimaInstancia.getVuelosInstancia(),
                new ArrayList<>()
        );
        final CapacidadesACO capacidades = preparadorContexto.recalcularCapacidades(
                vuelosDisponibles,
                ultimaInstancia.getAeropuertos()
        );
        final SubproblemaACO subproblema = preparadorContexto.construirSubproblema(
                ultimaInstancia.getMaletas(),
                vuelosDisponibles,
                capacidades,
                0,
                preparadorContexto.obtenerTiempoBase(ultimaInstancia)
        );
        final EvaluacionACO evaluacion = evaluador.evaluarSolucion(ultimaSolucion, subproblema);
        ultimoCosto = evaluacion.getCosto();
        ultimoReporte.setIntervalosProcesados(
                Math.max(ultimoReporte.getIntervalosProcesados(), configuracion.getNts())
        );
        ultimoReporte.setPlanesConfirmados(ultimaSolucion.getSubrutas().size());
        ultimoReporte.setRutasFactibles(evaluacion.getRutasFactibles());
        ultimoReporte.setRutasNoFactibles(evaluacion.getRutasNoFactibles());
        ultimoReporte.setTiempoTotalDias(evaluacion.getTiempoTotalDias());
        ultimoReporte.setIncumplimientosPlazo(evaluacion.getIncumplimientosPlazo());
        ultimoReporte.setSobrecargaVuelos(evaluacion.getSobrecargaVuelos());
        ultimoReporte.setSobrecargaAlmacenes(evaluacion.getSobrecargaAlmacenes());
        ultimoReporte.setNumeroReplanificaciones(evaluacion.getNumeroReplanificaciones());
        ultimoReporte.setMejorCosto(ultimoCosto);
    }

    public Solucion getUltimaSolucion() {
        return ultimaSolucion;
    }

    public ACOReporte getUltimoReporte() {
        return ultimoReporte;
    }

    public double getUltimoCosto() {
        return ultimoCosto;
    }

    public ACOConfiguracion getConfiguracion() {
        return configuracion;
    }

    private SubproblemaACO prepararSubproblema(
            final InstanciaProblema instancia,
            final ArrayList<Ruta> planesConfirmados,
            final LocalDateTime tiempoBase,
            final int intervaloActual,
            final ArrayList<String> eventos
    ) {
        final ArrayList<pe.edu.pucp.aeroluggage.dominio.entidades.Maleta> maletasPendientes =
                preparadorContexto.actualizarMaletasPendientes(
                        instancia.getMaletas(),
                        eventos,
                        planesConfirmados
                );
        final ArrayList<VueloInstancia> vuelosDisponibles = preparadorContexto.actualizarVuelosDisponibles(
                instancia.getVuelosInstancia(),
                eventos
        );
        final CapacidadesACO capacidades = preparadorContexto.recalcularCapacidades(
                vuelosDisponibles,
                instancia.getAeropuertos()
        );
        return preparadorContexto.construirSubproblema(
                maletasPendientes,
                vuelosDisponibles,
                capacidades,
                intervaloActual,
                tiempoBase
        );
    }

    private Solucion seleccionarMejorSolucionInicial(final SubproblemaACO subproblema) {
        Solucion mejorSolucion = null;
        EvaluacionACO mejorEvaluacion = null;
        final ArrayList<Solucion> poblacionInicial = constructorSoluciones.generarPoblacionInicial(
                subproblema,
                feromonas
        );

        for (final Solucion solucionInicial : poblacionInicial) {
            final Solucion solucionReparada = constructorSoluciones.repararInconsistencias(
                    solucionInicial,
                    subproblema
            );
            final Solucion solucionMejorada = constructorSoluciones.mejoraLocal(solucionReparada, subproblema);
            final EvaluacionACO evaluacionInicial = evaluador.evaluarSolucion(solucionMejorada, subproblema);
            if (evaluador.esMejorEvaluacion(evaluacionInicial, mejorEvaluacion)) {
                mejorSolucion = constructorSoluciones.clonarSolucion(solucionMejorada);
                mejorEvaluacion = evaluacionInicial;
            }
        }

        return mejorSolucion == null ? new Solucion() : mejorSolucion;
    }

    private void confirmarDecisionesProximas(
            final ArrayList<Ruta> planesConfirmados,
            final Solucion mejorSolucionIntervalo,
            final LocalDateTime ventanaCompromiso
    ) {
        if (mejorSolucionIntervalo == null || mejorSolucionIntervalo.getSubrutas().isEmpty()) {
            return;
        }

        final Set<String> yaConfirmadas = new HashSet<>();
        for (final Ruta ruta : planesConfirmados) {
            if (ruta == null || ruta.getIdMaleta() == null) {
                continue;
            }
            yaConfirmadas.add(ruta.getIdMaleta());
        }

        for (final Ruta rutaOriginal : mejorSolucionIntervalo.getSubrutas()) {
            if (rutaOriginal == null || rutaOriginal.getIdMaleta() == null) {
                continue;
            }
            if (yaConfirmadas.contains(rutaOriginal.getIdMaleta())) {
                continue;
            }

            final LocalDateTime primeraSalida = constructorSoluciones.obtenerPrimeraSalida(rutaOriginal);
            final boolean confirmarAhora = primeraSalida == null
                    || ventanaCompromiso == null
                    || !primeraSalida.isAfter(ventanaCompromiso);
            if (!confirmarAhora) {
                continue;
            }

            final Ruta rutaConfirmada = constructorSoluciones.clonarRuta(rutaOriginal);
            if (constructorSoluciones.esRutaFactible(rutaConfirmada)) {
                rutaConfirmada.setEstado("CONFIRMADA");
            }
            planesConfirmados.add(rutaConfirmada);
            yaConfirmadas.add(rutaConfirmada.getIdMaleta());
        }
    }

    private Solucion consolidarPlanes(final ArrayList<Ruta> planesConfirmados, final Solucion mejorSolucion) {
        final ArrayList<Ruta> rutasConsolidadas = new ArrayList<>();
        final Set<String> maletasConsolidadas = new HashSet<>();

        if (planesConfirmados != null) {
            for (final Ruta ruta : planesConfirmados) {
                if (ruta == null || ruta.getIdMaleta() == null || maletasConsolidadas.contains(ruta.getIdMaleta())) {
                    continue;
                }
                rutasConsolidadas.add(constructorSoluciones.clonarRuta(ruta));
                maletasConsolidadas.add(ruta.getIdMaleta());
            }
        }

        if (mejorSolucion != null) {
            for (final Ruta ruta : mejorSolucion.getSubrutas()) {
                if (ruta == null || ruta.getIdMaleta() == null || maletasConsolidadas.contains(ruta.getIdMaleta())) {
                    continue;
                }
                rutasConsolidadas.add(constructorSoluciones.clonarRuta(ruta));
                maletasConsolidadas.add(ruta.getIdMaleta());
            }
        }

        return new Solucion(rutasConsolidadas);
    }

    private Solucion completarRutasFaltantes(final InstanciaProblema instancia, final Solucion solucion) {
        final ArrayList<Ruta> rutasCompletas = new ArrayList<>();
        final Set<String> maletasConRuta = new HashSet<>();

        if (solucion != null) {
            for (final Ruta ruta : solucion.getSubrutas()) {
                if (ruta == null || ruta.getIdMaleta() == null) {
                    continue;
                }
                rutasCompletas.add(constructorSoluciones.clonarRuta(ruta));
                maletasConRuta.add(ruta.getIdMaleta());
            }
        }

        if (instancia == null) {
            return new Solucion(rutasCompletas);
        }

        for (final pe.edu.pucp.aeroluggage.dominio.entidades.Maleta maleta : instancia.getMaletas()) {
            if (maleta == null || maleta.getIdMaleta() == null || maletasConRuta.contains(maleta.getIdMaleta())) {
                continue;
            }
            final Ruta rutaSinPlan = new Ruta(
                    "RUTA-" + maleta.getIdMaleta(),
                    maleta.getIdMaleta(),
                    Double.MAX_VALUE,
                    0D,
                    new ArrayList<>(),
                    "NO_FACTIBLE"
            );
            rutasCompletas.add(rutaSinPlan);
            maletasConRuta.add(maleta.getIdMaleta());
        }

        return new Solucion(rutasCompletas);
    }
}
