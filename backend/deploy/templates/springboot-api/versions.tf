terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # El pipeline inyecta la clave de estado con -backend-config
  # (tenants/<tenantId>/<appId>/terraform.tfstate en el bucket de la plataforma).
  backend "s3" {}
}

provider "aws" {
  region = var.region
  dynamic "assume_role" {
    for_each = var.tenant_role_arn != "" ? [1] : []
    content {
      role_arn     = var.tenant_role_arn
      external_id  = var.external_id
      session_name = "releasy-terraform"
    }
  }
}
