package pe.castillo.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Los datos de un comprobante SIN el archivo (que puede pesar hasta 5 MB).
 * Spring Data solo trae de la base las columnas que declara esta interfaz.
 */
public interface ComprobanteInfo {

    Long getId();
    Long getObligacionId();
    String getNombreArchivo();
    String getTipoMime();
    Integer getTamanoBytes();
    String getHashSha256();
    String getLector();
    LocalDate getFechaLeida();
    String getNroOperacionLeido();
    BigDecimal getMontoLeido();
    LocalDate getFechaEnviada();
    String getNroOperacionEnviado();
    BigDecimal getMontoEnviado();
    Boolean getFechaManual();
    Boolean getNroOperacionManual();
    Boolean getMontoManual();
    String getEstado();
    Long getSubidoPor();
    OffsetDateTime getSubidoEn();
}
