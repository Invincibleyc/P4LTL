/*
 * Copyright (C) 2021 Jonas Werner (wernerj@informatik.uni-freiburg.de)
 * Copyright (C) 2021 University of Freiburg
 *
 * This file is part of the ULTIMATE IcfgTransformer library.
 *
 * The ULTIMATE IcfgTransformer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE IcfgTransformer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE IcfgTransformer library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE IcfgTransformer library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE IcfgTransformer grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.icfgtransformer.loopacceleration.qvasr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.SimultaneousUpdate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.TransFormulaBuilder;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtSortUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 *
 * A summarizer for an ({@link UnmodifiableTransFormula}).
 *
 * @author Jonas Werner (wernerj@informatik.uni-freiburg.de)
 *
 */
public class QvasrSummarizer {
	private final ILogger mLogger;
	private final ManagedScript mScript;
	private final IUltimateServiceProvider mServices;

	/**
	 * Construct a new ({@link UnmodifiableTransFormula}) summarizer based on rational vector addition systems with
	 * resets (Q-VASR)
	 *
	 * @param logger
	 *            A {@link ILogger}
	 * @param services
	 *            {@link IUltimateServiceProvider}
	 * @param script
	 *            A {@link ManagedScript}
	 */
	public QvasrSummarizer(final ILogger logger, final IUltimateServiceProvider services, final ManagedScript script) {
		mLogger = logger;
		mScript = script;
		mServices = services;

	}

	/**
	 * Summarize a {@link UnmodifiableTransFormula} using Q-Vasr.
	 *
	 * @param transitionFormula
	 *            A {@link UnmodifiableTransFormula} representing changes to variables.
	 * @return A summary of these changes in form of a {@link UnmodifiableTransFormula}
	 */
	public UnmodifiableTransFormula summarizeLoop(final UnmodifiableTransFormula transitionFormula) {
		final SimultaneousUpdate su;
		try {
			su = SimultaneousUpdate.fromTransFormula(transitionFormula, mScript);
		} catch (final Exception e) {
			throw new UnsupportedOperationException("Could not compute Simultaneous Update!");
		}
		final Map<IProgramVar, TermVariable> inVarsReal = new HashMap<>();
		final Map<IProgramVar, TermVariable> outVarsReal = new HashMap<>();
		for (final IProgramVar assVar : su.getDeterministicAssignment().keySet()) {
			if (transitionFormula.getInVars().containsKey(assVar)) {
				inVarsReal.put(assVar, transitionFormula.getInVars().get(assVar));
			} else if (transitionFormula.getOutVars().containsKey(assVar)) {
				inVarsReal.put(assVar, transitionFormula.getOutVars().get(assVar));
			}
			if (transitionFormula.getOutVars().containsKey(assVar)) {
				outVarsReal.put(assVar, transitionFormula.getOutVars().get(assVar));
			}
		}

		final int tfDimension = transitionFormula.getAssignedVars().size();
		final Rational[][] identityMatrix = QvasrUtils.getIdentityMatrix(tfDimension);
		QvasrAbstraction bestAbstraction = new QvasrAbstraction(identityMatrix, new Qvasr());
		final Term transitionTerm = transitionFormula.getFormula();
		final Term transitionTermDnf = SmtUtils.toDnf(mServices, mScript, transitionTerm,
				XnfConversionTechnique.BOTTOM_UP_WITH_LOCAL_SIMPLIFICATION);
		final List<Term> disjuncts = QvasrUtils.splitDisjunction(transitionTermDnf);

		for (final Term disjunct : disjuncts) {
			final UnmodifiableTransFormula disjunctTf = QvasrUtils.buildFormula(transitionFormula, disjunct, mScript);
			final QvasrAbstraction qvasrAbstraction = QvasrAbstractor.computeAbstraction(mScript, disjunctTf);
			bestAbstraction = QvasrAbstractionJoin.join(mScript, bestAbstraction, qvasrAbstraction).getThird();
		}

		final IntvasrAbstraction intVasrAbstraction = QvasrUtils.qvasrAbstractionToInt(bestAbstraction);
		return intVasrAbstractionToFormula(mScript, intVasrAbstraction, inVarsReal, outVarsReal);
	}

