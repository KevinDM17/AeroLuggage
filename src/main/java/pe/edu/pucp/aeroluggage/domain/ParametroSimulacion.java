package pe.edu.pucp.aeroluggage.domain;

public class ParametroSimulacion {
    private String idParametro;
    private String nombre;
    private String valor;

    public ParametroSimulacion() {
    }

    public ParametroSimulacion(final String idParametro, final String nombre, final String valor) {
        this.idParametro = idParametro;
        this.nombre = nombre;
        this.valor = valor;
    }

    public String getIdParametro() {
        return idParametro;
    }

    public void setIdParametro(final String idParametro) {
        this.idParametro = idParametro;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(final String nombre) {
        this.nombre = nombre;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(final String valor) {
        this.valor = valor;
    }

    @Override
    public String toString() {
        return "ParametroSimulacion{"
            + "idParametro='" + idParametro + '\''
            + ", nombre='" + nombre + '\''
            + ", valor='" + valor + '\''
            + '}';
    }
}
