## 
# Build the stateless resources for an environment (ASG, security groups, etc)

locals {
  azs             = ["us-east-1a", "us-east-1b", "us-east-1c"]
  env_config      = { env = var.env_config.env, tags = var.env_config.tags, vpc_id = data.aws_vpc.main.id, zone_id = data.aws_route53_zone.local_zone.id, azs = local.azs }
  port            = 7443
  cw_period       = 60 # Seconds
  cw_eval_periods = 3

  # add new peerings here
  vpc_peerings_by_env = {
    test = [
      "bfd-test-vpc-to-bluebutton-test"
    ],
    prod = [
      "bfd-prod-vpc-to-dpc-prod-vpc",
      "bfd-prod-vpc-to-bluebutton-prod",
      "bfd-prod-vpc-to-bcda-prod-vpc",
      "bfd-prod-to-ab2d-prod"
    ],
    prod-sbx = [
      "bfd-prod-sbx-to-ab2d-dev", "bfd-prod-sbx-to-ab2d-impl", "bfd-prod-sbx-to-ab2d-sbx",
      "bfd-prod-sbx-to-bcda-dev", "bfd-prod-sbx-to-bcda-test", "bfd-prod-sbx-to-bcda-sbx", "bfd-prod-sbx-to-bcda-opensbx",
      "bfd-prod-sbx-vpc-to-bluebutton-impl", "bfd-prod-sbx-vpc-to-bluebutton-test",
      "bfd-prod-sbx-vpc-to-dpc-prod-sbx-vpc", "bfd-prod-sbx-vpc-to-dpc-test-vpc", "bfd-prod-sbx-vpc-to-dpc-dev-vpc"
    ]
  }
  vpc_peerings = local.vpc_peerings_by_env[var.env_config.env]
}


# account number
data "aws_caller_identity" "current" {}

# vpc
data "aws_vpc" "main" {
  filter {
    name   = "tag:Name"
    values = ["bfd-${var.env_config.env}-vpc"]
  }
}

# mgmt vpc
data "aws_vpc" "mgmt" {
  filter {
    name   = "tag:Name"
    values = ["bfd-mgmt-vpc"]
  }
}

# peerings
data "aws_vpc_peering_connection" "peers" {
  count = length(local.vpc_peerings)
  tags  = { Name = local.vpc_peerings[count.index] }
}

# dns
data "aws_route53_zone" "local_zone" {
  name         = "bfd-${var.env_config.env}.local"
  private_zone = true
}

# s3 buckets
data "aws_s3_bucket" "admin" {
  bucket = "bfd-${var.env_config.env}-admin-${data.aws_caller_identity.current.account_id}"
}

data "aws_s3_bucket" "logs" {
  bucket = "bfd-${var.env_config.env}-logs-${data.aws_caller_identity.current.account_id}"
}

# cloudwatch topics
data "aws_sns_topic" "cloudwatch_alarms" {
  name = "bfd-${var.env_config.env}-cloudwatch-alarms"
}
data "aws_sns_topic" "cloudwatch_ok" {
  name = "bfd-${var.env_config.env}-cloudwatch-ok"
}

# aurora security group
data "aws_security_group" "aurora_cluster" {
  filter {
    name   = "tag:Name"
    values = ["bfd-${var.env_config.env}-aurora-cluster"]
  }
}

# vpn security group
data "aws_security_group" "vpn" {
  filter {
    name   = "tag:Name"
    values = ["bfd-${var.env_config.env}-vpn-private"]
  }
}

# get vpn cidr blocks from shared CMS prefix list
data "aws_ec2_managed_prefix_list" "vpn" {
  filter {
    name   = "prefix-list-name"
    values = ["cmscloud-vpn"]
  }
}

# get cbc jenkins cidr block
data "aws_ec2_managed_prefix_list" "jenkins" {
  filter {
    name   = "prefix-list-name"
    values = ["bfd-cbc-jenkins"]
  }
}

# tools security group
data "aws_security_group" "tools" {
  filter {
    name   = "tag:Name"
    values = ["bfd-${var.env_config.env}-enterprise-tools"]
  }
}

# management security group
data "aws_security_group" "remote" {
  filter {
    name   = "tag:Name"
    values = ["bfd-${var.env_config.env}-remote-management"]
  }
}

# ansible vault pw read only policy
data "aws_iam_policy" "ansible_vault_pw_ro_s3" {
  arn = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/bfd-ansible-vault-pw-ro-s3"
}


## BEGIN
# 


## IAM role for FHIR
#
module "fhir_iam" {
  source = "../resources/iam"

  env_config = local.env_config
  name       = "fhir"
}
resource "aws_iam_role_policy_attachment" "fhir_iam_ansible_vault_pw_ro_s3" {
  role       = module.fhir_iam.role
  policy_arn = data.aws_iam_policy.ansible_vault_pw_ro_s3.arn
}


## NLB for the FHIR server (SSL terminated by the FHIR server)
#
module "fhir_lb" {
  source = "../resources/lb"

  env_config = local.env_config
  role       = "fhir"
  layer      = "dmz"
  log_bucket = data.aws_s3_bucket.logs.id
  is_public  = var.is_public

  ingress = var.is_public ? {
    description     = "Public Internet access"
    port            = 443
    cidr_blocks     = ["0.0.0.0/0"]
    prefix_list_ids = []
    } : {
    description     = "From VPN, VPC peerings, the MGMT VPC, and self"
    port            = 443
    cidr_blocks     = concat(data.aws_vpc_peering_connection.peers[*].peer_cidr_block, [data.aws_vpc.mgmt.cidr_block, data.aws_vpc.main.cidr_block])
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.vpn.id, data.aws_ec2_managed_prefix_list.jenkins.id]
  }

  egress = {
    description = "To VPC instances"
    port        = local.port
    cidr_blocks = [data.aws_vpc.main.cidr_block]
  }
}

