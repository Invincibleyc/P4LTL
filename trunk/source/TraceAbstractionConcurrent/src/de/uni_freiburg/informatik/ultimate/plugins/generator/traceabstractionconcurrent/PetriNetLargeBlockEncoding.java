/*
 * Copyright (C) 2019 Elisabeth Schanno
 * Copyright (C) 2019 Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 * Copyright (C) 2019 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2019 University of Freiburg
 *
 * This file is part of the ULTIMATE TraceAbstractionConcurrent plug-in.
 *
 * The ULTIMATE TraceAbstractionConcurrent plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE TraceAbstractionConcurrent plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE TraceAbstractionConcurrent plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE TraceAbstractionConcurrent plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE TraceAbstractionConcurrent plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstractionconcurrent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.Set;

import de.uni_freiburg.informatik.ultimate.automata.AutomataLibraryServices;
import de.uni_freiburg.informatik.ultimate.automata.AutomataOperationCanceledException;
import de.uni_freiburg.informatik.ultimate.automata.nestedword.operations.minimization.parallel.Tuple;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.IPetriNet;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.ITransition;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.PetriNetNot1SafeException;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.netdatastructures.BoundedPetriNet;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.operations.CopySubnet;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.unfolding.BranchingProcess;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.unfolding.Condition;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.unfolding.Event;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.unfolding.FinitePrefix;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.unfolding.ICoRelation;
import de.uni_freiburg.informatik.ultimate.core.model.models.ModelUtils;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILogger;
import de.uni_freiburg.informatik.ultimate.core.model.services.ILoggingService;
import de.uni_freiburg.informatik.ultimate.core.model.services.IUltimateServiceProvider;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.CfgSmtToolkit;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.IcfgUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IIcfgTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgEdge;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgEdgeFactory;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgInternalTransition;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.structure.IcfgLocation;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.TransFormulaUtils;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.transitions.UnmodifiableTransFormula;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.cfg.variables.IProgramVar;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.SmtUtils.SimplificationTechnique;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.SmtUtils.XnfConversionTechnique;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.lib.modelcheckerutils.smt.predicates.IPredicate;
import de.uni_freiburg.informatik.ultimate.plugins.generator.rcfgbuilder.cfg.Summary;
import de.uni_freiburg.informatik.ultimate.plugins.generator.traceabstraction.Activator;
import de.uni_freiburg.informatik.ultimate.util.HashUtils;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.Triple;
/**
 * TODO: Documentation
 * 
 * @author Elisabeth Schanno
 * @author Dominik Klumpp (klumpp@informatik.uni-freiburg.de)
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 *
 */
public class PetriNetLargeBlockEncoding {

	private final ILogger mLogger;
	private final BoundedPetriNet<IIcfgTransition<?>, IPredicate> mResult;
	private final SimplificationTechnique simplification = SimplificationTechnique.NONE;
	private final XnfConversionTechnique conversion = XnfConversionTechnique.BDD_BASED;
	private final IcfgEdgeFactory mEdgeFactory;
	private final ManagedScript mManagedScript;
	private HashRelation<ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> mCoEnabledRelation;

	private final IUltimateServiceProvider mServices;
	public PetriNetLargeBlockEncoding(IUltimateServiceProvider services, CfgSmtToolkit cfgSmtToolkit,
			BoundedPetriNet<IIcfgTransition<?>, IPredicate> petriNet)
			throws AutomataOperationCanceledException, PetriNetNot1SafeException {
		mLogger = services.getLoggingService().getLogger(Activator.PLUGIN_ID);
		mServices = services;
		mManagedScript = cfgSmtToolkit.getManagedScript();
		mEdgeFactory = cfgSmtToolkit.getIcfgEdgeFactory();
		BoundedPetriNet<IIcfgTransition<?>, IPredicate> result1 = choiceRule(services, petriNet);
		for (int i = 0; i < 3; i++) {
		BoundedPetriNet<IIcfgTransition<?>, IPredicate> result2 = sequenceRule(services, result1);
		result1 = choiceRule(services, result2);
		}
		mResult = result1;
	}

