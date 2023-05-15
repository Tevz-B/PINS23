/**
 * @ Author: turk
 * @ Description: Generator vmesne kode.
 */

package compiler.ir;

import static common.RequireNonNull.requireNonNull;

import java.util.ArrayList;
import java.util.List;

import common.Constants;
import common.Report;
import compiler.common.Visitor;
import compiler.frm.Access;
import compiler.frm.Frame;
import compiler.frm.Frame.Label;
import compiler.ir.chunk.Chunk;
import compiler.ir.chunk.Chunk.CodeChunk;
import compiler.ir.chunk.Chunk.DataChunk;
import compiler.ir.chunk.Chunk.GlobalChunk;
import compiler.ir.code.IRNode;
import compiler.ir.code.expr.*;
import compiler.ir.code.expr.BinopExpr.Operator;
import compiler.ir.code.stmt.*;
import compiler.parser.ast.def.*;
import compiler.parser.ast.def.FunDef.Parameter;
import compiler.parser.ast.expr.*;
import compiler.parser.ast.type.Array;
import compiler.parser.ast.type.Atom;
import compiler.parser.ast.type.TypeName;
import compiler.seman.common.NodeDescription;
import compiler.seman.type.type.Type;

public class IRCodeGenerator implements Visitor {
    /**
     * Preslikava iz vozlišč AST v vmesno kodo.
     */
    private NodeDescription<IRNode> imcCode;

    /**
     * Razrešeni klicni zapisi.
     */
    private final NodeDescription<Frame> frames;

    /**
     * Razrešeni dostopi.
     */
    private final NodeDescription<Access> accesses;

    /**
     * Razrešene definicije.
     */
    private final NodeDescription<Def> definitions;

    /**
     * Razrešeni tipi.
     */
    private final NodeDescription<Type> types;

    /**
     * **Rezultat generiranja vmesne kode** - seznam fragmentov.
     */
    public List<Chunk> chunks = new ArrayList<>();

    /**
     * Static Level
     */
    private int sl = 1;

    private static boolean ARRAYS_AS_REF = true;

    public IRCodeGenerator(
        NodeDescription<IRNode> imcCode,
        NodeDescription<Frame> frames, 
        NodeDescription<Access> accesses,
        NodeDescription<Def> definitions,
        NodeDescription<Type> types
    ) {
        requireNonNull(imcCode, frames, accesses, definitions, types);
        this.types = types;
        this.imcCode = imcCode;
        this.frames = frames;
        this.accesses = accesses;
        this.definitions = definitions;
    }

    private EseqExpr assign(IRExpr lvalue, IRExpr rvalue) {
        if (!(lvalue instanceof MemExpr) && !(lvalue instanceof TempExpr))
            Report.error("Assign Error: lvalue not writable");
        MoveStmt mv = new MoveStmt(lvalue, rvalue);
        return new EseqExpr(mv, rvalue);
    }

    @Override
    public void visit(Defs defs) {
        for (var d : defs.definitions) d.accept(this);
    }

    @Override
    public void visit(Binary binary) {
        binary.left.accept(this);
        binary.right.accept(this);
        IRExpr imcLeft = (IRExpr)imcCode.valueFor(binary.left).get();
        IRExpr imcRight = (IRExpr)imcCode.valueFor(binary.right).get();

        if (binary.operator.ordinal() <= Binary.Operator.GEQ.ordinal()) {
            BinopExpr.Operator op = BinopExpr.Operator.values()[binary.operator.ordinal()];
            imcCode.store(new BinopExpr(imcLeft, imcRight, op), binary);
        } else if (binary.operator == Binary.Operator.ASSIGN) {
            var eseq = assign(imcLeft, imcRight);
            imcCode.store(eseq, binary);
        } else if (binary.operator == Binary.Operator.ARR) { // test Arr[123]
            Type.Array t = (Type.Array) types.valueFor(binary.left).get().asArray().get();
            if (imcRight instanceof ConstantExpr) {
                if (((ConstantExpr) imcRight).constant >= t.size)
                    Report.error(binary.position, "Array index is larger than array size!");
            }
            if (ARRAYS_AS_REF) {
                IRExpr address;
                if (t.type.isArray()) {
                    address = ((MemExpr) imcLeft).expr;
                } else {
                    address = imcLeft;
                }
                IRExpr offset = new BinopExpr(imcRight, new ConstantExpr(Constants.WordSize), Operator.MUL);
                IRExpr indexAddr = new BinopExpr(address, offset, Operator.ADD);
                imcCode.store(new MemExpr(indexAddr), binary);
            } else {
                var address = ((MemExpr) imcLeft).expr;
                IRExpr offset = new BinopExpr(imcRight, new ConstantExpr(t.elementSizeInBytes()), Operator.MUL);
                IRExpr indexAddr = new BinopExpr(address, offset, Operator.ADD);
                imcCode.store(new MemExpr(indexAddr), binary);
            }
        } else Report.error(binary.position, "IR: Binary: operator conversion broken");
    }

