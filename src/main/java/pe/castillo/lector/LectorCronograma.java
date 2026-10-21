package pe.castillo.lector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Lee un cronograma de préstamo: el Excel (.xlsx) se lee directo, sin IA; el PDF o la foto, con IA.
 * Sin clave de IA solo se puede leer el Excel (o escribir a mano).
 */
@Component
public class LectorCronograma {

    /** null si no hay clave de IA. */
    private final ClienteIaCronograma ia;

    public LectorCronograma(@Value("${ANTHROPIC_API_KEY:}") String claveIa,
                            @Value("${castillo.ia.modelo:claude-haiku-5-5}") String modeloIa,
                            @Value("${castillo.ia.url:https://api.anthropic.com}") String urlIa,
                            @Value("${castillo.ia.limite-segundos:40}") long limiteIa) {
        this.ia = claveIa == null || claveIa.isBlank() ? null : new ClienteIaCronograma(claveIa.trim(), modeloIa, urlIa, limiteIa);
    }

    public boolean leeImagenes() {
        return ia != null;
    }

    public LecturaCronograma leerExcel(byte[] archivo) {
        return LectorExcelCronograma.leer(archivo);
    }

    /** @param tipoMime image/jpeg, image/png o application/pdf */
    public LecturaCronograma leerConIa(byte[] archivo, String tipoMime) {
        if (ia == null) {
            throw new LectorNoDisponibleException("No hay clave de IA");
        }
        return ia.leer(archivo, tipoMime);
    }
}
