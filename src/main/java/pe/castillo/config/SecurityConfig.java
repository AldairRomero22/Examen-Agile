package pe.castillo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * HU-01: inicio de sesión por rol.
 * - Solo login.html, css y js son públicos; cualquier otra pantalla exige sesión (criterio 5).
 * - Contraseñas con BCrypt (criterio 4).
 * - "Cerrar sesión" en /logout (criterio 6).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Las llamadas a /api/** sin sesión responden 401 (el JS redirige al login)
        RequestMatcher esApi = request -> request.getRequestURI().startsWith("/api/");

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login.html", "/css/**", "/js/**", "/favicon.ico", "/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login.html")
                .loginProcessingUrl("/login")
                .usernameParameter("correo")
                .passwordParameter("clave")
                .defaultSuccessUrl("/panel.html", true)
                .failureUrl("/login.html?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login.html?salir")
                .permitAll())
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), esApi))
            // CSRF con cookie XSRF-TOKEN legible por JS: las páginas son HTML estático
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
