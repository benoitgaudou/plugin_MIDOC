package gama.extension.GTFS;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

import gama.annotations.doc;
import gama.annotations.file;
import gama.annotations.example;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.date.IDate;
import gama.api.types.file.GamaFile;
import gama.api.types.geometry.GamaPointFactory;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.api.types.geometry.IPoint;
import gama.api.types.pair.GamaPairFactory;
import gama.api.types.pair.IPair;
import gama.api.utils.geometry.GamaEnvelopeFactory;
import gama.api.utils.geometry.IEnvelope;

/**
 * Reading and processing GTFS files in GAMA. This class reads multiple GTFS files
 * and creates TransportRoute, TransportTrip, and TransportStop objects.
 */
@file(
    name = "gtfs",
    extensions = { "txt" },
    buffer_type = IType.LIST,
    buffer_content = IType.STRING,
    buffer_index = IType.INT,
    concept = { IConcept.FILE },
    doc = @doc("GTFS files represent public transportation data in CSV format, typically with the '.txt' extension.")
)
public class GamaGTFSFile extends GamaFile<IList<String>, String> {

    // Required files for GTFS data
    private static final String[] REQUIRED_FILES = {
        "routes.txt", "trips.txt", "stop_times.txt", "stops.txt"
    };

    // Data structure to store GTFS files
    private IMap<String, List<String[]>> gtfsData;
    
    // New field to store header mappings for each file
    @SuppressWarnings("unchecked")
	private IMap<String, IMap<String, Integer>> headerMaps = GamaMapFactory.create(Types.STRING, Types.get(IMap.class));

    // Collections for objects created from GTFS files
    private IMap<String, TransportTrip> tripsMap;
    private IMap<String, TransportStop> stopsMap;
    private IMap<String, TransportShape> shapesMap;
    private IMap<String, TransportRoute> routesMap; 
    private IMap<String, Integer> shapeRouteTypeMap;
    private Map<String, Character> fileSeparators = new HashMap<>();
    
    private boolean shapesTxtPresent = false;
    public boolean isShapesTxtPresent() { return shapesTxtPresent; }
    private IMap<String, Integer> routeTypeMapGlobal;
    private IScope initScope;
    
    /**
     * Constructor for reading GTFS files.
     *
     * @param scope    The simulation context in GAMA.
     * @param pathName The directory path containing GTFS files.
     * @throws GamaRuntimeException If an error occurs while loading the files.
     */
    @doc (
            value = "This constructor allows loading GTFS files from a specified directory.",
            examples = { @example (value = "GTFS_reader gtfs <- GTFS_reader(scope, \"path_to_gtfs_directory\");")})
    public GamaGTFSFile(final IScope scope, final String pathName) throws GamaRuntimeException {
        super(scope, pathName);
        this.initScope = scope;
        
        // Debug: Print the GTFS path in the GAMA console
        if (scope != null && scope.getGui() != null) {
            scope.getGui().getConsole().informConsole("GTFS path used: "  + pathName, scope.getSimulation());
        } else {
            System.out.println("GTFS path used: " + pathName);  
        }

        // Load GTFS files
        System.out.println("Loading GTFS files...");
        loadGtfsFiles(scope);
        System.out.println("File loading completed.");
        
        // Create transport objects
        System.out.println("Creating transport objects...");
        createTransportObjects(scope);
        System.out.println("Transport object creation completed.");
       
    }

    public GamaGTFSFile(final String pathName) throws GamaRuntimeException {
        super(null, pathName);  // Pass 'null' for IScope as it is not needed here
        this.initScope = null;
        checkValidity(null);  // Pass 'null' if IScope is not necessary for this check
        loadGtfsFiles(null);
        createTransportObjects(null);
    }
    
    /**
     * Method to retrieve the list of stops (TransportStop) from stopsMap.
     * @return List of transport stops
     */
    public List<TransportStop> getStops() {
        List<TransportStop> stopList = new ArrayList<>(stopsMap.values());
        System.out.println("Number of created stops: " + stopList.size());
        return stopList;
    }
    
    /**
     * Method to retrieve the list of shape (TransportShape) from shapesMap.
     * @return List of transport shapes
     */   
    public List<TransportShape> getShapes() {
        // Si déjà présents, renvoyer
        if (!shapesMap.isEmpty()) return new ArrayList<>(shapesMap.values());
        // Si shapes.txt absent → construire maintenant (lazy)
        if (!shapesTxtPresent) {
            if (initScope == null) {
                System.err.println("[ERROR] buildFakeShapesLazily requires a non-null scope (initScope=null). "
                    + "Call getShapes(scope) from GAML context instead.");
                return new ArrayList<>(shapesMap.values());
            }
            buildFakeShapesLazily(initScope, routeTypeMapGlobal);
        }
        return new ArrayList<>(shapesMap.values());
    }
    
    public List<TransportShape> getShapes(final IScope scopeForLazy) {
        if (!shapesMap.isEmpty()) return new ArrayList<>(shapesMap.values());
        if (!shapesTxtPresent) {
            buildFakeShapesLazily(scopeForLazy, routeTypeMapGlobal);
        }
        return new ArrayList<>(shapesMap.values());
    }
    
    
    private void buildFakeShapesLazily(final IScope scope, final IMap<String, Integer> routeTypeMap) {
        System.out.println("[LAZY] Building fake shapes now (requested by create transport_shape)...");
        for (TransportTrip trip : tripsMap.values()) {
            String tripId = trip.getTripId();
            String fakeShapeId = trip.getShapeId();
            if (fakeShapeId == null || fakeShapeId.isEmpty()) {
                fakeShapeId = "fake_" + tripId;
                trip.setShapeId(fakeShapeId);
            }
            if (shapesMap.containsKey(fakeShapeId)) continue;

            List<IPoint> pts = new ArrayList<>();
            List<String> orderedStops = trip.getStopsInOrder();
            if (orderedStops == null || orderedStops.isEmpty()) {
                List<String[]> stopTimesData = gtfsData.get("stop_times.txt");
                IMap<String, Integer> stopTimesHeader = headerMaps.get("stop_times.txt");
                Integer tripIdIdx = findColumnIndex(stopTimesHeader, "trip_id");
                Integer stopIdIdx = findColumnIndex(stopTimesHeader, "stop_id");
                Integer seqIdx    = findColumnIndex(stopTimesHeader, "stop_sequence");
                if (stopTimesData != null && tripIdIdx != null && stopIdIdx != null && seqIdx != null) {
                    List<String[]> lines = new ArrayList<>();
                    for (String[] st : stopTimesData) {
                        if (st != null && st.length > Math.max(tripIdIdx, Math.max(stopIdIdx, seqIdx))) {
                            if (tripId.equals(st[tripIdIdx].trim().replace("\"","").replace("'",""))) {
                                lines.add(st);
                            }
                        }
                    }
                    lines.sort((a,b) -> Integer.compare(
                        Integer.parseInt(a[seqIdx].trim()),
                        Integer.parseInt(b[seqIdx].trim())
                    ));
                    for (String[] st : lines) {
                        String stopId = st[stopIdIdx].trim().replace("\"","").replace("'","");
                        TransportStop stop = stopsMap.get(stopId);
                        if (stop != null) pts.add(GamaPointFactory.create(stop.getStopLat(), stop.getStopLon()));
                    }
                }
            } else {
                for (String stopId : orderedStops) {
                    TransportStop st = stopsMap.get(stopId);
                    if (st != null) pts.add(GamaPointFactory.create(st.getStopLat(), st.getStopLon()));
                }
            }

            if (pts.size() > 1) {
                String routeId = trip.getRouteId();
                TransportShape fake = new TransportShape(fakeShapeId, routeId);
                for (IPoint p : pts) { fake.addPoint(p.getX(), p.getY(), scope); }
                if (routeTypeMap != null && routeTypeMap.containsKey(routeId)) {
                    fake.setRouteType(routeTypeMap.get(routeId));
                }
                fake.setTripId(tripId);
                shapesMap.put(fakeShapeId, fake);
            }
        }
        System.out.println("[LAZY] Fake shapes built: " + shapesMap.size());
    }

    
    /**
     * Method to retrieve the list of trips (TransportTrip) from tripsMap.
     * @return List of transport trips
     */
    public List<TransportTrip> getTrips() {
    	return new ArrayList<>(tripsMap.values());
    }
    
    /**
     * Method to retrieve the list of routes (TransportRoute) from routesMap.
     * @return List of transport routes
     */
    public List<TransportRoute> getRoutes() {
        return new ArrayList<>(routesMap.values());
    }

