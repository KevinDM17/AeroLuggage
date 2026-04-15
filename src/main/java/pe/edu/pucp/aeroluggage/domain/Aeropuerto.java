package pe.edu.pucp.aeroluggage.domain;

public class Aeropuerto {
    private String idAeropuerto;
    private String idCiudad;
    private String nombre;
    private int capacidadAlmacen;
    private int maletasActuales;
    private float longitud;
    private float latitud;

    public Aeropuerto() {
    }

    public Aeropuerto(final String idAeropuerto, final String idCiudad, final String nombre,
        final int capacidadAlmacen, final int maletasActuales, final float longitud, final float latitud) {
        this.idAeropuerto = idAeropuerto;
        this.idCiudad = idCiudad;
        this.nombre = nombre;
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

    public String getIdCiudad() {
        return idCiudad;
    }

    public void setIdCiudad(final String idCiudad) {
        this.idCiudad = idCiudad;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(final String nombre) {
        this.nombre = nombre;
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

    @Override
    public String toString() {
        return "Aeropuerto{"
            + "idAeropuerto='" + idAeropuerto + '\''
            + ", idCiudad='" + idCiudad + '\''
            + ", nombre='" + nombre + '\''
            + ", capacidadAlmacen=" + capacidadAlmacen
            + ", maletasActuales=" + maletasActuales
            + ", longitud=" + longitud
            + ", latitud=" + latitud
            + '}';
    }
}
