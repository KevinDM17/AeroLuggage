package pe.edu.pucp.aeroluggage.escenarios;

import java.time.LocalDate;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;

public final class ParametrosEscenario {
    private final TipoEscenario tipo;
    private final LocalDate fechaInicio;
    private final int diasSimulacion;
    private final int limiteEnvios;
    private final int tamanoLote;
    private final double umbralColapso;
    private final int maxLotesColapso;
    private final ConfiguracionGenetico configuracionGenetico;

    private ParametrosEscenario(final Builder builder) {
        this.tipo = builder.tipo;
        this.fechaInicio = builder.fechaInicio;
        this.diasSimulacion = builder.diasSimulacion;
        this.limiteEnvios = builder.limiteEnvios;
        this.tamanoLote = builder.tamanoLote;
        this.umbralColapso = builder.umbralColapso;
        this.maxLotesColapso = builder.maxLotesColapso;
        this.configuracionGenetico = builder.configuracionGenetico;
    }

    public TipoEscenario getTipo() {
        return tipo;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public int getDiasSimulacion() {
        return diasSimulacion;
    }

    public int getLimiteEnvios() {
        return limiteEnvios;
    }

    public int getTamanoLote() {
        return tamanoLote;
    }

    public double getUmbralColapso() {
        return umbralColapso;
    }

    public int getMaxLotesColapso() {
        return maxLotesColapso;
    }

    public ConfiguracionGenetico getConfiguracionGenetico() {
        return configuracionGenetico;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ParametrosEscenario diaADia(final LocalDate fechaInicio, final int tamanoLote,
        final ConfiguracionGenetico configuracion) {
        return builder()
            .withTipo(TipoEscenario.DIA_A_DIA)
            .withFechaInicio(fechaInicio)
            .withDiasSimulacion(1)
            .withLimiteEnvios(tamanoLote)
            .withTamanoLote(tamanoLote)
            .withConfiguracionGenetico(configuracion)
            .build();
    }

    public static ParametrosEscenario simulacionPeriodo(final LocalDate fechaInicio, final int dias,
        final int limiteEnvios, final ConfiguracionGenetico configuracion) {
        return builder()
            .withTipo(TipoEscenario.SIMULACION_PERIODO)
            .withFechaInicio(fechaInicio)
            .withDiasSimulacion(dias)
            .withLimiteEnvios(limiteEnvios)
            .withTamanoLote(limiteEnvios)
            .withConfiguracionGenetico(configuracion)
            .build();
    }

    public static ParametrosEscenario colapso(final LocalDate fechaInicio, final int tamanoLote,
        final double umbralColapso, final ConfiguracionGenetico configuracion) {
        return builder()
            .withTipo(TipoEscenario.COLAPSO_OPERACIONES)
            .withFechaInicio(fechaInicio)
            .withDiasSimulacion(30)
            .withLimiteEnvios(Integer.MAX_VALUE)
            .withTamanoLote(tamanoLote)
            .withUmbralColapso(umbralColapso)
            .withMaxLotesColapso(20)
            .withConfiguracionGenetico(configuracion)
            .build();
    }

    public static final class Builder {
        private TipoEscenario tipo = TipoEscenario.DIA_A_DIA;
        private LocalDate fechaInicio = LocalDate.of(2026, 1, 2);
        private int diasSimulacion = 1;
        private int limiteEnvios = 200;
        private int tamanoLote = 200;
        private double umbralColapso = 0.2;
        private int maxLotesColapso = 0;
        private ConfiguracionGenetico configuracionGenetico = ConfiguracionGenetico.porDefecto();

        public Builder withTipo(final TipoEscenario valor) {
            this.tipo = valor;
            return this;
        }

        public Builder withFechaInicio(final LocalDate valor) {
            this.fechaInicio = valor;
            return this;
        }

        public Builder withDiasSimulacion(final int valor) {
            this.diasSimulacion = valor;
            return this;
        }

        public Builder withLimiteEnvios(final int valor) {
            this.limiteEnvios = valor;
            return this;
        }

        public Builder withTamanoLote(final int valor) {
            this.tamanoLote = valor;
            return this;
        }

        public Builder withUmbralColapso(final double valor) {
            this.umbralColapso = valor;
            return this;
        }

        public Builder withMaxLotesColapso(final int valor) {
            this.maxLotesColapso = Math.max(0, valor);
            return this;
        }

        public Builder withConfiguracionGenetico(final ConfiguracionGenetico valor) {
            this.configuracionGenetico = valor == null ? ConfiguracionGenetico.porDefecto() : valor;
            return this;
        }

        public ParametrosEscenario build() {
            return new ParametrosEscenario(this);
        }
    }
}
