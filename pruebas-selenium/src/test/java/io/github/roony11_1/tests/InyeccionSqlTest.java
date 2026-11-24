package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.github.roony11_1.base.BaseTest;
import io.github.roony11_1.dao.PacienteDao;

/**
 * AUT-PNF-002 · Validación contra Inyección SQL en inicio de sesión.
 *
 * <p>Requerimiento: NFR-SEG-5. Tipo: No funcional / Seguridad.</p>
 *
 * <p>El caso envía seis payloads de inyección SQL a dos puntos de entrada y
 * comprueba en PostgreSQL que la tabla {@code pacientes} no fue alterada:</p>
 * <ol>
 *   <li>{@code POST /autenticacion/login} → HTTP 401 con "Credenciales inválidas".</li>
 *   <li>{@code GET /pacientes/buscar/rut/:rut} (payloads codificados) → HTTP 404
 *       con "No encontrado".</li>
 *   <li>La tabla {@code pacientes} conserva sus registros (20 según el caso) y
 *       no aparecen errores SQL en las respuestas.</li>
 * </ol>
 */
public class InyeccionSqlTest extends BaseTest
{
    /** Registros que el caso AUT-PNF-002 documenta en la tabla {@code pacientes}. */
    private static final int REGISTROS_ESPERADOS = 20;

    private static final List<String> PAYLOADS = List.of(
        "' OR '1'='1",
        "admin'--",
        "\" OR \"\"=",
        "UNION SELECT",
        "DROP TABLE",
        ";DELETE--"
    );

    /** Marcas típicas de un error de PostgreSQL filtrado al cliente. */
    private static final List<String> MARCAS_ERROR_SQL = List.of(
        "syntax error",
        "sqlstate",
        "pg_",
        "postgresql",
        "unterminated",
        "at or near",
        "relation \"",
        "stack"
    );

    private final PacienteDao pacientes = new PacienteDao();

    /**
     * AUT-PNF-002 · Paso 1: seis payloads SQL en {@code POST /autenticacion/login}.
     *
     * <p>Esperado: HTTP 401 con "Credenciales inválidas", sin token y sin
     * mensajes de error SQL.</p>
     */
    @Test
    @DisplayName("AUT-PNF-002 · Payloads SQL en POST /autenticacion/login")
    void payloadsSqlEnLogin() throws Exception
    {
        List<Intento> intentos = new ArrayList<>();

        for (String payload : PAYLOADS)
            intentos.add(new Intento(payload,
                api.postJson("/autenticacion/login", null, cuerpoLogin(payload))));

        assertAll("POST /autenticacion/login con payloads SQL",
            intentos.stream().map(intento -> (Executable) () ->
            {
                String contexto = contexto(intento);

                assertEquals(401, intento.respuesta().statusCode(),
                    "Se esperaba HTTP 401. " + contexto);

                String cuerpo = intento.respuesta().body().toLowerCase(Locale.ROOT);

                assertTrue(cuerpo.contains("credenciales inv"),
                    "Se esperaba el mensaje genérico 'Credenciales inválidas'. " + contexto);

                assertFalse(cuerpo.contains("token"),
                    "La respuesta no debe entregar un token. " + contexto);

                assertFalse(contieneErrorSql(cuerpo),
                    "La respuesta expone un error SQL. " + contexto);
            }));
    }

    /**
     * AUT-PNF-002 · Paso 2: seis payloads SQL codificados en
     * {@code GET /pacientes/buscar/rut/:rut}.
     *
     * <p>Esperado: HTTP 404 con "No encontrado" y sin mensajes de error SQL.</p>
     */
    @Test
    @DisplayName("AUT-PNF-002 · Payloads SQL en GET /pacientes/buscar/rut/:rut")
    void payloadsSqlEnBusquedaRut() throws Exception
    {
        List<Intento> intentos = new ArrayList<>();

        for (String payload : PAYLOADS)
            intentos.add(new Intento(payload,
                api.get(rutaBusquedaRut(payload), null)));

        assertAll("GET /pacientes/buscar/rut/:rut con payloads SQL",
            intentos.stream().map(intento -> (Executable) () ->
            {
                String contexto = contexto(intento);

                assertEquals(404, intento.respuesta().statusCode(),
                    "Se esperaba HTTP 404. " + contexto);

                String cuerpo = intento.respuesta().body().toLowerCase(Locale.ROOT);

                assertTrue(cuerpo.contains("no encontrado"),
                    "Se esperaba el mensaje genérico 'No encontrado'. " + contexto);

                assertFalse(contieneErrorSql(cuerpo),
                    "La respuesta expone un error SQL. " + contexto);
            }));
    }

    /**
     * AUT-PNF-002 · Paso 3: la tabla {@code pacientes} conserva sus 20 registros
     * después de ejecutar los doce payloads.
     */
    @Test
    @DisplayName("AUT-PNF-002 · PostgreSQL conserva los registros de pacientes")
    void postgresqlConservaLaTablaPacientes() throws Exception
    {
        int antes = pacientes.contar();

        assertEquals(REGISTROS_ESPERADOS, antes,
            "El caso AUT-PNF-002 documenta " + REGISTROS_ESPERADOS
                + " registros en la tabla pacientes, pero se encontraron " + antes);

        for (String payload : PAYLOADS)
        {
            api.postJson("/autenticacion/login", null, cuerpoLogin(payload));
            api.get(rutaBusquedaRut(payload), null);
        }

        int despues = pacientes.contar();

        assertAll("Estado de la tabla pacientes tras los payloads SQL",
            () -> assertTrue(pacientes.existeTabla(),
                "La tabla pacientes ya no existe: el payload DROP TABLE tuvo efecto"),
            () -> assertEquals(antes, despues,
                "La tabla pacientes cambió de tamaño tras los payloads SQL"));
    }

    private static String cuerpoLogin(String nombreUsuario)
    {
        return "{\"nombre_usuario\":\"" + escaparJson(nombreUsuario)
            + "\",\"password\":\"test\"}";
    }

    private static String rutaBusquedaRut(String payload)
    {
        String codificado = URLEncoder.encode(payload, StandardCharsets.UTF_8)
            .replace("+", "%20");

        return "/pacientes/buscar/rut/" + codificado;
    }

    private static String escaparJson(String valor)
    {
        return valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static boolean contieneErrorSql(String cuerpo)
    {
        return MARCAS_ERROR_SQL.stream().anyMatch(cuerpo::contains);
    }

    private static String contexto(Intento intento)
    {
        return "Payload=[" + intento.payload() + "] HTTP="
            + intento.respuesta().statusCode() + " Body=" + intento.respuesta().body();
    }

    private record Intento(String payload, HttpResponse<String> respuesta) {}
}
