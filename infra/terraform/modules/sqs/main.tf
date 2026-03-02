locals {
  queues = ["meta-sync", "campaign-publish", "creative-analyze", "report-generate", "ai-suggestions"]
}

# ── Dead Letter Queues ───────────────────────────────────────────────────

resource "aws_sqs_queue" "dlq" {
  for_each                  = toset(local.queues)
  name                      = "${var.prefix}-${each.key}-dlq"
  message_retention_seconds = 1209600 # 14 days
  tags                      = { Name = "${var.prefix}-${each.key}-dlq" }
}

# ── Main queues with DLQ redrive ─────────────────────────────────────────

resource "aws_sqs_queue" "main" {
  for_each                   = toset(local.queues)
  name                       = "${var.prefix}-${each.key}"
  visibility_timeout_seconds = 300
  message_retention_seconds  = 86400 # 1 day
  receive_wait_time_seconds  = 20    # long polling

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dlq[each.key].arn
    maxReceiveCount     = 3
  })

  tags = { Name = "${var.prefix}-${each.key}" }
}

# ── EventBridge Scheduler rules ──────────────────────────────────────────

resource "aws_scheduler_schedule" "daily_sync" {
  name       = "${var.prefix}-daily-sync"
  group_name = "default"

  schedule_expression          = "cron(30 1 * * ? *)" # 03:30 Europe/Sofia = 01:30 UTC
  schedule_expression_timezone = "UTC"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = aws_sqs_queue.main["meta-sync"].arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ jobType = "DAILY", scope = "ALL_ACTIVE" })
  }
}

resource "aws_scheduler_schedule" "nightly_suggestions" {
  name       = "${var.prefix}-nightly-suggestions"
  group_name = "default"

  schedule_expression          = "cron(30 2 * * ? *)" # 04:30 Europe/Sofia = 02:30 UTC
  schedule_expression_timezone = "UTC"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = aws_sqs_queue.main["ai-suggestions"].arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ jobType = "NIGHTLY", scope = "ALL_ACTIVE" })
  }
}

resource "aws_scheduler_schedule" "monthly_reports" {
  name       = "${var.prefix}-monthly-reports"
  group_name = "default"

  schedule_expression          = "cron(0 4 1 * ? *)" # 1st of month 06:00 Europe/Sofia = 04:00 UTC
  schedule_expression_timezone = "UTC"

  flexible_time_window { mode = "OFF" }

  target {
    arn      = aws_sqs_queue.main["report-generate"].arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ jobType = "MONTHLY", scope = "ALL_ACTIVE" })
  }
}

# ── IAM role for EventBridge Scheduler ───────────────────────────────────

resource "aws_iam_role" "scheduler" {
  name = "${var.prefix}-scheduler-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
    }]
  })
}

resource "aws_iam_role_policy" "scheduler_sqs" {
  name = "${var.prefix}-scheduler-sqs"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["sqs:SendMessage"]
      Resource = [for q in aws_sqs_queue.main : q.arn]
    }]
  })
}
