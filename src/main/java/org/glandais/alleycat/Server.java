package org.glandais.alleycat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD {

	public Server() throws IOException {
		super(18080);
		start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
	}

	public static void main(String[] args) {
		try {
			new Server();
		} catch (IOException ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
		}
	}

	@Override
	public Response serve(IHTTPSession session) {
		try {
			String template = new String(Files.readAllBytes(Paths.get("template.html")));
			session.parseBody(new HashMap<String, String>());
			Map<String, String> parms = session.getParms();
			String problem = parms.get("problem");
			String solution = "";
			List<String> view = new ArrayList<>();
			view.add("function viewSolution() {");
			if (problem != null) {
				solution = new Solver().solve(problem, view);
				System.out.println("problem : " + problem);
				System.out.println("solution : " + solution);
			} else {
				problem = "A=Butte Sainte Anne\n" + "A:47.201637,-1.577545\n" + "\n" + "C=Chantenay\n"
						+ "C:47.197235,-1.594598\n" + "\n" + "P=Polo\n" + "P:47.206279,-1.568839\n" + "\n"
						+ "B=Basket\n" + "B:47.206623,-1.565723\n" + "\n" + "J=Jardin des 5 sens\n"
						+ "J:47.207327,-1.533319\n" + "\n" + "S=Manifeste\n" + "S:47.219669,-1.538194\n" + "\n"
						+ "F=Caf K\n" + "F:2 Rue Bossuet, Nantes\n" + "\n" + "A1>F\n" + "B1>J1\n" + "J1>F\n" + "P>F\n"
						+ "J2>B2\n" + "B2>F\n" + "J2>C\n" + "C>A2\n" + "A2>F\n";
			}
			view.add("}");
			template = template.replace("${problem}", problem).replace("${solution}", solution).replace("${view}",
					view.stream().collect(Collectors.joining("\r\n")));
			return newFixedLengthResponse(template);
		} catch (Exception e) {
			return newFixedLengthResponse("ERROR");

		}
	}
}
