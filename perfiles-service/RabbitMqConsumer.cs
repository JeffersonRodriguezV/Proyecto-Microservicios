using System.Text;
using System.Text.Json;
using Microsoft.Data.Sqlite;
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

    private static void ProcesarMensaje(EventoEnvelope envelope)
    {
        using var conexion = Db.ObtenerConexion();

        var checkCmd = conexion.CreateCommand();
        checkCmd.CommandText = "SELECT COUNT(*) FROM eventos_procesados WHERE id = @id";
        checkCmd.Parameters.AddWithValue("@id", envelope.Id);
        var existe = (long)checkCmd.ExecuteScalar()! > 0;

        if (existe)
        {
            Console.WriteLine($"Evento {envelope.Id} ya procesado, se descarta (deduplicación)");
            return;
        }

        var empleadoId = envelope.Data.GetProperty("empleadoId").GetString() ?? "";

        switch (envelope.Type)
        {
            case "empleado.creado":
                CrearPerfilPorDefecto(conexion, envelope);
                break;
            case "empleado.actualizado":
                SincronizarPerfil(conexion, envelope);
                break;
            case "empleado.retirado":
                ArchivarPerfil(conexion, empleadoId);
                break;
        }

        var insertCmd = conexion.CreateCommand();
        insertCmd.CommandText = "INSERT INTO eventos_procesados (id, procesado_en) VALUES (@id, @fecha)";
        insertCmd.Parameters.AddWithValue("@id", envelope.Id);
        insertCmd.Parameters.AddWithValue("@fecha", DateTime.UtcNow.ToString("o"));
        insertCmd.ExecuteNonQuery();
    }

    private static void CrearPerfilPorDefecto(SqliteConnection conexion, EventoEnvelope envelope)
    {
        var empleadoId = envelope.Data.GetProperty("empleadoId").GetString() ?? "";
        var nombre = envelope.Data.GetProperty("nombre").GetString() ?? "";
        var email = envelope.Data.GetProperty("email").GetString() ?? "";

        var cmd = conexion.CreateCommand();
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

        Console.WriteLine($"Perfil creado para empleado {empleadoId}");
    }

    private static void SincronizarPerfil(SqliteConnection conexion, EventoEnvelope envelope)
    {
        var empleadoId = envelope.Data.GetProperty("empleadoId").GetString() ?? "";
        var nombre = envelope.Data.GetProperty("nombre").GetString() ?? "";
        var email = envelope.Data.GetProperty("email").GetString() ?? "";

        var cmd = conexion.CreateCommand();
        cmd.CommandText = @"
            UPDATE perfiles SET nombre = @nombre, email = @email
            WHERE empleado_id = @empleadoId
        ";
        cmd.Parameters.AddWithValue("@nombre", nombre);
        cmd.Parameters.AddWithValue("@email", email);
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        cmd.ExecuteNonQuery();

        Console.WriteLine($"Perfil sincronizado para empleado {empleadoId}");
    }

    private static void ArchivarPerfil(SqliteConnection conexion, string empleadoId)
    {
        var cmd = conexion.CreateCommand();
        cmd.CommandText = "UPDATE perfiles SET archivado = 1 WHERE empleado_id = @empleadoId";
        cmd.Parameters.AddWithValue("@empleadoId", empleadoId);
        cmd.ExecuteNonQuery();

        Console.WriteLine($"Perfil archivado para empleado {empleadoId}");
    }
}