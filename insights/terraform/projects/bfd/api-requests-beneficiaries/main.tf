# Beneficiary and Beneficiary Unique tables and glue jobs to provide BFD Insights with analysis of
# the number of beneficiaries and when they were first seen.
#
# NOTE: This depends on the api-requests section of BFD Insights.

locals {
  environment          = terraform.workspace
  full_name            = "bfd-insights-${local.project}-${local.environment}"
  full_name_underscore = replace(local.full_name, "-", "_")
  database             = local.full_name
  project              = "bfd"
  region               = "us-east-1"
  account_id           = data.aws_caller_identity.current.account_id
  glue_workflow_name   = "${local.full_name}-update-insights-workflow"

  # How many Glue Job workers to use for the Glue jobs. Note that there is a cap of 500
  # total workers (1,000 DPUs) across all Glue jobs. See README for details.
  glue_worker_count    = (local.environment == "prod" ? 100 : 50)

  # Maximum size of files for the Glue jobs to process at once.
  # 2 GB (prod) or 0.5 GB (everything else). Unit is bytes, must be string.
  glue_file_size  = (local.environment == "prod" ? "2000000000" : "500000000")

  # Should we schedule the Glue jobs? Currently, we only run prod and prod-sbx jobs on a schedule.
  schedule_glue_jobs   = false # local.environment == "prod" || local.environment == "prod-sbx"

  tags = {
    business    = "OEDA",
    application = "bfd-insights",
    project     = "bfd"
  }
}
