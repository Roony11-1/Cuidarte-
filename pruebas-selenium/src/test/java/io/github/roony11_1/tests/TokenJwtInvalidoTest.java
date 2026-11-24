package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.github.roony11_1.api.ApiClient;
import io.github.roony11_1.base.BaseTest;

/**
 * GEM-PNF-002 · Manejo seguro de token JWT adulterado o inválido.
 *
 * <p>Requerimientos: NFR-SEG-9 y NFR-SEG-4. Tipo: No funcional / Seguridad.</p>
 *
 * <p>Verifica que {@code GET /examenes/:id} responde siempre HTTP 401 con el mismo
 * cuerpo genérico cuando el token está ausente, malformado, con la firma
 * alterada o expirado, y HTTP 200 cuando el token es válido.</p>
 *
 * <p>El examen consultado se crea en {@code @BeforeEach} con la API real y se
 * elimina en {@code @AfterEach}. Así el test no depende de IDs que existan en la
 * base de datos (por ejemplo, la base limpia que levanta el pipeline).</p>
 *
 * <p>Para que el caso "token expirado" sea real (firma correcta y solo el
 * {@code exp} vencido) el test firma sus propios JWT HS256 con el secreto
 * configurado en {@code security.jwt_secret}. Además firma un token vigente con
 * el mismo procedimiento: si ese token vigente responde 200, queda demostrado
 * que el secreto y la firma son correctos y que el rechazo del expirado se debe
 * únicamente a su expiración.</p>
 */
public class TokenJwtInvalidoTest extends BaseTest
{
    private static final String TOKEN_MALFORMADO = "abc.def.ghi";

    private static final String SECRETO = CONFIG.security("jwt_secret");

    private String tokenMedico;
    private long examenId;

    @BeforeEach
    void crearExamenDePrueba() throws Exception
    {
        tokenMedico = api.iniciarSesion("medico", "medico");

        String payload = """
            {
                "paciente_id": 1,
                "tipo_examen_medico_id": 1,
                "diagnosis": "Examen GEM-PNF-002",
                "tratamiento": "Sin tratamiento",
                "observaciones": "Prueba de manejo de token JWT",
                "notas": "Creado vía API por TokenJwtInvalidoTest"
            }
            """;

        HttpResponse<String> creado = api.postJson("/examenes", tokenMedico, payload);

        assertEquals(201, creado.statusCode(),
            "No se pudo crear el examen de prueba. Body=" + creado.body());

        examenId = ApiClient.extraerId(creado.body());
    }

    @AfterEach
    void eliminarExamenDePrueba() throws Exception
    {
        if (examenId <= 0)
            return;

        String tokenAdmin = api.iniciarSesion("admin", "admin");

        api.delete("/examenes/" + examenId, tokenAdmin);
    }

    @Test
    @DisplayName("GEM-PNF-002 · Token JWT ausente, malformado, adulterado o expirado")
    void tokensInvalidosDevuelven401Uniforme() throws Exception
    {
        String tokenValido = tokenMedico;
        long usuarioId = usuarioIdDe(tokenValido);

        HttpResponse<String> sinToken = api.get(rutaExamen(), null);
        HttpResponse<String> malformado = api.get(rutaExamen(), TOKEN_MALFORMADO);
        HttpResponse<String> firmaAlterada = api.get(rutaExamen(), alterarFirma(tokenValido));
        HttpResponse<String> expirado = api.get(rutaExamen(),
            firmar(usuarioId, ahoraEnSegundos() - 3600));

        List<Caso> invalidos = List.of(
            new Caso("token ausente", sinToken),
            new Caso("token malformado " + TOKEN_MALFORMADO, malformado),
            new Caso("token con firma alterada", firmaAlterada),
            new Caso("token expirado", expirado)
        );

        String cuerpoEsperado = sinToken.body();

        assertAll("GET " + rutaExamen() + " con tokens inválidos",
            invalidos.stream().map(caso -> (Executable) () ->
            {
                assertEquals(401, caso.respuesta().statusCode(),
                    "Se esperaba HTTP 401 con " + caso.descripcion()
                        + ". Body=" + caso.respuesta().body());

                assertEquals(cuerpoEsperado, caso.respuesta().body(),
                    "El cuerpo 401 debe ser uniforme con " + caso.descripcion()
                        + ". Body=" + caso.respuesta().body());

                assertTrue(caso.respuesta().body().contains("\"error\":\"No autorizado\""),
                    "Se esperaba 'No autorizado' con " + caso.descripcion()
                        + ". Body=" + caso.respuesta().body());

                assertTrue(caso.respuesta().body().contains("\"glosa\":\"Token inválido\""),
                    "Se esperaba 'Token inválido' con " + caso.descripcion()
                        + ". Body=" + caso.respuesta().body());
            }));
    }

    @Test
    @DisplayName("GEM-PNF-002 · Control con token válido y token firmado vigente")
    void tokensValidosDevuelven200() throws Exception
    {
        String tokenValido = tokenMedico;
        long usuarioId = usuarioIdDe(tokenValido);

        HttpResponse<String> controlLogin = api.get(rutaExamen(), tokenValido);
        HttpResponse<String> controlFirmado = api.get(rutaExamen(),
            firmar(usuarioId, ahoraEnSegundos() + 3600));

        assertAll("GET " + rutaExamen() + " con tokens válidos",
            () -> assertEquals(200, controlLogin.statusCode(),
                "El token de login debía permitir el acceso. Body=" + controlLogin.body()),
            () -> assertTrue(controlLogin.body().contains("\"id\":\"" + examenId + "\""),
                "La respuesta debía corresponder al examen " + examenId
                    + ". Body=" + controlLogin.body()),

            () -> assertEquals(200, controlFirmado.statusCode(),
                "Un JWT firmado con el secreto configurado debía ser aceptado; "
                    + "si falla, 'security.jwt_secret' no coincide con el del backend. "
                    + "Body=" + controlFirmado.body())
        );
    }

    private String rutaExamen()
    {
        return "/examenes/" + examenId;
    }

    /** Cambia el primer carácter de la firma para invalidarla sin tocar el payload. */
    private static String alterarFirma(String token)
    {
        String[] partes = token.split("\\.");

        char original = partes[2].charAt(0);
        char alterado = original == 'A' ? 'B' : 'A';

        partes[2] = alterado + partes[2].substring(1);

        return String.join(".", partes);
    }

    /** Firma un JWT HS256 con {@code usuarioId} y {@code exp} indicados. */
    private static String firmar(long usuarioId, long exp) throws Exception
    {
        String cabecera = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String cuerpo = base64Url("{\"usuarioId\":" + usuarioId + ",\"exp\":" + exp + "}");
        String firma = base64Url(hmacSha256(SECRETO, cabecera + "." + cuerpo));

        return cabecera + "." + cuerpo + "." + firma;
    }

    private static byte[] hmacSha256(String secreto, String datos) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");

        mac.init(new SecretKeySpec(
            secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        return mac.doFinal(datos.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(String texto)
    {
        return base64Url(texto.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] bytes)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static long usuarioIdDe(String token)
    {
        String[] partes = token.split("\\.");
        String payload = new String(
            Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);

        Matcher matcher = Pattern.compile("\"usuarioId\"\\s*:\\s*\"?(\\d+)\"?").matcher(payload);

        if (!matcher.find())
            throw new IllegalStateException("El JWT no contiene usuarioId: " + payload);

        return Long.parseLong(matcher.group(1));
    }

    private static long ahoraEnSegundos()
    {
        return System.currentTimeMillis() / 1000;
    }

    private record Caso(String descripcion, HttpResponse<String> respuesta) {}
}
