terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Estado de la propia plataforma. Se deja sin configurar aqui: se inicializa con
  # -backend-config o, la primera vez, con backend local (bootstrap de la plataforma).
  backend "s3" {}
}

provider "aws" {
  region = var.region
}
