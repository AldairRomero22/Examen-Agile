package pe.castillo.usuario;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String correo;

    @Column(name = "clave_hash", nullable = false)
    private String claveHash;

    @Column(nullable = false)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "creado_en", insertable = false, updatable = false)
    private OffsetDateTime creadoEn;

    protected Usuario() {
        // para JPA
    }

    public Usuario(String correo, String claveHash, String nombre, Rol rol) {
        this.correo = correo;
        this.claveHash = claveHash;
        this.nombre = nombre;
        this.rol = rol;
    }

    public Long getId() { return id; }
    public String getCorreo() { return correo; }
    public String getClaveHash() { return claveHash; }
    public String getNombre() { return nombre; }
    public Rol getRol() { return rol; }
    public boolean isActivo() { return activo; }
    public OffsetDateTime getCreadoEn() { return creadoEn; }

    public void setActivo(boolean activo) { this.activo = activo; }
}
