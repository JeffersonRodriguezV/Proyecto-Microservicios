/// <summary>
/// Puerto de salida del procesador de eventos: todo lo que necesita guardar o consultar.
///
/// El procesador depende de esta interfaz y no de SQLite (Inversión de Dependencias, la "D" de SOLID).
/// En producción lo implementa <see cref="AlmacenPerfilesSqlite"/>; en las pruebas unitarias,
/// un doble en memoria. Así la regla de negocio se prueba sin base de datos ni RabbitMQ.
/// </summary>
public interface IAlmacenPerfiles
{
    /// <summary>¿Ya se procesó un evento con este id? (deduplicación de mensajes repetidos)</summary>
    bool EventoYaProcesado(string eventoId);

    /// <summary>Crea el perfil por defecto de un empleado recién creado.</summary>
    void CrearPerfilPorDefecto(string empleadoId, string nombre, string email);

    /// <summary>Copia nombre y email del empleado al perfil existente.</summary>
    void SincronizarDatosBasicos(string empleadoId, string nombre, string email);

    /// <summary>Marca el perfil como archivado. Nunca se borra.</summary>
    void Archivar(string empleadoId);

    /// <summary>Registra el evento como procesado, para descartar repeticiones.</summary>
    void MarcarProcesado(string eventoId);
}
