package gama.extension.GTFS.gaml.file.object;

import java.util.List;
import gama.api.types.date.IDate;
import gama.api.types.pair.IPair;


public class TransportTripTimeTable {
	List<IPair<String,IDate>> timetable;
	
	public void addStopTime(String stopId, IDate departureTime) {
		// TODO
	}
	
	public IDate getStartDate() {
		// TODO
	}

	public IDate getEndDate() {
		// TODO
	}

	public IList<IPair<IAgent,IDate>> agentify(){
	
	}
}
