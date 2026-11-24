package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.roony11_1.base.BaseTest;

/**
 * GUS-PNF-003 · Verificación de flujo con teclado y legibilidad para adultos mayores.
 *
 * <p>Requerimiento: NFR-USAB-1. Tipo: No funcional / Usabilidad y accesibilidad.
 * Viewport: 1366x768.</p>
 *
 * <p>Parte automatizada (este test) sobre login, listado de pacientes y
 * formulario de examen:</p>
 * <ul>
 *   <li>Recorrido del flujo solo con teclado (Tab, Shift+Tab, Enter, Espacio y
 *       flechas para el patrón ARIA de pestañas).</li>
 *   <li>Orden lógico de tabulación en cada pantalla.</li>
 *   <li>Foco visible: cada elemento alcanzado con Tab cumple {@code :focus-visible}.</li>
 *   <li>El login se completa y envía con teclado.</li>
 * </ul>
 *
 * <p><b>Checklist manual (no automatizado en este test):</b></p>
 * <ul>
 *   <li><b>Contraste</b>: razón mínima 4.5:1 para texto normal y 3:1 para texto
 *       grande (WCAG 2.1 AA), medido con Colour Contrast Analyser o axe DevTools.</li>
 *   <li><b>Tamaño de fuente</b>: cuerpo legible para adultos mayores (≥16 px sin
 *       zoom), textos de ayuda ≥14 px y sin pérdida de contenido al 200 % de zoom.</li>
 *   <li><b>Legibilidad</b>: claridad del contenido, mensajes de error y textos de
 *       ayuda evaluados por una persona.</li>
 *   <li><b>Tamaño de objetivos</b>: controles de al menos 24x24 px (WCAG 2.2 AA)
 *       y, recomendado para adultos mayores, 44x44 px.</li>
 *   <li><b>Foco perceptible</b>: el indicador visual (anillo, borde o subrayado)
 *       debe ser claramente visible, no solo existir en el DOM.</li>
 * </ul>
 */
public class UsabilidadTecladoTest extends BaseTest
{
    private static final int MAX_TABS = 80;

    @BeforeEach
    void configurarViewport()
    {
        driver.manage().window().setSize(new Dimension(1366, 768));
    }

    /**
     * GUS-PNF-003 · El login se completa solo con teclado: Tab entre campos,
     * Shift+Tab para volver, Enter para enviar.
     */
    @Test
    @DisplayName("GUS-PNF-003 · Login completable solo con teclado")
    void loginSoloConTeclado()
    {
        driver.get(BASE_URL + "/login");

        new WebDriverWait(driver, Duration.ofSeconds(15)).until(
            ExpectedConditions.visibilityOfElementLocated(By.id("nombre_usuario")));

        WebElement usuario = tabularHasta("campo 'Nombre de usuario'",
            el -> "nombre_usuario".equals(el.getAttribute("id")), 15);

        usuario.sendKeys("medico");

        WebElement password = tabularHasta("campo 'Contraseña'",
            el -> "password".equals(el.getAttribute("id")), 5);

        password.sendKeys("medico");

        // Shift+Tab debe devolver el foco al campo anterior.
        pulsarShiftTab();

        assertEquals("nombre_usuario", driver.switchTo().activeElement().getAttribute("id"),
            "Shift+Tab debía volver al campo 'Nombre de usuario'");

        pulsar(Keys.TAB);

        assertEquals("password", driver.switchTo().activeElement().getAttribute("id"),
            "Tab debía volver al campo 'Contraseña'");

        WebElement boton = tabularHasta("botón 'Entrar'",
            el -> "button-login".equals(el.getAttribute("data-testid")), 5);

        boton.sendKeys(Keys.ENTER);

        new WebDriverWait(driver, Duration.ofSeconds(15)).until(
            d -> d.getCurrentUrl().contains("/home"));

        String token = (String) jsExecutor.executeScript(
            "return localStorage.getItem('token')");

        assertNotNull(token, "El login enviado con Enter no almacenó el token");
    }

