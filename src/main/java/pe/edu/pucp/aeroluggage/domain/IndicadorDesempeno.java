package pe.edu.pucp.aeroluggage.domain;

public class IndicadorDesempeno {
    private String idIndicador;
    private String nombre;
    private double valor;
    private String colorSemaforo;

    public IndicadorDesempeno() {
    }

    public IndicadorDesempeno(final String idIndicador, final String nombre, final double valor,
        final String colorSemaforo) {
        this.idIndicador = idIndicador;
        this.nombre = nombre;
        this.valor = valor;
        this.colorSemaforo = colorSemaforo;
    }

    public String getIdIndicador() {
        return idIndicador;
    }

    public void setIdIndicador(final String idIndicador) {
        this.idIndicador = idIndicador;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(final String nombre) {
        this.nombre = nombre;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(final double valor) {
        this.valor = valor;
    }

    public String getColorSemaforo() {
        return colorSemaforo;
    }

    public void setColorSemaforo(final String colorSemaforo) {
        this.colorSemaforo = colorSemaforo;
    }

    @Override
    public String toString() {
        return "IndicadorDesempeno{"
            + "idIndicador='" + idIndicador + '\''
            + ", nombre='" + nombre + '\''
            + ", valor=" + valor
            + ", colorSemaforo='" + colorSemaforo + '\''
            + '}';
    }
}
