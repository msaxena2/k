package org.kframework.kil;

import org.kframework.kil.loader.JavaClassesFactory;
import org.kframework.kil.matchers.Matcher;
import org.kframework.kil.visitors.Transformer;
import org.kframework.kil.visitors.Visitor;
import org.kframework.kil.visitors.exceptions.TransformerException;
import org.kframework.utils.xml.XML;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;


/**
 * AST representation a term of sort K explicitly constructed as an application of a KLabel to a KList.
 */
public class KApp extends Term {
	/**
     * A KLabel represented as a non-null instance of {@link KLabel}, {@link Variable} of sort KLabel, or {@link Ambiguity}.
     */
	private Term label;
	/**
     * A KList represented as a non-null instance of {@link KList}, {@link Variable} of sort KList, or {@link Ambiguity}.
     */
	private Term child;

    /**
     * Constructs the application of the given KLabel to a KList with the given elements.
     *
     * @param label the KLabel which is applied to a KList with the given elements. A non-null instance of {@link KLabel}, {@link Variable} of sort KLabel or {@link Ambiguity}.
     * @param elements the elements of the KList.
     * @return a {@link KApp} which represents the application of the given KLabel to a KList with the given elements.
     */
    public static KApp of(Term label, Term ... elements) {
        return new KApp(label, new KList(Arrays.asList(elements)));
    }

    /**
     * Constructs the application of the specified KLabel to the specified KList.
     *
     * @param location the line and column
     * @param filename the complete name of the file
     * @param label the KLabel which is applied to the given KList. A non-null instance of {@link KLabel}, {@link Variable} of sort KLabel or {@link Ambiguity}.
     * @param child the KList which the given KLabel is applied to. A non-null instance of {@link KList}, {@link Variable} of sort KList, or {@link Ambiguity}.
     * @return a {@link KApp} which represents the application of the KLabel argument to the KList argument.
     */
	public KApp(String location, String filename, Term label, Term child) {
		super(location, filename, "K");
        setLabel(label);
        setChild(child);
	}

    /**
     * Constructs the application of the specified KLabel to the specified KList.
     *
     * @param label the KLabel which is applied to the given KList. A non-null instance of {@link KLabel}, {@link Variable} of sort KLabel or {@link Ambiguity}.
     * @param child the KList which the given KLabel is applied to. A non-null instance of {@link KList}, {@link Variable} of sort KList, or {@link Ambiguity}.
     * @return a {@link KApp} which represents the application of the KLabel argument to the KList argument.
     */
	public KApp(Term label, Term child) {
		super("K");
        setLabel(label);
        setChild(child);
	}

    /**
     * Constructs a {@link KApp} object from an XML element.
     */
	public KApp(Element element) {
		super(element);
        List<Element> childrenElements = XML.getChildrenElements(element);
        Element body = XML.getChildrenElements(childrenElements.get(0)).get(0);
        setLabel((Term) JavaClassesFactory.getTerm(body));
        Term term = (Term) JavaClassesFactory.getTerm(childrenElements.get(1));
        if (!(term.getSort().equals(KSorts.KLIST) || term instanceof Ambiguity)) {
            setChild(new KList(Collections.<Term>singletonList(term)));
        } else {
            setChild(term);
        }
	}

	private KApp(KApp node) {
		super(node);
        setLabel(node.label);
        setChild(node.child);
	}

    @Override
	public String toString() {
		return label + "(" + child + ")";
	}

	public Term getLabel() {
		return label;
	}

    /**
     * Sets the KLabel of this K application.
     *
     * @param label the KLabel represented as a non-null instance of {@link KLabel}, {@link Variable} of sort KLabel or {@link Ambiguity}.
     */
	public void setLabel(Term label) {
        assert label != null;
        assert label.getSort().equals(KSorts.KLABEL) || child instanceof Ambiguity:
                "unexpected sort " + label.getSort() + " of KApp first argument " + label + ";"
                        + " expected KLabel";

		this.label = label;
	}

	public Term getChild() {
		return child;
	}

    /**
     * Sets the KLabel of this K application.
     *
     * @param child the KList represented as a non-null instance of {@link KList}, {@link Variable} of sort KList, or {@link Ambiguity}
     */
	public void setChild(Term child) {
        assert child != null;
        assert child.getSort().equals(KSorts.KLIST) || child instanceof Ambiguity:
                "unexpected sort " + child.getSort() + " of KApp second argument " + child + ";"
                        + "; expected KList";

		this.child = child;
	}

	@Override
	public void accept(Visitor visitor) {
		visitor.visit(this);
	}

	@Override
	public ASTNode accept(Transformer transformer) throws TransformerException {
		return transformer.transform(this);
	}

    @Override
        public void accept(Matcher matcher, Term toMatch){
        matcher.match(this, toMatch);
    }

	@Override
	public KApp shallowCopy() {
		return new KApp(this);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof KApp)) return false;
		KApp k = (KApp)o;
		return label.equals(k.label) && child.equals(k.child);
	}

	@Override
	public boolean contains(Object o) {
		if (o instanceof Bracket)
			return contains(((Bracket)o).getContent());
		if (o instanceof Cast)
			return contains(((Cast)o).getContent());
		if (!(o instanceof KApp)) return false;
		KApp k = (KApp)o;
		return label.contains(k.label) && child.contains(k.child);
	}


	@Override
	public int hashCode() {
		return label.hashCode() * 23 + child.hashCode();
	}

}