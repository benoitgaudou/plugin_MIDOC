package gama.extension.GTFS.gaml.statement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import gama.api.gaml.statements.IStatement;
import gama.api.gaml.statements.IStatement.Create;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.agent.IPopulation;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.IList;
import gama.extension.GTFS.gaml.file.object.TransportTrip;

public class TransportTripCreator implements GTFSAgentCreator {
	

	private List<TransportTrip> trips;
	
	public TransportTripCreator(List<TransportTrip> trips) {
		this.trips = (trips != null) ? trips : new ArrayList<>();
	}


	@Override
	public void addInits(IScope scope, List<Map<String, Object>> inits, Integer max) {
	    int limit = (max != null) ? Math.min(max, trips.size()) : trips.size();

	    for (int i = 0; i < limit; i++) {
	        TransportTrip trip = trips.get(i);
//	        System.out.println("[DEBUG] Creating agent for tripId=" + trip.getTripId() + " with shapeId=" + trip.getShapeId());
	        Map<String, Object> tripInit = new HashMap<>();
	        tripInit.put("tripId", trip.getTripId());
	        tripInit.put("routeId", trip.getRouteId());
	        tripInit.put("shapeId", trip.getShapeId());
	        tripInit.put("routeType", trip.getRouteType());
	        inits.add(tripInit);
	    }
	}

	@Override
	public boolean handlesCreation() {
	
		return true;
	}

	@Override
	public IList<? extends IAgent> createAgents(IScope scope, IPopulation<? extends IAgent> population,
			List<Map<String, Object>> inits, Create statement, IStatement sequence) {
		
		return population.createAgents(scope, inits.size(), inits, false, true);
	}

}
