package pe.edu.pucp.aeroluggage.algoritmos.aco;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import pe.edu.pucp.aeroluggage.algoritmos.Solucion;
import pe.edu.pucp.aeroluggage.dominio.entidades.Aeropuerto;
import pe.edu.pucp.aeroluggage.dominio.entidades.Maleta;
import pe.edu.pucp.aeroluggage.dominio.entidades.Pedido;
import pe.edu.pucp.aeroluggage.dominio.entidades.Ruta;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloInstancia;
import pe.edu.pucp.aeroluggage.dominio.entidades.VueloProgramado;
import pe.edu.pucp.aeroluggage.dominio.enums.EstadoVuelo;

final class ACOConstructorSoluciones {
    private static final String ESTADO_PLANIFICADA = "PLANIFICADA";
    private static final String ESTADO_NO_FACTIBLE = "NO_FACTIBLE";
    private static final String ESTADO_REPLANIFICADA = "REPLANIFICADA";
    private static final int UNIDAD_MALETA = 1;

    private final ACOConfiguracion configuracion;
    private final Random random;

    ACOConstructorSoluciones(final ACOConfiguracion configuracion) {
        this.configuracion = configuracion;
        this.random = new Random(configuracion.getSemilla());
    }

    ArrayList<Solucion> generarPoblacionInicial(
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas
    ) {
        final ArrayList<Solucion> poblacionInicial = new ArrayList<>();
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>(subproblema.getCapacidadRestanteVueloBase());
        final Map<String, Integer> capacidadRestanteAlmacen = new HashMap<>(
                subproblema.getCapacidadRestanteAlmacenBase()
        );
        final ArrayList<Ruta> rutas = new ArrayList<>();

        for (final Maleta maleta : subproblema.getMaletasPendientes()) {
            rutas.add(construirRutaParaMaleta(
                    maleta,
                    subproblema,
                    feromonas,
                    capacidadRestanteVuelo,
                    capacidadRestanteAlmacen,
                    true
            ));
        }

        poblacionInicial.add(new Solucion(rutas));
        return poblacionInicial;
    }

    Solucion construirSolucionHormiga(
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas
    ) {
        final Map<String, Integer> capacidadRestanteVuelo = new HashMap<>(subproblema.getCapacidadRestanteVueloBase());
        final Map<String, Integer> capacidadRestanteAlmacen = new HashMap<>(
                subproblema.getCapacidadRestanteAlmacenBase()
        );
        final ArrayList<Ruta> rutas = new ArrayList<>();

        for (final Maleta maleta : subproblema.getMaletasPendientes()) {
            rutas.add(construirRutaParaMaleta(
                    maleta,
                    subproblema,
                    feromonas,
                    capacidadRestanteVuelo,
                    capacidadRestanteAlmacen,
                    false
            ));
        }

        return new Solucion(rutas);
    }

    Solucion repararInconsistencias(final Solucion solucion, final SubproblemaACO subproblema) {
        final ArrayList<Ruta> rutasReparadas = new ArrayList<>();
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new Solucion(rutasReparadas);
        }

        for (final Ruta rutaOriginal : solucion.getSubrutas()) {
            final Ruta ruta = clonarRuta(rutaOriginal);
            final ArrayList<VueloInstancia> vuelos = new ArrayList<>(ruta.getSubrutas());
            vuelos.sort(Comparator.comparing(VueloInstancia::getFechaSalida));
            ruta.setSubrutas(vuelos);

            if (ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || vuelos.isEmpty()) {
                ruta.setEstado(ESTADO_NO_FACTIBLE);
                ruta.calcularPlazo();
                rutasReparadas.add(ruta);
                continue;
            }

            boolean inconsistente = false;
            for (int i = 1; i < vuelos.size(); i++) {
                final VueloInstancia anterior = vuelos.get(i - 1);
                final VueloInstancia actual = vuelos.get(i);
                final boolean cadenaInvalida = !esMismoAeropuerto(
                        anterior.getAeropuertoDestino(),
                        actual.getAeropuertoOrigen()
                );
                final boolean secuenciaTemporalInvalida = actual.getFechaSalida().isBefore(anterior.getFechaLlegada());
                if (cadenaInvalida || secuenciaTemporalInvalida) {
                    inconsistente = true;
                    break;
                }
            }

            final Maleta maleta = subproblema.obtenerMaleta(ruta.getIdMaleta());
            final Pedido pedido = maleta == null ? null : maleta.getPedido();
            final Aeropuerto destinoEsperado = pedido == null ? null : pedido.getAeropuertoDestino();
            final VueloInstancia ultimoVuelo = vuelos.get(vuelos.size() - 1);
            if (destinoEsperado != null && !esMismoAeropuerto(ultimoVuelo.getAeropuertoDestino(), destinoEsperado)) {
                inconsistente = true;
            }

            if (inconsistente) {
                ruta.setEstado(ESTADO_NO_FACTIBLE);
            }
            ruta.calcularPlazo();
            rutasReparadas.add(ruta);
        }

