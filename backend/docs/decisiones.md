# Decisiones de diseño

Registro de decisiones tomadas cuando el enunciado era ambiguo o contradictorio. En cada caso se ha elegido la opción más simple que cumple el objetivo del MVP.

## D-01 · Versión de Spring Boot

- **Contexto:** el enunciado pide "Spring Boot 3 (última versión estable)". A fecha de 2026-10-17, la última estable es la 4.1.1 y Spring Initializr ya no ofrece la rama 3.x.
- **Decisión:** usar **Spring Boot 3.5.16**, la última versión de la rama 3.
- **Motivo:** respeta el requisito explícito de "Spring Boot 3" y es compatible con Java 21 y AWS SDK v2. Pasar a Spring Boot 4 obligaría a cambiar los starters y a migrar a Jackson 3, sin aportar nada al MVP.
- **Consecuencia:** la rama 3.5 ya no tiene soporte OSS gratuito. Migrar a la 4.x queda como trabajo futuro.

## D-02 · JDK en la máquina de desarrollo

- **Contexto:** el cask `temurin@21` de Homebrew necesita `sudo` interactivo.
- **Decisión:** usar la fórmula `openjdk@21` de Homebrew, que es equivalente funcionalmente (OpenJDK 21) y no necesita `sudo`.
- **Consecuencia:** la imagen de contenedor sigue usando `eclipse-temurin:21-jre`, como pide el enunciado.

## D-03 · URL "predecible" de la aplicación

- **Contexto:** se pide asignar la URL al crear la aplicación, pero el dominio de CloudFront (`dXXXX.cloudfront.net`) no se conoce hasta el primer `terraform apply`.
- **Decisión:** al crear la aplicación se guarda una URL provisional derivada de sus identificadores. El primer despliegue que termina bien la sustituye por la URL real del output de Terraform.
- **Alternativa descartada:** exigir un dominio propio en Route 53 con certificado ACM. Es más complejo y tiene coste.

## D-04 · Creación asíncrona de la cuenta AWS

- **Contexto:** `CreateAccount` tarda minutos en completarse.
- **Decisión:** `POST /api/tenants` responde enseguida con el cliente en estado `CREATING`. El polling de `DescribeCreateAccountStatus` se hace en segundo plano y deja el estado en `ACTIVE` o `FAILED`.

## D-05 · Persistencia en modo `mock`

- **Decisión:** en modo `mock` se usan repositorios en memoria que implementan las mismas interfaces que los de DynamoDB. Así no hacen falta DynamoDB Local ni Docker para la demo ni para las pruebas.

## D-06 · Estilos del portal: Tailwind CSS en lugar de Angular Material

- **Contexto:** el enunciado permitía Angular Material como librería de componentes. El autor pidió después usar **Tailwind CSS**.
- **Decisión:** el portal usa Tailwind CSS (sin librería de componentes) y se retira Angular Material, que se añadió en la fase 1.

## D-07 · Un único secreto de webhook para toda la plataforma

- **Decisión:** todas las aplicaciones comparten un secreto de webhook, `RELEASY_GITHUB_WEBHOOK_SECRET` (en AWS se guarda en Secrets Manager).
- **Motivo:** es lo más simple que cumple la validación HMAC. Un secreto por aplicación queda como mejora futura.

## D-08 · Solo se registran aplicaciones en clientes con cuenta activa

- **Decisión:** `POST /api/tenants/{id}/apps` responde 409 si la cuenta AWS del cliente no está en `ACTIVE`.
- **Motivo:** sin cuenta no hay `tenant_role_arn` y no se puede desplegar. Así el webhook nunca recibe una aplicación sin destino.

## D-09 · Reenvíos de GitHub

- **Decisión:** si ya existe un release del mismo SHA que no haya fallado, el webhook se ignora. Para reintentar un SHA fallido basta con reenviar el evento desde GitHub.
- **Motivo:** GitHub permite reenviar entregas ("Redeliver") y no deben duplicar despliegues.

## D-10 · Quién escribe el estado del release

- **Modo real:** la API crea el release en `PENDING` y arranca Step Functions (el nombre de la ejecución es el `releaseId`). A partir de ahí, `RUNNING`, `SUCCEEDED` y `FAILED` los escriben la máquina de estados y `buildspec-deploy.yml` directamente en DynamoDB. La API solo marca `FAILED` si no consigue arrancar la ejecución. Así se evitan condiciones de carrera entre la API y el pipeline.
- **Modo mock:** `MockDeploymentOrchestrator` simula esas transiciones a través de `ReleaseService`, que aplica las reglas de `ReleaseStatus`.

## D-11 · Modelo de DynamoDB

| Tabla | Clave de partición | Clave de ordenación | Índices globales |
|---|---|---|---|
| `tenants` | `tenantId` | — (acceso por ID; el listado es un scan, porque la tabla es pequeña) | — |
| `apps` | `appId` | — | `tenantId-index` (PK `tenantId`), `repository-index` (PK `repository`) |
| `releases` | `appId` | `releaseId` (empieza por la fecha, así que se ordena cronológicamente) | — |

## D-12 · Disparadores de la demo en modo mock

