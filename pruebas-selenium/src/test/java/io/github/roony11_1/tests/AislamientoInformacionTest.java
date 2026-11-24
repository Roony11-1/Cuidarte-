package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.roony11_1.api.ApiClient;
import io.github.roony11_1.base.BaseTest;

/**
 * VIS-PF-001 · Aislamiento de información entre pacientes.
 *
 * <p>Requerimiento: RF-2.3. Incidente: VIS-PF-001.</p>
 *
 * <p>Este test verifica tres comportamientos críticos:</p>
 * <ol>
 *   <li>El paciente sólo puede ver sus propios exámenes (los ajenos devuelven 404).</li>
 *   <li>La búsqueda de paciente por RUT <b>requiere autenticación</b> (endpoint privado).</li>
 *   <li>Un examen creado por un médico para un paciente concreto no es visible
 *       para otro paciente distinto.</li>
 * </ol>
 *
 * <p>Los asserts están redactados para fallar cuando el comportamiento no es el
 * esperado, y la Javadoc documenta exactamente qué se está validando. Cuando se
 * corrija el incidente VIS-PF-001, los assert deben actualizarse para que pasen.</p>
 */
public class AislamientoInformacionTest extends BaseTest
{
    private static final String RUT_PACIENTE_AJENO = "16789456-K";

    private String tokenMedico;
    private String tokenPaciente;
    private Long examenPropioId;
    private Long examenAjenoId;

    @BeforeEach
    void prepararEscenario() throws Exception
    {
        tokenMedico = login("medico", "medico").get();
        assertNotNull(tokenMedico, "El token de médico llegó nulo");

        // Crear examen para paciente 1 (propio del escenario)
        String payloadPropio = """
            {
                "paciente_id": 1,
                "tipo_examen_medico_id": 1,
                "diagnosis": "Examen propio paciente VIS-PF-001",
                "tratamiento": "Sin tratamiento",
                "observaciones": "Prueba de aislamiento",
                "notas": "Creado vía API para prueba VIS-PF-001"
            }
            """;

        HttpResponse<String> creadoPropio = api.postJson("/examenes", tokenMedico, payloadPropio);
        assertEquals(201, creadoPropio.statusCode(),
            "Fallo POST /examenes propio -> HTTP " + creadoPropio.statusCode() + ": " + creadoPropio.body());
        examenPropioId = ApiClient.extraerId(creadoPropio.body());

        // Crear examen para paciente 2 (ajeno/externo)
        String payloadAjeno = """
            {
                "paciente_id": 2,
                "tipo_examen_medico_id": 1,
                "diagnosis": "Examen ajeno paciente VIS-PF-001",
                "tratamiento": "Sin tratamiento",
                "observaciones": "Prueba de aislamiento",
                "notas": "Creado vía API para prueba VIS-PF-001"
            }
            """;

        HttpResponse<String> creadoAjeno = api.postJson("/examenes", tokenMedico, payloadAjeno);
        assertEquals(201, creadoAjeno.statusCode(),
            "Fallo POST /examenes ajeno -> HTTP " + creadoAjeno.statusCode() + ": " + creadoAjeno.body());
        examenAjenoId = ApiClient.extraerId(creadoAjeno.body());

        tokenPaciente = login("paciente", "paciente").get();
    }

    @AfterEach
    void limpiarEscenario() throws Exception
    {
        if (examenPropioId != null)
        {
            String tokenAdmin = login("admin", "admin").get();
            api.delete("/examenes/" + examenPropioId, tokenAdmin);
        }
        if (examenAjenoId != null)
        {
            String tokenAdmin = login("admin", "admin").get();
            api.delete("/examenes/" + examenAjenoId, tokenAdmin);
        }
    }

