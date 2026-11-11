# -----------------------------------------------------------------------------
# Backend en EC2 + Auto Scaling + ALB (D-23). Un contenedor por instancia.
# -----------------------------------------------------------------------------

data "aws_region" "current" {}

locals {
  alb_internal = var.api_exposure == "api_gateway"
  use_api_gw   = var.api_exposure == "api_gateway"
  autoscale    = var.max_instances > var.min_instances
}

# --- Registro de imagenes (ECR) ---
resource "aws_ecr_repository" "app" {
  name                 = var.name_prefix
  image_tag_mutability = "MUTABLE"
  force_delete         = true
  image_scanning_configuration {
    scan_on_push = true
  }
  tags = var.tags
}

# --- Rol de instancia: leer de ECR, enviar logs y (opcional) X-Ray ---
data "aws_iam_policy_document" "assume_ec2" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "instance" {
  name               = "${var.name_prefix}-ec2"
  assume_role_policy = data.aws_iam_policy_document.assume_ec2.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "ecr" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

resource "aws_iam_role_policy_attachment" "cloudwatch" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/CloudWatchAgentServerPolicy"
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy_attachment" "xray" {
  count      = var.enable_xray ? 1 : 0
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AWSXRayDaemonWriteAccess"
}

resource "aws_iam_instance_profile" "instance" {
  name = "${var.name_prefix}-ec2"
  role = aws_iam_role.instance.name
  tags = var.tags
}

# --- Security groups ---
resource "aws_security_group" "alb" {
  name        = "${var.name_prefix}-alb"
  description = "Entrada al ALB del backend"
  vpc_id      = var.vpc_id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-alb-sg" })
}

resource "aws_security_group" "instance" {
  name        = "${var.name_prefix}-instance"
  description = "Instancias del backend; solo aceptan trafico del ALB"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Trafico del ALB al contenedor"
    from_port       = var.container_port
    to_port         = var.container_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "${var.name_prefix}-instance-sg" })
}

# --- AMI base: Amazon Linux 2023 ---
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]
  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }
  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# --- user-data: instala Docker, autentica contra ECR y arranca el contenedor ---
locals {
  env_flags = join(" ", [for k, v in var.env_vars : "-e ${k}=${v}"])

  user_data = base64encode(templatefile("${path.module}/user-data.sh.tftpl", {
    aws_region      = data.aws_region.current.name
    container_image = var.container_image
    container_port  = var.container_port
    env_flags       = local.env_flags
    log_group       = var.log_group_name
    enable_xray     = var.enable_xray
  }))
}

resource "aws_launch_template" "app" {
  name_prefix   = "${var.name_prefix}-"
  image_id      = data.aws_ami.al2023.id
  instance_type = var.instance_type
  user_data     = local.user_data

  iam_instance_profile {
    arn = aws_iam_instance_profile.instance.arn
  }

  vpc_security_group_ids = [aws_security_group.instance.id]

  monitoring {
    enabled = var.detailed_monitoring
  }

  tag_specifications {
    resource_type = "instance"
    tags          = merge(var.tags, { Name = var.name_prefix })
  }

  lifecycle {
    create_before_destroy = true
  }
}

# --- ALB + target group + listener ---
resource "aws_lb" "app" {
  name               = substr("${var.name_prefix}-alb", 0, 32)
  internal           = local.alb_internal
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = local.alb_internal ? var.private_subnet_ids : var.public_subnet_ids
  tags               = var.tags
}

resource "aws_lb_target_group" "app" {
  name     = substr("${var.name_prefix}-tg", 0, 32)
  port     = var.container_port
  protocol = "HTTP"
  vpc_id   = var.vpc_id

  health_check {
    path                = var.health_check_path
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  tags = var.tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# --- Auto Scaling Group con instance refresh (despliegue escalonado + rollback) ---
resource "aws_autoscaling_group" "app" {
  name                      = var.name_prefix
  min_size                  = var.min_instances
  max_size                  = var.max_instances
  desired_capacity          = var.min_instances
  vpc_zone_identifier       = var.private_subnet_ids
  target_group_arns         = [aws_lb_target_group.app.arn]
  health_check_type         = "ELB"
  health_check_grace_period = 120

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  # Despliegue escalonado: al cambiar la version, renueva las instancias poco a poco
  # y vuelve atras si las nuevas no superan el health check.
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 50
      auto_rollback          = true
    }
  }

  dynamic "tag" {
    for_each = merge(var.tags, { Name = var.name_prefix })
    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = true
    }
  }
}

# --- Autoescalado por CPU (solo si max > min) ---
resource "aws_autoscaling_policy" "cpu" {
  count                  = local.autoscale ? 1 : 0
  name                   = "${var.name_prefix}-cpu"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = var.cpu_target
  }
}

# --- API Gateway opcional (HTTP API) delante del ALB interno ---
resource "aws_apigatewayv2_api" "app" {
  count         = local.use_api_gw ? 1 : 0
  name          = "${var.name_prefix}-api"
  protocol_type = "HTTP"
  tags          = var.tags
}

resource "aws_apigatewayv2_integration" "app" {
  count              = local.use_api_gw ? 1 : 0
  api_id             = aws_apigatewayv2_api.app[0].id
  integration_type   = "HTTP_PROXY"
  integration_method = "ANY"
  integration_uri    = aws_lb_listener.http.arn
  connection_type    = "VPC_LINK"
  connection_id      = aws_apigatewayv2_vpc_link.app[0].id
}

resource "aws_apigatewayv2_route" "app" {
  count     = local.use_api_gw ? 1 : 0
  api_id    = aws_apigatewayv2_api.app[0].id
  route_key = "ANY /{proxy+}"
  target    = "integrations/${aws_apigatewayv2_integration.app[0].id}"
}

resource "aws_apigatewayv2_vpc_link" "app" {
  count              = local.use_api_gw ? 1 : 0
  name               = "${var.name_prefix}-vpclink"
  security_group_ids = [aws_security_group.alb.id]
  subnet_ids         = var.private_subnet_ids
  tags               = var.tags
}

resource "aws_apigatewayv2_stage" "app" {
  count       = local.use_api_gw ? 1 : 0
  api_id      = aws_apigatewayv2_api.app[0].id
  name        = "$default"
  auto_deploy = true

  default_route_settings {
    throttling_rate_limit  = var.api_throttle_rps
    throttling_burst_limit = var.api_throttle_rps * 2
  }

  tags = var.tags
}
