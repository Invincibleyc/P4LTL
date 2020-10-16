package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.tracehandling;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.automata.nestedword.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.transitions.IncomingInternalTransition;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicateUnifier;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;

/**
 * IInterpolantProvider using strongest postcondition. For every state we use the disjunction of sp for all incoming
 * transitions.
 *
 * @author Frank Schüssele (schuessf@informatik.uni-freiburg.de)
 */
public class SpInterpolantProvider<LETTER extends IIcfgTransition<?>> extends SpWpInterpolantProvider<LETTER> {
	public SpInterpolantProvider(final IUltimateServiceProvider services, final ILogger logger,
			final ManagedScript managedScript, final SimplificationTechnique simplificationTechnique,
			final XnfConversionTechnique xnfConversionTechnique, final IPredicateUnifier predicateUnifier) {
		super(services, logger, managedScript, simplificationTechnique, xnfConversionTechnique, predicateUnifier);
	}

	@Override
	protected <STATE> Term calculateTerm(final INestedWordAutomaton<LETTER, STATE> automaton, final STATE state,
			final Map<STATE, IPredicate> stateMap) {
		final List<Term> disjuncts = new ArrayList<>();
		for (final IncomingInternalTransition<LETTER, STATE> edge : automaton.internalPredecessors(state)) {
			final IPredicate pred = stateMap.get(edge.getPred());
			if (pred != null) {
				disjuncts.add(mPredicateTransformer.strongestPostcondition(pred, edge.getLetter().getTransformula()));
			}
		}
		return disjuncts.isEmpty() ? null : SmtUtils.or(mScript, disjuncts);
	}

	@Override
	protected Term getAbstraction(final Term term, final List<TermVariable> variables) {
		return SmtUtils.quantifier(mScript, QuantifiedFormula.EXISTS, variables, term);
	}

	@Override
	protected boolean useReversedOrder() {
		return false;
	}
}
