package pe.edu.pucp.aeroluggage.domain;

public class Ciudad {
    private String idCiudad;
    private String nombre;
    private String continente;

    public Ciudad() {
    }

    public Ciudad(final String idCiudad, final String nombre, final String continente) {
        this.idCiudad = idCiudad;
        this.nombre = nombre;
        this.continente = continente;
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

    public String getContinente() {
        return continente;
    }

    public void setContinente(final String continente) {
        this.continente = continente;
    }

    @Override
    public String toString() {
        return "Ciudad{"
            + "idCiudad='" + idCiudad + '\''
            + ", nombre='" + nombre + '\''
            + ", continente='" + continente + '\''
            + '}';
    }
}
