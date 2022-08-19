# Terraform for investigating AWS Glue costs
#
# NOTE: This module depends on the resources in common.

locals {
  full_name            = "bfd-insights-${local.project}-investigations"
  full_name_underscore = replace(local.full_name, "-", "_")
  database             = local.full_name
  project              = "bfd"
  region               = "us-east-1"
  account_id           = data.aws_caller_identity.current.account_id
  tests                = toset(["original", "rename", "parquet", "g2x", "schema"])

  test_params = {
    original = {
      source_dir = "original"
      workers    = "20"
      instance   = "G.1X"
      storage    = "json"
    }
    rename = {
      source_dir = "rename"
      workers    = "20"
      instance   = "G.1X"
      storage    = "json"
    }
    parquet = {
      source_dir = "parquet"
      workers    = "20"
      instance   = "G.1X"
      storage    = "parquet"
    }
    g2x = {
      source_dir = "parquet" # Same script as the prior test
      workers    = "10"
      instance   = "G.2X"
      storage    = "parquet"
    }
    schema = {
      source_dir = "schema"
      workers    = "10"
      instance   = "G.2X"
      storage    = "parquet"
    }
  }

  tags = {
    business    = "OEDA"
    application = "bfd-insights"
    project     = "bfd"
    task        = "bfd-insights-investigations"
  }
}
