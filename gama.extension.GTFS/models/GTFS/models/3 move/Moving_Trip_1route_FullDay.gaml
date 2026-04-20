model Moving_Trip

/**
* Name: test
* Based on the internal empty template. 
* Author: tiend
* Tags: 
*/

global {
	 gtfs_file gtfs_f <- gtfs_file("../../includes/tisseo_gtfs_v2");
     shape_file boundary_shp <- shape_file("../../includes/ShapeFileToulouse.shp");
	 geometry shape <- envelope(boundary_shp);
	 graph shape_network; 

	 int selected_bus_stop <- 1033;

	 bus_stop starts_stop;	 
	 transport_shape my_shape ;
	 transport_trip my_trip;
	 
	 init{
	 	write "Loading GTFS contents from: " + gtfs_f;
              
        create bus_stop from: gtfs_f ;
        create transport_trip from: gtfs_f ;
        create transport_shape from: gtfs_f ;
        
		//
		write "Select the bus stop " + selected_bus_stop;
        starts_stop <- bus_stop[selected_bus_stop];
        ask starts_stop {self.color <- #red;}
        
        string tId <- starts_stop.tripShapeMap.keys()[0];
        string sId <- starts_stop.tripShapeMap.values()[0];
        
        my_shape <- transport_shape first_with(each.shapeId = sId);
        my_trip <- transport_trip first_with(each.tripId = tId);
        
     	shape_network <- as_edge_graph(my_shape);
   
        ask my_shape {
        	self.to_display <- true;
        }

        create bus {
			list<pair<bus_stop,string>> departureStopsInfo <- starts_stop.departureStopsInfo[string(my_trip.tripId)];
			stops <- departureStopsInfo collect (each.key);
			current_stop_index <- 0;
			location <- stops[0].location;
			target_location <- stops[1].location;	
			
			write departureStopsInfo;  		 
		}
		
	 }
}

species bus_stop skills: [TransportStopSkill] {
    rgb color <- rgb(0,0,255); 
    
    init {
  //  	write self.departureStopsInfo;
    }
    
    reflex act {
    	write self.departureStopsInfo;

    }
	
    aspect base {
      draw circle(20) color: color;
    }
}

species transport_trip skills: [TransportTripSkill];

species transport_shape skills: [TransportShapeSkill]{
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
	list<bus_stop> stops;
	
	init {
        speed <- 10.0;
    }

     // Move the bus to the next stop
    reflex move when: self.location != target_location  {
        do goto(target: target_location, on: shape_network, speed: speed);
    }
    
   // Check whether the bus arrived and choose the next stop
    reflex check_arrival when: self.location = target_location {
    	ask stops[current_stop_index] {color<-#red;}
        write "Bus arrived at : " + stops[current_stop_index].name;
        
        if (current_stop_index < length(stops) - 1) {
            current_stop_index <- current_stop_index + 1;
            target_location <- stops[current_stop_index].location;
            write "Next stop: " + stops[current_stop_index].name;
        } else {
            write "Bus reached the last stop.";
            target_location <- nil;
        }
    }

	aspect base {
        draw rectangle(200, 100) color: #red rotate: heading;
    }
}

experiment GTFSExperiment type: gui {
    output {
        display "Bus Simulation" {
            species bus_stop aspect: base refresh: true;
            species bus aspect: base;
            species transport_shape aspect:default;
        }
    }
}
