package org.example;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.exporting.PnmlExportNet;

public class BeamCombinerTest {
	private static final PluginContext context = new CLIPluginContext(new CLIContext(), "context");
	
	public static void main(String[] args) throws Exception {
		PipelineOptions pipelineOptions = PipelineOptionsFactory.fromArgs(args).withValidation().create();
		Pipeline pipeline = Pipeline.create(pipelineOptions);

		pipeline.apply(kafkaReader())
				.apply(parseLogs())
				.apply(Window.<KV<String, LogEntry>>into(FixedWindows.of(Duration.standardSeconds(5))))
				.apply(GroupByKey.<String, LogEntry>create())
				.apply(mapToEventRelations())
				.apply(GroupByKey.<Void, EventRelationMatrix>create())
				.apply(Combine.<Void, EventRelationMatrix>groupedValues((Iterable<EventRelationMatrix> values) -> {
					return EventRelationMatrix.join(values);
				}))
				.apply(constructNet())
				.apply(kafkaWriter());

		PipelineResult result = pipeline.run();
		try {
			result.waitUntilFinish();
		} catch (Exception exc) {
			result.cancel();
		}
	}

	private static PTransform<PBegin,PCollection<KV<String, String>>> kafkaReader() {
		return KafkaIO.<String, String>read()
				.withBootstrapServers("localhost:9092")
				.withTopic("logs-input")
				.withKeyDeserializer(StringDeserializer.class)
				.withValueDeserializer(StringDeserializer.class)
				.withoutMetadata();
	}

	private static KafkaIO.Write<String, String> kafkaWriter() {
		return KafkaIO.<String, String>write()
				.withBootstrapServers("localhost:9092")
				.withTopic("logs-petri")
				.withKeySerializer(StringSerializer.class)
				.withValueSerializer(StringSerializer.class);
	}

	private static MapElements<KV<String, String>, KV<String, LogEntry>> parseLogs() {
		return MapElements
				.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptor.of(LogEntry.class)))
				.via((KV<String, String> kv) -> {
					String[] split = kv.getValue().split(",", 3);
					String eventId = split[2];
					String caseId = split[0];
					Instant timestamp = new Instant(Long.parseLong(split[1]));
					return KV.of(caseId, new LogEntry(eventId, caseId, timestamp));
				});
	}
	
	private static MapElements<KV<String, Iterable<LogEntry>>, KV<Void, EventRelationMatrix>> mapToEventRelations() {
		return MapElements
				.into(TypeDescriptors.kvs(TypeDescriptors.nulls(), TypeDescriptor.of(EventRelationMatrix.class)))
				.via((KV<String, Iterable<LogEntry>> kv) -> {
					return KV.of(null, new EventRelationMatrix(kv.getValue()));
				});
	}
	
	private static MapElements<KV<Void, EventRelationMatrix>, KV<String, String>> constructNet() {
		return MapElements
				.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
				.via((KV<Void, EventRelationMatrix> kv) -> {
					Petrinet net = kv.getValue().mine();
					String net_xml = (new PnmlExportNet()).exportPetriNetToPNMLOrEPNMLString(
							context, net, Pnml.PnmlType.PNML, true);
					return KV.of("", net_xml);
				});
	}
}
