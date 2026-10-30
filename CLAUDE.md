# ReleEasy Cloud: contexto para Claude Code

Trabajo Fin de Máster. Plataforma de autoservicio para que PYMEs desplieguen aplicaciones **en su propia cuenta de AWS** sin gestionar infraestructura. **Se prioriza código claro, organizado y documentado por encima de funcionalidades extra.** La documentación y los comentarios se escriben en español.

## Alcance actual (cambios de alcance del 2026-10-17 y 2026-10-19, D-18 a D-26)

1. El **cliente se registra** en el portal (empresa, email y contraseña) e inicia sesión. Hay dos roles: `CLIENT` y `ADMIN`.
2. **Conecta su cuenta de AWS:** el portal le da un External ID y un enlace *Quick-create* de CloudFormation que crea el rol `ReleEasyDeployRole` (confía en la cuenta de la plataforma y exige el External ID). Después pega el ARN del rol y la plataforma lo verifica con `sts:AssumeRole`. **Ya no se usa AWS Organizations ni `CreateAccount`** (D-18).
3. **Conecta GitHub** instalando la GitHub App de ReleEasy y **autorizándola con su usuario (OAuth)**. Así da acceso de solo lectura a los repos que elige y la app recibe los push. El cliente no crea nada: solo instala y autoriza, y ve ReleEasy en su GitHub, en *Settings → Applications* (D-25, D-26).
4. **Elige una plantilla** del catálogo: Angular, Spring Boot o Angular + Spring Boot. Rellena su **formulario de componentes opcionales** (observabilidad, WAF, dominio, API Gateway, autoescalado de EC2, variables de entorno…), le **asigna un repositorio y una rama**, e indica su **uso estimado**. Mientras tanto ve el **coste mensual estimado**, separado en AWS y cuota de ReleEasy (D-20, D-22).
5. Al crear la app se despliega el último commit, y después cada **merge a la rama**: el webhook dispara Step Functions + CodeBuild + Terraform en la cuenta del cliente.
6. El cliente ve sus apps, su URL, sus componentes, su coste y sus releases (`PENDING`, `RUNNING`, `SUCCEEDED` o `FAILED`). El **administrador** ve a todos los clientes, sus conexiones y sus apps, en solo lectura.
7. **Base de datos:** fuera de alcance por ahora (D-19).
8. **Sin ECS Fargate ni SQS** (D-23): el backend Spring Boot corre en EC2 con Auto Scaling detrás de un ALB.

## Estado (actualizado 2026-10-24)

| Pieza | Estado |
|---|---|
| 1. Estructura y dependencias | ✅ |
| 2. Backend | ✅ **Nuevo alcance completo** (D-24 a D-26; 62 pruebas): login con JWT, GitHub App **real y probada** (o mock), y AWS, STS y despliegues **simulados**. Falta: AWS real (Terraform) y webhook con URL pública. |
| 3. Portal | ✅ **Conectado a la API** (`mockApi: false` desde D-25; el modo demo sigue disponible): vistas de cliente y de admin, registro, conexiones AWS y GitHub (con `/portal/github/callback`), catálogo, formulario dinámico, estimador de costes y releases. 53 pruebas Vitest. |
| 4. Terraform y pipelines | ✅ **Completa y validada** (sin `apply`): `bootstrap/releasy-deploy-role.yaml` (rol del cliente, D-28); las **tres plantillas** `angular-static` (D-29), `springboot-api` (D-30) y `angular-springboot` (D-31) con módulos `network`/`frontend`/`backend`/`observability`; `platform/` (estado S3, 4 tablas DynamoDB, CodeBuild, Step Functions, IAM; D-32); y `pipelines/` (`buildspec-build.yml`, `buildspec-deploy.yml`, `state-machine.asl.json`; D-33). **Falta solo** activar `RELEASY_AWS_MODE=real` + un `apply` controlado contra AWS para probarlo en vivo (ver limitaciones en D-33). |
| 5. Documentación | 🟡 `backend/docs/decisiones.md` (D-01 a D-33), `backend/docs/github-app.md`, `backend/deploy/README.md`, `frontend/README.md`, `SESION-Y-USUARIOS.md` y la memoria `TFM.docx` (capítulo 4 escrito) |

## Dónde retomar (sesión del 2026-10-24)

