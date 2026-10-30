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
}

# CloudFront exige WAF y sus certificados/metricas en us-east-1.
provider "aws" {
  alias  = "us_east_1"
  region = "us-east-1"
}
