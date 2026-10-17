# ReleEasy Cloud: contexto para Claude Code

Trabajo Fin de Máster. MVP de una plataforma de autoservicio para que PYMEs desplieguen aplicaciones en AWS sin gestionar infraestructura. **Se prioriza código claro, organizado y documentado por encima de funcionalidades extra.** La documentación y los comentarios se escriben en español.

## Flujo que demuestra el MVP

1. El administrador registra un cliente en el portal. La API crea una **cuenta miembro de AWS** con AWS Organizations (`CreateAccount` y polling de `DescribeCreateAccountStatus`), desde la cuenta de administración.
2. El administrador registra una aplicación del cliente: repo de GitHub `owner/repo`, rama `master` y plan. El repo es externo y del cliente; no forma parte de este proyecto.
3. Un merge de un PR a `master` en el repo del cliente provoca un `push` y GitHub envía un webhook a `POST /api/webhooks/github`.
4. Step Functions + CodeBuild construyen el front Angular y el back Spring Boot del cliente, publican los artefactos y despliegan con Terraform en la cuenta del cliente.
5. El portal muestra el estado del release (`PENDING`, `RUNNING`, `SUCCEEDED` o `FAILED`) y la URL pública.

## Estado (actualizado 2026-10-17)

| Fase | Estado |
|---|---|
| 1. Estructura y dependencias | ✅ Terminada |
| 2. Backend | ✅ Terminada: 43 pruebas en verde y flujo completo verificado en modo mock con curl |
| 3. Frontend (portal) | ✅ Terminada: Tailwind 4, 4 pantallas, 19 pruebas Vitest en verde, build correcto e integración con la API comprobada (config.json y CORS) |
| 4. Terraform y pipelines | ⏳ **SIGUIENTE**: `backend/deploy/*` vacías |
| 5. Documentación | 🟡 Existen `backend/docs/decisiones.md` (D-01 a D-16) y `frontend/README.md` |

**Git:** hay commits locales en `main`, con remoto `origin` = https://github.com/jdan2/ReleEasy-Cloud. **El push está pendiente:** la cuenta `Marmolito` aún no tiene permiso de escritura (error 403). Cuando lo tenga, ejecutar `git push -u origin main`.

**Docker Desktop:** instalado, pero el usuario tiene que abrir la app (`open -a Docker`) para que arranque el motor.

## Reglas de trabajo (del enunciado original)

- Trabajar por fases y mostrar un resumen al terminar cada una.
- **No ejecutar nada que cree o modifique recursos reales en AWS** (`terraform apply`, `CreateAccount` real, creación de webhooks en GitHub) sin confirmación explícita del usuario.
- **Pedir confirmación antes de instalar herramientas globales del sistema.**
- Nunca guardar secretos en el repo: usar variables de entorno, `.env.example` y Secrets Manager en AWS.
- En la raíz solo están `frontend/` y `backend/`, más `DEPENDENCIAS.txt`, `CLAUDE.md`, `.gitignore` y `.vscode/` (ver D-13). No crear más carpetas en la raíz.
- Si algo es ambiguo, elegir la opción más simple que cumpla el MVP y anotarla en `backend/docs/decisiones.md` (siguiente número: **D-17**).
- **Registrar toda dependencia, herramienta, extensión, provider de Terraform o imagen base en `DEPENDENCIAS.txt`**, con versión, ámbito, forma de instalación y fecha, y añadir una línea al historial del final.

## Entorno (macOS 15.5, Apple Silicon)

- Node 24.22.0 vía nvm (`frontend/.nvmrc`), Angular CLI 22.2.3, OpenJDK 21 (Homebrew `openjdk@21`, con `JAVA_HOME` en `~/.zshrc`), Terraform 1.16.6, AWS CLI 2.38.1, Docker Desktop 4.95.0, gh 2.102.0.
- En comandos no interactivos, exportar antes:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"`
  y para Node: `export NVM_DIR="$HOME/.nvm"; . "$NVM_DIR/nvm.sh"; nvm use 24.22.0`.
- Cuidado: los comandos Bash que se lanzan en paralelo comparten el directorio de trabajo. Usar siempre rutas absolutas.

## Backend (hecho): `backend/`