**Hecho en la última sesión:**
- **GitHub App real creada y probada:** `releeasy-cloud-dev` (App ID 5457976), con Callback URL `http://localhost:4200/portal/github/callback`. El cliente demo la instaló y autorizó, y la conexión funcionó.
  - Clave privada en `~/.releasy/github-app.pem`, fuera del repo.
  - `RELEASY_GITHUB_MODE=real` y `RELEASY_GITHUB_*` en `backend/.env`; `githubAppSlug` y `githubClientId` en `config.json`.
  - El webhook de la app sigue **desactivado** (`events: []`).
- `SESION-Y-USUARIOS.md` explica dónde se guardan los usuarios (en memoria con `mock`, en DynamoDB con `real`) y la sesión (JWT en `sessionStorage`).
- **Memoria del TFM** (`TFM.docx`, con `PLANTILLA.docx` de UNIR como referencia):
  - Se escribió el **capítulo 4**: requisitos, sistema, decisiones de arquitectura, estado y evaluación, con las tablas 5 a 12.
  - Los capítulos 1 a 3 y el artículo se alinearon con lo implementado: **Angular** (con justificación técnica frente a React), **EC2 con Auto Scaling**, **CloudWatch/X-Ray/RUM** y sin Fargate, RDS ni Prometheus/Grafana.
  - Los índices se rehicieron con campos `TOC` y `SEQ`, y Word pide actualizarlos al abrir.
  - Detalles de formato en la memoria `tfm-document`.

**Para verlo funcionar:**
1. **API:** F5 "ReleEasy API (mock)" (lee `backend/.env`: AWS simulado, GitHub real).
2. **Portal:** `npx ng serve`, **en el puerto 4200**, porque la Callback URL de la GitHub App apunta ahí.
3. **Entrar** con `demo@ferreteria-lopez.es` / `demo1234`.
4. **Recorrer el flujo:**
   - conectar GitHub (instala y autoriza la app real; con `RELEASY_GITHUB_MODE=mock` se simula);
   - conectar AWS con cualquier ARN válido (si contiene `fail`, simula un error);
   - crear una app con un repo real, que se despliega sola (simulado) hasta `SUCCEEDED`.

**Siguientes pasos posibles** (preguntar al usuario por cuál seguir antes de empezar):
1. **Webhook de push en local** con una URL pública (p. ej. smee.io): activar el evento *Push* en la app, con el mismo secreto que `RELEASY_GITHUB_WEBHOOK_SECRET`.
2. **Fase 4:** Terraform, pipelines y la plantilla de CloudFormation del rol del cliente (ver más abajo). **Nunca `apply` sin confirmación.**
3. **Fase 5:** README de puesta en marcha y diagrama de arquitectura (en el TFM queda reservada la **Figura 3**, marcada en amarillo).
4. **TFM:** al cerrar cada fase, actualizar el capítulo 4, sobre todo 4.2.9 (estado) y 4.3 (evaluación). Queda un hueco en amarillo en 4.3.2: falta indicar qué más del flujo probó el usuario.

**Git:** hay un commit local del 2026-10-19 sin subir y, además, cambios sin commitear del 2026-10-24: `config.json`, `CLAUDE.md`, `DEPENDENCIAS.txt`, `SESION-Y-USUARIOS.md`, `TFM.docx` y `PLANTILLA.docx`. Los `.docx` se **versionan a propósito** para compartirlos con el compañero; no añadirlos al `.gitignore`. El usuario hace el `git push`.

**Al cambiar el catálogo** (`template-catalog.ts`), ejecutar `npm run export:catalog` en `frontend/`, porque la API lee el JSON exportado.

**Git:** la rama `main` está subida a https://github.com/jdan2/ReleEasy-Cloud (repo **público**), con `origin/main` como rama de seguimiento. La cuenta `Marmolito` es colaboradora con permiso de escritura (`gh` autenticado).

**Docker Desktop:** instalado; el usuario tiene que abrirlo (`open -a Docker`) para que arranque el motor.

## Reglas de trabajo

