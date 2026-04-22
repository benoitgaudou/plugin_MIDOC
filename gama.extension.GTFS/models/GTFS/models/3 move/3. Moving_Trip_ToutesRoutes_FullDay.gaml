model Moving_Trip

/**
 * Name: test
 * Based on the internal empty template. 
 * Author: tiend
 * Tags: 
 */
global {
	// Path to the GTFS file
	string gtfs_f_path;
	string boundary_shp_path;

    gtfs_file gtfs_f <- gtfs_file(gtfs_f_path);    
	shape_file boundary_shp <- shape_file(boundary_shp_path);
	geometry shape <- envelope(boundary_shp);
	date starting_date <- date("1970-01-01 05:00:00");
		
	 
	float step <- 10#s;
	 
	// To optimize: compute 1 graph per shapeId
	map<string,graph> mapGraphs <- map([]);	
	 
	init{
		create bus_stop from: gtfs_f ;
        create transport_shape from: gtfs_f ;
         
		//
        ask bus_stop where(! empty(each.departureStopsInfo)) {
			is_start_stop <- true;
        	self.color <- #red;
        	do update_next_trip();
		}
	}
}

species bus_stop skills: [TransportStopSkill] schedules:bus_stop where (each.is_start_stop) {
	rgb color <- rgb(0, 0, 255); 
    
    bool is_start_stop <- false;
    
    int next_trip_index <- -1;
    date next_departure;
    string next_trip_id <- "";
    
    action update_next_trip() {
		next_trip_index <- (next_trip_index + 1) mod length(departureStopsInfo) ;    	
		next_trip_id <- departureStopsInfo.keys()[next_trip_index];
		next_departure <- map<string,date>(departureStopsInfo[next_trip_id]).values()[0];	
    }
    
    reflex create_bus {
    	write sample(""+cycle + " bus_stop " + int(self));
		loop while: (world.current_date > next_departure) {
			create bus {
				departureStopsInfo <- myself.departureStopsInfo[myself.next_trip_id];
				stops <- departureStopsInfo.keys;
				current_stop_index <- 0;
				location <- stops[0].location;
				target_location <- stops[1].location;
				
				if(mapGraphs.keys contains myself.tripShapeMap[myself.next_trip_id]) {
					shape_network <- mapGraphs[string(myself.tripShapeMap[myself.next_trip_id])];
				} else {
					transport_shape my_shape <- transport_shape first_with (each.shapeId = myself.tripShapeMap[myself.next_trip_id]);
	          
			        ask my_shape {
						self.to_display <- true;
					}
					
					shape_network <- as_edge_graph(my_shape);
					add shape_network at: string(myself.tripShapeMap[myself.next_trip_id]) to: mapGraphs ;
				}
			}
			
			do update_next_trip();
		}
	}
	
    aspect base {
		draw circle(20) color: color;
	}
}

species transport_shape skills: [TransportShapeSkill] schedules: []{
	bool to_display <- false;

	aspect default {
		if (to_display){
			draw shape color: #green;
		}
	}
}

species bus skills: [moving] {
	int current_stop_index <- 0;
	point target_location;
	map<bus_stop,date> departureStopsInfo;
	list<bus_stop> stops;
	graph shape_network;
	
	init {
		speed <- 10.0;
	}

     // Move the bus to the next stop
    reflex move when: self.location != target_location  {
		do goto(target: target_location, on: shape_network, speed: speed);
	}
    
   // Check whether the bus arrived and choose the next stop
    reflex check_arrival when: self.location = target_location {
		ask stops[current_stop_index] {
			color<-#red;}
//        write "Bus arrived at : " + stops[current_stop_index].name;
        
        if (current_stop_index < length(stops) - 1) {
			current_stop_index <- current_stop_index + 1;
            target_location <- stops[current_stop_index].location;
//            write "Next stop: " + stops[current_stop_index].name;
		} else {
//			write "Bus reached the last stop.";
            target_location <- nil;
            do die();
		}
	}

	aspect base {
		draw rectangle(200, 100) color: #red rotate: heading;
	}
}

experiment GTFSExperiment type: gui virtual: true {
	output {
		display "Bus Simulation" type: 3d {
            species bus_stop aspect: base refresh: true;
            species bus aspect: base;
            species transport_shape aspect:default;
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