- Un email con `+fail` (p. ej. `demo+fail@acme.es`) hace que la creación de la cuenta termine en `FAILED`.
- Un mensaje de commit con `[fail]` hace que el release termine en `FAILED`.
- El perfil `mock-fast` (`SPRING_PROFILES_ACTIVE=mock-fast`) acorta los tiempos de la simulación.

## D-13 · Carpeta `.vscode/` en la raíz

- **Contexto:** el enunciado limita la raíz a `frontend/` y `backend/`.
- **Decisión:** se permite además la carpeta oculta `.vscode/`, que es configuración del editor y no código. Contiene la ruta del JDK, el arranque de la API con F5 y las extensiones recomendadas, y no guarda secretos: las variables las lee de `backend/.env`, que no se versiona.

## D-14 · Polling del portal también en reposo

- **Contexto:** el enunciado pide refrescar "cada pocos segundos mientras haya un release en curso". Si solo se hiciera polling en ese caso, un release nuevo disparado por un webhook no aparecería hasta recargar la página.
- **Decisión:** el detalle de la aplicación refresca cada **3 s** si hay un release `PENDING` o `RUNNING`, cada **15 s** en reposo, y tiene un botón "Actualizar". Las pantallas de clientes refrescan cada 3 s solo mientras alguna cuenta está en `CREATING`.

## D-15 · Login del portal y almacenamiento de credenciales

- **Decisión:** el portal tiene un formulario de login que comprueba las credenciales con una petición real a la API. Guarda `usuario:contraseña` en Base64 en `sessionStorage` (se borra al cerrar la pestaña). Un interceptor añade la cabecera `Authorization` solo a las peticiones dirigidas a la API, y un 401 cierra la sesión.
- **Motivo:** es lo mínimo compatible con el HTTP Basic del MVP. Un proveedor de identidad (p. ej. Cognito) queda como trabajo futuro.

## D-16 · CSS en lugar de SCSS en el portal

- **Decisión:** los estilos globales están en `src/styles.css`. Los componentes usan utilidades de Tailwind en las plantillas y, para patrones repetidos, clases de componente definidas con `@apply`.
- **Motivo:** Tailwind CSS 4 se integra como plugin de PostCSS (`.postcssrc.json`) e importa con `@import 'tailwindcss'` desde CSS; SCSS no aporta nada aquí.

## D-17 · Modo demo del portal sin backend (`mockApi`)

- **Contexto:** el autor quiere enseñar el portal sin arrancar la API.
- **Decisión:** `config.json` admite `"mockApi": true`. Con ese valor, `mockApiInterceptor` responde a las llamadas a la API con `MockBackend`, que guarda los datos en memoria y aplica las mismas reglas y estados que el modo mock del backend. Además:
  - se acepta cualquier usuario y contraseña;
  - una franja amarilla avisa de que el modo demo está activo;
  - el detalle de la aplicación muestra un botón "Simular merge", en lugar de depender de GitHub.
- **Uso:** está activado mientras se revisa el front. Para usar la API real hay que poner `"mockApi": false` en `config.json`, sin recompilar. En los despliegues reales debe estar siempre en `false`.

---

# Cambio de alcance (2026-10-17): vista de cliente, cuenta AWS propia y plantillas configurables

## D-18 · El cliente usa su propia cuenta de AWS (sustituye a AWS Organizations)

- **Contexto:** el enunciado original creaba una cuenta miembro por cliente con `organizations:CreateAccount`. Tiene inconvenientes:
  - la cuota de cuentas por organización es baja por defecto y ampliarla requiere una revisión manual de AWS;
  - cerrar una cuenta tarda 90 días y hay límites de cierre;
  - la factura de todos los clientes llega a la plataforma, que tendría que refacturarla.
- **Decisión:** cada cliente conecta **su propia cuenta de AWS**:
  1. el portal le da un **External ID** único y un enlace *Quick-create* de CloudFormation;
  2. el stack crea en la cuenta del cliente el rol `ReleEasyDeployRole`, que confía en la cuenta de la plataforma y exige ese External ID;
  3. el cliente pega el ARN del rol y la plataforma lo verifica con `sts:AssumeRole`.
- **Por qué no claves de acceso:** las *access keys* son credenciales de larga duración. El rol con External ID es el patrón estándar (Datadog, Spacelift…): la plataforma solo obtiene credenciales temporales y el cliente revoca el acceso borrando el stack. El External ID evita el problema del *confused deputy* (que alguien haga que la plataforma actúe sobre una cuenta ajena).
- **Consecuencias:**
  - desaparecen `CreateAccount`, el polling de la creación de cuentas y el estado `CREATING`; el cliente tiene un estado de conexión `NOT_CONNECTED` o `CONNECTED`;
  - el pipeline asume `ReleEasyDeployRole` (con External ID) en lugar de `OrganizationAccountAccessRole`;
  - AWS factura directamente al cliente, y la plataforma cobra su propia cuota (D-20);
  - **el backend y el Terraform de la fase 2 deben adaptarse** (pendiente; ver CLAUDE.md).

## D-19 · Base de datos fuera del alcance por ahora

