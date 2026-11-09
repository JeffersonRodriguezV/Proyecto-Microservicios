using System.Diagnostics;
using System.Text;
using OpenTelemetry.Context.Propagation;
using Serilog;
using Serilog.Events;
using Serilog.Templates;

/// <summary>
/// Reto 8: trazas (OpenTelemetry), logs JSON (Serilog) y propagación del traceparent por el broker.
/// </summary>
public static class Observabilidad
{
    public const string Servicio = "perfiles-service";

    /// <summary>Fuente de spans propios (el consumidor del broker). Debe registrarse con AddSource.</summary>
    public static readonly ActivitySource Fuente = new(Servicio);

    // Rutas que Docker (healthcheck) y Prometheus consultan cada pocos segundos: ni trazas ni log.
    public static bool EsRutaSilenciosa(PathString ruta) =>
        ruta.StartsWithSegments("/health") || ruta.StartsWithSegments("/metrics");

    /// <summary>JSON plano, una línea por evento: timestamp, level, message, service, traceId y propiedades.</summary>
    public static LoggerConfiguration ConfigurarLogs(LoggerConfiguration cfg) => cfg
        .MinimumLevel.Information()
        .MinimumLevel.Override("Microsoft", LogEventLevel.Warning)
        .Enrich.FromLogContext()
        .WriteTo.Console(new ExpressionTemplate(
            "{ {timestamp: @t, level: @l, message: @m, service: 'perfiles-service', traceId: @tr, ..@p} }\n"));

    // Propagador W3C explícito (no el global del SDK, que solo existe una vez arrancado OpenTelemetry).
    private static readonly TraceContextPropagator Propagador = new();

    // Los encabezados AMQP de texto llegan como byte[]; el propagador espera strings.
    private static IEnumerable<string> LeerCabecera(IDictionary<string, object?> cabeceras, string clave)
    {
        if (cabeceras.TryGetValue(clave, out var valor) && valor is not null)
        {
            yield return valor is byte[] bytes ? Encoding.UTF8.GetString(bytes) : valor.ToString() ?? "";
        }
    }

    /// <summary>
    /// Extrae el contexto W3C (traceparent) de las cabeceras del mensaje. Sin cabecera devuelve un
    /// contexto vacío: el span del consumidor abrirá una traza nueva en vez de colgar de otra.
    /// </summary>
    public static PropagationContext ExtraerContexto(IDictionary<string, object?>? cabeceras) =>
        cabeceras is null
            ? default
            : Propagador.Extract(default, cabeceras, LeerCabecera);

    /// <summary>Abre el span CONSUMER del mensaje, hijo del span del productor (si el mensaje trae traceparent).</summary>
    public static Activity? IniciarConsumo(string tipoEvento, IDictionary<string, object?>? cabeceras)
    {
        var padre = ExtraerContexto(cabeceras);
        var actividad = Fuente.StartActivity($"perfiles.procesar {tipoEvento}", ActivityKind.Consumer,
            padre.ActivityContext);
        actividad?.SetTag("messaging.system", "rabbitmq");
        actividad?.SetTag("evento.tipo", tipoEvento);
        return actividad;
    }
}
