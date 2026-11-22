package io.github.roony11_1.tests;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.roony11_1.base.BaseTest;
import io.github.roony11_1.dao.UsuarioDao;
import io.github.roony11_1.model.Usuario;

public class ContraseñaEncriptadaTest extends BaseTest
{
    private final UsuarioDao usuarios = new UsuarioDao();

    @Test 
    @Disabled("INC-DIN-01: bug conocido — contraseñas en texto plano. " + "Quitar @Disabled cuando el backend use bcrypt.")
    @DisplayName("AUT-PF-01 - Contraseñas deben almacenarse hasheadas con bcrypt")
    void contraseñaDeberiaEstarEncriptada()
    {
        var user = crearUsuarioUnico("fulanitoHackeable", "DeberiaEstarHasheada");

        registrar(user.usuario(), user.correo(), user.password());

        Optional<Usuario> guardado = usuarios.findByNombreUsuario(user.usuario());

        assertTrue(guardado.isPresent(),
            "El usuario '" + user.usuario() + "' no se creó en la BD.");

        String contraseñaSupuestamenteHasheada = guardado.get().contraseña();

        assertNotEquals(user.password(), contraseñaSupuestamenteHasheada, 
            "INC-DIN-01: contraseña en TEXTO PLANO: " + contraseñaSupuestamenteHasheada);

        // Prueba el formato de un hash de bcrypt
        assertTrue(contraseñaSupuestamenteHasheada.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$"),
            "Se esperaba hash bcrypt, pero se almacenó: " + contraseñaSupuestamenteHasheada);
    }
}
