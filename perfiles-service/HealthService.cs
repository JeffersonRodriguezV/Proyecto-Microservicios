/// <summary>
/// Lógica del endpoint /health (formato común del proyecto):
///   { "status": "UP|DOWN", "service": "...", "checks": { "database": "...", "messageBroker": "..." } }
/// HTTP 200 si todo está UP y 503 si alguna dependencia está DOWN.
/// Las verificaciones se inyectan para poder probarlo sin SQLite ni RabbitMQ reales.
/// </summary>
public record RespuestaHealth(string Status, string Service, Dictionary<string, string> Checks);

public static class HealthService
{
    public const string NombreServicio = "perfiles-service";

    private static string Estado(bool ok) => ok ? "UP" : "DOWN";

    // Una verificación que lanza excepción cuenta como DOWN: el healthcheck nunca debe fallar con 500.
    private static bool Seguro(Func<bool> verificacion)
    {
        try { return verificacion(); }
        catch { return false; }
    }

    public static (int Codigo, RespuestaHealth Cuerpo) Evaluar(Func<bool> baseDatos, Func<bool> broker)
    {
        var bdOk = Seguro(baseDatos);
        var brokerOk = Seguro(broker);
        var arriba = bdOk && brokerOk;

        var cuerpo = new RespuestaHealth(
            Estado(arriba),
            NombreServicio,
            new Dictionary<string, string>
            {
                ["database"] = Estado(bdOk),
                ["messageBroker"] = Estado(brokerOk)
            });

        return (arriba ? 200 : 503, cuerpo);
    }
}