- Java 21, **Spring Boot 3.5.16** (D-01) y AWS SDK v2 2.56.0, con Maven Wrapper (`./mvnw`).
- Paquete `com.releasy`, en capas: `domain`, sin dependencias; `application`, con los casos de uso y los puertos `AccountProvisioner` y `DeploymentOrchestrator`, sin anotaciones de Spring; `infrastructure`, con `mock/`, `memory/`, `aws/`, `dynamodb/` y `config/`; y `web`.
- `releasy.aws.mode=mock|real` (por defecto `mock`). En mock se usan repos en memoria, `MockAccountProvisioner` y `MockDeploymentOrchestrator`.
- Endpoints: `POST/GET /api/tenants`, `GET /api/tenants/{id}`, `POST/GET /api/tenants/{id}/apps`, `GET /api/apps/{id}`, `GET /api/apps/{id}/releases`, `POST /api/webhooks/github` y `GET /actuator/health`.
- Seguridad: HTTP Basic con un administrador (`RELEASY_ADMIN_USER` / `RELEASY_ADMIN_PASSWORD`). Un 401 se devuelve **sin** `WWW-Authenticate`. CORS permite por defecto `http://localhost:4200` (`RELEASY_CORS_ALLOWED_ORIGINS`). El webhook y el health son públicos.
- Modelo JSON:
  - `TenantResponse{id,name,email,status(CREATING|ACTIVE|FAILED),accountId,roleArn,failureReason,createdAt,updatedAt}`
  - `AppResponse{id,tenantId,name,repository,branch,plan(BASIC|STANDARD),url,createdAt,updatedAt}`
  - `ReleaseResponse{id,appId,commitSha,commitMessage,status,url,errorMessage,createdAt,updatedAt}`
  - `POST /api/tenants` recibe `{name,email}`.
  - `POST /api/tenants/{id}/apps` recibe `{name (^[a-z][a-z0-9-]{2,19}$), repository (owner/repo), branch?, plan}`. Responde 409 si el cliente no está ACTIVE.
  - Los errores siguen el formato ProblemDetail (RFC 9457).
- DynamoDB (D-11): `tenants` (PK `tenantId`); `apps` (PK `appId`, GSIs `tenantId-index` y `repository-index`); `releases` (PK `appId`, SK `releaseId` = `r-yyyyMMddHHmmss-xxxxxx`). Nombres por defecto: `releasy-tenants`, `releasy-apps` y `releasy-releases`.
- **Contrato con los pipelines (D-10):** en modo real, la API crea el release en PENDING y llama a `StartExecution` con `name=releaseId` y la entrada `DeploymentRequest{releaseId,tenantId,appId,appName,accountId,tenantRoleArn,repository,branch,commitSha,commitMessage,plan}`. Después, la máquina de estados y `buildspec-deploy.yml` escriben en DynamoDB los atributos `status`, `url`, `errorMessage` y `updatedAt` (ISO-8601) del release, y la `url` de la app al terminar bien.
- Disparadores de la demo en mock (D-12): un email con `+fail` hace fallar la cuenta; un commit con `[fail]` hace fallar el release. El perfil `mock-fast` acorta los tiempos.
- Arranque en local: F5 en VS Code ("ReleEasy API (mock)", que lee `backend/.env`, no versionado), o
  `cd backend && set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=mock-fast ./mvnw spring-boot:run`.

## Portal (hecho): `frontend/`

