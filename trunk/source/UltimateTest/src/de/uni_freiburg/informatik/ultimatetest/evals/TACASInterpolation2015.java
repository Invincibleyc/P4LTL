package de.uni_freiburg.informatik.ultimatetest.evals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.plugins.generator.buchiautomizer.TimingBenchmark;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.CodeCheckBenchmarks;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.TraceAbstractionBenchmarks;
import de.uni_freiburg.informatik.ultimate.util.Benchmark;
import de.uni_freiburg.informatik.ultimate.util.csv.ICsvProviderProvider;
import de.uni_freiburg.informatik.ultimatetest.AbstractModelCheckerTestSuite;
import de.uni_freiburg.informatik.ultimatetest.TraceAbstractionTestSummary;
import de.uni_freiburg.informatik.ultimatetest.UltimateRunDefinition;
import de.uni_freiburg.informatik.ultimatetest.UltimateTestCase;
import de.uni_freiburg.informatik.ultimatetest.decider.ITestResultDecider;
import de.uni_freiburg.informatik.ultimatetest.decider.SafetyCheckTestResultDecider;
import de.uni_freiburg.informatik.ultimatetest.evals.TACAS2015Summary.Aggregate;
import de.uni_freiburg.informatik.ultimatetest.summary.IIncrementalLog;
import de.uni_freiburg.informatik.ultimatetest.summary.ITestSummary;
import de.uni_freiburg.informatik.ultimatetest.summary.IncrementalLogWithVMParameters;
import de.uni_freiburg.informatik.ultimatetest.util.Util;
import de.uni_freiburg.informatik.ultimatetest.util.Util.IPredicate;
import de.uni_freiburg.informatik.ultimatetest.util.Util.IReduce;

/**
 * 
 * Test suite that contains the evaluation for the coming paper
 * "An interpolant generator for when you don't have an interpolant generator"
 * (see https://sotec.informatik.uni-freiburg.de/svn/swt/devel/heizmann/publish/
 * 2015TACAS-Interpolation/)
 * 
 * @author dietsch@informatik.uni-freiburg.de
 * 
 */
public class TACASInterpolation2015 extends AbstractModelCheckerTestSuite {

	private IncrementalLogWithVMParameters mIncrementalLog;
	// @formatter:off
	private static final String[] mDirectories = {
			// not good for CodeCheck
			// "examples/svcomp/eca-rers2012/",
			// "examples/svcomp/ntdrivers-simplified/",

			"examples/svcomp/ssh-simplified/", 
//			"examples/svcomp/loop-invgen/", 
			"examples/svcomp/locks/",
//			"examples/svcomp/loop-new/",
			"examples/svcomp/recursive/", 
			"examples/svcomp/systemc/",
			// "examples/svcomp/systemc/kundu2_false-unreach-call_false-termination.cil.c"

	};
	// @formatter:on

	// Time out for each test case in milliseconds
	private final static int mTimeout = 60 * 1000;
	private final static String[] mFileEndings = new String[] { ".c" };

	// if -1 use all
	private final int mFilesPerCategory = 20;

	@Override
	public Collection<UltimateTestCase> createTestCases() {
		if (mTestCases.size() == 0) {
			List<UltimateTestCase> testcases = new ArrayList<>();
//			addTestCasesFixed("AutomizerC.xml", "TACASInterpolation2015/BackwardPredicates.epf", testcases);
			
			addTestCasesFixed("AutomizerC.xml", "TACASInterpolation2015/ForwardPredicates.epf", testcases);

			addTestCasesFixed("AutomizerC.xml", "TACASInterpolation2015/TreeInterpolation.epf", testcases);

//			
//			addTestCasesFixed("CodeCheckWithBE-C.xml", "TACASInterpolation2015/Kojak-FP.epf", testcases);
//
//			addTestCasesFixed("CodeCheckWithBE-C.xml", "TACASInterpolation2015/Kojak-TreeInterpolation.epf", testcases);
//
//			addTestCasesFixed("ImpulseWithBE-C.xml", "TACASInterpolation2015/Impulse-FP.epf", testcases);
//
//			addTestCasesFixed("ImpulseWithBE-C.xml", "TACASInterpolation2015/Impulse-TreeInterpolation.epf",
//					testcases);
			
			addTestCasesFixed("CodeCheckNoBE-C.xml", "TACASInterpolation2015/Kojak-FP-nBE.epf", testcases);

			addTestCasesFixed("CodeCheckNoBE-C.xml", "TACASInterpolation2015/Kojak-TreeInterpolation-nBE.epf", testcases);

			addTestCasesFixed("ImpulseNoBE-C.xml", "TACASInterpolation2015/Impulse-FP-nBE.epf", testcases);

			addTestCasesFixed("ImpulseNoBE-C.xml", "TACASInterpolation2015/Impulse-TreeInterpolation-nBE.epf",
					testcases);

			if (mFilesPerCategory != -1) {
				mTestCases = testcases;
			}

			mIncrementalLog.setCountTotal(mTestCases.size());
		}
		// Util.filter(files, regex)
		// return Util.firstN(super.createTestCases(), 3);
		return super.createTestCases();
	}

