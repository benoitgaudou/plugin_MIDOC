package gama.extension.GTFS.GamaGTFSUtils;

import gama.api.runtime.scope.IScope;
import gama.api.types.geometry.GamaPoint;
import gama.api.types.geometry.GamaPointFactory;
import gama.api.types.geometry.IPoint;
import gama.api.types.geometry.IShape;
import gama.gaml.operators.spatial.SpatialProjections;

public class SpatialUtils {

    /**
     * Converts latitude and longitude to GAMA CRS.
     *
     * @param scope - The GAMA simulation scope.
     * @param lat - Latitude in EPSG:4326.
     * @param lon - Longitude in EPSG:4326.
     * @return Transformed GamaPoint in the GAMA CRS.
     */
    public static GamaPoint toGamaCRS(IScope scope, double lat, double lon) {
        // Create a GamaPoint for the original location
        IPoint rawLocation = GamaPointFactory.create(lon, lat, 0.0); // Longitude (X), Latitude (Y), Altitude (Z)  

        // Transform the point to the GAMA CRS using "to_GAMA_CRS"
        IShape transformedShape = SpatialProjections.to_GAMA_CRS(scope, rawLocation, "EPSG:4326");

        // Return the location as a GamaPoint
        return (GamaPoint) transformedShape.getLocation();
    }
}