package de.dlr.shepard.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class GracePeriodUtil {

	private final Map<String, Date> lastSeen;
	private final int period;

	public GracePeriodUtil(int period) {
		this.period = period;
		lastSeen = new HashMap<>();
	}

	public boolean elementIsKnown(String key) {
		if (!lastSeen.containsKey(key))
			return false;

		var threshold = new Date(System.currentTimeMillis() - period);
		return lastSeen.get(key).after(threshold);
	}

	public void elementSeen(String key) {
		lastSeen.put(key, new Date());
	}

}
