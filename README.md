# Process mining a stream with ProM and Apache Beam

This library includes examples of integration of Apache Beam, which is a stream processing framework, with ProM which is a framework used by scientists for designing and testing process mining algorightms.

Two examples are implemented: Alpha miner which reads data from Kafka, uses Beams build in windowing mechanism, applies Alpha miner algoritm on data in each window and saves the result as an xml back to kafka.

Second example Fuzzy Miner, usses different process mining technique and instead of savind the result in a text format it visualizes the process and saves results to kafka as png images.

This library also includes an utility box (demo_data directory) which includes scripts for configuring a test Apache Kafka instance and populate it with some demo data.

## Requirements

Both aplha miner and fuzzy miner examples can be build using `ant`(http://ant.apache.org/manual/install.html) and `ivy`(http://ant.apache.org/ivy/history/latest-milestone/install.html) build systems.

## Quickstart

1. Build alpha miner using `ant` command in `alpha_miner` directory.
2. Start local kafka instance by executing `./demo_data/start_kafka.sh` script.
3. Start populating kafka-s `logs-input` topic with data by executing: `./demo_data/kafka_demo_producer.sh`
4. Start processing stream by running: `java -jar ./alpha_miner/dist/alpha_miner-with-dependencies.jar`.

You can watch the results by reading output topic:
```
./kafka_2.11-1.0.0/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic logs-petri --property print.key=true --property key.separator=": "
```
