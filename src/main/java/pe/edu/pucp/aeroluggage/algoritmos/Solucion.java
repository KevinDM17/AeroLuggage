package pe.edu.pucp.aeroluggage.algoritmos;

import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public class Solucion {

    private ArrayList<Ruta> solucion;
    private double fitness;
    private int maletasEntregadasATiempo;
    private int maletasIncumplidas;
    private double ocupacionPromedioVuelos;

    public Solucion() {
        this.solucion = new ArrayList<>();
    }

    public Solucion(final ArrayList<Ruta> solucion) {
        this.solucion = solucion != null ? solucion : new ArrayList<>();
    }

    public void calcularMetricas() {
        if (solucion == null || solucion.isEmpty()) {
            this.maletasEntregadasATiempo = 0;
            this.maletasIncumplidas = 0;
            this.ocupacionPromedioVuelos = 0.0;
            return;
        }

        int entregadasATiempo = 0;
        int incumplidas = 0;
        double sumaOcupacion = 0.0;
        int totalVuelos = 0;

        for (final Ruta ruta : solucion) {
            if (ruta == null) {
                continue;
            }
            final boolean cumplePlazo = ruta.getDuracion() <= ruta.getPlazoMaximoDias() * 24.0;
            final boolean estadoValido = ruta.getEstado() != EstadoRuta.FALLIDA;
            if (cumplePlazo && estadoValido) {
                entregadasATiempo++;
            } else {
                incumplidas++;
            }

            final List<VueloInstancia> vuelos = ruta.getSubrutas();
            if (vuelos == null) {
                continue;
            }
            for (final VueloInstancia vuelo : vuelos) {
                if (vuelo == null || vuelo.getCapacidadMaxima() <= 0) {
                    continue;
                }
                final double ocupadas = vuelo.getCapacidadMaxima() - vuelo.getCapacidadDisponible();
                sumaOcupacion += ocupadas / (double) vuelo.getCapacidadMaxima();
                totalVuelos++;
            }
        }

        this.maletasEntregadasATiempo = entregadasATiempo;
        this.maletasIncumplidas = incumplidas;
        this.ocupacionPromedioVuelos = totalVuelos > 0 ? sumaOcupacion / totalVuelos : 0.0;
    }

    public boolean esFactible() {
        return maletasIncumplidas == 0;
    }

    public Solucion clonarProfundo() {
        final ArrayList<Ruta> copia = new ArrayList<>(solucion.size());
        for (final Ruta ruta : solucion) {
            if (ruta == null) {
                copia.add(null);
                continue;
            }
            final List<VueloInstancia> subrutasOriginales = ruta.getSubrutas();
            final List<VueloInstancia> subrutasCopia = subrutasOriginales != null
                    ? new ArrayList<>(subrutasOriginales)
                    : new ArrayList<>();
            final Ruta rutaCopia = new Ruta(
                    ruta.getIdRuta(),
                    ruta.getIdMaleta(),
                    ruta.getPlazoMaximoDias(),
                    ruta.getDuracion(),
                    subrutasCopia,
                    ruta.getEstado());
            copia.add(rutaCopia);
        }
        final Solucion clon = new Solucion(copia);
        clon.fitness = this.fitness;
        clon.maletasEntregadasATiempo = this.maletasEntregadasATiempo;
        clon.maletasIncumplidas = this.maletasIncumplidas;
        clon.ocupacionPromedioVuelos = this.ocupacionPromedioVuelos;
        return clon;
    }

    public ArrayList<Ruta> getSolucion() {
        return solucion;
    }

    public void setSolucion(final ArrayList<Ruta> solucion) {
        this.solucion = solucion != null ? solucion : new ArrayList<>();
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(final double fitness) {
        this.fitness = fitness;
    }

    public int getMaletasEntregadasATiempo() {
        return maletasEntregadasATiempo;
    }

    public void setMaletasEntregadasATiempo(final int maletasEntregadasATiempo) {
        this.maletasEntregadasATiempo = maletasEntregadasATiempo;
    }

    public int getMaletasIncumplidas() {
        return maletasIncumplidas;
    }

    public void setMaletasIncumplidas(final int maletasIncumplidas) {
        this.maletasIncumplidas = maletasIncumplidas;
    }

    public double getOcupacionPromedioVuelos() {
        return ocupacionPromedioVuelos;
    }

    public void setOcupacionPromedioVuelos(final double ocupacionPromedioVuelos) {
        this.ocupacionPromedioVuelos = ocupacionPromedioVuelos;
    }

    @Override
    public String toString() {
        return "Solucion{"
                + "rutas=" + (solucion != null ? solucion.size() : 0)
                + ", fitness=" + fitness
                + ", aTiempo=" + maletasEntregadasATiempo
                + ", incumplidas=" + maletasIncumplidas
                + ", ocupacionProm=" + String.format("%.3f", ocupacionPromedioVuelos)
                + '}';
    }
}
