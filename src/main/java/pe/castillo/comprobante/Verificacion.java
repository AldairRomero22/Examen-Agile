package pe.castillo.comprobante;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** HU-08 criterio 6: cada verificación queda registrada con fecha, hora y resultado. */
@Entity
@Table(name = "verificacion")
public class Verificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comprobante_id", nullable = false, updatable = false)
    private Long comprobanteId;

    @Column(name = "obligacion_id", nullable = false, updatable = false)
    private Long obligacionId;

    /** PAGADO u OBSERVADO */
    @Column(nullable = false, updatable = false)
    private String resultado;

    @Column(name = "regla_monto", nullable = false, updatable = false)
    private boolean reglaMonto;

    @Column(name = "regla_fecha", nullable = false, updatable = false)
    private boolean reglaFecha;

    @Column(name = "regla_operacion", nullable = false, updatable = false)
    private boolean reglaOperacion;

    @Column(name = "regla_archivo", nullable = false, updatable = false)
    private boolean reglaArchivo;

    @Column(updatable = false)
    private String detalle;

    @Column(name = "verificado_por", nullable = false, updatable = false)
    private Long verificadoPor;

    @Column(name = "verificado_en", nullable = false, updatable = false)
    private OffsetDateTime verificadoEn;

    protected Verificacion() { }

    public Verificacion(Long comprobanteId, Long obligacionId, boolean reglaMonto, boolean reglaFecha,
                        boolean reglaOperacion, boolean reglaArchivo, String detalle,
                        Long verificadoPor, OffsetDateTime verificadoEn) {
        this.comprobanteId = comprobanteId;
        this.obligacionId = obligacionId;
        this.reglaMonto = reglaMonto;
        this.reglaFecha = reglaFecha;
        this.reglaOperacion = reglaOperacion;
        this.reglaArchivo = reglaArchivo;
        this.resultado = (reglaMonto && reglaFecha && reglaOperacion && reglaArchivo) ? "PAGADO" : "OBSERVADO";
        this.detalle = detalle;
        this.verificadoPor = verificadoPor;
        this.verificadoEn = verificadoEn;
    }

    public Long getId() { return id; }
    public Long getComprobanteId() { return comprobanteId; }
    public Long getObligacionId() { return obligacionId; }
    public String getResultado() { return resultado; }
    public boolean isReglaMonto() { return reglaMonto; }
    public boolean isReglaFecha() { return reglaFecha; }
    public boolean isReglaOperacion() { return reglaOperacion; }
    public boolean isReglaArchivo() { return reglaArchivo; }
    public String getDetalle() { return detalle; }
    public Long getVerificadoPor() { return verificadoPor; }
    public OffsetDateTime getVerificadoEn() { return verificadoEn; }
}
