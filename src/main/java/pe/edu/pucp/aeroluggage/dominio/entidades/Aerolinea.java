package pe.edu.pucp.aeroluggage.domain.entities;

public class Aerolinea {
    private String idAerolinea;
    private String nombre;

    public Aerolinea() {
    }

    public Aerolinea(final String idAerolinea, final String nombre) {
        this.idAerolinea = idAerolinea;
        this.nombre = nombre;
    }

    public String getIdAerolinea() {
        return idAerolinea;
    }

    public void setIdAerolinea(final String idAerolinea) {
        this.idAerolinea = idAerolinea;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(final String nombre) {
        this.nombre = nombre;
    }
}