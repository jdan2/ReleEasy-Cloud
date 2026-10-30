# -----------------------------------------------------------------------------
# angular-static: solo front. No hay backend, asi que api_origin_domain queda vacio
# y CloudFront sirve unicamente el contenido estatico del bucket.
# -----------------------------------------------------------------------------

module "frontend" {
  source = "../modules/frontend"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  app_name    = var.app_name
  tenant_slug = var.tenant_slug
  tags        = var.tags

  enable_waf          = var.enable_waf
  enable_alarms       = var.enable_alarms
  enable_rum          = var.enable_rum
  custom_domain       = var.custom_domain
  domain_name         = var.domain_name
  acm_certificate_arn = var.acm_certificate_arn
  notification_email  = var.notification_email
}
