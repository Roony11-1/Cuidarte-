package io.github.roony11_1.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import io.github.roony11_1.db.DatabaseConnection;
import io.github.roony11_1.model.Usuario;

public class UsuarioDao
{
    private static final String QUERY_FIND_BY_USERNAME = "SELECT id, nombre_usuario, contrasena, correo FROM usuarios WHERE nombre_usuario = ?";

    private static final String QUERY_EXISTS_BY_USERNAME = "SELECT 1 FROM usuarios WHERE nombre_usuario = ?";

    public Optional<Usuario> findByNombreUsuario(String nombreUsuario) 
    {
        try (Connection conn = DatabaseConnection.getConexion();
            PreparedStatement ps = conn.prepareStatement(QUERY_FIND_BY_USERNAME)) 
        {
            ps.setString(1, nombreUsuario);
            try (ResultSet rs = ps.executeQuery()) 
            {
                if (!rs.next()) 
                    return Optional.empty();

                var usuario = new Usuario(
                    rs.getLong("id"), 
                    rs.getString("nombre_usuario"), 
                    rs.getString("contrasena"),
                    rs.getString("correo")
                );

                return Optional.of(usuario);
            }
        } 
        catch (SQLException e) 
        {
            throw new RuntimeException("Error buscando usuario: " + nombreUsuario, e);
        }
    }

    public boolean existsByNombreUsuario(String nombreUsuario)
    {
        try (Connection conn = DatabaseConnection.getConexion(); PreparedStatement ps = conn.prepareStatement(QUERY_EXISTS_BY_USERNAME))
        {
            ps.setString(1, nombreUsuario);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next(); // Si existe almenos una columna devuelve verdadero
            }
        }
        catch (SQLException ex) 
        {
            throw new RuntimeException("Error verificando existencia de usuario: " + nombreUsuario, ex);
        }
    }
}
