// Copyright (c) 2012-2014 K Team. All Rights Reserved.
package org.kframework.parser.generator;

import org.kframework.compile.utils.MetaK;
import org.kframework.kil.Definition;
import org.kframework.kil.Import;
import org.kframework.kil.Module;
import org.kframework.kil.ModuleItem;
import org.kframework.kil.loader.Context;
import org.kframework.kil.visitors.BasicVisitor;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class CollectSynModulesVisitor extends BasicVisitor {

    private final KExceptionManager kem;

    public CollectSynModulesVisitor(Context context, KExceptionManager kem) {
        super(context);
        this.kem = kem;
    }

    public Set<String> synModNames = new HashSet<String>();

    public Void visit(Definition def, Void _void) {
        List<String> synQue = new LinkedList<String>();
        if (def.getDefinitionContext().containsModule(def.getMainSyntaxModule())) {
            synQue.add(def.getMainSyntaxModule());
        } else {
            String msg = "Module " + def.getMainSyntaxModule() + " is not imported by the main module " +
                    def.getMainModule() + ".  The parser generator will use " + def.getMainModule() +
                    " as the main syntax module.";
            kem.register(new KException(ExceptionType.WARNING, KExceptionGroup.INNER_PARSER, msg));
            synQue.add(def.getMainModule());
        }

        Module bshm = def.getDefinitionContext().getModuleByName("AUTO-INCLUDED-MODULE-SYNTAX");
        if (bshm == null) {
            String msg = "Could not find module AUTO-INCLUDED-MODULE-SYNTAX (automatically included in the main syntax module)!";
            kem.register(new KException(ExceptionType.HIDDENWARNING, KExceptionGroup.INNER_PARSER, msg));
        } else
            synQue.add("AUTO-INCLUDED-MODULE-SYNTAX");

        while (!synQue.isEmpty()) {
            String mname = synQue.remove(0);
            if (!synModNames.contains(mname)) {
                synModNames.add(mname);

                Module m = def.getDefinitionContext().getModuleByName(mname);
                for (ModuleItem s : m.getItems())
                    if (s instanceof Import) {
                        Import imp = ((Import) s);
                        String mname2 = imp.getName();
                        Module mm = def.getDefinitionContext().getModuleByName(mname2);
                        // if the module starts with # it means it is predefined in maude
                        if (!mname2.startsWith("#"))
                            if (mm != null)
                                synQue.add(mm.getName());
                            else if (!MetaK.isKModule(mname2)) {
                                String msg = "Could not find module: " + mname2 + " imported from: " + m.getName();
                                kem.register(new KException(ExceptionType.ERROR, KExceptionGroup.INNER_PARSER, msg, getName(), imp.getSource(), imp.getLocation()));
                            }
                    }
            }
        }
        return null;
    }
}