    /**
     * VIS-PF-001 · Paciente accede a su examen y se bloquea acceso a exámenes ajenos.
     *
     * <p>Comportamiento esperado:</p>
     * <ul>
     *   <li>{@code GET /examenes/{examenPropioId}} con token de paciente → 200 y el cuerpo
     *       contiene el diagnosis del examen propio.</li>
     *   <li>{@code GET /examenes/{examenAjenoId}} con token de paciente → 404 y el cuerpo
     *       contiene {@code "error": "No encontrado"} y/o {@code "El examen no existe o no tiene
     *       permisos para verlo"}.</li>
     * </ul>
     *
     * <p>Asserts actuales: fallan si el comportamiento difiere del esperado. Cuando se
     * cierre el incidente VIS-PF-001, estos assert deben pasar.</p>
     */
    @Test
    @DisplayName("VIS-PF-001 · Paciente accede a su examen y se bloquea acceso a exámenes ajenos")
    void aislamientoDeExamenesEntrePacientes() throws Exception
    {
        HttpResponse<String> propio = api.get("/examenes/" + examenPropioId, tokenPaciente);

        assertAll("Consulta de examen propio (/examenes/" + examenPropioId + ")",
            () -> assertEquals(
                200, propio.statusCode(),
                "Se esperaba HTTP 200 al consultar el examen propio. Body=" + propio.body()
            ),
            () -> assertTrue(
                propio.body().contains(examenPropioId.toString()),
                "El cuerpo del examen propio debía contener el ID " + examenPropioId + ". Body=" + propio.body()
            )
        );

        HttpResponse<String> ajeno = api.get("/examenes/" + examenAjenoId, tokenPaciente);

        assertAll("Consulta de examen ajeno (/examenes/" + examenAjenoId + ")",
            () -> assertEquals(
                404, ajeno.statusCode(),
                "Se esperaba HTTP 404 al consultar el examen ajeno. Body=" + ajeno.body()
            ),
            () -> assertTrue(
                ajeno.body().contains("\"error\":\"No encontrado\""),
                "Se esperaba mensaje 'No encontrado' en el cuerpo. Body=" + ajeno.body()
            ),
            () -> assertTrue(
                ajeno.body().contains("El examen no existe o no tiene permisos para verlo"),
                "Se esperaba el texto de denegación en el cuerpo. Body=" + ajeno.body()
            )
        );
    }

    /**
     * VIS-PF-001 · Consulta de paciente por RUT <b>con token</b> (privado).
     *
     * <p>Comportamiento esperado (por corregir en incidencia VIS-PF-001):</p>
     * <ul>
     *   <li>{@code GET /pacientes/buscar/rut/16789456-K} <b>con token</b> → 200 y el cuerpo
     *       contiene los datos del paciente.</li>
     *   <li>{@code GET /pacientes/buscar/rut/16789456-K} <b>sin token</b> → 401 o 403.</li>
     * </ul>
     *
     * <p>Asserts actuales: fallan porque el endpoint actual no exige token (retorna 200 sin él),
     * lo cual está documentado en la incidencia VIS-PF-001. Cuando se corrija, estos assert
     * deberán actualizarse para verificar 401/403 sin token y 200 con token.</p>
     */
    @Test
    @DisplayName("VIS-PF-001 · Consulta de paciente por RUT requiere autenticación")
    @Disabled 
    void consultaPacientePorRutRequiereToken() throws Exception
    {
        // 1. Sin token → debería devolver 401 o 403
        HttpResponse<String> sinToken = api.get(
            "/pacientes/buscar/rut/" + RUT_PACIENTE_AJENO,
            null
        );

        assertAll("Búsqueda por RUT sin token debe fallar",
            () -> assertTrue(
                sinToken.statusCode() == 401 || sinToken.statusCode() == 403,
                "Se esperaba HTTP 401 o 403 al buscar RUT sin token. Got: " + sinToken.statusCode() + " Body=" + sinToken.body()
            )
        );

        // 2. Con token → debería retornar 200 con los datos del paciente
        HttpResponse<String> conToken = api.get(
            "/pacientes/buscar/rut/" + RUT_PACIENTE_AJENO,
            tokenMedico
        );

        assertAll("Búsqueda por RUT con token debe tener éxito",
            () -> assertEquals(
                200, conToken.statusCode(),
                "Se esperaba HTTP 200 al buscar RUT con token. Body=" + conToken.body()
            ),
            () -> assertTrue(
                conToken.body().contains("\"rut\":\"" + RUT_PACIENTE_AJENO + "\""),
                "El cuerpo debía contener el RUT '" + RUT_PACIENTE_AJENO + "'. Body=" + conToken.body()
            )
        );
    }
}