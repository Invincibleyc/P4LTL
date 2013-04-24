/**
 * 
 */
package de.uni_freiburg.informatik.ultimate.blockencoding.rating.util;


/**
 * To create an rating heuristic, which can decide on basis of the underlying
 * program, we need to store some statistical properties, which we can use for
 * that. <br>
 * So for example, the amount of edges in the graph, or the amount of
 * disjunctions used in the minimization, or the amount of variables, and so
 * on...
 * 
 * @author Stefan Wissert
 * 
 */
public class EncodingStatistics {

	/**
	 * stores the total amount of basic edges in the original graph
	 */
	public static int countOfBasicEdges;

	/**
	 * stores the total amount of created disjunctions (minimizing parallel
	 * edges), this is useful because it seems to be good, to break at some
	 * amount of disjunctions
	 */
	public static int countOfDisjunctions;

	/**
	 * stores the maximum number of disjunctions, which are contained in one
	 * composite edge
	 */
	public static int maxDisjunctionsInOneEdge;
	
	/**
	 * stores the maximum number of elements in one single disjunction
	 */
	public static int maxElementsInOneDisjunction;
	
	/**
	 * stores the max. number of variables used in one edge
	 */
	public static int maxDiffVariablesInOneEdge;
	
	/**
	 * stores the min. number of variables used in one edge
	 */
	public static int minDiffVariablesInOneEdge;

	/**
	 * Initializes, all stored statistics. This have to be done, before we start
	 * a new run of block encoding.
	 */
	public static void init() {
		countOfBasicEdges = 0;
		countOfDisjunctions = 0;
		maxDisjunctionsInOneEdge = 0;
		maxElementsInOneDisjunction = 0;
		maxDiffVariablesInOneEdge = 0;
		minDiffVariablesInOneEdge = Integer.MAX_VALUE;
	}

	/**
	 * 
	 */
	public static void incCountOfBasicEdges() {
		countOfBasicEdges++;
	}

	/**
	 * 
	 */
	public static void incCountOfDisjunctions() {
		countOfDisjunctions++;
	}
	
	/**
	 * @param value
	 */
	public static void setMaxDisjunctionsInOneEdge(int value) {
		if (value > maxDisjunctionsInOneEdge) {
			maxDisjunctionsInOneEdge = value;
		}
	}
	
	/**
	 * @param value
	 */
	public static void setMaxElementsInOneDisjunction(int value) {
		if (value > maxElementsInOneDisjunction) {
			maxElementsInOneDisjunction = value;
		}
	}
	
	/**
	 * @param value
	 */
	public static void setMaxMinDiffVariablesInOneEdge(int value) {
		// we ignore zero
		if (value == 0) {
			return;
		}
		if (value > maxDiffVariablesInOneEdge) {
			maxDiffVariablesInOneEdge = value;
		}
		if (value < minDiffVariablesInOneEdge) {
			minDiffVariablesInOneEdge = value;
		}
	}
	
}
