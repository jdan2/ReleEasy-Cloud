# Infraestructura como código (Fase 4)

Este directorio contiene la infraestructura como código de ReleEasy Cloud: el rol que
el cliente crea en su cuenta, la infraestructura de la plataforma, las plantillas de
despliegue de las aplicaciones y el pipeline que las aplica.

> **Todo se valida sin desplegar.** Los comandos de este README comprueban que el
> código es correcto (`validate`), pero **no crean recursos en AWS**. Nunca se ejecuta
> `terraform apply` ni se crea un stack de CloudFormation sin confirmación explícita.

## Estructura

```
deploy/
  bootstrap/
    releasy-deploy-role.yaml   # Rol del cliente (CloudFormation, enlace Quick-create)
  platform/                    # Infra de la plataforma (Terraform)
  templates/
    modules/
      network/                 # VPC publica/privada, NAT, rutas (reutilizable)
      frontend/                # S3 privado + CloudFront con OAC (reutilizable)
      backend/                 # EC2 ASG + ALB + ECR + API Gateway opc. (reutilizable)
      observability/           # log group + alarmas CPU/5xx/latencia + SNS (reutilizable)
    angular-static/            # Plantilla: web/SPA Angular (solo front)
    springboot-api/            # Plantilla: API Java (solo backend)
    angular-springboot/        # Plantilla: front + API en un mismo dominio
  pipelines/
    buildspec-build.yml        # construye y publica la imagen del cliente
    buildspec-deploy.yml       # terraform apply de la plantilla + estado del release
    state-machine.asl.json     # definicion canonica de la maquina de estados
  README.md
```

## Pipeline (`pipelines/`)

Los scripts que CodeBuild ejecuta, orquestados por la maquina de estados de la plataforma:

- **`buildspec-build.yml`:** clona el
  repositorio en el commit indicado (token de la GitHub App), construye el front y/o el
  backend segun `templateId` y publica la imagen en el ECR del cliente (asumiendo
  `ReleEasyDeployRole` solo en un subshell). Si hay front,
  comprime el dist y lo sube a `s3://<STATE_BUCKET>/artifacts/<tenantId>/<appId>/<releaseId>/dist.tgz`
  (variable `DIST_ARTIFACT_KEY`, que calcula la maquina de estados).
- **`buildspec-deploy.yml`:** clona las plantillas del repo publico de ReleEasy
  (`RELEASEY_REPO_URL`, `RELEASEY_REPO_BRANCH`; variables de plataforma `releasy_repo_url` y
  `releasy_repo_branch`), descarga el dist de `DIST_ARTIFACT_KEY`, `terraform init` con estado
  por app (`tenants/<tenantId>/<appId>/terraform.tfstate`), `apply` de la plantilla, subida del
  front a S3, invalidacion de CloudFront y escritura del estado del release en DynamoDB
  (tambien `FAILED` si algo falla).
- **Credenciales (D-37):** el entorno del build es siempre el de la plataforma (backend S3,
  artefactos, DynamoDB). Terraform asume `ReleEasyDeployRole` en los providers `aws`
  (`assume_role` con `TF_VAR_tenant_role_arn` y `TF_VAR_external_id`), y `s3 sync`,
  CloudFront y ECR lo asumen en un subshell. Funciona con cuentas distintas o la misma.
- **`state-machine.asl.json`:** copia canonica de la definicion que `platform/stepfunctions.tf`
  genera; `build -> deploy -> estado final`, con `Catch` hacia `Failed`.

Estos scripts se ejecutan dentro de CodeBuild en AWS; no hay un comando local de
validacion mas alla de comprobar que el YAML/JSON son correctos. El flujo completo solo
corre con el backend en modo `real` y un `apply` real (pendiente, bajo confirmacion).

## Plataforma (`platform/`)

Infraestructura de la cuenta de ReleEasy que soporta los despliegues. Se crea **una sola
vez** (no en cada release):

- **`storage.tf`:** bucket S3 del estado de Terraform (versionado, cifrado, privado) y las
  cuatro tablas DynamoDB (`releasy-users`, `releasy-tenants`, `releasy-apps`,
  `releasy-releases`), con las mismas claves e índices que usa el backend en modo real.
- **`iam.tf`:** rol de CodeBuild (que **asume `ReleEasyDeployRole`** en la cuenta del
  cliente) y rol de Step Functions.
- **`codebuild.tf`:** proyectos `releasy-build` y `releasy-deploy`.
- **`stepfunctions.tf`:** máquina de estados `build → deploy → estado final`; su ARN es el
  `RELEASY_STATE_MACHINE_ARN` del backend en modo real.

```powershell
cd platform
terraform init -backend=false
terraform validate
```

