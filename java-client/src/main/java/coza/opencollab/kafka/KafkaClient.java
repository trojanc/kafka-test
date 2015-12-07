package coza.opencollab.kafka;

import kafka.api.FetchRequest;
import kafka.api.FetchRequestBuilder;
import kafka.api.PartitionOffsetRequestInfo;
import kafka.common.ErrorMapping;
import kafka.common.TopicAndPartition;
import kafka.javaapi.*;
import kafka.javaapi.consumer.SimpleConsumer;
import kafka.message.MessageAndOffset;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import kafka.cluster.BrokerEndPoint;

public class KafkaClient {

	private static int MAX_READS = 1000;

	private static final String TOPIC = "my-test-topic";
	private static final int PARTITION = 0;
	private static final int PORT = 9092;

	/** Initial list of known brokers */
	private final List<String> seedBrokers = new ArrayList();

	/** Brokers that are discovered and available */
	private List<String> availableBrokers = new ArrayList<String>();


	public KafkaClient() {
		seedBrokers.add("localhost");
		try {
			run();
		} catch (Exception ex) {
			Logger.getLogger(KafkaClient.class.getName()).log(Level.SEVERE, null, ex);
		}
	}


	public static void main(String args[]) {
		KafkaClient kafkaClient = new KafkaClient();
	}





