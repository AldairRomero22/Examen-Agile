package pe.castillo.config;

import java.util.LinkedHashMap;
import java.util.Map;

/** Errores de validación por campo; la respuesta es { mensaje, errores: { campo: mensaje } }. */
public class ValidacionException extends RuntimeException {

    private final Map<String, String> errores;

    public ValidacionException(Map<String, String> errores) {
        super("Revisa los campos marcados");
        this.errores = new LinkedHashMap<>(errores);
    }

    public Map<String, String> getErrores() {
        return errores;
    }

    /** Agrega el error de un campo si hay mensaje; devuelve el mapa para encadenar. */
    public static void agregar(Map<String, String> errores, String campo, String mensaje) {
        if (mensaje != null) {
            errores.put(campo, mensaje);
        }
    }

    public static void lanzarSiHay(Map<String, String> errores) {
        if (!errores.isEmpty()) {
            throw new ValidacionException(errores);
        }
    }
}