    /**
     * Method to verify the directory's validity.
     *
     * @param scope    The simulation context in GAMA.
     * @throws GamaRuntimeException If the directory is invalid or does not contain required files.
     */
    @Override
    protected void checkValidity(final IScope scope) throws GamaRuntimeException {
        System.out.println("Starting directory validity check...");

        File folder = getFile(scope);
        
        if (!folder.exists() || !folder.isDirectory()) {
            throw GamaRuntimeException.error("The provided path for GTFS files is invalid. Ensure it is a directory containing .txt files.", scope);
        }
        Set<String> requiredFilesSet = new HashSet<>(Set.of(REQUIRED_FILES));
        System.out.println("Required GTFS files: " + requiredFilesSet);
        System.out.println("Vérification du dossier GTFS : " + getName(null));
        File[] files = folder.listFiles();
        //System.out.println("Liste des fichiers trouvés : " + Arrays.toString(files));
        if (files != null) {
            for (File file : files) {
                String fileName = file.getName();
                if (fileName.endsWith(".txt")) {
                    requiredFilesSet.remove(fileName);
                }
            }
        }

        if (!requiredFilesSet.isEmpty()) {
            throw GamaRuntimeException.error("Missing GTFS files: " + requiredFilesSet, scope);
        }     
        System.out.println("Directory validity check completed.");
    }

    /**
     * Loads GTFS files and verifies if all required files are present.
     */
    @SuppressWarnings("unchecked")
	private void loadGtfsFiles(final IScope scope) throws GamaRuntimeException {
        gtfsData = GamaMapFactory.create(Types.STRING, Types.LIST); // Use GamaMap for storing GTFS files
        headerMaps = GamaMapFactory.create(Types.STRING, Types.get(IMap.class));
        try {
            File folder = this.getFile(scope);
            File[] files = folder.listFiles();  // List of files in the folder
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".txt")) {
                    	// 1. Détecte le séparateur
                    	char separator = detectSeparator(file);
                    	//System.out.println("Séparateur détecté pour " + file.getName() + " : " + separator);
                    	// 2. Mémorise le séparateur pour ce fichier
                    	fileSeparators.put(file.getName(), separator);
                    	// 3. Utilise OpenCSV avec le séparateur détecté
                    	Map<String, Integer> headerMap = new HashMap<>();
                        	// 3.1 Lit le fichier CSV et récupère le contenu
                    	List<String[]> fileContent = readCsvFileOpenCSV(file, headerMap);
//                    	String sepStr;
//                        if (separator == ',') sepStr = "virgule (,)";
//                        else if (separator == ';') sepStr = "point-virgule (;)";
//                        else if (separator == '\t') sepStr = "tabulation";
//                        else sepStr = String.valueOf(separator);

                        	// 4. Stocke le contenu du fichier et le header dans les maps
                    	gtfsData.put(file.getName(), fileContent);
                    	IMap<String, Integer> headerIMap = GamaMapFactory.wrap(Types.STRING, Types.INT, headerMap);
                    	headerMaps.put(file.getName(), headerIMap);  
                        
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error while loading GTFS files: " + e.getMessage());
            throw GamaRuntimeException.create(e, scope);
        }
        System.out.println("All GTFS files have been loaded.");
    }
    
    /**
     * Retrieves the header map for a given file.
     *
     * @param fileName The name of the file
     * @return The header map
     */
    private void createTransportObjectsWithShapes(
    	    IScope scope,
    	    IMap<String, Integer> routeTypeMap,
    	    IMap<String, String>  shapeRouteMap,       
    	    IMap<String, Integer> shapeRouteTypeMap     
    	)
    {	
    	    // 1. Création des TransportShape à partir de shapes.txt
    	    List<String[]> shapesData = gtfsData.get("shapes.txt");
    	    IMap<String, Integer> headerMap = headerMaps.get("shapes.txt");
    	    Integer shapeIdIndex = findColumnIndex(headerMap, "shape_id");
    	    Integer latIndex = findColumnIndex(headerMap, "shape_pt_lat");
    	    Integer lonIndex = findColumnIndex(headerMap, "shape_pt_lon");

    	    for (String[] fields : shapesData) {
    	        if (fields == null) continue;
    	        try {
    	        	String shapeId = fields[shapeIdIndex].trim().replace("\"","").replace("'","");
    	        	double lat = Double.parseDouble(fields[latIndex]);
    	        	double lon = Double.parseDouble(fields[lonIndex]);

    	        	TransportShape shape = shapesMap.get(shapeId);
    	        	if (shape == null) { shape = new TransportShape(shapeId, ""); shapesMap.put(shapeId, shape); }
    	        	shape.addPoint(lat, lon, scope);

    	        } catch (Exception e) {
    	            System.err.println("[ERROR] Processing shape line: " + java.util.Arrays.toString(fields) + " -> " + e.getMessage());
    	        }
    	    }

    	    // 2. Création des trips (avec shapeId réel)
    	    List<String[]> tripsData = gtfsData.get("trips.txt");
    	    IMap<String, Integer> tripsHeaderMap = headerMaps.get("trips.txt");
    	    Integer routeIdIndex = findColumnIndex(tripsHeaderMap, "route_id");
    	    Integer tripIdIndex = findColumnIndex(tripsHeaderMap, "trip_id");
    	    Integer shapeIdIdx = findColumnIndex(tripsHeaderMap, "shape_id");

    	    for (String[] fields : tripsData) {
    	        if (fields == null) continue;
    	        try {
    	            String routeId = fields[routeIdIndex].trim().replace("\"", "").replace("'", "");
    	            String tripId = fields[tripIdIndex].trim().replace("\"", "").replace("'", "");
    	            String shapeId = null;
    	            if (shapeIdIdx != null && fields.length > shapeIdIdx) {
    	                String raw = fields[shapeIdIdx].trim().replace("\"","").replace("'","");
    	                if (!raw.isEmpty()) shapeId = raw;
    	            }
    	            TransportTrip trip = tripsMap.get(tripId);
    	            if (trip == null) {
    	                trip = new TransportTrip(routeId, "", tripId, 0, shapeId);
    	                tripsMap.put(tripId, trip);
    	            }
    	            if (shapeId != null && shapesMap.containsKey(shapeId)) {
    	                shapeRouteTypeMap.put(shapeId, trip.getRouteType());
    	                shapeRouteMap.put(shapeId, routeId);
    	                shapesMap.get(shapeId).setTripId(tripId);
    	            }
    	        } catch (Exception e) {
    	            System.err.println("[ERROR] Invalid trip line in trips.txt: " + java.util.Arrays.toString(fields) + " -> " + e.getMessage());
    	        }
    	    }

    	    // 3. Assigner routeId/routeType aux shapes
    	    for (TransportShape shape : shapesMap.values()) {
    	        String shapeId = shape.getShapeId();
    	        if (shapeRouteMap.containsKey(shapeId)) {
    	            String routeId = shapeRouteMap.get(shapeId);
    	            shape.setRouteId(routeId);
    	        }
    	        if (shapeRouteTypeMap.containsKey(shapeId)) {
    	            shape.setRouteType(shapeRouteTypeMap.get(shapeId));
    	        }
    	    }

    	    // 4. Assigner routeType à tous les trips
    	    for (TransportTrip trip : tripsMap.values()) {
    	        if (trip.getRouteType() == -1 && routeTypeMap.containsKey(trip.getRouteId())) {
    	            trip.setRouteType(routeTypeMap.get(trip.getRouteId()));
    	        }
    	    }
    	}
    
