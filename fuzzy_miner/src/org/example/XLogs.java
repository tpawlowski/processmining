package org.example;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.deckfour.xes.classification.XEventNameClassifier;
import org.deckfour.xes.extension.XExtensionManager;
import org.deckfour.xes.factory.XFactory;
import org.deckfour.xes.factory.XFactoryNaiveImpl;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeLiteralImpl;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XAttributeTimestampImpl;
import org.deckfour.xes.model.impl.XEventImpl;

public class XLogs {
	private static final XFactory factory = new XFactoryNaiveImpl();
	private static final XExtensionManager extensionManager = XExtensionManager.instance();
	private static final Calendar cal = Calendar.getInstance();

	static XLog parse(String name, Iterable<LogEntry> logEntries) {
		Map<String, XTrace> traces = new HashMap<String, XTrace>();
		for (LogEntry logEntry: logEntries) {
			XTrace trace = traces.get(logEntry.getCaseId());
			if (trace == null) {
				trace = factory.createTrace();
				trace.getAttributes().put("concept:name", factory.createAttributeLiteral("concept:name", logEntry.getCaseId(), extensionManager.getByPrefix("concept")));
				traces.put(logEntry.getCaseId(), trace);
			}

			cal.setTimeInMillis(logEntry.getTimestamp().getMillis());

			XAttributeMapImpl map = new XAttributeMapImpl();
			map.put("concept:name", new XAttributeLiteralImpl("concept:name", logEntry.getEventId()));
			map.put("lifecycle:transition", new XAttributeLiteralImpl("lifecycle:transition", "complete"));
			map.put("time:timestamp", new XAttributeTimestampImpl("time:timestamp", cal.getTime()));
			XEvent event = new XEventImpl(map);

			trace.add(event);
		}
		XLog log = emptyLog(name);
		log.addAll(traces.values());
		return log;
	}

	private static XLog emptyLog(String name) {
		XFactory factory = new XFactoryNaiveImpl();
		XExtensionManager extensionManager = XExtensionManager.instance();
		XLog log = factory.createLog();
		log.getAttributes().put("concept:name", factory.createAttributeLiteral("concept:name", name, extensionManager.getByPrefix("concept")));
		log.getAttributes().put("lifecycle:model", factory.createAttributeLiteral("lifecycle:model", "standard", extensionManager.getByPrefix("lifecycle")));
		log.getExtensions().add(extensionManager.getByPrefix("time"));
		log.getExtensions().add(extensionManager.getByPrefix("lifecycle"));
		log.getExtensions().add(extensionManager.getByPrefix("concept"));
		log.getClassifiers().add(new XEventNameClassifier());
		return log;
	}
}
