# -----------------------------------------------------------------------------
# Maquina de estados del despliegue. La arranca el backend (StepFunctionsDeployment
# Orchestrator) con el DeploymentRequest serializado como entrada. Encadena:
#   build (CodeBuild) -> deploy (CodeBuild) -> estado final.
# Un Catch marca el release como FAILED si cualquier paso falla.
#
# La definicion canonica tambien se versiona en pipelines/state-machine.asl.json; aqui
# se construye con jsonencode para inyectar los nombres reales de los proyectos CodeBuild.
# -----------------------------------------------------------------------------

resource "aws_sfn_state_machine" "deploy" {
  name     = "releasy-deploy"
  role_arn = aws_iam_role.sfn.arn
  tags     = var.tags

  definition = jsonencode({
    Comment = "Despliegue de un release de ReleEasy: build -> deploy."
    StartAt = "Build"
    States = {
      Build = {
        Type     = "Task"
        Resource = "arn:aws:states:::codebuild:startBuild.sync"
        Parameters = {
          ProjectName = aws_codebuild_project.build.name
          # Las variables de entorno llevan los datos del DeploymentRequest al build.
          EnvironmentVariablesOverride = [
            { "Name" = "RELEASE_ID", "Value.$" = "$.releaseId" },
            { "Name" = "TENANT_ID", "Value.$" = "$.tenantId" },
            { "Name" = "APP_ID", "Value.$" = "$.appId" },
            { "Name" = "APP_NAME", "Value.$" = "$.appName" },
            { "Name" = "ACCOUNT_ID", "Value.$" = "$.accountId" },
            { "Name" = "TENANT_ROLE_ARN", "Value.$" = "$.tenantRoleArn" },
            { "Name" = "EXTERNAL_ID", "Value.$" = "$.externalId" },
            { "Name" = "REPOSITORY", "Value.$" = "$.repository" },
            { "Name" = "BRANCH", "Value.$" = "$.branch" },
            { "Name" = "COMMIT_SHA", "Value.$" = "$.commitSha" },
            { "Name" = "TEMPLATE_ID", "Value.$" = "$.templateId" },
          ]
        }
        ResultPath = "$.buildResult"
        Next       = "Deploy"
        Catch = [{
          ErrorEquals = ["States.ALL"]
          ResultPath  = "$.error"
          Next        = "Failed"
        }]
      }
      Deploy = {
        Type     = "Task"
        Resource = "arn:aws:states:::codebuild:startBuild.sync"
        Parameters = {
          ProjectName = aws_codebuild_project.deploy.name
          EnvironmentVariablesOverride = [
            { "Name" = "RELEASE_ID", "Value.$" = "$.releaseId" },
            { "Name" = "TENANT_ID", "Value.$" = "$.tenantId" },
            { "Name" = "APP_ID", "Value.$" = "$.appId" },
            { "Name" = "APP_NAME", "Value.$" = "$.appName" },
            { "Name" = "ACCOUNT_ID", "Value.$" = "$.accountId" },
            { "Name" = "TENANT_ROLE_ARN", "Value.$" = "$.tenantRoleArn" },
            { "Name" = "EXTERNAL_ID", "Value.$" = "$.externalId" },
            { "Name" = "TEMPLATE_ID", "Value.$" = "$.templateId" },
          ]
        }
        ResultPath = "$.deployResult"
        Next       = "Succeeded"
        Catch = [{
          ErrorEquals = ["States.ALL"]
          ResultPath  = "$.error"
          Next        = "Failed"
        }]
      }
      Succeeded = {
        Type = "Succeed"
      }
      Failed = {
        Type  = "Fail"
        Error = "DeploymentFailed"
        Cause = "El build o el deploy del release fallaron; el estado FAILED lo escribe el propio pipeline."
      }
    }
  })
}
