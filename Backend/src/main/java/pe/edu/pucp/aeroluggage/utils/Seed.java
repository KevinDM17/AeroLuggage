package pe.edu.pucp.aeroluggage.utils;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import pe.edu.pucp.aeroluggage.AeroLuggageApplication;
import pe.edu.pucp.aeroluggage.controlador.ControladorCargadorDatos;

@RestController
@RequestMapping("/api/seed")
public class Seed {

    public static void main(String[] args) {

        ConfigurableApplicationContext context = new SpringApplicationBuilder(AeroLuggageApplication.class)
                .web(WebApplicationType.NONE)
                .run(args
                );

        try {
            ControladorCargadorDatos cargador = context.getBean(ControladorCargadorDatos.class);

            cargador.seed(args.length > 0 && args[0].equals("clean"));
            System.out.println("Seed completado con éxito.");

        } catch (Exception e) {
            System.err.println("Error durante el seed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            context.close();
        }
    }
}