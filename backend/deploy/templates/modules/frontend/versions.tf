terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source = "hashicorp/aws"
      # El modulo usa dos configuraciones de AWS: la region del cliente (por defecto)
      # y us-east-1, obligatoria para WAF y las metricas de CloudFront.
      version               = "~> 5.0"
      configuration_aliases = [aws.us_east_1]
    }
  }
}
