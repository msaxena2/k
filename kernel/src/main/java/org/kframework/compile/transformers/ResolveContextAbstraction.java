// Copyright (c) 2012-2015 K Team. All Rights Reserved.
package org.kframework.compile.transformers;

import org.kframework.compile.utils.ConfigurationStructure;
import org.kframework.compile.utils.ConfigurationStructureMap;
import org.kframework.compile.utils.MetaK;
import org.kframework.kil.*;
import org.kframework.kil.Cell.Ellipses;
import org.kframework.kil.Cell.Multiplicity;
import org.kframework.kil.visitors.BasicVisitor;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.errorsystem.KExceptionManager;
import java.util.*;

public class ResolveContextAbstraction extends CopyOnWriteTransformer {

    private int maxLevel;
    private ConfigurationStructureMap config;

    private final KExceptionManager kem;

    public ResolveContextAbstraction(org.kframework.kil.loader.Context context, KExceptionManager kem) {
        super("Resolve Context Abstraction", context);
        config = context.getConfigurationStructureMap();
        maxLevel = context.getMaxConfigurationLevel();
        this.kem = kem;
    }

    @Override
    public ASTNode visit(Module node, Void _void)  {
        if (config.isEmpty()) return node;
        return super.visit(node, _void);
    }

    @Override
    public ASTNode visit(Configuration node, Void _void)  {
        return node;
    }

    @Override
    public ASTNode visit(Syntax node, Void _void)  {
        return node;
    }

    @Override
    public ASTNode visit(org.kframework.kil.Context node, Void _void)  {
        return node;
    }


    @Override
    public ASTNode visit(Rule node, Void _void)  {
        if (MetaK.isAnywhere(node)) return node;
        boolean change = false;
        if (MetaK.getTopCells(node.getBody(), context).isEmpty()) return node;
        Rule rule = (Rule) super.visit(node, _void);

        SplitByLevelVisitor visitor = new SplitByLevelVisitor(-1, context);
        visitor.visitNode(rule.getBody());

        int min = visitor.max;
        for (int i=visitor.max-1; i>0; i--) {
            if (!visitor.levels.get(i).isEmpty()) min = i;
        }

        if (min < visitor.max) change = true;
        Cell parentCell = null;
        do {
            if (min < visitor.max) {
                bringToLevel(visitor, min);
                visitor.max = min;
            }
            LinkedList<Term> cells = visitor.levels.get(min);
            if (areMultipleCells(cells)) change = true;
            ConfigurationStructure parent = findParent(cells.peek());
            parentCell = createParentCell(parent, cells);
            if (!cells.isEmpty()) {
                if (min <= 1) {
                    throw KExceptionManager.compilerError(
                            "Got to the top cell while trying to fill up context for cell " + cells.peek() + ".  Perhaps missing a multiplicity declaration in configuration? ",
                            this, node);
                }
                change = true;
                min--;
                visitor.levels.get(min).add(parentCell);
            }
        } while (min < visitor.max);
        if (change) {
            rule = rule.shallowCopy();
//            if (MetaK.getTopCells(parentCell.getContents(), context).size() > 1) {
            rule.setBody(parentCell);
//            } else {
//            rule.setBody(parentCell.getContents());
//            }
        }
        return rule;
    }

    private boolean areMultipleCells(LinkedList<Term> cells) {
        if (cells.size() > 1) return true;
        if (cells.isEmpty()) return false;
        Term trm = cells.element();
        if (trm instanceof Cell) return false;
        assert trm instanceof Rewrite;
        Rewrite rew = (Rewrite) trm;
        Term left = rew.getLeft();
        Term right = rew.getRight();
        if (!(left instanceof Cell && right instanceof Cell)) return true;
        if (!((Cell) left).getId().equals(((Cell) right).getId())) return true;
        return false;
    }

    @Override
    public ASTNode visit(Cell node, Void _void)  {
        boolean change = false;
        Cell cell = (Cell)super.visit(node, _void);
        if (cell.getEllipses() == Ellipses.NONE) return cell;
        ConfigurationStructure confCell = config.get(cell);
        if (confCell == null)
        {
            throw KExceptionManager.criticalError(
                    "Cell " + cell.getLabel() + " is not part of the configuration ",
                    this, node);
        }

        if (confCell.sons.isEmpty()) return cell;
        SplitByLevelVisitor visitor = new SplitByLevelVisitor(confCell.level, context);
        visitor.visitNode(cell.getContents());
        int min = 0;
        if (visitor.max>min) change = true;
        bringToLevel(visitor, min);
        LinkedList<Term> cells = visitor.levels.get(min);
        Cell parentCell = createParentCell(confCell, cells);
        assert(cells.isEmpty());
        if (change) cell = parentCell;
        return cell;
    }


    private void bringToLevel(SplitByLevelVisitor visitor, int level) {
        while (visitor.max > level) {
            LinkedList<Term> cells = visitor.levels.get(visitor.max);
            if (cells.isEmpty()) { visitor.max--; continue;}
            ConfigurationStructure parent = findParent(cells.peek());
            Cell parentCell = createParentCell(parent, cells);
            visitor.levels.get(visitor.max-1).add(parentCell);
        }
    }

