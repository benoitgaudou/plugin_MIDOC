package gama.extension.GTFS.gaml.file.object;

import gama.gaml.operators.spatial.SpatialCreation;

import java.util.ArrayList;
import java.util.List;

import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.geometry.IPoint;
import gama.api.types.geometry.IShape;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.extension.GTFS.gaml.file.GamaGTFSFile;
import gama.extension.GTFS.utils.SpatialUtils;
import gama.extension.GTFS.utils.file.GTFSKeywords;


public class TransportShape {
	private final String shapeId;
    private String routeId;
    // TODO : virer le tripId ... il est redondant avec le tripId du trip, et on peut faire le lien via la shapeId
    private String tripId;
    private final IList<IPoint> points; 
    private int routeType = -1;

    public TransportShape(String shapeId, String routeId) {
        this.shapeId = shapeId;
        this.routeId = routeId;
        this.points = GamaListFactory.create();        
    }

    public TransportShape(String shapeId, String routeId, String tripId) {
        this.shapeId = shapeId;
        this.routeId = routeId;
        this.tripId = tripId;
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


    @SuppressWarnings("unchecked")
	public static IMap<String, TransportShape> createTransportShapesFromGtfs(
    		IScope scope, List<String[]> shapesData, IMap<String, Integer> shapeHeaderMap) {

    	IMap<String, TransportShape> shapesMap = GamaMapFactory.create(Types.STRING, Types.get(TransportShape.class));    
    	    	
		Integer shapeIdIndex = GamaGTFSFile.findColumnIndex(shapeHeaderMap, GTFSKeywords.COL_SHAPE_ID);
		Integer latIndex = GamaGTFSFile.findColumnIndex(shapeHeaderMap, GTFSKeywords.COL_SHAPE_PT_LAT);
		Integer lonIndex = GamaGTFSFile.findColumnIndex(shapeHeaderMap, GTFSKeywords.COL_SHAPE_PT_LON);
	
		for (String[] fields : shapesData) {
	
			String shapeId = GamaGTFSFile.clean(fields[shapeIdIndex]);
			double lat = Double.parseDouble(fields[latIndex]);
			double lon = Double.parseDouble(fields[lonIndex]);
	
			TransportShape shape = shapesMap.get(shapeId);
			if (shape == null) {
				shape = new TransportShape(shapeId, "");
				shapesMap.put(shapeId, shape);
			}
			shape.addPoint(lat, lon, scope);
		}
		
		return shapesMap;
	}
    
    
	public static IMap<String, TransportShape> createTransportShapesWithoutGtfs(
			IScope scope, IMap<String, TransportStop> stopsMap, IMap<String, TransportTrip> tripsMap ){
		
    	IMap<String, TransportShape> shapesMap = GamaMapFactory.create(Types.STRING, Types.get(TransportShape.class));    

		for (TransportTrip trip : tripsMap.values()) {
			String tripId = trip.getTripId();
			String fakeShapeId = trip.getShapeId();

			if (shapesMap.containsKey(fakeShapeId))
				continue;
			
			List<IPoint> pts = new ArrayList<>();
			List<String> orderedStops = trip.getStopsInOrder();
			if (orderedStops != null && !orderedStops.isEmpty()) {
				for (String stopId : orderedStops) {
					TransportStop st = stopsMap.get(stopId);
					if (st != null) {
						pts.add(st.getLocation());
					}
				}

				if (pts.size() > 1) {
					String routeId = trip.getRouteId();
					TransportShape fake = new TransportShape(fakeShapeId, routeId, GamaListFactory.create(scope, Types.POINT, pts));						
					fake.setTripId(tripId);
					shapesMap.put(fakeShapeId, fake);
				}
			}
		}

		return shapesMap;

	}
	
}