/*    private void createTransportObjectsWithFakeShapes(
    	    IScope scope,
    	    IMap<String, Integer> routeTypeMap
    	) {
    	    // 1. Création des trips et shapes "fictifs"
    	    List<String[]> tripsData = gtfsData.get("trips.txt");
    	    IMap<String, Integer> tripsHeaderMap = headerMaps.get("trips.txt");
    	    Integer routeIdIndex = findColumnIndex(tripsHeaderMap, "route_id");
    	    Integer tripIdIndex = findColumnIndex(tripsHeaderMap, "trip_id");

    	    // On crée les trips, et pour chaque trip, on va créer un fake shape
    	    for (String[] fields : tripsData) {
    	        if (fields == null) continue;
    	        try {
    	            String routeId = fields[routeIdIndex].trim().replace("\"", "").replace("'", "");
    	            String tripId = fields[tripIdIndex].trim().replace("\"", "").replace("'", "");
    	            String fakeShapeId = "fake_" + tripId; // garanti unique

    	            // Crée le trip, avec shapeId = fakeShapeId
    	            TransportTrip trip = new TransportTrip(routeId, "", tripId, 0, fakeShapeId);
    	            tripsMap.put(tripId, trip);

    	            // Récupère la liste des stops pour ce trip
    	            List<String> stopIdsInOrder = new ArrayList<>();
    	            List<String[]> stopTimesData = gtfsData.get("stop_times.txt");
    	            IMap<String, Integer> stopTimesHeader = headerMaps.get("stop_times.txt");
    	            Integer tripIdIdxST = findColumnIndex(stopTimesHeader, "trip_id");
    	            Integer stopIdIdxST = findColumnIndex(stopTimesHeader, "stop_id");
    	            if (stopTimesData != null && tripIdIdxST != null && stopIdIdxST != null) {
    	                for (String[] stFields : stopTimesData) {
    	                    if (stFields == null) continue;
    	                    String tripIdST = stFields[tripIdIdxST].trim().replace("\"", "").replace("'", "");
    	                    if (tripIdST.equals(tripId)) {
    	                        String stopId = stFields[stopIdIdxST].trim().replace("\"", "").replace("'", "");
    	                        stopIdsInOrder.add(stopId);
    	                    }
    	                }
    	            }
    	            // On construit la fake polyline avec les coordonnées des stops
    	            List<IPoint> shapePoints = new ArrayList<>();
    	            for (String stopId : stopIdsInOrder) {
    	                TransportStop stop = stopsMap.get(stopId);
    	                if (stop != null) {
    	                    shapePoints.add(GamaPointFactory.create(stop.getStopLat(), stop.getStopLon()));
    	                }
    	            }
    	            // Crée le fake shape seulement s'il y a au moins 2 points
    	            if (shapePoints.size() > 1) {
    	            	TransportShape fakeShape = new TransportShape(fakeShapeId, routeId);
    	                for (IPoint pt : shapePoints) fakeShape.addPoint(pt.getX(), pt.getY(), scope);
    	                // On peut setter la routeType à ce fakeShape
    	                if (routeTypeMap.containsKey(routeId)) fakeShape.setRouteType(routeTypeMap.get(routeId));
    	                // TripId pour référence
    	                fakeShape.setTripId(tripId);
    	                shapesMap.put(fakeShapeId, fakeShape);
    	            }

    	            // Set le routeType du trip
    	            if (routeTypeMap.containsKey(routeId)) {
    	                trip.setRouteType(routeTypeMap.get(routeId));
    	            }
    	        } catch (Exception e) {
    	            
    	        }
    	    }
    	}
*/
    
    private void createTripsWithoutShapes(IScope scope, IMap<String, Integer> routeTypeMap) {
        List<String[]> tripsData = gtfsData.get("trips.txt");
        IMap<String, Integer> tripsHeader = headerMaps.get("trips.txt");
        Integer routeIdIndex = findColumnIndex(tripsHeader, "route_id");
        Integer tripIdIndex  = findColumnIndex(tripsHeader,  "trip_id");
        if (tripsData == null || routeIdIndex == null || tripIdIndex == null) return;

        for (String[] fields : tripsData) {
            if (fields == null) continue;
            try {
                String routeId = fields[routeIdIndex].trim().replace("\"","").replace("'","");
                String tripId  = fields[tripIdIndex ].trim().replace("\"","").replace("'","");
                String fakeShapeId = "fake_" + tripId;

                TransportTrip trip = tripsMap.get(tripId);
                if (trip == null) {
                    trip = new TransportTrip(routeId, "", tripId, 0, fakeShapeId); // shapeId placeholder
                    if (routeTypeMap.containsKey(routeId)) trip.setRouteType(routeTypeMap.get(routeId));
                    tripsMap.put(tripId, trip);
                }
            } catch (Exception ignore) {}
        }
    }



    @SuppressWarnings("unchecked")
    private void createTransportObjects(IScope scope) {
        System.out.println("Starting transport object creation...");

        // Initialisation des maps globales
        routesMap = GamaMapFactory.create(Types.STRING, Types.get(TransportRoute.class)); 
        stopsMap = GamaMapFactory.create(Types.STRING, Types.get(TransportStop.class));   
        tripsMap = GamaMapFactory.create(Types.STRING, Types.get(TransportTrip.class));     
        shapesMap = GamaMapFactory.create(Types.STRING, Types.get(TransportShape.class));
        shapeRouteTypeMap = GamaMapFactory.create(Types.STRING, Types.INT);

        // Map pour lier shapeId <-> routeId, shapeId <-> routeType
        IMap<String, String>  shapeRouteMap = GamaMapFactory.create(Types.STRING, Types.STRING);
        IMap<String, Integer> shapeRouteTypeMapLocal = GamaMapFactory.create(Types.STRING, Types.INT);

        // 1. Lecture des routeType par routeId (commune)
        IMap<String, Integer> routeTypeMap = GamaMapFactory.create(Types.STRING, Types.INT);
        List<String[]> routesData = gtfsData.get("routes.txt");
        IMap<String, Integer> routesHeader = headerMaps.get("routes.txt");

        if (routesData != null && routesHeader != null) {
            Integer routeIdIndex = findColumnIndex(routesHeader, "route_id");
            Integer routeTypeIndex = findColumnIndex(routesHeader, "route_type");
            if (routeIdIndex == null || routeTypeIndex == null) {
                throw new RuntimeException("route_id or route_type column not found in routes.txt!");
            }
            for (String[] fields : routesData) {
                if (fields == null) continue;
                try {
                    String routeId = fields[routeIdIndex].trim().replace("\"", "").replace("'", "");
                    int routeType = Integer.parseInt(fields[routeTypeIndex]);
                    routeTypeMap.put(routeId, routeType);
                } catch (Exception e) {
                    System.err.println("[ERROR] Invalid routeType in routes.txt: " + java.util.Arrays.toString(fields) + " -> " + e.getMessage());
                }
            }
        }
        
        this.routeTypeMapGlobal = routeTypeMap;

        // 2. Collecte des stop_ids utilisés (commun)
        Set<String> usedStopIds = new HashSet<>();
        List<String[]> stopTimesData = gtfsData.get("stop_times.txt");
        IMap<String, Integer> stopTimesHeader = headerMaps.get("stop_times.txt");

        if (stopTimesData != null && stopTimesHeader != null && stopTimesHeader.containsKey("stop_id")) {
            Integer stopIdIndex = stopTimesHeader.get("stop_id");
            if (stopIdIndex == null) throw new RuntimeException("stop_id column not found in stop_times.txt!");
            for (String[] fields : stopTimesData) {
                if (fields == null || fields.length <= stopIdIndex) continue;
                usedStopIds.add(fields[stopIdIndex].trim().replace("\"", "").replace("'", ""));
            }
        }

        // 3. Création des stops (commun)
        List<String[]> stopsData = gtfsData.get("stops.txt");
        IMap<String, Integer> headerIMap = headerMaps.get("stops.txt");

        if (stopsData != null && headerIMap != null) {
            Integer stopIdIndex = findColumnIndex(headerIMap, "stop_id");
            Integer stopNameIndex = findColumnIndex(headerIMap, "stop_name");
            Integer stopLatIndex = findColumnIndex(headerIMap, "stop_lat");
            Integer stopLonIndex = findColumnIndex(headerIMap, "stop_lon");

            if (stopIdIndex == null || stopNameIndex == null || stopLatIndex == null || stopLonIndex == null) {
                throw new RuntimeException("stop_id, stop_name, stop_lat or stop_lon column not found in stops.txt!");
            }

            for (String[] fields : stopsData) {
                if (fields == null) continue;
                try {
                    String stopId = fields[stopIdIndex].trim().replace("\"", "").replace("'", ""); 
                    if (!usedStopIds.contains(stopId)) continue;

                    String stopName = fields[stopNameIndex];
                    double stopLat = Double.parseDouble(fields[stopLatIndex]);
                    double stopLon = Double.parseDouble(fields[stopLonIndex]);

                    TransportStop stop = new TransportStop(stopId, stopName, stopLat, stopLon, scope);
                    stopsMap.put(stopId, stop);
                } catch (Exception e) {
                    System.err.println("[ERROR] Processing stop line: " + java.util.Arrays.toString(fields) + " -> " + e.getMessage());
                }
            }
            System.out.println("Nombre d'objets TransportStop créés : " + stopsMap.size());System.out.println("Nombre d'objets TransportStop créés : " + stopsMap.size());
        }
        System.out.println("Finished creating TransportStop objects.");

        // 4. Teste la présence de shapes.txt
        List<String[]> shapesData = gtfsData.get("shapes.txt");
        IMap<String, Integer> headerMap = headerMaps.get("shapes.txt");
        boolean shapesTxtExists = (shapesData != null && headerMap != null && !shapesData.isEmpty());

        // 5. Appelle la bonne méthode selon shapes.txt
        if (shapesTxtExists) {
            System.out.println("[INFO] shapes.txt found. Using standard GTFS shapes pipeline.");
            createTransportObjectsWithShapes(scope, routeTypeMap, shapeRouteMap, shapeRouteTypeMapLocal);
            // Fusionne dans la map globale si besoin
            shapeRouteTypeMap.putAll(shapeRouteTypeMapLocal);
            this.shapesTxtPresent = true;
        } else {
        	 System.out.println("[INFO] shapes.txt NOT found. Deferring fake shapes creation until transport_shape agents are created.");
        	 this.shapesTxtPresent = false;
        	 createTripsWithoutShapes(scope, routeTypeMap);
        }

        // 6. Affecte le routeType à tous les trips qui n'ont pas été remplis (commune)
        for (TransportTrip trip : tripsMap.values()) {
            if (trip.getRouteType() == -1 && routeTypeMap.containsKey(trip.getRouteId())) {
                trip.setRouteType(routeTypeMap.get(trip.getRouteId()));
            }
        }

        // 7. Résumé et computeDepartureInfo (communs)
        System.out.println("---- Récapitulatif création objets GTFS ----");
        System.out.println("Nombre de stops lus dans stops.txt          : " + (stopsData != null ? stopsData.size() : 0));
        System.out.println("Nombre de stops créés (stopsMap)            : " + stopsMap.size());
        System.out.println("Nombre de trips créés (tripsMap)            : " + tripsMap.size());
        System.out.println("Nombre de shapes lus dans shapes.txt        : " + (shapesData != null ? shapesData.size() : 0));
        System.out.println("Nombre de shapes créés (shapesMap)          : " + shapesMap.size());
        System.out.println("--------------------------------------------");

        System.out.println("[INFO] Finished assigning routeType to TransportShape and TransportTrip.");
        System.out.println("[INFO] Calling computeDepartureInfo...");
        computeDepartureInfo(scope);
        
        System.out.println("[INFO] Début de la propagation finale des routeType aux stops...");
        int propagated = 0;
        for (TransportTrip trip : tripsMap.values()) {
            int routeType = trip.getRouteType();
            if (routeType == -1) {
                System.out.println("[DEBUG] Trip " + trip.getTripId() + " a routeType=-1 => ignoré");
                continue;
            }
            List<String> orderedStops = trip.getStopsInOrder();
            if (orderedStops == null || orderedStops.isEmpty()) {
                continue;
            }

            for (String stopId : orderedStops) {
                TransportStop stop = stopsMap.get(stopId);
                if (stop != null && stop.getRouteType() == -1) {
                    stop.setRouteType(routeType);
                    propagated++;
                    System.out.println("[INFO] Propagation : stop " + stopId + " reçoit routeType " + routeType + " depuis trip " + trip.getTripId());
                }
            }
        }
        System.out.println("Tous les stops ont reçu leur routeType à partir des trips complets. (nouveaux assignés : " + propagated + ")");
        System.out.println("[INFO] computeDepartureInfo completed.");

        System.out.println("[INFO] Réinitialisation des routeType à -1 pour tous les stops...");
        for (TransportStop stop : stopsMap.values()) {
            stop.setRouteType(-1);
        }

        System.out.println("[INFO] Début de la propagation finale des routeType aux stops...");
        int counter = 0;
        for (TransportTrip trip : tripsMap.values()) {
            int routeType = trip.getRouteType();
            if (routeType == -1) continue;

            for (String stopId : trip.getStopsInOrder()) {
                TransportStop stop = stopsMap.get(stopId);
                if (stop != null && stop.getRouteType() == -1) {
                    stop.setRouteType(routeType);
                    counter++;
                }
            }
        }
        System.out.println("Tous les stops ont reçu leur routeType à partir des trips complets. (nouveaux assignés : " + counter + ")");

    }



    private char detectSeparator(File file) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Ignore les lignes vides
                if (line.trim().isEmpty()) continue;

                // Compte virgules/points-virgules hors guillemets
                int commaCount = 0, semicolonCount = 0;
                boolean inQuotes = false;
                for (char c : line.toCharArray()) {
                    if (c == '"') inQuotes = !inQuotes;
                    if (!inQuotes) {
                        if (c == ',') commaCount++;
                        if (c == ';') semicolonCount++;
                    }
                }
                if (semicolonCount > commaCount) return ';';
                else return ','; // Virgule par défaut
            }
        }
        // Par défaut, virgule
        return ',';
    }

    
    public static String[] parseCsvLine(String line, char separator) {
        try {
            CSVParser parser = new CSVParserBuilder().withSeparator(separator).build();
            return parser.parseLine(line);
        } catch (Exception e) {
            System.err.println("[ERROR] CSV parsing failed: " + line);
            return null;
        }
    }

    /**
     * Reads a CSV file 
     */
    private List<String[]> readCsvFileOpenCSV(File file, Map<String, Integer> headerMap) throws IOException, CsvValidationException {
        List<String[]> content = new ArrayList<>();
        if (!file.isFile()) {
            throw new IOException(file.getAbsolutePath() + " is not a valid file.");
        }

        char separator = detectSeparator(file);
 
        try (CSVReader reader = new CSVReaderBuilder(new FileReader(file))
                                    .withSkipLines(0)
                                    .withCSVParser(new CSVParserBuilder().withSeparator(separator).build())
                                    .build()) {
            // Lis et nettoie le header
            String[] headers = reader.readNext();
            while (headers != null && headers.length == 1 && headers[0].trim().isEmpty()) {
                headers = reader.readNext();
            }
            if (headers != null) {
                for (int i = 0; i < headers.length; i++) {
                    String col = headers[i].trim().replace("\uFEFF", "").toLowerCase();
                    headerMap.put(col, i);
                }
            }
            //System.out.println("Headers trouvés dans " + file.getName() + " : " + headerMap.keySet());
            String[] line;
            while ((line = reader.readNext()) != null) {
                // Complète les champs manquants (à droite)
                if (line.length < headerMap.size()) {
                    String[] newLine = new String[headerMap.size()];
                    System.arraycopy(line, 0, newLine, 0, line.length);
                    for (int i = line.length; i < headerMap.size(); i++) {
                        newLine[i] = "";
                    }
                    line = newLine;
                }
                // Ignore les lignes totalement vides
                boolean isEmpty = true;
                for (String field : line) {
                    if (field != null && !field.trim().isEmpty()) {
                        isEmpty = false;
                        break;
                    }
                }
                if (isEmpty) continue;
                content.add(line); // Ajoute le tableau de champs
            }
        }
        //System.out.println("⇒ Fichier '" + file.getName() + "' : " + content.size() + " lignes lues.");
        return content;
    }




    /**
     * Trouve l’index d’une colonne parmi plusieurs possibilités dans le headerMap.
     * @param headerMap La map colonne → index.
     * @param possibleNames Liste de noms possibles (ex: "stop_id", "stopid"...).
     * @return L’index si trouvé, sinon null.
     */
    private Integer findColumnIndex(Map<String, Integer> headerMap, String... possibleNames) {
        if (headerMap == null) return null;
        for (String target : possibleNames) {
            for (String col : headerMap.keySet()) {
                if (col.equalsIgnoreCase(target.trim())) return headerMap.get(col);
            }
        }
        return null;
    }

    
    @Override
    protected void fillBuffer(final IScope scope) throws GamaRuntimeException {
    	System.out.println("Filling buffer...");
        if (gtfsData == null) {
        	System.out.println("gtfsData is null, loading GTFS files...");
            loadGtfsFiles(scope);
            System.out.println("Finished loading GTFS files.");
        }else
        	 System.out.println("gtfsData is already initialized.");
    
    }

    @Override
    public IList<String> getAttributes(final IScope scope) {
    	System.out.println("Retrieving GTFS data attributes...");
    	if (gtfsData != null) {
            Set<String> keySet = gtfsData.keySet();
            System.out.println("Attributes retrieved: " + keySet);
            return GamaListFactory.createWithoutCasting(Types.STRING, keySet.toArray(new String[0]));
        } else {
            System.out.println("gtfsData is null, no attributes to retrieve.");
            return GamaListFactory.createWithoutCasting(Types.STRING);
        }
    }

    @Override
    public IEnvelope computeEnvelope(final IScope scope) {
        // Provide a default implementation or return an empty envelope
        return GamaEnvelopeFactory.EMPTY;
    }

    public List<TransportTrip> getActiveTripsForDate(IScope scope, LocalDate date) {
        Set<String> activeTripIds = getActiveTripIdsForDate(scope, date);
        List<TransportTrip> activeTrips = new ArrayList<>();
        for (String tripId : activeTripIds) {
            TransportTrip trip = tripsMap.get(tripId);
            if (trip != null) activeTrips.add(trip);
        }
        return activeTrips;
    }
    


    public void computeDepartureInfo(IScope scope) {
        System.out.println("Starting computeDepartureInfo...");

        // 1. Détermination de la stratégie de filtrage
        LocalDate simulationDate = LocalDate.now(); // Date par défaut
        boolean startingDateDefini = false;
        boolean useAllTrips = false;

        try {
            Object startingDateObj = scope != null ? scope.getGlobalVarValue("starting_date") : null;
            
         // LOGS DEBUG CAS 3
            System.out.println("DEBUG CAS 3 - starting_date check:");
            System.out.println("   → scope: " + scope);
            System.out.println("   → scope != null: " + (scope != null));
            System.out.println("   → startingDateObj: " + startingDateObj);
            System.out.println("   → startingDateObj == null: " + (startingDateObj == null));
            if (startingDateObj != null) {
                System.out.println("   → startingDateObj type: " + startingDateObj.getClass());
                System.out.println("   → startingDateObj toString: '" + startingDateObj.toString() + "'");
            }
            
            boolean isDefaultDate = false;
            simulationDate = null; // reset avant

            if (startingDateObj != null) {
                if (startingDateObj instanceof IDate) {
                    IDate gamaDate = (IDate) startingDateObj;
                    LocalDateTime localDateTime = gamaDate.getLocalDateTime();
                    LocalDate dateValue = localDateTime.toLocalDate();

                    // Détecter la date par défaut de GAML (1970-01-01)
                    if (dateValue.equals(LocalDate.of(1970, 1, 1))) {
                        isDefaultDate = true;
                        System.out.println("[INFO] Date par défaut GAML détectée (1970-01-01) → CAS 3 forcé");
                    } else {
                        simulationDate = dateValue;
                    }
                } else if (startingDateObj instanceof java.util.Date) {
                    java.util.Date date = (java.util.Date) startingDateObj;
                    simulationDate = date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                } else {
                    // Tentative de parsing en String
                    String dateStr = startingDateObj.toString();
                    if (dateStr.length() >= 10 && dateStr.charAt(4) == '-' && dateStr.charAt(7) == '-') {
                        String datePart = dateStr.substring(0, 10);
                        simulationDate = LocalDate.parse(datePart);
                    }
                }
            }

            if (startingDateObj != null && !isDefaultDate && simulationDate != null) {
                startingDateDefini = true;
                System.out.println("[INFO] starting_date DÉFINI: " + simulationDate);
            } else {
                // CAS 3 : starting_date non défini OU date par défaut
                startingDateDefini = false;
                useAllTrips = true;
                System.out.println("[INFO] starting_date NON DÉFINI → TOUS LES TRIPS SERONT UTILISÉS");
            }
        } catch (Exception e) {
            System.out.println("[WARNING] Erreur parsing starting_date: " + e.getMessage());
            startingDateDefini = false;
            useAllTrips = true;
            System.out.println("[INFO] Fallback → TOUS LES TRIPS SERONT UTILISÉS");
        }
        // 2. Détermination des trips actifs selon la stratégie
        Set<String> activeTripIds;
        
        if (useAllTrips) {
            // CAS 3 : Utiliser TOUS les trips
            activeTripIds = new HashSet<>(tripsMap.keySet());
            System.out.println("=== CAS 3 : TOUS LES TRIPS UTILISÉS ===");
            System.out.println("Nombre total de trips: " + activeTripIds.size());
        } else {
            // CAS 1 & 2 : Filtrage par date (logique existante)
            activeTripIds = getActiveTripIdsForDate(scope, simulationDate);
            System.out.println("=== CAS 1/2 : FILTRAGE PAR DATE ===");
            System.out.println("Date utilisée: " + simulationDate);
            System.out.println("Trips actifs trouvés: " + activeTripIds.size());
        }
        
        System.out.println("DEBUG Java - activeTripIds.size() = " + activeTripIds.size());
       

        // 3. Traitement des stop_times (identique pour tous les cas)
        List<String[]> stopTimesData = (List<String[]>) gtfsData.get("stop_times.txt");
        IMap<String, Integer> stopTimesHeader = headerMaps.get("stop_times.txt");
        
        if (stopTimesData == null || stopTimesHeader == null) {
            System.err.println("[ERROR] stop_times.txt data or headers are missing!");
            return;
        }

        Integer tripIdIndex = findColumnIndex(stopTimesHeader, "trip_id");
        Integer stopIdIndex = findColumnIndex(stopTimesHeader, "stop_id");
        Integer departureTimeIndex = findColumnIndex(stopTimesHeader, "departure_time");
        Integer stopSequenceIndex = findColumnIndex(stopTimesHeader, "stop_sequence");

        if (tripIdIndex == null || stopIdIndex == null || departureTimeIndex == null || stopSequenceIndex == null) {
            System.err.println("[ERROR] Required columns missing in stop_times.txt!");
            return;
        }

        // 4. Remplissage des trips et stops (avec filtrage conditionnel)
        int totalAdded = 0;
        int totalSkipped = 0;
        int totalMissingTrip = 0;
        int totalFilteredOut = 0; // NOUVEAU compteur
        
        int processedTrips = 0;
        int filteredTrips = 0;

        for (String[] fields : stopTimesData) {
            if (fields == null || fields.length <= Math.max(Math.max(tripIdIndex, stopIdIndex), Math.max(departureTimeIndex, stopSequenceIndex))) {
                totalSkipped++;
                continue;
            }

            try {
                String tripId = fields[tripIdIndex].trim().replace("\"", "").replace("'", "");
                
                
                
                // FILTRAGE CONDITIONNEL selon la stratégie
                if (!useAllTrips && !activeTripIds.contains(tripId)) {
                    totalFilteredOut++;
                    filteredTrips++;
                    continue; // Skip seulement si on filtre par date      
                }
                processedTrips++; 
                String stopId = fields[stopIdIndex].trim().replace("\"", "").replace("'", "");
                String departureTime = fields[departureTimeIndex];
//                int stopSequence = Integer.parseInt(fields[stopSequenceIndex]);

                TransportTrip trip = tripsMap.get(tripId);
                if (trip == null) {
                    totalMissingTrip++;
                    continue;
                }

                trip.addStop(stopId);
                trip.addStopDetail(stopId, departureTime, 0.0);
                totalAdded++;

                TransportStop stop = stopsMap.get(stopId);
                if (stop != null) {
                    int tripRouteType = trip.getRouteType();
                    if (tripRouteType != -1 && stop.getRouteType() == -1) {
                        stop.setRouteType(tripRouteType);
                    }
                    stop.addTripShapePair(tripId, trip.getShapeId()); // maintenant String → OK après 2) et 3)
                }

            } catch (Exception e) {
                System.err.println("[ERROR] Échec traitement ligne : " + Arrays.toString(fields) + " → " + e.getMessage());
            }
        }
        
        System.out.println("DEBUG stop_times boucle:");
        System.out.println("   → Trips processés: " + processedTrips);
        System.out.println("   → Trips filtrés: " + filteredTrips);

        // 5. Résumé avec nouvelles métriques
        System.out.println("Résumé computeDepartureInfo():");
        System.out.println("   → Stratégie: " + (useAllTrips ? "TOUS LES TRIPS" : "FILTRAGE PAR DATE"));
        System.out.println("   → starting_date défini: " + startingDateDefini);
        if (!useAllTrips) {
            System.out.println("   → Date de simulation: " + simulationDate);
            System.out.println("   → Trips actifs trouvés: " + activeTripIds.size());
        }
        System.out.println("   → Stops ajoutés dans trips : " + totalAdded);
        System.out.println("   → Lignes stop_times ignorées (incomplètes) : " + totalSkipped);
        System.out.println("   → tripId non trouvés dans tripsMap : " + totalMissingTrip);
        System.out.println("   → Trips filtrés par date : " + totalFilteredOut);

        // 6. Création des departureTripsInfo (identique)
        IMap<String, IList<IPair<String, String>>> departureTripsInfo = GamaMapFactory.create(Types.STRING, Types.LIST);
        
        // IMPORTANT : Utiliser la même logique de filtrage ici
        Set<String> tripsToProcess = useAllTrips ? tripsMap.keySet() : activeTripIds;
        
        for (String tripId : tripsToProcess) {
            TransportTrip trip = tripsMap.get(tripId);
            if (trip == null) continue;
            
            IList<String> stopsInOrder = trip.getStopsInOrder();
            IList<IMap<String, Object>> stopDetails = trip.getStopDetails();
            IList<IPair<String, String>> stopPairs = GamaListFactory.create(Types.PAIR);

            if (stopsInOrder.isEmpty() || stopDetails.size() != stopsInOrder.size()) continue;

            for (int i = 0; i < stopsInOrder.size(); i++) {
                String stopId = stopsInOrder.get(i);
                String departureTime = stopDetails.get(i).get("departureTime").toString();
                String departureInSeconds = convertTimeToSeconds(departureTime);
                stopPairs.add(GamaPairFactory.createWith(stopId, departureInSeconds, Types.STRING, Types.STRING));
            }
            departureTripsInfo.put(tripId, stopPairs);
        }


     // 6. Détermination des stops de départ : prendre le plus petit stop_sequence par trip
        Map<String, List<String>> stopToTripIds = new HashMap<>();
        Set<String> seenTripSignatures = new HashSet<>();

        Map<String, String> tripToFirstStop = new HashMap<>();
        Map<String, String> tripToFirstStopTime = new HashMap<>();
        Map<String, Integer> tripToMinSeq = new HashMap<>();

        int tripsFiltresDansStopsDepart = 0;
        int tripsTraitesDansStopsDepart = 0;

        for (String[] fields : stopTimesData) {
            if (fields == null || fields.length <= Math.max(Math.max(tripIdIndex, stopIdIndex),
                                                            Math.max(departureTimeIndex, stopSequenceIndex))) {
                continue;
            }

            try {
                String tripId = fields[tripIdIndex].trim().replace("\"","").replace("'","");
                if (!useAllTrips && !activeTripIds.contains(tripId)) { tripsFiltresDansStopsDepart++; continue; }
                if (useAllTrips && !tripsMap.containsKey(tripId))     { tripsFiltresDansStopsDepart++; continue; }

                String stopId = fields[stopIdIndex].trim().replace("\"","").replace("'","");
                String departureTime = fields[departureTimeIndex];
                int seq;
                try {
                    seq = Integer.parseInt(fields[stopSequenceIndex].trim());
                } catch (Exception ex) {
                    // si stop_sequence manquant ou non numérique, on ignore cette ligne
                    continue;
                }

                tripsTraitesDansStopsDepart++;

                Integer curMin = tripToMinSeq.get(tripId);
                if (curMin == null || seq < curMin) {
                    // nouveau minimum
                    tripToMinSeq.put(tripId, seq);
                    tripToFirstStop.put(tripId, stopId);
                    tripToFirstStopTime.put(tripId, convertTimeToSeconds(departureTime));
                } else if (curMin != null && seq == curMin) {
                    // égalité : garder le départ le plus tôt
                    String curTime = tripToFirstStopTime.get(tripId);
                    String newTime = convertTimeToSeconds(departureTime);
                    if (curTime == null || Integer.parseInt(newTime) < Integer.parseInt(curTime)) {
                        tripToFirstStop.put(tripId, stopId);
                        tripToFirstStopTime.put(tripId, newTime);
                    }
                }

            } catch (Exception e) {
                // on ignore les erreurs de parsing ici
            }
        }

        System.out.println("DEBUG stops de départ:");
        System.out.println("   → Trips traités pour stops départ: " + tripsTraitesDansStopsDepart);
        System.out.println("   → Trips filtrés pour stops départ: " + tripsFiltresDansStopsDepart);
        System.out.println("   → Stops de départ identifiés: " + tripToFirstStop.size());


     // Utiliser les vrais stops de départ pour créer stopToTripIds
     for (String tripId : departureTripsInfo.keySet()) {
         IList<IPair<String, String>> stopPairs = departureTripsInfo.get(tripId);
         if (stopPairs == null || stopPairs.isEmpty()) continue;

         // Utiliser le stop avec stop_sequence = 1 si disponible
         String firstStopId = tripToFirstStop.get(tripId);
         String departureTime = tripToFirstStopTime.get(tripId);
         
         // Fallback : si pas de stop_sequence = 1, utiliser le premier dans la liste
         if (firstStopId == null) {
             firstStopId = stopPairs.get(0).key();
             departureTime = stopPairs.get(0).value();
             System.out.println("[WARNING] Trip " + tripId + " n'a pas de stop_sequence=1, utilise le premier stop rencontré: " + firstStopId);
         }

         // Créer la signature pour éviter les doublons
         StringBuilder stopSequence = new StringBuilder();
         for (IPair<String, String> pair : stopPairs) {
             stopSequence.append(pair.key()).append(";");
         }
         String signature = firstStopId + "_" + departureTime + "_" + stopSequence;

         if (seenTripSignatures.contains(signature)) continue;
         seenTripSignatures.add(signature);
         stopToTripIds.computeIfAbsent(firstStopId, k -> new ArrayList<>()).add(tripId);
     }

     // 7. Affectation dans chaque stop + tri + comptage
     for (Map.Entry<String, List<String>> entry : stopToTripIds.entrySet()) {
         String stopId = entry.getKey();
         List<String> tripIds = entry.getValue();

         tripIds.sort((id1, id2) -> {
             String t1 = tripToFirstStopTime.getOrDefault(id1, departureTripsInfo.get(id1).get(0).value());
             String t2 = tripToFirstStopTime.getOrDefault(id2, departureTripsInfo.get(id2).get(0).value());
             return Integer.compare(Integer.parseInt(t1), Integer.parseInt(t2));
         });

         TransportStop stop = stopsMap.get(stopId);
         if (stop == null) continue;
         stop.ensureDepartureTripsInfo();
         for (String tripId : tripIds) {
             IList<IPair<String, String>> pairs = departureTripsInfo.get(tripId);
             stop.addStopPairs(tripId, pairs);
         }
         stop.setTripNumber(stop.getDepartureTripsInfo().size());
     }

     // 8. Résumé final
     int nbStopsAvecTrips = 0;
     for (TransportStop stop : stopsMap.values()) {
         if (stop.getDepartureTripsInfo() != null && !stop.getDepartureTripsInfo().isEmpty()) {
             nbStopsAvecTrips++;
         }
     }
     System.out.println("Nombre de stops avec departureTripsInfo non vide : " + nbStopsAvecTrips);
     System.out.println("Nombre de trips au total dans tripsMap : " + tripsMap.size());
     System.out.println("Nombre de stops de départ identifiés (stop_sequence=1) : " + tripToFirstStop.size());
     System.out.println("computeDepartureInfo completed successfully.");
 }


 private Set<String> getActiveTripIdsForDate(IScope scope, LocalDate date) {
	    System.out.println("\n=== DÉBUT getActiveTripIdsForDate ===");
	    System.out.println("Recherche trips actifs pour la date: " + date);
	    System.out.println("Jour de la semaine: " + date.getDayOfWeek());
	    System.out.println("Format GTFS: " + date.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")));
	    
	    Set<String> validTripIds = new HashSet<>();
	    Map<String, String> tripIdToServiceId = new HashMap<>();

	    // 1. Construction de la map trip -> service_id
	    System.out.println("\n--- Phase 1: Lecture trips.txt ---");
	    List<String[]> tripsData = (List<String[]>) gtfsData.get("trips.txt");
	    IMap<String, Integer> tripsHeader = headerMaps.get("trips.txt");

	    if (tripsData == null || tripsHeader == null) {
	        System.err.println("[ERROR] trips.txt data or headers are missing!");
	        return validTripIds;
	    }

	    Integer tripIdIdx = findColumnIndex(tripsHeader, "trip_id");
	    Integer serviceIdIdx = findColumnIndex(tripsHeader, "service_id");
	    if (tripIdIdx == null || serviceIdIdx == null) {
	        System.err.println("[ERROR] trip_id or service_id column missing in trips.txt!");
	        System.err.println("   → trip_id index: " + tripIdIdx);
	        System.err.println("   → service_id index: " + serviceIdIdx);
	        return validTripIds;
	    }

	    int tripsProcessed = 0;
	    int tripsIgnored = 0;
	    for (String[] fields : tripsData) {
	        // Ignore les lignes vides ou mal formées
	        if (fields.length > Math.max(tripIdIdx, serviceIdIdx)) {
	            tripIdToServiceId.put(fields[tripIdIdx].trim().replace("\"", ""), fields[serviceIdIdx].trim().replace("\"", ""));
	            tripsProcessed++;
	        } else {
	            tripsIgnored++;
	        }
	    }
	    System.out.println("trips.txt traitement:");
	    System.out.println("   → Trips traités: " + tripsProcessed);
	    System.out.println("   → Trips ignorés: " + tripsIgnored);
	    System.out.println("   → Services uniques: " + tripIdToServiceId.values().stream().distinct().count());

	    // 2. Vérification des fichiers calendrier
	    System.out.println("\n--- Phase 2: Vérification fichiers calendrier ---");
	    List<String[]> calendarData = (List<String[]>) gtfsData.get("calendar.txt");
	    List<String[]> calendarDatesData = (List<String[]>) gtfsData.get("calendar_dates.txt");
	    boolean hasCalendar = (calendarData != null && !calendarData.isEmpty());
	    boolean hasCalendarDates = (calendarDatesData != null && !calendarDatesData.isEmpty());

	    System.out.println("Disponibilité fichiers:");
	    System.out.println("   → calendar.txt: " + (hasCalendar ? "(" + calendarData.size() + " lignes)" : "X"));
	    System.out.println("   → calendar_dates.txt: " + (hasCalendarDates ? " (" + calendarDatesData.size() + " lignes)" : "X"));

	    Set<String> activeServiceIds = new HashSet<>();
	    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
	    String dayOfWeek = date.getDayOfWeek().toString().toLowerCase();
	    String dateString = date.format(formatter);

	    System.out.println("Paramètres recherche:");
	    System.out.println("   → Date: " + dateString);
	    System.out.println("   → Jour: " + dayOfWeek);

	    // 3. Traitement calendar.txt
	    if (hasCalendar) {
	        System.out.println("\n--- Phase 3: Traitement calendar.txt ---");
	        IMap<String, Integer> calendarHeader = headerMaps.get("calendar.txt");
	        if (calendarHeader == null) {
	            System.err.println("[ERROR] calendar.txt headers missing!");
	        } else {
	            try {
	                Integer serviceIdIdxCal = findColumnIndex(calendarHeader, "service_id");
	                Integer startIdx = findColumnIndex(calendarHeader, "start_date");
	                Integer endIdx = findColumnIndex(calendarHeader, "end_date");
	                Integer dayIdx = findColumnIndex(calendarHeader, dayOfWeek);
	                
	                System.out.println("Index des colonnes:");
	                System.out.println("   → service_id: " + serviceIdIdxCal);
	                System.out.println("   → start_date: " + startIdx);
	                System.out.println("   → end_date: " + endIdx);
	                System.out.println("   → " + dayOfWeek + ": " + dayIdx);
	                
	                if (serviceIdIdxCal == null || startIdx == null || endIdx == null || dayIdx == null) {
	                    System.err.println("[ERROR] Some required columns are missing in calendar.txt!");
	                } else {
	                    int servicesActifs = 0;
	                    int servicesInactifs = 0;
	                    int servicesHorsPeriode = 0;
	                    int servicesJourInactif = 0;
	                    
	                    for (String[] fields : calendarData) {
	                        if (fields.length <= Math.max(Math.max(serviceIdIdxCal, startIdx), Math.max(endIdx, dayIdx))) continue;
	                        
	                        try {
	                            String serviceId = fields[serviceIdIdxCal].trim().replace("\"", "");
	                            LocalDate start = LocalDate.parse(fields[startIdx], formatter);
	                            LocalDate end = LocalDate.parse(fields[endIdx], formatter);
	                            boolean dayActive = fields[dayIdx].equals("1");
	                            boolean inPeriod = !date.isBefore(start) && !date.isAfter(end);
	                            boolean runsToday = dayActive && inPeriod;
	                            
	                            if (runsToday) {
	                                activeServiceIds.add(serviceId);
	                                servicesActifs++;
	                            } else {
	                                servicesInactifs++;
	                                if (!inPeriod) servicesHorsPeriode++;
	                                if (!dayActive) servicesJourInactif++;
	                            }
	                        } catch (Exception e) {
	                            System.err.println("Erreur ligne calendar.txt: " + Arrays.toString(fields) + " -> " + e.getMessage());
	                        }
	                    }
	                    
	                    System.out.println("Résultats calendar.txt pour " + date + ":");
	                    System.out.println("   → Services actifs: " + servicesActifs);
	                    System.out.println("   → Services inactifs: " + servicesInactifs);
	                    System.out.println("     ↳ Hors période: " + servicesHorsPeriode);
	                    System.out.println("     ↳ Jour inactif: " + servicesJourInactif);
	                }
	            } catch (Exception e) {
	                System.err.println("[ERROR] Processing calendar.txt failed: " + e.getMessage());
	                e.printStackTrace();
	            }
	        }
	    }

	    // 4. Traitement calendar_dates.txt
	    if (hasCalendarDates) {
	        System.out.println("\n--- Phase 4: Traitement calendar_dates.txt ---");
	        IMap<String, Integer> calDatesHeader = headerMaps.get("calendar_dates.txt");
	        if (calDatesHeader == null) {
	            System.err.println("[ERROR] calendar_dates.txt headers missing!");
	        } else {
	            try {
	                Integer serviceIdIdxCal = findColumnIndex(calDatesHeader, "service_id");
	                Integer dateIdx = findColumnIndex(calDatesHeader, "date");
	                Integer exceptionTypeIdx = findColumnIndex(calDatesHeader, "exception_type");
	                
	                if (serviceIdIdxCal == null || dateIdx == null || exceptionTypeIdx == null) {
	                    System.err.println("[ERROR] Some required columns are missing in calendar_dates.txt!");
	                } else {
	                    int ajouts = 0;
	                    int suppressions = 0;
	                    int datesNonCorrespondantes = 0;
	                    
	                    for (String[] fields : calendarDatesData) {
	                        if (fields.length <= Math.max(Math.max(serviceIdIdxCal, dateIdx), exceptionTypeIdx)) continue;
	                        
	                        try {
	                            String serviceId = fields[serviceIdIdxCal].trim().replace("\"", "");
	                            LocalDate exceptionDate = LocalDate.parse(fields[dateIdx], formatter);
	                            int exceptionType = Integer.parseInt(fields[exceptionTypeIdx]);
	                            
	                            if (exceptionDate.equals(date)) {
	                                if (exceptionType == 1) {
	                                    activeServiceIds.add(serviceId);
	                                    ajouts++;
	                
	                                }
	                                if (exceptionType == 2) {
	                                    boolean wasActive = activeServiceIds.remove(serviceId);
	                                    suppressions++;
	                                    System.out.println("➖ Service supprimé: " + serviceId + " (exception_type=2, était actif: " + wasActive + ")");
	                                }
	                            } else {
	                                datesNonCorrespondantes++;
	                            }
	                        } catch (Exception e) {
	                            System.err.println("Erreur ligne calendar_dates.txt: " + Arrays.toString(fields) + " -> " + e.getMessage());
	                        }
	                    }
	                    
	                    System.out.println("Résultats calendar_dates.txt:");
	                    System.out.println("   → Services ajoutés (type=1): " + ajouts);
	                    System.out.println("   → Services supprimés (type=2): " + suppressions);
	                    System.out.println("   → Dates non correspondantes: " + datesNonCorrespondantes);
	                }
	            } catch (Exception e) {
	                System.err.println("[ERROR] Processing calendar_dates.txt failed: " + e.getMessage());
	                e.printStackTrace();
	            }
	        }
	    }

	    // 5. Conversion services -> trips
	    System.out.println("\n--- Phase 5: Conversion services -> trips ---");
	    System.out.println("Services actifs identifiés: " + activeServiceIds.size());
	    if (activeServiceIds.size() <= 10) {
	        System.out.println("Services actifs: " + activeServiceIds);
	    }

	    int tripsActifs = 0;
	    for (Map.Entry<String, String> e : tripIdToServiceId.entrySet()) {
	        if (activeServiceIds.contains(e.getValue())) {
	            validTripIds.add(e.getKey());
	            tripsActifs++;
	        }
	    }
	    
	    System.out.println("Conversion résultat:");
	    System.out.println("   → Trips actifs trouvés: " + tripsActifs);

	    // 6. FALLBACK SI AUCUN TRIP
	    if (validTripIds.isEmpty()) {
	        System.err.println("\n [WARNING] AUCUN TRIP ACTIF pour la date: " + date);
	        System.out.println("[FALLBACK CAS 2] Recherche d'un jour équivalent dans GTFS...");
	        
	        LocalDate altDate = findFirstDateWithSameWeekDay(date);
	        if (altDate != null && !altDate.equals(date)) {
	            System.out.println("[FALLBACK CAS 2] Jour équivalent trouvé: " + altDate);
	            Set<String> fallbackTrips = getActiveTripIdsForDate(scope, altDate);
	            System.out.println("[FALLBACK CAS 2] Trips récupérés: " + fallbackTrips.size());
	            return fallbackTrips;
	        } else {
	            System.err.println("[FALLBACK CAS 2] No matching weekday found in GTFS.");
	            // NE PAS faire de fallback vers tous les trips ici
	            // Laissez le CAS 3 être géré dans computeDepartureInfo
	        }
	    }
	    
	    return validTripIds;
	}

	private LocalDate findFirstDateWithSameWeekDay(LocalDate wantedDate) {
	    System.out.println("\n findFirstDateWithSameWeekDay appelée...");
	    System.out.println(" Date recherchée: " + wantedDate + " (" + wantedDate.getDayOfWeek() + ")");
	    
	    List<LocalDate> allDates = new ArrayList<>();
	    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
	    
	    // calendar.txt
	    System.out.println("\n Collecte des dates depuis calendar.txt...");
	    List<String[]> calendarData = (List<String[]>) gtfsData.get("calendar.txt");
	    if (calendarData != null && !calendarData.isEmpty()) {
	        IMap<String, Integer> header = headerMaps.get("calendar.txt");
	        if (header != null) {
	            Integer startIdx = findColumnIndex(header, "start_date");
	            Integer endIdx = findColumnIndex(header, "end_date");
	            if (startIdx != null && endIdx != null) {
	                int periodesTraitees = 0;
	                int datesAjoutees = 0;
	                for (String[] fields : calendarData) {
	                    if (fields.length > endIdx) {
	                        try {
	                            LocalDate start = LocalDate.parse(fields[startIdx], formatter);
	                            LocalDate end = LocalDate.parse(fields[endIdx], formatter);
	                            
	                            System.out.println("   Période: " + start + " → " + end);
	                            
	                            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
	                                allDates.add(d);
	                                datesAjoutees++;
	                            }
	                            periodesTraitees++;
	                        } catch (Exception e) {
	                            System.err.println("Erreur parsing période: " + Arrays.toString(fields));
	                        }
	                    }
	                }
	                System.out.println("calendar.txt:");
	                System.out.println("   → Périodes traitées: " + periodesTraitees);
	                System.out.println("   → Dates ajoutées: " + datesAjoutees);
	            }
	        }
	    } else {
	        System.out.println("calendar.txt non disponible");
	    }
	    
	    // calendar_dates.txt
	    System.out.println("\n Collecte des dates depuis calendar_dates.txt...");
	    List<String[]> calendarDates = (List<String[]>) gtfsData.get("calendar_dates.txt");
	    if (calendarDates != null && !calendarDates.isEmpty()) {
	        IMap<String, Integer> header = headerMaps.get("calendar_dates.txt");
	        if (header != null) {
	            Integer dateIdx = findColumnIndex(header, "date");
	            if (dateIdx != null) {
	                int datesAjoutees = 0;
	                for (String[] fields : calendarDates) {
	                    if (fields.length > dateIdx) {
	                        try {
	                            LocalDate d = LocalDate.parse(fields[dateIdx], formatter);
	                            allDates.add(d);
	                            datesAjoutees++;
	                        } catch (Exception e) {
	                            System.err.println("Erreur parsing date: " + Arrays.toString(fields));
	                        }
	                    }
	                }
	                System.out.println("calendar_dates.txt:");
	                System.out.println("   → Dates ajoutées: " + datesAjoutees);
	            }
	        }
	    } else {
	        System.out.println("calendar_dates.txt non disponible");
	    }
	    
	    System.out.println("\n Total dates collectées: " + allDates.size());
	    
	    // Recherche du premier jour avec le même dayOfWeek
	    System.out.println("Recherche du premier " + wantedDate.getDayOfWeek() + " disponible...");
	    
	    LocalDate firstMatch = null;
	    int correspondances = 0;
	    LocalDate minDate = null;
	    LocalDate maxDate = null;
	    
	    for (LocalDate d : allDates) {
	        // Mise à jour min/max pour debug
	        if (minDate == null || d.isBefore(minDate)) minDate = d;
	        if (maxDate == null || d.isAfter(maxDate)) maxDate = d;
	        
	        if (d.getDayOfWeek().equals(wantedDate.getDayOfWeek())) {
	            correspondances++;
	            if (firstMatch == null || d.isBefore(firstMatch)) {
	                firstMatch = d;
	                System.out.println("      → Nouveau premier match: " + firstMatch);
	            }
	        }
	    }
	    
	    System.out.println("\nRésultat recherche:");
	    System.out.println("   → Période GTFS: " + minDate + " → " + maxDate);
	    System.out.println("   → Correspondances " + wantedDate.getDayOfWeek() + ": " + correspondances);
	    System.out.println("   → Premier match: " + firstMatch);
	    
	    if (firstMatch != null) {
	        System.out.println("Date de fallback choisie: " + firstMatch);
	        System.out.println("   → Écart avec date demandée: " + java.time.temporal.ChronoUnit.DAYS.between(wantedDate, firstMatch) + " jours");
	    } else {
	        System.out.println("Aucun jour équivalent trouvé");
	    }
	    
	    return firstMatch;
	}

    
    public java.time.LocalDate getStartingDate() {
        java.time.LocalDate minDate = null;
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");

        // calendar.txt
        List<String[]> calendarData = (List<String[]>) gtfsData.get("calendar.txt");
        if (calendarData != null && !calendarData.isEmpty()) {
            IMap<String, Integer> header = headerMaps.get("calendar.txt");
            if (header != null) {
                Integer startIdx = findColumnIndex(header, "start_date");
                if (startIdx != null) {
                    for (String[] fields : calendarData) {
                        if (fields.length > startIdx) {
                            java.time.LocalDate d = java.time.LocalDate.parse(fields[startIdx], formatter);
                            if (minDate == null || d.isBefore(minDate)) minDate = d;
                        }
                    }
                }
            }
        }

        // calendar_dates.txt
        List<String[]> calendarDates = (List<String[]>) gtfsData.get("calendar_dates.txt");
        if (calendarDates != null && !calendarDates.isEmpty()) {
            IMap<String, Integer> header = headerMaps.get("calendar_dates.txt");
            if (header != null) {
                Integer dateIdx = findColumnIndex(header, "date");
                if (dateIdx != null) {
                    for (String[] fields : calendarDates) {
                        if (fields.length > dateIdx) {
                            java.time.LocalDate d = java.time.LocalDate.parse(fields[dateIdx], formatter);
                            if (minDate == null || d.isBefore(minDate)) minDate = d;
                        }
                    }
                }
            }
        }
        return minDate;
    }


    public java.time.LocalDate getEndingDate() {
        java.time.LocalDate maxDate = null;
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");

        // calendar.txt
        List<String[]> calendarData = (List<String[]>) gtfsData.get("calendar.txt");
        if (calendarData != null && !calendarData.isEmpty()) {
            IMap<String, Integer> header = headerMaps.get("calendar.txt");
            if (header != null) {
                Integer endIdx = findColumnIndex(header, "end_date");
                if (endIdx != null) {
                    for (String[] fields : calendarData) {
                        if (fields.length > endIdx) {
                            java.time.LocalDate d = java.time.LocalDate.parse(fields[endIdx], formatter);
                            if (maxDate == null || d.isAfter(maxDate)) maxDate = d;
                        }
                    }
                }
            }
        }
        // calendar_dates.txt
        List<String[]> calendarDates = (List<String[]>) gtfsData.get("calendar_dates.txt");
        if (calendarDates != null && !calendarDates.isEmpty()) {
            IMap<String, Integer> header = headerMaps.get("calendar_dates.txt");
            if (header != null) {
                Integer dateIdx = findColumnIndex(header, "date");
                if (dateIdx != null) {
                    for (String[] fields : calendarDates) {
                        if (fields.length > dateIdx) {
                            java.time.LocalDate d = java.time.LocalDate.parse(fields[dateIdx], formatter);
                            if (maxDate == null || d.isAfter(maxDate)) maxDate = d;
                        }
                    }
                }
            }
        }
        return maxDate;
    }

    

    
    // Method to convert departureTime of stops into seconds
    private String convertTimeToSeconds(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            int totalSeconds = (hours * 3600 + minutes * 60 + seconds);
            return String.valueOf(totalSeconds);
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to convert time: " + timeStr + " -> " + e.getMessage());
            return "0";  // fallback
        }
    }
    
   
}