model TestImportationControledesDonnees

global {
	// Path to the GTFS file
	string gtfs_f_path ;
	string boundary_shp_path;

    gtfs_file gtfs_f <- gtfs_file(gtfs_f_path);    
	shape_file boundary_shp <- shape_file(boundary_shp_path);
	
	geometry shape <- envelope(boundary_shp);
	
	map color_shape <- map([
		-1::#black,
		0::#green,
		1::#red,
		2::#purple,
		3::#blue
	]);
	
	
    init {   
        // Create bus_stop agents from the GTFS data
		create bus_stop from: gtfs_f ;  
       	create transport_shape from: gtfs_f {
			color <- (color_shape.keys contains routeType) ? color_shape[routeType] : #black;    		
       	}
       
       	// 
       	write ""+length(bus_stop)+" bus stops have been created";
       	write ""+length(transport_shape)+" transport shape have been created";       
    }
}

// Species representing each transport stop
species bus_stop skills: [TransportStopSkill] {

     aspect base { 	
		draw circle (40.0) at: location color:#lightgrey border:#black;	
     }
}

species transport_shape skills: [TransportShapeSkill] {
	rgb color <- #black;
    aspect base { 	
		draw shape +20#m color:color;	
    }
}

experiment GTFSExperiment type: gui virtual: true {
    
    output {
        // Display the bus stops on the map
        display "Bus Stops And Envelope" type: 3d{  
            // Display the bus_stop agents on the map
            species bus_stop aspect: base;
            species transport_shape aspect: base; 
        }
    }
}

experiment Toulouse type: gui parent: GTFSExperiment {
	parameter "GTFS file path" var: gtfs_f_path <-  "../../includes/tisseo_gtfs_v2";	
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