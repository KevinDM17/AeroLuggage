package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoRuta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class RutaRepositorio {

    private final JdbcTemplate jdbcTemplate;

    public RutaRepositorio(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertar(Ruta ruta) {
        eliminarSubrutasPorRuta(ruta.getIdMaleta());
        String sql = "INSERT OR REPLACE INTO ruta (id_maleta, plazo_maximo_dias, duracion, estado) " +
                "VALUES (?, ?, ?, ?)";
        int filas = jdbcTemplate.update(sql,
                ruta.getIdMaleta(),
                ruta.getPlazoMaximoDias(),
                ruta.getDuracion(),
                ruta.getEstado() != null ? ruta.getEstado().name() : null);

        insertarSubrutas(ruta.getIdMaleta(), ruta.getSubrutaIds());
        return filas;
    }

    public Optional<Ruta> obtenerPorId(String idMaleta) {
        String sql = "SELECT id_maleta, plazo_maximo_dias, duracion, estado " +
                "FROM ruta WHERE id_maleta = ?";
        List<Ruta> resultado = jdbcTemplate.query(sql, new RutaRowMapper(), idMaleta);
        if (resultado.isEmpty()) {
            return Optional.empty();
        }
        Ruta ruta = resultado.get(0);
        ruta.setSubrutaIds(cargarSubrutas(idMaleta));
        return Optional.of(ruta);
    }

    public List<Ruta> obtenerTodos() {
        String sql = "SELECT id_maleta, plazo_maximo_dias, duracion, estado FROM ruta";
        List<Ruta> rutas = jdbcTemplate.query(sql, new RutaRowMapper());
        for (Ruta ruta : rutas) {
            ruta.setSubrutaIds(cargarSubrutas(ruta.getIdMaleta()));
        }
        return rutas;
    }

    public List<Ruta> obtenerActivasPlanificadas() {
        String sql = "SELECT id_maleta, plazo_maximo_dias, duracion, estado " +
                "FROM ruta WHERE estado IN ('PLANIFICADA', 'ACTIVA')";
        List<Ruta> rutas = jdbcTemplate.query(sql, new RutaRowMapper());
        for (Ruta ruta : rutas) {
            ruta.setSubrutaIds(cargarSubrutas(ruta.getIdMaleta()));
        }
        return rutas;
    }

    public int actualizar(Ruta ruta) {
        String sql = "UPDATE ruta SET plazo_maximo_dias=?, duracion=?, estado=? " +
                "WHERE id_maleta=?";
        int filas = jdbcTemplate.update(sql,
                ruta.getPlazoMaximoDias(),
                ruta.getDuracion(),
                ruta.getEstado() != null ? ruta.getEstado().name() : null,
                ruta.getIdMaleta());

        eliminarSubrutasPorRuta(ruta.getIdMaleta());
        insertarSubrutas(ruta.getIdMaleta(), ruta.getSubrutaIds());
        return filas;
    }

    public int eliminar(String idMaleta) {
        eliminarSubrutasPorRuta(idMaleta);
        return jdbcTemplate.update("DELETE FROM ruta WHERE id_maleta=?", idMaleta);
    }

    public int insertarSubruta(String idMaleta, String idVueloInstancia, int orden) {
        String sql = "INSERT OR REPLACE INTO ruta_vuelo_instancia (id_maleta, id_vuelo_instancia, orden) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, idMaleta, idVueloInstancia, orden);
    }

    public int eliminarSubrutasPorRuta(String idMaleta) {
        return jdbcTemplate.update("DELETE FROM ruta_vuelo_instancia WHERE id_maleta=?", idMaleta);
    }

    public List<String> obtenerIdsVuelosPorRuta(String idMaleta) {
        String sql = "SELECT id_vuelo_instancia FROM ruta_vuelo_instancia " +
                "WHERE id_maleta = ? ORDER BY orden";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("id_vuelo_instancia"), idMaleta);
    }

    private void insertarSubrutas(String idMaleta, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int orden = 1;
        for (String idVi : ids) {
            if (idVi != null) {
                insertarSubruta(idMaleta, idVi, orden++);
            }
        }
    }

    private List<String> cargarSubrutas(String idMaleta) {
        return obtenerIdsVuelosPorRuta(idMaleta);
    }

    private static final class RutaRowMapper implements RowMapper<Ruta> {
        @Override
        public Ruta mapRow(ResultSet rs, int rowNum) throws SQLException {
            String estadoStr = rs.getString("estado");
            return new Ruta(
                    rs.getString("id_maleta"),
                    rs.getDouble("plazo_maximo_dias"),
                    rs.getDouble("duracion"),
                    new ArrayList<>(),
                    estadoStr != null ? EstadoRuta.valueOf(estadoStr) : null
            );
        }
    }
}
