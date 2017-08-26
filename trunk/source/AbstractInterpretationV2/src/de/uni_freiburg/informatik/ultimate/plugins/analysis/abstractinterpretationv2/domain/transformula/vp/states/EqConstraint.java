/*
 * Copyright (C) 2017 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
 *
 * This file is part of the ULTIMATE AbstractInterpretationV2 plug-in.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE AbstractInterpretationV2 plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE AbstractInterpretationV2 plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE AbstractInterpretationV2 plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE AbstractInterpretationV2 plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.states;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.ConstantFinder;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.IEqNodeIdentifier;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.elements.IEqFunctionIdentifier;
import de.uni_freiburg.informatik.ultimate.util.datastructures.CongruenceClosure;
import de.uni_freiburg.informatik.ultimate.util.datastructures.EqualityStatus;

/**
 *
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 * @param <ACTION>
 * @param <NODE>
 * @param <FUNCTION>
 */
public class EqConstraint<ACTION extends IIcfgTransition<IcfgLocation>,
		NODE extends IEqNodeIdentifier<NODE, FUNCTION>,
		FUNCTION extends IEqFunctionIdentifier<NODE, FUNCTION>> {

	private final WeqCongruenceClosure<ACTION, NODE, FUNCTION> mPartialArrangement;
//	private final WeakEquivalenceGraph<ACTION, NODE, FUNCTION> mWeakEquivalenceGraph;

	private boolean mIsFrozen;

	final EqConstraintFactory<ACTION, NODE, FUNCTION> mFactory;
	/**
	 * The IProgramVars whose getTermVariable()-value is used in a NODE inside this constraint;
	 * computed lazily by getVariables.
	 */
	private Set<IProgramVar> mVariables;
	/**
	 * Same as mVariables, but with respect to IProgramVarOrConst, and getTerm, instead of IProgramVar and
	 * getTermVariable.
	 */
	private Set<IProgramVarOrConst> mPvocs;
	private Term mTerm;



	/**
	 * Creates an empty constraint (i.e. an EqConstraint that does not constrain anything, whose toTerm() will return
	 * "true").
	 *
	 * @param factory
	 */
	public EqConstraint(final EqConstraintFactory<ACTION, NODE, FUNCTION> factory) {
		mFactory = factory;
		mPartialArrangement = new WeqCongruenceClosure<>(factory);
	}

	public EqConstraint(final WeqCongruenceClosure<ACTION, NODE, FUNCTION> cClosure,
			final EqConstraintFactory<ACTION, NODE, FUNCTION> factory) {
		mFactory = factory;
		mPartialArrangement = new WeqCongruenceClosure<>(cClosure);
	}

	/**
	 * Copy constructor.
	 *
	 * @param constraint
	 */
	public EqConstraint(final EqConstraint<ACTION, NODE, FUNCTION> constraint) {
		mFactory = constraint.mFactory;
		mPartialArrangement = new WeqCongruenceClosure<>(constraint.mPartialArrangement);
	}

	public void freeze() {
		assert !mIsFrozen;
		mIsFrozen = true;
	}

	/**
	 * Whenever an EqConstraint becomes inconsistent, we replace it with an EqBottomConstraint.
	 * Thus this should always return false. (see also checkForContradictionMethod)
	 * @return
	 */
	public boolean isBottom() {
		assert !mPartialArrangement.isInconsistent();
		return false;
	}

	public Set<NODE> getAllNodes() {
		return mPartialArrangement.getAllElements();
	}

	public boolean reportEquality(final NODE node1, final NODE node2) {
		assert !mIsFrozen;

		return mPartialArrangement.reportEquality(node1, node2);
	}



	public boolean reportDisequality(final NODE node1, final NODE node2) {
		assert !mIsFrozen;
		final boolean paHasChanged = mPartialArrangement.reportDisequality(node1, node2);
		return paHasChanged;
	}

	public boolean reportFunctionEquality(final FUNCTION func1, final FUNCTION func2) {
		assert !mIsFrozen;
		final boolean paHasChanged = mPartialArrangement.reportFunctionEquality(func1, func2);
		return paHasChanged;
	}

	public boolean reportFunctionDisequality(final FUNCTION func1, final FUNCTION func2) {
		assert !mIsFrozen;
		final boolean paHasChanged = mPartialArrangement.reportFunctionDisequality(func1, func2);
		return paHasChanged;
	}

	public void reportWeakEquivalence(final FUNCTION array1, final FUNCTION array2,
			final List<NODE> storeIndex) {
		assert !mIsFrozen;
		mPartialArrangement.reportWeakEquivalence(array1, array2, storeIndex);
	}

	public boolean isFrozen() {
		return mIsFrozen;
	}



	private static <E, F extends E> boolean arrayContains(final E[] freeVars, final F var) {
		for (int i = 0; i < freeVars.length; i++) {
			if (freeVars[i].equals(var)) {
				return true;
			}
		}
		return false;
	}

	public void renameVariables(final Map<Term, Term> substitutionMapping) {
		assert !mIsFrozen;
		mPartialArrangement.renameVariables(substitutionMapping);
		resetCachingFields();
	}

	private void resetCachingFields() {
		mVariables = null;
		mPvocs = null;
		mTerm = null;
	}

	/**
	 *
	 * @param node1
	 * @param node2
	 * @return true iff this constraint implies that node1 and node2 are equal
	 */
	public boolean areEqual(final NODE node1, final NODE node2) {
		if (!mPartialArrangement.hasElement(node1)
		 || !mPartialArrangement.hasElement(node2)) {
			return false;
		}
		return mPartialArrangement.getEqualityStatus(node1, node2) == EqualityStatus.EQUAL;
	}

	/**
	 *
	 * @param node1
	 * @param node2
	 * @return true iff this constraint implies that node1 and node2 are unequal
	 */
	public boolean areUnequal(final NODE node1, final NODE node2) {
		if (!mPartialArrangement.hasElement(node1)
		 || !mPartialArrangement.hasElement(node2)) {
			return false;
		}
		return mPartialArrangement.getEqualityStatus(node1, node2) == EqualityStatus.NOT_EQUAL;
	}

	public boolean areEqual(final FUNCTION func1, final FUNCTION func2) {
		if (!mPartialArrangement.hasFunction(func1)
		 || !mPartialArrangement.hasFunction(func2)) {
			return false;
		}
		return mPartialArrangement.getEqualityStatus(func1, func2) == EqualityStatus.EQUAL;
	}

	public boolean areUnequal(final FUNCTION func1, final FUNCTION func2) {
		if (!mPartialArrangement.hasFunction(func1)
		 || !mPartialArrangement.hasFunction(func2)) {
			return false;
		}
		return mPartialArrangement.getEqualityStatus(func1, func2) == EqualityStatus.NOT_EQUAL;
	}

	public Term getTerm(final Script script) {
		assert mIsFrozen : "not yet frozen, term may not be final..";
		if (mTerm != null) {
			return mTerm;
		}

		final Term result = mPartialArrangement.getTerm(script);
		if (mIsFrozen) {
			mTerm = result;
		}
		return result;
	}

	static <NODE extends IEqNodeIdentifier<NODE, FUNCTION>, FUNCTION extends IEqFunctionIdentifier<NODE, FUNCTION>>
		List<Term> partialArrangementToCube(final Script script, final CongruenceClosure<NODE, FUNCTION> pa) {

		final List<Term> elementEqualities = pa.getSupportingElementEqualities().entrySet().stream()
				.map(en -> script.term("=", en.getKey().getTerm(), en.getValue().getTerm()))
				.collect(Collectors.toList());
		final List<Term> elementDisequalities = pa.getElementDisequalities().entrySet().stream()
				.map(pair -> script.term("distinct", pair.getKey().getTerm(), pair.getValue().getTerm()))
				.collect(Collectors.toList());
		final List<Term> functionEqualities = pa.getSupportingFunctionEqualities().entrySet().stream()
				.map(en -> script.term("=", en.getKey().getTerm(), en.getValue().getTerm()))
				.collect(Collectors.toList());
		final List<Term> functionDisequalities = pa.getFunctionDisequalities().entrySet().stream()
				.map(pair -> script.term("distinct", pair.getKey().getTerm(), pair.getValue().getTerm()))
				.collect(Collectors.toList());

		final List<Term> result = new ArrayList<>(elementEqualities.size() + elementDisequalities.size()
			+ functionEqualities.size() + functionDisequalities.size());
		result.addAll(elementEqualities);
		result.addAll(elementDisequalities);
		result.addAll(functionEqualities);
		result.addAll(functionDisequalities);
		return result;
	}

	/**
	 * This only really makes sense when this constraint is in a renaming state
	 * such that the TermVariables are "normalized" to the TermVariables that
	 * are associated to IProgramVars.
	 *
	 * I.e. when it is the constraint of a EqPredicate or an EqState
	 *
	 * @return
	 */
	public Set<IProgramVar> getVariables(final IIcfgSymbolTable symbolTable) {
		if (mVariables != null) {
			return mVariables;
		}
		final Set<TermVariable> allTvs = new HashSet<>();
		mPartialArrangement.getAllElements().stream()
		.forEach(node -> allTvs.addAll(Arrays.asList(node.getTerm().getFreeVars())));

		mPartialArrangement.getAllFunctions().stream()
		.forEach(func -> allTvs.addAll(Arrays.asList(func.getTerm().getFreeVars())));

		/*
		 * note this will probably crash if this method is called on an
		 * EqConstraint that does not belong to a predicate or state
		 */
		mVariables = allTvs.stream().map(symbolTable::getProgramVar).collect(Collectors.toSet());

		assert !mVariables.stream().anyMatch(Objects::isNull);
		return mVariables;
	}

	/**
	 * Collects the Pvocs (IprogramVarOrConsts) that are mentioned in this EqConstraint by looking up the TermVariables
	 * and nullary ApplicationTerms in the symbol table.
	 *
	 * These Pvocs correspond to the Pvocs of the compacted version of an EqState that has this constraint, i.e.,
	 * only Pvocs that are actually constrained by this constraint are mentioned.
	 *
	 * We expect this to only be called when this constraint is the constraint
	 * of an EqState, thus we expect all TermVariables to correspond to an IProgramVar and all nullary ApplicationTerms
	 * to correspond to a constant that is mentioned in the symbol table.
	 *
	 * @param symbolTable
	 *
	 * @return
	 */
	public Set<IProgramVarOrConst> getPvocs(final IIcfgSymbolTable symbolTable) {
		assert mIsFrozen;
		if (mPvocs != null) {
			return mPvocs;
		}
		mPvocs = new HashSet<>();
		mPvocs.addAll(getVariables(symbolTable));

		final Set<ApplicationTerm> constants = new HashSet<>();
		mPartialArrangement.getAllElements().stream()
			.forEach(node -> constants.addAll(new ConstantFinder().findConstants(node.getTerm(), false)));
		// TODO do we need to find literals here, too?? (i.e. ConstantTerms)

		mPartialArrangement.getAllFunctions().stream()
			.forEach(func -> constants.addAll(new ConstantFinder().findConstants(func.getTerm(), false)));

		mPvocs.addAll(constants.stream().map(c -> symbolTable.getProgramConst(c)).collect(Collectors.toSet()));

		assert !mPvocs.stream().anyMatch(Objects::isNull);
		return mPvocs;
	}

	@Override
	public String toString() {
		return mPartialArrangement.toString();
	}

	public boolean hasNode(final NODE node) {
		return mPartialArrangement.getAllElements().contains(node);
	}

	public Set<FUNCTION> getAllFunctions() {
		return mPartialArrangement.getAllFunctions();
	}

	public boolean isTop() {
		return mPartialArrangement.isTautological();
	}

	public EqConstraint<ACTION, NODE, FUNCTION> join(final EqConstraint<ACTION, NODE, FUNCTION> other) {
		final WeqCongruenceClosure<ACTION, NODE, FUNCTION> newPartialArrangement = this.mPartialArrangement.join(
				other.mPartialArrangement);
		final EqConstraint<ACTION, NODE, FUNCTION> res = mFactory.getEqConstraint(newPartialArrangement);
		res.freeze();
		return res;
	}


	public EqConstraint<ACTION, NODE, FUNCTION> meet(final EqConstraint<ACTION, NODE, FUNCTION> other) {


		final WeqCongruenceClosure<ACTION, NODE, FUNCTION> newPa = mPartialArrangement.meet(other.mPartialArrangement);

		final EqConstraint<ACTION, NODE, FUNCTION> res = mFactory.getEqConstraint(newPa);
		res.freeze();
		return res;

//		final WeakEquivalenceGraph<ACTION, NODE, FUNCTION> currentWeqGraph = other.mWeakEquivalenceGraph;
//
//		final WeqCongruenceClosure<ACTION, NODE, FUNCTION> meetPartialArrangement =
//				this.mPartialArrangement.meet(other.mPartialArrangement);
//		if (meetPartialArrangement.isInconsistent()) {
//			return mFactory.getBottomConstraint();
//		}
//
//		final WeakEquivalenceGraph<ACTION, NODE, FUNCTION> weqMeet =
//					mWeakEquivalenceGraph.meet(currentWeqGraph, meetPartialArrangement);
//		assert !weqMeet.hasArrayEqualities();
//
//		final EqConstraint<ACTION, NODE, FUNCTION> res = mFactory.getEqConstraint(meetPartialArrangement, weqMeet);
//		res.freeze();
//		return res;

		/////////////////

//		while (weqMeet.hasArrayEqualities()) {
//			Entry<FUNCTION, FUNCTION> aeq = weqMeet.pollArrayEquality();
//			meetPartialArrangement.reportFunctionEquality(f1, f2)
//		}
//		while (true) {
//
//			if (!weqMeet.hasArrayEqualities()) {
//				// no weak equivalence edges' label became inconsistent -- report result
//				final EqConstraint<ACTION, NODE, FUNCTION> res =
//						mFactory.getEqConstraint(meetPartialArrangement, weqMeet);
//				res.freeze();
//				return res;
//			}
//
//			for (final Entry<FUNCTION, FUNCTION> aeq : weqMeet.getArrayEqualities().entrySet()) {
//				meetPartialArrangement.reportFunctionEquality(aeq.getKey(), aeq.getValue());
//			}
//			mWeakEquivalenceGraph.applyChangesInGroundPartialArrangement();
//		}
	}


	/**
	 *
	 * @param other
	 * @return true iff this is more or equally constraining than other
	 */
	public boolean isStrongerThan(final EqConstraint<ACTION, NODE, FUNCTION> other) {
		return mPartialArrangement.isStrongerThan(other.mPartialArrangement);
	}

	public void addNode(final NODE nodeToAdd) {
		assert !mIsFrozen;
		mPartialArrangement.getRepresentativeAndAddElementIfNeeded(nodeToAdd);
	}

	public void addFunction(final FUNCTION func) {
		assert !mIsFrozen;
		mPartialArrangement.getRepresentativeAndAddFunctionIfNeeded(func);
	}

	public void removeFunction(final FUNCTION functionToHavoc) {
		mPartialArrangement.removeFunction(functionToHavoc);
	}

	public void removeElement(final NODE elemToHavoc) {
		mPartialArrangement.removeElement(elemToHavoc);
	}




//	void saturate() {
//
//		final boolean changedWeqGraph = mWeakEquivalenceGraph.applyChangesInGroundPartialArrangement();
//		if (!changedWeqGraph) {
//			// nothing to do
//			return;
//		}
//
//		while (true) {
//			if (!mWeakEquivalenceGraph.hasArrayEqualities()) {
//				// no weak equivalence edges' label became inconsistent (anymor), we are done
//				// the result is true because the partial arrangement has changed
//				assert sanityCheck();
//				return;
//			}
//
//
//			for (final Entry<FUNCTION, FUNCTION> aeq : mWeakEquivalenceGraph.getArrayEqualities().entrySet()) {
//				mPartialArrangement.reportFunctionEquality(aeq.getKey(), aeq.getValue());
//			}
//			mWeakEquivalenceGraph.applyChangesInGroundPartialArrangement();
//		}
//	}

//	private boolean sanityCheck() {
//		if (!mWeakEquivalenceGraph.sanityCheck()) {
//			return false;
//		}
//		return true;
//	}

}

