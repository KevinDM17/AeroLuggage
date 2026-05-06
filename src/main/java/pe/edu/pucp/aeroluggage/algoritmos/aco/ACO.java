package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Metaheuristico;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public class ACO extends Metaheuristico {
    private static final int MIN_ITERACIONES_SIN_MEJORA = 3;
    private static final int DIVISOR_ITERACIONES_SIN_MEJORA = 4;

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
        if (instancia == null) {
            return;
        }
        ultimaInstancia = instancia;
        constructorSoluciones.setMinutosConexion(instancia.getMinutosConexion());
        constructorSoluciones.setTiempoRecojo(instancia.getTiempoRecojo());
        ultimaSolucion = new Solucion();
        ultimoReporte = new ACOReporte();
        ultimoCosto = Double.POSITIVE_INFINITY;
        feromonas = new FeromonasACO(configuracion);
        if (instancia.getMaletas().isEmpty()) {
            return;
        }

        final LocalDateTime tiempoBase = preparadorContexto.obtenerTiempoBase(instancia);
        final ArrayList<pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia> vuelosDisponibles =
                preparadorContexto.actualizarVuelosDisponibles(instancia.getVuelosInstancia(), new ArrayList<>());
        final CapacidadesACO capacidadesBase = preparadorContexto.recalcularCapacidades(
                instancia,
                vuelosDisponibles,
                instancia.getAeropuertos()
        );
        final SubproblemaACO subproblema = prepararSubproblema(
                instancia,
                tiempoBase,
                vuelosDisponibles,
                capacidadesBase
        );
        if (subproblema.getMaletasPendientes().isEmpty()) {
            ultimoReporte.setIntervalosProcesados(1);
            ultimoReporte.setPlanesConfirmados(0);
            return;
        }

        Solucion mejorSolucionCiclo = seleccionarMejorSolucionInicial(subproblema);
        EvaluacionACO mejorEvaluacionCiclo = evaluador.evaluarSolucion(mejorSolucionCiclo, subproblema);

        int iteracionesSinMejora = 0;
        final int maxIteracionesSinMejora = Math.max(
                MIN_ITERACIONES_SIN_MEJORA,
                configuracion.getMaxIter() / DIVISOR_ITERACIONES_SIN_MEJORA
        );
        final int maxIteraciones = Math.max(1, configuracion.getMaxIter());
        final int hormigas = Math.max(1, configuracion.getNAnts());

        for (int iter = 1; iter <= maxIteraciones; iter++) {
            boolean huboMejora = false;
            for (int hormiga = 1; hormiga <= hormigas; hormiga++) {
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
                if (evaluador.esMejorEvaluacion(evaluacion, mejorEvaluacionCiclo)) {
                    mejorSolucionCiclo = constructorSoluciones.clonarSolucion(solucionMejorada);
                    mejorEvaluacionCiclo = evaluacion;
                    huboMejora = true;
                }
            }
            evaluador.aplicarActualizacionGlobalFeromona(
                    feromonas,
                    mejorSolucionCiclo,
                    mejorEvaluacionCiclo
            );
            feromonas = evaluador.conservarYAdaptarFeromonas(
                    feromonas,
                    mejorSolucionCiclo,
                    mejorEvaluacionCiclo,
                    new ArrayList<>()
            );
            if (huboMejora) {
                iteracionesSinMejora = 0;
                continue;
            }
            iteracionesSinMejora++;
            if (iteracionesSinMejora >= maxIteracionesSinMejora) {
                break;
            }
        }

        ultimaSolucion = completarRutasFaltantes(instancia, mejorSolucionCiclo);
        evaluar();
    }

    @Override
    public void evaluar() {
        if (ultimaInstancia == null) {
            return;
        }

        final ArrayList<pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia> vuelosDisponibles =
                preparadorContexto.actualizarVuelosDisponibles(
                        ultimaInstancia.getVuelosInstancia(),
                        new ArrayList<>()
                );
        final CapacidadesACO capacidades = preparadorContexto.recalcularCapacidades(
                ultimaInstancia,
                vuelosDisponibles,
                ultimaInstancia.getAeropuertos()
        );
        final SubproblemaACO subproblema = preparadorContexto.construirSubproblema(
                ultimaInstancia.getMaletas(),
                vuelosDisponibles,
                capacidades,
                preparadorContexto.obtenerTiempoBase(ultimaInstancia)
        );
        final EvaluacionACO evaluacion = evaluador.evaluarSolucion(ultimaSolucion, subproblema);
        ultimoCosto = evaluacion.getCosto();
        ultimoReporte.setIntervalosProcesados(1);
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
            final LocalDateTime tiempoBase,
            final ArrayList<pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia> vuelosDisponibles,
            final CapacidadesACO capacidades
    ) {
        return preparadorContexto.construirSubproblema(
                instancia.getMaletas(),
                vuelosDisponibles,
                capacidades,
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
                    EstadoRuta.FALLIDA
            );
            rutasCompletas.add(rutaSinPlan);
            maletasConRuta.add(maleta.getIdMaleta());
        }

        return new Solucion(rutasCompletas);
    }
}
