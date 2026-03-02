environment          = "prod"
aws_region           = "eu-central-1"
db_instance_class    = "db.t4g.medium"
api_desired_count    = 2
worker_desired_count = 1
api_image            = "ACCOUNT_ID.dkr.ecr.eu-central-1.amazonaws.com/amp-api:v0.1.0"
worker_image         = "ACCOUNT_ID.dkr.ecr.eu-central-1.amazonaws.com/amp-worker:v0.1.0"
