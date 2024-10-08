/**
 * @ Author: tevz
 * @ Description: Analizator klicnih zapisov.
 */

package compiler.frm;

import static common.RequireNonNull.requireNonNull;

import java.util.Stack;

import common.Constants;
import compiler.common.Visitor;
import compiler.frm.Frame.Builder;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.Array;
import compiler.parser.ast.type.Atom;
import compiler.parser.ast.type.TypeName;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;

public class FrameEvaluator implements Visitor {
    /**
     * Opis definicij funkcij in njihovih klicnih zapisov.
     */
    private NodeDescription<Frame> frames;

    /**
     * Opis definicij spremenljivk in njihovih dostopov.
     */
    private NodeDescription<Access> accesses;

    /**
     * Opis vozlišč in njihovih definicij.
     */
    private final NodeDescription<Def> definitions;

    /**
     * Opis vozlišč in njihovih podatkovnih tipov.
     */
    private final NodeDescription<Type> types;

    private int level;
    private Stack<Builder> b;

    public FrameEvaluator(
        NodeDescription<Frame> frames, 
        NodeDescription<Access> accesses,
        NodeDescription<Def> definitions,
        NodeDescription<Type> types
    ) {
        requireNonNull(frames, accesses, definitions, types);
        this.frames = frames;
        this.accesses = accesses;
        this.definitions = definitions;
        this.types = types;
        this.level = 0;
        this.b = new Stack<>();
    }

    @Override
    public void visit(Call call) { // rezerviramo plac za argumente
        int argSize = Constants.WordSize; // static link
        for (var arg : call.arguments) {
            arg.accept(this); // need?
            argSize += types.valueFor(arg).get().sizeInBytesAsParam();
        }
        b.peek().addFunctionCall(argSize);
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
        forLoop.low.accept(this);
        forLoop.high.accept(this);
        forLoop.step.accept(this);
        forLoop.body.accept(this);
    }

    @Override
    public void visit(IfThenElse ifThenElse) { 
        ifThenElse.condition.accept(this);
        ifThenElse.thenExpression.accept(this);
        if (ifThenElse.elseExpression.isPresent())
            ifThenElse.elseExpression.get().accept(this);
    }

    @Override
    public void visit(Unary unary) { unary.expr.accept(this); }


    @Override
    public void visit(While whileLoop) {
        whileLoop.condition.accept(this);
        whileLoop.body.accept(this);
    }

    @Override
    public void visit(Where where) {
        where.defs.accept(this);
        where.expr.accept(this);
    }

    @Override
    public void visit(Defs defs) {
        for (var d : defs.definitions)
            d.accept(this);
    }

    @Override
    public void visit(FunDef funDef) {
        if (level == 0)
            b.push(new Frame.Builder(Frame.Label.named(funDef.name), ++level));
        else
            b.push(new Frame.Builder(Frame.Label.nextAnonymous(), ++level));
        
        b.peek().addParameter(Constants.WordSize); // static link
        for (var p : funDef.parameters) {
            p.accept(this);
        }
        funDef.body.accept(this);
        var frame = b.peek().build(); b.pop();
        frames.store(frame, funDef);
        --level;
    }

    @Override
    public void visit(VarDef varDef) {
        var size = types.valueFor(varDef).get().sizeInBytes();
        if (level == 0)
            accesses.store(new Access.Global(size, Frame.Label.named(varDef.name)), varDef);
        else {
            int offset = b.peek().addLocalVariable(size);
            accesses.store(new Access.Local(size, offset, level), varDef);
        }
    }

    @Override
    public void visit(Parameter parameter) {
        var size = types.valueFor(parameter).get().sizeInBytesAsParam();
        int offset = b.peek().addParameter(size);
        var access = new Access.Parameter(size, offset, level);
        accesses.store(access, parameter);
    }

    @Override
    public void visit(Name name) { /* nothing to do */ }

    @Override
    public void visit(TypeDef typeDef) { /* nothing to do */ }

    @Override
    public void visit(Literal literal) { /* nothing to do */ }

    @Override
    public void visit(Array array) {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Atom atom) {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(TypeName name) {
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }
}
