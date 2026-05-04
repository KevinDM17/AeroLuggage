package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.common.CalculadorSemaforo;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public final class FuncionCosto {

    private FuncionCosto() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static double calcularCostoSolucion(final Solucion solucion, final InstanciaProblema instancia,
                                               final ParametrosGA params) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()) {
            return params.getPesoNoEnrutadas() * 10;
        }

        final Map<String, Maleta> maletasPorId = instancia.getMaletasPorId();
        int maletasNoEnrutadas = 0;
        double duracionTotalHoras = 0.0;
        double sumaEscalas = 0.0;
        int rutasValidas = 0;
        final Map<String, Integer> cargaPorVuelo = new HashMap<>();
        final Map<String, Integer> cargaPorAeropuerto = new HashMap<>();

        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null) {
                maletasNoEnrutadas++;
                continue;
            }
            final Maleta maleta = maletasPorId.get(ruta.getIdMaleta());
            final List<VueloInstancia> subrutas = ruta.getSubrutas();

            if (subrutas == null || subrutas.isEmpty()
                    || ruta.getEstado() == EstadoRuta.FALLIDA
                    || esRutaParcial(ruta, maleta)) {
                maletasNoEnrutadas++;
                continue;
            }

            for (final VueloInstancia vuelo : subrutas) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                if (vuelo.getFechaSalida() != null && vuelo.getFechaLlegada() != null) {
                    duracionTotalHoras += Duration.between(
                            vuelo.getFechaSalida(), vuelo.getFechaLlegada()).toMinutes() / 60.0;
                }
                cargaPorVuelo.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
                final String idOrigen = vuelo.getAeropuertoOrigen() != null
                        ? vuelo.getAeropuertoOrigen().getIdAeropuerto() : null;
                final String idDestino = vuelo.getAeropuertoDestino() != null
                        ? vuelo.getAeropuertoDestino().getIdAeropuerto() : null;
                if (idOrigen != null) {
                    cargaPorAeropuerto.merge(idOrigen, 1, Integer::sum);
                }
                if (idDestino != null) {
                    cargaPorAeropuerto.merge(idDestino, 1, Integer::sum);
                }
            }
            sumaEscalas += Math.max(0, subrutas.size() - 1);
            rutasValidas++;
        }

        final double overflowVuelos = calcularOverflowVuelos(cargaPorVuelo, instancia);
        final double[] metricasAlmacenes = calcularMetricasAlmacenes(cargaPorAeropuerto, instancia);
        final double overflowAlmacenes = metricasAlmacenes[0];
        final double ocupacionPromedioAlmacenes = metricasAlmacenes[1];
        final double promedioEscalas = rutasValidas > 0 ? sumaEscalas / rutasValidas : 0.0;
        final double ocupacionPromedio = calcularOcupacionPromedio(cargaPorVuelo, instancia);

        // --- Capa 1: duras ---
        final double pesoNoEnrutadas = params.getPesoNoEnrutadas();
        final double pesoAeropuertosOverflow = params.getPesoAeropuertosOverflow();
        final double pesoVuelosOverflow = params.getPesoVuelosOverflow();
        double costoTotal = pesoNoEnrutadas * maletasNoEnrutadas;
        costoTotal += pesoAeropuertosOverflow * overflowAlmacenes;
        costoTotal += pesoVuelosOverflow * overflowVuelos;
        // --- Capa 2: calidad ---
        costoTotal += 1.0 * duracionTotalHoras;
        costoTotal += 5.0 * promedioEscalas;

        solucion.setCostoTotal(costoTotal);
        solucion.setMaletasEntregadasATiempo(rutasValidas);
        solucion.setMaletasIncumplidas(maletasNoEnrutadas);
        solucion.setOcupacionPromedioVuelos(ocupacionPromedio);
        solucion.setOcupacionPromedioAlmacenes(ocupacionPromedioAlmacenes);
        solucion.setOverflowAlmacenes(overflowAlmacenes);
        solucion.setFactible(maletasNoEnrutadas == 0 && overflowVuelos == 0.0 && overflowAlmacenes == 0.0);
        solucion.setSemaforo(CalculadorSemaforo.clasificarGlobal(solucion, instancia, params));
        return costoTotal;
    }

    private static boolean esRutaParcial(final Ruta ruta, final Maleta maleta) {
        if (ruta == null || maleta == null || maleta.getPedido() == null) {
            return false;
        }
        final List<VueloInstancia> subrutas = ruta.getSubrutas();
        if (subrutas == null || subrutas.isEmpty()) {
            return false;
        }
        return !ultimoVueloCoherente(subrutas, maleta.getPedido().getAeropuertoDestino());
    }

    private static boolean ultimoVueloCoherente(final List<VueloInstancia> subrutas, final Aeropuerto destino) {
        if (subrutas.isEmpty() || destino == null) {
            return false;
        }
        final Aeropuerto fin = subrutas.get(subrutas.size() - 1).getAeropuertoDestino();
        return fin != null && fin.getIdAeropuerto() != null
                && fin.getIdAeropuerto().equals(destino.getIdAeropuerto());
    }

    private static double calcularOcupacionPromedio(final Map<String, Integer> cargaPorVuelo,
                                                    final InstanciaProblema instancia) {
        if (instancia == null || instancia.getVueloInstancias() == null
                || instancia.getVueloInstancias().isEmpty()) {
            return 0.0;
        }
        double suma = 0.0;
        int cuenta = 0;
        for (final VueloInstancia v : instancia.getVueloInstancias()) {
            if (v == null || v.getIdVueloInstancia() == null || v.getCapacidadMaxima() <= 0) {
                continue;
            }
            final int carga = cargaPorVuelo.getOrDefault(v.getIdVueloInstancia(), 0);
            suma += Math.min(1.0, carga / (double) v.getCapacidadMaxima());
            cuenta++;
        }
        return cuenta > 0 ? suma / cuenta : 0.0;
    }

    private static double calcularOverflowVuelos(final Map<String, Integer> cargaPorVuelo,
                                                 final InstanciaProblema instancia) {
        if (cargaPorVuelo.isEmpty() || instancia == null) {
            return 0.0;
        }
        final Map<String, VueloInstancia> vuelos = instancia.getVuelosPorId();
        double overflow = 0.0;
        for (final Map.Entry<String, Integer> entry : cargaPorVuelo.entrySet()) {
            final VueloInstancia vuelo = vuelos.get(entry.getKey());
            if (vuelo == null) {
                continue;
            }
            final int exceso = entry.getValue() - Math.max(0, vuelo.getCapacidadDisponible());
            if (exceso > 0) {
                overflow += exceso;
            }
        }
        return overflow;
    }

    // Retorna [overflowAlmacenes, ocupacionPromedioAlmacenes].
    private static double[] calcularMetricasAlmacenes(final Map<String, Integer> cargaPorAeropuerto,
                                                      final InstanciaProblema instancia) {
        if (cargaPorAeropuerto.isEmpty() || instancia == null || instancia.getAeropuertos() == null) {
            return new double[]{0.0, 0.0};
        }
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();
        double overflow = 0.0;
        double sumaPromedio = 0.0;
        int cuentaPromedio = 0;
        for (final Map.Entry<String, Integer> entry : cargaPorAeropuerto.entrySet()) {
            final Aeropuerto aeropuerto = aeropuertos.get(entry.getKey());
            if (aeropuerto == null) {
                continue;
            }
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            if (capacidad <= 0) {
                overflow += entry.getValue();
                continue;
            }
            overflow += (double) entry.getValue() / capacidad;
            sumaPromedio += Math.min(1.0, entry.getValue() / (double) capacidad);
            cuentaPromedio++;
        }
        return new double[]{overflow, cuentaPromedio > 0 ? sumaPromedio / cuentaPromedio : 0.0};
    }

    public static double costoAFitness(final double costoTotal) {
        return 1.0 / (1.0 + Math.max(0.0, costoTotal));
    }
}
