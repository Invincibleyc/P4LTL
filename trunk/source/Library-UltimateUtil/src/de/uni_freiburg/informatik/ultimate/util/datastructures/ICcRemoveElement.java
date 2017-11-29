package de.uni_freiburg.informatik.ultimate.util.datastructures;

import java.util.Map;
import java.util.Set;

public interface ICcRemoveElement<ELEM extends ICongruenceClosureElement<ELEM>> {

	boolean isInconsistent();

	boolean hasElement(ELEM elem);

	Set<ELEM> collectElementsToRemove(ELEM elem);

	ELEM getOtherEquivalenceClassMember(ELEM elemToRemove, Set<ELEM> elementsToRemove);

	boolean isConstrained(ELEM elemToRemove);

	Set<ELEM> getNodesToIntroduceBeforeRemoval(ELEM elemToRemove, Map<ELEM, ELEM> nodeToReplacementNode);

	boolean addElementRec(ELEM proxyElem);

	boolean sanityCheck();

	void applyClosureOperations();

	void prepareForRemove(boolean useWeqGpa);

	Set<ELEM> removeElementAndDependents(ELEM elem, Set<ELEM> elementsToRemove, Map<ELEM, ELEM> nodeToReplacementNode,
			boolean useWeqGpa);

}
