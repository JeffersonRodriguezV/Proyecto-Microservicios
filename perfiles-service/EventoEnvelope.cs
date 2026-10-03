using System.Text.Json;
using System.Text.Json.Serialization;

public class EventoEnvelope
{
    [JsonPropertyName("id")] public string Id { get; set; } = "";
    [JsonPropertyName("type")] public string Type { get; set; } = "";
    [JsonPropertyName("version")] public int Version { get; set; }
    [JsonPropertyName("occurredAt")] public string OccurredAt { get; set; } = "";
    [JsonPropertyName("producer")] public string Producer { get; set; } = "";
    [JsonPropertyName("data")] public JsonElement Data { get; set; }
}