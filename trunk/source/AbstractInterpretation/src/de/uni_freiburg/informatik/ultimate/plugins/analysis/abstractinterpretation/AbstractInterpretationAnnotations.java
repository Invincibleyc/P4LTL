/**
 * 
 */
package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.model.IElement;
import de.uni_freiburg.informatik.ultimate.model.annotation.AbstractAnnotations;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.CallStatement;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.abstractdomain.AbstractState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.abstractdomain.AbstractState.ArrayData;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.abstractdomain.AbstractState.Pair;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.abstractdomain.AbstractState.ScopedAbstractState;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretation.abstractdomain.AbstractState.LoopStackElement;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.CodeBlock;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.ProgramPoint;

/**
 * @author Christopher Dillo
 *
 */
public class AbstractInterpretationAnnotations extends AbstractAnnotations {

	private static final long serialVersionUID = 1L;

	public static final String s_annotationName = "Abstract Interpretation";
	
	private final List<AbstractState> m_states;
	
	public AbstractInterpretationAnnotations(List<AbstractState> states) {
		m_states = states;
	}

	/* (non-Javadoc)
	 * @see de.uni_freiburg.informatik.ultimate.model.annotation.AbstractAnnotations#getFieldNames()
	 */
	@Override
	protected String[] getFieldNames() {
		return new String[] { "Abstract states"};
	}

	/* (non-Javadoc)
	 * @see de.uni_freiburg.informatik.ultimate.model.annotation.AbstractAnnotations#getFieldValue(java.lang.String)
	 */
	@Override
	protected Object getFieldValue(String field) {
		switch (field) {
		case "Abstract states" :
			if (m_states == null) return "No states";
			// state -> (scope -> (value, array -> (value, has unclear indices)), trace, loop stack)
			List<Object> states = new ArrayList<Object>(m_states.size());
			for (AbstractState state : m_states) {
				// scope -> (value, array -> (value, has unclear indices))
				Map<String, Object> callstackData = new LinkedHashMap<String, Object>(2);
				List<ScopedAbstractState> callstack = state.getCallStack();
				for (ScopedAbstractState cse : callstack) {
					// array -> (value, has unclear indices)
					Map<Pair, Map<String, Object>> arrayInfo = new LinkedHashMap<Pair, Map<String, Object>>(2);
					Map<Pair, ArrayData> arrays = cse.getArrays();
					for (Pair ident : arrays.keySet()) {
						ArrayData a = arrays.get(ident);
						Map<String, Object> aInfo = new LinkedHashMap<String, Object>();
						aInfo.put("Merged value", a.getValue());
						aInfo.put("Has unclear indices", a.getIndicesUnclear());
						arrayInfo.put(ident, aInfo);
					}
					// store (value, array -> (value, has unclear indices))
					Map<String, Object> scopeData = new LinkedHashMap<String, Object>();
					if (!cse.getValues().isEmpty())
						scopeData.put("Values", cse.getValues());
					if (!arrayInfo.isEmpty())
						scopeData.put("Arrays", arrayInfo);
					CallStatement csmt = cse.getCallStatement();
					String functionName = (csmt == null) ? "GLOBAL" : csmt.getMethodName();
					callstackData.put(String.format("%s", functionName), scopeData);
				}
				List<CodeBlock> passedCodeBlocks = state.getTrace();
				List<String> trace =
						new ArrayList<String>(passedCodeBlocks.size());
				for (CodeBlock block : passedCodeBlocks)
					trace.add(block.getPrettyPrintedStatements());
				List<LoopStackElement> loopStackEntries = state.getLoopEntryNodes();
				List<String> loopStack =
						new ArrayList<String>(loopStackEntries.size());
				for (LoopStackElement lse : loopStackEntries)
					if (lse.getLoopNode() != null)
					loopStack.add(String.format("%s -> ... -> %s -> %s",
							lse.getLoopNode(), (ProgramPoint) lse.getExitEdge().getSource(), lse.getLoopNode()));
				Map<String, Object> stateData = new LinkedHashMap<String, Object>();
				stateData.put("Call stack", callstackData);
				stateData.put("Trace", trace);
				stateData.put("Loop stack", loopStack);
				states.add(stateData);
			}
			return states;
		default:
			return null;
		}
	}

	/**
	 * Annotate a given IElement with this annotation object
	 * @param element The IElement object this annotation shall be added to
	 */
	public void annotate(IElement element) {
		element.getPayload().getAnnotations().put(s_annotationName, this);
	}

	/**
	 * Checks the given IElement for an AbstractInterpretationAnnotation and returns it
	 * @param element The element whose Annotation is to be retrieved
	 * @return An AbstractInterpretationAnnotation on the IElement or null if none is present
	 */
	public static AbstractInterpretationAnnotations getAnnotation(IElement element) {
		if (!element.hasPayload()) {
			return null;
		}
		if (!element.getPayload().hasAnnotation()) {
			return null;
		}
		return (AbstractInterpretationAnnotations) element.getPayload().getAnnotations().get(s_annotationName);
	}
	
	@Override
	public String toString() {
		return "Abstract Interpretation";
	}

}
