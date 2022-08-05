import sys
import psycopg2
import re
from pathlib import Path

def validate_and_update(args):
    """
    Validates (unless specified to skip) and then updates the
    synthea.properties file with the specified end state data.
    If validation is not skipped, and fails, the properties file
    will not be updated.
    """
    
    ##TODO: Save some time and validate the end state properties file path and final write path exist before we do anything

    skip_validation = True if len(args) > 4 and args[4] == "True" else False
    end_state_properties_file = Path(args[0]).read_text()
    db_string = args[3]
    generated_benes = args[2]
    synthea_prop_filepath = args[1]
        
    #Validate the ranges - number to be generated
    overall_validation_result = True if skip_validation else check_ranges(end_state_properties_file, generated_benes, db_string)
    if overall_validation_result == True:
        update_property_file(end_state_properties_file, synthea_prop_filepath)
        print("Updated synthea properties")
        return 0
    else:
        print("Failed validation, not updating synthea properties")
        return 1

def check_ranges(properties_file, number_of_benes_to_generate, db_string):
    """
    Checks that the synthea properties values to update to have no
    conflicts with the target database by checking there are no conflicting 
    existing items beyond the starting value for each field.
    """
    
    ## This will be updated with each validation; if False it will stay false
    overall_validation_result = True
    
    clm_id_start = re.findall("exporter.bfd.clm_id_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from carrier_claims where clm_id <= {clm_id_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_id_start, "carrier_claims", "clm_id", result, overall_validation_result)
        
    bene_id_start = re.findall("exporter.bfd.bene_id_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    bene_id_end = int(bene_id_start) - int(number_of_benes_to_generate)
    query = f"select count(*) from beneficiaries where bene_id <= {bene_id_start} and bene_id > {bene_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(bene_id_start, "beneficiaries", "bene_id", result, overall_validation_result)
        
    clm_grp_id_start = re.findall("exporter.bfd.clm_grp_id_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    ## this is the end of the batch 1 relocated ids; should be nothing between the generated start and this
    clm_grp_id_end = '-99999831003'
    query = f"select count(*) from carrier_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "carrier_claims", "clm_grp_id", result, overall_validation_result)
    
    query = f"select count(*) from inpatient_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "inpatient_claims", "clm_grp_id", result, overall_validation_result)

    query = f"select count(*) from outpatient_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "outpatient_claims", "clm_grp_id", result, overall_validation_result)
    
    query = f"select count(*) from snf_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "snf_claims", "clm_grp_id", result, overall_validation_result)
    
    query = f"select count(*) from dme_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "dme_claims", "clm_grp_id", result, overall_validation_result)
    
    query = f"select count(*) from hha_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "hha_claims", "clm_grp_id", result, overall_validation_result)
    
    query = f"select count(*) from hospice_claims where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "hospice_claims", "clm_grp_id", result, overall_validation_result)
    
    query = f"select count(*) from partd_events where clm_grp_id <= {clm_grp_id_start} and clm_grp_id > {clm_grp_id_end}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(clm_grp_id_start, "partd_events", "clm_grp_id", result, overall_validation_result)
    
    
    pde_id_start = re.findall("exporter.bfd.pde_id_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from partd_events where pde_id::bigint <= {pde_id_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(pde_id_start, "partd_events", "pde_id", result, overall_validation_result)
    
    carr_clm_ctrl_num_start = re.findall("exporter.bfd.carr_clm_cntl_num_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from carrier_claims where carr_clm_cntl_num::bigint <= {carr_clm_ctrl_num_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(carr_clm_ctrl_num_start, "carrier_claims", "carr_clm_cntl_num", result, overall_validation_result)

    ## Check fi_num_start in all the tables it exists in
    fi_num_start = re.findall("exporter.bfd.fi_doc_cntl_num_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from outpatient_claims where fi_doc_clm_cntl_num::bigint <= {fi_num_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(fi_num_start, "outpatient_claims", "fi_doc_clm_cntl_num", result, overall_validation_result)
    
    fi_num_start = re.findall("exporter.bfd.fi_doc_cntl_num_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from inpatient_claims where fi_doc_clm_cntl_num::bigint <= {fi_num_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(fi_num_start, "inpatient_claims", "fi_doc_clm_cntl_num", result, overall_validation_result)
    
    fi_num_start = re.findall("exporter.bfd.fi_doc_cntl_num_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from hha_claims where fi_doc_clm_cntl_num::bigint <= {fi_num_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(fi_num_start, "hha_claims", "fi_doc_clm_cntl_num", result, overall_validation_result)
    
    fi_num_start = re.findall("exporter.bfd.fi_doc_cntl_num_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from snf_claims where fi_doc_clm_cntl_num::bigint <= {fi_num_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(fi_num_start, "snf_claims", "fi_doc_clm_cntl_num", result, overall_validation_result)
    
    fi_num_start = re.findall("exporter.bfd.fi_doc_cntl_num_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from hospice_claims where fi_doc_clm_cntl_num::bigint <= {fi_num_start}"
    result = _execute_single_count_query(db_string, query)
    overall_validation_result = field_has_room_in_table(fi_num_start, "hospice_claims", "fi_doc_clm_cntl_num", result, overall_validation_result)
    
    ## Since MBI and HICN are incremented in difficult-to-query ways, just check if it exists; if it doesnt exist the range should be fine
    hicn_start = re.findall("exporter.bfd.hicn_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from beneficiaries where hicn_unhashed = \'{hicn_start}\'"
    result = _execute_single_count_query(db_string, query)
    if result > 0:
        print(f"(Validation Failure) Start HICN {hicn_start} exists in DB")
        overall_validation_result = False
    else:
        print("(Validation Success) Start HICN does not exist in DB")
        
    mbi_start = re.findall("exporter.bfd.mbi_start.*$",properties_file,re.MULTILINE)[0].split("=")[1]
    query = f"select count(*) from beneficiaries where mbi_num = \'{mbi_start}\'"
    result = _execute_single_count_query(db_string, query)
    if result > 0:
        print(f"(Validation Failure) Start MBI {mbi_start} exists in DB")
        overall_validation_result = False
    else:
        print("(Validation Success) Start MBI does not exist in DB")
        
    ## Ensure no mbi_hash has more than one bene_id associated with it from any previous loads
    query = "select count(*) from (select mbi_hash, bene_id, count(bene_id) from beneficiaries_history where mbi_hash IS NOT NULL group by mbi_hash, bene_id having count(bene_id) > 1) as s"
    result = _execute_single_count_query(db_string, query)
    if result > 0:
        print(f"(Validation Warning) {result} MBI(s) resolve to multiple bene_ids; a given mbi should only resolve to one bene_id (this needs cleanup to avoid errors using that data)")
    else:
        print("(Validation Success) No MBIs resolve to multiple bene_ids")
        
    return overall_validation_result


def field_has_room_in_table(starting_value, table, field, query_result, overall_validation_result):
    """
    Checks if for a given starting value, for a specific table and field,
    the query that checked the range was 0 (meaning there were no rows with 
    values beyond the starting value). If the query was successful, return
    the overall success value for the entire validation across tables.
    """
    if query_result > 0:
        print(f"(Validation Failure) {table} : {field} has conflict with data to load, {query_result} rows beyond starting value {starting_value}")
        return False
    else:
        print(f"(Validation Success) {table} : {field} has clearance from starting value {starting_value}")
        return overall_validation_result
    

def update_property_file(end_state_file_data, synthea_props_file_location):
    """
    Updates the synthea properties file to prepare
    for the next batch creation.
    """
    
    ## TODO: Update props file with the end state data using find/replace in specified file
    
    return False
    

    
def _execute_single_count_query(uri: str, query: str):
    """
    Execute a PSQL select statement and return its results
    """

    conn = None
    try:
        with psycopg2.connect(uri) as conn:
            with conn.cursor() as cursor:
                cursor.execute(query)
                results = cursor.fetchall()
    finally:
        conn.close()

    if len(results) <= 0 and len(results[0]) <= 0:
        return 0

    #we want just a single number; the count, so extract this from the results set
    return results[0][0]
    

## Runs the program via run args when this file is run
if __name__ == "__main__":
    # 5 args:
    # arg1: previous end state properties file location
    # arg2: number of items to be generated
    # arg3: file system location of synthea properties file to edit
    # arg4: db string for target environment DB, in this format: postgres://<dbName>:<db-pass>@<aws db url>:5432/fhirdb
    # arg5: (optional) skip validation, useful if re-generating a bad batch, True or False
    validate_and_update(sys.argv[1:])
