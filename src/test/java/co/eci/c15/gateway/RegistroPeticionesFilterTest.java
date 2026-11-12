package co.eci.c15.gateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RegistroPeticionesFilterTest {

    private final RegistroPeticionesFilter filtro = new RegistroPeticionesFilter();
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RegistroPeticionesFilter.class);

    @BeforeEach
    void escuchar() {
        eventos.start();
        logger.addAppender(eventos);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(eventos);
    }

    @Test
    void cadaPeticionDejaUnaLineaConRutaEstadoYDuracion() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/partidas/3f2a9c1e-0000-4000-8000-1234567890ab/jugadores/auth0%7Cabc123/movimiento"));

        filtro.filter(intercambio, e -> {
            e.getResponse().setStatusCode(HttpStatus.CONFLICT);
            return Mono.empty();
        }).block();

        assertEquals(1, eventos.list.size());
        Map<String, Object> campos = eventos.list.get(0).getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> kv.value));
        assertEquals("http.peticion", campos.get("evento"));
        assertEquals("POST", campos.get("http.metodo"));
        assertEquals("/api/partidas/{id}/jugadores/{id}/movimiento", campos.get("http.ruta"));
        assertEquals(409, campos.get("http.estado"));
        assertTrue(((Long) campos.get("duracion.ms")) >= 0);
    }

    @Test
    void sinCodigoDeRespuestaCuentaComo200() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(MockServerHttpRequest.get("/api/version"));

        filtro.filter(intercambio, e -> Mono.empty()).block();

        assertEquals(200, valor("http.estado"));
    }

    @Test
    void elWebSocketNoSeMide() {
        MockServerWebExchange intercambio = MockServerWebExchange.from(MockServerHttpRequest.get("/ws"));

        filtro.filter(intercambio, e -> Mono.empty()).block();

        assertTrue(eventos.list.isEmpty());
    }

    @Test
    void losIdsDeLaRutaSeAgrupan() {
        assertEquals("/api/salas/{id}/listo", RegistroPeticionesFilter.plantilla("/api/salas/9b1d2c3e-aaaa-4bbb-8ccc-0123456789ab/listo"));
        assertEquals("/api/partidas/{id}/cronometro", RegistroPeticionesFilter.plantilla("/api/partidas/12345/cronometro"));
        assertEquals("/api/chat/global", RegistroPeticionesFilter.plantilla("/api/chat/global"));
        assertEquals("/api/admin/errores/{id}", RegistroPeticionesFilter.plantilla("/api/admin/errores/abcd1234"));
        assertEquals("/", RegistroPeticionesFilter.plantilla("/"));
    }

    private Object valor(String clave) {
        return eventos.list.get(0).getKeyValuePairs().stream()
                .filter(kv -> kv.key.equals(clave)).map(kv -> kv.value).findFirst().orElse(null);
    }
}
