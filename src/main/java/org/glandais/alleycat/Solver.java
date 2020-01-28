package org.glandais.alleycat;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.glandais.alleycat.App.Stamp;

public class Solver {

	public String solve(String problem, List<String> view) {
		Map<String, String> adresses = new TreeMap<>();
		Map<String, String> friendly = new TreeMap<>();
		Map<String, Stamp> stamps = new TreeMap<>();

		Scanner scanner = new Scanner(problem);
		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();
			if (line.contains(":")) {
				int p = line.indexOf(':');
				String cp = line.substring(0, p);
				String loc = line.substring(p + 1);
				adresses.put(cp, loc);
			} else if (line.contains(">")) {
				int p = line.indexOf('>');
				Stamp prevSt = getStamp(line.substring(0, p), stamps);
				Stamp nextSt = getStamp(line.substring(p + 1), stamps);
				nextSt.previous.add(prevSt.name);
			} else if (line.contains("=")) {
				int p = line.indexOf('=');
				String cp = line.substring(0, p);
				String fr = line.substring(p + 1);
				friendly.put(cp, fr);
			}
		}
		scanner.close();

		adresses.keySet().forEach(k -> {
			if (!friendly.containsKey(k)) {
				friendly.put(k, adresses.get(k));
			}
		});

		try {
			List<String> resolve = App.resolve(adresses, friendly, stamps, view);
			return resolve.stream().collect(Collectors.joining("\r\n"));
		} catch (Exception e) {
			e.printStackTrace();
			return e.toString();
		}

	}

	private Stamp getStamp(String st, Map<String, Stamp> stamps) {
		Stamp stamp = stamps.get(st);
		if (stamp == null) {
			stamp = new Stamp(st, st.substring(0, 1));
			stamps.put(st, stamp);
		}
		return stamp;
	}
}
