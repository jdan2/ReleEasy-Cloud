# ReleEasy Cloud: contexto para Claude Code

Trabajo Fin de Máster. Plataforma de autoservicio para que PYMEs desplieguen aplicaciones **en su propia cuenta de AWS** sin gestionar infraestructura. **Se prioriza código claro, organizado y documentado por encima de funcionalidades extra.** La documentación y los comentarios se escriben en español.

## Alcance actual (tras el cambio de alcance del 2026-10-17, D-18 a D-22)

1. El **cliente se registra** en el portal (empresa, email y contraseña) e inicia sesión. Hay dos roles: `CLIENT` y `ADMIN`.
2. **Conecta su cuenta de AWS:** el portal le da un External ID y un enlace *Quick-create* de CloudFormation que crea el rol `ReleEasyDeployRole` (confía en la cuenta de la plataforma y exige el External ID). Después pega el ARN del rol y la plataforma lo verifica con `sts:AssumeRole`. **Ya no se usa AWS Organizations ni `CreateAccount`** (D-18).
3. **Conecta GitHub** instalando la GitHub App de ReleEasy, que da acceso a los repos elegidos y configura el webhook de push.
4. **Elige una plantilla** del catálogo: Angular, Spring Boot o Angular + Spring Boot. Rellena su **formulario de componentes opcionales** (observabilidad, WAF, dominio, API Gateway, SQS, autoescalado, variables de entorno…), le **asigna un repositorio y una rama**, e indica su **uso estimado**. Mientras tanto ve el **coste mensual estimado**, separado en AWS y cuota de ReleEasy (D-20, D-22).
5. Al crear la app se despliega el último commit, y después cada **merge a la rama**: el webhook dispara Step Functions + CodeBuild + Terraform en la cuenta del cliente.
6. El cliente ve sus apps, su URL, sus componentes, su coste y sus releases (`PENDING`, `RUNNING`, `SUCCEEDED` o `FAILED`). El **administrador** ve a todos los clientes, sus conexiones y sus apps, en solo lectura.
7. **Base de datos:** fuera de alcance por ahora (D-19).

## Estado (actualizado 2026-10-17)

| Pieza | Estado |
|---|---|
| 1. Estructura y dependencias | ✅ |
| 2. Backend | 🟠 **Hecho para el alcance ORIGINAL** (admin + Organizations, 43 pruebas). **Hay que adaptarlo al nuevo alcance**; ver "Backend: pendiente de adaptar". |
| 3. Portal | ✅ **Nuevo alcance completo, en modo demo** (`mockApi: true`): vistas de cliente y de admin, registro, conexiones AWS y GitHub, catálogo, formulario dinámico, estimador de costes y releases. 46 pruebas Vitest en verde. |
| 4. Terraform y pipelines | ⏳ Sin empezar (`backend/deploy/*` vacías). Hay que hacerlo **con el nuevo alcance**. |
| 5. Documentación | 🟡 `backend/docs/decisiones.md` (D-01 a D-22) y `frontend/README.md` |

**Prioridad del usuario:** de momento le interesa sobre todo el **flujo del front**. Antes de empezar con el backend o el Terraform, preguntarle por dónde seguir.

**Git:** hay commits locales en `main`, con remoto `origin` = https://github.com/jdan2/ReleEasy-Cloud. **El push está pendiente:** la cuenta `Marmolito` no tiene permiso de escritura (error 403).

**Docker Desktop:** instalado; el usuario tiene que abrirlo (`open -a Docker`) para que arranque el motor.

## Reglas de trabajo

- Trabajar por fases y mostrar un resumen al terminar cada una.
- **No ejecutar nada que cree o modifique recursos reales en AWS** (`terraform apply`, `AssumeRole` contra cuentas reales, creación de la GitHub App o de webhooks) sin confirmación explícita del usuario.
- **Pedir confirmación antes de instalar herramientas globales del sistema.**
- Nunca guardar secretos en el repo: usar variables de entorno, `.env.example` y Secrets Manager en AWS.
- En la raíz solo están `frontend/` y `backend/`, más `DEPENDENCIAS.txt`, `CLAUDE.md`, `.gitignore` y `.vscode/` (D-13).
- Si algo es ambiguo, elegir la opción más simple que cumpla el objetivo y anotarla en `backend/docs/decisiones.md` (siguiente número: **D-23**).
- **Registrar toda dependencia, herramienta, extensión, provider de Terraform o imagen base en `DEPENDENCIAS.txt`** (con versión, ámbito, forma de instalación y fecha) y añadir una línea al historial del final.
- El usuario suele tener su propio `ng serve` en el puerto 4200: hacer las pruebas en otro puerto y no matar su proceso.

