from base64 import standard_b64encode
from hashlib import pbkdf2_hmac, sha256
from os import urandom
import hmac
import sys
import getpass
import string
import random
import re

# allowed password special characters (before encrypting)
allowed_specials = "~!$%#^&{}_><+=.;?|"

# hash settings
salt_size = 16
digest_len = 32
iterations = 4096

# A valid password should contain:
# - 12-99 characters (default to 24)
# - at least 2 uppercase letters
# - at least 2 lowercase letters
# - at least 2 numbers
# - at least 2 special characters
min_length = 15
uppers = list(string.ascii_uppercase)
lowers = list(string.ascii_lowercase)
digits = list(string.digits)
specials = list(allowed_specials)
characters = list(string.ascii_letters + string.digits)

# regex pattern to match a valid password
# at least two uppers: (?=.*?[A-Z]+.*?[A-Z]+)
# at least two lowers: (?=.*?[a-z]+.*?[a-z]+)
# at least two numbers: (?=.*?[0-9]+.*?[0-9]+)
# at least 2 specials: (?=.*?[{allowed_specials}]+.*?[{allowed_specials}]+)'
# and at least min_length long
password_re = f'^[A-Za-z]+(?=.*?[A-Z]+.*?[A-Z]+)(?=.*?[a-z]+.*?[a-z]+)(?=.*?[0-9]+.*?[0-9]+)(?=.*?[{allowed_specials}]+.*?[{allowed_specials}]+).{{{min_length},}}$'


def generate_random_password(length: int = 24):
    if length < 15:
        print('Not enough characters for a strong password')
        sys.exit(1)

    # requirements (at least)
    upper_count = 2
    lower_count = 2
    number_count = 2
    special_count = 2

    # initializing the password
    password = []

    # random uppers
    for i in range(upper_count):
        password.append(random.choice(uppers))

    # random lowers
    for i in range(lower_count):
        password.append(random.choice(lowers))

    # random numbers
    for i in range(number_count):
        password.append(random.choice(digits))

    # random specials
    for i in range(special_count):
        password.append(random.choice(specials))

    # add random characters until the password is the appropriate length
    if len(password) < length:
        random.shuffle(characters)
        for i in range(length - len(password)):
            password.append(random.choice(characters))

    # shuffle until the password starts with a alpha (just in case)
    match_re = '^[A-Za-z]+'
    match = None
    while match is None:
        random.shuffle(password)
        match = re.search(match_re, ''.join(password))

    return ''.join(password)


def b64enc(b: bytes) -> str:
    return standard_b64encode(b).decode('utf8')


def pg_scram_sha256(passwd: str) -> str:
    salt = urandom(salt_size)
    digest_key = pbkdf2_hmac('sha256', passwd.encode('utf8'), salt, iterations,
                             digest_len)
    client_key = hmac.digest(digest_key, 'Client Key'.encode('utf8'), 'sha256')
    stored_key = sha256(client_key).digest()
    server_key = hmac.digest(digest_key, 'Server Key'.encode('utf8'), 'sha256')
    return (
        f'SCRAM-SHA-256${iterations}:{b64enc(salt)}${b64enc(stored_key)}:{b64enc(server_key)}'
        # f'${b64enc(stored_key)}:{b64enc(server_key)}'
    )


def main():
    args = sys.argv[1:]

    if args and len(args) > 0:
        print("pgpass-gen.py takes no arguments")
        sys.exit(1)

    # generate a strong random password
    print("Generating random password..", end='  ')
    passwd = generate_random_password()
    if not passwd:
        print('[ERR]')
        sys.exit(1)
    print("    [OK]")

    # validate the password before encrypting
    print('Validating password complexity..', end='  ')
    match = re.search(password_re, passwd)
    if match:
        print("[OK]\n")
    else:
        print(f"[FAIL]\n'{passwd}' is an invalid password.")
        sys.exit(1)

    # hash it with scram-sha-256
    encrypted_password = pg_scram_sha256(passwd)
    if not encrypted_password:
        print("Error encrypting password using SCRAM-SHA-256")
        sys.exit(1)

    # print instructions
    print('Instructions:')
    print("  1. Save this newly generated password to your favorite password manager. You will use this to log into the database.")
    print(f"   YOUR PASSWORD: {passwd}", end='\n\n')
    print("  2. Send the following ENCRYPTED string to your database admin so they can update your account (admins will not be able to determine your real password from this)")
    print(f"   '{encrypted_password}'")


if __name__ == "__main__":
    main()
