# -----------------------------------------------------------------------------
# Almacenamiento de la plataforma: estado de Terraform (S3) y datos (DynamoDB).
# -----------------------------------------------------------------------------

# --- Bucket del estado de Terraform de las apps de los clientes ---
resource "aws_s3_bucket" "state" {
  bucket = var.state_bucket_name
  tags   = var.tags
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# --- Tablas DynamoDB de la plataforma (modo real del backend) ---
# Las claves e indices coinciden con el modelo del backend (infrastructure/adapters/dynamodb):
#   users:    particion email
#   tenants:  particion tenantId
#   apps:     particion appId + GSI tenantId-index y repository-index
#   releases: particion appId + ordenacion releaseId

resource "aws_dynamodb_table" "users" {
  name         = "releasy-users"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "email"

  attribute {
    name = "email"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = merge(var.tags, { Name = "releasy-users" })
}

resource "aws_dynamodb_table" "tenants" {
  name         = "releasy-tenants"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "tenantId"

  attribute {
    name = "tenantId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = merge(var.tags, { Name = "releasy-tenants" })
}

resource "aws_dynamodb_table" "apps" {
  name         = "releasy-apps"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "appId"

  attribute {
    name = "appId"
    type = "S"
  }
  attribute {
    name = "tenantId"
    type = "S"
  }
  attribute {
    name = "repository"
    type = "S"
  }

  global_secondary_index {
    name            = "tenantId-index"
    hash_key        = "tenantId"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "repository-index"
    hash_key        = "repository"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = merge(var.tags, { Name = "releasy-apps" })
}

resource "aws_dynamodb_table" "releases" {
  name         = "releasy-releases"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "appId"
  range_key    = "releaseId"

  attribute {
    name = "appId"
    type = "S"
  }
  attribute {
    name = "releaseId"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = merge(var.tags, { Name = "releasy-releases" })
}