- Trabajar por fases y mostrar un resumen al terminar cada una.
- **No ejecutar nada que cree o modifique recursos reales en AWS** (`terraform apply`, `AssumeRole` contra cuentas reales, creación de la GitHub App o de webhooks) sin confirmación explícita del usuario.
- **Pedir confirmación antes de instalar herramientas globales del sistema.**
- Nunca guardar secretos en el repo: usar variables de entorno, `.env.example` y Secrets Manager en AWS.
- En la raíz solo están `frontend/` y `backend/`, más `DEPENDENCIAS.txt`, `SESION-Y-USUARIOS.md` (dónde se guardan usuarios y sesión), `TFM.docx` y `PLANTILLA.docx` (memoria del TFM y plantilla de UNIR, versionadas para el compañero), `CLAUDE.md`, `.gitignore` y `.vscode/` (D-13).
- **Memoria del TFM:** al terminar cada fase, registrar lo hecho en el capítulo 4 de `TFM.docx` (en español, tono académico). Los capítulos 1 a 3 los escriben los autores: no tocarlos sin que lo pidan. No hay pandoc ni LibreOffice: se edita el XML y se valida con el skill docx.
- Si algo es ambiguo, elegir la opción más simple que cumpla el objetivo y anotarla en `backend/docs/decisiones.md` (siguiente número: **D-27**).
- **Registrar toda dependencia, herramienta, extensión, provider de Terraform o imagen base en `DEPENDENCIAS.txt`** (con versión, ámbito, forma de instalación y fecha) y añadir una línea al historial del final.
- El usuario suele tener su propio `ng serve` en el puerto 4200: hacer las pruebas en otro puerto y no matar su proceso. Excepción: para probar la GitHub App, el portal tiene que estar en el 4200 (Callback URL).

## Entorno (macOS 15.5, Apple Silicon)

- Node 24.22.0 vía nvm (`frontend/.nvmrc`), Angular CLI 22.2.3, OpenJDK 21 (Homebrew `openjdk@21`, con `JAVA_HOME` en `~/.zshrc`), Terraform 1.16.6, AWS CLI 2.38.1, Docker Desktop 4.95.0 y gh 2.102.0.
- En comandos no interactivos, exportar antes:
  `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"`
  y para Node: `export NVM_DIR="$HOME/.nvm"; . "$NVM_DIR/nvm.sh"; nvm use 24.22.0`.
- Los comandos Bash que se lanzan en paralelo comparten el directorio de trabajo: usar rutas absolutas.

### Arranque en Windows (PC del autor, sesión del 2026-10-26)

Este equipo es Windows, no macOS; el arranque difiere del bloque anterior.
- **JDK 21** en `C:\Users\juand\.jdks\ms-21.0.11` (el `java` por defecto es un 17 que **no compila** el backend). En IntelliJ: *Project Structure* con SDK 21 y, en la run config, **perfil `mock-fast`** (campo *Active profiles* o `SPRING_PROFILES_ACTIVE=mock-fast`); sin ese perfil no se crea el usuario demo y el login da 401. El `.env` lo carga el plugin **EnvFile** (IntelliJ no lo lee solo); en VS Code lo hace el `launch.json`.
- Por terminal: `$env:JAVA_HOME="C:\Users\juand\.jdks\ms-21.0.11"; .\mvnw.cmd test` (62 pruebas en verde).
- **Node 24.22.0** vía nvm-windows; `node -v` debe decir v24 (si no, `nvm use 24.22.0` en una terminal nueva). Angular 22 **rechaza** Node < 22.22.3.
- **PowerShell bloquea `*.ps1`** (ExecutionPolicy): usar `npm.cmd` / `npx.cmd` / `.\mvnw.cmd`, o los binarios de `node_modules\.bin`.
- Si el 8080 queda ocupado tras "detener" en IntelliJ, es un proceso `java` colgado: hay que terminarlo.
- **AWS CLI** instalado (`C:\Program Files\Amazon\AWSCLIV2\aws.exe`), con credenciales configuradas. Sirve para `aws cloudformation validate-template` (solo valida, no crea nada).
- El `.env` del backend está en `backend\.env` y la clave de la GitHub App en `C:\Users\juand\Documents\Maestria\TFM\github-app.pem` (fuera del repo); `RELEASY_GITHUB_PRIVATE_KEY_PATH` apunta ahí.

## Portal: `frontend/`

