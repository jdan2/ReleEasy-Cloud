output "state_bucket_name" {
  description = "Bucket del estado de Terraform. El pipeline lo usa con -backend-config."
  value       = aws_s3_bucket.state.bucket
}

output "state_machine_arn" {
  description = "ARN de la maquina de estados. Es el RELEASY_STATE_MACHINE_ARN del backend en modo real."
  value       = aws_sfn_state_machine.deploy.arn
}

output "codebuild_build_project" {
  description = "Nombre del proyecto CodeBuild de build."
  value       = aws_codebuild_project.build.name
}

output "codebuild_deploy_project" {
  description = "Nombre del proyecto CodeBuild de deploy."
  value       = aws_codebuild_project.deploy.name
}

output "codebuild_role_arn" {
  description = "ARN del rol de CodeBuild (el que asume ReleEasyDeployRole en la cuenta del cliente)."
  value       = aws_iam_role.codebuild.arn
}

output "dynamodb_tables" {
  description = "Nombres de las tablas DynamoDB de la plataforma."
  value = {
    users    = aws_dynamodb_table.users.name
    tenants  = aws_dynamodb_table.tenants.name
    apps     = aws_dynamodb_table.apps.name
    releases = aws_dynamodb_table.releases.name
  }
}
