/// <summary>
/// Doble de prueba (fake) de <see cref="IAlmacenPerfiles"/>: guarda todo en memoria.
///
/// Es un FAKE con estado, no un mock que verifica llamadas: las pruebas comprueban el RESULTADO
/// (qué perfiles quedaron y cómo), no cuántas veces se invocó cada método. Así no se rompen si
/// la implementación cambia de forma sin cambiar el comportamiento.
/// </summary>
public class AlmacenPerfilesFake : IAlmacenPerfiles
{
    public record PerfilFake(string EmpleadoId, string Nombre, string Email, bool Archivado);

    private readonly HashSet<string> _procesados = new();

    /// <summary>Perfiles por empleadoId.</summary>
    public Dictionary<string, PerfilFake> Perfiles { get; } = new();

    /// <summary>Cuántas veces se creó un perfil (para detectar creaciones duplicadas).</summary>
    public int Creaciones { get; private set; }

    /// <summary>Si es true, crear un perfil lanza una excepción (simula una falla de la base de datos).</summary>
    public bool FallarAlCrear { get; set; }

    public bool FueProcesado(string eventoId) => _procesados.Contains(eventoId);

    public bool EventoYaProcesado(string eventoId) => _procesados.Contains(eventoId);

    public void CrearPerfilPorDefecto(string empleadoId, string nombre, string email)
    {
        if (FallarAlCrear)
        {
            throw new InvalidOperationException("Fallo simulado de base de datos");
        }

        Creaciones++;
        Perfiles[empleadoId] = new PerfilFake(empleadoId, nombre, email, Archivado: false);
    }

    public void SincronizarDatosBasicos(string empleadoId, string nombre, string email)
    {
        // Igual que el UPDATE real: si no existe el perfil, no hace nada.
        if (Perfiles.TryGetValue(empleadoId, out var perfil))
        {
            Perfiles[empleadoId] = perfil with { Nombre = nombre, Email = email };
        }
    }

    public void Archivar(string empleadoId)
    {
        if (Perfiles.TryGetValue(empleadoId, out var perfil))
        {
            Perfiles[empleadoId] = perfil with { Archivado = true };
        }
    }

    public void MarcarProcesado(string eventoId) => _procesados.Add(eventoId);
}
