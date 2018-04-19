# Alpha Miner

This example shows how `AlphaMinerPlugin` from ProMs repository can be used inside Apache Beam pipeline.

## Quickstart

1. Build alpha miner using `ant` command in `alpha_miner` directory.
2. Start local kafka instance by executing `./demo_data/start_kafka.sh` script.
3. Start populating kafka-s `logs-input` topic with data by executing: `./demo_data/kafka_demo_producer.sh`
4. Start processing stream by running: `java -jar ./alpha_miner/dist/alpha_miner-with-dependencies.jar`.

You can watch the results by reading output topic:
```
./kafka_2.11-1.0.0/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic logs-petri --property print.key=true --property key.separator=": "
```