	private BoundedPetriNet<IIcfgTransition<?>, IPredicate> choiceRule(IUltimateServiceProvider services, BoundedPetriNet<IIcfgTransition<?>, IPredicate> petriNet)
			throws AutomataOperationCanceledException, PetriNetNot1SafeException {
		Collection<ITransition<IIcfgTransition<?>, IPredicate>> transitions = petriNet.getTransitions();
		List<Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>>> meltingStack = new ArrayList<>();
		for (ITransition<IIcfgTransition<?>, IPredicate> t1 : transitions) {
			Set<IPredicate> t1PostSet = petriNet.getSuccessors(t1);
			for (IPredicate place : t1PostSet) {
				if (petriNet.getPredecessors(place).size() > 1) {
					Collection<ITransition<IIcfgTransition<?>, IPredicate>> placePreset = petriNet.getPredecessors(place);
					for (ITransition<IIcfgTransition<?>, IPredicate> t2 : placePreset) {
						if (t1.equals(t2)) {
							continue;
						}
						// Check if Pre- and Postset are identical for t1 and t2.
						if (petriNet.getPredecessors(t1).equals(petriNet.getPredecessors(t2)) && petriNet.getSuccessors(t1).equals(petriNet.getSuccessors(t2))) {
							List<IIcfgTransition<?>> IIcfgTransitionsToRemove = new ArrayList<>();
							IIcfgTransitionsToRemove.add(t1.getSymbol());
							IIcfgTransitionsToRemove.add(t2.getSymbol());
							IcfgEdge meltedIcfgEdge = constructParallelComposition(t1.getSymbol().getSource(), t2.getSymbol().getTarget(), IIcfgTransitionsToRemove);
							// Create new element of meltingStack.
							Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> element = 
									new Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>>(meltedIcfgEdge, t1, t2);
							meltingStack.add(element);
						}
					}
				}
			}
		}
		BoundedPetriNet<IIcfgTransition<?>, IPredicate> newNet = copyPetriNetWithModification(services, petriNet, meltingStack);
		return newNet;
	}

	
	private BoundedPetriNet<IIcfgTransition<?>, IPredicate> sequenceRule(IUltimateServiceProvider services, BoundedPetriNet<IIcfgTransition<?>, IPredicate> petriNet) 
			throws AutomataOperationCanceledException, PetriNetNot1SafeException {
		Collection<ITransition<IIcfgTransition<?>, IPredicate>> transitions = petriNet.getTransitions();
		List<Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>>> meltingStack = new ArrayList<>();
		for (ITransition<IIcfgTransition<?>, IPredicate> t1 : transitions) {
			Set<IPredicate> t1PostSet = petriNet.getSuccessors(t1);
			if (t1PostSet.size() == 1) {
				for (IPredicate state : t1PostSet) {
					if (petriNet.getPredecessors(state).size() == 1) {
						for (ITransition<IIcfgTransition<?>, IPredicate> t2 : petriNet.getSuccessors(state)) {
							if (petriNet.getPredecessors(t2).size() == 1 && petriNet.getSuccessors(state).size() == 1 && !petriNet.getSuccessors(t2).contains(state)) {
								boolean moverCheck = isMover(t1, t2, petriNet);
								if (moverCheck && onlyInternal(t1.getSymbol()) && onlyInternal(t2.getSymbol())) {
									mLogger.info("Element added to the stack.");
									IcfgEdge meltedIcfgEdge = constructSequentialComposition(t1.getSymbol().getSource(), t2.getSymbol().getTarget(), t1.getSymbol(), t2.getSymbol());
									//create new element of the meltingStack.
									Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> element = 
											new Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>>(meltedIcfgEdge, t1, t2);
									meltingStack.add(element);
									}
								}
							}
						}
					}
				}
			}
		BoundedPetriNet<IIcfgTransition<?>, IPredicate> newNet = copyPetriNetWithModification(services, petriNet, meltingStack);
		return newNet;
	}
	
