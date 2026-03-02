variable "prefix" {
  description = "Resource name prefix"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "ecs_cluster" {
  description = "ECS cluster name"
  type        = string
}

variable "api_service" {
  description = "ECS API service name"
  type        = string
}

variable "worker_service" {
  description = "ECS Worker service name"
  type        = string
}

variable "sqs_queue_names" {
  description = "List of SQS queue names"
  type        = list(string)
}

variable "db_cluster_id" {
  description = "Aurora cluster identifier"
  type        = string
}
