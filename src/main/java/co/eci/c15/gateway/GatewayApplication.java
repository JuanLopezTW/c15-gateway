package co.eci.c15.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway del EscapeRoom C15: la unica puerta de entrada del front. Reparte cada peticion al servicio que
 * le toca (salas, gameplay, social) y el WebSocket al broker, valida el JWT en la entrada y maneja CORS.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
