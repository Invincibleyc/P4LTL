/**
 * 
 */
package de.uni_freiburg.informatik.ultimate.blockencoding.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.blockencoding.model.interfaces.ICompositeEdge;
import de.uni_freiburg.informatik.ultimate.blockencoding.model.interfaces.IMinimizedEdge;
import de.uni_freiburg.informatik.ultimate.blockencoding.rating.util.EncodingStatistics;
import de.uni_freiburg.informatik.ultimate.model.IPayload;
import de.uni_freiburg.informatik.ultimate.model.Payload;
import de.uni_freiburg.informatik.ultimate.model.boogie.BoogieVar;
import de.uni_freiburg.informatik.ultimate.model.structure.IWalkable;

/**
 * Basic abstract class for composite edges (Conjunction or Disjunction),
 * because their behavior is here exactly the same.
 * 
 * @author Stefan Wissert
 * 
 */
public abstract class AbstractCompositeEdge implements ICompositeEdge {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * This edge (exactly the formula of this edge) is the left side of the
	 * operation
	 */
	protected IMinimizedEdge leftEdge;

	/**
	 * This edge (exactly the formula of this edge) is the right side of the
	 * operation
	 */
	protected IMinimizedEdge rightEdge;

	protected int containedDisjunctions;

	protected Payload payload;

	private int counter;

	private HashSet<IMinimizedEdge> containedEdges;

	private HashSet<BoogieVar> usedVariables;

	/**
	 * Constructs a conjunction which is build up on left and right. <br>
	 * Conjunction = left /\ right
	 * 
	 * @param left
	 *            the left side of the conjunction
	 * @param right
	 *            the right side of the conjunction
	 */
	protected AbstractCompositeEdge(IMinimizedEdge left, IMinimizedEdge right) {
		this.containedEdges = initContainedEdges(left, right);
		this.leftEdge = left;
		counter = counter + left.getElementCount();
		this.rightEdge = right;
		counter = counter + right.getElementCount();
		this.payload = new Payload();
		// update the contained disjunctions
		if (left instanceof AbstractCompositeEdge) {
			containedDisjunctions = ((AbstractCompositeEdge) left)
					.getContainedDisjunctions();
		}
		if (right instanceof AbstractCompositeEdge) {
			containedDisjunctions += ((AbstractCompositeEdge) right)
					.getContainedDisjunctions();
		}
		this.usedVariables = new HashSet<BoogieVar>();
		this.usedVariables.addAll(left.getDifferentVariables());
		this.usedVariables.addAll(right.getDifferentVariables());
		EncodingStatistics.setMaxMinDiffVariablesInOneEdge(this.usedVariables
				.size());
	}

	/**
	 * This empty constructor is needed to create some edges
	 */
	protected AbstractCompositeEdge() {

	}

	/**
	 * @param left
	 * @param right
	 * @return
	 */
	private HashSet<IMinimizedEdge> initContainedEdges(IMinimizedEdge left,
			IMinimizedEdge right) {
		HashSet<IMinimizedEdge> conEdges = new HashSet<IMinimizedEdge>();
		if (left.isBasicEdge()) {
			conEdges.add(left);
		} else {
			conEdges.addAll(((AbstractCompositeEdge) left)
					.getContainedEdgesSet());
		}
		if (right.isBasicEdge()) {
			conEdges.add(right);
		} else {
			conEdges.addAll(((AbstractCompositeEdge) right)
					.getContainedEdgesSet());
		}
		return conEdges;
	}

	/**
	 * @return
	 */
	public Set<IMinimizedEdge> getContainedEdgesSet() {
		return this.containedEdges;
	}

