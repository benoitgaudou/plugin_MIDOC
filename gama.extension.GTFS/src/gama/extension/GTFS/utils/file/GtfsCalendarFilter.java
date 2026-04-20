package gama.extension.GTFS.utils.file;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.logging.Logger;

import gama.api.runtime.scope.IScope;
import gama.api.types.date.GamaDateFactory;
import gama.api.types.date.IDate;

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
    
    public static IDate GTFSTimeToDate(IScope scope, String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            int seconds = Integer.parseInt(parts[2]);
//            IDate date = GamaDateFactory.createFromString(scope, timeStr);
                        
            IDate date = GamaDateFactory.createFromIDate(scope, scope.getSimulation().getClock().getCurrentDate())
            		.with(ChronoField.HOUR_OF_DAY, hours % 24) // Wrap around hours to fit in a day
            		.with(ChronoField.MINUTE_OF_HOUR, minutes)
            		.with(ChronoField.SECOND_OF_MINUTE, seconds)
            		.plus( (double)(hours / 24), ChronoUnit.DAYS);
            		
			return date;	
        } catch (Exception e) {
        	LOGGER.severe("[ERROR] Failed to convert time: " + timeStr + " -> " + e.getMessage());
            return GamaDateFactory.createFromString(scope,"1970-01-01T00:00:00");
        }
    }


}
