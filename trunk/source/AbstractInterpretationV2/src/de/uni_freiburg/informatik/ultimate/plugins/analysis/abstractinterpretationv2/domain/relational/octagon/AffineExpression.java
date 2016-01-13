package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.relational.octagon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.util.NumUtil;

public class AffineExpression {

	private Map<String, BigDecimal> mCoefficients;
	private BigDecimal mConstant;
	
	public AffineExpression(Map<String, BigDecimal> coefficients, BigDecimal constant) {
		assert coefficients != null && constant != null;
		mCoefficients = coefficients;
		mConstant = constant;
		removeZeroSummands();
	}

	public AffineExpression(BigDecimal constant) {
		this(new HashMap<>(), constant);
	}
	
	private AffineExpression() {
		this(BigDecimal.ZERO);
	}
	
	private void removeZeroSummands() {
		Iterator<BigDecimal> iter = mCoefficients.values().iterator();
		while (iter.hasNext()) {
			if (iter.next().signum() == 0) { // "signum()" is faster than "comparTo(0)"
				iter.remove();
			}
		}
	}
	
	public BigDecimal getCoefficient(String var) {
		BigDecimal factor = mCoefficients.get(var);
		return (factor == null) ? BigDecimal.ZERO : factor;
	}
	
	public boolean isConstant() {
//		for (BigDecimal factor : mMapVarToCoefficient.values()) {
//			if (factor.signum() != 0) {
//				return false;
//			}
//		}
//		return true;
		return mCoefficients.isEmpty();
	}
	
	public AffineExpression add(AffineExpression summand) {
		AffineExpression sum = new AffineExpression();
		sum.mConstant = mConstant.add(summand.mConstant);
		Set<String> vars = new HashSet<>();
		vars.addAll(mCoefficients.keySet());
		vars.addAll(summand.mCoefficients.keySet());
		for (String v : vars) {
			BigDecimal sumFactor = getCoefficient(v).add(summand.getCoefficient(v));
			sum.mCoefficients.put(v, sumFactor);
		}
		sum.removeZeroSummands();
		return sum;
	}
	
	public AffineExpression negate() {
		AffineExpression negation = new AffineExpression();
		negation.mConstant = mConstant.negate();
		for (Map.Entry<String, BigDecimal> entry : mCoefficients.entrySet()) {
			negation.mCoefficients.put(entry.getKey(), entry.getValue().negate());
		}
		return negation;
	}
	
	public AffineExpression multiply(AffineExpression factor) {
		AffineExpression affineFactor, constantFactor;
		if (isConstant()) {
			affineFactor = factor;
			constantFactor = this;
		} else if (factor.isConstant()) {
			affineFactor = this;
			constantFactor = factor;
		} else {
			return null;
		}
		if (constantFactor.mConstant.signum() == 0) {
			return new AffineExpression();
		}
		AffineExpression product = new AffineExpression();
		product.mConstant = affineFactor.mConstant.multiply(constantFactor.mConstant);
		for (Map.Entry<String, BigDecimal> entry : affineFactor.mCoefficients.entrySet()) {
			BigDecimal newCoefficent = entry.getValue().multiply(constantFactor.mConstant);
			product.mCoefficients.put(entry.getKey(), newCoefficent);
		}
		return product;
	}

	public AffineExpression divide(AffineExpression divisor, boolean integerDivison) {
		try {
			if (divisor.isConstant()) {
				return divideByConstant(divisor, integerDivison);
			} else {
				return divideByNonConstant(divisor, integerDivison);
			}
		} catch (ArithmeticException e) {
		}
		return null;
	}

	private AffineExpression divideByConstant(AffineExpression divisor, boolean integerDivison) {
		assert divisor.isConstant();
		AffineExpression quotient = new AffineExpression();
		BiFunction<BigDecimal, BigDecimal, BigDecimal> divOp =
				integerDivison ? NumUtil::euclideanDivision : BigDecimal::divide;
		quotient.mConstant = divOp.apply(mConstant, divisor.mConstant);
		for (Map.Entry<String, BigDecimal> entry : mCoefficients.entrySet()) {
			BigDecimal newCoefficent = divOp.apply(entry.getValue(), divisor.mConstant);
			quotient.mCoefficients.put(entry.getKey(), newCoefficent);
		}
		return quotient;
	}
	
	// also allows to divide by constants, but returns null more often
	// divide by constant may return an AfffineTerm where this doesn't
	// the result will always be a constant
	private AffineExpression divideByNonConstant(AffineExpression divisor, boolean integerDivison) {
		Set<String> vars = mCoefficients.keySet();
		if (!vars.equals(divisor.mCoefficients.keySet())) {
			return null;
		}
		BiFunction<BigDecimal, BigDecimal, BigDecimal> divOp =
				integerDivison ? NumUtil::exactDivison : BigDecimal::divide;
		BigDecimal qFixed = null;
		if (divisor.mConstant.signum() != 0) {
			qFixed = divOp.apply(mConstant, divisor.mConstant);
		} else if (mConstant.signum() != 0) {
			return null; // (x + c) / (x + 0) is not affine
		}
		for (String v : vars) {
			BigDecimal c = mCoefficients.get(v);
			BigDecimal d = divisor.mCoefficients.get(v);
			BigDecimal q = divOp.apply(c, d);
			if (qFixed == null) {
				qFixed = q;
			} else if (q.compareTo(qFixed) != 0) {
				return null;
			}
		}
		return new AffineExpression(qFixed);
	}
	
	public AffineExpression modulo(AffineExpression divisor) {
		try {
			if (isConstant() && divisor.isConstant()) {
				return new AffineExpression(NumUtil.euclideanModulo(mConstant, divisor.mConstant));
			} else if (mCoefficients.keySet().equals(divisor.mCoefficients.keySet())) {
				AffineExpression qAe = this.divideByNonConstant(divisor, true);
				assert qAe.isConstant();
				// only case "(int * x) % x = 0" can be computed
				//
				// TODO remove -- THIS IS WRONG!
				//                x % x is NOT 0, since x could be 0 and 0 % 0 is undefined.
				//
				if (NumUtil.isIntegral(qAe.mConstant)) {
					return new AffineExpression(BigDecimal.ZERO);
				}
			}
		} catch (ArithmeticException e) {
		}
		return null;
	}

	@Override
	public int hashCode() {
		return 31 * mCoefficients.hashCode() + mConstant.hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (obj == null) {
			return false;
		} else if (getClass() != obj.getClass()) {
			return false;
		}
		AffineExpression other = (AffineExpression) obj;
		if (mConstant.compareTo(other.mConstant) != 0) {
			return false;
		}
		Set<String> vars = new HashSet<>();
		vars.addAll(mCoefficients.keySet());
		vars.addAll(other.mCoefficients.keySet());
		for (String v : vars) {
			if (getCoefficient(v).compareTo(other.getCoefficient(v)) != 0) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, BigDecimal> entry : mCoefficients.entrySet()) {
			sb.append(entry.getValue());
			sb.append("\u22C5"); // multiplication dot
			sb.append(entry.getKey());
			sb.append(" + ");
		}
		sb.append(mConstant);
		return sb.toString();
	}
}
