FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY *.csproj .
RUN dotnet restore

COPY . .
RUN dotnet publish -c Release -o /app/publish

FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /app

RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/publish .

EXPOSE 8083

HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=15s \
    CMD wget -qO- http://localhost:8083/health || exit 1

ENTRYPOINT ["dotnet", "perfiles-service.dll"]