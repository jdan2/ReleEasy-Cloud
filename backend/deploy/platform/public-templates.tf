# -----------------------------------------------------------------------------
# Plantillas publicas de solo lectura (D-35).
# Aloja la plantilla de CloudFormation del rol del cliente para el enlace
# Quick-create del portal. Solo el prefijo cloudformation/ es legible por todos.
# -----------------------------------------------------------------------------

resource "aws_s3_bucket" "public" {
  bucket        = "releasy-public-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
  tags          = var.tags
}

# Se permite politica publica (la del prefijo cloudformation/); las ACL siguen bloqueadas.
resource "aws_s3_bucket_public_access_block" "public" {
  bucket                  = aws_s3_bucket.public.id
  block_public_acls       = true
  block_public_policy     = false
  ignore_public_acls      = true
  restrict_public_buckets = false
}

data "aws_iam_policy_document" "public_templates" {
  statement {
    sid       = "PublicReadCloudFormationTemplates"
    effect    = "Allow"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.public.arn}/cloudformation/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }
  }
}

resource "aws_s3_bucket_policy" "public" {
  bucket = aws_s3_bucket.public.id
  policy = data.aws_iam_policy_document.public_templates.json

  depends_on = [aws_s3_bucket_public_access_block.public]
}

resource "aws_s3_object" "role_template" {
  bucket       = aws_s3_bucket.public.id
  key          = "cloudformation/releasy-deploy-role.yaml"
  source       = "${path.module}/../bootstrap/releasy-deploy-role.yaml"
  etag         = filemd5("${path.module}/../bootstrap/releasy-deploy-role.yaml")
  content_type = "text/yaml"
  tags         = var.tags
}
