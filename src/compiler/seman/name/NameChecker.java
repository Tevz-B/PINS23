/**
 * @ Author: turk
 * @ Description: Preverjanje in razreševanje imen.
 */

package compiler.seman.name;

import static common.RequireNonNull.requireNonNull;

import common.Report;
import compiler.common.Visitor;
import compiler.lexer.Position;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.*;
import compiler.seman.common.NodeDescription;
import compiler.seman.name.env.SymbolTable;
import compiler.seman.name.env.SymbolTable.DefinitionAlreadyExistsException;

public class NameChecker implements Visitor {
    
    private NodeDescription<Def> definitions; // za izpis, klici ko se uporabi ID
    // puscica, 2. obhod

    /**
     * Simbolna tabela.
     */
    private SymbolTable symbolTable; // za preverjanje napak, shrani definicije
    // 1. obhod

    /**
     * [true] Faza vstavljanja v simbolno tabelo /
     * [false] faza razresevanja
     */
    private boolean insertPhase;

    public NameChecker(
        NodeDescription<Def> definitions,
        SymbolTable symbolTable
    ) 
    {
        requireNonNull(definitions, symbolTable);
        this.definitions = definitions;
        this.symbolTable = symbolTable;
        this.insertPhase = true;
    }

    @Override
    public void visit(Call call) {
        if (!insertPhase) {
            // resolve call
            var def = symbolTable.definitionFor(call.name);
            if (def.isPresent()) {
                if (def.get() instanceof FunDef) 
                    definitions.store(def.get(), call);
                else err_type(call.position, call.name, "function");
            } 
            else err_nodef(call.position, call.name);
            // resolve args
            for (var arg : call.arguments)
                arg.accept(this);
        }
    }

    @Override
    public void visit(Binary binary) {
        binary.left.accept(this);
        binary.right.accept(this);
    }

    @Override
    public void visit(Block block) {
        for (var e : block.expressions)
            e.accept(this);
    }

    @Override
    public void visit(For forLoop) {
        forLoop.counter.accept(this);
        forLoop.low.accept(this);
        forLoop.high.accept(this);
        forLoop.step.accept(this);
        forLoop.body.accept(this);
    }

    @Override
    public void visit(Name name) {
        if (!insertPhase) {
            var def = symbolTable.definitionFor(name.name);
            if (def.isPresent()) {
                if (def.get() instanceof VarDef || def.get() instanceof Parameter)
                    definitions.store(def.get(), name);
                else err_type(name.position, name.name, "variable");
            } 
            else err_nodef(name.position, name.name);
        }
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        ifThenElse.condition.accept(this);
        ifThenElse.thenExpression.accept(this);
        if (ifThenElse.elseExpression.isPresent())
            ifThenElse.elseExpression.get().accept(this);
    }

    @Override
    public void visit(Unary unary) {
        unary.expr.accept(this);
    }

    @Override
    public void visit(While whileLoop) {
        whileLoop.condition.accept(this);
        whileLoop.body.accept(this);
    }

    @Override
    public void visit(Where where) {
        symbolTable.pushScope();
        where.defs.accept(this);
        where.expr.accept(this);
        symbolTable.popScope();
    }

    @Override
    public void visit(Defs defs) {
        insertPhase = true;
        for (Def d : defs.definitions)
            d.accept(this);
        insertPhase = false;
        for (Def d : defs.definitions) 
            d.accept(this);
    }

    @Override
    public void visit(FunDef funDef) {
        if (insertPhase)
            insert(funDef);
        else /* resolve phase */ {
            funDef.type.accept(this); // check types
            for (Parameter p : funDef.parameters)
                p.type.accept(this);

            symbolTable.pushScope();
            insertPhase = true; // definicije
            for (Parameter p : funDef.parameters) 
                p.accept(this);
            funDef.body.accept(this);
            
            insertPhase = false; // resolve
            funDef.body.accept(this);
            symbolTable.popScope();
        }
    }

    @Override
    public void visit(TypeDef typeDef) {
        if (insertPhase)
            insert(typeDef);
        else /* resolve phase */ 
            typeDef.type.accept(this);
    }

    @Override
    public void visit(VarDef varDef) {
        if (insertPhase)
            insert(varDef);
        else /* resolve phase */
            varDef.type.accept(this);
    }

    @Override
    public void visit(Parameter parameter) {
        if (insertPhase) 
            insert(parameter);
        else /* resolve phase */
            parameter.type.accept(this);
    }

    @Override
    public void visit(Array array) {
        array.type.accept(this);
    }

    @Override
    public void visit(TypeName name) {
        if (!insertPhase) {
            var def = symbolTable.definitionFor(name.identifier);
            if (def.isPresent()) {
                if (def.get() instanceof TypeDef)
                    definitions.store(def.get(), name);
                else err_type(name.position, name.identifier, "type");
            } 
            else err_nodef(name.position, name.identifier);
        }
    }

    @Override
    public void visit(Atom atom) {}
    @Override
    public void visit(Literal literal) {}

    /**
     * Helpers
     */

    private void insert(Def definition) {
        try {
            symbolTable.insert(definition);
        }
        catch (DefinitionAlreadyExistsException e) {
            err_duplicate(definition.position, definition.name);
        }
    }

    private void err_duplicate(Position pos, String name) {
        Report.error(pos, "Name Resolve Error: Name '" + name + "'' already exists in this scope!");
    }

    private void err_nodef(Position pos, String name) {
        Report.error(pos, "Name Resolve Error: No definition for '" + name + "'!");
    }

    private void err_type(Position pos, String name, String correctType) {
        Report.error(pos, "Name Resolve Error: Identifier '" + name + "' is not defined as '" + correctType + "'!");
    }
}
