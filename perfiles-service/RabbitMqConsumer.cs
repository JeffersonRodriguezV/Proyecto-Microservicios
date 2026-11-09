using System.Text;
using System.Text.Json;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;
using Serilog;

public static class RabbitMqConsumer
{
    // Refleja si el consumidor está conectado al broker (lo lee /health).
    private static volatile bool _conectado;
    public static bool Conectado => _conectado;

    public static async Task Iniciar()
    {
        var host = Environment.GetEnvironmentVariable("RABBITMQ_HOST") ?? "localhost";
        var port = int.Parse(Environment.GetEnvironmentVariable("RABBITMQ_PORT") ?? "5672");
        var usuario = Environment.GetEnvironmentVariable("RABBITMQ_USERNAME") ?? "admin";
        var password = Environment.GetEnvironmentVariable("RABBITMQ_PASSWORD")
            ?? throw new InvalidOperationException("RABBITMQ_PASSWORD no está definido (revise el archivo .env)");
        var exchange = Environment.GetEnvironmentVariable("EVENTOS_EXCHANGE") ?? "ecosistema.eventos";

        var factory = new ConnectionFactory
        {
            HostName = host,
            Port = port,
            UserName = usuario,
            Password = password,
            // Reconexión automática (incluye re-declarar colas y consumidores) si el broker se reinicia.
            AutomaticRecoveryEnabled = true,
            TopologyRecoveryEnabled = true,
            NetworkRecoveryInterval = TimeSpan.FromSeconds(5)
        };

        var conexion = await factory.CreateConnectionAsync();
        // Si el broker cierra la conexión, /health pasa a DOWN.
        conexion.ConnectionShutdownAsync += (_, _) => { _conectado = false; return Task.CompletedTask; };
        // Al recuperarse la conexión, /health vuelve a UP.
        conexion.RecoverySucceededAsync += (_, _) => { _conectado = true; return Task.CompletedTask; };
        var canal = await conexion.CreateChannelAsync();

        await canal.ExchangeDeclareAsync(exchange, ExchangeType.Topic, durable: true);

        var colaResult = await canal.QueueDeclareAsync("perfiles-service.eventos", durable: true, exclusive: false, autoDelete: false);
        var nombreCola = colaResult.QueueName;

        string[] eventosRelevantes = { "empleado.creado", "empleado.actualizado", "empleado.retirado" };
        foreach (var routingKey in eventosRelevantes)
        {
            await canal.QueueBindAsync(nombreCola, exchange, routingKey);
        }

        var consumidor = new AsyncEventingBasicConsumer(canal);
        consumidor.ReceivedAsync += async (modelo, evento) =>
        {
            try
            {
                var json = Encoding.UTF8.GetString(evento.Body.ToArray());
                var envelope = JsonSerializer.Deserialize<EventoEnvelope>(json);
                if (envelope != null)
                {
                    // Continúa la traza del productor: el traceparent viaja en las cabeceras del mensaje.
                    using var actividad = Observabilidad.IniciarConsumo(envelope.Type, evento.BasicProperties.Headers);
                    ProcesarMensaje(envelope);
                }
            }
            catch (Exception ex)
            {
                Log.Error(ex, "Error procesando mensaje");
            }
            finally
            {
                await canal.BasicAckAsync(evento.DeliveryTag, multiple: false);
            }
        };

        await canal.BasicConsumeAsync(nombreCola, autoAck: false, consumidor);
        _conectado = true;

        Log.Information("Escuchando eventos: empleado.creado, empleado.actualizado, empleado.retirado");
    }

    /// <summary>
    /// Adaptador: abre la conexión a SQLite y delega la regla de negocio en ProcesadorEventos.
    /// </summary>
    private static void ProcesarMensaje(EventoEnvelope envelope)
    {
        using var almacen = new AlmacenPerfilesSqlite(Db.ObtenerConexion());
        new ProcesadorEventos(almacen, mensaje => Log.Information("{Mensaje}", mensaje)).Procesar(envelope);
    }
}
