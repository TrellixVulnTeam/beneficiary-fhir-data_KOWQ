# /usr/bin/env python3

import sys
from pathlib import Path

import yaml

classNameSuffix = "Z"


def create_mapping(summary):
    mapping = dict()
    mapping["id"] = summary["headerEntity"]
    mapping["entityClassName"] = f'{summary["packageName"]}.{summary["headerEntity"]}{classNameSuffix}'

    primaryKeyColumns = []
    table = {"name": summary["headerTable"], "primaryKeyColumns": primaryKeyColumns}
    mapping["table"] = table

    fields = set()
    columns = list()
    for rif_field in summary["rifLayout"]["fields"]:
        field_name = rif_field["javaFieldName"]
        if field_name not in fields:
            columns.append(create_column(rif_field))
            fields.add(field_name)
        else:
            sys.stderr.write(f'warning: {mapping["id"]} column {rif_field["rifColumnName"]} has a duplicate javaFieldName {field_name}\n')

    table["columns"] = columns

    if summary["headerEntityGeneratedIdField"] is not None:
        primaryKeyColumns.append(summary["headerEntityGeneratedIdField"])
        columns.append(create_generated_id_column(summary["headerEntityGeneratedIdField"]))
    if summary["headerEntityIdField"] is not None:
        primaryKeyColumns.append(summary["headerEntityIdField"])

    return mapping


def create_generated_id_column(column_name):
    column = {"name": column_name, "sqlType": "bigint", "javaType": "long", "nullable": False, "identity": True}
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


summaryFilePath = Path("target/rif-mapping-summary.yaml")
mappings = yaml.safe_load(summaryFilePath.read_text())

output_mappings = []
for mapping in mappings:
    # print(f'{mapping["packageName"]}.{mapping["headerEntity"]}')
    dsl = create_mapping(mapping)
    output_mappings.append(dsl)
    # print(yaml.dump(dsl))

result = {"mappings": output_mappings}
print(yaml.dump(result, default_flow_style=False))