    private Cell createParentCell(ConfigurationStructure parent,
            LinkedList<Term> cells) {
        Cell p = new Cell();
        p.setLabel(parent.cell.getLabel());
        p.setId(parent.id);
        Bag contents = new Bag();
        List<Term> items = new ArrayList<Term>();
        contents.setContents(items);
        p.setContents(contents);
        Ellipses e = fillParentItems(parent, cells, items);
        p.setEllipses(e);
        return p;
    }


    private Ellipses fillParentItems(ConfigurationStructure parent, LinkedList<Term> cells, List<Term> items) {
        Map<String, ConfigurationStructure> potentialSons = new HashMap<String, ConfigurationStructure>(parent.sons);
        ListIterator<Term> i = cells.listIterator();
        while (i.hasNext()) {
            Term t = i.next();
            List<Cell> inCells = MetaK.getTopCells(t, context);
            boolean allAvailable = true;
            for (Cell cell : inCells) {
                if (!potentialSons.containsKey(cell.getId())) {
                    allAvailable = false;
                    break;
                }
            }
            if (allAvailable) {
                for (Cell cell : inCells) {
                    ConfigurationStructure cellCfg = potentialSons.get(cell.getId());
                    if (cellCfg == null) {
                        kem.register(new KException(ExceptionType.HIDDENWARNING,
                                KExceptionGroup.INTERNAL,
                                "Cell " + cell + " appears more than its multiplicity in " + t + ". \n\tTransformation: " + getName(),
                                getName(),
                                t.getSource(), t.getLocation()));
                        continue;
                    }
                    if (cellCfg.multiplicity == Multiplicity.MAYBE || cellCfg.multiplicity == Multiplicity.ONE)
                        potentialSons.remove(cell.getId());
                }
                items.add(t);
                i.remove();
            }
        }
        if (potentialSons.isEmpty()) return Ellipses.NONE;
        return Ellipses.BOTH;
    }


    private ConfigurationStructure findParent(Term t) {
        if (t instanceof Cell) {
             return config.get(((Cell)t)).parent;
        }
        if (!(t instanceof Rewrite)) {
            throw KExceptionManager.internalError(
                    "Expecting Rewrite, but got " + t.getClass() + " while " + getName(),
                    this, t);

        }
        List<Cell> cells = MetaK.getTopCells(t, context);
        if (cells.isEmpty()) {
            throw KExceptionManager.internalError(
                    "Expecting some cells in here, but got none while " + getName(),
                    this, t);
        }
        Iterator<Cell> i = cells.iterator();
        ConfigurationStructure parent = config.get(i.next()).parent;
        while (i.hasNext()) {
            if (parent != config.get(i.next()).parent) {
                throw KExceptionManager.internalError(
                        "Not all cells " + cells + "have parent " + parent + " while " + getName(),
                        this, t);

            }
        }
        return parent;
    }

    private class SplitByLevelVisitor extends BasicVisitor {
        ArrayList<LinkedList<Term>> levels;
        private int level;
        private int max;

        public SplitByLevelVisitor(int level, org.kframework.kil.loader.Context context) {
            super(context);
            levels = new ArrayList<LinkedList<Term>>(maxLevel-level + 1);
            for (int i=0; i<=maxLevel-level; i++) levels.add(new LinkedList<Term>());
            this.level = level + 1;
            max = 0;
        }

        @Override
        public Void visit(Cell node, Void _void) {
            int level = config.get(node).level - this.level;
            if (level < 0) {
                throw KExceptionManager.internalError(
                        "Cell " + node + " Has a higher level than its parent.",
                        this, node);

            }
            if (max<level) max = level;
            levels.get(level).add(node);
            return null;
        }

        @Override
        public Void visit(KLabelConstant node, Void _void) {
            levels.get(0).add(node);
            return null;
        }

        @Override
        public Void visit(Token node, Void _void) {
            levels.get(0).add(node);
            return null;
        }

        @Override
        public Void visit(TermCons node, Void _void) {
            levels.get(0).add(node);
            return null;
        }

        @Override
        public Void visit(Variable node, Void _void) {
            levels.get(0).add(node);
            return null;
        }

        @Override
        public Void visit(Rewrite node, Void _void) {
            List<Cell> cells = MetaK.getTopCells(node, context);
            int level = 0;
            if (!cells.isEmpty()) {
                Iterator<Cell> i = cells.iterator();
                level = config.get(i.next()).level - this.level;
                if (!(level >= 0)) {
                    throw KExceptionManager.internalError(
                            "Rewrite not at the right level in configuration",
                            this, node);
                }
                if (max < level) max = level;
                while(i.hasNext()) //Sanity check -- see that all cells in a rewrite are at the same level
                    if (level != config.get(i.next()).level - this.level) {
                        throw KExceptionManager.internalError(
                                "Expecting all cells in " + node + " to be at the same level when " + getName(),
                                this, node);
                    }
            }
            levels.get(level).add(node);
            return null;
        }
    }


}
