output "url" {
  description = "URL publica de la aplicacion (CloudFront o dominio propio). Sirve el front y /api/*."
  value       = module.frontend.url
}

output "bucket_name" {
  description = "Bucket S3 al que el pipeline sube el contenido del front."
  value       = module.frontend.bucket_name
}

output "distribution_id" {
  description = "Distribucion de CloudFront a invalidar tras cada despliegue."
  value       = module.frontend.distribution_id
}

output "ecr_repository_url" {
  description = "Repositorio ECR al que el pipeline sube la imagen del backend."
  value       = module.backend.ecr_repository_url
}

output "autoscaling_group_name" {
  description = "Auto Scaling Group del backend."
  value       = module.backend.autoscaling_group_name
}

output "alb_dns_name" {
  description = "DNS del ALB del backend (origen de /api/* en CloudFront)."
  value       = module.backend.alb_dns_name
}
