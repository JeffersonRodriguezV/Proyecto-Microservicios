using System.Text;
using System.Text.Json;
using RabbitMQ.Client;
using RabbitMQ.Client.Events;

public static class RabbitMqConsumer
{
    public static async Task Iniciar()
    {
        var host = Environment.GetEnvironmentVariable("RABBITMQ_HOST") ?? "localhost";
        var port = int.Parse(Environment.GetEnvironmentVariable("RABBITMQ_PORT") ?? "5672");
        var usuario = Environment.GetEnvironmentVariable("RABBITMQ_USERNAME") ?? "admin";
        var password = Environment.GetEnvironmentVariable("RABBITMQ_PASSWORD") ?? "admin";
        var exchange = Environment.GetEnvironmentVariable("EVENTOS_EXCHANGE") ?? "ecosistema.eventos";

        var factory = new ConnectionFactory
        {
            HostName = host,
            Port = port,
            UserName = usuario,
            Password = password
        };

        var conexion = await factory.CreateConnectionAsync();
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
                    ProcesarMensaje(envelope);
                }
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Error procesando mensaje: {ex.Message}");
            }
            finally
            {
                await canal.BasicAckAsync(evento.DeliveryTag, multiple: false);
            }
        };

        await canal.BasicConsumeAsync(nombreCola, autoAck: false, consumidor);

        Console.WriteLine("Escuchando eventos: empleado.creado, empleado.actualizado, empleado.retirado");
    }

    /// <summary>
    /// Adaptador: abre la conexión a SQLite y delega la regla de negocio en ProcesadorEventos.
    /// </summary>
    private static void ProcesarMensaje(EventoEnvelope envelope)
    {
        using var almacen = new AlmacenPerfilesSqlite(Db.ObtenerConexion());
        new ProcesadorEventos(almacen, Console.WriteLine).Procesar(envelope);
    }
}
