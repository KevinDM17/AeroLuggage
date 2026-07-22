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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    @Transactional
    public List<VueloProgramado> listarTodos() {
        return vueloProgramadoRepositorio.obtenerTodos();
    }

    @Transactional
    public List<VueloProgramado> listarPorAeropuerto(final String iata) {
        return vueloProgramadoRepositorio.obtenerPorAeropuerto(iata);
    }

    @Transactional
    public Optional<VueloProgramado> obtenerPorId(final String id) {
        return vueloProgramadoRepositorio.obtenerPorId(id);
    }

    @Transactional
    public VueloProgramado crear(final VueloProgramado vuelo) {
        if (vuelo.getCodigo() == null || vuelo.getCodigo().isBlank()) {
            throw new IllegalArgumentException("El codigo del vuelo es requerido");
        }
        if (vuelo.getAeropuertoOrigen() == null || vuelo.getAeropuertoDestino() == null) {
            throw new IllegalArgumentException("Los aeropuertos de origen y destino son requeridos");
        }
        vuelo.setActivo(true);
        vueloProgramadoRepositorio.insertar(vuelo);
        return vueloProgramadoRepositorio.obtenerPorId(vuelo.getIdVueloProgramado()).orElse(vuelo);
    }

    @Transactional
    public Optional<VueloProgramado> actualizar(final String id, final VueloProgramado vuelo) {
        final Optional<VueloProgramado> existente = vueloProgramadoRepositorio.obtenerPorId(id);
        if (existente.isEmpty()) {
            return Optional.empty();
        }
        vuelo.setIdVueloProgramado(id);
        vuelo.setActivo(true);
        vueloProgramadoRepositorio.actualizar(vuelo);
        return vueloProgramadoRepositorio.obtenerPorId(id);
    }

    @Transactional
    public boolean eliminar(final String id) {
        final Optional<VueloProgramado> existente = vueloProgramadoRepositorio.obtenerPorId(id);
        if (existente.isEmpty()) {
            return false;
        }
        vueloProgramadoRepositorio.eliminar(id);
        return true;
    }

    @Transactional
    public Map<String, Object> crearDesdeTexto(final String content) {
        final List<String> errors = new ArrayList<>();
        final Map<String, Aeropuerto> aeropuertosPorIcao = aeropuertoRepositorio.obtenerTodosComoMapa();
        final String[] lineas = content.split("\\r?\\n");
        int created = 0;
        int skipped = 0;
        int secuencia = vueloProgramadoRepositorio.obtenerMaximoSecuencial();

        for (final String lineaCruda : lineas) {
            final String linea = lineaCruda.trim();
            if (linea.isEmpty() || linea.startsWith("*")) {
                skipped++;
                continue;
            }
            final String[] partes = linea.split("-");
            if (partes.length != 5) {
                errors.add("Formato invalido: " + linea);
                skipped++;
                continue;
            }
            final String icaoOrigen = partes[0].trim();
            final String icaoDestino = partes[1].trim();
            final Aeropuerto origen = aeropuertosPorIcao.get(icaoOrigen);
            final Aeropuerto destino = aeropuertosPorIcao.get(icaoDestino);
            if (origen == null || destino == null) {
                errors.add("Aeropuerto no encontrado: " + icaoOrigen + " o " + icaoDestino);
                skipped++;
                continue;
            }
            final LocalTime horaSalida;
            final LocalTime horaLlegada;
            try {
                horaSalida = LocalTime.parse(partes[2].trim(), DateTimeFormatter.ofPattern("HH:mm"));
                horaLlegada = LocalTime.parse(partes[3].trim(), DateTimeFormatter.ofPattern("HH:mm"))
                        .minusHours(origen.getHusoGMT())
                        .plusHours(destino.getHusoGMT());
            } catch (final DateTimeParseException e) {
                errors.add("Hora invalida en linea: " + linea);
                skipped++;
                continue;
            }
            final int capacidad;
            try {
                capacidad = Integer.parseInt(partes[4].trim());
            } catch (final NumberFormatException e) {
                errors.add("Capacidad invalida en linea: " + linea);
                skipped++;
                continue;
            }
            secuencia++;
            final String id = String.format("VP%06d", secuencia);
            final String codigo = icaoOrigen + "-" + icaoDestino + "-" + partes[2].trim();
            final VueloProgramado vuelo = new VueloProgramado(id, codigo, horaSalida, horaLlegada, capacidad,
                    origen, destino);
            vuelo.setActivo(true);
            vueloProgramadoRepositorio.insertar(vuelo);
            created++;
        }

        final Map<String, Object> resultado = new HashMap<>();
        resultado.put("received", lineas.length);
        resultado.put("created", created);
        resultado.put("skipped", skipped);
        resultado.put("errors", errors);
        return resultado;
    }
}
