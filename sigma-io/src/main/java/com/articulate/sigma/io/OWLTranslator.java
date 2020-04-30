package com.articulate.sigma.io;

import com.articulate.sigma.*;
import com.articulate.sigma.wn.WordNet;
import com.articulate.sigma.wn.WordNetUtilities;

import java.io.*;
import java.util.*;

/* This code is copyright Articulate Software (c) 2010.
This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
Users of this code also consent, by use of this code, to credit Articulate Software
in any writings, briefings, publications, presentations, or 
other representations of any software which incorporates, builds on, or uses this 
code.  Please cite the following article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment, 
in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico. See also sigmakee.sourceforge.net
*/

/**
 * Read and write OWL format from Sigma data structures.
 */
public class OWLTranslator
{
	/**
	 * Sync lock
	 */
	private static final Object LOCK = new Object();

	public KB kb;

	/**
	 * A map of functional statements and the automatically generated term that is created for it.
	 */
	private final Map<String, String> functionTable = new HashMap<>();

	/**
	 * Axioms
	 */
	public final SortedMap<String, Formula> axiomMap = new TreeMap<>();

	/**
	 * Keys are SUMO term name Strings, values are YAGO/DBPedia term name Strings.
	 */
	private final Map<String, String> SUMOYAGOMap = new HashMap<>();

	/**
	 * Process strings fro XML output
	 */
	private static String processStringForXMLOutput(String s)
	{
		if (s == null)
			return null;
		s = s.replaceAll("<", "&lt;");
		s = s.replaceAll(">", "&gt;");
		s = s.replaceAll("&", "&amp;");
		return s;
	}

	/**
	 *
	 */
	private static String processStringForKIFOutput(String s)
	{
		if (s == null)
			return null;
		return s.replaceAll("\"", "&quot;");
	}

