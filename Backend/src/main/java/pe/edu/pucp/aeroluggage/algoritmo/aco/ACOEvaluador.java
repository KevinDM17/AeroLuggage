package pe.edu.pucp.aeroluggage.algoritmo.aco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

final class ACOEvaluador {
    private static final EstadoRuta ESTADO_NO_FACTIBLE = EstadoRuta.FALLIDA;
    private static final EstadoRuta ESTADO_REPLANIFICADA = EstadoRuta.REPLANIFICADA;
    private static final int UNIDAD_MALETA = 1;
    private static final double COSTO_MINIMO = 0.000001D;

    private final ACOConfiguracion configuracion;

    ACOEvaluador(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion;
    }

    EvaluacionACO evaluarSolucion(final Solucion solucion, final SubproblemaACO subproblema) {
        double tiempoTotalDias = 0D;
        int incumplimientosPlazo = 0;
        int rutasFactibles = 0;
        int rutasNoFactibles = 0;
        int numeroReplanificaciones = 0;
        final Map<String, Integer> usoVuelos = new HashMap<>();
        final Map<String, Integer> usoAlmacenes = new HashMap<>();

        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new EvaluacionACO(
                    configuracion.getPenalizacionNoFactible(),
                    0D,
                    1,
                    0,
                    1,
                    0,
                    0,
                    0
            );
        }

        for (final Ruta ruta : solucion.getSubrutas()) {
            if (ruta == null) {
                continue;
            }

            final boolean rutaNoFactible = ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || ruta.getSubrutas().isEmpty();
            if (rutaNoFactible) {
                rutasNoFactibles++;
                incumplimientosPlazo++;
                continue;
            }

            rutasFactibles++;
            final double duracion = ruta.calcularPlazo();
            tiempoTotalDias += duracion;
            if (duracion > ruta.getPlazoMaximoDias()) {
                incumplimientosPlazo++;
            }
            if (ESTADO_REPLANIFICADA.equals(ruta.getEstado())) {
                numeroReplanificaciones++;
            }

            final List<VueloInstancia> subrutas = ruta.getSubrutas();
            for (int i = 0; i < subrutas.size(); i++) {
                final VueloInstancia subruta = subrutas.get(i);
                final String idVuelo = subruta.getIdVueloInstancia();
                usoVuelos.put(idVuelo, usoVuelos.getOrDefault(idVuelo, 0) + UNIDAD_MALETA);

                if (i == subrutas.size() - 1) {
                    continue;
                }
                final String idAeropuerto = subruta.getAeropuertoDestino().getIdAeropuerto();
                usoAlmacenes.put(idAeropuerto, usoAlmacenes.getOrDefault(idAeropuerto, 0) + UNIDAD_MALETA);
            }
        }

        int sobrecargaVuelos = 0;
        for (final Map.Entry<String, Integer> entry : usoVuelos.entrySet()) {
            final int capacidadBase = subproblema.getCapacidadRestanteVueloBase().getOrDefault(entry.getKey(), 0);
            sobrecargaVuelos += Math.max(0, entry.getValue() - capacidadBase);
        }

        double ocupacionAlmacenes = 0D;
        for (final Map.Entry<String, Integer> entry : usoAlmacenes.entrySet()) {
            final int capacidadBase = subproblema.getCapacidadRestanteAlmacenBase().getOrDefault(entry.getKey(), 0);
            if (capacidadBase > 0) {
                ocupacionAlmacenes += (double) entry.getValue() / capacidadBase;
            } else {
                ocupacionAlmacenes += entry.getValue();
            }
        }

        final double costo = tiempoTotalDias
                + rutasNoFactibles * configuracion.getPenalizacionNoFactible()
                + incumplimientosPlazo * configuracion.getPenalizacionIncumplimiento()
                + sobrecargaVuelos * configuracion.getPenalizacionSobrecargaVuelo()
                + ocupacionAlmacenes * configuracion.getPenalizacionSobrecargaAlmacen()
                + numeroReplanificaciones * configuracion.getPenalizacionReplanificacion();

