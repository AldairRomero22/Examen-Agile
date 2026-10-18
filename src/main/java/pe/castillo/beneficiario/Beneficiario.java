package pe.castillo.beneficiario;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_por", nullable = false, updatable = false)
    private Long creadoPor;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected Beneficiario() { }

    public Beneficiario(String nombre, String documento, String banco, String cuenta, Long creadoPor) {
        this.nombre = nombre;
        this.documento = documento;
        this.banco = banco;
        this.cuenta = cuenta;
        this.creadoPor = creadoPor;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getDocumento() { return documento; }
    public String getBanco() { return banco; }
    public String getCuenta() { return cuenta; }
    public boolean isActivo() { return activo; }
    public Long getCreadoPor() { return creadoPor; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }
}
