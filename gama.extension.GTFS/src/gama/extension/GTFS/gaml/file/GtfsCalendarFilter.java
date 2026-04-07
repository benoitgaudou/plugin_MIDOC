package gama.extension.GTFS.gaml.file;

import java.util.logging.Logger;

public class GtfsCalendarFilter {

	private static final Logger LOGGER = Logger.getLogger(GtfsCalendarFilter.class.getName());	

    // Method to convert departureTime of stops into seconds
    public static String convertTimeToSeconds(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
            int totalSeconds = (hours * 3600 + minutes * 60 + seconds);
            return String.valueOf(totalSeconds);
        } catch (Exception e) {
        	LOGGER.severe("[ERROR] Failed to convert time: " + timeStr + " -> " + e.getMessage());
            return "0";  // fallback
        }
    }

}
