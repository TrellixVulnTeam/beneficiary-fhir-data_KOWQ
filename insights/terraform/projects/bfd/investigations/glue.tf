# Creates AWS Glue Database named "bfd-insights-bfd-investigations"
module "database" {
  source     = "../../../modules/database"
  database   = local.database
  bucket     = data.aws_s3_bucket.bfd-insights-bucket.bucket
  bucket_cmk = data.aws_kms_key.kms_key.arn
  tags       = local.tags
}

# BFD History
#
# Storage for ingesting unaltered json log files. This table is used as the source for all tests.

# Glue Table to store API History
module "glue-table-api-history" {
  source         = "../../../modules/table"
  table          = "${local.full_name_underscore}_api_history"
  description    = "Store log files from BFD for analysis in BFD Insights"
  database       = module.database.name
  bucket         = data.aws_s3_bucket.bfd-insights-bucket.bucket
  bucket_cmk     = data.aws_kms_key.kms_key.arn
  storage_format = "json"
  serde_format   = "grok"
  serde_parameters = {
    "input.format" = "%%{TIMESTAMP_ISO8601:timestamp:string} %%{GREEDYDATA:message:string}"
  }
  tags           = local.tags
  partitions = [
    {
      name    = "partition_0"
      type    = "string"
      comment = ""
    },
    {
      name    = "partition_1"
      type    = "string"
      comment = ""
    }
  ]

  # Don't specify here, because the schema is complex and it's sufficient to
  # allow the crawler to define the columns
  columns = []
}

# Glue Crawler for the API History table
resource "aws_glue_crawler" "glue-crawler-api-history" {
  database_name = module.database.name
  name          = "${local.full_name}-history-crawler"
  description   = "Glue Crawler to ingest logs into the API History Glue Table"
  role          = data.aws_iam_role.iam-role-glue.arn

  classifiers = [
    aws_glue_classifier.glue-classifier-api-history.name,
  ]

  lineage_configuration {
    crawler_lineage_settings = "DISABLE"
  }

  recrawl_policy {
    recrawl_behavior = "CRAWL_EVERYTHING"
  }

  catalog_target {
    database_name = module.database.name
    tables        = [ module.glue-table-api-history.name ]
  }

  schema_change_policy {
    delete_behavior = "LOG" # "DEPRECATE_IN_DATABASE"
    update_behavior = "UPDATE_IN_DATABASE"
  }

  configuration = jsonencode(
    {
      "Version": 1.0,
      "Grouping": {
        "TableGroupingPolicy": "CombineCompatibleSchemas"
      }
    }
  )
}

# Classifier for the History Crawler
resource "aws_glue_classifier" "glue-classifier-api-history" {
  name = "${local.full_name}-historicals-local"

  grok_classifier {
    classification = "cw-history"
    grok_pattern   = "%%{TIMESTAMP_ISO8601:timestamp:string} %%{GREEDYDATA:message:string}"
  }
}


## Test Resources

# API Requests
#
# Target location for ingested logs, no matter the method of ingestion.

# Target Glue Table where ingested logs are eventually stored
module "glue-table-api-requests" {
  for_each       = local.tests
  source         = "../../../modules/table"
  table          = "${local.full_name_underscore}_${each.key}_api_requests"
  description    = "Target Glue Table where ingested logs are eventually stored"
  database       = module.database.name
  bucket         = data.aws_s3_bucket.bfd-insights-bucket.bucket
  bucket_cmk     = data.aws_kms_key.kms_key.arn
  storage_format = local.test_params[each.key].storage
  serde_format   = local.test_params[each.key].storage
  tags           = local.tags

  partitions = [
    {
      name    = "year"
      type    = "string"
      comment = "Year of request"
    },
    {
      name    = "month"
      type    = "string"
      comment = "Month of request"
    },
    {
      name    = "day"
      type    = "string"
      comment = "Day of request"
    }
  ]

