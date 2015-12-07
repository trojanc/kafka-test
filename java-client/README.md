node-client
==========

This is a simple nodejs client for Kafka

# Install Kafka
Follow the [instructions](http://kafka.apache.org/documentation.html#quickstart) on the Kafka wiki to build Kafka 0.9 and get a test broker up and running.

**Start Zookeeper**

`bin/zookeeper-server-start.sh config/zookeeper.properties`

**Start Kafka Server**

`bin/kafka-server-start.sh config/server.properties`

**Create the topic this client will be listening for**

`bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic my-test-topic`

**Build the maven project**

`mvn clean install`

**Run the java application**

**Send messages to the client from the command line**

`bin/kafka-console-producer.sh --broker-list localhost:9092 --topic my-test-topic`
You should now see the messages appear on the node-client terminal
