/**
 * @ Author: turk
 * @ Description: Preverjanje tipov.
 */

package compiler.seman.type;

import static common.RequireNonNull.requireNonNull;

import java.util.ArrayList;
import java.util.List;

import common.Report;
import compiler.common.Visitor;
import compiler.parser.ast.Ast;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.*;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;
import compiler.seman.type.type.Type.Atom.Kind;

public class TypeChecker implements Visitor {
    /**
     * Opis vozlišč in njihovih definicij.
     */
    private final NodeDescription<Def> definitions;

    /**
     * Opis vozlišč, ki jim priredimo podatkovne tipe.
     */
    private NodeDescription<Type> types;

    private ArrayList<Def> cycleCatch;

    public TypeChecker(NodeDescription<Def> definitions, NodeDescription<Type> types) {
        requireNonNull(definitions, types);
        this.definitions = definitions;
        this.types = types;
        cycleCatch = new ArrayList<>();
    }

    @Override
    public void visit(Call call) {
        FunDef def = (FunDef) definitions.valueFor(call).get();
        if (!types.valueFor(def).isPresent())
            def.accept(this);
        var funType = types.valueFor(def).get().asFunction().get();
        if (call.arguments.size() != def.parameters.size())
            err(call, "Function call error - argument count mismatch.");
        for (int i = 0; i < call.arguments.size(); ++i) {
            var a = call.arguments.get(i);
            var pType = funType.parameters.get(i);
            a.accept(this);
            var aType = types.valueFor(a).get();
            if (!aType.equals(pType))
                err(call, "Function call error - " + (i+1) + ". argument is type '" + aType + "', but function parameter is type '" + pType + "'");
        }
        types.store(funType.returnType, call);
    }

    @Override
    public void visit(Binary binary) {
        binary.left.accept(this);
        binary.right.accept(this);
        var l = types.valueFor(binary.left).get();
        var r = types.valueFor(binary.right).get();
        Type binType = null;
        switch (binary.operator) {
            case ADD:
            case SUB:
            case MOD:                        
            case MUL:
            case DIV:
                if (l.isInt() && r.isInt())
                    binType = l;
                else
                    err_typ(binary, "Arithmetic expression error.", l, r);
                break;
            case AND:
            case OR:
                if (l.isLog() && r.isLog())
                    binType = l;
                else
                    err_typ(binary, "Logical expression error.", l, r);
                break;
            case ARR:
                if (l.isArray() && r.isInt())
                    binType = l.asArray().get().type;
                else
                    err_typ(binary, "Array expression error.", l, r);
                break;
            case ASSIGN:
                if (l.equals(r) && l.isAtom() && !l.isVoid())
                    binType = l;
                else
                    err_typ(binary, "Assign expression error - (type VOID or not structuraly equal).", l, r);
                break;
            case EQ:
            case GEQ:
            case GT:
            case LEQ:
            case LT:
            case NEQ:
                if (l.equals(r) && (l.isLog() || l.isInt()))
                    binType = new Type.Atom(Kind.LOG);
                else
                    err_typ(binary, "Comparrison expression error.", l, r);
                break;
        }
        types.store(binType, binary);
    }

    @Override
    public void visit(Block block) {
        Type type = null;
        for (int i = 0; i < block.expressions.size(); ++i) {
            var e = block.expressions.get(i);
            e.accept(this);
            if (i == block.expressions.size() - 1)
                type = types.valueFor(e).get();
        }
        types.store(type, block);
    }

    @Override
    public void visit(For forLoop) {
        forLoop.counter.accept(this);
        forLoop.low.accept(this);
        forLoop.high.accept(this);
        forLoop.step.accept(this);
        forLoop.body.accept(this);
        if (types.valueFor(forLoop.counter).get().isInt() &&
            types.valueFor(forLoop.low).get().isInt() && 
            types.valueFor(forLoop.high).get().isInt() &&
            types.valueFor(forLoop.step).get().isInt()
        )
            types.store(new Type.Atom(Kind.VOID), forLoop);
        else
            err(forLoop, "FOR loop error - counter, low, high or step are not all type INT");
    }

    @Override
    public void visit(Name name) {
        var def = definitions.valueFor(name).get();
        types.store(types.valueFor(def).get(), name);
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        ifThenElse.condition.accept(this);
        ifThenElse.thenExpression.accept(this);
        if (ifThenElse.elseExpression.isPresent())
            ifThenElse.elseExpression.get().accept(this);
        if (types.valueFor(ifThenElse.condition).get().isLog())
            types.store(new Type.Atom(Kind.VOID), ifThenElse);
        else
            err_typ(ifThenElse, "IF statement error - condition not logical type", types.valueFor(ifThenElse.condition).get(), null);
    }

