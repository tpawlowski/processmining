package org.example;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
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
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.deckfour.xes.model.XLog;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.processmining.contexts.cli.CLIContext;
import org.processmining.contexts.cli.CLIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.models.graphbased.directed.fuzzymodel.MutableFuzzyGraph;
import org.processmining.models.graphbased.directed.fuzzymodel.metrics.MetricsRepository;
import org.processmining.plugins.fuzzymodel.FuzzyModelVisualization;
import org.processmining.plugins.fuzzymodel.adapter.FuzzyAdapterPlugin;
import org.processmining.plugins.fuzzymodel.miner.FuzzyMinerPlugin;

public class BeamFuzzyMiner {
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

	private static KafkaIO.Write<String, byte[]> kafkaWriter() {
		return KafkaIO.<String, byte[]>write()
				.withBootstrapServers("localhost:9092")
				.withTopic("logs-images")
				.withKeySerializer(StringSerializer.class)
				.withValueSerializer(ByteArraySerializer.class);
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

	private static MapElements<KV<String,Iterable<LogEntry>>, KV<String, byte[]>> mineWindow() {
		return MapElements.into(TypeDescriptors.kvs(TypeDescriptors.strings(), TypeDescriptor.of(byte[].class)))
				.via((kv) -> {
					XLog log = XLogs.parse(kv.getKey(), kv.getValue());
					MetricsRepository metricRepo = (new FuzzyMinerPlugin()).mineDefault(context, log);
					MutableFuzzyGraph fuzzyGraph = (new FuzzyAdapterPlugin()).mineGeneric(context, metricRepo);
					JComponent component = (new FuzzyModelVisualization()).visualize(context, fuzzyGraph);
					
					JFrame frame = new javax.swing.JFrame();
					frame.add(component);
					frame.setSize(800,800);
					frame.setVisible(true);
					BufferedImage bi = new java.awt.image.BufferedImage(800, 800, java.awt.image.BufferedImage.TYPE_INT_ARGB);
					Graphics2D g = bi.createGraphics();
					frame.paintComponents(g);
					g.dispose();
					
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					
					try {
						ImageIO.write(bi, "png", outputStream);
						byte[] data = outputStream.toByteArray();
						return KV.of(kv.getKey(), data);
					} catch (IOException e) {
						return null;
					}
				});
	}
}
