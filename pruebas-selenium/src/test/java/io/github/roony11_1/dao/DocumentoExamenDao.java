package io.github.roony11_1.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.roony11_1.db.DatabaseConnection;

/**
 * Consultas de solo lectura sobre {@code examen_medico}, {@code documentos_examen}
 * y {@code auditoria} usadas para verificar en PostgreSQL lo que la UI reporta.
 */
public class DocumentoExamenDao
{
    /** Fila relevante de {@code auditoria} para verificar una eliminación. */
    public record Auditoria(long id, Long usuarioId, String accion, Timestamp fechaHora) {}

    private static final String QUERY_EXISTE_EXAMEN =
        "SELECT 1 FROM examen_medico WHERE id = ?";

    private static final String QUERY_IDS_EXAMENES =
        "SELECT id FROM examen_medico ORDER BY id";

    private static final String QUERY_ULTIMA_ELIMINACION =
        "SELECT id, usuario_id, accion, fecha_hora FROM auditoria " +
        "WHERE accion LIKE 'EXAMENES_ELIMINAR:%' ORDER BY id DESC LIMIT 1";

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

    /** Indica si el examen sigue existiendo en la base de datos. */
    public boolean existeExamen(long examenId)
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_EXISTE_EXAMEN))
        {
            ps.setLong(1, examenId);

            try (ResultSet rs = ps.executeQuery())
            {
                return rs.next();
            }
        }
        catch (SQLException e)
        {
            throw new RuntimeException(
                "Error verificando la existencia del examen " + examenId, e);
        }
    }

    /** IDs de todos los exámenes registrados, para detectar bajas colaterales. */
    public List<Long> idsExamenes()
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_IDS_EXAMENES);
             ResultSet rs = ps.executeQuery())
        {
            List<Long> ids = new ArrayList<>();

            while (rs.next())
                ids.add(rs.getLong("id"));

            return ids;
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error listando los exámenes registrados", e);
        }
    }

    /** Último registro de auditoría de tipo {@code EXAMENES_ELIMINAR}. */
    public Optional<Auditoria> ultimaEliminacion()
    {
        try (Connection conn = DatabaseConnection.getConexion();
             PreparedStatement ps = conn.prepareStatement(QUERY_ULTIMA_ELIMINACION);
             ResultSet rs = ps.executeQuery())
        {
            if (!rs.next())
                return Optional.empty();

            long usuarioId = rs.getLong("usuario_id");
            Long usuarioIdONull = rs.wasNull() ? null : usuarioId;

            return Optional.of(new Auditoria(
                rs.getLong("id"),
                usuarioIdONull,
                rs.getString("accion"),
                rs.getTimestamp("fecha_hora")
            ));
        }
        catch (SQLException e)
        {
            throw new RuntimeException("Error buscando la última eliminación auditada", e);
        }
    }
}
