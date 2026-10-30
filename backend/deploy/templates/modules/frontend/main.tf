# -----------------------------------------------------------------------------
# Frontend estatico: bucket S3 privado servido por CloudFront con Origin Access
# Control (OAC). El bucket nunca es publico; solo CloudFront puede leerlo.
# -----------------------------------------------------------------------------

locals {
  bucket_name = "releasy-${var.tenant_slug}-${var.app_name}"
  # El front se sirve en el dominio propio si se configuro; si no, en el de CloudFront.
  aliases = var.custom_domain && var.domain_name != "" ? [var.domain_name] : []
  has_api = var.api_origin_domain != ""
}

# --- Bucket de contenido estatico (privado y cifrado) ---
resource "aws_s3_bucket" "site" {
  bucket = local.bucket_name
  tags   = var.tags
}

resource "aws_s3_bucket_public_access_block" "site" {
  bucket                  = aws_s3_bucket.site.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "site" {
  bucket = aws_s3_bucket.site.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "site" {
  bucket = aws_s3_bucket.site.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

# --- Origin Access Control: CloudFront firma las peticiones al bucket ---
resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "${local.bucket_name}-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# --- WAF opcional (debe estar en us-east-1 para CloudFront) ---
resource "aws_wafv2_web_acl" "site" {
  count    = var.enable_waf ? 1 : 0
  provider = aws.us_east_1
  name     = "${local.bucket_name}-waf"
  scope    = "CLOUDFRONT"

  default_action {
    allow {}
  }

  rule {
    name     = "AWSManagedRulesCommonRuleSet"
    priority = 1
    override_action {
      none {}
    }
    statement {
      managed_rule_group_statement {
        name        = "AWSManagedRulesCommonRuleSet"
        vendor_name = "AWS"
      }
    }
    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${local.bucket_name}-common"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${local.bucket_name}-waf"
    sampled_requests_enabled   = true
  }

  tags = var.tags
}

# --- Distribucion CloudFront ---
resource "aws_cloudfront_distribution" "site" {
  enabled             = true
  default_root_object = "index.html"
  aliases             = local.aliases
  web_acl_id          = var.enable_waf ? aws_wafv2_web_acl.site[0].arn : null
  tags                = var.tags

  # Origen principal: el bucket S3 (a traves de OAC).
  origin {
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_id                = "s3-${local.bucket_name}"
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  # Origen opcional: el backend, para enrutar /api/* (plantilla combinada).
  dynamic "origin" {
    for_each = local.has_api ? [1] : []
    content {
      domain_name = var.api_origin_domain
      origin_id   = "api-${local.bucket_name}"
      custom_origin_config {
        http_port              = 80
        https_port             = 443
        origin_protocol_policy = "https-only"
        origin_ssl_protocols   = ["TLSv1.2"]
      }
    }
  }

  default_cache_behavior {
    target_origin_id       = "s3-${local.bucket_name}"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    # CachingOptimized (politica gestionada de AWS).
    cache_policy_id = "658327ea-f89d-4fab-a63d-7e88639e58f6"
  }

  # Comportamiento para /api/* hacia el backend, sin cache.
  dynamic "ordered_cache_behavior" {
    for_each = local.has_api ? [1] : []
    content {
      path_pattern           = "/api/*"
      target_origin_id       = "api-${local.bucket_name}"
      viewer_protocol_policy = "redirect-to-https"
      allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods         = ["GET", "HEAD"]
      # CachingDisabled y AllViewerExceptHostHeader (politicas gestionadas de AWS).
      cache_policy_id          = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad"
      origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac"
    }
  }

  # SPA: las rutas del cliente devuelven index.html (el enrutado lo hace Angular).
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }
  custom_error_response {
    error_code         = 404
    response_code      = 200
    response_page_path = "/index.html"
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = var.custom_domain ? false : true
    acm_certificate_arn            = var.custom_domain ? var.acm_certificate_arn : null
    ssl_support_method             = var.custom_domain ? "sni-only" : null
    minimum_protocol_version       = var.custom_domain ? "TLSv1.2_2021" : "TLSv1"
  }
}

# --- Politica del bucket: solo esta distribucion de CloudFront puede leerlo ---
data "aws_iam_policy_document" "site" {
  statement {
    sid       = "AllowCloudFrontRead"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.site.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.site.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id
  policy = data.aws_iam_policy_document.site.json
}

# --- Avisos (SNS) y alarmas de CloudWatch opcionales ---
resource "aws_sns_topic" "alerts" {
  count = var.enable_alarms ? 1 : 0
  name  = "${local.bucket_name}-alerts"
  tags  = var.tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.enable_alarms && var.notification_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.notification_email
}

# Las metricas de CloudFront viven en us-east-1.
resource "aws_cloudwatch_metric_alarm" "error_rate" {
  count               = var.enable_alarms ? 1 : 0
  provider            = aws.us_east_1
  alarm_name          = "${local.bucket_name}-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "5xxErrorRate"
  namespace           = "AWS/CloudFront"
  period              = 300
  statistic           = "Average"
  threshold           = 5
  alarm_description   = "Tasa de errores 5xx de CloudFront por encima del 5%."
  alarm_actions       = var.enable_alarms ? [aws_sns_topic.alerts[0].arn] : []
  dimensions = {
    DistributionId = aws_cloudfront_distribution.site.id
    Region         = "Global"
  }
  tags = var.tags
}

# --- CloudWatch RUM opcional ---
resource "aws_rum_app_monitor" "site" {
  count  = var.enable_rum ? 1 : 0
  name   = local.bucket_name
  domain = var.custom_domain && var.domain_name != "" ? var.domain_name : aws_cloudfront_distribution.site.domain_name
  tags   = var.tags
}
