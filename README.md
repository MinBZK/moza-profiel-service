# Profiel service

## hoe generate ik een client gebaseerd op een openapi spec

dotnet tool install --global NSwag.ConsoleCore //indien nodig

nswag openapi2csclient /input:api_basisprofiel.yaml /output:ProfielService/services/clients/KvkClient.cs /classname:KvkProfielClient /namespace:ProfielService.services.clients


## Database lokaal todo

podman run --name ProfielDb -e POSTGRES_PASSWORD=password -p 5432:5432 harbor.cicd.s15m.nl/docker-hub-proxy/postgres:17.5
