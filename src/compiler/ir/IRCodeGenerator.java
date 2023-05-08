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
    private int sl = -1;

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

    @Override
    public void visit(Defs defs) {
        for (var d : defs.definitions) d.accept(this);
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
        IRExpr imcLeft = (IRExpr)imcCode.valueFor(binary.left).get();
        IRExpr imcRight = (IRExpr)imcCode.valueFor(binary.right).get();

        BinopExpr rez = null;;
        if (binary.operator.ordinal() <= Binary.Operator.GEQ.ordinal()) {
            BinopExpr.Operator op = BinopExpr.Operator.values()[binary.operator.ordinal()];
            rez = new BinopExpr(imcLeft, imcRight, op);
        } else if (binary.operator == Binary.Operator.ASSIGN) {
            // TODO
            if (!(imcLeft instanceof MemExpr) && !(imcLeft instanceof TempExpr))
                Report.error(binary.position, "Assign Error: lvalue not writable");
            MoveStmt mv = new MoveStmt(imcLeft, imcRight);
            imcCode.store(mv, binary);
            return;
        } else if (binary.operator == Binary.Operator.ARR) {
            // TODO
        } else Report.error("Vmesna koda: Binary: operator je cuden");
        imcCode.store(rez, binary);
    }

    @Override
    public void visit(Block block) {
        for (var b : block.expressions) {
            b.accept(this);
        }
        List<IRStmt> stmts = new ArrayList<>();
        for (var b : block.expressions) {
            IRNode node = imcCode.valueFor(b).get();
            IRStmt s;
            if (node instanceof IRExpr)
                s = new ExpStmt((IRExpr) node);
            else
                s = (IRStmt) node;
            stmts.add(s);
        }
        imcCode.store(new SeqStmt(stmts), block);
    }

    @Override
    public void visit(For forLoop) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Name name) {
        IRExpr rez;
        var def = definitions.valueFor(name).get();
        var a = accesses.valueFor(def).get();
        if (a instanceof Access.Global) {
            Access.Global access = (Access.Global) a;
            // TODO
            rez = new MemExpr(new NameExpr(access.label));
        } else {
            if (a instanceof Access.Local) {
                var access = (Access.Local) a;
                // pridobi razliko staticnih nivojev
                int deltaSL = sl - access.staticLevel; // currentSL - definitionSL
                var addr = new BinopExpr(NameExpr.FP(), new ConstantExpr(access.offset), BinopExpr.Operator.ADD);
                rez = new MemExpr(addr);
            } else {
                var access = (Access.Parameter) a;
                // pridobi razliko staticnih nivojev
                int deltaSL = sl - access.staticLevel; // currentSL - definitionSL
                var addr = new BinopExpr(NameExpr.FP(), new ConstantExpr(access.offset), BinopExpr.Operator.ADD);
                rez = new MemExpr(addr); 
            }
        }
        imcCode.store(rez, name);
    }

    @Override
    public void visit(IfThenElse ifThenElse) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Literal literal) {
        ConstantExpr rez = null;
        switch (literal.type) {
            case INT:
                rez = new ConstantExpr(Integer.parseInt(literal.value));
                break;
            case LOG:
                rez = literal.value.equals("true") ? new ConstantExpr(1) : new ConstantExpr(0); // test for true
                break;
            case STR:
                // STRING CONSTANT (DATA CHUNK)
                Access.Global access = new Access.Global(literal.value.length(), Label.nextAnonymous());
                accesses.store(access, literal);
                DataChunk str = new DataChunk(access, literal.value);
                chunks.add(str);
                rez = null;
                break;
        }
        imcCode.store(rez, literal);
    }

    @Override
    public void visit(Unary unary) {
        unary.expr.accept(this);
        if (unary.operator == Unary.Operator.SUB) {
            var code = new BinopExpr(new ConstantExpr(0), (IRExpr) imcCode.valueFor(unary.expr).get(), BinopExpr.Operator.SUB);
            imcCode.store(code, unary);
        } else {
            imcCode.store((IRExpr) imcCode.valueFor(unary.expr).get(), unary);
        }
    }

    @Override
    public void visit(While whileLoop) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'visit'");
    }

    @Override
    public void visit(Where where) {
        where.defs.accept(this);
        where.expr.accept(this);
        imcCode.store(imcCode.valueFor(where.expr).get(), where); // eseq?
    }

    @Override
    public void visit(FunDef funDef) {
        int prevSL = sl;
        sl = frames.valueFor(funDef).get().staticLevel;

        funDef.body.accept(this);
        IRNode node = imcCode.valueFor(funDef.body).get();
        IRStmt code = null;
        if (node instanceof IRStmt) {
            code = (IRStmt) node;
        } else {
            code = new ExpStmt((IRExpr) node);
        }
        // dodaj funkcijo v CodeChunk
        CodeChunk c = new CodeChunk(frames.valueFor(funDef).get(), code);
        chunks.add(c);
        
        sl = prevSL;
    }

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

    @Override
    public void visit(VarDef varDef) { /* skip */
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

}
