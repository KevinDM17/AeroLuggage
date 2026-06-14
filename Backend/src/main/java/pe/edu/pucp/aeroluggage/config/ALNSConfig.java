package pe.edu.pucp.aeroluggage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import pe.edu.pucp.aeroluggage.algoritmo.alns.ParametrosALNS;

@Component
@ConfigurationProperties(prefix = "algoritmos.alns")
@PropertySource(value = "classpath:system_params.yml", factory = YamlPropertySourceFactory.class)
public class ALNSConfig {

    private int maxIteraciones = 150;
    private int maxIteracionesSinMejora = 20;
    private long tiempoMaximoMs = 60_000L;
    private int qMin = 1;
    private int qMax = 6;
    private int qCritical = 4;
    private int maxReintentosRuteo = 100;
    private long minutosConexion = 10L;
    private long tiempoRecojo = 10L;
    private double umbralCriticoAeropuerto = 0.25D;
    private double temperaturaInicial = 5.0D;
    private double factorEnfriamiento = 0.98D;
    private long semilla = 42L;

    private Pesos pesos = new Pesos();
    private Adaptativo adaptativo = new Adaptativo();

    public ParametrosALNS toParametrosALNS() {
        final ParametrosALNS p = new ParametrosALNS();
        p.setMaxIteraciones(maxIteraciones);
        p.setMaxIteracionesSinMejora(maxIteracionesSinMejora);
        p.setTiempoMaximoMs(tiempoMaximoMs);
        p.setQMin(qMin);
        p.setQMax(qMax);
        p.setQCritical(qCritical);
        p.setMaxReintentosRuteo(maxReintentosRuteo);
        p.setMinutosConexion(minutosConexion);
        p.setTiempoRecojo(tiempoRecojo);
        p.setUmbralCriticoAeropuerto(umbralCriticoAeropuerto);
        p.setTemperaturaInicial(temperaturaInicial);
        p.setFactorEnfriamiento(factorEnfriamiento);
        p.setSemilla(semilla);
        p.setPesoMaletasNoEnrutadas(pesos.maletasNoEnrutadas);
        p.setPesoMaletasFueraDePlazo(pesos.maletasFueraDePlazo);
        p.setPesoOverflowVuelos(pesos.overflowVuelos);
        p.setPesoOverflowAeropuertos(pesos.overflowAeropuertos);
        p.setPesoOcupacionPromedioVuelos(pesos.ocupacionPromedioVuelos);
        p.setPesoOcupacionPromedioAeropuertos(pesos.ocupacionPromedioAeropuertos);
        p.setPesoHolgura(pesos.holgura);
        p.setSegmentoIteraciones(adaptativo.segmentoIteraciones);
        p.setSigma1(adaptativo.sigma1);
        p.setSigma2(adaptativo.sigma2);
        p.setSigma3(adaptativo.sigma3);
        p.setSigma4(adaptativo.sigma4);
        p.setRho(adaptativo.rho);
        p.setPesoMinimoOperador(adaptativo.pesoMinimoOperador);
        return p;
    }

    public int getMaxIteraciones() {
        return maxIteraciones;
    }

    public void setMaxIteraciones(final int maxIteraciones) {
        this.maxIteraciones = maxIteraciones;
    }

    public int getMaxIteracionesSinMejora() {
        return maxIteracionesSinMejora;
    }

    public void setMaxIteracionesSinMejora(final int maxIteracionesSinMejora) {
        this.maxIteracionesSinMejora = maxIteracionesSinMejora;
    }

    public long getTiempoMaximoMs() {
        return tiempoMaximoMs;
    }

    public void setTiempoMaximoMs(final long tiempoMaximoMs) {
        this.tiempoMaximoMs = tiempoMaximoMs;
    }

    public int getQMin() {
        return qMin;
    }

    public void setQMin(final int qMin) {
        this.qMin = qMin;
    }

    public int getQMax() {
        return qMax;
    }

    public void setQMax(final int qMax) {
        this.qMax = qMax;
    }

    public int getQCritical() {
        return qCritical;
    }

    public void setQCritical(final int qCritical) {
        this.qCritical = qCritical;
    }

    public int getMaxReintentosRuteo() {
        return maxReintentosRuteo;
    }

    public void setMaxReintentosRuteo(final int maxReintentosRuteo) {
        this.maxReintentosRuteo = maxReintentosRuteo;
    }

    public long getMinutosConexion() {
        return minutosConexion;
    }

    public void setMinutosConexion(final long minutosConexion) {
        this.minutosConexion = minutosConexion;
    }

    public long getTiempoRecojo() {
        return tiempoRecojo;
    }

    public void setTiempoRecojo(final long tiempoRecojo) {
        this.tiempoRecojo = tiempoRecojo;
    }

