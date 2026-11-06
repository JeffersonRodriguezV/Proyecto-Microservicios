using Microsoft.Data.Sqlite;

public static class Db
{
    private static readonly string RutaBd = Environment.GetEnvironmentVariable("DB_PATH") ?? "perfiles.db";
    private static readonly string ConnectionString = $"Data Source={RutaBd}";

    public static SqliteConnection ObtenerConexion()
    {
        var conexion = new SqliteConnection(ConnectionString);
        conexion.Open();
        return conexion;
    }

    /// <summary>True si SQLite responde a un SELECT 1 (lo usa /health).</summary>
    public static bool EstaDisponible()
    {
        try
        {
            using var conexion = ObtenerConexion();
            var comando = conexion.CreateCommand();
            comando.CommandText = "SELECT 1";
            comando.ExecuteScalar();
            return true;
        }
        catch
        {
            return false;
        }
    }

    public static void Inicializar()
    {
        using var conexion = ObtenerConexion();
        var comando = conexion.CreateCommand();
        comando.CommandText = @"
            CREATE TABLE IF NOT EXISTS perfiles (
                id TEXT PRIMARY KEY,
                empleado_id TEXT UNIQUE NOT NULL,
                nombre TEXT,
                email TEXT,
                telefono TEXT DEFAULT '',
                direccion TEXT DEFAULT '',
                ciudad TEXT DEFAULT '',
                biografia TEXT DEFAULT '',
                fecha_creacion TEXT,
                archivado INTEGER DEFAULT 0
            );

            CREATE TABLE IF NOT EXISTS eventos_procesados (
                id TEXT PRIMARY KEY,
                procesado_en TEXT NOT NULL
            );
        ";
        comando.ExecuteNonQuery();
    }
}