        return new Solucion(rutasReparadas);
    }

    Solucion mejoraLocal(final Solucion solucion, final SubproblemaACO subproblema) {
        final ArrayList<Ruta> rutasMejoradas = new ArrayList<>();
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new Solucion(rutasMejoradas);
        }

        for (final Ruta rutaOriginal : solucion.getSubrutas()) {
            final Ruta ruta = clonarRuta(rutaOriginal);
            if (ESTADO_NO_FACTIBLE.equals(ruta.getEstado()) || ruta.getSubrutas().size() <= 1) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final Maleta maleta = subproblema.obtenerMaleta(ruta.getIdMaleta());
            final VueloProgramado vueloDirecto = buscarMejorVueloDirecto(maleta, subproblema);
            if (vueloDirecto == null) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final LocalDateTime llegadaActual = ruta.getSubrutas().get(ruta.getSubrutas().size() - 1).getFechaLlegada();
            if (llegadaActual != null && !vueloDirecto.getHoraLlegada().isBefore(llegadaActual)) {
                rutasMejoradas.add(ruta);
                continue;
            }

            final ArrayList<VueloInstancia> nuevaSubruta = new ArrayList<>();
            nuevaSubruta.add(convertirVueloProgramado(
                    vueloDirecto,
                    Math.max(0, vueloDirecto.getCapacidadMaxima() - UNIDAD_MALETA)
            ));
            ruta.setSubrutas(nuevaSubruta);
            ruta.calcularPlazo();
            ruta.setEstado(ESTADO_REPLANIFICADA);
            rutasMejoradas.add(ruta);
        }

        return new Solucion(rutasMejoradas);
    }

    Solucion clonarSolucion(final Solucion solucion) {
        final ArrayList<Ruta> rutas = new ArrayList<>();
        if (solucion == null || solucion.getSubrutas().isEmpty()) {
            return new Solucion(rutas);
        }
        for (final Ruta ruta : solucion.getSubrutas()) {
            rutas.add(clonarRuta(ruta));
        }
        return new Solucion(rutas);
    }

    Ruta clonarRuta(final Ruta ruta) {
        if (ruta == null) {
            return null;
        }
        final ArrayList<VueloInstancia> subrutas = new ArrayList<>();
        if (ruta.getSubrutas() != null) {
            for (final VueloInstancia subruta : ruta.getSubrutas()) {
                subrutas.add(clonarVueloInstancia(subruta));
            }
        }
        return new Ruta(
                ruta.getIdRuta(),
                ruta.getIdMaleta(),
                ruta.getPlazoMaximoDias(),
                ruta.getDuracion(),
                subrutas,
                ruta.getEstado()
        );
    }

    LocalDateTime obtenerPrimeraSalida(final Ruta ruta) {
        if (ruta == null || ruta.getSubrutas() == null || ruta.getSubrutas().isEmpty()) {
            return null;
        }
        return ruta.getSubrutas().get(0).getFechaSalida();
    }

    boolean esRutaFactible(final Ruta ruta) {
        return ruta != null && !ESTADO_NO_FACTIBLE.equals(ruta.getEstado());
    }

    void aplicarActualizacionLocalFeromona(
            final FeromonasACO feromonas,
            final Maleta maleta,
            final VueloProgramado vuelo
    ) {
        if (feromonas == null) {
            return;
        }
        feromonas.actualizarLocal(maleta, vuelo);
    }

    private Ruta construirRutaParaMaleta(
            final Maleta maleta,
            final SubproblemaACO subproblema,
            final FeromonasACO feromonas,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final boolean modoCodicioso
    ) {
        if (maleta == null || maleta.getIdMaleta() == null || maleta.getPedido() == null) {
            return crearRutaNoFactible(maleta, new ArrayList<>(), subproblema);
        }

        final Pedido pedido = maleta.getPedido();
        final Aeropuerto origen = pedido.getAeropuertoOrigen();
        final Aeropuerto destino = pedido.getAeropuertoDestino();
        if (origen == null || destino == null) {
            return crearRutaNoFactible(maleta, new ArrayList<>(), subproblema);
        }

        Aeropuerto actual = origen;
        LocalDateTime tiempoActual = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        final ArrayList<VueloInstancia> plan = new ArrayList<>();
        final Set<String> visitados = new HashSet<>();
        if (actual.getIdAeropuerto() != null) {
            visitados.add(actual.getIdAeropuerto());
        }

        while (!esMismoAeropuerto(actual, destino)) {
            final ArrayList<VueloProgramado> candidatos = vuelosFactibles(
                    actual,
                    destino,
                    tiempoActual,
                    subproblema.getVuelosDisponibles(),
                    capacidadRestanteVuelo,
                    capacidadRestanteAlmacen,
                    plazo,
                    visitados
            );
            if (candidatos.isEmpty()) {
                return crearRutaNoFactible(maleta, plan, subproblema);
            }

            final VueloProgramado siguienteVuelo = modoCodicioso
                    ? seleccionarCodiciosamente(candidatos, destino, tiempoActual, plazo)
                    : seleccionarProbabilisticamente(candidatos, feromonas, maleta, destino, tiempoActual, plazo);
            if (siguienteVuelo == null) {
                return crearRutaNoFactible(maleta, plan, subproblema);
            }

            final int nuevaCapacidad = actualizarEstadoTemporal(
                    siguienteVuelo,
                    capacidadRestanteVuelo,
                    capacidadRestanteAlmacen,
                    destino
            );
            plan.add(convertirVueloProgramado(siguienteVuelo, nuevaCapacidad));
            aplicarActualizacionLocalFeromona(feromonas, maleta, siguienteVuelo);
            actual = siguienteVuelo.getAeropuertoDestino();
            tiempoActual = siguienteVuelo.getHoraLlegada();
            if (actual != null && actual.getIdAeropuerto() != null) {
                visitados.add(actual.getIdAeropuerto());
            }
        }

        if (!esMismoAeropuerto(actual, destino)) {
            return crearRutaNoFactible(maleta, plan, subproblema);
        }
        return crearRutaFactible(maleta, plan, subproblema, ESTADO_PLANIFICADA);
    }

    private ArrayList<VueloProgramado> vuelosFactibles(
            final Aeropuerto actual,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final ArrayList<VueloProgramado> vuelos,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final LocalDateTime plazo,
            final Set<String> visitados
    ) {
        final ArrayList<VueloProgramado> candidatos = new ArrayList<>();
        if (vuelos == null || vuelos.isEmpty() || actual == null || actual.getIdAeropuerto() == null) {
            return candidatos;
        }

        for (final VueloProgramado vuelo : vuelos) {
            if (!actual.getIdAeropuerto().equals(obtenerIdAeropuerto(vuelo.getAeropuertoOrigen()))) {
                continue;
            }
            if (vuelo.getHoraSalida().isBefore(tiempoActual)) {
                continue;
            }
            if (plazo != null && vuelo.getHoraLlegada().isAfter(plazo)) {
                continue;
            }

            final int capacidadVuelo = capacidadRestanteVuelo.getOrDefault(vuelo.getIdVueloProgramado(), 0);
            if (capacidadVuelo < UNIDAD_MALETA) {
                continue;
            }

            final String idDestino = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            if (idDestino == null || visitados.contains(idDestino)) {
                continue;
            }

            final boolean esDestinoFinal = esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
            if (!esDestinoFinal) {
                final int capacidadAlmacen = capacidadRestanteAlmacen.getOrDefault(idDestino, 0);
                if (capacidadAlmacen < UNIDAD_MALETA) {
                    continue;
                }
            }
            candidatos.add(vuelo);
        }
        return candidatos;
    }

    private VueloProgramado seleccionarCodiciosamente(
            final ArrayList<VueloProgramado> candidatos,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo
    ) {
        VueloProgramado mejorVuelo = null;
        double mejorHeuristica = Double.NEGATIVE_INFINITY;

        for (final VueloProgramado candidato : candidatos) {
            final double heuristica = calcularHeuristica(candidato, destinoFinal, tiempoActual, plazo);
            if (heuristica <= mejorHeuristica) {
                continue;
            }
            mejorHeuristica = heuristica;
            mejorVuelo = candidato;
        }
        return mejorVuelo;
    }

    private VueloProgramado seleccionarProbabilisticamente(
            final ArrayList<VueloProgramado> candidatos,
            final FeromonasACO feromonas,
            final Maleta maleta,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo
    ) {
        double sumaPesos = 0D;
        final Map<String, Double> pesos = new HashMap<>();

        for (final VueloProgramado candidato : candidatos) {
            final double feromona = obtenerFeromona(feromonas, maleta, candidato);
            final double heuristica = calcularHeuristica(candidato, destinoFinal, tiempoActual, plazo);
            final double peso = Math.pow(feromona, configuracion.getAlpha())
                    * Math.pow(heuristica, configuracion.getBeta());
            pesos.put(candidato.getIdVueloProgramado(), peso);
            sumaPesos += peso;
        }

        if (sumaPesos <= 0D) {
            return seleccionarCodiciosamente(candidatos, destinoFinal, tiempoActual, plazo);
        }

        double umbral = random.nextDouble() * sumaPesos;
        for (final VueloProgramado candidato : candidatos) {
            umbral -= pesos.getOrDefault(candidato.getIdVueloProgramado(), 0D);
            if (umbral > 0D) {
                continue;
            }
            return candidato;
        }
        return candidatos.get(candidatos.size() - 1);
    }

    private double calcularHeuristica(
            final VueloProgramado vuelo,
            final Aeropuerto destinoFinal,
            final LocalDateTime tiempoActual,
            final LocalDateTime plazo
    ) {
        final long esperaMinutos = Math.max(0L, Duration.between(tiempoActual, vuelo.getHoraSalida()).toMinutes());
        final long duracionMinutos = Math.max(
                1L,
                Duration.between(vuelo.getHoraSalida(), vuelo.getHoraLlegada()).toMinutes()
        );
        final boolean vueloDirecto = esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal);
        final double bonificacionDestino = vueloDirecto ? 3D : 1D;
        double holgura = 1D;

        if (plazo != null && !vuelo.getHoraLlegada().isAfter(plazo)) {
            final long minutosHolgura = Math.max(0L, Duration.between(vuelo.getHoraLlegada(), plazo).toMinutes());
            holgura += Math.min(1440D, minutosHolgura) / 1440D;
        }

        return bonificacionDestino * holgura / (1D + esperaMinutos + duracionMinutos);
    }

    private int actualizarEstadoTemporal(
            final VueloProgramado vuelo,
            final Map<String, Integer> capacidadRestanteVuelo,
            final Map<String, Integer> capacidadRestanteAlmacen,
            final Aeropuerto destinoFinal
    ) {
        final String idVuelo = vuelo.getIdVueloProgramado();
        final int capacidadActualVuelo = capacidadRestanteVuelo.getOrDefault(idVuelo, 0);
        final int nuevaCapacidadVuelo = Math.max(0, capacidadActualVuelo - UNIDAD_MALETA);
        capacidadRestanteVuelo.put(idVuelo, nuevaCapacidadVuelo);

        if (!esMismoAeropuerto(vuelo.getAeropuertoDestino(), destinoFinal)) {
            final String idAeropuerto = obtenerIdAeropuerto(vuelo.getAeropuertoDestino());
            final int capacidadActualAlmacen = capacidadRestanteAlmacen.getOrDefault(idAeropuerto, 0);
            final int nuevaCapacidadAlmacen = Math.max(0, capacidadActualAlmacen - UNIDAD_MALETA);
            capacidadRestanteAlmacen.put(idAeropuerto, nuevaCapacidadAlmacen);
        }

        return nuevaCapacidadVuelo;
    }

    private VueloProgramado buscarMejorVueloDirecto(final Maleta maleta, final SubproblemaACO subproblema) {
        if (maleta == null || maleta.getPedido() == null) {
            return null;
        }

        final Pedido pedido = maleta.getPedido();
        final Aeropuerto origen = pedido.getAeropuertoOrigen();
        final Aeropuerto destino = pedido.getAeropuertoDestino();
        if (origen == null || destino == null) {
            return null;
        }

        final LocalDateTime tiempoDisponible = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        VueloProgramado mejorVuelo = null;

        for (final VueloProgramado vuelo : subproblema.getVuelosDisponibles()) {
            final boolean esDirecto = esMismoAeropuerto(vuelo.getAeropuertoOrigen(), origen)
                    && esMismoAeropuerto(vuelo.getAeropuertoDestino(), destino);
            if (!esDirecto) {
                continue;
            }
            if (vuelo.getHoraSalida().isBefore(tiempoDisponible)) {
                continue;
            }
            if (plazo != null && vuelo.getHoraLlegada().isAfter(plazo)) {
                continue;
            }
            if (mejorVuelo != null && !vuelo.getHoraLlegada().isBefore(mejorVuelo.getHoraLlegada())) {
                continue;
            }
            mejorVuelo = vuelo;
        }
        return mejorVuelo;
    }

    private Ruta crearRutaFactible(
            final Maleta maleta,
            final ArrayList<VueloInstancia> plan,
            final SubproblemaACO subproblema,
            final String estado
    ) {
        final Ruta ruta = new Ruta(
                "RUTA-" + maleta.getIdMaleta(),
                maleta.getIdMaleta(),
                calcularPlazoMaximoDias(maleta, subproblema),
                0D,
                plan,
                estado
        );
        ruta.calcularPlazo();
        return ruta;
    }

    private Ruta crearRutaNoFactible(
            final Maleta maleta,
            final ArrayList<VueloInstancia> plan,
            final SubproblemaACO subproblema
    ) {
        final String idMaleta = maleta == null ? "SIN_MALETA" : maleta.getIdMaleta();
        final double plazoMaximo = maleta == null ? Double.MAX_VALUE : calcularPlazoMaximoDias(maleta, subproblema);
        final Ruta ruta = new Ruta("RUTA-" + idMaleta, idMaleta, plazoMaximo, 0D, plan, ESTADO_NO_FACTIBLE);
        ruta.calcularPlazo();
        return ruta;
    }

    private double calcularPlazoMaximoDias(final Maleta maleta, final SubproblemaACO subproblema) {
        final LocalDateTime plazo = subproblema.getPlazoPorMaleta().get(maleta.getIdMaleta());
        final LocalDateTime tiempoDisponible = obtenerTiempoDisponible(maleta, subproblema.getInicioIntervalo());
        if (plazo == null || tiempoDisponible == null || plazo.isBefore(tiempoDisponible)) {
            return Double.MAX_VALUE;
        }
        return Duration.between(tiempoDisponible, plazo).toMinutes() / (24D * 60D);
    }

    private VueloInstancia convertirVueloProgramado(final VueloProgramado vuelo, final int capacidadDisponible) {
        return new VueloInstancia(
                vuelo.getIdVueloProgramado(),
                vuelo.getCodigo(),
                vuelo.getHoraSalida(),
                vuelo.getHoraLlegada(),
                vuelo.getCapacidadMaxima(),
                capacidadDisponible,
                vuelo.getAeropuertoOrigen(),
                vuelo.getAeropuertoDestino(),
                EstadoVuelo.PROGRAMADO
        );
    }

    private LocalDateTime obtenerTiempoDisponible(final Maleta maleta, final LocalDateTime inicioIntervalo) {
        final LocalDateTime fechaMaleta = maleta == null ? null : maleta.getFechaRegistro();
        final Pedido pedido = maleta == null ? null : maleta.getPedido();
        final LocalDateTime fechaPedido = pedido == null ? null : pedido.getFechaRegistro();
        final LocalDateTime base = maximoTiempo(fechaMaleta, fechaPedido);
        return maximoTiempo(base, inicioIntervalo);
    }

    private double obtenerFeromona(final FeromonasACO feromonas, final Maleta maleta, final VueloProgramado vuelo) {
        if (feromonas == null) {
            return configuracion.getTau0();
        }
        return feromonas.obtener(maleta, vuelo);
    }

    private double limitarFeromona(final double valor) {
        final double valorMinimo = Math.max(0D, configuracion.getTauMin());
        final double valorMaximo = Math.max(valorMinimo, configuracion.getTauMax());
        return Math.max(valorMinimo, Math.min(valor, valorMaximo));
    }

    private VueloInstancia clonarVueloInstancia(final VueloInstancia vueloInstancia) {
        if (vueloInstancia == null) {
            return null;
        }
        return new VueloInstancia(
                vueloInstancia.getIdVueloInstancia(),
                vueloInstancia.getCodigo(),
                vueloInstancia.getFechaSalida(),
                vueloInstancia.getFechaLlegada(),
                vueloInstancia.getCapacidadMaxima(),
                vueloInstancia.getCapacidadDisponible(),
                vueloInstancia.getAeropuertoOrigen(),
                vueloInstancia.getAeropuertoDestino(),
                vueloInstancia.getEstado()
        );
    }

    private String obtenerIdAeropuerto(final Aeropuerto aeropuerto) {
        return aeropuerto == null ? null : aeropuerto.getIdAeropuerto();
    }

    private boolean esMismoAeropuerto(final Aeropuerto primero, final Aeropuerto segundo) {
        final String idPrimero = obtenerIdAeropuerto(primero);
        final String idSegundo = obtenerIdAeropuerto(segundo);
        if (idPrimero == null || idSegundo == null) {
            return false;
        }
        return idPrimero.equals(idSegundo);
    }

    private LocalDateTime maximoTiempo(final LocalDateTime primero, final LocalDateTime segundo) {
        if (primero == null) {
            return segundo;
        }
        if (segundo == null) {
            return primero;
        }
        return primero.isAfter(segundo) ? primero : segundo;
    }
}
