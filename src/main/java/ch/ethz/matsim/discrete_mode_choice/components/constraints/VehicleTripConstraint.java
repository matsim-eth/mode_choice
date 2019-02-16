package ch.ethz.matsim.discrete_mode_choice.components.constraints;

import java.util.Collection;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;

import ch.ethz.matsim.discrete_mode_choice.model.DiscreteModeChoiceTrip;
import ch.ethz.matsim.discrete_mode_choice.model.trip_based.TripConstraint;
import ch.ethz.matsim.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import ch.ethz.matsim.discrete_mode_choice.model.trip_based.candidates.TripCandidate;

/*
 * TODO: This should be generalized to Id<? extends BasicLocation>
 */
public class VehicleTripConstraint implements TripConstraint {
	private final List<DiscreteModeChoiceTrip> trips;

	private Collection<String> requireStartAtHome;
	private Collection<String> requireContinuity;
	private Collection<String> requireEndAtHome;
	private boolean requireExistingHome;

	private Id<Link> homeLinkId;

	public VehicleTripConstraint(List<DiscreteModeChoiceTrip> trips, Id<Link> homeLinkId,
			Collection<String> requireStartAtHome, Collection<String> requireContinuity,
			Collection<String> requireEndAtHome, boolean requireExistingHome) {
		this.trips = trips;
		this.homeLinkId = homeLinkId;
		this.requireStartAtHome = requireStartAtHome;
		this.requireEndAtHome = requireEndAtHome;
		this.requireContinuity = requireContinuity;
		this.requireExistingHome = requireExistingHome;
	}

	private Id<Link> getCurrentLinkId(String mode, List<String> previousModes) {
		for (int i = previousModes.size() - 1; i >= 0; i--) {
			if (previousModes.get(i).equals(mode)) {
				return trips.get(i).getDestinationActivity().getLinkId();
			}
		}

		return null;
	}

	private boolean canReturnHome(List<String> previousModes) {
		for (int index = previousModes.size(); index < trips.size(); index++) {
			if (trips.get(index).getDestinationActivity().getLinkId().equals(homeLinkId)) {
				return true;
			}
		}

		if (homeLinkId != null || requireExistingHome) {
			return false;
		} else {
			return true;
		}
	}

	private boolean willReturn(Id<Link> linkId, List<String> previousModes) {
		for (int index = previousModes.size(); index < trips.size(); index++) {
			DiscreteModeChoiceTrip trip = trips.get(index);

			if (trip.getDestinationActivity().getLinkId().equals(linkId)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean validateBeforeEstimation(DiscreteModeChoiceTrip trip, String mode, List<String> previousModes) {
		// First, we check whether we start using a restricted mode out of home although
		// we require to start at home
		if (requireStartAtHome.contains(mode)) {
			boolean isFirst = !previousModes.contains(mode);

			if (isFirst && !trip.getOriginActivity().getLinkId().equals(homeLinkId)) {
				// The trip is the first of the restricted mode, but we're not home!

				if (homeLinkId != null || requireExistingHome) {
					return false;
				}
			}
		}

		// Second, we make sure we are only using a restricted mode at a location where
		// it has been moved to before.
		if (requireContinuity.contains(mode)) {
			Id<Link> currentLinkId = getCurrentLinkId(mode, previousModes);

			if (currentLinkId != null) { // We have moved the vehicle already
				if (currentLinkId.equals(trip.getOriginActivity().getLinkId())) {
					// But the vehicle is not where we're currently trying to depart
					return false;
				}
			}
		}

		// Third, we look at the requirement to end at home. This is tricky, especially
		// whith multiple modes. With one mode
		// it is straightforward: We can only go on a trip with that mode if we will
		// ever arrive back home. And we need to
		// make sure that when we are on a trip with that mode we don't choose any other
		// mode until we are home.
		// I'm not sure how easy it is to generalized this to multiple modes that can
		// float around anywhere in the network. For now, we restrict the use of one
		// active mode. Active here means that a mode has been moved away from its
		// starting position.
		if (requireEndAtHome.size() > 0) {
			// Make sure we can go back home
			if (requireEndAtHome.contains(mode)) {
				if (!canReturnHome(previousModes)) {
					return false;
				}
			}

			String activeMode = null;
			Id<Link> currentActiveModeLinkId = null;

			for (String restrictedMode : requireEndAtHome) {
				Id<Link> currentLinkId = getCurrentLinkId(restrictedMode, previousModes);

				if (currentLinkId != null && !currentLinkId.equals(homeLinkId)) {
					// Vehicle has been moved and is out of home
					activeMode = restrictedMode;
					currentActiveModeLinkId = currentLinkId;
					break;
				}
			}

			if (activeMode != null && !activeMode.equals(mode)) {
				// There is an active mode, otherwise we can do what we want
				// And here we check the case where we already know that we want to use
				// something else than active mode

				if (requireEndAtHome.contains(mode)) {
					// If the proposal is another constrained mode, we forbid to use it here,
					// because we're already
					// on the road with activeMode
					return false;
				}

				if (!willReturn(currentActiveModeLinkId, previousModes)) {
					// In case we are able to return to the current location, we can do some walking
					// or similar in between, becasue we know that we will have a chance later to
					// pick up the vehicle again. However, if we do not return to the current
					// location we cannot bring it back.
					return false; // Here we are enforcing the active mode
				}
			}
		}

		// If none of the constraints catched some infeasible situation, the mode
		// proposal is fine!
		return true;
	}

	@Override
	public boolean validateAfterEstimation(DiscreteModeChoiceTrip trip, TripCandidate candidate,
			List<TripCandidate> previousCandidates) {
		return true;
	}

	static public class Factory implements TripConstraintFactory {
		private Collection<String> requireStartAtHome;
		private Collection<String> requireContinuity;
		private Collection<String> requireEndAtHome;
		private boolean requireExistingHome;

		public Factory(Collection<String> requireStartAtHome, Collection<String> requireContinuity,
				Collection<String> requireEndAtHome, boolean requireExistingHome) {
			this.requireStartAtHome = requireStartAtHome;
			this.requireContinuity = requireContinuity;
			this.requireEndAtHome = requireEndAtHome;
			this.requireExistingHome = requireExistingHome;
		}

		@Override
		public TripConstraint createConstraint(Person person, List<DiscreteModeChoiceTrip> trips,
				Collection<String> availableModes) {
			return new VehicleTripConstraint(trips, getHomeLinkId(trips), requireStartAtHome, requireContinuity,
					requireEndAtHome, requireExistingHome);
		}

		private Id<Link> getHomeLinkId(List<DiscreteModeChoiceTrip> trips) {
			for (DiscreteModeChoiceTrip trip : trips) {
				if (trip.getOriginActivity().getType().equals("home")) {
					return trip.getOriginActivity().getLinkId();
				}

				if (trip.getDestinationActivity().getType().equals("home")) {
					return trip.getDestinationActivity().getLinkId();
				}
			}

			return null;
		}
	}
}
