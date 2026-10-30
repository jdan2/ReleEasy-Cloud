output "log_group_name" {
  description = "Nombre del grupo de logs del backend (lo usa el user-data del modulo backend)."
  value       = aws_cloudwatch_log_group.app.name
}

output "alerts_topic_arn" {
  description = "ARN del topic SNS de alarmas (vacio si no hay alarmas)."
  value       = var.enable_alarms ? aws_sns_topic.alerts[0].arn : ""
}
