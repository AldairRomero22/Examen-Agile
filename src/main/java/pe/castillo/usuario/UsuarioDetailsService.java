package pe.castillo.usuario;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security usa esta clase para buscar al usuario por su correo al iniciar sesión. */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarios;

    public UsuarioDetailsService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario u = usuarios.findByCorreoIgnoreCase(correo == null ? "" : correo.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas"));

        return User.withUsername(u.getCorreo())
                .password(u.getClaveHash())
                .roles(u.getRol().name())          // queda como ROLE_ASISTENTE o ROLE_GERENTE
                .disabled(!u.isActivo())
                .build();
    }
}
