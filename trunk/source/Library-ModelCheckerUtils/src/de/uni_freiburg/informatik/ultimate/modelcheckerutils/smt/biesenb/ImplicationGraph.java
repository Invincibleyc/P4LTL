/*
 * Copyright (C) 2018 Ben Biesenbach (ben.biesenbach@neptun.uni-freiburg.de)
 * Copyright (C) 2018 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtilsTest Library.
 *
 * The ULTIMATE ModelCheckerUtilsTest Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtilsTest Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtilsTest Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtilsTest Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtilsTest Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.biesenb;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.util.datastructures.HashDeque;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 * Data structure that stores predicates and there implication-relation. A predicate implies its descendants and is
 * implied by its ancestors.
 *
 * @author Ben Biesenbach (ben.biesenbach@neptun.uni-freiburg.de)
 */
public class ImplicationGraph<T extends IPredicate> {
	private final ManagedScript mMgdScript;
	private final Set<ImplicationVertex<T>> mVertices;
	private final Map<T, ImplicationVertex<T>> mPredicateMap;
	private ImplicationVertex<T> mTrueVertex;
	private ImplicationVertex<T> mFalseVertex;

	protected ImplicationGraph(final ManagedScript script, final T predicateFalse, final T predicateTrue) {
		mMgdScript = script;
		mVertices = new HashSet<>();
		mPredicateMap = new HashMap<>();
		mFalseVertex = new ImplicationVertex<>(predicateFalse, new HashSet<>(), new HashSet<>());
		mTrueVertex = new ImplicationVertex<>(predicateTrue, new HashSet<>(), new HashSet<>());
		mFalseVertex.addChild(mTrueVertex);
		mTrueVertex.addParent(mFalseVertex);

		mVertices.add(mTrueVertex);
		mVertices.add(mFalseVertex);
		mPredicateMap.put(predicateTrue, mTrueVertex);
		mPredicateMap.put(predicateFalse, mFalseVertex);
	}

	protected ImplicationVertex<T> getTrueVertex() {
		return mTrueVertex;
	}

	protected ImplicationVertex<T> getFalseVertex() {
		return mFalseVertex;
	}

	protected ImplicationVertex<T> getVertex(final T pred) {
		final ImplicationVertex<T> rtr = mPredicateMap.get(pred);
		if (rtr == null) {
			throw new IllegalArgumentException("predicate " + pred + " is unknown");
		}
		return rtr;
	}

	protected Set<ImplicationVertex<T>> getVertices() {
		return mVertices;
	}

