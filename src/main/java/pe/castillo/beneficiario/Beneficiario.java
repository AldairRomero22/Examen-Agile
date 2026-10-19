package pe.castillo.beneficiario;

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
@Table(name = "beneficiario")
public class Beneficiario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String documento;

    @Column(nullable = false)
    private String banco;

    @Column(nullable = false)
    private String cuenta;

    /** Rubro del beneficiario; vacío solo en beneficiarios antiguos sin obligaciones. */
    @ManyToOne
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private Long creadoPor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected Beneficiario() { }

    public Beneficiario(String nombre, String documento, String banco, String cuenta, Categoria categoria, Long creadoPor) {
        this.nombre = nombre;
        this.documento = documento;
        this.banco = banco;
        this.cuenta = cuenta;
        this.categoria = categoria;
        this.creadoPor = creadoPor;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDocumento() { return documento; }
    public String getBanco() { return banco; }
    public String getCuenta() { return cuenta; }
    public Categoria getCategoria() { return categoria; }
    public boolean isActivo() { return activo; }
    public Long getCreadoPor() { return creadoPor; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
