variable "environment" {
  description = "Environment name (staging/prod)"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "eu-central-1"
}

variable "project" {
  description = "Project name"
  type        = string
  default     = "ai-marketing-platform"
}

variable "db_instance_class" {
  description = "Aurora instance class"
  type        = string
  default     = "db.t4g.medium"
}

variable "api_desired_count" {
  description = "Number of API ECS tasks"
  type        = number
  default     = 2
}

variable "worker_desired_count" {
  description = "Number of Worker ECS tasks"
  type        = number
  default     = 1
}

variable "domain_name" {
  description = "Domain name for the platform"
  type        = string
  default     = ""
}

variable "api_image" {
  description = "Docker image for API service"
  type        = string
}

variable "worker_image" {
  description = "Docker image for Worker service"
  type        = string
}

variable "frontend_bucket_name" {
  description = "S3 bucket for frontend static files"
  type        = string
  default     = ""
}
