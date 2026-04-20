package gama.extension.GTFS.gaml.statement;

import gama.api.gaml.statements.IStatement;
import gama.api.gaml.statements.IStatement.Create;
import gama.api.gaml.types.Types;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.agent.IPopulation;
import gama.api.runtime.scope.IScope;
import gama.api.types.date.IDate;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.extension.GTFS.gaml.file.object.TransportStop;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransportStopCreator implements GTFSAgentCreator {
    
    private List<TransportStop> stops;

    public TransportStopCreator(List<TransportStop> stops) {
        this.stops = stops;
    }

    @Override
    public void addInits(IScope scope, List<Map<String, Object>> inits, Integer max) {
        int limit = (max != null) ? Math.min(max, stops.size()) : stops.size();

        for (int i = 0; i < limit; i++) {
            TransportStop stop = stops.get(i);
            Map<String, Object> stopInit = new HashMap<>();
            stopInit.put("stopId", stop.getStopId());
            stopInit.put("stopName", stop.getStopName());
            stopInit.put("location", stop.getLocation());
            stopInit.put("routeType", stop.getRouteType());
            stopInit.put("departureTripsInfo", stop.getDepartureTripsInfo());
            stopInit.put("tripShapeMap", stop.getTripShapeMap());
            stopInit.put("name", stop.getStopName());
            stopInit.put("tripNumber", stop.getTripNumber()); 
            inits.add(stopInit);
        }
    }

    @SuppressWarnings("unchecked")
	@Override  
    public IList<? extends IAgent> createAgents(IScope scope, IPopulation<? extends IAgent> population, List<Map<String, Object>> inits, Create statement, IStatement sequence) {

    	IList<? extends IAgent> createdAgents = population.createAgents(scope, inits.size(), inits, false, true);

        IMap<String, IAgent> stopIdToAgentMap = GamaMapFactory.create(Types.STRING, Types.AGENT);

        for (IAgent agent : createdAgents) {
            String stopId = (String) agent.getAttribute("stopId");
            stopIdToAgentMap.put(stopId, agent);
        }

        for (IAgent agent : createdAgents) {
            IMap<String, IMap<String, IDate>> departureTripsInfo =
                    (IMap<String, IMap<String, IDate>>) agent.getAttribute("departureTripsInfo");

            if (departureTripsInfo == null || departureTripsInfo.isEmpty()) {
                continue;
            }

            IMap<String, IMap<IAgent, IDate>> departureStopsInfo = GamaMapFactory.create(Types.STRING, Types.LIST);

            for (Map.Entry<String, IMap<String, IDate>> entry : departureTripsInfo.entrySet()) {
                IMap<IAgent, IDate> convertedStops = GamaMapFactory.create(Types.STRING, Types.DATE);
                for (String keyOfMapPairs : entry.getValue().getKeys()) {
                    IAgent stopAgent = stopIdToAgentMap.get(keyOfMapPairs);
                    if (stopAgent != null) {
             //           convertedStops.add(GamaPairFactory.createWith(stopAgent, pair.value(), Types.AGENT, Types.STRING));
                    	convertedStops.put(stopAgent, entry.getValue().get(keyOfMapPairs));

                    }
                }
                departureStopsInfo.put(entry.getKey(), convertedStops);
            }

            agent.setAttribute("departureStopsInfo", departureStopsInfo);

            // Important : departureShapeDistances n'a pas besoin de conversion, donc on le laisse comme il est
            // (déjà chargé dans addInits)
        }

        return createdAgents;
    }

    @Override
    public boolean handlesCreation() {
        return true;
    }
}
