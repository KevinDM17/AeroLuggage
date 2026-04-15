package pe.edu.pucp.aeroluggage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.pucp.aeroluggage.algorithms.Asignacion;
import pe.edu.pucp.aeroluggage.algorithms.Individuo;
import pe.edu.pucp.aeroluggage.algorithms.InstanciaProblema;
import pe.edu.pucp.aeroluggage.algorithms.MaletaProcesada;
import pe.edu.pucp.aeroluggage.algorithms.aco.AlgoritmoColoniaHormigas;
import pe.edu.pucp.aeroluggage.domain.Aeropuerto;
import pe.edu.pucp.aeroluggage.domain.Vuelo;

class AlgorithmCoreTests {
    @Test
    void instancia_problema_conserva_datos_de_entrada_ok() {
        final List<MaletaProcesada> maletasProcesadas = new ArrayList<>();
        final List<Vuelo> vuelos = new ArrayList<>();
        final List<Aeropuerto> aeropuertos = new ArrayList<>();
        final MaletaProcesada maletaProcesada = new MaletaProcesada(
            "MAL-001",
            "ENV-001",
            "AEP-001",
            "AEP-002",
            1
        );
        final Vuelo vuelo = new Vuelo("VUE-001", "TA-100", new Date(), new Date(), 80, 25, "PROGRAMADO");
        final Aeropuerto aeropuerto = new Aeropuerto(
            "AEP-001",
            "CIU-001",
            "Jorge Chavez",
            500,
            120,
            -77.1143f,
            -12.0219f
        );

        maletasProcesadas.add(maletaProcesada);
        vuelos.add(vuelo);
        aeropuertos.add(aeropuerto);

        final InstanciaProblema instanciaProblema = new InstanciaProblema(maletasProcesadas, vuelos, aeropuertos);
        maletasProcesadas.clear();
        vuelos.clear();
        aeropuertos.clear();

        assertEquals(1, instanciaProblema.getMaletasProcesadas().size());
        assertEquals(1, instanciaProblema.getVuelos().size());
        assertEquals(1, instanciaProblema.getAeropuertos().size());
        assertEquals("MAL-001", instanciaProblema.getMaletasProcesadas().get(0).getIdMaleta());
        assertEquals("ENV-001", instanciaProblema.getMaletasProcesadas().get(0).getIdEnvio());
        assertEquals("VUE-001", instanciaProblema.getVuelos().get(0).getIdVuelo());
        assertEquals("AEP-001", instanciaProblema.getAeropuertos().get(0).getIdAeropuerto());
    }

    @Test
    void maleta_procesada_constructor_ok() {
        final MaletaProcesada maletaProcesada = new MaletaProcesada(
            "MAL-001",
            "ENV-001",
            "AEP-001",
            "AEP-002",
            2
        );

        assertEquals("MAL-001", maletaProcesada.getIdMaleta());
        assertEquals("ENV-001", maletaProcesada.getIdEnvio());
        assertEquals("AEP-001", maletaProcesada.getIdAeropuertoSalida());
        assertEquals("AEP-002", maletaProcesada.getIdAeropuertoLlegada());
        assertEquals(2, maletaProcesada.getPlazoMaximoDias());
    }

    @Test
    void asignacion_constructor_y_copia_ok() {
        final Asignacion asignacion = new Asignacion("MAL-001", "VUE-001");
        final Asignacion copiaAsignacion = new Asignacion(asignacion);

        assertEquals("MAL-001", asignacion.getIdMaleta());
        assertEquals("VUE-001", asignacion.getIdVuelo());
        assertEquals("MAL-001", copiaAsignacion.getIdMaleta());
        assertEquals("VUE-001", copiaAsignacion.getIdVuelo());
        assertNotSame(asignacion, copiaAsignacion);
    }

    @Test
    void individuo_copia_asignaciones_y_conserva_fitness_ok() {
        final List<Asignacion> asignaciones = new ArrayList<>();
        final Asignacion asignacion = new Asignacion("MAL-001", "VUE-001");

        asignaciones.add(asignacion);

        final Individuo individuo = new Individuo(asignaciones, 98.5);
        asignaciones.clear();
        final List<Asignacion> asignacionesIndividuo = individuo.getAsignaciones();
        asignacionesIndividuo.clear();

        assertEquals(98.5, individuo.getFitness());
        assertEquals(1, individuo.getAsignaciones().size());
        assertEquals("MAL-001", individuo.getAsignaciones().get(0).getIdMaleta());
        assertEquals("VUE-001", individuo.getAsignaciones().get(0).getIdVuelo());
        assertNotSame(asignacion, individuo.getAsignaciones().get(0));
    }

    @Test
    void algoritmo_colonia_hormigas_genera_individuo_ok() {
        final List<MaletaProcesada> maletasProcesadas = new ArrayList<>();
        final List<Vuelo> vuelos = new ArrayList<>();
        final List<Aeropuerto> aeropuertos = new ArrayList<>();

        maletasProcesadas.add(new MaletaProcesada("MAL-001", "ENV-001", "AEP-001", "AEP-002", 1));
        vuelos.add(new Vuelo("VUE-001", "TA-100", new Date(), new Date(), 80, 25, "PROGRAMADO"));
        aeropuertos.add(new Aeropuerto("AEP-001", "CIU-001", "Jorge Chavez", 500, 120, -77.1143f, -12.0219f));

        final InstanciaProblema instanciaProblema = new InstanciaProblema(maletasProcesadas, vuelos, aeropuertos);
        final AlgoritmoColoniaHormigas algoritmoColoniaHormigas = new AlgoritmoColoniaHormigas(
            instanciaProblema,
            3,
            2,
            0.1,
            1.0,
            2.0,
            0.5,
            1.0
        );

        final Individuo individuo = algoritmoColoniaHormigas.ejecutar();

        assertEquals(1, individuo.getAsignaciones().size());
        assertEquals("MAL-001", individuo.getAsignaciones().get(0).getIdMaleta());
        assertEquals("VUE-001", individuo.getAsignaciones().get(0).getIdVuelo());
        assertEquals(1, algoritmoColoniaHormigas.getFeromonas().size());
    }
}
