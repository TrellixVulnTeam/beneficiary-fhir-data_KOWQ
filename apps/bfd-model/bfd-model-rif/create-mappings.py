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

    return mapping


def add_primary_key(summary, primary_key_columns, column_name):
    field_name = find_field_name(summary, column_name)
    primary_key_columns.append(field_name)


def create_line_mapping(summary):
    if not summary["hasLines"]:
        return None

    mapping = dict()
    parent_name = summary["headerEntity"]
    mapping["id"] = f'{parent_name}Line'
    mapping["entityClassName"] = f'{summary["packageName"]}.{mapping["id"]}{classNameSuffix}'

    primary_key_columns = []
    table = {"name": mapping["id"], "primaryKeyColumns": primary_key_columns}
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
        primary_key_columns.append(summary["headerEntityGeneratedIdField"])
    if summary["headerEntityIdField"] is not None:
        primary_key_columns.append(summary["headerEntityIdField"])
    add_primary_key(summary, primary_key_columns, line_number_column_name)

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
        if scale == 0 or scale is None:
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


# def to_camel_case(name):
#     first, *others = name.split('_')
#     result = ''.join([first.lower(), *map(str.title, others)])
#     return result
#
#
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
