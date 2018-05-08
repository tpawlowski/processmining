package org.example;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.swing.JComponent;
import javax.swing.JFrame;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.kafka.KafkaIO;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.transforms.windowing.WindowFn;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.TypeDescriptor;
import org.apache.beam.sdk.values.TypeDescriptors;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.deckfour.xes.model.XLog;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.processmining.alphaminer.plugins.AlphaMinerPlugin;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.AttributeMap;
import org.processmining.models.graphbased.ViewSpecificAttributeMap;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.jgraph.ProMJGraphVisualizer;
import org.processmining.models.semantics.petrinet.Marking;
import org.processmining.plugins.pnml.base.Pnml;
import org.processmining.plugins.pnml.exporting.PnmlExportNet;

public class BeamAlphaMiner {
	private static final PnmlExportNet exportNet = new PnmlExportNet();
	private static final PluginContext context = new CLIPluginContext(new CLIContext(), "context");

	public static void main(String[] args) throws Exception {
		Pipeline pipeline = Pipeline.create(pipelineOptions(args));
		pipeline.apply(kafkaReader())
				.apply(parseLogs())
				.apply(Window.<KV<String, LogEntry>>into(windowStrategy()))
				.apply(GroupByKey.<String, LogEntry>create())
				.apply(mineWindow())
				.apply(kafkaWriter());
		pipeline.run();
	}
	
	private static WindowFn<? super KV<String, LogEntry>,?> windowStrategy() {
		return FixedWindows.of(Duration.standardMinutes(1));
	}
	
	private static PipelineOptions pipelineOptions(String[] args) {
		return PipelineOptionsFactory.fromArgs(args).withValidation().create();
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

	private static MapElements<KV<String, String>, KV<String,LogEntry>> parseLogs() {
		return MapElements
				.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptor.of(LogEntry.class)))
				.via((KV<String, String> kv) -> {
					String[] split = kv.getValue().split(",", 3);
					String eventId = split[2];
					String caseId = split[0];
					Instant timestamp = new Instant(Long.parseLong(split[1]));
					return KV.of(kv.getKey(), new LogEntry(eventId, caseId, timestamp));
				});
	}

	private static MapElements<KV<String,Iterable<LogEntry>>, KV<String,String>> mineWindow() {
		return MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptors.strings()))
				.via((kv) -> {
					XLog log = XLogs.parse(kv.getKey(), kv.getValue());
					Object[] net_and_marking = 
							AlphaMinerPlugin.applyAlphaClassic(context, log, log.getClassifiers().get(0));
					
					visualise((Petrinet)net_and_marking[0], "/tmp/test_alpha.png");
					
					String net_xml = exportNet.exportPetriNetToPNMLOrEPNMLString(
							context, (Petrinet)net_and_marking[0], Pnml.PnmlType.PNML, true);
					return KV.of(kv.getKey(), net_xml);
				});
	}
	
	private static void visualise(Petrinet p, String path) {
		ViewSpecificAttributeMap map = new ViewSpecificAttributeMap();
		JComponent comp = ProMJGraphVisualizer.instance().visualizeGraph(context, p, map);
		JFrame frame = new javax.swing.JFrame();
	    frame.add(comp);
	    frame.setSize(800,800);
	    frame.setVisible(true);
	    BufferedImage bi = new BufferedImage(800, 800, BufferedImage.TYPE_INT_ARGB);
	    Graphics2D g = bi.createGraphics();
	    frame.paintComponents(g);
	    g.dispose();

	    // debugging - same files are saved in kafka
	    try {
	    		javax.imageio.ImageIO.write(bi, "png", new File(path));
	    } catch (IOException e) {
	    }
	}
}
