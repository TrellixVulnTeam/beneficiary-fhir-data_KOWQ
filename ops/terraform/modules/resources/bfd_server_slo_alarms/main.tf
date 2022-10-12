locals {
  app = "bfd-server"

  alert_arn   = var.alert_notification_arn == null ? [] : [var.alert_notification_arn]
  warning_arn = var.warning_notification_arn == null ? [] : [var.warning_notification_arn]
  ok_arn      = var.ok_notification_arn == null ? [] : [var.ok_notification_arn]

  namespace = "bfd-${var.env}/${local.app}"
  metrics = {
    coverage_latency                    = "http-requests/latency/coverageAll"
    eob_latency                         = "http-requests/latency/eobAll"
    eob_latency_by_kb                   = "http-requests/latency-by-kb/eobAll"
    patient_no_contract_latency         = "http-requests/latency/patientNotByContract"
    patient_contract_count_4000_latency = "http-requests/latency/patientByContractCount4000"
    all_error_rate                      = "http-requests/count-500"
  }

  bulk_partners = [
    "bcda",
    "dpc",
    "abd2d"
  ]

  non_bulk_partners = [
    "bb"
  ]

  partner_timeouts_ms = {
    ab2d = (300 * 1000) / 2
    bcda = (45 * 1000) / 2
    dpc  = (30 * 1000) / 2
    bb   = (120 * 1000) / 2
  }

  ext_stat_99p = "p99"
}

resource "aws_cloudwatch_metric_alarm" "slo_coverage_latency_mean_15m_alert" {
  alarm_name          = "${local.app}-${var.env}-slo-coverage-latency-mean-15m-alert"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  period              = "900"
  statistic           = "Average"
  threshold           = "260"
  alarm_description   = "/v*/fhir/Coverage response mean 15 minute latency exceeded ALERT SLO threshold of 260ms for ${local.app}"

  metric_name = "${local.metrics.coverage_latency}/all"
  namespace   = local.namespace

  alarm_actions = local.alert_arn
  ok_actions    = local.ok_arn

  datapoints_to_alarm = "1"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "slo_coverage_latency_mean_15m_warning" {
  alarm_name          = "${local.app}-${var.env}-slo-coverage-latency-mean-15m-warning"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  period              = "900"
  statistic           = "Average"
  threshold           = "180"
  alarm_description   = "/v*/fhir/Coverage response mean 15 minute latency exceeded WARNING SLO threshold of 180ms for ${local.app}"

  metric_name = "${local.metrics.coverage_latency}/all"
  namespace   = local.namespace

  alarm_actions = local.warning_arn
  ok_actions    = local.ok_arn

  datapoints_to_alarm = "1"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "slo_coverage_bulk_latency_99p_15m_alert" {
  for_each = local.bulk_partners

  alarm_name          = "${local.app}-${var.env}-slo-coverage-bulk-latency-99p-15m-alert-${each.key}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  period              = "900"
  extended_statistic  = local.ext_stat_99p
  threshold           = "${local.partner_timeouts_ms[each.key]}"
  alarm_description = join("", [
    "/v*/fhir/Coverage response 99% 15 minute BULK latency exceeded ALERT SLO threshold of ",
    "${local.partner_timeouts_ms[each.key]} ms for partner ${each.key} for ${local.app}"
  ])

  metric_name = "${local.metrics.coverage_latency}/${each.key}"
  namespace   = local.namespace

  alarm_actions = local.alert_arn
  ok_actions    = local.ok_arn

  datapoints_to_alarm = "1"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "slo_coverage_bulk_latency_99p_15m_warning" {
  for_each = local.bulk_partners

  alarm_name          = "${local.app}-${var.env}-slo-coverage-bulk-latency-99p-15m-warning-${each.key}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  period              = "900"
  extended_statistic  = local.ext_stat_99p
  threshold           = "1440"
  alarm_description   = join("", [
    "/v*/fhir/Coverage response 99% 15 minute BULK latency exceeded WARNING SLO threshold of 1440 ",
    "ms for partner ${each.key} for ${local.app}"
  ])

  metric_name = "${local.metrics.coverage_latency}/${each.key}"
  namespace   = local.namespace

  alarm_actions = local.warning_arn
  ok_actions    = local.ok_arn

  datapoints_to_alarm = "1"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "slo_coverage_nonbulk_latency_99p_15m_alert" {
  for_each = local.non_bulk_partners

  alarm_name          = "${local.app}-${var.env}-slo-coverage-nonbulk-latency-99p-15m-alert-${each.key}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  period              = "900"
  extended_statistic  = local.ext_stat_99p
  threshold           = "2050"
  alarm_description   = join("", [
    "/v*/fhir/Coverage response 99% 15 minute NON-BULK latency exceeded ALERT SLO threshold of ",
    "2050 ms for partner ${each.key} for ${local.app}"
  ])

  metric_name = "${local.metrics.coverage_latency}/${each.key}"
  namespace   = local.namespace

  alarm_actions = local.alert_arn
  ok_actions    = local.ok_arn

  datapoints_to_alarm = "1"
  treat_missing_data  = "notBreaching"
}

resource "aws_cloudwatch_metric_alarm" "slo_coverage_nonbulk_latency_99p_15m_warning" {
  for_each = local.non_bulk_partners

  alarm_name          = "${local.app}-${var.env}-slo-coverage-nonbulk-latency-99p-15m-warning-${each.key}"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = "1"
  period              = "900"
  extended_statistic  = local.ext_stat_99p
  threshold           = "1440"
  alarm_description   = join("", [
    "/v*/fhir/Coverage response 99% 15 minute NON-BULK latency exceeded WARNING SLO threshold of ",
    "1440 ms for partner ${each.key} for ${local.app}"
  ])

  metric_name = "${local.metrics.coverage_latency}/${each.key}"
  namespace   = local.namespace

  alarm_actions = local.warning_arn
  ok_actions    = local.ok_arn

  datapoints_to_alarm = "1"
  treat_missing_data  = "notBreaching"
}