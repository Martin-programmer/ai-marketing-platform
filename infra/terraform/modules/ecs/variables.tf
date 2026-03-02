variable "prefix" {
  description = "Resource name prefix"
  type        = string
}

variable "environment" {
  description = "Environment name"
  type        = string
}

variable "vpc_id" {
  description = "VPC ID"
  type        = string
}

variable "public_subnets" {
  description = "Public subnet IDs for ALB"
  type        = list(string)
}

variable "private_subnets" {
  description = "Private subnet IDs for ECS tasks"
  type        = list(string)
}

variable "api_image" {
  description = "Docker image for API service"
  type        = string
}

variable "worker_image" {
  description = "Docker image for Worker service"
  type        = string
}

variable "api_desired_count" {
  description = "Desired API task count"
  type        = number
}

variable "worker_desired_count" {
  description = "Desired Worker task count"
  type        = number
}

variable "db_endpoint" {
  description = "Aurora cluster writer endpoint"
  type        = string
}

variable "db_name" {
  description = "Database name"
  type        = string
}

variable "db_secret_arn" {
  description = "Secrets Manager ARN for DB credentials"
  type        = string
}

variable "assets_bucket_arn" {
  description = "S3 assets bucket ARN"
  type        = string
}

variable "sqs_queue_arns" {
  description = "SQS queue ARNs"
  type        = list(string)
}

variable "redis_endpoint" {
  description = "Redis endpoint address"
  type        = string
}
