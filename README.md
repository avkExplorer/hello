# hello

Small Spring Boot 4 (Java 21) service that proves the AVK deployment pipeline end to end. Public on purpose: the
platform's free security features need a public repository. Nothing in it is secret.

| Path | Purpose |
|---|---|
| `GET /` | static page |
| `GET /healthz` | liveness: process is up |
| `GET /readyz` | readiness: database reachable (503 otherwise) |
| `GET /api/v1/ping` | `{"message":"pong"}`, read from the database seeded by Flyway |

## Run and test

```powershell
$env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"   # only needed behind Norton
.\mvnw.cmd verify          # unit + API tests, 70% line-coverage gate
.\run-local.cmd            # app + throwaway Postgres at http://127.0.0.1:4820/
```

Configuration comes only from environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`). `java -jar /app/app.jar --migrate-only` applies Flyway migrations and exits.
The platform contract is in `avk.yaml`. Releases are built, scanned, signed and approved by the pipeline in
[avk-platform](https://github.com/avkExplorer/avk-platform).
