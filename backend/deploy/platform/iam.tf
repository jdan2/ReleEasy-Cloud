# -----------------------------------------------------------------------------
# Roles IAM de la plataforma: CodeBuild (build y deploy) y Step Functions.
# -----------------------------------------------------------------------------

data "aws_caller_identity" "current" {}

# =============================================================================
# Rol de CodeBuild
# Lo comparten los dos proyectos (build y deploy). Puede: escribir logs, leer/escribir
# el estado de Terraform en S3, usar ECR, leer el secreto de la GitHub App y, sobre todo,
# ASUMIR el ReleEasyDeployRole en la cuenta del cliente (con External ID) para desplegar.
# =============================================================================
data "aws_iam_policy_document" "codebuild_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["codebuild.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "codebuild" {
  name               = "releasy-codebuild"
  assume_role_policy = data.aws_iam_policy_document.codebuild_assume.json
  tags               = var.tags
}

data "aws_iam_policy_document" "codebuild" {
  # Logs de CodeBuild.
  statement {
    sid = "Logs"
    actions = [
      "logs:CreateLogGroup",
      "logs:CreateLogStream",
      "logs:PutLogEvents",
    ]
    resources = ["arn:aws:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/aws/codebuild/releasy-*"]
  }

  # Estado de Terraform en el bucket de la plataforma.
  statement {
    sid       = "TerraformState"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
    resources = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]
  }

  # Estado del release en DynamoDB (la escribe el pipeline, tambien si falla).
  statement {
    sid       = "ReleasesTable"
    actions   = ["dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:GetItem"]
    resources = [aws_dynamodb_table.releases.arn, aws_dynamodb_table.apps.arn]
  }

  # ECR: construir y subir la imagen del cliente (el repositorio vive en la cuenta del cliente,
  # asi que el push real lo hace la sesion asumida; aqui basta con el token de autenticacion).
  statement {
    sid       = "EcrAuth"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  # Secreto con el token de la GitHub App (si se configuro).
  dynamic "statement" {
    for_each = var.github_token_secret_arn != "" ? [1] : []
    content {
      sid       = "GithubSecret"
      actions   = ["secretsmanager:GetSecretValue"]
      resources = [var.github_token_secret_arn]
    }
  }

  # Asumir el rol de despliegue en la cuenta del cliente. El recurso es cualquier rol
  # llamado ReleEasyDeployRole en cualquier cuenta; la confianza real la impone la
  # politica del propio rol del cliente (cuenta de la plataforma + External ID).
  statement {
    sid       = "AssumeTenantRole"
    actions   = ["sts:AssumeRole"]
    resources = ["arn:aws:iam::*:role/ReleEasyDeployRole"]
  }
}

resource "aws_iam_role_policy" "codebuild" {
  name   = "releasy-codebuild"
  role   = aws_iam_role.codebuild.id
  policy = data.aws_iam_policy_document.codebuild.json
}

# =============================================================================
# Rol de Step Functions
# La maquina de estados solo necesita arrancar y vigilar los proyectos de CodeBuild.
# =============================================================================
data "aws_iam_policy_document" "sfn_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["states.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "sfn" {
  name               = "releasy-stepfunctions"
  assume_role_policy = data.aws_iam_policy_document.sfn_assume.json
  tags               = var.tags
}

data "aws_iam_policy_document" "sfn" {
  statement {
    sid = "RunCodeBuild"
    actions = [
      "codebuild:StartBuild",
      "codebuild:StopBuild",
      "codebuild:BatchGetBuilds",
    ]
    resources = [aws_codebuild_project.build.arn, aws_codebuild_project.deploy.arn]
  }

  # Step Functions usa reglas de EventBridge para esperar a que CodeBuild termine (patron .sync).
  statement {
    sid = "CodeBuildSync"
    actions = [
      "events:PutTargets",
      "events:PutRule",
      "events:DescribeRule",
    ]
    resources = ["arn:aws:events:${var.region}:${data.aws_caller_identity.current.account_id}:rule/StepFunctionsGetEventForCodeBuildStartBuildRule"]
  }
}

resource "aws_iam_role_policy" "sfn" {
  name   = "releasy-stepfunctions"
  role   = aws_iam_role.sfn.id
  policy = data.aws_iam_policy_document.sfn.json
}
