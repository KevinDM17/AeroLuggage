package pe.edu.pucp.aeroluggage.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SistemaConfiguracion {

    private final NegocioConfig negocioConfig;

    public int getUmbralConfirmacionMinutos() {
        return negocioConfig.getUmbralConfirmacionMinutos();
    }
}
