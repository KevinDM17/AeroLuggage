package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public class VueloProgramadoRepositorio {

    private static final String SELECT_CON_JOINS =
            "SELECT vp.id_vuelo_programado, vp.codigo, vp.hora_salida, vp.hora_llegada, vp.capacidad_maxima, " +
            "ao.id_aeropuerto AS ao_id, ao.capacidad_almacen AS ao_cap, ao.maletas_actuales AS ao_mal, " +
            "ao.longitud AS ao_lon, ao.latitud AS ao_lat, ao.huso_gmt AS ao_huso, " +
            "co.id_ciudad AS co_id, co.nombre AS co_nombre, co.continente AS co_continente, " +
            "ad.id_aeropuerto AS ad_id, ad.capacidad_almacen AS ad_cap, ad.maletas_actuales AS ad_mal, " +
            "ad.longitud AS ad_lon, ad.latitud AS ad_lat, ad.huso_gmt AS ad_huso, " +
            "cd.id_ciudad AS cd_id, cd.nombre AS cd_nombre, cd.continente AS cd_continente " +
            "FROM vuelo_programado vp " +
            "JOIN aeropuerto ao ON vp.id_aeropuerto_origen  = ao.id_aeropuerto " +
            "JOIN ciudad     co ON ao.id_ciudad = co.id_ciudad " +
            "JOIN aeropuerto ad ON vp.id_aeropuerto_destino = ad.id_aeropuerto " +
            "JOIN ciudad     cd ON ad.id_ciudad = cd.id_ciudad";

    private final JdbcTemplate jdbcTemplate;

    public VueloProgramadoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertar(VueloProgramado vueloProgramado) {
        String sql = "INSERT INTO vuelo_programado " +
                "(id_vuelo_programado, codigo, hora_salida, hora_llegada, capacidad_maxima, " +
                "id_aeropuerto_origen, id_aeropuerto_destino) VALUES (?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                vueloProgramado.getIdVueloProgramado(),
                vueloProgramado.getCodigo(),
                vueloProgramado.getHoraSalida() != null ? vueloProgramado.getHoraSalida().toString() : null,
                vueloProgramado.getHoraLlegada() != null ? vueloProgramado.getHoraLlegada().toString() : null,
                vueloProgramado.getCapacidadBase(),
                vueloProgramado.getAeropuertoOrigen() != null ? vueloProgramado.getAeropuertoOrigen().getIdAeropuerto() : null,
                vueloProgramado.getAeropuertoDestino() != null ? vueloProgramado.getAeropuertoDestino().getIdAeropuerto() : null);
    }

    public Optional<VueloProgramado> obtenerPorId(String id) {
        String sql = SELECT_CON_JOINS + " WHERE vp.id_vuelo_programado = ?";
        List<VueloProgramado> resultado = jdbcTemplate.query(sql, new VueloProgramadoRowMapper(), id);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    public List<VueloProgramado> obtenerTodos() {
        return jdbcTemplate.query(SELECT_CON_JOINS, new VueloProgramadoRowMapper());
    }

    public int actualizar(VueloProgramado vueloProgramado) {
        String sql = "UPDATE vuelo_programado SET codigo=?, hora_salida=?, hora_llegada=?, " +
                "capacidad_maxima=?, id_aeropuerto_origen=?, id_aeropuerto_destino=? " +
                "WHERE id_vuelo_programado=?";
        return jdbcTemplate.update(sql,
                vueloProgramado.getCodigo(),
                vueloProgramado.getHoraSalida() != null ? vueloProgramado.getHoraSalida().toString() : null,
                vueloProgramado.getHoraLlegada() != null ? vueloProgramado.getHoraLlegada().toString() : null,
                vueloProgramado.getCapacidadBase(),
                vueloProgramado.getAeropuertoOrigen() != null ? vueloProgramado.getAeropuertoOrigen().getIdAeropuerto() : null,
                vueloProgramado.getAeropuertoDestino() != null ? vueloProgramado.getAeropuertoDestino().getIdAeropuerto() : null,
                vueloProgramado.getIdVueloProgramado());
    }

    public int eliminar(String id) {
        return jdbcTemplate.update("DELETE FROM vuelo_programado WHERE id_vuelo_programado=?", id);
    }

    private static final class VueloProgramadoRowMapper implements RowMapper<VueloProgramado> {
        @Override
        public VueloProgramado mapRow(ResultSet rs, int rowNum) throws SQLException {
            Ciudad ciudadOrigen = new Ciudad(rs.getString("co_id"), rs.getString("co_nombre"),
                    Continente.valueOf(rs.getString("co_continente")));
            Aeropuerto origen = new Aeropuerto();
            origen.setIdAeropuerto(rs.getString("ao_id"));
            origen.setCiudad(ciudadOrigen);
            origen.setCapacidadAlmacen(rs.getInt("ao_cap"));
            origen.setMaletasActuales(rs.getInt("ao_mal"));
            origen.setLongitud(rs.getFloat("ao_lon"));
            origen.setLatitud(rs.getFloat("ao_lat"));
            origen.setHusoGMT(rs.getInt("ao_huso"));

            Ciudad ciudadDestino = new Ciudad(rs.getString("cd_id"), rs.getString("cd_nombre"),
                    Continente.valueOf(rs.getString("cd_continente")));
            Aeropuerto destino = new Aeropuerto();
            destino.setIdAeropuerto(rs.getString("ad_id"));
            destino.setCiudad(ciudadDestino);
            destino.setCapacidadAlmacen(rs.getInt("ad_cap"));
            destino.setMaletasActuales(rs.getInt("ad_mal"));
            destino.setLongitud(rs.getFloat("ad_lon"));
            destino.setLatitud(rs.getFloat("ad_lat"));
            destino.setHusoGMT(rs.getInt("ad_huso"));

            String horaSalidaStr = rs.getString("hora_salida");
            String horaLlegadaStr = rs.getString("hora_llegada");
            return new VueloProgramado(
                    rs.getString("id_vuelo_programado"),
                    rs.getString("codigo"),
                    horaSalidaStr != null ? LocalTime.parse(horaSalidaStr) : null,
                    horaLlegadaStr != null ? LocalTime.parse(horaLlegadaStr) : null,
                    rs.getInt("capacidad_maxima"),
                    origen,
                    destino
            );
        }
    }
}
