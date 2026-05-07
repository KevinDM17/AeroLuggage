package pe.edu.pucp.aeroluggage.servicios;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.pucp.aeroluggage.cargador.GeneradorVueloInstancias;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.repositorio.VueloInstanciaRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.VueloProgramadoRepositorio;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServicioVueloInstancia {

    private final VueloProgramadoRepositorio vueloProgramadoRepositorio;
    private final VueloInstanciaRepositorio vueloInstanciaRepositorio;

    public ServicioVueloInstancia(VueloProgramadoRepositorio vueloProgramadoRepositorio,
                                  VueloInstanciaRepositorio vueloInstanciaRepositorio) {
        this.vueloProgramadoRepositorio = vueloProgramadoRepositorio;
        this.vueloInstanciaRepositorio = vueloInstanciaRepositorio;
    }

    @Transactional
    public List<VueloInstancia> cargar(LocalDate fechaInicio, LocalDate fechaFin) {
        List<VueloProgramado> programados = vueloProgramadoRepositorio.obtenerTodos();
        Map<String, String> codigoAIdProgramado = programados.stream()
                .collect(Collectors.toMap(
                        VueloProgramado::getCodigo,
                        VueloProgramado::getIdVueloProgramado));
        List<VueloInstancia> instancias = GeneradorVueloInstancias.generar(programados, fechaInicio, fechaFin);
        for (VueloInstancia instancia : instancias) {
            String idVueloProgramado = codigoAIdProgramado.get(instancia.getCodigo());
            vueloInstanciaRepositorio.insertar(instancia, idVueloProgramado);
        }
        return instancias;
    }

    @Transactional
    public List<VueloInstancia> cargarDesdeRecursos() {
        LocalDate hoy = LocalDate.now();
        return cargar(hoy, hoy.plusDays(6));
    }
}
