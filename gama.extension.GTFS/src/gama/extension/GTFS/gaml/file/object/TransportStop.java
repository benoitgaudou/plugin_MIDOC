package gama.extension.GTFS.gaml.file.object;

import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.date.IDate;
import gama.api.types.geometry.IPoint;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.extension.GTFS.gaml.file.GamaGTFSFile;
import gama.extension.GTFS.utils.SpatialUtils;
import gama.extension.GTFS.utils.file.GTFSKeywords;

public class TransportStop {

	private static final Logger LOGGER = Logger.getLogger(TransportStop.class.getName());	

    private String stopId;
    private String stopName;
//    private double stopLat;   // Latitude originale du GTFS
//    private double stopLon;   // Longitude originale du GTFS
    private IPoint location;
//    private int routeType = -1;
    private int tripNumber = 0; 
    private IMap<String, IMap<String, IDate>> departureTripsInfo;
    private IMap<String, String> tripShapeMap;
    private IMap<String, IList<Double>> departureShapeDistances;

    @SuppressWarnings("unchecked")
    public TransportStop(String stopId, String stopName, double stopLat, double stopLon, IScope scope) {
        this.stopId = stopId;
        this.stopName = stopName;
//        this.stopLat = stopLat;
//        this.stopLon = stopLon;
        
        // Conversion pour la simulation GAMA (en CRS interne)
        this.location = SpatialUtils.toGamaCRS(scope, stopLat, stopLon);
        this.departureTripsInfo = null;
        this.tripShapeMap = GamaMapFactory.create(Types.STRING, Types.STRING);
        this.departureShapeDistances = GamaMapFactory.create(Types.STRING, Types.LIST);
    }

    // --- GETTER / SETTER
    public String getStopId() { return stopId; }
    public String getStopName() { return stopName; }
    public IPoint getLocation() { return location; }
//    public int getRouteType() { return routeType; }
    public IPoint getGeometry() { return location; } 
//    public double getStopLat() { return stopLat; }
//    public double getStopLon() { return stopLon; }
    public IMap<String, IMap<String, IDate>> getDepartureTripsInfo() { return departureTripsInfo; }
    public IMap<String, String> getTripShapeMap() { return tripShapeMap; }
    public int getTripNumber() { return tripNumber; }
    public IMap<String, IList<Double>> getDepartureShapeDistances() { return departureShapeDistances; }
    
    public void setTripNumber(int tripNumber) { this.tripNumber = tripNumber;} 
//    public void setRouteType(int routeType) { this.routeType = routeType; }

    
    // 
    public void addStopPairs(String tripId, IMap<String, IDate> stopPairs) {
        departureTripsInfo.put(tripId, stopPairs);
    }

    public void setDepartureTripsInfo(IMap<String, IMap<String, IDate>> departureTripsInfo) {
        this.departureTripsInfo = departureTripsInfo;
    }

    @SuppressWarnings("unchecked")
    public void ensureDepartureTripsInfo() {
        if (this.departureTripsInfo == null) {
            this.departureTripsInfo = GamaMapFactory.create(Types.STRING, Types.LIST);
        }
    }

    public void addTripShapePair(String tripId, String shapeId) { this.tripShapeMap.put(tripId, shapeId); }
    
    public void addDepartureShapeDistances(String tripId, IList<Double> distances) {
        departureShapeDistances.put(tripId, distances);
    }
     
    @Override
    public String toString() {
        String locationStr = (location != null)
                ? String.format("x=%.2f, y=%.2f", location.getX(), location.getY())
                : "null";
        return "TransportStop{id='" + stopId + "', name='" + stopName
                + "', location={" + locationStr + "}, "
//                + "routeType=" + routeType + ", "
                + "tripShapeMap=" + tripShapeMap + "}";
    }
    
    @SuppressWarnings("unchecked")
	public static IMap<String, TransportStop> createTransportStopsFromGtfs(
    		IScope scope, List<String[]> stopsData, IMap<String, Integer> headerIMap, Set<String> usedStopIds) {

    	IMap<String, TransportStop> stopsMap = GamaMapFactory.create(Types.STRING, Types.get(TransportStop.class));

        if (stopsData != null && headerIMap != null) {
            Integer stopIdIndex = GamaGTFSFile.findColumnIndex(headerIMap, GTFSKeywords.COL_STOP_ID);
            Integer stopNameIndex = GamaGTFSFile.findColumnIndex(headerIMap, GTFSKeywords.COL_STOP_NAME);
            Integer stopLatIndex = GamaGTFSFile.findColumnIndex(headerIMap, GTFSKeywords.COL_STOP_LAT);
            Integer stopLonIndex = GamaGTFSFile.findColumnIndex(headerIMap, GTFSKeywords.COL_STOP_LON);

            if (stopIdIndex == null || stopNameIndex == null || stopLatIndex == null || stopLonIndex == null) {
                throw new RuntimeException("stop_id, stop_name, stop_lat or stop_lon column not found in stops.txt!");
            }

            for (String[] fields : stopsData) {
                String stopId = GamaGTFSFile.clean(fields[stopIdIndex]); 
                if (usedStopIds.contains(stopId)) {
                    String stopName = fields[stopNameIndex];
                    double stopLat = Double.parseDouble(fields[stopLatIndex]);
                    double stopLon = Double.parseDouble(fields[stopLonIndex]);

                    TransportStop stop = new TransportStop(stopId, stopName, stopLat, stopLon, scope);
                    stopsMap.put(stopId, stop);                    	
                }
            }
        }
        LOGGER.info("Finished creating TransportStop objects.");
    
        return stopsMap;
    }
    
}
