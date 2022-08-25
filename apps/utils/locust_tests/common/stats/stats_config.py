import dataclasses
import re
from argparse import Namespace
from dataclasses import dataclass
from enum import Enum
from typing import Any, Dict, List, Optional

from locust.argument_parser import LocustArgumentParser


class StatsStorageType(str, Enum):
    """Enumeration for each available type of storage for JSON stats"""

    FILE = "file"
    """Indicates that aggregated statistics will be stored to a local file"""
    S3 = "s3"
    """Indicates that aggregated statistics will be stored to an S3 bucket"""


class StatsEnvironment(str, Enum):
    """Enumeration for each possible test running environment"""

    TEST = "test"
    """Indicates that the running environment is in the TEST environment"""

    # TODO: PROD_SBX may be "prod-sbx" or "prod_sbx" depending on context (specifically, Glue Table
    # partition columns) so a better way of handling its string representation should be considered.
    # For now, "prod-sbx" is the only string representation expected to be encountered by this code
    # and other contexts
    PROD_SBX = "prod-sbx"
    """Indicates that the running environment is in the PROD-SBX environment"""
    PROD = "prod"
    """Indicates that the running environment is in the PROD environment"""


class StatsComparisonType(str, Enum):
    """Enumeration for each possible type of stats comparison"""

    PREVIOUS = "previous"
    """Indicates that the comparison will be against the most recent, previous run under a given
    tag"""
    AVERAGE = "average"
    """Indicates that the comparison will be against the average of all runs under a given tag"""


@dataclass
class StatsConfiguration:
<<<<<<< HEAD
    """Dataclass that holds data about where and how aggregated performance statistics are stored and compared"""
=======
    """Dataclass that holds data about where and how aggregated performance statistics are stored
    and compared"""
>>>>>>> cbrune/bfd-1922-add-pipeline-files

    stats_store: StatsStorageType
    """The storage type that the stats will be written to"""
    stats_env: StatsEnvironment
    """The test running environment from which the statistics will be collected"""
<<<<<<< HEAD
    store_tag: str
    """A simple string tag that is used to partition collected statistics when stored"""
    path: Optional[str]
    """The local parent directory where JSON files will be written to.
    Used only if type is file, ignored if type is s3"""
    bucket: Optional[str]
    """The AWS S3 Bucket that the JSON will be written to.
    Used only if type is s3, ignored if type is file"""
    database: Optional[str]
    """Name of the Athena database that is queried upon when comparing statistics.
    Also used as part of the file path when storing stats in S3"""
    table: Optional[str]
    """Name of the table to query using Athena if store is s3 and compare is set.
    Also used as part of the file path when storing stats in S3"""
    compare: Optional[StatsComparisonType]
=======
    stats_store_tags: List[str]
    """A simple List of string tags that are used to partition collected statistics when stored"""
    stats_store_file_path: Optional[str]
    """The local parent directory where JSON files will be written to.
    Used only if type is file, ignored if type is s3"""
    stats_store_s3_bucket: Optional[str]
    """The AWS S3 Bucket that the JSON will be written to.
    Used only if type is s3, ignored if type is file"""
    stats_store_s3_database: Optional[str]
    """Name of the Athena database that is queried upon when comparing statistics.
    Also used as part of the file path when storing stats in S3"""
    stats_store_s3_table: Optional[str]
    """Name of the table to query using Athena if store is s3 and compare is set.
    Also used as part of the file path when storing stats in S3"""
    stats_compare: Optional[StatsComparisonType]
>>>>>>> cbrune/bfd-1922-add-pipeline-files
    """Indicates the type of performance stats comparison that will be done"""
    stats_compare_tag: Optional[str]
    """Indicates the tag from which comparison statistics will be loaded"""
<<<<<<< HEAD

    def to_key_val_str(self) -> str:
        """Returns a key-value string representation of this StatsConfiguration instance.
        Used to serialize this object to config.

        Returns:
            str: The key-value string representation of this object.
        """
        as_dict = dataclasses.asdict(self)
        dict_non_empty = {k: v for k, v in as_dict.items() if v is not None and v != ""}
        return ";".join(
            [
                f"{k}={str(v) if not isinstance(v, Enum) else v.name}"
                for k, v in dict_non_empty.items()
            ]
        )
