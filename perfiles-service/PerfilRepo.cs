using Microsoft.Data.Sqlite;

public static class PerfilRepo
{
    public static object? ObtenerPorEmpleadoId(string empleadoId)
    {
        using var conexion = Db.ObtenerConexion();
        var cmd = conexion.CreateCommand();
        cmd.CommandText = "SELECT * FROM perfiles WHERE empleado_id = @empleadoId";
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        using var reader = cmd.ExecuteReader();
        if (!reader.Read()) return null;
        return Mapear(reader);
    }

    public static List<object> ListarTodos()
    {
        using var conexion = Db.ObtenerConexion();
        var cmd = conexion.CreateCommand();
        cmd.CommandText = "SELECT * FROM perfiles";
        using var reader = cmd.ExecuteReader();
        var resultado = new List<object>();
        while (reader.Read())
        {
            resultado.Add(Mapear(reader));
        }
        return resultado;
    }

    public static bool Actualizar(string empleadoId, string? telefono, string? direccion, string? ciudad, string? biografia)
    {
        using var conexion = Db.ObtenerConexion();
        var cmd = conexion.CreateCommand();
        cmd.CommandText = @"
            UPDATE perfiles SET
                telefono = COALESCE(@telefono, telefono),
                direccion = COALESCE(@direccion, direccion),
                ciudad = COALESCE(@ciudad, ciudad),
                biografia = COALESCE(@biografia, biografia)
            WHERE empleado_id = @empleadoId
        ";
        cmd.Parameters.AddWithValue("@telefono", (object?)telefono ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@direccion", (object?)direccion ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@ciudad", (object?)ciudad ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@biografia", (object?)biografia ?? DBNull.Value);
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        var filas = cmd.ExecuteNonQuery();
        return filas > 0;
    }

    private static object Mapear(SqliteDataReader reader)
    {
        return new
        {
            id = reader.GetString(reader.GetOrdinal("id")),
            empleadoId = reader.GetString(reader.GetOrdinal("empleado_id")),
            nombre = reader.GetString(reader.GetOrdinal("nombre")),
            email = reader.GetString(reader.GetOrdinal("email")),
            telefono = reader.GetString(reader.GetOrdinal("telefono")),
            direccion = reader.GetString(reader.GetOrdinal("direccion")),
            ciudad = reader.GetString(reader.GetOrdinal("ciudad")),
            biografia = reader.GetString(reader.GetOrdinal("biografia")),
            fechaCreacion = reader.GetString(reader.GetOrdinal("fecha_creacion")),
            archivado = reader.GetInt32(reader.GetOrdinal("archivado")) == 1
        };
    }
}