    /**
     * GUS-PNF-003 · El listado de pacientes y su pestaña de registro se recorren
     * solo con teclado, y las acciones de la tabla son alcanzables con Tab.
     */
    @Test
    @DisplayName("GUS-PNF-003 · Listado de pacientes navegable solo con teclado")
    void listadoPacientesNavegableConTeclado()
    {
        login("medico", "medico");

        driver.get(BASE_URL + "/pacientes");

        new WebDriverWait(driver, Duration.ofSeconds(25)).until(
            ExpectedConditions.elementToBeClickable(tab("Listar Pacientes")));

        WebElement tabListar = tabularHasta("pestaña 'Listar Pacientes'",
            el -> esTab(el, "Listar Pacientes"), MAX_TABS);

        assertEquals("true", tabListar.getAttribute("aria-selected"),
            "La pestaña 'Listar Pacientes' debía estar seleccionada");

        // Patrón ARIA de pestañas: las flechas mueven el foco, Enter/Espacio activan.
        pulsar(Keys.ARROW_RIGHT);

        assertTrue(esTab(driver.switchTo().activeElement(), "Registrar Paciente"),
            "La flecha derecha debía mover el foco a 'Registrar Paciente'. Activo: "
                + describirActivo());

        WebElement tabRegistrar = driver.switchTo().activeElement();

        // Espacio también debe activar el control enfocado (patrón de botones).
        tabRegistrar.sendKeys(Keys.SPACE);

        new WebDriverWait(driver, Duration.ofSeconds(15)).until(
            ExpectedConditions.visibilityOfElementLocated(By.name("rut")));

        WebElement rut = tabularHasta("campo 'RUT' del formulario de paciente",
            el -> "rut".equals(el.getAttribute("name")), 10);

        assertTrue(rut.isDisplayed(), "El campo RUT debía ser visible");

        pulsarShiftTab();

        assertTrue(esTab(driver.switchTo().activeElement(), "Registrar Paciente"),
            "Shift+Tab debía volver a la pestaña 'Registrar Paciente'. Activo: "
                + describirActivo());

        // Volver al listado con flecha izquierda + Enter.
        pulsar(Keys.ARROW_LEFT);
        driver.switchTo().activeElement().sendKeys(Keys.ENTER);

        new WebDriverWait(driver, Duration.ofSeconds(15)).until(
            ExpectedConditions.visibilityOfElementLocated(By.cssSelector("table")));

        WebElement accion = tabularHasta("botón de edición de un paciente",
            el ->
            {
                String testId = el.getAttribute("data-testid");

                return testId != null && testId.startsWith("button-cliente-editar-");
            }, MAX_TABS);

        assertTrue(accion.isDisplayed(),
            "Las acciones de la tabla debían ser alcanzables con Tab");
    }

