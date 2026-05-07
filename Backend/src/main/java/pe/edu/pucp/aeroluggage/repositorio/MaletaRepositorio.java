package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoMaleta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoPedido;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MaletaRepositorio {

    private static final String SELECT_CON_PEDIDO =
            "SELECT m.id_maleta, m.fecha_registro, m.fecha_llegada, m.estado, " +
            "p.id_pedido, p.fecha_registro AS p_fecha_registro, p.fecha_hora_plazo, " +
            "p.cantidad_maletas, p.estado AS p_estado, " +
            "p.id_aeropuerto_origen, p.id_aeropuerto_destino " +
            "FROM maleta m " +
            "JOIN pedido p ON m.id_pedido = p.id_pedido";

    private static final String SELECT_CON_JOINS =
            "SELECT m.id_maleta, m.fecha_registro, m.fecha_llegada, m.estado, " +
            "p.id_pedido, p.fecha_registro AS p_fecha_registro, p.fecha_hora_plazo, " +
            "p.cantidad_maletas, p.estado AS p_estado, " +
            "ao.id_aeropuerto AS ao_id, ao.capacidad_almacen AS ao_cap, ao.maletas_actuales AS ao_mal, " +
            "ao.longitud AS ao_lon, ao.latitud AS ao_lat, ao.huso_gmt AS ao_huso, " +
            "co.id_ciudad AS co_id, co.nombre AS co_nombre, co.continente AS co_continente, " +
            "ad.id_aeropuerto AS ad_id, ad.capacidad_almacen AS ad_cap, ad.maletas_actuales AS ad_mal, " +
            "ad.longitud AS ad_lon, ad.latitud AS ad_lat, ad.huso_gmt AS ad_huso, " +
            "cd.id_ciudad AS cd_id, cd.nombre AS cd_nombre, cd.continente AS cd_continente " +
            "FROM maleta m " +
            "JOIN pedido     p  ON m.id_pedido = p.id_pedido " +
            "JOIN aeropuerto ao ON p.id_aeropuerto_origen  = ao.id_aeropuerto " +
            "JOIN ciudad     co ON ao.id_ciudad = co.id_ciudad " +
            "JOIN aeropuerto ad ON p.id_aeropuerto_destino = ad.id_aeropuerto " +
            "JOIN ciudad     cd ON ad.id_ciudad = cd.id_ciudad";

    private final JdbcTemplate jdbcTemplate;

    public MaletaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertar(Maleta maleta) {
        String sql = "INSERT INTO maleta (id_maleta, id_pedido, fecha_registro, fecha_llegada, estado) " +
                "VALUES (?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql,
                maleta.getIdMaleta(),
                maleta.getPedido() != null ? maleta.getPedido().getIdPedido() : null,
                maleta.getFechaRegistro() != null ? maleta.getFechaRegistro().toString() : null,
                maleta.getFechaLlegada() != null ? maleta.getFechaLlegada().toString() : null,
                maleta.getEstado() != null ? maleta.getEstado().name() : null);
    }

    public Optional<Maleta> obtenerPorId(String id) {
        String sql = SELECT_CON_JOINS + " WHERE m.id_maleta = ?";
        List<Maleta> resultado = jdbcTemplate.query(sql, new MaletaRowMapper(), id);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }

    public List<Maleta> obtenerTodos() {
        return jdbcTemplate.query(SELECT_CON_JOINS, new MaletaRowMapper());
    }

    public List<Maleta> obtenerTodosConCache(Map<String, Aeropuerto> aeropuertos) {
        return jdbcTemplate.query(SELECT_CON_PEDIDO, new MaletaConCacheRowMapper(aeropuertos));
    }

    public int actualizar(Maleta maleta) {
        String sql = "UPDATE maleta SET id_pedido=?, fecha_registro=?, fecha_llegada=?, estado=? " +
                "WHERE id_maleta=?";
        return jdbcTemplate.update(sql,
                maleta.getPedido() != null ? maleta.getPedido().getIdPedido() : null,
                maleta.getFechaRegistro() != null ? maleta.getFechaRegistro().toString() : null,
                maleta.getFechaLlegada() != null ? maleta.getFechaLlegada().toString() : null,
                maleta.getEstado() != null ? maleta.getEstado().name() : null,
                maleta.getIdMaleta());
    }

    public int eliminar(String id) {
        return jdbcTemplate.update("DELETE FROM maleta WHERE id_maleta=?", id);
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

    private static final class MaletaRowMapper implements RowMapper<Maleta> {
        @Override
        public Maleta mapRow(ResultSet rs, int rowNum) throws SQLException {
            Aeropuerto origen = mapAeropuerto(rs, "ao", "co");
            Aeropuerto destino = mapAeropuerto(rs, "ad", "cd");

            String pFechaRegistroStr = rs.getString("p_fecha_registro");
            String pFechaPlazoStr = rs.getString("fecha_hora_plazo");
            String pEstadoStr = rs.getString("p_estado");

            Pedido pedido = new Pedido(
                    rs.getString("id_pedido"),
                    origen,
                    destino,
                    pFechaRegistroStr != null ? LocalDateTime.parse(pFechaRegistroStr) : null,
                    pFechaPlazoStr != null ? LocalDateTime.parse(pFechaPlazoStr) : null,
                    rs.getInt("cantidad_maletas"),
                    pEstadoStr != null ? EstadoPedido.valueOf(pEstadoStr) : null
            );

            String fechaRegistroStr = rs.getString("fecha_registro");
            String fechaLlegadaStr = rs.getString("fecha_llegada");
            String estadoStr = rs.getString("estado");

            return new Maleta(
                    rs.getString("id_maleta"),
                    pedido,
                    fechaRegistroStr != null ? LocalDateTime.parse(fechaRegistroStr) : null,
                    fechaLlegadaStr != null ? LocalDateTime.parse(fechaLlegadaStr) : null,
                    estadoStr != null ? EstadoMaleta.valueOf(estadoStr) : null
            );
        }
    }

    private static final class MaletaConCacheRowMapper implements RowMapper<Maleta> {
        private final Map<String, Aeropuerto> aeropuertos;

        MaletaConCacheRowMapper(Map<String, Aeropuerto> aeropuertos) {
            this.aeropuertos = aeropuertos;
        }

        @Override
        public Maleta mapRow(ResultSet rs, int rowNum) throws SQLException {
            Aeropuerto origen  = aeropuertos.get(rs.getString("id_aeropuerto_origen"));
            Aeropuerto destino = aeropuertos.get(rs.getString("id_aeropuerto_destino"));

            String pFechaRegistroStr = rs.getString("p_fecha_registro");
            String pFechaPlazoStr    = rs.getString("fecha_hora_plazo");
            String pEstadoStr        = rs.getString("p_estado");

            Pedido pedido = new Pedido(
                    rs.getString("id_pedido"),
                    origen,
                    destino,
                    pFechaRegistroStr != null ? LocalDateTime.parse(pFechaRegistroStr) : null,
                    pFechaPlazoStr    != null ? LocalDateTime.parse(pFechaPlazoStr)    : null,
                    rs.getInt("cantidad_maletas"),
                    pEstadoStr != null ? EstadoPedido.valueOf(pEstadoStr) : null
            );

            String fechaRegistroStr = rs.getString("fecha_registro");
            String fechaLlegadaStr  = rs.getString("fecha_llegada");
            String estadoStr        = rs.getString("estado");

            return new Maleta(
                    rs.getString("id_maleta"),
                    pedido,
                    fechaRegistroStr != null ? LocalDateTime.parse(fechaRegistroStr) : null,
                    fechaLlegadaStr  != null ? LocalDateTime.parse(fechaLlegadaStr)  : null,
                    estadoStr        != null ? EstadoMaleta.valueOf(estadoStr)        : null
            );
        }
    }
}
