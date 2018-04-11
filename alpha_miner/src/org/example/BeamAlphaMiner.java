package org.example;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
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
import org.deckfour.xes.classification.XEventClass;
import org.deckfour.xes.model.XLog;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.processmining.alphaminer.abstractions.AlphaClassicAbstraction;
import org.processmining.alphaminer.algorithms.AlphaMiner;
import org.processmining.alphaminer.algorithms.AlphaMinerFactory;
import org.processmining.alphaminer.parameters.AlphaMinerParameters;
import org.processmining.alphaminer.parameters.AlphaVersion;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.util.Pair;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.exporting.PnmlExportNet;

public class BeamAlphaMiner {
	private static final PnmlExportNet exportNet = new PnmlExportNet();
	private static final PluginContext context = new CLIPluginContext(new CLIContext(), "context");
	
	public static void main(String[] args) throws Exception {
		PipelineOptions options = PipelineOptionsFactory
				.fromArgs(args)
                .withValidation()
                .create();
		Pipeline pipeline = Pipeline.create(options);
		
		PTransform<PBegin,PCollection<KV<String, String>>> kafkaIOReader = KafkaIO.<String, String>read()
	       .withBootstrapServers("localhost:9092")
	       .withTopic("logs-input")
	       .withKeyDeserializer(StringDeserializer.class)
	       .withValueDeserializer(StringDeserializer.class)
	       .withoutMetadata();
		
		KafkaIO.Write<String, String> kafkaIOWriter = KafkaIO.<String, String>write()
				.withBootstrapServers("localhost:9092")
				.withTopic("logs-petri")
				.withKeySerializer(StringSerializer.class)
				.withValueSerializer(StringSerializer.class);
		
		pipeline.apply(kafkaIOReader)
			.apply(MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptor.of(LogEntry.class)))
					.via((kv) -> {
						String[] split = kv.getValue().split(",", 3);
						String eventId = split[2];
				        String caseId = split[0];
				        Instant timestamp = new Instant(Long.parseLong(split[1]));
						return KV.of(kv.getKey(), new LogEntry(eventId, caseId, timestamp));
					}))
			.apply(Window.<KV<String, LogEntry>>into(FixedWindows.of(Duration.standardSeconds(10))))
			.apply(GroupByKey.<String, LogEntry>create())
			.apply(MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
				.via((kv) -> {
					XLog log = XLogs.parse(kv.getKey(), kv.getValue());
	        			AlphaMiner<XEventClass, ? extends AlphaClassicAbstraction<XEventClass>, ? extends AlphaMinerParameters> miner = AlphaMinerFactory
	        					.createAlphaMiner(log, log.getClassifiers().get(0), new AlphaMinerParameters(AlphaVersion.CLASSIC));
	        			Pair<Petrinet, Marking> net_and_marking = miner.run();
	        			String net_xml = exportNet.exportPetriNetToPNMLOrEPNMLString(context, net_and_marking.getFirst(), Pnml.PnmlType.PNML, true);
					return KV.of(kv.getKey(), net_xml);
				}))
			.apply(kafkaIOWriter);
		
		PipelineResult result = pipeline.run();
	    try {
	    		result.waitUntilFinish();
	    } catch (Exception exc) {
	    		result.cancel();
	    }
	}
}
