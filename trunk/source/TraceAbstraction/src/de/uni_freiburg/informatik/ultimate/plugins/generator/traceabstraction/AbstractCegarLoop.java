/*
 * Copyright (C) 2013-2021 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2010-2021 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015-2021 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstraction plug-in.
 *
 * The ULTIMATE TraceAbstraction plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstraction plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstraction plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstraction plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstraction plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryException;
import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter.Format;
import de.uni_freiburg.informatik.ultimate.automata.AutomatonDefinitionPrinter.NamedAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.IAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.IRun;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.INwaBasis;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.IPetriNet;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.IRunningTaskStackProvider;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.RunningTaskInfo;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.TaskCanceledException;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.TaskCanceledException.UserDefinedLimit;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainCanceledException;
import de.uni_freiburg.informatik.ultimate.core.lib.exceptions.ToolchainExceptionWrapper;
import de.uni_freiburg.informatik.ultimate.core.lib.results.UnprovabilityReason;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.core.model.translation.AtomicTraceElement;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfg;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.debugidentifiers.DebugIdentifier;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.hoaretriple.IncrementalHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.interpolant.IInterpolantGenerator;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.HoareAnnotation;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.PredicateFactory;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.taskidentifier.SubtaskFileIdentifier;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.taskidentifier.TaskIdentifier;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.transitionappender.AbstractInterpolantAutomaton;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.InductivityCheck;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences.Artifact;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.FloydHoareAutomataReuse;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.HoareAnnotationPositions;
import de.uni_freiburg.informatik.ultimate.util.ReflectionUtil;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;
import de.uni_freiburg.informatik.ultimate.util.statistics.IStatisticsDataProvider;

/**
 * CEGAR loop of a trace abstraction. Can be used to check safety and termination of sequential and concurrent programs.
 * Defines roughly the structure of the CEGAR loop, concrete algorithms are implemented in classes which extend this
 * one.
 *
 * @author heizmann@informatik.uni-freiburg.de
 */
public abstract class AbstractCegarLoop<L extends IIcfgTransition<?>> {
	private static final boolean DUMP_BIGGEST_AUTOMATON = false;
	private static final boolean EXTENDED_HOARE_ANNOTATION_LOGGING = true;

	protected final ILogger mLogger;
	protected final SimplificationTechnique mSimplificationTechnique;
	protected final XnfConversionTechnique mXnfConversionTechnique;
	protected final Class<L> mTransitionClazz;

	/**
	 * Interprocedural control flow graph.
	 */
	protected final IIcfg<? extends IcfgLocation> mIcfg;

	/**
	 * Intermediate layer to encapsulate communication with SMT solvers.
	 */
	protected final CfgSmtToolkit mCsToolkit;
	protected final PredicateFactory mPredicateFactory;

	/**
	 * Intermediate layer to encapsulate preferences.
	 */
	protected final TAPreferences mPref;
	protected final boolean mComputeHoareAnnotation;

	/**
	 * Set of error location whose reachability is analyzed by this CEGAR loop.
	 */
	protected final Set<? extends IcfgLocation> mErrorLocs;

	/**
	 * Current Iteration of this CEGAR loop.
	 */
	protected int mIteration;

	/**
	 * Accepting run of the abstraction obtained in this iteration.
	 */
	protected IRun<L, ?> mCounterexample;

	/**
	 * Abstraction of this iteration. The language of mAbstraction is a set of traces which is
	 * <ul>
	 * <li>a superset of the feasible program traces.
	 * <li>a subset of the traces which respect the control flow of the program.
	 */
	protected IAutomaton<L, IPredicate> mAbstraction;

	/**
	 * IInterpolantGenerator that was used in the current iteration.
	 */
	protected IInterpolantGenerator<L> mInterpolantGenerator;

	/**
	 * Interpolant automaton of this iteration.
	 */
	protected NestedWordAutomaton<L, IPredicate> mInterpolAutomaton;

	// used for debugging only
	protected IAutomaton<L, IPredicate> mArtifactAutomaton;

	protected final Format mPrintAutomataLabeling;