- **Decisión:** las plantillas con Spring Boot todavía no gestionan base de datos.
- **Para más adelante:** se añadiría como una opción "con base de datos" de esas plantillas, no como una plantilla aparte. Puede ser RDS PostgreSQL creado por la plataforma o la BD externa del cliente, y en ambos casos la conexión se guarda en Secrets Manager y se inyecta en el contenedor. Las semillas no necesitan coordinación: el repo del cliente incluye migraciones con Flyway o Liquibase, que Spring Boot ejecuta al arrancar.

## D-20 · Estimación de costes y cuota de la plataforma

- **Decisión:**
  - cada plantilla declara qué servicios de AWS crea y cada opción, qué servicios añade;
  - con el **uso estimado** que indica el cliente (visitas, peso de página, peticiones a la API, tamaño de respuesta, mensajes SQS), `core/pricing/cost-estimator.ts` calcula el coste mensual por servicio;
  - el formulario lo recalcula en vivo.
- **Precios:** son precios de referencia bajo demanda de eu-west-1, centralizados en `core/pricing/aws-prices.ts` con su fecha de revisión. Son **aproximados**: no incluyen capa gratuita, impuestos ni descuentos, y se indica al cliente que es una estimación.
- **Precio total:** se muestra separado en "Infraestructura AWS" (lo factura AWS a la cuenta del cliente) y "Cuota ReleEasy" (simulada: 9 USD por app y mes).
- **Supuestos visibles:** unas 10 peticiones por visita, backend 24/7 con el mínimo de contenedores, 1 NAT Gateway, ~1 KB de logs por petición y muestreo de X-Ray del 5 %. Si hay autoescalado, se muestra también el coste máximo.

## D-21 · Roles, registro de clientes y contrato de la API definido por el portal

- **Decisión:**
  - Hay dos roles, `ADMIN` y `CLIENT`, con un único login que lleva a cada uno a su vista (`/admin` o `/portal`).
  - El cliente **se registra solo** (empresa, email y contraseña) y queda sin conexiones.
  - La sesión usa un token *Bearer* opaco, que será un JWT en el backend.
  - El administrador ve a todos los clientes, sus conexiones y sus apps, en solo lectura.
  - **Solo el cliente crea aplicaciones.**
- **Contrato:** las rutas (`/api/auth/*`, `/api/me/*`, `/api/templates`, `/api/tenants/*`, `/api/apps/*`) y los tipos están en `frontend/src/app/core/api/`. De momento solo los implementa el modo demo; el backend se adaptará después.

## D-22 · Plantillas configurables

- **Decisión:** hay tres plantillas, Angular (S3 + CloudFront), Spring Boot (ECS Fargate) y Angular + Spring Boot, definidas de forma declarativa en `core/templates/template-catalog.ts`. Cada una incluye grupos de opciones (interruptores, selectores, números, textos y variables de entorno), campos que dependen de otros (`visibleWhen`), validaciones, campos de uso y perfiles de uso (Bajo, Medio, Alto).
- **Opciones del front:** alarmas 4xx/5xx, CloudWatch RUM, AWS WAF y dominio propio (Route 53 + ACM).
- **Opciones del back:**
  - entrada por ALB o por API Gateway HTTP API (con límite de peticiones; usa VPC Link y Cloud Map y evita el ALB);
  - tamaño y número de contenedores con autoescalado;
  - retención de logs, alarmas, Container Insights y X-Ray;
  - cola SQS con DLQ;
  - variables de entorno.
- **Fase 4:** cada opción se traducirá en una variable de Terraform del módulo de la plantilla (por ejemplo `enable_sqs`).
- **Primer despliegue:** al crear la app se despliega el último commit de la rama (en el modo demo, a los pocos segundos); después, cada merge.

## D-23 · Sin ECS Fargate ni SQS: el backend corre en EC2 con Auto Scaling

- **Fecha:** 2026-10-19.
- **Decisión:**
  - se elimina la opción de **cola SQS** (y el campo de uso "mensajes SQS");
  - el backend Spring Boot deja de usar **ECS Fargate** y pasa a un **Auto Scaling Group de EC2** (tipos `t3.micro` a `t3.large`) en subredes privadas, detrás de un **ALB**. Cada instancia ejecuta con Docker la imagen del backend guardada en ECR.
- **Alternativas descartadas:**
  - App Runner, porque no admite clientes nuevos desde el 30 de abril de 2026;
  - ECS Express Mode, porque también usa Fargate;
  - Lambda + SnapStart, porque obliga a cambiar el código del cliente;
  - Elastic Beanstalk, porque da menos control.
- **Consecuencias:**
  - El ALB existe siempre. Con API Gateway pasa a ser interno y API Gateway (HTTP API + VPC Link) se coloca delante, así que **ya no ahorra el coste del ALB** y desaparece Cloud Map.
  - Los despliegues usan *instance refresh* del ASG, con health check del ALB en `/actuator/health` y vuelta atrás automática (sustituye al *circuit breaker* de ECS).
  - Container Insights se sustituye por la **monitorización detallada de EC2** (métricas cada minuto).
  - Opciones renombradas: `task_size` pasa a ser `instance_type`, y `min_tasks`/`max_tasks` pasan a ser `min_instances`/`max_instances`. Los datos de demostración pasan a `releasy.mock-state.v3`.