    @Override
    public void visit(Block block) {
        for (var b : block.expressions) {
            b.accept(this);
        }
        List<IRStmt> stmts = new ArrayList<>();
        int i;
        for (i = 0; i < block.expressions.size()-1; ++i) {
            IRNode node = imcCode.valueFor(block.expressions.get(i)).get();
            if (node instanceof IRExpr)
                stmts.add(new ExpStmt((IRExpr) node));
            else
               stmts.add((IRStmt) node);
        }
        var lastInBlock = imcCode.valueFor(block.expressions.get(i)).get();
        IRExpr lastExpr;
        // handle if last in block is statement
        if ( lastInBlock instanceof IRStmt) {
            stmts.add((IRStmt) lastInBlock);
            lastExpr = new ConstantExpr(0);
        }
        else {
            lastExpr = (IRExpr) lastInBlock;
        }
        imcCode.store(new EseqExpr(new SeqStmt(stmts), lastExpr), block);
    }


    @Override
    public void visit(Name name) {
        IRExpr rez;
        var def = definitions.valueFor(name).get();
        var a = accesses.valueFor(def).get();
        if (a instanceof Access.Global) {
            Access.Global access = (Access.Global) a;
            rez = new MemExpr(new NameExpr(access.label));
        } else {
            if (a instanceof Access.Local) {
                var access = (Access.Local) a;
                // pridobi razliko staticnih nivojev
                int deltaSL = sl - access.staticLevel; // currentSL - definitionSL

                IRExpr addr = NameExpr.FP();
                for (int i = 0; i < deltaSL; ++i) {
                    addr = new MemExpr(addr);
                }
                var offset = new BinopExpr(NameExpr.FP(), new ConstantExpr(access.offset), BinopExpr.Operator.ADD);
                rez = new MemExpr(offset);
            } else {
                var access = (Access.Parameter) a;
                // pridobi razliko staticnih nivojev
                int deltaSL = sl - access.staticLevel; // currentSL - definitionSL

                IRExpr addr = NameExpr.FP();
                for (int i = 0; i < deltaSL; ++i) {
                    addr = new MemExpr(addr);
                }
                var offset = new BinopExpr(NameExpr.FP(), new ConstantExpr(access.offset), BinopExpr.Operator.ADD);
                rez = new MemExpr(offset); 
            }
        }
        imcCode.store(rez, name);
    }

    @Override
    public void visit(Literal literal) {
        IRExpr rez = null;
        switch (literal.type) {
            case INT:
                rez = new ConstantExpr(Integer.parseInt(literal.value));
                break;
            case LOG:
                rez = literal.value.equals("true") ? new ConstantExpr(1) : new ConstantExpr(0); // test for true
                break;
            case STR:       // STRING CONSTANT (DATA CHUNK)
                Label label = Label.nextAnonymous();
                Access.Global access = new Access.Global(literal.value.length() * Constants.WordSize, label);
                DataChunk str = new DataChunk(access, literal.value);
                chunks.add(str);
                rez = new NameExpr(label);
                break;
        }
        imcCode.store(rez, literal);
    }

