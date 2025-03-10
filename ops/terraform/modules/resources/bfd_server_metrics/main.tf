locals {
  log_groups = {
    access = "/bfd/${var.env}/bfd-server/access.json"
  }
  namespace = "bfd-${var.env}/bfd-server"
  endpoints = {
    all                 = "*/fhir/*"
    metadata            = "*/fhir/metadata"
    coverageAll         = "*/fhir/Coverage"
    patientAll          = "*/fhir/Patient"
    eobAll              = "*/fhir/ExplanationOfBenefit"
    claimAll            = "*/fhir/Claim"
    claimResponseAll    = "*/fhir/ClaimResponse"
  }

  partners = {
    all  = "*"
    bb   = "*BlueButton*"
    bcda = "*bcda*"
    dpc  = "*dpc*"
    ab2d = "*ab2d*"
    test = "*data-server-client-test*"
  }

  metric_config = flatten([
    for endpoint_key, endpoint_value in local.endpoints : [
      for partner_key, partner_value in local.partners : {
        name    = "${endpoint_key}/${partner_key}"
        pattern = "($.mdc.http_access_request_clientSSL_DN = \"${partner_value}\") && ($.mdc.http_access_request_uri = \"${endpoint_value}\")"
      }
    ]
  ])
}

# Metric Filters Configured with new json format
# Count requests per endpoint, per partner
resource "aws_cloudwatch_log_metric_filter" "http_requests_count" {
  count          = length(local.metric_config)
  name           = "bfd-${var.env}/bfd-server/http-requests/count/${local.metric_config[count.index].name}"
  pattern        = "{ ${local.metric_config[count.index].pattern} }"
  log_group_name = local.log_groups.access

  metric_transformation {
    name          = "http-requests/count/${local.metric_config[count.index].name}"
    namespace     = local.namespace
    value         = "1"
    default_value = "0"
  }
}

# Latency per endpoint, per partner
resource "aws_cloudwatch_log_metric_filter" "http_requests_latency" {
  count          = length(local.metric_config)
  name           = "bfd-${var.env}/bfd-server/http-requests/latency/${local.metric_config[count.index].name}"
  pattern        = "{${local.metric_config[count.index].pattern} && ($.mdc.http_access_response_duration_milliseconds = *)}"
  log_group_name = local.log_groups.access

  metric_transformation {
    name          = "http-requests/latency/${local.metric_config[count.index].name}"
    namespace     = local.namespace
    value         = "$.mdc.http_access_response_duration_milliseconds"
    default_value = null
  }
}

# Count HTTP 500s per partner
resource "aws_cloudwatch_log_metric_filter" "http_requests_count_500" {
  for_each       = local.partners
  name           = "bfd-${var.env}/bfd-server/http-requests/count-500/${each.key}"
  pattern        = "{($.mdc.http_access_request_clientSSL_DN = \"${each.value}\") && ($.mdc.http_access_response_status = 500)}"
  log_group_name = local.log_groups.access

  metric_transformation {
    name          = "http-requests/count-500/${each.key}"
    namespace     = local.namespace
    value         = "1"
    default_value = "0"
  }
}

# Count HTTP non-2XXs per partner
resource "aws_cloudwatch_log_metric_filter" "http_requests_count_not_2xx" {
  for_each       = local.partners
  name           = "bfd-${var.env}/bfd-server/http-requests/count-not-2xx/${each.key}"
  pattern        = "{($.mdc.http_access_request_clientSSL_DN = \"${each.value}\") && ($.mdc.http_access_response_status != 200)}"
  log_group_name = local.log_groups.access

  metric_transformation {
    name          = "http-requests/count-not-2xx/${each.key}"
    namespace     = local.namespace
    value         = "1"
    default_value = "0"
  }
}
