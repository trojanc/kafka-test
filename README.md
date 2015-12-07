kafka-test
==========

This project contains a few sample code to test integration to a Kafka server using nodejs and java
This code is probably not suitable for production use. It only serves as a way to get yourself starting interacting with kafka.

# Install Kafka
Follow the [instructions](http://kafka.apache.org/documentation.html#quickstart) on the Kafka wiki to build Kafka 0.9 and get a test broker up and running.

**Start Zookeeper**

`bin/zookeeper-server-start.sh config/zookeeper.properties`

**Start Kafka Server**

`bin/kafka-server-start.sh config/server.properties`

**Create the topic this client will be listening for**

`bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic my-test-topic`

# Using the provided examples
Go to on of the sub directories and see the README on how to start the code
The examples are tested with Oracle JDK 8, and nodejs 4.2.2 (although other versions might work too)

All these examples use a topic "my-test-topic", but you can change to code to use any topic you migh currently have active on your kafka server.

The provided examples assumes all services are running locally on your testing system. If this is not the case you may update the parameters to reflect your installation.
