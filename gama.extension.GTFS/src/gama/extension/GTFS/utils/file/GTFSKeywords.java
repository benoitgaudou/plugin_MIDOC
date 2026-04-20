package gama.extension.GTFS.utils.file;

public interface GTFSKeywords {
	// Constants for GTFS file names
	public static final String FILE_ROUTES       = "routes.txt";
	public static final String FILE_TRIPS        = "trips.txt";
	public static final String FILE_STOPS        = "stops.txt";
	public static final String FILE_STOP_TIMES   = "stop_times.txt";
	public static final String FILE_SHAPES       = "shapes.txt";
	public static final String FILE_CALENDAR     = "calendar.txt";
	public static final String FILE_CALENDAR_DATES = "calendar_dates.txt";
	
	// Constants for column names
	public static final String COL_ROUTE_ID      = "route_id";
	public static final String COL_TRIP_ID       = "trip_id";
	public static final String COL_SERVICE_ID    = "service_id";
	public static final String COL_SHAPE_ID      = "shape_id";
	public static final String COL_STOP_ID       = "stop_id";

	// Column names — stop_times.txt
	public static final String COL_STOP_SEQUENCE   = "stop_sequence";
	public static final String COL_DEPARTURE_TIME  = "departure_time";

	// Column names — routes.txt
	public static final String COL_ROUTE_TYPE      = "route_type";

	// Column names — stops.txt
	public static final String COL_STOP_NAME       = "stop_name";
	public static final String COL_STOP_LAT        = "stop_lat";
	public static final String COL_STOP_LON        = "stop_lon";

	// Column names — shapes.txt
	public static final String COL_SHAPE_PT_LAT    = "shape_pt_lat";
	public static final String COL_SHAPE_PT_LON    = "shape_pt_lon";

	// Column names — calendar.txt / calendar_dates.txt
	public static final String COL_START_DATE      = "start_date";
	public static final String COL_END_DATE        = "end_date";
	public static final String COL_DATE            = "date";
	public static final String COL_EXCEPTION_TYPE  = "exception_type";

	// Misc constants
	public static final String GTFS_DATE_FORMAT       = "yyyyMMdd";
	public static final String GAML_VAR_STARTING_DATE = "starting_date";
	public static final String KEY_DEPARTURE_TIME      = "departureTime";
	
}
