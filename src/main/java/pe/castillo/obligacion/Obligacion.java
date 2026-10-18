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

import pe.castillo.beneficiario.Beneficiario;
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

    @ManyToOne(optional = false)
    @JoinColumn(name = "beneficiario_id", nullable = false)
    private Beneficiario beneficiario;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    private String observacion;

    /** PENDIENTE, EN_VERIFICACION, PAGADO u OBSERVADO. Por pagar/Por vencer/Vencido se calculan. */
    @Column(name = "estado_pago", nullable = false)
    private String estadoPago = "PENDIENTE";

    @Column(name = "en_espera", nullable = false)
    private boolean enEspera = false;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private Long registradoPor;

    @Column(name = "registrado_en", insertable = false, updatable = false)
    private OffsetDateTime registradoEn;

    protected Obligacion() { }

    public Obligacion(String concepto, Categoria categoria, Beneficiario beneficiario, BigDecimal monto,
                      LocalDate fechaVencimiento, String observacion, Long registradoPor) {
        this.concepto = concepto;
        this.categoria = categoria;
        this.beneficiario = beneficiario;
        this.monto = monto;
        this.fechaVencimiento = fechaVencimiento;
        this.observacion = observacion;
        this.registradoPor = registradoPor;
    }

    public Long getId() { return id; }
    public String getConcepto() { return concepto; }
    public Categoria getCategoria() { return categoria; }
    public Beneficiario getBeneficiario() { return beneficiario; }
    public BigDecimal getMonto() { return monto; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public String getObservacion() { return observacion; }
    public String getEstadoPago() { return estadoPago; }
    public boolean isEnEspera() { return enEspera; }
    public boolean isActivo() { return activo; }
    public Long getRegistradoPor() { return registradoPor; }
    public OffsetDateTime getRegistradoEn() { return registradoEn; }
}
