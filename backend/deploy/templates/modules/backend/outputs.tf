output "ecr_repository_url" {
  description = "URL del repositorio ECR al que el pipeline sube la imagen del backend."
  value       = aws_ecr_repository.app.repository_url
}

output "alb_dns_name" {
  description = "DNS del ALB. Es el origen de /api/* en la plantilla combinada."
  value       = aws_lb.app.dns_name
}

output "alb_arn_suffix" {
  description = "Sufijo del ARN del ALB, para las metricas de CloudWatch (modulo observability)."
  value       = aws_lb.app.arn_suffix
}

output "autoscaling_group_name" {
  description = "Nombre del Auto Scaling Group del backend."
  value       = aws_autoscaling_group.app.name
}

output "api_url" {
  description = "URL publica de la API: la de API Gateway si esta activo, o la del ALB."
  value       = local.use_api_gw ? aws_apigatewayv2_stage.app[0].invoke_url : "http://${aws_lb.app.dns_name}"
}