=======
    stats_compare_load_limit: int
    """Indicates the limit of previous AggregatedStats loaded for comparison; used only for average
    comparisons"""
    stats_compare_meta_file: Optional[str]
    """Indicates the path to a JSON file containing metadata about how stats should be compared for
    the running test suite. Overrides the default specified by the test suite, if any"""
>>>>>>> cbrune/bfd-1922-add-pipeline-files

    @classmethod
    def register_custom_args(cls, parser: LocustArgumentParser) -> None:
        """Registers commnad-line arguments representing the fields of this dataclass

        Args:
            parser (LocustArgumentParser): The argument parser to register custom arguments to
        """
        stats_group = parser.add_argument_group(
            title="stats",
            description="Argparse group for stats collection and comparison related arguments",
        )

        # Ensure only file _or_ S3 storage can be selected, not both
        storage_type_group = stats_group.add_mutually_exclusive_group()
        storage_type_group.add_argument(
            "--stats-store-file",
            help="Specifies that stats will be written to a local file",
            dest="stats_store",
            env_var="LOCUS_STATS_STORE_TO_FILE",
            action="store_const",
            const=StatsStorageType.FILE,
        )
        storage_type_group.add_argument(
            "--stats-store-s3",
            help="Specifies that stats will be written to an S3 bucket",
            dest="stats_store",
            env_var="LOCUS_STATS_STORE_TO_S3",
            action="store_const",
            const=StatsStorageType.S3,
        )

        stats_group.add_argument(
            "--stats-env",
            type=cls.__env_from_value,
            help="Specifies the test running environment which the tests are running against",
            dest="stats_env",
            env_var="LOCUST_STATS_ENVIRONMENT",
        )
        stats_group.add_argument(
            "--stats-store-tag",
            type=cls.__validate_tag,
            help=(
                "Specifies the tags under which collected statistics will be stored. Can be"
                " specified multiple times"
            ),
            dest="stats_store_tags",
            env_var="LOCUS_STATS_STORE_TAG",
            action="append",
        )
        stats_group.add_argument(
            "--stats-store-file-path",
            type=str,
            help=(
                "Specifies the parent directory where JSON stats will be written to. Only used if"
                ' --stats-store is "FILE"'
            ),
            dest="stats_store_file_path",
            env_var="LOCUS_STATS_STORE_FILE_PATH",
            default="./",
        )
        stats_group.add_argument(
            "--stats-store-s3-bucket",
            type=str,
            help=(
                "Specifies the S3 bucket that JSON stats will be written to. Only used if"
                ' --stats-store is "S3"'
            ),
            dest="stats_store_s3_bucket",
            env_var="LOCUS_STATS_STORE_S3_BUCKET",
        )
        stats_group.add_argument(
            "--stats-store-s3-database",
            type=str,
            help=(
                "Specifies the Athena database that is queried upon when comparing statistics. Also"
                " used as part of the S3 key/path when storing stats to S3"
            ),
            dest="stats_store_s3_database",
            env_var="LOCUS_STATS_STORE_S3_DATABASE",
        )
        stats_group.add_argument(
            "--stats-store-s3-table",
            type=str,
            help=(
                "Specifies the Athena table that is queried upon when comparing statistics. Also"
                " used as part of the S3 key/path when storing stats to S3"
            ),
            dest="stats_store_s3_table",
            env_var="LOCUS_STATS_STORE_S3_TABLE",
        )

        # Ensure that only one type of comparison can be chosen via arguments
        compare_type_group = stats_group.add_mutually_exclusive_group()
        compare_type_group.add_argument(
            "--stats-compare-previous",
            help=(
                "Specifies that the current run's performance statistics will be compared against"
                " the previous matching run's performance statistics"
            ),
            dest="stats_compare",
            env_var="LOCUST_STATS_COMPARE_PREVIOUS",
            action="store_const",
            const=StatsComparisonType.PREVIOUS,
        )
        compare_type_group.add_argument(
            "--stats-compare-average",
            help=(
                "Specifies that the current run's performance statistics will be compared against"
                " an average of the last, by default, 5 matching runs"
            ),
            dest="stats_compare",
            env_var="LOCUST_STATS_COMPARE_AVERAGE",
            action="store_const",
            const=StatsComparisonType.AVERAGE,
        )

