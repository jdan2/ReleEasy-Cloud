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
