# /usr/bin/env python3

import sys
from pathlib import Path

import yaml

classNameSuffix = "Z"


def create_mapping(summary):
    mapping = dict()
    mapping["id"] = summary["headerEntity"]
    mapping["entityClassName"] = f'{summary["packageName"]}.{mapping["id"]}{classNameSuffix}'

    primary_key_columns = []
    table = {"name": summary["headerTable"], "primaryKeyColumns": primary_key_columns}
    mapping["table"] = table

    columns = list()
    if summary["headerEntityGeneratedIdField"] is not None:
        field_name = summary["headerEntityGeneratedIdField"]
        primary_key_columns.append(field_name)
        columns.append(create_generated_id_column(summary, field_name))

    line_number_column_name = summary["lineEntityLineNumberField"]
    for rif_field in summary["rifLayout"]["fields"]:
        column_name = rif_field["rifColumnName"]
        if line_number_column_name == column_name:
            break

        column = create_column(rif_field)
        add_transient_to_column(summary, column)
        columns.append(column)

    for rif_field in summary["headerEntityAdditionalDatabaseFields"]:
        column = create_column(rif_field)
        add_transient_to_column(summary, column)
        columns.append(column)

    table["columns"] = columns

    if summary["headerEntityIdField"] is not None:
        add_primary_key(summary, primary_key_columns, summary["headerEntityIdField"])

    joins = []
    for join_relationship in summary["innerJoinRelationship"]:
        joins.append(create_join(summary, join_relationship))
    if len(joins) > 0:
        table["joins"] = joins

    return mapping


def add_primary_key(summary, primary_key_columns, column_name):
    field_name = find_field_name(summary, column_name)
    primary_key_columns.append(field_name)


def create_line_mapping(summary):
    if not summary["hasLines"]:
        return None

    mapping = dict()
    parent_name = summary["headerEntity"]
    parent_table = summary["headerTable"]
    parent_key = summary["headerEntityIdField"].lower()
    line_name = f'{parent_name}Line'
    line_table = f'{parent_table}_lines'
    mapping["id"] = line_name
    mapping["entityClassName"] = f'{summary["packageName"]}.{line_name}{classNameSuffix}'

    primary_key_columns = []
    table = {"name": line_table, "primaryKeyColumns": primary_key_columns}
    mapping["table"] = table

    columns = list()
    line_number_column_name = summary["lineEntityLineNumberField"]
    line_fields_started = False
    for rif_field in summary["rifLayout"]["fields"]:
        if line_number_column_name == rif_field["rifColumnName"]:
            line_fields_started = True

        if not line_fields_started:
            continue

        columns.append(create_column(rif_field))

    table["columns"] = columns

    if summary["headerEntityGeneratedIdField"] is not None:
        add_primary_key(summary, primary_key_columns, summary["headerEntityGeneratedIdField"])
    if summary["headerEntityIdField"] is not None:
        add_primary_key(summary, primary_key_columns, summary["headerEntityIdField"])
    add_primary_key(summary, primary_key_columns, line_number_column_name)

    parent_entity = f'{summary["packageName"]}.{parent_name}{classNameSuffix}'
    join = dict()
    join["fieldName"] = "parentClaim"
    join["entityClass"] = parent_entity
    join["joinColumnName"] = parent_key
    join["joinType"] = "ManyToOne"
    join["fetchType"] = "EAGER"
    join["foreignKey"] = f'{line_table}_{parent_key}_to_{parent_table}'
    table["joins"] = [join]
    primary_key_columns.insert(0, "parentClaim")

    return mapping


def create_generated_id_column(summary, column_name):
    sequence = {"name": summary["sequenceNumberGeneratorName"], "allocationSize": 50}
    column = dict()
    column["name"] = column_name.lower()
    column["sqlType"] = "bigint"
    column["javaType"] = "long"
    column["nullable"] = False
    column["updatable"] = False
    column["sequence"] = sequence
    return column


