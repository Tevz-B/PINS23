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
        // TODO check if both integers
        var binType = types.valueFor(binary.right).get();
        types.store(binType, binary);
        // TODO other operators
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
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
        compiler.seman.type.type.Type.Atom type = null;
        switch (atom.type) {
            case INT:
                type = new compiler.seman.type.type.Type.Atom( Type.Atom.Kind.INT );
            break;
            case LOG:
                type = new compiler.seman.type.type.Type.Atom( Type.Atom.Kind.LOG );
            break;
            case STR:
                type = new compiler.seman.type.type.Type.Atom( Type.Atom.Kind.STR );
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
}
