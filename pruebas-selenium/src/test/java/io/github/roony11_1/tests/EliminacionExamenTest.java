package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.roony11_1.api.ApiClient;
import io.github.roony11_1.base.BaseTest;
import io.github.roony11_1.dao.DocumentoExamenDao;

/**
 * GEM-PF-003 · Verificación de eliminación lógica de exámenes.
 *
 * <p>Requerimientos: RF-3.3, RF-5.1. Incidente: INC-DIN-03.</p>
 *
 * <p>Pasos de la ficha: verificar en PostgreSQL el examen y su documento,
 * eliminarlo con {@code DELETE /examenes/:id} usando un token de administrador,
 * volver a consultar la base de datos y revisar {@code auditoria}.</p>
 *
 * <p>Comportamiento actual (documentado por estos tests): el endpoint ejecuta un
 * {@code DELETE} físico sobre {@code examen_medico}; la FK de
 * {@code documentos_examen} tiene {@code ON DELETE CASCADE}, así que el documento
 * también desaparece. La auditoría sí registra usuario, acción y fecha, pero solo
 * guarda el ID dentro del texto de la acción. Lo esperado por el ERS es un borrado
 * lógico: el examen se conserva con estado inactivo y sus documentos permanecen.</p>
 *
 * <p>El examen y el documento se crean con la API real para que cada ejecución sea
 * independiente de los IDs que existan en la base de datos de desarrollo.</p>
 */
public class EliminacionExamenTest extends BaseTest
{
    private static final long PACIENTE_ID = 1L;
    private static final long TIPO_EXAMEN_ID = 1L;
    private static final int TAMANO_PDF = 218;

    private final DocumentoExamenDao dao = new DocumentoExamenDao();

    @Test
    @DisplayName("GEM-PF-003 - El borrado actual es físico y en cascada (INC-DIN-03)")
    void eliminacionActualEsFisicaYEnCascada() throws Exception
    {
        TokenJWT token = login("admin", "admin");

        String diagnosis = "GEM-PF-003-" + System.nanoTime();
        long examenId = crearExamenConDocumento(token.get(), diagnosis);

        assertTrue(
            dao.existeExamen(examenId),
            "El examen de prueba no quedó registrado en PostgreSQL."
        );

        assertEquals(
            1,
            dao.contarDocumentos(examenId),
            "El documento de prueba no quedó registrado en PostgreSQL."
        );

        verificarVisibleEnListado(diagnosis);

        Set<Long> otrosExamenes = new HashSet<>(dao.idsExamenes());
        otrosExamenes.remove(examenId);

        long auditoriaPrevia = dao.ultimaEliminacion()
            .map(DocumentoExamenDao.Auditoria::id)
            .orElse(0L);

        HttpResponse<String> respuesta = api.delete(
            "/examenes/" + examenId, token.get());

        assertEquals(
            200,
            respuesta.statusCode(),
            "El DELETE debía responder 200. Body=" + respuesta.body()
        );

        assertTrue(
            respuesta.body().contains("\"deleted\":true"),
            "Se esperaba el cuerpo {\"deleted\":true}. Body=" + respuesta.body()
        );

        // INC-DIN-03: estos asserts describen el borrado FÍSICO actual. Cuando se
        // implemente el borrado lógico tendrán que invertirse (examen y documentos
        // conservados, pero ocultos en el listado).
        assertFalse(
            dao.existeExamen(examenId),
            "INC-DIN-03: el examen se conservó en la base de datos; "
                + "actualizar este test al borrado lógico."
        );

        assertEquals(
            0,
            dao.contarDocumentos(examenId),
            "INC-DIN-03: los documentos se conservaron; "
                + "actualizar este test al borrado lógico."
        );

        assertEquals(
            otrosExamenes,
            new HashSet<>(dao.idsExamenes()),
            "El DELETE afectó exámenes que no eran el objetivo."
        );

        DocumentoExamenDao.Auditoria auditoria = dao.ultimaEliminacion()
            .orElseThrow(() -> new AssertionError(
                "No se registró la eliminación en la tabla auditoria."));

        assertTrue(
            auditoria.id() > auditoriaPrevia,
            "No se insertó un registro nuevo de auditoría para la eliminación."
        );

        assertTrue(
            Pattern.compile("ID\\s+" + examenId + "\\b")
                .matcher(auditoria.accion())
                .find(),
            "La auditoría no identifica el examen eliminado. Acción="
                + auditoria.accion()
        );

        assertEquals(
            1L,
            auditoria.usuarioId(),
            "La auditoría debía registrar al administrador (usuario_id = 1)."
        );

        assertNotNull(
            auditoria.fechaHora(),
            "La auditoría no registró la fecha y hora de la eliminación."
        );

        verificarOcultoEnListado(diagnosis);
    }

