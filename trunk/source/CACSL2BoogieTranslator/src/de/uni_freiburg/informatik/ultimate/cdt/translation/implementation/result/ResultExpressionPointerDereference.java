package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.result;

import java.util.ArrayList;
import java.util.Map;

import de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.CACSLLocation;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Declaration;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.Statement;
import de.uni_freiburg.informatik.ultimate.model.boogie.ast.VariableDeclaration;


/**
 * Result for Pointer Dereference where the pointer is stored explicitly. The 
 * pointer can be given either as base and offset or directly by the expression 
 * m_Pointer.  
 * If the points-to-type is not a primitive type the expr may be null.
 * Otherwise expr is an auxiliary variables which is the result of a read call.
 * The declaration of this auxiliary variable and the read call are also stored.
 * This allows you to removed both if you do not want to dereference the pointer, 
 * e.g. because it occurs on the left hand side of an assignment. 
 * A typical application is not only *p, but also a field reference p->f,
 * where the m_Pointer has the appropriate offset 
 * @author Matthias Heizmann
 *
 */
public class ResultExpressionPointerDereference extends ResultExpression {
	
	public final Expression m_Pointer;
	
	public final Statement m_ReadCall;
	public final VariableDeclaration m_CallResult;

	public ResultExpressionPointerDereference(ArrayList<Statement> stmt,
			Expression expr, ArrayList<Declaration> decl,
			Map<VariableDeclaration, CACSLLocation> auxVars, Expression pointer,
			Statement readCall, VariableDeclaration callResult) {
		super(stmt, expr, decl, auxVars);
		m_Pointer = pointer;
		m_ReadCall = readCall;
		m_CallResult = callResult;
	}

	/**
	 * Remove from this ResultExpression
	 * <ul>
	 * <li> the declaration of the auxiliary variable that will store the result
	 * value of the read call
	 * <li> the statement that represents the read call
	 * <li> the auxiliary variable from the super.auxVars mapping
	 *    
	 */
	public void removePointerDereference() {
		if (m_CallResult != null) {
			boolean removed;
			removed = stmt.remove(m_ReadCall);
			assert removed;
			removed = decl.remove(m_CallResult);
			assert removed;
			CACSLLocation value = auxVars.remove(m_CallResult);
			assert value != null;
		}
	}
	

}
