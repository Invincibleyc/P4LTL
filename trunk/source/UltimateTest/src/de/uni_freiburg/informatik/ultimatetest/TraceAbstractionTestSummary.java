package de.uni_freiburg.informatik.ultimatetest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.uni_freiburg.informatik.ultimate.core.services.IResultService;
import de.uni_freiburg.informatik.ultimate.result.BenchmarkResult;
import de.uni_freiburg.informatik.ultimatetest.decider.ITestResultDecider.TestResult;
import de.uni_freiburg.informatik.ultimatetest.summary.NewTestSummary;
import de.uni_freiburg.informatik.ultimatetest.util.Util;

public class TraceAbstractionTestSummary extends NewTestSummary {

	private int mCount;

	/**
	 * A map from file names to benchmark results.
	 */
	private Map<UltimateRunDefinition, Collection<BenchmarkResult>> m_TraceAbstractionBenchmarks;

	public TraceAbstractionTestSummary(Class<? extends UltimateTestSuite> ultimateTestSuite) {
		super(ultimateTestSuite);
		mCount = 0;
		m_TraceAbstractionBenchmarks = new HashMap<UltimateRunDefinition, Collection<BenchmarkResult>>();
	}

	@Override
	public String getFilenameExtension() {
		return ".log";
	}

	@Override
	public String getDescriptiveLogName() {
		return this.getClass().getSimpleName();
	}

	@Override
	public void addResult(UltimateRunDefinition ultimateRunDefinition, TestResult threeValuedResult, String category,
			String message, String testname, IResultService resultService) {
		super.addResult(ultimateRunDefinition, threeValuedResult, category, message, testname, resultService);

		if (resultService != null) {
			addTraceAbstractionBenchmarks(ultimateRunDefinition,
					Util.filterResults(resultService.getResults(), BenchmarkResult.class));
		}

	}

	public void addTraceAbstractionBenchmarks(UltimateRunDefinition ultimateRunDefinition,
			Collection<BenchmarkResult> benchmarkResults) {
		assert !m_TraceAbstractionBenchmarks.containsKey(ultimateRunDefinition) : "benchmarks already added";
		m_TraceAbstractionBenchmarks.put(ultimateRunDefinition, benchmarkResults);
	}

	@Override
	public String getSummaryLog() {

		StringBuilder sb = new StringBuilder();
		int total = 0;
		mCount = 0;

		sb.append("################# ").append("Trace Abstraction Test Summary").append(" #################")
				.append("\n");

		PartitionedResults results = partitionResults(mResults.entrySet());

		sb.append(getSummaryLog(results.Success, "SUCCESSFUL TESTS"));
		int success = mCount;
		total = total + mCount;
		mCount = 0;
		sb.append(getSummaryLog(results.Unknown, "UNKNOWN TESTS"));
		int unknown = mCount;
		total = total + mCount;
		mCount = 0;
		sb.append(getSummaryLog(results.Failure, "FAILED TESTS"));
		int fail = mCount;
		total = total + mCount;
		sb.append("\n");
		sb.append("====== SUMMARY for ").append("Trace Abstraction").append(" ======").append("\n");
		sb.append("Success:\t" + success).append("\n");
		sb.append("Unknown:\t" + unknown).append("\n");
		sb.append("Failures:\t" + fail).append("\n");
		sb.append("Total:\t\t" + total);
		return sb.toString();

	}

	private String getSummaryLog(Collection<Entry<UltimateRunDefinition, ExtendedResult>> results, String title) {
		StringBuilder sb = new StringBuilder();
		sb.append("====== ").append(title).append(" =====").append("\n");

		// group by category
		HashMap<String, Collection<Entry<UltimateRunDefinition, ExtendedResult>>> resultsByCategory = new HashMap<>();
		for (Entry<UltimateRunDefinition, ExtendedResult> entry : results) {
			Collection<Entry<UltimateRunDefinition, ExtendedResult>> coll = resultsByCategory
					.get(entry.getValue().Category);
			if (coll == null) {
				coll = new ArrayList<>();
				resultsByCategory.put(entry.getValue().Category, coll);
			}
			coll.add(entry);
		}

		for (Entry<String, Collection<Entry<UltimateRunDefinition, ExtendedResult>>> entry : resultsByCategory
				.entrySet()) {
			sb.append("\t").append(entry.getKey()).append("\n");

			for (Entry<UltimateRunDefinition, ExtendedResult> currentResult : entry.getValue()) {
				sb.append("\t\t").append(currentResult.getKey()).append(": ").append(currentResult.getValue().Message)
						.append("\n");
				// Add TraceAbstraction benchmarks
				Collection<BenchmarkResult> benchmarks = m_TraceAbstractionBenchmarks.get(currentResult.getKey());
				if (benchmarks == null) {
					sb.append("\t\t").append("No benchmark results available.").append("\n");
				} else {
					for (BenchmarkResult<Object> benchmark : benchmarks) {
						sb.append("\t\t").append(benchmark.getLongDescription()).append("\n");
					}
				}
			}

			sb.append("\tCount for ").append(entry.getKey()).append(": ").append(entry.getValue().size())
					.append("\n");
			sb.append("\t--------").append("\n");
			mCount = mCount + entry.getValue().size();
		}
		sb.append("Count: ").append(mCount);
		sb.append("\n\n");
		return sb.toString();
	}

}