> Al contrario que las plantillas, estos recursos serían permanentes si se aplicaran.
> Mientras tanto, solo se validan (sin `apply`).

## Plantillas de aplicaciones (`templates/`)

Cada plantilla del catálogo del portal tiene aquí su configuración de Terraform. Se
construyen a partir de módulos reutilizables en `templates/modules/`. Cada opción del
formulario del portal es una variable de Terraform.

### `modules/frontend` y plantilla `angular-static`

Front estático: bucket S3 **privado** (versionado y cifrado) servido por CloudFront con
**Origin Access Control** (el bucket nunca es público). Incluye el enrutado SPA (403/404
devuelven `index.html`) y, como opciones del catálogo: WAF (`enable_waf`), alarmas de
errores + SNS por email (`enable_alarms`, `notification_email`), RUM (`enable_rum`) y
dominio propio con ACM (`custom_domain`, `domain_name`, `acm_certificate_arn`). El módulo
admite además `api_origin_domain` para enrutar `/api/*` al backend; en `angular-static`
queda vacío (lo usará la plantilla combinada).

El módulo usa dos proveedores AWS: la región del cliente y `us-east-1` (obligatoria para
WAF y las métricas de CloudFront).

### Validar (no crea nada)

```powershell
cd templates/angular-static
terraform init -backend=false     # descarga el provider; no toca AWS
terraform validate                # comprueba la configuración
terraform fmt -recursive -check ../..
```

El `backend "s3"` queda vacío a propósito: el pipeline inyecta la clave de estado con
`-backend-config` (`tenants/<tenantId>/<appId>/terraform.tfstate`). Por eso en local se
valida con `-backend=false`.

### `modules/network`, `modules/backend`, `modules/observability` y plantilla `springboot-api`

API Java en **EC2 con Auto Scaling detrás de un ALB** (D-23, D-30), sin orquestador de
contenedores: cada instancia ejecuta un contenedor (`docker run` en el user-data).

- **`network`:** VPC con subredes públicas (ALB) y privadas (instancias) en dos zonas,
  Internet Gateway y NAT (único por defecto para contener coste).
- **`backend`:** ECR, rol de instancia, Auto Scaling Group con **instance refresh**
  (rolling + rollback automático) y autoescalado por CPU, ALB con health check en
  `/actuator/health`, y **API Gateway (HTTP API) opcional** con VPC Link y throttling
  cuando `api_exposure = api_gateway` (el ALB pasa entonces a interno). Opciones:
  `instance_type`, `min_instances`, `max_instances`, `cpu_target`, `detailed_monitoring`,
  `enable_xray`, `env_vars`.
- **`observability`:** grupo de logs (retención configurable), SNS por email y alarmas de
  CPU, errores 5xx y latencia p95.

El nombre del grupo de logs se fija en la composición y se comparte entre `backend` y
`observability` para evitar una dependencia circular entre módulos.

Validación idéntica a la anterior:

```powershell
cd templates/springboot-api
terraform init -backend=false
terraform validate
terraform fmt -recursive -check ../..
```

### Plantilla `angular-springboot` (front + API en un dominio)

Combina los módulos `network`, `backend`, `frontend` y `observability`. CloudFront sirve
el front desde S3 y **enruta `/api/*` al ALB del backend** (mismo dominio, sin CORS). El
backend usa `api_exposure = alb` (CloudFront es la puerta pública; API Gateway no se usa
en esta plantilla). Reúne las opciones del front y del backend. Se valida igual
(`init -backend=false`, `validate`, `fmt`).

## Bootstrap: rol del cliente (`bootstrap/releasy-deploy-role.yaml`)

Plantilla de CloudFormation que crea el rol `ReleEasyDeployRole` en la cuenta del
cliente (D-18, D-28). El portal la ofrece como enlace *Quick-create*: el cliente abre
su consola de AWS con la plantilla cargada, revisa y pulsa "Crear stack". No instala
ni ejecuta nada.

- **Parámetros:** `PlatformAccountId` (cuenta de la plataforma) y `ExternalId`
  (secreto del cliente, `NoEcho`).
- **Confianza:** solo la cuenta de la plataforma puede asumir el rol, y únicamente si
  presenta el External ID (`sts:ExternalId`).
- **Permisos:** acotados a los servicios de las plantillas (S3, CloudFront, VPC, EC2
  Auto Scaling, ALB, ECR, CloudWatch, SNS, RUM, X-Ray, WAF, Route 53, ACM, API
  Gateway). Incluyen las lecturas completas por servicio (`Get*`/`List*`/`Describe*`)
  que el provider de Terraform hace en cada `apply`/refresh, y las escrituras para
  crear, etiquetar y destruir (`terraform destroy`) los recursos (D-38). Los buckets
  S3, los roles e instance profiles IAM, los repositorios ECR, los topics SNS y las
  alarmas se limitan al prefijo `releasy-*`; los grupos de logs, a `/releasy/*`.
