package pe.castillo.obligacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import pe.castillo.categoria.Categoria;

@Entity
@Table(name = "obligacion")
public class Obligacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String concepto;

    @ManyToOne(optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /** A quién se paga (texto): lo escribe el asistente o se lee del documento. */
    @Column(name = "beneficiario_nombre", nullable = false, length = 150)
    private String beneficiarioNombre;

    /** RUC (11 dígitos) o DNI (8) de a quién se paga. Null solo en pagos registrados antes de pedirlo. */
    @Column(name = "beneficiario_documento", length = 11)
    private String beneficiarioDocumento;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    private String observacion;

    /** PENDIENTE, EN_VERIFICACION, PAGADO u OBSERVADO. Por pagar/Por vencer/Vencido se calculan. */
    @Column(name = "estado_pago", nullable = false)
    private String estadoPago = "PENDIENTE";

    /** HU-08 criterio 2: AUTOMATICA o MANUAL (si el asistente escribió algún dato a mano). Solo cuando está Pagado. */
    @Column(name = "tipo_verificacion")
    private String tipoVerificacion;

    @Column(name = "pagado_en")
    private OffsetDateTime pagadoEn;

    // HU-10 · En espera (lo pone y lo quita el gerente)
    @Column(name = "en_espera", nullable = false)
    private boolean enEspera = false;

    @Column(name = "nota_espera")
    private String notaEspera;

    @Column(name = "espera_por")
    private Long esperaPor;

    @Column(name = "espera_en")
    private OffsetDateTime esperaEn;

    @Column(name = "liberado_por")
    private Long liberadoPor;

    @Column(name = "liberado_en")
    private OffsetDateTime liberadoEn;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private Long registradoPor;

    @Column(name = "registrado_en", insertable = false, updatable = false)
    private OffsetDateTime registradoEn;

    protected Obligacion() { }

    public Obligacion(String concepto, Categoria categoria, String beneficiarioNombre, String beneficiarioDocumento,
                      BigDecimal monto, LocalDate fechaVencimiento, String observacion, Long registradoPor) {
        this.concepto = concepto;
        this.categoria = categoria;
        this.beneficiarioNombre = beneficiarioNombre;
        this.beneficiarioDocumento = beneficiarioDocumento;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
        this.observacion = observacion;
        this.registradoPor = registradoPor;
    }

    public Long getId() { return id; }
    public String getConcepto() { return concepto; }
    public Categoria getCategoria() { return categoria; }
    public String getBeneficiarioNombre() { return beneficiarioNombre; }
    public String getBeneficiarioDocumento() { return beneficiarioDocumento; }
    public BigDecimal getMonto() { return monto; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public String getObservacion() { return observacion; }
    public String getEstadoPago() { return estadoPago; }
    public String getTipoVerificacion() { return tipoVerificacion; }
    public OffsetDateTime getPagadoEn() { return pagadoEn; }
    public boolean isEnEspera() { return enEspera; }
    public String getNotaEspera() { return notaEspera; }
    public Long getEsperaPor() { return esperaPor; }
    public OffsetDateTime getEsperaEn() { return esperaEn; }
    public Long getLiberadoPor() { return liberadoPor; }
    public OffsetDateTime getLiberadoEn() { return liberadoEn; }
    public boolean isActivo() { return activo; }
    public Long getRegistradoPor() { return registradoPor; }
    public OffsetDateTime getRegistradoEn() { return registradoEn; }

    /** HU-08 criterio 2: las cuatro reglas se cumplieron. */
    public void marcarPagado(String tipoVerificacion, OffsetDateTime ahora) {
        this.estadoPago = "PAGADO";
        this.tipoVerificacion = tipoVerificacion;
        this.pagadoEn = ahora;
    }

    /** HU-08 criterio 3: alguna regla falló; el asistente puede subir otro comprobante. */
    public void marcarObservado() {
        this.estadoPago = "OBSERVADO";
        this.tipoVerificacion = null;
        this.pagadoEn = null;
    }

    /** HU-10 criterio 2: pasa a "En espera" y guarda la nota, el usuario y la fecha. */
    public void ponerEnEspera(String nota, Long usuarioId, OffsetDateTime ahora) {
        this.enEspera = true;
        this.notaEspera = nota;
        this.esperaPor = usuarioId;
        this.esperaEn = ahora;
        this.liberadoPor = null;
        this.liberadoEn = null;
    }

    /**
     * HU-10 criterio 4: "Liberar pago". Al quitar la marca, el estado vuelve a calcularse
     * por la fecha (Por pagar, Por vencer o Vencido). La última nota queda guardada como historial.
     */
    public void liberar(Long usuarioId, OffsetDateTime ahora) {
        this.enEspera = false;
        this.liberadoPor = usuarioId;
        this.liberadoEn = ahora;
    }
}