## Entorno (macOS 15.5, Apple Silicon)

- Node 24.22.0 vía nvm (`frontend/.nvmrc`), Angular CLI 22.2.3, OpenJDK 21 (Homebrew `openjdk@21`, con `JAVA_HOME` en `~/.zshrc`), Terraform 1.16.6, AWS CLI 2.38.1, Docker Desktop 4.95.0 y gh 2.102.0.
- En comandos no interactivos, exportar antes:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"`
  y para Node: `export NVM_DIR="$HOME/.nvm"; . "$NVM_DIR/nvm.sh"; nvm use 24.22.0`.
- Los comandos Bash que se lanzan en paralelo comparten el directorio de trabajo: usar rutas absolutas.

## Portal (nuevo alcance, modo demo): `frontend/`

- Angular 22 con standalone components, zoneless y TS `strict` + `strictTemplates`. Tailwind CSS 4.3.3 (PostCSS) y clases de componente en `src/styles.css` (`.card`, `.btn-primary`, `.form-input`, `.data-table`…). Sin Angular Material (D-06); estilos en CSS (D-16).
- `main.ts` lee `public/config.json` (`{"apiUrl", "mockApi"}`) antes de arrancar Angular (token `APP_CONFIG`). **`mockApi: true`** activa el modo demo (D-17): `core/mock/mock-api.interceptor.ts` responde con `MockBackend`, que guarda el estado en `localStorage` (botón "Restablecer datos" en la franja amarilla).
- Usuarios de demostración: cliente `demo@ferreteria-lopez.es` / `demo1234` (AWS y GitHub conectados, 2 apps) y administrador `admin` / `admin`.
- **Contrato de la API (D-21), definido en `core/api/models.ts` y `core/api/releasy-api.service.ts`:**
  - Autenticación: `POST /api/auth/register {companyName,email,password}` y `POST /api/auth/login {email,password}`, que devuelven `Session{token,user{id,email,name,role,tenantId}}`. Después, `Authorization: Bearer <token>`.
  - Cliente:
    - `GET /api/me/tenant` → `Tenant{id,name,email,aws{status,externalId,platformAccountId,roleArn,accountId,connectedAt},github{status,accountLogin,installationId,connectedAt},appCount,createdAt}`
    - `POST /api/me/aws-connection {roleArn}` y `DELETE` para desconectar
    - `POST` y `DELETE /api/me/github-connection`
    - `GET /api/me/github/repositories` → `[{fullName,description,private,defaultBranch,branches[]}]`
    - `GET` y `POST /api/me/apps` (`{name,templateId,repository,branch,options,usage}`)
  - Catálogo: `GET /api/templates` y `GET /api/templates/{id}`.
  - Administrador: `GET /api/tenants`, `GET /api/tenants/{id}` y `GET /api/tenants/{id}/apps`.
  - App (propietario o admin): `GET /api/apps/{id}` y `GET /api/apps/{id}/releases`.
  - `App{id,tenantId,name,templateId,repository,branch,options,usage,url,createdAt,updatedAt}`. Errores en formato ProblemDetail.
- **Plantillas (D-22):** `core/templates/template-catalog.ts` define de forma declarativa las 3 plantillas, con grupos de opciones (`toggle`, `select`, `number`, `text` y `keyvalue`, más `visibleWhen`), campos y perfiles de uso. Incluye `validateOptions` (que la API debe replicar) y `describeOptions`.
- **Costes (D-20):** precios de referencia de eu-west-1 en `core/pricing/aws-prices.ts` y la función pura `estimateMonthlyCost(template, options, usage)` en `core/pricing/cost-estimator.ts`. La cuota simulada es de 9 USD por app y mes.
- Rutas:
  - `/login` y `/register`;
  - `/portal` (inicio con los pasos de configuración y las apps), `/portal/aws`, `/portal/github`, `/portal/templates`, `/portal/templates/:templateId/new` (formulario + coste en vivo) y `/portal/apps/:appId`;
  - `/admin`, `/admin/tenants/:tenantId` y `/admin/apps/:appId`;
  - protegidas con `roleGuard`.
- Pruebas: `npx ng test --watch=false` (46). Arranque: `cd frontend && npx ng serve`.

