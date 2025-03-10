variable "ami_id" {
  description = "Provided AMI ID for the migrator."
  type        = string
  default     = null
}

variable "create_migrator_instance" {
  default     = false
  description = "When true, create the migrator instance, security group, and RDS security group rules"
  type        = bool
}

variable "migrator_monitor_enabled_override" {
  default     = null
  description = "When true, migrator system emits signals to SQS. Defaults to `true` via locals"
  type        = bool
}

variable "migrator_monitor_heartbeat_interval_seconds_override" {
  default     = null
  description = "Sets interval for migrator monitor heartbeat in seconds. Defaults to `300` via locals"
  type        = number
}
