package pe.edu.pucp.aeroluggage.algorithms.ga;

public final class ConfiguracionGenetico {
    private final int tamanoPoblacion;
    private final int generacionesMaximas;
    private final double tasaCruce;
    private final double tasaMutacion;
    private final int tamanoTorneo;
    private final int iteracionesBusquedaLocal;
    private final long tiempoMaximoMs;
    private final long semilla;
    private final double pesoViolacionPlazo;
    private final double pesoSobrecargaCapacidad;
    private final double pesoMaletaNoAsignada;
    private final double pesoLongitudRuta;

    private ConfiguracionGenetico(final Builder builder) {
        this.tamanoPoblacion = builder.tamanoPoblacion;
        this.generacionesMaximas = builder.generacionesMaximas;
        this.tasaCruce = builder.tasaCruce;
        this.tasaMutacion = builder.tasaMutacion;
        this.tamanoTorneo = builder.tamanoTorneo;
        this.iteracionesBusquedaLocal = builder.iteracionesBusquedaLocal;
        this.tiempoMaximoMs = builder.tiempoMaximoMs;
        this.semilla = builder.semilla;
        this.pesoViolacionPlazo = builder.pesoViolacionPlazo;
        this.pesoSobrecargaCapacidad = builder.pesoSobrecargaCapacidad;
        this.pesoMaletaNoAsignada = builder.pesoMaletaNoAsignada;
        this.pesoLongitudRuta = builder.pesoLongitudRuta;
    }

    public int getTamanoPoblacion() {
        return tamanoPoblacion;
    }

    public int getGeneracionesMaximas() {
        return generacionesMaximas;
    }

    public double getTasaCruce() {
        return tasaCruce;
    }

    public double getTasaMutacion() {
        return tasaMutacion;
    }

    public int getTamanoTorneo() {
        return tamanoTorneo;
    }

    public int getIteracionesBusquedaLocal() {
        return iteracionesBusquedaLocal;
    }

    public long getTiempoMaximoMs() {
        return tiempoMaximoMs;
    }

    public long getSemilla() {
        return semilla;
    }

    public double getPesoViolacionPlazo() {
        return pesoViolacionPlazo;
    }

    public double getPesoSobrecargaCapacidad() {
        return pesoSobrecargaCapacidad;
    }

    public double getPesoMaletaNoAsignada() {
        return pesoMaletaNoAsignada;
    }

    public double getPesoLongitudRuta() {
        return pesoLongitudRuta;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ConfiguracionGenetico porDefecto() {
        return builder().build();
    }

    public static final class Builder {
        private int tamanoPoblacion = 40;
        private int generacionesMaximas = 60;
        private double tasaCruce = 0.85;
        private double tasaMutacion = 0.15;
        private int tamanoTorneo = 3;
        private int iteracionesBusquedaLocal = 10;
        private long tiempoMaximoMs = 60_000L;
        private long semilla = 42L;
        private double pesoViolacionPlazo = 500.0;
        private double pesoSobrecargaCapacidad = 300.0;
        private double pesoMaletaNoAsignada = 1000.0;
        private double pesoLongitudRuta = 5.0;

        public Builder withTamanoPoblacion(final int valor) {
            this.tamanoPoblacion = valor;
            return this;
        }

        public Builder withGeneracionesMaximas(final int valor) {
            this.generacionesMaximas = valor;
            return this;
        }

        public Builder withTasaCruce(final double valor) {
            this.tasaCruce = valor;
            return this;
        }

        public Builder withTasaMutacion(final double valor) {
            this.tasaMutacion = valor;
            return this;
        }

        public Builder withTamanoTorneo(final int valor) {
            this.tamanoTorneo = valor;
            return this;
        }

        public Builder withIteracionesBusquedaLocal(final int valor) {
            this.iteracionesBusquedaLocal = valor;
            return this;
        }

        public Builder withTiempoMaximoMs(final long valor) {
            this.tiempoMaximoMs = valor;
            return this;
        }

        public Builder withSemilla(final long valor) {
            this.semilla = valor;
            return this;
        }

        public Builder withPesoViolacionPlazo(final double valor) {
            this.pesoViolacionPlazo = valor;
            return this;
        }

        public Builder withPesoSobrecargaCapacidad(final double valor) {
            this.pesoSobrecargaCapacidad = valor;
            return this;
        }

        public Builder withPesoMaletaNoAsignada(final double valor) {
            this.pesoMaletaNoAsignada = valor;
            return this;
        }

        public Builder withPesoLongitudRuta(final double valor) {
            this.pesoLongitudRuta = valor;
            return this;
        }

        public ConfiguracionGenetico build() {
            return new ConfiguracionGenetico(this);
        }
    }
}
