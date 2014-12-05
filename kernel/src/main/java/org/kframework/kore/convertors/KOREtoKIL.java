// Copyright (c) 2014 K Team. All Rights Reserved.

package org.kframework.kore.convertors;

import org.kframework.kil.Attribute;
import org.kframework.kil.Production;
import org.kframework.kil.UserList;
import org.kframework.kore.*;
import org.kframework.kore.outer.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.kframework.kore.Collections.*;

public class KOREtoKIL {

    private static AssertionError NOT_IMPLEMENTED() {
        return NOT_IMPLEMENTED("Not implemented");
    }

    private static AssertionError NOT_IMPLEMENTED(String s) {
        return new AssertionError(s);
    }

    class ListProductionCollector {
        private Map<KString, List<SyntaxProduction>> listProds;
        private List<org.kframework.kil.Syntax> userLists;

        public List<org.kframework.kil.Syntax> getResults() {
            return userLists;
        }

        public List<Sentence> collectAndRemoveListProds(List<Sentence> sentences) {
            listProds = new HashMap<>();
            List<Sentence> ret = new ArrayList<>(sentences);
            Iterator<Sentence> iter = ret.iterator();
            while (iter.hasNext()) {
                Sentence sentence = iter.next();
                if (sentence instanceof SyntaxProduction) {
                    SyntaxProduction prod = (SyntaxProduction) sentence;
                    List<K> attrs = stream(prod.att().att()).collect(Collectors.toList());
                    KString listType = searchListType(attrs);
                    if (listType != null) {
                        List<SyntaxProduction> prods = listProds.get(listType);
                        if (prods == null) {
                            prods = new ArrayList<>(3);
                            listProds.put(listType, prods);
                        }
                        prods.add(prod);
                        iter.remove();
                    }
                }
            }
            generateUserLists();
            return ret;
        }

        private KString searchListType(List<K> attrs) {
            for (K attr : attrs) {
                if (attr instanceof KToken) {
                    KToken kToken = (KToken) attr;
                    if (kToken.sort().name().equals("userList")) {
                        return (kToken.s());
                    }
                }
            }
            return null;
        }

        private void generateUserLists() {
            userLists = new ArrayList<>();
            for (Map.Entry<KString, List<SyntaxProduction>> entry : listProds.entrySet()) {
                KString listType = entry.getKey();
                List<SyntaxProduction> prods = entry.getValue();
                if (prods.size() != 3 && prods.size() != 2) {
                    throw new AssertionError("Found list with " + prods.size() + " elements.");
                }

                if (prods.size() == 2) {
                    userLists.add(makeNonEmptyUserList(prods, listType.s()));
                } else {
                    userLists.add(makeUserList(prods, listType.s()));
                }
            }
        }

        // TODO: Remove duplicated code (makeUserList and makeNonEmptyUserList)
        private org.kframework.kil.Syntax makeUserList(List<SyntaxProduction> prods, String listType) {
            List<ProductionItem> prod1Items = stream(prods.get(0).items()).collect(Collectors.toList());
            List<ProductionItem> prod2Items = stream(prods.get(1).items()).collect(Collectors.toList());
            List<ProductionItem> prod3Items = stream(prods.get(2).items()).collect(Collectors.toList());

            Terminal sep;
            NonTerminal elem;

            if (prod1Items.size() == 3) {
                sep = (Terminal) prod1Items.get(1);
            } else if (prod2Items.size() == 3) {
                sep = (Terminal) prod2Items.get(1);
            } else {
                sep = (Terminal) prod3Items.get(1);
            }

            if (prod1Items.size() == 1 && prod1Items.get(0) instanceof NonTerminal) {
                elem = (NonTerminal) prod1Items.get(0);
            } else if (prod2Items.get(0) instanceof NonTerminal) {
                elem = (NonTerminal) prod2Items.get(0);
            } else {
                elem = (NonTerminal) prod3Items.get(0);
            }

            org.kframework.kil.Sort listSort = org.kframework.kil.Sort.of(listType);

            org.kframework.kil.UserList userList = new org.kframework.kil.UserList(
                    org.kframework.kil.Sort.of(elem.sort().name()), sep.value(), UserList.ZERO_OR_MORE);

            List<org.kframework.kil.ProductionItem> prodItems = new ArrayList<>(1);
            prodItems.add(userList);

            org.kframework.kil.Production prod = new org.kframework.kil.Production(
                    new org.kframework.kil.NonTerminal(listSort), prodItems);

            org.kframework.kil.PriorityBlock pb = new org.kframework.kil.PriorityBlock("", prod);
            return new org.kframework.kil.Syntax(new org.kframework.kil.NonTerminal(listSort), pb);
        }
    }

