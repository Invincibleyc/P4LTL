package de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.smt.linearTerms;

import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermTransformer;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.smt.linearTerms.BinaryNumericRelation.NotBinaryNumericRelationException;

public class AffineSubtermNormalizer extends TermTransformer {
	
	private final Script m_Script;

	/**
	 * Transform all subterm that are an affine relation to positive normal form.
	 */
	public AffineSubtermNormalizer(Script script) {
		super();
		m_Script = script;
	}

	private static boolean isBinaryNumericRelation(Term term) {
		boolean result = true;
		try {
			new BinaryNumericRelation(term);
		} catch (NotBinaryNumericRelationException e) {
			result = false;
		}
		return result;
	}

	@Override
	protected void convert(Term term) {
		if (!term.getSort().getName().equals("Bool")) {
			// do not descend further
			super.setResult(term);
			return;
		}
		if (isBinaryNumericRelation(term)) {
			AffineRelation affRel = null;
			try {
				affRel = new AffineRelation(term);
			} catch (NotAffineException e) {
				setResult(term);
				return;
			}
			Term pnf = affRel.positiveNormalForm(m_Script);
			setResult(pnf);
			return;
			}

		super.convert(term);
	}

	
	
}
