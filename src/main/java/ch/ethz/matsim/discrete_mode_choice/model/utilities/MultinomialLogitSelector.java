package ch.ethz.matsim.discrete_mode_choice.model.utilities;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

/**
 * The MultinomialLogitSelector collects a set of candidates with given
 * utilities and then selects on according to the multinomial logit model. For
 * each candidate (i) a utility is calculated as:
 * 
 * <code>P(i) = exp( Ui ) / Sum( U1 + U2 + ... + Un )</code>
 * 
 * For large utilities the exponential terms can exceed the value range of a
 * double. Therefore, the selector has a cutoff value, which is a maximum of
 * 700.0 by default. If this value is reached a warning will be shown.
 * 
 * @author sebhoerl
 */
public class MultinomialLogitSelector<T extends UtilityCandidate> implements UtilitySelector<T> {
	private final static Logger logger = Logger.getLogger(MultinomialLogitSelector.class);

	final private List<T> candidates = new LinkedList<>();

	private final double maximumUtility;
	private final double minimumUtility;

	/**
	 * Creates a MultinomialSelector. The utility cutoff value defines the maximum
	 * utility possible.
	 */
	public MultinomialLogitSelector(double maximumUtility, double minimumUtility) {
		this.maximumUtility = maximumUtility;
		this.minimumUtility = minimumUtility;
	}

	@Override
	public void addCandidate(T candidate) {
		candidates.add(candidate);
	}

	@Override
	public Optional<T> select(Random random) {
		// I) If not candidates are available, give back nothing
		if (candidates.size() == 0) {
			return Optional.empty();
		}

		// II) Filter candidates that have a very low utility
		List<T> filteredCandidates = candidates.stream() //
				.filter(c -> c.getUtility() > -minimumUtility) //
				.collect(Collectors.toList());

		if (filteredCandidates.size() == 0) {
			logger.warn(String.format(
					"Encountered choice where all utilities were smaller than %f (minimum configured utility)",
					minimumUtility));
			return Optional.empty();
		}

		// III) Create a probability distribution over candidates
		List<Double> density = new ArrayList<>(filteredCandidates.size());

		for (T candidate : filteredCandidates) {
			double utility = candidate.getUtility();

			// Warn if there is a utility that is exceeding the feasible range
			if (utility > maximumUtility) {
				utility = maximumUtility;
				logger.warn(String.format(
						"Encountered choice where a utility is larger than %f (axmimum configured utility)",
						maximumUtility));
			}

			density.add(Math.exp(utility));
		}

		// IV) Build a cumulative density of the distribution
		List<Double> cumulativeDensity = new ArrayList<>(density.size());
		double totalDensity = 0.0;

		for (int i = 0; i < density.size(); i++) {
			totalDensity += density.get(i);
			cumulativeDensity.add(totalDensity);
		}

		// V) Perform a selection using the CDF
		double pointer = random.nextDouble() * totalDensity;

		int selection = (int) cumulativeDensity.stream().filter(f -> f < pointer).count();
		return Optional.of(candidates.get(selection));
	}

	public static class Factory<TF extends UtilityCandidate> implements UtilitySelectorFactory<TF> {
		private final double minimumUtility;
		private final double maximumUtility;

		public Factory(double minimumUtility, double maximumUtility) {
			this.minimumUtility = minimumUtility;
			this.maximumUtility = maximumUtility;
		}

		@Override
		public UtilitySelector<TF> createUtilitySelector() {
			return new MultinomialLogitSelector<>(minimumUtility, maximumUtility);
		}
	}
}