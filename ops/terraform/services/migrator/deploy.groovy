#!/usr/bin/env groovy

// entrypoint to migrator deployment, requires mapped arguments and an aws authentication closure
// attempts to deploy and monitor and return `true` when the migrator signals a zero exit status
boolean deployMigrator(Map args = [:]) {
    amiId = args.amiId
    bfdEnv = args.bfdEnv
    heartbeatInterval = args.heartbeatInterval ?: 30
    awsRegion = args.awsRegion ?: 'us-east-1'

    // authenticate
    awsAuth.assumeRole()

    // set sqsQueueName
    sqsQueueName = "bfd-${bfdEnv}-migrator"

    // precheck
    if (canMigratorDeploymentProceed(sqsQueueName, awsRegion)) {
        println "Proceeding to Migrator Deployment"
    } else {
        println "Halting Migrator Deployment. Check the SQS Queue ${sqsQueueName}."
        return false
    }

    // plan/apply terraform
    terraform.deployTerraservice(
	    env: bfdEnv,
	    directory: "ops/terraform/services/migrator",
	    tfVars: [
                ami_id: amiId,
                create_migrator_instance: true,
                migrator_monitor_heartbeat_interval_seconds_override: heartbeatInterval
	    ]
    )

    // monitor migrator deployment
    finalMigratorStatus = monitorMigrator(
        sqsQueueName: sqsQueueName,
        awsRegion: awsRegion,
        heartbeatInterval: heartbeatInterval,
        maxMessages: 10
    )

    // re-authenticate
    awsAuth.assumeRole()

    // set return value for final disposition
    if (finalMigratorStatus == '0') {
        migratorDeployedSuccessfully = true
        // Teardown when there is a healthy exit status
        terraform.deployTerraservice(
            env: bfdEnv,
            directory: "ops/terraform/services/migrator",
            tfVars: [
                ami_id: amiId,
                create_migrator_instance: false
            ]
        )
    } else {
        migratorDeployedSuccessfully = false
    }

    awsSqs.purgeQueue(sqsQueueName)

    println "Migrator completed with exit status ${finalMigratorStatus}"
    return migratorDeployedSuccessfully
}


// polls the given AWS SQS Queue `sqsQueueName` for migrator messages for
// 20s at the `heartbeatInterval`
String monitorMigrator(Map args = [:]) {
    sqsQueueName = args.sqsQueueName
    awsRegion = args.awsRegion
    heartbeatInterval = args.heartbeatInterval
    maxMessages = args.maxMessages

    sqsQueueUrl = awsSqs.getQueueUrl(sqsQueueName)
    while (true) {
        awsAuth.assumeRole()
        messages = awsSqs.receiveMessages(
            sqsQueueUrl: sqsQueueUrl,
            awsRegion: awsRegion,
            visibilityTimeoutSeconds: 30,
            waitTimeSeconds: 20,
            maxMessages: maxMessages
        )

        // 1. "handle" (capture status, print, delete) each message
        // 2. if the message body contains a non "0/0" (running) value, return it
        for (msg in messages) {
            migratorStatus = msg.body.status
            printMigratorMessage(msg)
            awsSqs.deleteMessage(msg.receipt, sqsQueueUrl)
            if (migratorStatus != '0/0') {
                return migratorStatus
            }
        }
        sleep(heartbeatInterval)
    }
}

// print formatted migrator messages
void printMigratorMessage(message) {
    body = message.body
    println "Timestamp: ${java.time.LocalDateTime.now().toString()}"

    if (body.stop_time == "n/a") {
        println "Migrator ${body.pid} started at ${body.start_time} is running"
    } else {
        println "Migrator ${body.pid} started at ${body.start_time} is no longer running: '${body.code}' '${body.status}' as of ${body.stop_time}"
    }
}

// checks for indications of a running migrator deployment by looking for unconsumed SQS messages
boolean canMigratorDeploymentProceed(String sqsQueueName, String awsRegion) {
    println "Checking Migrator Queue ${sqsQueueName} State..."

    if (awsSqs.queueExists(sqsQueueName)) {
        sqsQueueUrl = awsSqs.getQueueUrl(sqsQueueName)
        println "Queue ${sqsQueueName} exists. Checking for messages in ${sqsQueueUrl} ..."
        migratorMessages = awsSqs.receiveMessages(
                sqsQueueUrl: sqsQueueUrl,
                awsRegion: awsRegion,
                maxMessages: 10,
                visibilityTimeoutSeconds: 0,
                waitTimeSeconds: 0)
        if (migratorMessages?.isEmpty()) {
            println "Queue ${sqsQueueName} is empty. Migrator deployment can proceed!"
            return true
        } else {
            println "Queue ${sqsQueueName} has messages. Is there an old bfd-db-migrator instance running? Migrator deployment cannot proceed."
            return false
        }
    } else {
        println "Queue ${sqsQueueName} can not be found. Migrator deployment can proceed!"
        return true
    }
}

return this
