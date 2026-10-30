# -----------------------------------------------------------------------------
# Plantilla angular-springboot: front Angular (S3 + CloudFront) y API Spring Boot
# (EC2 con Auto Scaling + ALB) en un mismo dominio. CloudFront enruta /api/* al ALB,
# asi no hay problemas de CORS.
#
# Combina los modulos frontend y backend. Reune las opciones de ambas plantillas.
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
  description = "Imagen Docker del backend (repositorio:tag en ECR). Vacio al validar."
  type        = string
  default     = ""
}

# --- Opciones del front (grupos front-observability, front-security, front-domain) ---
variable "front_alarms" {
  type    = bool
  default = true
}

variable "front_rum" {
  type    = bool
  default = false
}

variable "waf" {
  type    = bool
  default = false
}

variable "custom_domain" {
  type    = bool
  default = false
}

variable "domain_name" {
  type    = string
  default = ""
}

variable "acm_certificate_arn" {
  type    = string
  default = ""
}

# --- Opciones del backend (grupos back-exposure, back-capacity, back-observability) ---
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

# --- Comun: destino de las alarmas ---
variable "notification_email" {
  type    = string
  default = ""
}
