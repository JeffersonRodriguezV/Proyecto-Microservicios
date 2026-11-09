using Microsoft.OpenApi;
using OpenTelemetry.Resources;
using OpenTelemetry.Trace;
using Prometheus;
using Serilog;

var builder = WebApplication.CreateBuilder(args);

// Reto 8 ---------------------------------------------------------------------------------------
// Logs JSON con traceId (Serilog)
builder.Host.UseSerilog((_, cfg) => Observabilidad.ConfigurarLogs(cfg));

// Trazas: OpenTelemetry con exportador Zipkin y propagación W3C (traceparent).
// El paquete Zipkin está marcado obsoleto a favor de OTLP, pero el Reto pide Zipkin.
#pragma warning disable CS0618
builder.Services.AddOpenTelemetry().WithTracing(t => t
    .SetResourceBuilder(ResourceBuilder.CreateDefault().AddService(
        Environment.GetEnvironmentVariable("OTEL_SERVICE_NAME") ?? Observabilidad.Servicio))
    .AddAspNetCoreInstrumentation(o => o.Filter = ctx => !Observabilidad.EsRutaSilenciosa(ctx.Request.Path))
    .AddSource(Observabilidad.Servicio)
    .AddZipkinExporter(o => o.Endpoint = new Uri(
        Environment.GetEnvironmentVariable("OTEL_EXPORTER_ZIPKIN_ENDPOINT") ?? "http://zipkin:9411/api/v2/spans")));
#pragma warning restore CS0618
// ---------------------------------------------------------------------------------------------

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
    c.SwaggerDoc("v1", new OpenApiInfo
    {
        Title = "Perfiles Service API",
        Version = "v1",
        Description = "Gestión de perfiles de empleados, reactivo + REST"
    });

    // Reto 5: el contrato declara que los endpoints requieren un JWT emitido por auth-service.
    // La validación real la hace el API Gateway; aquí solo se documenta para Swagger UI.
    c.AddSecurityDefinition("BearerAuth", new OpenApiSecurityScheme
    {
        Type = SecuritySchemeType.Http,
        Scheme = "bearer",
        BearerFormat = "JWT",
        Description = "Pegue el accessToken devuelto por POST /auth/login (sin el prefijo 'Bearer ')."
    });

    c.AddSecurityRequirement(document => new OpenApiSecurityRequirement
    {
        [new OpenApiSecuritySchemeReference("BearerAuth", document)] = new List<string>()
    });
});

var app = builder.Build();

// Log por petición (sin /health ni /metrics) y métricas Prometheus (CPU/memoria del proceso + HTTP)
app.UseSerilogRequestLogging(o => o.GetLevel = (ctx, _, ex) =>
    ex is null && Observabilidad.EsRutaSilenciosa(ctx.Request.Path)
        ? Serilog.Events.LogEventLevel.Verbose
        : Serilog.Events.LogEventLevel.Information);
app.UseHttpMetrics();
app.MapMetrics();   // GET /metrics

app.UseSwagger(c => c.RouteTemplate = "perfiles/swagger/{documentName}/swagger.json");
app.UseSwaggerUI(c =>
{
    c.SwaggerEndpoint("/perfiles/swagger/v1/swagger.json", "Perfiles Service API v1");
    c.RoutePrefix = "perfiles/swagger";
});


Db.Inicializar();
await RabbitMqConsumer.Iniciar();

app.MapGet("/health", () =>
{
    var (codigo, cuerpo) = HealthService.Evaluar(Db.EstaDisponible, () => RabbitMqConsumer.Conectado);
    return Results.Json(cuerpo, statusCode: codigo);
})
    .WithSummary("Estado del servicio y de sus dependencias (base de datos, broker). 200 si UP, 503 si DOWN");

app.MapGet("/perfiles/{empleadoId}", (string empleadoId) =>
{
    var perfil = PerfilRepo.ObtenerPorEmpleadoId(empleadoId);
    return perfil is null
        ? Results.NotFound(new { status = 404, mensaje = $"No existe un perfil para el empleado {empleadoId}" })
        : Results.Ok(perfil);
})
    .WithSummary("Consulta el perfil de un empleado por su empleadoId");

app.MapPut("/perfiles/{empleadoId}", (string empleadoId, PerfilActualizacion body) =>
{
    var actualizado = PerfilRepo.Actualizar(empleadoId, body.Telefono, body.Direccion, body.Ciudad, body.Biografia);
    if (!actualizado)
    {
        return Results.NotFound(new { status = 404, mensaje = $"No existe un perfil para el empleado {empleadoId}" });
    }
    return Results.Ok(PerfilRepo.ObtenerPorEmpleadoId(empleadoId));
})
    .WithSummary("Actualiza telefono, direccion, ciudad y/o biografia de un perfil");

app.MapGet("/perfiles", () => Results.Ok(PerfilRepo.ListarTodos()))
    .WithSummary("Lista todos los perfiles registrados");

app.Run("http://0.0.0.0:8083");