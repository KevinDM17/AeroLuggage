package pe.edu.pucp.aeroluggage.algoritmos;

import java.util.ArrayList;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

public class InstanciaProblema {
    private String idInstanciaProblema;
    private ArrayList<Maleta> maletas;
    private ArrayList<VueloProgramado> vuelos;
    private ArrayList<Aeropuerto> aeropuertos;

    public InstanciaProblema() {
        this.maletas = new ArrayList<>();
        this.vuelos = new ArrayList<>();
        this.aeropuertos = new ArrayList<>();
    }

    public InstanciaProblema(final String idInstanciaProblema, final ArrayList<Maleta> maletas,
                           final ArrayList<VueloProgramado> vuelos, final ArrayList<Aeropuerto> aeropuertos) {
        this.idInstanciaProblema = idInstanciaProblema;
        this.maletas = maletas;
        this.vuelos = vuelos;
        this.aeropuertos = aeropuertos;
    }

    public String getIdInstanciaProblema() {
        return idInstanciaProblema;
    }

    public void setIdInstanciaProblema(final String idInstanciaProblema) {
        this.idInstanciaProblema = idInstanciaProblema;
    }

    public ArrayList<Maleta> getMaletas() {
        return maletas;
    }

    public void setMaletas(final ArrayList<Maleta> maletas) {
        this.maletas = maletas;
    }

    public ArrayList<VueloProgramado> getVuelos() {
        return vuelos;
    }

    public void setVuelos(final ArrayList<VueloProgramado> vuelos) {
        this.vuelos = vuelos;
    }

    public ArrayList<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public void setAeropuertos(final ArrayList<Aeropuerto> Aeropuertos) {
        this.aeropuertos = Aeropuertos;
    }
}