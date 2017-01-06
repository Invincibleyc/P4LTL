/*
 * Copyright (C) 2017 Christian Schilling (schillic@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 * 
 * This file is part of the ULTIMATE Automata Library.
 * 
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 * 
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.IDoubleDeckerAutomaton;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.maxsat.collections.TransitivityGeneralMaxSatSolver;
import de.uni_freiburg.informatik.ultimate.automata.statefactory.IStateFactory;
import de.uni_freiburg.informatik.ultimate.util.datastructures.UnionFind;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;

/**
 * Partial Max-SAT based minimization of NWA using (asymmetric) {@link Pair}s of states as variable type.<br>
 * In contrast to {@link MinimizeNwaPmaxSat}, this class works for simulation rather than bisimulation. Accrodingly, a
 * pair (q0, q1) means that the state q1 simulates the state q0.
 * 
 * @author Christian Schilling (schillic@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            letter type
 * @param <STATE>
 *            state type
 * @see MinimizeNwaMaxSat2
 */
public class MinimizeNwaPmaxSatAsymmetric<LETTER, STATE> extends MinimizeNwaMaxSat2<LETTER, STATE, Pair<STATE, STATE>> {
	@SuppressWarnings("rawtypes")
	private static final Pair[] EMPTY_LITERALS = new Pair[0];
	private STATE mEmptyStackState;
	
	/**
	 * Constructor that should be called by the automata script interpreter.
	 * 
	 * @param services
	 *            Ultimate services
	 * @param stateFactory
	 *            state factory
	 * @param operand
	 *            operand
	 * @throws AutomataOperationCanceledException
	 *             thrown by cancel request
	 */
	public MinimizeNwaPmaxSatAsymmetric(final AutomataLibraryServices services, final IStateFactory<STATE> stateFactory,
			final IDoubleDeckerAutomaton<LETTER, STATE> operand) throws AutomataOperationCanceledException {
		this(services, stateFactory, operand, createPairs(operand.getStates()), new Settings<>());
	}
	
	/**
	 * Constructor with initial pairs.
	 * 
	 * @param services
	 *            Ultimate services
	 * @param stateFactory
	 *            state factory
	 * @param operand
	 *            input nested word automaton
	 * @param initialPairs
	 *            allowed pairs of states
	 * @param settings
	 *            settings wrapper
	 * @throws AutomataOperationCanceledException
	 *             thrown by cancel request
	 */
	public MinimizeNwaPmaxSatAsymmetric(final AutomataLibraryServices services, final IStateFactory<STATE> stateFactory,
			final IDoubleDeckerAutomaton<LETTER, STATE> operand, final Iterable<Pair<STATE, STATE>> initialPairs,
			final Settings<STATE> settings) throws AutomataOperationCanceledException {
		super(services, stateFactory, "MinimizeNwaPmaxSatAsymmetric", operand, settings.setSolverModeGeneral());
		mEmptyStackState = mOperand.getEmptyStackState();
		
		// FIXME support transitivity
		if (mSolver instanceof TransitivityGeneralMaxSatSolver) {
			throw new IllegalArgumentException("Transitivity is currently not supported for asymmetric variables.");
		}
		
		fillMapWithInitialPairs(initialPairs);
		
		run();
	}
	
	private void fillMapWithInitialPairs(final Iterable<Pair<STATE, STATE>> initialPairs) {
		for (final Pair<STATE, STATE> pair : initialPairs) {
			mStatePairs.put(pair.getFirst(), pair.getSecond(), pair);
		}
	}
	
	@Override
	protected void generateVariablesAndAcceptingConstraints() throws AutomataOperationCanceledException {
		final boolean separateFinalAndNonfinalStates = mSettings.getFinalStateConstraints();
		
		for (final Triple<STATE, STATE, Pair<STATE, STATE>> triple : mStatePairs.entrySet()) {
			final Pair<STATE, STATE> pair = triple.getThird();
			final STATE state1 = triple.getFirst();
			final STATE state2 = triple.getSecond();
			
			addStateToTransitivityGeneratorIfNotPresent(state1);
			addStateToTransitivityGeneratorIfNotPresent(state2);
			
			mSolver.addVariable(pair);
			
			// separate final first and nonfinal second state
			if (separateFinalAndNonfinalStates && mOperand.isFinal(state1) && !mOperand.isFinal(state2)) {
				setStatesDifferent(pair);
			}
		}
	}
	
