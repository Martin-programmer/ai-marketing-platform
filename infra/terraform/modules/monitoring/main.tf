# ── SNS Topic for alarms ─────────────────────────────────────────────────

resource "aws_sns_topic" "alarms" {
  name = "${var.prefix}-alarms"
}

# ── API error rate alarm ─────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  alarm_name          = "${var.prefix}-api-5xx-errors"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 2
  metric_name         = "HTTPCode_Target_5XX_Count"
  namespace           = "AWS/ApplicationELB"
  period              = 300
  statistic           = "Sum"
  threshold           = 10
  alarm_description   = "API 5xx errors exceed threshold"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  tags                = { Name = "${var.prefix}-api-5xx" }
}

# ── DLQ alarms for each queue ───────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "dlq" {
  for_each            = toset(var.sqs_queue_names)
  alarm_name          = "${each.key}-dlq-not-empty"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 300
  statistic           = "Maximum"
  threshold           = 0
  alarm_description   = "DLQ ${each.key} has messages"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  dimensions          = { QueueName = "${each.key}-dlq" }
}

# ── DB CPU alarm ─────────────────────────────────────────────────────────

resource "aws_cloudwatch_metric_alarm" "db_cpu" {
  alarm_name          = "${var.prefix}-aurora-cpu-high"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 3
  metric_name         = "CPUUtilization"
  namespace           = "AWS/RDS"
  period              = 300
  statistic           = "Average"
  threshold           = 80
  alarm_description   = "Aurora CPU > 80%"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  dimensions          = { DBClusterIdentifier = var.db_cluster_id }
}

# ── CloudWatch Dashboard ────────────────────────────────────────────────

resource "aws_cloudwatch_dashboard" "main" {
  dashboard_name = "${var.prefix}-overview"
  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric"
        x    = 0, y = 0, width = 12, height = 6
        properties = {
          title   = "API Latency (p95)"
          metrics = [["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", "${var.prefix}-alb"]]
          period  = 300
          stat    = "p95"
        }
      },
      {
        type = "metric"
        x    = 12, y = 0, width = 12, height = 6
        properties = {
          title   = "Aurora CPU"
          metrics = [["AWS/RDS", "CPUUtilization", "DBClusterIdentifier", var.db_cluster_id]]
          period  = 300
        }
      }
    ]
  })
}
