package gama.extension.GTFS.gaml;

import gama.api.gaml.statements.IStatement;
import gama.api.gaml.statements.IStatement.Create;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.agent.IPopulation;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.IList;
import java.util.List;
import java.util.Map;

public interface GTFSAgentCreator {
    void addInits(IScope scope, List<Map<String, Object>> inits, Integer max);
    IList<? extends IAgent> createAgents(IScope scope, IPopulation<? extends IAgent> population, List<Map<String, Object>> inits, Create statement, IStatement sequence);
    boolean handlesCreation();
}
