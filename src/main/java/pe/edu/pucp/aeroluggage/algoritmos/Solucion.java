package pe.edu.pucp.aeroluggage.algoritmos;

import java.util.ArrayList;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;


public class Solucion {
    
    ArrayList<Ruta> solucion;

    public Solucion () {

    }

    public Solucion(final ArrayList<Ruta> solucion){
        this.solucion = solucion;
    }
    
    public ArrayList<Ruta> getSolucion() {
        return solucion;
    }

    public void setSolucion(ArrayList<Ruta> solucion) {
        this.solucion = solucion;
    }

}
