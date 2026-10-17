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
