using Xunit;
namespace PerfilesService.Tests;

/// <summary>Pruebas del formato y los códigos HTTP de /health (sin SQLite ni RabbitMQ).</summary>
public class HealthServiceTests
{
    [Fact]
    public void TodoUp_Responde200ConFormatoComun()
    {
        var (codigo, cuerpo) = HealthService.Evaluar(() => true, () => true);

        Assert.Equal(200, codigo);
        Assert.Equal("UP", cuerpo.Status);
        Assert.Equal("perfiles-service", cuerpo.Service);
        Assert.Equal("UP", cuerpo.Checks["database"]);
        Assert.Equal("UP", cuerpo.Checks["messageBroker"]);
    }

    [Fact]
    public void BrokerCaido_Responde503YMarcaSoloElBroker()
    {
        var (codigo, cuerpo) = HealthService.Evaluar(() => true, () => false);

        Assert.Equal(503, codigo);
        Assert.Equal("DOWN", cuerpo.Status);
        Assert.Equal("UP", cuerpo.Checks["database"]);
        Assert.Equal("DOWN", cuerpo.Checks["messageBroker"]);
    }

    [Fact]
    public void BaseDeDatosCaida_Responde503()
    {
        var (codigo, cuerpo) = HealthService.Evaluar(() => false, () => true);

        Assert.Equal(503, codigo);
        Assert.Equal("DOWN", cuerpo.Checks["database"]);
    }

    [Fact]
    public void UnaVerificacionQueLanzaExcepcion_CuentaComoDown()
    {
        var (codigo, cuerpo) = HealthService.Evaluar(() => throw new InvalidOperationException("sin bd"), () => true);

        Assert.Equal(503, codigo);
        Assert.Equal("DOWN", cuerpo.Checks["database"]);
    }

    [Fact]
    public void BaseDeDatosSqlite_EstaDisponibleConRutaValida()
    {
        // Db lee DB_PATH al cargarse; con una ruta temporal SQLite crea el archivo y responde.
        Assert.True(Db.EstaDisponible());
    }
}
