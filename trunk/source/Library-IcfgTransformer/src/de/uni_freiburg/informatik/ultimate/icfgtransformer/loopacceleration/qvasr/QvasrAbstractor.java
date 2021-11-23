/*
 * Copyright (C) 2021 Jonas Werner (wernerj@informatik.uni-freiburg.de)
 * Copyright (C) 2021 University of Freiburg
 *
 * This file is part of the ULTIMATE IcfgTransformer library.
 *
 * The ULTIMATE IcfgTransformer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE IcfgTransformer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE IcfgTransformer library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE IcfgTransformer library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE IcfgTransformer grant you additional permission
 * to convey the resulting work.
 */

package de.uni_freiburg.informatik.ultimate.icfgtransformer.loopacceleration.qvasr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.SimultaneousUpdate;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtSortUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SubstitutionWithLocalSimplification;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.polynomials.AffineTerm;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.polynomials.IPolynomialTerm;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.polynomials.PolynomialTerm;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.polynomials.PolynomialTermOperations;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.polynomials.PolynomialTermUtils;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.util.datastructures.HashDeque;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Pair;

/**
 *
 * @author Jonas Werner (wernerj@informatik.uni-freiburg.de) This class is used to compute a {@link QvasrAbstraction}
 *         for a given {@link Term} by solving various sets of linear equation systems.
 *
 *
 */
public class QvasrAbstractor {

	private final ManagedScript mScript;
	private final IUltimateServiceProvider mServices;
	private final ILogger mLogger;
	private final Term mZero;
	private final Term mOne;

	/**
	 *
	 * @author Jonas Werner (wernerj@informatik.uni-freiburg.de) Define which kind of base matrix. Resets: Where the
	 *         outvars only depend on the invars and addition vector a. Additions: Where outvars depend on invars and
	 *         addition vector a.
	 *
	 */
	private enum BaseType {
		RESETS, ADDITIONS
	}

	/**
	 * Computes a Q-Vasr-abstraction (S, V), with linear simulation matrix S and Q-Vasr V. A transition formula can be
	 * overapproximated using a Q-Vasr-abstraction.
	 *
	 * @param script
	 * @param logger
	 */
	public QvasrAbstractor(final ManagedScript script, final ILogger logger, final IUltimateServiceProvider services) {
		mScript = script;
		mLogger = logger;
		mServices = services;
		mZero = mScript.getScript().decimal("0");
		mOne = mScript.getScript().decimal("1");
	}

	/**
	 * Compute a Q-Vasr-abstraction for a given transition formula.
	 *
	 * @param transitionTerm
	 * @param transitionFormula
	 * @return
	 */
	public QvasrAbstraction computeAbstraction(final Term transitionTerm,
			final UnmodifiableTransFormula transitionFormula) {

		final Map<Term, Term> updatesInFormulaAdditions = getUpdates(transitionFormula, BaseType.ADDITIONS);
		final Map<Term, Term> updatesInFormulaResets = getUpdates(transitionFormula, BaseType.RESETS);
		final Term[][] newUpdatesMatrixResets = constructBaseMatrix(updatesInFormulaResets, transitionFormula);
		final Term[][] newUpdatesMatrixAdditions = constructBaseMatrix(updatesInFormulaAdditions, transitionFormula);

		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Resets: ");
			printMatrix(newUpdatesMatrixResets);
			mLogger.debug("Additions: ");
			printMatrix(newUpdatesMatrixAdditions);
		}

		final Term[][] gaussedAdditions = gaussPartialPivot(newUpdatesMatrixAdditions);
		printMatrix(gaussedAdditions);
		final Term[][] gaussedAdditionsOnes = gaussRowEchelonForm(gaussedAdditions);
		printMatrix(gaussedAdditionsOnes);
		final Term[][] gaussedAdditionsOnesPruned = removeZeroRows(gaussedAdditionsOnes);
		final Term[] solutions = backSub(gaussedAdditionsOnesPruned);

		final Term[][] gaussedResets = gaussPartialPivot(newUpdatesMatrixResets);
		printMatrix(gaussedResets);
		final Term[][] gaussedResetsOnes = gaussRowEchelonForm(gaussedResets);
		printMatrix(gaussedResetsOnes);
		final Term[][] gaussedResetsOnesPruned = removeZeroRows(gaussedResetsOnes);
		printMatrix(gaussedResetsOnesPruned);
		final Term[] solutionsResets = backSub(gaussedResetsOnesPruned);