	protected CegarLoopStatisticsGenerator mCegarLoopBenchmark;

	protected final IUltimateServiceProvider mServices;

	protected final TaskIdentifier mTaskIdentifier;

	protected Dumper mDumper;

	/**
	 * Unique mName of this CEGAR loop to distinguish this instance from other instances in a complex verification task.
	 * Important only for debugging and debugging output written to files.
	 */
	private final DebugIdentifier mName;
	protected final CegarLoopResultBuilder mResultBuilder;

	protected AbstractCegarLoop(final IUltimateServiceProvider services, final DebugIdentifier name,
			final IIcfg<?> rootNode, final CfgSmtToolkit csToolkit, final PredicateFactory predicateFactory,
			final TAPreferences taPrefs, final Set<? extends IcfgLocation> errorLocs, final ILogger logger,
			final Class<L> transitionClazz, final boolean computeHoareAnnotation) {
		mServices = services;
		mLogger = logger;
		mSimplificationTechnique = taPrefs.getSimplificationTechnique();
		mXnfConversionTechnique = taPrefs.getXnfConversionTechnique();
		mPrintAutomataLabeling = taPrefs.getAutomataFormat();
		mName = name;
		mIcfg = rootNode;
		mCsToolkit = csToolkit;
		mPredicateFactory = predicateFactory;
		mPref = taPrefs;
		mErrorLocs = errorLocs;
		mTransitionClazz = transitionClazz;
		mComputeHoareAnnotation = computeHoareAnnotation;
		// TODO: TaskIdentifier should probably be provided by caller
		mTaskIdentifier = new SubtaskFileIdentifier(null, mIcfg.getIdentifier() + "_" + name);
		mResultBuilder = new CegarLoopResultBuilder();
	}

	/**
	 * Construct the automaton mAbstraction such that the language recognised by mAbstraction is a superset of the
	 * language of the program. The initial abstraction in our implementations will usually be an automaton that has the
	 * same graph as the program.
	 *
	 * @throws AutomataLibraryException
	 */
	protected abstract void getInitialAbstraction() throws AutomataLibraryException;

	/**
	 * Return true iff the mAbstraction does not accept any trace.
	 *
	 * @throws AutomataOperationCanceledException
	 */
	protected abstract boolean isAbstractionEmpty() throws AutomataOperationCanceledException;

	/**
	 * Determine if the trace of mCounterexample is a feasible sequence of CodeBlocks. Return
	 * <ul>
	 * <li>SAT if the trace is feasible
	 * <li>UNSAT if the trace is infeasible
	 * <li>UNKNOWN if the algorithm was not able to determine the feasibility.
	 * </ul>
	 *
	 * @throws AutomataOperationCanceledException
	 *
	 *             TODO Christian 2016-11-11: Merge the methods isCounterexampleFeasible() and
	 *             constructInterpolantAutomaton() after {@link TreeAutomizerCEGAR} does not depend on this class
	 *             anymore.
	 */
	protected abstract Pair<LBool, IProgramExecution<L, Term>> isCounterexampleFeasible()
			throws AutomataOperationCanceledException;

	/**
	 * Construct an automaton mInterpolantAutomaton which
	 * <ul>
	 * <li>accepts the trace of mCounterexample,
	 * <li>accepts only infeasible traces.
	 * </ul>
	 *
	 * @throws AutomataOperationCanceledException
	 */
	protected abstract void constructInterpolantAutomaton() throws AutomataOperationCanceledException;

	/**
	 * Construct an automaton that
	 * <ul>
	 * <li>accepts the trace of mCounterexample,
	 * <li>accepts only feasible traces.
	 * </ul>
	 */
	protected abstract void constructErrorAutomaton(final LBool isCounterexampleFeasible)
			throws AutomataOperationCanceledException;

	/**
	 * Construct a new automaton mAbstraction such that
	 * <ul>
	 * <li>the language of the new mAbstraction is (not necessary strictly) smaller than the language of the old
	 * mAbstraction
	 * <li>the new mAbstraction accepts all feasible traces of the old mAbstraction (only infeasible traces are removed)
	 * <ul>
	 *
	 * @return true iff the trace of mCounterexample (which was accepted by the old mAbstraction) is not accepted by the
	 *         mAbstraction.
	 * @throws AutomataLibraryException
	 */
	protected abstract boolean refineAbstraction() throws AutomataLibraryException;

