output "cluster_name" {
  description = "ECS cluster name"
  value       = aws_ecs_cluster.main.name
}

output "api_service_name" {
  description = "ECS API service name"
  value       = aws_ecs_service.api.name
}

output "worker_service_name" {
  description = "ECS Worker service name"
  value       = aws_ecs_service.worker.name
}

output "api_security_group_id" {
  description = "API task security group ID"
  value       = aws_security_group.api.id
}

output "worker_security_group_id" {
  description = "Worker task security group ID"
  value       = aws_security_group.worker.id
}

output "alb_dns_name" {
  description = "ALB DNS name"
  value       = aws_lb.api.dns_name
}