def create_column(rif_field):
    column = {"name": rif_field["javaFieldName"], "dbName": rif_field["rifColumnName"].lower()}
    required = not rif_field["rifColumnOptional"]
    if required:
        column["nullable"] = False
    comment = ""
    if rif_field["rifColumnLabel"] != "":
        comment = rif_field["rifColumnLabel"]
    if rif_field["dataDictionaryEntry"] != "":
        comment = f'{comment} ({rif_field["dataDictionaryEntry"]})'
    column["comment"] = comment
    column_type = rif_field["rifColumnType"]
    if column_type == "CHAR":
        column["sqlType"] = f'varchar({rif_field["rifColumnLength"]})'
        length = rif_field["rifColumnLength"]
        if length == 1:
            if required:
                column["javaType"] = "char"
            else:
                column["javaType"] = "Character"
    elif column_type == "DATE":
        column["sqlType"] = "date"
    elif column_type == "TIMESTAMP":
        column["sqlType"] = "timestamp with time zone"
    elif column_type == "NUM":
        length = rif_field["rifColumnLength"]
        scale = rif_field["rifColumnScale"]
        if length == 0 or length is None:
            column["sqlType"] = f'numeric'
        elif scale == 0 or scale is None:
            column["sqlType"] = f'numeric({length})'
        else:
            column["sqlType"] = f'decimal({length},{scale})'
    return column


def add_transient_to_column(summary, column):
    if column["dbName"].upper() in summary["headerEntityTransientFields"]:
        column["fieldType"] = "Transient"


def find_field_name(summary, column_name):
    for rif_field in summary["rifLayout"]["fields"]:
        if column_name.upper() == rif_field["rifColumnName"]:
            return rif_field["javaFieldName"]
    sys.stderr.write(f'error: {summary["headerEntity"]} has no {column_name} column\n')
    raise


def create_join(summary, join_relationship):
    join = dict()
    join["fieldName"] = join_relationship["childField"]
    join["entityClass"] = f'{summary["packageName"]}.{join_relationship["childEntity"]}{classNameSuffix}'
    join["mappedBy"] = join_relationship["mappedBy"]
    join["joinType"] = "OneToMany"
    join["collectionType"] = "Set"
    join["cascadeTypes"] = ["ALL"]
    join["fetchType"] = "LAZY"
    join["orphanRemoval"] = False
    if join_relationship["orderBy"] is not None:
        join["orderBy"] = join_relationship["orderBy"]
    return join


def summary_with_name(all_summaries, name):
    for summary in all_summaries:
        if summary["rifLayout"]["name"] == name:
            return summary
    return None


def mapping_with_id(all_mappings, id):
    for mapping in all_mappings:
        if mapping["id"] == id:
            return mapping
    return None


# Unfortunately the BeneficiaryMonthly is a special case in the RifLayoutsProcessor and uses
# hard coded values so we have to do the same here.  In particular the foreignKey does not
# follow the normal pattern so there is nothing in the summary to handle it.
def add_join_to_monthlies(all_mappings):
    parent = mapping_with_id(all_mappings, "Beneficiary")
    monthly = mapping_with_id(all_mappings, "BeneficiaryMonthly")
    if (parent is not None) and (monthly is not None):
        if "joins" in monthly["table"].keys():
            joins = monthly["table"]["joins"]
        else:
            joins = []
            monthly["table"]["joins"] = joins
        join = dict()
        join["fieldName"] = "parentBeneficiary"
        join["entityClass"] = parent["entityClassName"]
        join["joinColumnName"] = "bene_id"
        join["joinType"] = "ManyToOne"
        join["fetchType"] = "EAGER"
        join["foreignKey"] = "beneficiary_monthly_bene_id_to_beneficiary"
        joins.append(join)
        monthly["table"]["primaryKeyColumns"].insert(0, "parentBeneficiary")

summaryFilePath = Path("target/rif-mapping-summary.yaml")
summaries = yaml.safe_load(summaryFilePath.read_text())

output_mappings = []
for summary in summaries:
    dsl = create_mapping(summary)
    output_mappings.append(dsl)
    dsl = create_line_mapping(summary)
    if dsl is not None:
        output_mappings.append(dsl)
add_join_to_monthlies(output_mappings)

result = {"mappings": output_mappings}
print(yaml.dump(result, default_flow_style=False))