	/**
	 * In case error traces are not reported immediately, the analysis may terminate with an empty abstraction or may
	 * run into termination issues, but it has already found out that the program contains errors. This method can be
	 * used to ask for such results whenever the analysis terminates.
	 *
	 * @param errorGeneralizationEnabled
	 *            {@code true} iff error generalization is enabled
	 * @param abstractResult
	 *            result that would be reported by {@link AbstractCegarLoop}
	 * @return {@code true} if at least one feasible counterexample was detected
	 */
	protected abstract boolean isResultUnsafe(boolean errorGeneralizationEnabled, Result abstractResult);

	/**
	 * Add Hoare annotation to the control flow graph. Use the information computed so far annotate the ProgramPoints of
	 * the control flow graph with invariants.
	 */
	protected abstract void computeIcfgHoareAnnotation();

	protected abstract Set<Pair<AbstractInterpolantAutomaton<L>, IPredicateUnifier>> getFloydHoareAutomata();

	/**
	 * Return the Artifact whose computation was requested. This artifact can be either the control flow graph, an
	 * abstraction, an interpolant automaton, or a negated interpolant automaton. The artifact is only used for
	 * debugging.
	 *
	 * @return The root node of the artifact after it was transformed to an ULTIMATE model.
	 */
	public abstract IElement getArtifact();

	public int getIteration() {
		return mIteration;
	}

	public String errorLocs() {
		return mErrorLocs.toString();
	}

	public IStatisticsDataProvider getCegarLoopBenchmark() {
		return mCegarLoopBenchmark;
	}

	/**
	 * Method that is called at the end of {@link #iterate()}.
	 */
	protected abstract void finish();

	public final CegarLoopResult<L> iterate() {
		final CegarLoopResult<L> r = iterateInternal();
		finish();
		return r;
	}

