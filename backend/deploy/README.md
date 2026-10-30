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

- **`buildspec-build.yml`:** asume `ReleEasyDeployRole` en la cuenta del cliente, clona el
  repositorio en el commit indicado (token de la GitHub App), construye el front y/o el
  backend segun `templateId` y publica la imagen en el ECR del cliente.
- **`buildspec-deploy.yml`:** `terraform init` con estado por app
  (`tenants/<tenantId>/<appId>/terraform.tfstate`), `apply` de la plantilla, subida del
  front a S3, invalidacion de CloudFront y escritura del estado del release en DynamoDB
  (tambien `FAILED` si algo falla).
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
  Gateway). Los buckets S3 y los roles IAM que puede crear se limitan al prefijo
  `releasy-*`.
- **Salida:** `RoleArn`, que el cliente pega en el portal para completar la conexión.

### Validar (no crea nada)

```powershell
aws cloudformation validate-template --template-body file://bootstrap/releasy-deploy-role.yaml
```

Comprueba la sintaxis con una llamada de solo lectura a la API de CloudFormation. La
respuesta indica `CAPABILITY_NAMED_IAM`, lo normal al declarar un rol con nombre fijo.

### Publicar la plantilla (paso manual, cuando proceda)

Para que el enlace *Quick-create* funcione, la plantilla debe estar en una URL pública
(un bucket S3 de la plataforma). El portal construye entonces un enlace del tipo:

```
https://console.aws.amazon.com/cloudformation/home?#/stacks/create/review
  ?templateURL=<URL de la plantilla>
  &stackName=ReleEasy-DeployRole
  &param_PlatformAccountId=<cuenta de la plataforma>
  &param_ExternalId=<External ID del cliente>
```

## Plataforma, plantillas y pipelines

Pendientes (siguientes bloques de la Fase 4). Cada uno se validará con
`terraform init -backend=false`, `terraform validate` y `terraform fmt -check`, sin
`apply`.