	private void addTestCasesFixed(String toolchain, String setting, List<UltimateTestCase> testcases) {
		addTestCases(toolchain, setting, mDirectories, mFileEndings, mTimeout);
		testcases.addAll(limitTestFiles());
	}

	@Override
	protected ITestSummary[] constructTestSummaries() {
		// @formatter:off
		ArrayList<Class<? extends ICsvProviderProvider<? extends Object>>> benchmarks 
			= new ArrayList<Class<? extends ICsvProviderProvider<? extends Object>>>();
		benchmarks.add(TimingBenchmark.class);
		benchmarks.add(Benchmark.class);
		benchmarks.add(TraceAbstractionBenchmarks.class);
		benchmarks.add(CodeCheckBenchmarks.class);

		String[] columnsToKeep = new String[] { 
				"Runtime (ns)", 
				"Allocated memory end (bytes)", 
				"Overall iterations",
				"NumberOfCodeBlocks", 
				"SizeOfPredicatesFP",
				"SizeOfPredicatesBP",
				"Conjuncts in SSA", 
				"Conjuncts in UnsatCore", 
				"ICC %" 
		};
		String[] tableTitles = new String[] { 
				"Avg. runtime", 
				"Mem{-}ory", 
				"Iter{-}ations", 
				"Loc{-}ations",
				"Pred. size FP",
				"Pred. size BP",
				"Conj. SSA", 
				"Conj. IC", 
				"ICC %" 
		};
		ConversionContext[] conversionInfo = new ConversionContext[] { 
				ConversionContext.Divide(1000000000, 2, " s"),
				ConversionContext.Divide(1048576, 2, " MB"),
				ConversionContext.BestFitNumber(),
				ConversionContext.BestFitNumber(),
				ConversionContext.BestFitNumber(),
				ConversionContext.BestFitNumber(),
				ConversionContext.BestFitNumber(),
				ConversionContext.BestFitNumber(),
				ConversionContext.Percent(true,2)
		};
		Aggregate[] aggregationInfoSingleRunToOneRow = new Aggregate[] {
				Aggregate.Sum,
				Aggregate.Max,
				Aggregate.Ignore,
				Aggregate.Ignore,
				Aggregate.Ignore,
				Aggregate.Ignore,
				Aggregate.Ignore,
				Aggregate.Ignore,
				Aggregate.Ignore,
		};
		Aggregate[] aggregationInfoManyRunsToOneRow = new Aggregate[] {
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
				Aggregate.Average,
		};

		return new ITestSummary[] {
				new TACAS2015Summary(getClass(), benchmarks, columnsToKeep, tableTitles, conversionInfo,
						aggregationInfoSingleRunToOneRow, aggregationInfoManyRunsToOneRow),
				new TraceAbstractionTestSummary(getClass()) };

		// @formatter:on
	}

	@Override
	protected IIncrementalLog[] constructIncrementalLog() {
		if (mIncrementalLog == null) {
			mIncrementalLog = new IncrementalLogWithVMParameters(this.getClass());
		}
		return new IIncrementalLog[] { mIncrementalLog };
	}

	@SuppressWarnings("unused")
	private List<UltimateTestCase> limitTestFiles() {
		if (mFilesPerCategory == -1) {
			return new ArrayList<>();
		}
		List<UltimateTestCase> testcases = new ArrayList<>();

		Set<String> categories = Util.reduceDistinct(mTestCases, new IReduce<String, UltimateTestCase>() {
			@Override
			public String reduce(UltimateTestCase entry) {
				return entry.getUltimateRunDefinition().getInput().getParentFile().getName();
			}
		});

		for (final String category : categories) {
			testcases.addAll(Util.where(mTestCases, new IPredicate<UltimateTestCase>() {
				int i = 0;

				@Override
				public boolean check(UltimateTestCase entry) {
					if (entry.getUltimateRunDefinition().getInput().getParentFile().getName().equals(category)) {
						if (i < mFilesPerCategory) {
							i++;
							return true;
						}
					}
					return false;
				}
			}));
		}
		mTestCases = new ArrayList<>();
		return testcases;
	}

	@Override
	public ITestResultDecider constructITestResultDecider(UltimateRunDefinition urd) {
		return new SafetyCheckTestResultDecider(urd, false);
	}

}
