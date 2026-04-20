package gama.extension.GTFS.gaml.operators;

import gama.annotations.doc;
import gama.annotations.example;
import gama.annotations.operator;
import gama.annotations.support.IOperatorCategory;
import gama.api.gaml.types.IType;
import gama.api.runtime.scope.IScope;
import gama.api.types.date.GamaDateFactory;
import gama.api.types.date.IDate;
import gama.extension.GTFS.gaml.file.GamaGTFSFile;


public class GTFSOperators {

	@operator(
	    value = "starting_date_gtfs",
	    type = IType.DATE,
	    category = { IOperatorCategory.DATE }
	)
	@doc(value = "Returns the starting date of the GTFS data as a Gama date. If the GTFS file does not contain a starting date, it returns null.",
		examples = {
			@example("date startDate = starting_date_gtfs(myGTFSFile);")
		}
	)
	public static IDate starting_date_gtfs(final IScope scope, final GamaGTFSFile gtfs) {
	    java.time.LocalDate localDate = gtfs.getStartingDate();
	    if (localDate == null) return null;
	    return GamaDateFactory.createFromTemporal(scope, localDate);
	}

	@operator(
	    value = "ending_date_gtfs",
	    type = IType.DATE,
	    category = { IOperatorCategory.DATE }
	)
	@doc(value = "Returns the ending date of the GTFS data as a Gama date. If the GTFS file does not contain an ending date, it returns null.",
		examples = {
			@example("date endDate = ending_date_gtfs(myGTFSFile);")
		}
	)
	public static IDate ending_date_gtfs(final IScope scope, final GamaGTFSFile gtfs) {
	    java.time.LocalDate localDate = gtfs.getEndingDate();
	    if (localDate == null) return null;
	    return GamaDateFactory.createFromTemporal(scope, localDate);
	}
}
