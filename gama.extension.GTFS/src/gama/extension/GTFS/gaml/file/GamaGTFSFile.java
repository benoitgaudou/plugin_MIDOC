package gama.extension.GTFS.gaml.file;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import gama.annotations.doc;
import gama.annotations.file;
import gama.annotations.example;
import gama.annotations.support.IConcept;
import gama.api.exceptions.GamaRuntimeException;
import gama.api.gaml.types.IType;
import gama.api.gaml.types.Types;
import gama.api.runtime.scope.IScope;
import gama.api.types.file.GamaFile;
import gama.api.types.list.GamaListFactory;
import gama.api.types.list.IList;
import gama.api.types.map.GamaMapFactory;
import gama.api.types.map.IMap;
import gama.api.utils.geometry.GamaEnvelopeFactory;
import gama.api.utils.geometry.IEnvelope;
import gama.extension.GTFS.gaml.file.object.DepartureInfos;
import gama.extension.GTFS.gaml.file.object.TransportRoute;
import gama.extension.GTFS.gaml.file.object.TransportShape;
import gama.extension.GTFS.gaml.file.object.TransportStop;
import gama.extension.GTFS.gaml.file.object.TransportTrip;
import gama.extension.GTFS.utils.file.GTFSKeywords;
import gama.extension.GTFS.utils.file.GtfsCsvReader;

/**
 * Reading and processing GTFS files in GAMA. This class reads multiple GTFS
 * files and creates TransportRoute, TransportTrip, and TransportStop objects.
 */
@file(name = "gtfs", extensions = {
		"txt" }, buffer_type = IType.LIST, buffer_content = IType.STRING, buffer_index = IType.INT, concept = {
				IConcept.FILE }, doc = @doc("GTFS files represent public transportation data in CSV format, typically with the '.txt' extension."))
public class GamaGTFSFile extends GamaFile<IList<String>, String> {

	private static final Logger LOGGER = Logger.getLogger(GamaGTFSFile.class.getName());

	// Required files for GTFS data
	private static final String[] REQUIRED_FILES = { GTFSKeywords.FILE_ROUTES, GTFSKeywords.FILE_TRIPS,
			GTFSKeywords.FILE_STOP_TIMES, GTFSKeywords.FILE_STOPS };

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
//	private IMap<String, Integer> shapeRouteTypeMap;
	private Map<String, Character> fileSeparators = new HashMap<>();

//	private boolean shapesTxtPresent = false;
//	private IMap<String, Integer> routeTypeMapGlobal;
//	private IScope initScope;

