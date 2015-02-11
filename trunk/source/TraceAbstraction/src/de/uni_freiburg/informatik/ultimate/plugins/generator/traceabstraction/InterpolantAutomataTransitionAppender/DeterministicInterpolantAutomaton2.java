package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.InterpolantAutomataTransitionAppender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.INestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedWordAutomaton;
import de.uni_freiburg.informatik.ultimate.core.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.logic.Script.LBool;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.ModifiableGlobalVariableManager;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.TransFormula.Infeasibility;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.TermVarsProc;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Call;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Return;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.DivisibilityPredicateGenerator;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.IHoareTripleChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.IHoareTripleChecker.Validity;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.InterpolatingTraceChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.PredicateUnifier;
import de.uni_freiburg.informatik.ultimate.util.HashRelation;

/**
 * Deterministic interpolant automaton with on-demand construction.
 * @author Matthias Heizmann
 *
 */
public class DeterministicInterpolantAutomaton2 extends AbstractInterpolantAutomaton2 {
	
	private final Map<Set<IPredicate>, IPredicate> m_InputPreds2ResultPreds = 
			new HashMap<Set<IPredicate>, IPredicate>();
	private final HashRelation<IPredicate, IPredicate> m_ResPred2InputPreds = 
			new HashRelation<IPredicate, IPredicate>();


	private final IPredicate m_IaTrueState;
	
	private final Set<IPredicate> m_NonTrivialPredicates;

	private final PredicateUnifier m_PredicateUnifier;
	
	/**
	 * Split up predicates in their conjuncts. 
	 * First experiments on few examples showed that this is decreasing the
	 * performance.
	 */
	private boolean m_Cannibalize = false;
	private boolean m_SplitNumericEqualities = true;
	private boolean m_DivisibilityPredicates = false;
	

	

	public DeterministicInterpolantAutomaton2(IUltimateServiceProvider services, 
			SmtManager smtManager, ModifiableGlobalVariableManager modglobvarman, IHoareTripleChecker hoareTripleChecker,
			INestedWordAutomaton<CodeBlock, IPredicate> abstraction, 
			NestedWordAutomaton<CodeBlock, IPredicate> interpolantAutomaton, 
			InterpolatingTraceChecker traceChecker, Logger  logger) {
		super(services, smtManager, hoareTripleChecker, abstraction, traceChecker.getPostcondition(), interpolantAutomaton, logger);
		m_PredicateUnifier = traceChecker.getPredicateUnifier();
		Collection<IPredicate> allPredicates;
		if (m_Cannibalize ) {
			allPredicates = m_PredicateUnifier.cannibalizeAll(m_SplitNumericEqualities, interpolantAutomaton.getStates().toArray(new IPredicate[0]));
			for (IPredicate pred : allPredicates) {
				if (!interpolantAutomaton.getStates().contains(pred)) {
					interpolantAutomaton.addState(false, false, pred);
				}
			}
		} else {
			allPredicates = interpolantAutomaton.getStates(); 
		}
		if (m_DivisibilityPredicates) {
			allPredicates = new ArrayList<IPredicate>(allPredicates);
			DivisibilityPredicateGenerator dpg = new DivisibilityPredicateGenerator(m_SmtManager, m_PredicateUnifier);
			Collection<IPredicate> divPreds = dpg.divisibilityPredicates(allPredicates);
			allPredicates.addAll(divPreds);
			for (IPredicate pred : divPreds) {
				if (!interpolantAutomaton.getStates().contains(pred)) {
					interpolantAutomaton.addState(false, false, pred);
				}
			}
		}
		
		m_IaTrueState = traceChecker.getPrecondition();
		assert m_IaTrueState.getFormula().toString().equals("true");
		assert allPredicates.contains(m_IaTrueState);
		m_Result.addState(true, false, m_IaTrueState);
		m_ResPred2InputPreds.addPair(m_IaTrueState, m_IaTrueState);
		assert m_IaFalseState.getFormula().toString().equals("false");
		assert allPredicates.contains(m_IaFalseState);
		m_Result.addState(false, true, m_IaFalseState);
		m_ResPred2InputPreds.addPair(m_IaFalseState, m_IaFalseState);

		m_NonTrivialPredicates = new HashSet<IPredicate>();
		for (IPredicate state : allPredicates) {
			if (state != m_IaTrueState && state != m_IaFalseState) {
				m_ResPred2InputPreds.addPair(state, state);
				m_NonTrivialPredicates.add(state);
			}
		}

		mLogger.info(startMessage());
		
	}
	