    /**
     * GUS-PNF-003 · El formulario de examen respeta un orden lógico de tabulación
     * desde los selectores hasta el botón de envío, incluida el área de adjuntos.
     */
    @Test
    @DisplayName("GUS-PNF-003 · Formulario de examen navegable solo con teclado")
    void formularioExamenNavegableConTeclado()
    {
        login("medico", "medico");

        driver.get(BASE_URL + "/examenes");

        new WebDriverWait(driver, Duration.ofSeconds(25)).until(
            ExpectedConditions.elementToBeClickable(tab("Listar Exámenes")));

        tabularHasta("pestaña 'Listar Exámenes'",
            el -> esTab(el, "Listar Exámenes"), MAX_TABS);

        pulsar(Keys.ARROW_RIGHT);

        assertTrue(esTab(driver.switchTo().activeElement(), "Nuevo Examen"),
            "La flecha derecha debía mover el foco a 'Nuevo Examen'. Activo: "
                + describirActivo());

        driver.switchTo().activeElement().sendKeys(Keys.ENTER);

        // GET /pacientes y GET /tipo-examen demoran por el delay artificial.
        new WebDriverWait(driver, Duration.ofSeconds(30)).until(
            ExpectedConditions.visibilityOfElementLocated(
                By.id("mui-component-select-tipo_examen_medico_id")));

        tabularHasta("selector 'Tipo de Examen'",
            el -> "mui-component-select-tipo_examen_medico_id".equals(
                el.getAttribute("id")), 10);

        tabularHasta("selector 'Paciente'",
            el -> "mui-component-select-paciente_id".equals(
                el.getAttribute("id")), 5);

        tabularHasta("campo 'Diagnóstico'",
            el -> "diagnosis".equals(el.getAttribute("name")), 5);

        tabularHasta("campo 'Tratamiento'",
            el -> "tratamiento".equals(el.getAttribute("name")), 5);

        tabularHasta("campo 'Observaciones'",
            el -> "observaciones".equals(el.getAttribute("name")), 5);

        tabularHasta("campo 'Notas'",
            el -> "notas".equals(el.getAttribute("name")), 5);

        WebElement dropzone = tabularHasta("zona de carga de documentos",
            el -> el.getText().contains("Arrastra un archivo aquí"), 5);

        assertEquals("0", dropzone.getAttribute("tabindex"),
            "La zona de carga debía ser alcanzable con teclado (tabindex=0)");

        WebElement boton = tabularHasta("botón 'Crear Examen'",
            el -> "button".equals(el.getTagName())
                && el.getText().contains("Crear Examen"), 5);

        assertTrue(boton.isDisplayed(), "El botón 'Crear Examen' debía ser visible");
    }

    // ------------------------------------------------------------------
    // Navegación con teclado
    // ------------------------------------------------------------------

    /**
     * Avanza con Tab hasta que el elemento activo cumpla la condición. Exige que
     * el foco sea visible ({@code :focus-visible}) en cada parada.
     */
    private WebElement tabularHasta(
        String descripcion, Predicate<WebElement> condicion, int maxTabs)
    {
        for (int intento = 0; intento < maxTabs; intento++)
        {
            pulsar(Keys.TAB);

            WebElement activo = driver.switchTo().activeElement();

            if (!condicion.test(activo))
                continue;

            assertTrue(focoVisible(),
                "El foco de " + descripcion + " no es visible (:focus-visible). Activo: "
                    + describirActivo());

            return activo;
        }

        throw new AssertionError("No se alcanzó " + descripcion + " con Tab en "
            + maxTabs + " intentos. Activo: " + describirActivo());
    }

    private void pulsar(CharSequence tecla)
    {
        new Actions(driver).sendKeys(tecla).perform();
    }

    private void pulsarShiftTab()
    {
        new Actions(driver)
            .keyDown(Keys.SHIFT)
            .sendKeys(Keys.TAB)
            .keyUp(Keys.SHIFT)
            .perform();
    }

    private boolean focoVisible()
    {
        return Boolean.TRUE.equals(jsExecutor.executeScript(
            "return document.activeElement.matches(':focus-visible')"));
    }

    private static By tab(String etiqueta)
    {
        return By.xpath("//*[@role='tab' and contains(normalize-space(.), '"
            + etiqueta + "')]");
    }

    private static boolean esTab(WebElement elemento, String etiqueta)
    {
        String texto = elemento.getText();

        return "tab".equals(elemento.getAttribute("role"))
            && texto != null
            && texto.toLowerCase().contains(etiqueta.toLowerCase());
    }

    private String describirActivo()
    {
        WebElement activo = driver.switchTo().activeElement();

        return activo.getTagName()
            + "[id=" + activo.getAttribute("id")
            + ", name=" + activo.getAttribute("name")
            + ", testid=" + activo.getAttribute("data-testid")
            + ", texto=" + activo.getText() + "]";
    }
}
