package pe.castillo.prestamo;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Una fila del cronograma: N.º, vencimiento, tipo (NORMAL o DOBLE) e importes. */
@Entity
@Table(name = "prestamo_cuota")
public class PrestamoCuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prestamo_id", nullable = false)
    private Prestamo prestamo;

    @Column(nullable = false)
    private int numero;

    @Column(nullable = false)
    private LocalDate vencimiento;

    @Column(nullable = false, length = 10)
    private String tipo;

    @Column(name = "saldo_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoInicial;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal interes;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amortizacion;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cuota;

    @Column(name = "saldo_final", nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoFinal;

    protected PrestamoCuota() { }

    public PrestamoCuota(int numero, LocalDate vencimiento, String tipo, BigDecimal saldoInicial, BigDecimal interes,
                         BigDecimal amortizacion, BigDecimal cuota, BigDecimal saldoFinal) {
        this.numero = numero;
        this.vencimiento = vencimiento;
        this.tipo = tipo;
        this.saldoInicial = saldoInicial;
        this.interes = interes;
        this.amortizacion = amortizacion;
        this.cuota = cuota;
        this.saldoFinal = saldoFinal;
    }

    void asignarPrestamo(Prestamo p) { this.prestamo = p; }

    public Long getId() { return id; }
    public int getNumero() { return numero; }
    public LocalDate getVencimiento() { return vencimiento; }
    public String getTipo() { return tipo; }
    public BigDecimal getSaldoInicial() { return saldoInicial; }
    public BigDecimal getInteres() { return interes; }
    public BigDecimal getAmortizacion() { return amortizacion; }
    public BigDecimal getCuota() { return cuota; }
    public BigDecimal getSaldoFinal() { return saldoFinal; }
}
