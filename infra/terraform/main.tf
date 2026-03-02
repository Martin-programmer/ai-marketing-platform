terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # backend "s3" {
  #   # Configure per environment:
  #   # bucket = "ai-marketing-platform-tfstate-<env>"
  #   # key    = "terraform.tfstate"
  #   # region = "eu-central-1"
  # }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project
      Environment = var.environment
      ManagedBy   = "terraform"
    }
  }
}

locals {
  prefix = "${var.project}-${var.environment}"
}

module "vpc" {
  source      = "./modules/vpc"
  prefix      = local.prefix
  environment = var.environment
  aws_region  = var.aws_region
}

module "aurora" {
  source                  = "./modules/aurora"
  prefix                  = local.prefix
  environment             = var.environment
  vpc_id                  = module.vpc.vpc_id
  private_subnets         = module.vpc.private_subnet_ids
  instance_class          = var.db_instance_class
  allowed_security_groups = [module.ecs.api_security_group_id, module.ecs.worker_security_group_id]
}

module "s3" {
  source      = "./modules/s3"
  prefix      = local.prefix
  environment = var.environment
}

module "sqs" {
  source      = "./modules/sqs"
  prefix      = local.prefix
  environment = var.environment
}

module "ecs" {
  source               = "./modules/ecs"
  prefix               = local.prefix
  environment          = var.environment
  vpc_id               = module.vpc.vpc_id
  public_subnets       = module.vpc.public_subnet_ids
  private_subnets      = module.vpc.private_subnet_ids
  api_image            = var.api_image
  worker_image         = var.worker_image
  api_desired_count    = var.api_desired_count
  worker_desired_count = var.worker_desired_count
  db_endpoint          = module.aurora.cluster_endpoint
  db_name              = module.aurora.database_name
  db_secret_arn        = module.aurora.db_secret_arn
  assets_bucket_arn    = module.s3.assets_bucket_arn
  sqs_queue_arns       = module.sqs.queue_arns
  redis_endpoint       = module.vpc.redis_endpoint
}

module "cdn" {
  source                          = "./modules/cdn"
  prefix                          = local.prefix
  environment                     = var.environment
  frontend_bucket                 = module.s3.frontend_bucket_name
  frontend_bucket_arn             = module.s3.frontend_bucket_arn
  frontend_bucket_regional_domain = module.s3.frontend_bucket_regional_domain
  domain_name                     = var.domain_name
}

module "monitoring" {
  source          = "./modules/monitoring"
  prefix          = local.prefix
  environment     = var.environment
  ecs_cluster     = module.ecs.cluster_name
  api_service     = module.ecs.api_service_name
  worker_service  = module.ecs.worker_service_name
  sqs_queue_names = module.sqs.queue_names
  db_cluster_id   = module.aurora.cluster_id
}
