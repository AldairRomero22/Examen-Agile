package pe.castillo.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * HU-01 criterio 1 y 4: crea las dos cuentas (asistente y gerente) la primera vez que arranca.
 * Las claves vienen de variables de entorno, se guardan con BCrypt y nunca están en el código.
 * Si la cuenta ya existe, no la toca.
 */
@Component
public class CuentasIniciales implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CuentasIniciales.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    @Value("${castillo.asistente.correo}")
    private String correoAsistente;

    @Value("${castillo.asistente.clave:}")
    private String claveAsistente;

    @Value("${castillo.gerente.correo}")
    private String correoGerente;

    @Value("${castillo.gerente.clave:}")
    private String claveGerente;

    public CuentasIniciales(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        crearSiFalta(correoAsistente, claveAsistente, "Asistente", Rol.ASISTENTE, "CASTILLO_ASISTENTE_CLAVE");
        crearSiFalta(correoGerente, claveGerente, "Gerente", Rol.GERENTE, "CASTILLO_GERENTE_CLAVE");
    }

    private void crearSiFalta(String correo, String clave, String nombre, Rol rol, String variable) {
        if (usuarios.existsByCorreoIgnoreCase(correo)) {
            return;
        }
        if (clave == null || clave.length() < 8) {
            log.warn("No se creó la cuenta {}: falta la variable {} (mínimo 8 caracteres).", correo, variable);
            return;
        }
        usuarios.save(new Usuario(correo, encoder.encode(clave), nombre, rol));
        log.info("Cuenta creada: {} ({})", correo, rol);
    }
}
