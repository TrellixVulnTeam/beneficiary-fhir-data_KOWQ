{
    "widgets": [
        {
            "height": 6,
            "width": 18,
            "y": 0,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count/metadata/all" ],
                    [ ".", "http-requests/count/coverageAll/all" ],
                    [ ".", "http-requests/count/patientAll/all" ],
                    [ ".", "http-requests/count/eobAll/all" ]
                ],
                "view": "timeSeries",
                "stacked": true,
                "title": "Request Count All",
                "region": "us-east-1",
                "stat": "Sum",
                "period": 300
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 6,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count/metadata/bb" ],
                    [ ".", "http-requests/count/coverageAll/bb" ],
                    [ ".", "http-requests/count/patientAll/bb" ],
                    [ ".", "http-requests/count/eobAll/bb" ]
                ],
                "view": "timeSeries",
                "stacked": true,
                "title": "BB Request Count",
                "region": "us-east-1",
                "stat": "Sum",
                "period": 300
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 6,
            "x": 12,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count/metadata/bcda" ],
                    [ ".", "http-requests/count/coverageAll/bcda" ],
                    [ ".", "http-requests/count/patientAll/bcda" ],
                    [ ".", "http-requests/count/eobAll/bcda" ]
                ],
                "view": "timeSeries",
                "stacked": true,
                "title": "BCDA Request Count",
                "region": "us-east-1",
                "stat": "Sum",
                "period": 300
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 6,
            "x": 6,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count/metadata/mct" ],
                    [ ".", "http-requests/count/coverageAll/mct" ],
                    [ ".", "http-requests/count/patientAll/mct" ],
                    [ ".", "http-requests/count/eobAll/mct" ]
                ],
                "view": "timeSeries",
                "stacked": true,
                "title": "MCT Request Count",
                "region": "us-east-1",
                "stat": "Sum",
                "period": 300
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 12,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/latency/eobAll/bb" ],
                    [ ".", "http-requests/latency/eobAll/bcda" ],
                    [ ".", "http-requests/latency/eobAll/mct" ],
                    [ ".", "http-requests/latency/eobAll/ab2d" ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "title": "EOB P50 Latency",
                "region": "us-east-1",
                "period": 300,
                "stat": "p50"
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 12,
            "x": 6,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/latency/eobAll/bb" ],
                    [ ".", "http-requests/latency/eobAll/bcda" ],
                    [ ".", "http-requests/latency/eobAll/mct" ],
                    [ ".", "http-requests/latency/eobAll/ab2d" ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "title": "EOB P95 Latency",
                "region": "us-east-1",
                "stat": "p95",
                "period": 300
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 12,
            "x": 12,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/latency/eobAll/bb" ],
                    [ ".", "http-requests/latency/eobAll/bcda" ],
                    [ ".", "http-requests/latency/eobAll/mct" ],
                    [ ".", "http-requests/latency/eobAll/ab2d" ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "title": "EOB P99 Latency",
                "region": "us-east-1",
                "stat": "p99",
                "period": 300
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 12,
            "x": 18,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/latency/eobAll/bb" ],
                    [ ".", "http-requests/latency/eobAll/bcda" ],
                    [ ".", "http-requests/latency/eobAll/mct" ],
                    [ ".", "http-requests/latency/eobAll/ab2d" ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "title": "EOB Max Latency",
                "region": "us-east-1",
                "stat": "Maximum",
                "period": 300
            }
        },
        {
            "height": 5,
            "width": 12,
            "y": 23,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "AWS/EC2", "CPUUtilization", { "visible": false } ],
                    [ ".", ".", "InstanceType", "c5.4xlarge", { "color": "#2ca02c", "stat": "Average" } ],
                    [ "...", { "color": "#d62728" } ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "region": "us-east-1",
                "stat": "Maximum",
                "period": 3600,
                "title": "ASG CPU Usage"
            }
        },
        {
            "height": 3,
            "width": 24,
            "y": 28,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count-500/bb" ],
                    [ ".", "http-requests/count-500/bcda" ],
                    [ ".", "http-requests/count-500/mct" ],
                    [ ".", "http-requests/count-500/dpc" ],
                    [ ".", "http-requests/count-500/ab2d" ]
                ],
                "view": "singleValue",
                "region": "us-east-1",
                "title": "Total number of HTTP 500s errors in the last 24 hours",
                "period": 86400,
                "stat": "Sum"
            }
        },
        {
            "height": 3,
            "width": 24,
            "y": 31,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count-not-2xx/bb" ],
                    [ ".", "http-requests/count-not-2xx/bcda" ],
                    [ ".", "http-requests/count-not-2xx/mct" ],
                    [ ".", "http-requests/count-not-2xx/dpc" ],
                    [ ".", "http-requests/count-not-2xx/ab2d" ]
                ],
                "view": "singleValue",
                "region": "us-east-1",
                "title": "Total number of HTTP non-2XXs errors in the last 24 hours",
                "period": 86400,
                "stat": "Sum"
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 6,
            "x": 18,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count/metadata/dpc" ],
                    [ ".", "http-requests/count/coverageAll/dpc" ],
                    [ ".", "http-requests/count/patientAll/dpc" ],
                    [ ".", "http-requests/count/eobAll/dpc" ]
                ],
                "view": "timeSeries",
                "stacked": true,
                "region": "us-east-1",
                "stat": "Sum",
                "period": 300,
                "title": "DPC Request Count"
            }
        },
        {
            "height": 6,
            "width": 6,
            "y": 0,
            "x": 18,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/count/metadata/ab2d" ],
                    [ ".", "http-requests/count/coverageAll/ab2d" ],
                    [ ".", "http-requests/count/patientAll/ab2d" ],
                    [ ".", "http-requests/count/eobAll/ab2d" ]
                ],
                "view": "timeSeries",
                "stacked": true,
                "region": "us-east-1",
                "title": "AB2D Request Count",
                "period": 300,
                "stat": "Sum"
            }
        },
        {
            "height": 10,
            "width": 12,
            "y": 18,
            "x": 12,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "${dashboard_namespace}", "http-requests/latency/all/all", { "stat": "Average", "color": "#2ca02c" } ],
                    [ "...", { "color": "#ff7f0e", "stat": "p99" } ],
                    [ "...", { "color": "#d62728" } ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "title": "Latency Percentiles - All Requests",
                "region": "us-east-1",
                "stat": "Maximum",
                "period": 300
            }
        },
        {
            "height": 5,
            "width": 12,
            "y": 18,
            "x": 0,
            "type": "metric",
            "properties": {
                "metrics": [
                    [ "AWS/AutoScaling", "GroupInServiceInstances", "AutoScalingGroupName", "${asg_name}" ]
                ],
                "view": "timeSeries",
                "stacked": false,
                "region": "us-east-1",
                "stat": "Maximum",
                "period": 3600,
                "title": "ASG Instance Counts"
            }
        }
    ]
}
