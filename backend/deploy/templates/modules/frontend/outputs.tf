output "bucket_name" {
  description = "Nombre del bucket S3 donde se publica el contenido estatico."
  value       = aws_s3_bucket.site.bucket
}

output "distribution_id" {
  description = "ID de la distribucion de CloudFront (para invalidar la cache tras cada despliegue)."
  value       = aws_cloudfront_distribution.site.id
}

output "url" {
  description = "URL publica de la aplicacion."
  value       = var.custom_domain && var.domain_name != "" ? "https://${var.domain_name}" : "https://${aws_cloudfront_distribution.site.domain_name}"
}
