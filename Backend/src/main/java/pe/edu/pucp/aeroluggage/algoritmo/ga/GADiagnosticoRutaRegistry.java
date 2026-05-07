package pe.edu.pucp.aeroluggage.algoritmo.ga;

import java.util.HashMap;
import java.util.Map;

public final class GADiagnosticoRutaRegistry {

    private static final ThreadLocal<Map<String, DiagnosticoRuta>> REGISTRO =
            ThreadLocal.withInitial(HashMap::new);

    private GADiagnosticoRutaRegistry() {
        throw new UnsupportedOperationException("This class should never be instantiated");
    }

    public static void limpiar() {
        REGISTRO.get().clear();
    }

    public static void registrar(final String idMaleta,
                                 final String grupoConflicto,
                                 final String fase,
                                 final String motivo) {
        if (idMaleta == null || idMaleta.isBlank()) {
            return;
        }
        REGISTRO.get().put(idMaleta, new DiagnosticoRuta(idMaleta, grupoConflicto, fase, motivo));
    }

    public static DiagnosticoRuta obtener(final String idMaleta) {
        if (idMaleta == null || idMaleta.isBlank()) {
            return null;
        }
        return REGISTRO.get().get(idMaleta);
    }

    public static Map<String, DiagnosticoRuta> snapshot() {
        return new HashMap<>(REGISTRO.get());
    }

    public record DiagnosticoRuta(String idMaleta, String grupoConflicto, String fase, String motivo) {
    }
}
