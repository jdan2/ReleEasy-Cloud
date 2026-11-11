output "url" {
  description = "URL publica de la API (API Gateway si esta activo, o el ALB)."
  value       = module.backend.api_url
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
  description = "DNS del ALB del backend."
  value       = module.backend.alb_dns_name
}

output "log_group_name" {
  description = "Grupo de logs del backend en CloudWatch."
  value       = module.observability.log_group_name
}
