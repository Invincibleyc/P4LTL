package de.uni_freiburg.informatik.ultimate.reqtotest;

import java.util.List;
import java.util.function.Function;

import de.uni_freiburg.informatik.ultimate.core.lib.models.ObjectContainer;
import de.uni_freiburg.informatik.ultimate.core.lib.observers.BaseObserver;
import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.results.IResult;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.IToolchainStorage;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.srparse.pattern.PatternType;
import de.uni_freiburg.informatik.ultimate.logic.Logics;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverMode;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.SolverBuilder.SolverSettings;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.reqtotest.graphtransformer.GraphToBoogie;
import de.uni_freiburg.informatik.ultimate.reqtotest.graphtransformer.ThreeValuedAuxVarGen;
import de.uni_freiburg.informatik.ultimate.reqtotest.req.ReqGuardGraph;
import de.uni_freiburg.informatik.ultimate.reqtotest.req.ReqSymbolTable;
import de.uni_freiburg.informatik.ultimate.reqtotest.req.ReqToDeclarations;
import de.uni_freiburg.informatik.ultimate.reqtotest.req.ReqToGraph;
import de.uni_freiburg.informatik.ultimate.reqtotest.testgenerator.CounterExampleToTest;

public class ReqToTestObserver extends BaseObserver{

	private final ILogger mLogger;
	private final IUltimateServiceProvider mServices;
	private IElement mBoogieAst;
	private final IToolchainStorage mStorage;
	private CounterExampleToTest mResultTransformer;
	
	private final ManagedScript mManagedScript;
	private final Script mScript;

	public ReqToTestObserver(final ILogger logger, final IUltimateServiceProvider services,
			final IToolchainStorage storage) {
		mLogger = logger;
		mServices = services;
		mStorage = storage;
		
		final SolverSettings settings = SolverBuilder.constructSolverSettings("", SolverMode.External_DefaultMode,
				false, SolverBuilder.COMMAND_Z3_NO_TIMEOUT, false, null);
		mScript = SolverBuilder.buildAndInitializeSolver(services, storage, SolverMode.External_DefaultMode, settings,
				false, false, Logics.ALL.toString(), "RtInconsistencySolver");

		mManagedScript = new ManagedScript(services, mScript);
		
	}

	@Override
	public boolean process(final IElement root) throws Throwable {
		if (!(root instanceof ObjectContainer)) {
			return false;
		}

		
		final List<PatternType> rawPatterns = ((ObjectContainer<List<PatternType>>) root).getValue();
		final ReqSymbolTable symbolTable = 
				new ReqToDeclarations(mLogger).initPatternToSymbolTable(rawPatterns);
		final ThreeValuedAuxVarGen threeValuedAuxVarGen = new ThreeValuedAuxVarGen(mLogger, mScript, symbolTable);
		final ReqToGraph reqToBuchi = new ReqToGraph(mLogger, mServices, mStorage, symbolTable, threeValuedAuxVarGen, mScript, mManagedScript);
		final List<ReqGuardGraph> automata = reqToBuchi.patternListToBuechi(rawPatterns);
		final GraphToBoogie graphToBoogie = new GraphToBoogie(mLogger, mServices, mStorage, symbolTable, threeValuedAuxVarGen, automata, mScript, mManagedScript);
		mBoogieAst = graphToBoogie.getAst();
		
		mResultTransformer = new CounterExampleToTest(mLogger, mServices, symbolTable);
		final Function<IResult, IResult> resultTransformer = mResultTransformer::convertCounterExampleToTest;
		mServices.getResultService().registerTransformer("CexToTest", resultTransformer);
		
		return false;
	}
	
	public IElement getAst() {
		return mBoogieAst;
	}


}