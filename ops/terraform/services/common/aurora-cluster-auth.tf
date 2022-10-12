locals {
  rds_db_access_users = [for user in data.aws_iam_group.db_access.users : user.user_name]
}

data "aws_iam_group" "db_access" {
  group_name = "bfd-${local.env}-db-access-group"
}

# group to manage db access for users
resource "aws_iam_group" "db_access" {
  count = local.rds_iam_database_authentication_enabled ? 1 : 0

  name = "bfd-${local.env}-db-access-group"
  path = "/"
}

# policy allowing local.rds_db_access_users access
resource "aws_iam_policy" "db_access" {
  count = local.rds_iam_database_authentication_enabled ? 1 : 0
  depends_on = [
    aws_iam_group.db_access
  ]

  name        = "bfd-${local.env}-db-auth-assume-role-policy"
  description = "Allows IAM authentication to the database"
  path        = "/"
  policy      = data.aws_iam_policy_document.db_access.json
  tags        = local.shared_tags
}

data "aws_iam_policy_document" "db_access" {
  statement {
    sid = "AllowDbAccess"

    actions = [
      "rds-db:connect",
    ]

    resources = [
      for user in local.rds_db_access_users : "arn:aws:rds-db:${local.region}:${local.account_id}:dbuser:${aws_rds_cluster.aurora_cluster.id}/${user}"
    ]
  }
}

# role users will assume when accessing the db
resource "aws_iam_role" "db_assume" {
  count = local.rds_iam_database_authentication_enabled ? 1 : 0

  name               = "bfd-${local.env}-db-access-assume-role"
  assume_role_policy = data.aws_iam_policy_document.db_assume.json
}

# assume role policy
resource "aws_iam_role_policy" "db_assume" {
  count = local.rds_iam_database_authentication_enabled ? 1 : 0

  name   = "bfd-${local.env}-db-access-assume-role-policy"
  role   = aws_iam_role.db_assume[0].id
  policy = data.aws_iam_policy_document.db_assume.json
}

data "aws_iam_policy_document" "db_assume" {
  statement {
    effect = "Allow"

    principals {
      type        = "AWS"
      identifiers = [for user in local.rds_db_access_users : "arn:aws:rds-db:${local.region}:${local.account_id}:dbuser:${aws_rds_cluster.aurora_cluster.id}/${user},"]
    }

    actions = [
      "sts:AssumeRole",
    ]
  }
}
