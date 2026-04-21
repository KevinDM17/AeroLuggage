package pe.edu.pucp.aeroluggage.algoritmos;

import java.util.ArrayList;

import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;

public class Solucion {
    private ArrayList<Ruta> subrutas;

    public Solucion() {
        this.subrutas = new ArrayList<>();
    }

    public Solucion(final ArrayList<Ruta> subrutas) {
        setSubrutas(subrutas);
    }

    public ArrayList<Ruta> getSubrutas() {
        return subrutas;
    }

    public void setSubrutas(final ArrayList<Ruta> subrutas) {
        if (subrutas == null) {
            this.subrutas = new ArrayList<>();
            return;
        }
        this.subrutas = new ArrayList<>(subrutas);
    }

    public ArrayList<Ruta> getSolucion() {
        return getSubrutas();
    }

    public void setSolucion(final ArrayList<Ruta> solucion) {
        setSubrutas(solucion);
    }
}
