provider "aws" {
  version = "~> 4" # TODO: remove on upgrades beyond v0.12 tf
  region  = "us-east-1"
  default_tags {
    tags = local.default_tags
  }
}

terraform {
  backend "s3" {
    bucket         = "bfd-tf-state"
    key            = "global/s3/terraform.tfstate"
    region         = "us-east-1"
    dynamodb_table = "bfd-tf-table"
    encrypt        = "1"
    kms_key_id     = "alias/bfd-tf-state"
  }
}
