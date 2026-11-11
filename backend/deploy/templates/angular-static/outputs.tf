output "url" {
  description = "URL publica de la aplicacion (dominio propio o de CloudFront)."
  value       = module.frontend.url
}

output "bucket_name" {
  description = "Bucket S3 al que el pipeline sube el contenido del front."
  value       = module.frontend.bucket_name
}

output "distribution_id" {
  description = "Distribucion de CloudFront a invalidar tras cada despliegue."
  value       = module.frontend.distribution_id
}