	@Override
	protected String startMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("Constructing interpolant automaton ");
		sb.append("starting with ");
		sb.append(m_NonTrivialPredicates.size() + 2);
		sb.append(" interpolants.");
		return sb.toString();
	}
	
	@Override
	protected String switchToReadonlyMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("Switched to read-only mode: deterministic interpolant automaton has ");
		sb.append(m_Result.size()).append(" states. ");
		return sb.toString();
	}
	
	@Override
	protected String switchToOnTheFlyConstructionMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append("Switched to On-DemandConstruction mode: deterministic interpolant automaton has ");
		sb.append(m_Result.size()).append(" states. ");
		return sb.toString();
	}


	
	protected void computeSuccs(IPredicate resPred, IPredicate resHier, 
			CodeBlock letter, SuccessorComputationHelper sch) {
		// if (linear) predecessor is false, the successor is false
		if (sch.isLinearPredecessorFalse(resPred)) {
			sch.addTransition(resPred, resHier, letter, m_IaFalseState);
			return;
		}
		// if (hierarchical) predecessor is false, the successor is false
		if (sch.isHierarchicalPredecessorFalse(resHier)) {
			sch.addTransition(resPred, resHier, letter, m_IaFalseState);
			return;
		} 
		// if the letter is already infeasible, the successor is false
		if (letter.getTransitionFormula().isInfeasible() == Infeasibility.INFEASIBLE) {
			sch.addTransition(resPred, resHier, letter, m_IaFalseState);
			return;
		}
		// get all successor whose inductivity we already know from the
		// input interpolant automaton
		final Collection<IPredicate> automatonSuccs = 
				getConjunctSuccsInterpolantAutomaton(resPred, resHier, letter, sch);
		// check if false is implied
		if (automatonSuccs.contains(m_IaFalseState)){
			sch.addTransition(resPred, resHier, letter, m_IaFalseState);
			return;
		} else {
			Validity sat = sch.computeSuccWithSolver(resPred, resHier, letter, m_IaFalseState);
			if (sat == Validity.VALID) {
				sch.addTransition(resPred, resHier, letter, m_IaFalseState);
				return;
			}
		}
		// check all other predicates
		final Set<IPredicate> inputSuccs = new HashSet<IPredicate>();
		for (IPredicate succCand : m_NonTrivialPredicates) {
			if (automatonSuccs.contains(succCand)) {
				inputSuccs.add(succCand);
			} else {
				Validity sat = sch.computeSuccWithSolver(resPred, resHier, letter, succCand);
				if (sat == Validity.VALID) {
					inputSuccs.add(succCand);
				}
			}
		}
		IPredicate resSucc = getOrConstructPredicate(inputSuccs);
		sch.addTransition(resPred, resHier, letter, resSucc);
	}


	/**
	 * Returns all successors of resPred, resHier, and letter in automaton.
	 * If resPred and resHier were constructed as a conjunction of 
	 * inputPredicates, we also take the conjuncts.
	 */
	private Collection<IPredicate> getConjunctSuccsInterpolantAutomaton(
			IPredicate resPred, IPredicate resHier, CodeBlock letter,
			SuccessorComputationHelper sch) {
		final Set<IPredicate> resPredConjuncts = m_ResPred2InputPreds.getImage(resPred);
		assert resPredConjuncts != null;
		final Set<IPredicate> resHierConjuncts;
		if (resHier == null) {
			resHierConjuncts = null;
		} else {
			resHierConjuncts = m_ResPred2InputPreds.getImage(resHier);
		}
		Collection<IPredicate> result;
		if (resPredConjuncts.size() == 1 && 
				(resHier == null || resHierConjuncts.size() == 1)) {
			result = sch.getSuccsInterpolantAutomaton(resPred, resHier, letter);
		} else {
			result = new HashSet<IPredicate>();
			for (IPredicate inputPred : resPredConjuncts) {
				if (resHier == null) {
					result = sch.getSuccsInterpolantAutomaton(inputPred, null, letter);
				} else {
					for (IPredicate inputHier : resHierConjuncts) {
						result = sch.getSuccsInterpolantAutomaton(inputPred, inputHier, letter);
					}
				}
			}
		}
		return result;
	}


	/**
	 * Returns true iff  both of the following are true: 
	 *  - m_InterpolantAutomaton contains resPred
	 *  - resHier is null or m_InterpolantAutomaton contains resHier
	 */
	private boolean interpolantAutomatonContainsStates(IPredicate resPred,
			IPredicate resHier) {
		Collection<IPredicate> states = m_InterpolantAutomaton.getStates();
		if (states.contains(resPred)) {
			if (resHier == null || states.contains(resHier)) {
				return true;
			} else {
				return false;
			}
		} else {
			return false;
		}
	}


	


	


	private IPredicate getOrConstructPredicate(Set<IPredicate> succs) {
		final IPredicate result;
		if (succs.isEmpty()) {
			result = m_IaTrueState;
		} else if (succs.size() == 1) {
			result = succs.iterator().next();
			if (!m_Result.contains(result)) {
				m_Result.addState(false, false, result);
			}
		} else {
			IPredicate resSucc = m_InputPreds2ResultPreds.get(succs);
			if (resSucc == null) {
				TermVarsProc conjunction = m_SmtManager.and(succs.toArray(new IPredicate[0]));
				resSucc = m_PredicateUnifier.getOrConstructPredicate(conjunction);
				m_InputPreds2ResultPreds.put(succs, resSucc);
				for (IPredicate succ : succs) {
					if (m_NonTrivialPredicates.contains(succ)) {
						m_ResPred2InputPreds.addPair(resSucc, succ);
					}
				}
				if (!m_Result.contains(resSucc)) {
					m_Result.addState(false, false, resSucc);
				}
			} 
			result = resSucc;
		}
		return result;
	}
	
	
	
	
	/**
	 * Return true if results are compatible or one is UNKNOWN
	 */
	private boolean satCompatible(Validity sat1, LBool sat2) {
		switch (sat1) {
		case VALID:
			return (sat2 == LBool.UNSAT || sat2 == LBool.UNKNOWN);
		case INVALID:
			return (sat2 == LBool.SAT || sat2 == LBool.UNKNOWN);
		case UNKNOWN:
			return true;
		default:
			throw new UnsupportedOperationException();
		}
	}
	

	@Override
	protected boolean areInternalSuccsComputed(IPredicate state, CodeBlock letter) {
		Collection<IPredicate> succs = m_Result.succInternal(state, letter);
		if (succs == null) {
			return false;
		} else {
			return succs.iterator().hasNext();
		}
	}

	@Override
	protected boolean areCallSuccsComputed(IPredicate state, Call call) {
		Collection<IPredicate> succs = m_Result.succCall(state, call);
		if (succs == null) {
			return false;
		} else {
			return succs.iterator().hasNext();
		}
	}


	@Override
	protected boolean areReturnSuccsComputed(IPredicate state, IPredicate hier,	Return ret) {
		Collection<IPredicate> succs = m_Result.succReturn(state, hier, ret);
		if (succs == null) {
			return false;
		} else {
			return succs.iterator().hasNext();
		}
	}
	
	
}