	/**
	 * Constructor for reading GTFS files.
	 *
	 * @param scope    The simulation context in GAMA.
	 * @param pathName The directory path containing GTFS files.
	 * @throws GamaRuntimeException If an error occurs while loading the files.
	 */
	@doc(value = "This constructor allows loading GTFS files from a specified directory.", examples = {
			@example(value = "GTFS_reader gtfs <- GTFS_reader(scope, \"path_to_gtfs_directory\");") })
	public GamaGTFSFile(final IScope scope, final String pathName) throws GamaRuntimeException {
		super(scope, pathName);
//		this.initScope = scope;

		// Debug: Print the GTFS path in the GAMA console
		LOGGER.info("Loading GTFS files from: " + pathName);
		loadGtfsFiles(scope);
		LOGGER.info("File loading completed.");

		// Create transport objects
		LOGGER.info("Creating transport objects...");
		createTransportObjects(scope);
		LOGGER.info("Transport object creation completed.");

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
			File[] files = folder.listFiles(); // List of files in the folder
			if (files != null) {
				for (File file : files) {
					if (file.isFile() && file.getName().endsWith(".txt")) {
						// 1. Détecte le séparateur
						char separator = GtfsCsvReader.detectSeparator(file);
						// 2. Mémorise le séparateur pour ce fichier
						fileSeparators.put(file.getName(), separator);
						// 3. Utilise OpenCSV avec le séparateur détecté
						Map<String, Integer> headerMap = new HashMap<>();
						// 3.1 Lit le fichier CSV et récupère le contenu
						List<String[]> fileContent = GtfsCsvReader.readCsvFileOpenCSV(file, headerMap);
						// 4. Stocke le contenu du fichier et le header dans les maps
						gtfsData.put(file.getName(), fileContent);
						IMap<String, Integer> headerIMap = GamaMapFactory.wrap(Types.STRING, Types.INT, headerMap);
						headerMaps.put(file.getName(), headerIMap);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.severe("Error while loading GTFS files: " + e.getMessage());
			throw GamaRuntimeException.create(e, scope);
		}
		LOGGER.info("All GTFS files have been loaded.");
	}
	
	
	private void createTransportObjects(IScope scope) {
		System.out.println("Starting transport object creation...");

		/**********************
		* 1. Creating of TransportRoute objects
		* ==> init routesMap
		***********************/
		List<String[]> routesData = gtfsData.get(GTFSKeywords.FILE_ROUTES);
		IMap<String, Integer> routesHeader = headerMaps.get(GTFSKeywords.FILE_ROUTES);
		routesMap = TransportRoute.createTransportStopsFromGtfs(scope, routesData, routesHeader);

		/**********************
		* 2. Filter stops to keep only the ones used in stop_times.txt file
		* ==> usedStopIds
		***********************/
		Set<String> usedStopIds = new HashSet<>();
		List<String[]> stopTimesData = gtfsData.get(GTFSKeywords.FILE_STOP_TIMES);
		IMap<String, Integer> stopTimesHeader = headerMaps.get(GTFSKeywords.FILE_STOP_TIMES);

		if (stopTimesData != null && stopTimesHeader != null && stopTimesHeader.containsKey(GTFSKeywords.COL_STOP_ID)) {
			Integer stopIdIndex = stopTimesHeader.get(GTFSKeywords.COL_STOP_ID);
			if (stopIdIndex == null)
				throw new RuntimeException("stop_id column not found in stop_times.txt!");
			for (String[] fields : stopTimesData) {
				usedStopIds.add(clean(fields[stopIdIndex]));
			}
		}

		/**********************
		* 3. Creation of stops (only the ones used in stop_times.txt)
		* ==> init stopsMap
		***********************/ 
		List<String[]> stopsData = gtfsData.get(GTFSKeywords.FILE_STOPS);
		IMap<String, Integer> headerIMap = headerMaps.get(GTFSKeywords.FILE_STOPS);
		stopsMap = TransportStop.createTransportStopsFromGtfs(scope, stopsData, headerIMap, usedStopIds);		

		/**********************
		* 4. Creation of trips  
		* ==> init tripsMap
		***********************/ 
		List<String[]> tripsData = gtfsData.get(GTFSKeywords.FILE_TRIPS);
		IMap<String, Integer> tripsHeaderMap = headerMaps.get(GTFSKeywords.FILE_TRIPS);
		tripsMap = TransportTrip.createTransportTripsFromGtfs(scope, tripsData, tripsHeaderMap, routesMap);
			
		/**********************
		* 5. computeDepartureInfo (communs)
		* ==> update trips and stops
		***********************/ 
		LOGGER.info("[INFO] Calling computeDepartureInfo...");
		DepartureInfos.computeDepartureInfo(scope, tripsMap, stopsMap, gtfsData, headerMaps, null, null);
		
		/**********************
		* 6. Creation of shapes 
		* ==> init shapesMap
		***********************/ 		
		List<String[]> shapesData = gtfsData.get(GTFSKeywords.FILE_SHAPES);
		IMap<String, Integer> shapesHeaderMap = headerMaps.get(GTFSKeywords.FILE_SHAPES);
			
		if (shapesData != null && shapesHeaderMap != null && !shapesData.isEmpty()) {
			shapesMap = TransportShape.createTransportShapesFromGtfs(scope, shapesData, shapesHeaderMap);
		} else {
			shapesMap = TransportShape.createTransportShapesWithoutGtfs(scope, stopsMap, tripsMap);			
		}

		/**********************
		* 7. Assign routeType to each trip and shape 
		* ==> update shapes and trips
		***********************/ 		

		for (TransportTrip trip : tripsMap.values()) {
			if (routesMap.containsKey(trip.getRouteId())) {
				trip.setRouteType(routesMap.get(trip.getRouteId()).getType());	
				shapesMap.get(trip.getShapeId()).setRouteType(routesMap.get(trip.getRouteId()).getType());
			}
		}
	}

//	public boolean isShapesTxtPresent() {
//		return shapesTxtPresent;
//	}

	/**
	 * Method to retrieve the list of stops (TransportStop) from stopsMap.
	 * 
	 * @return List of transport stops
	 */
	public List<TransportStop> getStops() {
		return new ArrayList<>(stopsMap.values());
	}

	/**
	 * Method to retrieve the list of shape (TransportShape) from shapesMap.
	 * 
	 * @return List of transport shapes
	 */
	public List<TransportShape> getShapes() {
		return new ArrayList<>(shapesMap.values());
	}


	/**
	 * Method to build fake shapes for trips that don't have an associated shapeId.
	 * This is done lazily when getShapes() is called and shapes.txt is not present.
	 * 
	 * It requires trips and stops.
	 *
	 * @param scope         The simulation context in GAMA.
	 * @param routeTypeMap  A map of routeId to routeType for assigning route types to fake shapes.
	 */
//	private void buildFakeShapesLazily(final IScope scope, final IMap<String, Integer> routeTypeMap) {
//		System.out.println("[LAZY] Building fake shapes now (requested by create transport_shape)...");
//		for (TransportTrip trip : tripsMap.values()) {
//			String tripId = trip.getTripId();
//			String fakeShapeId = trip.getShapeId();
//			if (fakeShapeId == null || fakeShapeId.isEmpty()) {
//				fakeShapeId = "fake_" + tripId;
//				trip.setShapeId(fakeShapeId);
//			}
//			if (shapesMap.containsKey(fakeShapeId))
//				continue;
//
//			List<IPoint> pts = new ArrayList<>();
//			List<String> orderedStops = trip.getStopsInOrder();
//			if (orderedStops == null || orderedStops.isEmpty()) {
//				List<String[]> stopTimesData = gtfsData.get(GTFSKeywords.FILE_STOP_TIMES);
//				IMap<String, Integer> stopTimesHeader = headerMaps.get(GTFSKeywords.FILE_STOP_TIMES);
//				Integer tripIdIdx = findColumnIndex(stopTimesHeader, GTFSKeywords.COL_TRIP_ID);
//				Integer stopIdIdx = findColumnIndex(stopTimesHeader, GTFSKeywords.COL_STOP_ID);
//				Integer seqIdx = findColumnIndex(stopTimesHeader, GTFSKeywords.COL_STOP_SEQUENCE);
//				if (stopTimesData != null && tripIdIdx != null && stopIdIdx != null && seqIdx != null) {
//					List<String[]> lines = new ArrayList<>();
//					for (String[] st : stopTimesData) {
//						if (st != null && st.length > Math.max(tripIdIdx, Math.max(stopIdIdx, seqIdx))) {
//							if (tripId.equals(clean(st[tripIdIdx]))) {
//								lines.add(st);
//							}
//						}
//					}
//					lines.sort((a, b) -> Integer.compare(Integer.parseInt(a[seqIdx].trim()),
//							Integer.parseInt(b[seqIdx].trim())));
//					for (String[] st : lines) {
//						String stopId = clean(st[stopIdIdx]);
//						TransportStop stop = stopsMap.get(stopId);
//						if (stop != null)
////				pts.add(GamaPointFactory.create(stop.getStopLat(), stop.getStopLon()));
//							pts.add(stop.getLocation());
//					}
//				}
//			} else {
//				for (String stopId : orderedStops) {
//					TransportStop st = stopsMap.get(stopId);
//					if (st != null)
////						pts.add(GamaPointFactory.create(st.getStopLat(), st.getStopLon()));
//						pts.add(st.getLocation());
//				}
//			}
//
//			if (pts.size() > 1) {
//				String routeId = trip.getRouteId();
////				TransportShape fake = new TransportShape(fakeShapeId, routeId);
//				TransportShape fake = new TransportShape(fakeShapeId, routeId, GamaListFactory.create(scope, Types.POINT, pts));
//						
////				for (IPoint p : pts) {
////					fake.addPoint(p.getX(), p.getY(), scope);
////				}
//				if (routeTypeMap != null && routeTypeMap.containsKey(routeId)) {
//					fake.setRouteType(routeTypeMap.get(routeId));
//				}
//				fake.setTripId(tripId);
//				shapesMap.put(fakeShapeId, fake);
//			}
//		}
//		LOGGER.info("[LAZY] Fake shapes built: " + shapesMap.size());
//	}

	/**
	 * Method to retrieve the list of trips (TransportTrip) from tripsMap.
	 * 
	 * @return List of transport trips
	 */
	public List<TransportTrip> getTrips() {
		return new ArrayList<>(tripsMap.values());
	}

	/**
	 * Method to retrieve the list of routes (TransportRoute) from routesMap.
	 * 
	 * @return List of transport routes
	 */
	public List<TransportRoute> getRoutes() {
		return new ArrayList<>(routesMap.values());
	}

	/**
	 * Method to verify the directory's validity.
	 *
	 * @param scope The simulation context in GAMA.
	 * @throws GamaRuntimeException If the directory is invalid or does not contain
	 *                              required files.
	 */
	@Override
	protected void checkValidity(final IScope scope) throws GamaRuntimeException {
		LOGGER.info("Starting directory validity check...");

		File folder = getFile(scope);

		if (!folder.exists() || !folder.isDirectory()) {
			throw GamaRuntimeException.error(
					"The provided path for GTFS files is invalid. Ensure it is a directory containing .txt files.",
					scope);
		}
		Set<String> requiredFilesSet = new HashSet<>(Set.of(REQUIRED_FILES));
		LOGGER.info("Required GTFS files: " + requiredFilesSet);
		LOGGER.info("Vérification du dossier GTFS : " + getName(null));
		File[] files = folder.listFiles();
		// System.out.println("Liste des fichiers trouvés : " + Arrays.toString(files));
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
		LOGGER.info("Directory validity check completed.");
	}

	/**
	 * Retrieves the header map for a given file.
	 *
	 * @param fileName The name of the file
	 * @return The header map
	 */
/*	private void createTransportObjectsWithShapes(IScope scope, IMap<String, Integer> routeTypeMap,
			IMap<String, String> shapeRouteMap, IMap<String, Integer> shapeRouteTypeMap) {
		// 1. Création des TransportShape à partir de shapes.txt
		List<String[]> shapesData = gtfsData.get(GTFSKeywords.FILE_SHAPES);
		IMap<String, Integer> headerMap = headerMaps.get(GTFSKeywords.FILE_SHAPES);
		Integer shapeIdIndex = findColumnIndex(headerMap, GTFSKeywords.COL_SHAPE_ID);
		Integer latIndex = findColumnIndex(headerMap, GTFSKeywords.COL_SHAPE_PT_LAT);
		Integer lonIndex = findColumnIndex(headerMap, GTFSKeywords.COL_SHAPE_PT_LON);

		for (String[] fields : shapesData) {

			String shapeId = clean(fields[shapeIdIndex]);
			double lat = Double.parseDouble(fields[latIndex]);
			double lon = Double.parseDouble(fields[lonIndex]);

			TransportShape shape = shapesMap.get(shapeId);
			if (shape == null) {
				shape = new TransportShape(shapeId, "");
				shapesMap.put(shapeId, shape);
			}
			shape.addPoint(lat, lon, scope);

		}

		// 2. Création des trips (avec shapeId réel)
		List<String[]> tripsData = gtfsData.get(GTFSKeywords.FILE_TRIPS);
		IMap<String, Integer> tripsHeaderMap = headerMaps.get(GTFSKeywords.FILE_TRIPS);
		Integer routeIdIndex = findColumnIndex(tripsHeaderMap, GTFSKeywords.COL_ROUTE_ID);
		Integer tripIdIndex = findColumnIndex(tripsHeaderMap, GTFSKeywords.COL_TRIP_ID);
		Integer shapeIdIdx = findColumnIndex(tripsHeaderMap, GTFSKeywords.COL_SHAPE_ID);

		for (String[] fields : tripsData) {
			if (fields == null)
				continue;
			try {
				String routeId = clean(fields[routeIdIndex]);
				String tripId = clean(fields[tripIdIndex]);
				String shapeId = null;
				if (shapeIdIdx != null && fields.length > shapeIdIdx) {
					String raw = clean(fields[shapeIdIdx]);
					if (!raw.isEmpty())
						shapeId = raw;
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
				LOGGER.severe("[ERROR] Invalid trip line in " + GTFSKeywords.FILE_TRIPS + ": "
						+ java.util.Arrays.toString(fields) + " -> " + e.getMessage());
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
*/
	
/*	private void createTripsWithoutShapes(IScope scope, IMap<String, Integer> routeTypeMap) {
		List<String[]> tripsData = gtfsData.get(GTFSKeywords.FILE_TRIPS);
		IMap<String, Integer> tripsHeader = headerMaps.get(GTFSKeywords.FILE_TRIPS);
		Integer routeIdIndex = findColumnIndex(tripsHeader, GTFSKeywords.COL_ROUTE_ID);
		Integer tripIdIndex = findColumnIndex(tripsHeader, GTFSKeywords.COL_TRIP_ID);
		if (tripsData == null || routeIdIndex == null || tripIdIndex == null)
			return;

		for (String[] fields : tripsData) {
			if (fields == null)
				continue;
			try {
				String routeId = clean(fields[routeIdIndex]);
				String tripId = clean(fields[tripIdIndex]);
				String fakeShapeId = "fake_" + tripId;

				TransportTrip trip = tripsMap.get(tripId);
				if (trip == null) {
					trip = new TransportTrip(routeId, "", tripId, 0, fakeShapeId); // shapeId placeholder
					if (routeTypeMap.containsKey(routeId))
						trip.setRouteType(routeTypeMap.get(routeId));
					tripsMap.put(tripId, trip);
				}
			} catch (Exception ignore) {
				LOGGER.warning("Skipping malformed trip line: " + ignore.getMessage());
			}
		}
	}
*/

	/**
	 * Trouve l’index d’une colonne parmi plusieurs possibilités dans le headerMap.
	 * 
	 * @param headerMap La map colonne → index.
	 * @param name      du nom (ex: "stop_id", "stopid"...).
	 * @return L’index si trouvé, sinon null.
	 */
	public static Integer findColumnIndex(Map<String, Integer> headerMap, String name) {
		if (headerMap == null)
			return null;
		for (String col : headerMap.keySet()) {
			if (col.equalsIgnoreCase(name.trim()))
				return headerMap.get(col);
		}
		return null;
	}

	@Override
	protected void fillBuffer(final IScope scope) throws GamaRuntimeException {
		System.out.println("Filling buffer...");
		if (gtfsData == null) {
			LOGGER.info("gtfsData is null, loading GTFS files...");
			loadGtfsFiles(scope);
			LOGGER.info("Finished loading GTFS files.");
		} else
			LOGGER.info("gtfsData is already initialized.");

	}

	@Override
	public IList<String> getAttributes(final IScope scope) {
		LOGGER.info("Retrieving GTFS data attributes...");
		if (gtfsData != null) {
			Set<String> keySet = gtfsData.keySet();
			LOGGER.info("Attributes retrieved: " + keySet);
			return GamaListFactory.createWithoutCasting(Types.STRING, keySet.toArray(new String[0]));
		} else {
			LOGGER.info("gtfsData is null, no attributes to retrieve.");
			return GamaListFactory.createWithoutCasting(Types.STRING);
		}
	}

	@Override
	public IEnvelope computeEnvelope(final IScope scope) {
		// Provide a default implementation or return an empty envelope
		return GamaEnvelopeFactory.EMPTY;
	}

	public java.time.LocalDate getStartingDate() {
		java.time.LocalDate minDate = null;
		java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
				.ofPattern(GTFSKeywords.GTFS_DATE_FORMAT);

		// calendar.txt
		List<String[]> calendarData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR);
		if (calendarData != null && !calendarData.isEmpty()) {
			IMap<String, Integer> header = headerMaps.get(GTFSKeywords.FILE_CALENDAR);
			if (header != null) {
				Integer startIdx = findColumnIndex(header, GTFSKeywords.COL_START_DATE);
				if (startIdx != null) {
					for (String[] fields : calendarData) {
						if (fields.length > startIdx) {
							java.time.LocalDate d = java.time.LocalDate.parse(fields[startIdx], formatter);
							if (minDate == null || d.isBefore(minDate))
								minDate = d;
						}
					}
				}
			}
		}

		// calendar_dates.txt
		List<String[]> calendarDates = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR_DATES);
		if (calendarDates != null && !calendarDates.isEmpty()) {
			IMap<String, Integer> header = headerMaps.get(GTFSKeywords.FILE_CALENDAR_DATES);
			if (header != null) {
				Integer dateIdx = findColumnIndex(header, GTFSKeywords.COL_DATE);
				if (dateIdx != null) {
					for (String[] fields : calendarDates) {
						if (fields.length > dateIdx) {
							java.time.LocalDate d = java.time.LocalDate.parse(fields[dateIdx], formatter);
							if (minDate == null || d.isBefore(minDate))
								minDate = d;
						}
					}
				}
			}
		}
		return minDate;
	}

