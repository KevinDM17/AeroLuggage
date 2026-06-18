package pe.edu.pucp.aeroluggage.servicios;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.cdimascio.dotenv.Dotenv;
import pe.edu.pucp.aeroluggage.cargador.CargadorAeropuertos;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

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

    @Transactional
    public List<Aeropuerto> listarTodos() {
        return aeropuertoRepositorio.obtenerTodosConCiudad();
    }

    @Transactional
    public Optional<Aeropuerto> obtenerPorId(final String iata) {
        return aeropuertoRepositorio.obtenerPorIdConCiudad(iata);
    }

    @Transactional
    public Aeropuerto crear(final Aeropuerto aeropuerto) {
        if (aeropuerto.getCiudad() != null) {
            final Ciudad ciudad = aeropuerto.getCiudad();
            jdbcTemplate.update(
                    "INSERT OR IGNORE INTO ciudad (id_ciudad, nombre, continente) VALUES (?, ?, ?)",
                    ciudad.getIdCiudad(),
                    ciudad.getNombre(),
                    ciudad.getContinente() != null ? ciudad.getContinente().name() : null);
        }
        aeropuerto.setActivo(true);
        aeropuertoRepositorio.insertar(aeropuerto);
        return aeropuertoRepositorio.obtenerPorIdConCiudad(aeropuerto.getIdAeropuerto())
                .orElse(aeropuerto);
    }

    @Transactional
    public Optional<Aeropuerto> actualizar(final String iata, final Aeropuerto aeropuerto) {
        final Optional<Aeropuerto> existente = aeropuertoRepositorio.obtenerPorIdConCiudad(iata);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        aeropuerto.setIdAeropuerto(iata);
        if (aeropuerto.getCiudad() != null) {
            final Ciudad ciudad = aeropuerto.getCiudad();
            jdbcTemplate.update(
                    "INSERT OR IGNORE INTO ciudad (id_ciudad, nombre, continente) VALUES (?, ?, ?)",
                    ciudad.getIdCiudad(),
                    ciudad.getNombre(),
                    ciudad.getContinente() != null ? ciudad.getContinente().name() : null);
        }
        aeropuertoRepositorio.actualizar(aeropuerto);
        return aeropuertoRepositorio.obtenerPorIdConCiudad(iata);
    }

    @Transactional
    public boolean eliminar(final String iata) {
        final Optional<Aeropuerto> existente = aeropuertoRepositorio.obtenerPorIdConCiudad(iata);
        if (existente.isEmpty()) {
            return false;
        }
        aeropuertoRepositorio.eliminar(iata);
        return true;
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
            aeropuerto.setActivo(true);
            aeropuertoRepositorio.insertar(aeropuerto);
        }
        return aeropuertos;
    }
}
