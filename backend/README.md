# House System Backend

Spring Boot service for the house system project. Keep backend source, configuration, and backend-specific documentation in this directory.

## Requirements

- JDK 17
- Maven 3.6.3 or newer
- MySQL matching the schema maintained by the project

The project uses Spring Boot 3.5 and MyBatis. It does not create or modify the database schema on startup.

## Configure and run

Set these environment variables in the IDE run configuration or shell when needed:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile |
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | Local `house_system` MySQL URL | JDBC URL |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | empty | MySQL password; set this locally, do not commit secrets |
| `AI_PRICE_BASE_URL` | `http://127.0.0.1:8001` | Internal price-estimation service base URL |
| `AI_TEXT_BASE_URL` | `http://127.0.0.1:5001` | Internal text-analysis service base URL |

From this directory, run:

```shell
mvn spring-boot:run
```

Copy `src/main/resources/application-dev.example.yml` to `application-dev.yml` if you want the development-only logging settings. The actuator health endpoint is `GET http://localhost:8080/actuator/health`.

## Structure

- `common`: shared API response, pagination, business exception, and error responses
- `config`: cross-origin, upload, and internal service configuration
- `security`, `auth`, `system`, `owner`, `customer`, `showing`, `deal`, `ai`, `report`: package boundaries for the remaining backend modules
- `house`: paged house search and house detail API
- `src/main/resources`: Spring profiles and MyBatis/database configuration

New business features should be added as focused packages under `system` (for example, `owner`, `customer`, or `deal`) with controller, service, mapper, and model kept together. Keep controller code thin and put business rules in services.
