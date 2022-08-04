# pg-password-gen - A tool to help generate, encrypt, and share database passwords

This python3 script creates a strong randomly generated password, then runs it through the same cryptographic functions postgres would do for passwords generated on the server.

It takes no arguments and outputs two strings:
  1. A randomly generated password you should save to your password manager
  2. A longs specially crafted string you will need to share with a database administrator. 
  
The second string is encrypted in a way that allows sharing your password without revealing it to the db admin in the process. 

Note, that while the encrypted string is considered secure, it should not be shared with the public, so share it directly with the database administrator only via TLS encrypted comms.

## Quickstart

1. Clone the BFD repo

```bash
git clone https://github.com/CMSgov/beneficiary-fhir-data.git
cd beneficiary-fhir-data/ops/ccs-ops-misc/pg-password-generator
```

2. Run the script

```bash
python3 pgpass-gen.py
```

3. Copy and **save** the generated password in your favorite password manager.
4. Copy and **send** the encrypted password (not the actual password!) to a database administrator so they can update your account.

## Database admins


```sql
ALTER ROLE joe_user WITH LOGIN PASSWORD 'SCRAM-SHA-256$4096:QvFQ9c8S...==$VonRpO5K9...nENlN0=:mdAL8...ArF/Ufy4n...bQyc=';
```

But don't forget to set/extend their password expiration! You can use this snippet to set the password and extend expiration:

```sql
DO $$
DECLARE
  user_name TEXT := 'foobar'; -- CHANGE ME (ie firstname_lastname)
  user_scram TEXT := 'SCRAM-SHA-256$4096:QvFQ9c8S...==$VonRpO5K9...nENlN0=:mdAL8...ArF/Ufy4n...bQyc='; -- AND ME TOO
  valid_until timestamp := current_timestamp + interval '60 days'; -- NOT GREATER THAN 60 DAYS IN PROD!
BEGIN
  EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L VALID UNTIL %L;', user_name, user_scram, valid_until);
END $$ LANGUAGE plpgsql
```

## Salted Challenge Response Authentication Mechanism (SCRAM)

See:
  - [PostgreSQL password docs](https://www.postgresql.org/docs/current/auth-password.html)
  - [General Info](https://www.percona.com/blog/postgresql-14-and-recent-scram-authentication-changes-should-i-migrate-to-scram/)
  - [Where some of the python came from](https://stackoverflow.com/questions/68400120/how-to-generate-scram-sha-256-to-create-postgres-13-user)

