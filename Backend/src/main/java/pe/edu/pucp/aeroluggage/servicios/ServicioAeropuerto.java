package pe.edu.pucp.aeroluggage.servicios;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import io.github.cdimascio.dotenv.Dotenv;
import pe.edu.pucp.aeroluggage.cargador.CargadorAeropuertos;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class ServicioAeropuerto {

    private static final String AEROPUERTO_FILE = "AEROPUERTO_FILE_PATH";

    private final AeropuertoRepositorio aeropuertoRepositorio;
    private final JdbcTemplate jdbcTemplate;
    private final Dotenv dotenv;

    public ServicioAeropuerto(final AeropuertoRepositorio aeropuertoRepositorio,
                              final JdbcTemplate jdbcTemplate,
                              final Dotenv dotenv) {
        this.aeropuertoRepositorio = aeropuertoRepositorio;
        this.jdbcTemplate = jdbcTemplate;
        this.dotenv = dotenv;
    }

    @Transactional
    public List<Aeropuerto> cargarDesdeRecursos() throws IOException {
        ClassPathResource recurso = new ClassPathResource(dotenv.get(AEROPUERTO_FILE));
        Path temp = Files.createTempFile("aeropuertos-", ".txt");
        try (InputStream is = recurso.getInputStream()) {
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            return persistir(CargadorAeropuertos.cargar(temp));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private List<Aeropuerto> persistir(List<Aeropuerto> aeropuertos) {
        for (Aeropuerto aeropuerto : aeropuertos) {
            if (aeropuerto.getCiudad() != null) {
                jdbcTemplate.update(
                        "INSERT OR IGNORE INTO ciudad (id_ciudad, nombre, continente) VALUES (?, ?, ?)",
                        aeropuerto.getCiudad().getIdCiudad(),
                        aeropuerto.getCiudad().getNombre(),
                        aeropuerto.getCiudad().getContinente() != null
                                ? aeropuerto.getCiudad().getContinente().name() : null);
            }
            aeropuertoRepositorio.insertar(aeropuerto);
        }
        return aeropuertos;
    }
}
