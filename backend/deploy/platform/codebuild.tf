# -----------------------------------------------------------------------------
# Proyectos CodeBuild: build (construye y publica la imagen) y deploy (terraform apply).
# Los buildspec viven en backend/deploy/pipelines/ (bloque siguiente de la Fase 4).
# La entrada de cada build llega como variables de entorno desde Step Functions.
# -----------------------------------------------------------------------------

locals {
  # Imagen gestionada de CodeBuild con Docker y herramientas de build.
  codebuild_image = "aws/codebuild/amazonlinux-x86_64-standard:5.0"
}

resource "aws_codebuild_project" "build" {
  name         = "releasy-build"
  description  = "Construye el codigo del cliente en el commit indicado y publica la imagen en ECR."
  service_role = aws_iam_role.codebuild.arn

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type    = "BUILD_GENERAL1_SMALL"
    image           = local.codebuild_image
    type            = "LINUX_CONTAINER"
    privileged_mode = true # necesario para construir imagenes Docker

    environment_variable {
      name  = "STATE_BUCKET"
      value = aws_s3_bucket.state.bucket
    }
  }

  source {
    type      = "NO_SOURCE"
    buildspec = file("${path.module}/../pipelines/buildspec-build.yml")
  }

  logs_config {
    cloudwatch_logs {
      group_name = "/aws/codebuild/releasy-build"
    }
  }

  tags = var.tags
}

resource "aws_codebuild_project" "deploy" {
  name         = "releasy-deploy"
  description  = "Ejecuta terraform apply de la plantilla en la cuenta del cliente y actualiza el estado del release."
  service_role = aws_iam_role.codebuild.arn

  artifacts {
    type = "NO_ARTIFACTS"
  }

  environment {
    compute_type    = "BUILD_GENERAL1_SMALL"
    image           = local.codebuild_image
    type            = "LINUX_CONTAINER"
    privileged_mode = false

    environment_variable {
      name  = "STATE_BUCKET"
      value = aws_s3_bucket.state.bucket
    }
    environment_variable {
      name  = "RELEASES_TABLE"
      value = aws_dynamodb_table.releases.name
    }
    environment_variable {
      name  = "APPS_TABLE"
      value = aws_dynamodb_table.apps.name
    }
  }

  source {
    type      = "NO_SOURCE"
    buildspec = file("${path.module}/../pipelines/buildspec-deploy.yml")
  }

  logs_config {
    cloudwatch_logs {
      group_name = "/aws/codebuild/releasy-deploy"
    }
  }

  tags = var.tags
}
