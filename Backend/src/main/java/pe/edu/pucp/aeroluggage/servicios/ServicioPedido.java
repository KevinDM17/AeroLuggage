package pe.edu.pucp.aeroluggage.servicios;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.pucp.aeroluggage.cargador.CargadorEnvios;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.repositorio.AeropuertoRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.MaletaRepositorio;
import pe.edu.pucp.aeroluggage.repositorio.PedidoRepositorio;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

@Service
public class ServicioPedido {

    private final PedidoRepositorio pedidoRepositorio;
    private final MaletaRepositorio maletaRepositorio;
    private final AeropuertoRepositorio aeropuertoRepositorio;

    public ServicioPedido(PedidoRepositorio pedidoRepositorio,
                          MaletaRepositorio maletaRepositorio,
                          AeropuertoRepositorio aeropuertoRepositorio) {
        this.pedidoRepositorio = pedidoRepositorio;
        this.maletaRepositorio = maletaRepositorio;
        this.aeropuertoRepositorio = aeropuertoRepositorio;
    }

    @Transactional
    public CargadorEnvios.ResultadoCarga cargar(MultipartFile archivo, String icaoOrigen) throws IOException {
        Map<String, Aeropuerto> mapa = aeropuertoRepositorio.obtenerTodosComoMapa();
        Aeropuerto origen = mapa.get(icaoOrigen);
        if (origen == null) {
            throw new IllegalArgumentException("Aeropuerto de origen no encontrado: " + icaoOrigen);
        }
        Path temp = Files.createTempFile("envios-", ".txt");
        try (InputStream is = archivo.getInputStream()) {
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            return persistir(CargadorEnvios.cargarArchivo(temp, origen, mapa));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Transactional
    public CargadorEnvios.ResultadoCarga cargarDesdeRecursos() throws IOException {
        Map<String, Aeropuerto> mapa = aeropuertoRepositorio.obtenerTodosComoMapa();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] recursos = resolver.getResources("classpath:datos/Envios/_envios_*.txt");

        Path tempDir = Files.createTempDirectory("envios-");
        try {
            for (Resource recurso : recursos) {
                Path destino = tempDir.resolve(recurso.getFilename());
                try (InputStream is = recurso.getInputStream()) {
                    Files.copy(is, destino, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return persistir(CargadorEnvios.cargarDesdeDirectorio(tempDir, mapa));
        } finally {
            borrarDirectorio(tempDir);
        }
    }

    private CargadorEnvios.ResultadoCarga persistir(CargadorEnvios.ResultadoCarga resultado) {
        for (Pedido pedido : resultado.getPedidos()) {
            pedidoRepositorio.insertar(pedido);
        }
        for (Maleta maleta : resultado.getMaletas()) {
            maletaRepositorio.insertar(maleta);
        }
        return resultado;
    }

    private void borrarDirectorio(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        }
    }
}
