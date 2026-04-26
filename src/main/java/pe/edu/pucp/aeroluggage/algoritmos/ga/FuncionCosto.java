package pe.edu.pucp.aeroluggage.algoritmos.ga;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmos.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.algoritmos.common.CalculadorSemaforo;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

public final class FuncionCosto {

    private FuncionCosto() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static double calcularCostoSolucion(final Solucion solucion, final InstanciaProblema instancia,
                                               final ParametrosGA params) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()) {
            return params.getPenalizacionRutaVacia() * 10;
        }

        final Map<String, Maleta> maletasPorId = indexarMaletas(instancia);
        double costoRutas = 0.0;
        int incumplidas = 0;
        int aTiempo = 0;
        double sumaTransito = 0.0;
        int rutasContadas = 0;

        final Map<String, Integer> cargaPorVuelo = new HashMap<>();

        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null) {
                incumplidas++;
                continue;
            }
            final Maleta maleta = maletasPorId.get(ruta.getIdMaleta());
            final double costoRuta = calcularCostoRuta(ruta, maleta, params);
            costoRutas += costoRuta;

            final List<VueloInstancia> subrutas = ruta.getSubrutas();
            if (subrutas == null || subrutas.isEmpty()) {
                incumplidas++;
                continue;
            }

            for (final VueloInstancia vuelo : subrutas) {
                if (vuelo == null || vuelo.getIdVueloInstancia() == null) {
                    continue;
                }
                cargaPorVuelo.merge(vuelo.getIdVueloInstancia(), 1, Integer::sum);
            }

            if (cumplePlazo(ruta, maleta)) {
                aTiempo++;
            } else {
                incumplidas++;
            }

            sumaTransito += ruta.getDuracion();
            rutasContadas++;
        }

        final double overflowVuelos = calcularOverflowVuelos(cargaPorVuelo, instancia);
        final double overflowAlmacenes = calcularOverflowAlmacenes(solucion, instancia);
        final double transitoPromedio = rutasContadas > 0 ? sumaTransito / rutasContadas : 0.0;
        final double ocupacionPromedio = calcularOcupacionPromedio(cargaPorVuelo, instancia);

        final double costoTotal =
                costoRutas
                + params.getW1MaletasIncumplidas() * incumplidas
                + params.getW3OverflowVuelo() * overflowVuelos
                + params.getW4OverflowAlmacen() * overflowAlmacenes
                + params.getW5TransitoPromedio() * transitoPromedio;

        solucion.setCostoTotal(costoTotal);
        solucion.setMaletasEntregadasATiempo(aTiempo);
        solucion.setMaletasIncumplidas(incumplidas);
        solucion.setOcupacionPromedioVuelos(ocupacionPromedio);
        solucion.setOcupacionPromedioAlmacenes(calcularOcupacionPromedioAlmacenes(solucion, instancia));
        solucion.setOverflowAlmacenes((int) overflowAlmacenes);
        solucion.setFactible(incumplidas == 0 && overflowVuelos == 0.0 && overflowAlmacenes == 0.0);
        solucion.setSemaforo(CalculadorSemaforo.clasificarGlobal(solucion, instancia, params));
        return costoTotal;
    }

    public static double calcularCostoRuta(final Ruta ruta, final Maleta maleta, final ParametrosGA params) {
        if (ruta == null) {
            return params.getPenalizacionRutaInvalida();
        }
        final List<VueloInstancia> subrutas = ruta.getSubrutas();
        if (subrutas == null || subrutas.isEmpty()) {
            return params.getPenalizacionRutaVacia();
        }
        if (maleta == null || maleta.getPedido() == null) {
            return params.getPenalizacionSinDestino();
        }

        double costo = 0.0;
        for (final VueloInstancia vuelo : subrutas) {
            if (vuelo == null) {
                costo += params.getPenalizacionRutaInvalida();
                continue;
            }
            if (vuelo.getEstado() == EstadoVuelo.CANCELADO) {
                costo += params.getPenalizacionRutaInvalida();
            }
            if (vuelo.getFechaSalida() != null && vuelo.getFechaLlegada() != null) {
                costo += Duration.between(vuelo.getFechaSalida(), vuelo.getFechaLlegada()).toMinutes() / 60.0;
            }
        }

        costo += (subrutas.size() - 1) * 0.5;

        final double excesoHoras = calcularExcesoHoras(ruta, maleta);
        if (excesoHoras > 0) {
            costo += params.getW2ExcesoHorasPlazo() * excesoHoras;
        }

        if (ruta.getEstado() == EstadoRuta.FALLIDA) {
            costo += params.getPenalizacionRutaInvalida();
        }

        final Pedido pedido = maleta.getPedido();
        if (!primerVueloCoherente(subrutas, pedido.getAeropuertoOrigen())) {
            costo += params.getPenalizacionRutaInvalida();
        }
        if (!ultimoVueloCoherente(subrutas, pedido.getAeropuertoDestino())) {
            costo += params.getPenalizacionSinDestino();
        }
        if (!conexionesCoherentes(subrutas)) {
            costo += params.getPenalizacionRutaInvalida();
        }

        return costo;
    }

    private static double calcularExcesoHoras(final Ruta ruta, final Maleta maleta) {
        final List<VueloInstancia> subrutas = ruta.getSubrutas();
        if (subrutas == null || subrutas.isEmpty() || maleta == null || maleta.getPedido() == null) {
            return 0.0;
        }
        final LocalDateTime llegada = subrutas.get(subrutas.size() - 1).getFechaLlegada();
        final LocalDateTime plazo = maleta.getPedido().getFechaHoraPlazo();
        if (llegada == null || plazo == null) {
            return 0.0;
        }
        if (llegada.isAfter(plazo)) {
            return Duration.between(plazo, llegada).toMinutes() / 60.0;
        }
        return 0.0;
    }

    private static boolean cumplePlazo(final Ruta ruta, final Maleta maleta) {
        if (ruta == null || ruta.getEstado() == EstadoRuta.FALLIDA) {
            return false;
        }
        return calcularExcesoHoras(ruta, maleta) <= 0.0;
    }

    private static boolean primerVueloCoherente(final List<VueloInstancia> subrutas, final Aeropuerto origen) {
        if (subrutas.isEmpty() || origen == null) {
            return false;
        }
        final Aeropuerto inicio = subrutas.get(0).getAeropuertoOrigen();
        return inicio != null && inicio.getIdAeropuerto() != null
                && inicio.getIdAeropuerto().equals(origen.getIdAeropuerto());
    }

    private static boolean ultimoVueloCoherente(final List<VueloInstancia> subrutas, final Aeropuerto destino) {
        if (subrutas.isEmpty() || destino == null) {
            return false;
        }
        final Aeropuerto fin = subrutas.get(subrutas.size() - 1).getAeropuertoDestino();
        return fin != null && fin.getIdAeropuerto() != null
                && fin.getIdAeropuerto().equals(destino.getIdAeropuerto());
    }

    private static boolean conexionesCoherentes(final List<VueloInstancia> subrutas) {
        for (int i = 0; i < subrutas.size() - 1; i++) {
            final VueloInstancia actual = subrutas.get(i);
            final VueloInstancia siguiente = subrutas.get(i + 1);
            if (actual == null || siguiente == null) {
                return false;
            }
            final Aeropuerto destinoActual = actual.getAeropuertoDestino();
            final Aeropuerto origenSiguiente = siguiente.getAeropuertoOrigen();
            if (destinoActual == null || origenSiguiente == null) {
                return false;
            }
            if (!destinoActual.getIdAeropuerto().equals(origenSiguiente.getIdAeropuerto())) {
                return false;
            }
            if (actual.getFechaLlegada() != null && siguiente.getFechaSalida() != null
                    && actual.getFechaLlegada().isAfter(siguiente.getFechaSalida())) {
                return false;
            }
        }
        return true;
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
        if (cargaPorVuelo.isEmpty() || instancia == null || instancia.getVueloInstancias() == null) {
            return 0.0;
        }
        final Map<String, VueloInstancia> vuelos = new HashMap<>();
        for (final VueloInstancia v : instancia.getVueloInstancias()) {
            if (v != null && v.getIdVueloInstancia() != null) {
                vuelos.put(v.getIdVueloInstancia(), v);
            }
        }
        double overflow = 0.0;
        for (final Map.Entry<String, Integer> entry : cargaPorVuelo.entrySet()) {
            final VueloInstancia vuelo = vuelos.get(entry.getKey());
            if (vuelo == null) {
                continue;
            }
            final int exceso = entry.getValue() - vuelo.getCapacidadMaxima();
            if (exceso > 0) {
                overflow += exceso;
            }
        }
        return overflow;
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

    private static double calcularOverflowAlmacenes(final Solucion solucion, final InstanciaProblema instancia) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()
                || instancia == null || instancia.getAeropuertos() == null) {
            return 0.0;
        }
        final Map<String, Integer> cargaPorAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();

        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final List<VueloInstancia> subrutas = ruta.getSubrutas();
            for (final VueloInstancia vuelo : subrutas) {
                if (vuelo == null) {
                    continue;
                }
                if (vuelo.getAeropuertoOrigen() != null && vuelo.getAeropuertoOrigen().getIdAeropuerto() != null) {
                    cargaPorAeropuerto.merge(vuelo.getAeropuertoOrigen().getIdAeropuerto(), 1, Integer::sum);
                }
                if (vuelo.getAeropuertoDestino() != null && vuelo.getAeropuertoDestino().getIdAeropuerto() != null) {
                    cargaPorAeropuerto.merge(vuelo.getAeropuertoDestino().getIdAeropuerto(), 1, Integer::sum);
                }
            }
        }

        double overflow = 0.0;
        for (final Map.Entry<String, Integer> entry : cargaPorAeropuerto.entrySet()) {
            final Aeropuerto aeropuerto = aeropuertos.get(entry.getKey());
            if (aeropuerto == null) {
                continue;
            }
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            if (capacidad <= 0) {
                continue;
            }
            final int carga = entry.getValue();
            if (carga > capacidad) {
                overflow += carga - capacidad;
            }
        }
        return overflow;
    }

    private static double calcularOcupacionPromedioAlmacenes(final Solucion solucion,
                                                            final InstanciaProblema instancia) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()
                || instancia == null || instancia.getAeropuertos() == null) {
            return 0.0;
        }
        final Map<String, Integer> cargaPorAeropuerto = new HashMap<>();
        final Map<String, Aeropuerto> aeropuertos = instancia.indexarAeropuertosPorIcao();

        for (final Ruta ruta : solucion.getSolucion()) {
            if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
                continue;
            }
            final List<VueloInstancia> subrutas = ruta.getSubrutas();
            for (final VueloInstancia vuelo : subrutas) {
                if (vuelo == null) {
                    continue;
                }
                if (vuelo.getAeropuertoOrigen() != null && vuelo.getAeropuertoOrigen().getIdAeropuerto() != null) {
                    cargaPorAeropuerto.merge(vuelo.getAeropuertoOrigen().getIdAeropuerto(), 1, Integer::sum);
                }
                if (vuelo.getAeropuertoDestino() != null && vuelo.getAeropuertoDestino().getIdAeropuerto() != null) {
                    cargaPorAeropuerto.merge(vuelo.getAeropuertoDestino().getIdAeropuerto(), 1, Integer::sum);
                }
            }
        }

        double suma = 0.0;
        int cuenta = 0;
        for (final Map.Entry<String, Integer> entry : cargaPorAeropuerto.entrySet()) {
            final Aeropuerto aeropuerto = aeropuertos.get(entry.getKey());
            if (aeropuerto == null) {
                continue;
            }
            final int capacidad = aeropuerto.getCapacidadAlmacen();
            if (capacidad <= 0) {
                continue;
            }
            suma += Math.min(1.0, entry.getValue() / (double) capacidad);
            cuenta++;
        }
        return cuenta > 0 ? suma / cuenta : 0.0;
    }

    public static double costoAFitness(final double costoTotal) {
        return 1.0 / (1.0 + Math.max(0.0, costoTotal));
    }
}