    @Test
    @Disabled("INC-DIN-03: el endpoint borra físicamente el examen y sus documentos "
        + "en cascada. Habilitar cuando se implemente el borrado lógico del ERS.")
    @DisplayName("GEM-PF-003 - El borrado lógico conserva examen y documentos")
    void eliminacionLogicaConservaExamenYDocumentos() throws Exception
    {
        TokenJWT token = login("admin", "admin");

        String diagnosis = "GEM-PF-003-logico-" + System.nanoTime();
        long examenId = crearExamenConDocumento(token.get(), diagnosis);

        assertEquals(1, dao.contarDocumentos(examenId),
            "El escenario necesita un documento asociado al examen.");

        HttpResponse<String> respuesta = api.delete(
            "/examenes/" + examenId, token.get());

        assertEquals(200, respuesta.statusCode(),
            "El DELETE debía responder 200. Body=" + respuesta.body());

        assertTrue(
            dao.existeExamen(examenId),
            "El examen debe conservarse en la base de datos (borrado lógico)."
        );

        assertEquals(
            1,
            dao.contarDocumentos(examenId),
            "Los documentos del examen deben conservarse."
        );

        HttpResponse<String> listado = api.get("/examenes", token.get());

        assertEquals(200, listado.statusCode(),
            "No se pudo listar los exámenes. Body=" + listado.body());

        assertFalse(
            Pattern.compile("\"id\"\\s*:\\s*\"?" + examenId + "\"?\\b")
                .matcher(listado.body())
                .find(),
            "El examen eliminado lógicamente no debe aparecer en GET /examenes."
        );

        DocumentoExamenDao.Auditoria auditoria = dao.ultimaEliminacion()
            .orElseThrow(() -> new AssertionError(
                "No se registró la eliminación en la tabla auditoria."));

        assertEquals(1L, auditoria.usuarioId(),
            "La auditoría debía registrar al administrador (usuario_id = 1).");

        assertNotNull(auditoria.fechaHora(),
            "La auditoría no registró la fecha y hora de la eliminación.");
    }

    // ------------------------------------------------------------------
    // Escenario
    // ------------------------------------------------------------------

    /**
     * Crea el examen y sube su PDF usando el token de administrador, igual que
     * los pasos de la ficha. Devuelve el ID del examen.
     */
    private long crearExamenConDocumento(String token, String diagnosis)
        throws Exception
    {
        String json = String.format(
            "{\"tipo_examen_medico_id\":%d,\"paciente_id\":%d,\"diagnosis\":\"%s\"}",
            TIPO_EXAMEN_ID, PACIENTE_ID, diagnosis
        );

        HttpResponse<String> creado = api.postJson("/examenes", token, json);

        assertEquals(
            201,
            creado.statusCode(),
            "No se pudo crear el examen de prueba. Body=" + creado.body()
        );

        long examenId = ApiClient.extraerId(creado.body());

        Path pdf = crearArchivo(
            "gem-pf-003", "muestra.pdf", TAMANO_PDF, "%PDF-1.4\n%");

        HttpResponse<String> subido = api.postMultipartPdf(
            "/documentos",
            token,
            Map.of(
                "examen_medico_id", Long.toString(examenId),
                "paciente_id", Long.toString(PACIENTE_ID)
            ),
            pdf
        );

        assertEquals(
            201,
            subido.statusCode(),
            "No se pudo subir el documento de prueba. Body=" + subido.body()
        );

        return examenId;
    }

    /**
     * Comprueba que el examen aparece en el listado antes de eliminarlo. Sin esta
     * verificación, la ausencia posterior podría ser un falso positivo por una
     * tabla que todavía no terminó de cargar.
     */
    private void verificarVisibleEnListado(String diagnosis)
    {
        driver.get(BASE_URL + "/examenes");

        esperar(25).until(ExpectedConditions.textToBePresentInElementLocated(
            By.tagName("body"), diagnosis));
    }

    /**
     * Recarga el listado y espera a que la tabla tenga filas (GET /examenes tarda
     * 5 s por el delay artificial) antes de comprobar que el examen ya no está.
     */
    private void verificarOcultoEnListado(String diagnosis)
    {
        driver.get(BASE_URL + "/examenes");

        esperar(25).until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath("//table//tbody/tr")));

        assertFalse(
            driver.getPageSource().contains(diagnosis),
            "El examen eliminado sigue apareciendo en el listado del frontend."
        );
    }

    private WebDriverWait esperar(int segundos)
    {
        return new WebDriverWait(driver, Duration.ofSeconds(segundos));
    }
}
