package gama.extension.GTFS.gaml.statement;

import gama.api.additions.delegates.ICreateDelegate;
import gama.api.gaml.expressions.IExpression;
import gama.api.gaml.statements.IStatement;
import gama.api.gaml.symbols.Arguments;
import gama.api.gaml.types.Cast;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.agent.IPopulation;
import gama.api.kernel.species.ISpecies;
import gama.api.runtime.scope.IScope;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.extension.GTFS.gaml.file.GamaGTFSFile;

import java.util.List;
import java.util.Map;

/**
 * Class responsible for creating GTFS agents by delegating their creation to specific classes..
 */
public class CreateAgentsFromGTFS implements ICreateDelegate {
	
	private GTFSAgentCreator agentCreator;

	 @Override
	    public boolean handlesCreation() {
	        return agentCreator != null && agentCreator.handlesCreation();
	    }

    @Override
    public boolean acceptSource(IScope scope, Object source) {
        return source instanceof GamaGTFSFile;
    }

    @Override
    public boolean createFrom(IScope scope, List<Map<String, Object>> inits, Integer max, Object source, Arguments init, IStatement.Create statement) {

        GamaGTFSFile gtfsReader = (GamaGTFSFile) source;
        IExpression speciesExpr = statement.getFacet("species");
        ISpecies targetSpecies = Cast.asSpecies(scope, speciesExpr.value(scope));

        if (targetSpecies == null) {
            scope.getGui().getConsole().informConsole("No species specified", scope.getSimulation());
            return false;
        }

        IPopulation<? extends IAgent> population = scope.getSimulation().getPopulationFor(targetSpecies);
        if (population == null) {
            System.err.println("[ERROR] Population not found for species: " + targetSpecies.getName());
            return false;
        }

        // Select the appropriate class to handle the creation of agents
        agentCreator = getAgentCreator(scope, targetSpecies, gtfsReader);
        if (agentCreator == null) {
            scope.getGui().getConsole().informConsole("Unrecognized skill", scope.getSimulation());
            return false;
        }

     // Generation of initializations
        agentCreator.addInits(scope, inits, max);
     // Exporter les routes en .shp
//        try {
//            List<TransportShape> shapes = gtfsReader.getShapes();
//            System.out.println(">>> Appel exportShapesToShapefile avec " + shapes.size() + " shapes");
//            GeoToolsShapeExporter.exportShapesToShapefile(shapes, "C:/Users/tiend/Desktop/Formation");
//            System.out.println(">>> Export shapefile terminé !");
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        return true;
    }

    @Override
    public IType<?> fromFacetType() {
        return Types.FILE;
    }

    @Override
    public IList<? extends IAgent> createAgents(IScope scope, IPopulation<? extends IAgent> population, List<Map<String, Object>> inits, IStatement.Create statement, IStatement sequence) {

    	if (inits.isEmpty()) {
            System.out.println("[INFO] No agents to create.");
            return GamaListFactory.create(Types.AGENT); 
        }
        
        System.out.println("[DEBUG] Checking trip inits before creating agents...");
        
        IList<? extends IAgent> createdAgents = agentCreator.createAgents(scope, population, inits, statement, sequence);
        IList<IAgent> agentList = GamaListFactory.create(Types.AGENT);
        agentList.addAll(createdAgents); 
        
        System.out.println("[DEBUG] Created " + agentList.size() + " agents.");
        
        return agentList;
    }

    
   


    /**
     * Selects the appropriate agent creation handler based on the species type.
     */
    private GTFSAgentCreator getAgentCreator(IScope scope, ISpecies species, GamaGTFSFile gtfsReader) {
        if (species.implementsSkill("TransportStopSkill")) {
            return new TransportStopCreator(gtfsReader != null ? gtfsReader.getStops() : null);
        } else if (species.implementsSkill("TransportShapeSkill")) {
            return new TransportShapeCreator(gtfsReader != null ? gtfsReader.getShapes(scope) : null);
        } else if (species.implementsSkill("TransportTripSkill")) {
            return new TransportTripCreator(gtfsReader != null ? gtfsReader.getTrips() : null);
        }
        return null;
    }

}