    @Override
    public void visit(Literal literal) {
        switch (literal.type) {
            case INT:
                types.store(new Type.Atom(Kind.INT), literal);
                return;
            case LOG:
                types.store(new Type.Atom(Kind.LOG), literal);
                return;
            case STR:
                types.store(new Type.Atom(Kind.STR), literal);
                return;
        }
    }

    @Override
    public void visit(Unary unary) {
        unary.expr.accept(this);
        var type = types.valueFor(unary.expr).get();
        switch (unary.operator) {
            case ADD:
            case SUB:
                if (type.isInt())
                    types.store(type, unary);
                else
                    err_typ(unary, "Unary positive/negative error (+-expression)", type, null);
                break;
            case NOT:
                if (type.isLog())
                    types.store(type, unary);
                 else 
                    err_typ(unary, "Unary logical expression error (!expression)", type, null);
                break;
        }
    }

    @Override
    public void visit(While whileLoop) {
        whileLoop.condition.accept(this);
        whileLoop.body.accept(this);
        if (types.valueFor(whileLoop.condition).get().isLog())
            types.store(new Type.Atom(Kind.VOID), whileLoop);
        else 
            err_typ(whileLoop, "WHILE loop error - condition not logical type", types.valueFor(whileLoop.condition).get(), null);
    }

    @Override
    public void visit(Where where) {
        where.defs.accept(this);
        where.expr.accept(this);
        types.store(types.valueFor(where.expr).get(), where);
    }

    @Override
    public void visit(Defs defs) {
        for (Def d : defs.definitions)
            d.accept(this);
    }

    @Override
    public void visit(FunDef funDef) {
        funDef.type.accept(this);
        var returnType = types.valueFor(funDef.type).get();
        List<Type> paramTypes = new ArrayList<>();
        for (var p : funDef.parameters) {
            p.accept(this);
            paramTypes.add(types.valueFor(p).get());
        }
        var funType = new Type.Function(paramTypes, returnType);
        types.store(funType, funDef);
        funDef.body.accept(this);
        if (!returnType.equals(types.valueFor(funDef.body).get()))
            err(funDef, "Function body returns '" + types.valueFor(funDef.body).get() + "' and does not match return type '" + returnType + "'");
    }

    @Override
    public void visit(TypeDef typeDef) {
        typeDef.type.accept(this);
        types.store(types.valueFor(typeDef.type).get(), typeDef);
    }

    @Override
    public void visit(VarDef varDef) {
        varDef.type.accept(this);
        types.store( types.valueFor(varDef.type).get(), varDef);
    }

    @Override
    public void visit(Parameter parameter) {
        parameter.type.accept(this);
        types.store(types.valueFor(parameter.type).get(), parameter);
    }

    @Override
    public void visit(Array array) {
        array.type.accept(this);
        var arrayType = new Type.Array(array.size, types.valueFor(array.type).get());
        types.store(arrayType, array);
    }

    @Override
    public void visit(Atom atom) {
        Type.Atom type = null;
        switch (atom.type) {
            case INT:
                type = new Type.Atom( Kind.INT );
            break;
            case LOG:
                type = new Type.Atom( Kind.LOG );
            break;
            case STR:
                type = new Type.Atom( Kind.STR );
            break;
        }
        types.store(type, atom);
    }

    @Override
    public void visit(TypeName name) { // poglej v globino kateri tip je
        var typeDef = definitions.valueFor(name).get();
        if (cycleCatch.contains(typeDef))
            err(name, "Cyclic type definition"); // ujemi cikel
        cycleCatch.add(typeDef);
        typeDef.accept(this); // izračunaj STRUKT tip za TypeDef (typDef -> TypName ->typDef -> TypName -> ... -> AtomType/ArrType)
        types.store(types.valueFor(typeDef).get(), name); // shrani strukturni tip za TypeName
        cycleCatch.clear();
    }

    private void err(Ast node, String message) {
        Report.error(node.position, "Type Error: " + message);
    }

    private void err_typ(Ast node, String message, Type t1, Type t2) {
        if (t2 != null) 
            err(node, message + "\nTypes are: '" + t1.toString() + "' and '" + t2.toString() + "'");        
        else 
            err(node, message + "\nType is: '" + t1.toString() + "'"); 
    }
}
