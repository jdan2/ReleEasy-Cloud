# -----------------------------------------------------------------------------
# springboot-api: red + backend (EC2/ASG/ALB/ECR) + observabilidad.
# -----------------------------------------------------------------------------

locals {
  name_prefix = "releasy-${var.tenant_slug}-${var.app_name}"
  # Nombre del grupo de logs, fijado aqui para que lo compartan backend y
  # observability sin crear una dependencia circular entre modulos.
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
  api_exposure        = var.api_exposure
  api_throttle_rps    = var.api_throttle_rps
  env_vars            = var.env_vars
  enable_xray         = var.enable_xray

  # El grupo de logs lo crea el modulo observability; aqui solo se pasa su nombre.
  log_group_name = local.log_group_name
}

module "observability" {
  source = "../modules/observability"

  name_prefix        = local.name_prefix
  log_group_name     = local.log_group_name
  log_retention_days = var.log_retention_days
  enable_alarms      = var.backend_alarms
  notification_email = var.notification_email

  # Recursos a vigilar (del modulo backend).
  autoscaling_group_name = module.backend.autoscaling_group_name
  alb_arn_suffix         = module.backend.alb_arn_suffix
}
