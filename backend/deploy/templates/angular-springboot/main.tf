# -----------------------------------------------------------------------------
# angular-springboot: front (S3 + CloudFront) + backend (EC2/ASG/ALB/ECR) + obs.
#
# CloudFront sirve el front desde S3 y enruta /api/* al ALB del backend (origen
# api_origin_domain del modulo frontend), de modo que front y API comparten dominio.
# En esta plantilla se fuerza api_exposure = "alb": el punto de entrada publico es
# CloudFront, que habla con el ALB; API Gateway no se usa.
# -----------------------------------------------------------------------------

locals {
  name_prefix    = "releasy-${var.tenant_slug}-${var.app_name}"
  log_group_name = "/releasy/${local.name_prefix}"
}

module "network" {
  source = "../modules/network"

  name_prefix = local.name_prefix
  tags        = var.tags
}

module "backend" {
  source = "../modules/backend"

  name_prefix        = local.name_prefix
  vpc_id             = module.network.vpc_id
  public_subnet_ids  = module.network.public_subnet_ids
  private_subnet_ids = module.network.private_subnet_ids
  tags               = var.tags

  container_image     = var.container_image
  instance_type       = var.instance_type
  min_instances       = var.min_instances
  max_instances       = var.max_instances
  cpu_target          = var.cpu_target
  detailed_monitoring = var.detailed_monitoring
  # El ALB es el origen de /api/* en CloudFront: siempre publico (modo alb).
  api_exposure   = "alb"
  env_vars       = var.env_vars
  enable_xray    = var.enable_xray
  log_group_name = local.log_group_name
}

module "frontend" {
  source = "../modules/frontend"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  app_name    = var.app_name
  tenant_slug = var.tenant_slug
  tags        = var.tags

  # CloudFront enruta /api/* al ALB del backend.
  api_origin_domain = module.backend.alb_dns_name

  enable_waf          = var.waf
  enable_alarms       = var.front_alarms
  enable_rum          = var.front_rum
  custom_domain       = var.custom_domain
  domain_name         = var.domain_name
  acm_certificate_arn = var.acm_certificate_arn
  notification_email  = var.notification_email
}

module "observability" {
  source = "../modules/observability"

  name_prefix        = local.name_prefix
  log_group_name     = local.log_group_name
  log_retention_days = var.log_retention_days
  enable_alarms      = var.backend_alarms
  notification_email = var.notification_email

  autoscaling_group_name = module.backend.autoscaling_group_name
  alb_arn_suffix         = module.backend.alb_arn_suffix
}
