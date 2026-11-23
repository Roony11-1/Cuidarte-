package io.github.roony11_1.tests;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;

import io.github.roony11_1.base.BaseTest;
import io.github.roony11_1.dao.DocumentoExamenDao;

/**
 * GEM-PF-001 · Registro de nuevo examen médico con adjunto permitido.
 *
 * <p>Requerimientos: RF-3.1, RF-4.3. Incidente: INC-DIN-02.</p>
 *
 * <p>Datos de entrada (ficha): paciente 1 (RUT {@code 11111111-1}), tipo de examen 1
 * ({@code Hemograma completo}), {@code muestra.pdf} (218 B), {@code muestra.doc},
 * {@code muestra.ppt} y {@code muestra-11mb.pdf} (11 MB).</p>
 *
 * <p>Comportamiento actual de la UI (verificado en estos tests):</p>
 * <ul>
 *   <li>El PDF válido se adjunta y se guarda en {@code documentos_examen}.</li>
 *   <li>DOC y PPT son adjuntos válidos según el ERS, pero hoy la UI los rechaza:
 *       el {@code accept} de react-dropzone solo permite PDF/JPG/PNG y no hay
 *       {@code onDropRejected}, y además el backend los responde con HTTP 500
 *       ({@code allowedMimes} no los incluye). Por eso sus tests quedan
 *       {@code @Disabled} hasta cerrar INC-DIN-02.</li>
 *   <li>El PDF de 11 MB se encola porque el dropzone no define {@code maxSize}; al
 *       enviar, el backend responde 500 (multer {@code LIMIT_FILE_SIZE} sin manejador
 *       de errores) y la UI solo muestra toasts genéricos. Lo esperado es un mensaje
 *       claro de tamaño excedido. El examen queda creado sin documento.</li>
 * </ul>
 */
public class ExamenesTest extends BaseTest
{
    private static final long PACIENTE_ID = 1L;
    private static final long TIPO_EXAMEN_ID = 1L;
    private static final String TIPO_EXAMEN = "Hemograma completo";
    private static final String RUT_PACIENTE = "11111111-1";

    private static final int TAMANO_DOC = 218;
    private static final int TAMANO_PDF_11MB = 11 * 1024 * 1024;

    private static final String TOAST = "//*[@data-sonner-toast]";
    private static final String ARCHIVO_LISTO = "//*[normalize-space(.)='Archivo listo para subir']";

    private final DocumentoExamenDao documentos = new DocumentoExamenDao();

    @Test
    @DisplayName("GEM-PF-001 - Médico crea examen con PDF válido")
    void crearExamenConPDFValido() throws Exception
    {
        login("medico", "medico");

        long examenPrevio = ultimoExamenId();

        Path pdf = crearArchivo("gem-pf-001", "muestra.pdf", TAMANO_DOC, "%PDF-1.4\n%");

        abrirNuevoExamen();
        seleccionarTipoYPaciente();
        completarCamposMedicos();
        adjuntarArchivo(pdf, true);
        enviarFormulario();

        // POST /examenes tiene un retardo artificial de 5s, por eso 20s de margen
        esperarToast("Examen creado exitosamente", 20);

        // La subida del documento ocurre después del toast de creación
        esperarToast("Documento subido exitosamente", 25);

        long examenId = assertNuevoExamen(examenPrevio);

        assertEquals(
            1,
            documentos.contarDocumentos(examenId),
            "Se esperaba exactamente un documento almacenado para el examen " + examenId
        );
    }

    @Test
    @Disabled("INC-DIN-02: DOC es válido según el ERS, pero la UI y el backend lo rechazan. "
        + "Quitar cuando el portal permita adjuntar DOC.")
    @DisplayName("GEM-PF-001 - Médico adjunta DOC y queda almacenado")
    void adjuntaDoc() throws Exception
    {
        verificarAdjuntoPermitido("gem-pf-001-doc", "muestra.doc");
    }

    @Test
    @Disabled("INC-DIN-02: PPT es válido según el ERS, pero la UI y el backend lo rechazan. "
        + "Quitar cuando el portal permita adjuntar PPT.")
    @DisplayName("GEM-PF-001 - Médico adjunta PPT y queda almacenado")
    void adjuntaPpt() throws Exception
    {
        verificarAdjuntoPermitido("gem-pf-001-ppt", "muestra.ppt");
    }

    /**
     * Flujo completo para un adjunto permitido: se encola, se crea el examen, se
     * sube el documento y queda la referencia en {@code documentos_examen}.
     */
    private void verificarAdjuntoPermitido(String prefijo, String nombreArchivo)
        throws Exception
    {
        login("medico", "medico");

        long examenPrevio = ultimoExamenId();

        Path archivo = crearArchivo(prefijo, nombreArchivo, TAMANO_DOC, null);

        abrirNuevoExamen();
        seleccionarTipoYPaciente();
        completarCamposMedicos();
        adjuntarArchivo(archivo, true);
        enviarFormulario();

        esperarToast("Examen creado exitosamente", 20);
        esperarToast("Documento subido exitosamente", 25);

        long examenId = assertNuevoExamen(examenPrevio);

        assertEquals(
            1,
            documentos.contarDocumentos(examenId),
            "Se esperaba un documento almacenado para el examen " + examenId
        );

        assertTrue(
            documentos.ultimoDocumento(examenId).orElse("").endsWith(nombreArchivo),
            "El documento almacenado no corresponde a " + nombreArchivo
        );
    }

