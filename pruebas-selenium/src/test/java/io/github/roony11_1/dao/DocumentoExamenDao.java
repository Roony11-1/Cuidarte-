package io.github.roony11_1.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import io.github.roony11_1.db.DatabaseConnection;

/**
 * Consultas de solo lectura sobre {@code examen_medico} y {@code documentos_examen}
 * usadas para verificar en PostgreSQL lo que la UI reporta.
 */
public class DocumentoExamenDao
{
    private static final String QUERY_ULTIMO_EXAMEN =
        "SELECT id FROM examen_medico " +
        "WHERE paciente_id = ? AND tipo_examen_medico_id = ? " +
        "ORDER BY id DESC LIMIT 1";

    private static final String QUERY_CONTAR_DOCUMENTOS =
        "SELECT count(*) FROM documentos_examen WHERE examen_medico_id = ?";

    private static final String QUERY_ULTIMO_DOCUMENTO =
        "SELECT nombre_archivo FROM documentos_examen " +
        "WHERE examen_medico_id = ? ORDER BY id DESC LIMIT 1";

    /** Último examen registrado para un paciente y tipo de examen. */
    public Optional<Long> ultimoExamen(long pacienteId, long tipoExamenId)
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_ULTIMO_EXAMEN))
        {
            ps.setLong(1, pacienteId);
            ps.setLong(2, tipoExamenId);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? Optional.of(rs.getLong("id")) : Optional.empty();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(
                "Error buscando el último examen del paciente " + pacienteId, e);
        }
    }

    /** Cantidad de documentos asociados a un examen. */
    public int contarDocumentos(long examenId)
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_CONTAR_DOCUMENTOS))
        {
            ps.setLong(1, examenId);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(
                "Error contando documentos del examen " + examenId, e);
        }
    }

    /** Nombre del último documento almacenado para un examen. */
    public Optional<String> ultimoDocumento(long examenId)
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_ULTIMO_DOCUMENTO))
        {
            ps.setLong(1, examenId);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next()
                    ? Optional.of(rs.getString("nombre_archivo"))
                    : Optional.empty();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(
                "Error buscando el último documento del examen " + examenId, e);
        }
    }
}
