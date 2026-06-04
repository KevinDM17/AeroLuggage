package pe.edu.pucp.aeroluggage.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import pe.edu.pucp.aeroluggage.controller.DataLoaderController;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;

@Slf4j
@Component
public class SeedAlInicio implements ApplicationRunner {

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final DataLoaderController dataLoaderController;

    public SeedAlInicio(final AeropuertoRepositorio aeropuertoRepositorio,
                        final DataLoaderController dataLoaderController) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.dataLoaderController = dataLoaderController;
    }

    @Override
    public void run(final ApplicationArguments args) throws Exception {
        if (!aeropuertoRepositorio.obtenerTodos().isEmpty()) {
            log.info("[AeroLuggage/Seed] - DB ya contiene aeropuertos, se omite la carga inicial");
            return;
        }
        log.info("[AeroLuggage/Seed] - DB vacia, sembrando aeropuertos y vuelos desde recursos...");
        dataLoaderController.cargarDatosIniciales();
        log.info("[AeroLuggage/Seed] - Carga inicial completada");
    }
}