	private BoundedPetriNet<IIcfgTransition<?>, IPredicate> copyPetriNetWithModification(IUltimateServiceProvider services,
			BoundedPetriNet<IIcfgTransition<?>,IPredicate> petriNet,
			List<Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>>> meltingStack)
					throws AutomataOperationCanceledException, PetriNetNot1SafeException {
		Set<IIcfgTransition<?>> newAlphabet = new HashSet<IIcfgTransition<?>>();
		//Collection<ITransition<IIcfgTransition<?>, IPredicate>> transitionsToKeep = petriNet.getTransitions();
		Collection<ITransition<IIcfgTransition<?>, IPredicate>> transitionsToKeep = new ArrayList<>();
		for (Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> triplet : meltingStack) {
			petriNet.getAlphabet().add(triplet.getFirst());
			petriNet.addTransition(triplet.getFirst(), petriNet.getPredecessors(triplet.getSecond()), petriNet.getSuccessors(triplet.getThird()));
		}
		for (ITransition<IIcfgTransition<?>, IPredicate> transition : petriNet.getTransitions()) {
			transitionsToKeep.add(transition);
		}
		for (Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> triplet : meltingStack) {
			newAlphabet.add(triplet.getFirst());
			transitionsToKeep.remove(triplet.getSecond());
			transitionsToKeep.remove(triplet.getThird());
		}
		Set<ITransition<IIcfgTransition<?>, IPredicate>> mySet = new HashSet<ITransition<IIcfgTransition<?>, IPredicate>>();
		mySet.addAll(transitionsToKeep);
		for (ITransition<IIcfgTransition<?>, IPredicate> transition : transitionsToKeep) {
			newAlphabet.add(transition.getSymbol());
		}
		BoundedPetriNet<IIcfgTransition<?>, IPredicate> newNet = CopySubnet.copy(new AutomataLibraryServices(services), petriNet,
				mySet, newAlphabet);
		// Add preset of transitionToRemove1 and postset of transitionToRemove2.
		for (Triple<IcfgEdge, ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> triplet : meltingStack) {
			for (IPredicate place : petriNet.getPredecessors(triplet.getSecond())) {
				if (!newNet.getPlaces().contains(place)) {
					newNet.addPlace(place, petriNet.getInitialPlaces().contains(place), petriNet.getAcceptingPlaces().contains(place));
				}
			}
		}
		BranchingProcess<IIcfgTransition<?>, IPredicate> bp2 = new FinitePrefix<>(new AutomataLibraryServices(services),
				newNet).getResult();
		mCoEnabledRelation = computeCoEnabledRelation(newNet, bp2);
		return newNet;
	}

	private HashRelation<ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> computeCoEnabledRelation(
			IPetriNet<IIcfgTransition<?>, IPredicate> net, BranchingProcess<IIcfgTransition<?>, IPredicate> bp) {
		HashRelation<ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> hashRelation = new HashRelation<>();
		ICoRelation<IIcfgTransition<?>, IPredicate> coRelation = bp.getCoRelation();
		Collection<Event<IIcfgTransition<?>, IPredicate>> events = bp.getEvents();
		for (Event<IIcfgTransition<?>, IPredicate> event1: events) {
			for (Event<IIcfgTransition<?>, IPredicate> event2 : events) {
				if (event1 == bp.getDummyRoot() || event2 == bp.getDummyRoot()) {
					continue;
				}
				boolean coEnabled = isInCoRelation(event1, event2, coRelation);
				if (coEnabled) {
					ITransition<IIcfgTransition<?>, IPredicate> transition1 = event1.getTransition();
					ITransition<IIcfgTransition<?>, IPredicate> transition2 = event2.getTransition();
					hashRelation.addPair(transition1, transition2);
					}
				}
		}
		return hashRelation;
	}


	boolean isInCoRelation(Event<IIcfgTransition<?>, IPredicate> e1, Event<IIcfgTransition<?>, IPredicate> e2,
			ICoRelation<IIcfgTransition<?>, IPredicate> coRelation) {
		 Set<Condition<IIcfgTransition<?>, IPredicate>> preSetE2 = e2.getPredecessorConditions();
		boolean coRel = false;
		for (Condition<IIcfgTransition<?>, IPredicate> condition : preSetE2) {
			if (coRelation.isInCoRelation(condition, e1)) {
				coRel = true;
			}
			else {
				coRel = false;
				break;
			}
		}
		return coRel;
	}