  # Don't specify here, because the schema is complex and it's sufficient to
  # allow the crawler to define the columns
  columns = [] 
}

# S3 object containing the Glue Script for history ingestion
resource "aws_s3_object" "s3-script-history-ingest" {
  for_each           = local.tests
  bucket             = data.aws_s3_bucket.bfd-insights-bucket.id
  bucket_key_enabled = false
  content_type       = "application/octet-stream; charset=UTF-8"
  key                = "scripts/investigations/${each.key}/bfd_history_ingest.py"
  metadata           = {}
  storage_class      = "STANDARD"
  # Parquet and G2X tests use the same source
  source_hash        = filemd5("glue_src/${local.test_params[each.key].source_dir}/bfd_history_ingest.py")
  source             = "glue_src/${local.test_params[each.key].source_dir}/bfd_history_ingest.py"
}

# Glue Job for history ingestion
resource "aws_glue_job" "glue-job-history-ingest" {
  for_each                  = local.tests
  name                      = "${local.full_name}-${each.key}-history-ingest"
  description               = "Ingest historical log data"
  connections               = []
  glue_version              = "3.0"
  max_retries               = 0
  non_overridable_arguments = {}
  number_of_workers         = local.test_params[each.key].workers
  role_arn                  = data.aws_iam_role.iam-role-glue.arn
  timeout                   = 240
  worker_type               = local.test_params[each.key].instance

  tags = {
    "test-id" = each.key
  }

  default_arguments = {
    "--TempDir"                          = "s3://${data.aws_s3_bucket.bfd-insights-bucket.id}/temporary/${each.key}/"
    "--class"                            = "GlueApp"
    "--enable-continuous-cloudwatch-log" = "true"
    "--enable-glue-datacatalog"          = "true"
    "--enable-job-insights"              = "true"
    "--enable-metrics"                   = "true"
    "--enable-spark-ui"                  = "true"
    "--job-bookmark-option"              = "job-bookmark-enable"
    "--job-language"                     = "python"
    "--spark-event-logs-path"            = "s3://${data.aws_s3_bucket.bfd-insights-bucket.id}/sparkHistoryLogs/${each.key}/"
    "--tempLocation"                     = "s3://${data.aws_s3_bucket.bfd-insights-bucket.id}/temp/${each.key}/history-ingest/"
    "--sourceDatabase"                   = module.database.name
    "--sourceTable"                      = module.glue-table-api-history.name
    "--targetDatabase"                   = module.database.name
    "--targetTable"                      = module.glue-table-api-requests[each.key].name
  }

  command {
    name            = "glueetl"
    python_version  = "3"
    script_location = "s3://${aws_s3_object.s3-script-history-ingest[each.key].bucket}/${aws_s3_object.s3-script-history-ingest[each.key].key}"
  }

  execution_property {
    max_concurrent_runs = 1
  }
}

# Crawler for the API Requests table
resource "aws_glue_crawler" "glue-crawler-api-requests" {
  for_each      = local.tests
  classifiers   = []
  database_name = module.database.name
  name          = "${local.full_name}-${each.key}-api-requests-crawler"
  role          = data.aws_iam_role.iam-role-glue.arn

  configuration = jsonencode(
    {
      CrawlerOutput = {
        Partitions = {
          AddOrUpdateBehavior = "InheritFromTable"
        }
      }
      Grouping = {
        TableGroupingPolicy = "CombineCompatibleSchemas"
      }
      Version = 1
    }
  )

  catalog_target {
    database_name = module.database.name
    tables = [
      module.glue-table-api-requests[each.key].name,
    ]
  }

  lineage_configuration {
    crawler_lineage_settings = "DISABLE"
  }

  recrawl_policy {
    recrawl_behavior = "CRAWL_EVERYTHING"
  }

  schema_change_policy {
    delete_behavior = "LOG"
    update_behavior = "UPDATE_IN_DATABASE"
  }
}
