# -----------------------------------------------------------------------------
# Modulo network: VPC con subredes publicas y privadas en dos zonas de
# disponibilidad. Las instancias EC2 del backend viven en las subredes privadas;
# el ALB, en las publicas. Salida a internet de las privadas via NAT Gateway.
# -----------------------------------------------------------------------------

variable "name_prefix" {
  description = "Prefijo para nombrar y etiquetar los recursos (p. ej. releasy-<slug>-<app>)."
  type        = string
}

variable "vpc_cidr" {
  description = "Rango CIDR de la VPC."
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Numero de zonas de disponibilidad a usar (minimo 2 para alta disponibilidad del ALB)."
  type        = number
  default     = 2

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count debe ser 2 o 3."
  }
}

variable "single_nat_gateway" {
  description = <<-EOT
    Si es true, usa un unico NAT Gateway para todas las subredes privadas (mas barato,
    menos tolerante a fallos). Si es false, uno por zona. Por defecto true para contener coste.
  EOT
  type        = bool
  default     = true
}

variable "tags" {
  description = "Etiquetas comunes."
  type        = map(string)
  default     = {}
}
