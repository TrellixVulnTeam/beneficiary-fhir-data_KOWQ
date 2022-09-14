import sys
import os
import boto3
import botocore
import fnmatch
import time
from botocore.config import Config
from botocore.exceptions import ClientError
from pathlib import Path

boto_config = Config(region_name="us-east-1")
s3_client = boto3.client('s3', config=boto_config)

mitre_synthea_bucket = "bfd-synthea"
mitre_synthea_end_state = "/end_state/end_state.properties"
bfd_synthea_bucket = "bfd-test-synthea-etl-577373831711"
bfd_synthea_incoming = "Incoming/"
bfd_synthea_done = "Done/"
end_state_props_file = "end_state/end_state.properties"

code_map_files = [
    'betos_code_map.json',
    'condition_code_map.json',
    'dme_code_map.json',
    'drg_code_map.json',
    'hcpcs_code_map.json',
    'medication_code_map.json',
    'snf_pdpm_code_map.json',
    'snf_pps_code_map.json',
    'snf_rev_cntr_code_map.json',
    'external_codes.csv'
    ]

code_script_files = [
    'national_bfd.sh',
    'national_bfd_v2.sh'
    ]

def download_synthea_files(target_dir):
    for fn in code_map_files:
        output_fn = target_dir + fn
        print(f"file_path: {output_fn}")
        try:
            s3_client.download_file(mitre_synthea_bucket, fn, output_fn)
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] == "404":
                print(f"The object does not exist: {fn}")
            else:
                raise

def download_synthea_scripts(target_dir):
    for fn in code_script_files:
        output_fn = target_dir + fn
        print(f"download_synthea_scripts, file_path: {output_fn}")
        try:
            s3_client.download_file(mitre_synthea_bucket, fn, output_fn)
            os.chmod(os.fspath(output_fn), 0o744)
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] == "404":
                print(f"The object does not exist: {fn}")
            else:
                raise

def download_end_state_props_file(target_dir):
    base_name = os.path.basename(end_state_props_file)
    output_fn = target_dir + base_name
    try:
        s3_client.download_file(mitre_synthea_bucket, end_state_props_file, output_fn)
    except botocore.exceptions.ClientError as e:
        if e.response['Error']['Code'] == "404":
            print("The object does not exist.")
        else:
            raise

def upload_end_state_props_file(file_name):
    print(f"upload_end_state_props_file, file_name: {file_name}")
    try:
        s3_client.upload_file(file_name, mitre_synthea_bucket, mitre_synthea_end_state)
    except botocore.exceptions.ClientError as e:
        if e.response['Error']['Code'] == "404":
            print("The object does not exist.")
        else:
            raise

def extract_timestamp_from_manifest(synthea_output_dir) -> str:
    if not os.path.exists(synthea_output_dir + 'manifest.xml'):
        return ""
    lines = []
    with open(synthea_output_dir + 'manifest.xml') as file:
        lines = file.readlines()
    for line in lines:
        if line.startswith("<dataSetManifest"):
            beg_ix = line.find('timestamp=')
            if beg_ix > 0:
                ## timestamp="yyyy-mm-ddThh:mi:ssZ"
                ts = line[beg_ix:beg_ix+31]
                lines = ts.split("\"")
                return lines[1] if len(lines) > 1 else ""

def upload_rif_files(synthea_output_dir, s3_folder):
    print(f"upload_rif_files, remote_fn: {s3_folder}")
    for fn in os.listdir(synthea_output_dir):
        ## ignore the export_summary.csv
        if fn.startswith("export_summary"):
            continue
        tmp_ext = fn.split('.')[1:]
        if len(tmp_ext) > 0 and tmp_ext[0] == 'csv':
            try:
                local_fn = synthea_output_dir + fn
                remote_fn = s3_folder + "/" + fn
                print(f"upload_rif_files, local_fn: {local_fn}, remote_fn: {remote_fn}")
                s3_client.upload_file(local_fn, bfd_synthea_bucket, remote_fn)
            except botocore.exceptions.ClientError as e:
                if e.response['Error']['Code'] == "404":
                    print("The object does not exist.")
                else:
                    raise

def upload_manifest_file(synthea_output_dir, s3_folder):
    local_fn = synthea_output_dir + "manifest.xml"
    remote_fn = s3_folder + "/" + "manifest.xml"
    
    if os.path.exists(local_fn):
        print(f"upload_manifest_file, local_fn: {local_fn}, remote_fn: {remote_fn}")
        try:
            s3_client.upload_file(local_fn, bfd_synthea_bucket, remote_fn)
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] == "404":
                print("The object does not exist.")
            else:
                raise

def path_exists(s3_resource, key_name, loop_cnt, max_tries):
    """Check to see if an object exists on S3"""
    cnt = loop_cnt
    print(f"S3 waiting for: Bucket: {bfd_synthea_bucket}, Key: {key_name}")
    while loop_cnt < max_tries:
        try:
            s3_resource.ObjectSummary(bucket_name=bfd_synthea_bucket, key=key_name).load()
            return cnt
        except botocore.exceptions.ClientError as e:
            if e.response['Error']['Code'] == '404':
                cnt += 1
                continue
            else:
                raise Exception( "boto3 client error in wait_for_manifest_done: " + e.__str__())
    
def wait_for_manifest_done(s3_folder):
    print(f"wait_for_manifest_done, S3 folder: {s3_folder}")
    s3_resource = boto3.resource('s3', config=boto_config)
    num_mins_3_days = 4320
    loop_cnt = 0
    key_name = s3_folder + "/"

    loop_cnt = path_exists(s3_resource, key_name, loop_cnt, num_mins_3_days)
    if loop_cnt >= num_mins_3_days:
        raise Exception(f"failed to detect S3 folder using key: {key_name}")
    else:
        key_name = s3_folder + "/manifest.xml"
        loop_cnt = path_exists(s3_resource, key_name, loop_cnt, num_mins_3_days)
        if loop_cnt >= num_mins_3_days:
            raise Exception(f"failed to detect manifest.xml using key: {key_name}")

def upload_synthea_results(synthea_output_dir):
    manifest_ts = extract_timestamp_from_manifest(synthea_output_dir)
    if len(manifest_ts) < 1:
        raise
    s3_folder = bfd_synthea_incoming + manifest_ts
    print(f"uploading RIF files to: {s3_folder}");
    upload_rif_files(synthea_output_dir, s3_folder)
    upload_manifest_file(synthea_output_dir, s3_folder)

    s3_folder = bfd_synthea_done + manifest_ts
    wait_for_manifest_done(s3_folder)

def main(args):
    target = args[0] if len(args) > 0 else "./"
    if not target.endswith('/'):
        target = target + "/"
    op = args[1] if len(args) > 1 else "download_file"
    print(f"op: {op}, target_dir: {target}")
    match op:
        case "download_file":
            download_synthea_files(target)
        case "download_script":
            download_synthea_scripts(target)
        case "download_prop":
            download_end_state_props_file(target)
        case "upload_prop":
            upload_end_state_props_file(target)
        case "upload_synthea_results":
            upload_synthea_results(target)
        case _:
            return 1


if __name__ == "__main__":
    main(sys.argv[1:])