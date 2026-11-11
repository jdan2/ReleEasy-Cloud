# -----------------------------------------------------------------------------
# Modulo observability: grupo de logs y alarmas del backend.
#
# El grupo de logs lo consume el user-data del modulo backend (driver awslogs).
# Las alarmas vigilan el Auto Scaling Group (CPU) y el ALB (errores 5xx y latencia).
# -----------------------------------------------------------------------------

variable "name_prefix" {
  description = "Prefijo para nombrar y etiquetar los recursos."
  type        = string
}

variable "log_group_name" {
  description = "Nombre del grupo de logs a crear. Lo fija la composicion para evitar dependencias circulares."
  type        = string
}

variable "tags" {
  description = "Etiquetas comunes."
  type        = map(string)
  default     = {}
}

# --- Opcion del catalogo: log_retention_days (7 | 14 | 30 | 90) ---
variable "log_retention_days" {
  description = "Dias de retencion de los logs del backend en CloudWatch."
  type        = number
  default     = 14

  validation {
    condition     = contains([7, 14, 30, 90], var.log_retention_days)
    error_message = "log_retention_days debe ser 7, 14, 30 o 90."
  }
}

# --- Opcion del catalogo: backend_alarms ---
variable "enable_alarms" {
  description = "Crea alarmas de CPU, errores 5xx y latencia, con aviso por email."
  type        = bool
  default     = true
}

# --- Opcion del catalogo: notification_email ---
variable "notification_email" {
  description = "Email para las alarmas. Obligatorio si enable_alarms = true."
  type        = string
  default     = ""
}

# --- Recursos que vigilan las alarmas (del modulo backend) ---
variable "autoscaling_group_name" {
  description = "Nombre del Auto Scaling Group a vigilar (CPU)."
  type        = string
  default     = ""
}

variable "alb_arn_suffix" {
  description = "Sufijo del ARN del ALB (para las metricas de errores y latencia)."
  type        = string
  default     = ""
}
