model TestImportationControledesDonnees

global {
	// Path to the GTFS file
	string gtfs_f_path ;
	string boundary_shp_path;
	
	string output_folder <- "./outputs";
	

    gtfs_file gtfs_f <- gtfs_file(gtfs_f_path);    
	shape_file boundary_shp <- shape_file(boundary_shp_path);
	
	geometry shape <- envelope(boundary_shp);
	
    init {   
        // Create bus_stop agents from the GTFS data
       create bus_stop from: gtfs_f ;  
       create transport_shape from: gtfs_f ;  

       //
       save bus_stop to: output_folder+"/bus_stop.shp" format: "shp" attributes: ["name","stopName","stopId","routeType","tripNumber"];              
       save transport_shape to: output_folder+"/transport_shape.shp" format: "shp" attributes: ["name","routeId","routeType","shapeId","tripId"];       
    }
}

// Species representing each transport stop
species bus_stop skills: [TransportStopSkill] {

     aspect base { 	
		draw circle (100.0) at: location color:#blue;	
     }
}

species transport_shape skills: [TransportShapeSkill] {
     aspect base { 	
		draw shape color:#darkgrey;	
     }
}

experiment GTFSExperiment type: gui virtual: true {
    
    output {
        // Display the bus stops on the map
        display "Bus Stops And Envelope" {  
            // Display the bus_stop agents on the map
            species bus_stop aspect: base;
            species transport_shape aspect: base; 
        }
    }
}

experiment TestImportationControledesDonneesToulouse type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/tisseo_gtfs_v2";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileToulouse.shp";
}

experiment TestImportationControledesDonneesNantes type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/nantes_gtfs";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileNantes.shp";
}

experiment TestImportationControledesDonneesHanoi type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/hanoi_gtfs_pm";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileHanoishp.shp";
}