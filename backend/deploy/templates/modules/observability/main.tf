# -----------------------------------------------------------------------------
# Grupo de logs y alarmas del backend.
# -----------------------------------------------------------------------------

# --- Grupo de logs al que las instancias envian la salida del contenedor ---
# El nombre lo fija la composicion (var.log_group_name) para que el modulo backend
# pueda referenciar el mismo nombre sin crear una dependencia circular entre modulos.
resource "aws_cloudwatch_log_group" "app" {
  name              = var.log_group_name
  retention_in_days = var.log_retention_days
  tags              = var.tags
}

# --- Avisos por email (SNS) ---
resource "aws_sns_topic" "alerts" {
  count = var.enable_alarms ? 1 : 0
  name  = "${var.name_prefix}-alerts"
  tags  = var.tags
}

resource "aws_sns_topic_subscription" "email" {
  count     = var.enable_alarms && var.notification_email != "" ? 1 : 0
  topic_arn = aws_sns_topic.alerts[0].arn
  protocol  = "email"
  endpoint  = var.notification_email
}

# --- Alarma de CPU del Auto Scaling Group ---
resource "aws_cloudwatch_metric_alarm" "cpu" {
  count               = var.enable_alarms && var.autoscaling_group_name != "" ? 1 : 0
  alarm_name          = "${var.name_prefix}-cpu-alta"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/EC2"
  period              = 300
  statistic           = "Average"
  threshold           = 85
  alarm_description   = "CPU media del Auto Scaling Group por encima del 85%."
  alarm_actions       = [aws_sns_topic.alerts[0].arn]
  ok_actions          = [aws_sns_topic.alerts[0].arn]
  dimensions = {
    AutoScalingGroupName = var.autoscaling_group_name
  }
  tags = var.tags
}

# --- Alarma de errores 5xx del ALB ---
resource "aws_cloudwatch_metric_alarm" "errors_5xx" {
  count               = var.enable_alarms && var.alb_arn_suffix != "" ? 1 : 0
  alarm_name          = "${var.name_prefix}-5xx"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 10
  treat_missing_data  = "notBreaching"
  alarm_description   = "Mas de 10 respuestas 5xx del backend en 5 minutos."
  alarm_actions       = [aws_sns_topic.alerts[0].arn]
  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }
  tags = var.tags
}

# --- Alarma de latencia del ALB ---
resource "aws_cloudwatch_metric_alarm" "latency" {
  count               = var.enable_alarms && var.alb_arn_suffix != "" ? 1 : 0
  alarm_name          = "${var.name_prefix}-latencia"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "TargetResponseTime"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  extended_statistic  = "p95"
  threshold           = 2
  alarm_description   = "Latencia p95 del backend por encima de 2 s."
  alarm_actions       = [aws_sns_topic.alerts[0].arn]
  dimensions = {
    LoadBalancer = var.alb_arn_suffix
  }
  tags = var.tags
}