	/**
	 * Remove quotes around a string
	 */
	public static String removeQuotes(String s)
	{
		if (s == null)
			return null;
		s = s.trim();
		if (s.length() < 1)
			return s;
		if (s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
			s = s.substring(1, s.length() - 1);
		return s;
	}

	/**
	 * Turn a function statement into an identifier.
	 */
	private String instantiateFunction(String s)
	{
		String result = removeQuotes(s);
		result = result.substring(1, s.length() - 1);  // remove outer parens
		result = StringToKIFid(result);
		functionTable.put(s, result);
		return result;
	}

	/**
	 * State definitional information for automatically defined terms that replace function statements.
	 */
	private void defineFunctionalTerms(PrintWriter pw)
	{
		for (String functionTerm : functionTable.keySet())
		{
			String term = functionTable.get(functionTerm);

			Formula f = new Formula();
			f.set(functionTerm);
			String func = f.getArgument(0);

			List<Formula> ranges = kb.askWithRestriction(0, "range", 1, func);
			String range;
			if (ranges.size() > 0)
			{
				Formula f2 = ranges.get(0);
				range = f2.getArgument(2);
				pw.println("<owl:Thing rdf:about=\"#" + term + "\">");
				pw.println("  <rdf:type rdf:resource=\"" + (range.equals("Entity") ? "&owl;Thing" : "#" + range) + "\"/>");
				pw.println("  <rdfs:comment>A term generated automatically in the " + "translation from SUO-KIF to OWL to replace the functional " + "term "
						+ functionTerm + " that connect be directly " + "expressed in OWL. </rdfs:comment>");
				pw.println("</owl:Thing>");
				pw.println();
			}
			else
			{
				List<Formula> subranges = kb.askWithRestriction(0, "rangeSubclass", 1, functionTerm);
				if (subranges.size() > 0)
				{
					Formula f2 = subranges.get(0);
					range = f2.getArgument(2);
					pw.println("<owl:Class rdf:about=\"#" + term + "\">");
					pw.println("  <rdfs:subClassOf rdf:resource=\"" + (range.equals("Entity") ? "&owl;Thing" : "#" + range) + "\"/>");
					pw.println("  <rdfs:comment>A term generated automatically in the " + "translation from SUO-KIF to OWL to replace the functional " + "term "
							+ functionTerm + " that connect be directly " + "expressed in OWL. </rdfs:comment>");
					pw.println("</owl:Class>");
					pw.println();
				}
				else
					return;
			}
		}
	}

	/**
	 * Convert an arbitrary string to a legal KIF identifier by
	 * substituting dashes for illegal characters. ToDo:
	 * isJavaIdentifierPart() isn't sufficient, since it allows
	 * characters KIF doesn't
	 */
	public static String StringToKIFid(String s)
	{
		if (s == null)
			return null;
		s = s.trim();
		if (s.length() < 1)
			return s;
		if (s.length() > 1 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"')
			s = s.substring(1, s.length() - 1);
		if (s.charAt(0) != '?' && (!Character.isJavaIdentifierStart(s.charAt(0)) || s.charAt(0) > 122))
			s = "S" + s.substring(1);
		int i = 1;
		while (i < s.length())
		{
			if (!Character.isJavaIdentifierPart(s.charAt(i)) || s.charAt(i) > 122)
				s = s.substring(0, i) + "-" + s.substring(i + 1);
			i++;
		}
		return s;
	}

	/**
	 *
	 */
	private static String getParentReference(SimpleElement se)
	{
		String value = null;
		List<SimpleElement> children = se.getChildElements();
		if (children.size() > 0)
		{
			SimpleElement child = children.get(0);
			if (child.getTagName().equals("owl:Class"))
			{
				value = child.getAttribute("rdf:ID");
				if (value == null)
					value = child.getAttribute("rdf:about");
				if (value != null && value.contains("#"))
					value = value.substring(value.indexOf("#") + 1);
			}
		}
		else
		{
			value = se.getAttribute("rdf:resource");
			if (value != null)
			{
				if (value.contains("#"))
					value = value.substring(value.indexOf("#") + 1);
			}
		}
		return StringToKIFid(value);
	}

	/**
	 * Read OWL format and write out KIF.
	 */
	private static void decode(PrintWriter pw, SimpleElement se, String parentTerm, String indent)
	{
		String tag = se.getTagName();
		String value = null;
		String existential = null;
		String parens = null;
		switch (tag)
		{
			case "owl:Class":
			case "owl:ObjectProperty":
			case "owl:DatatypeProperty":
			case "owl:InverseFunctionalProperty":
			case "owl:TransitiveProperty":
			case "owl:SymmetricProperty":
			case "rdf:Description":
				parentTerm = se.getAttribute("rdf:ID");
				if (parentTerm != null)
				{
					if (parentTerm.contains("#"))
						parentTerm = parentTerm.substring(parentTerm.indexOf("#") + 1);
				}
				else
				{
					parentTerm = se.getAttribute("rdf:about");
					if (parentTerm != null)
					{
						if (parentTerm.contains("#"))
							parentTerm = parentTerm.substring(parentTerm.indexOf("#") + 1);
					}
					else
					{
						// pw.println(";; nodeID? ");
						parentTerm = se.getAttribute("rdf:nodeID");
						if (parentTerm != null)
						{
							parentTerm = "?nodeID-" + parentTerm;
							existential = parentTerm;
						}
					}
				}
				parentTerm = StringToKIFid(parentTerm);
				if (parentTerm != null)
				{
					if (tag.equals("owl:ObjectProperty") || tag.equals("owl:DatatypeProperty") || tag.equals("owl:InverseFunctionalProperty"))
						pw.println(indent + "(instance " + parentTerm + " BinaryRelation)");
					if (tag.equals("owl:TransitiveProperty"))
						pw.println(indent + "(instance " + parentTerm + " TransitiveRelation)");
					if (tag.equals("owl:SymmetricProperty"))
						pw.println(indent + "(instance " + parentTerm + " SymmetricRelation)");
				}
				break;
			case "owl:FunctionalProperty":
				value = se.getAttribute("rdf:about");
				if (value != null)
				{
					if (value.contains("#"))
						value = value.substring(value.indexOf("#") + 1);
					value = StringToKIFid(value);
					pw.println(indent + "(instance " + value + " SingleValuedRelation)");
					//pw.println(indent + "(instance " + parentTerm + " SingleValuedRelation)");
				}
				break;
			case "rdfs:domain":
				value = se.getAttribute("rdf:resource");
				if (value != null)
				{
					if (value.contains("#"))
						value = value.substring(value.indexOf("#") + 1);
					value = StringToKIFid(value);
					if (value != null && parentTerm != null)
						pw.println(indent + "(domain " + parentTerm + " 1 " + value + ")");
				}
				break;
			case "rdfs:range":
				value = se.getAttribute("rdf:resource");
				if (value != null)
				{
					if (value.contains("#"))
						value = value.substring(value.indexOf("#") + 1);
					value = StringToKIFid(value);
					if (value != null && parentTerm != null)
						pw.println(indent + "(domain " + parentTerm + " 2 " + value + ")");
				}
				break;
			case "rdfs:comment":
			{
				String text = se.getText();
				text = processStringForKIFOutput(text);
				if (parentTerm != null && text != null)
					pw.println(DB.wordWrap(indent + "(documentation " + parentTerm + " EnglishLanguage \"" + text + "\")", 70));
				break;
			}
			case "rdfs:label":
			{
				String text = se.getText();
				text = processStringForKIFOutput(text);
				if (parentTerm != null && text != null)
					pw.println(DB.wordWrap(indent + "(termFormat EnglishLanguage " + parentTerm + " \"" + text + "\")", 70));
				break;
			}
			case "owl:inverseOf":
				List<SimpleElement> children = se.getChildElements();
				if (children.size() > 0)
				{
					SimpleElement child = children.get(0);
					if (child.getTagName().equals("owl:ObjectProperty") || child.getTagName().equals("owl:InverseFunctionalProperty"))
					{
						value = child.getAttribute("rdf:ID");
						if (value == null)
							value = child.getAttribute("rdf:about");
						if (value == null)
							value = child.getAttribute("rdf:resource");
						if (value != null && value.contains("#"))
							value = value.substring(value.indexOf("#") + 1);
					}
				}
				value = StringToKIFid(value);
				if (value != null && parentTerm != null)
					pw.println(indent + "(inverse " + parentTerm + " " + value + ")");
				break;
			case "rdfs:subClassOf":
				value = getParentReference(se);
				value = StringToKIFid(value);
				if (value != null)
					pw.println(indent + "(subclass " + parentTerm + " " + value + ")");
				else
					pw.println(";; missing or unparsed subclass statement for " + parentTerm);
				break;
			case "owl:Restriction":
			case "owl:maxCardinality":
			case "owl:minCardinality":
			case "owl:cardinality":
			case "owl:onProperty":
				break;
			case "owl:unionOf":
			case "owl:intersectionOf":
			case "owl:complementOf":
				return;
			case "rdf:type":
				value = getParentReference(se);
				value = StringToKIFid(value);
				if (value != null)
					pw.println(indent + "(instance " + parentTerm + " " + value + ")");
				else
					pw.println(";; missing or unparsed subclass statement for " + parentTerm);
				break;
			default:
				value = se.getAttribute("rdf:resource");
				if (value != null)
				{
					if (value.contains("#"))
						value = value.substring(value.indexOf("#") + 1);
					value = StringToKIFid(value);
					tag = StringToKIFid(tag);
					if (value != null && parentTerm != null)
						pw.println(indent + "(" + tag + " " + parentTerm + " " + value + ")");
				}
				else
				{
					String text = se.getText();
					String datatype = se.getAttribute("rdf:datatype");
					text = processStringForKIFOutput(text);
					if (datatype == null || (!datatype.endsWith("integer") && !datatype.endsWith("decimal")))
						text = "\"" + text + "\"";
					tag = StringToKIFid(tag);
					if (!DB.emptyString(text) && !text.equals("\"\""))
					{
						if (parentTerm != null && tag != null)
							pw.println(indent + "(" + tag + " " + parentTerm + " " + text + ")");
					}
					else
					{
						List<SimpleElement> children2 = se.getChildElements();
						if (children2.size() > 0)
						{
							SimpleElement child = children2.get(0);
							if (child.getTagName().equals("owl:Class"))
							{
								value = child.getAttribute("rdf:ID");
								if (value == null)
									value = child.getAttribute("rdf:about");
								if (value != null && value.contains("#"))
									value = value.substring(value.indexOf("#") + 1);
								if (value != null && parentTerm != null)
									pw.println(indent + "(" + tag + " " + parentTerm + " " + value + ")");
							}
						}
					}
				}
				break;
		}
		if (existential != null)
		{
			pw.println("(exists (" + existential + ") ");
			if (se.getChildElements().size() > 1)
			{
				pw.println("  (and ");
				indent = indent + "    ";
				parens = "))";
			}
			else
			{
				indent = indent + "  ";
				parens = ")";
			}
		}
		Set<String> s = se.getAttributeNames();
		for (String att : s)
		{
			se.getAttribute(att);
		}
		List<SimpleElement> al = se.getChildElements();
		for (SimpleElement child : al)
		{
			decode(pw, child, parentTerm, indent);
		}
		if (existential != null)
		{
			pw.println(parens);
		}
	}

	/**
	 * Read OWL format.
	 */
	public static void translateTo(String filename) throws IOException
	{
		try(PrintWriter pw = new PrintWriter(new FileWriter(filename + ".kif")))
		{
			SimpleElement se = SimpleDOMParser.readFile(filename);
			decode(pw, se, "", "");
		}
		catch (java.io.IOException e)
		{
			throw new IOException("Error writing file " + filename + " " + e.getMessage());
		}
	}

	/**
	 * Remove special characters in documentation.
	 */
	private static String processDoc(String doc)
	{
		String result = doc;
		result = result.replaceAll("&%", "");
		result = result.replaceAll("&", "&#38;");
		result = result.replaceAll(">", "&gt;");
		result = result.replaceAll("<", "&lt;");
		result = removeQuotes(result);
		return result;
	}

	/**
	 *
	 */
	private void writeTermFormat(PrintWriter pw, String term)
	{
		List<Formula> al = kb.askWithRestriction(0, "termFormat", 2, term);
		if (al.size() > 0)
		{
			for (Formula form : al)
			{
				String lang = form.getArgument(1);
				if (lang.equals("EnglishLanguage"))
					lang = "en";
				String st = form.getArgument(3);
				st = removeQuotes(st);
				pw.println("  <rdfs:label xml:lang=\"" + lang + "\">" + st + "</rdfs:label>");
			}
		}
	}

	/**
	 * Write synonymous
	 */
	private void writeSynonymous(PrintWriter pw, String term, String termType)
	{
		List<Formula> syn = kb.askWithRestriction(0, "synonymousExternalConcept", 2, term);
		if (syn.size() > 0)
		{
			for (Formula form : syn)
			{
				String st = form.getArgument(1);
				st = StringToKIFid(st);
				String lang = form.getArgument(3);
				String entity = lang.equals("Entity") ? "&owl;Thing" : "#" + lang;
				switch (termType)
				{
					case "relation":
						pw.println("  <owl:equivalentProperty rdf:resource=\"" + entity + ":" + st + "\" />");
						break;
					case "instance":
						pw.println("  <owl:sameAs rdf:resource=\"" + entity + ":" + st + "\" />");
						break;
					case "class":
						pw.println("  <owl:equivalentClass rdf:resource=\"" + entity + ":" + st + "\" />");
						break;
				}
			}
		}
	}

	/**
	 *
	 */
	private void writeAxiomLinks(PrintWriter pw, String term)
	{
		List<Formula> al = kb.ask("ant", 0, term);
		for (Formula f : al)
		{
			String st = f.createID();
			pw.println("  <kbd:axiom rdf:resource=\"#axiom-" + st + "\"/>");
		}
		al = kb.ask("cons", 0, term);
		for (Formula f : al)
		{
			String st = f.createID();
			pw.println("  <kbd:axiom rdf:resource=\"#axiom-" + st + "\"/>");
		}
		//pw.println("  <fullDefinition rdf:datatype=\"&xsd;anyURI\">" +
		//           "http://sigma.ontologyportal.org:4010/sigma/Browse.jsp?lang=EnglishLanguage&kb=SUMO&term=" +
		//           term + "</fullDefinition>");
	}

	/**
	 *
	 */
	private void writeWordNetLink(PrintWriter pw, String term)
	{
		WordNet.initOnce();
		// get list of synsets with part of speech prepended to the synset number.
		List<String> al = WordNet.wn.SUMOHash.get(term);
		if (al != null)
		{
			for (String synset : al)
			{
				String termMapping = null;
				// GetSUMO terms with the &% prefix and =, +, @ or [ suffix.
				switch (synset.charAt(0))
				{
					case '1':
						termMapping = WordNet.wn.nounSUMOHash.get(synset.substring(1));
						break;
					case '2':
						termMapping = WordNet.wn.verbSUMOHash.get(synset.substring(1));
						break;
					case '3':
						termMapping = WordNet.wn.adjectiveSUMOHash.get(synset.substring(1));
						break;
					case '4':
						termMapping = WordNet.wn.adverbSUMOHash.get(synset.substring(1));
						break;
				}
				String rel = null;
				if (termMapping != null)
				{
					switch (termMapping.charAt(termMapping.length() - 1))
					{
						case '=':
							rel = "equivalenceRelation";
							break;
						case '+':
							rel = "subsumingRelation";
							break;
						case '@':
							rel = "instanceRelation";
							break;
						case ':':
							rel = "antiEquivalenceRelation";
							break;
						case '[':
							rel = "antiSubsumingRelation";
							break;
						case ']':
							rel = "antiInstanceRelation";
							break;
					}
				}
				pw.println("  <wnd:" + rel + " rdf:resource=\"&wnd;WN30-" + synset + "\"/>");
			}
		}
	}

	/**
	 *
	 */
	private void writeAxioms(PrintWriter pw)
	{
		SortedSet<Formula> ts = new TreeSet<>(kb.formulaMap.values());
		for (Formula f : ts)
		{
			if (f.isRule())
			{
				String form = f.toString();
				form = form.replaceAll("<=>", "iff");
				form = form.replaceAll("=>", "implies");
				form = processDoc(form);
				pw.println("<owl:Thing rdf:about=\"#axiom-" + f.createID() + "\">");
				pw.println("  <rdfs:comment xml:lang=\"en\">A SUO-KIF axiom that may not be directly expressible in OWL. "
						+ "See www.ontologyportal.org for the original SUO-KIF source.\n " + form + "</rdfs:comment>");
				pw.println("</owl:Thing>");
			}
		}
	}

	/**
	 * Write one axiom
	 */
	private void writeOneAxiom(PrintWriter pw, String id)
	{
		Formula f = axiomMap.get(id);
		if (f != null && f.isRule())
		{
			String form = f.toString();
			form = form.replaceAll("<=>", "iff");
			form = form.replaceAll("=>", "implies");
			form = processDoc(form);
			pw.println("<owl:Thing rdf:about=\"#" + id + "\">");
			pw.println("  <rdfs:comment xml:lang=\"en\">A SUO-KIF axiom that may not be directly expressible in OWL. "
					+ "See www.ontologyportal.org for the original SUO-KIF source.\n " + form + "</rdfs:comment>");
			pw.println("</owl:Thing>");
		}
		else
			System.err.println("ERROR in OWLTranslator.writeOneAxiom(): null or non-axiom for ID: " + id);
	}

	/**
	 * Write documentation
	 */
	private void writeDocumentation(PrintWriter pw, String term)
	{
		List<Formula> doc = kb.askWithRestriction(0, "documentation", 1, term);    // Class expressions for term.
		if (doc.size() > 0)
		{
			for (Formula form : doc)
			{
				String lang = form.getArgument(2);
				String documentation = form.getArgument(3);
				String langString = "";
				if (lang.equals("EnglishLanguage"))
					langString = " xml:lang=\"en\"";
				if (documentation != null)
					pw.println("  <rdfs:comment" + langString + ">" + StringUtil.wordWrap(processDoc(documentation)) + "</rdfs:comment>");
			}
		}
	}

	/**
	 *
	 */
	private void writeYAGOMapping(PrintWriter pw, String term)
	{
		String YAGO = SUMOYAGOMap.get(term);
		if (YAGO != null)
		{
			pw.println("  <owl:sameAs rdf:resource=\"http://dbpedia.org/resource/" + YAGO + "\" />");
			pw.println("  <owl:sameAs rdf:resource=\"http://yago-knowledge.org/resource/" + YAGO + "\" />");
			pw.println("  <rdfs:seeAlso rdf:resource=\"http://en.wikipedia.org/wiki/" + YAGO + "\" />");
		}
	}

	/**
	 * Write OWL format.
	 */
	private void writeRelations(PrintWriter pw, String term)
	{
		String propType = "ObjectProperty";
		if (kb.childOf(term, "SymmetricRelation"))
			propType = "SymmetricProperty";
		else if (kb.childOf(term, "TransitiveRelation"))
			propType = "TransitiveProperty";
		else if (kb.childOf(term, "Function"))
			propType = "FunctionalProperty";

		pw.println("<owl:" + propType + " rdf:about=\"#" + term + "\">");
		List<Formula> argTypes = kb.askWithRestriction(0, "domain", 1, term);  // domain expressions for term.
		List<Formula> subs = kb.askWithRestriction(0, "subrelation", 1, term);  // subrelation expressions for term.
		if (argTypes.size() > 0)
		{
			for (Formula f : argTypes)
			{
				String arg = f.getArgument(2);
				String argType = f.getArgument(3);
				String entity = argType.equals("Entity") ? "&owl;Thing" : "#" + argType;
				if (arg.equals("1") && Formula.atom(argType))
					pw.println("  <rdfs:domain rdf:resource=\"" + entity + "\" />");
				if (arg.equals("2") && Formula.atom(argType))
					pw.println("  <rdfs:range rdf:resource=\"" + entity + "\" />");
			}
		}

		List<Formula> ranges = kb.askWithRestriction(0, "range", 1, term);  // domain expressions for term.
		if (ranges.size() > 0)
		{
			Formula form = ranges.get(0);
			String argType = form.getArgument(2);
			if (Formula.atom(argType))
				pw.println("  <rdfs:range rdf:resource=\"" + (argType.equals("Entity") ? "&owl;Thing" : "#" + argType) + "\" />");
		}

		List<Formula> inverses = kb.askWithRestriction(0, "inverse", 1, term);  // inverse expressions for term.
		if (inverses.size() > 0)
		{
			Formula form = inverses.get(0);
			String arg = form.getArgument(2);
			if (Formula.atom(arg))
				pw.println("  <owl:inverseOf rdf:resource=\"" + (arg.equals("Entity") ? "&owl;Thing" : "#" + arg) + "\" />");
		}

		if (subs.size() > 0)
		{
			for (Formula f : subs)
			{
				String superProp = f.getArgument(2);
				pw.println("  <owl:subPropertyOf rdf:resource=\"" + (superProp.equals("Entity") ? "&owl;Thing" : "#" + superProp) + "\" />");
			}
		}
		writeDocumentation(pw, term);
		writeSynonymous(pw, term, "relation");
		writeYAGOMapping(pw, term);
		writeTermFormat(pw, term);
		writeAxiomLinks(pw, term);
		writeWordNetLink(pw, term);
		pw.println("</owl:" + propType + ">");
		pw.println();
	}

	/**
	 * Write instances
	 */
	private void writeInstances(PrintWriter pw, String term, List<Formula> instances)
	{
		pw.println("<owl:Thing rdf:about=\"#" + term + "\">");
		pw.println("  <rdfs:isDefinedBy rdf:resource=\"http://www.ontologyportal.org/SUMO.owl\"/>");
		for (Formula form : instances)
		{
			String parent = form.getArgument(2);
			if (Formula.atom(parent))
				pw.println("  <rdf:type rdf:resource=\"" + (parent.equals("Entity") ? "&owl;Thing" : "#" + parent) + "\"/>");
		}
		writeDocumentation(pw, term);

		List<Formula> statements = kb.ask("arg", 1, term);
		for (Formula form : statements)
		{
			String rel = form.getArgument(0);
			if (!rel.equals("instance") && !rel.equals("subclass") && !rel.equals("documentation") && !rel.equals("subrelation") && kb
					.childOf(rel, "BinaryRelation"))
			{
				String range = form.getArgument(2);
				if (range == null || "".equals(range))
				{
					System.err.println("ERROR in OWLTranslator.writeInstance(): missing range in statement: " + form);
					continue;
				}
				if (Formula.listP(range))
					range = instantiateFunction(range);
				if (range.charAt(0) == '"' && range.charAt(range.length() - 1) == '"')
				{
					range = removeQuotes(range);
					if (range.startsWith("http://"))
						pw.println("  <" + rel + " rdf:datatype=\"&xsd;anyURI\">" + range + "</" + rel + ">");

					else
						pw.println("  <" + rel + " rdf:datatype=\"&xsd;string\">" + range + "</" + rel + ">");
				}
				else if (((range.charAt(0) == '-' && Character.isDigit(range.charAt(1))) || (Character.isDigit(range.charAt(0)))) && !range.contains("."))
					pw.println("  <" + rel + " rdf:datatype=\"&xsd;integer\">" + range + "</" + rel + ">");
				else
					pw.println("  <" + rel + " rdf:resource=\"" + (range.equals("Entity") ? "&owl;Thing" : "#" + range) + "\" />");
			}
		}

		writeSynonymous(pw, term, "instance");
		writeTermFormat(pw, term);
		writeAxiomLinks(pw, term);
		writeYAGOMapping(pw, term);
		writeWordNetLink(pw, term);
		pw.println("</owl:Thing>");
		pw.println();
	}

	/**
	 * Write classes
	 */
	private void writeClasses(PrintWriter pw, String term, List<Formula> classes)
	{
		pw.println("<owl:Class rdf:about=\"#" + term + "\">");
		pw.println("  <rdfs:isDefinedBy rdf:resource=\"http://www.ontologyportal.org/SUMO.owl\"/>");
		for (Formula form : classes)
		{
			String parent = form.getArgument(2);
			if (Formula.atom(parent))
				pw.println("  <rdfs:subClassOf rdf:resource=\"" + (parent.equals("Entity") ? "&owl;Thing" : "#" + parent) + "\"/>");
		}
		writeDocumentation(pw, term);

		List<Formula> statements = kb.ask("arg", 1, term);
		for (Formula form : statements)
		{
			String rel = form.getArgument(0);
			if (!rel.equals("instance") && !rel.equals("subclass") && !rel.equals("documentation") && !rel.equals("subrelation") && kb
					.childOf(rel, "BinaryRelation"))
			{
				String range = form.getArgument(2);
				if (Formula.listP(range))
					range = instantiateFunction(range);
				if (rel.equals("disjoint"))
					pw.println("  <owl:disjointWith rdf:resource=\"" + (range.equals("Entity") ? "&owl;Thing" : "#" + range) + "\" />");
				else //noinspection StatementWithEmptyBody
					if (rel.equals("synonymousExternalConcept"))
					{
						// since argument order is reversed between OWL and SUMO, this must be handled below
					}
					else if (range.charAt(0) == '"' && range.charAt(range.length() - 1) == '"')
					{
						range = removeQuotes(range);
						if (range.startsWith("http://"))
							pw.println("  <" + rel + " rdf:datatype=\"&xsd;anyURI\">" + range + "</" + rel + ">");

						else
							pw.println("  <" + rel + " rdf:datatype=\"&xsd;string\">" + range + "</" + rel + ">");
					}
					else if (((range.charAt(0) == '-' && Character.isDigit(range.charAt(1))) || (Character.isDigit(range.charAt(0)))) && !range.contains("."))
						pw.println("  <" + rel + " rdf:datatype=\"&xsd;integer\">" + range + "</" + rel + ">");
					else
						pw.println("  <" + rel + " rdf:resource=\"" + (range.equals("Entity") ? "&owl;Thing" : "#" + range) + "\" />");
			}
		}
		List<Formula> syn = kb.askWithRestriction(0, "synonymousExternalConcept", 2, term);
		if (syn.size() > 0)
		{
			for (Formula form : syn)
			{
				String st = form.getArgument(1);
				st = StringToKIFid(st);
				String lang = form.getArgument(3);
				pw.println("  <owl:equivalentClass rdf:resource=\"" + (lang.equals("Entity") ? "&owl;Thing" : "#" + lang) + ":" + st + "\" />");
			}
		}
		writeSynonymous(pw, term, "class");
		writeTermFormat(pw, term);
		writeYAGOMapping(pw, term);
		writeAxiomLinks(pw, term);
		writeWordNetLink(pw, term);
		pw.println("</owl:Class>");
		pw.println();
	}

	/**
	 * Read a mapping file from YAGO to SUMO terms and store in SUMOYAGOMap
	 */
	private void readYAGOSUMOMappings()
	{
		FileReader r = null;
		LineNumberReader lr = null;
		try
		{
			String kbDir = KBManager.getMgr().getPref("kbDir");
			File f = new File(kbDir + File.separator + "yago-sumo-mappings.txt");
			if (!f.canRead())
			{
				System.err.println(
						"WARNING in readYAGOSUMOMappings(): " + "The mappings file " + kbDir + File.separator + "yago-sumo-mappings.txt does not exist");
				return;
			}
			r = new FileReader(f);
			lr = new LineNumberReader(r);
			String line;
			while ((line = lr.readLine()) != null)
			{
				line = line.trim();
				if (!line.isEmpty() && line.charAt(0) != '#')
				{
					String yAGO = line.substring(0, line.indexOf(" "));
					String sUMO = line.substring(line.indexOf(" ") + 1);
					SUMOYAGOMap.put(sUMO, yAGO);
				}
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			try
			{
				if (lr != null)
					lr.close();
			}
			catch (Exception e)
			{
				System.err.println("ERROR: Exception in OWLTranslator.readYAGOSUMOMappings()");
				System.err.println(e.getMessage());
			}
			try
			{
				if (r != null)
					r.close();
			}
			catch (Exception e)
			{
				System.err.println("ERROR: Exception in OWLTranslator.readYAGOSUMOMappings()");
				System.err.println(e.getMessage());
			}
		}
	}

	/**
	 * Write OWL file header.
	 */
	private void writeKBHeader(PrintWriter pw)
	{
		pw.println("<!DOCTYPE rdf:RDF [");
		pw.println("   <!ENTITY wnd \"http://www.ontologyportal.org/WNDefs.owl#\">");
		pw.println("   <!ENTITY kbd \"http://www.ontologyportal.org/KBDefs.owl#\">");
		pw.println("   <!ENTITY xsd \"http://www.w3.org/2001/XMLSchema#\">");
		pw.println("   <!ENTITY rdf \"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">");
		pw.println("   <!ENTITY rdfs \"http://www.w3.org/2000/01/rdf-schema#\">");
		pw.println("   <!ENTITY owl \"http://www.w3.org/2002/07/owl#\">");
		pw.println("]>");
		pw.println("<rdf:RDF");
		pw.println("xmlns=\"http://www.ontologyportal.org/SUMO.owl#\"");
		pw.println("xml:base=\"http://www.ontologyportal.org/SUMO.owl\"");
		pw.println("xmlns:wnd=\"http://www.ontologyportal.org/WNDefs.owl#\"");
		pw.println("xmlns:kbd=\"http://www.ontologyportal.org/KBDefs.owl#\"");
		pw.println("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\"");
		pw.println("xmlns:rdf =\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
		pw.println("xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"");
		pw.println("xmlns:owl =\"http://www.w3.org/2002/07/owl#\">");

		pw.println("<owl:Ontology rdf:about=\"http://www.ontologyportal.org/SUMO.owl\">");
		pw.println("<rdfs:comment xml:lang=\"en\">A provisional and necessarily lossy translation to OWL.  Please see");
		pw.println("www.ontologyportal.org for the original KIF, which is the authoritative");
		pw.println("source.  This software is released under the GNU Public License");
		pw.println("www.gnu.org.</rdfs:comment>");

		Date d = new Date();
		pw.println("<rdfs:comment xml:lang=\"en\">Produced on date: " + d.toString() + "</rdfs:comment>");
		pw.println("</owl:Ontology>");
	}

	/**
	 * Write OWL format.
	 */
	public void writeSUMOTerm(PrintWriter pw, String term)
	{
		if (kb.childOf(term, "BinaryRelation") && kb.isInstance(term))
			writeRelations(pw, term);
		if (Character.isUpperCase(term.charAt(0)))
		{
			List<Formula> instances = kb.askWithRestriction(0, "instance", 1, term);  // Instance expressions for term.
			List<Formula> classes = kb.askWithRestriction(0, "subclass", 1, term);    // Class expressions for term.
			if (instances.size() > 0 && !kb.childOf(term, "BinaryRelation"))
				writeInstances(pw, term, instances);
			// boolean isInstance = false;
			if (classes.size() > 0)
			{
				// if (instances.size() > 0)
				//	isInstance = true;
				writeClasses(pw, term, classes);
			}
		}
	}

	/**
	 * Write OWL format.
	 */
	public void writeKB(final PrintStream ps)
	{
		readYAGOSUMOMappings();
		try (PrintWriter pw = new PrintWriter(ps))
		{
			writeKBHeader(pw);

			Set<String> kbTerms = kb.getTerms();
			synchronized (LOCK)
			{
				for (String term : kbTerms)
				{
					writeSUMOTerm(pw, term);
					pw.flush();
				}
			}
			defineFunctionalTerms(pw);
			writeAxioms(pw);
			pw.println("</rdf:RDF>");
		}
	}

	/**
	 * Write WordNet class definitions
	 */
	private void writeWordNetClassDefinitions(PrintWriter pw)
	{
		List<String> WordNetClasses = new ArrayList<>(Arrays.asList("Synset", "NounSynset", "VerbSynset", "AdjectiveSynset", "AdverbSynset"));
		for (String term : WordNetClasses)
		{
			pw.println("<owl:Class rdf:about=\"#" + term + "\">");
			pw.println("  <rdfs:label xml:lang=\"en\">" + term + "</rdfs:label>");
			if (!term.equals("Synset"))
			{
				pw.println("  <rdfs:subClassOf rdf:resource=\"#Synset\"/>");
				String POS = term.substring(0, term.indexOf("Synset"));
				pw.println("  <rdfs:comment xml:lang=\"en\">A group of " + POS + "s having the same meaning.</rdfs:comment>");
			}
			else
			{
				pw.println("  <rdfs:comment xml:lang=\"en\">A group of words having the same meaning.</rdfs:comment>");
			}
			pw.println("</owl:Class>");
		}
		pw.println("<owl:Class rdf:about=\"#WordSense\">");
		pw.println("  <rdfs:label xml:lang=\"en\">word sense</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A particular sense of a word.</rdfs:comment>");
		pw.println("</owl:Class>");
		pw.println("<owl:Class rdf:about=\"#Word\">");
		pw.println("  <rdfs:label xml:lang=\"en\">word</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A particular word.</rdfs:comment>");
		pw.println("</owl:Class>");
		pw.println("<owl:Class rdf:about=\"#VerbFrame\">");
		pw.println("  <rdfs:label xml:lang=\"en\">verb frame</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A string template showing allowed form of use of a verb.</rdfs:comment>");
		pw.println("</owl:Class>");
	}

	/**
	 * Write verb frames
	 */
	private void writeVerbFrames(PrintWriter pw)
	{
		List<String> verbFrames = new ArrayList<>(
				Arrays.asList("Something ----s", "Somebody ----s", "It is ----ing", "Something is ----ing PP", "Something ----s something Adjective/Noun",
						"Something ----s Adjective/Noun", "Somebody ----s Adjective", "Somebody ----s something", "Somebody ----s somebody",
						"Something ----s somebody", "Something ----s something", "Something ----s to somebody", "Somebody ----s on something",
						"Somebody ----s somebody something", "Somebody ----s something to somebody", "Somebody ----s something from somebody",
						"Somebody ----s somebody with something", "Somebody ----s somebody of something", "Somebody ----s something on somebody",
						"Somebody ----s somebody PP", "Somebody ----s something PP", "Somebody ----s PP", "Somebody's (body part) ----s",
						"Somebody ----s somebody to INFINITIVE", "Somebody ----s somebody INFINITIVE", "Somebody ----s that CLAUSE",
						"Somebody ----s to somebody", "Somebody ----s to INFINITIVE", "Somebody ----s whether INFINITIVE",
						"Somebody ----s somebody into V-ing something", "Somebody ----s something with something", "Somebody ----s INFINITIVE",
						"Somebody ----s VERB-ing", "It ----s that CLAUSE", "Something ----s INFINITIVE"));

		for (int i = 0; i < verbFrames.size(); i++)
		{
			String frame = verbFrames.get(i);
			String numString = String.valueOf(i);
			if (numString.length() == 1)
				numString = "0" + numString;
			pw.println("<owl:Thing rdf:about=\"#WN30VerbFrame-" + numString + "\">");
			pw.println("  <rdfs:comment xml:lang=\"en\">" + frame + "</rdfs:comment>");
			pw.println("  <rdfs:label xml:lang=\"en\">" + frame + "</rdfs:label>");
			pw.println("  <rdf:type rdf:resource=\"#VerbFrame\"/>");
			pw.println("</owl:Thing>");
		}
	}

	/**
	 * Write WordNet relation definitions
	 */
	private void writeWordNetRelationDefinitions(PrintWriter pw)
	{
		List<String> WordNetRelations = new ArrayList<>(
				Arrays.asList("antonym", "hypernym", "instance-hypernym", "hyponym", "instance-hyponym", "member-holonym", "substance-holonym", "part-holonym",
						"member-meronym", "substance-meronym", "part-meronym", "attribute", "derivationally-related", "domain-topic", "member-topic",
						"domain-region", "member-region", "domain-usage", "member-usage", "entailment", "cause", "also-see", "verb-group", "similar-to",
						"participle", "pertainym"));
		for (String rel : WordNetRelations)
		{
			String tag;
			if (rel.equals("antonym") || rel.equals("similar-to") || rel.equals("verb-group") || rel.equals("derivationally-related"))
				tag = "owl:SymmetricProperty";
			else
				tag = "owl:ObjectProperty";
			pw.println("<" + tag + " rdf:about=\"#" + rel + "\">");
			pw.println("  <rdfs:label xml:lang=\"en\">" + rel + "</rdfs:label>");
			pw.println("  <rdfs:domain rdf:resource=\"#Synset\" />");
			pw.println("  <rdfs:range rdf:resource=\"#Synset\" />");
			pw.println("</" + tag + ">");
		}

		pw.println("<owl:ObjectProperty rdf:about=\"#word\">");
		pw.println("  <rdfs:domain rdf:resource=\"#Synset\" />");
		pw.println("  <rdfs:range rdf:resource=\"rdfs:Literal\" />");
		pw.println("  <rdfs:label xml:lang=\"en\">word</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A relation between a WordNet synset and a word\n" + "which is a member of the synset.</rdfs:comment>");
		pw.println("</owl:ObjectProperty>");

		pw.println("<owl:ObjectProperty rdf:about=\"#singular\">");
		pw.println("  <rdfs:domain rdf:resource=\"#Word\" />");
		pw.println("  <rdfs:range rdf:resource=\"rdfs:Literal\" />");
		pw.println("  <rdfs:label xml:lang=\"en\">singular</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A relation between a WordNet synset and a word\n" + "which is a member of the synset.</rdfs:comment>");
		pw.println("</owl:ObjectProperty>");

		pw.println("<owl:ObjectProperty rdf:about=\"#infinitive\">");
		pw.println("  <rdfs:domain rdf:resource=\"#Word\" />");
		pw.println("  <rdfs:range rdf:resource=\"rdfs:Literal\" />");
		pw.println("  <rdfs:label xml:lang=\"en\">infinitive</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A relation between a word\n" + " in its past tense and infinitive form.</rdfs:comment>");
		pw.println("</owl:ObjectProperty>");

		pw.println("<owl:ObjectProperty rdf:about=\"#senseKey\">");
		pw.println("  <rdfs:domain rdf:resource=\"#Word\" />");
		pw.println("  <rdfs:range rdf:resource=\"#WordSense\" />");
		pw.println("  <rdfs:label xml:lang=\"en\">sense key</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A relation between a word\n" + "and a particular sense of the word.</rdfs:comment>");
		pw.println("</owl:ObjectProperty>");

		pw.println("<owl:ObjectProperty rdf:about=\"#synset\">");
		pw.println("  <rdfs:domain rdf:resource=\"#WordSense\" />");
		pw.println("  <rdfs:range rdf:resource=\"#Synset\" />");
		pw.println("  <rdfs:label xml:lang=\"en\">synset</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A relation between a sense of a particular word\n" + "and the synset in which it appears.</rdfs:comment>");
		pw.println("</owl:ObjectProperty>");

		pw.println("<owl:ObjectProperty rdf:about=\"#verbFrame\">");
		pw.println("  <rdfs:domain rdf:resource=\"#WordSense\" />");
		pw.println("  <rdfs:range rdf:resource=\"#VerbFrame\" />");
		pw.println("  <rdfs:label xml:lang=\"en\">verb frame</rdfs:label>");
		pw.println("  <rdfs:comment xml:lang=\"en\">A relation between a verb word sense and a template that\n"
				+ "describes the use of the verb in a sentence.</rdfs:comment>");
		pw.println("</owl:ObjectProperty>");
	}

	/**
	 * Write OWL format for SUMO-WordNet mappings.
	 *
	 * @param synset is a POS prefixed synset number
	 */
	private void writeWordNetSynset(PrintWriter pw, String synset)
	{
		if (synset.startsWith("WN30-"))
			synset = synset.substring(5);
		List<String> al = WordNet.wn.synsetsToWords.get(synset);
		if (al != null)
		{
			pw.println("<owl:Thing rdf:about=\"#WN30-" + synset + "\">");
			String parent = "Noun";
			switch (synset.charAt(0))
			{
				case '1':
					parent = "NounSynset";
					break;
				case '2':
					parent = "VerbSynset";
					break;
				case '3':
					parent = "AdjectiveSynset";
					break;
				case '4':
					parent = "AdverbSynset";
					break;
			}
			pw.println("  <rdf:type rdf:resource=\"" + "&wnd;" + parent + "\"/>");
			if (al.size() > 0)
				pw.println("  <rdfs:label>" + al.get(0) + "</rdfs:label>");
			for (String word : al)
			{
				String wordAsID = StringToKIFid(word);
				pw.println("  <wnd:word rdf:resource=\"#WN30Word-" + wordAsID + "\"/>");
			}
			String doc = null;
			switch (synset.charAt(0))
			{
				case '1':
					doc = WordNet.wn.nounDocumentationHash.get(synset.substring(1));
					break;
				case '2':
					doc = WordNet.wn.verbDocumentationHash.get(synset.substring(1));
					break;
				case '3':
					doc = WordNet.wn.adjectiveDocumentationHash.get(synset.substring(1));
					break;
				case '4':
					doc = WordNet.wn.adverbDocumentationHash.get(synset.substring(1));
					break;
			}
			doc = processStringForXMLOutput(doc);
			pw.println("  <rdfs:comment xml:lang=\"en\">" + doc + "</rdfs:comment>");
			List<AVPair> al2 = WordNet.wn.relations.get(synset);
			if (al2 != null)
			{
				for (AVPair avp : al2)
				{
					String rel = StringToKIFid(avp.attribute);
					pw.println("  <wnd:" + rel + " rdf:resource=\"#WN30-" + avp.value + "\"/>");
				}
			}
			pw.println("</owl:Thing>");
		}
	}

	/**
	 * Write word exceptions
	 */
	private void writeWordNetExceptions(PrintWriter pw)
	{
		Iterator<String> it = WordNet.wn.exceptionNounHash.keySet().iterator();
		while (it.hasNext())
		{
			String plural = it.next();
			String singular = WordNet.wn.exceptionNounHash.get(plural);
			pw.println("<owl:Thing rdf:about=\"#" + plural + "\">");
			pw.println("  <wnd:singular>" + singular + "</wnd:singular>");
			pw.println("  <rdf:type rdf:resource=\"#Word\"/>");
			pw.println("  <rdfs:label xml:lang=\"en\">" + singular + "</rdfs:label>");
			pw.println("  <rdfs:comment xml:lang=\"en\">\"" + singular + "\", is the singular form" + " of the irregular plural \"" + plural
					+ "\"</rdfs:comment>");
			pw.println("</owl:Thing>");
		}
		Iterator<String> it2 = WordNet.wn.exceptionVerbHash.keySet().iterator();
		while (it.hasNext())
		{
			String past = it2.next();
			String infinitive = WordNet.wn.exceptionVerbHash.get(past);
			pw.println("<owl:Thing rdf:about=\"#" + past + "\">");
			pw.println("  <wnd:infinitive>" + infinitive + "</wnd:infinitive>");
			pw.println("  <rdf:type rdf:resource=\"#Word\"/>");
			pw.println("  <rdfs:label xml:lang=\"en\">" + past + "</rdfs:label>");
			pw.println("  <rdfs:comment xml:lang=\"en\">\"" + past + "\", is the irregular past tense form" + " of the infinitive \"" + infinitive
					+ "\"</rdfs:comment>");
			pw.println("</owl:Thing>");
		}
	}

	/**
	 *
	 */
	private void writeOneWordToSenses(PrintWriter pw, String word)
	{
		String wordAsID = StringToKIFid(word);
		pw.println("<owl:Thing rdf:about=\"#WN30Word-" + wordAsID + "\">");
		pw.println("  <rdf:type rdf:resource=\"#Word\"/>");
		pw.println("  <rdfs:label xml:lang=\"en\">" + word + "</rdfs:label>");
		String wordOrPhrase = "word";
		if (word.contains("_"))
			wordOrPhrase = "phrase";
		pw.println("  <rdfs:comment xml:lang=\"en\">The English " + wordOrPhrase + " \"" + word + "\".</rdfs:comment>");
		List<String> senses = WordNet.wn.wordsToSenses.get(word);
		if (senses != null)
		{
			for (String sense : senses)
			{
				pw.println("  <wnd:senseKey rdf:resource=\"#WN30WordSense-" + sense + "\"/>");
			}
		}
		else
			System.err.println("ERROR in OWLTranslator.writeOneWordToSenses(): no senses for word: " + word);
		pw.println("</owl:Thing>");
	}

	/**
	 * Write words to senses
	 */
	private void writeWordsToSenses(PrintWriter pw)
	{
		for (String word : WordNet.wn.wordsToSenses.keySet())
		{
			writeOneWordToSenses(pw, word);
		}
	}

	/**
	 * Write sense index
	 */
	private void writeSenseIndex(PrintWriter pw)
	{
		for (String sense : WordNet.wn.senseIndex.keySet())
		{
			String synset = WordNet.wn.senseIndex.get(sense);
			pw.println("<owl:Thing rdf:about=\"#WN30WordSense-" + sense + "\">");
			pw.println("  <rdf:type rdf:resource=\"#WordSense\"/>");
			pw.println("  <rdfs:label xml:lang=\"en\">" + sense + "</rdfs:label>");
			pw.println("  <rdfs:comment xml:lang=\"en\">The WordNet word sense \"" + sense + "\".</rdfs:comment>");
			String pos = WordNetUtilities.getPOSFromKey(sense);
			String word = WordNetUtilities.getWordFromKey(sense);
			String posNum = WordNetUtilities.posLettersToNumber(pos);
			pw.println("  <wnd:synset rdf:resource=\"#WN30-" + posNum + synset + "\"/>");
			if (posNum.equals("2"))
			{
				List<String> frames = WordNet.wn.verbFrames.get(synset + "-" + word);
				if (frames != null)
				{
					for (String frame : frames)
					{
						pw.println("  <wnd:verbFrame rdf:resource=\"#WN30VerbFrame-" + frame + "\"/>");
					}
				}
			}
			pw.println("</owl:Thing>");
		}
	}

	/**
	 * Write header
	 */
	private void writeWordNetHeader(PrintWriter pw)
	{
		pw.println("<!DOCTYPE rdf:RDF [");
		pw.println("   <!ENTITY wnd \"http://www.ontologyportal.org/WNDefs.owl#\">");
		pw.println("   <!ENTITY kbd \"http://www.ontologyportal.org/KBDefs.owl#\">");
		pw.println("   <!ENTITY xsd \"http://www.w3.org/2001/XMLSchema#\">");
		pw.println("   <!ENTITY rdf \"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">");
		pw.println("   <!ENTITY rdfs \"http://www.w3.org/2000/01/rdf-schema#\">");
		pw.println("   <!ENTITY owl \"http://www.w3.org/2002/07/owl#\">");
		pw.println("]>");
		pw.println("<rdf:RDF");
		pw.println("xmlns=\"http://www.ontologyportal.org/WordNet.owl#\"");
		pw.println("xml:base=\"http://www.ontologyportal.org/WordNet.owl\"");
		pw.println("xmlns:wnd =\"http://www.ontologyportal.org/WNDefs.owl#\"");
		pw.println("xmlns:kbd =\"http://www.ontologyportal.org/KBDefs.owl#\"");
		pw.println("xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\"");
		pw.println("xmlns:rdf =\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
		pw.println("xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"");
		pw.println("xmlns:owl =\"http://www.w3.org/2002/07/owl#\">");

		pw.println("<owl:Ontology rdf:about=\"http://www.ontologyportal.org/WordNet.owl\">");
		pw.println("<rdfs:comment xml:lang=\"en\">An expression of the Princeton WordNet " + "( http://wordnet.princeton.edu ) "
				+ "in OWL.  Use is subject to the Princeton WordNet license at " + "http://wordnet.princeton.edu/wordnet/license/</rdfs:comment>");
		Date d = new Date();
		pw.println("<rdfs:comment xml:lang=\"en\">Produced on date: " + d.toString() + "</rdfs:comment>");
		pw.println("</owl:Ontology>");
	}
}


