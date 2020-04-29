package de.uni_freiburg.informatik.ultimate.lib.acceleratedinterpolation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.TransFormula;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.TransFormulaUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.Substitution;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.PredicateTransformer;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

public class Interpolator {

	private final IPredicateUnifier mPredicateUnifier;
	private final PredicateTransformer<Term, IPredicate, TransFormula> mPredTransformer;
	private final PredicateHelper mPredHelper;
	private final ILogger mLogger;
	private final ManagedScript mScript;
	private final IUltimateServiceProvider mServices;

	/**
	 * Class to help with interplation.
	 *
	 * @param predicateUnifier
	 * @param predTransformer
	 * @param logger
	 * @param script
	 * @param services
	 * @param predHelper
	 */
	public Interpolator(final IPredicateUnifier predicateUnifier,
			final PredicateTransformer<Term, IPredicate, TransFormula> predTransformer, final ILogger logger,
			final ManagedScript script, final IUltimateServiceProvider services, final PredicateHelper predHelper) {
		mPredicateUnifier = predicateUnifier;
		mPredTransformer = predTransformer;
		mPredHelper = predHelper;
		mScript = script;
		mLogger = logger;
		mServices = services;
	}

	/**
	 * Generate inteprolants using a given infeasible counterexample.
	 *
	 * @param counterexample
	 * @return
	 */
	public IPredicate[] generateInterpolants(final List<UnmodifiableTransFormula> counterexample) {
		final IPredicate[] interpolants = new IPredicate[counterexample.size() + 1];

		interpolants[0] = mPredicateUnifier.getTruePredicate();
		interpolants[counterexample.size()] = mPredicateUnifier.getFalsePredicate();
		final Term[] counterexampleTerms = new Term[counterexample.size()];
		for (int i = 0; i < counterexample.size(); i++) {
			counterexampleTerms[i] = counterexample.get(i).getFormula();
		}
		for (int j = 0; j < counterexample.size(); j++) {
			final Term first = mPredTransformer.strongestPostcondition(interpolants[j], counterexample.get(j));
			final IPredicate firstPred = mPredicateUnifier.getOrConstructPredicate(first);
			Term second = mPredicateUnifier.getTruePredicate().getFormula();

			final List<UnmodifiableTransFormula> secondTfList = new ArrayList<>();

			for (int k = j + 1; k < counterexample.size(); k++) {
				second = SmtUtils.and(mScript.getScript(), second, counterexample.get(k).getClosedFormula());
				secondTfList.add(counterexample.get(k));
			}
			final UnmodifiableTransFormula secondTf = TransFormulaUtils.sequentialComposition(mLogger, mServices,
					mScript, true, true, false, XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION,
					SimplificationTechnique.SIMPLIFY_DDA, secondTfList);

			final Map<IProgramVar, TermVariable> inVars = secondTf.getOutVars();
			final Map<IProgramVar, TermVariable> outVars = secondTf.getOutVars();

			final Map<Term, Term> subIn = new HashMap<>();
			final Map<Term, Term> subOut = new HashMap<>();
			// final Map<Term, Term> subFirst = new HashMap<>();
			for (final Entry<IProgramVar, TermVariable> inVar : inVars.entrySet()) {
				subIn.put(inVar.getKey().getDefaultConstant(), inVar.getKey().getTermVariable());
			}
			for (final Entry<IProgramVar, TermVariable> outVar : outVars.entrySet()) {
				subOut.put(outVar.getKey().getPrimedConstant(), outVar.getKey().getTermVariable());
			}

			final Pair<LBool, Term> interpolPair = SmtUtils.interpolateBinary(mScript.getScript(),
					firstPred.getClosedFormula(), secondTf.getClosedFormula());
			/*
			 * Interpolant consists of constants, we need to unconstant them
			 */
			final Substitution substitutionIn = new Substitution(mScript, subIn);
			Term interpolant = substitutionIn.transform(interpolPair.getSecond());

			final Substitution substitutionOut = new Substitution(mScript, subOut);
			interpolant = substitutionOut.transform(interpolant);

			interpolants[j + 1] = mPredicateUnifier.getOrConstructPredicate(interpolant);
		}
		final IPredicate[] actualInterpolants = Arrays.copyOfRange(interpolants, 1, counterexample.size());
		return actualInterpolants;
	}

	/**
	 * Naive way of generating interpolants, by just applying the post operator
	 *
	 * @param counterexample
	 * @return
	 */
	public IPredicate[] generateInterpolantsPost(final List<UnmodifiableTransFormula> counterexample) {
		final IPredicate[] interpolants = new IPredicate[counterexample.size() + 1];
		interpolants[0] = mPredicateUnifier.getTruePredicate();
		interpolants[counterexample.size()] = mPredicateUnifier.getFalsePredicate();
		for (int i = 1; i <= counterexample.size(); i++) {
			interpolants[i] = mPredicateUnifier.getOrConstructPredicate(
					mPredTransformer.strongestPostcondition(interpolants[i - 1], counterexample.get(i - 1)));
		}
		final IPredicate[] actualInterpolants = Arrays.copyOfRange(interpolants, 1, counterexample.size());
		return actualInterpolants;
	}
}