    private org.kframework.kil.Syntax makeNonEmptyUserList(List<SyntaxProduction> prods, String listType) {
        List<ProductionItem> prod1Items = stream(prods.get(0).items()).collect(Collectors.toList());
        List<ProductionItem> prod2Items = stream(prods.get(1).items()).collect(Collectors.toList());

        Terminal sep;
        NonTerminal elem;

        if (prod1Items.size() == 3) {
            sep = (Terminal) prod1Items.get(1);
        } else {
            sep = (Terminal) prod2Items.get(1);
        }

        if (prod1Items.size() == 1 && prod1Items.get(0) instanceof NonTerminal) {
            elem = (NonTerminal) prod1Items.get(0);
        } else {
            elem = (NonTerminal) prod2Items.get(0);
        }

        org.kframework.kil.Sort listSort = org.kframework.kil.Sort.of(listType);

        org.kframework.kil.UserList userList = new org.kframework.kil.UserList(
                org.kframework.kil.Sort.of(elem.sort().name()), sep.value(), UserList.ONE_OR_MORE);

        List<org.kframework.kil.ProductionItem> prodItems = new ArrayList<>(1);
        prodItems.add(userList);

        org.kframework.kil.Production prod = new org.kframework.kil.Production(
                new org.kframework.kil.NonTerminal(listSort), prodItems);

        org.kframework.kil.PriorityBlock pb = new org.kframework.kil.PriorityBlock("", prod);
        return new org.kframework.kil.Syntax(new org.kframework.kil.NonTerminal(listSort), pb);
    }

    private org.kframework.kil.loader.Context dummyContext = new org.kframework.kil.loader.Context();

    Map<String, org.kframework.kil.Production> kilProductionIdToProductionInstance = new HashMap<>();

    public org.kframework.kil.Definition convertDefinition(Definition definition) {
        List<org.kframework.kil.DefinitionItem> items = new ArrayList<>();

        for (Require r : iterable(definition.requires())) {
            items.add(convertRequire(r));
        }

        for (Module m : iterable(definition.modules())) {
            items.add(convertModule(m));
        }

        org.kframework.kil.Definition def = new org.kframework.kil.Definition();
        def.setItems(items);
        return def;
    }

    public org.kframework.kil.Require convertRequire(Require require) {
        return new org.kframework.kil.Require(require.file().getPath());
    }

    public org.kframework.kil.Module convertModule(Module module) {
        org.kframework.kil.Module mod = new org.kframework.kil.Module(module.name());

        List<Sentence> sentences = scala.collection.JavaConversions.seqAsJavaList(module
                .sentences().toList());
        mod = mod.addModuleItems(convertSentences(sentences));

        mod.setAttributes(convertAttributes(module.att()));
        return mod;
    }

