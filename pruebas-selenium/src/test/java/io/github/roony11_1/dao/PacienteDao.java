package io.github.roony11_1.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import io.github.roony11_1.db.DatabaseConnection;

/**
 * Consultas de solo lectura sobre {@code pacientes} usadas para verificar en
 * PostgreSQL que los payloads de inyección SQL o XSS no alteraron la tabla.
 */
public class PacienteDao
{
    /** Campos de texto médico usados por la prueba de XSS almacenado. */
    public record TextosMedicos(String alergias, String observacionesMedicasGenerales) {}

    private static final String QUERY_CONTAR_PACIENTES =
        "SELECT count(*) FROM pacientes";

    private static final String QUERY_EXISTE_TABLA =
        "SELECT to_regclass('public.pacientes') IS NOT NULL";

    private static final String QUERY_EXISTE_PACIENTE =
        "SELECT 1 FROM pacientes WHERE id = ?";

    private static final String QUERY_TEXTOS_MEDICOS =
        "SELECT alergias, observaciones_medicas_generales FROM pacientes WHERE id = ?";

    /** Cantidad actual de filas en {@code pacientes}. */
    public int contar()
    {
        try (Connection conn = DatabaseConnection.getConexion();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(QUERY_CONTAR_PACIENTES))
        {
            rs.next();

            return rs.getInt(1);
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error contando pacientes", e);
        }
    }

    /**
     * {@code true} si la tabla {@code pacientes} sigue existiendo.
     * Un {@code DROP TABLE} exitoso haría que este método devolviera {@code false}.
     */
    public boolean existeTabla()
    {
        try (Connection conn = DatabaseConnection.getConexion();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(QUERY_EXISTE_TABLA))
        {
            rs.next();

            return rs.getBoolean(1);
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error verificando la tabla pacientes", e);
        }
    }

    /** {@code true} si existe una fila con ese {@code id}. */
    public boolean existe(long id)
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_EXISTE_PACIENTE))
        {
            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error verificando el paciente " + id, e);
        }
    }

    /** Valores almacenados en los campos de texto médico del paciente. */
    public Optional<TextosMedicos> textosMedicos(long id)
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_TEXTOS_MEDICOS))
        {
            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery())
            {
                if (!rs.next())
                    return Optional.empty();

                return Optional.of(new TextosMedicos(
                    rs.getString("alergias"),
                    rs.getString("observaciones_medicas_generales")
                ));
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error leyendo los textos médicos del paciente " + id, e);
        }
    }
}