- Angular 22 con standalone components, zoneless y TS `strict` + `strictTemplates`. Tailwind CSS 4.3.3 (PostCSS) y clases de componente en `src/styles.css` (`.card`, `.btn-primary`, `.form-input`, `.data-table`…). Sin Angular Material (D-06); estilos en CSS (D-16).
- `main.ts` lee `public/config.json` (`{"apiUrl", "mockApi", "githubAppSlug", "githubClientId"}`) antes de arrancar Angular (token `APP_CONFIG`). Ahora está en `mockApi: false`, contra la API. **`mockApi: true`** activa el modo demo (D-17): `core/mock/mock-api.interceptor.ts` responde con `MockBackend`, que guarda el estado en `localStorage` (botón "Restablecer datos" en la franja amarilla).
- Usuarios de demostración: en el modo demo del portal, cliente `demo@ferreteria-lopez.es` / `demo1234` (con AWS y GitHub conectados y 2 apps) y administrador `admin` / `admin`. Contra la API (perfil `mock-fast`), el mismo cliente pero sin conexiones; el administrador sale de `backend/.env`.
- **Contrato de la API (D-21), definido en `core/api/models.ts` y `core/api/releasy-api.service.ts`:**
  - Autenticación: `POST /api/auth/register {companyName,email,password}` y `POST /api/auth/login {email,password}`, que devuelven `Session{token,user{id,email,name,role,tenantId}}`. Después, `Authorization: Bearer <token>`.
  - Cliente:
    - `GET /api/me/tenant` → `Tenant{id,name,email,aws{status,externalId,platformAccountId,roleArn,accountId,connectedAt},github{status,accountLogin,installationId,authorizedBy,connectedAt},appCount,createdAt}`
    - `POST /api/me/aws-connection {roleArn}` y `DELETE` para desconectar
    - `POST /api/me/github-connection {code, installationId?}` (código OAuth de GitHub, D-26) y `DELETE`
    - `GET /api/me/github/repositories` → `[{fullName,description,private,defaultBranch,branches[]}]`
    - `GET` y `POST /api/me/apps` (`{name,templateId,repository,branch,options,usage}`)
  - Catálogo: `GET /api/templates` y `GET /api/templates/{id}`.
  - Administrador: `GET /api/tenants`, `GET /api/tenants/{id}` y `GET /api/tenants/{id}/apps`.
  - App (propietario o admin): `GET /api/apps/{id}` y `GET /api/apps/{id}/releases`.
  - `App{id,tenantId,name,templateId,repository,branch,options,usage,url,createdAt,updatedAt}`. Errores en formato ProblemDetail.
- **Plantillas (D-22):** `core/templates/template-catalog.ts` define de forma declarativa las 3 plantillas, con grupos de opciones (`toggle`, `select`, `number`, `text` y `keyvalue`, más `visibleWhen`), campos y perfiles de uso. Incluye `validateOptions` (la API lo replica en `TemplateCatalog`, con los mismos mensajes) y `describeOptions`.
- **Costes (D-20):** precios de referencia de eu-west-1 en `core/pricing/aws-prices.ts` y la función pura `estimateMonthlyCost(template, options, usage)` en `core/pricing/cost-estimator.ts`. La cuota simulada es de 9 USD por app y mes.
- Rutas:
  - `/login` y `/register`;
  - `/portal` (inicio con los pasos de configuración y las apps), `/portal/aws`, `/portal/github`, `/portal/github/callback` (Callback URL de la GitHub App: comprueba el `state` y envía el `code`), `/portal/templates`, `/portal/templates/:templateId/new` (formulario + coste en vivo) y `/portal/apps/:appId`;
  - `/admin`, `/admin/tenants/:tenantId` y `/admin/apps/:appId`;
  - protegidas con `roleGuard`.
- Pruebas: `npx ng test --watch=false` (53). El catálogo se exporta a la API con `npm run export:catalog` (D-25). Arranque: `cd frontend && npx ng serve`.

## Backend (`backend/`): nuevo alcance hecho, AWS simulado

- Java 21, Spring Boot 3.5.16 y AWS SDK v2 2.56.0, con capas `domain`, `application` (servicios y puertos), `infrastructure` (mock/memory/aws/dynamodb/github/security/config) y `web`.
- **Modos independientes:**
  - `RELEASY_AWS_MODE=mock|real`: STS, Step Functions y DynamoDB, o memoria;
  - `RELEASY_GITHUB_MODE=mock|real`: GitHub App real (`RELEASY_GITHUB_APP_ID`, `_PRIVATE_KEY_PATH`, `_CLIENT_ID` y `_CLIENT_SECRET`; guía en `backend/docs/github-app.md`). Al conectar, el usuario autoriza con OAuth y la API comprueba que la instalación es suya (D-26).
- **Autenticación (D-24):**
  - `POST /api/auth/register|login` devuelven un JWT HS256 (`RELEASY_JWT_SECRET`), con contraseñas en BCrypt.
  - Usuarios en la tabla `releasy-users` (clave: email). El administrador sale de `.env`.