	/**
	 * /** Compute a {@link UnmodifiableTransFormula} as loop summary. This version can deal with branching loops.
	 *
	 *
	 * @param script
	 *            A {@link ManagedScript}
	 * @param intvasrAbstraction
	 *            A {@link QvasrAbstraction} whose reachability relation we want to compute.
	 *
	 *
	 * @param invars
	 *            Invariables of the original loop formula.
	 * @param outvars
	 *            Outvariables of the original loopformula.
	 * @return An overapproximative loop summary computed from an {@link IntvasrAbstraction}.
	 */
	public static UnmodifiableTransFormula intVasrAbstractionToFormula(final ManagedScript script,
			final IntvasrAbstraction intvasrAbstraction, final Map<IProgramVar, TermVariable> invars,
			final Map<IProgramVar, TermVariable> outvars) {
		final Term[] inVarsReal = invars.values().toArray(new Term[invars.size()]);
		final Term[] outVarsReal = outvars.values().toArray(new Term[outvars.size()]);

		final Map<IProgramVar, TermVariable> newInvars = invars;
		final Map<IProgramVar, TermVariable> newOutvars = outvars;

		final Term[][] variableRelationsIn = QvasrUtils.matrixVectorMultiplicationWithVariables(script,
				intvasrAbstraction.getSimulationMatrix(), QvasrUtils.transposeRowToColumnTermVector(inVarsReal));
		final Term[][] variableRelationsOut = QvasrUtils.matrixVectorMultiplicationWithVariables(script,
				intvasrAbstraction.getSimulationMatrix(), QvasrUtils.transposeRowToColumnTermVector(outVarsReal));

		final List<Term> qvasrDimensionConjunction = new ArrayList<>();

		final Map<Integer, TermVariable> kToTransformer = new HashMap<>();

		for (int dimension = 0; dimension < intvasrAbstraction.getVasr().getDimension(); dimension++) {
			final Set<Term> dimensionDisjunction = new HashSet<>();
			Term dimensionSumTerm = variableRelationsIn[dimension][0];
			boolean incrementFlag = false;
			int transformerId = 0;
			for (final Pair<Integer[], Integer[]> transformer : intvasrAbstraction.getVasr().getTransformer()) {
				final Integer dimensionReset = transformer.getFirst()[dimension];
				final Integer dimensionAddition = transformer.getSecond()[dimension];
				if (dimensionReset == 0) {
					final Term equality =
							SmtUtils.binaryEquality(script.getScript(), variableRelationsOut[dimension][0],
									script.getScript().numeral(dimensionAddition.toString()));
					dimensionDisjunction.add(equality);
				} else {
					TermVariable k;
					if (kToTransformer.containsKey(transformerId)) {
						k = kToTransformer.get(transformerId);
					} else {
						k = script.constructFreshTermVariable("k", SmtSortUtils.getIntSort(script));
						kToTransformer.put(transformerId, k);
					}
					final Term quantifiedAddition = SmtUtils.mul(script.getScript(), "*",
							script.getScript().numeral(transformer.getSecond()[dimension].toString()), k);
					dimensionSumTerm = SmtUtils.sum(script.getScript(), "+", dimensionSumTerm, quantifiedAddition);
					incrementFlag = true;
				}
				transformerId++;
			}
			if (incrementFlag) {
				final Term equality = SmtUtils.binaryEquality(script.getScript(), variableRelationsOut[dimension][0],
						dimensionSumTerm);
				dimensionDisjunction.add(equality);
			}
			qvasrDimensionConjunction.add(SmtUtils.or(script.getScript(), dimensionDisjunction));
		}

		for (final Term k : kToTransformer.values()) {
			final Term kGeqZero = SmtUtils.geq(script.getScript(), k, script.getScript().numeral("0"));
			qvasrDimensionConjunction.add(kGeqZero);
		}
		Term loopSummary = SmtUtils.and(script.getScript(), qvasrDimensionConjunction);
		loopSummary = SmtUtils.quantifier(script.getScript(), QuantifiedFormula.EXISTS, kToTransformer.values(),
				SmtUtils.and(script.getScript(), loopSummary));
		final TransFormulaBuilder tfb = new TransFormulaBuilder(newInvars, newOutvars, true, null, true, null, true);
		tfb.setFormula(loopSummary);
		tfb.setInfeasibility(Infeasibility.NOT_DETERMINED);
		return tfb.finishConstruction(script);
	}
}