	protected boolean removeVertex(final ImplicationVertex<T> vertex) {
		if (mVertices.remove(vertex)) {
			final Set<ImplicationVertex<T>> parents = vertex.getParents();
			final Set<ImplicationVertex<T>> children = vertex.getChildren();
			for (final ImplicationVertex<T> p : parents) {
				p.removeChild(vertex);
				for (final ImplicationVertex<T> c : children) {
					c.removeParent(vertex);
					if (!p.getDescendants().contains(c)) {
						c.addParent(p);
						p.addChild(c);
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public String toString() {
		final StringBuilder bld = new StringBuilder();
		for (final ImplicationVertex<T> vertex : mVertices) {
			bld.append("\n " + vertex.toString());
		}
		return bld.toString();
	}

	/**
	 * Insert a predicate into the implication graph
	 *
	 * @param predicate
	 * @return the implication-vertex it is stored in
	 */
	protected ImplicationVertex<T> unifyPredicate(final T predicate) {
		int max;
		boolean implying = true;
		final Pair<ImplicationGraph<T>, Map<ImplicationVertex<T>, ImplicationVertex<T>>> copy = createFullCopy();
		final Set<ImplicationVertex<T>> marked = new HashSet<>();
		ImplicationVertex<T> maxVertex = null;
		// find the predicates that imply the given predicate
		while (!marked.containsAll(copy.getFirst().mVertices)) {
			max = 0;
			maxVertex = null;
			for (final ImplicationVertex<T> vertex : copy.getFirst().mVertices) {
				int count;
				if (marked.contains(vertex) || (count = vertex.getImplicationCount(implying)) <= max) {
					continue;
				}
				max = count;
				maxVertex = vertex;
			}
			if (internImplication(maxVertex.getPredicate(), predicate, true)) {
				marked.add(maxVertex);
				copy.getFirst().removeAllVerticesImplying(maxVertex);
				continue;
			}
			copy.getFirst().internRemoveAllImpliedVertices(maxVertex, false);
			for (final ImplicationVertex<T> v2 : maxVertex.getParents()) {
				v2.removeChild(maxVertex);
			}
			copy.getFirst().mVertices.remove(maxVertex);
		}
		final Set<ImplicationVertex<T>> parents = new HashSet<>();
		copy.getFirst().mVertices.forEach(v -> parents.add(copy.getSecond().get(v)));
		implying = false;
		final Pair<ImplicationGraph<T>, Map<ImplicationVertex<T>, ImplicationVertex<T>>> subCopy =
				createSubCopy(parents, false);
		marked.clear();
		// find the predicates that are implied by the given predicate
		while (!marked.containsAll(subCopy.getFirst().mVertices)) {
			max = 0;
			maxVertex = null;
			for (final ImplicationVertex<T> vertex : subCopy.getFirst().mVertices) {
				int count;
				if (marked.contains(vertex) || (count = vertex.getImplicationCount(implying)) <= max) {
					continue;
				}
				max = count;
				maxVertex = vertex;
			}
			if (internImplication(predicate, maxVertex.getPredicate(), true)) {
				marked.add(maxVertex);
				subCopy.getFirst().internRemoveAllImpliedVertices(maxVertex, false);
				continue;
			}
			subCopy.getFirst().removeAllVerticesImplying(maxVertex);
			for (final ImplicationVertex<T> v3 : maxVertex.getParents()) {
				v3.removeChild(maxVertex);
			}
			subCopy.getFirst().mVertices.remove(maxVertex);
		}
		final HashSet<ImplicationVertex<T>> children = new HashSet<>();
		subCopy.getFirst().mVertices.forEach(v -> children.add(subCopy.getSecond().get(v)));
		final ImplicationVertex<T> newVertex = new ImplicationVertex<>(predicate, children, parents);
		newVertex.transitiveReductionAfterAdding();
		mVertices.add(newVertex);
		mPredicateMap.put(predicate, newVertex);
		completeGraph();
		return newVertex;
	}

	/**
	 * removes all implied predicates from the implication graph
	 *
	 * @return false if the predicate is not in the implication graph, else true
	 */
	protected boolean removeAllImpliedVertices(final ImplicationVertex<T> vertex) {
		return internRemoveAllImpliedVertices(vertex, true);
	}

	private boolean internRemoveAllImpliedVertices(final ImplicationVertex<T> vertex, final boolean keepTrueVertex) {
		if (!mVertices.contains(vertex)) {
			return false;
		}
		final Deque<ImplicationVertex<T>> children = new ArrayDeque<>(vertex.getChildren());
		while (!children.isEmpty()) {
			final ImplicationVertex<T> current = children.pop();
			if (!mVertices.remove(current)) {
				continue;
			}
			final Set<ImplicationVertex<T>> pCopy = new HashSet<>(current.getParents());
			for (final ImplicationVertex<T> p : pCopy) {
				p.removeChild(current);
				current.removeParent(p);
			}
			children.addAll(current.getChildren());
		}
		if (keepTrueVertex) {
			for (final ImplicationVertex<T> v : mVertices) {
				if (v.getChildren().isEmpty()) {
					v.addChild(mTrueVertex);
					mTrueVertex.addParent(v);
				}
			}
			mVertices.add(mTrueVertex);
		}
		completeGraph();
		return true;
	}

	/**
	 * removes all predicates implying the vertex.mPredicate from the implication graph
	 *
	 * @return false if the predicate is not in the implication graph, else true
	 */
	protected boolean removeAllVerticesImplying(final ImplicationVertex<T> vertex) {
		if (!mVertices.contains(vertex)) {
			return false;
		}
		final Deque<ImplicationVertex<T>> parents = new ArrayDeque<>(vertex.getParents());
		while (!parents.isEmpty()) {
			final ImplicationVertex<T> current = parents.pop();
			if (!mVertices.remove(current)) {
				continue;
			}
			current.getChildren().forEach(v -> v.removeParent(current));
			parents.addAll(current.getParents());
		}
		return true;
	}

	/**
	 * removes all predicates form the collection, that are implied within the collection
	 */
	protected Collection<T> removeImpliedVerticesFromCollection(final Collection<T> collection) {
		final Collection<T> result = new HashSet<>();
		final ImplicationGraph<T> copyGraph = createFullCopy().getFirst();
		final Deque<ImplicationVertex<T>> parentless = new HashDeque<>();
		parentless.add(copyGraph.getFalseVertex());

		// remove predicates that are implied by other predicates of the collection
		while (!parentless.isEmpty()) {
			final ImplicationVertex<T> next = parentless.pop();
			if (collection.contains(next.getPredicate())) {
				result.add(next.getPredicate());
				copyGraph.internRemoveAllImpliedVertices(next, false);
			} else {
				for (final ImplicationVertex<T> child : next.getChildren()) {
					child.removeParent(next);
					if (child.getParents().isEmpty()) {
						parentless.add(child);
					}
				}
			}
		}
		return result;
	}

	/**
	 * checks for implication - if the predicates are known, the graph is used
	 *
	 * @return true if a implies b
	 */
	protected boolean implication(final T a, final T b) {
		return internImplication(a, b, false);
	}

	protected boolean internImplication(final T a, final T b, final boolean useSolver) {
		if (a.equals(b)) {
			return true;
		}
		if (mPredicateMap.containsKey(a) && mPredicateMap.containsKey(b)) {
			return getVertex(a).getDescendants().contains(getVertex(b));
		}
		if (useSolver) {
			final Term acf = a.getClosedFormula();
			final Term bcf = b.getClosedFormula();
			if (mMgdScript.isLocked()) {
				mMgdScript.requestLockRelease();
			}
			mMgdScript.lock(this);
			final Term imp = mMgdScript.term(this, "and", acf, mMgdScript.term(this, "not", bcf));
			mMgdScript.push(this, 1);
			try {
				mMgdScript.assertTerm(this, imp);
				final Script.LBool result = mMgdScript.checkSat(this);
				if (result == Script.LBool.UNSAT) {
					return true;
				}
				if (result == Script.LBool.SAT) {
					return false;
				}
				throw new UnsupportedOperationException(
						"Cannot handle case were solver cannot decide implication of predicates");
			} finally {
				mMgdScript.pop(this, 1);
				mMgdScript.unlock(this);
			}
		}
		throw new IllegalArgumentException("predicate is not known, use the solver-option");
	}

	/**
	 * creates a copy of the implication graph
	 */
	protected Pair<ImplicationGraph<T>, Map<ImplicationVertex<T>, ImplicationVertex<T>>> createFullCopy() {
		// create new ImplicationGraph and empty it completely
		final ImplicationGraph<T> copy =
				new ImplicationGraph<>(mMgdScript, mFalseVertex.getPredicate(), mTrueVertex.getPredicate());
		copy.mVertices.clear();
		final Map<ImplicationVertex<T>, ImplicationVertex<T>> vertexCopyMap = new HashMap<>();
		// copy vertices without implications
		for (final ImplicationVertex<T> vertex : mVertices) {
			vertexCopyMap.put(vertex, new ImplicationVertex<>(vertex.getPredicate(), new HashSet<>(), new HashSet<>()));
		}
		// add implications
		for (final ImplicationVertex<T> vertex : mVertices) {
			final ImplicationVertex<T> vertexCopy = vertexCopyMap.get(vertex);
			for (final ImplicationVertex<T> child : vertex.getChildren()) {
				vertexCopy.addChild(vertexCopyMap.get(child));
			}
			for (final ImplicationVertex<T> parent : vertex.getParents()) {
				vertexCopy.addParent(vertexCopyMap.get(parent));
			}
			copy.mVertices.add(vertexCopy);
		}
		// replace vertex for true and false
		copy.mFalseVertex = vertexCopyMap.get(mFalseVertex);
		copy.mTrueVertex = vertexCopyMap.get(mTrueVertex);

		final Map<ImplicationVertex<T>, ImplicationVertex<T>> invertedMap = new HashMap<>();
		for (final Map.Entry<ImplicationVertex<T>, ImplicationVertex<T>> entry : vertexCopyMap.entrySet()) {
			invertedMap.put(entry.getValue(), entry.getKey());
		}
		copy.completeGraph();
		return new Pair<>(copy, invertedMap);
	}

	/**
	 * creates a copy of the subgraph with the given set and the predicates that are implied by every predicate in the
	 * set
	 */
	protected Pair<ImplicationGraph<T>, Map<ImplicationVertex<T>, ImplicationVertex<T>>>
			createSubCopy(final Set<ImplicationVertex<T>> parents, final boolean keep) {
		final ImplicationGraph<T> copy =
				new ImplicationGraph<>(mMgdScript, mFalseVertex.getPredicate(), mTrueVertex.getPredicate());
		copy.mVertices.clear();
		// get vertices that have all vertices from "parents" as an ancestor
		final Set<ImplicationVertex<T>> subVertices = parents.iterator().next().getDescendants();
		for (final ImplicationVertex<T> init : parents) {
			final Set<ImplicationVertex<T>> toRemove = new HashSet<>();
			for (final ImplicationVertex<T> vertex : subVertices) {
				if (init.getDescendants().contains(vertex)) {
					continue;
				}
				toRemove.add(vertex);
			}
			subVertices.removeAll(toRemove);
		}
		// create new vertices
		final HashMap<ImplicationVertex<T>, ImplicationVertex<T>> vertexCopyMap = new HashMap<>();
		if (keep) {
			// keep parents in graph
			subVertices.addAll(parents);
		}
		for (final ImplicationVertex<T> vertex : subVertices) {
			vertexCopyMap.put(vertex, new ImplicationVertex<>(vertex.getPredicate(), new HashSet<>(), new HashSet<>()));
		}
		// create new edges
		for (final ImplicationVertex<T> vertex : subVertices) {
			final ImplicationVertex<T> vertexCopy = vertexCopyMap.get(vertex);
			for (final ImplicationVertex<T> child : vertex.getChildren()) {
				if (!subVertices.contains(child)) {
					continue;
				}
				vertexCopy.addChild(vertexCopyMap.get(child));
			}
			for (final ImplicationVertex<T> parent : vertex.getParents()) {
				if (!subVertices.contains(parent)) {
					continue;
				}
				vertexCopy.addParent(vertexCopyMap.get(parent));
			}
			copy.mVertices.add(vertexCopy);
		}
		if (keep) {
			copy.mFalseVertex.removeChild(copy.mTrueVertex);
			copy.mTrueVertex.removeParent(copy.mFalseVertex);
			copy.mVertices.add(copy.mFalseVertex);
			parents.forEach(p -> {
				copy.mFalseVertex.addChild(p);
				p.addParent(copy.mFalseVertex);
			});
		}
		final Map<ImplicationVertex<T>, ImplicationVertex<T>> invertedMap = new HashMap<>();
		for (final Map.Entry<ImplicationVertex<T>, ImplicationVertex<T>> entry : vertexCopyMap.entrySet()) {
			invertedMap.put(entry.getValue(), entry.getKey());
		}
		copy.completeGraph();
		return new Pair<>(copy, invertedMap);
	}

	private void completeGraph() {
		for (final ImplicationVertex<T> vertex : mVertices) {
			vertex.complete();
		}
	}
}
