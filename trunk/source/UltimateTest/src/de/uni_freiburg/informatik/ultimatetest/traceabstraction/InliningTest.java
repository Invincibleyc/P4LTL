/**
 * 
 */
package de.uni_freiburg.informatik.ultimatetest.traceabstraction;

import java.util.Collection;

import de.uni_freiburg.informatik.ultimatetest.UltimateTestCase;

/**
 * Test for the inlining of Boogie procedures which in implemented by Claus. 
 * @author heizmanninformatik.uni-freiburg.de
 *
 */

public class InliningTest extends
		AbstractTraceAbstractionTestSuite {
	private static final String[] m_Directories = {
		"examples/programs/regression",
		"examples/programs/quantifier/",
		"examples/programs/quantifier/regression",
		"examples/programs/recursivePrograms",
		"examples/programs/toy"
	};
	
	private static final boolean m_TraceAbstractionBoogie = false;
	private static final boolean m_TraceAbstractionBoogieInline = true;
	private static final boolean m_TraceAbstractionC = false;
	private static final boolean m_TraceAbstractionCInline = !true;
	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getTimeout() {
		return 10 * 1000;
	}
	
	@Override
	public Collection<UltimateTestCase> createTestCases() {
		if (m_TraceAbstractionBoogie) {
			addTestCases(
					"AutomizerBpl.xml",
					"automizer/ForwardPredicates.epf",
				    m_Directories,
				    new String[] {".bpl"});
		}
		if (m_TraceAbstractionBoogieInline) {
			addTestCases(
					"AutomizerBplInline.xml",
					"automizer/ForwardPredicates.epf",
				    m_Directories,
				    new String[] {".bpl"});
		}
		if (m_TraceAbstractionC) {
			addTestCases(
					"AutomizerC.xml",
					"automizer/ForwardPredicates.epf",
				    m_Directories,
				    new String[] {".c", ".i"});
		}
		if (m_TraceAbstractionCInline) {
			addTestCases(
					"AutomizerCInline.xml",
					"automizer/ForwardPredicates.epf",
				    m_Directories,
				    new String[] {".c", ".i"});
		} 
		return super.createTestCases();
	}

	
}
