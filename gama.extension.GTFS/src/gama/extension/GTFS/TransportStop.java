package gama.extension.GTFS;

import gama.core.metamodel.shape.GamaPoint;

public class TransportStop {
	
	 private String stopId;
	    private String stopName;
	    private GamaPoint location;  // Utilisation de GamaPoint pour représenter la latitude et la longitude

	    // Constructeur avec un GamaPoint
	    public TransportStop(String stopId, String stopName, double stopLat, double stopLon) {
	        this.stopId = stopId;
	        this.stopName = stopName;
	        // Création de GamaPoint à partir des coordonnées de latitude et longitude
	        this.location = new GamaPoint(stopLat, stopLon);
	    }

	    // Getters pour accéder aux attributs
	    public String getStopId() {
	        return stopId;
	    }

	    public String getStopName() {
	        return stopName;
	    }

	    public GamaPoint getLocation() {
	        return location;
	    }

	    // Méthode pour obtenir la localisation au format chaîne de caractères
	    public String getLocationAsString() {
	        return "Point(" + location.getX() + ", " + location.getY() + ")";
	    }



}