    public List<org.kframework.kil.ModuleItem> convertSentences(List<Sentence> sentences) {
        List<org.kframework.kil.ModuleItem> ret = new ArrayList<>();
        ListProductionCollector listCollector = new ListProductionCollector();
        sentences = listCollector.collectAndRemoveListProds(sentences);
        ret.addAll(listCollector.getResults());

        sentences
                .stream()
                .filter(s -> s instanceof SyntaxProduction)
                .forEach(
                        sentence -> {
                            SyntaxProduction syntaxProduction = (SyntaxProduction) sentence;
                            List<org.kframework.kil.ProductionItem> kilProdItems = new ArrayList<>();
                            for (ProductionItem it : scala.collection.JavaConversions
                                    .seqAsJavaList(syntaxProduction.items())) {
                                kilProdItems.add(convertProdItem(it));
                            }
                            org.kframework.kil.NonTerminal lhs = new org.kframework.kil.NonTerminal(
                                    convertSort(syntaxProduction.sort()));
                            org.kframework.kil.Production kilProd = new org.kframework.kil.Production(
                                    lhs, kilProdItems);

                            kilProductionIdToProductionInstance.put(
                                    sentence.att().getString(KILtoInnerKORE.PRODUCTION_ID).get(),
                                    kilProd);

                            org.kframework.kil.PriorityBlock kilPB = new org.kframework.kil.PriorityBlock(
                                    "", kilProd);
                            ret.add(new org.kframework.kil.Syntax(lhs, kilPB));
                        });

        Set<Sentence> allTheRest = sentences.stream()
                .filter(s -> !(s instanceof SyntaxProduction)).collect(Collectors.toSet());

        for (Sentence sentence : allTheRest) {
            if (sentence instanceof Import) {
                ret.add(convertModuleItem((Import) sentence));
            } else if (sentence instanceof Bubble) {
                ret.add(convertModuleItem((Bubble) sentence));
            } else if (sentence instanceof ModuleComment) {
                ret.add(convertModuleItem((ModuleComment) sentence));
            } else if (sentence instanceof Configuration) {
                ret.add(convertModuleItem((Configuration) sentence));
            } else if (sentence instanceof SyntaxProduction) {
                ret.add(convertModuleItem((SyntaxProduction) sentence));
            } else if (sentence instanceof SyntaxSort) {
                ret.add(convertModuleItem((SyntaxSort) sentence));
            } else if (sentence instanceof Rule) {
                ret.add(convertModuleItem((Rule) sentence));
            } else if (sentence instanceof Context) {
                ret.add(convertModuleItem((Context) sentence));
            } else if (sentence instanceof SyntaxAssociativity) {
                ret.add(convertModuleItem((SyntaxAssociativity) sentence));
            } else if (sentence instanceof SyntaxPriority) {
                ret.add(convertModuleItem((SyntaxPriority) sentence));
            } else {
                throw NOT_IMPLEMENTED("Not implemented: KORE.Sentence("
                        + sentence.getClass().getName() + ") -> KIL.Sentence");
            }
        }

        return ret;
    }

    public org.kframework.kil.ModuleItem convertModuleItem(Import anImport) {
        org.kframework.kil.Import kilImport = new org.kframework.kil.Import(
                anImport.what());
        kilImport.setAttributes(convertAttributes(anImport.att()));
        return kilImport;
    }

    public org.kframework.kil.ModuleItem convertModuleItem(Bubble bubble) {
        org.kframework.kil.StringSentence kilBubble = new org.kframework.kil.StringSentence(
                bubble.contents(), null, bubble.sentenceType(), null);
        kilBubble.setAttributes(convertAttributes(bubble.att()));
        return kilBubble;
    }

    public org.kframework.kil.ModuleItem convertModuleItem(ModuleComment moduleComment) {
        org.kframework.kil.LiterateModuleComment kilComment =
                // TODO: we lost type information
                new org.kframework.kil.LiterateModuleComment(moduleComment.comment(), null);
        kilComment.setAttributes(convertAttributes(moduleComment.att()));
        return kilComment;
    }

    public org.kframework.kil.ModuleItem convertModuleItem(Configuration conf) {
        org.kframework.kil.Configuration kilConf = new org.kframework.kil.Configuration();
        kilConf.setBody(convertConfBody(conf.body()));
        kilConf.setEnsures(convertKBool(conf.ensures()));
        kilConf.setAttributes(convertAttributes(conf.att()));
        return kilConf;
    }

    public org.kframework.kil.ModuleItem convertModuleItem(SyntaxProduction syntaxProduction) {
        List<org.kframework.kil.ProductionItem> kilProdItems = new ArrayList<>();
        for (ProductionItem it : scala.collection.JavaConversions
                .seqAsJavaList(syntaxProduction.items())) {
            kilProdItems.add(convertProdItem(it));
        }
        org.kframework.kil.NonTerminal lhs = new org.kframework.kil.NonTerminal(
                convertSort(syntaxProduction.sort()));
        org.kframework.kil.Production kilProd = new org.kframework.kil.Production(lhs,
                kilProdItems);

        kilProd.setAttributes(convertAttributes(syntaxProduction.att()));

        kilProductionIdToProductionInstance.put(
                syntaxProduction.att().getString(KILtoInnerKORE.PRODUCTION_ID).get(), kilProd);

        org.kframework.kil.PriorityBlock kilPB = new org.kframework.kil.PriorityBlock("",
                kilProd);

        return new org.kframework.kil.Syntax(lhs, kilPB);
    }

