/*
 * Copyright (C) 2020 Marcel Ebbinghaus
 * Copyright (C) 2020 Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 * Copyright (C) 2020 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.automata.partialorder;

/**
 * Interface for the Visitor Class for Partial Order Reductions
 * 
 * @author Marcel Ebbinghaus
 *
 * @param <L>
 * 		letter
 * @param <S>
 * 		state
 */
public interface IPartialOrderVisitor<L, S> {
	/**
	 * Method to discover a given transition.
	 * 
	 * @param source
	 * 		source state of the given transition
	 * @param letter
	 * 		letter of the given transition
	 * @param target
	 * 		target of the given transition
	 * @return
	 * 		return value can be used to detect and react to certain circumstances
	 * 		(for instance by returning true if the target should not be visited)
	 */
	boolean discoverTransition(S source, L letter, S target);

	/**
	 * Method to backtrack a given state.
	 * 
	 * @param state
	 * 		state to backtrack
	 */
	void backtrackState(S state);

	// TODO (Dominik 2021-01-24) Medium-term we should try to get rid of this method, as "delaying" states is an
	// implementation detail of SleepSetDelayReduction that should not exposed to visitors.
	/**
	 * Method to delay a given state.
	 * 
	 * @param state
	 * 		state to delay
	 */
	void delayState(S state);

	/**
	 * Method to add a given state as a start state.
	 * 
	 * @param state
	 * 		state to add as a start state
	 * @return
	 * 		return value can be used to detect and react to certain circumstances
	 * 		(for instance by returning true if the visitor was searching for the given state)
	 */
	boolean addStartState(S state);

	/**
	 * Method to discover a given state.
	 * 
	 * @param state
	 * 		state to discover
	 * @return
	 * 		return value can be used to detect and react to certain circumstances
	 * 		(for instance by returning true if the visitor was searching for the given state)
	 */
	boolean discoverState(S state);
}
