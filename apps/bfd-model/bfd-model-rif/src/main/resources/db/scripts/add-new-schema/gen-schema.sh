#!/bin/bash
PROGNAME=${0##*/}
default_migration_dir=../../migration

SCHEMA_NAME="${SCHEMA_NAME:-}"
MIGRATION_VER="${MIGRATION_VER:-}"
MIGRATION_NAME="${MIGRATION_NAME:-}"
MIGRATION_DIR="${MIGRATION_DIR:-$default_migration_dir}"
MIGRATION_FILENAME="${MIGRATION_FILENAME:-}"
DATABASE_NAME="${DATABASE_NAME:-fhirdb}"
PSQL_PLACEHOLDER="${PSQL_PLACEHOLDER:-'logic.psql-only'}"
WRITE_DIR="${WRITE_DIR:-$MIGRATION_DIR}" # where to write the migration file (if -w flag is set)
STDOUT_ONLY="${STDOUT_ONLY:-'false'}"

migration_template() {
  cat <<- _EOF_
  /*
    Adds new schema '${SCHEMA_NAME}' and configures Role-Based Access Controls
  */

  -- create the schema
  CREATE SCHEMA IF NOT EXISTS ${SCHEMA_NAME};

  -- add rbac roles and groups
  \${${PSQL_PLACEHOLDER}} DO \$\$
  \${${PSQL_PLACEHOLDER}} BEGIN
  \${${PSQL_PLACEHOLDER}}   -- create ${SCHEMA_NAME} read, write, and migrate roles
  \${${PSQL_PLACEHOLDER}}   PERFORM create_role_if_not_exists('${SCHEMA_NAME}_reader_role');
  \${${PSQL_PLACEHOLDER}}   PERFORM create_role_if_not_exists('${SCHEMA_NAME}_writer_role');
  \${${PSQL_PLACEHOLDER}}   PERFORM create_role_if_not_exists('${SCHEMA_NAME}_migrator_role');
  \${${PSQL_PLACEHOLDER}}
  \${${PSQL_PLACEHOLDER}}   -- apply the roles to the schema
  \${${PSQL_PLACEHOLDER}}   PERFORM add_reader_role_to_schema('${SCHEMA_NAME}_reader_role', '${SCHEMA_NAME}');
  \${${PSQL_PLACEHOLDER}}   PERFORM add_writer_role_to_schema('${SCHEMA_NAME}_writer_role', '${SCHEMA_NAME}');
  \${${PSQL_PLACEHOLDER}}   PERFORM add_migrator_role_to_schema('${SCHEMA_NAME}_migrator_role', '${SCHEMA_NAME}');
  \${${PSQL_PLACEHOLDER}}
  \${${PSQL_PLACEHOLDER}}   -- add ${SCHEMA_NAME} user groups
  \${${PSQL_PLACEHOLDER}}   PERFORM add_db_group_if_not_exists('${DATABASE_NAME}', '${SCHEMA_NAME}_analyst_group');
  \${${PSQL_PLACEHOLDER}}   PERFORM add_db_group_if_not_exists('${DATABASE_NAME}', '${SCHEMA_NAME}_data_admin_group');
  \${${PSQL_PLACEHOLDER}}   PERFORM add_db_group_if_not_exists('${DATABASE_NAME}', '${SCHEMA_NAME}_schema_admin_group');
  \${${PSQL_PLACEHOLDER}}   GRANT ${SCHEMA_NAME}_reader_role TO ${SCHEMA_NAME}_analyst_group;
  \${${PSQL_PLACEHOLDER}}   GRANT ${SCHEMA_NAME}_writer_role TO ${SCHEMA_NAME}_data_admin_group;
  \${${PSQL_PLACEHOLDER}}   GRANT ${SCHEMA_NAME}_migrator_role TO ${SCHEMA_NAME}_schema_admin_group;
  \${${PSQL_PLACEHOLDER}}
  \${${PSQL_PLACEHOLDER}}   -- ensure our read, write, and migrate services can read, write, and migrate the schema
  \${${PSQL_PLACEHOLDER}}   GRANT ${SCHEMA_NAME}_reader_role TO api_reader_svcs;
  \${${PSQL_PLACEHOLDER}}   GRANT ${SCHEMA_NAME}_writer_role TO api_pipeline_svcs;
  \${${PSQL_PLACEHOLDER}}   GRANT ${SCHEMA_NAME}_migrator_role TO api_migrator_svcs;
  \${${PSQL_PLACEHOLDER}} END
  \${${PSQL_PLACEHOLDER}} \$\$ LANGUAGE plpgsql;

_EOF_
}

usage() {
  echo -e "Usage: $PROGNAME [-h|--help] [-v|--version-num VERSION_NUMBER] [-m|--migrations-dir WRITE_DIR] <SCHEMA_NAME>"
}

help_message() {
  cat <<- _EOF_
  $PROGNAME
  Generates a Flyway Migration file you can use to add a new schema to the database including appropriate roles and permissions.

  $(usage)

  Options:
  -h, --help  Display this help message and exit.
  -v, --version-num VERSION_NUMBER set the desired migration version number (defaults to 00000)
    Where VERSION_NUMBER is the desired number without the leading "V" or trailing "_"
  -m, --migrations-dir MIGRATION_DIR (defaults to $default_migration_dir)
    Where MIGRATION_DIR is the (full or relative) path to the directory containing flyway migrations

  Examples:
    $ ./gen-schema.sh foo

    $ ./gen-schema.sh --ver 999 -m ~/Desktop bar

_EOF_
  return
}

# find the highest flyway version number in a directory
find_last_migration_ver(){
  local last_ver
  (
    if cd "$MIGRATION_DIR"; then
      for v in $(find . -type f -iname "V*.sql" -exec echo {} \; | grep -Eo 'V[0-9]+__' | tr -d 'V_' | sort -n); do
        last_ver="$v"
      done
      if [[ $last_ver =~ ^[0-9]+$ ]]; then
        echo "$last_ver"
      else
        echo "00000"
      fi
    else
      echo "ERROR: Could not access MIGRATION_DIR '$MIGRATION_DIR'"
      exit 1
    fi
  )
}

# parse args
while [[ -n $1 ]]; do
  case $1 in
    -h | -help | --help)                    help_message; exit ;;
    -V | -ver | --ver)                      shift; MIGRATION_VER="$1" ;;
    -m | -migration-dir | --migration-dir)  shift; MIGRATION_DIR="$1" ;;
    -s)                                     STDOUT_ONLY='true' ;;
    -*)                                     usage; echo "ERROR: Unknown option $1"; exit 1;;
    *)                                      SCHEMA_NAME="$1"; break ;;
  esac
  shift
