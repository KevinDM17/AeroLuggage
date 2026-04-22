package pe.edu.pucp.aeroluggage.algoritmos;

import java.util.ArrayList;
import java.util.List;

import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;
import pe.edu.pucp.aeroluggage.dominio.enums.Semaforo;

public class Solucion {
    private ArrayList<Ruta> solucion;
    private double fitness;
    private double costoTotal;
    private boolean factible;
    private int maletasEntregadasATiempo;
    private int maletasIncumplidas;
    private double ocupacionPromedioVuelos;
    private Semaforo semaforo;

    public Solucion() {
        this.solucion = new ArrayList<>();
    }

    public Solucion(final ArrayList<Ruta> solucion) {
        setSolucion(solucion);
    }

    public void calcularMetricas() {
        if (solucion == null || solucion.isEmpty()) {
            maletasEntregadasATiempo = 0;
            maletasIncumplidas = 0;
            ocupacionPromedioVuelos = 0D;
            factible = true;
            return;
        }

        int entregadasATiempo = 0;
        int incumplidas = 0;
        double sumaOcupacion = 0D;
        int totalVuelos = 0;

        for (final Ruta ruta : solucion) {
            if (ruta == null) {
                continue;
            }
            final boolean cumplePlazo = ruta.getDuracion() <= ruta.getPlazoMaximoDias();
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

        maletasEntregadasATiempo = entregadasATiempo;
        maletasIncumplidas = incumplidas;
        ocupacionPromedioVuelos = totalVuelos > 0 ? sumaOcupacion / totalVuelos : 0D;
        factible = incumplidas == 0;
    }

    public boolean esFactible() {
        return factible;
    }

    public boolean isFactible() {
        return factible;
    }

    public void setFactible(final boolean factible) {
        this.factible = factible;
    }

    public Solucion clonarProfundo() {
        final ArrayList<Ruta> copia = new ArrayList<>(solucion.size());
        for (final Ruta ruta : solucion) {
            if (ruta == null) {
                copia.add(null);
                continue;
            }
            final List<VueloInstancia> subrutasOriginales = ruta.getSubrutas();
            final List<VueloInstancia> subrutasCopia = subrutasOriginales == null
                    ? new ArrayList<>()
                    : new ArrayList<>(subrutasOriginales);
            copia.add(new Ruta(
                    ruta.getIdRuta(),
                    ruta.getIdMaleta(),
                    ruta.getPlazoMaximoDias(),
                    ruta.getDuracion(),
                    subrutasCopia,
                    ruta.getEstado()
            ));
        }
        final Solucion clon = new Solucion(copia);
        clon.fitness = fitness;
        clon.costoTotal = costoTotal;
        clon.factible = factible;
        clon.maletasEntregadasATiempo = maletasEntregadasATiempo;
        clon.maletasIncumplidas = maletasIncumplidas;
        clon.ocupacionPromedioVuelos = ocupacionPromedioVuelos;
        clon.semaforo = semaforo;
        return clon;
    }

    public ArrayList<Ruta> getSubrutas() {
        return getSolucion();
    }

    public void setSubrutas(final ArrayList<Ruta> subrutas) {
        setSolucion(subrutas);
    }

    public ArrayList<Ruta> getSolucion() {
        return new ArrayList<>(solucion);
    }

    public void setSolucion(final ArrayList<Ruta> solucion) {
        this.solucion = solucion == null ? new ArrayList<>() : new ArrayList<>(solucion);
    }

    public double getFitness() {
        return fitness;
    }

    public void setFitness(final double fitness) {
        this.fitness = fitness;
    }

    public double getCostoTotal() {
        return costoTotal;
    }

    public void setCostoTotal(final double costoTotal) {
        this.costoTotal = costoTotal;
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

    public Semaforo getSemaforo() {
        return semaforo;
    }

    public void setSemaforo(final Semaforo semaforo) {
        this.semaforo = semaforo;
    }

    @Override
    public String toString() {
        return "Solucion{"
                + "rutas=" + solucion.size()
                + ", fitness=" + fitness
                + ", aTiempo=" + maletasEntregadasATiempo
                + ", incumplidas=" + maletasIncumplidas
                + ", ocupacionProm=" + String.format("%.3f", ocupacionPromedioVuelos)
                + '}';
    }
}