- **Sobre la "Transferencia de datos":** solo aparece en la plantilla Spring Boot sin front. Es lo que cobra AWS por los GB que salen hacia internet (las respuestas de la API, a ~0,09 USD/GB); el tráfico de entrada es gratis. En Angular + Spring Boot las respuestas salen por CloudFront, que cobra su propia tarifa por GB, y el tramo del ALB a CloudFront no tiene coste.

## D-24 · Registro, login con JWT y tenant con conexiones AWS y GitHub

- **Fecha:** 2026-10-19.
- **Decisión:** primera pieza del backend con el nuevo alcance.
  - **Usuarios:** `User{id,email,name,role,tenantId,passwordHash}` con contraseña en **BCrypt**.
    - Se guardan en memoria en modo mock y en la tabla DynamoDB **`releasy-users`** en modo real. Su clave es el email normalizado, así que el login es un `GetItem` y la unicidad la garantiza una escritura condicional (`attribute_not_exists`), sin índices secundarios.
    - El **administrador no se guarda**: sus credenciales salen de `RELEASY_ADMIN_USER`/`RELEASY_ADMIN_PASSWORD`.
  - **Sesión:**
    - JWT firmado con **HMAC-SHA256** (`RELEASY_JWT_SECRET`, de al menos 32 caracteres) y válido 12 h (`RELEASY_TOKEN_TTL`).
    - Lleva los claims `sub`, `email`, `name`, `role` y `tenantId`.
    - Se usa `spring-boot-starter-oauth2-resource-server` (Nimbus) para emitirlo y validarlo. No hay refresco de token: al caducar, se vuelve a iniciar sesión.
  - **Rutas:**
    - `POST /api/auth/register` (201) y `POST /api/auth/login` (200) devuelven `Session{token,user}`.
    - `GET /api/me/tenant` (rol `CLIENT`; el cliente sale del token, nunca de la URL).
    - `/api/tenants/**` es solo para `ADMIN`. Se elimina el alta de clientes por el administrador.
  - **Tenant:** pasa a tener `aws{status,externalId,roleArn,accountId,connectedAt}` y `github{status,accountLogin,installationId,connectedAt}`.
    - El External ID es un UUID que se genera al registrarse.
    - `platformAccountId` sale de la configuración (`RELEASY_PLATFORM_ACCOUNT_ID`).
    - Desaparecen `TenantStatus`, `AccountProvisioner`, `AccountCreationPoller` y la dependencia de Organizations.
  - Registrar apps y desplegar exige ahora la **cuenta AWS conectada**. `DeploymentRequest` incluye el `externalId`.
- **Pendiente:**
  - conexión AWS (`AwsConnectionVerifier` con `sts:AssumeRole`) y GitHub (`GithubGateway`);
  - apps con plantillas en `/api/me/apps`;
  - autorización por propietario en `/api/apps/{id}`;
  - recuperación de contraseña.

## D-25 · Login y GitHub reales; AWS y despliegues simulados en la API

- **Fecha:** 2026-10-19.
- **Decisión:** el portal pasa a usar la API (`mockApi: false`) y la simulación se hace **en el backend**, no en el navegador.
  - **Reales:** registro, login, la conexión con GitHub (`RELEASY_GITHUB_MODE=real`) y el catálogo de plantillas con su validación.
  - **Simulados** (`RELEASY_AWS_MODE=mock`): la verificación STS de la cuenta AWS (un ARN con `fail` se rechaza) y los despliegues. El orquestador simulado lleva cada release de `PENDING` a `SUCCEEDED`.
  - El modo de GitHub (`releasy.github.mode`) es **independiente** del de AWS.
