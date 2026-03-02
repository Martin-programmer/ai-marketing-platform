resource "aws_db_subnet_group" "main" {
  name       = "${var.prefix}-aurora-subnets"
  subnet_ids = var.private_subnets
  tags       = { Name = "${var.prefix}-aurora-subnets" }
}

resource "aws_security_group" "aurora" {
  name   = "${var.prefix}-aurora-sg"
  vpc_id = var.vpc_id

  dynamic "ingress" {
    for_each = var.allowed_security_groups
    content {
      from_port       = 5432
      to_port         = 5432
      protocol        = "tcp"
      security_groups = [ingress.value]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${var.prefix}-aurora-sg" }
}

resource "random_password" "db" {
  length  = 32
  special = false
}

resource "aws_secretsmanager_secret" "db" {
  name = "${var.prefix}-db-credentials"
  tags = { Name = "${var.prefix}-db-credentials" }
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id
  secret_string = jsonencode({
    username = "amp"
    password = random_password.db.result
    dbname   = "amp"
  })
}

resource "aws_rds_cluster" "main" {
  cluster_identifier        = "${var.prefix}-aurora"
  engine                    = "aurora-postgresql"
  engine_version            = "16.1"
  database_name             = "amp"
  master_username           = "amp"
  master_password           = random_password.db.result
  db_subnet_group_name      = aws_db_subnet_group.main.name
  vpc_security_group_ids    = [aws_security_group.aurora.id]
  storage_encrypted         = true
  backup_retention_period   = var.environment == "prod" ? 14 : 7
  preferred_backup_window   = "03:00-04:00"
  skip_final_snapshot       = var.environment != "prod"
  final_snapshot_identifier = var.environment == "prod" ? "${var.prefix}-final-snapshot" : null
  tags                      = { Name = "${var.prefix}-aurora" }
}

resource "aws_rds_cluster_instance" "main" {
  count              = var.environment == "prod" ? 2 : 1
  identifier         = "${var.prefix}-aurora-${count.index + 1}"
  cluster_identifier = aws_rds_cluster.main.id
  instance_class     = var.instance_class
  engine             = aws_rds_cluster.main.engine
  engine_version     = aws_rds_cluster.main.engine_version
  tags               = { Name = "${var.prefix}-aurora-${count.index + 1}" }
}
