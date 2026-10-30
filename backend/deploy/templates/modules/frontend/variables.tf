# -----------------------------------------------------------------------------
# Modulo frontend: S3 privado + CloudFront con OAC para una SPA/web estatica.
#
# Cada variable corresponde a una opcion del catalogo del portal (templates.json),
# de modo que el formulario del cliente se traduce directamente a infraestructura.
# -----------------------------------------------------------------------------

variable "app_name" {
  description = "Nombre corto de la aplicacion (slug). Se usa en nombres de recursos."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,19}$", var.app_name))
    error_message = "El nombre debe tener 3-20 caracteres en minusculas, digitos o guiones, y empezar por letra."
  }
}

variable "tenant_slug" {
  description = "Identificador del cliente. Forma parte del nombre del bucket para evitar colisiones."
  type        = string
}

variable "tags" {
  description = "Etiquetas comunes que se aplican a todos los recursos."
  type        = map(string)
  default     = {}
}

# --- Opcion del catalogo: back-end sobre el que enruta /api/* (plantilla combinada) ---
variable "api_origin_domain" {
  description = <<-EOT
    Dominio del backend al que CloudFront enruta /api/* (p. ej. el DNS del ALB). Si se
    deja vacio, CloudFront solo sirve el front (caso de la plantilla angular-static).
  EOT
  type        = string
  default     = ""
}

# --- Opcion del catalogo: waf ---
variable "enable_waf" {
  description = "Activa AWS WAF (reglas gestionadas y limite por IP) delante de CloudFront."
  type        = bool
  default     = false
}

# --- Opcion del catalogo: front_alarms ---
variable "enable_alarms" {
  description = "Crea alarmas de CloudWatch sobre la tasa de errores 4xx/5xx de CloudFront."
  type        = bool
  default     = true
}

# --- Opcion del catalogo: front_rum ---
variable "enable_rum" {
  description = "Activa CloudWatch RUM (monitorizacion de usuarios reales en el navegador)."
  type        = bool
  default     = false
}

# --- Opcion del catalogo: custom_domain + domain_name ---
variable "custom_domain" {
  description = "Si es true, sirve la web en un dominio propio con certificado de ACM."
  type        = bool
  default     = false
}

variable "domain_name" {
  description = "Nombre de dominio propio (obligatorio si custom_domain = true)."
  type        = string
  default     = ""

  validation {
    condition     = var.domain_name == "" || can(regex("^([a-z0-9-]+\\.)+[a-z]{2,}$", var.domain_name))
    error_message = "El dominio debe ir en minusculas (p. ej. www.mitienda.es)."
  }
}

variable "acm_certificate_arn" {
  description = <<-EOT
    ARN de un certificado de ACM en us-east-1 para el dominio propio. CloudFront exige
    que el certificado este en us-east-1. Obligatorio si custom_domain = true.
  EOT
  type        = string
  default     = ""
}

# --- Opcion del catalogo: notification_email (destino de las alarmas) ---
variable "notification_email" {
  description = "Email al que se envian las alarmas. Obligatorio si enable_alarms = true."
  type        = string
  default     = ""
}
