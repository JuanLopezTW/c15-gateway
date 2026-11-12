package co.eci.c15.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Commit del gateway que esta corriendo (lo pone el Dockerfile en APP_VERSION; "dev" fuera de la imagen). Cada
 * servicio tiene el suyo; este es el que ve el front en {@code /api/version}.
 */
@RestController
public class VersionController {

    private final String version;

    public VersionController(@Value("${APP_VERSION:dev}") String version) {
        this.version = version;
    }

    @GetMapping("/api/version")
    public Map<String, String> version() {
        return Map.of("version", version);
    }
}
