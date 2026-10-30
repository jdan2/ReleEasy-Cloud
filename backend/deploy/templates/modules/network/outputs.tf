output "vpc_id" {
  description = "ID de la VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "IDs de las subredes publicas (ALB)."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs de las subredes privadas (instancias EC2 del backend)."
  value       = aws_subnet.private[*].id
}