- Angular 22 con standalone components, zoneless y TypeScript `strict` + `strictTemplates`. **Tailwind CSS 4.3.3** como plugin de PostCSS (`.postcssrc.json`) y `src/styles.css` con clases de componente (`.card`, `.btn-primary`, `.form-input`, `.data-table`...) hechas con `@apply`. Sin Angular Material (retirado, D-06). Estilos en CSS, no SCSS (D-16).
- `main.ts` lee `public/config.json` (`{"apiUrl": ...}`) **antes** de arrancar Angular y lo inyecta como el token `APP_CONFIG` (`core/config/app-config.ts`).
- `core/api`: `models.ts` (tipos de los DTOs) y `ReleasyApi` (cliente HTTP). `core/auth`: `AuthService` (Basic en `sessionStorage`, D-15), `authInterceptor` (solo para peticiones a `apiUrl`; un 401 lleva al login) y `authGuard`.
- `shared`: `StatusBadge` (muestra el código de estado con una descripción en español), `Poller` (`setTimeout` sin solapes, cancelado con `DestroyRef`), `polling-policy.ts` (3 s si hay algo en curso, 15 s en reposo; D-14) y `describeHttpError` (lee el `detail` de ProblemDetail).
- `features`: `login`, `tenants/tenant-list.page` (alta y lista), `tenants/tenant-detail.page` (cuenta, apps y alta de app; formulario desactivado si el cliente no está ACTIVE) y `apps/app-detail.page` (URL marcada como provisional hasta el primer SUCCEEDED, e historial de releases).
- Las rutas reciben sus parámetros como inputs (`withComponentInputBinding`). Pantallas cargadas en diferido.
- Pruebas: `npx ng test --watch=false` (19 pruebas). Los datos de prueba compartidos están en `src/testing/test-data.ts`.
- Arranque: `cd frontend && npx ng serve` (http://localhost:4200). Necesita la API en `http://localhost:8080`.
- **Pendiente para la fase 4:** decidir cómo se publica el portal en AWS (p. ej. S3 + CloudFront en la cuenta de administración, con su propio `config.json`) y anotarlo como D-17.

## Fase 4, Terraform y pipelines: `backend/deploy/` (pendiente)

- Terraform >= 1.10, backend S3 con `use_lockfile = true`. Variables con descripción y validaciones; nada sensible en el código. Validar con `terraform init -backend=false` y `terraform validate` en cada carpeta. **Nunca `apply` sin confirmación.**
- `platform/` (cuenta de administración): bucket de estados (versionado, cifrado y acceso público bloqueado), las 3 tablas DynamoDB con los GSIs anteriores, ECR, proyectos de CodeBuild (build y deploy), Step Functions y roles IAM con mínimo privilegio. El rol de CodeBuild debe poder asumir el rol de despliegue en las cuentas de los clientes (`OrganizationAccountAccessRole`).
- `templates/angular-springboot/` (cuenta de cada cliente):
  - `provider "aws"` con `assume_role` (`tenant_role_arn`).
  - VPC con subredes públicas y privadas.
  - ECS Fargate + ALB con health check en `/actuator/health` y deployment circuit breaker con rollback.
  - Bucket S3 privado + CloudFront con OAC. CloudFront enruta `/*` al front y `/api/*` al ALB, en el mismo dominio y sin CORS.
  - El plan determina el tamaño: BASIC = 0,25 vCPU, 512 MiB y 1 tarea; STANDARD = 0,5 vCPU, 1 GiB y 2 tareas.
  - Outputs: URL pública, nombre del bucket, ID de la distribución, y nombres del cluster y del servicio.
- `pipelines/`:
  - `buildspec-build.yml`: clona el repo del cliente en el SHA, ejecuta `npm ci` y `ng build` en `/frontend` y `./mvnw -B verify` en `/backend`, y construye la imagen Docker con el SHA como tag. Si el repo no tiene Dockerfile, usa uno de la plataforma basado en `eclipse-temurin:21-jre`. **Contrato del repo del cliente:** front en `/frontend` y back en `/backend`; hay que documentarlo.
  - `buildspec-deploy.yml`: `terraform init` con backend parcial (clave `tenants/<tenantId>/<appId>/terraform.tfstate`), `plan` y `apply` pasando `image_tag` y `tenant_role_arn`. Después sube el front a S3 e invalida CloudFront. Guarda los outputs y el estado final en DynamoDB, también cuando falla (`post_build`).
  - `state-machine.asl.json`: marca RUNNING, luego build, deploy y actualización de estado, con Catch que marque el release como `FAILED`.

## Fase 5, documentación: `backend/docs/` (pendiente, en español)

- README de puesta en marcha: en local en modo mock y en AWS.
- Diagrama de arquitectura en Mermaid.
- Pasos manuales en AWS y GitHub: habilitar Organizations, registrar el webhook con su secreto, crear el token de GitHub y guardarlo en Secrets Manager.
- Verificación final: todo compila, las pruebas pasan, y cómo arrancar el front y el back en local en modo mock.

## Commits

Terminar los mensajes de commit con:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
