package co.eci.c15.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Set;

/**
 * El token se valida aqui, en la entrada: lo que llega sin un token valido de Auth0 ni toca los servicios (ellos
 * lo vuelven a validar por si alguien llega directo). /ws queda abierto porque el navegador no puede mandar
 * headers al abrir el WebSocket: el broker valida el token en el CONNECT de STOMP.
 */
@Configuration
@EnableWebFluxSecurity
public class SeguridadConfig {

    private static final Set<HttpMethod> SEGUROS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS, HttpMethod.TRACE);

    @Bean
    public SecurityWebFilterChain seguridad(ServerHttpSecurity http) {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.requireCsrfProtectionMatcher(sinTokenBearer()))
                .authorizeExchange(a -> a
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/api/version", "/ws/**", "/actuator/health").permitAll()
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
                .build();
    }

    /**
     * CSRF solo protege lo que se autentica con cookies. Las peticiones que cambian algo y traen token Bearer
     * no lo necesitan (como en los servicios: oauth2ResourceServer las exime); las demas siguen protegidas.
     */
    static ServerWebExchangeMatcher sinTokenBearer() {
        return exchange -> {
            boolean cambia = !SEGUROS.contains(exchange.getRequest().getMethod());
            String autorizacion = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            boolean conBearer = autorizacion != null && autorizacion.startsWith("Bearer ");
            return cambia && !conBearer ? ServerWebExchangeMatcher.MatchResult.match() : ServerWebExchangeMatcher.MatchResult.notMatch();
        };
    }

    /** El front (Static Web App en Azure, Vite en local) llama a una sola URL: la del gateway. */
    @Bean
    public CorsConfigurationSource cors(@Value("${c15.cors.origenes}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // El front muestra los primeros caracteres del traceId cuando algo falla (5xx)
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/**", config);
        return fuente;
    }
}
