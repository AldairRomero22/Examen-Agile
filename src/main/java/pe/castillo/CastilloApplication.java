package pe.castillo;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CastilloApplication {

    public static void main(String[] args) {
        // Las fechas (vencimientos, "fecha no posterior a hoy") se calculan con la hora de Perú,
        // aunque Render corra en UTC.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(CastilloApplication.class, args);
    }
}