module "lb_alarms" {
  source = "../resources/lb_alarms"

  load_balancer_name     = module.fhir_lb.name
  alarm_notification_arn = data.aws_sns_topic.cloudwatch_alarms.arn
  ok_notification_arn    = data.aws_sns_topic.cloudwatch_ok.arn
  env                    = var.env_config.env
  app                    = "bfd"

  # NLBs only have this metric to alarm on
  healthy_hosts = {
    eval_periods = local.cw_eval_periods
    period       = local.cw_period
    threshold    = 1 # Count
  }
}


## Autoscale group for the FHIR server
#
module "fhir_asg" {
  source = "../resources/asg"

  env_config = local.env_config
  role       = "fhir"
  layer      = "app"
  lb_config  = module.fhir_lb.lb_config

  # Initial size is one server per AZ
  asg_config = {
    min             = local.env_config.env == "prod-sbx" ? length(local.azs) : 2 * length(local.azs)
    max             = 8 * length(local.azs)
    max_warm        = 4 * length(local.azs)
    desired         = local.env_config.env == "prod-sbx" ? length(local.azs) : 2 * length(local.azs)
    sns_topic_arn   = ""
    instance_warmup = 430
  }

  launch_config = {
    # instance_type must support NVMe EBS volumes: https://github.com/CMSgov/beneficiary-fhir-data/pull/110
    instance_type = "c5.4xlarge"
    volume_size   = var.env_config.env == "prod" ? 250 : 60 # GB
    ami_id        = var.fhir_ami
    key_name      = var.ssh_key_name

    profile       = module.fhir_iam.profile
    user_data_tpl = "fhir_server.tpl" # See templates directory for choices
    account_id    = data.aws_caller_identity.current.account_id
    git_branch    = var.git_branch_name
    git_commit    = var.git_commit_id
  }

  db_config = {
    db_sg = data.aws_security_group.aurora_cluster.id
    role  = "aurora cluster"
  }

  mgmt_config = {
    vpn_sg    = data.aws_security_group.vpn.id
    tool_sg   = data.aws_security_group.tools.id
    remote_sg = data.aws_security_group.remote.id
    ci_cidrs  = [data.aws_vpc.mgmt.cidr_block]
  }
}


## FHIR server metrics, per partner
#

module "bfd_server_metrics" {
  source = "../resources/bfd_server_metrics"
  env    = var.env_config.env
  asg_id = module.fhir_asg.asg_id
}
# TODO: purge all access.txt 
#all 
module "bfd_server_metrics_all" {
  source = "../resources/bfd_server_metrics_txt"

  env = var.env_config.env

  metric_config = {
    partner_name  = "all"
    partner_regex = "*"
  }
}

# bluebutton
module "bfd_server_metrics_bb" {
  source = "../resources/bfd_server_metrics_txt"

  env = var.env_config.env

  metric_config = {
    partner_name  = "bb"
    partner_regex = "*BlueButton*"
  }
}

# bcda
module "bfd_server_metrics_bcda" {
  source = "../resources/bfd_server_metrics_txt"

  env = var.env_config.env

  metric_config = {
    partner_name  = "bcda"
    partner_regex = "*bcda*"
  }
}

# dpc
module "bfd_server_metrics_dpc" {
  source = "../resources/bfd_server_metrics_txt"

  env = var.env_config.env

  metric_config = {
    partner_name  = "dpc"
    partner_regex = "*dpc*"
  }
}

# ab2d
module "bfd_server_metrics_ab2d" {
  source = "../resources/bfd_server_metrics_txt"

  env = var.env_config.env

  metric_config = {
    partner_name  = "ab2d"
    partner_regex = "*ab2d*"
  }
}


## FHIR server alarms, partner specific
#

# TODO: Deprecate this alarm in favor of metric math expression to more accurately
# represet our error budget
module "bfd_server_alarm_all_500s" {
  source = "../resources/bfd_server_alarm"

  env = var.env_config.env

  alarm_config = {
    alarm_name       = "all-500s"
    partner_name     = "all"
    metric_prefix    = "http-requests/count-500"
    eval_periods     = "15"
    period           = "60"
    datapoints       = "15"
    statistic        = "Sum"
    ext_statistic    = null
    threshold        = "8.0"
    alarm_notify_arn = data.aws_sns_topic.cloudwatch_alarms.arn
    ok_notify_arn    = data.aws_sns_topic.cloudwatch_ok.arn
  }
}

module "bfd_server_alarm_all_eob_6s-p95" {
  source = "../resources/bfd_server_alarm"

  env = var.env_config.env

  alarm_config = {
    alarm_name       = "all-eob-6s-p95"
    partner_name     = "all"
    metric_prefix    = "http-requests/latency/eobAll"
    eval_periods     = "15"
    period           = "60"
    datapoints       = "15"
    statistic        = null
    ext_statistic    = "p95"
    threshold        = "6000.0" # milliseconds
    alarm_notify_arn = data.aws_sns_topic.cloudwatch_alarms.arn
    ok_notify_arn    = data.aws_sns_topic.cloudwatch_ok.arn
  }
}

## This is where cloudwatch dashboards are managed. 
#
module "bfd_dashboards" {
  source              = "../resources/bfd_cw_dashboards"
  dashboard_name      = var.dashboard_name
  dashboard_namespace = var.dashboard_namespace
  asg                 = module.fhir_asg.asg_id
}
