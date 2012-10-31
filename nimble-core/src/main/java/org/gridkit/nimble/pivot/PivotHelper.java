package org.gridkit.nimble.pivot;

import java.io.Serializable;

class PivotHelper {

	public static Pivot.Aggregator createGaussianAggregator(SampleExtractor extractor) {
		return new DistributionAggregator(extractor);
	}
	
	public static Pivot.Aggregator createFrequencyAggregator(SampleExtractor extractor) {
		return new FrequencyAggregator(new StandardEventFrequencyExtractor(extractor));
	}

	public static Pivot.Aggregator createConstantAggregator(final SampleExtractor extractor) {
		return new ConstantAggregator(extractor);
	}

	public static Pivot.Aggregator createStaticValue(final Object value) {
		return new StaticValue(value);
	}
		
	private static final class ConstantAggregator implements Pivot.Aggregator, Serializable {

		private static final long serialVersionUID = 20121014L;
		
		private final SampleExtractor extractor;

		private ConstantAggregator(SampleExtractor extractor) {
			this.extractor = extractor;
		}

		@Override
		public Aggregation<?> newAggregation() {
			return new ConstantAggregation(extractor);
		}
	}

	private static final class StaticValue implements Pivot.Aggregator, Serializable {

		private static final long serialVersionUID = 20121014L;
		
		private final Object value;

		public StaticValue(Object value) {
			this.value = value;
		}

		@Override
		public Aggregation<?> newAggregation() {
			return new StaticAggregation<Object>(value);
		}
	}
	
	private static class DistributionAggregator implements Pivot.Aggregator, Serializable {

		private static final long serialVersionUID = 20121010L;
		
		private final SampleExtractor extractor;
		
		public DistributionAggregator(SampleExtractor extractor) {
			this.extractor = extractor;
		}
		
		@Override
		public Aggregation<?> newAggregation() {
			return new DistributionAggregation(extractor);
		}		
	}
	
	private static class FrequencyAggregator implements Pivot.Aggregator, Serializable {
		
		private static final long serialVersionUID = 20121014L;
		
		private final EventFrequencyExtractor extractor;

		public FrequencyAggregator(EventFrequencyExtractor extractor) {
			this.extractor = extractor;
		}
		
		@Override
		public Aggregation<?> newAggregation() {
			return new FrequencyAggregation(extractor);
		}				
	}	
}
