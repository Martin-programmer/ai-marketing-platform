output "alb_dns_name" { value = module.ecs.alb_dns_name }
output "cdn_domain" { value = module.cdn.cdn_domain }
output "db_endpoint" { value = module.aurora.cluster_endpoint }
output "redis_endpoint" { value = module.vpc.redis_endpoint }
output "alarm_topic_arn" { value = module.monitoring.alarm_topic_arn }