	public final CegarLoopResult<L> iterateInternal() {
		mIteration = 0;
		if (mLogger.isInfoEnabled()) {
			mLogger.info("======== Iteration %s == of CEGAR loop == %s ========", mIteration, mName);
			mLogger.info("Settings: %s", ReflectionUtil.instanceFieldsToString(mPref));
			mLogger.info("Starting to check reachability of %s error locations.", mErrorLocs.size());
		}
		// initialize dump of debugging output to files if necessary
		if (mPref.dumpAutomata()) {
			mDumper = new Dumper(mLogger, mPref, mName, mIteration);
		}
		try {
			mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.InitialAbstractionConstructionTime.toString());
			try {
				abortIfTimeout();
				getInitialAbstraction();
			} catch (final AutomataOperationCanceledException aoce) {
				final RunningTaskInfo runningTaskInfo =
						new RunningTaskInfo(this.getClass(), "constructing initial abstraction");
				aoce.addRunningTaskInfo(runningTaskInfo);
				throw aoce;
			} catch (final ToolchainCanceledException tce) {
				final RunningTaskInfo runningTaskInfo =
						new RunningTaskInfo(this.getClass(), "constructing initial abstraction");
				tce.addRunningTaskInfo(runningTaskInfo);
				throw tce;
			} finally {
				mCegarLoopBenchmark.stop(CegarLoopStatisticsDefinitions.InitialAbstractionConstructionTime.toString());
			}

			if (mIteration <= mPref.watchIteration()
					&& (mPref.artifact() == Artifact.ABSTRACTION || mPref.artifact() == Artifact.RCFG)) {
				mArtifactAutomaton = mAbstraction;
			}
			if (mPref.dumpAutomata()) {
				final String filename = mTaskIdentifier + "_InitialAbstraction";
				writeAutomatonToFile(mAbstraction, filename);
			}
			mCegarLoopBenchmark.reportAbstractionSize(mAbstraction.size(), mIteration);

			final boolean initalAbstractionCorrect = isAbstractionEmpty();
			if (initalAbstractionCorrect) {
				mResultBuilder.addResultForAllRemaining(Result.SAFE);
			}

			for (mIteration = 1; mIteration <= mPref.maxIterations(); mIteration++) {
				abortIfTimeout();
				final String msg = String.format("=== Iteration %s === %s ===", mIteration, errorLocs());
				mServices.getStorage().pushMarker(msg);
				mLogger.info(msg);
				try {

					mCegarLoopBenchmark.announceNextIteration();
					if (mPref.dumpAutomata()) {
						mDumper = new Dumper(mLogger, mPref, mName, mIteration);
					}

					final String automatonType;
					final Pair<LBool, IProgramExecution<L, Term>> isCexResult = isCounterexampleFeasible();
					final LBool isCounterexampleFeasible = isCexResult.getFirst();
					final IProgramExecution<L, Term> programExecution = isCexResult.getSecond();
					if (isCounterexampleFeasible == Script.LBool.SAT) {
						mResultBuilder.addResultForProgramExecution(Result.UNSAFE, programExecution, null, null);
						if (mPref.allErrorLocsAtOnce()) {
							return mResultBuilder.addResultForAllRemaining(Result.UNKNOWN).getResult();
						}
						if (mLogger.isInfoEnabled()) {
							mLogger.info("Generalizing and excluding counterexample to continue analysis");
						}
						automatonType = "Error";
						constructErrorAutomaton(isCounterexampleFeasible);
					} else if (isCounterexampleFeasible == Script.LBool.UNKNOWN) {
						final UnprovabilityReason reasonUnknown =
								new UnprovabilityReason("unable to decide satisfiability of path constraint");
						mResultBuilder.addResultForProgramExecution(Result.UNKNOWN, programExecution, null,
								reasonUnknown);
						if (mPref.allErrorLocsAtOnce()) {
							return mResultBuilder.addResultForAllRemaining(Result.UNKNOWN).getResult();
						}
						if (mLogger.isInfoEnabled()) {
							mLogger.warn("Generalizing and excluding unknown counterexample to continue analysis");
						}
						automatonType = "Unknown";
						constructErrorAutomaton(isCounterexampleFeasible);
					} else {
						automatonType = "Interpolant";
						constructInterpolantAutomaton();
					}

					if (mInterpolAutomaton != null) {
						mLogger.info("%s automaton has %s states", automatonType,
								mInterpolAutomaton.getStates().size());
						if (mIteration <= mPref.watchIteration()
								&& mPref.artifact() == Artifact.INTERPOLANT_AUTOMATON) {
							mArtifactAutomaton = mInterpolAutomaton;
						}
					}

					final boolean progress = refineAbstraction();
					if (!progress) {
						final String msgNoProgress =
								"No progress! Counterexample is still accepted by refined abstraction.";
						mLogger.fatal(msgNoProgress);
						throw new AssertionError(msgNoProgress);
					}

					if (mInterpolAutomaton != null) {
						mLogger.info("Abstraction has %s", mAbstraction.sizeInformation());
						mLogger.info("%s automaton has %s", automatonType, mInterpolAutomaton.sizeInformation());
					}

					if (mComputeHoareAnnotation
							&& mPref.getHoareAnnotationPositions() == HoareAnnotationPositions.All) {
						assert new InductivityCheck<>(mServices, (INestedWordAutomaton<L, IPredicate>) mAbstraction,
								false, true, new IncrementalHoareTripleChecker(mCsToolkit, false))
										.getResult() : "Not inductive";
					}

					if (mIteration <= mPref.watchIteration() && mPref.artifact() == Artifact.ABSTRACTION) {
						mArtifactAutomaton = mAbstraction;
					}

					final boolean newMaximumReached =
							mCegarLoopBenchmark.reportAbstractionSize(mAbstraction.size(), mIteration);
					if (DUMP_BIGGEST_AUTOMATON && mIteration > 4 && newMaximumReached) {
						final String filename = mIcfg.getIdentifier() + "_BiggestAutomaton";
						writeAutomatonToFile(mAbstraction, filename);
					}

					final boolean isAbstractionCorrect = isAbstractionEmpty();
					if (isAbstractionCorrect) {
						return mResultBuilder.addResultForAllRemaining(Result.SAFE).getResult();
					}
				} finally {
					final Set<String> destroyedStorables = mServices.getStorage().destroyMarker(msg);
					if (!destroyedStorables.isEmpty()) {
						mLogger.warn("Destroyed unattended storables created during the last iteration: "
								+ destroyedStorables.stream().collect(Collectors.joining(",")));
					}
				}
			}
			return mResultBuilder.addResultForAllRemaining(Result.USER_LIMIT_ITERATIONS).getResult();
		} catch (AutomataOperationCanceledException | ToolchainCanceledException e) {
			return performLimitReachedActions(e);
		} catch (final AutomataLibraryException e) {
			throw new ToolchainExceptionWrapper(Activator.PLUGIN_ID, e);
		}
	}

	private CegarLoopResult<L> performLimitReachedActions(final IRunningTaskStackProvider e) {
		mLogger.warn("Verification canceled: %s", e.printRunningTaskMessage());

		final Result res;
		if (e instanceof TaskCanceledException) {
			final EnumSet<UserDefinedLimit> limits = ((TaskCanceledException) e).getLimits();
			res = Result.convert(limits.iterator().next());
		} else {
			res = Result.TIMEOUT;
		}
		return mResultBuilder.addResultForAllRemaining(res, null, e, null).getResult();
	}

	protected void writeAutomatonToFile(final IAutomaton<L, IPredicate> automaton, final String filename) {
		mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.DumpTime);
		new AutomatonDefinitionPrinter<String, String>(new AutomataLibraryServices(mServices),
				determineAutomatonName(automaton), mPref.dumpPath() + File.separator + filename, mPrintAutomataLabeling,
				"", automaton);
		mCegarLoopBenchmark.stop(CegarLoopStatisticsDefinitions.DumpTime);
	}

	protected void writeAutomataToFile(final String filename, final String atsHeaderMessage, final String atsCommands,
			final NamedAutomaton<L, IPredicate>... automata) {
		mCegarLoopBenchmark.start(CegarLoopStatisticsDefinitions.DumpTime);
		AutomatonDefinitionPrinter.writeAutomatonToFile(new AutomataLibraryServices(mServices),
				mPref.dumpPath() + File.separator + filename, mPrintAutomataLabeling, atsHeaderMessage, atsCommands,
				automata);
		mCegarLoopBenchmark.stop(CegarLoopStatisticsDefinitions.DumpTime);
	}

	private String determineAutomatonName(final IAutomaton<L, IPredicate> automaton) {
		String result;
		if (automaton instanceof INwaBasis) {
			result = "nwa";
		} else if (automaton instanceof IPetriNet) {
			result = "net";
		} else {
			throw new UnsupportedOperationException(
					"unknown kind of automaton " + automaton.getClass().getSimpleName());
		}
		return result;
	}

	protected void abortIfTimeout() {
		if (!mServices.getProgressMonitorService().continueProcessing()) {
			throw new ToolchainCanceledException(getClass());
		}
	}

	/**
	 * Result of CEGAR loop iteration
	 */
	public enum Result {
		/**
		 * there is no feasible trace to an error location
		 */
		SAFE(2),
		/**
		 * there is a feasible trace to an error location (the underlying program has at least one execution which
		 * violates its specification)
		 */
		UNSAFE(1),
		/**
		 * The core timeout was hit or a user canceled the verification
		 */
		TIMEOUT(4),
		/**
		 * we found a trace for which we could not decide feasibility or we found an infeasible trace but were not able
		 * to exclude it in abstraction refinement.
		 */
		UNKNOWN(3),
		/**
		 * The user-specified timeout for the analysis was hit
		 */
		USER_LIMIT_TIME(4),
		/**
		 * The user-specified limit for the max-size of the trace histogram was hit
		 */
		USER_LIMIT_TRACEHISTOGRAM(4),
		/**
		 * The user-specified limit for the amount of CEGAR iterations was hit
		 */
		USER_LIMIT_ITERATIONS(4),
		/**
		 * The user-specified limit for the amount of analysis attempts per path program was hit
		 */
		USER_LIMIT_PATH_PROGRAM(4);

		private final int mHierarchy;

		Result(final int hierarchy) {
			mHierarchy = hierarchy;
		}

		/**
		 * Compare two results and return true iff this is more important or of equal importance than the other
		 * according to their partial order: {@link #UNSAFE} >> {@link #SAFE} >> {@link #UNKNOWN} >> the rest.
		 */
		public boolean compareAuthority(final Result other) {
			return mHierarchy <= other.mHierarchy;
		}

		/**
		 * @return true if one result is {@link #UNSAFE} and the other is {@link #SAFE}, false otherwise.
		 */
		public boolean isConflicting(final Result other) {
			return this == SAFE && other == UNSAFE || this == UNSAFE && other == SAFE;
		}

		public static final Set<Result> USER_LIMIT_RESULTS =
				EnumSet.of(USER_LIMIT_ITERATIONS, USER_LIMIT_PATH_PROGRAM, USER_LIMIT_TIME, USER_LIMIT_TRACEHISTOGRAM);

		public static final Result convert(final TaskCanceledException.UserDefinedLimit limit) {
			switch (limit) {
			case ITERATIONS:
				return Result.USER_LIMIT_ITERATIONS;
			case PATH_PROGRAM_ATTEMPTS:
				return Result.USER_LIMIT_PATH_PROGRAM;
			case TIME_PER_ERROR_LOCATION:
				return USER_LIMIT_TIME;
			case TRACE_HISTOGRAM:
				return Result.USER_LIMIT_TRACEHISTOGRAM;
			default:
				throw new UnsupportedOperationException("Unknown UserDefinedLimit " + limit);
			}
		}

	}

	private final class CegarLoopResultBuilder {
		private final Map<IcfgLocation, CegarLoopLocalResult<L>> mResults = new LinkedHashMap<>();

		public CegarLoopResultBuilder addResultForAllRemaining(final Result result) {
			return addResultForAllRemaining(result, null, null, null);
		}

		public CegarLoopResultBuilder addResultForAllRemaining(final Result result,
				final IProgramExecution<L, Term> rcfgProgramExecution, final IRunningTaskStackProvider rtsp,
				final UnprovabilityReason reasonUnknown) {
			mErrorLocs.stream().filter(elem -> !mResults.keySet().contains(elem))
					.forEachOrdered(a -> addResult(a, result, rcfgProgramExecution, rtsp, reasonUnknown));
			return this;
		}

		public CegarLoopResultBuilder addResultForProgramExecution(final Result result,
				final IProgramExecution<L, Term> programExecution, final IRunningTaskStackProvider rtsp,
				final UnprovabilityReason reasonUnknown) {
			final AtomicTraceElement<L> lastElem = programExecution.getTraceElement(programExecution.getLength() - 1);
			final IcfgLocation loc = lastElem.getStep().getTarget();
			return addResult(loc, result, programExecution, rtsp, reasonUnknown);
		}

		public CegarLoopResultBuilder addResult(final IcfgLocation loc, final Result result,
				final IProgramExecution<L, Term> rcfgProgramExecution, final IRunningTaskStackProvider rtsp,
				final UnprovabilityReason reasonUnknown) {

			final IProgramExecution<L, Term> programExecution;
			if (result == Result.UNSAFE || result == Result.UNKNOWN) {
				programExecution = rcfgProgramExecution;
			} else {
				programExecution = null;
				assert rcfgProgramExecution == null;
			}

			final List<UnprovabilityReason> unprovabilityReasons;
			if (result == Result.UNKNOWN) {
				unprovabilityReasons = new ArrayList<>();
				if (reasonUnknown != null) {
					unprovabilityReasons.add(reasonUnknown);
				}

				if (programExecution != null) {
					unprovabilityReasons.addAll(UnprovabilityReason.getUnprovabilityReasons(programExecution));
				}

				if (unprovabilityReasons.isEmpty()) {
					unprovabilityReasons.add(new UnprovabilityReason("Not analyzed"));
				}

			} else {
				unprovabilityReasons = null;
				assert reasonUnknown == null;
			}

			final IRunningTaskStackProvider runningTaskStackProvider;
			if (result == Result.TIMEOUT || result == Result.USER_LIMIT_ITERATIONS
					|| result == Result.USER_LIMIT_PATH_PROGRAM || result == Result.USER_LIMIT_TIME
					|| result == Result.USER_LIMIT_TRACEHISTOGRAM) {
				runningTaskStackProvider = rtsp;
			} else {
				runningTaskStackProvider = null;
				assert rtsp == null;
			}

			final CegarLoopLocalResult<L> localResult = new CegarLoopLocalResult<>(result, programExecution,
					unprovabilityReasons, runningTaskStackProvider);
			final CegarLoopLocalResult<L> old = mResults.get(loc);
			if (old != null) {
				if (old.getResult().isConflicting(localResult.getResult())) {
					throw new AssertionError(String.format("New result %s conflicts with already found result %s",
							result, old.getResult()));
				}
				if (old.getResult().compareAuthority(localResult.getResult())) {
					mLogger.warn("Keeping previous result %s instead of new one %s", old, localResult);
					return this;
				}
				mLogger.warn("Overwriting previous result %s with the new one %s", old, localResult);
			}
			mResults.put(loc, localResult);
			return this;

		}

		public CegarLoopResult<L> getResult() {
			final IStatisticsDataProvider cegarLoopBenchmarkGenerator = getCegarLoopBenchmark();

			final List<Pair<AbstractInterpolantAutomaton<L>, IPredicateUnifier>> floydHoareAutomata;
			if (mPref.getFloydHoareAutomataReuse() != FloydHoareAutomataReuse.NONE) {
				floydHoareAutomata = new ArrayList<>(getFloydHoareAutomata());
			} else {
				floydHoareAutomata = null;
			}

			if (mComputeHoareAnnotation && mResults.values().stream().anyMatch(a -> a.getResult() == Result.SAFE)) {
				computeIcfgHoareAnnotation();
				writeHoareAnnotationToLogger();
			} else {
				mLogger.debug("Omitting computation of Hoare annotation");
			}
			return new CegarLoopResult<>(mResults, cegarLoopBenchmarkGenerator, getArtifact(), floydHoareAutomata);
		}

		@SuppressWarnings("unchecked")
		private void writeHoareAnnotationToLogger() {
			final IIcfg<IcfgLocation> root = (IIcfg<IcfgLocation>) mIcfg;
			for (final Entry<String, Map<DebugIdentifier, IcfgLocation>> proc2label2pp : root.getProgramPoints()
					.entrySet()) {
				for (final IcfgLocation pp : proc2label2pp.getValue().values()) {
					final HoareAnnotation hoare = HoareAnnotation.getAnnotation(pp);

					if (hoare != null && !SmtUtils.isTrueLiteral(hoare.getFormula())) {

						mLogger.info("At program point %s the Hoare annotation is: %s", prettyPrintProgramPoint(pp),
								hoare.getFormula());
					} else if (EXTENDED_HOARE_ANNOTATION_LOGGING) {
						if (hoare == null) {
							mLogger.info("For program point %s no Hoare annotation was computed.",
									prettyPrintProgramPoint(pp));
						} else {
							mLogger.info("At program point %s the Hoare annotation is: %s", prettyPrintProgramPoint(pp),
									hoare.getFormula());
						}
					}
				}
			}
		}

		private String prettyPrintProgramPoint(final IcfgLocation pp) {
			final ILocation loc = ILocation.getAnnotation(pp);
			if (loc == null) {
				return "";
			}
			final int startLine = loc.getStartLine();
			final int endLine = loc.getEndLine();
			final StringBuilder sb = new StringBuilder();
			sb.append(pp);
			if (startLine == endLine) {
				sb.append("(line ");
				sb.append(startLine);
			} else {
				sb.append("(lines ");
				sb.append(startLine);
				sb.append(" ");
				sb.append(endLine);
			}
			sb.append(")");
			return sb.toString();
		}

	}

}
