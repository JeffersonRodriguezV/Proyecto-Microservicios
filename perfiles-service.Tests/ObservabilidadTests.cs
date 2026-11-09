using System.Diagnostics;
using System.Text;
using Xunit;

/// <summary>Reto 8: extracción del traceparent de los mensajes del broker y span del consumidor.</summary>
public class ObservabilidadTests
{
    private const string TraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    private const string Traceparent = $"00-{TraceId}-00f067aa0ba902b7-01";

    // Escucha la fuente de spans propia; sin un listener StartActivity devuelve null.
    private static ActivityListener Escuchar()
    {
        var listener = new ActivityListener
        {
            ShouldListenTo = f => f.Name == Observabilidad.Servicio,
            Sample = (ref ActivityCreationOptions<ActivityContext> _) => ActivitySamplingResult.AllDataAndRecorded
        };
        ActivitySource.AddActivityListener(listener);
        return listener;
    }

    [Fact]
    public void ExtraerContexto_LeeElTraceparentEnBytes()
    {
        var cabeceras = new Dictionary<string, object?> { ["traceparent"] = Encoding.UTF8.GetBytes(Traceparent) };

        var contexto = Observabilidad.ExtraerContexto(cabeceras);

        Assert.Equal(TraceId, contexto.ActivityContext.TraceId.ToString());
        Assert.Equal("00f067aa0ba902b7", contexto.ActivityContext.SpanId.ToString());
    }

    [Fact]
    public void ExtraerContexto_LeeElTraceparentEnTexto()
    {
        var contexto = Observabilidad.ExtraerContexto(new Dictionary<string, object?> { ["traceparent"] = Traceparent });
        Assert.Equal(TraceId, contexto.ActivityContext.TraceId.ToString());
    }

    [Fact]
    public void ExtraerContexto_SinCabecerasDevuelveContextoVacio()
    {
        Assert.Equal(default, Observabilidad.ExtraerContexto(null).ActivityContext);
        Assert.Equal(default, Observabilidad.ExtraerContexto(new Dictionary<string, object?>()).ActivityContext);
    }

    [Fact]
    public void IniciarConsumo_CuelgaDeLaTrazaDelProductor()
    {
        using var listener = Escuchar();
        var cabeceras = new Dictionary<string, object?> { ["traceparent"] = Encoding.UTF8.GetBytes(Traceparent) };

        using var actividad = Observabilidad.IniciarConsumo("empleado.creado", cabeceras);

        Assert.NotNull(actividad);
        Assert.Equal(TraceId, actividad!.TraceId.ToString());
        Assert.Equal("00f067aa0ba902b7", actividad.ParentSpanId.ToString());
        Assert.Equal(ActivityKind.Consumer, actividad.Kind);
        Assert.Equal("perfiles.procesar empleado.creado", actividad.OperationName);
    }

    [Fact]
    public void IniciarConsumo_SinTraceparentAbreUnaTrazaNueva()
    {
        using var listener = Escuchar();

        using var actividad = Observabilidad.IniciarConsumo("empleado.creado", null);

        Assert.NotNull(actividad);
        Assert.NotEqual(TraceId, actividad!.TraceId.ToString());
    }

    [Theory]
    [InlineData("/health", true)]
    [InlineData("/metrics", true)]
    [InlineData("/perfiles/E1", false)]
    public void EsRutaSilenciosa_SoloHealthYMetrics(string ruta, bool esperado) =>
        Assert.Equal(esperado, Observabilidad.EsRutaSilenciosa(new Microsoft.AspNetCore.Http.PathString(ruta)));
}
