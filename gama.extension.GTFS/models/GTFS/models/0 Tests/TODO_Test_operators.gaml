// Cas de test où la starting_date est en dehors de la période du GTFS : on choisit le premier jour ayant le même jour de la semaine.
model datefilter




global {
	// Path to the GTFS file
	string gtfs_f_path ;
	string boundary_shp_path;

    gtfs_file gtfs_f <- gtfs_file(gtfs_f_path);    
	shape_file boundary_shp <- shape_file(boundary_shp_path);
	
	geometry shape <- envelope(boundary_shp);
	
    init {   
        // Create bus_stop agents from the GTFS data
		create bus_stop from: gtfs_f ;  
       	create transport_shape from: gtfs_f ;  
       
       
       	loop i over: remove_duplicates(transport_shape collect each.routeType) {
       		rgb c <- rnd_color(255);
			ask (transport_shape where (each.routeType =i)) {
				color <- c;
			}
       	}  
       	
       	date min_date_gtfs <- starting_date_gtfs(gtfs_f);
    	date max_date_gtfs <- ending_date_gtfs(gtfs_f);
       	     
        write "Le premier jour du GTFS = " + min_date_gtfs;
        write "Le dernier jour du GTFS = " + max_date_gtfs;

    }
}

// Species representing each transport stop
species bus_stop skills: [TransportStopSkill] {

     aspect base { 	
		draw circle (100.0) at: location color:#blue;	
     }
}

species transport_shape skills: [TransportShapeSkill] {
	rgb color ;
    aspect base { 	
		draw shape color:color;	
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

experiment Toulouse type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/tisseo_gtfs_v2";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileToulouse.shp";
}

experiment Nantes type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/nantes_gtfs";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileNantes.shp";
}

experiment Hanoi type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <- "../../includes/hanoi_gtfs_pm";	
	parameter "Boundary shapefile" var: boundary_shp_path <- "../../includes/shapeFileHanoishp.shp";
}

experiment "3 villes" type: gui parent: GTFSExperiment {
	action _init_() {
		create simulation(gtfs_f_path: "../../includes/tisseo_gtfs_v2", boundary_shp_path: "../../includes/shapeFileToulouse.shp");
		create simulation(gtfs_f_path: "../../includes/nantes_gtfs", boundary_shp_path: "../../includes/shapeFileNantes.shp");
		create simulation(gtfs_f_path: "../../includes/hanoi_gtfs_pm", boundary_shp_path: "../../includes/shapeFileHanoishp.shp");
	}
}
