# Profiel service

## Hoe generate ik een client gebaseerd op een openapi spec

dotnet tool install --global NSwag.ConsoleCore //indien nodig

nswag openapi2csclient /input:src/Moza.ProfielService.Api/External/OpenApiSpecs/api_basisprofiel.yaml /output:src/Moza.ProfielService.Api/External/Clients/KvkProfielClient.cs /classname:KvkProfielClient /namespace:Moza.ProfielService.Api.External.Clients

## Database lokaal todo

podman run --name ProfielDb -e POSTGRES_PASSWORD=password -p 5432:5432 harbor.cicd.s15m.nl/docker-hub-proxy/postgres:17.5
