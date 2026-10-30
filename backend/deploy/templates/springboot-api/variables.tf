# -----------------------------------------------------------------------------
# Plantilla springboot-api: API Java en EC2 con Auto Scaling detras de un ALB (D-23).
#
# El pipeline pasa estas variables a partir del DeploymentRequest y del mapa de
# opciones (options{}) configurado por el cliente en el portal.
# -----------------------------------------------------------------------------

variable "region" {
  description = "Region de AWS donde se despliega la aplicacion."
  type        = string
  default     = "eu-west-1"
}

variable "app_name" {
  description = "Nombre corto de la aplicacion (slug)."
  type        = string
}

variable "tenant_slug" {
  description = "Identificador del cliente propietario."
  type        = string
}

variable "tags" {
  description = "Etiquetas comunes para todos los recursos."
  type        = map(string)
  default     = {}
}

# --- Imagen del backend (la publica el pipeline de build en ECR) ---
variable "container_image" {
  description = "Imagen Docker a ejecutar (repositorio:tag en ECR). Vacio al validar."
  type        = string
  default     = ""
}

# --- Opciones del catalogo (grupos back-exposure, back-capacity, back-observability) ---
variable "api_exposure" {
  type    = string
  default = "alb"
}

variable "api_throttle_rps" {
  type    = number
  default = 100
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}

variable "min_instances" {
  type    = number
  default = 1
}

variable "max_instances" {
  type    = number
  default = 2
}

variable "cpu_target" {
  type    = number
  default = 70
}

variable "log_retention_days" {
  type    = number
  default = 14
}

variable "backend_alarms" {
  type    = bool
  default = true
}

variable "detailed_monitoring" {
  type    = bool
  default = false
}

variable "enable_xray" {
  type    = bool
  default = false
}

variable "env_vars" {
  type    = map(string)
  default = {}
}

variable "notification_email" {
  type    = string
  default = ""
}