	/**
	 * Old and recursive variant of duplicationOfFormula
	 * 
	 * @param edgeToCheck
	 * @return
	 */
	@SuppressWarnings("unused")
	private boolean duplicationOfFormulaRecursive(IMinimizedEdge edgeToCheck) {
		if (edgeToCheck.isBasicEdge()) {
			return containedEdges.contains(edgeToCheck);
		} else {
			boolean flag = containedEdges.contains(edgeToCheck);
			if (flag) {
				return true;
			}
		}
		IMinimizedEdge[] compositeEdges = ((ICompositeEdge) edgeToCheck)
				.getCompositeEdges();
		boolean left = duplicationOfFormula(compositeEdges[0]);
		boolean right = duplicationOfFormula(compositeEdges[1]);
		return left || right;
	}

	/**
	 * This method checks whether we duplicate parts of our formula. In general
	 * there should be no duplication, but there exist examples,
	 * BigBranchingExample, where this can happen. If we have a duplication, we
	 * not allow the algorithm to merge them!
	 * 
	 * @param edgeToCheck
	 * @return true if there is a duplication, false if there is no duplication
	 */
	@Override
	public boolean duplicationOfFormula(IMinimizedEdge edgeToCheck) {
		// Instead of using the recursive algorithm we use here Set-Intersection
		// it seems to be a little bit faster, and way more easier to debug.
		if (!edgeToCheck.isBasicEdge()) {
			HashSet<IMinimizedEdge> interSection = new HashSet<IMinimizedEdge>(
					containedEdges);
			interSection.retainAll(((AbstractCompositeEdge) edgeToCheck)
					.getContainedEdgesSet());

			return !interSection.isEmpty();
		} else {
			return containedEdges.contains(edgeToCheck);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.interfaces.model.
	 * IMinimizedEdge#getSource()
	 */
	@Override
	public MinimizedNode getSource() {
		// the source is the source of the leftSide
		return leftEdge.getSource();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.interfaces.model.
	 * IMinimizedEdge#getTarget()
	 */
	@Override
	public MinimizedNode getTarget() {
		// the target is the target of the rightSide
		return rightEdge.getTarget();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.interfaces.model.
	 * IMinimizedEdge#isBasicEdge()
	 */
	@Override
	public boolean isBasicEdge() {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.interfaces.model.
	 * ICompositeEdge#getCompositeEdges()
	 */
	@Override
	public IMinimizedEdge[] getCompositeEdges() {
		return new IMinimizedEdge[] { leftEdge, rightEdge };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		// StringBuilder sb = new StringBuilder();
		// sb.append(leftEdge.toString());
		// sb.append(" /\\ ");
		// sb.append(rightEdge.toString());
		// return sb.toString();
		return "CompositeEdge";
	}

	@Override
	public void setTarget(MinimizedNode target) {
		this.rightEdge.setTarget(target);
	}

	@Override
	public void setSource(MinimizedNode source) {
		this.leftEdge.setSource(source);
	}

	@Override
	public IPayload getPayload() {
		return payload;
	}

	@Override
	public boolean hasPayload() {
		return true;
	}

	@Override
	public void redirectTarget(MinimizedNode target) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void redirectSource(MinimizedNode source) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void disconnectTarget() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void disconnectSource() {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public List<IWalkable> getSuccessors() {
		return (List<IWalkable>) (List) Arrays.asList(new IMinimizedEdge[] {
				leftEdge, rightEdge });
	}

	@Override
	public int getElementCount() {
		return counter;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.model.interfaces.
	 * IMinimizedEdge#isOldVarInvolved()
	 */
	@Override
	public boolean isOldVarInvolved() {
		return leftEdge.isOldVarInvolved() || rightEdge.isOldVarInvolved();
	}

	/**
	 * @return the containedDisjunctions
	 */
	public int getContainedDisjunctions() {
		return containedDisjunctions;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see de.uni_freiburg.informatik.ultimate.blockencoding.model.interfaces.
	 * IMinimizedEdge#getDifferentVariables()
	 */
	@Override
	public Set<BoogieVar> getDifferentVariables() {
		return this.usedVariables;
	}

}
