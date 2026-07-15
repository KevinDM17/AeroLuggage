package pe.edu.pucp.aeroluggage.simulacion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OperationsAutoStart {

    private final OperacionesDiaADiaService service;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            service.autoStart();
        } catch (IllegalStateException e) {
        }
    }
}
