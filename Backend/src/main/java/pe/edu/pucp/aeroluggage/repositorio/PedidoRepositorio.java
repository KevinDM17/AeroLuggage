package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class PedidoRepositorio {

    private static final String SELECT_CON_JOINS =
            "SELECT p.id_pedido, p.fecha_registro, p.fecha_hora_plazo, p.cantidad_maletas, p.estado, " +
            "ao.id_aeropuerto AS ao_id, ao.capacidad_almacen AS ao_cap, ao.maletas_actuales AS ao_mal, " +
            "ao.longitud AS ao_lon, ao.latitud AS ao_lat, ao.huso_gmt AS ao_huso, " +
            "co.id_ciudad AS co_id, co.nombre AS co_nombre, co.continente AS co_continente, " +
            "ad.id_aeropuerto AS ad_id, ad.capacidad_almacen AS ad_cap, ad.maletas_actuales AS ad_mal, " +
            "ad.longitud AS ad_lon, ad.latitud AS ad_lat, ad.huso_gmt AS ad_huso, " +
            "cd.id_ciudad AS cd_id, cd.nombre AS cd_nombre, cd.continente AS cd_continente " +
            "FROM pedido p " +
            "JOIN aeropuerto ao ON p.id_aeropuerto_origen  = ao.id_aeropuerto " +
            "JOIN ciudad     co ON ao.id_ciudad = co.id_ciudad " +
            "JOIN aeropuerto ad ON p.id_aeropuerto_destino = ad.id_aeropuerto " +
            "JOIN ciudad     cd ON ad.id_ciudad = cd.id_ciudad";

    private final JdbcTemplate jdbcTemplate;

    public PedidoRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertar(Pedido pedido) {
        String sql = "INSERT INTO pedido " +
                "(id_pedido, id_aeropuerto_origen, id_aeropuerto_destino, " +
                "fecha_registro, fecha_hora_plazo, cantidad_maletas, estado) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                pedido.getIdPedido(),
                pedido.getAeropuertoOrigen() != null ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null,
                pedido.getAeropuertoDestino() != null ? pedido.getAeropuertoDestino().getIdAeropuerto() : null,
                pedido.getFechaRegistro() != null ? pedido.getFechaRegistro().toString() : null,
                pedido.getFechaHoraPlazo() != null ? pedido.getFechaHoraPlazo().toString() : null,
                pedido.getCantidadMaletas(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null);
    }

    public Optional<Pedido> obtenerPorId(String id) {
        String sql = SELECT_CON_JOINS + " WHERE p.id_pedido = ?";
        List<Pedido> resultado = jdbcTemplate.query(sql, new PedidoRowMapper(), id);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    public List<Pedido> obtenerTodos() {
        return jdbcTemplate.query(SELECT_CON_JOINS, new PedidoRowMapper());
    }

    public int actualizar(Pedido pedido) {
        String sql = "UPDATE pedido SET id_aeropuerto_origen=?, id_aeropuerto_destino=?, " +
                "fecha_registro=?, fecha_hora_plazo=?, cantidad_maletas=?, estado=? " +
                "WHERE id_pedido=?";
        return jdbcTemplate.update(sql,
                pedido.getAeropuertoOrigen() != null ? pedido.getAeropuertoOrigen().getIdAeropuerto() : null,
                pedido.getAeropuertoDestino() != null ? pedido.getAeropuertoDestino().getIdAeropuerto() : null,
                pedido.getFechaRegistro() != null ? pedido.getFechaRegistro().toString() : null,
                pedido.getFechaHoraPlazo() != null ? pedido.getFechaHoraPlazo().toString() : null,
                pedido.getCantidadMaletas(),
                pedido.getEstado() != null ? pedido.getEstado().name() : null,
                pedido.getIdPedido());
    }

    public int eliminar(String id) {
        return jdbcTemplate.update("DELETE FROM pedido WHERE id_pedido=?", id);
    }

    private static Aeropuerto mapAeropuerto(ResultSet rs, String prefijo, String prefijoCiudad)
            throws SQLException {
        Ciudad ciudad = new Ciudad(
                rs.getString(prefijoCiudad + "_id"),
                rs.getString(prefijoCiudad + "_nombre"),
                Continente.valueOf(rs.getString(prefijoCiudad + "_continente"))
        );
        Aeropuerto aeropuerto = new Aeropuerto();
        aeropuerto.setIdAeropuerto(rs.getString(prefijo + "_id"));
        aeropuerto.setCiudad(ciudad);
        aeropuerto.setCapacidadAlmacen(rs.getInt(prefijo + "_cap"));
        aeropuerto.setMaletasActuales(rs.getInt(prefijo + "_mal"));
        aeropuerto.setLongitud(rs.getFloat(prefijo + "_lon"));
        aeropuerto.setLatitud(rs.getFloat(prefijo + "_lat"));
        aeropuerto.setHusoGMT(rs.getInt(prefijo + "_huso"));
        return aeropuerto;
    }

    private static final class PedidoRowMapper implements RowMapper<Pedido> {
        @Override
        public Pedido mapRow(ResultSet rs, int rowNum) throws SQLException {
            Aeropuerto origen = mapAeropuerto(rs, "ao", "co");
            Aeropuerto destino = mapAeropuerto(rs, "ad", "cd");

            String fechaRegistroStr = rs.getString("fecha_registro");
            String fechaPlazoStr = rs.getString("fecha_hora_plazo");
            String estadoStr = rs.getString("estado");

            return new Pedido(
                    rs.getString("id_pedido"),
                    origen,
                    destino,
                    fechaRegistroStr != null ? LocalDateTime.parse(fechaRegistroStr) : null,
                    fechaPlazoStr != null ? LocalDateTime.parse(fechaPlazoStr) : null,
                    rs.getInt("cantidad_maletas"),
                    estadoStr != null ? EstadoPedido.valueOf(estadoStr) : null
            );
        }
    }
}
