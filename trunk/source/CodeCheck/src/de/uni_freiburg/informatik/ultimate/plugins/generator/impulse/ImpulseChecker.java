package de.uni_freiburg.informatik.ultimate.plugins.generator.impulse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import org.apache.log4j.Logger;

import de.uni_freiburg.informatik.ultimate.automata.nwalibrary.NestedRun;
import de.uni_freiburg.informatik.ultimate.model.IElement;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.AnnotatedProgramPoint;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.AppEdge;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.AppHyperEdge;
import de.uni_freiburg.informatik.ultimate.plugins.generator.appgraph.ImpRootNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.CodeChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.codecheck.GraphWriter;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Return;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.RootNode;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.EdgeChecker;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.predicates.SmtManager;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.preferences.TAPreferences;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.singleTraceCheck.PredicateUnifier;

public class ImpulseChecker extends CodeChecker {
	
	private HashMap <AnnotatedProgramPoint, AnnotatedProgramPoint> _cloneNode;
	public ImpulseChecker(IElement root, SmtManager m_smtManager, TAPreferences m_taPrefs, RootNode m_originalRoot, ImpRootNode m_graphRoot,
			GraphWriter m_graphWriter, EdgeChecker edgeChecker, PredicateUnifier predicateUnifier, Logger logger) {
		super(root, m_smtManager, m_taPrefs, m_originalRoot, m_graphRoot,
				m_graphWriter, edgeChecker, predicateUnifier, logger);
	}
	
	public void replaceEdge(AppEdge edge, AnnotatedProgramPoint newTarget) {

		//System.err.println("HERE " + edge);
		if (edge instanceof AppHyperEdge)
			edge.getSource().connectOutgoingReturn(((AppHyperEdge) edge).getHier(), (Return) edge.getStatement(), newTarget);
		else
			edge.getSource().connectOutgoing(edge.getStatement(), newTarget);

		edge.disconnect();
	}
	
	public boolean defaultRedirecting(AnnotatedProgramPoint[] nodes) {
		
		boolean errorReached = false;
		for (int i = 0; i + 1 < nodes.length; ++i) {
			if (nodes[i + 1].isErrorLocation()) {
				_cloneNode.get(nodes[i]).getEdge(nodes[i + 1]).disconnect();
				errorReached = true;
			} else {
				System.err.println("HERE1 : " + nodes[i] + " {{{ " + nodes[i].getEdge(nodes[i + 1]) + " }}}  " + nodes[i + 1]);
				System.err.println("HERE2 : " + _cloneNode.get(nodes[i]) + " {{{ " + _cloneNode.get(nodes[i]).getEdge(nodes[i + 1]) + " }}}  " + nodes[i + 1]);
				replaceEdge(_cloneNode.get(nodes[i]).getEdge(nodes[i + 1]), _cloneNode.get(nodes[i + 1]));
			}
		}
		
		return errorReached;
	}

	public boolean redirectEdges(AnnotatedProgramPoint[] nodes) {
		for (AnnotatedProgramPoint node : nodes) {
			if (node.isErrorLocation())
				continue;
			AppEdge[] prevEdges = node.getIncomingEdges().toArray(new AppEdge[]{});
			for (AppEdge prevEdge : prevEdges) {
				if (prevEdge instanceof AppHyperEdge) {
					if (connectOutgoingReturnIfValid(prevEdge.getSource(), ((AppHyperEdge) prevEdge).getHier(), (Return) prevEdge.getStatement(), _cloneNode.get(node))) {
						prevEdge.disconnect();
					}
				} else {
					if (connectOutgoingIfValid(prevEdge.getSource(), prevEdge.getStatement(), _cloneNode.get(node))) {
						prevEdge.disconnect();
					}
				}
			}
		}
		return true;
	}
	@Override
	public boolean codeCheck(
			NestedRun<CodeBlock, AnnotatedProgramPoint> errorRun,
			IPredicate[] interpolants, AnnotatedProgramPoint procedureRoot) {

		AnnotatedProgramPoint[] nodes = errorRun.getStateSequence().toArray(new AnnotatedProgramPoint[0]);
		
		
		System.err.println("vor DFS");
		visited = new HashSet<AnnotatedProgramPoint>();
		dfsDEBUG(m_graphRoot, true);
		System.err.println(String.format("Graph nodes: %s\n", visited));
		System.err.println("Nach DFS");
		

		 
		ArrayList<AnnotatedProgramPoint> path = new ArrayList<AnnotatedProgramPoint>();
		Collections.addAll(path, nodes);
		System.err.println(String.format("Nodes: %s", path));

		ArrayList<IPredicate> interpolantsDBG = new ArrayList<IPredicate>();
		Collections.addAll(interpolantsDBG, interpolants);
		System.err.println(String.format("Inters: %s\n", interpolantsDBG));
		
		
		_cloneNode = new HashMap <AnnotatedProgramPoint, AnnotatedProgramPoint>();
		
		AnnotatedProgramPoint newRoot = new AnnotatedProgramPoint(nodes[0], nodes[0].getPredicate(), true);
		
		_cloneNode.put(newRoot, nodes[0]);
		nodes[0] = newRoot;
		
		for (int i = 0; i < interpolants.length; ++i) {
			_cloneNode.put(nodes[i + 1], new AnnotatedProgramPoint(nodes[i + 1], conjugatePredicates(nodes[i + 1].getPredicate(), interpolants[i]), true));
		}
		
		if (!defaultRedirecting(nodes))
			throw new AssertionError("The error location hasn't been reached.");
		//improveAnnotations(newRoot);
		redirectEdges(nodes);

		return true;
	}
	
