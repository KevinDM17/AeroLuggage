package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class AeropuertoRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public AeropuertoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertar(Aeropuerto aeropuerto) {
        String sql = "INSERT INTO aeropuerto " +
                "(id_aeropuerto, id_ciudad, capacidad_almacen, maletas_actuales, longitud, latitud, huso_gmt) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        String idCiudad = aeropuerto.getCiudad() != null ? aeropuerto.getCiudad().getIdCiudad() : null;
        return jdbcTemplate.update(sql,
                aeropuerto.getIdAeropuerto(),
                idCiudad,
                aeropuerto.getCapacidadAlmacen(),
                aeropuerto.getMaletasActuales(),
                aeropuerto.getLongitud(),
                aeropuerto.getLatitud(),
                aeropuerto.getHusoGMT());
    }

    public Optional<Aeropuerto> obtenerPorId(String id) {
        String sql = "SELECT id_aeropuerto, capacidad_almacen, maletas_actuales, longitud, latitud, huso_gmt " +
                "FROM aeropuerto WHERE id_aeropuerto = ?";
        List<Aeropuerto> resultado = jdbcTemplate.query(sql, new AeropuertoRowMapper(), id);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    public List<Aeropuerto> obtenerTodos() {
        String sql = "SELECT id_aeropuerto, capacidad_almacen, maletas_actuales, longitud, latitud, huso_gmt " +
                "FROM aeropuerto";
        return jdbcTemplate.query(sql, new AeropuertoRowMapper());
    }

    public Optional<Aeropuerto> obtenerPorIdConCiudad(String id) {
        String sql = "SELECT a.id_aeropuerto, a.capacidad_almacen, a.maletas_actuales, " +
                "a.longitud, a.latitud, a.huso_gmt, " +
                "c.id_ciudad, c.nombre AS ciudad_nombre, c.continente " +
                "FROM aeropuerto a JOIN ciudad c ON a.id_ciudad = c.id_ciudad " +
                "WHERE a.id_aeropuerto = ?";
        List<Aeropuerto> resultado = jdbcTemplate.query(sql, new AeropuertoConCiudadRowMapper(), id);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    public List<Aeropuerto> obtenerTodosConCiudad() {
        String sql = "SELECT a.id_aeropuerto, a.capacidad_almacen, a.maletas_actuales, " +
                "a.longitud, a.latitud, a.huso_gmt, " +
                "c.id_ciudad, c.nombre AS ciudad_nombre, c.continente " +
                "FROM aeropuerto a JOIN ciudad c ON a.id_ciudad = c.id_ciudad";
        return jdbcTemplate.query(sql, new AeropuertoConCiudadRowMapper());
    }

    public Map<String, Aeropuerto> obtenerTodosComoMapa() {
        return obtenerTodosConCiudad().stream()
                .collect(Collectors.toMap(Aeropuerto::getIdAeropuerto, a -> a));
    }

    public int actualizar(Aeropuerto aeropuerto) {
        String sql = "UPDATE aeropuerto SET id_ciudad=?, capacidad_almacen=?, maletas_actuales=?, " +
                "longitud=?, latitud=?, huso_gmt=? WHERE id_aeropuerto=?";
        String idCiudad = aeropuerto.getCiudad() != null ? aeropuerto.getCiudad().getIdCiudad() : null;
        return jdbcTemplate.update(sql,
                idCiudad,
                aeropuerto.getCapacidadAlmacen(),
                aeropuerto.getMaletasActuales(),
                aeropuerto.getLongitud(),
                aeropuerto.getLatitud(),
                aeropuerto.getHusoGMT(),
                aeropuerto.getIdAeropuerto());
    }

    public int eliminar(String id) {
        return jdbcTemplate.update("DELETE FROM aeropuerto WHERE id_aeropuerto=?", id);
    }

    private static final class AeropuertoRowMapper implements RowMapper<Aeropuerto> {
        @Override
        public Aeropuerto mapRow(ResultSet rs, int rowNum) throws SQLException {
            Aeropuerto aeropuerto = new Aeropuerto();
            aeropuerto.setIdAeropuerto(rs.getString("id_aeropuerto"));
            aeropuerto.setCapacidadAlmacen(rs.getInt("capacidad_almacen"));
            aeropuerto.setMaletasActuales(rs.getInt("maletas_actuales"));
            aeropuerto.setLongitud(rs.getFloat("longitud"));
            aeropuerto.setLatitud(rs.getFloat("latitud"));
            aeropuerto.setHusoGMT(rs.getInt("huso_gmt"));
            return aeropuerto;
        }
    }

    private static final class AeropuertoConCiudadRowMapper implements RowMapper<Aeropuerto> {
        @Override
        public Aeropuerto mapRow(ResultSet rs, int rowNum) throws SQLException {
            Ciudad ciudad = new Ciudad(
                    rs.getString("id_ciudad"),
                    rs.getString("ciudad_nombre"),
                    Continente.valueOf(rs.getString("continente"))
            );
            Aeropuerto aeropuerto = new Aeropuerto();
            aeropuerto.setIdAeropuerto(rs.getString("id_aeropuerto"));
            aeropuerto.setCiudad(ciudad);
            aeropuerto.setCapacidadAlmacen(rs.getInt("capacidad_almacen"));
            aeropuerto.setMaletasActuales(rs.getInt("maletas_actuales"));
            aeropuerto.setLongitud(rs.getFloat("longitud"));
            aeropuerto.setLatitud(rs.getFloat("latitud"));
            aeropuerto.setHusoGMT(rs.getInt("huso_gmt"));
            return aeropuerto;
        }
    }
}