- **Salida:** `RoleArn`, que el cliente pega en el portal para completar la conexión.

### Validar (no crea nada)

```powershell
aws cloudformation validate-template --template-body file://bootstrap/releasy-deploy-role.yaml
```

Comprueba la sintaxis con una llamada de solo lectura a la API de CloudFormation. La
respuesta indica `CAPABILITY_NAMED_IAM`, lo normal al declarar un rol con nombre fijo.

### Publicar la plantilla (la gestiona `platform/`, D-35)

Para que el enlace *Quick-create* funcione, la plantilla debe estar en una URL pública.
`platform/public-templates.tf` crea el bucket `releasy-public-<cuenta>` (lectura pública
solo del prefijo `cloudformation/`) y sube `bootstrap/releasy-deploy-role.yaml`. Tras el
`apply`, `terraform output role_template_url` da la URL, que se configura en el backend
(`RELEASY_AWS_ROLE_TEMPLATE_URL`, propiedad `releasy.aws.role-template-url`). El portal
construye entonces un enlace regional del tipo:

```
https://<region>.console.aws.amazon.com/cloudformation/home?region=<region>#/stacks/quickcreate
  ?templateURL=<URL de la plantilla>
  &stackName=ReleEasy-DeployRole
  &param_PlatformAccountId=<cuenta de la plataforma>
  &param_ExternalId=<External ID del cliente>
```

## Runbook de despliegue

Procedimiento seguido en la primera prueba real (D-39). Solo `angular-static` se probó
de extremo a extremo; las otras dos plantillas se han validado con `terraform validate`.
Los marcadores `<...>` se sustituyen por valores propios; no se guardan secretos en el repo.

1. **Preparar el perfil AWS.** Crear un usuario IAM de pruebas con clave de acceso y
   configurar un perfil con nombre (no pegar nunca las claves en chats ni en archivos del repo).

   ```powershell
   aws configure --profile releasy
   $env:AWS_PROFILE = "releasy"
   $env:AWS_DEFAULT_REGION = "us-east-1"
   aws sts get-caller-identity   # comprueba la cuenta
   ```

2. **Desplegar la plataforma** (`backend/deploy/platform`). El estado es local: el bucket
   de estado lo crea esta misma configuración, así que no puede guardarse en él. Crear
   `backend_override.tf` (no versionado) y `terraform.tfvars` con los valores de la cuenta.

   ```powershell
   cd backend\deploy\platform
   # backend_override.tf: fuerza el estado local
   #   terraform { backend "local" {} }
   # terraform.tfvars: region, state_bucket_name (releasy-state-<ID_CUENTA>),
   #   github_token_secret_arn, releasy_repo_url y releasy_repo_branch (ver variables.tf)
   terraform init
   terraform plan
   terraform apply
   terraform output
   ```

   Si un corte de red interrumpe el `apply`, repetirlo: es idempotente. Las salidas dan el
   ARN de la máquina de estados (`RELEASY_STATE_MACHINE_ARN`) y `role_template_url`
   (`RELEASY_AWS_ROLE_TEMPLATE_URL`).

3. **Arrancar el backend en modo real** con `RELEASY_AWS_MODE=real`, las variables de
   `backend/.env` (ver `.env.example`: JWT, GitHub App, ARN de la máquina de estados, URL de
   la plantilla) y, en el portal, `mockApi: false`.

4. **Crear el stack del rol desde el portal.** En *Conectar AWS* el portal muestra el
   enlace *Quick-create* de CloudFormation. El cliente lo abre en su consola, crea el
   stack `ReleEasy-DeployRole`, copia la salida `RoleArn` y la pega en el portal; el
   backend la valida con `sts:AssumeRole` y el External ID.

5. **Conectar GitHub y crear la aplicación.** Instalar y autorizar la GitHub App, elegir la
   plantilla `angular-static`, el repositorio y la rama. Al crear la app se lanza el
   release del último commit (Step Functions → `releasy-build` → `releasy-deploy`).

6. **Redesplegar si falla.** En el detalle de la app, botón *Redesplegar*
   (`POST /api/apps/{id}/releases`). Es idempotente por commit: relanza un release `FAILED`,
   pero no uno `PENDING`, `RUNNING` o `SUCCEEDED`. Para ver la causa, revisar los logs de
   CodeBuild (`/aws/codebuild/releasy-build` y `releasy-deploy`).

7. **Comprobar.** El release debe llegar a `SUCCEEDED` en el portal; la ejecución de Step
   Functions debe terminar en verde; la URL de CloudFront de la app abre la aplicación.

