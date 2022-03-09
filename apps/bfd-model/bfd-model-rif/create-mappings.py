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
        primary_key_columns.append(summary["headerEntityIdField"])

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
        primary_key_columns.append(summary["headerEntityGeneratedIdField"].lower())
    if summary["headerEntityIdField"] is not None:
        primary_key_columns.append(summary["headerEntityIdField"].lower())
    add_primary_key(summary, primary_key_columns, line_number_column_name)

    parent_entity = f'{summary["packageName"]}.{parent_name}{classNameSuffix}'
    join = {}
    if "claim" in parent_name.lower():
        join["fieldName"] = "parentClaim"
    else:
        join["fieldName"] = "parentBeneficiary"

    join["entityClass"] = parent_entity
    join["joinColumnName"] = parent_key
    join["joinType"] = "ManyToOne"
    join["fetchType"] = "EAGER"
    join["foreignKey"] = f'{line_table}_{parent_key}_to_{parent_table}'
    table["joins"] = [join]

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
        if column_name == rif_field["rifColumnName"]:
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


summaryFilePath = Path("target/rif-mapping-summary.yaml")
mappings = yaml.safe_load(summaryFilePath.read_text())

output_mappings = []
for mapping in mappings:
    dsl = create_mapping(mapping)
    output_mappings.append(dsl)
    dsl = create_line_mapping(mapping)
    if dsl is not None:
        output_mappings.append(dsl)

result = {"mappings": output_mappings}
print(yaml.dump(result, default_flow_style=False))
