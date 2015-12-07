package coza.opencollab.kafka;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;
import kafka.javaapi.consumer.ConsumerConnector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ConsumerGroupTest {
	
	/**
	 * Where zookeeper is running
	 */
	private static final String ZOOKEEPER = "localhost:2181";
	
	/**
	 * ID of of the group
	 */
	private static final String GROUP_ID = "1";
	
	/**
	 * Topic to listen for
	 */
	private static final String TOPIC = "my-test-topic";
	
	/**
	 * Number of threads to spawn
	 */
	private static final int THREADS = 1;
	
	/**
	 * How long should the consumers run before this test stops
	 */
	private static final int RUN_TIME = 60000;
	
	
	private final ConsumerConnector consumer;
	private final String topic;
	private ExecutorService executor;
	
	public static void main(String[] args) {
		// Read the arguments and use default where required
		String zooKeeper = (args.length >= 1 ? args[0] : ZOOKEEPER);
		String groupId = (args.length >= 2 ? args[1] : GROUP_ID);
		String topic = (args.length >= 3 ? args[2] : TOPIC);
		int threads = (args.length >= 4 ? Integer.parseInt(args[3]) : THREADS);
		
		// Instantiate our example
		ConsumerGroupTest example = new ConsumerGroupTest(zooKeeper, groupId, topic);
		example.run(threads);

		// let it run for the specified time, before shutting down
		// You probably wouldn't want to do this in production
		try {
			Thread.sleep(RUN_TIME);
		} catch (InterruptedException ie) {}
		example.shutdown();
	}

	public ConsumerGroupTest(String zookeeper, String groupId, String topic) {
		this.consumer = kafka.consumer.Consumer.createJavaConsumerConnector(createConsumerConfig(zookeeper, groupId));
		this.topic = topic;
	}

	public void shutdown() {
		if (consumer != null) consumer.shutdown();
		if (executor != null) executor.shutdown();
		try {
			if (!executor.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
				System.out.println("Timed out waiting for consumer threads to shut down, exiting uncleanly");
			}
		} catch (InterruptedException e) {
			System.out.println("Interrupted during shutdown, exiting uncleanly");
		}
	}

	public void run(int numThreads) {
		Map<String, Integer> topicCountMap = new HashMap<String, Integer>();
		topicCountMap.put(this.topic, new Integer(numThreads));
		Map<String, List<KafkaStream<byte[], byte[]>>> consumerMap = consumer.createMessageStreams(topicCountMap);
		List<KafkaStream<byte[], byte[]>> streams = consumerMap.get(topic);

		// now launch all the threads
		//
		executor = Executors.newFixedThreadPool(numThreads);

		// now create an object to consume the messages
		//
		int threadNumber = 0;
		for (final KafkaStream stream : streams) {
			executor.submit(new ConsumerTest(stream, threadNumber));
			threadNumber++;
		}
	}

	private static ConsumerConfig createConsumerConfig(String zookeeper, String groupId) {
		Properties props = new Properties();
		props.put("zookeeper.connect", zookeeper);
		props.put("group.id", groupId);
		props.put("zookeeper.session.timeout.ms", "400");
		props.put("zookeeper.sync.time.ms", "200");
		props.put("auto.commit.interval.ms", "1000");
		return new ConsumerConfig(props);
	}


	final class ConsumerTest implements Runnable {
		private KafkaStream stream;
		private int threadNumber;

		public ConsumerTest(KafkaStream stream, int threadNumber) {
			this.threadNumber = threadNumber;
			this.stream = stream;
			System.out.println("Created ConsumerTest: Thread: " + threadNumber);
		}

		public void run() {
			ConsumerIterator<byte[], byte[]> it = stream.iterator();
			while (it.hasNext())
				System.out.println("Thread " + threadNumber + ": " + new String(it.next().message()));
			System.out.println("Shutting down Thread: " + threadNumber);
		}
	}
}
