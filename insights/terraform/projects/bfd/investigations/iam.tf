# Current Account
data "aws_caller_identity" "current" {}

# Glue

# Role for Glue to assume with S3 permissions
data "aws_iam_role" "iam-role-glue" {
  name = "bfd-insights-bfd-glue-role"
}
