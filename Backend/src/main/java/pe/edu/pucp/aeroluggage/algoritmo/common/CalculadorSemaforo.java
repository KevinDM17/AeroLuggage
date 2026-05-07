package pe.edu.pucp.aeroluggage.algoritmo.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import pe.edu.pucp.aeroluggage.algoritmo.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algoritmo.Solucion;
import pe.edu.pucp.aeroluggage.algoritmo.ga.ParametrosGA;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.Semaforo;

public final class CalculadorSemaforo {

    private CalculadorSemaforo() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static Semaforo clasificarGlobal(final Solucion solucion, final InstanciaProblema instancia,
                                            final ParametrosGA parametros) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()) {
            return Semaforo.ROJO;
        }
        final double ocupacion = ocupacionPromedio(solucion, instancia);
        final int totalRutas = solucion.getSolucion().size();
        final double tasaIncumplimiento = totalRutas == 0
                ? 1.0
                : (double) solucion.getMaletasIncumplidas() / (double) totalRutas;

        if (tasaIncumplimiento > parametros.getToleranciaIncumplimientoAmbar()
                || ocupacion > parametros.getUmbralSemaforoAmbar()) {
            return Semaforo.ROJO;
        }
        if (tasaIncumplimiento > parametros.getToleranciaIncumplimientoVerde()
                || ocupacion > parametros.getUmbralSemaforoVerde()) {
            return Semaforo.AMBAR;
        }
        return Semaforo.VERDE;
    }

    public static Semaforo clasificarVuelo(final VueloInstancia vuelo, final ParametrosGA parametros) {
        if (vuelo == null || vuelo.getCapacidadMaxima() <= 0) {
            return Semaforo.ROJO;
        }
        final double usadas = vuelo.getCapacidadMaxima() - vuelo.getCapacidadDisponible();
        final double ratio = usadas / (double) vuelo.getCapacidadMaxima();
        if (ratio > parametros.getUmbralSemaforoAmbar()) {
            return Semaforo.ROJO;
        }
        if (ratio > parametros.getUmbralSemaforoVerde()) {
            return Semaforo.AMBAR;
        }
        return Semaforo.VERDE;
    }

    public static Semaforo clasificarRuta(final Ruta ruta) {
        if (ruta == null || ruta.getEstado() == EstadoRuta.FALLIDA) {
            return Semaforo.ROJO;
        }
        final double horasPlazo = ruta.getPlazoMaximoDias() * 24.0;
        if (horasPlazo <= 0) {
            return Semaforo.ROJO;
        }
        final double consumo = ruta.getDuracion() / horasPlazo;
        if (consumo > 1.0) {
            return Semaforo.ROJO;
        }
        if (consumo > 0.80) {
            return Semaforo.AMBAR;
        }
        return Semaforo.VERDE;
    }

    public static Map<String, Integer> contarPorSemaforo(final Solucion solucion) {
        final Map<String, Integer> conteo = new HashMap<>();
        conteo.put(Semaforo.VERDE.name(), 0);
        conteo.put(Semaforo.AMBAR.name(), 0);
        conteo.put(Semaforo.ROJO.name(), 0);
        if (solucion == null || solucion.getSolucion() == null) {
            return conteo;
        }
        for (final Ruta ruta : solucion.getSolucion()) {
            final Semaforo s = clasificarRuta(ruta);
            conteo.merge(s.name(), 1, Integer::sum);
        }
        return conteo;
    }

    private static double ocupacionPromedio(final Solucion solucion, final InstanciaProblema instancia) {
        if (solucion.getOcupacionPromedioVuelos() > 0.0) {
            return solucion.getOcupacionPromedioVuelos();
        }
        if (instancia == null || instancia.getVueloInstancias() == null
                || instancia.getVueloInstancias().isEmpty()) {
            return 0.0;
        }
        double suma = 0.0;
        int cuenta = 0;
        for (final VueloInstancia v : instancia.getVueloInstancias()) {
            if (v == null || v.getCapacidadMaxima() <= 0) {
                continue;
            }
            suma += (v.getCapacidadMaxima() - v.getCapacidadDisponible()) / (double) v.getCapacidadMaxima();
            cuenta++;
        }
        return cuenta > 0 ? suma / cuenta : 0.0;
    }

    public static double tasaEntregaATiempo(final Solucion solucion) {
        if (solucion == null || solucion.getSolucion() == null || solucion.getSolucion().isEmpty()) {
            return 0.0;
        }
        final List<Ruta> rutas = solucion.getSolucion();
        return (double) solucion.getMaletasEntregadasATiempo() / (double) rutas.size();
    }
}
