package pe.edu.pucp.aeroluggage.escenarios;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.algorithms.ga.AlgoritmoGeneticoHibrido;
import pe.edu.pucp.aeroluggage.algorithms.ga.ConfiguracionGenetico;
import pe.edu.pucp.aeroluggage.algorithms.ga.MetricasSolucion;
import pe.edu.pucp.aeroluggage.algorithms.ga.ResultadoGenetico;
import pe.edu.pucp.aeroluggage.data.AeropuertoLoader;
import pe.edu.pucp.aeroluggage.data.CargadorDatos;
import pe.edu.pucp.aeroluggage.data.EnvioLoader;
import pe.edu.pucp.aeroluggage.data.PlanDeVuelo;
import pe.edu.pucp.aeroluggage.data.VueloLoader;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Envio;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

public final class EjecutorEscenario {
    private final List<Aeropuerto> aeropuertos;
    private final Map<String, Integer> husosHorarios;
    private final List<PlanDeVuelo> planesVuelo;
    private final String recursoEnvios;
    private final String origenEnvios;

    public EjecutorEscenario() {
        this(
            CargadorDatos.RECURSO_AEROPUERTOS,
            CargadorDatos.RECURSO_PLANES_VUELO,
            CargadorDatos.RECURSO_ENVIOS_EBCI,
            "EBCI"
        );
    }

    public EjecutorEscenario(final String recursoAeropuertos, final String recursoPlanes,
        final String recursoEnvios, final String origenEnvios) {
        this.aeropuertos = AeropuertoLoader.cargarDesdeClasspath(recursoAeropuertos);
        this.husosHorarios = AeropuertoLoader.husosHorarios(recursoAeropuertos);
        this.planesVuelo = VueloLoader.cargarPlanes(recursoPlanes);
        this.recursoEnvios = recursoEnvios;
        this.origenEnvios = origenEnvios;
    }

    public ReporteEscenario ejecutar(final ParametrosEscenario parametros) {
        if (parametros == null) {
            throw new IllegalArgumentException("Los parametros del escenario no pueden ser nulos");
        }
        switch (parametros.getTipo()) {
            case DIA_A_DIA:
                return ejecutarDiaADia(parametros);
            case SIMULACION_PERIODO:
                return ejecutarSimulacionPeriodo(parametros);
            case COLAPSO_OPERACIONES:
                return ejecutarColapso(parametros);
            default:
                throw new IllegalStateException("Tipo de escenario desconocido: " + parametros.getTipo());
        }
    }

    private ReporteEscenario ejecutarDiaADia(final ParametrosEscenario parametros) {
        final long inicio = System.currentTimeMillis();
        final List<Envio> envios = EnvioLoader.cargar(recursoEnvios, origenEnvios, parametros.getLimiteEnvios());
        final List<Vuelo> vuelos = VueloLoader.expandirVuelos(
            planesVuelo,
            parametros.getFechaInicio(),
            Math.max(1, parametros.getDiasSimulacion()),
            husosHorarios
        );
        final List<ResultadoGenetico> resultados = new ArrayList<>();
        int totalMaletas = 0;
        final List<Envio> lotes = envios;
        for (int i = 0; i < lotes.size(); i += Math.max(1, parametros.getTamanoLote())) {
            final int fin = Math.min(lotes.size(), i + parametros.getTamanoLote());
            final List<Envio> bloque = lotes.subList(i, fin);
            final List<MaletaProcesada> maletas = CargadorDatos.expandirMaletas(bloque);
            totalMaletas += maletas.size();
            final InstanciaProblema instancia = new InstanciaProblema(maletas, vuelos, aeropuertos);
            final AlgoritmoGeneticoHibrido algoritmo = new AlgoritmoGeneticoHibrido(
                instancia,
                parametros.getConfiguracionGenetico()
            );
            resultados.add(algoritmo.ejecutar());
        }
        final MetricasSolucion agregadas = agregarMetricas(resultados);
        return new ReporteEscenario(
            parametros.getTipo(),
            envios.size(),
            totalMaletas,
            System.currentTimeMillis() - inicio,
            resultados,
            agregadas,
            false,
            -1
        );
    }

    private ReporteEscenario ejecutarSimulacionPeriodo(final ParametrosEscenario parametros) {
        final long inicio = System.currentTimeMillis();
        final List<Envio> envios = EnvioLoader.cargar(recursoEnvios, origenEnvios, parametros.getLimiteEnvios());
        final List<Vuelo> vuelos = VueloLoader.expandirVuelos(
            planesVuelo,
            parametros.getFechaInicio(),
            Math.max(1, parametros.getDiasSimulacion()),
            husosHorarios
        );
        final List<MaletaProcesada> maletas = CargadorDatos.expandirMaletas(envios);
        final InstanciaProblema instancia = new InstanciaProblema(maletas, vuelos, aeropuertos);
        final AlgoritmoGeneticoHibrido algoritmo = new AlgoritmoGeneticoHibrido(
            instancia,
            parametros.getConfiguracionGenetico()
        );
        final ResultadoGenetico resultado = algoritmo.ejecutar();
        final List<ResultadoGenetico> resultados = new ArrayList<>();
        resultados.add(resultado);
        return new ReporteEscenario(
            parametros.getTipo(),
            envios.size(),
            maletas.size(),
            System.currentTimeMillis() - inicio,
            resultados,
            resultado.getMetricas(),
            false,
            -1
        );
    }

