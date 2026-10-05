var builder = WebApplication.CreateBuilder(args);

builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen(c =>
{
c.SwaggerDoc("v1", new Microsoft.OpenApi.OpenApiInfo
    {
        Title = "Perfiles Service API",
        Version = "v1",
        Description = "Gestión de perfiles de empleados, reactivo + REST"
    });
});

var app = builder.Build();

app.UseSwagger(c => c.RouteTemplate = "perfiles/swagger/{documentName}/swagger.json");
app.UseSwaggerUI(c =>
{
    c.SwaggerEndpoint("/perfiles/swagger/v1/swagger.json", "Perfiles Service API v1");
    c.RoutePrefix = "perfiles/swagger";
});


Db.Inicializar();
await RabbitMqConsumer.Iniciar();

app.MapGet("/health", () => Results.Ok(new { servicio = "perfiles-service", estado = "activo" }))
    .WithSummary("Verifica que el servicio esté activo");

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