    public double getUmbralCriticoAeropuerto() {
        return umbralCriticoAeropuerto;
    }

    public void setUmbralCriticoAeropuerto(final double umbralCriticoAeropuerto) {
        this.umbralCriticoAeropuerto = umbralCriticoAeropuerto;
    }

    public double getTemperaturaInicial() {
        return temperaturaInicial;
    }

    public void setTemperaturaInicial(final double temperaturaInicial) {
        this.temperaturaInicial = temperaturaInicial;
    }

    public double getFactorEnfriamiento() {
        return factorEnfriamiento;
    }

    public void setFactorEnfriamiento(final double factorEnfriamiento) {
        this.factorEnfriamiento = factorEnfriamiento;
    }

    public long getSemilla() {
        return semilla;
    }

    public void setSemilla(final long semilla) {
        this.semilla = semilla;
    }

    public Pesos getPesos() {
        return pesos;
    }

    public void setPesos(final Pesos pesos) {
        this.pesos = pesos;
    }

    public Adaptativo getAdaptativo() {
        return adaptativo;
    }

    public void setAdaptativo(final Adaptativo adaptativo) {
        this.adaptativo = adaptativo;
    }

    public static class Pesos {
        private double maletasNoEnrutadas = 1000D;
        private double maletasFueraDePlazo = 800D;
        private double overflowVuelos = 700D;
        private double overflowAeropuertos = 700D;
        private double ocupacionPromedioVuelos = 5D;
        private double ocupacionPromedioAeropuertos = 5D;
        private double holgura = 3D;

        public double getMaletasNoEnrutadas() {
            return maletasNoEnrutadas;
        }

        public void setMaletasNoEnrutadas(final double maletasNoEnrutadas) {
            this.maletasNoEnrutadas = maletasNoEnrutadas;
        }

        public double getMaletasFueraDePlazo() {
            return maletasFueraDePlazo;
        }

        public void setMaletasFueraDePlazo(final double maletasFueraDePlazo) {
            this.maletasFueraDePlazo = maletasFueraDePlazo;
        }

        public double getOverflowVuelos() {
            return overflowVuelos;
        }

        public void setOverflowVuelos(final double overflowVuelos) {
            this.overflowVuelos = overflowVuelos;
        }

        public double getOverflowAeropuertos() {
            return overflowAeropuertos;
        }

        public void setOverflowAeropuertos(final double overflowAeropuertos) {
            this.overflowAeropuertos = overflowAeropuertos;
        }

        public double getOcupacionPromedioVuelos() {
            return ocupacionPromedioVuelos;
        }

        public void setOcupacionPromedioVuelos(final double ocupacionPromedioVuelos) {
            this.ocupacionPromedioVuelos = ocupacionPromedioVuelos;
        }

        public double getOcupacionPromedioAeropuertos() {
            return ocupacionPromedioAeropuertos;
        }

        public void setOcupacionPromedioAeropuertos(final double ocupacionPromedioAeropuertos) {
            this.ocupacionPromedioAeropuertos = ocupacionPromedioAeropuertos;
        }

        public double getHolgura() {
            return holgura;
        }

        public void setHolgura(final double holgura) {
            this.holgura = holgura;
        }
    }

    public static class Adaptativo {
        private int segmentoIteraciones = 15;
        private double sigma1 = 10D;
        private double sigma2 = 6D;
        private double sigma3 = 2D;
        private double sigma4 = 0D;
        private double rho = 0.2D;
        private double pesoMinimoOperador = 0.1D;

        public int getSegmentoIteraciones() {
            return segmentoIteraciones;
        }

        public void setSegmentoIteraciones(final int segmentoIteraciones) {
            this.segmentoIteraciones = segmentoIteraciones;
        }

        public double getSigma1() {
            return sigma1;
        }

        public void setSigma1(final double sigma1) {
            this.sigma1 = sigma1;
        }

        public double getSigma2() {
            return sigma2;
        }

        public void setSigma2(final double sigma2) {
            this.sigma2 = sigma2;
        }

        public double getSigma3() {
            return sigma3;
        }

        public void setSigma3(final double sigma3) {
            this.sigma3 = sigma3;
        }

        public double getSigma4() {
            return sigma4;
        }

        public void setSigma4(final double sigma4) {
            this.sigma4 = sigma4;
        }

        public double getRho() {
            return rho;
        }

        public void setRho(final double rho) {
            this.rho = rho;
        }

        public double getPesoMinimoOperador() {
            return pesoMinimoOperador;
        }

        public void setPesoMinimoOperador(final double pesoMinimoOperador) {
            this.pesoMinimoOperador = pesoMinimoOperador;
        }
    }
}
