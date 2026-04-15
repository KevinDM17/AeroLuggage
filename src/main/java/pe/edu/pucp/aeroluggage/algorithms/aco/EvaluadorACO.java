package pe.edu.pucp.aeroluggage.algorithms.aco;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluadorACO {
    private static final double PENALIZACION_NO_FACTIBLE = 10_000.0D;
    private static final double PENALIZACION_SOBRECARGA = 1_000.0D;

    private double pesoTiempoTotal;
    private double pesoIncumplimientosPlazo;
    private double pesoSobrecargaVuelos;
    private double pesoSobrecargaAlmacenes;
    private double pesoAsignacionesNoFactibles;

    public EvaluadorACO() {
        this.pesoTiempoTotal = 1.0D;
        this.pesoIncumplimientosPlazo = 1.0D;
        this.pesoSobrecargaVuelos = 1.0D;
        this.pesoSobrecargaAlmacenes = 1.0D;
        this.pesoAsignacionesNoFactibles = 1.0D;
    }

    public double evaluar(final SolucionACO solucionACO, final List<VueloOperacionACO> vuelosDisponibles,
        final Map<String, Integer> capacidadAlmacenPorAeropuerto) {
        if (solucionACO == null) {
            return Double.POSITIVE_INFINITY;
        }

        final Map<String, Integer> cargaPorVuelo = new HashMap<>();
        final Map<String, Integer> cargaPorAeropuerto = new HashMap<>();
        double tiempoTotal = 0.0D;
        int incumplimientos = 0;
        int sobrecargaVuelos = 0;
        int sobrecargaAlmacenes = 0;
        int noFactibles = 0;

        final Map<String, VueloOperacionACO> vuelosPorId = construirIndiceVuelos(vuelosDisponibles);
        for (final PlanMaletaACO planMaletaACO : solucionACO.getAsignaciones()) {
            if (planMaletaACO == null || planMaletaACO.getVueloAsignado() == null
                || planMaletaACO.getVueloAsignado().getVuelo() == null) {
                noFactibles++;
                tiempoTotal += PENALIZACION_NO_FACTIBLE;
                continue;
            }

            final VueloOperacionACO vueloOperacionACO =
                vuelosPorId.getOrDefault(planMaletaACO.getIdVueloAsignado(), planMaletaACO.getVueloAsignado());
            final int cantidad = planMaletaACO.getVueloAsignado().getCapacidadSolicitada();
            final String idVuelo = vueloOperacionACO.getVuelo().getIdVuelo();
            final int nuevaCargaVuelo = cargaPorVuelo.getOrDefault(idVuelo, 0) + cantidad;
            cargaPorVuelo.put(idVuelo, nuevaCargaVuelo);
            if (nuevaCargaVuelo > vueloOperacionACO.getVuelo().getCapacidadMaxima()) {
                sobrecargaVuelos++;
                tiempoTotal += PENALIZACION_SOBRECARGA;
            }

            final String idAeropuertoDestino = vueloOperacionACO.getIdAeropuertoDestino();
            if (idAeropuertoDestino != null) {
                final int nuevaCargaAeropuerto = cargaPorAeropuerto.getOrDefault(idAeropuertoDestino, 0) + cantidad;
                cargaPorAeropuerto.put(idAeropuertoDestino, nuevaCargaAeropuerto);
                final Integer capacidadAlmacen = capacidadAlmacenPorAeropuerto.get(idAeropuertoDestino);
                if (capacidadAlmacen != null && nuevaCargaAeropuerto > capacidadAlmacen) {
                    sobrecargaAlmacenes++;
                    tiempoTotal += PENALIZACION_SOBRECARGA;
                }
            }

            tiempoTotal += planMaletaACO.getCostoAsignacion();
            if (!planMaletaACO.isFactible()) {
                incumplimientos++;
                noFactibles++;
                tiempoTotal += PENALIZACION_NO_FACTIBLE;
            }
        }

        solucionACO.setTiempoTotal(tiempoTotal);
        solucionACO.setIncumplimientosDePlazo(incumplimientos);
        solucionACO.setSobrecargaDeVuelos(sobrecargaVuelos);
        solucionACO.setSobrecargaDeAlmacenes(sobrecargaAlmacenes);
        solucionACO.setAsignacionesNoFactibles(noFactibles);

        final double costo =
            (solucionACO.getTiempoTotal() * pesoTiempoTotal)
                + (solucionACO.getIncumplimientosDePlazo() * pesoIncumplimientosPlazo)
                + (solucionACO.getSobrecargaDeVuelos() * pesoSobrecargaVuelos)
                + (solucionACO.getSobrecargaDeAlmacenes() * pesoSobrecargaAlmacenes)
                + (solucionACO.getAsignacionesNoFactibles() * pesoAsignacionesNoFactibles);
        solucionACO.setCostoTotal(costo);
        return costo;
    }

    private Map<String, VueloOperacionACO> construirIndiceVuelos(final List<VueloOperacionACO> vuelosDisponibles) {
        final Map<String, VueloOperacionACO> indice = new HashMap<>();
        if (vuelosDisponibles == null) {
            return indice;
        }

        for (final VueloOperacionACO vueloOperacionACO : vuelosDisponibles) {
            if (vueloOperacionACO == null || vueloOperacionACO.getVuelo() == null) {
                continue;
            }

            indice.put(vueloOperacionACO.getVuelo().getIdVuelo(), vueloOperacionACO);
        }
        return indice;
    }

    @Override
    public String toString() {
        return "EvaluadorACO{"
            + "pesoTiempoTotal=" + pesoTiempoTotal
            + ", pesoIncumplimientosPlazo=" + pesoIncumplimientosPlazo
            + ", pesoSobrecargaVuelos=" + pesoSobrecargaVuelos
            + ", pesoSobrecargaAlmacenes=" + pesoSobrecargaAlmacenes
            + ", pesoAsignacionesNoFactibles=" + pesoAsignacionesNoFactibles
            + '}';
    }
}