- **Rutas:**
  - `/api/me/*` (`CLIENT`): `tenant`, `aws-connection`, `github-connection`, `github/repositories`, `apps`.
  - `/api/templates`.
  - `/api/tenants/**` (`ADMIN`, solo lectura).
  - `/api/apps/{id}` y `/releases` (propietario o administrador; si no, 404).
  - Webhook `POST /api/webhooks/github` (HMAC).
- **Piezas principales:**
  - Puertos: `GithubGateway` (`GithubAppClient` / `MockGithubGateway`), `AwsConnectionVerifier` (`StsAwsConnectionVerifier` / `MockAwsConnectionVerifier`) y `DeploymentOrchestrator`.
  - `ReleaseLauncher` lanza los releases, tanto al crear la app (último commit de la rama) como desde el webhook.
  - `TemplateCatalog` carga `resources/catalog/templates.json` (exportado del portal) y valida igual que el front.
- **Arranque:**
  - F5 "ReleEasy API (mock)", o `cd backend && set -a; source .env; set +a; SPRING_PROFILES_ACTIVE=mock-fast ./mvnw spring-boot:run`.
  - `mock-fast` crea `demo@ferreteria-lopez.es` / `demo1234`.
  - El `.env` necesita `RELEASY_JWT_SECRET`; ver `.env.example`.
- **Pendiente:**
  - webhook de la app con URL pública;
  - AWS real (fase 4).

## Fase 4, Terraform y pipelines: `backend/deploy/` (pendiente, con el nuevo alcance)

- Terraform >= 1.10, backend S3 con `use_lockfile = true`. Variables con descripción y validaciones. Validar con `terraform init -backend=false` y `terraform validate`. **Nunca `apply` sin confirmación.**
- `platform/` (cuenta de la plataforma): bucket de estados (versionado, cifrado y sin acceso público), tablas DynamoDB (`users`, `tenants`, `apps` y `releases`), ECR, CodeBuild (build y deploy), Step Functions e IAM con mínimo privilegio. El rol de CodeBuild asume `ReleEasyDeployRole` **con External ID** en la cuenta del cliente.
- **Plantilla de CloudFormation del rol del cliente** (`releasy-deploy-role.yaml`): parámetros `PlatformAccountId` y `ExternalId`, permisos limitados a los servicios de las plantillas y salida `RoleArn`.
- `templates/`: **tres módulos**, `angular-static`, `springboot-api` y `angular-springboot` (o módulos comunes `frontend` y `backend` compuestos). Cada opción del catálogo es una variable (`enable_waf`, `enable_rum`, `custom_domain`, `api_exposure = alb|api_gateway`, `instance_type`, `min/max_instances`, `cpu_target`, `detailed_monitoring`, `enable_xray`, `log_retention_days`, `env_vars`, `notification_email`…).
  - Requisitos que se mantienen: VPC pública/privada, Auto Scaling Group de EC2 (Docker + imagen de ECR) detrás de un ALB, con *instance refresh* y rollback, health check en `/actuator/health`, S3 privado + CloudFront con OAC y `/api/*` hacia el backend.
  - Outputs: URL, bucket, distribución, Auto Scaling Group y ALB.
- `pipelines/`:
  - `buildspec-build.yml`: clona en el SHA (con el token de instalación de la GitHub App) y construye según la plantilla (`npm ci && ng build` en `/frontend` y/o `./mvnw -B verify` en `/backend`, más la imagen Docker; si no hay Dockerfile, `eclipse-temurin:21-jre`).
  - `buildspec-deploy.yml`: `terraform init` con backend parcial (`tenants/<tenantId>/<appId>/terraform.tfstate`), `plan` y `apply`, sube el front a S3, invalida CloudFront y escribe el estado en DynamoDB, también si falla.
  - `state-machine.asl.json`: RUNNING → build → deploy → estado final, con Catch que marca `FAILED`.

## Fase 5, documentación: `backend/docs/` (pendiente, en español)

- README de puesta en marcha (local en modo mock y en AWS), diagrama de arquitectura en Mermaid y pasos manuales: publicar la plantilla de CloudFormation del rol y guardar los secretos en Secrets Manager. La guía de la GitHub App ya está en `backend/docs/github-app.md`; falta añadirle el webhook.
- Verificación final: todo compila y pasan las pruebas.

## Commits

Terminar los mensajes de commit con:
`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