    public org.kframework.kil.ModuleItem convertModuleItem(SyntaxSort syntaxSort) {
        return new org.kframework.kil.Syntax(new org.kframework.kil.NonTerminal(
                org.kframework.kil.Sort.of(syntaxSort.sort().name())), new ArrayList<>(0));
    }

    public org.kframework.kil.ModuleItem convertModuleItem(Rule rule) {
        if (rule.body() instanceof KRewrite) {
            KRewrite body = (KRewrite) rule.body();
            return new org.kframework.kil.Rule(convertK(body.left()), convertK(body
                    .right()), convertKBool(rule.requires()),
                    convertKBool(rule.ensures()), dummyContext);
        }
        throw NOT_IMPLEMENTED("Not implemented: Rule -> KIL.Sentence");
    }

    public org.kframework.kil.ModuleItem convertModuleItem(Context context) {
        org.kframework.kil.Context kilContext = new org.kframework.kil.Context();
        kilContext.setBody(convertK(context.body()));
        kilContext.setRequires(convertKBool(context.requires()));
        return kilContext;
    }

    public org.kframework.kil.ModuleItem convertModuleItem(SyntaxAssociativity syntaxAssociativity) {
        String assoc = convertAssoc(syntaxAssociativity.assoc());
        List<org.kframework.kil.KLabelConstant> tags =
                stream(syntaxAssociativity.tags()).map(this::convertTag).collect(Collectors.toList());
        return new org.kframework.kil.PriorityExtendedAssoc(assoc, tags);
    }

    public org.kframework.kil.ModuleItem convertModuleItem(SyntaxPriority syntaxPriority) {
        List<org.kframework.kil.PriorityBlockExtended> priorities =
                stream(syntaxPriority.priorities()).map(set -> {
                    List<Tag> tags = stream(set).collect(Collectors.toList());
                    return tagsToPriBlockExt(tags);
                }).collect(Collectors.toList());
        return new org.kframework.kil.PriorityExtended(priorities);
    }

    public org.kframework.kil.PriorityBlockExtended tagsToPriBlockExt(List<Tag> tags) {
        return new org.kframework.kil.PriorityBlockExtended(
                tags.stream().map(this::convertTag).collect(Collectors.toList()));
    }

    public String convertAssoc(scala.Enumeration.Value assoc) {
        if (assoc.equals(Associativity.Left())) {
            return "left";
        } else if (assoc.equals(Associativity.Right())) {
            return "right";
        } else if (assoc.equals(Associativity.NonAssoc())) {
            return "non-assoc";
        } else {
            throw new AssertionError("Unhandled enum value for Associativity.");
        }
    }

    public org.kframework.kil.KLabelConstant convertTag(Tag tag) {
        return new org.kframework.kil.KLabelConstant(tag.name());
    }

    public org.kframework.kil.ProductionItem convertProdItem(ProductionItem prodItem) {
        if (prodItem instanceof NonTerminal) {
            NonTerminal item = (NonTerminal) prodItem;
            return new org.kframework.kil.NonTerminal(convertSort(item.sort()));
        } else if (prodItem instanceof Terminal) {
            Terminal item = (Terminal) prodItem;
            return new org.kframework.kil.Terminal(item.value());
        } else if (prodItem instanceof RegexTerminal) {
            RegexTerminal item = (RegexTerminal) prodItem;
            return new org.kframework.kil.Lexical(item.regex(), null);
        } else {
            throw NOT_IMPLEMENTED();
        }
    }

    public org.kframework.kil.Sort convertSort(Sort sort) {
        return org.kframework.kil.Sort.of(sort.name());
    }

    public org.kframework.kil.Attributes convertAttributes(Attributes koreAttributes) {
        org.kframework.kil.Attributes kilAttributes = new org.kframework.kil.Attributes();
        koreAttributes.stream().forEach(a -> {
            if (a instanceof KApply) {
                KApply attr = (KApply) a;
                KLabel key = attr.klabel();
                KList klist = attr.klist();
                if (klist.size() == 1 && klist.get(0) instanceof KToken) {
                    String value = ((KToken) klist.get(0)).s().s();
                    kilAttributes.add(Attribute.of(key.name(), value));
                } else {
                    throw NOT_IMPLEMENTED();
                }
            } else if (a instanceof KToken) {
                KToken attr = (KToken) a;
                String stringValue = attr.s().s();
                kilAttributes.add(Attribute.of(stringValue, stringValue));
            } else {
                throw NOT_IMPLEMENTED();
            }
        });
        return kilAttributes;
    }