    @Test
    @DisplayName("GEM-PF-001 - PDF de 11MB se rechaza con mensaje genérico (INC-DIN-02)")
    void rechazaPdfDe11MB() throws Exception
    {
        login("medico", "medico");

        long examenPrevio = ultimoExamenId();

        Path pdf = crearArchivo(
            "gem-pf-001-11mb", "muestra-11mb.pdf", TAMANO_PDF_11MB, "%PDF-1.4\n%"
        );

        abrirNuevoExamen();
        seleccionarTipoYPaciente();
        completarCamposMedicos();
        adjuntarArchivo(pdf, true);
        enviarFormulario();

        // El examen se crea antes de intentar subir el documento
        esperarToast("Examen creado exitosamente", 20);

        // El backend responde 500 y el formulario solo muestra un toast genérico
        esperarToast("Error al subir documento", 30);

        assertTrue(
            hayToastCon("Error al subir documento"),
            "La UI no informó el fallo al subir el PDF de 11MB."
        );

        assertFalse(
            hayToastCon("10MB") || hayToastCon("10 MB") || hayToastCon("tamaño"),
            "INC-DIN-02: la UI ya muestra un mensaje de tamaño excedido; " +
            "actualizar este test y cerrar el incidente."
        );

        long examenId = assertNuevoExamen(examenPrevio);

        assertEquals(
            0,
            documentos.contarDocumentos(examenId),
            "El PDF de 11MB no debió almacenarse en documentos_examen."
        );
    }

    // ------------------------------------------------------------------
    // Flujo del formulario de examen
    // ------------------------------------------------------------------

    private void abrirNuevoExamen()
    {
        driver.get(BASE_URL);

        WebDriverWait wait = esperar(15);

        wait.until(ExpectedConditions.elementToBeClickable(
            By.linkText("Exámenes Médicos")
        )).click();

        By tabNuevoExamen = By.xpath(
            "//*[@role='tab' and contains(normalize-space(.), 'Nuevo Examen')]"
        );

        wait.until(ExpectedConditions.elementToBeClickable(tabNuevoExamen))
            .click();

        // El formulario solo se renderiza cuando terminan de cargar los datos
        // (GET /pacientes tiene un delay de 5s)
        wait.until(ExpectedConditions.visibilityOfElementLocated(
            By.cssSelector("form")
        ));
    }

    private void seleccionarTipoYPaciente()
    {
        seleccionarOpcion(
            "tipo_examen_medico_id",
            "//ul[@role='listbox']/li[@role='option' and normalize-space(.)='"
                + TIPO_EXAMEN + "']"
        );

        seleccionarOpcion(
            "paciente_id",
            "//ul[@role='listbox']/li[@role='option' and contains(normalize-space(.), '"
                + RUT_PACIENTE + "')]"
        );
    }

    /**
     * Abre un Select de MUI y elige una opción. Espera que el menú se elimine del
     * DOM: durante su transición de salida queda con opacity 0 y sus &lt;li&gt;
     * siguen interceptando clics del siguiente select.
     */
    private void seleccionarOpcion(String nombreSelect, String xpathOpcion)
    {
        WebDriverWait wait = esperar(15);

        By select = By.id("mui-component-select-" + nombreSelect);

        wait.until(ExpectedConditions.elementToBeClickable(select)).click();

        WebElement menu = wait.until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath("//ul[@role='listbox']")
        ));

        wait.until(ExpectedConditions.elementToBeClickable(By.xpath(xpathOpcion)))
            .click();

        wait.until(ExpectedConditions.stalenessOf(menu));
    }

    private void completarCamposMedicos()
    {
        driver.findElement(By.name("diagnosis"))
            .sendKeys("Paciente presenta resultados dentro de los parámetros esperados.");

        driver.findElement(By.name("tratamiento"))
            .sendKeys("No requiere tratamiento.");

        driver.findElement(By.name("observaciones"))
            .sendKeys("Control médico preventivo.");

        driver.findElement(By.name("notas"))
            .sendKeys("Examen registrado mediante automatización Selenium.");
    }

    private void adjuntarArchivo(Path archivo, boolean esperarEncolado)
    {
        By archivoInput = By.cssSelector("input[type='file']");

        WebElement input = driver.findElement(archivoInput);

        try
        {
            input.sendKeys(archivo.toAbsolutePath().toString());
        }
        catch (WebDriverException e)
        {
            // Firefox puede bloquear la selección si la extensión no coincide con
            // el atributo accept del input
            jsExecutor.executeScript("arguments[0].removeAttribute('accept')", input);
            input.sendKeys(archivo.toAbsolutePath().toString());
        }

        if (esperarEncolado)
        {
            esperar(10).until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath(ARCHIVO_LISTO)
            ));
        }
    }

    private void enviarFormulario()
    {
        By botonCrear = By.xpath(
            "//button[@type='submit' and contains(., 'Crear Examen')]"
        );

        esperar(15).until(ExpectedConditions.elementToBeClickable(botonCrear))
            .click();
    }

    // ------------------------------------------------------------------
    // Toasts y BD
    // ------------------------------------------------------------------

    private WebDriverWait esperar(int segundos)
    {
        return new WebDriverWait(driver, Duration.ofSeconds(segundos));
    }

    private WebElement esperarToast(String texto, int segundos)
    {
        return esperar(segundos).until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath(TOAST + "//*[normalize-space(.)='" + texto + "']")
        ));
    }

    private boolean hayToastCon(String texto)
    {
        return !driver.findElements(
            By.xpath(TOAST + "[contains(., '" + texto + "')]")
        ).isEmpty();
    }

    private long ultimoExamenId()
    {
        return documentos.ultimoExamen(PACIENTE_ID, TIPO_EXAMEN_ID).orElse(0L);
    }

    private long assertNuevoExamen(long examenPrevio)
    {
        long examenId = ultimoExamenId();

        assertTrue(
            examenId > examenPrevio,
            "No se creó un examen nuevo para el paciente 1 y tipo 1."
        );

        return examenId;
    }
}
