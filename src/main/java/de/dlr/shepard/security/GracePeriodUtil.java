package de.dlr.shepard.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class Fetch<T> {
	Date lastSeen;
	T element;
}

public class GracePeriodUtil<T> {

	private final Map<String, Fetch<T>> lastSeen;
	private final int period;

	public GracePeriodUtil(int period) {
		this.period = period;
		lastSeen = new HashMap<String, Fetch<T>>();
	}

	public boolean elementIsKnown(String key) {
		if (!lastSeen.containsKey(key))
			return false;

		var threshold = new Date(System.currentTimeMillis() - period);
		return lastSeen.get(key).lastSeen.after(threshold);
	}

	public void elementSeen(String key, T value) {
		lastSeen.put(key, new Fetch<T>(new Date(), value));
	}

	public T getValue(String key) {
		return lastSeen.get(key).element;
	}
}
