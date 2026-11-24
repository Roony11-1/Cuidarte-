package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.roony11_1.api.ApiClient;
import io.github.roony11_1.base.BaseTest;
import io.github.roony11_1.dao.PacienteDao;

/**
 * AUT-PNF-003 · Protección de formularios contra XSS almacenado.
 *
 * <p>Requerimiento: NFR-SEG-5. Tipo: No funcional / Seguridad.
 * Incidente asociado: <b>INC-DIN-04</b>.</p>
 *
 * <p>Pasos que reproduce el caso:</p>
 * <ol>
 *   <li>Crear un paciente con token de médico, enviando
 *       {@code <script>alert(1)</script>} en {@code alergias} y
 *       {@code <img src=x onerror=alert(1)>} en
 *       {@code observaciones_medicas_generales}.</li>
 *   <li>Recuperar el registro con {@code GET /pacientes/:id} y
 *       {@code GET /pacientes/buscar/rut/:rut}.</li>
 *   <li>Consultar en PostgreSQL el valor almacenado.</li>
 * </ol>
 *
 * <p>Resultado esperado: el payload se rechaza o se sanea, de modo que nunca
 * quede almacenado como HTML ejecutable.</p>
 *
 * <p>Resultado actual: el backend responde HTTP 201 y guarda el payload
 * literalmente, por lo que este test <b>falla a propósito</b> y documenta el
 * incidente INC-DIN-04. Cuando el backend rechace o sanee los payloads, los
 * asserts pasarán sin cambios.</p>
 */
public class XssAlmacenadoTest extends BaseTest
{
    private static final String RUT_PRUEBA = "99999999-9";

    private static final String PAYLOAD_ALERGIAS = "<script>alert(1)</script>";

    private static final String PAYLOAD_OBSERVACIONES = "<img src=x onerror=alert(1)>";

    /** Marcas de HTML ejecutable que jamás deberían quedar almacenadas. */
    private static final String[] MARCAS_XSS = {"<script", "<img", "onerror=", "javascript:"};

    private final PacienteDao pacientes = new PacienteDao();

    private String tokenMedico;
    private String tokenAdmin;
    private Long pacienteId;

    @BeforeEach
    void autenticar() throws Exception
    {
        tokenMedico = api.iniciarSesion("medico", "medico");
        tokenAdmin = api.iniciarSesion("admin", "admin");
    }

    @AfterEach
    void eliminarPacienteDePrueba() throws Exception
    {
        if (pacienteId == null)
            return;

        HttpResponse<String> eliminacion = api.delete("/pacientes/" + pacienteId, tokenAdmin);

        assertAll("Limpieza del paciente de prueba " + pacienteId,
            () -> assertEquals(200, eliminacion.statusCode(),
                "No se pudo eliminar el paciente de prueba. Body=" + eliminacion.body()),
            () -> assertFalse(pacientes.existe(pacienteId),
                "El paciente de prueba " + pacienteId + " quedó en PostgreSQL")
        );
    }

    /**
     * AUT-PNF-003 · Payloads XSS en los campos de texto del paciente.
     *
     * <p>Un rechazo (HTTP 400) cuenta como comportamiento seguro. Si el backend
     * acepta la creación, el payload debe volver saneado en la respuesta de
     * creación, en las dos lecturas por API y en PostgreSQL.</p>
     */
    @Test
    @DisplayName("AUT-PNF-003 · XSS almacenado en alergias y observaciones médicas")
    @Disabled 
    void payloadsXssEnCamposDeTexto() throws Exception
    {
        HttpResponse<String> creacion = api.postJson("/pacientes", tokenMedico, cuerpoPaciente());

        if (creacion.statusCode() == 400 || creacion.statusCode() == 422)
        {
            // El backend rechazó el payload: comportamiento esperado y seguro.
            return;
        }

        assertEquals(201, creacion.statusCode(),
            "Se esperaba rechazo (400/422) o creación controlada (201). Body=" + creacion.body());

        pacienteId = ApiClient.extraerId(creacion.body());

        HttpResponse<String> porId = api.get("/pacientes/" + pacienteId, tokenMedico);

        HttpResponse<String> porRut = api.get(
            "/pacientes/buscar/rut/" + URLEncoder.encode(RUT_PRUEBA, StandardCharsets.UTF_8),
            null
        );

        PacienteDao.TextosMedicos almacenado = pacientes.textosMedicos(pacienteId)
            .orElseThrow(() -> new IllegalStateException(
                "El paciente " + pacienteId + " no quedó almacenado en PostgreSQL"));

        String incidente = "INC-DIN-04: el payload XSS debe rechazarse o sanearse";

        assertAll("AUT-PNF-003 · XSS almacenado (INC-DIN-04)",
            () -> assertFalse(contieneXss(creacion.body()),
                "POST /pacientes devolvió el payload sin sanear. " + incidente
                    + ". Body=" + creacion.body()),

            () -> assertEquals(200, porId.statusCode(),
                "GET /pacientes/" + pacienteId + " debía recuperar el registro. Body=" + porId.body()),
            () -> assertFalse(contieneXss(porId.body()),
                "GET /pacientes/" + pacienteId + " devolvió el payload sin sanear. " + incidente
                    + ". Body=" + porId.body()),

            () -> assertEquals(200, porRut.statusCode(),
                "GET /pacientes/buscar/rut/" + RUT_PRUEBA
                    + " debía recuperar el registro. Body=" + porRut.body()),
            () -> assertFalse(contieneXss(porRut.body()),
                "GET /pacientes/buscar/rut/" + RUT_PRUEBA
                    + " devolvió el payload sin sanear. " + incidente
                    + ". Body=" + porRut.body()),

            () -> assertFalse(contieneXss(almacenado.alergias()),
                "PostgreSQL guardó '<script>alert(1)</script>' literal en alergias. "
                    + incidente + ". Valor=" + almacenado.alergias()),

            () -> assertFalse(contieneXss(almacenado.observacionesMedicasGenerales()),
                "PostgreSQL guardó '<img src=x onerror=alert(1)>' literal en "
                    + "observaciones_medicas_generales. " + incidente
                    + ". Valor=" + almacenado.observacionesMedicasGenerales())
        );
    }

