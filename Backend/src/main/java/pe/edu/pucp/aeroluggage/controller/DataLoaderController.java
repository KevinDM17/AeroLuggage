package pe.edu.pucp.aeroluggage.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.pucp.aeroluggage.servicios.ServicioAeropuerto;
import pe.edu.pucp.aeroluggage.servicios.ServicioVueloProgramado;

import java.io.IOException;

@Service
public class DataLoaderController {

    private final ServicioAeropuerto servicioAeropuerto;
    private final ServicioVueloProgramado servicioVueloProgramado;
    private final JdbcTemplate jdbcTemplate;

    public DataLoaderController(ServicioAeropuerto servicioAeropuerto,
                                     ServicioVueloProgramado servicioVueloProgramado,
                                     JdbcTemplate jdbcTemplate) {
        this.servicioAeropuerto = servicioAeropuerto;
        this.servicioVueloProgramado = servicioVueloProgramado;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void limpiarDatos() {
        jdbcTemplate.update("DELETE FROM ruta_vuelo_instancia");
        jdbcTemplate.update("DELETE FROM ruta");
        jdbcTemplate.update("DELETE FROM maleta");
        jdbcTemplate.update("DELETE FROM pedido");
        jdbcTemplate.update("DELETE FROM vuelo_instancia");
        jdbcTemplate.update("DELETE FROM vuelo_programado");
        jdbcTemplate.update("DELETE FROM aeropuerto");
        jdbcTemplate.update("DELETE FROM ciudad");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cargarDatosIniciales() throws IOException {
        servicioAeropuerto.cargarDesdeRecursos();
        servicioVueloProgramado.cargarDesdeRecursos();
    }

    public void seed(boolean limpiar) throws IOException {
        if (limpiar) {
            limpiarDatos();
        }
        cargarDatosIniciales();
    }
}
