package pe.edu.pucp.aeroluggage.domain.entities;

public class Aeropuerto {
    private String idAeropuerto;
    private Ciudad ciudad;
    private int capacidadAlmacen;
    private int maletasActuales;
    private float longitud;
    private float latitud;

    public Aeropuerto() {
    }

    public Aeropuerto(final String idAeropuerto, final Ciudad ciudad, final int capacidadAlmacen,
                     final int maletasActuales, final float longitud, final float latitud) {
        this.idAeropuerto = idAeropuerto;
        this.ciudad = ciudad;
        this.capacidadAlmacen = capacidadAlmacen;
        this.maletasActuales = maletasActuales;
        this.longitud = longitud;
        this.latitud = latitud;
    }

    public String getIdAeropuerto() {
        return idAeropuerto;
    }

    public void setIdAeropuerto(final String idAeropuerto) {
        this.idAeropuerto = idAeropuerto;
    }

    public Ciudad getCiudad() {
        return ciudad;
    }

    public void setCiudad(final Ciudad ciudad) {
        this.ciudad = ciudad;
    }

    public int getCapacidadAlmacen() {
        return capacidadAlmacen;
    }

    public void setCapacidadAlmacen(final int capacidadAlmacen) {
        this.capacidadAlmacen = capacidadAlmacen;
    }

    public int getMaletasActuales() {
        return maletasActuales;
    }

    public void setMaletasActuales(final int maletasActuales) {
        this.maletasActuales = maletasActuales;
    }

    public float getLongitud() {
        return longitud;
    }

    public void setLongitud(final float longitud) {
        this.longitud = longitud;
    }

    public float getLatitud() {
        return latitud;
    }

    public void setLatitud(final float latitud) {
        this.latitud = latitud;
    }
}