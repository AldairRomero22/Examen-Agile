package pe.castillo.prestamo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/** Préstamo de capital con su cronograma (solo consulta: las cuotas no se pagan desde aquí). */
@Entity
@Table(name = "prestamo")
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String entidad;

    @Column(name = "nro_credito", nullable = false, length = 40)
    private String nroCredito;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    /** Tasa de interés anual en %. */
    @Column(name = "tasa_interes", nullable = false, precision = 7, scale = 4)
    private BigDecimal tasaInteres;

    /** Número de cuotas. */
    @Column(nullable = false)
    private int plazo;

    @Column(name = "fecha_desembolso", nullable = false)
    private LocalDate fechaDesembolso;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "registrado_por", nullable = false, updatable = false)
    private Long registradoPor;

    @Column(name = "registrado_en", insertable = false, updatable = false)
    private OffsetDateTime registradoEn;

    @OneToMany(mappedBy = "prestamo", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numero ASC")
    private List<PrestamoCuota> cuotas = new ArrayList<>();

    protected Prestamo() { }

    public Prestamo(String entidad, String nroCredito, BigDecimal monto, BigDecimal tasaInteres, int plazo,
                    LocalDate fechaDesembolso, Long registradoPor) {
        this.entidad = entidad;
        this.nroCredito = nroCredito;
        this.monto = monto;
        this.tasaInteres = tasaInteres;
        this.plazo = plazo;
        this.fechaDesembolso = fechaDesembolso;
        this.registradoPor = registradoPor;
    }

    public void agregarCuota(PrestamoCuota cuota) {
        cuota.asignarPrestamo(this);
        cuotas.add(cuota);
    }

    public Long getId() { return id; }
    public String getEntidad() { return entidad; }
    public String getNroCredito() { return nroCredito; }
    public BigDecimal getMonto() { return monto; }
    public BigDecimal getTasaInteres() { return tasaInteres; }
    public int getPlazo() { return plazo; }
    public LocalDate getFechaDesembolso() { return fechaDesembolso; }
    public boolean isActivo() { return activo; }
    public Long getRegistradoPor() { return registradoPor; }
    public OffsetDateTime getRegistradoEn() { return registradoEn; }
    public List<PrestamoCuota> getCuotas() { return cuotas; }
}
