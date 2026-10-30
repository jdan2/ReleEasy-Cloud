# -----------------------------------------------------------------------------
# Infraestructura de la plataforma ReleEasy (cuenta propia). Se despliega una vez.
# -----------------------------------------------------------------------------

variable "region" {
  description = "Region de AWS de la plataforma."
  type        = string
  default     = "eu-west-1"
}

variable "state_bucket_name" {
  description = <<-EOT
    Nombre del bucket S3 donde el pipeline guarda el estado de Terraform de las apps de
    los clientes (clave tenants/<tenantId>/<appId>/terraform.tfstate). Debe ser unico.
  EOT
  type        = string
}

variable "github_token_secret_arn" {
  description = <<-EOT
    ARN del secreto de Secrets Manager con el token de instalacion de la GitHub App que
    CodeBuild usa para clonar el repositorio del cliente. Se crea aparte (paso manual).
  EOT
  type        = string
  default     = ""
}

variable "tags" {
  description = "Etiquetas comunes."
  type        = map(string)
  default = {
    Project   = "releasy-cloud"
    ManagedBy = "terraform"
  }
}
