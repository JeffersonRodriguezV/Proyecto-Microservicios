using Microsoft.Data.Sqlite;

/// <summary>
/// Implementación real de <see cref="IAlmacenPerfiles"/> sobre SQLite. Contiene únicamente SQL:
/// las decisiones viven en <see cref="ProcesadorEventos"/>. Se instancia por mensaje, con su
/// propia conexión, y la cierra al desecharse (igual que el código original).
/// </summary>
public sealed class AlmacenPerfilesSqlite : IAlmacenPerfiles, IDisposable
{
    private readonly SqliteConnection _conexion;

    public AlmacenPerfilesSqlite(SqliteConnection conexion)
    {
        _conexion = conexion;
    }

    public bool EventoYaProcesado(string eventoId)
    {
        using var cmd = _conexion.CreateCommand();
        cmd.CommandText = "SELECT COUNT(*) FROM eventos_procesados WHERE id = @id";
        cmd.Parameters.AddWithValue("@id", eventoId);
        return (long)cmd.ExecuteScalar()! > 0;
    }

    public void CrearPerfilPorDefecto(string empleadoId, string nombre, string email)
    {
        using var cmd = _conexion.CreateCommand();
        cmd.CommandText = @"
            INSERT INTO perfiles (id, empleado_id, nombre, email, fecha_creacion)
            VALUES (@id, @empleadoId, @nombre, @email, @fecha)
        ";
        cmd.Parameters.AddWithValue("@id", Guid.NewGuid().ToString());
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        cmd.Parameters.AddWithValue("@nombre", nombre);
        cmd.Parameters.AddWithValue("@email", email);
        cmd.Parameters.AddWithValue("@fecha", DateTime.UtcNow.ToString("o"));
        cmd.ExecuteNonQuery();
    }

    public void SincronizarDatosBasicos(string empleadoId, string nombre, string email)
    {
        using var cmd = _conexion.CreateCommand();
        cmd.CommandText = @"
            UPDATE perfiles SET nombre = @nombre, email = @email
            WHERE empleado_id = @empleadoId
        ";
        cmd.Parameters.AddWithValue("@nombre", nombre);
        cmd.Parameters.AddWithValue("@email", email);
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        cmd.ExecuteNonQuery();
    }

    public void Archivar(string empleadoId)
    {
        using var cmd = _conexion.CreateCommand();
        cmd.CommandText = "UPDATE perfiles SET archivado = 1 WHERE empleado_id = @empleadoId";
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        cmd.ExecuteNonQuery();
    }

    public void MarcarProcesado(string eventoId)
    {
        using var cmd = _conexion.CreateCommand();
        cmd.CommandText = "INSERT INTO eventos_procesados (id, procesado_en) VALUES (@id, @fecha)";
        cmd.Parameters.AddWithValue("@id", eventoId);
        cmd.Parameters.AddWithValue("@fecha", DateTime.UtcNow.ToString("o"));
        cmd.ExecuteNonQuery();
    }

    public void Dispose() => _conexion.Dispose();
}