    public org.kframework.kil.Term convertConfBody(K k) {
        if (k instanceof KApply) {
            KApply kApply = (KApply) k;
            KLabel confLabel = kApply.klabel();
            org.kframework.kil.Cell kilCell = new org.kframework.kil.Cell();
            kilCell.setLabel(confLabel.name());
            List<K> args = kApply.list();
            if (args.size() == 1) {
                kilCell.setContents(convertK(args.get(0)));
            } else {
                kilCell.setContents(new org.kframework.kil.Bag(args.stream()
                        .map(kk -> convertConfBody(kk)).collect(Collectors.toList())));
            }
            return kilCell;
        }
        throw new AssertionError("Unexpected conf body found.");
    }

    public org.kframework.kil.Term convertK(K k) {
        if (k instanceof KSequence) {
            KSequence kSequence = (KSequence) k;
            return new org.kframework.kil.KSequence(kSequence.stream().map(this::convertK)
                    .collect(Collectors.toList()));
        } else if (k instanceof KApply) {
            KApply kApply = (KApply) k;
            // FIXME: a lot of things that are not a KApply are translated to
            // KORE KApply, we need to figure out every one of them and handle
            // here
            return convertKApply(kApply);
        } else if (k instanceof KBag) {
            KBag kBag = (KBag) k;
            List<K> bagItems = kBag.list();
            org.kframework.kil.Bag kilBag = new org.kframework.kil.Bag();
            List<org.kframework.kil.Term> kilBagItems = new ArrayList<>();
            for (K bagItem : bagItems) {
                if (bagItem instanceof KBag) {
                    KBag item = (KBag) bagItem;
                    List<K> kbagItems = item.list();
                    kilBagItems.addAll(kbagItems.stream().map(this::convertK)
                            .collect(Collectors.toList()));
                } else {
                    kilBagItems.add(convertK(bagItem));
                }
            }
            kilBag.setContents(kilBagItems);
            return kilBag;
        } else if (k instanceof KVariable) {
            return convertKVariable((KVariable) k);
        }
        System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
        throw NOT_IMPLEMENTED("Not implemented: KORE.K(" + k.getClass().getName()
                + ") -> KIL.Term");
    }

    public org.kframework.kil.Variable convertKVariable(KVariable var) {
        List<K> attrs = stream(var.att().att()).collect(Collectors.toList());
        org.kframework.kil.Sort sort = null;
        for (K k : attrs) {
            if (k instanceof KApply) {
                KApply kApply = (KApply) k;
                if (kApply.klabel().equals(new ConcreteKLabel("sort"))) {
                    List<K> args = kApply.list();
                    if (args.size() == 1 && args.get(0) instanceof KToken) {
                        KToken tok = (KToken) args.get(0);
                        sort = org.kframework.kil.Sort.of(tok.s().s());
                        break;
                    }
                }
            }
        }
        if (sort == null) {
            throw new AssertionError("Can't figure sort of KVariable");
        }
        return new org.kframework.kil.Variable(var.name(), sort);
    }

    public org.kframework.kil.Term convertKBool(K k) {
        if (k instanceof KBoolean) {
            return null; // FIXME
        }
        return convertK(k);
    }

    public org.kframework.kil.Term convertKApply(KApply kApply) {
        KLabel label = kApply.klabel();
        List<K> contents = kApply.list();
        if (label == Hole$.MODULE$) {
            Sort sort = ((KToken) contents.get(0)).sort();
            return new org.kframework.kil.Hole(org.kframework.kil.Sort.of(sort.name()));
        } else {
            List<K> args = stream(kApply.klist().iterable()).collect(Collectors.toList());
            List<org.kframework.kil.Term> kilTerms = new ArrayList<>();
            for (K arg : args) {
                kilTerms.add(convertK(arg));
            }
            String kilProductionId = kApply.att().getString(KILtoInnerKORE.PRODUCTION_ID).get();

            Production production = kilProductionIdToProductionInstance.get(kilProductionId);
            if (production == null) {
                throw new AssertionError("Could not find production for: " + kApply);
            }

            return new org.kframework.kil.TermCons(org.kframework.kil.Sort.of(label.name()),
                    kilTerms, production);
        }
    }
}
