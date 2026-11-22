package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import io.github.roony11_1.base.BaseTest;
import io.github.roony11_1.dao.UsuarioDao;

public class RegistroTest extends BaseTest
{
    private final UsuarioDao usuarios = new UsuarioDao();

    @Test
    @DisplayName("Registro de un nuevo usuario Paciente")
    void registro()
    {
        String sufijo = String.valueOf(Instant.now().toEpochMilli());

        String usuario = "ricardito" + sufijo;
        String correo = usuario + "@gmail.com";
        String contraseña = "ContraseñaHiperSegura";

        registrar(usuario, correo, contraseña);

        new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/login")
            ));

        assertTrue(usuarios.existsByNombreUsuario(usuario));
    }
}
