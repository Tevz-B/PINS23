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
    /**
     * Opis vozlišč, ki jih povežemo z njihovimi
     * definicijami.
     */
    private NodeDescription<Def> definitions; // za izpis, klici ko se uporabi ID
    // puscica, 2. obhod

    /**
     * Simbolna tabela.
     */
    private SymbolTable symbolTable; // za preverjanje napak, shrani definicije
    // 1. obhod

    private boolean insertPhase;

    /**
     * Ustvari nov razreševalnik imen.
     */
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
        // check if function
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Binary binary) {
        binary.left.accept(this);
        binary.right.accept(this);
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
        var def = symbolTable.definitionFor(name.name);
        if (def.isPresent()) {
            definitions.store(def.get(), name);
        } else {
            err_nodef(name.position, name.name);
        }
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
        insertPhase = true;
        for (Def d : defs.definitions) {
            d.accept(this);
        }
        insertPhase = false;
        for (Def d : defs.definitions) {
            d.accept(this);
        }
    }

    @Override
    public void visit(FunDef funDef) {
        if (insertPhase) {
            insert(funDef);
            funDef.type.accept(this);
        }
        else /* resolve phase */ {
            symbolTable.pushScope();
            insertPhase = true; // definicije
            for (Parameter p : funDef.parameters) 
                p.accept(this);
            funDef.body.accept(this);

            insertPhase = false; // resolve
            for (Parameter p : funDef.parameters) 
                p.accept(this);
            funDef.body.accept(this);

            symbolTable.popScope();
        }
    }

    @Override
    public void visit(TypeDef typeDef) {
        if (insertPhase) {
            insert(typeDef);
        }
        else /* resolve phase */ {
            typeDef.type.accept(this);
        }
    }

    @Override
    public void visit(VarDef varDef) {
        if (insertPhase) {
            insert(varDef);
        }
        else /* resolve phase */{  
            varDef.type.accept(this);
        }
    }

    @Override
    public void visit(Parameter parameter) { // TODO
        if (insertPhase) {
            insert(parameter);
        }
        else /* resolve phase */{  
            parameter.type.accept(this);
        }
    }

    @Override
    public void visit(Array array) {
        array.type.accept(this);
    }

    @Override
    public void visit(Atom atom) {
        // Do nothing
    }

    @Override
    public void visit(TypeName name) {
        var def = symbolTable.definitionFor(name.identifier);
        if (def.isPresent()) {
            definitions.store(def.get(), name);
        } else {
            err_nodef(name.position, name.identifier);
        }
    }

    /**
     * Helpers
     */

    private void insert(Def definition) {
        try {
            symbolTable.insert(definition);
        }
        catch (Exception e) {
            err_duplicate(definition.position, definition.name);
        }
    }

    private void err_duplicate(Position pos, String name) {
        Report.error(pos, "Name Resolve Error: Name '" + name + "'' already exists in this scope!");
    }

    private void err_nodef(Position pos, String name) {
        Report.error(pos, "Name Resolve Error: No definition for '" + name + "'!");
    }
}