    private ReporteEscenario ejecutarColapso(final ParametrosEscenario parametros) {
        final long inicio = System.currentTimeMillis();
        final List<Vuelo> vuelos = VueloLoader.expandirVuelos(
            planesVuelo,
            parametros.getFechaInicio(),
            Math.max(1, parametros.getDiasSimulacion()),
            husosHorarios
        );
        final int ventanaLote = Math.max(1, parametros.getTamanoLote());
        final int maxLotes = parametros.getMaxLotesColapso();
        final int topeEnvios = maxLotes > 0 ? maxLotes * ventanaLote : ventanaLote * 20;
        final List<Envio> todosEnvios = EnvioLoader.cargar(recursoEnvios, origenEnvios, topeEnvios);
        final List<ResultadoGenetico> resultados = new ArrayList<>();
        int totalEnviosProcesados = 0;
        int totalMaletas = 0;
        int numeroLote = 0;
        boolean colapso = false;
        int loteColapso = -1;
        int offset = 0;

        while (!colapso && offset < todosEnvios.size()) {
            if (maxLotes > 0 && numeroLote >= maxLotes) {
                break;
            }
            final int fin = Math.min(todosEnvios.size(), offset + ventanaLote);
            final List<Envio> bloque = todosEnvios.subList(offset, fin);
            if (bloque.isEmpty()) {
                break;
            }
            final List<MaletaProcesada> maletas = CargadorDatos.expandirMaletas(bloque);
            totalMaletas += maletas.size();
            totalEnviosProcesados += bloque.size();
            final InstanciaProblema instancia = new InstanciaProblema(maletas, vuelos, aeropuertos);
            final AlgoritmoGeneticoHibrido algoritmo = new AlgoritmoGeneticoHibrido(
                instancia,
                parametros.getConfiguracionGenetico()
            );
            final ResultadoGenetico resultado = algoritmo.ejecutar();
            resultados.add(resultado);
            numeroLote++;
            offset = fin;
            if (hayColapso(resultado.getMetricas(), parametros.getUmbralColapso())) {
                colapso = true;
                loteColapso = numeroLote;
            }
        }
        return new ReporteEscenario(
            parametros.getTipo(),
            totalEnviosProcesados,
            totalMaletas,
            System.currentTimeMillis() - inicio,
            resultados,
            agregarMetricas(resultados),
            colapso,
            loteColapso
        );
    }

    private boolean hayColapso(final MetricasSolucion metricas, final double umbral) {
        if (metricas == null || metricas.getTotalMaletas() == 0) {
            return false;
        }
        final double problemas = metricas.getMaletasSinRuta()
            + metricas.getViolacionesPlazo()
            + metricas.getSumaSobrecarga();
        return (problemas / metricas.getTotalMaletas()) > umbral;
    }

    private MetricasSolucion agregarMetricas(final List<ResultadoGenetico> resultados) {
        int totalMaletas = 0;
        int asignadas = 0;
        int sinRuta = 0;
        int violaciones = 0;
        int sobrecargados = 0;
        int sumaSobrecarga = 0;
        int totalSegmentos = 0;
        double penalizacion = 0.0;
        for (final ResultadoGenetico resultado : resultados) {
            final MetricasSolucion metricas = resultado.getMetricas();
            if (metricas == null) {
                continue;
            }
            totalMaletas += metricas.getTotalMaletas();
            asignadas += metricas.getMaletasAsignadas();
            sinRuta += metricas.getMaletasSinRuta();
            violaciones += metricas.getViolacionesPlazo();
            sobrecargados += metricas.getVuelosSobrecargados();
            sumaSobrecarga += metricas.getSumaSobrecarga();
            totalSegmentos += metricas.getTotalSegmentosRuta();
            penalizacion += metricas.getPenalizacionTotal();
        }
        return new MetricasSolucion(
            totalMaletas,
            asignadas,
            sinRuta,
            violaciones,
            sobrecargados,
            sumaSobrecarga,
            totalSegmentos,
            penalizacion
        );
    }

    public List<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public LocalDate fechaPorDefecto() {
        return LocalDate.of(2026, 1, 2);
    }

    public ConfiguracionGenetico configuracionPorDefecto() {
        return ConfiguracionGenetico.porDefecto();
    }
}
