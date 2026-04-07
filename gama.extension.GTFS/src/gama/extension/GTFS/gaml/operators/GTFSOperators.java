package gama.extension.GTFS.gaml.operators;

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
	public static IDate ending_date_gtfs(final IScope scope, final GamaGTFSFile gtfs) {
	    java.time.LocalDate localDate = gtfs.getEndingDate();
	    if (localDate == null) return null;
	    return GamaDateFactory.createFromTemporal(scope, localDate);
	}
}
