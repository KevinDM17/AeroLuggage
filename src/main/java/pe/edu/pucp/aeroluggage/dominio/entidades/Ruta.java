package pe.edu.pucp.aeroluggage.dominio.entidades;

import java.util.List;

import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

public class Ruta {
    private static final double PLAZO_MISMO_CONTINENTE_DIAS = 1.0;
    private static final double PLAZO_DISTINTO_CONTINENTE_DIAS = 2.0;

    private String idRuta;
    private String idMaleta;
    private double plazoMaximoDias;
    private double duracion;
    private List<VueloInstancia> subrutas;
    private EstadoRuta estado;

    public Ruta() {
    }

    public Ruta(final String idRuta, final String idMaleta, final double plazoMaximoDias,
                final double duracion, final List<VueloInstancia> subrutas, final EstadoRuta estado) {
        this.idRuta = idRuta;
        this.idMaleta = idMaleta;
        this.plazoMaximoDias = plazoMaximoDias;
        this.duracion = duracion;
        this.subrutas = subrutas;
        this.estado = estado;
    }

    public double calcularPlazo(final Aeropuerto origen, final Aeropuerto destino) {
        if (origen == null || destino == null || origen.getCiudad() == null || destino.getCiudad() == null) {
            return PLAZO_DISTINTO_CONTINENTE_DIAS;
        }
        final Continente continenteOrigen = origen.getCiudad().getContinente();
        final Continente continenteDestino = destino.getCiudad().getContinente();
        if (continenteOrigen == continenteDestino) {
            return PLAZO_MISMO_CONTINENTE_DIAS;
        }
        return PLAZO_DISTINTO_CONTINENTE_DIAS;
    }

    public String getIdRuta() {
        return idRuta;
    }

    public void setIdRuta(final String idRuta) {
        this.idRuta = idRuta;
    }

    public String getIdMaleta() {
        return idMaleta;
    }

    public void setIdMaleta(final String idMaleta) {
        this.idMaleta = idMaleta;
    }

    public double getPlazoMaximoDias() {
        return plazoMaximoDias;
    }

    public void setPlazoMaximoDias(final double plazoMaximoDias) {
        this.plazoMaximoDias = plazoMaximoDias;
    }

    public double getDuracion() {
        return duracion;
    }

    public void setDuracion(final double duracion) {
        this.duracion = duracion;
    }

    public List<VueloInstancia> getSubrutas() {
        return subrutas;
    }

    public void setSubrutas(final List<VueloInstancia> subrutas) {
        this.subrutas = subrutas;
    }

    public EstadoRuta getEstado() {
        return estado;
    }

    public void setEstado(final EstadoRuta estado) {
        this.estado = estado;
    }
}
