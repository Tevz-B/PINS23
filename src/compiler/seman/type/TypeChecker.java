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

    public TypeChecker(NodeDescription<Def> definitions, NodeDescription<Type> types) {
        requireNonNull(definitions, types);
        this.definitions = definitions;
        this.types = types;
    }

    @Override
    public void visit(Call call) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
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
                if (l.equals(r) && l.isAtom())
                    binType = l;
                else
                    err_typ(binary, "Assign expression error - (not structuraly equal).", l, r);
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
                    err_typ(binary, "Comparrisson expression error.", l, r);
                break;
        }
        types.store(binType, binary);
    }

    @Override
    public void visit(Block block) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(For forLoop) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Name name) {
        var def = definitions.valueFor(name).get();
        types.store(types.valueFor(def).get(), name);
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(While whileLoop) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Where where) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
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
        funDef.body.accept(this);
        if (!returnType.equals(types.valueFor(funDef.body).get()))
            err(funDef, "Function body does not match return type");
        var funType = new Type.Function(paramTypes, returnType);
        types.store(funType, funDef);
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
        typeDef.accept(this); // izračunaj STRUKT tip za TypeDef (typDef -> TypName ->typDef -> TypName -> ... -> AtomType/ArrType)
        types.store(types.valueFor(typeDef).get(), name); // shrani strukturni tip za TypeName
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
