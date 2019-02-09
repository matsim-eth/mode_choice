package ch.ethz.matsim.discrete_mode_choice.modules;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.AbstractModule;

import com.google.inject.Binder;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.multibindings.MapBinder;

import ch.ethz.matsim.discrete_mode_choice.components.constraints.LinkAttributeConstraint;
import ch.ethz.matsim.discrete_mode_choice.components.constraints.ShapeFileConstraint;
import ch.ethz.matsim.discrete_mode_choice.components.constraints.TransitWalkConstraint;
import ch.ethz.matsim.discrete_mode_choice.components.constraints.VehicleTourConstraint;
import ch.ethz.matsim.discrete_mode_choice.components.constraints.VehicleTripConstraint;
import ch.ethz.matsim.discrete_mode_choice.model.constraints.CompositeTourConstraintFactory;
import ch.ethz.matsim.discrete_mode_choice.model.constraints.CompositeTripConstraintFactory;
import ch.ethz.matsim.discrete_mode_choice.model.constraints.TourFromTripConstraintFactory;
import ch.ethz.matsim.discrete_mode_choice.model.tour_based.TourConstraintFactory;
import ch.ethz.matsim.discrete_mode_choice.model.trip_based.TripConstraintFactory;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.LinkAttributeConstraintConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.ShapeFileConstraintConfigGroup;
import ch.ethz.matsim.discrete_mode_choice.modules.config.VehicleConstraintConfigGroup;

public class ConstraintModule extends AbstractModule {
	static public LinkedBindingBuilder<TourConstraintFactory> bindTourConstraintFactory(Binder binder, String name) {
		return MapBinder.newMapBinder(binder, String.class, TourConstraintFactory.class).addBinding(name);
	}

	static public LinkedBindingBuilder<TripConstraintFactory> bindTripConstraintFactory(Binder binder, String name) {
		return MapBinder.newMapBinder(binder, String.class, TripConstraintFactory.class).addBinding(name);
	}

	public final static String FROM_TRIP_BASED = "FromTripBased";
	public final static String VEHICLE_TOUR = "VehicleTour";

	public final static String SHAPE_FILE = "ShapeFile";
	public final static String TRANSIT_WALK = "TransitWalk";
	public final static String VEHICLE_TRIP = "VehicleTrip";

	public final Collection<String> CONSTRAINTS = Arrays.asList(FROM_TRIP_BASED, VEHICLE_TOUR, SHAPE_FILE, TRANSIT_WALK,
			VEHICLE_TRIP);

	@Override
	public void install() {
		bindTourConstraintFactory(binder(), FROM_TRIP_BASED).to(TourFromTripConstraintFactory.class);
		bindTourConstraintFactory(binder(), VEHICLE_TOUR).to(VehicleTourConstraint.Factory.class);

		bindTripConstraintFactory(binder(), SHAPE_FILE).to(ShapeFileConstraint.Factory.class);
		bindTripConstraintFactory(binder(), TRANSIT_WALK).to(TransitWalkConstraint.Factory.class);
		bindTripConstraintFactory(binder(), VEHICLE_TRIP).to(VehicleTripConstraint.Factory.class);
	}

	private TourConstraintFactory getTourConstraintFactory(String name,
			Map<String, Provider<TourConstraintFactory>> components) {
		Provider<TourConstraintFactory> provider = components.get(name);

		if (provider != null) {
			return provider.get();
		} else {
			throw new IllegalStateException(
					String.format("There is no TourConstraintFactory component called '%s',", name));
		}
	}

	private TripConstraintFactory getTripConstraintFactory(String name,
			Map<String, Provider<TripConstraintFactory>> components) {
		Provider<TripConstraintFactory> provider = components.get(name);

		if (provider != null) {
			return provider.get();
		} else {
			throw new IllegalStateException(
					String.format("There is no TripConstraintFactory component called '%s',", name));
		}
	}

	@Provides
	@Singleton
	public TourConstraintFactory provideTourConstraintFactory(DiscreteModeChoiceConfigGroup dmcConfig,
			Map<String, Provider<TourConstraintFactory>> components) {
		Collection<String> names = dmcConfig.getActiveTourConstraints();

		if (names.size() == 0) {
			return getTourConstraintFactory(names.iterator().next(), components);
		} else {
			CompositeTourConstraintFactory factory = new CompositeTourConstraintFactory();

			for (String name : names) {
				factory.addFactory(getTourConstraintFactory(name, components));
			}

			return factory;
		}
	}

	@Provides
	@Singleton
	public TripConstraintFactory provideTripConstraintFactory(DiscreteModeChoiceConfigGroup dmcConfig,
			Map<String, Provider<TripConstraintFactory>> components) {
		Collection<String> names = dmcConfig.getActiveTourConstraints();

		if (names.size() == 0) {
			return getTripConstraintFactory(names.iterator().next(), components);
		} else {
			CompositeTripConstraintFactory factory = new CompositeTripConstraintFactory();

			for (String name : names) {
				factory.addFactory(getTripConstraintFactory(name, components));
			}

			return factory;
		}
	}

	@Provides
	@Singleton
	public TourFromTripConstraintFactory provideTourFromTripConstraintFactory(
			TripConstraintFactory tripConstraintFactory) {
		return new TourFromTripConstraintFactory(tripConstraintFactory);
	}

	@Provides
	@Singleton
	public LinkAttributeConstraint.Factory provideLinkAttributeConstraintFactory(Network network,
			DiscreteModeChoiceConfigGroup dmcConfig) {
		LinkAttributeConstraintConfigGroup config = dmcConfig.getLinkAttributeConstraintConfigGroup();
		return new LinkAttributeConstraint.Factory(network, config.getConstrainedModes(), config.getAttributeName(),
				config.getAttributeValue(), config.getRequirement());
	}

	@Provides
	@Singleton
	public ShapeFileConstraint.Factory provideShapeFileConstraintFactory(Network network,
			DiscreteModeChoiceConfigGroup dmcConfig, Config matsimConfig) {
		ShapeFileConstraintConfigGroup config = dmcConfig.getShapeFileConstraintConfigGroup();
		URL url = ConfigGroup.getInputFileURL(matsimConfig.getContext(), config.getPath());
		return new ShapeFileConstraint.Factory(network, config.getConstrainedModes(), config.getRequirement(), url);
	}

	@Provides
	@Singleton
	public VehicleTripConstraint.Factory provideVehicleTripConstraintFactory(DiscreteModeChoiceConfigGroup dmcConfig) {
		VehicleConstraintConfigGroup config = dmcConfig.getVehicleTripConstraintConfig();
		return new VehicleTripConstraint.Factory(config.getRequireStartAtHome(), config.getRequireContinuity(),
				config.getRequireEndAtHome(), config.getRequireHomeExists());
	}

	@Provides
	@Singleton
	public VehicleTourConstraint.Factory provideVehicleTourConstraintFactory(DiscreteModeChoiceConfigGroup dmcConfig) {
		VehicleConstraintConfigGroup config = dmcConfig.getVehicleTourConstraintConfig();
		return new VehicleTourConstraint.Factory(config.getRequireStartAtHome(), config.getRequireContinuity(),
				config.getRequireEndAtHome(), config.getRequireHomeExists());
	}
}