    @Override
    public void visit(Unary unary) {
        unary.expr.accept(this);
        if (unary.operator == Unary.Operator.SUB) {
            var expr = new BinopExpr(new ConstantExpr(0), (IRExpr) imcCode.valueFor(unary.expr).get(), BinopExpr.Operator.SUB);
            imcCode.store(expr, unary);
        } else if (unary.operator == Unary.Operator.NOT) {
            var expr = new BinopExpr(new ConstantExpr(1), (IRExpr) imcCode.valueFor(unary.expr).get(), Operator.SUB);
            imcCode.store(expr, unary);
        } else {
            imcCode.store((IRExpr) imcCode.valueFor(unary.expr).get(), unary);
        }
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        List<IRStmt> statements = new ArrayList<>();

        ifThenElse.condition.accept(this);
        var condition = (IRExpr) imcCode.valueFor(ifThenElse.condition).get();
        ifThenElse.thenExpression.accept(this);
        var thenExpr = (IRExpr) imcCode.valueFor(ifThenElse.thenExpression).get();
        Label thenLabel = Label.nextAnonymous();
        Label endLabel = Label.nextAnonymous();

        if (ifThenElse.elseExpression.isPresent()) {
            ifThenElse.elseExpression.get().accept(this);
            var elseExpr = (IRExpr) imcCode.valueFor( ifThenElse.elseExpression.get()).get();
            Label elseLabel = Label.nextAnonymous();
            statements.add(new CJumpStmt(condition, thenLabel, elseLabel));
            statements.add(new LabelStmt(thenLabel));
            statements.add(new ExpStmt(thenExpr));
            statements.add(new JumpStmt(endLabel));
            statements.add(new LabelStmt(elseLabel));
            statements.add(new ExpStmt(elseExpr));
            statements.add(new LabelStmt(endLabel));
        }
        else {
            statements.add(new CJumpStmt(condition, thenLabel, endLabel));
            statements.add(new LabelStmt(thenLabel));
            statements.add(new ExpStmt(thenExpr));
            statements.add(new LabelStmt(endLabel));
        }
        imcCode.store(new SeqStmt(statements), ifThenElse);
    }

    @Override
    public void visit(For forLoop) {
        List<IRStmt> statements = new ArrayList<>();

        forLoop.counter.accept(this);
        forLoop.low.accept(this);
        forLoop.high.accept(this);
        forLoop.step.accept(this);
        forLoop.body.accept(this);

        IRExpr counterExpr = (IRExpr) imcCode.valueFor(forLoop.counter).get();
        IRExpr lowExpr = (IRExpr) imcCode.valueFor(forLoop.low).get();
        IRExpr highExpr = (IRExpr) imcCode.valueFor(forLoop.high).get();
        IRExpr stepExpr = (IRExpr) imcCode.valueFor(forLoop.step).get();
        var bodyNode = imcCode.valueFor(forLoop.body).get();
        IRStmt bodyStmt = (bodyNode instanceof IRStmt) ? (IRStmt) bodyNode : (IRStmt) new ExpStmt((IRExpr) bodyNode);
        BinopExpr condition = new BinopExpr(counterExpr, highExpr, Operator.LT);
        IRStmt increment = assign(counterExpr, new BinopExpr(counterExpr, stepExpr, Operator.ADD)).stmt;

        Label startlabel = Label.nextAnonymous();
        Label continueLabel = Label.nextAnonymous();
        Label endLabel = Label.nextAnonymous();

        statements.add(assign(counterExpr, lowExpr).stmt);
        statements.add(new LabelStmt(startlabel));
        statements.add(new CJumpStmt(condition, continueLabel, endLabel));
        statements.add(new LabelStmt(continueLabel));
        statements.add(bodyStmt);
        statements.add(increment);
        statements.add(new JumpStmt(startlabel));
        statements.add(new LabelStmt(endLabel));

        imcCode.store(new SeqStmt(statements), forLoop);
    }

