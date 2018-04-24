# Alpha Miner

This example shows how `AlphaMinerPlugin` from ProMs repository can be used inside Apache Beam pipeline.

## Requirements

Alpha miner example can be build using `ant` build tool with `ivy` dependency manager. In order to install them refer to http://ant.apache.org/manual/install.html and  http://ant.apache.org/ivy/history/latest-milestone/install.html respectively.

This example utilizes Apache Kafka to manage stream input and output. Script `../demo_data/start_kafka.sh` manages download , configuration and start of standalone Kafka instance.

## Quickstart

1. Build alpha miner by executing `ant` command. As a result of this file `alpha_miner-with-dependencies.jar` in `dist` directory will be generates.
2. Download, configure and start local Kafka instance by executing `../demo_data/start_kafka.sh` script.
3. Start populating Kafka-s `logs-input` topic with demo data by executing: `../demo_data/kafka_demo_producer.sh`
4. Start processing stream by running: `java -jar ./alpha_miner/dist/alpha_miner-with-dependencies.jar`.
5. Show results by using Kafka-s script for reading data from selected topic:
```
./kafka_2.11-1.0.0/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic logs-petri --property print.key=true --property key.separator=": "
```
