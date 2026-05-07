package pe.edu.pucp.aeroluggage.repositorio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
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
        String sql = "INSERT INTO ruta (id_ruta, id_maleta, plazo_maximo_dias, duracion, estado) " +
                "VALUES (?, ?, ?, ?, ?)";
        int filas = jdbcTemplate.update(sql,
                ruta.getIdRuta(),
                ruta.getIdMaleta(),
                ruta.getPlazoMaximoDias(),
                ruta.getDuracion(),
                ruta.getEstado() != null ? ruta.getEstado().name() : null);

        insertarSubrutas(ruta.getIdRuta(), ruta.getSubrutas());
        return filas;
    }

    public Optional<Ruta> obtenerPorId(String id) {
        String sql = "SELECT id_ruta, id_maleta, plazo_maximo_dias, duracion, estado " +
                "FROM ruta WHERE id_ruta = ?";
        List<Ruta> resultado = jdbcTemplate.query(sql, new RutaRowMapper(), id);
        if (resultado.isEmpty()) {
            return Optional.empty();
        }
        Ruta ruta = resultado.get(0);
        ruta.setSubrutas(cargarSubrutas(id));
        return Optional.of(ruta);
    }

    public List<Ruta> obtenerTodos() {
        String sql = "SELECT id_ruta, id_maleta, plazo_maximo_dias, duracion, estado FROM ruta";
        List<Ruta> rutas = jdbcTemplate.query(sql, new RutaRowMapper());
        for (Ruta ruta : rutas) {
            ruta.setSubrutas(cargarSubrutas(ruta.getIdRuta()));
        }
        return rutas;
    }

    public int actualizar(Ruta ruta) {
        String sql = "UPDATE ruta SET id_maleta=?, plazo_maximo_dias=?, duracion=?, estado=? " +
                "WHERE id_ruta=?";
        int filas = jdbcTemplate.update(sql,
                ruta.getIdMaleta(),
                ruta.getPlazoMaximoDias(),
                ruta.getDuracion(),
                ruta.getEstado() != null ? ruta.getEstado().name() : null,
                ruta.getIdRuta());

        eliminarSubrutasPorRuta(ruta.getIdRuta());
        insertarSubrutas(ruta.getIdRuta(), ruta.getSubrutas());
        return filas;
    }

    public int eliminar(String id) {
        eliminarSubrutasPorRuta(id);
        return jdbcTemplate.update("DELETE FROM ruta WHERE id_ruta=?", id);
    }

    public int insertarSubruta(String idRuta, String idVueloInstancia, int orden) {
        String sql = "INSERT INTO ruta_vuelo_instancia (id_ruta, id_vuelo_instancia, orden) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, idRuta, idVueloInstancia, orden);
    }

    public int eliminarSubrutasPorRuta(String idRuta) {
        return jdbcTemplate.update("DELETE FROM ruta_vuelo_instancia WHERE id_ruta=?", idRuta);
    }

    public List<String> obtenerIdsVuelosPorRuta(String idRuta) {
        String sql = "SELECT id_vuelo_instancia FROM ruta_vuelo_instancia " +
                "WHERE id_ruta = ? ORDER BY orden";
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("id_vuelo_instancia"), idRuta);
    }

    private void insertarSubrutas(String idRuta, List<VueloInstancia> subrutas) {
        if (subrutas == null || subrutas.isEmpty()) {
            return;
        }
        int orden = 1;
        for (VueloInstancia vi : subrutas) {
            if (vi != null && vi.getIdVueloInstancia() != null) {
                insertarSubruta(idRuta, vi.getIdVueloInstancia(), orden++);
            }
        }
    }

    private List<VueloInstancia> cargarSubrutas(String idRuta) {
        List<String> ids = obtenerIdsVuelosPorRuta(idRuta);
        List<VueloInstancia> subrutas = new ArrayList<>();
        for (String idVi : ids) {
            VueloInstancia vi = new VueloInstancia();
            vi.setIdVueloInstancia(idVi);
            subrutas.add(vi);
        }
        return subrutas;
    }

    private static final class RutaRowMapper implements RowMapper<Ruta> {
        @Override
        public Ruta mapRow(ResultSet rs, int rowNum) throws SQLException {
            String estadoStr = rs.getString("estado");
            return new Ruta(
                    rs.getString("id_ruta"),
                    rs.getString("id_maleta"),
                    rs.getDouble("plazo_maximo_dias"),
                    rs.getDouble("duracion"),
                    new ArrayList<>(),
                    estadoStr != null ? EstadoRuta.valueOf(estadoStr) : null
            );
        }
    }
}
