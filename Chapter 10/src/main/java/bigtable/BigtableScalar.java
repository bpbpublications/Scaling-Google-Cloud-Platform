package bigtable;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.bigtable.admin.v2.Cluster;
import com.google.cloud.bigtable.grpc.BigtableClusterName;
import com.google.cloud.bigtable.grpc.BigtableClusterUtilities;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.cloud.monitoring.v3.PagedResponseWrappers.ListTimeSeriesPagedResponse;
import com.google.monitoring.v3.ListTimeSeriesRequest.TimeSeriesView;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.protobuf.Timestamp;

public class BigtableScalar {
	private String gcpProject;
	private String instance;
	private ProjectName projectName;
	private String clusterId;
	private String zoneId;

	/**
	 * Set of Bigtable cluster utilities to get the size of cluster and trigger
	 * scale up or down commands.
	 */
	private BigtableClusterUtilities clusterUtility;

	/**
	 * Its a stack driver client to fetch the details of a metric in a specified
	 * time interval.
	 */
	private MetricServiceClient metricServiceClient;

	public BigtableScalar(String project, String instance) throws IOException, GeneralSecurityException {
		this.gcpProject = project;
		this.instance = instance;
		clusterUtility = BigtableClusterUtilities.forInstance(gcpProject, instance);
		Cluster cluster = clusterUtility.getSingleCluster();
		this.clusterId = new BigtableClusterName(cluster.getName()).getClusterId();
		this.zoneId = BigtableClusterUtilities.getZoneId(cluster);
		// Instantiates a client
		metricServiceClient = MetricServiceClient.create();
		projectName = ProjectName.create(gcpProject);
	}

	/**
	 * Monitoring Metric on which the scaling will occur.COmplete list of metric
	 * could be seen here:
	 * https://cloud.google.com/monitoring/api/metrics_gcp#gcp-bigtable
	 */
	public static final String CPU_METRIC = "bigtable.googleapis.com/cluster/cpu_load";

	/**
	 * Variable to configure minimum number of nodes. This has a hard limit to be
	 * not less than 10% of maximimum number of nodes.
	 */
	public static final int MINIMUM_NUMBER_OF_NODES = 1;

	/**
	 * Variable to configure maximum number of nodes.
	 */
	public static final int MAXIMUM_NUMBER_OF_NODES = 10;

	/**
	 * Period after which metrics will be evalauted to get the new number of nodes.
	 * Every 10 minutes the metric of CPU load will be fetched and new number of
	 * nodes will be calculated.
	 */
	public static final long PROCESS_METRIC_PERIOD = 10;

	/**
	 * The number of nodes to increase or decrease the cluster by.
	 */
	public static final int NUMBER_OF_NODES_TO_INC_OR_DEC = 1;

	/**
	 * Value of CPU utilization to trigger scaling down.
	 */
	public static double CPU_PERCENT_SCALEDOWN = .5;

	/**
	 * Value of CPU percent to start scaling up.
	 */
	public static double CPU_PERCENT_SCALEUP = .7;

	/**
	 * The below function gets the list of CPU consumption for all nodes in last 10
	 * minutes and takes the value which is the greatest among them.
	 * 
	 * @return
	 * @throws IOException
	 */
	Point getLatestValue() throws IOException {

		Timestamp now = Timestamp.newBuilder().setSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
				.build();
		Timestamp tenMinutesAgo = Timestamp.newBuilder()
				.setSeconds(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) - 10 * 60).build();
		TimeInterval interval = TimeInterval.newBuilder().setStartTime(tenMinutesAgo).setEndTime(now).build();
		String filter = "metric.type=\"" + CPU_METRIC + "\"";

		ListTimeSeriesPagedResponse response = metricServiceClient.listTimeSeries(projectName, filter, interval,
				TimeSeriesView.FULL);
		return response.getPage().getValues().iterator().next().getPointsList().get(0);

	}

	/**
	 * Method to scale up or down the Bigtable instance.
	 */
	public Runnable getRunnable() {
		return new Runnable() {
			public void run() {
				try {
					double latestValue = getLatestValue().getValue().getDoubleValue();
					if (latestValue < CPU_PERCENT_SCALEDOWN) {

						if (clusterUtility.getClusterNodeCount(clusterId, zoneId) > MINIMUM_NUMBER_OF_NODES) {
							clusterUtility.setClusterSize(clusterId, zoneId,
									Math.max(clusterUtility.getClusterNodeCount(clusterId, zoneId)
											- NUMBER_OF_NODES_TO_INC_OR_DEC, MINIMUM_NUMBER_OF_NODES));
						}
					} else if (latestValue > CPU_PERCENT_SCALEUP) {

						if (clusterUtility.getClusterNodeCount(clusterId, zoneId) <= MAXIMUM_NUMBER_OF_NODES) {
							clusterUtility.setClusterSize(clusterId, zoneId,
									Math.min(clusterUtility.getClusterNodeCount(clusterId, zoneId)
											+ NUMBER_OF_NODES_TO_INC_OR_DEC, MAXIMUM_NUMBER_OF_NODES));
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
	}

	public static void main(String[] args) throws IOException, GeneralSecurityException {
		if (args.length < 2) {
			System.out.println("Usage: " + BigtableScalar.class.getName() + " <project-id> <instance-id>");
			System.exit(-1);
		}
		BigtableScalar bigtableScaler = new BigtableScalar(args[0], args[1]);
		ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(bigtableScaler.getRunnable(), 0, PROCESS_METRIC_PERIOD, TimeUnit.MINUTES);

	}

}