		final Rational[][] out = new Rational[2][2];
		final Qvasr qvasr = null;
		return new QvasrAbstraction(out, qvasr);
	}

	/**
	 * Convert a matrix in upper triangular form into row echelon form -> only leading 1s using {@link PolynomialTerm}
	 *
	 * @param matrix
	 * @return
	 */
	private Term[][] gaussRowEchelonFormPolynomial(final Term[][] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[0].length; j++) {
				if (!SmtUtils.areFormulasEquivalent(matrix[i][j], mZero, mScript.getScript())) {

					final IPolynomialTerm divider = PolynomialTermOperations.convert(mScript.getScript(), matrix[i][j]);
					for (int k = j; k < matrix[0].length; k++) {
						final IPolynomialTerm[] polyArr = new IPolynomialTerm[2];
						final IPolynomialTerm toBeDivided =
								PolynomialTermOperations.convert(mScript.getScript(), matrix[i][k]);

						polyArr[0] = toBeDivided;
						polyArr[1] = divider;

						final IPolynomialTerm polyDiv;
						if (PolynomialTerm.divisionPossible(polyArr)) {
							polyDiv = AffineTerm.divide(polyArr, mScript.getScript());
						} else {
							polyDiv = PolynomialTermUtils.simplifyImpossibleDivision("/", polyArr, mScript.getScript());
						}
						matrix[i][k] = polyDiv.toTerm(mScript.getScript());
					}
					break;
				}
			}
		}
		return matrix;
	}

	/**
	 * Convert a matrix in upper triangular form into row echelon form -> only leading 1s using Standard Real Division.
	 *
	 * @param matrix
	 * @return
	 *
	 */
	private Term[][] gaussRowEchelonForm(final Term[][] matrix) {
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[0].length; j++) {
				if (!SmtUtils.areFormulasEquivalent(matrix[i][j], mZero, mScript.getScript())) {
					final Term divider = matrix[i][j];
					for (int k = j; k < matrix[0].length; k++) {
						final Term toBeDivided = matrix[i][k];
						final Term division = QvasrAbstractor.simplifyRealDivision(mScript, toBeDivided, divider);
						if (mLogger.isDebugEnabled()) {
							mLogger.debug("Matrix " + i + k + " =  " + division.toStringDirect());
						}
						matrix[i][k] = division;
					}
					break;
				}
			}
		}
		return matrix;
	}

	/**
	 * Use back substitution to compute solutions for a matrix in row echelon form.
	 *
	 * @param matrix
	 * @return
	 */
	private Term[] backSub(final Term[][] matrix) {
		final Term[] solutions = new Term[matrix[0].length - 1];
		for (int k = 0; k < matrix[0].length - 1; k++) {
			solutions[k] = mZero;
		}
		final int columns = matrix[0].length - 1;
		int solCounter = matrix[0].length - 2;
		for (int i = matrix.length - 1; 0 <= i; i--) {
			solutions[solCounter] = matrix[i][columns];
			for (int j = solCounter + 1; j <= columns - 1; j++) {
				final Term mul = QvasrAbstractor.simplifyRealMultiplication(mScript, matrix[i][j], solutions[j]);
				final Term sub = QvasrAbstractor.simplifyRealSubtraction(mScript, solutions[solCounter], mul);
				solutions[solCounter] = sub;
			}
			solCounter--;
		}
		return solutions;
	}

	/**
	 * Remove rows with all 0s from a given matrix.
	 *
	 * @param matrix
	 * @return
	 */
	private Term[][] removeZeroRows(final Term[][] matrix) {
		final Set<Integer> toBeEliminated = new HashSet<>();
		for (int i = 0; i < matrix.length; i++) {
			for (int j = 0; j < matrix[0].length; j++) {
				if (!SmtUtils.areFormulasEquivalent(matrix[i][j], mZero, mScript.getScript())) {
					break;
				}
				if (j == matrix[0].length - 1) {
					toBeEliminated.add(i);
				}
			}
		}
		final int prunedLength = matrix.length - toBeEliminated.size();
		final Term[][] prunedMatrix = new Term[prunedLength][matrix[0].length];
		int cnt = 0;
		for (int k = 0; k < prunedLength; k++) {
			if (!toBeEliminated.contains(k)) {
				for (int l = 0; l < matrix[0].length; l++) {
					prunedMatrix[cnt][l] = matrix[k][l];
				}
				cnt++;
			}
		}
		return prunedMatrix;
	}

	/**
	 * Bring a given matrix into upper triangle form.
	 *
	 * @param matrix
	 * @return
	 */
	private Term[][] gaussPartialPivot(Term[][] matrix) {
		for (int k = 0; k < matrix.length - 1; k++) {
			int max = -1;
			if ((k + 1) < matrix.length && (k + 1) < matrix[0].length) {
				max = findPivot(matrix, k);
			}
			if (max == -1) {
				mLogger.warn("Gaussian Elimination Done.");
				return matrix;
			}
			if (max != 0) {
				matrix = swap(matrix, k, max);
			}
			final Term pivot = matrix[k][k];
			// i is the row
			for (int i = k + 1; i < matrix.length; i++) {
				final Term toBeEliminated = matrix[i][k];
				final Term newDiv = QvasrAbstractor.simplifyRealDivision(mScript, toBeEliminated, pivot);

				// k is the column
				for (int j = k; j < matrix[0].length; j++) {
					final Term currentColumn = matrix[k][j];
					final Term currentEntry = matrix[i][j];
					final Term newMul = QvasrAbstractor.simplifyRealMultiplication(mScript, newDiv, currentColumn);
					mLogger.debug(newMul.toStringDirect());
					final Term newSub = QvasrAbstractor.simplifyRealSubtraction(mScript, currentEntry, newMul);
					mLogger.debug(newSub.toStringDirect());
					matrix[i][j] = newSub;
				}
			}
		}
		return matrix;
	}

	private static Term factorOutRealSum(final ManagedScript script, final Term sum) {
		if (sum instanceof ApplicationTerm) {
			final ApplicationTerm sumAppTerm = (ApplicationTerm) sum;
			final List<Term> summands = getApplicationTermSumParams(sumAppTerm);
			final List<Term> simplifiedSummands = new ArrayList<>();

			for (final Term summandOne : summands) {
				if (summandOne instanceof ApplicationTerm
						&& ((ApplicationTerm) summandOne).getFunction().getName().equals("*")) {
					final List<Term> factors = getApplicationTermMultiplicationParams(script, summandOne);
					final Term occurencesMult = script.getScript()
							.decimal(Integer.toString(Collections.frequency(factors, summandOne)) + 1);
					int occurences = 1;
					Term factorToBeFactored = summandOne;
					Term factorNotToBeFactored = summandOne;
					for (final Term factor : factors) {
						factorToBeFactored = factor;
						if (occurences > 1) {
							continue;
						}
						occurences = Collections.frequency(summands, factor) + 1;
						factorNotToBeFactored = factor;
					}
					final Term summandOneSimplified = SmtUtils.mul(script.getScript(), "*", occurencesMult,
							factorNotToBeFactored, SmtUtils.sum(script.getScript(), "+",
									script.getScript().decimal(Integer.toString(occurences)), factorToBeFactored));
					simplifiedSummands.add(summandOneSimplified);
				}
			}
			return SmtUtils.sum(script.getScript(), "+",
					simplifiedSummands.toArray(new Term[simplifiedSummands.size()]));
		}
		return sum;
	}

	/**
	 * Simplify multiplications of two factors.
	 *
	 * TODO: FactorOne || FactorTwo no constantTerms
	 *
	 * @param factorOne
	 * @param factorTwo
	 * @return
	 */
	public static Term expandRealMultiplication(final ManagedScript script, final Term factorOne,
			final Term factorTwo) {
		Term result = script.getScript().decimal("0");
		if (factorOne instanceof ApplicationTerm) {
			final ApplicationTerm factorOneAppTerm = (ApplicationTerm) factorOne;
			if (factorTwo instanceof ApplicationTerm) {
				final ApplicationTerm factorTwoAppTerm = (ApplicationTerm) factorTwo;
				for (final Term paramFactorOne : factorOneAppTerm.getParameters()) {
					for (final Term paramFactorTwo : factorTwoAppTerm.getParameters()) {
						final Term mult = SmtUtils.mul(script.getScript(), "*", paramFactorOne, paramFactorTwo);
						result = SmtUtils.sum(script.getScript(), "+", result, mult);
					}
				}
			} else {
				for (final Term paramFactorOne : factorOneAppTerm.getParameters()) {
					final Term mult = SmtUtils.mul(script.getScript(), "*", paramFactorOne, factorTwo);
					result = SmtUtils.sum(script.getScript(), "+", result, mult);
				}
			}
		} else if (factorTwo instanceof ApplicationTerm) {
			final ApplicationTerm factorTwoAppTerm = (ApplicationTerm) factorTwo;
			for (final Term paramFactorTwo : factorTwoAppTerm.getParameters()) {
				final Term mult = SmtUtils.mul(script.getScript(), "*", paramFactorTwo, factorOne);
				result = SmtUtils.sum(script.getScript(), "+", result, mult);
			}
		}
		return result;
	}

	/**
	 * Simplify multiplications of two factors.
	 *
	 * TODO: FactorOne || FactorTwo no constantTerms
	 *
	 * @param factorOne
	 * @param factorTwo
	 * @return
	 */
	public static Term expandRealMultiplication(final ManagedScript script, final List<Term> factors) {
		Term result = script.getScript().decimal("0");
		if (factors.size() < 2) {
			return factors.get(0);
		}
		final Deque<Term> factorStack = new ArrayDeque<>(factors);
		while (!factorStack.isEmpty()) {
			final Term factorOne = factorStack.pop();
			for (final Term factorTwo : factorStack) {
				if (factorOne instanceof ApplicationTerm) {
					final ApplicationTerm factorOneAppTerm = (ApplicationTerm) factorOne;
					if (factorTwo instanceof ApplicationTerm) {
						final ApplicationTerm factorTwoAppTerm = (ApplicationTerm) factorTwo;
						for (final Term paramFactorOne : factorOneAppTerm.getParameters()) {
							for (final Term paramFactorTwo : factorTwoAppTerm.getParameters()) {
								final Term mult = SmtUtils.mul(script.getScript(), "*", paramFactorOne, paramFactorTwo);
								result = SmtUtils.sum(script.getScript(), "+", result, mult);
							}
						}
					} else {
						for (final Term paramFactorOne : factorOneAppTerm.getParameters()) {
							final Term mult = SmtUtils.mul(script.getScript(), "*", paramFactorOne, factorTwo);
							result = SmtUtils.sum(script.getScript(), "+", result, mult);
						}
					}
				} else if (factorTwo instanceof ApplicationTerm) {
					final ApplicationTerm factorTwoAppTerm = (ApplicationTerm) factorTwo;
					for (final Term paramFactorTwo : factorTwoAppTerm.getParameters()) {
						final Term mult = SmtUtils.mul(script.getScript(), "*", paramFactorTwo, factorOne);
						result = SmtUtils.sum(script.getScript(), "+", result, mult);
					}
				} else {
					final Term mult = SmtUtils.mul(script.getScript(), "*", factorOne, factorTwo);
					result = SmtUtils.sum(script.getScript(), "+", result, mult);
				}
			}
		}
		return result;
	}

	/**
	 * Simplify a Division which is multiplied.
	 *
	 * @param dividend
	 * @param divisor
	 * @param mult
	 * @return
	 */
	public static Term simplifyRealDivision(final ManagedScript script, final Term dividend, final Term divisor) {
		Term simplifiedDividend = dividend;
		Term simplifiedDivisor = divisor;
		final Term zero = script.getScript().decimal("0");
		final Term one = script.getScript().decimal("1");

		Term result = SmtUtils.divReal(script.getScript(), dividend, divisor);
		/*
		 * Can be represented by AffineTerm -> less expensive
		 */
		if (SmtUtils.areFormulasEquivalent(divisor, zero, script.getScript())) {
			throw new UnsupportedOperationException("cannot divide by 0!");
		}
		if (SmtUtils.areFormulasEquivalent(divisor, script.getScript().decimal("1"), script.getScript())) {
			return dividend;
		}
		if (SmtUtils.areFormulasEquivalent(dividend, zero, script.getScript())) {
			return zero;
		}

		if (SmtUtils.areFormulasEquivalent(dividend, divisor, script.getScript())) {
			return one;
		}

		if (simplifiedDividend instanceof ApplicationTerm && simplifiedDivisor instanceof ApplicationTerm) {

			/*
			 * Fraction of two fractions
			 */
			if (simplifiedDividend instanceof ApplicationTerm && simplifiedDivisor instanceof ApplicationTerm
					&& ((ApplicationTerm) simplifiedDividend).getFunction().getName().equals("/")
					&& ((ApplicationTerm) simplifiedDivisor).getFunction().getName().equals("/")) {
				final ApplicationTerm dividendAppTerm = (ApplicationTerm) simplifiedDividend;
				final ApplicationTerm divisorAppTerm = (ApplicationTerm) simplifiedDivisor;
				final Term dividendDividend = dividendAppTerm.getParameters()[0];
				final Term dividendDivisor = dividendAppTerm.getParameters()[1];
				final Term divisorDividend = divisorAppTerm.getParameters()[0];
				final Term divisorDivisor = divisorAppTerm.getParameters()[1];

				simplifiedDividend = SmtUtils.mul(script.getScript(), "*", dividendDividend, divisorDivisor);
				simplifiedDivisor = SmtUtils.mul(script.getScript(), "*", dividendDivisor, divisorDividend);

				final Pair<Term, Term> reduced =
						QvasrAbstractor.reduceRealDivision(script, simplifiedDividend, simplifiedDivisor);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();

				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
			/*
			 * Fraction where the dividend is a fraction.
			 */
			if (simplifiedDividend instanceof ApplicationTerm && simplifiedDivisor instanceof ApplicationTerm
					&& ((ApplicationTerm) simplifiedDividend).getFunction().getName().equals("/")
					&& !((ApplicationTerm) simplifiedDivisor).getFunction().getName().equals("/")) {
				final ApplicationTerm dividendAppTerm = (ApplicationTerm) simplifiedDividend;
				final Term dividendDividend = dividendAppTerm.getParameters()[0];
				final Term dividendDivisor = dividendAppTerm.getParameters()[1];

				simplifiedDividend = dividendDividend;
				simplifiedDivisor = SmtUtils.mul(script.getScript(), "*", dividendDivisor, divisor);
				final Pair<Term, Term> reduced =
						QvasrAbstractor.reduceRealDivision(script, simplifiedDividend, simplifiedDivisor);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
			/*
			 * Fraction where the divisor is a fraction.
			 */
			if (simplifiedDividend instanceof ApplicationTerm && simplifiedDivisor instanceof ApplicationTerm
					&& !((ApplicationTerm) simplifiedDividend).getFunction().getName().equals("/")
					&& ((ApplicationTerm) simplifiedDivisor).getFunction().getName().equals("/")) {
				final ApplicationTerm divisorAppTerm = (ApplicationTerm) simplifiedDivisor;
				final Term divisorDividend = divisorAppTerm.getParameters()[0];
				final Term divisorDivisor = divisorAppTerm.getParameters()[1];

				simplifiedDividend = SmtUtils.mul(script.getScript(), "*", dividend, divisorDivisor);
				simplifiedDivisor = divisorDividend;
				final Pair<Term, Term> reduced =
						QvasrAbstractor.reduceRealDivision(script, simplifiedDividend, simplifiedDivisor);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
			/*
			 * Simplify multiplications, where dividend and divisor are multiplications.
			 */
			if (simplifiedDividend instanceof ApplicationTerm && simplifiedDivisor instanceof ApplicationTerm
					&& ((ApplicationTerm) simplifiedDividend).getFunction().getName().equals("*")
					&& ((ApplicationTerm) simplifiedDivisor).getFunction().getName().equals("*")) {
				final ApplicationTerm dividendAppTerm = (ApplicationTerm) simplifiedDividend;
				final ApplicationTerm divisorAppTerm = (ApplicationTerm) simplifiedDivisor;
				final Pair<Term, Term> simplifiedPair =
						QvasrAbstractor.reduceRealDivision(script, dividendAppTerm, divisorAppTerm);
				simplifiedDividend = simplifiedPair.getFirst();
				simplifiedDivisor = simplifiedPair.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
		}

		if (!(simplifiedDividend instanceof ApplicationTerm) && simplifiedDivisor instanceof ApplicationTerm) {
			final ApplicationTerm divisorAppTerm = (ApplicationTerm) simplifiedDivisor;

			/*
			 * Simplify multiplications, where dividend is a term variable or constant term
			 */
			if (divisorAppTerm.getFunction().getName().equals("*")) {
				final Set<Term> simplifiedDivisorParamSet =
						new HashSet<>(Arrays.asList(divisorAppTerm.getParameters()));
				for (final Term divisorAppTermParam : divisorAppTerm.getParameters()) {
					if (SmtUtils.areFormulasEquivalent(simplifiedDividend, divisorAppTerm, script.getScript())) {
						simplifiedDividend = one;
						simplifiedDivisorParamSet.remove(divisorAppTermParam);
						break;
					}
				}
				final Term[] divisorArray =
						simplifiedDivisorParamSet.toArray(new Term[simplifiedDivisorParamSet.size()]);
				simplifiedDivisor = SmtUtils.mul(script.getScript(), "*", divisorArray);
				final Pair<Term, Term> reduced =
						QvasrAbstractor.reduceRealDivision(script, simplifiedDividend, simplifiedDivisor);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
			if (divisorAppTerm.getFunction().getName().equals("/")) {
				final Term divisorDividend = divisorAppTerm.getParameters()[0];
				final Term divisorDivisor = divisorAppTerm.getParameters()[1];
				final Pair<Term, Term> reduced = reduceRealDivision(script,
						SmtUtils.mul(script.getScript(), "*", simplifiedDividend, divisorDivisor), divisorDividend);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
		}

		if (simplifiedDividend instanceof ApplicationTerm && !(simplifiedDivisor instanceof ApplicationTerm)) {
			final ApplicationTerm dividendAppTerm = (ApplicationTerm) simplifiedDividend;

			/*
			 * Simplify multiplications, where dividend is a term variable or constant term
			 */
			if (dividendAppTerm.getFunction().getName().equals("*")) {
				final Set<Term> simplifiedDividendParamSet =
						new HashSet<>(Arrays.asList(dividendAppTerm.getParameters()));
				for (final Term divisorAppTermParam : dividendAppTerm.getParameters()) {
					if (SmtUtils.areFormulasEquivalent(simplifiedDividend, dividendAppTerm, script.getScript())) {
						simplifiedDividendParamSet.remove(divisorAppTermParam);
						break;
					}
				}
				final Term[] dividendArray =
						simplifiedDividendParamSet.toArray(new Term[simplifiedDividendParamSet.size()]);
				simplifiedDivisor = SmtUtils.mul(script.getScript(), "*", dividendArray);
				final Pair<Term, Term> reduced =
						QvasrAbstractor.reduceRealDivision(script, simplifiedDividend, simplifiedDivisor);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
			if (dividendAppTerm.getFunction().getName().equals("/")) {
				final Term dividendDividend = dividendAppTerm.getParameters()[0];
				final Term dividendDivisor = dividendAppTerm.getParameters()[1];
				final Pair<Term, Term> reduced = reduceRealDivision(script,
						SmtUtils.mul(script.getScript(), "*", simplifiedDividend, dividendDivisor), dividendDividend);
				simplifiedDividend = reduced.getFirst();
				simplifiedDivisor = reduced.getSecond();
				result = SmtUtils.divReal(script.getScript(), simplifiedDividend, simplifiedDivisor);
			}
		}
		return result;
	}

	/**
	 * Simplify a multiplication between two reals.
	 *
	 * @param script
	 * @param factorOne
	 * @param factorTwo
	 * @return
	 */
	public static Term simplifyRealMultiplication(final ManagedScript script, final Term factorOne,
			final Term factorTwo) {
		Term result = SmtUtils.mul(script.getScript(), "*", factorOne, factorTwo);
		final Term zero = script.getScript().decimal("0");
		final Term one = script.getScript().decimal("1");

		if (SmtUtils.areFormulasEquivalent(factorOne, zero, script.getScript())
				|| SmtUtils.areFormulasEquivalent(factorTwo, zero, script.getScript())) {
			return zero;
		}

		if (SmtUtils.areFormulasEquivalent(factorOne, one, script.getScript())) {
			return factorTwo;
		}

		if (SmtUtils.areFormulasEquivalent(factorTwo, one, script.getScript())) {
			return factorOne;
		}

		if (factorOne instanceof ApplicationTerm && factorTwo instanceof ApplicationTerm) {
			final ApplicationTerm factorOneAppTerm = (ApplicationTerm) factorOne;
			final ApplicationTerm factorTwoAppTerm = (ApplicationTerm) factorTwo;
			if (factorOneAppTerm.getFunction().getName().equals("/")
					&& factorTwoAppTerm.getFunction().getName().equals("/")) {
				final Term commonDivisor = script.getScript().term("*", factorOneAppTerm.getParameters()[1],
						factorTwoAppTerm.getParameters()[1]);
				final Term commonDividend = script.getScript().term("*", factorOneAppTerm.getParameters()[0],
						factorTwoAppTerm.getParameters()[0]);
				result = QvasrAbstractor.simplifyRealDivision(script, commonDividend, commonDivisor);
			}
			if (!factorOneAppTerm.getFunction().getName().equals("/")
					&& factorTwoAppTerm.getFunction().getName().equals("/")) {
				final Term commonDividend =
						SmtUtils.mul(script.getScript(), "*", factorOneAppTerm, factorTwoAppTerm.getParameters()[0]);
				result = QvasrAbstractor.simplifyRealDivision(script, commonDividend,
						factorTwoAppTerm.getParameters()[1]);
			}
			if (factorOneAppTerm.getFunction().getName().equals("/")
					&& !factorTwoAppTerm.getFunction().getName().equals("/")) {
				final Term commonDividend =
						SmtUtils.mul(script.getScript(), "*", factorOneAppTerm.getParameters()[0], factorTwoAppTerm);
				result = QvasrAbstractor.simplifyRealDivision(script, commonDividend,
						factorOneAppTerm.getParameters()[1]);
			}
		}

		if (!(factorOne instanceof ApplicationTerm) && factorTwo instanceof ApplicationTerm) {
			final ApplicationTerm factorTwoAppTerm = (ApplicationTerm) factorTwo;
			if (factorTwoAppTerm.getFunction().getName().equals("/")) {
				final Term commonDividend =
						SmtUtils.mul(script.getScript(), "*", factorOne, factorTwoAppTerm.getParameters()[0]);
				final Pair<Term, Term> reduced =
						reduceRealDivision(script, commonDividend, factorTwoAppTerm.getParameters()[1]);
				result = SmtUtils.divReal(script.getScript(), reduced.getFirst(), reduced.getSecond());
			}
		}

		if (factorOne instanceof ApplicationTerm && !(factorTwo instanceof ApplicationTerm)) {
			final ApplicationTerm factorOneAppTerm = (ApplicationTerm) factorOne;
			if (factorOneAppTerm.getFunction().getName().equals("/")) {
				final Term commonDividend =
						SmtUtils.mul(script.getScript(), "*", factorTwo, factorOneAppTerm.getParameters()[0]);
				final Pair<Term, Term> reduced =
						reduceRealDivision(script, commonDividend, factorOneAppTerm.getParameters()[1]);
				result = SmtUtils.divReal(script.getScript(), reduced.getFirst(), reduced.getSecond());
			}
		}

		if (factorOne instanceof ApplicationTerm && (factorTwo instanceof ApplicationTerm)) {

		}
		return result;
	}

	/**
	 * Simplify differences where either the minuend or subtrahend is a division, or only one of them.
	 *
	 * @param minuend
	 * @param subtrahend
	 * @return
	 */
	public static Term simplifyRealSubtraction(final ManagedScript script, final Term minuend, final Term subtrahend) {
		Term result = SmtUtils.minus(script.getScript(), minuend, subtrahend);
		if (subtrahend instanceof ApplicationTerm && minuend instanceof ApplicationTerm) {
			final ApplicationTerm appTermSubrahend = (ApplicationTerm) subtrahend;
			final ApplicationTerm appTermMinuend = (ApplicationTerm) minuend;
			if (appTermSubrahend.getFunction().getName().equals("/")) {
				Term simplifiedMinuend;
				final Term dividentSubtrahend = appTermSubrahend.getParameters()[0];
				final Term divisorSubtrahend = appTermSubrahend.getParameters()[1];
				if (!appTermMinuend.getFunction().getName().equals("/")) {
					final List<Term> paramsMinuend = getApplicationTermMultiplicationParams(script, appTermMinuend);
					paramsMinuend.addAll(getApplicationTermMultiplicationParams(script, divisorSubtrahend));
					final List<Term> paramsDividentSubtrahend =
							getApplicationTermMultiplicationParams(script, dividentSubtrahend);
					final Term divSubMul = expandRealMultiplication(script, paramsMinuend);
					final Term minSubMul = expandRealMultiplication(script, paramsDividentSubtrahend);
					simplifiedMinuend = SmtUtils.minus(script.getScript(), divSubMul, minSubMul);
					result = QvasrAbstractor.simplifyRealDivision(script, simplifiedMinuend, divisorSubtrahend);
					result.toStringDirect();
				} else {
					final Term dividentMinuend = appTermMinuend.getParameters()[0];
					final Term divisorMinuend = appTermMinuend.getParameters()[1];
					if (SmtUtils.areFormulasEquivalent(divisorSubtrahend, divisorMinuend, script.getScript())) {
						final Term subMinuendSubtrahend =
								SmtUtils.minus(script.getScript(), dividentMinuend, dividentSubtrahend);
						result = QvasrAbstractor.simplifyRealDivision(script, subMinuendSubtrahend, divisorMinuend);
					} else {
						final Term commonDenominator =
								QvasrAbstractor.expandRealMultiplication(script, divisorMinuend, divisorSubtrahend);
						final Term commonDenominatorDividentMinuend =
								QvasrAbstractor.expandRealMultiplication(script, dividentMinuend, divisorSubtrahend);
						final Term commonDenominatorDividentSubtrahend =
								QvasrAbstractor.expandRealMultiplication(script, dividentSubtrahend, divisorMinuend);
						final Term commonDenominatorSub = SmtUtils.minus(script.getScript(),
								commonDenominatorDividentMinuend, commonDenominatorDividentSubtrahend);
						result = QvasrAbstractor.simplifyRealDivision(script, commonDenominatorSub, commonDenominator);
					}
				}
			}
		}

		if (!(subtrahend instanceof ApplicationTerm) && minuend instanceof ApplicationTerm) {
			final ApplicationTerm appTermMinuend = (ApplicationTerm) minuend;
			if (appTermMinuend.getFunction().getName().equals("/")) {
				final Term simplifiedMinuend;
				final Term dividentMinuend = appTermMinuend.getParameters()[0];
				final Term divisorMinuend = appTermMinuend.getParameters()[1];
				final Term commonDenominatorMinuend = expandRealMultiplication(script, subtrahend, divisorMinuend);
				final List<Term> paramsDividentSubtrahend =
						getApplicationTermMultiplicationParams(script, dividentMinuend);
				final Term commonDenominatorSubtrahend = expandRealMultiplication(script, paramsDividentSubtrahend);
				simplifiedMinuend =
						SmtUtils.minus(script.getScript(), commonDenominatorMinuend, commonDenominatorSubtrahend);
				result = QvasrAbstractor.simplifyRealDivision(script, simplifiedMinuend, divisorMinuend);
				result.toStringDirect();

			}
		}
		if (subtrahend instanceof ApplicationTerm && !(minuend instanceof ApplicationTerm)) {
			final ApplicationTerm appTermSubrahend = (ApplicationTerm) subtrahend;
			if (appTermSubrahend.getFunction().getName().equals("/")) {
				final Term simplifiedMinuend;
				final Term dividentSubtrahend = appTermSubrahend.getParameters()[0];
				final Term divisorSubtrahend = appTermSubrahend.getParameters()[1];
				final Term commonDenominatorMinuend = expandRealMultiplication(script, minuend, divisorSubtrahend);
				final List<Term> paramsDividentSubtrahend =
						getApplicationTermMultiplicationParams(script, dividentSubtrahend);
				final Term commonDenominatorSubtrahend = expandRealMultiplication(script, paramsDividentSubtrahend);
				simplifiedMinuend =
						SmtUtils.minus(script.getScript(), commonDenominatorMinuend, commonDenominatorSubtrahend);

				result = QvasrAbstractor.simplifyRealDivision(script, simplifiedMinuend, divisorSubtrahend);
			}
		}
		return result;
	}

	/**
	 * Reduces a division of dividend / divisor.
	 *
	 * @param script
	 * @param dividend
	 * @param divisor
	 * @return
	 */
	public static Pair<Term, Term> reduceRealDivision(final ManagedScript script, final Term dividend,
			final Term divisor) {
		final Term one = script.getScript().decimal("1");
		Term simplifiedDividend = dividend;
		Term simplifiedDivisor = divisor;
		while (true) {
			final Term simplifiedDividendPre = simplifiedDividend;
			if (simplifiedDividend instanceof ApplicationTerm && simplifiedDivisor instanceof ApplicationTerm) {
				final ApplicationTerm dividendAppTerm = (ApplicationTerm) simplifiedDividend;
				final ApplicationTerm divisorAppTerm = (ApplicationTerm) simplifiedDivisor;
				if (dividendAppTerm.getFunction().getName().equals("*")
						&& divisorAppTerm.getFunction().getName().equals("*")) {
					final List<Term> paramsDividend = getApplicationTermMultiplicationParams(script, dividendAppTerm);
					final List<Term> paramsDivisor = getApplicationTermMultiplicationParams(script, divisorAppTerm);
					final Set<Term> reducedParamsDividend = new HashSet<>(paramsDividend);
					final Set<Term> reducedParamsDivisor = new HashSet<>(paramsDivisor);
					for (final Term dividendFactor : paramsDividend) {
						for (final Term divisorFactor : paramsDivisor) {
							if (SmtUtils.areFormulasEquivalent(dividendFactor, divisorFactor, script.getScript())) {
								reducedParamsDividend.remove(dividendFactor);
								reducedParamsDivisor.remove(divisorFactor);
							}
						}
					}
					final Term[] dividendArray = reducedParamsDividend.toArray(new Term[reducedParamsDividend.size()]);
					final Term[] divisorArray = reducedParamsDivisor.toArray(new Term[reducedParamsDivisor.size()]);
					simplifiedDividend = SmtUtils.mul(script.getScript(), "*", dividendArray);
					simplifiedDivisor = SmtUtils.mul(script.getScript(), "*", divisorArray);
				}
			}
			if (simplifiedDividend instanceof ApplicationTerm && !(simplifiedDivisor instanceof ApplicationTerm)) {
				final ApplicationTerm dividendAppTerm = (ApplicationTerm) simplifiedDividend;
				if (dividendAppTerm.getFunction().getName().equals("*")) {
					final Set<Term> simplifiedDividendParamSet =
							new HashSet<>(Arrays.asList(dividendAppTerm.getParameters()));
					for (final Term dividendFactor : dividendAppTerm.getParameters()) {
						if (SmtUtils.areFormulasEquivalent(dividendFactor, simplifiedDivisor, script.getScript())) {
							simplifiedDividendParamSet.remove(dividendFactor);
						}
					}
					final Term[] dividendArray =
							simplifiedDividendParamSet.toArray(new Term[simplifiedDividendParamSet.size()]);
					simplifiedDividend = SmtUtils.mul(script.getScript(), "*", dividendArray);
					simplifiedDivisor = one;
				}
			}
			if (!(simplifiedDividend instanceof ApplicationTerm) && simplifiedDivisor instanceof ApplicationTerm) {
				final ApplicationTerm divisorAppTerm = (ApplicationTerm) simplifiedDivisor;
				if (divisorAppTerm.getFunction().getName().equals("*")) {
					final Set<Term> simplifiedDivisorParamSet =
							new HashSet<>(Arrays.asList(divisorAppTerm.getParameters()));
					for (final Term divisorFactor : divisorAppTerm.getParameters()) {
						if (SmtUtils.areFormulasEquivalent(divisorFactor, simplifiedDivisor, script.getScript())) {
							simplifiedDivisorParamSet.remove(divisorFactor);
						}
					}
					final Term[] dividendArray =
							simplifiedDivisorParamSet.toArray(new Term[simplifiedDivisorParamSet.size()]);
					simplifiedDivisor = SmtUtils.mul(script.getScript(), "*", dividendArray);
					simplifiedDividend = one;
				}
			}
			if (SmtUtils.areFormulasEquivalent(simplifiedDividendPre, simplifiedDividend, script.getScript())) {
				break;
			}
		}
		return new Pair<>(simplifiedDividend, simplifiedDivisor);
	}

	public static List<Term> getApplicationTermMultiplicationParams(final ManagedScript script, final Term appTerm) {
		final List<Term> params = new ArrayList<>();
		if (appTerm instanceof ApplicationTerm) {
			if (((ApplicationTerm) appTerm).getFunction().getName().equals("*")) {
				for (final Term param : ((ApplicationTerm) appTerm).getParameters()) {
					if (param instanceof ApplicationTerm
							&& ((ApplicationTerm) param).getFunction().getName().equals("*")) {
						params.addAll(getApplicationTermMultiplicationParams(script, param));
					} else {
						params.add(param);
					}
				}
			} else {
				params.add(appTerm);
			}
		} else

		{
			params.add(appTerm);
		}
		return params;
	}

	public static List<Term> getApplicationTermSumParams(final ApplicationTerm appTerm) {
		final List<Term> params = new ArrayList<>();
		for (final Term param : appTerm.getParameters()) {
			if (param instanceof ApplicationTerm && ((ApplicationTerm) param).getFunction().getName().equals("+")) {
				params.addAll(getApplicationTermSumParams((ApplicationTerm) param));
			} else {
				params.add(param);
			}
		}
		return params;
	}

	/**
	 * Find a column to use as pivot in the gaussian elimination algorithm.
	 *
	 * @param matrix
	 * @param col
	 * @return
	 */
	private int findPivot(final Term[][] matrix, final int col) {
		int maxRow = -1;
		for (int row = col; row < matrix.length; row++) {
			if (!SmtUtils.areFormulasEquivalent(matrix[row][col], mZero, mScript.getScript())) {
				maxRow = row;
				break;
			}
		}
		return maxRow;
	}

	/**
	 * Swap two rows in a matrix.
	 *
	 * @param matrix
	 * @param col
	 * @param row
	 * @return
	 */
	private static Term[][] swap(final Term[][] matrix, final int col, final int row) {
		Term temp;
		for (int i = col; i < matrix[col].length; i++) {
			temp = matrix[col][i];
			matrix[col][i] = matrix[row][i];
			matrix[row][i] = temp;
		}
		return matrix;
	}

	/**
	 * Find Updates to variables in the transition formula. Needed to construct a linear system of equations to compute
	 * bases of resets and increments for the Q-Vasr-abstraction.
	 *
	 * @param transitionTerm
	 * @param outVariables
	 * @return
	 */
	private Map<Term, Term> getUpdates(final UnmodifiableTransFormula transitionFormula, final BaseType baseType) {
		final Map<Term, Term> assignments = new HashMap<>();
		final Map<Term, Term> realTvs = new HashMap<>();
		final HashMap<IProgramVar, Term> realUpdates = new HashMap<>();
		final SimultaneousUpdate su;
		try {
			su = SimultaneousUpdate.fromTransFormula(transitionFormula, mScript);
		} catch (final Exception e) {
			throw new UnsupportedOperationException("Could not compute Simultaneous Update!");
		}
		/*
		 * Create a new real sort termvariable.
		 */
		final Map<IProgramVar, Term> updates = su.getDeterministicAssignment();
		for (final IProgramVar pv : updates.keySet()) {
			realTvs.put(pv.getTermVariable(), mScript.constructFreshTermVariable(pv.getGloballyUniqueId() + "_real",
					SmtSortUtils.getRealSort(mScript)));
		}
		/*
		 * Transform the updates to variables to real sort.
		 */
		final SubstitutionWithLocalSimplification subTv = new SubstitutionWithLocalSimplification(mScript, realTvs);
		for (final Entry<IProgramVar, Term> update : updates.entrySet()) {
			final Term intUpdate = update.getValue();
			final Term realUpdate = subTv.transform(intUpdate);
			realUpdates.put(update.getKey(), realUpdate);
		}
		for (final Entry<IProgramVar, Term> varUpdate : realUpdates.entrySet()) {
			final IProgramVar progVar = varUpdate.getKey();
			final Term varUpdateTerm = varUpdate.getValue();
			final HashMap<Term, Term> subMappingTerm = new HashMap<>();
			Term realTerm;
			if (varUpdateTerm instanceof ApplicationTerm) {
				final ApplicationTerm varUpdateAppterm = (ApplicationTerm) varUpdateTerm;
				subMappingTerm.putAll(appTermToReal(varUpdateAppterm));
				final SubstitutionWithLocalSimplification subTerm =
						new SubstitutionWithLocalSimplification(mScript, subMappingTerm);
				realTerm = subTerm.transform(varUpdateAppterm);
			} else if (varUpdateTerm instanceof ConstantTerm) {
				final Rational value = SmtUtils.toRational((ConstantTerm) varUpdateTerm);
				realTerm = value.toTerm(SmtSortUtils.getRealSort(mScript));
			} else {
				realTerm = realTvs.get(progVar.getTermVariable());
			}
			if (baseType == BaseType.ADDITIONS) {
				final Term realVar = realTvs.get(progVar.getTermVariable());
				realTerm = SmtUtils.minus(mScript.getScript(), realTerm, realVar);
			}
			assignments.put(realTvs.get(progVar.getTermVariable()), realTerm);
		}
		return assignments;
	}

	/**
	 * Converts a give application term to real sort.
	 *
	 * @param appTerm
	 * @return
	 */
	private Map<Term, Term> appTermToReal(final ApplicationTerm appTerm) {
		final Map<Term, Term> subMap = new HashMap<>();
		for (final Term param : appTerm.getParameters()) {
			if (param.getSort() == SmtSortUtils.getRealSort(mScript)) {
				continue;
			}
			if (param instanceof ConstantTerm) {
				final ConstantTerm paramConst = (ConstantTerm) param;
				final Rational paramValue = (Rational) paramConst.getValue();
				subMap.put(param, paramValue.toTerm(SmtSortUtils.getRealSort(mScript)));
			} else {
				subMap.putAll(appTermToReal((ApplicationTerm) param));
			}
		}
		return subMap;
	}

	/**
	 * Construct a matrix representing a set of linear equations that model updates to variables in a given transition
	 * formula. The matrix has 2^n columns with n being the number of outvars, because we have to set each variable to 0
	 * to be able to use Gaussian elimination. We want to have a matrix for the bases of resets Res: {[s_1, s_2, ...,
	 * s_n] [x_1', x_2', ...] = a} and additions Inc: {[s_1, s_2, ..., s_n] [x_1', x_2', ...] = [s_1, s_2, ..., s_n]
	 * [x_1, x_2, ..., x_n] + a}
	 *
	 * @param updates
	 * @param transitionFormula
	 * @param typeOfBase
	 * @return
	 */
	private Term[][] constructBaseMatrix(final Map<Term, Term> updates,
			final UnmodifiableTransFormula transitionFormula) {
		final int columnDimension = transitionFormula.getOutVars().size();

		final Set<Set<Term>> setToZero = new HashSet<>();
		final Map<Term, Term> intToReal = new HashMap<>();
		for (final Term tv : updates.keySet()) {
			final Set<Term> inVar = new HashSet<>();
			inVar.add(tv);
			setToZero.add(inVar);
		}
		/*
		 * To get a linear set of equations, which we want to solve, we set the various variables to 0.
		 */
		Set<Set<Term>> powerset = new HashSet<>(setToZero);
		for (final Set<Term> inTv : setToZero) {
			powerset = QvasrUtils.joinSet(powerset, inTv);
		}
		final Term[][] baseMatrix = new Term[powerset.size() + 1][columnDimension + 1];

		final Deque<Set<Term>> zeroStack = new HashDeque<>();
		zeroStack.addAll(powerset);
		int j = 0;
		final TermVariable a = mScript.constructFreshTermVariable("a", SmtSortUtils.getRealSort(mScript));
		while (!zeroStack.isEmpty()) {
			int i = 0;
			baseMatrix[j][columnDimension] = a;
			final Map<Term, Term> subMapping = new HashMap<>();
			if (j > 0) {
				final Set<Term> toBeSetZero = zeroStack.pop();
				for (final Term tv : toBeSetZero) {
					subMapping.put(tv, mZero);
				}
			}
			for (final Entry<Term, Term> update : updates.entrySet()) {
				final Term updateTerm = update.getValue();
				Term toBeUpdated;
				final SubstitutionWithLocalSimplification sub =
						new SubstitutionWithLocalSimplification(mScript, subMapping);
				toBeUpdated = sub.transform(updateTerm);
				final SubstitutionWithLocalSimplification subReal =
						new SubstitutionWithLocalSimplification(mScript, intToReal);
				final Term toBeUpdatedReal = subReal.transform(toBeUpdated);

				baseMatrix[j][i] = toBeUpdatedReal;

				i++;
			}
			j++;
		}
		return baseMatrix;
	}

	/**
	 * Construct a formula modeling updates to variables done in a transition formula in relation to a new termvariable
	 * s. (May not be needed, as we can skip this construction)
	 *
	 * @param updates
	 * @param transitionFormula
	 * @param typeOfBase
	 * @return
	 */
	private Term constructBaseFormula(final Map<TermVariable, Set<Term>> updates,
			final UnmodifiableTransFormula transitionFormula, final BaseType baseType) {
		int sCount = 0;
		final Set<Term> newUpdates = new HashSet<>();
		for (final var variableUpdate : updates.entrySet()) {
			final TermVariable s = mScript.constructFreshTermVariable("s" + sCount, SmtSortUtils.getRealSort(mScript));
			for (final Term update : variableUpdate.getValue()) {
				final Term mult = SmtUtils.mul(mScript.getScript(), "*", s, update);
				newUpdates.add(mult);
			}
			sCount++;
		}
		Term addition = mScript.getScript().decimal("1");
		for (final Term update : newUpdates) {
			addition = SmtUtils.sum(mScript.getScript(), "+", addition, update);
		}
		addition = SmtUtils.equality(mScript.getScript(), addition,
				mScript.constructFreshTermVariable("a", SmtSortUtils.getRealSort(mScript)));
		return addition;
	}

	/**
	 * Print the given matrix in readable form.
	 *
	 * @param matrix
	 */
	private void printMatrix(final Term[][] matrix) {
		if (mLogger.isDebugEnabled()) {
			mLogger.debug("Matrix: ");
			for (int i = 0; i < matrix.length; i++) {
				mLogger.debug(Arrays.toString(matrix[i]));
			}
		}
	}

}
