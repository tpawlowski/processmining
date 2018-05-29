package org.example;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.beam.sdk.coders.DefaultCoder;
import org.apache.beam.sdk.coders.SerializableCoder;
import org.apache.logging.log4j.util.Strings;
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
		int i = 0;
		for (LogEntry entry : entries) {
			this.events.add(entry.getEventId());
			if (previous != null) {
				this.relation.add(new EventRelation(previous, entry.getEventId()));
			} else {
				this.startingEvents.add(entry.getEventId());
			}
			previous = entry.getEventId();
			i++;
		}
		if (previous != null) {
			this.endEvents.add(previous);
		}
		System.out.println("Init matrix with " + Integer.toString(i) + " entries");
	}
	
	private void merge(EventRelationMatrix second) {
		relation.addAll(second.relation);
		startingEvents.addAll(second.startingEvents);
		endEvents.addAll(second.endEvents);
		events.addAll(second.events);
	}
	
	public static EventRelationMatrix join(Iterable<EventRelationMatrix> values) {
		EventRelationMatrix relation  = new EventRelationMatrix();
		
		int i = 0;
		for (EventRelationMatrix value : values) {
			relation.merge(value);
			i += 1;
		}
		System.out.println("Merged " + Integer.toString(i) + " matrices. Relation size: " + Integer.toString(relation.relation.size()) + " different events: " + Integer.toString(relation.events.size()));
		
		return relation;
	}
	
	public Petrinet mine() {
		Petrinet net = PetrinetFactory.newPetrinet("Petri Net");
		
		HashMap<String, Transition> transitions = addTransitions(net);
		addPlacesAndArcs(net, transitions, new LinkedList<String>(), new LinkedList<String>(), 
				new LinkedList<String>(events), new LinkedList<String>(events), new LinkedList<String>(), new LinkedList<String>());
		
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
			System.out.println("Add transition " + event);
		}
		return transitions;
	}
	
	private void addPlacesAndArcs(Petrinet net, HashMap<String, Transition> transitions, 
			LinkedList<String> a, LinkedList<String> b, 
			LinkedList<String> ma, LinkedList<String> mb, // which elements can be included
			LinkedList<String> fa, LinkedList<String> fb) { // which elements should not be included
		if (ma.size() > 0) {
			String event = ma.removeFirst();
			// event is included in a
			a.add(event);
			LinkedList<String> nma = independent(ma, event);
			LinkedList<String> nfa = independent(fa, event);
			LinkedList<String> nmb = children(mb, event);
			LinkedList<String> nfb = children(fb, event);

			if (nmb.isEmpty() && b.isEmpty()) {
			} else if (nma.isEmpty() && nmb.isEmpty() && nfa.isEmpty() && nfb.isEmpty()) {
				addPlace(net, transitions, a, b);
			} else {
				addPlacesAndArcs(net, transitions, a, b, nma, nmb, nfa, nfb);
			}
			a.removeLast();

			// event is not included in a
			fa.add(event);
			addPlacesAndArcs(net, transitions, a, b, ma, mb, fa, fb);
		} else if 	(mb.size() > 0){
			String event = mb.removeFirst();
			// event is included in b

			b.add(event);
			LinkedList<String> nmb = independent(mb, event);
			LinkedList<String> nfb = independent(fb, event);
			LinkedList<String> nma = parents(ma, event);
			LinkedList<String> nfa = parents(fa, event);
			
			if (nma.isEmpty() && a.isEmpty()) {
			} else if (nma.isEmpty() && nmb.isEmpty() && nfa.isEmpty() && nfb.isEmpty()) {
				addPlace(net, transitions, a, b);
			} else {
				addPlacesAndArcs(net, transitions, a, b, nma, nmb, nfa, nfb);
			}
			b.removeLast();

			fb.add(event);
			addPlacesAndArcs(net, transitions, a, b, ma, mb, fa, fb);
		}
	}
	
	private void addPlace(Petrinet net, HashMap<String, Transition> transitions, LinkedList<String> a, LinkedList<String> b) {
		System.out.println("Add place " + Strings.join(a, ',') + " " + Strings.join(b, ','));
		Place p = net.addPlace("([" + Strings.join(a, ',')  + "], [" + Strings.join(b, ',')  + "])" );
		for (String event : a) {
			net.addArc(transitions.get(event), p, 1);
		}
		for (String event : b) {
			net.addArc(p, transitions.get(event), 1);
		}
	}
	
	private LinkedList<String> independent(List<String> list, String event) {
		LinkedList<String> output = new LinkedList<String>(list);
		output.removeIf((element) -> {
			return element == event || contains(element, event) || contains(event, element);
		});
		return output;
	}
	
	private LinkedList<String> children(List<String> list, String event) {
		LinkedList<String> output = new LinkedList<String>(list);
		output.removeIf((element) -> {
			return element == event || contains(element, event) || !contains(event, element);
		});
		return output;
	}
	
	private LinkedList<String> parents(List<String> list, String event) {
		LinkedList<String> output = new LinkedList<String>(list);
		output.removeIf((element) -> {
			return element == event || !contains(element, event) || contains(event, element);
		});
		return output;
	}
	
	private boolean contains(String event_one, String event_two) {
		return relation.contains(new EventRelation(event_one, event_two));
	}
}