	public boolean improveAnnotations(AnnotatedProgramPoint root) {
		HashSet <AnnotatedProgramPoint> seen = new HashSet <AnnotatedProgramPoint>();

		HashSet <AnnotatedProgramPoint> pushed = new HashSet <AnnotatedProgramPoint>();
		Queue <AnnotatedProgramPoint> queue = new LinkedList<AnnotatedProgramPoint>();
		
		queue.add(root);
		pushed.add(root);
		
		while (!queue.isEmpty()) {
			AnnotatedProgramPoint peak = queue.poll();
			AnnotatedProgramPoint[] prevNodes = peak.getIncomingNodes().toArray(new AnnotatedProgramPoint[]{});
			if (prevNodes.length == 1) {
				//TODO: Modify the new predicate.
				AnnotatedProgramPoint prevNode = prevNodes[0];
				if (seen.contains(prevNode)) {
					// peak.predicate &= prevNode.predicate o edge.formula
				}
			} else {
				//TODO: To handle this case later
				// Formula = false;
				for (AnnotatedProgramPoint prevNode : prevNodes) {
					if (seen.contains(prevNode)) {
						// Formula |= prevNode.predicate o edge.formula
					}
				}
				// peak.predicate &= Formula;
			}
			
			AnnotatedProgramPoint[] nextNodes = peak.getOutgoingNodes().toArray(new AnnotatedProgramPoint[]{});
			for (AnnotatedProgramPoint nextNode : nextNodes) {
				if (!pushed.contains(nextNode)) {
					pushed.add(nextNode);
					queue.add(nextNode);
				}
			}
			seen.add(peak);
		}
		
		return true;
	}
	
	@Override
	public boolean codeCheck(NestedRun<CodeBlock, AnnotatedProgramPoint> errorRun, IPredicate[] interpolants,
			AnnotatedProgramPoint procedureRoot,
			HashMap<IPredicate, HashMap<CodeBlock, HashSet<IPredicate>>> satTriples,
			HashMap<IPredicate, HashMap<CodeBlock, HashSet<IPredicate>>> unsatTriples) {
		this._satTriples = satTriples;
		this._unsatTriples = unsatTriples;
		return this.codeCheck(errorRun, interpolants, procedureRoot);
	}

	@Override
	public boolean codeCheck(NestedRun<CodeBlock, AnnotatedProgramPoint> errorRun, IPredicate[] interpolants,
			AnnotatedProgramPoint procedureRoot,
			HashMap<IPredicate, HashMap<CodeBlock, HashSet<IPredicate>>> satTriples,
			HashMap<IPredicate, HashMap<CodeBlock, HashSet<IPredicate>>> unsatTriples,
			HashMap<IPredicate, HashMap<IPredicate, HashMap<CodeBlock, HashSet<IPredicate>>>> satQuadruples,
			HashMap<IPredicate, HashMap<IPredicate, HashMap<CodeBlock, HashSet<IPredicate>>>> unsatQuadruples) {
		this._satQuadruples = satQuadruples;
		this._unsatQuadruples = unsatQuadruples;
		return this.codeCheck(errorRun, interpolants, procedureRoot, satTriples, unsatTriples);
	}
	
	
	

	protected boolean connectOutgoingIfValid(AnnotatedProgramPoint source, CodeBlock statement, AnnotatedProgramPoint target) {
		if (isValidEdge(source, statement, target)) {
			source.connectOutgoing(statement, target);
			return true;
		} else {
			return false;
		}
	}

	protected boolean connectOutgoingReturnIfValid(AnnotatedProgramPoint source, AnnotatedProgramPoint hier,
			Return statement, AnnotatedProgramPoint target) {
		if (isValidReturnEdge(source, statement, target, hier)) {
			source.connectOutgoingReturn(hier, statement, target);
			return true;
		} else {
			return false;
		}
	}
	
	HashSet <AnnotatedProgramPoint> visited;
	protected void dfsDEBUG(AnnotatedProgramPoint node, boolean print) {
		
		if (visited.contains(node))
			return ;
		visited.add(node);
		if (print) {
			System.err.println(String.format("\n%s\n", node));
			System.err.print("[ ");
			for (AppEdge nextEdge : node.getOutgoingEdges()) {
				System.err.print(" << " + (nextEdge instanceof AppHyperEdge ? ("return to " + ((AppHyperEdge) nextEdge).getHier()) : nextEdge.getStatement()) + " >> " + nextEdge.getTarget());
				System.err.print(" , ");
			}
			System.err.println("]");
		}
		for (AnnotatedProgramPoint nextNode : node.getOutgoingNodes()) {
			dfsDEBUG(nextNode, print);
		}
	}
}