- **GitHub App:**
  - **Flujo:**
    1. El portal lleva a `github.com/apps/<slug>/installations/new`.
    2. GitHub vuelve a la *Setup URL* (`/portal/github/callback?installation_id=…`).
    3. El portal envía `POST /api/me/github-connection {installationId}`.
    4. La API comprueba la instalación (`GET /app/installations/{id}`, con el JWT de la app) y guarda la cuenta.
  - **Cliente:** `GithubAppClient`, con el `HttpClient` del JDK.
    - El JWT de la app se firma con RS256 (Nimbus) y vale 9 minutos.
    - Los tokens de instalación (1 h) se guardan en memoria y se renuevan 5 minutos antes de caducar.
    - La clave `.pem` se lee tal como la entrega GitHub (PKCS#1); se convierte a PKCS#8 en código.
  - **Límites:**
    - Se lee una sola página: hasta 100 repositorios y 100 ramas por repositorio.
    - **Limitación de seguridad conocida:** no se comprueba que quien envía el `installation_id` sea quien instaló la app; solo se impide que dos clientes compartan una instalación. Para cerrarlo hay que activar la autorización OAuth durante la instalación y comprobar la instalación con `GET /user/installations`, usando el token del usuario.
  - **Webhook:** desactivado en desarrollo, porque necesita una URL pública. El de push ya existe (`/api/webhooks/github`).
- **Catálogo compartido:**
  - La fuente sigue siendo `template-catalog.ts`. `npm run export:catalog` lo exporta a `backend/src/main/resources/catalog/templates.json`, usando el `esbuild` que ya trae `@angular/build`.
  - La API lo sirve tal cual (`GET /api/templates`). `TemplateCatalog` replica `validateOptions` con los mismos mensajes y descarta las opciones que la plantilla no declara.
  - **Hay que volver a exportarlo cada vez que cambie el catálogo.**
- **Apps:**
  - `App{templateId,options,usage}` sustituye a `plan`; en DynamoDB, las opciones y el uso se guardan como JSON.
  - `POST /api/me/apps` comprueba:
    - que las conexiones AWS y GitHub existen;
    - que la plantilla existe y sus opciones son válidas;
    - que el repositorio y la rama están en la instalación.
  - Después lanza el despliegue del **último commit de la rama** con `ReleaseLauncher`, el mismo que usa el webhook.
  - `/api/apps/{id}` responde 404 a quien no es el propietario ni el administrador.
  - `DeploymentRequest` lleva `templateId` y `options`.
- **Otros cambios:**
  - Los ID de release llevan milisegundos (`r-yyyyMMddHHmmssSSS-…`). Antes, dos releases creados en el mismo segundo se ordenaban al azar.
  - El perfil `mock-fast` crea el cliente `demo@ferreteria-lopez.es` / `demo1234` al arrancar.

## D-26 · Autorización OAuth del usuario al conectar GitHub

- **Fecha:** 2026-10-19.
- **Contexto:** en D-25 la API aceptaba cualquier `installation_id` (limitación conocida). Además, el usuario quiere que el cliente vea en su GitHub que ha autorizado a ReleEasy.
- **Decisión:** la GitHub App se configura con *Request user authorization (OAuth) during installation*.
  - **Vuelta al portal:** al instalar, el cliente también autoriza a ReleEasy con su usuario, y GitHub vuelve a la *Callback URL* (`/portal/github/callback`) con `code`, `installation_id` y `state`.
  - **Comprobación en la API** (`POST /api/me/github-connection {code, installationId?}`):
    1. canjea el `code` (client ID y client secret) por un token del usuario;
    2. con ese token consulta `/user` y `/user/installations`;
    3. **solo acepta una instalación accesible para ese usuario**;
    4. guarda `github.authorizedBy` (quién autorizó).
    - El token del usuario **no se guarda**.
  - Una instalación sigue sin poder pertenecer a dos clientes.
  - **CSRF:** el portal genera un `state` aleatorio (en `sessionStorage`) antes de ir a GitHub, y la página de vuelta lo exige.
  - **App ya instalada:** el cliente puede solo autorizar (`/login/oauth/authorize?client_id=…`). Entonces llega sin `installation_id` y se usa la primera instalación del usuario.
  - **Visibilidad para el cliente:** aparece en GitHub en *Settings → Applications*, tanto en "Installed GitHub Apps" como en "Authorized GitHub Apps".
- **Configuración nueva:**
  - API: `RELEASY_GITHUB_CLIENT_ID` y `RELEASY_GITHUB_CLIENT_SECRET` (el secret es secreto: `.env` o Secrets Manager).
  - Portal: `githubClientId` en `config.json` (es público).
- **Mock:** cualquier código vale. El usuario es `usuario-demo` y el ID de instalación se calcula a partir del código.
- **Resuelve** la limitación de seguridad de D-25.
## D-27 · Reorganización de paquetes: adaptadores de entrada y de salida explícitos
- **Fecha:** 2026-10-25.
- **Contexto:** la arquitectura ya era hexagonal (dominio aislado, puertos en `application/port` y adaptadores en `infrastructure`), pero la separación entre adaptadores **de entrada** (quién llama a la aplicación) y **de salida** (a quién llama la aplicación) no era visible en la estructura de carpetas. Además, tres excepciones transversales estaban sueltas en la raíz de `application`, mientras el resto se agrupaba por feature.
- **Decisión:** reordenar los paquetes para que la frontera entrada/salida sea explícita, siguiendo la convención de la industria (p. ej. el arquetipo de Bancolombia usa `entrypoints`/`adapters`):
  - `web/` → `infrastructure/entrypoints/` (controladores REST, webhook, DTOs, seguridad y manejador de excepciones): son los **adaptadores de entrada** (driving).
  - `infrastructure/{github,aws,dynamodb,memory,mock,security}/` → `infrastructure/adapters/…`: son los **adaptadores de salida** (driven).
  - Las excepciones transversales (`ConflictException`, `InvalidRequestException`, `NotFoundException`) pasan a `application/shared/`, en simetría con `domain/shared`.
  - `infrastructure/config/` **no se mueve**: es wiring de Spring, ni entrada ni salida.
- **Qué NO se cambió, y por qué:**
  - Los **enums** (`Role`, `ReleaseStatus`, `ConnectionStatus`) siguen junto al concepto de dominio al que pertenecen. Agruparlos en un paquete `enums` separaría cada enum de su significado: se organiza por concepto de negocio, no por mecanismo del lenguaje.
  - No se añaden sufijos como `AppModel`/`AppDto`: el paquete ya comunica el rol (`domain/app/App` es el modelo; `…entrypoints/dto/AppResponse` es el DTO).
  - Los casos de uso siguen en `application` (no se mueven a `domain`, a diferencia del arquetipo de Bancolombia): mantener dominio y aplicación separados es más fiel a la distinción entre reglas de negocio y orquestación.
  - No se introducen *mappers* dedicados por frontera: para este tamaño, los métodos `from(...)` en los DTO son suficientes y evitan ceremonia.
- **Verificación:** `./mvnw test` → 62 pruebas en verde, incluida la de integración que levanta el contexto de Spring Boot. No quedaron referencias a los paquetes antiguos.
## D-28 · Bootstrap del rol del cliente con CloudFormation, y el resto con Terraform
- **Fecha:** 2026-10-26.
- **Contexto:** la plataforma despliega la infraestructura del cliente con Terraform asumiendo el rol `ReleEasyDeployRole` en su cuenta (D-18). Pero ese rol tiene que existir antes de poder asumirlo: es un problema de arranque (*bootstrap*). Había que decidir cómo lo crea el cliente.
- **Decisión:** el rol se crea con una plantilla de **CloudFormation** (`backend/deploy/bootstrap/releasy-deploy-role.yaml`), que el portal ofrece como enlace *Quick-create*. El resto de la infraestructura (plataforma y plantillas de aplicaciones) es **Terraform**.
  - **Motivo:** el enlace *Quick-create* de CloudFormation abre la consola del cliente con la plantilla ya cargada; el cliente solo revisa y pulsa "Crear". No instala la CLI, no ejecuta `terraform apply` ni custodia ningún estado. Es coherente con la propuesta de valor ("sin conocimientos de DevOps") y es el patrón habitual de las herramientas BYOA del sector (Datadog, Vanta, etc.). Hacerlo con Terraform obligaría a la PYME a hacer DevOps justo para empezar a no hacerlo.
  - **Alcance de la plantilla:** parámetros `PlatformAccountId` y `ExternalId` (este con `NoEcho`); política de confianza que solo admite a la cuenta de la plataforma y exige el External ID (`sts:ExternalId`); permisos de despliegue agrupados por área (frontend, backend, observabilidad, opcionales e IAM) y acotados a los servicios de las plantillas. Los buckets S3 se limitan al prefijo `releasy-*` y la creación de roles/perfiles IAM al prefijo `releasy-*`. Salida: `RoleArn`, que el cliente pega en el portal.
- **Verificación:** `aws cloudformation validate-template` correcto (solo sintaxis; no crea ningún stack ni recurso, sin coste). AWS informa de que requiere `CAPABILITY_NAMED_IAM`, lo normal al declarar un rol con nombre fijo.
- **Mejora futura:** algunos permisos usan comodines (p. ej. `autoscaling:*`) para no quedarse cortos; cuando se cierren las plantillas de Terraform se conocerá la lista exacta de acciones y podrá afinarse el mínimo privilegio.
- **Relación:** implementa el lado del cliente de D-18; es el primer entregable de la Fase 4 (infraestructura como código).
## D-29 · Plantillas de Terraform por composición de módulos; primera plantilla angular-static
- **Fecha:** 2026-10-26.
- **Contexto:** las tres plantillas del catálogo (`angular-static`, `springboot-api`, `angular-springboot`) comparten piezas (red, front, backend, observabilidad). Había que decidir cómo estructurarlas para no duplicar código.
- **Decisión:** cada plantilla es una composición raíz en `backend/deploy/templates/<plantilla>/` que usa **módulos reutilizables** en `templates/modules/` (`frontend`, y más adelante `network`, `backend`, `observability`). La primera implementada es `angular-static`, que usa solo `modules/frontend`.
  - **Mapeo opción→variable:** cada opción del formulario del portal es una variable de Terraform (`waf`→`enable_waf`, `front_alarms`→`enable_alarms`, `front_rum`→`enable_rum`, `custom_domain`/`domain_name`, `notification_email`), de modo que `options{}` del `DeploymentRequest` se traduce directamente a infraestructura.
  - **Frontend:** bucket S3 privado (versionado y cifrado) + CloudFront con **Origin Access Control** (el bucket nunca es público), enrutado SPA (403/404 → `index.html`), y como opcionales WAF, alarmas con SNS por email, RUM y dominio propio con ACM. El módulo admite `api_origin_domain` para enrutar `/api/*` al backend (lo usará la plantilla combinada; en `angular-static` queda vacío).
  - **Dos proveedores AWS:** la región del cliente y `us-east-1`, obligatoria para WAF y las métricas de CloudFront. El módulo lo declara con `configuration_aliases`.
  - **Estado remoto:** el bloque `backend "s3"` se deja vacío; el pipeline inyecta la clave `tenants/<tenantId>/<appId>/terraform.tfstate` con `-backend-config`. En local se valida con `-backend=false`.
- **Verificación:** `terraform init -backend=false` (provider AWS 5.100.0), `terraform validate` → "Success! The configuration is valid.", y `terraform fmt -check` sin cambios. No se ejecutó `apply`; no se creó ningún recurso.
- **Relación:** segundo entregable de la Fase 4, sobre el contrato `DeploymentOrchestrator.DeploymentRequest` ya existente.
## D-30 · Plantilla springboot-api: EC2 con Auto Scaling, ALB, ECR y API Gateway opcional
- **Fecha:** 2026-10-26.
- **Contexto:** la plantilla del backend Java. Se evaluó de nuevo cómo ejecutar el contenedor (reabriendo el debate EC2+ASG vs ECS sobre EC2 vs ECS Fargate).
- **Decisión:** se mantiene la D-23: el backend corre como **un contenedor por instancia EC2 en un Auto Scaling Group detrás de un ALB**, sin orquestador de contenedores. ECS (en cualquiera de sus modos) se descarta porque añade una capa de orquestación que este caso —un contenedor por aplicación— no necesita, y rompería la coherencia con el catálogo, el estimador de costes y la memoria, que hablan de "EC2 Auto Scaling".
- **Estructura (composición de módulos, D-29):** la plantilla `templates/springboot-api` compone tres módulos reutilizables:
  - **`network`:** VPC con subredes públicas (ALB) y privadas (instancias), en 2 zonas, con Internet Gateway y NAT (único por defecto, para contener coste).
  - **`backend`:** ECR, rol de instancia (lectura de ECR, logs, SSM y X-Ray opcional), launch template con user-data que instala Docker, se autentica contra ECR y arranca el contenedor (`docker run`), ASG con **instance refresh** (rolling + rollback automático), autoescalado por CPU (target tracking, solo si `max_instances > min_instances`), ALB con health check en `/actuator/health`, y **API Gateway (HTTP API) opcional** con VPC Link y throttling cuando `api_exposure = api_gateway` (entonces el ALB pasa a interno).
  - **`observability`:** grupo de logs con retención configurable, SNS con aviso por email y alarmas de CPU (ASG), errores 5xx y latencia p95 (ALB).
- **Mapeo opción→variable:** `instance_type`, `min_instances`, `max_instances`, `cpu_target`, `api_exposure`, `api_throttle_rps`, `log_retention_days`, `backend_alarms`→`enable_alarms`, `detailed_monitoring`, `xray_tracing`→`enable_xray`, `env_vars`, `notification_email`.
- **Detalle técnico (dependencia circular):** `backend` necesita el nombre del grupo de logs y `observability` necesita el ASG y el ALB de `backend`. Para no crear un ciclo entre módulos, el **nombre del grupo de logs se fija en la composición** (`local.log_group_name = "/releasy/<prefijo>"`) y se pasa como literal a ambos módulos; así `backend` no depende del recurso que crea `observability`.
- **Verificación:** `terraform init -backend=false` (3 módulos, provider AWS 5.100.0), `terraform validate` → "Success! The configuration is valid.", `terraform fmt` sin cambios pendientes. No se ejecutó `apply`; no se creó ningún recurso.
- **Mejora futura:** los nombres de ALB y target group se truncan a 32 caracteres (límite de AWS); con slugs largos podrían colisionar. Conviene derivar un sufijo corto determinista cuando se afine la plantilla.
## D-31 · Plantilla angular-springboot: front y API en un mismo dominio
- **Fecha:** 2026-10-26.
- **Contexto:** la tercera plantilla del catálogo combina un front Angular y una API Spring Boot. El reto es servir ambos bajo el mismo dominio para evitar CORS.
- **Decisión:** `templates/angular-springboot` **compone los módulos ya existentes** `network`, `backend`, `frontend` y `observability`, sin duplicar código. CloudFront es el punto de entrada público: sirve el front desde S3 y **enruta `/api/*` al ALB del backend** (comportamiento `ordered_cache_behavior` del módulo `frontend`, que recibe `api_origin_domain = module.backend.alb_dns_name`). Así front y API comparten dominio y no hay CORS.
  - En esta plantilla el backend se fuerza a `api_exposure = "alb"`: el punto público es CloudFront hablando con el ALB, por lo que API Gateway no se usa aquí (sí está disponible en `springboot-api`).
  - Reúne las opciones de las dos plantillas base: las del front (`waf`, `front_alarms`, `front_rum`, dominio propio) y las del backend (`instance_type`, `min/max_instances`, `cpu_target`, `log_retention_days`, `backend_alarms`, `detailed_monitoring`, `enable_xray`, `env_vars`), con `notification_email` común.
- **Verificación:** `terraform init -backend=false` (4 módulos, provider AWS 5.100.0), `terraform validate` → "Success! The configuration is valid.", `terraform fmt` sin cambios. No se ejecutó `apply`.
- **Resultado:** con esto quedan **las tres plantillas del catálogo** (`angular-static`, `springboot-api`, `angular-springboot`) escritas y validadas. Pendiente de la Fase 4: infraestructura de la plataforma (`platform/`) y el pipeline (`pipelines/`).
## D-32 · Infraestructura de la plataforma (platform/) en Terraform
- **Fecha:** 2026-10-26.
- **Contexto:** las plantillas describen la infraestructura de las apps del cliente, pero hace falta la infraestructura de la propia plataforma (cuenta de ReleEasy) que ejecuta los despliegues y guarda los datos. Se crea una sola vez.
- **Decisión:** `backend/deploy/platform/` (Terraform) define:
  - **Estado (`storage.tf`):** bucket S3 para el estado de Terraform de las apps (versionado, cifrado con KMS, sin acceso público; clave `tenants/<tenantId>/<appId>/terraform.tfstate`) y las **cuatro tablas DynamoDB** del backend en modo real. Las claves e índices **coinciden exactamente con el modelo del backend** (`infrastructure/adapters/dynamodb`): `users` (partición `email`), `tenants` (partición `tenantId`), `apps` (partición `appId` + GSI `tenantId-index` y `repository-index`) y `releases` (partición `appId` + ordenación `releaseId`). `PAY_PER_REQUEST` y point-in-time recovery.
  - **IAM (`iam.tf`):** rol de CodeBuild que puede escribir logs, leer/escribir el estado en S3, actualizar el estado del release en DynamoDB, autenticarse contra ECR, leer el secreto de la GitHub App y, sobre todo, **asumir `ReleEasyDeployRole`** (`sts:AssumeRole` sobre `arn:aws:iam::*:role/ReleEasyDeployRole`; la confianza real la impone la política del rol del cliente, con cuenta de plataforma + External ID). Rol de Step Functions que solo arranca y vigila CodeBuild.
  - **CodeBuild (`codebuild.tf`):** dos proyectos, `releasy-build` (con `privileged_mode` para construir imágenes Docker) y `releasy-deploy`. Sus buildspec se leen de `../pipelines/` (bloque siguiente).
  - **Step Functions (`stepfunctions.tf`):** máquina de estados `build → deploy → Succeeded`, con `Catch` que lleva a `Failed`. La entrada es el `DeploymentRequest` que envía el backend; cada paso traslada sus campos a CodeBuild como `EnvironmentVariablesOverride`. Su ARN es el `RELEASY_STATE_MACHINE_ARN` del backend en modo real.
- **Verificación:** `terraform init -backend=false` (provider AWS 5.100.0), `terraform validate` → "Success! The configuration is valid.", `terraform fmt` sin cambios. No se ejecutó `apply`.
- **Nota de coste:** a diferencia de las plantillas (que solo existen cuando hay apps), estos recursos serían **permanentes** si se aplicaran. DynamoDB en modo bajo demanda y CodeBuild por uso mantienen el coste en reposo cercano a cero, pero conviene tenerlo presente antes de un `apply` real.
- **Relación:** cierra el lado de la plataforma del flujo BYOA; el backend ya tenía el puerto `StepFunctionsDeploymentOrchestrator` preparado para disparar esta máquina de estados.
## D-33 · Pipelines de build y deploy; cierre de la Fase 4
- **Fecha:** 2026-10-26.
- **Contexto:** faltaban los scripts que la máquina de estados ejecuta en CodeBuild para convertir un release en infraestructura real.
- **Decisión:** `backend/deploy/pipelines/` contiene:
  - **`buildspec-build.yml`:** asume `ReleEasyDeployRole` en la cuenta del cliente (con External ID), clona el repositorio en el `commitSha` con el token de la GitHub App, construye según `templateId` (`npm ci && npm run build` para el front y/o `./mvnw -B verify` para el backend) y, si hay backend, construye la imagen Docker (genera un `Dockerfile` con base `eclipse-temurin:21-jre` si el repo no trae uno) y la publica en el ECR del cliente.
  - **`buildspec-deploy.yml`:** instala Terraform, asume el rol, ejecuta `terraform init` con backend parcial (clave `tenants/<tenantId>/<appId>/terraform.tfstate` en el bucket de la plataforma) y `terraform apply` de la plantilla con las variables del release; sube el front a S3, invalida CloudFront y escribe el estado final del release en DynamoDB. El contrato de atributos (`status`, `url`, `errorMessage`, `updatedAt`; clave `appId`+`releaseId`) coincide con `ReleaseItem`. El estado `FAILED` también se escribe si el apply falla.
  - **`state-machine.asl.json`:** copia canónica en ASL de la definición que `platform/stepfunctions.tf` genera con `jsonencode`.
- **Verificación:** los dos buildspec validan como YAML y el `asl.json` como JSON; `platform/` revalida con `terraform validate` ("Success!") al leer los buildspec reales con `file()`. No se ejecutó `apply`.
- **Limitaciones conocidas (para un `apply` real):**
  1. El backend S3 del estado necesita credenciales de la **plataforma**, mientras que el `provider` del apply usa las credenciales **asumidas** del cliente. En el buildspec se asume el rol antes del `init`; para separarlos limpiamente conviene un bloque `assume_role` en el `provider` (no en las credenciales de proceso) o `AWS_PROFILE` para el backend. Queda anotado para el despliegue real.
  2. El token de la GitHub App (`GITHUB_TOKEN`) debe inyectarse desde Secrets Manager; el proyecto CodeBuild lo tiene preparado pero comentado.
  3. Los nombres de ALB/target group se truncan a 32 caracteres (ver D-30).
- **Estado:** con esto, la **Fase 4 queda completa y validada** (bootstrap, tres plantillas, plataforma y pipelines), sin `apply`. El flujo de extremo a extremo (front → backend → Step Functions → CodeBuild → Terraform en la cuenta del cliente) está escrito; solo falta activar el modo `real` del backend y ejecutar un `apply` controlado para probarlo contra AWS.
