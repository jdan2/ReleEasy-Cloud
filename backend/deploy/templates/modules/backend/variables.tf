# -----------------------------------------------------------------------------
# Modulo backend: la API en EC2 con Auto Scaling detras de un ALB (D-23).
#
# Cada instancia ejecuta la imagen Docker del backend (desde ECR) con `docker run`
# en el user-data. No hay orquestador de contenedores: un contenedor por instancia.
# Cada variable corresponde a una opcion del catalogo del portal.
# -----------------------------------------------------------------------------

variable "name_prefix" {
  description = "Prefijo para nombrar y etiquetar los recursos."
  type        = string
}

variable "vpc_id" {
  description = "VPC donde se despliega (del modulo network)."
  type        = string
}

variable "public_subnet_ids" {
  description = "Subredes publicas para el ALB."
  type        = list(string)
}

variable "private_subnet_ids" {
  description = "Subredes privadas para las instancias EC2."
  type        = list(string)
}

variable "tags" {
  description = "Etiquetas comunes."
  type        = map(string)
  default     = {}
}

# --- Opcion del catalogo: instance_type ---
variable "instance_type" {
  description = "Tipo de instancia EC2 que ejecuta la imagen del backend."
  type        = string
  default     = "t3.small"
}

# --- Opciones del catalogo: min_instances / max_instances / cpu_target ---
variable "min_instances" {
  description = "Numero minimo de instancias en el Auto Scaling Group."
  type        = number
  default     = 1
}

variable "max_instances" {
  description = "Numero maximo de instancias. Si es mayor que el minimo, se activa el autoescalado por CPU."
  type        = number
  default     = 2
}

variable "cpu_target" {
  description = "Porcentaje de CPU objetivo del autoescalado (target tracking)."
  type        = number
  default     = 70

  validation {
    condition     = var.cpu_target >= 20 && var.cpu_target <= 90
    error_message = "cpu_target debe estar entre 20 y 90."
  }
}

# --- Opcion del catalogo: detailed_monitoring ---
variable "detailed_monitoring" {
  description = "Metricas de EC2 cada minuto en vez de cada 5 minutos."
  type        = bool
  default     = false
}

# --- Opcion del catalogo: api_exposure (alb | api_gateway) ---
variable "api_exposure" {
  description = <<-EOT
    Punto de entrada de la API. "alb": el ALB es publico. "api_gateway": el ALB pasa a
    interno y API Gateway (HTTP API) se coloca delante con limite de peticiones.
  EOT
  type        = string
  default     = "alb"

  validation {
    condition     = contains(["alb", "api_gateway"], var.api_exposure)
    error_message = "api_exposure debe ser 'alb' o 'api_gateway'."
  }
}

# --- Opcion del catalogo: api_throttle_rps (solo con api_gateway) ---
variable "api_throttle_rps" {
  description = "Limite de peticiones por segundo en API Gateway (solo si api_exposure = api_gateway)."
  type        = number
  default     = 100
}

# --- Opcion del catalogo: env_vars ---
variable "env_vars" {
  description = "Variables de entorno no sensibles que se pasan al contenedor."
  type        = map(string)
  default     = {}
}

# --- Imagen a desplegar (la publica el pipeline de build en ECR) ---
variable "container_image" {
  description = <<-EOT
    Referencia de la imagen Docker a ejecutar (repositorio:tag en ECR). Si se deja vacio,
    el modulo crea el repositorio ECR pero el ASG arranca sin imagen (caso de validacion).
  EOT
  type        = string
  default     = ""
}

variable "container_port" {
  description = "Puerto en el que escucha el backend dentro del contenedor."
  type        = number
  default     = 8080
}

variable "health_check_path" {
  description = "Ruta del health check que comprueba el ALB."
  type        = string
  default     = "/actuator/health"
}

variable "log_group_name" {
  description = "Nombre del grupo de logs de CloudWatch al que las instancias envian la salida del contenedor."
  type        = string
  default     = ""
}

variable "enable_xray" {
  description = "Si es true, ejecuta tambien el daemon de X-Ray junto al contenedor."
  type        = bool
  default     = false
}
