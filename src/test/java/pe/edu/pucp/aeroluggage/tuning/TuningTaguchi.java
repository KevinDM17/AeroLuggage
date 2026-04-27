package pe.edu.pucp.aeroluggage.tuning;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;

class TuningTaguchi {

    @Test
    void ejecutarTuningGA() throws IOException {
        assumeTrue(Files.isDirectory(TuningTaguchiRunner.DOCS),
                "Carpeta Documentos no encontrada, se omite el tuning.");
        assumeTrue(Files.exists(TuningTaguchiRunner.GA_TUNING_FILE),
                "ga_tuning.txt no encontrado, se omite el tuning.");
        TuningTaguchiRunner.ejecutarGA();
    }

    @Test
    void ejecutarTuningACO() throws IOException {
        assumeTrue(Files.isDirectory(TuningTaguchiRunner.DOCS),
                "Carpeta Documentos no encontrada, se omite el tuning.");
        assumeTrue(Files.exists(TuningTaguchiRunner.ACO_TUNING_FILE),
                "aco_tuning.txt no encontrado, se omite el tuning.");
        TuningTaguchiRunner.ejecutarACO();
    }
}
