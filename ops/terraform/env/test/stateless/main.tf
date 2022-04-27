terraform {
  required_version = "0.13.7"
}

provider "aws" {
  version = "~> 3.44.0"
  region  = "us-east-1"
}

module "stateless" {
  source = "../../../modules/stateless"

  env_config = {
    env  = "test"
    tags = { application = "bfd", business = "oeda", stack = "test", Environment = "test" }
  }

  fhir_ami        = var.fhir_ami
  etl_ami         = var.etl_ami
  ssh_key_name    = var.ssh_key_name
  git_branch_name = var.git_branch_name
  git_commit_id   = var.git_commit_id
}
