package gama.extension.GTFS.skills;

import gama.annotations.skill;
import gama.annotations.variable;
import gama.annotations.vars;
import gama.annotations.doc;
import gama.annotations.getter;
import gama.annotations.setter;
import gama.api.gaml.types.IType;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.skill.Skill;

@skill(name = "TransportRouteSkill", doc = @doc("Skill for agents that represent transport routes with attributes like routeId, shortName, longName, and type."))
@vars({
    @variable(name = "routeId", type = IType.STRING, doc = @doc("The ID of the transport route.")),
    @variable(name = "shortName", type = IType.STRING, doc = @doc("The short name of the transport route.")),
    @variable(name = "longName", type = IType.STRING, doc = @doc("The long name of the transport route.")),
    @variable(name = "type", type = IType.INT, doc = @doc("The type of the transport route (e.g., bus, tram, etc.)."))
})
public class TransportRouteSkill extends Skill {

    @getter("routeId")
    public String getRouteId(final IAgent agent) {
        return (String) agent.getAttribute("routeId");
    }

    @setter("routeId")
    public void setRouteId(final IAgent agent, final String routeId) {
        agent.setAttribute("routeId", routeId);
    }

    @getter("shortName")
    public String getShortName(final IAgent agent) {
        return (String) agent.getAttribute("shortName");
    }

    @setter("shortName")
    public void setShortName(final IAgent agent, final String shortName) {
        agent.setAttribute("shortName", shortName);
    }

    @getter("longName")
    public String getLongName(final IAgent agent) {
        return (String) agent.getAttribute("longName");
    }

    @setter("longName")
    public void setLongName(final IAgent agent, final String longName) {
        agent.setAttribute("longName", longName);
    }

    @getter("type")
    public int getType(final IAgent agent) {
        return (int) agent.getAttribute("type");
    }

    @setter("type")
    public void setType(final IAgent agent, final int type) {
        agent.setAttribute("type", type);
    }
}
