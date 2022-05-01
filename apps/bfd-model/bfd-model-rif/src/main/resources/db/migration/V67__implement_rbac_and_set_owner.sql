${logic.psql-only} $$ LANGUAGE plpgsql;
${logic.psql-only} DO $$
${logic.psql-only} DECLARE
${logic.psql-only} 	t record;
${logic.psql-only} BEGIN
${logic.psql-only}   -- add bfd read, write, and migrate roles
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'bfd_reader_role');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'bfd_writer_role');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'bfd_migrator_role');
${logic.psql-only}   DROP ROLE IF EXISTS bfd_reader_role;
${logic.psql-only}   DROP ROLE IF EXISTS bfd_writer_role;
${logic.psql-only}   DROP ROLE IF EXISTS bfd_migrator_role;
${logic.psql-only}   PERFORM create_role_if_not_exists('bfd_reader_role');
${logic.psql-only}   PERFORM create_role_if_not_exists('bfd_writer_role');
${logic.psql-only}   PERFORM create_role_if_not_exists('bfd_migrator_role');
${logic.psql-only}   -- add the roles to BFD's (public) schema
${logic.psql-only}   PERFORM add_reader_role_to_schema('bfd_reader_role', 'public');
${logic.psql-only}   PERFORM add_writer_role_to_schema('bfd_writer_role', 'public');
${logic.psql-only}   PERFORM add_migrator_role_to_schema('bfd_migrator_role', 'public');
${logic.psql-only} 
${logic.psql-only}   -- add bfd user groups
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'bfd_analyst_group');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'bfd_data_admin_group');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'bfd_schema_admin_group');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'bfd_analyst_group');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'bfd_data_admin_group');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'bfd_schema_admin_group');
${logic.psql-only}   GRANT bfd_reader_role TO bfd_analyst_group;
${logic.psql-only}   GRANT bfd_writer_role TO bfd_data_admin_group;
${logic.psql-only}   GRANT bfd_migrator_role TO bfd_schema_admin_group;
${logic.psql-only} 
${logic.psql-only}   -- create paca read, write, and migrate roles
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'paca_reader_role');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'paca_writer_role');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'paca_migrator_role');
${logic.psql-only}   DROP ROLE IF EXISTS paca_reader_role;
${logic.psql-only}   DROP ROLE IF EXISTS paca_writer_role;
${logic.psql-only}   DROP ROLE IF EXISTS paca_migrator_role;
${logic.psql-only}   PERFORM create_role_if_not_exists('paca_reader_role');
${logic.psql-only}   PERFORM create_role_if_not_exists('paca_writer_role');
${logic.psql-only}   PERFORM create_role_if_not_exists('paca_migrator_role');
${logic.psql-only}   -- add the roles to paca's rda schema
${logic.psql-only}   PERFORM add_reader_role_to_schema('paca_reader_role', 'rda');
${logic.psql-only}   PERFORM add_writer_role_to_schema('paca_writer_role', 'rda');
${logic.psql-only}   PERFORM add_migrator_role_to_schema('paca_migrator_role', 'rda');
${logic.psql-only} 
${logic.psql-only}   -- paca user groups
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'paca_analyst_group');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'paca_data_admin_group');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'paca_schema_admin_group');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'paca_analyst_group');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'paca_data_admin_group');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'paca_schema_admin_group');
${logic.psql-only}   GRANT paca_reader_role TO paca_analyst_group;
${logic.psql-only}   GRANT paca_writer_role TO paca_data_admin_group;
${logic.psql-only}   GRANT paca_migrator_role TO paca_schema_admin_group;
${logic.psql-only}
${logic.psql-only}   -- add a fhirdb migrator role that can run both paca and bfd migrations
${logic.psql-only}   PERFORM create_role_if_not_exists('fhirdb_migrator_role');
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'fhirdb_migrator_role');
${logic.psql-only}   GRANT bfd_migrator_role TO fhirdb_migrator_role;
${logic.psql-only}   GRANT paca_migrator_role TO fhirdb_migrator_role;
${logic.psql-only} 
${logic.psql-only}   -- add an rds_superuser role to emulate AWS's "superuser" role for local postgres installs
${logic.psql-only}   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'rds_superuser') THEN
${logic.psql-only}     CREATE ROLE rds_superuser WITH CREATEDB CREATEROLE INHERIT NOLOGIN;
${logic.psql-only}     GRANT ALL ON DATABASE fhirdb TO rds_superuser;
${logic.psql-only}     GRANT fhirdb_migrator_role TO rds_superuser;
${logic.psql-only}   END IF;
${logic.psql-only} 
${logic.psql-only}   -- add a db admin superuser group
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'fhirdb_admin_group');
${logic.psql-only}   ALTER ROLE fhirdb_admin_group WITH CREATEDB CREATEROLE;
${logic.psql-only}   GRANT rds_superuser TO fhirdb_admin_group WITH ADMIN OPTION;
${logic.psql-only}   GRANT fhirdb_migrator_role TO fhirdb_admin_group WITH ADMIN OPTION;
${logic.psql-only} 
${logic.psql-only}   -- add a group for managing fhir api service accounts (read only)
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'api_reader_svcs');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'api_reader_svcs');
${logic.psql-only}   GRANT bfd_reader_role TO api_reader_svcs;
${logic.psql-only}   GRANT paca_reader_role TO api_reader_svcs;
${logic.psql-only}   
${logic.psql-only}   -- add a group for managing pipeline service accounts (read+write)
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'api_pipeline_svcs');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'api_pipeline_svcs');
${logic.psql-only}   GRANT bfd_writer_role TO api_pipeline_svcs;
${logic.psql-only}   GRANT paca_writer_role TO api_pipeline_svcs;
${logic.psql-only}   
${logic.psql-only}   -- add a group for managing db migrator service accounts (read+write+ddl)
${logic.psql-only}   PERFORM revoke_db_privs('fhirdb', 'api_migrator_svcs');
${logic.psql-only}   PERFORM add_db_group_if_not_exists('fhirdb', 'api_migrator_svcs');
${logic.psql-only}   ALTER ROLE api_migrator_svcs WITH CREATEDB CREATEROLE;
${logic.psql-only}   GRANT fhirdb_migrator_role TO api_migrator_svcs;
${logic.psql-only}   GRANT fhirdb_migrator_role TO api_pipeline_svcs; -- will revoke when migrator and pipeline are split
${logic.psql-only}
${logic.psql-only}   -- designate a fhirdb owner that will own all bfd/paca tables, views, sequences, and procedures
${logic.psql-only}   PERFORM create_role_if_not_exists('fhir');
${logic.psql-only}   PERFORM set_fhirdb_owner('fhir');
${logic.psql-only}
${logic.psql-only}   -- ensure our migrators can alter the things that fhir owns
${logic.psql-only}   GRANT fhir TO api_migrator_svcs;
${logic.psql-only}
${logic.psql-only}   -- ensure the current user is an api migrator
${logic.psql-only}   GRANT api_migrator_svcs TO CURRENT_USER;
${logic.psql-only}
${logic.psql-only} END 
${logic.psql-only} $$ LANGUAGE plpgsql;
