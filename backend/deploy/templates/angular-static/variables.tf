# -----------------------------------------------------------------------------
# Plantilla angular-static: web o SPA Angular en S3 privado + CloudFront.
#
# El pipeline pasa estas variables a partir del DeploymentRequest y del mapa de
# opciones (options{}) que el cliente configuro en el portal.
# -----------------------------------------------------------------------------

variable "region" {
  description = "Region de AWS donde se despliega la aplicacion."
  type        = string
  default     = "eu-west-1"
}

variable "tenant_role_arn" {
  description = "ARN del rol ReleEasyDeployRole de la cuenta del cliente. Si se informa, el provider aws lo asume (el estado S3 sigue usando las credenciales de la plataforma). Vacio = credenciales del entorno."
  type        = string
  default     = ""
}
variable "external_id" {
  description = "External ID para asumir tenant_role_arn."
  type        = string
  default     = ""
  sensitive   = true
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

# --- Opciones del catalogo (grupo front-security, front-observability, front-domain) ---
variable "enable_waf" {
  type    = bool
  default = false
}

variable "enable_alarms" {
  type    = bool
  default = true
}

variable "enable_rum" {
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

variable "notification_email" {
  type    = string
  default = ""
}
