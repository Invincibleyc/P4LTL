/*
 * Copyright (C) 2016 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2016 University of Freiburg
 *
 * This file is part of the ULTIMATE HeapSeparator plug-in.
 *
 * The ULTIMATE HeapSeparator plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE HeapSeparator plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE HeapSeparator plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE HeapSeparator plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE HeapSeparator plug-in grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.heapseparator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieNonOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.BoogieOldVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.boogie.LocalBoogieVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.DefaultIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.IIcfgSymbolTable;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.transformations.ReplacementVar;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.cfg.variables.IProgramVarOrConst;
import de.uni_freiburg.informatik.ultimate.modelcheckerutils.smt.managedscript.ManagedScript;
import de.uni_freiburg.informatik.ultimate.plugins.analysis.abstractinterpretationv2.domain.transformula.vp.EqNode;

public class NewArrayIdProvider {
	
	private final Map<IProgramVarOrConst, PartitionInformation> mArrayToPartitionInformation = new HashMap<>();
	private final DefaultIcfgSymbolTable mNewSymbolTable;

	private final ManagedScript mManagedScript;
	private final IIcfgSymbolTable mSymbolTable;
	public NewArrayIdProvider(ManagedScript ms, IIcfgSymbolTable iIcfgSymbolTable) {
		mManagedScript = ms;
		mSymbolTable = iIcfgSymbolTable;
		mNewSymbolTable = new DefaultIcfgSymbolTable((DefaultIcfgSymbolTable) iIcfgSymbolTable);
	}

	/**
	 * Return the partition id of the partitioned array belonging to originalArrayId at position indexVector
	 * @param originalArrayId
	 * @param indexVector
	 * @return
	 */
	public IProgramVarOrConst getNewArrayId(IProgramVarOrConst originalArrayId, List<EqNode> indexVector) {
		return mArrayToPartitionInformation.get(originalArrayId).getNewArray(indexVector);
	}

	public void registerEquivalenceClass(
			final IProgramVarOrConst arrayId, 
			final Set<EqNode> ec) {
		final IndexPartition indexPartition = new IndexPartition(arrayId, ec);

		PartitionInformation partitionInfo = mArrayToPartitionInformation.get(arrayId);
		if (partitionInfo == null) {
			partitionInfo = new PartitionInformation(arrayId, mManagedScript, mNewSymbolTable);
			mArrayToPartitionInformation.put(arrayId, partitionInfo);
		}
		partitionInfo.addPartition(indexPartition);
	}
}

/*
 * Represents a set of Array Indices which, with respect to a given array, may never alias with 
 * the indices in any other partition.
 */
class IndexPartition {
	final IProgramVarOrConst arrayId;
	final Set<EqNode> indices;

	public IndexPartition(IProgramVarOrConst arrayId, Set<EqNode> indices) {
		this.arrayId = arrayId;
		this.indices = indices;
	}
}

class PartitionInformation {
	private final IProgramVarOrConst arrayId;
	int versionCounter = 0;
	private final DefaultIcfgSymbolTable mNewSymbolTable;
	private final Set<IndexPartition> indexPartitions;
	
	final Map<IndexPartition, IProgramVarOrConst> indexPartitionToNewArrayId = new HashMap<>();
	
	private final Map<List<IndexPartition>, IProgramVarOrConst> partitionVectorToNewArrayId = new HashMap<>();
	
	private final Map<EqNode, IndexPartition> indexToIndexPartition = new HashMap<>();
	private final ManagedScript mManagedScript;
	
	public PartitionInformation(IProgramVarOrConst arrayId, ManagedScript mScript, DefaultIcfgSymbolTable newSymbolTable) {
		this.arrayId = arrayId;
		indexPartitions = new HashSet<>();
		mManagedScript = mScript;
		mNewSymbolTable = newSymbolTable;
	}
	
	public IProgramVarOrConst getNewArray(List<EqNode> indexVector) {
		List<IndexPartition> partitionVector = 
				indexVector.stream().map(eqNode -> indexToIndexPartition.get(eqNode)).collect(Collectors.toList());
		return getNewArrayIdForIndexPartitionVector(partitionVector);
	}

	private IProgramVarOrConst getNewArrayIdForIndexPartitionVector(List<IndexPartition> partitionVector) {
		IProgramVarOrConst result = partitionVectorToNewArrayId.get(partitionVector);
		if (result == null) {
			result = obtainFreshProgramVar(arrayId);
			partitionVectorToNewArrayId.put(partitionVector, arrayId);
		}
		return result;
	}

	void addPartition(IndexPartition ip) {
		indexPartitions.add(ip);
	}

	private int getFreshVersionIndex() {
		//TODO: a method seems overkill right now -- remove if nothing changes..
		return versionCounter++;
	}

	private IProgramVarOrConst obtainFreshProgramVar(IProgramVarOrConst originalArrayId) {
		// TODO: would it be a good idea to introduce something like ReplacementVar for this??
		
		IProgramVarOrConst freshVar = null;
		
		if (originalArrayId instanceof LocalBoogieVar) {
			LocalBoogieVar lbv = (LocalBoogieVar) originalArrayId;
			String newId = lbv.getIdentifier() + "_part_" + getFreshVersionIndex();
			TermVariable newTv = mManagedScript.constructFreshCopy(lbv.getTermVariable());
			ApplicationTerm newConst = (ApplicationTerm) mManagedScript.getScript().term(newId + "_const");
			ApplicationTerm newPrimedConst = (ApplicationTerm) mManagedScript.getScript().term(newId + "_const_primed");
			freshVar = new LocalBoogieVar(
					newId, 
					lbv.getProcedure(), 
					null, 
					newTv, 
					newConst, 
					newPrimedConst);
		} else if (originalArrayId instanceof BoogieNonOldVar) {
			assert false : "TODO: implement";
		} else if (originalArrayId instanceof BoogieOldVar) {
			assert false : "TODO: implement";
		} else if (originalArrayId instanceof ReplacementVar) {
			assert false : "TODO: implement";
		} else if (originalArrayId instanceof BoogieConst) {
			assert false : "TODO: implement";
		} else {
			assert false : "case missing --> add it?";
		}
		
		mNewSymbolTable.add(freshVar);
		return freshVar;
	}
}