## Backend: pendiente de adaptar al nuevo alcance (`backend/`)

**Lo que existe** (alcance original, D-01 a D-12):
- Java 21, Spring Boot 3.5.16 y AWS SDK v2 2.56.0, con capas `domain`, `application` (puertos `AccountProvisioner` y `DeploymentOrchestrator`), `infrastructure` (mock/memory/aws/dynamodb/config) y `web`.
- `releasy.aws.mode=mock|real`.
- Webhook `POST /api/webhooks/github` con HMAC-SHA256, filtro de push y reenvíos; estados del release (`ReleaseStatus`); repos DynamoDB; Basic con un administrador.
- 43 pruebas.
- Arranque: F5 "ReleEasy API (mock)", o `cd backend && set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=mock-fast ./mvnw spring-boot:run`.

**Lo que hay que cambiar:**
- Usuarios con rol (`ADMIN`/`CLIENT`), registro, login con JWT y contraseñas con BCrypt. Una tabla `users` (o un GSI por email).
- Sustituir `AccountProvisioner` (Organizations) por un puerto `AwsConnectionVerifier` (`sts:AssumeRole` con External ID). Tenant con `aws{...}` y `github{...}`.
- GitHub App: instalación (`installation_id`), lista de repos con un token de instalación y webhook de la app. El webhook HMAC actual se reutiliza.
- Plantillas: catálogo, validación de opciones (la misma que en el front) y `App{templateId,options,usage}` en lugar de `plan`.
- Endpoints `/api/me/*`, `/api/templates`, y autorización por propietario en `/api/apps/{id}`.
- `DeploymentRequest`: añadir `templateId`, `options` y `externalId`, y quitar `plan`.

## Fase 4, Terraform y pipelines: `backend/deploy/` (pendiente, con el nuevo alcance)

- Terraform >= 1.10, backend S3 con `use_lockfile = true`. Variables con descripción y validaciones. Validar con `terraform init -backend=false` y `terraform validate`. **Nunca `apply` sin confirmación.**
- `platform/` (cuenta de la plataforma): bucket de estados (versionado, cifrado y sin acceso público), tablas DynamoDB, ECR, CodeBuild (build y deploy), Step Functions e IAM con mínimo privilegio. El rol de CodeBuild asume `ReleEasyDeployRole` **con External ID** en la cuenta del cliente.
- **Plantilla de CloudFormation del rol del cliente** (`releasy-deploy-role.yaml`): parámetros `PlatformAccountId` y `ExternalId`, permisos limitados a los servicios de las plantillas y salida `RoleArn`.
- `templates/`: **tres módulos**, `angular-static`, `springboot-api` y `angular-springboot` (o módulos comunes `frontend` y `backend` compuestos). Cada opción del catálogo es una variable (`enable_waf`, `enable_rum`, `custom_domain`, `api_exposure = alb|api_gateway`, `task_cpu/memory`, `min/max_tasks`, `enable_sqs`, `enable_xray`, `log_retention_days`, `env_vars`, `notification_email`…).
  - Requisitos que se mantienen: VPC pública/privada, ECS Fargate con circuit breaker y rollback, health check en `/actuator/health`, S3 privado + CloudFront con OAC y `/api/*` hacia el backend.
  - Outputs: URL, bucket, distribución, cluster y servicio.
- `pipelines/`:
  - `buildspec-build.yml`: clona en el SHA (con el token de instalación de la GitHub App) y construye según la plantilla (`npm ci && ng build` en `/frontend` y/o `./mvnw -B verify` en `/backend`, más la imagen Docker; si no hay Dockerfile, `eclipse-temurin:21-jre`).
  - `buildspec-deploy.yml`: `terraform init` con backend parcial (`tenants/<tenantId>/<appId>/terraform.tfstate`), `plan` y `apply`, sube el front a S3, invalida CloudFront y escribe el estado en DynamoDB, también si falla.
  - `state-machine.asl.json`: RUNNING → build → deploy → estado final, con Catch que marca `FAILED`.

## Fase 5, documentación: `backend/docs/` (pendiente, en español)

- README de puesta en marcha (local en modo mock y en AWS), diagrama de arquitectura en Mermaid y pasos manuales: crear la GitHub App (permisos, webhook y secreto), publicar la plantilla de CloudFormation del rol y guardar los secretos en Secrets Manager.
- Verificación final: todo compila y pasan las pruebas.

## Commits

Terminar los mensajes de commit con:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
