package gama.extension.GTFS.gaml.file.object;

import java.util.List;
import java.util.logging.Logger;

import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.extension.GTFS.gaml.file.GamaGTFSFile;
import gama.extension.GTFS.utils.file.GTFSKeywords;

public class TransportRoute {

	private static final Logger LOGGER = Logger.getLogger(TransportStop.class.getName());		
	
    private String routeId; // Route identifier
    private String shortName; // Short name of the route
    private String longName; // Long name of the route
    private int type; // Type of transport (e.g., bus, tram, etc.)

    // Constructor
    public TransportRoute(String routeId, String shortName, String longName, int type) {
        this.routeId = routeId;
        this.shortName = shortName;
        this.longName = longName;
        this.type = type;
    }

    // Getters and Setters
    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getLongName() {
        return longName;
    }

    public void setLongName(String longName) {
        this.longName = longName;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    @SuppressWarnings("unchecked")
	public static IMap<String, TransportRoute> createTransportStopsFromGtfs(
    		IScope scope, List<String[]> routesData, IMap<String, Integer> routesHeader) {

    	IMap<String, TransportRoute> routesMap = GamaMapFactory.create(Types.STRING, Types.get(TransportRoute.class));

		if (routesData != null && routesHeader != null) {
			Integer routeIdIndex = GamaGTFSFile.findColumnIndex(routesHeader, GTFSKeywords.COL_ROUTE_ID);
			Integer routeTypeIndex = GamaGTFSFile.findColumnIndex(routesHeader, GTFSKeywords.COL_ROUTE_TYPE);
			if (routeIdIndex == null || routeTypeIndex == null) {
				throw new RuntimeException("route_id or route_type column not found in " + GTFSKeywords.FILE_ROUTES);
			}
			for (String[] fields : routesData) {
				try {
					String routeId = GamaGTFSFile.clean(fields[routeIdIndex]);
					int routeType = Integer.parseInt(fields[routeTypeIndex]);
					
					TransportRoute route = new TransportRoute(routeId, null, null, routeType);
					routesMap.put(routeId, route);                    	

	//				routeTypeMap.put(routeId, routeType);
				} catch (Exception e) {
					LOGGER.severe("[ERROR] Invalid routeType in " + GTFSKeywords.FILE_ROUTES + ": "
							+ java.util.Arrays.toString(fields) + " -> " + e.getMessage());
				}
			}
		}
		return routesMap;
    }     

    // Method to display route information
    @Override
    public String toString() {
        return "Route ID: " + routeId + ", Short Name: " + shortName + ", Long Name: " + longName + ", Type: " + type;
    }
    
}
