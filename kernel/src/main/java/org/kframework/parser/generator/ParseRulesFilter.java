// Copyright (c) 2012-2015 K Team. All Rights Reserved.
package org.kframework.parser.generator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import org.kframework.kil.ASTNode;
import org.kframework.kil.Configuration;
import org.kframework.kil.Location;
import org.kframework.kil.Module;
import org.kframework.kil.Rule;
import org.kframework.kil.Sentence;
import org.kframework.kil.Source;
import org.kframework.kil.StringSentence;
import org.kframework.kil.Term;
import org.kframework.kil.loader.Constants;
import org.kframework.kil.loader.Context;
import org.kframework.kil.loader.JavaClassesFactory;
import org.kframework.kil.visitors.ParseForestTransformer;
import org.kframework.parser.concrete2.Grammar;
import org.kframework.parser.concrete2.MakeConsList;
import org.kframework.parser.concrete2.Parser;
import org.kframework.parser.concrete2.TreeCleanerVisitor;
import org.kframework.utils.errorsystem.ParseFailedException;
import org.kframework.parser.utils.CachedSentence;
import org.kframework.utils.XmlLoader;
import org.kframework.utils.errorsystem.KException;
import org.kframework.utils.errorsystem.KException.ExceptionType;
import org.kframework.utils.errorsystem.KException.KExceptionGroup;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class ParseRulesFilter extends ParseForestTransformer {
    final Map<String, CachedSentence> cachedDef;
    private final KExceptionManager kem;


    public ParseRulesFilter(Context context, KExceptionManager kem) {
        super("Parse Rules", context);
        cachedDef = new HashMap<>();
        this.kem = kem;
    }

    public ParseRulesFilter(Context context, Map<String, CachedSentence> cachedDef, KExceptionManager kem) {
        super("Parse Rules", context);
        this.cachedDef = cachedDef;
        this.kem = kem;
    }

    String localModule = null;

    @Override
    public ASTNode visit(Module m, Void _void) throws ParseFailedException {
        localModule = m.getName();
        return super.visit(m, _void);
    }

    public ASTNode visit(StringSentence ss, Void _void) throws ParseFailedException {
        if (ss.getType().equals(Constants.RULE) || ss.getType().equals(Constants.CONTEXT)) {
            long startTime = System.currentTimeMillis();
            Sentence sentence;
            Sentence st;

            if (!context.kompileOptions.experimental.javaParserRules) {
                String parsed = null;
                if (ss.containsAttribute("kore")) {

                    long koreStartTime = System.currentTimeMillis();
                    parsed = org.kframework.parser.concrete.DefinitionLocalKParser.ParseKoreString(ss.getContent(), context.files.resolveKompiled("."));
                    if (context.globalOptions.verbose)
                        System.out.println("Parsing with Kore: " + ss.getSource() + ":" + ss.getLocation() + " - " + (System.currentTimeMillis() - koreStartTime));
                } else {
                    try {
                        parsed = org.kframework.parser.concrete.DefinitionLocalKParser.ParseKConfigString(ss.getContent(), context.files.resolveKompiled("."));
                    // DISABLE EXCEPTION CHECKSTYLE
                    } catch (RuntimeException e) {
                        String msg = "SDF failed to parse a rule by throwing: " + e.getCause().getLocalizedMessage();
                        throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, ss.getSource(), ss.getLocation(), e));
                    }
                    // ENABLE EXCEPTION CHECKSTYLE
                }
                Document doc = XmlLoader.getXMLDoc(parsed);

                // replace the old xml node with the newly parsed sentence
                Node xmlTerm = doc.getFirstChild().getFirstChild().getNextSibling();
                XmlLoader.updateLocation(xmlTerm, ss.getContentStartLine(), ss.getContentStartColumn());
                XmlLoader.addSource(xmlTerm, ss.getSource());
                XmlLoader.reportErrors(doc, ss.getType());

                st = (Sentence) new JavaClassesFactory(context).getTerm((Element) xmlTerm);
                assert st.getLabel().equals(""); // labels should have been parsed in Outer Parsing
                st.setLabel(ss.getLabel());
                st.setAttributes(ss.getAttributes());
                st.setLocation(ss.getLocation());
                st.setSource(ss.getSource());
            } else  {
                // parse with the new parser for rules
                Grammar ruleGrammar = getCurrentModule().getRuleGrammar(kem);
                st = RuleParserHelper.parseSentence(ss, ruleGrammar.get("MetaKList"), ss.getType());
            }

            if (Constants.CONTEXT.equals(ss.getType()))
                sentence = new org.kframework.kil.Context(st);
            else if (Constants.RULE.equals(ss.getType()))
                sentence = new Rule(st);
            else { // should not reach here
                sentence = null;
                assert false : "Only context and rules have been implemented. Found: " + ss.getType();
            }

            String key = localModule + ss.getContent();
            if (cachedDef.containsKey(key)) {
                Source source= ss.getSource();
                Location location = ss.getLocation();
                String msg = "Duplicate rule found in module " + localModule + " at: " + cachedDef.get(key).sentence.getLocation();
                throw new ParseFailedException(new KException(ExceptionType.ERROR, KExceptionGroup.CRITICAL, msg, source, location));
            }
            cachedDef.put(key, new CachedSentence(sentence, ss.getContentStartLine(), ss.getContentStartColumn()));

            if (context.globalOptions.debug) {
                File file = context.files.resolveTemp("timing.log");
                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                    throw KExceptionManager.criticalError("Could not create directory " + file.getParentFile());
                }
                try (Formatter f = new Formatter(new FileWriter(file, true))) {
                    f.format("Parsing rule: Time: %6d Location: %s:%s%n", (System.currentTimeMillis() - startTime), ss.getSource(), ss.getLocation());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return sentence;
        }
        return ss;
    }
}