done

# validate SCHEMA_NAME
# starts with a letter, contains 1 or more letters or underscores, and ends with a letter
if [[ -z "$SCHEMA_NAME" || ! "$SCHEMA_NAME" =~ ^[A-Za-z]+[A-Za-z_]+[A-Za-z]$ ]]; then
  printf "ERROR: Invalid SCHEMA_NAME or SCHEMA_NAME not set\n\n"
  usage && exit 1
fi
SCHEMA_NAME=$(echo "${SCHEMA_NAME}" | tr '[:upper:]' '[:lower:]')

# make sure migration dir exists and is readable
if [[ ! -r "$MIGRATION_DIR" ]]; then
  [[ -z "$MIGRATION_DIR" ]] && echo "ERROR: MIGRATION_DIR is unset (did you forget -m?)" && usage
  [[ -n "$MIGRATION_DIR" ]] && echo "ERROR: Cannot access migration directory '$MIGRATION_DIR'"
  exit 1
fi

# grab the next version number if unset
if [[ -z $MIGRATION_VER ]]; then
  MIGRATION_VER=$(( $(find_last_migration_ver) + 1 ))
fi

# validate the number (use 00000 if there is a problem)
if [[ -z $MIGRATION_VER || ! $MIGRATION_VER =~ ^[0-9]+$ ]]; then
  MIGRATION_VER="00000"
fi

MIGRATION_FILENAME="V${MIGRATION_VER}__add_${SCHEMA_NAME}_schema.sql"

# render the migration template to stdout if -s
if [[ "$STDOUT_ONLY" == "true" ]]; then
  migration_template && exit
fi

# don't overwrite
if [[ -f "$MIGRATION_FILENAME" ]]; then
  echo "ERROR: Migration $MIGRATION_FILENAME already exists"
  exit 1
fi

# write the migration to disk
if cd "$MIGRATION_DIR" && migration_template > "$MIGRATION_FILENAME"; then
  echo "Added migration: $MIGRATION_DIR/${MIGRATION_FILENAME}"
else
  echo "ERROR: Failed to write $MIGRATION_FILENAME"
  exit 1
fi
