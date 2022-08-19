'''Ingest historical log data.'''
import sys

from awsglue.context import GlueContext
from awsglue.dynamicframe import DynamicFrame
from awsglue.job import Job
from awsglue.transforms import Unbox, Map
from awsglue.utils import getResolvedOptions
from pyspark.context import SparkContext
from pyspark.sql import functions as SqlFuncs


def format_field_names(record):
    ''' Format field names within a record to remove "." characters and lowercase. '''

    # Make a new record, because we can't iterate over the old one AND change the keys
    record_copy = {}

    for field_id in record.keys():
        record_copy[field_id.lower().replace('.', '_')] = record[field_id]

    return record_copy


def add_time_columns(record):
    """ Modify a record to add columns for year, month, day. """

    if 'timestamp' not in record:
        return record

    record['year'] = record['timestamp'][0:4]
    record['month'] = record['timestamp'][5:7]
    record['day'] = record['timestamp'][8:10]

    return record


def transform_record(record):
    """ Modify a record to fit BFD Insights' requirements. """

    try:
        return add_time_columns(format_field_names(record))

    except Exception as e:
        # AWS Glue jobs are not good at providing error output from these
        # Mapping functions, which get outsourced to separate threads
        print('BFD_ERROR: {0}'.format(e))
        raise e

# Main

args = getResolvedOptions(sys.argv, ["JOB_NAME"])
sc = SparkContext()
glueContext = GlueContext(sc)
spark = glueContext.spark_session
job = Job(glueContext)
job.init(args["JOB_NAME"], args)

args = getResolvedOptions(sys.argv,
                          ['JOB_NAME',
                           'tempLocation',
                           'sourceDatabase',
                           'sourceTable',
                           'targetDatabase',
                           # INVESTIGATION: Remove executionSize argument
                           # 'targetTable',
                           # 'executionSize'])
                           'targetTable'])
                           # INVESTIGATION: End modification

print("sourceDatabase is set to: ", args['sourceDatabase'])
print("   sourceTable is set to: ", args['sourceTable'])
print("targetDatabase is set to: ", args['targetDatabase'])
print("   targetTable is set to: ", args['targetTable'])
# INVESTIGATION: Removed print statement
# print(" executionSize is set to: ", args['executionSize'])
# INVESTIGATION: End modification

SourceDf = glueContext.create_dynamic_frame.from_catalog(
    database=args['sourceDatabase'],
    table_name=args['sourceTable'],
    transformation_ctx="SourceDf"
    # INVESTIGATION: Removed execution size
    # additional_options={
    #     # Restrict the execution to a certain amount of data to prevent memory overflows.
    #     "boundedSize" : args['executionSize'], # Unit is bytes. Must be a string.
    # }
    # INVESTIGATION: End modification
)

record_count = SourceDf.count()

print("Starting run of {count} records.".format(count=record_count))

# With bookmarks enabled, we have to make sure that there is data to be processed
if record_count > 0:
    print("Here is the schema from the source")
    SourceDf.printSchema()

    NextNode = DynamicFrame.fromDF(
        Unbox.apply(frame = SourceDf, path = "message", format="json").toDF()
        .select(SqlFuncs.col("message.*")), glueContext, "nextNode")

    RelationalizeBeneNode = NextNode.relationalize(
        'root',
        args['tempLocation']
        ).select('root')

    OutputDy = Map.apply(frame = RelationalizeBeneNode,
            f = transform_record, transformation_ctx = 'Reformat_Field_Names')

    print("Here is the output schema:")
    OutputDy.printSchema()

    # Script generated for node Data Catalog table
    glueContext.write_dynamic_frame.from_catalog(
        frame=OutputDy,
        database=args['targetDatabase'],
        table_name=args['targetTable'],
        format="glueparquet",
        # INVESTIGATION: The version of this script copied from source control did not have this
        # bit, but the version actually run did (and it was only added to version control later).
        additional_options={
            "enableUpdateCatalog": True,
            "updateBehavior": "UPDATE_IN_DATABASE",
            "partitionKeys": ["year", "month", "day"],
        },
        # INVESTIGATION: End modification
        transformation_ctx="DataCatalogtable_node3",
    )

job.commit()

print("Job complete. %d records processed." % record_count)
