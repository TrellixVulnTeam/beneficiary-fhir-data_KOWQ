import sys
from awsglue.transforms import *
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from awsglue.context import GlueContext
from awsglue.job import Job
from awsglue.dynamicframe import DynamicFrame
from pyspark.sql import functions as SqlFuncs
from pyspark.sql.window import Window

args = getResolvedOptions(sys.argv, ["JOB_NAME"])
sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args["JOB_NAME"], args)

args = getResolvedOptions(sys.argv,
                          ['JOB_NAME',
                           'initialize',
                           'sourceTable',
                           'targetTable'])
                           
print("initialize is set to: ", args['initialize'])
print("sourceTable is set to: ", args['sourceTable'])
print("targetTable is set to: ", args['targetTable'])

SourceDyf = glueContext.create_dynamic_frame.from_catalog(database="bfd", table_name=args['sourceTable'], transformation_ctx="SourceDyf",)

JoinLhsDf = (
    SourceDyf.toDF()
    .withColumn("row",SqlFuncs.row_number().over(
        Window.partitionBy("bene_id").orderBy(SqlFuncs.col("timestamp")))
        )
    .filter(SqlFuncs.col("row") == 1)
    .drop("row")
    )

if args['initialize'] != 'True':
    # Beneficiaries_unique table
    BeneUniqueCatalogDf = glueContext.create_dynamic_frame.from_catalog(
        database="bfd",
        table_name=args['targetTable'],
        transformation_ctx="BeneUniqueCatalogDf",
    )
    # Script generated for node Rename Field
    RenameBeneFieldNode = RenameField.apply(
        frame=BeneUniqueCatalogDf,
        old_name="`bene_id`",
        new_name="`r_bene_id`",
        transformation_ctx="RenameBeneFieldNode",
    )
    # Script generated for node Join
    JoinRhsDf = RenameBeneFieldNode.toDF()
    JoinNode = DynamicFrame.fromDF(
        JoinLhsDf.join(
            JoinRhsDf,
            (
                JoinLhsDf["bene_id"]
                == JoinRhsDf["`r_bene_id`"]
            ),
            "leftanti",
        ),
        glueContext,
        "JoinNode",
    )
else:
    JoinNode = DynamicFrame.fromDF(JoinLhsDf, glueContext, "initializeNode")

# Script generated for node Select Fields
SelectJoinNode = SelectFields.apply(
    frame=JoinNode,
    paths=["bene_id", "timestamp", "year", "month"],
    transformation_ctx="SelectJoinNode",
)

# Rename to first_seen
RenameTimestampFieldNode = RenameField.apply(
    frame=SelectJoinNode,
    old_name="timestamp",
    new_name="first_seen",
    transformation_ctx="RenameTimestampFieldNode",
)

# Script generated for node AWS Glue Data Catalog
WriteBeneUniqueNode = glueContext.write_dynamic_frame.from_catalog(
    frame=RenameTimestampFieldNode,
    database="bfd",
    table_name=args['targetTable'],
    additional_options={
        "updateBehavior": "UPDATE_IN_DATABASE",
        "partitionKeys": ["year", "month"],
    },
    transformation_ctx="WriteBeneUniqueNode",
)

job.commit()