<<<<<<< HEAD
        # Validate necessary parameters if S3 is specified
        if storage_type == StatsStorageType.S3:
            # Validate that parameters necessary to store stats in S3
            # are specified if S3 is the store
            if not "bucket" in config_dict:
                raise ValueError('"bucket" must be specified if "store" is "s3"') from None
            if not "database" in config_dict:
                raise ValueError('"database" must be specified if "store" is "s3"') from None
            if not "table" in config_dict:
                raise ValueError('"table" must be specified if "store" is "s3"') from None

        return cls(
            store=storage_type,
            env=stats_environment,
            store_tag=storage_tag,
            path=config_dict.get("path") or "./",
            bucket=config_dict.get("bucket"),
            database=config_dict.get("database"),
            table=config_dict.get("table"),
            compare=compare_type,
            comp_tag=comparison_tag,
=======
        stats_group.add_argument(
            "--stats-compare-tag",
            type=cls.__validate_tag,
            help="Specifies the tag that matching runs will be found under to compare against",
            dest="stats_compare_tag",
            env_var="LOCUST_STATS_COMPARE_TAG",
        )
        stats_group.add_argument(
            "--stats-compare-load-limit",
            type=int,
            help=(
                "Specifies the limit for number of previous stats to load when when doing"
                " comparisons, defaults to 5. Used solely for limiting stats loaded during average"
                " comparisons"
            ),
            dest="stats_compare_load_limit",
            env_var="LOCUST_STATS_COMPARE_LOAD_LIMIT",
            default=5,
        )
        stats_group.add_argument(
            "--stats-compare-meta-file",
            type=str,
            help=(
                "Specifies the file path to a JSON file containing metadata about how stats should"
                " be compared for a given test suite. Overrides the default path specified by a"
                " test suite, if any"
            ),
            dest="stats_compare_meta_file",
            env_var="LOCUST_STATS_COMPARE_META_FILE",
>>>>>>> cbrune/bfd-1922-add-pipeline-files
        )

    @classmethod
    def from_parsed_opts(cls, parsed_opts: Namespace) -> "StatsConfiguration":
        """Constructs an instance of StatsConfiguration from a parsed options Namespace. This will
        typically be the Locust Environment.parsed_options Namespace.

        Returns:
            Optional[StatsConfiguration]: A StatsConfiguration instance if "stats_config" is valid,
            None otherwise
        """
        opts_as_dict = vars(parsed_opts)
        common_keys = opts_as_dict.keys() & {
            field.name for field in dataclasses.fields(StatsConfiguration)
        }
        stats_args: Dict[str, Any] = {k: v for k, v in opts_as_dict.items() if k in common_keys}

        try:
<<<<<<< HEAD
            stats_config = StatsConfiguration.from_key_val_str(stats_config_str)
        except ValueError as e:
            logger = logging.getLogger()
            logger.warning('--stats-config was invalid: "%s"', e)
            return None
=======
            stats_config = StatsConfiguration(**stats_args)
        except ValueError as exc:
            raise ValueError(
                f"Unable to create instance of StatsConfiguration from given arguments: {str(exc)}"
            ) from exc
>>>>>>> cbrune/bfd-1922-add-pipeline-files

        return stats_config

    @staticmethod
    def __env_from_value(val: str) -> StatsEnvironment:
        try:
            return StatsEnvironment(val.lower())
        except ValueError as exc:
            raise ValueError(
                f'Value must be one of: {", ".join([e.value for e in StatsEnvironment])}'
            ) from exc

    @staticmethod
    def __validate_tag(tag: str) -> str:
        # Tags must follow the BFD Insights data convention constraints for
        # partition/folders names, as it is used as a partition folder when uploading
        # to S3
        if not re.fullmatch("[a-z0-9_]+", tag) or not tag:
            raise ValueError(
                'Value must only consist of lower-case letters, numbers and the "_" character'
            ) from None

        return tag
