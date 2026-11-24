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

/**
 * AUT-PF-001 · Validación de hashing de contraseñas con bcrypt.
 *
 * <p>Requerimientos: RF-2.1, NFR-SEG-2. Incidente: INC-DIN-01.</p>
 *
 * <p>Verifica que al registrar un usuario, el campo {@code usuarios.contrasena}
 * se persiste como hash bcrypt y no en texto plano.</p>
 *
 * <p>Pasos:</p>
 * <ol>
 *   <li>Registrar usuario con contraseña conocida.</li>
 *   <li>Leer el usuario desde BD.</li>
 *   <li>Comprobar que la contraseña guardada ≠ contraseña original.</li>
 *   <li>Comprobar formato bcrypt: {@code ^\$2[aby]\$\d{2}\$.{53}$}.</li>
 * </ol>
 *
 * <p>Equivalente manual: registrar en {@code http://localhost:3333} y consultar en
 * {@code psql} con {@code SELECT contrasena FROM usuarios ORDER BY id DESC LIMIT 3;}.</p>
 *
 * <p>Estado actual: <b>FAIL</b> — contraseña en texto plano (INC-DIN-01).
 */
public class ContraseñaEncriptadaTest extends BaseTest
{
    private final UsuarioDao usuarios = new UsuarioDao();

    /**
     * AUT-PF-001 — Contraseñas deben almacenarse hasheadas con bcrypt.
     *
     * <p>Criterios de aceptación:</p>
     * <ul>
     *   <li>El usuario existe en BD.</li>
     *   <li>{@code contrasenaBD != contrasenaOriginal}.</li>
     *   <li>{@code contrasenaBD} cumple el patrón bcrypt.</li>
     * </ul>
     *
     * <p>Deshabilitado hasta corregir INC-DIN-01. Al cerrar el incidente, quitar
     * {@code @Disabled} y actualizar el estado a PASS en el Javadoc.</p>
     */
    @Test
    @Disabled("INC-DIN-01: contraseñas en texto plano. Quitar cuando el backend use bcrypt.")
    @DisplayName("AUT-PF-001 - Contraseñas deben almacenarse hasheadas con bcrypt")
    void contraseñaDeberiaEstarEncriptada()
    {
        var user = crearUsuarioUnico("fulanitoHackeable", "DeberiaEstarHasheada");

        registrar(user.usuario(), user.correo(), user.password());

        Optional<Usuario> guardado = usuarios.findByNombreUsuario(user.usuario());
        assertTrue(guardado.isPresent(),
            "El usuario '" + user.usuario() + "' no se creó en la BD.");

        String hash = guardado.get().contraseña();

        assertNotEquals(user.password(), hash,
            "INC-DIN-01: contraseña en TEXTO PLANO: " + hash);

        assertTrue(hash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$"),
            "Se esperaba hash bcrypt, pero se almacenó: " + hash);
    }
}