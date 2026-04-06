package gama.extension.GTFS.skills;

import gama.annotations.action;
import gama.annotations.getter;
import gama.annotations.variable;
import gama.annotations.vars;
import gama.annotations.skill;
import gama.annotations.doc;

import gama.api.gaml.types.IType;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.skill.Skill;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.api.types.map.IMap;
import gama.api.types.pair.IPair;

/**
 * Skill for managing individual transport stops. Provides access to stopId, stopName,
 * routeType, and detailed departure information for each stop using the departureStopsInfo structure.
 */
@skill(name = "TransportStopSkill", doc = @doc("Skill for agents representing transport stops. Manages stop details, routeType, departure information, and shape distances."))
@vars({
    @variable(name = "stopId", type = IType.STRING, doc = @doc("The unique ID of the transport stop.")),
    @variable(name = "stopName", type = IType.STRING, doc = @doc("The name of the transport stop.")),
    @variable(name = "routeType", type = IType.INT, doc = @doc("The type of transport route associated with the stop.")),
    @variable(name = "departureStopsInfo", type = IType.MAP, doc = @doc("Map where keys are trip IDs and values are lists of GamaPair<IAgent, String> (stop agent and departure time).")),
    @variable(name = "tripShapeMap", type = IType.MAP, doc = @doc("Map where keys are trip IDs and values are shape IDs.")),
    @variable(name = "tripNumber", type = IType.INT, doc = @doc("Number of trips starting from this stop."))
})
public class TransportStopSkill extends Skill {

    // Getter for stopId
    @getter("stopId")
    public String getStopId(final IAgent agent) {
        return (String) agent.getAttribute("stopId");
    }

    // Getter for stopName
    @getter("stopName")
    public String getStopName(final IAgent agent) {
        return (String) agent.getAttribute("stopName");
    }

    // Getter for routeType
    @getter("routeType")
    public int getRouteType(final IAgent agent) {
        return (int) agent.getAttribute("routeType");
    }

    // Getter for departureStopsInfo
    @SuppressWarnings("unchecked")
    @getter("departureStopsInfo")
    public IMap<String, IList<IPair<IAgent, String>>> getDepartureStopsInfo(final IAgent agent) {
        return (IMap<String, IList<IPair<IAgent, String>>>) agent.getAttribute("departureStopsInfo");
    }
    
    @getter("tripNumber")
    public int getTripNumber(final IAgent agent) {
        return (int) agent.getAttribute("tripNumber");
    }

    // Getter for tripShapeMap
    @SuppressWarnings("unchecked")
    @getter("tripShapeMap")
    public IMap<String, Integer> tripShapeMap(final IAgent agent) {
        return (IMap<String, Integer>) agent.getAttribute("tripShapeMap");
    }


    // Action to check if departureStopsInfo is not empty
    @action(name = "isDeparture")
    public boolean isDeparture(final IScope scope) {
        IAgent agent = scope.getAgent();
        @SuppressWarnings("unchecked")
        IMap<String, IList<IPair<IAgent, String>>> departureStopsInfo =
                (IMap<String, IList<IPair<IAgent, String>>>) agent.getAttribute("departureStopsInfo");

        return departureStopsInfo != null && !departureStopsInfo.isEmpty();
    }

    // Retrieve departure stop agents for a specific trip
    @getter("agentsForTrip")
    public IList<IAgent> getAgentsForTrip(final IAgent agent, final String tripId) {
        IMap<String, IList<IPair<IAgent, String>>> departureStopsInfo = getDepartureStopsInfo(agent);
        if (departureStopsInfo == null || !departureStopsInfo.containsKey(tripId)) {
            System.err.println("[ERROR] No trip info found for tripId=" + tripId + " at stopId=" + getStopId(agent));
            return GamaListFactory.create();
        }
        IList<IPair<IAgent, String>> stopPairs = departureStopsInfo.get(tripId);
        IList<IAgent> agentsList = GamaListFactory.create();
        for (IPair<IAgent, String> pair : stopPairs) {
            agentsList.add(pair.key());
        }
        return agentsList;
    }
}
