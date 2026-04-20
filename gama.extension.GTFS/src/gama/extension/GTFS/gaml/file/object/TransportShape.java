package gama.extension.GTFS.gaml.file.object;

import gama.gaml.operators.spatial.SpatialCreation;
import gama.api.runtime.scope.IScope;
import gama.api.types.geometry.IPoint;
import gama.api.types.geometry.IShape;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.extension.GTFS.utils.SpatialUtils;


public class TransportShape {
	private final String shapeId;
    private String routeId;
    private String tripId;
    private final IList<IPoint> points; 
    private int routeType = -1;

    public TransportShape(String shapeId, String routeId) {
        this.shapeId = shapeId;
        this.routeId = routeId;
        this.points = GamaListFactory.create();        
    }
    
    public TransportShape(String shapeId, String routeId, IList<IPoint> pts) {
        this.shapeId = shapeId;
        this.routeId = routeId;
        this.points = pts;        
    }

    public void addPoint(double lat, double lon, IScope scope) {
        points.add(SpatialUtils.toGamaCRS(scope, lat, lon));
    }

    public IShape generateShape(IScope scope) {
        if (points.isEmpty()) {
            return null;
        }

        IList<IShape> shapePoints = GamaListFactory.create();
        for (IPoint point : points) {
            shapePoints.add(point);
        }

        return SpatialCreation.line(scope, shapePoints);
    }

    public String getShapeId() { 
    	return shapeId; 
    	}

    public IList<IPoint> getPoints() {
        return points;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }


    public int getRouteType() {
        return routeType;
    }

    public void setRouteType(int routeType) {
        this.routeType = routeType;
    }
    
    public String getTripId() { 
    	        return tripId;
    }

    public void setTripId(String tripId) {
        this.tripId = tripId;
    }
    
    public IShape getGeometry(IScope scope) {
        return generateShape(scope);
    }

    @Override
    public String toString() {
        return "Shape ID: " + shapeId + ", Route ID: " + routeId + ", Route Type: " + routeType + ", Points: " + points.size();
    }
}