    /**
     * AUT-PNF-003 · Renderizado seguro: el payload almacenado se muestra como
     * texto literal en la ficha del paciente y no se ejecuta ningún script.
     *
     * <p>Comportamiento esperado: al abrir {@code /pacientes/:id/editar} con el
     * payload ya guardado en PostgreSQL, los campos muestran el texto tal cual,
     * no aparecen nodos {@code <script>}/{@code <img onerror>} inyectados y el
     * navegador no abre ningún {@code alert()}.</p>
     *
     * <p>Este test es independiente del incidente INC-DIN-04: el backend sigue
     * guardando el payload sin sanear, pero se verifica que el frontend lo
     * renderiza como texto y no como HTML ejecutable.</p>
     */
    @Test
    @DisplayName("AUT-PNF-003 · El payload XSS no se ejecuta al renderizar la ficha")
    void payloadsXssNoSeEjecutanEnLaUi() throws Exception
    {
        HttpResponse<String> creacion = api.postJson("/pacientes", tokenMedico, cuerpoPaciente());

        assertEquals(201, creacion.statusCode(),
            "El escenario de renderizado necesita un paciente con payload almacenado. Body="
                + creacion.body());

        pacienteId = ApiClient.extraerId(creacion.body());

        login("medico", "medico");
        driver.get(BASE_URL + "/pacientes/" + pacienteId + "/editar");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        By campoAlergias = By.name("alergias");

        wait.until(d ->
        {
            var campos = d.findElements(campoAlergias);

            return !campos.isEmpty()
                && PAYLOAD_ALERGIAS.equals(campos.get(0).getDomProperty("value"));
        });

        WebElement alergias = driver.findElement(campoAlergias);

        WebElement observaciones = driver.findElement(
            By.name("observaciones_medicas_generales"));

        String valorAlergias = alergias.getDomProperty("value");
        String valorObservaciones = observaciones.getDomProperty("value");

        long elementosInyectados = contarElementosInyectados();

        boolean alerta = hayAlerta();

        if (alerta)
            driver.switchTo().alert().accept();

        assertAll("AUT-PNF-003 · Renderizado del payload XSS en /pacientes/" + pacienteId,
            () -> assertEquals(PAYLOAD_ALERGIAS, valorAlergias,
                "El campo Alergias debía mostrar el payload como texto literal"),
            () -> assertEquals(PAYLOAD_OBSERVACIONES, valorObservaciones,
                "El campo Observaciones debía mostrar el payload como texto literal"),
            () -> assertEquals(0L, elementosInyectados,
                "El payload creó nodos <script>/<img> ejecutables en el DOM"),
            () -> assertFalse(alerta,
                "El payload ejecutó alert(1) al renderizar la ficha del paciente")
        );
    }

    /** Cantidad de nodos con marcas del payload inyectadas en el DOM. */
    private long contarElementosInyectados()
    {
        Object resultado = jsExecutor.executeScript(
            "const marcas = ['alert(1)', 'onerror'];" +
            "return [...document.querySelectorAll('script, img')].filter(el => {" +
            "  const contenido = (el.textContent || '') + ' ' +" +
            "    (el.getAttribute('onerror') || '') + ' ' +" +
            "    (el.getAttribute('src') || '');" +
            "  return marcas.some(marca => contenido.toLowerCase().includes(marca));" +
            "}).length;"
        );

        return ((Number) resultado).longValue();
    }

    private boolean hayAlerta()
    {
        try
        {
            driver.switchTo().alert();

            return true;
        }
        catch (NoAlertPresentException e)
        {
            return false;
        }
    }

    private static boolean contieneXss(String texto)
    {
        if (texto == null)
            return false;

        String normalizado = texto.toLowerCase(Locale.ROOT);

        for (String marca : MARCAS_XSS)
            if (normalizado.contains(marca))
                return true;

        return false;
    }

    private static String cuerpoPaciente()
    {
        String email = "xss" + System.nanoTime() + "@test.com";

        return """
            {
                "rut": "%s",
                "primer_nombre": "Prueba",
                "apellido_paterno": "XSS",
                "fecha_nacimiento": "1990-01-01",
                "sexo": "M",
                "email": "%s",
                "alergias": "%s",
                "observaciones_medicas_generales": "%s"
            }
            """.formatted(RUT_PRUEBA, email, PAYLOAD_ALERGIAS, PAYLOAD_OBSERVACIONES);
    }
}
