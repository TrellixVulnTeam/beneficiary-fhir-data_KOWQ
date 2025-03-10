variable "fhir_ami" {
  description = "FHIR server AMI"
  type        = string
}

variable "ssh_key_name" {
  description = "SSH Key"
  type        = string
}

variable "git_branch_name" {
  description = "git branch of beneficiary-fhir-data"
  type        = string
}

variable "git_commit_id" {
  description = "git commit of beneficiary-fhir-data"
  type        = string
}
