package com.umc.pediupatrao.config;

import com.umc.pediupatrao.service.UsuarioDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Configuração de segurança.
 *
 * - Três perfis: ADMIN, GERENTE e ATENDENTE (authorities ROLE_*).
 * - Autorização por rota (aqui) + @PreAuthorize nos serviços (defesa em profundidade).
 * - CSRF habilitado para todas as páginas e APIs; formulários Thymeleaf enviam o token
 *   automaticamente e as chamadas fetch usam o cabeçalho X-CSRF-TOKEN (ver layout.html).
 * - Chamadas anônimas a /api/** recebem 401; páginas redirecionam para /login.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String GERENTE = "GERENTE";
    private static final String ATENDENTE = "ATENDENTE";

    private final UsuarioDetailsService usuarioDetailsService;

    public SecurityConfig(UsuarioDetailsService usuarioDetailsService) {
        this.usuarioDetailsService = usuarioDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(usuarioDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /** Registro de sessões: permite encerrar as sessões de um usuário cujo perfil foi alterado. */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        LoginUrlAuthenticationEntryPoint login = new LoginUrlAuthenticationEntryPoint("/login");
        HttpStatusEntryPoint api = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        return (request, response, ex) -> {
            if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                api.commence(request, response, ex);
            } else {
                login.commence(request, response, ex);
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF permanece HABILITADO (sem exceções para APIs ou páginas).
                .csrf(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // públicos
                        .requestMatchers("/login", "/error", "/css/**", "/dist/**", "/plugins/**",
                                "/js/**", "/images/**").permitAll()

                        // administração de usuários e perfis: somente ADMIN
                        .requestMatchers("/usuarios/**", "/api/usuarios/**", "/admin/**").hasRole(ADMIN)

                        // auditoria: somente consulta, para ADMIN e GERENTE
                        .requestMatchers(HttpMethod.GET, "/auditoria/**", "/api/auditoria/**")
                        .hasAnyRole(ADMIN, GERENTE)
                        .requestMatchers("/auditoria/**", "/api/auditoria/**").denyAll()

                        // pedidos: desconto e cancelamento exclusivos do GERENTE (nem o ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos/*/desconto", "/api/pedidos/*/cancelar",
                                "/pedidos/*/desconto", "/pedidos/*/cancelar").hasRole(GERENTE)
                        // demais escritas em pedidos: ATENDENTE e GERENTE
                        .requestMatchers(HttpMethod.GET, "/pedidos/novo", "/pedidos/*/editar")
                        .hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.POST, "/api/pedidos", "/api/pedidos/**", "/pedidos/**")
                        .hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/pedidos/**").hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/pedidos/**", "/pedidos/**").denyAll()
                        // consulta de pedidos
                        .requestMatchers(HttpMethod.GET, "/api/pedidos/**", "/pedidos/**", "/pedidos")
                        .hasAnyRole(ADMIN, GERENTE, ATENDENTE)

                        // clientes: consulta (GERENTE, ATENDENTE); cadastro/alteração (ATENDENTE); exclusão (GERENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes/novo", "/clientes/editar/*").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.GET, "/clientes/**", "/api/clientes/**", "/clientes")
                        .hasAnyRole(GERENTE, ATENDENTE)
                        .requestMatchers(HttpMethod.DELETE, "/api/clientes/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/clientes/**", "/api/clientes/**").hasRole(ATENDENTE)
                        .requestMatchers(HttpMethod.PUT, "/api/clientes/**").hasRole(ATENDENTE)

                        // produtos: consulta para os três perfis; manutenção do catálogo (ADMIN e GERENTE)
                        .requestMatchers(HttpMethod.GET, "/produtos/**", "/api/produtos/**", "/produtos")
                        .hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                        .requestMatchers("/produtos/**", "/api/produtos/**").hasAnyRole(ADMIN, GERENTE)

                        // qualquer outra rota exige um dos três perfis
                        .anyRequest().hasAnyRole(ADMIN, GERENTE, ATENDENTE)
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .formLogin(form -> form
                        .loginPage("/login")
                        .failureUrl("/login?error")
                        .successHandler(authenticationSuccessHandler())
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .sessionManagement(sm -> sm
                        .sessionConcurrency(c -> c.maximumSessions(-1).sessionRegistry(sessionRegistry()))
                );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.sendRedirect(request.getContextPath() + "/"); // página inicial após login
        };
    }
}
