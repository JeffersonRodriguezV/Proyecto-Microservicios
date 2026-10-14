using System.Text.Json;

/// <summary>Qué pasó con un evento al procesarlo.</summary>
public enum ResultadoProcesamiento
{
    /// <summary>Se aplicó y quedó registrado como procesado.</summary>
    Aplicado,

    /// <summary>Ya se había procesado antes: se descartó sin tocar nada.</summary>
    Duplicado
}

/// <summary>
/// Regla de negocio de perfiles ante los eventos de empleados:
///   empleado.creado      -> crea el perfil por defecto
///   empleado.actualizado -> sincroniza nombre y email
///   empleado.retirado    -> archiva el perfil (nunca lo borra)
/// con deduplicación por id de evento.
///
/// No conoce SQLite, RabbitMQ ni la consola: recibe el almacén por constructor y el log como
/// función opcional. Por eso se puede probar con dobles, sin infraestructura.
/// </summary>
public class ProcesadorEventos
{
    public const string TipoCreado = "empleado.creado";
    public const string TipoActualizado = "empleado.actualizado";
    public const string TipoRetirado = "empleado.retirado";

    private readonly IAlmacenPerfiles _almacen;
    private readonly Action<string> _log;

    public ProcesadorEventos(IAlmacenPerfiles almacen, Action<string>? log = null)
    {
        _almacen = almacen;
        _log = log ?? (_ => { });
    }

    /// <summary>
    /// Procesa un evento. Si algo falla, la excepción se propaga y el evento NO se marca como
    /// procesado (el último paso es MarcarProcesado), de modo que un reintento puede aplicarlo.
    /// </summary>
    public ResultadoProcesamiento Procesar(EventoEnvelope envelope)
    {
        if (_almacen.EventoYaProcesado(envelope.Id))
        {
            _log($"Evento {envelope.Id} ya procesado, se descarta (deduplicación)");
            return ResultadoProcesamiento.Duplicado;
        }

        var empleadoId = Texto(envelope.Data, "empleadoId");

        switch (envelope.Type)
        {
            case TipoCreado:
                _almacen.CrearPerfilPorDefecto(empleadoId, Texto(envelope.Data, "nombre"), Texto(envelope.Data, "email"));
                _log($"Perfil creado para empleado {empleadoId}");
                break;
            case TipoActualizado:
                _almacen.SincronizarDatosBasicos(empleadoId, Texto(envelope.Data, "nombre"), Texto(envelope.Data, "email"));
                _log($"Perfil sincronizado para empleado {empleadoId}");
                break;
            case TipoRetirado:
                _almacen.Archivar(empleadoId);
                _log($"Perfil archivado para empleado {empleadoId}");
                break;
        }

        _almacen.MarcarProcesado(envelope.Id);
        return ResultadoProcesamiento.Aplicado;
    }

    /// <summary>Lee un campo de texto del payload; "" si es null. Lanza KeyNotFoundException si falta.</summary>
    private static string Texto(JsonElement data, string campo) =>
        data.GetProperty(campo).GetString() ?? "";
}