    @Override
    public void visit(While whileLoop) {
        List<IRStmt> statements = new ArrayList<>();

        whileLoop.condition.accept(this);
        whileLoop.body.accept(this);

        IRExpr condition = (IRExpr) imcCode.valueFor(whileLoop.condition).get();
        var bodyNode = imcCode.valueFor(whileLoop.body).get();
        IRStmt bodyStmt = (bodyNode instanceof IRStmt) ? (IRStmt) bodyNode : (IRStmt) new ExpStmt((IRExpr) bodyNode);

        Label startLabel = Label.nextAnonymous();
        Label continueLabel = Label.nextAnonymous();
        Label endLabel = Label.nextAnonymous();

        statements.add(new LabelStmt(startLabel));
        statements.add(new CJumpStmt(condition, continueLabel, endLabel));
        statements.add(new LabelStmt(continueLabel));
        statements.add(bodyStmt);
        statements.add(new JumpStmt(startLabel));
        statements.add(new LabelStmt(endLabel));

        imcCode.store(new SeqStmt(statements), whileLoop);
    }

    @Override
    public void visit(Where where) {
        where.defs.accept(this);
        where.expr.accept(this);

        var expTemp = imcCode.valueFor(where.expr).get();
        if (expTemp instanceof IRExpr)
            imcCode.store((IRExpr) expTemp, where);
        else
            imcCode.store(new EseqExpr((IRStmt) expTemp, new ConstantExpr(-1)), where);
    }

    @Override
    public void visit(Call call) {
        for (var a : call.arguments)
            a.accept(this);
        List<IRExpr> argv = new ArrayList<>(call.arguments.size());
        for (var a : call.arguments)
            argv.add((IRExpr)imcCode.valueFor(a).get());

        var def = definitions.valueFor(call).get();
        if (frames.valueFor(def).isEmpty()) {  // std functions
            argv.add(0, NameExpr.FP());
            imcCode.store(new CallExpr(Label.named(call.name), argv), call);
            return;
        }
        var frm = frames.valueFor(def).get();
        // shrani si FP v oldFP
        int offset = frm.oldFPOffset();
        MoveStmt eseqStmt = new MoveStmt(new MemExpr(new BinopExpr(NameExpr.SP(), new ConstantExpr(offset), Operator.SUB)), NameExpr.FP());
        /* 
         * Ce funkcija klice svojo notranjo funkcijo -> za Static Link poda svoj FP                   - FP           MEMS:0   deltaSL = -1
         * Ce funkcija klice sebe / isti nivo ->   za Static link poda svoj Static Link               - MEM(FP)         MEMS:1   deltaSL = 0
         * Ce funkcija klice zunanjo funkcijo ->  za Static link poda FP od klicane zunanje funkcije  - MEM(MEM( ... (FP)) MEMS:2+  deltaSL + 1
        */
        int deltaSL = sl - frm.staticLevel;
        IRExpr staticLink = NameExpr.FP();
        for (int i = 0; i <= deltaSL; i++) {
            staticLink = new MemExpr(staticLink);
        }
        argv.add(0, staticLink);
        CallExpr callExpr = new CallExpr(frm.label, argv);
        EseqExpr eseq = new EseqExpr(eseqStmt, callExpr);
        imcCode.store(eseq, call);
    }

    @Override
    public void visit(FunDef funDef) {
        int prevSL = sl;
        sl = frames.valueFor(funDef).get().staticLevel;

        funDef.body.accept(this);
        IRExpr bodyExp = (IRExpr) imcCode.valueFor(funDef.body).get();
        MemExpr rv = new MemExpr(NameExpr.FP()); // rezultat f na mesto 1. argumenta (static link)
        IRStmt funStmt = new MoveStmt(rv, bodyExp);
        // dodaj funkcijo v CodeChunk
        imcCode.store(funStmt , funDef);
        chunks.add(new CodeChunk(frames.valueFor(funDef).get(), funStmt));

        sl = prevSL;
    }

    @Override
    public void visit(VarDef varDef) {
        Access a = accesses.valueFor(varDef).get();
        if (a instanceof Access.Global) {
            var access = (Access.Global) a;
            chunks.add(new GlobalChunk(access));
        }
    }

    @Override
    public void visit(TypeDef typeDef) { /* skip */ }

    @Override
    public void visit(TypeName name) { /* skip */ }

    @Override
    public void visit(Parameter parameter) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Array array) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Atom atom) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

}
