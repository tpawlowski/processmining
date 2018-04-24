package org.example;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.processmining.models.graphbased.directed.petrinet.Petrinet;
import org.processmining.models.graphbased.directed.petrinet.elements.Place;
import org.processmining.models.graphbased.directed.petrinet.elements.Transition;
import org.processmining.models.graphbased.directed.petrinet.impl.PetrinetFactory;

@DefaultCoder(SerializableCoder.class)
public class EventRelationMatrix implements Serializable {
	private static final long serialVersionUID = -1372245237575805723L;

	private HashSet<String> startingEvents;
	private HashSet<String> endEvents;
	private HashSet<String> events;
	private HashSet<EventRelation> relation;
	
	public EventRelationMatrix() {
		this.relation = new HashSet<EventRelation>();
		this.startingEvents = new HashSet<String>();
		this.endEvents = new HashSet<String>();
		this.events = new HashSet<String>();
	}
	
	public EventRelationMatrix(Iterable<LogEntry> entries) {
		this();
		this.addLogEntries(entries);
	}
	
	private void addLogEntries(Iterable<LogEntry> entries) {
		String previous = null;
		for (LogEntry entry : entries) {
			this.events.add(entry.getEventId());
			if (previous != null) {
				this.relation.add(new EventRelation(previous, entry.getEventId()));
			} else {
				this.startingEvents.add(entry.getEventId());
			}
			previous = entry.getEventId();
		}
		if (previous != null) {
			this.endEvents.add(previous);
		}
	}
	
	private void merge(EventRelationMatrix second) {
		relation.addAll(second.relation);
		startingEvents.addAll(second.startingEvents);
		endEvents.addAll(second.endEvents);
		events.addAll(second.events);
	}
	
	public static EventRelationMatrix join(Iterable<EventRelationMatrix> values) {
		EventRelationMatrix relation  = new EventRelationMatrix();
		
		for (EventRelationMatrix value : values) {
			relation.merge(value);
		}
		
		return relation;
	}
	
	public Petrinet mine() {
		Petrinet net = PetrinetFactory.newPetrinet("Petri Net");
		
		HashMap<String, Transition> transitions = addTransitions(net);
		addPlacesAndArcs(net, transitions, new LinkedList<String>(), new LinkedList<String>(), 
				new LinkedList<String>(events), new LinkedList<String>(events));
		
		Place input = net.addPlace("start");
		for (String event : startingEvents) {
			net.addArc(input, transitions.get(event));
		}
		
		Place output = net.addPlace("end");
		for (String event : endEvents) {
			net.addArc(transitions.get(event), output);
		}
		
		return net;
	}
	
	private HashMap<String, Transition> addTransitions(Petrinet net) {
		HashMap<String, Transition> transitions = new HashMap<String, Transition>();
		for (String event : this.events) {
			transitions.put(event, net.addTransition(event));
		}
		return transitions;
	}
	
	private void addPlacesAndArcs(Petrinet net, HashMap<String, Transition> transitions, LinkedList<String> a, LinkedList<String> b, List<String> ma, List<String> mb) {
		for (String event : ma) {
			a.add(event);
			List<String> nma = independent(ma, event);
			List<String> nmb = children(mb, event);
			
			if (nmb.isEmpty() && b.isEmpty()) {
			} else if (nma.isEmpty() && nmb.isEmpty()) {
				addPlace(net, transitions, a, b);
			} else {
				addPlacesAndArcs(net, transitions, a, b, nma, nmb);
			}
			
			a.removeLast();
		}
		for (String event : mb) {
			b.add(event);
			List<String> nmb = independent(mb, event);
			List<String> nma = parents(ma, event);
			
			if (nma.isEmpty() && a.isEmpty()) {
			} else if (nma.isEmpty() && nmb.isEmpty()) {
				addPlace(net, transitions, a, b);
			} else {
				addPlacesAndArcs(net, transitions, a, b, nma, nmb);
			}
			b.removeLast();
		}
	}
	
	private void addPlace(Petrinet net, HashMap<String, Transition> transitions, LinkedList<String> a, LinkedList<String> b) {
		Place p = net.addPlace("");
		for (String event : a) {
			net.addArc(transitions.get(event), p);
		}
		for (String event : b) {
			net.addArc(p, transitions.get(event));
		}
	}
	
	private List<String> independent(List<String> list, String event) {
		List<String> output = new LinkedList<String>(list);
		output.removeIf((element) -> {
			return element == event || contains(element, event) || contains(event, element);
		});
		return output;
	}
	
	private List<String> children(List<String> list, String event) {
		List<String> output = new LinkedList<String>(list);
		output.removeIf((element) -> {
			return element == event || contains(element, event) || !contains(event, element);
		});
		return output;
	}
	
	private List<String> parents(List<String> list, String event) {
		List<String> output = new LinkedList<String>(list);
		output.removeIf((element) -> {
			return element == event || !contains(element, event) || contains(event, element);
		});
		return output;
	}
	
	private boolean contains(String event_one, String event_two) {
		return relation.contains(new EventRelation(event_one, event_two));
	}
}
