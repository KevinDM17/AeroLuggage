package pe.edu.pucp.aeroluggage.algoritmo.alns;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

final class ALNSAdaptativo {
    private final Map<String, EstadoOperador> operadores = new LinkedHashMap<>();

    ALNSAdaptativo(final Collection<String> nombresOperadores) {
        if (nombresOperadores == null) {
            return;
        }
        for (final String nombre : nombresOperadores) {
            if (nombre != null && !nombre.isBlank()) {
                operadores.put(nombre, new EstadoOperador(nombre, 1.0D));
            }
        }
    }

    String seleccionar(final Random random) {
        if (operadores.isEmpty()) {
            return null;
        }
        final double total = operadores.values().stream().mapToDouble(EstadoOperador::getPeso).sum();
        final double umbral = random.nextDouble() * total;
        double acumulado = 0.0D;
        for (final EstadoOperador operador : operadores.values()) {
            acumulado += operador.getPeso();
            if (acumulado >= umbral) {
                return operador.getNombre();
            }
        }
        return operadores.keySet().iterator().next();
    }

    void registrarUso(final String nombre, final double puntaje) {
        final EstadoOperador operador = operadores.get(nombre);
        if (operador == null) {
            return;
        }
        operador.usosSegmento++;
        operador.puntajeSegmento += puntaje;
    }

    void actualizarPesos(final ParametrosALNS parametros) {
        for (final EstadoOperador operador : operadores.values()) {
            if (operador.usosSegmento > 0) {
                final double rendimiento = operador.puntajeSegmento / operador.usosSegmento;
                final double nuevoPeso = (1.0D - parametros.getRho()) * operador.peso + parametros.getRho() * rendimiento;
                operador.peso = Math.max(parametros.getPesoMinimoOperador(), nuevoPeso);
            }
            operador.usosSegmento = 0;
            operador.puntajeSegmento = 0.0D;
        }
    }

    private static final class EstadoOperador {
        private final String nombre;
        private double peso;
        private int usosSegmento;
        private double puntajeSegmento;

        private EstadoOperador(final String nombre, final double peso) {
            this.nombre = nombre;
            this.peso = peso;
        }

        private String getNombre() {
            return nombre;
        }

        private double getPeso() {
            return peso;
        }
    }
}
