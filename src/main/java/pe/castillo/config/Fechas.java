package pe.castillo.config;

import java.time.LocalDate;
import java.time.ZoneId;

/** "Hoy" siempre con la hora de Perú, aunque el servidor (Render) esté en UTC. */
public final class Fechas {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private Fechas() { }

    public static LocalDate hoy() {
        return LocalDate.now(LIMA);
    }
}