	public java.time.LocalDate getEndingDate() {
		java.time.LocalDate maxDate = null;
		java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
				.ofPattern(GTFSKeywords.GTFS_DATE_FORMAT);

		// calendar.txt
		List<String[]> calendarData = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR);
		if (calendarData != null && !calendarData.isEmpty()) {
			IMap<String, Integer> header = headerMaps.get(GTFSKeywords.FILE_CALENDAR);
			if (header != null) {
				Integer endIdx = findColumnIndex(header, GTFSKeywords.COL_END_DATE);
				if (endIdx != null) {
					for (String[] fields : calendarData) {
						if (fields.length > endIdx) {
							java.time.LocalDate d = java.time.LocalDate.parse(fields[endIdx], formatter);
							if (maxDate == null || d.isAfter(maxDate))
								maxDate = d;
						}
					}
				}
			}
		}
		// calendar_dates.txt
		List<String[]> calendarDates = (List<String[]>) gtfsData.get(GTFSKeywords.FILE_CALENDAR_DATES);
		if (calendarDates != null && !calendarDates.isEmpty()) {
			IMap<String, Integer> header = headerMaps.get(GTFSKeywords.FILE_CALENDAR_DATES);
			if (header != null) {
				Integer dateIdx = findColumnIndex(header, GTFSKeywords.COL_DATE);
				if (dateIdx != null) {
					for (String[] fields : calendarDates) {
						if (fields.length > dateIdx) {
							java.time.LocalDate d = java.time.LocalDate.parse(fields[dateIdx], formatter);
							if (maxDate == null || d.isAfter(maxDate))
								maxDate = d;
						}
					}
				}
			}
		}
		return maxDate;
	}

	public static String clean(String s) {
		return s == null ? "" : s.trim().replace("\"", "").replace("'", "");
	}

}