	public void run() throws Exception {


		// find the meta data about the topic and partition we are interested in
		//
		PartitionMetadata metadata = findLeader(seedBrokers, PORT, TOPIC, PARTITION);
		if (metadata == null) {
			System.out.println("Can't find metadata for Topic and Partition. Exiting");
			return;
		}
		if (metadata.leader() == null) {
			System.out.println("Can't find Leader for Topic and Partition. Exiting");
			return;
		}
		String leadBroker = metadata.leader().host();
		String clientName = "Client_" + TOPIC + "_" + PARTITION;

		SimpleConsumer consumer = new SimpleConsumer(leadBroker, PORT, 100000, 64 * 1024, clientName);
		long readOffset = getLastOffset(consumer,TOPIC, PARTITION, kafka.api.OffsetRequest.EarliestTime(), clientName);

		int numErrors = 0;
		while (MAX_READS > 0) {
			if (consumer == null) {
				consumer = new SimpleConsumer(leadBroker, PORT, 100000, 64 * 1024, clientName);
			}
			FetchRequest req = new FetchRequestBuilder()
			.clientId(clientName)
			.addFetch(TOPIC, PARTITION, readOffset, 100000) // Note: this fetchSize of 100000 might need to be increased if large batches are written to Kafka
			.build();
			FetchResponse fetchResponse = consumer.fetch(req);

			if (fetchResponse.hasError()) {
				numErrors++;
				// Something went wrong!
				short code = fetchResponse.errorCode(TOPIC, PARTITION);
				System.out.println("Error fetching data from the Broker:" + leadBroker + " Reason: " + code);
				if (numErrors > 5) break;
				if (code == ErrorMapping.OffsetOutOfRangeCode())  {
					// We asked for an invalid offset. For simple case ask for the last element to reset
					readOffset = getLastOffset(consumer, TOPIC, PARTITION, kafka.api.OffsetRequest.LatestTime(), clientName);
					continue;
				}
				consumer.close();
				consumer = null;
				leadBroker = findNewLeader(leadBroker, TOPIC, PARTITION, PORT);
				continue;
			}
			numErrors = 0;

			long numRead = 0;
			for (MessageAndOffset messageAndOffset : fetchResponse.messageSet(TOPIC, PARTITION)) {
				long currentOffset = messageAndOffset.offset();
				if (currentOffset < readOffset) {
					System.out.println("Found an old offset: " + currentOffset + " Expecting: " + readOffset);
					continue;
				}
				readOffset = messageAndOffset.nextOffset();
				ByteBuffer payload = messageAndOffset.message().payload();

				byte[] bytes = new byte[payload.limit()];
				payload.get(bytes);
				System.out.println(String.valueOf(messageAndOffset.offset()) + ": " + new String(bytes, "UTF-8"));
				numRead++;
				MAX_READS--;
			}

			if (numRead == 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
				}
			}
		}
		if (consumer != null) consumer.close();
	}

	public static long getLastOffset(SimpleConsumer consumer, String topic, int partition, long whichTime, String clientName) {
		TopicAndPartition topicAndPartition = new TopicAndPartition(topic, partition);
		
		Map<TopicAndPartition, PartitionOffsetRequestInfo> requestInfo = new HashMap<TopicAndPartition, PartitionOffsetRequestInfo>();
		requestInfo.put(topicAndPartition, new PartitionOffsetRequestInfo(whichTime, 1));
		
		// Create a request to get our current offset
		kafka.javaapi.OffsetRequest request = new kafka.javaapi.OffsetRequest(requestInfo, kafka.api.OffsetRequest.CurrentVersion(), clientName);
		OffsetResponse response = consumer.getOffsetsBefore(request);

		if (response.hasError()) {
			System.out.println("Error fetching data Offset Data the Broker. Reason: " + response.errorCode(topic, partition) );
			return 0;
		}
		long[] offsets = response.offsets(topic, partition);
		return offsets[0];
	}

	/**
	 * Search for the new leader
	 * @param oldLeader Name of the previous leader
	 * @param topic
	 * @param partition
	 * @param port
	 * @return
	 * @throws Exception
	 */
	private String findNewLeader(String oldLeader, String topic, int partition, int port) throws Exception {
		for (int i = 0; i < 3; i++) {
			boolean goToSleep = false;
			PartitionMetadata metadata = findLeader(availableBrokers, port, topic, partition);
			if (metadata == null) {
				goToSleep = true;
			} else if (metadata.leader() == null) {
				goToSleep = true;
			} else if (oldLeader.equalsIgnoreCase(metadata.leader().host()) && i == 0) {
				// first time through if the leader hasn't changed give ZooKeeper a second to recover
				// second time, assume the broker did recover before failover, or it was a non-Broker issue
				//
				goToSleep = true;
			} else {
				return metadata.leader().host();
			}
			if (goToSleep) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
				}
			}
		}
		System.out.println("Unable to find new leader after Broker failure. Exiting");
		throw new Exception("Unable to find new leader after Broker failure. Exiting");
	}

	/**
	 * Find the current leader
	 * @param seedBrokers List of seeds to work from
	 * @param port Port the brokers are listening on
	 * @param topic Topic we are interested in
	 * @param partition Partition we are interested in
	 * @return The Partition Metadata with the leader information
	 */
	private PartitionMetadata findLeader(List<String> seedBrokers, int port, String topic, int partition) {
		PartitionMetadata returnMetaData = null;
		loop:
		// Loop through all the seed brokers to find the current leader
		for (String seed : seedBrokers) {
			SimpleConsumer consumer = null;
			try {
				// Create a temp consumer to one of the seeds
				consumer = new SimpleConsumer(seed, port, 100000, 64 * 1024, "leaderLookup");
				
				// Convert our single topic to a list
				List<String> topics = Collections.singletonList(topic);
				
				// Create topic meta data request with our list
				TopicMetadataRequest req = new TopicMetadataRequest(topics);
				
				kafka.javaapi.TopicMetadataResponse resp = consumer.send(req);

				List<TopicMetadata> metaData = resp.topicsMetadata();
				for (TopicMetadata item : metaData) {
					for (PartitionMetadata part : item.partitionsMetadata()) {
						if (part.partitionId() == partition) {
							returnMetaData = part;
							break loop;
						}
					}
				}
			} catch (Exception e) {
				System.out.println("Error communicating with Broker [" + seed + "] to find Leader for [" + topic + ", " + partition + "] Reason: " + e);
			} finally {
				if (consumer != null) consumer.close();
			}
		}
		
		// Clear the list and add the new brokers that was found
		if (returnMetaData != null) {
			availableBrokers.clear();
			for (BrokerEndPoint replica : returnMetaData.replicas()) {
				availableBrokers.add(replica.host());
			}
		}
		return returnMetaData;
	}
}
