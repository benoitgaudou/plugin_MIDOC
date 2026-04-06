package gama.extension.GTFS.gaml;

import gama.annotations.constants.IKeyword;
import gama.api.gaml.statements.IStatement;
import gama.api.gaml.statements.IStatement.Create;
import gama.api.kernel.agent.IAgent;
import gama.api.kernel.agent.IPopulation;
import gama.api.runtime.scope.IScope;
import gama.api.types.geometry.IShape;
import gama.api.types.list.IList;
import gama.extension.GTFS.TransportShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransportShapeCreator implements GTFSAgentCreator {
	
	private List<TransportShape> shapes;

	/** Constructor with a list of shapes **/
	public TransportShapeCreator(List<TransportShape> shapes) {
		this.shapes = (shapes != null) ? shapes : new ArrayList<>();
	}

	@Override
	public void addInits(IScope scope, List<Map<String, Object>> inits, Integer max) {
	    int limit = (max != null) ? Math.min(max, shapes.size()) : shapes.size();

	    for (int i = 0; i < limit; i++) {
	        TransportShape shape = shapes.get(i);
	        IShape polyline = shape.generateShape(scope);

	        if (polyline == null) {
	            System.err.println("[ERROR] Shape generation failed for Shape ID: " + shape.getShapeId());
	            continue;
	        }

	        int routeType = shape.getRouteType();

	        final Map<String, Object> map = polyline.getAttributes(true);
	        polyline.setAttribute(IKeyword.SHAPE, polyline); 
	        map.put("shapeId", shape.getShapeId());
	        map.put("routeType", routeType);
	        map.put("routeId", shape.getRouteId());
	        map.put("tripId", shape.getTripId());
	        map.put("shape_points", shape.getPoints());

	        inits.add(map);
	    }
	}

	@Override
	public IList<? extends IAgent> createAgents(IScope scope, IPopulation<? extends IAgent> population, List<Map<String, Object>> inits, Create statement, IStatement sequence) {
		throw new UnsupportedOperationException("createAgents() should not be called on TransportShapeCreator.");
	}
	
	@Override
	public boolean handlesCreation() {
	    return false; 
	}
}
