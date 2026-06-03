package pe.edu.pucp.aeroluggage.servicios;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.github.cdimascio.dotenv.Dotenv;
import pe.edu.pucp.aeroluggage.cargador.CargadorPlanesVuelo;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloProgramadoRepositorio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

@Service
public class ServicioVueloProgramado {

    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;
    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final Dotenv dotenv;

    public ServicioVueloProgramado(final VueloProgramadoRepositorio vueloProgramadoRepositorio,
                                   final AeropuertoRepositorio aeropuertoRepositorio,
                                   final Dotenv dotenv) {
        this.vueloProgramadoRepositorio = vueloProgramadoRepositorio;
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.dotenv = dotenv;
    }

    @Transactional
    public List<VueloProgramado> cargarDesdeRecursos() throws IOException {
        ClassPathResource recurso = new ClassPathResource(dotenv.get("PLANES_VUELO_FILE_PATH"));
        Path temp = Files.createTempFile("planes-vuelo-", ".txt");
        try (InputStream is = recurso.getInputStream()) {
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            return persistir(cargarDesdeRuta(temp));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<VueloProgramado> cargarDesdeRuta(Path ruta) {
        Map<String, Aeropuerto> mapa = aeropuertoRepositorio.obtenerTodosComoMapa();
        return CargadorPlanesVuelo.cargar(ruta, mapa);
    }

    private List<VueloProgramado> persistir(List<VueloProgramado> vuelos) {
        for (VueloProgramado vuelo : vuelos) {
            vueloProgramadoRepositorio.insertar(vuelo);
        }
        return vuelos;
    }
}
