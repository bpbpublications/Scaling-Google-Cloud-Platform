import argparse
import os
import pprint
import time
import uuid

PROJECT_ID = "scaling-gcp-demo"


def create_metric_descriptor(project_id):
    # [START monitoring_create_metric]
    from google.api import label_pb2 as ga_label
    from google.api import metric_pb2 as ga_metric
    from google.cloud import monitoring_v3

    client = monitoring_v3.MetricServiceClient()
    project_name = f"projects/{project_id}"
    descriptor = ga_metric.MetricDescriptor()
    descriptor.type = "custom.googleapis.com/my_metric"
    descriptor.metric_kind = ga_metric.MetricDescriptor.MetricKind.GAUGE
    descriptor.value_type = ga_metric.MetricDescriptor.ValueType.DOUBLE
    descriptor.description = "This is a simple example of a custom metric."

    labels = ga_label.LabelDescriptor()
    labels.key = "TestLabel"
    labels.value_type = ga_label.LabelDescriptor.ValueType.STRING
    labels.description = "This is a test label"
    descriptor.labels.append(labels)
    print(descriptor)
    descriptor = client.create_metric_descriptor(
        name=project_name, metric_descriptor=descriptor
    )
    print("Created {}.".format(descriptor.name))
    # [END monitoring_create_metric]


def delete_metric_descriptor(descriptor_name):
    # [START monitoring_delete_metric]
    from google.cloud import monitoring_v3

    client = monitoring_v3.MetricServiceClient()
    client.delete_metric_descriptor(name=descriptor_name)
    print("Deleted metric descriptor {}.".format(descriptor_name))
    # [END monitoring_delete_metric]


def write_time_series(project_id):
    # [START monitoring_write_timeseries]
    from google.cloud import monitoring_v3

    client = monitoring_v3.MetricServiceClient()
    project_name = f"projects/{project_id}"

    series = monitoring_v3.TimeSeries()
    series.metric.type = "custom.googleapis.com/my_metric"
    #+ str(uuid.uuid4())
    series.resource.type = "k8s_container"
    series.resource.labels["cluster_name"] = "cluster_name"
    series.resource.labels["container_name"] = "container_name"
    series.resource.labels["pod_name"] = "pod_name"
    series.resource.labels["namespace_name"] = "namespace_name"
    series.resource.labels["location"] = "us-central1-f"
    series.metric.labels["TestLabel"] = "My Label Data"
    now = time.time()
    seconds = int(now)
    nanos = int((now - seconds) * 10 ** 9)
    interval = monitoring_v3.TimeInterval(
        {"end_time": {"seconds": seconds, "nanos": nanos}}
    )
    point = monitoring_v3.Point({"interval": interval, "value": {"double_value": 3.14}})
    series.points = [point]
    client.create_time_series(name=project_name, time_series=[series])
    # [END monitoring_write_timeseries]


if __name__ == "__main__":
    create_metric_descriptor("infinite-zephyr-353609")
    while True:
        try:
            write_time_series("infinite-zephyr-353609")        
        except:
            print("An exception occurred")
        thread.sleep(60)
