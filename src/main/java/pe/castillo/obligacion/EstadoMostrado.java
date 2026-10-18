package pe.castillo.obligacion;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Estado que se muestra de una obligación. Se calcula al consultar (no hay tareas programadas):
 * - Pagado, En verificación y Observado vienen guardados (HU-07/08).
 * - En espera lo pone el gerente (HU-10) y no cambia por la fecha.
 * - Por pagar / Por vencer / Vencido salen de la fecha de vencimiento (HU-05):
 *   "Por vencer" cuando faltan 3 días o menos; "Vencido" desde el día siguiente al vencimiento.
 */
public enum EstadoMostrado {

    PAGADO("Pagado"),
    EN_ESPERA("En espera"),
    EN_VERIFICACION("En verificación"),
    OBSERVADO("Observado"),
    VENCIDO("Vencido"),
    POR_VENCER("Por vencer"),
    POR_PAGAR("Por pagar");

    private final String etiqueta;

    EstadoMostrado(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public static EstadoMostrado de(Obligacion o, LocalDate hoy) {
        if ("PAGADO".equals(o.getEstadoPago())) {
            return PAGADO;
        }
        if (o.isEnEspera()) {
            return EN_ESPERA;
        }
        if ("EN_VERIFICACION".equals(o.getEstadoPago())) {
            return EN_VERIFICACION;
        }
        if ("OBSERVADO".equals(o.getEstadoPago())) {
            return OBSERVADO;
        }
        long dias = ChronoUnit.DAYS.between(hoy, o.getFechaVencimiento());
        if (dias < 0) {
            return VENCIDO;
        }
        if (dias <= 3) {
            return POR_VENCER;
        }
        return POR_PAGAR;
    }
}
