package pe.castillo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                // HU-01 criterio 3: solo el asistente crea o modifica; el gerente solo consulta.
                .requestMatchers(HttpMethod.POST, "/api/beneficiarios/**", "/api/obligaciones/**").hasRole("ASISTENTE")
                .requestMatchers("/beneficiario-nuevo.html", "/obligacion-nueva.html").hasRole("ASISTENTE")
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
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), esApi)
                .accessDeniedHandler((request, response, e) -> {
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.setStatus(HttpStatus.FORBIDDEN.value());
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"mensaje\":\"No tienes permiso para esta acción\"}");
                    } else {
                        response.sendRedirect("/acceso-denegado.html");
                    }
                }))
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