	public BoundedPetriNet<IIcfgTransition<?>, IPredicate> getResult() {
		return mResult;
	}


boolean isMover(ITransition<IIcfgTransition<?>, IPredicate> t1, ITransition<IIcfgTransition<?>, IPredicate> t2, IPetriNet<IIcfgTransition<?>, IPredicate> petriNet) {
	// Filter which elements of coEnabledRelation are relevant.
	List<ITransition<IIcfgTransition<?>, IPredicate>> coEnabledTransitions = new ArrayList<>();
	for (Entry<ITransition<IIcfgTransition<?>, IPredicate>, ITransition<IIcfgTransition<?>, IPredicate>> element : mCoEnabledRelation) {
		if (element.getKey() == t1) {
			coEnabledTransitions.add(element.getValue());
		}
		if (element.getValue() == t1) {
			coEnabledTransitions.add(element.getKey());
		}
	}
	if (coEnabledTransitions.size() == 0) {
		return true;
	}
	// Extract the modified and used variables of t1.
	Set<IProgramVar> modifiedVarsT1 = t1.getSymbol().getTransformula().getAssignedVars();
	Set<IProgramVar> usedVarsT1 = t1.getSymbol().getTransformula().getInVars().keySet();
	// Extract the modified and used variables of t2.
	Set<IProgramVar> modifiedVarsT2 = t2.getSymbol().getTransformula().getAssignedVars();
	Set<IProgramVar> usedVarsT2 = t2.getSymbol().getTransformula().getInVars().keySet();
	boolean check1 = true;
	boolean check2 = true;
	for (ITransition<IIcfgTransition<?>, IPredicate> t3: coEnabledTransitions) {
		// Filter all coEnabled elements t3 and extract the used and modified variables.
		Set<IProgramVar> modifiedVarsT3 = t3.getSymbol().getTransformula().getAssignedVars();
		Set<IProgramVar> usedVarsT3 = t3.getSymbol().getTransformula().getInVars().keySet();
		for (IProgramVar var : modifiedVarsT3) {
			if (usedVarsT1.contains(var) || modifiedVarsT1.contains(var)) {
				check1 = false;
			}
			if (usedVarsT2.contains(var) || modifiedVarsT2.contains(var)) {
				check2 = false;
			}
		}
		for (IProgramVar var : usedVarsT3) {
			if (modifiedVarsT1.contains(var) || check1 == false) {
				check1 = false;
			}
			if (modifiedVarsT2.contains(var) || check2 == false) {
				check2 = false;
				break;
				}
			}

	}
	if (!check1 && !check2) {	
		return false;
	}
	else {
		return true;
	}
}

// Methods from IcfgEdgeBuilder!
private static boolean onlyInternal(final IIcfgTransition<?> transition) {
	return transition instanceof IIcfgInternalTransition<?> && !(transition instanceof Summary);
}

private static boolean onlyInternal(final List<IIcfgTransition<?>> transitions) {
	return transitions.stream().allMatch(PetriNetLargeBlockEncoding::onlyInternal);
}

public IcfgEdge constructSequentialComposition(final IcfgLocation source, final IcfgLocation target,
		final IIcfgTransition<?> first, final IIcfgTransition<?> second) {
	final List<IIcfgTransition<?>> codeblocks = Arrays.asList(new IIcfgTransition<?>[] { first, second });
	return constructSequentialComposition(source, target, codeblocks, false, false);
}

private IcfgEdge constructSequentialComposition(final IcfgLocation source, final IcfgLocation target,
		final List<IIcfgTransition<?>> transitions, final boolean simplify, final boolean elimQuants) {
	assert onlyInternal(transitions) : "You cannot have calls or returns in normal sequential compositions";
	final List<UnmodifiableTransFormula> transFormulas =
			transitions.stream().map(IcfgUtils::getTransformula).collect(Collectors.toList());
	final UnmodifiableTransFormula tf = TransFormulaUtils.sequentialComposition(mLogger, mServices, mManagedScript,
			simplify, elimQuants, false, conversion, simplification, transFormulas);
	final IcfgInternalTransition rtr = mEdgeFactory.createInternalTransition(source, target, null, tf);
	ModelUtils.mergeAnnotations(transitions, rtr);
	return rtr;
}

public IcfgEdge constructParallelComposition(final IcfgLocation source, final IcfgLocation target,
		final List<IIcfgTransition<?>> transitions) {
	assert onlyInternal(transitions) : "You cannot have calls or returns in normal sequential compositions";
	final List<UnmodifiableTransFormula> transFormulas =
			transitions.stream().map(IcfgUtils::getTransformula).collect(Collectors.toList());
	final UnmodifiableTransFormula[] tfArray =
			transFormulas.toArray(new UnmodifiableTransFormula[transFormulas.size()]);
	final int serialNumber = HashUtils.hashHsieh(293, (Object[]) tfArray);
	final UnmodifiableTransFormula parallelTf = TransFormulaUtils.parallelComposition(mLogger, mServices,
			serialNumber, mManagedScript, null, false, conversion, tfArray);
	final IcfgInternalTransition rtr = mEdgeFactory.createInternalTransition(source, target, null, parallelTf);
	ModelUtils.mergeAnnotations(transitions, rtr);
	return rtr;
}

}