	private void addStateToTransitivityGeneratorIfNotPresent(final STATE state) {
		if (mTransitivityGenerator != null) {
			mTransitivityGenerator.addContentIfNotPresent(state);
		}
	}
	
	@Override
	protected void generateTransitionAndTransitivityConstraints(final boolean addTransitivityConstraints)
			throws AutomataOperationCanceledException {
		for (final Triple<STATE, STATE, Pair<STATE, STATE>> triple : mStatePairs.entrySet()) {
			final Pair<STATE, STATE> pair = triple.getThird();
			
			// add transition constraints
			generateTransitionConstraintsHelper(triple.getFirst(), triple.getSecond(), pair);
			
			// add transitivity constraints
			if (addTransitivityConstraints) {
				generateTransitivityConstraints(pair);
			}
			
			checkTimeout(ADDING_TRANSITIVITY_CONSTRAINTS);
		}
		
		for (final STATE state : mOperand.getStates()) {
			generateTransitionConstraintsHelperReturn2(state, getDownStatesArray(state));
		}
	}
	
	private void generateTransitivityConstraints(final Pair<STATE, STATE> pair12)
			throws AutomataOperationCanceledException {
		for (final Entry<STATE, Pair<STATE, STATE>> state3toPair : mStatePairs.get(pair12.getSecond()).entrySet()) {
			final STATE state3 = state3toPair.getKey();
			final Pair<STATE, STATE> pair23 = state3toPair.getValue();
			final Pair<STATE, STATE> pair13 = mStatePairs.get(pair12.getFirst(), state3);
			if (pair13 == null) {
				continue;
			}
			
			addTransitivityClausesToSolver(pair12, pair23, pair13);
		}
	}
	
	@Override
	protected UnionFind<STATE> constructResultEquivalenceClasses() throws AssertionError {
		final Map<STATE, Set<STATE>> positivePairs = new HashMap<>();
		final UnionFind<STATE> resultingEquivalenceClasses = new UnionFind<>();
		for (final Entry<Pair<STATE, STATE>, Boolean> entry : mSolver.getValues().entrySet()) {
			if (entry.getValue() == null) {
				throw new AssertionError("value not determined " + entry.getKey());
			}
			if (!entry.getValue()) {
				continue;
			}
			
			final STATE state1 = entry.getKey().getFirst();
			final STATE state2 = entry.getKey().getSecond();
			final Set<STATE> setOfRhs2to1 = positivePairs.get(state2);
			if (setOfRhs2to1 != null && setOfRhs2to1.remove(state1)) {
				// symmetric positive pairs, merge states
				final STATE rep1 = resultingEquivalenceClasses.findAndConstructEquivalenceClassIfNeeded(state1);
				final STATE rep2 = resultingEquivalenceClasses.findAndConstructEquivalenceClassIfNeeded(state2);
				resultingEquivalenceClasses.union(rep1, rep2);
			} else if (isInitialPair(state2, state1)) {
				// other (symmetric) pair not seen yet (but will potentially come)
				Set<STATE> setOfRhs1to2 = positivePairs.get(state1);
				if (setOfRhs1to2 == null) {
					setOfRhs1to2 = new HashSet<>();
					positivePairs.put(state1, setOfRhs1to2);
				}
				setOfRhs1to2.add(state2);
			}
		}
		return resultingEquivalenceClasses;
	}
	
	@Override
	protected String createTaskDescription() {
		return "minimizing NWA with " + mOperand.size() + " states";
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected Pair<STATE, STATE>[] getEmptyVariableArray() {
		return EMPTY_LITERALS;
	}
	
	private static <STATE> Iterable<Pair<STATE, STATE>> createPairs(final Set<STATE> states) {
		final List<Pair<STATE, STATE>> result = new ArrayList<>(states.size() * states.size());
		for (final STATE state1 : states) {
			for (final STATE state2 : states) {
				result.add(new Pair<>(state1, state2));
			}
		}
		return result;
	}
	
	@Override
	protected boolean isInitialPair(final STATE state1, final STATE state2) {
		if (state1.equals(mEmptyStackState) || state2.equals(mEmptyStackState)) {
			return false;
		}
		return mStatePairs.get(state1).containsKey(state2);
	}
	
	@Override
	protected boolean isInitialPair(final Pair<STATE, STATE> pair) {
		return isInitialPair(pair.getFirst(), pair.getSecond());
	}
}
