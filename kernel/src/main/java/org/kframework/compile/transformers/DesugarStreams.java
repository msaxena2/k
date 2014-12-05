// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.compile.transformers;

import org.kframework.kil.*;
import org.kframework.kil.visitors.CopyOnWriteTransformer;
import org.kframework.utils.errorsystem.KExceptionManager;
import java.util.ArrayList;


public class DesugarStreams extends CopyOnWriteTransformer {

    ArrayList<String> channels = new ArrayList<String>();

    public DesugarStreams(org.kframework.kil.loader.Context context) {
        super("Desugar streams", context);

        channels.add("stdin");
        channels.add("stdout");
    }

    @Override
    public ASTNode visit(Cell node, Void _void)  {
        ASTNode result = super.visit(node, _void);
        if (!(result instanceof Cell)) {
            throw KExceptionManager.internalError(
                    "Expecting Cell, but got " + result.getClass() + " in Streams Desugarer.",
                    this, result);
        }
        Cell cell = (Cell) result;
        String stream = cell.getCellAttributes().get("stream");
        if (null == stream) return cell;
        if (result == node) {
            node = node.shallowCopy();
        } else node = cell;
        node.setContents(makeStreamList(stream, node));
        return node;
    }

    private Term makeStreamList(String stream, Cell node) {
        Term contents = node.getContents();

        Term addAtBeginning = null;
        Term addAtEnd = null;
        java.util.List<Term> items = new ArrayList<Term>();
        if ("stdin".equals(stream)) {
            addAtBeginning = contents;
            KApp buffer = KApp.of("'#buffer", new Variable("$stdin", Sort.STRING));
            items.add(newListItem(buffer));

            items.add(new Variable("$noIO", Sort.LIST));

            KApp stdinStream = KApp.of("'#istream", IntBuiltin.ZERO);
            items.add(newListItem(stdinStream));
        }
        if ("stdout".equals(stream)) {
            KApp stdoutStream = KApp.of("'#ostream", IntBuiltin.ONE);
            items.add(newListItem(stdoutStream));

            items.add(new Variable("$noIO", Sort.LIST));

            KApp buffer = KApp.of("'#buffer", StringBuiltin.EMPTY);
            items.add(newListItem(buffer));

            addAtEnd = contents;
        }
        if(channels.indexOf(stream) == -1){
            throw KExceptionManager.internalError(
                    "Make sure you give the correct stream names: " + channels.toString(),
                    this, node);
        }
        DataStructureSort myList = context.dataStructureListSortOf(DataStructureSort.DEFAULT_LIST_SORT);
        Term newItems = DataStructureSort.listOf(context, items.toArray(new Term[] {}));
        if (addAtBeginning != null) {
            newItems = KApp.of(KLabelConstant.of(myList.constructorLabel()), addAtBeginning, newItems);
        }
        if (addAtEnd != null) {
            newItems = KApp.of(KLabelConstant.of(myList.constructorLabel()), newItems, addAtEnd);
        }
        return newItems;
    }

    private Term newListItem(Term element) {
        DataStructureSort myList = context.dataStructureListSortOf(DataStructureSort.DEFAULT_LIST_SORT);
        return KApp.of(KLabelConstant.of(myList.elementLabel()), element);
    }

    @Override
    public ASTNode visit(org.kframework.kil.Context node, Void _void) {
        return node;
    }

    @Override
    public ASTNode visit(Rule node, Void _void) {
        return node;
    }

    @Override
    public ASTNode visit(Syntax node, Void _void) {
        return node;
    }

}
