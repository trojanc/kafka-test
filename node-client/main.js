'use strict';

var kafka = require('kafka-node');
var Consumer = kafka.Consumer;
var Offset = kafka.Offset;
var Client = kafka.Client;
var topic = "my-test-topic"

var client = new Client('localhost:2181');
var topics = [
        {topic: topic}
    ],
    options = { autoCommit: false, fetchMaxWaitMs: 1000, fetchMaxBytes: 1024*1024 };

var consumer = new Consumer(client, topics, options);
var offset = new Offset(client);

consumer.on('message', function (message) {
    console.log("Got message" + JSON.stringify(message));
});

consumer.on('error', function (err) {
    console.log('Got error', err);
});

/*
* If consumer get `offsetOutOfRange` event, fetch data from the smallest(oldest) offset
*/
consumer.on('offsetOutOfRange', function (topic) {
    topic.maxNum = 2;
    offset.fetch([topic], function (err, offsets) {
        var min = Math.min.apply(null, offsets[topic.topic][topic.partition]);
        consumer.setOffset(topic.topic, topic.partition, min);
    });
});
