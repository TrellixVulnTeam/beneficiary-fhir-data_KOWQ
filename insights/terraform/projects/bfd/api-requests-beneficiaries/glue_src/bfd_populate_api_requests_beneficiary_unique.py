'''Populate the Beneficiary-Unique table from the Beneficiary table.'''

import sys

from awsglue.context import GlueContext
from awsglue.dynamicframe import DynamicFrame
from awsglue.job import Job
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from pyspark.sql import functions as SqlFuncs

args = getResolvedOptions(sys.argv, ["JOB_NAME"])
sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args["JOB_NAME"], args)

args = getResolvedOptions(sys.argv,
                          ['JOB_NAME',
                           'initialize',
                           'sourceDatabase',
                           'sourceTable',
                           'targetDatabase',
                           'targetTable'])

print("initialize is set to: ", args['initialize'])
print("sourceDatabase is set to: ", args['sourceDatabase'])
print("   sourceTable is set to: ", args['sourceTable'])
print("targetDatabase is set to: ", args['targetDatabase'])
print("   targetTable is set to: ", args['targetTable'])

SourceDyf = glueContext.create_dynamic_frame.from_catalog(database=args['sourceDatabase'],
    table_name=args['sourceTable'], transformation_ctx="SourceDyf",)

record_count = SourceDyf.count()

print("Starting run of {count} records.".format(count=record_count))

# With bookmarks enabled, we have to make sure that there is data to be processed
if record_count > 0:
    TransformedDf = SourceDyf.toDF().groupBy("bene_id", "bene_id_hash").agg(SqlFuncs.min("timestamp").alias("first_seen"))

    OutputDyf = DynamicFrame.fromDF(TransformedDf, glueContext, "OutputDyf")

    # Truncate the destination table so that we don't get duplicates
    glueContext.purge_table(
        database=args['targetDatabase'],
        table_name=args['targetTable'],
        options={"retentionPeriod": 0},
        transformation_ctx="PurgeTable"
    )

    # Script generated for node AWS Glue Data Catalog
    WriteBeneUniqueNode = glueContext.write_dynamic_frame.from_catalog(
        frame=OutputDyf,
        database=args['targetDatabase'],
        table_name=args['targetTable'],
        additional_options={
            "updateBehavior": "UPDATE_IN_DATABASE",
            "partitionKeys": ["bene_id_hash"],
            "enableUpdateCatalog": True,
        },
        transformation_ctx="WriteBeneUniqueNode",
    )

job.commit()
