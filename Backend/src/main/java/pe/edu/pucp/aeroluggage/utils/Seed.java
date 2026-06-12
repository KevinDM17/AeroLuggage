package pe.edu.pucp.aeroluggage.utils;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import pe.edu.pucp.aeroluggage.AeroLuggageApplication;
import pe.edu.pucp.aeroluggage.controller.DataLoaderController;

import java.io.IOException;

@RestController
@RequestMapping("/api/seed")
public class Seed {

    private final DataLoaderController dataLoaderController;

    public Seed(final DataLoaderController dataLoaderController) {
        this.dataLoaderController = dataLoaderController;
    }

    @PostMapping
    public void seed(@RequestParam(defaultValue = "false") final boolean clean) throws IOException {
        dataLoaderController.seed(clean);
    }

    public static void main(String[] args) {

        ConfigurableApplicationContext context = new SpringApplicationBuilder(AeroLuggageApplication.class)
                .web(WebApplicationType.NONE)
                .run(args
                );

        try {
            DataLoaderController cargador = context.getBean(DataLoaderController.class);

            cargador.seed(Boolean.parseBoolean(System.getProperty("clean")));
            System.out.println("Seed completado con éxito.");

        } catch (Exception e) {
            System.err.println("Error durante el seed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            context.close();
        }
    }
}