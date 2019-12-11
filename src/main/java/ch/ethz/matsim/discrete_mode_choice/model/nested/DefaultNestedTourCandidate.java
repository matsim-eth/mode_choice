package ch.ethz.matsim.discrete_mode_choice.model.nested;

import java.util.List;

import ch.ethz.matsim.discrete_mode_choice.model.tour_based.DefaultTourCandidate;
import ch.ethz.matsim.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

public class DefaultNestedTourCandidate extends DefaultTourCandidate implements NestedUtilityCandidate {
	private final Nest nest;

	public DefaultNestedTourCandidate(double utility, List<TripCandidate> tripCandidates, Nest nest) {
		super(utility, tripCandidates);
		this.nest = nest;
	}

	@Override
	public Nest getNest() {
		return nest;
	}
}
