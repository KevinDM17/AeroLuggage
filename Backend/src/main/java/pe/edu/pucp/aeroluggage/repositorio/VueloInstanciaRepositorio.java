package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class VueloInstanciaRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public VueloInstanciaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertar(VueloInstancia vi, String idVueloProgramado) {
        String sql = "INSERT INTO vuelo_instancia " +
                "(id_vuelo_instancia, id_vuelo_programado, codigo, " +
                "fecha_salida, fecha_llegada, capacidad_maxima, capacidad_disponible, " +
                "id_aeropuerto_origen, id_aeropuerto_destino, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                vi.getIdVueloInstancia(),
                idVueloProgramado,
                vi.getCodigo(),
                vi.getFechaSalida() != null ? vi.getFechaSalida().toString() : null,
                vi.getFechaLlegada() != null ? vi.getFechaLlegada().toString() : null,
                vi.getCapacidadMaxima(),
                vi.getCapacidadDisponible(),
                vi.getAeropuertoOrigen() != null ? vi.getAeropuertoOrigen().getIdAeropuerto() : null,
                vi.getAeropuertoDestino() != null ? vi.getAeropuertoDestino().getIdAeropuerto() : null,
                vi.getEstado() != null ? vi.getEstado().name() : null);
    }

    public Optional<VueloInstancia> obtenerPorId(String id) {
        String sql = "SELECT id_vuelo_instancia, codigo, fecha_salida, fecha_llegada, " +
                "capacidad_maxima, capacidad_disponible, id_aeropuerto_origen, " +
                "id_aeropuerto_destino, estado FROM vuelo_instancia WHERE id_vuelo_instancia = ?";
        List<VueloInstancia> resultado = jdbcTemplate.query(sql, new VueloInstanciaRowMapper(), id);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    public List<VueloInstancia> obtenerTodos() {
        String sql = "SELECT id_vuelo_instancia, codigo, fecha_salida, fecha_llegada, " +
                "capacidad_maxima, capacidad_disponible, id_aeropuerto_origen, " +
                "id_aeropuerto_destino, estado FROM vuelo_instancia";
        return jdbcTemplate.query(sql, new VueloInstanciaRowMapper());
    }

    public int actualizar(VueloInstancia vi) {
        String sql = "UPDATE vuelo_instancia SET codigo=?, fecha_salida=?, fecha_llegada=?, " +
                "capacidad_maxima=?, capacidad_disponible=?, id_aeropuerto_origen=?, " +
                "id_aeropuerto_destino=?, estado=? WHERE id_vuelo_instancia=?";
        return jdbcTemplate.update(sql,
                vi.getCodigo(),
                vi.getFechaSalida() != null ? vi.getFechaSalida().toString() : null,
                vi.getFechaLlegada() != null ? vi.getFechaLlegada().toString() : null,
                vi.getCapacidadMaxima(),
                vi.getCapacidadDisponible(),
                vi.getAeropuertoOrigen() != null ? vi.getAeropuertoOrigen().getIdAeropuerto() : null,
                vi.getAeropuertoDestino() != null ? vi.getAeropuertoDestino().getIdAeropuerto() : null,
                vi.getEstado() != null ? vi.getEstado().name() : null,
                vi.getIdVueloInstancia());
    }

    public int eliminar(String id) {
        return jdbcTemplate.update("DELETE FROM vuelo_instancia WHERE id_vuelo_instancia=?", id);
    }

    private static final class VueloInstanciaRowMapper implements RowMapper<VueloInstancia> {
        @Override
        public VueloInstancia mapRow(ResultSet rs, int rowNum) throws SQLException {
            Aeropuerto origen = new Aeropuerto();
            origen.setIdAeropuerto(rs.getString("id_aeropuerto_origen"));

            Aeropuerto destino = new Aeropuerto();
            destino.setIdAeropuerto(rs.getString("id_aeropuerto_destino"));

            String fechaSalidaStr = rs.getString("fecha_salida");
            String fechaLlegadaStr = rs.getString("fecha_llegada");
            String estadoStr = rs.getString("estado");

            return new VueloInstancia(
                    rs.getString("id_vuelo_instancia"),
                    rs.getString("codigo"),
                    fechaSalidaStr != null ? LocalDateTime.parse(fechaSalidaStr) : null,
                    fechaLlegadaStr != null ? LocalDateTime.parse(fechaLlegadaStr) : null,
                    rs.getInt("capacidad_maxima"),
                    rs.getInt("capacidad_disponible"),
                    origen,
                    destino,
                    estadoStr != null ? EstadoVuelo.valueOf(estadoStr) : null
            );
        }
    }
}
