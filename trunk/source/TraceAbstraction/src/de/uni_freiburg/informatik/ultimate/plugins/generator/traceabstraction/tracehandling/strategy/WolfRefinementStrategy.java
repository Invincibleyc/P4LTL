/*
 * Copyright (C) 2016 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2018-2019 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2018-2019 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.strategy;

import java.util.ArrayList;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.SolverBuilder;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.TermClassifier;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.tracecheck.TraceCheckReasonUnknown.RefinementStrategyExceptionBlacklist;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.interpolantautomata.builders.StraightLineInterpolantAutomatonBuilder;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.InterpolationTechnique;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TraceAbstractionPreferenceInitializer.RefinementStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.IRefinementStrategy;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.IIpTcStrategyModule;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.RefinementStrategyUtils;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling.StrategyModuleFactory;

/**
 * {@link IRefinementStrategy} that first tries either {@code MathSat} for floating points or {@code CVC4} in bitvector
 * mode, and then {@code Z3}.
 * <p>
 * The class uses a {@link StraightLineInterpolantAutomatonBuilder} for constructing the interpolant automaton.
 *
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 */
public class WolfRefinementStrategy<LETTER extends IIcfgTransition<?>> extends BasicRefinementStrategy<LETTER> {

	public WolfRefinementStrategy(final StrategyModuleFactory<LETTER> factory,
			final RefinementStrategyExceptionBlacklist exceptionBlacklist) {
		super(factory, createModules(factory),
				factory.createIpAbStrategyModuleStraightlineAll(), exceptionBlacklist);
	}

	@SuppressWarnings("unchecked")
	private static <LETTER extends IIcfgTransition<?>> IIpTcStrategyModule<?, LETTER>[]
			createModules(final StrategyModuleFactory<LETTER> factory) {

		final TermClassifier tc = factory.getTermClassifierForTrace();
		final List<IIpTcStrategyModule<?, LETTER>> rtr = new ArrayList<>();
		if (RefinementStrategyUtils.hasNoFloats(tc)) {
			rtr.add(factory.createIpTcStrategyModuleCVC4(false, InterpolationTechnique.FPandBP,
					SolverBuilder.LOGIC_CVC4_BITVECTORS));
		} else if (RefinementStrategyUtils.hasNoQuantifiersNoBitvectorExtensions(tc)) {
			// floats, but no quantifiers and no extensions
			rtr.add(factory.createIpTcStrategyModuleMathsat(InterpolationTechnique.FPandBP));
		}
		rtr.add(factory.createIpTcStrategyModuleZ3(false, InterpolationTechnique.FPandBP));
		return rtr.toArray(new IIpTcStrategyModule[rtr.size()]);
	}

	@Override
	public String getName() {
		return RefinementStrategy.WOLF.toString();
	}
}