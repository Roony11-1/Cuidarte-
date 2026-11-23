package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.roony11_1.base.BaseTest;

/**
 * GUS-PF-003 · Prevención de acceso de Pacientes a listados de administración.
 *
 * <p>Requerimientos: RF-2.5, RB-1, NFR-SEG-4.</p>
 *
 * <p>Verifica que un paciente con JWT válido <b>no</b> puede obtener el listado
 * de usuarios vía {@code GET /usuarios}, y que un administrador <b>sí</b> puede
 * (control positivo). Además de las pruebas en UI</p>
 *
 *
 */
public class PruebaRBAC extends BaseTest
{
    /**
     * AUT de seguridad: paciente con token válido <b>no</b> debe listar usuarios.
     *
     * <p>Esperado: {@code 403} + {@code "No autorizado"}.</p>
     */
    @Test 
    @DisplayName("GUS-PF-003 - Paciente NO accede al listado de usuarios(403)")
    void pacienteNoDeberiaAccederAlListado() throws Exception
    {
        TokenJWT token = login("paciente", "paciente");

        HttpResponse<String> resp = get("/usuarios", token.get());

        assertEquals(403, resp.statusCode(),
            "Un paciente debería recibir 403. Body= " + resp.body());

        assertTrue(resp.body().toLowerCase().contains("no autorizado"),
            "Se esperaba mensaje de denegación. Body= " + resp.body());

        // Prueba visual
        driver.get(BASE_URL + "/usuarios");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
        By.tagName("body"), "No autorizado"));

        assertFalse(driver.getPageSource().contains("admin@cuidarteplus.com"),
                "El listado de usuarios se renderizó a pesar del guard RequireAdmin");
    }

    /**
     * AUT de control positivo: admin autenticado <b>sí</b> debe listar usuarios.
     *
     * <p>Garantiza que el 403 del test anterior no es un falso positivo por un endpoint roto.</p>
     */
    @Test
    @DisplayName("GUS-PF-003 . Admin SÍ debe acceder al listado de usuarios")
    void adminDeberiaAccederAlListado() throws Exception
    {
        TokenJWT token = login("admin", "admin");

        HttpResponse<String> resp = get("/usuarios", token.get());

        assertEquals(200, resp.statusCode(),
            "Admin debería recibir 200. Body= " + resp.body());

        assertFalse(resp.body().toLowerCase().contains("no autorizado"),
            "Admin no debería recibir denegación. Body=" + resp.body());

        assertTrue(resp.body().contains("\"nombre_usuario\""),
            "Se esperaba listado de usuarios en el body. Body=" + resp.body());

        // prueba visual
        driver.get(BASE_URL + "/usuarios");

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        wait.until(ExpectedConditions.textToBePresentInElementLocated(
                By.tagName("body"), "Gestión de Usuarios"));

        assertFalse(driver.getPageSource().contains("No autorizado"),
                "El guard mostró 'No autorizado' a un admin (debería permitir el acceso)");
    }
}
