/**
 * Name: testCreationObjetsJava - Modèle Réuni
 * Test de création d'objets GTFS pour différentes villes
 * Author: tiend
 * Tags: GTFS, multi-city
 */

model testCreationObjetsJava

global {
    // Paramètres configurables par experiment
    string gtfs_f_path;
    string boundary_shp_path;
    
    gtfs_file gtfs_f <- gtfs_file(gtfs_f_path);
    shape_file boundary_shp <- shape_file(boundary_shp_path);
    geometry shape <- envelope(boundary_shp);

    init {
        // Create bus_stop agents from the GTFS data
        create bus_stop from: gtfs_f;
       	create transport_shape from: gtfs_f ;  
        
        write string(length(bus_stop)) + " bus stops créés depuis " + gtfs_f_path;
    }
}

// Species representing each transport stop
species bus_stop skills: [TransportStopSkill] {}
species transport_shape skills: [TransportShapeSkill] {}



experiment TestsToulouse type: test {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/tisseo_gtfs_v2";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileToulouse.shp";

	test stops {
		assert length(bus_stop) = 3759;
	}
	
	test shapes {
		assert length(transport_shape) = 400;		
	}
}

experiment TestsNantes type: test {
    parameter "GTFS file path" var: gtfs_f_path <- "../../includes/nantes_gtfs";
    parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileNantes.shp";

	test stops {
		assert length(bus_stop) = 3759;
	}
	
	test shapes {
		assert length(transport_shape) = 400;		
	}
}

// Experiment pour Toulouse
experiment TestsHanoi type: test  {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/hanoi_gtfs_pm";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileHanoishp.shp";
    
	test stops {
		assert length(bus_stop) = 3759;
	}
	
	test shapes {
		assert length(transport_shape) = 400;		
	}    
}
