package gama.extension.GTFS.gaml.skills;

import gama.annotations.skill;
import gama.annotations.doc;
import gama.annotations.getter;
import gama.annotations.setter;
import gama.annotations.variable;
import gama.annotations.vars;

import gama.api.gaml.types.IType;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.skill.Skill;

/**
 * The skill TransportTripSkill for managing individual transport trips in GAMA.
 * This skill manages attributes like tripId, routeId, stopsInOrder, destination, and stopDetails.
 */
@skill(name = "TransportTripSkill", doc = @doc("Skill for agents that represent individual transport trips with attributes like tripId, routeId, stopsInOrder, destination, and stopDetails."))
@vars({
    @variable(name = "tripId", type = IType.STRING, doc = @doc("The unique identifier of the transport trip.")),
    @variable(name = "routeId", type = IType.STRING, doc = @doc("The unique identifier of the route associated with the trip.")),
    @variable(name = "routeType", type = IType.INT, doc = @doc("The type of transport associated with this trip (bus, tram, metro, etc.).")),
    @variable(name = "shapeId", type = IType.STRING, doc = @doc("The unique indentifier of shape"))
})
public class TransportTripSkill extends Skill {

    // Getter and setter for tripId
    @getter("tripId")
    public String getTripId(final IAgent agent) {
        return (String) agent.getAttribute("tripId");
    }

    @setter("tripId")
    public void setTripId(final IAgent agent, final String tripId) {
        agent.setAttribute("tripId", tripId);
    }

    // Getter and setter for shapeId
    @getter("shapeId")
    public String getShapeId(final IAgent agent) {
        return (String) agent.getAttribute("shapeId");
    }
    
    @setter("shapeId")
    public void setShapeId(final IAgent agent, final String shapeId) {
        agent.setAttribute("shapeId", shapeId);
    }
    

    @setter("routeId")
    public void setRouteId(final IAgent agent, final String routeId) {
        agent.setAttribute("routeId", routeId);
    }
    // Getter and setter for routeId
    @getter("routeId")
    public String getRouteId(final IAgent agent) {
        return (String) agent.getAttribute("routeId");
    }

 // Getter and setter for routeType
    @getter("routeType")
    public int getRouteType(final IAgent agent) {
        return (Integer) agent.getAttribute("routeType");
    }

    @setter("routeType")
    public void setRouteType(final IAgent agent, final int routeType) {
        agent.setAttribute("routeType", routeType);
    }
}