        return new EvaluacionACO(
                costo,
                tiempoTotalDias,
                incumplimientosPlazo,
                rutasFactibles,
                rutasNoFactibles,
                sobrecargaVuelos,
                ocupacionAlmacenes,
                numeroReplanificaciones
        );
    }

    void aplicarActualizacionGlobalFeromona(
            final FeromonasACO feromonas,
            final Solucion mejorSolucionIntervalo,
            final EvaluacionACO mejorEvaluacionIntervalo
    ) {
        if (feromonas != null && !feromonas.isEmpty()) {
            feromonas.evaporarGlobal();
        }
        if (feromonas == null || mejorSolucionIntervalo == null || mejorEvaluacionIntervalo == null) {
            return;
        }

        final double delta = 1D / Math.max(COSTO_MINIMO, mejorEvaluacionIntervalo.getCosto());
        for (final Ruta ruta : mejorSolucionIntervalo.getSubrutas()) {
            for (final VueloInstancia subruta : ruta.getSubrutas()) {
                feromonas.reforzar(ruta.getIdMaleta(), subruta, delta);
            }
        }
    }

    void actualizarIndicadores(
            final ACOReporte reporte,
            final ArrayList<Ruta> planesConfirmados,
            final int intervaloActual,
            final EvaluacionACO evaluacion
    ) {
        reporte.setIntervalosProcesados(intervaloActual + 1);
        reporte.setPlanesConfirmados(planesConfirmados.size());
        if (evaluacion == null) {
            return;
        }
        reporte.setRutasFactibles(evaluacion.getRutasFactibles());
        reporte.setRutasNoFactibles(evaluacion.getRutasNoFactibles());
        reporte.setTiempoTotalDias(evaluacion.getTiempoTotalDias());
        reporte.setIncumplimientosPlazo(evaluacion.getIncumplimientosPlazo());
        reporte.setSobrecargaVuelos(evaluacion.getSobrecargaVuelos());
        reporte.setSobrecargaAlmacenes(evaluacion.getSobrecargaAlmacenes());
        reporte.setNumeroReplanificaciones(evaluacion.getNumeroReplanificaciones());
        reporte.setMejorCosto(evaluacion.getCosto());
    }

    FeromonasACO conservarYAdaptarFeromonas(
            final FeromonasACO feromonas,
            final Solucion mejorSolucionIntervalo,
            final EvaluacionACO mejorEvaluacionIntervalo,
            final ArrayList<String> eventos
    ) {
        final FeromonasACO feromonasAdaptadas = feromonas == null
                ? new FeromonasACO(configuracion)
                : feromonas.copiarConAdaptacion(1D - configuracion.getGamma());

        if (mejorSolucionIntervalo == null || mejorEvaluacionIntervalo == null) {
            return feromonasAdaptadas;
        }

        final double factorEventos = eventos == null || eventos.isEmpty() ? 1D : 1D + eventos.size();
        final double delta = configuracion.getGamma() * factorEventos
                / Math.max(COSTO_MINIMO, mejorEvaluacionIntervalo.getCosto());
        for (final Ruta ruta : mejorSolucionIntervalo.getSubrutas()) {
            for (final VueloInstancia subruta : ruta.getSubrutas()) {
                feromonasAdaptadas.reforzar(ruta.getIdMaleta(), subruta, delta);
            }
        }
        return feromonasAdaptadas;
    }

    boolean esMejorEvaluacion(final EvaluacionACO candidata, final EvaluacionACO actual) {
        if (candidata == null) {
            return false;
        }
        if (actual == null) {
            return true;
        }
        return candidata.getCosto() < actual.getCosto();
    }

    private double limitarFeromona(final double valor) {
        final double valorMinimo = Math.max(0D, configuracion.getTauMin());
        final double valorMaximo = Math.max(valorMinimo, configuracion.getTauMax());
        return Math.max(valorMinimo, Math.min(valor, valorMaximo));
    }
}
