using System.Text.Json;
using Xunit;
namespace PerfilesService.Tests;

/// <summary>
/// Pruebas unitarias de la regla de negocio de perfiles ante eventos de empleados.
/// Sin SQLite, sin RabbitMQ, sin Docker: el almacén es un fake en memoria.
/// </summary>
public class ProcesadorEventosTests
{
    private static readonly object Ana = new { empleadoId = "E001", nombre = "Ana", email = "ana@empresa.com" };

    private static EventoEnvelope Evento(string id, string tipo, object data) => new()
    {
        Id = id,
        Type = tipo,
        Version = 1,
        OccurredAt = "2026-10-14T00:00:00Z",
        Producer = "empleados-service",
        Data = JsonSerializer.SerializeToElement(data)
    };

    private static (ProcesadorEventos procesador, AlmacenPerfilesFake almacen, List<string> log) Armar()
    {
        var almacen = new AlmacenPerfilesFake();
        var log = new List<string>();
        return (new ProcesadorEventos(almacen, log.Add), almacen, log);
    }

    // ---------- empleado.creado ----------

    [Fact]
    public void Creado_crea_el_perfil_por_defecto_con_los_datos_del_evento()
    {
        var (procesador, almacen, _) = Armar();

        var resultado = procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, Ana));

        Assert.Equal(ResultadoProcesamiento.Aplicado, resultado);
        Assert.True(almacen.Perfiles.ContainsKey("E001"));
        var perfil = almacen.Perfiles["E001"];
        Assert.Equal("Ana", perfil.Nombre);
        Assert.Equal("ana@empresa.com", perfil.Email);
        Assert.False(perfil.Archivado);
    }

    [Fact]
    public void Creado_registra_el_evento_como_procesado_y_lo_deja_en_el_log()
    {
        var (procesador, almacen, log) = Armar();

        procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, Ana));

        Assert.True(almacen.FueProcesado("ev-1"));
        Assert.Contains("Perfil creado para empleado E001", log);
    }

    [Fact]
    public void Creado_con_nombre_nulo_guarda_texto_vacio_en_lugar_de_fallar()
    {
        var (procesador, almacen, _) = Armar();

        procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado,
            new { empleadoId = "E003", nombre = (string?)null, email = "x@empresa.com" }));

        Assert.Equal("", almacen.Perfiles["E003"].Nombre);
    }

    // ---------- deduplicación ----------

    [Fact]
    public void El_mismo_evento_dos_veces_se_descarta_y_el_perfil_se_crea_una_sola_vez()
    {
        var (procesador, almacen, log) = Armar();
        var evento = Evento("ev-1", ProcesadorEventos.TipoCreado, Ana);

        var primero = procesador.Procesar(evento);
        var segundo = procesador.Procesar(evento);

        Assert.Equal(ResultadoProcesamiento.Aplicado, primero);
        Assert.Equal(ResultadoProcesamiento.Duplicado, segundo);
        Assert.Equal(1, almacen.Creaciones);
        Assert.Contains(log, linea => linea.Contains("ya procesado"));
    }

    // ---------- empleado.actualizado ----------

    [Fact]
    public void Actualizado_sincroniza_nombre_y_email_sin_archivar()
    {
        var (procesador, almacen, _) = Armar();
        procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, Ana));

        procesador.Procesar(Evento("ev-2", ProcesadorEventos.TipoActualizado,
            new { empleadoId = "E001", nombre = "Ana María", email = "ana.maria@empresa.com" }));

        var perfil = almacen.Perfiles["E001"];
        Assert.Equal("Ana María", perfil.Nombre);
        Assert.Equal("ana.maria@empresa.com", perfil.Email);
        Assert.False(perfil.Archivado);
    }

    // ---------- empleado.retirado ----------

    [Fact]
    public void Retirado_archiva_el_perfil_pero_no_lo_borra()
    {
        var (procesador, almacen, _) = Armar();
        procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, Ana));

        procesador.Procesar(Evento("ev-2", ProcesadorEventos.TipoRetirado, new { empleadoId = "E001" }));

        Assert.True(almacen.Perfiles.ContainsKey("E001"), "el perfil debe seguir existiendo");
        Assert.True(almacen.Perfiles["E001"].Archivado);
    }

    [Fact]
    public void Retirar_a_un_empleado_no_archiva_los_perfiles_de_los_demas()
    {
        var (procesador, almacen, _) = Armar();
        procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, Ana));
        procesador.Procesar(Evento("ev-2", ProcesadorEventos.TipoCreado,
            new { empleadoId = "E002", nombre = "Luis", email = "luis@empresa.com" }));

        procesador.Procesar(Evento("ev-3", ProcesadorEventos.TipoRetirado, new { empleadoId = "E001" }));

        Assert.True(almacen.Perfiles["E001"].Archivado);
        Assert.False(almacen.Perfiles["E002"].Archivado);
    }

    [Fact]
    public void Ciclo_completo_creado_actualizado_retirado_deja_el_perfil_archivado_con_los_datos_nuevos()
    {
        var (procesador, almacen, _) = Armar();

        procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, Ana));
        procesador.Procesar(Evento("ev-2", ProcesadorEventos.TipoActualizado,
            new { empleadoId = "E001", nombre = "Ana María", email = "ana.maria@empresa.com" }));
        procesador.Procesar(Evento("ev-3", ProcesadorEventos.TipoRetirado, new { empleadoId = "E001" }));

        var perfil = almacen.Perfiles["E001"];
        Assert.Equal("Ana María", perfil.Nombre);
        Assert.True(perfil.Archivado);
    }

    // ---------- fallos y casos límite ----------

    [Fact]
    public void Si_falla_el_almacen_el_evento_NO_se_marca_como_procesado_y_un_reintento_lo_aplica()
    {
        var (procesador, almacen, _) = Armar();
        var evento = Evento("ev-1", ProcesadorEventos.TipoCreado, Ana);
        almacen.FallarAlCrear = true;

        Assert.Throws<InvalidOperationException>(() => procesador.Procesar(evento));
        Assert.False(almacen.FueProcesado("ev-1"), "un evento fallido no debe quedar como procesado");

        almacen.FallarAlCrear = false; // la base de datos se recupera
        var reintento = procesador.Procesar(evento);

        Assert.Equal(ResultadoProcesamiento.Aplicado, reintento);
        Assert.True(almacen.Perfiles.ContainsKey("E001"));
    }

    [Fact]
    public void Un_tipo_de_evento_desconocido_no_toca_los_perfiles_pero_queda_registrado()
    {
        var (procesador, almacen, _) = Armar();

        var resultado = procesador.Procesar(Evento("ev-1", "empleado.otra-cosa", Ana));

        Assert.Equal(ResultadoProcesamiento.Aplicado, resultado);
        Assert.Empty(almacen.Perfiles);
        Assert.True(almacen.FueProcesado("ev-1"));
    }

    [Fact]
    public void Un_payload_sin_empleadoId_falla_y_no_se_marca_como_procesado()
    {
        var (procesador, almacen, _) = Armar();

        Assert.Throws<KeyNotFoundException>(() =>
            procesador.Procesar(Evento("ev-1", ProcesadorEventos.TipoCreado, new { nombre = "Ana" })));

        Assert.False(almacen.FueProcesado("ev-1"));
        Assert.Empty(almacen.Perfiles);
    }


}
