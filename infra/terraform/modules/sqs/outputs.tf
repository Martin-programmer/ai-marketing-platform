output "queue_arns" {
  description = "List of all SQS queue ARNs"
  value       = [for q in aws_sqs_queue.main : q.arn]
}

output "queue_names" {
  description = "List of all SQS queue names"
  value       = [for q in aws_sqs_queue.main : q.name]
}
