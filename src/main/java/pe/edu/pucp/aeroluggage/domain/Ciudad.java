package pe.edu.pucp.aeroluggage.domain;

public class Ciudad {
    private String idCiudad;
    private String nombre;
    private Continente continente;

    public Ciudad() {
    }

    public Ciudad(final String idCiudad, final String nombre, final Continente continente) {
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

    public Continente getContinente() {
        return continente;
    }

    public void setContinente(final Continente continente) {
        this.continente = continente;
    }

    @Override
    public String toString() {
        return "Ciudad{"
            + "idCiudad='" + idCiudad + '\''
            + ", nombre='" + nombre + '\''
            + ", continente=" + continente
            + '}';
    }
}
