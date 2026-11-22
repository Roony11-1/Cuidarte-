package io.github.roony11_1.model;

public record Usuario(
    long id,
    String nombreUsuario,
    String contraseña,
    String correo)
{

}
