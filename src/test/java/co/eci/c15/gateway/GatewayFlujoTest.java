package co.eci.c15.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * El gateway contra 4 servicios falsos que responden su nombre, la ruta y el token que les llego: cada ruta va
 * a su servicio, el token viaja, sin token no se llega a ninguno y el front puede llamar desde su origen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "c15.cors.origenes=http://localhost:5173")
@AutoConfigureWebTestClient
class GatewayFlujoTest {

    private static final List<HttpServer> SERVICIOS = new ArrayList<>();
    /** Cuantas peticiones llegaron a los servicios (para ver que lo rechazado no pasa). */
    private static final AtomicInteger LLEGADAS = new AtomicInteger();

    @DynamicPropertySource
    static void servicios(DynamicPropertyRegistry propiedades) throws IOException {
        for (String nombre : List.of("broker", "salas", "gameplay", "social")) {
            HttpServer servicio = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            servicio.createContext("/", intercambio -> {
                LLEGADAS.incrementAndGet();
                String token = intercambio.getRequestHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                byte[] cuerpo = (nombre + " " + intercambio.getRequestMethod() + " " + intercambio.getRequestURI() + " " + token)
                        .getBytes(StandardCharsets.UTF_8);
                intercambio.sendResponseHeaders(200, cuerpo.length);
                try (OutputStream salida = intercambio.getResponseBody()) {
                    salida.write(cuerpo);
                }
            });
            servicio.start();
            SERVICIOS.add(servicio);
            String url = "http://localhost:" + servicio.getAddress().getPort();
            propiedades.add(nombre.toUpperCase() + "_URL", () -> url);
        }
    }

    @AfterAll
    static void apagar() {
        SERVICIOS.forEach(s -> s.stop(0));
    }

    /** Sin esto el token iria a buscar las llaves de Auth0 por la red. */
    @MockitoBean
    private ReactiveJwtDecoder tokens;

    @Autowired
    private WebTestClient gateway;

    @LocalServerPort
    private int puerto;

    /** CORS se prueba con un cliente HTTP de verdad contra el puerto (como el navegador). */
    private WebTestClient navegador() {
        return WebTestClient.bindToServer().baseUrl("http://localhost:" + puerto).build();
    }

    @BeforeEach
    void setUp() {
        when(tokens.decode(anyString())).thenAnswer(i -> "token-falso".equals(i.getArgument(0))
                ? Mono.error(new BadJwtException("firma invalida"))
                : Mono.just(Jwt.withTokenValue(i.getArgument(0)).header("alg", "RS256").subject("auth0|ana")
                .claim("https://api.escaperoomc15/roles", "token-admin".equals(i.getArgument(0)) ? List.of("admin") : List.of())
                .issuedAt(Instant.EPOCH).expiresAt(Instant.parse("2999-01-01T00:00:00Z")).claims(c -> c.putAll(Map.of("aud", "x"))).build()));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/salas, salas",
            "/api/salas/s1/listo, salas",
            "/api/equipos/e1/miembros, salas",
            "/api/partidas/m1/cronometro, gameplay",
            "/api/perfil, social",
            "/api/usuarios, social",
            "/api/chat/global, social",
            "/api/estadisticas/ranking, social",
    })
    void cadaRutaVaASuServicioConElTokenDelJugador(String ruta, String servicio) {
        gateway.get().uri(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer token-ana").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(servicio + " GET " + ruta + " Bearer token-ana");
    }

    @Test
    void lasFuncionesDeAdministracionSoloLlegaConElRolAdmin() {
        int antes = LLEGADAS.get();
        gateway.get().uri("/dev/partidas/m1/inicio").header(HttpHeaders.AUTHORIZATION, "Bearer token-ana").exchange()
                .expectStatus().isForbidden();
        gateway.get().uri("/api/admin/resumen").header(HttpHeaders.AUTHORIZATION, "Bearer token-ana").exchange()
                .expectStatus().isForbidden();
        org.junit.jupiter.api.Assertions.assertEquals(antes, LLEGADAS.get(), "el jugador ni llega al servicio");

        gateway.get().uri("/dev/partidas/m1/inicio").header(HttpHeaders.AUTHORIZATION, "Bearer token-admin").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("gameplay GET /dev/partidas/m1/inicio Bearer token-admin");
        gateway.get().uri("/api/admin/tablero/negocio").header(HttpHeaders.AUTHORIZATION, "Bearer token-admin").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("social GET /api/admin/tablero/negocio Bearer token-admin");
    }

    @ParameterizedTest
    @CsvSource({"c15-broker, broker", "c15-gameplay, gameplay"})
    void laPruebaDeCaidaVaAlServicioQueSeNombraYSoloLaUsaElAdmin(String nombre, String servicio) {
        String ruta = "/dev/caida/" + nombre;
        int antes = LLEGADAS.get();
        gateway.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer token-ana").exchange()
                .expectStatus().isForbidden();
        org.junit.jupiter.api.Assertions.assertEquals(antes, LLEGADAS.get(), "un jugador no tumba nada");

        gateway.post().uri(ruta).header(HttpHeaders.AUTHORIZATION, "Bearer token-admin").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo(servicio + " POST " + ruta + " Bearer token-admin");
    }

    @Test
    void conTokenSePuedeEscribirSinCsrf() {
        gateway.post().uri("/api/salas").header(HttpHeaders.AUTHORIZATION, "Bearer token-ana").bodyValue("{}").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("salas POST /api/salas Bearer token-ana");
    }

    @Test
    void sinTokenOConTokenInvalidoNoSeLlegaANingunServicio() {
        int antes = LLEGADAS.get();

        gateway.get().uri("/api/salas").exchange().expectStatus().isUnauthorized();
        gateway.get().uri("/api/partidas/m1/mapa").header(HttpHeaders.AUTHORIZATION, "Bearer token-falso").exchange()
                .expectStatus().isUnauthorized();
        // Escribir sin token: CSRF lo frena y responde 401 (le falta autenticarse), igual que cualquier peticion sin token
        gateway.post().uri("/api/salas").bodyValue("{}").exchange().expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        gateway.delete().uri("/api/salas/s1/jugadores/u1").exchange().expectStatus().isUnauthorized();

        org.junit.jupiter.api.Assertions.assertEquals(antes, LLEGADAS.get());
    }

    @Test
    void elWebSocketYLaVersionNoPidenToken() {
        // El token del WebSocket lo valida el broker en el CONNECT de STOMP
        gateway.get().uri("/ws/info").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("broker GET /ws/info null");
        gateway.get().uri("/api/version").exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.version").isEqualTo("dev");
    }

    @Test
    void elFrontPuedeLlamarDesdeSuOrigenYVerElTraceId() {
        navegador().options().uri("/api/salas")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173");

        navegador().get().uri("/api/salas").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-ana").exchange()
                .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Trace-Id");
    }

    @Test
    void otroOrigenNoPuede() {
        navegador().options().uri("/api/salas")
                .header(HttpHeaders.ORIGIN, "https://sitio-ajeno.example")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .exchange()
                .expectStatus().isForbidden();
    }
}
