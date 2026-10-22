package pe.castillo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * La empresa dueña del sistema. Solo se registran deudas de esta empresa: un recibo o una factura a nombre de otra
 * persona o empresa se rechaza. El nombre es fijo (configuración castillo.empresa.nombre o variable EMPRESA_NOMBRE)
 * y nadie lo edita desde las pantallas.
 */
@Component
public class Empresa {

    private final String nombre;

    public Empresa(@Value("${castillo.empresa.nombre:Castillo SAC}") String nombre) {
        this.nombre = nombre == null || nombre.isBlank() ? "Castillo SAC" : nombre.trim();
    }

    public String nombre() {
        return nombre;
    }

    /**
     * ¿El titular leído es la empresa? Se compara sin mayúsculas, tildes, signos ni terminación legal:
     * "CASTILLO S.A.C." y "Castillo SAC" son el mismo nombre; "Juan Castillo" no.
     */
    public boolean esMia(String titular) {
        return titular != null && !titular.isBlank() && Reglas.empresa(titular).equals(Reglas.empresa(nombre));
    }
}
