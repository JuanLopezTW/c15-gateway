package co.eci.c15.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Una linea por peticion en la puerta de entrada (ruta, estado, ms), con los mismos campos que los servicios:
 * es el tiempo que siente el jugador. Los ids de la ruta se reemplazan por {id} para poder agrupar.
 * El WebSocket (/ws) no se mide: su "duracion" seria lo que dura la conexion.
 */
@Component
public class RegistroPeticionesFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RegistroPeticionesFilter.class);
    /** uuid, numeros, ids de Auth0 (con | o cualquier tramo codificado con %) o tokens largos */
    private static final Pattern ID = Pattern.compile("[0-9a-fA-F-]{8,}|\\d+|.*[|%].*|[A-Za-z0-9_-]{20,}");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ruta = exchange.getRequest().getPath().value();
        if (ruta.startsWith("/ws")) return chain.filter(exchange);
        long inicio = System.nanoTime();
        return chain.filter(exchange).doFinally(senal -> {
            HttpStatusCode codigo = exchange.getResponse().getStatusCode();
            int estado = codigo == null ? 200 : codigo.value();
            long ms = (System.nanoTime() - inicio) / 1_000_000;
            String metodo = exchange.getRequest().getMethod().name();
            String plantilla = plantilla(ruta);
            log.atInfo()
                    .addKeyValue("evento", "http.peticion")
                    .addKeyValue("http.metodo", metodo)
                    .addKeyValue("http.ruta", plantilla)
                    .addKeyValue("http.estado", estado)
                    .addKeyValue("duracion.ms", ms)
                    .log("{} {} -> {} en {} ms", metodo, plantilla, estado, ms);
        });
    }

    /** /api/partidas/3f2a.../jugadores/auth0%7Cabc/movimiento -> /api/partidas/{id}/jugadores/{id}/movimiento */
    static String plantilla(String ruta) {
        return Arrays.stream(ruta.split("/", -1))
                .map(s -> !s.isEmpty() && ID.matcher(s).matches() ? "{id}" : s)
                .collect(Collectors.joining("/"));
    }
}
