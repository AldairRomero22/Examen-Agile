package pe.castillo.comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import pe.castillo.lector.LecturaVoucher;

/**
 * HU-07/08: un voucher subido. Cada subida es una fila nueva (nada se borra).
 *
 * "Leído" es lo que extrajo el lector y nunca se cambia; "enviado" es lo que entra a verificación.
 * Un dato enviado solo puede diferir del leído si el lector lo dejó vacío (escrito a mano):
 * la base de datos lo exige con restricciones CHECK (ver V1__tablas.sql).
 *
 * Estados: LEIDO (subido, aún sin enviar) → ENVIADO → APROBADO o RECHAZADO.
 */
@Entity
@Table(name = "comprobante")
public class Comprobante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "obligacion_id", nullable = false, updatable = false)
    private Long obligacionId;

    @Column(nullable = false, updatable = false)
    private byte[] archivo;

    @Column(name = "nombre_archivo", nullable = false, updatable = false)
    private String nombreArchivo;

    @Column(name = "tipo_mime", nullable = false, updatable = false)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false, updatable = false)
    private int tamanoBytes;

    @Column(name = "hash_sha256", nullable = false, updatable = false)
    private String hashSha256;

    @Column(nullable = false, updatable = false)
    private String lector;

    // Valor leído por el lector (solo lectura)
    @Column(name = "fecha_leida", updatable = false)
    private LocalDate fechaLeida;

    @Column(name = "nro_operacion_leido", updatable = false)
    private String nroOperacionLeido;

    @Column(name = "monto_leido", precision = 12, scale = 2, updatable = false)
    private BigDecimal montoLeido;

    // Valor enviado a verificación
    @Column(name = "fecha_enviada")
    private LocalDate fechaEnviada;

    @Column(name = "nro_operacion_enviado")
    private String nroOperacionEnviado;

    @Column(name = "monto_enviado", precision = 12, scale = 2)
    private BigDecimal montoEnviado;

    // "escrito a mano": solo si el lector dejó ese campo vacío
    @Column(name = "fecha_manual", nullable = false)
    private boolean fechaManual;

    @Column(name = "nro_operacion_manual", nullable = false)
    private boolean nroOperacionManual;

    @Column(name = "monto_manual", nullable = false)
    private boolean montoManual;

    @Column(nullable = false)
    private String estado = "LEIDO";

    @Column(name = "subido_por", nullable = false, updatable = false)
    private Long subidoPor;

    @Column(name = "subido_en", nullable = false, updatable = false)
    private OffsetDateTime subidoEn;

    protected Comprobante() { }

    public Comprobante(Long obligacionId, byte[] archivo, String nombreArchivo, String tipoMime, String hashSha256,
                       String lector, LecturaVoucher lectura, Long subidoPor, OffsetDateTime subidoEn) {
        this.obligacionId = obligacionId;
        this.archivo = archivo;
        this.nombreArchivo = nombreArchivo;
        this.tipoMime = tipoMime;
        this.tamanoBytes = archivo.length;
        this.hashSha256 = hashSha256;
        this.lector = lector;
        this.fechaLeida = lectura.fecha();
        this.nroOperacionLeido = lectura.nroOperacion();
        this.montoLeido = lectura.monto();
        this.subidoPor = subidoPor;
        this.subidoEn = subidoEn;
    }

    public Long getId() { return id; }
    public Long getObligacionId() { return obligacionId; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getTipoMime() { return tipoMime; }
    public int getTamanoBytes() { return tamanoBytes; }
    public String getHashSha256() { return hashSha256; }
    public String getLector() { return lector; }
    public LocalDate getFechaLeida() { return fechaLeida; }
    public String getNroOperacionLeido() { return nroOperacionLeido; }
    public BigDecimal getMontoLeido() { return montoLeido; }
    public LocalDate getFechaEnviada() { return fechaEnviada; }
    public String getNroOperacionEnviado() { return nroOperacionEnviado; }
    public BigDecimal getMontoEnviado() { return montoEnviado; }
    public boolean isFechaManual() { return fechaManual; }
    public boolean isNroOperacionManual() { return nroOperacionManual; }
    public boolean isMontoManual() { return montoManual; }
    public String getEstado() { return estado; }
    public Long getSubidoPor() { return subidoPor; }
    public OffsetDateTime getSubidoEn() { return subidoEn; }

    /** HU-07 criterio 6: se envía a verificación con los tres datos (leídos o escritos a mano). */
    public void enviar(LocalDate fecha, String nroOperacion, BigDecimal monto,
                       boolean fechaManual, boolean nroOperacionManual, boolean montoManual) {
        this.fechaEnviada = fecha;
        this.nroOperacionEnviado = nroOperacion;
        this.montoEnviado = monto;
        this.fechaManual = fechaManual;
        this.nroOperacionManual = nroOperacionManual;
        this.montoManual = montoManual;
        this.estado = "ENVIADO";
    }

    public void aprobar() { this.estado = "APROBADO"; }

    public void rechazar() { this.estado = "RECHAZADO"; }

    /** HU-08 criterio 2: "Verificado manualmente" si el asistente escribió algún dato a mano. */
    public boolean tieneDatosManuales() {
        return fechaManual || nroOperacionManual || montoManual;
    }
}
