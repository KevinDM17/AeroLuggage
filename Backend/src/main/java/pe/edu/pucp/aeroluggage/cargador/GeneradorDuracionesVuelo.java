package pe.edu.pucp.aeroluggage.cargador;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;

public final class GeneradorDuracionesVuelo {

    private static final String DIR_DATOS = "Backend/src/main/resources/datos";
    private static final String NOMBRE_AEROPUERTOS = "Aeropuertos.txt";
    private static final String NOMBRE_PLANES = "planes_vuelo.txt";
    private static final String NOMBRE_SALIDA = "vuelos_duracion.txt";

    private static final int MINUTOS_POR_HORA = 60;
    private static final int MEDIO_DIA_MINUTOS = 12 * MINUTOS_POR_HORA;
    private static final int DIA_COMPLETO_MINUTOS = 24 * MINUTOS_POR_HORA;
    private static final int MINUTOS_POR_DIA = 24 * MINUTOS_POR_HORA;

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    private GeneradorDuracionesVuelo() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void main(final String[] args) {
        final Path dirDatos = resolverDirectorioDatos();
        final Path archivoAeropuertos = dirDatos.resolve(NOMBRE_AEROPUERTOS);
        final Path archivoPlanes = dirDatos.resolve(NOMBRE_PLANES);
        final Path archivoSalida = dirDatos.resolve(NOMBRE_SALIDA);

        System.out.println("Cargando aeropuertos desde: " + archivoAeropuertos);
        final List<Aeropuerto> aeropuertos = CargadorAeropuertos.cargar(archivoAeropuertos);
        final Map<String, Aeropuerto> aeropuertosPorIcao = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getIdAeropuerto, a -> a));
        System.out.println("Aeropuertos cargados: " + aeropuertosPorIcao.size());

        System.out.println("Cargando planes de vuelo desde: " + archivoPlanes);
        final List<VueloProgramado> vuelos = CargadorPlanesVuelo.cargar(archivoPlanes, aeropuertosPorIcao);
        System.out.println("Vuelos cargados: " + vuelos.size());

        final List<String> lineasSalida = new ArrayList<>();
        for (final VueloProgramado vuelo : vuelos) {
            final int duracionSimple = calcularDuracionUtc(vuelo);
            final int duracionContinental = calcularDuracionContinental(vuelo, duracionSimple);

            final String codigoOriginal = reconstruirCodigoOriginal(vuelo);
            final String linea = codigoOriginal
                    + " | " + formatearDuracion(duracionSimple)
                    + " | " + formatearDuracion(duracionContinental);
            lineasSalida.add(linea);
        }

        try {
            Files.write(archivoSalida, lineasSalida, StandardCharsets.UTF_8);
            System.out.println("Archivo generado: " + archivoSalida.toAbsolutePath());
            System.out.println("Lineas escritas: " + lineasSalida.size());
        } catch (final IOException ex) {
            throw new IllegalStateException("Error al escribir el archivo de salida: " + archivoSalida, ex);
        }
    }

    static int calcularDuracionUtc(final VueloProgramado vuelo) {
        final int salidaUtc = aMinutosUtc(vuelo.getHoraSalida(), vuelo.getAeropuertoOrigen().getHusoGMT());
        final int llegadaUtc = aMinutosUtc(vuelo.getHoraLlegada(), vuelo.getAeropuertoDestino().getHusoGMT());
        final int duracion = (llegadaUtc - salidaUtc + MINUTOS_POR_DIA) % MINUTOS_POR_DIA;
        return duracion == 0 ? MINUTOS_POR_DIA : duracion;
    }

    static int calcularDuracionContinental(final VueloProgramado vuelo, final int duracionUtc) {
        final boolean mismoContinente = vuelo.getAeropuertoOrigen().getCiudad().getContinente()
                == vuelo.getAeropuertoDestino().getCiudad().getContinente();
        final int esperado = mismoContinente ? MEDIO_DIA_MINUTOS : DIA_COMPLETO_MINUTOS;
        if (duracionUtc < esperado) {
            return duracionUtc + esperado;
        }
        return duracionUtc;
    }

    static String formatearDuracion(final int minutosTotales) {
        final int horas = minutosTotales / MINUTOS_POR_HORA;
        final int minutos = minutosTotales % MINUTOS_POR_HORA;
        return String.format("%02d:%02d", horas, minutos);
    }

    private static int aMinutosUtc(final LocalTime horaLocal, final int husoGMT) {
        final int minutosLocales = horaLocal.getHour() * MINUTOS_POR_HORA + horaLocal.getMinute();
        return (minutosLocales - husoGMT * MINUTOS_POR_HORA + MINUTOS_POR_DIA) % MINUTOS_POR_DIA;
    }

    private static String reconstruirCodigoOriginal(final VueloProgramado vuelo) {
        final String horaLlegadaStr = vuelo.getHoraLlegada().format(FORMATO_HORA);
        final String capacidadStr = String.format("%04d", vuelo.getCapacidadBase());
        return vuelo.getCodigo() + "-" + horaLlegadaStr + "-" + capacidadStr;
    }

    private static Path resolverDirectorioDatos() {
        final Path desdeRaiz = Path.of(DIR_DATOS);
        if (Files.exists(desdeRaiz)) {
            return desdeRaiz;
        }
        final Path desdeBackend = Path.of("src/main/resources/datos");
        if (Files.exists(desdeBackend)) {
            return desdeBackend;
        }
        return desdeRaiz;
    }
}