### Limitaciones a tener presentes

Las opciones del catálogo (WAF, RUM, email de alarmas) **no llegan todavía a Terraform**,
así que marcarlas en el portal no crea esos recursos. Otras que conviene conocer antes de
probar las plantillas con backend (D-40):

- **`angular-springboot`:** CloudFront habla con el ALB por HTTPS (`https-only`), pero el ALB
  solo escucha HTTP en el 80, así que `/api/*` no llegará al backend hasta corregirlo.
- **ECR:** el build sube la imagen antes de que Terraform cree el repositorio (D-37), así que el
  primer despliegue de `springboot-api` y `angular-springboot` fallará.
- **Estado en el portal:** en modo real el release pasa de `PENDING` a `SUCCEEDED`/`FAILED` sin
  `RUNNING`, y la app conserva la URL provisional; la URL real está en el release y en
  `terraform output url`.
- **Repos privados:** el build no recibe todavía el token de la GitHub App; usar un repo público.
- **Configuración:** la URL de la plantilla del rol va en `RELEASY_AWS_ROLE_TEMPLATE_URL` (con
  `AWS_`); si no, el portal desactiva el botón *Quick-create*.

La lista completa de deuda técnica conocida está en D-39 y D-40 (y en la Tabla 17 del TFM).

## Runbook de desmontaje

Orden recomendado para no dejar recursos huérfanos. Ejecutar siempre con el mismo perfil
AWS de la prueba. Los comandos destruyen recursos reales: revisar el `plan` y la cuenta antes.

1. **Destruir cada aplicación** (la que funcionó y la que quedó a medias). Cada app tiene su
   estado en el bucket de la plataforma.

   ```powershell
   cd backend\deploy\templates\angular-static
   terraform init -reconfigure `
     -backend-config="bucket=releasy-state-<ID_CUENTA>" `
     -backend-config="key=tenants/<tenant>/<app>/terraform.tfstate" `
     -backend-config="region=us-east-1"
   terraform destroy `
     -var "app_name=<app>" -var "tenant_slug=<tenant>" -var "region=us-east-1"
   ```

   Si las credenciales locales no son las del rol del cliente, pasar también
   `-var "tenant_role_arn=<ARN_ROL>"` y `-var "external_id=<EXTERNAL_ID>"` (D-37).
   Repetir con la plantilla que corresponda a cada app.

2. **Borrar el stack de CloudFormation del rol** (`ReleEasy-DeployRole`), desde la consola
   o con `aws cloudformation delete-stack --stack-name ReleEasy-DeployRole`. Debe ir después
   del paso 1, porque Terraform necesita el rol para destruir.

3. **Destruir la plataforma.** Los buckets tienen `force_destroy`, así que se vacían solos.

   ```powershell
   cd backend\deploy\platform
   terraform destroy
   ```

4. **Borrar los grupos de logs** que queden (no los gestiona Terraform).

   ```powershell
   aws logs describe-log-groups --log-group-name-prefix /aws/codebuild/releasy- --query "logGroups[].logGroupName"
   aws logs delete-log-group --log-group-name /aws/codebuild/releasy-build
   aws logs delete-log-group --log-group-name /aws/codebuild/releasy-deploy
   aws logs describe-log-groups --log-group-name-prefix /aws/vendedlogs --query "logGroups[].logGroupName"
   ```

   Revisar también los grupos de Step Functions que aparezcan y borrarlos de la misma forma.

5. **Verificar y cerrar.** Comprobar en la consola (S3, CloudFront, DynamoDB, CodeBuild,
   Step Functions, CloudFormation, IAM, CloudWatch Logs) que no queda nada con el prefijo
   `releasy`. Desactivar y borrar las claves de acceso del usuario IAM de pruebas. Mantener
   un AWS Budget unos días para detectar cargos inesperados.

## Costes y riesgos

- **Coste:** la cuenta de prueba no tenía free tier. La plataforma parada cuesta casi nada
  (DynamoDB bajo demanda; CodeBuild y Step Functions por uso). `angular-static` cuesta
  céntimos (CloudFront incluye 1 TB gratuito de transferencia). WAF tiene cargo mensual
  fijo. El NAT Gateway (~1 USD/día) encarece `springboot-api` y `angular-springboot`.
  Coste real de la prueba: [completar tras revisar Cost Explorer].
- **Claves:** no pegar claves de acceso ni secretos en chats, issues ni commits. Si se
  exponen, desactivarlas y rotarlas de inmediato.
- **Presupuesto:** crear un AWS Budget con aviso por email antes del primer `apply`.
- **Plataforma:** el estado de `platform/` es local; no perder la carpeta antes del
  `destroy`.
