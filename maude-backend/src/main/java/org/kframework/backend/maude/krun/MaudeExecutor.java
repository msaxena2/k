// Copyright (c) 2013-2014 K Team. All Rights Reserved.
package org.kframework.backend.maude.krun;

import com.google.inject.Inject;
import edu.uci.ics.jung.graph.DirectedGraph;
import edu.uci.ics.jung.io.GraphIOException;
import edu.uci.ics.jung.io.graphml.EdgeMetadata;
import edu.uci.ics.jung.io.graphml.GraphMLReader2;
import edu.uci.ics.jung.io.graphml.GraphMetadata;
import edu.uci.ics.jung.io.graphml.HyperEdgeMetadata;
import edu.uci.ics.jung.io.graphml.NodeMetadata;
import org.apache.commons.collections15.Transformer;
import org.kframework.backend.maude.MaudeFilter;
import org.kframework.backend.maude.MaudeKRunOptions;
import org.kframework.compile.utils.RuleCompilerSteps;
import org.kframework.kil.Ambiguity;
import org.kframework.kil.BackendTerm;
import org.kframework.kil.Bag;
import org.kframework.kil.BagItem;
import org.kframework.kil.BoolBuiltin;
import org.kframework.kil.Cell;
import org.kframework.kil.DataStructureBuiltin;
import org.kframework.kil.DataStructureSort;
import org.kframework.kil.Definition;
import org.kframework.kil.FloatBuiltin;
import org.kframework.kil.FreezerLabel;
import org.kframework.kil.GenericToken;
import org.kframework.kil.Hole;
import org.kframework.kil.IntBuiltin;
import org.kframework.kil.KApp;
import org.kframework.kil.KInjectedLabel;
import org.kframework.kil.KLabelConstant;
import org.kframework.kil.KList;
import org.kframework.kil.KSequence;
import org.kframework.kil.ListBuiltin;
import org.kframework.kil.MapBuiltin;
import org.kframework.kil.Production;
import org.kframework.kil.Rule;
import org.kframework.kil.SetBuiltin;
import org.kframework.kil.Sort;
import org.kframework.kil.StringBuiltin;
import org.kframework.kil.Term;
import org.kframework.kil.TermCons;
import org.kframework.kil.Token;
import org.kframework.kil.Variable;
import org.kframework.kil.loader.Context;
import org.kframework.krun.KRunExecutionException;
import org.kframework.krun.KRunOptions;
import org.kframework.krun.SubstitutionFilter;
import org.kframework.krun.XmlUtil;
import org.kframework.krun.api.KRunDebuggerResult;
import org.kframework.krun.api.KRunGraph;
import org.kframework.krun.api.KRunResult;
import org.kframework.krun.api.KRunState;
import org.kframework.krun.api.SearchResult;
import org.kframework.krun.api.SearchResults;
import org.kframework.krun.api.SearchType;
import org.kframework.krun.api.Transition;
import org.kframework.krun.runner.KRunner;
import org.kframework.krun.tools.Executor;
import org.kframework.utils.Stopwatch;
import org.kframework.utils.StringUtil;
import org.kframework.utils.errorsystem.KExceptionManager;
import org.kframework.utils.file.FileUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MaudeExecutor implements Executor {

    public static final String inFile = "maude_in";
    public static final String outFile = "maude_out";
    public static final String errFile = "maude_err";
    public static final String xmlOutFile = "maudeoutput.xml";

    private final KExceptionManager kem;
    private final KRunOptions options;
    private final MaudeKRunOptions maudeOptions;
    private final Stopwatch sw;
    private final Context context;
    private final FileUtil files;
    private final KRunner runner;
    private final File processedXmlOutFile;
    private final Definition def;
    private final KRunState.Counter stateCounter;

    private int counter = 0;

    @Inject
    MaudeExecutor(
            KRunOptions options,
            Stopwatch sw,
            Context context,
            KExceptionManager kem,
            MaudeKRunOptions maudeOptions,
            FileUtil files,
            KRunner runner,
            Definition def,
            KRunState.Counter stateCounter) {
        this.options = options;
        this.sw = sw;
        this.context = context;
        this.kem = kem;
        this.maudeOptions = maudeOptions;
        this.files = files;
        this.runner = runner;
        this.processedXmlOutFile = files.resolveTemp("maudeoutput_simplified.xml");
        this.def = def;
        this.stateCounter = stateCounter;
    }

    void executeKRun(StringBuilder maudeCmd) throws KRunExecutionException {
        files.saveToTemp(inFile, maudeCmd.toString());

        int returnValue;
        returnValue = runner.run();
        if (files.resolveTemp(errFile).exists()) {
            String content = files.loadFromTemp(errFile);
            if (content.length() > 0) {
                throw new KRunExecutionException(content);
            }
        }
        if (returnValue != 0) {
            throw KExceptionManager.criticalError("Maude returned non-zero value: " + returnValue);
        }
    }

    @Override
    public KRunResult<KRunState> run(Term cfg) throws KRunExecutionException {
        return run("erewrite", cfg);
    }

    private KRunResult<KRunState> run(String maude_cmd, Term cfg) throws KRunExecutionException {
        MaudeFilter maudeFilter = new MaudeFilter(context, kem);
        maudeFilter.visitNode(cfg);
        StringBuilder cmd = new StringBuilder();

        if(options.experimental.trace) {
            cmd.append("set trace on .\n");
        }
        if(maudeOptions.profile) {
            cmd.append("set profile on .\n");
        }

        cmd.append("set show command off .\n")
            .append(setCounter()).append(maude_cmd).append(" ")
            .append(maudeFilter.getResult()).append(" .\n");

        if(maudeOptions.profile) {
            cmd.append("show profile .\n");
        }

        cmd.append(getCounter());

        executeKRun(cmd);
        sw.printIntermediate("Execution");
        try {
            return parseRunResult(maude_cmd);
        } catch (IOException e) {
            throw new KRunExecutionException("Parsing maude output exception", e);
        }
    }

    private String setCounter() {
        return "red setCounter(" + Integer.toString(counter) + ") .\n";
    }

    private String getCounter() {
        return "\nred counter .";
    }

    @Override
    public KRunResult<KRunState> step(Term cfg, int steps) throws KRunExecutionException {
        KRunResult<KRunState> result;
        if (steps == 0) {
            result = run("red", cfg);
        } else {
            result = run("rew[" + steps + "]", cfg);
        }
        return result;
    }

    @Override
    public KRunResult<KRunDebuggerResult> debugStep(Term originalState) throws KRunExecutionException {
        return null;
    }


    //needed for --statistics command
    private String printStatistics(Element elem, String maude_cmd) {
        String result = "";
        if ("search".equals(maude_cmd)) {
            String totalStates = elem.getAttribute("total-states");
            String totalRewrites = elem.getAttribute("total-rewrites");
            String realTime = elem.getAttribute("real-time-ms");
            String cpuTime = elem.getAttribute("cpu-time-ms");
            String rewritesPerSecond = elem.getAttribute("rewrites-per-second");
            result += "states: " + totalStates + " rewrites: " + totalRewrites + " in " + cpuTime + "ms cpu (" + realTime + "ms real) (" + rewritesPerSecond + " rewrites/second)";
        } else {
            String totalRewrites = elem.getAttribute("total-rewrites");
            String realTime = elem.getAttribute("real-time-ms");
            String cpuTime = elem.getAttribute("cpu-time-ms");
            String rewritesPerSecond = elem.getAttribute("rewrites-per-second");
            result += "rewrites: " + totalRewrites + " in " + cpuTime + "ms cpu (" + realTime + "ms real) (" + rewritesPerSecond + " rewrites/second)";
        }
        return result;
    }

    private KRunResult<KRunState> parseRunResult(String maude_cmd) throws IOException {
        Document doc = XmlUtil.readXMLFromFile(files.resolveTemp(xmlOutFile).getAbsolutePath());
        NodeList list;
        Node nod;
        list = doc.getElementsByTagName("result");
        nod = list.item(1);

        assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
        Element elem = (Element) nod;
        List<Element> child = XmlUtil.getChildElements(elem);
        assertXML(child.size() == 1);

        KRunState state = parseElement(child.get(0), context);
        KRunResult<KRunState> ret = new KRunResult<KRunState>(state);
        String statistics = printStatistics(elem, maude_cmd);
        ret.setStatistics(statistics);
        ret.setRawOutput(files.loadFromTemp(outFile));
        parseCounter(list.item(2));
        return ret;
    }

    private KRunState parseElement(Element el, org.kframework.kil.loader.Context context) {
        Term rawResult = parseXML(el, context);

        return new KRunState(rawResult, stateCounter);
    }

    private void parseCounter(Node counterNode) {
        assertXML(counterNode != null && counterNode.getNodeType() == Node.ELEMENT_NODE);
        Element elem = (Element) counterNode;
        List<Element> child = XmlUtil.getChildElements(elem);
        assertXML(child.size() == 1);
        IntBuiltin intBuiltin = (IntBuiltin) parseXML(child.get(0), context);
        counter = intBuiltin.bigIntegerValue().intValue() - 1;
    }

    void assertXML(boolean assertion) {
        if (!assertion) {
            assert false;
        }
    }

    private static class InvalidMaudeXMLException extends Exception {}

    private static void assertXMLTerm(boolean assertion) throws InvalidMaudeXMLException {
        if (!assertion) {
            throw new InvalidMaudeXMLException();
        }
    }

    public static Pattern emptyPattern = Pattern.compile("\\.(Map|Bag|List|Set|K)");

    public static Term parseXML(Element xml, Context context) {
        String op = xml.getAttribute("op");
        String sort = xml.getAttribute("sort");
        sort = sort.replaceAll("`([{}\\[\\](),])", "$1");
        List<Element> list = XmlUtil.getChildElements(xml);

        DataStructureSort listSort = context.dataStructureSortOf(DataStructureSort.DEFAULT_LIST_SORT);
        DataStructureSort mapSort = context.dataStructureSortOf(DataStructureSort.DEFAULT_MAP_SORT);
        DataStructureSort setSort = context.dataStructureSortOf(DataStructureSort.DEFAULT_SET_SORT);

        try {
            if ((sort.equals(Sort.BAG_ITEM.toString()) || sort.equals("[Bag]")) && op.equals("<_>_</_>")) {
                Cell cell = new Cell();
                assertXMLTerm(list.size() == 3 && list.get(0).getAttribute("sort").equals("CellLabel") && list.get(2).getAttribute("sort").equals("CellLabel") && list.get(0).getAttribute("op").equals(list.get(2).getAttribute("op")));

                cell.setLabel(list.get(0).getAttribute("op"));
                cell.setContents(parseXML(list.get(1), context));
                return cell;
            } else if ((sort.equals(Sort.BAG_ITEM.toString()) || sort.equals("[Bag]")) && op.equals(Sort.BAG_ITEM.toString())) {
                assertXMLTerm(list.size() == 1);
                return new BagItem(parseXML(list.get(0), context));
            } else if ((sort.equals(Sort.MAP_ITEM.toString()) || sort.equals("[Map]")) && op.equals("_|->_")) {
                assertXMLTerm(list.size() == 2);
                return MapBuiltin.element(mapSort, parseXML(list.get(0), context), parseXML(list.get(1), context));
            } else if ((sort.equals(Sort.SET_ITEM.toString()) || sort.equals("[Set]")) && op.equals(Sort.SET_ITEM.toString())) {
                assertXMLTerm(list.size() == 1);
                return SetBuiltin.element(setSort, parseXML(list.get(0), context));
            } else if ((sort.equals(Sort.LIST_ITEM.toString()) || sort.equals("[List]")) && op.equals(Sort.LIST_ITEM.toString())) {
                assertXMLTerm(list.size() == 1);
                return ListBuiltin.element(listSort, parseXML(list.get(0), context));
            } else if (op.equals("_`,`,_") && sort.equals("NeKList")) {
                assertXMLTerm(list.size() >= 2);
                List<Term> l = new ArrayList<Term>();
                for (Element elem : list) {
                    l.add(parseXML(elem, context));
                }
                return new KList(l);
            } else if (sort.equals(Sort.K.toString()) && op.equals("_~>_")) {
                assertXMLTerm(list.size() >= 2);
                List<Term> l = new ArrayList<Term>();
                for (Element elem : list) {
                    l.add(parseXML(elem, context));
                }
                return new KSequence(l);
            } else if (op.equals("__") && (sort.equals("NeList") || sort.equals(Sort.LIST.toString()) || sort.equals("[List]"))) {
                assertXMLTerm(list.size() >= 2);
                List<Term> l = new ArrayList<Term>();
                for (Element elem : list) {
                    l.add(parseXML(elem, context));
                }
                return DataStructureBuiltin.of(listSort, l.toArray(new Term[l.size()]));
            } else if (op.equals("__") && (sort.equals("NeBag") || sort.equals(Sort.BAG.toString()) || sort.equals("[Bag]"))) {
                assertXMLTerm(list.size() >= 2);
                List<Term> l = new ArrayList<Term>();
                for (Element elem : list) {
                    l.add(parseXML(elem, context));
                }
                return new Bag(l);
            } else if (op.equals("__") && (sort.equals("NeSet") || sort.equals(Sort.SET.toString()) || sort.equals("[Set]"))) {
                assertXMLTerm(list.size() >= 2);
                List<Term> l = new ArrayList<Term>();
                for (Element elem : list) {
                    l.add(parseXML(elem, context));
                }
                return DataStructureBuiltin.of(setSort, l.toArray(new Term[l.size()]));
            } else if (op.equals("__") && (sort.equals("NeMap") || sort.equals(Sort.MAP.toString()) || sort.equals("[Map]"))) {
                assertXMLTerm(list.size() >= 2);
                List<Term> l = new ArrayList<Term>();
                for (Element elem : list) {
                    l.add(parseXML(elem, context));
                }
                return DataStructureBuiltin.of(mapSort, l.toArray(new Term[l.size()]));
            } else if ((op.equals("#_") || op.equals("Bag2KLabel_") || op.equals("KList2KLabel_") || op.equals("KLabel2KLabel_")) && (sort.equals(Sort.KLABEL.toString()) || sort.equals("[KLabel]"))) {
                assertXMLTerm(list.size() == 1);
                Term term = parseXML(list.get(0), context);
                if (op.equals("#_") && term instanceof Token) {
                    return term;
                } else {
                    return new KInjectedLabel(term);
                }
            } else if (op.equals("List2KLabel_") || op.equals("Map2KLabel_") || op.equals("Set2KLabel_")) {
                assertXMLTerm(list.size() == 1);
                return parseXML(list.get(0), context);
            } else if (sort.equals("#NzInt") && op.equals("--Int_")) {
                assertXMLTerm(list.size() == 1);
                return IntBuiltin.of("-" + ((IntBuiltin) parseXML(list.get(0), context)).value());
            } else if (sort.equals("#NzNat") && op.equals("sNat_")) {
                assertXMLTerm(list.size() == 1 && parseXML(list.get(0), context).equals(IntBuiltin.ZERO_TOKEN));
                return IntBuiltin.of(xml.getAttribute("number"));
            } else if (sort.equals("#Zero") && op.equals("0")) {
                assertXMLTerm(list.size() == 0);
                return IntBuiltin.ZERO_TOKEN;
            } else if (sort.equals("#Bool") && (op.equals("true") || op.equals("false"))) {
                assertXMLTerm(list.size() == 0);
                return BoolBuiltin.of(op);
            } else if (sort.equals("#Char") || sort.equals("#String")) {
                assertXMLTerm(list.size() == 0);
                assertXMLTerm(op.startsWith("\"") && op.endsWith("\""));
                return StringBuiltin.of(StringUtil.unquoteCString(op));
            } else if (op.equals("#token") && sort.equals(Sort.KLABEL.toString())) {
                // #token(String, String)
                assertXMLTerm(list.size() == 2);
                StringBuiltin sortString = (StringBuiltin) parseXML(list.get(0), context);
                StringBuiltin valueString = (StringBuiltin) parseXML(list.get(1), context);
                return GenericToken.of(Sort.of(sortString.stringValue()), valueString.stringValue());
            } else if (sort.equals("#FiniteFloat")) {
                assertXMLTerm(list.size() == 0);
                return FloatBuiltin.of(Double.parseDouble(op));
            } else if (emptyPattern.matcher(op).matches() && (sort.equals(Sort.BAG.toString()) || sort.equals(Sort.LIST.toString()) || sort.equals(Sort.MAP.toString()) || sort.equals(Sort.SET.toString()) || sort.equals(Sort.K.toString()))) {
                assertXMLTerm(list.size() == 0);
                if (sort.equals(Sort.BAG.toString())) {
                    return Bag.EMPTY;
                } else if (sort.equals(Sort.LIST.toString())) {
                    return DataStructureBuiltin.empty(listSort);
                } else if (sort.equals(Sort.MAP.toString())) {
                    return DataStructureBuiltin.empty(mapSort);
                } else if (sort.equals(Sort.SET.toString())) {
                    return DataStructureBuiltin.empty(setSort);
                } else {
                    return KSequence.EMPTY;
                }
            } else if (op.equals(".KList") && sort.equals(Sort.KLIST.toString())) {
                assertXMLTerm(list.size() == 0);
                return KList.EMPTY;
            } else if (op.equals("_`(_`)") && (sort.equals(Sort.KITEM.toString()) || sort.equals("[KList]"))) {
                assertXMLTerm(list.size() == 2);
                Term child = parseXML(list.get(1), context);
                if (!(child instanceof KList)) {
                    List<Term> terms = new ArrayList<Term>();
                    terms.add(child);
                    child = new KList(terms);
                }
                Term label = parseXML(list.get(0), context);
                if (label instanceof MapBuiltin || label instanceof SetBuiltin || label instanceof ListBuiltin) {
                    return label;
                }
                return new KApp(label,child);
            } else if (sort.equals(Sort.KLABEL.toString()) && list.size() == 0) {
                return KLabelConstant.of(StringUtil.unescapeMaude(op));
            } else if (sort.equals(Sort.KLABEL.toString()) && op.equals("#freezer_")) {
                assertXMLTerm(list.size() == 1);
                return new FreezerLabel(parseXML(list.get(0), context));
            } else if (op.equals("HOLE")) {
                assertXMLTerm(list.size() == 0 && sort.equals(Sort.KITEM.toString()));
                //return new Hole(sort);
                return Hole.KITEM_HOLE;
            } else if (op.matches(".*:.*") && op.endsWith(sort) && context.getAllSorts().contains(Sort.of(sort))) {
                return new Variable(op.substring(0, op.indexOf(":")), Sort.of(sort));
            } else {
                Set<Production> prods = context.klabels.get(StringUtil.unescapeMaude(op));
                Set<Production> validProds = new HashSet<>();
                List<Term> possibleTerms = new ArrayList<Term>();
                for (Production p : prods) {
                    if (p.getSort().getName().equals(sort) && p.getArity() == list.size()) {
                        validProds.add(p);
                    }
                }
                assertXMLTerm(validProds.size() > 0);
                List<Term> contents = new ArrayList<Term>();
                for (Element elem : list) {
                    contents.add(parseXML(elem, context));
                }
                for (Production prod : validProds) {
                    possibleTerms.add(new TermCons(Sort.of(sort), contents, prod));
                }
                if (possibleTerms.size() == 1) {
                    return possibleTerms.get(0);
                } else {
                    return new Ambiguity(Sort.of(sort), possibleTerms);
                }
            }
        } catch (InvalidMaudeXMLException e) {
            return new BackendTerm(Sort.of(sort), flattenXML(xml));
        }
    }

    public static String flattenXML(Element xml) {
        List<Element> children = XmlUtil.getChildElements(xml);
        if (children.size() == 0) {
            return xml.getAttribute("op");
        } else {
            String result = xml.getAttribute("op");
            if (!xml.getAttribute("number").equals("")) {
                result += "^" + xml.getAttribute("number");
            }
            String conn = "(";
            for (Element child : children) {
                result += conn;
                conn = ",";
                result += flattenXML(child);
            }
            result += ")";
            return result;
        }
    }

    private static String getSearchType(SearchType s) {
        if (s == SearchType.ONE) return "1";
        if (s == SearchType.PLUS) return "+";
        if (s == SearchType.STAR) return "*";
        if (s == SearchType.FINAL) return "!";
        throw new NullPointerException("null SearchType");
    }

    @Override
    public KRunResult<SearchResults> search(Integer bound, Integer depth,
                                        SearchType searchType, Rule pattern,
                                        Term cfg,
                                        RuleCompilerSteps compilationInfo)
            throws KRunExecutionException {
        StringBuilder cmd = new StringBuilder();
        if (options.experimental.trace) {
          cmd.append("set trace on .\n");
        }
        cmd.append("set show command off .\n").append(setCounter()).append("search ");
        if (bound != null && depth != null) {
            cmd.append("[").append(bound).append(",").append(depth).append("] ");
        } else if (bound != null) {
            cmd.append("[").append(bound).append("] ");
        } else if (depth != null) {
            cmd.append("[,").append(depth).append("] ");
        }
        MaudeFilter maudeFilter = new MaudeFilter(context, kem);
        maudeFilter.visitNode(cfg);
        cmd.append(maudeFilter.getResult()).append(" ");
        MaudeFilter patternBody = new MaudeFilter(context, kem);
        patternBody.visitNode(pattern.getBody());
        String patternString = "=>" + getSearchType(searchType) + " " + patternBody.getResult();
        //TODO: consider replacing Requires with Ensures here.
        if (pattern.getRequires() != null) {
            MaudeFilter patternCondition = new MaudeFilter(context, kem);
            patternCondition.visitNode(pattern.getRequires());
            patternString += " such that " + patternCondition.getResult() + " = # true(.KList)";
        }
        cmd.append(patternString).append(" .");
        boolean showGraph = options.graph || options.experimental.debugger();
        if (showGraph) {
            cmd.append("\nshow search graph .");
        }
        cmd.append(getCounter());
        executeKRun(cmd);
        SearchResults results;
        final List<SearchResult> solutions = parseSearchResults
                (pattern, compilationInfo);
        final boolean matches = patternString.trim().matches("=>[!*1+] " +
                "<_>_</_>\\(generatedTop, B:Bag, generatedTop\\)");
        DirectedGraph<KRunState, Transition> graph = (showGraph) ? parseSearchGraph() : null;
        results = new SearchResults(solutions, graph, matches);
        KRunResult<SearchResults> result = new KRunResult<SearchResults>(results);
        result.setRawOutput(files.loadFromTemp(outFile));
        return result;
    }

    private DirectedGraph<KRunState, Transition> parseSearchGraph() {
        try (
            Scanner scanner = new Scanner(files.resolveTemp(xmlOutFile));
            Writer writer = new OutputStreamWriter(new BufferedOutputStream(
                new FileOutputStream(processedXmlOutFile)))) {

            scanner.useDelimiter("\n");
            while (scanner.hasNext()) {
                String text = scanner.next();
                text = text.replaceAll("<data key=\"((rule)|(term))\">", "<data key=\"$1\"><![CDATA[");
                text = text.replaceAll("</data>", "]]></data>");
                writer.write(text, 0, text.length());
            }
        } catch (IOException e) {
            throw KExceptionManager.internalError("Could not read from " + xmlOutFile
                    + " and write to " + processedXmlOutFile, e);
        }

        Document doc = XmlUtil.readXMLFromFile(processedXmlOutFile.getAbsolutePath());
        NodeList list;
        Node nod;
        list = doc.getElementsByTagName("graphml");
        assertXML(list.getLength() == 1);
        nod = list.item(0);
        assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
        XmlUtil.serializeXML(nod, processedXmlOutFile.getAbsolutePath());

        Transformer<GraphMetadata, DirectedGraph<KRunState, Transition>> graphTransformer = new Transformer<GraphMetadata, DirectedGraph<KRunState, Transition>>() {
            @Override
            public DirectedGraph<KRunState, Transition> transform(GraphMetadata g) {
                return new KRunGraph();
            }
        };
        Transformer<NodeMetadata, KRunState> nodeTransformer = new Transformer<NodeMetadata, KRunState>() {
            @Override
            public KRunState transform(NodeMetadata n) {
                String nodeXmlString = n.getProperty("term");
                Element xmlTerm = XmlUtil.readXMLFromString(nodeXmlString).getDocumentElement();
                KRunState ret = parseElement(xmlTerm, context);
                String id = n.getId();
                id = id.substring(1);
                return ret;
            }
        };
        Transformer<EdgeMetadata, Transition> edgeTransformer = new Transformer<EdgeMetadata, Transition>() {
            @Override
            public Transition transform(EdgeMetadata e) {
                String edgeXmlString = e.getProperty("rule");
                Element elem = XmlUtil.readXMLFromString(edgeXmlString).getDocumentElement();
                String metadataAttribute = elem.getAttribute("metadata");
                Pattern pattern = Pattern.compile("([a-z]*)=\\((.*?)\\)");
                Matcher matcher = pattern.matcher(metadataAttribute);
                String location = null;
                String filename = null;
                while (matcher.find()) {
                    String name = matcher.group(1);
                    if (name.equals("location"))
                        location = matcher.group(2);
                    if (name.equals("filename"))
                        filename = matcher.group(2);
                }
                if (location == null || location.equals("generated") || filename == null) {
                    // we should avoid this wherever possible, but as a quick fix for the
                    // superheating problem and to avoid blowing things up by accident when
                    // location information is missing, I am creating non-RULE edges.
                    String labelAttribute = elem.getAttribute("label");
                    if (labelAttribute == null) {
                        return Transition.unlabelled();
                    } else {
                        return Transition.label(labelAttribute);
                    }
                }
                return Transition.rule(def.locations.get(filename + ":(" + location + ")"));
            }
        };

        Transformer<HyperEdgeMetadata, Transition> hyperEdgeTransformer = new Transformer<HyperEdgeMetadata, Transition>() {
            @Override
            public Transition transform(HyperEdgeMetadata h) {
                throw new RuntimeException("Found a hyper-edge. Has someone been tampering with our intermediate files?");
            }
        };

        try (Reader processedMaudeOutputReader
                 = new BufferedReader(new FileReader(processedXmlOutFile))) {
            GraphMLReader2<DirectedGraph<KRunState, Transition>, KRunState, Transition> graphmlParser
                = new GraphMLReader2<>(processedMaudeOutputReader, graphTransformer, nodeTransformer,
                edgeTransformer, hyperEdgeTransformer);
            return graphmlParser.readGraph();
        } catch (GraphIOException e) {
            throw KExceptionManager.internalError("Failed to parse graphml from maude", e);
        } catch (IOException e) {
            throw KExceptionManager.internalError("Failed to read from " + processedXmlOutFile, e);
        }
    }

    private List<SearchResult> parseSearchResults(Rule pattern, RuleCompilerSteps compilationInfo) {
        List<SearchResult> results = new ArrayList<SearchResult>();
        Document doc = XmlUtil.readXMLFromFile(files.resolveTemp(xmlOutFile).getAbsolutePath());
        NodeList list;
        Node nod;
        list = doc.getElementsByTagName("search-result");
        for (int i = 0; i < list.getLength(); i++) {
            nod = list.item(i);
            assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
            Element elem = (Element) nod;
            if (elem.getAttribute("solution-number").equals("NONE")) {
                continue;
            }
            Integer.parseInt(elem.getAttribute("state-number"));
            Map<String, Term> rawSubstitution = new HashMap<String, Term>();
            NodeList assignments = elem.getElementsByTagName("assignment");
            for (int j = 0; j < assignments.getLength(); j++) {
                nod = assignments.item(j);
                assertXML(nod != null && nod.getNodeType() == Node.ELEMENT_NODE);
                elem = (Element) nod;
                List<Element> child = XmlUtil.getChildElements(elem);
                assertXML(child.size() == 2);
                Term result = parseXML(child.get(1), context);
                String var = child.get(0).getAttribute("op");
                rawSubstitution.put(var.substring(0, var.indexOf(':')), result);
            }

            Term rawResult = (Term) new SubstitutionFilter(rawSubstitution, context)
                    .visitNode(pattern.getBody());
            KRunState state = new KRunState(rawResult, stateCounter);
            SearchResult result = new SearchResult(state, rawSubstitution, compilationInfo);
            results.add(result);
        }
        list = doc.getElementsByTagName("result");
        nod = list.item(1);
        parseCounter(nod);
        return results;
    }


}
