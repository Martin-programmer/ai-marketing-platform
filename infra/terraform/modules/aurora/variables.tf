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

variable "private_subnets" {
  description = "Private subnet IDs for DB placement"
  type        = list(string)
}

variable "instance_class" {
  description = "Aurora instance class"
  type        = string
}

variable "allowed_security_groups" {
  description = "Security group IDs allowed to connect to Aurora"
  type        = list(string)
}
