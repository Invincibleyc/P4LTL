package de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.util;

import java.math.BigDecimal;

/**
 * Utility functions for BigDecimal calculations, i.e.
 * Euclidean division and modulo operations
 * 
 * @author schaetzc@informatik.uni-freiburg.de
 */
public class NumUtil {

	/**
	 * Calculates the euclidean division.euc
	 * The result {@code q} of the euclidean division {@code a / b = q}
	 * satisfies {@code a = bq + r} where {@code 0 ≤ r < |b|} and {@code b ≠ 0}.
	 * <p>
	 * The type of division only matters for non-real numbers like integers
	 * or floats with limited precision.
	 * <p>
	 * Examples:<br>
	 * <code>
	 *     +7 / +3 = +2<br>
	 *     +7 / -3 = -2<br>
	 *     -7 / +3 = -3<br>
	 *     -7 / -3 = +3
	 * </code>
	 * 
	 * @param a dividend
	 * @param b divisor
	 * @return euclidean division {@code q = a / b}
	 * 
	 * @throws ArithmeticException if {@code b = 0}
	 */
	public static BigDecimal euclideanDivision(BigDecimal a, BigDecimal b) {
		BigDecimal[] quotientAndRemainder = a.divideAndRemainder(b);
		BigDecimal quotient = quotientAndRemainder[0];
		BigDecimal remainder = quotientAndRemainder[1];
		if (remainder.signum() != 0) {
			if (a.signum() < 0) { // sig(a) != 0, since "remainder != 0"
				if (b.signum() < 0) { // sig(b) != 0, since "a / 0" throws an exception
					quotient = quotient.add(BigDecimal.ONE);
				} else {
					quotient = quotient.subtract(BigDecimal.ONE);
				}
			}
		}
		return quotient;
	}
	
	/**
	 * Calculates {@code a / b} only if {@code b} is a divisor of {@code a}.
	 * 
	 * @param a dividend
	 * @param b true divisor of {@code a}
	 * @return exact result of {@code a / b} (always an integer)
	 * 
	 * @throws ArithmeticException if {@code b} is a not a divisor of {@code a}.
	 */
	public static BigDecimal exactDivison(BigDecimal a, BigDecimal b) {
		BigDecimal[] quotientAndRemainder = a.divideAndRemainder(b);
		if (quotientAndRemainder[1].signum() == 0) {
			return quotientAndRemainder[0];
		}
		throw new ArithmeticException("Divison not exact.");
	}
	
	/**
	 * Checks if a number is integral.
	 * 
	 * @param d number
	 * @return {@code d} is an integer
	 */
	public static boolean isIntegral(BigDecimal d) {
		return d.remainder(BigDecimal.ONE).signum() == 0;

		// alternative implementation (takes 4 times longer)
//		try {
//			d.setScale(0, RoundingMode.UNNECESSARY);
//			return true;
//		} catch (ArithmeticException e) {
//			return false;
//		}
	}

	/**
	 * Calculates the euclidean modulo.
	 * The result {@code r} is the remainder of the euclidean division {@code a / b = q},
	 * satisfying {@code a = bq + r} where {@code 0 ≤ r < |b|} and {@code b ≠ 0}.
	 * <p>
	 * Examples:<br>
	 * <code>
	 *     +7 % +3 = 1<br>
	 *     +7 % -3 = 1<br>
	 *     -7 % +3 = 2<br>
	 *     -7 % -3 = 2
	 * </code>
	 * 
	 * @param a dividend
	 * @param b divisor
	 * @return {@code r = a % b} (remainder of the euclidean division {@code a / b})
	 * 
	 * @throws ArithmeticException if {@code b = 0}
	 */
	public static BigDecimal euclideanModulo(BigDecimal a, BigDecimal b) {
		BigDecimal r = a.remainder(b);
		if (r.signum() < 0) {
			r = r.add(b.abs());
		}
		return r;
	}

}
