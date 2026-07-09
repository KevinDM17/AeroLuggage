package pe.edu.pucp.aeroluggage.controller.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ciudad;
import pe.edu.pucp.aeroluggage.dominio.enums.Continente;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoRequest;
import pe.edu.pucp.aeroluggage.dto.simulacion.rest.AeropuertoResponse;
import pe.edu.pucp.aeroluggage.servicios.ServicioAeropuerto;
import pe.edu.pucp.aeroluggage.simulacion.OperacionesDiaADiaService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/aeropuertos")
@RequiredArgsConstructor
public class AeropuertoRestController {

    private final ServicioAeropuerto servicioAeropuerto;

    @Lazy
    @Autowired
    private OperacionesDiaADiaService operacionesService;

    @GetMapping
    public List<AeropuertoResponse> listar() {
        return servicioAeropuerto.listarTodos().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @GetMapping("/{iata}")
    public AeropuertoResponse obtener(@PathVariable final String iata) {
        return servicioAeropuerto.obtenerPorId(iata)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aeropuerto no encontrado: " + iata));
    }

    @PostMapping
    public AeropuertoResponse crear(@RequestBody final AeropuertoRequest request) {
        final Aeropuerto aeropuerto = toEntity(request);
        final Aeropuerto creado = servicioAeropuerto.crear(aeropuerto);
        return toResponse(creado);
    }

    @PutMapping("/{iata}")
    public AeropuertoResponse actualizar(@PathVariable final String iata,
                                          @RequestBody final AeropuertoRequest request) {
        final Aeropuerto aeropuerto = toEntity(request);
        final Optional<Aeropuerto> actualizado = servicioAeropuerto.actualizar(iata, aeropuerto);
        final AeropuertoResponse response = actualizado
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aeropuerto no encontrado: " + iata));
        if (operacionesService != null) {
            operacionesService.onAeropuertoActualizado(iata, request.getCapacidadAlmacen());
        }
        return response;
    }

    private Aeropuerto toEntity(final AeropuertoRequest request) {
        final Aeropuerto aeropuerto = new Aeropuerto();
        aeropuerto.setIdAeropuerto(request.getIdAeropuerto());
        aeropuerto.setCapacidadAlmacen(request.getCapacidadAlmacen());
        aeropuerto.setMaletasActuales(request.getMaletasActuales());
        aeropuerto.setLatitud(request.getLatitud());
        aeropuerto.setLongitud(request.getLongitud());
        aeropuerto.setHusoGMT(request.getHusoGMT());
        if (request.getNombreCiudad() != null && !request.getNombreCiudad().isBlank()) {
            final String nombreCiudad = request.getNombreCiudad().trim();
            final String idCiudad = nombreCiudad.toUpperCase().replaceAll("[^A-Z0-9]", "_");
            final Ciudad ciudad = new Ciudad();
            ciudad.setIdCiudad(idCiudad);
            ciudad.setNombre(nombreCiudad);
            if (request.getContinente() != null && !request.getContinente().isBlank()) {
                ciudad.setContinente(Continente.valueOf(request.getContinente().trim().toUpperCase()));
            }
            aeropuerto.setCiudad(ciudad);
        }
        return aeropuerto;
    }

    private AeropuertoResponse toResponse(final Aeropuerto aeropuerto) {
        return AeropuertoResponse.builder()
                .withIdAeropuerto(aeropuerto.getIdAeropuerto())
                .withCiudad(aeropuerto.getCiudad())
                .withCapacidadAlmacen(aeropuerto.getCapacidadAlmacen())
                .withMaletasActuales(aeropuerto.getMaletasActuales())
                .withLatitud(aeropuerto.getLatitud())
                .withLongitud(aeropuerto.getLongitud())
                .withHusoGMT(aeropuerto.getHusoGMT())
                .build();
    }
}
