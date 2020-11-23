/*
 * Copyright (C) 2020 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2020 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 *
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.lib.smtlibutils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.FunctionSymbol;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;

/**
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 *
 */
public class TermContextTransformationEngine<C, R> {

	private final TermWalker<C, R> mTermWalker;
	private final ArrayDeque<Task> mStack;

	private TermContextTransformationEngine(final TermWalker<C, R> termWalker) {
		super();
		mTermWalker = termWalker;
		mStack = new ArrayDeque<>();
	}

	public static <C, R> R transform(final TermWalker<C, R> termWalker, final C initialContext, final Term term) {
		return new TermContextTransformationEngine<>(termWalker).transform(initialContext, term);
	}

	private R transform(final C context, final Term term) {
		final DescendResult dr = mTermWalker.convert(context, term);
		final Task initialTask = constructTaskForDescendResult(context, dr);
		if (initialTask instanceof TermContextTransformationEngine.AscendResultTask) {
			final AscendResultTask art = (TermContextTransformationEngine<C, R>.AscendResultTask) initialTask;
			return art.getResult();
		}
		mStack.push(initialTask);
		while (!mStack.isEmpty()) {
			// warning: doStep() might alter stack
			final Task newTask = mStack.peek().doStep();
			if (newTask instanceof TermContextTransformationEngine.AscendResultTask) {
				final AscendResultTask art = (TermContextTransformationEngine<C, R>.AscendResultTask) newTask;
				if (mStack.isEmpty()) {
					return art.getResult();
				} else {
					mStack.peek().integrateResult(art.getResult());
				}
			} else {
				mStack.push(newTask);
			}
		}
		throw new AssertionError("empty stack should habe caused return");
	}

	private abstract class Task {
		private final C mContext;

		public Task(final C context) {
			super();
			mContext = context;
		}

		abstract Task doStep();

		abstract void integrateResult(final R result);

	}

	private class AscendResultTask extends Task {
		public AscendResultTask(final C context, final R result) {
			super(context);
			mResult = result;
		}

		final R mResult;

		public R getResult() {
			return mResult;
		}

		@Override
		Task doStep() {
			throw new AssertionError();
		}

		@Override
		void integrateResult(final R result) {
			throw new AssertionError();
		}

	}

	private class ApplicationTermTask extends Task {
		int mNext;
		final ApplicationTerm mOriginal;
		final List<R> mResult;

		public ApplicationTermTask(final C context, final ApplicationTerm original) {
			super(context);
			mNext = 0;
			mOriginal = original;
			mResult = new ArrayList(original.getParameters().length);
		}

		@Override
		Task doStep() {
			final Task result;
			if (mNext == mOriginal.getParameters().length) {
				final R res = mTermWalker.constructResultForApplicationTerm(super.mContext, mOriginal, mResult);
				final Task old = mStack.pop();
				assert old == this;
				result = new AscendResultTask(super.mContext, res);
			} else {
				final ArrayList<Term> otherParams = new ArrayList<>(Arrays.asList(mOriginal.getParameters()));
				otherParams.remove(mNext);
				final C currentContext = mTermWalker.constructContextForApplicationTerm(super.mContext,
						mOriginal.getFunction(), otherParams);
				final DescendResult res = mTermWalker.convert(currentContext, mOriginal.getParameters()[mNext]);
				result = constructTaskForDescendResult(currentContext, res);
			}
			return result;
		}

		@Override
		void integrateResult(final R result) {
			assert (mNext < mOriginal.getParameters().length);
			mResult.add(result);
			mNext++;
			assert mNext == mResult.size();
		}
	}

	private Task constructTaskForDescendResult(final C currentContext, final DescendResult res) {
		final Task result;
		if (res instanceof IntermediateResultForDescend) {
			result = constructTask(currentContext, ((IntermediateResultForDescend) res).getIntermediateResult());
		} else if (res instanceof FinalResultForAscend) {
			result = new AscendResultTask(currentContext, ((FinalResultForAscend<R>) res).getFinalResult());
		} else {
			throw new AssertionError("unknown result " + res);
		}
		return result;
	}

	private class QuantifiedFormulaTask extends Task {
		private final QuantifiedFormula mOriginal;
		private R mResultSubformula;

		public QuantifiedFormulaTask(final C context, final QuantifiedFormula quantifiedFormula) {
			super(context);
			mOriginal = quantifiedFormula;
		}

		@Override
		Task doStep() {
			final Task result;
			if (mResultSubformula != null) {
				final R res = mTermWalker.constructResultForQuantifiedFormula(super.mContext, mOriginal, mResultSubformula);
				final Task old = mStack.pop();
				assert old == this;
				result = new AscendResultTask(super.mContext, res);
			} else {
				final C currentContext = mTermWalker.constructContextForQuantifiedFormula(super.mContext,
						mOriginal.getQuantifier(), mOriginal.getVariables());
				final DescendResult res = mTermWalker.convert(currentContext, mOriginal.getSubformula());
				result = constructTaskForDescendResult(currentContext, res);
			}
			return result;
		}

		@Override
		void integrateResult(final R result) {
			mResultSubformula = result;
		}

	}

	private Task constructTask(final C context, final Term term) {
		final Task result;
		if (term instanceof ApplicationTerm) {
			final ApplicationTerm appTerm = (ApplicationTerm) term;
			result = new ApplicationTermTask(context, appTerm);
		} else if (term instanceof QuantifiedFormula) {
			final QuantifiedFormula qf = (QuantifiedFormula) term;
			result = new QuantifiedFormulaTask(context, qf);
		} else {
			throw new AssertionError("unknown term " + term);
		}
		return result;
	}

	public abstract static class TermWalker<C, R> {

		abstract C constructContextForApplicationTerm(C context, FunctionSymbol symb, List<Term> otherParams);

		abstract C constructContextForQuantifiedFormula(C context, int quant, TermVariable[] vars);

		abstract DescendResult convert(final C context, final Term term);

		abstract R constructResultForApplicationTerm(C context, ApplicationTerm originalApplicationTerm,
				List<R> resultParams);

		abstract R constructResultForQuantifiedFormula(C context, QuantifiedFormula originalQuantifiedFormula,
				R resultSubformula);
	}

	public interface DescendResult {

	}

	public static class IntermediateResultForDescend implements DescendResult {
		private final Term mIntermediateResult;

		public IntermediateResultForDescend(final Term intermediateResult) {
			super();
			mIntermediateResult = intermediateResult;
		}

		public Term getIntermediateResult() {
			return mIntermediateResult;
		}

	}

	public static class FinalResultForAscend<R> implements DescendResult {
		private final R mFinalResult;

		public FinalResultForAscend(final R finalResult) {
			super();
			mFinalResult = finalResult;
		}

		public R getFinalResult() {
			return mFinalResult;
		}

	}

}
