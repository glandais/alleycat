package org.glandais.alleycat;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class App {

	private static final File ADRESSES_JSON = new File("adresses.json");
	private static final File ROUTES_JSON = new File("routes.json");

	private static final String token = "58d904a497c67e00015b45fc54a69a1e8cb748fc7c455a1e2d5c4998";

	private static ObjectMapper om = new ObjectMapper();

	private static Map<String, Coordinates> adresseCache = new HashMap<>();
	private static Map<String, Integer> routeCache = new HashMap<>();

	static {
		om.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
		if (ADRESSES_JSON.exists()) {
			try {
				Map<String, Map> cache = om.readValue(ADRESSES_JSON, Map.class);
				cache.entrySet().forEach(e -> {
					Double lat = (Double) e.getValue().get("lat");
					Double lon = (Double) e.getValue().get("lon");
					adresseCache.put(e.getKey(), new Coordinates(lat, lon));
				});
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (ROUTES_JSON.exists()) {
			try {
				routeCache = om.readValue(ROUTES_JSON, Map.class);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static class Stamp {
		String name;
		String cp;
		List<String> previous = new ArrayList<>();

		public Stamp(String name, String cp) {
			super();
			this.name = name;
			this.cp = cp;
		}

	}

	private static class Coordinates {
		private double lat;
		private double lon;

		public Coordinates(double lat, double lon) {
			super();
			this.lat = lat;
			this.lon = lon;
		}

		public double getLat() {
			return lat;
		}

		public void setLat(double lat) {
			this.lat = lat;
		}

		public double getLon() {
			return lon;
		}

		public void setLon(double lon) {
			this.lon = lon;
		}

		@Override
		public String toString() {
			return lon + "," + lat;
		}
	}

	private static class Path implements Comparable<Path> {
		String checkpoints;
		List<String> stamps;
		List<String> steps;
		String itiSimple;
		int duration;

		@Override
		public int compareTo(Path o) {
			return Integer.compare(this.duration, o.duration);
		}

	}

	public static void main(String[] args) throws Exception {
		Map<String, String> adresses = new TreeMap<>();
		adresses.put("S", "Place des fonderies, Nantes");
		adresses.put("A", "Rue René Cassin, Rezé");
		adresses.put("B", "Rue du Landreau, Nantes");
		adresses.put("C", "Rue Louis David, Nantes");
		adresses.put("D", "Rue du Ranzay, Nantes");
		adresses.put("F", "2 Rue Bossuet, Nantes");

		Map<String, String> friendly = new TreeMap<>();
		friendly.put("S", "Fonderies");
		friendly.put("A", "Cyrus");
		friendly.put("B", "Doulon");
		friendly.put("C", "Procé");
		friendly.put("D", "Haluchère");
		friendly.put("F", "Caf K");

		Map<String, Stamp> stamps = new TreeMap<>();
		stamps.put("A1", new Stamp("A1", "A"));
		stamps.put("A2", new Stamp("A2", "A"));
		stamps.put("B1", new Stamp("B1", "B"));
		stamps.put("B2", new Stamp("B2", "B"));
		stamps.put("C1", new Stamp("C1", "C"));
		stamps.put("C2", new Stamp("C2", "C"));
		stamps.put("D", new Stamp("D", "D"));
		stamps.put("F", new Stamp("F", "F"));

		stamps.get("A1").previous.add("B1");
		stamps.get("F").previous.add("A1");
		stamps.get("D").previous.add("B1");
		stamps.get("C1").previous.add("D");
		stamps.get("A2").previous.add("C1");
		stamps.get("F").previous.add("A2");
		stamps.get("B2").previous.add("C2");
		stamps.get("F").previous.add("B2");

		List<String> result = resolve(adresses, friendly, stamps, new ArrayList<>());

		for (String string : result) {
			System.out.println(result);
		}
	}

	public static List<String> resolve(Map<String, String> adresses, Map<String, String> friendly,
			Map<String, Stamp> stamps, List<String> view) throws UnsupportedEncodingException, ClientProtocolException,
			IOException, JsonParseException, JsonMappingException, JsonGenerationException {
		Map<String, Coordinates> coords = new HashMap<>();
		for (Entry<String, String> adresse : adresses.entrySet()) {

			Coordinates coordinates = tryParse(adresse.getValue());
			if (coordinates == null) {
				coordinates = adresseCache.get(adresse.getValue());
				if (coordinates == null) {
					String param = URLEncoder.encode(adresse.getValue(), "UTF-8");
					String url = "https://api.openrouteservice.org/geocoding?query=" + param + "&api_key=" + token;
					System.out.println(url);
					String result = Request.Get(url).execute().returnContent().asString();
					Map map = om.readValue(result, Map.class);
					List features = (List) map.get("features");
					Map feature = (Map) features.get(0);
					Map geometry = (Map) feature.get("geometry");
					List<Double> coords2 = (List<Double>) geometry.get("coordinates");
					coordinates = new Coordinates(coords2.get(1), coords2.get(0));
					adresseCache.put(adresse.getValue(), coordinates);
					om.writeValue(ADRESSES_JSON, adresseCache);
				}
			}

			coords.put(adresse.getKey(), coordinates);

		}

		Map<String, Integer> durations;

		durations = computeDurations(coords);
		// durations = om.readValue(new File("durations.json"), Map.class);

		// key : checkpoints, value : stamps
		Map<String, List<String>> paths = new TreeMap<>();
		paths.put("S", new ArrayList<>());
		browsePaths("S", paths, stamps);

		List<Path> parsedPaths = new ArrayList<>();
		for (String path : paths.keySet()) {
			List<String> stampsGot = paths.get(path);
			if (stampsGot.size() == stamps.size() && path.endsWith("F")) {
				Path p = new Path();
				p.checkpoints = path;
				p.stamps = stampsGot;
				p.steps = new ArrayList<>();
				p.itiSimple = "";
				p.duration = 0;
				for (int i = 1; i < path.length(); i++) {
					char cp1 = path.charAt(i - 1);
					char cp2 = path.charAt(i);
					Integer d = durations.get(cp1 + "-" + cp2);
					String fcp1 = friendly.get("" + cp1);
					String fcp2 = friendly.get("" + cp2);
					p.duration = p.duration + d;
					int m = d / 60;
					String step = cp1 + " (" + fcp1 + ") -> " + cp2 + " (" + fcp2 + ") (" + m;
					if (cp2 != 'F') {
						p.duration = p.duration + 120;
						step = step + "+2";
					}
					p.steps.add(step + ")");
					if (p.itiSimple.length() == 0) {
						p.itiSimple = p.itiSimple + fcp1 + " -> " + fcp2;
					} else {
						p.itiSimple = p.itiSimple + " -> " + fcp2;
					}
				}
				parsedPaths.add(p);
			}
		}
		Collections.sort(parsedPaths);

		List<String> result = new ArrayList<>();
		if (parsedPaths.size() > 0) {
			result.add("");
			result.add("");
			result.add(parsedPaths.get(0).itiSimple);
			result.add(parsedPaths.get(0).stamps.stream().collect(Collectors.joining(", ")));

			view.add("var latlngs = [");
			String path = parsedPaths.get(0).checkpoints;
			for (int i = 0; i < path.length(); i++) {
				Coordinates coordinates = coords.get("" + path.charAt(i));

				String s = "[" + coordinates.lat + ", " + coordinates.lon + "]";
				if (i == path.length() - 1) {
					view.add(s);
				} else {
					view.add(s + ",");
				}

			}

			view.add("];");
			view.add("var polyline = L.polyline(latlngs, {color: 'red'}).addTo(mymap);");

			result.add("");
			result.add("");
		}

		result.add("-- Adresses : ");
		for (Entry<String, String> e : adresses.entrySet()) {
			result.add(e.getKey() + " : " + friendly.get(e.getKey()) + " (" + e.getValue() + ")");
		}
		result.add("-- Tampons :");
		for (Entry<String, Stamp> stamp : stamps.entrySet()) {
			String required;
			if (stamp.getValue().previous.size() == 0) {
				required = "aucun";
			} else {
				required = stamp.getValue().previous.stream().collect(Collectors.joining(", "));
			}
			result.add(stamp.getValue().name + " (CP " + stamp.getValue().cp + " - " + friendly.get(stamp.getValue().cp)
					+ "), tampons nécessaires : " + required);
		}
		result.add("-- Routes :");
		int i = 1;
		for (Path path : parsedPaths) {
			result.add("-- Route n°" + i);
			i++;
			result.add("Durée : " + (path.duration / 60) + " minutes");
			result.add("Route : " + path.itiSimple);
			result.add("Etapes :");
			for (String step : path.steps) {
				result.add("  " + step);
			}
			// result.add("Checkpoints : " + path.checkpoints);
			result.add("Tampons : " + path.stamps.stream().collect(Collectors.joining(", ")));
		}
		return result;
	}

	private static Coordinates tryParse(String value) {
		String[] split = value.split(",");
		if (split.length == 2) {
			try {
				Double lat = Double.parseDouble(split[0]);
				Double lng = Double.parseDouble(split[1]);
				return new Coordinates(lat, lng);
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}

	public static Map<String, Integer> computeDurations(Map<String, Coordinates> coords)
			throws UnsupportedEncodingException, ClientProtocolException, IOException, JsonParseException,
			JsonMappingException, JsonGenerationException {
		Map<String, Integer> durations = new HashMap<>();
		List<Map.Entry<String, Coordinates>> mapCoords = new ArrayList<>(coords.entrySet());
		for (int i = 0; i < mapCoords.size(); i++) {
			for (int j = 0; j < mapCoords.size(); j++) {
				if (i != j) {
					Entry<String, Coordinates> from = mapCoords.get(i);
					Entry<String, Coordinates> to = mapCoords.get(j);
					String coordinates = from.getValue().toString() + "|" + to.getValue().toString();
					coordinates = URLEncoder.encode(coordinates, "UTF-8");

					Integer duration = routeCache.get(coordinates);
					if (duration == null) {
						String directionsUrl = "https://api.openrouteservice.org/directions?profile=cycling-road&geometry=false&instructions=false&coordinates="
								+ coordinates + "&api_key=" + token;
						System.out.println(directionsUrl);
						String result = Request.Get(directionsUrl).execute().returnContent().asString();
						Map map = om.readValue(result, Map.class);
						List routes = (List) map.get("routes");
						Map route = (Map) routes.get(0);
						Map summary = (Map) route.get("summary");
						duration = ((Number) summary.get("duration")).intValue();
						routeCache.put(coordinates, duration);
						om.writeValue(ROUTES_JSON, routeCache);
					}
					durations.put(from.getKey() + "-" + to.getKey(), duration);
				}
			}
		}
		om.writeValue(new File("durations.json"), durations);
		return durations;
	}

	private static void browsePaths(String path, Map<String, List<String>> paths, Map<String, Stamp> stamps) {
		List<String> curStamps = paths.get(path);
		for (Entry<String, Stamp> e : stamps.entrySet()) {
			if (!curStamps.contains(e.getKey()) && curStamps.containsAll(e.getValue().previous)) {
				String cp = e.getValue().cp;
				String path2 = path + cp;
				if (!paths.containsKey(path2)) {
					List<String> curStamps2 = new ArrayList<>(curStamps);

					for (Entry<String, Stamp> e2 : stamps.entrySet()) {
						if (e2.getValue().cp.equals(cp) && !curStamps.contains(e2.getKey())
								&& curStamps.containsAll(e2.getValue().previous)) {
							curStamps2.add(e2.getKey());
						}
					}
					// curStamps2.add(e.getKey());

					paths.put(path2, curStamps2);
					browsePaths(path2, paths, stamps);
				}
			}
		}
	}
}
