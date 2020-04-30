/* This code is copyrighted by Articulate Software (c) 2007.  It is
released under the GNU Public License
&lt;http://www.gnu.org/copyleft/gpl.html&gt;.  Users of this code also
consent, by use of this code, to credit Articulate Software in any
writings, briefings, publications, presentations, or other representations
of any software which incorporates, builds on, or uses this code.  Please
cite the following article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment, in Working
Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico.  See also http://sigmakee.sourceforge.net
*/

package com.articulate.sigma.io;

import com.articulate.sigma.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * A class to generate simplified HTML-based documentation for SUO-KIF terms.
 */
public class DocGen
{
	private static final String LS = StringUtil.getLineSeparator();

	/**
	 * This String token denotes Sigma's "simple" HTML layout, and is
	 * used as a flag in the HTML generation code to switch between
	 * full and simple modes.
	 */
	protected static final String F_SI = "si";

	protected static final List<String> F_CONTROL_TOKENS = new ArrayList<>();

	static
	{
		F_CONTROL_TOKENS.add(F_SI);
	}

	/**
	 * The default base plus file suffix name for the main index file
	 * for a set of HTML output files.
	 */
	protected static final String INDEX_FILE_NAME = "index.html";

	protected static final String DEFAULT_KEY = "docgen_default";

	protected static final Hashtable<String, DocGen> DOC_GEN_INSTANCES = new Hashtable<>();

	public static DocGen getInstance()
	{
		DocGen inst = DOC_GEN_INSTANCES.get(DEFAULT_KEY);
		if (inst == null)
		{
			inst = new DocGen();
			DOC_GEN_INSTANCES.put(DEFAULT_KEY, inst);
		}
		return inst;
	}

	/**
	 * Get instance
	 */
	public static DocGen getInstance(String compositeKey)
	{
		KBManager mgr = KBManager.getMgr();
		String interned = compositeKey.intern();
		DocGen inst = DOC_GEN_INSTANCES.get(interned);
		if (inst == null)
		{
			inst = new DocGen();
			int idx = interned.indexOf("-");
			KB kb = ((idx > -1) ? mgr.getKB(interned.substring(0, idx).trim()) : mgr.getKB(interned));
			if (kb != null)
				inst.setKB(kb);
			String ontology = null;
			if ((idx > 0) && (idx < (interned.length() - 1)))
				ontology = interned.substring(idx + 1).trim();
			if (isEmpty(ontology))
			{
				ontology = inst.getOntology(kb);
			}
			if (!ontology.isEmpty())
			{
				inst.setOntology(ontology);
				inst.setDefaultNamespace(inst.getDefaultNamespace());
				inst.setDefaultPredicateNamespace(inst.getDefaultPredicateNamespace());
			}
			inst.setDocGenKey(interned);
			DOC_GEN_INSTANCES.put(interned, inst);
		}
		return inst;
	}

	/**
	 * To obtain an instance of DocGen, use the static factory method
	 * getInstance().
	 */
	protected DocGen()
	{
	}

	/**
	 * The default namespace associated with this DocGen object
	 */
	protected String defaultNamespace = "";

	/**
	 * Returns the String denoting the default namespace
	 * associated with this DocGen object.
	 */
	public String getDefaultNamespace()
	{
		if (isEmpty(this.defaultNamespace))
		{
			// If no value has been set, check to see if a value
			// is stored in the KB.
			KB kb = this.getKB();
			String onto = this.getOntology();
			setDefaultNamespace(!onto.isEmpty() ? kb.getFirstTermViaAskWithRestriction(0, "docGenDefaultNamespace", 1, onto, 2) : null);
		}
		return this.defaultNamespace;
	}

	/**
	 * Sets the default namespace for this DocGen object.
	 */
	public void setDefaultNamespace(String namespace)
	{
		this.defaultNamespace = namespace;
	}

	/**
	 * The default namespace for predicates in the ontology associated
	 * with this DocGen object
	 */
	protected String defaultPredicateNamespace = "";

	/**
	 * Returns the String denoting the default namespace for
	 * predicates in the ontology associated with this DocGen
	 * object.
	 */
	public String getDefaultPredicateNamespace()
	{
		if (isEmpty(this.defaultPredicateNamespace))
		{
			KB kb = getKB();
			String onto = getOntology();
			String dpn = (!onto.isEmpty() ? kb.getFirstTermViaAskWithRestriction(0, "docGenDefaultPredicateNamespace", 1, onto, 2) : null);
			setDefaultPredicateNamespace(dpn);
			if (isEmpty(this.defaultPredicateNamespace))
			{
				setDefaultPredicateNamespace(getDefaultNamespace());
			}
		}
		return this.defaultPredicateNamespace;
	}

	/**
	 * Sets the default namespace for predicates in the ontology
	 * associated with this DB object.
	 */
	public void setDefaultPredicateNamespace(String namespace)
	{
		this.defaultPredicateNamespace = namespace;
	}

	/**
	 * The ontology associated with this DocGen object, and for
	 * which the DocGen object is used to generate files.
	 */
	protected String ontology = null;

	/**
	 * Set ontology
	 */
	public void setOntology(String term)
	{
		this.ontology = term;
	}

	/**
	 * Returns a term denoting the default Ontology for this DocGen
	 * object if an Ontology has been set, and tries very hard to find
	 * a relevant Ontology if one has not been set.
	 */
	public String getOntology()
	{
		String onto = this.ontology;
		if (isEmpty(onto))
		{
			KB kb = this.getKB();
			onto = this.getOntology(kb);
			if (!onto.isEmpty())
			{
				this.setOntology(onto);
			}
		}
		return this.ontology;
	}

	/**
	 * Returns a term denoting the default Ontology for this DocGen
	 * object if an Ontology has been set, and tries very hard to find
	 * a relevant Ontology if one has not been set.
	 */
	public String getOntology(KB kb)
	{
		String onto = null;
		if (!this.ontology.isEmpty())
			onto = this.ontology;
		else
		{
			if (kb == null)
				kb = this.getKB();

			// First, we try to find any obvious instances of Ontology, using predicate subsumption to take
			// advantage of any predicates that have been liked with SUMO's predicates.
			Set<String> candidates = new HashSet<>(kb.getAllInstancesWithPredicateSubsumption("Ontology"));
			if (candidates.isEmpty())
			{
				// Next, we check for explicit
				// ontologyNamespace statements.
				List<Formula> formulae = kb.ask("arg", 0, "ontologyNamespace");
				if ((formulae != null) && !formulae.isEmpty())
				{
					for (Formula f : formulae)
					{
						candidates.add(f.getArgument(1));
					}
				}
			}
			if (!candidates.isEmpty())
			{
				// Here we try to match one of the ontologies
				// to the name of the current KB, since we
				// have no other obvious way to determine
				// which ontology is appropriate if two or
				// more are represented in the KB.  This
				// section probably should use some word/token
				// based partial matching algorithm, but does
				// not.  We just accept the first fairly
				// liberal regex match.
				String kbNamePattern = ".*(?i)" + kb.name + ".*";
				for (String candidate : candidates)
				{
					String ontoPattern = ".*(?i)" + candidate + ".*";
					if (candidate.matches(kbNamePattern) || (kb.name != null && kb.name.matches(ontoPattern)))
					{
						onto = candidate;
						break;
					}
				}
				if (onto == null)
				{
					// Finally, if onto is still null and candidates is not empty, we just grab a/ candidate and try it.
					Iterator<String> it = candidates.iterator();
					if (it.hasNext())
						onto = it.next();
				}
			}
			if (onto != null && !onto.isEmpty())
				this.setOntology(onto);
		}
		return onto;
	}

	/**
	 * The KB associated with this DocGen object.
	 */
	protected KB kb = null;

	/**
	 * Set KB
	 */
	public void setKB(KB kb)
	{
		this.kb = kb;
	}

	/**
	 * Get KB
	 */
	public KB getKB()
	{
		return this.kb;
	}

	/**
	 * A Set of Strings.
	 */
	protected Set<String> codedIdentifiers = null;

	/**
	 * Collects and returns the Set containing all known coded
	 * identifiers in kb, including ISO code values stated to be such.
	 *
	 * @param kb The KB in which to gather terms defined as coded
	 *           identifiers
	 * @return A Set of all the terms that denote ISO code values and
	 * other coded identifiers
	 */
	protected Set<String> getCodedIdentifiers(KB kb)
	{
		if (codedIdentifiers == null)
		{
			codedIdentifiers = new TreeSet<>();
		}
		if (codedIdentifiers.isEmpty())
		{
			Set<String> codes = kb.getAllInstancesWithPredicateSubsumption("CodedIdentifier");
			Set<String> classNames = kb.getAllSubClassesWithPredicateSubsumption("CodedIdentifier");
			classNames.add("CodedIdentifier");
			for (String className : classNames)
			{
				codes.addAll(kb.getTermsViaPredicateSubsumption("instance", 2, className, 1, false));
			}
			codedIdentifiers.addAll(codes);
		}
		return codedIdentifiers;
	}

	/**
	 * The document title text to be used for HTML generation
	 */
	protected final String titleText = "";

	/**
	 * Returns the String that will be used as the title text for HTML
	 * document generation, else returns an empty String if no title
	 * text value has been set.
	 */
	public String getTitleText()
	{
		return titleText;
	}

	/**
	 * The document footer text to be used for HTML generation
	 */
	protected final String footerText = "";
	//"Produced by <a href=\"http://www.articulatesoftware.com\"> " + "Articulate Software</a> and its partners";

	/**
	 * Returns the String that will be used as the footer text for
	 * HTML document generation, else returns an empty String if no
	 * footer text value has been set.
	 */
	public String getFooterText()
	{
		return footerText;
	}

	/**
	 * The style sheet (CSS filename) to be referenced in HTML generation
	 */
	protected final String styleSheet = "simple.css";

	/**
	 * Returns the base filename plus filename suffix form of the
	 * Cascading Style Sheet file to be referenced during HTML
	 * document generation, else returns an empty String if no value
	 * has been set.
	 */
	public String getStyleSheet()
	{
		return styleSheet;
	}

	/**
	 * The default image file (such as an organization's logo) to be
	 * used in HTML generation, wrapped in any necessary additional
	 * markup required for proper display.
	 */
	protected final String defaultImageFileMarkup = "articulate_logo.gif";

	/**
	 * Returns the base filename plus filename suffix form of the logo
	 * image file, wrapped in any additional markup required for the
	 * intended rendering of the image.
	 */
	public String getDefaultImageFileMarkup()
	{
		return defaultImageFileMarkup;
	}

	/**
	 * The canonical pathname of the current directory in which
	 * output files will be (are being) saved.
	 */
	protected String outputDirectoryPath = "";

	/**
	 * Sets the canonical pathname String of the current directory in
	 * which output files will be (are being) saved.
	 *
	 * @param pathname A canonical pathname String
	 */
	public void setOutputDirectoryPath(String pathname)
	{
		outputDirectoryPath = pathname;
	}

	/**
	 * Returns the canonical pathname String of the current directory
	 * in which output files will be (are being) saved.
	 */
	public String getOutputDirectoryPath()
	{
		return this.outputDirectoryPath;
	}

	/**
	 * A Map containing String replacement pairs.  This is to provide
	 * adequate ASCII translations for HTML character entities, in
	 * circumstances where occurrences of the entities might cause
	 * parsing or rendering problems (e.g., apparently, in XSD files).
	 */
	protected Map<String, String> stringReplacementMap = null;

	/**
	 * Sets the Map to be used for HTML character entity to ASCII
	 * replacements.
	 */
	public void setStringReplacementMap(Map<String, String> keyValPairs)
	{
		this.stringReplacementMap = keyValPairs;
	}

	/**
	 * Returns the Map to be used for HTML character entity to ASCII
	 * replacements, attempting to build it from
	 * docGenCodeMapTranslation statements found in the KB if the Map
	 * does not already exist.
	 */
	public Map<String, String> getStringReplacementMap()
	{
		if (stringReplacementMap == null)
		{
			Map<String, String> srMap = new HashMap<>();
			KB kb = getKB();
			if (kb != null)
			{
				List<Formula> formulae = kb.ask("arg", 0, "docGenCodeMapTranslation");
				if (formulae != null)
				{
					for (Formula f : formulae)
					{
						srMap.put(StringUtil.removeEnclosingQuotes(f.getArgument(2)), StringUtil.removeEnclosingQuotes(f.getArgument(4)));
					}
				}
			}
			else
			{
				System.err.println("WARNING in DocGen.getStringReplacementMap()");
				System.err.println("  DocGen.defaultKB is not set");
			}
			if (srMap.isEmpty())
			{
				System.err.println("WARNING in DocGen.getStringReplacementMap()");
				System.err.println("  DocGen.stringReplacementMap is empty");
			}
			setStringReplacementMap(srMap);
		}
		return this.stringReplacementMap;
	}

	/**
	 * A set of the predicates that should not be displayed to the user.
	 */
	protected Set<String> inhibitDisplayRelations = null;

	/**
	 * Sets the predicates for which display should be suppressed to
	 * those contained in relations.
	 *
	 * @param relations A Set of predicate names
	 */
	public void setInhibitDisplayRelations(Set<String> relations)
	{
		this.inhibitDisplayRelations = relations;
	}

	/**
	 * Returns a Set containing the names of those predicates for
	 * which display should be suppressed, and tries to create the Set
	 * from docGenInhibitDisplayRelations statements found in the
	 * current KB if the Set does not already exist.
	 *
	 * @return a Set of predicate names
	 */
	public Set<String> getInhibitDisplayRelations()
	{
		if (inhibitDisplayRelations == null)
		{
			KB kb = getKB();
			String ontology = getOntology();
			Set<String> idr = new TreeSet<>();
			if ((kb != null) && !ontology.isEmpty())
			{
				idr.addAll(kb.getTermsViaAskWithRestriction(0, "docGenInhibitDisplayRelation", 1, ontology, 2));
			}
			setInhibitDisplayRelations(idr);
			if (inhibitDisplayRelations.isEmpty())
			{
				System.err.println("WARNING in DocGen.getInhibitDisplayRelations()");
				System.err.println("  DocGen.inhibitDisplayRelations is empty");
			}
		}
		return inhibitDisplayRelations;
	}

	/**
	 * The header to be used for the the table of contents (or index
	 * list) section during HTML generation.
	 */
	protected String tocHeader = "";

	/**
	 * Sets the String header to be used in generated HTML files to
	 * header.
	 */
	public void setTocHeader(String header)
	{
		this.tocHeader = header;
	}

	/**
	 * Returns the String header to be used in generated HTML files.
	 */
	public String getTocHeader()
	{
		return this.tocHeader;
	}

	/**
	 * A default key to identify this particular DocGen object
	 **/
	public String docGenKey = DEFAULT_KEY;

	/**
	 * Sets the String key that is the index for this particular
	 * DocGen object.
	 */
	public void setDocGenKey(String key)
	{
		this.docGenKey = key;
	}

	/**
	 * If true, a termFormat value obtained for term will be displayed
	 * rather than the term name itself.
	 */
	protected final boolean simplified = false;

	/**
	 * Returns true if a termFormat value obtained for term will be
	 * displayed during HTML rendering rather than the term name
	 * itself.
	 */
	public boolean getSimplified()
	{
		return this.simplified;
	}

	/**
	 * A Map in which each key is a KB name and the corresponding
	 * value is a List of the Predicates defined in the KB.
	 */
	protected final Map<KB, List<String>> relationsByKB = new HashMap<>();

	public Map<KB, List<String>> getRelationsByKB()
	{
		return relationsByKB;
	}

	/**
	 * Returns a String consisting of str concatenated indent times.
	 *
	 * @param str    The String to be concatenated with itself
	 * @param indent An int indicating the number of times str should
	 *               be concatenated
	 * @return A String
	 */
	public static String indentChars(String str, int indent)
	{
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < indent; i++)
		{
			result.append(str);
		}
		return result.toString();
	}

	public interface DisplayFilter
	{
		/**
		 * Returns true if suoKifTerm may be displayed or included in the
		 * particular UI text or other output generated by the DocGen
		 * object dg.
		 *
		 * @param dg         The DocGen object that will use this filter to
		 *                   determine which terms should be displayed or otherwise included
		 *                   in generated output
		 * @param suoKifTerm A term in the SUO-KIF representation
		 *                   language, which could be an atomic constant, a variable, a
		 *                   quoted character string, or a list
		 * @return true or false
		 */
		boolean isLegalForDisplay(DocGen dg, String suoKifTerm);
	}

	/**
	 * The DisplayFilter which, if present, determines if a given
	 * SUO-KIF object may be displayed or output by this DocGen
	 * object.
	 */
	protected DisplayFilter displayFilter = null;

	/**
	 * Returns the DisplayFilter object associated with this DocGen
	 * object, or null if no DisplayFilter has been set.
	 */
	public DisplayFilter getDisplayFilter()
	{
		return this.displayFilter;
	}

	/**
	 * Returns the DisplayFilter object associated with this DocGen
	 * object, or null if no DisplayFilter has been set.
	 */
	public void setDisplayFilter(DisplayFilter df)
	{
		this.displayFilter = df;
	}

	/**
	 * A SortedMap of SortedMaps of Lists where the keys are
	 * uppercase single characters (of term formats or headwords) and
	 * the values are SortedMaps with a key of the term formats or
	 * headwords and List values of the actual term names.  Note
	 * that if "simplified" is false actual term names will be used
	 * instead of term formats or headwords and the interior map will
	 * have keys that are the same as their values.
	 * Pictorially:
	 * letter->    formattedTerm1->term11,term12...term1N
	 * formattedTerm2->term21,term22...term2N
	 */
	protected final Map<String, Map<String, List<String>>> alphaList = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * @return a SortedMap of SortedMaps of Lists where the keys
	 * are uppercase single characters (of term formats or
	 * headwords) and the values are SortedMaps with a key of
	 * the term formats or headwords and List values
	 * of the actual term names.  Note that if "simplified"
	 * is false actual term names will be used instead of
	 * term formats or headwords and the interior map will
	 * have keys that are the same as their values.
	 * Pictorially:
	 * letter->    formattedTerm1->term11,term12...term1N
	 * formattedTerm2->term21,term22...term2N
	 */
	public Map<String, Map<String, List<String>>> getAlphaList(KB kb)
	{
		if (alphaList.isEmpty())
		{
			synchronized (alphaList)
			{
				createAlphaList(kb);
			}
		}
		return alphaList;
	}

	private static final Object LOCK = new Object();

	/**
	 * @return a SortedMap of SortedMaps of Lists where the keys
	 * are uppercase single characters (of term formats or
	 * headwords) and the values are SortedMaps with a key of
	 * the term formats or headwords and List values
	 * of the actual term names.  Note that if "simplified"
	 * is false actual term names will be used instead of
	 * term formats or headwords and the interior map will
	 * have keys that are the same as their values.
	 * Pictorially:
	 * letter->    formattedTerm1->term11,term12...term1N
	 * formattedTerm2->term21,term22...term2N
	 */
	@SuppressWarnings("UnusedReturnValue") protected Map<String, Map<String, List<String>>> createAlphaList(KB kb)
	{
		alphaList.clear();
		Set<String> kbTerms = kb.getTerms();
		synchronized (LOCK)
		{
			for (String term : kbTerms)
			{
				if (isLegalForDisplay(StringUtil.w3cToKif(term)) && !getCodedIdentifiers(kb).contains(term)
					// && !term.matches("^iso\\d+.*_.+")
				)
				{
					String formattedTerm = stripNamespacePrefix(term);
					if (getSimplified())
					{
						String smTerm = // stringMap.get(term);
								getTermPresentationName(kb, term);
						if (!smTerm.isEmpty())
						{
							formattedTerm = stripNamespacePrefix(smTerm);
						}
					}
					if (!formattedTerm.isEmpty())
					{
						String firstLetter = Character.toString(Character.toUpperCase(formattedTerm.charAt(0)));

						Set<String> alSet = alphaList.keySet();
						if (alSet.contains(firstLetter))
						{
							Map<String, List<String>> map = alphaList.get(firstLetter);
							List<String> al = map.computeIfAbsent(formattedTerm, k -> new ArrayList<>());
							al.add(term);
						}
						else
						{
							SortedMap<String, List<String>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
							List<String> al = new ArrayList<>();
							al.add(term);
							map.put(formattedTerm, al);
							alphaList.put(firstLetter, map);
						}
					}
				}
			}
		}
		return alphaList;
	}

	/**
	 * Returns true if term is an instance or subclass of
	 * CompositeContentBearingObject in kb, else returns false.
	 *
	 * @param kb   The KB in which to check the definition of term
	 * @param term A SUO-KIF term
	 * @return true or false
	 */
	public static boolean isComposite(KB kb, String term)
	{
		return (kb.isInstanceOf(term, "CompositeContentBearingObject") || kb.isSubclass(term, "CompositeContentBearingObject") || kb
				.isInstanceOf(term, "CompositeContentBearingObjectType"));
	}

	/**
	 * Create an HTML page that lists information about a particular
	 * composite term, which is a representation of an XML
	 * structure.
	 *
	 * @param alphaList a SortedMap of SortedMaps of Lists.  @see
	 *                  createAlphaList()
	 */
	public String createCompositePage(KB kb, String kbHref, String term, Map<String, Map<String, List<String>>> alphaList, String language, String formatToken)
	{
		String markup = "";
		if (!term.isEmpty())
		{
                /*
                  if (formatToken.equalsIgnoreCase(F_SI2)) {
                  markup = createCompositePage(kb, kbHref, term, alphaList, limit, language);
                  }
                  else {
                */
			StringBuilder result = new StringBuilder();

			if (!kbHref.isEmpty())
				result.append(generateDynamicTOCHeader(kbHref));
			else
				result.append(generateTocHeader(kb, alphaList, INDEX_FILE_NAME));
			result.append("<table width=\"100%\">");
			result.append(LS);
			result.append("  <tr bgcolor=\"#DDDDDD\">");
			result.append(LS);
			result.append("    <td valign=\"top\" class=\"title\">");
			result.append(LS);
			result.append("      ");
			result.append(showTermName(kb, term, language, true));
			result.append(LS);
			result.append("    </td>");
			result.append(LS);
			result.append("  </tr>");
			result.append(LS);
			result.append("  <tr bgcolor=\"#DDDDDD\">");
			result.append(LS);
			result.append("    <td valign=\"top\" class=\"cell\">");
			result.append(LS);
			result.append("    </td>");
			result.append(LS);
			result.append("  </tr>");
			result.append(LS);

			result.append(createDocs(kb, kbHref, term, language));
			result.append("</table>");
			result.append(LS);
			result.append("<table>");
			result.append(LS);
			result.append(createDisplayNames(kb, term, formatToken));
			result.append(LS);
			result.append(createSynonyms(kb, term));
			result.append(LS);

			List<String> superComposites = findContainingComposites(kb, term);
			superComposites.sort(String.CASE_INSENSITIVE_ORDER);

			StringBuilder sb1 = new StringBuilder();
			sb1.append(createHasSameComponents(kb, kbHref, term, language));
			if ((sb1.length() > 0) || !superComposites.isEmpty() || hasSubComponents(kb, term))
			{
				result.append("<tr class=\"title_cell\">");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"label\">Component Structure</td>");
				result.append(LS);
				result.append("  <td valign=\"top\" colspan=\"4\"></td>");
				result.append(LS);
				result.append("</tr>");
				result.append(LS);

				if (sb1.length() > 0)
				{
					result.append(sb1);
					sb1.setLength(0);
				}

				if (hasSubComponents(kb, term))
				{
					result.append("<tr>");
					result.append(LS);
					result.append("  <td valign=\"top\" class=\"label\">Components</td>");
					result.append(LS);
					result.append("  <td valign=\"top\" class=\"title_cell\">Name</td>");
					result.append(LS);
					result.append("  <td valign=\"top\" class=\"title_cell\">");
					result.append(LS);
					result.append("    Description of Element Role");
					result.append(LS);
					result.append("  </td>");
					result.append(LS);
					result.append("  <td valign=\"top\" class=\"title_cell\">Cardinality</td>");
					result.append(LS);
					result.append("  <td valign=\"top\" class=\"title_cell\">Data Type</td>");
					result.append(LS);
					result.append("</tr>");
					result.append(LS);

					List<AVPair> elems = new ArrayList<>();
					List<AVPair> attrs = new ArrayList<>();

					// If there are shared components, add them first.
					List<String> accumulator = new ArrayList<>(getSyntacticExtensionTerms(kb, term, 2, false));
					List<String> sharesComponentsWith = new ArrayList<>();
					while (!accumulator.isEmpty())
					{
						sharesComponentsWith.clear();
						sharesComponentsWith.addAll(accumulator);
						accumulator.clear();
						for (String nextTerm : sharesComponentsWith)
						{
							Tuple.Pair<List<AVPair>, List<AVPair>> nextPair = createCompositeRecurse(kb, nextTerm, false, 0);
							List<AVPair> nextAttrs = nextPair.first;
							List<AVPair> nextElems = nextPair.second;
							attrs.addAll(0, nextAttrs);
							if (!nextElems.isEmpty())
							{
								nextElems.remove(0);
								elems.addAll(0, nextElems);
							}
							accumulator.addAll(getSyntacticExtensionTerms(kb, nextTerm, 2, false));
						}
					}

					// Now add the components that pertain to only this term.
					Tuple.Pair<List<AVPair>, List<AVPair>> localPair = createCompositeRecurse(kb, term, false, 0);

					// No need to show the composite itself.
					List<AVPair> localAttrs = localPair.first;
					List<AVPair> localElems = localPair.second;
					attrs.addAll(localAttrs);
					if (!localElems.isEmpty())
					{
						localElems.remove(0);
						elems.addAll(localElems);
					}

					List<AVPair> hier = new ArrayList<>(attrs);
					hier.addAll(elems);
					result.append(formatCompositeHierarchy(kb, kbHref, hier, language));
				}

				if (!superComposites.isEmpty())
				{
					superComposites.sort(String.CASE_INSENSITIVE_ORDER);
					String formattedContainingComposites = formatContainingComposites(kb, kbHref, superComposites, term, language);
					if (!formattedContainingComposites.isEmpty())
					{
						result.append("<tr>");
						result.append(LS);
						result.append("  <td valign=\"top\" class=\"label\">");
						result.append(LS);
						result.append("    Is Member of Composites");
						result.append(LS);
						result.append("  </td>");
						result.append(LS);
						result.append("  <td valign=\"top\" class=\"title_cell\">");
						result.append(LS);
						result.append("    Composite Name");
						result.append(LS);
						result.append("  </td>");
						result.append(LS);
						result.append("  <td valign=\"top\" class=\"title_cell\">");
						result.append(LS);
						result.append("    Description of Element Role");
						result.append(LS);
						result.append("  </td>");
						result.append(LS);
						result.append("  <td valign=\"top\" class=\"title_cell\">");
						result.append(LS);
						result.append("    Cardinality");
						result.append("  </td>");
						result.append(LS);
						result.append("  <td valign=\"top\" class=\"title_cell\"> &nbsp; </td>");
						result.append(LS);
						result.append("</tr>");
						result.append(LS);

						result.append(formattedContainingComposites);
						result.append(LS);
					}
				}
			}

			sb1.append(createBelongsToClass(kb, kbHref, term, language));
			sb1.append(createUsingSameComponents(kb, kbHref, term, language));
			if (sb1.length() > 0)
			{
				result.append("<tr class=\"title_cell\">");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"label\">");
				result.append(LS);
				result.append("    Relationships");
				result.append(LS);
				result.append("  </td>");
				result.append(LS);
				result.append("  <td></td><td></td><td></td><td></td>");
				result.append(LS);
				result.append("</tr>");
				result.append(LS);

				result.append(sb1);

				sb1.setLength(0);
			}
			result.append("</table>");
			result.append(LS);

			result.append(generateHtmlFooter(""));

			result.append("  </body>");
			result.append(LS);
			result.append("</html>");
			result.append(LS);

			markup = result.toString();
			// }
		}
		return markup;
	}

	/**
	 * Create an HTML page that lists information about a particular term,
	 * with a limit on how many statements of each type should be
	 * displayed.
	 *
	 * @param alphaList a SortedMap of SortedMaps of Lists.
	 */
	public String createPage(KB kb, String kbHref, String term, Map<String, Map<String, List<String>>> alphaList, String language, String formatToken)
	{
		StringBuilder result = new StringBuilder();
		StringBuilder sb1 = new StringBuilder();
		StringBuilder sb2 = new StringBuilder();

		if (!kbHref.isEmpty())
		{
			if (!kbHref.endsWith("&term="))
			{
				kbHref += "&term=";
			}
			result.append(generateDynamicTOCHeader(kbHref));
		}
		else
		{
			result.append(generateTocHeader(kb, alphaList, INDEX_FILE_NAME));
		}
		result.append("<table width=\"100%\">");
		result.append(LS);
		result.append("  <tr bgcolor=\"#DDDDDD\">");
		result.append(LS);
		result.append("    <td valign=\"top\" class=\"title\">");
		result.append(LS);
		result.append("      ");
		result.append(showTermName(kb, term, language, true));
		result.append(LS);
		result.append("      ");
		result.append(LS);
		result.append("    </td>");
		result.append(LS);
		result.append("  </tr>");
		result.append(LS);
		result.append("  <tr bgcolor=\"#DDDDDD\">");
		result.append(LS);
		result.append("    <td valign=\"top\" class=\"cell\">");
		result.append(LS);
		result.append("    </td>");
		result.append(LS);
		result.append("  </tr>");
		result.append(LS);
		result.append(createDocs(kb, kbHref, term, language));
		result.append(LS);
		result.append("</table>");
		result.append(LS);
		result.append("<table width=\"100%\">");
		result.append(LS);
		result.append(createDisplayNames(kb, term, formatToken));
		result.append(LS);
		result.append(createSynonyms(kb, term));
		result.append(LS);
		result.append(createComments(kb, kbHref, term, language));
		result.append(LS);

		Set<String> parents = new HashSet<>();
		sb1.append(createParents(kb, kbHref, term, language, parents));
		sb1.append(LS);
		sb2.append(createChildren(kb, kbHref, term, language));
		sb2.append(LS);

		if ((sb1.length() > 0) || (sb2.length() > 0))
		{
			result.append("<tr class=\"title_cell\">");
			result.append(LS);
			result.append("  <td valign=\"top\" class=\"label\">");
			result.append(LS);
			result.append("    Relationships");
			result.append(LS);
			result.append("  </td>");
			result.append(LS);
			result.append("  <td>&nbsp;</td>");
			result.append(LS);
			result.append("  <td>&nbsp;</td>");
			result.append(LS);
			result.append("  <td>&nbsp;</td>");
			result.append(LS);
			result.append("</tr>");
			result.append(LS);

			// Parents
			result.append(sb1.toString());
			sb1.setLength(0);

			// Children
			result.append(sb2.toString());
			sb2.setLength(0);
		}

		List<String> superComposites = findContainingComposites(kb, term);
		superComposites.sort(String.CASE_INSENSITIVE_ORDER);

		result.append(createInstances(kb, kbHref, term, language, superComposites));
		result.append(LS);

		result.append(createRelations(kb, kbHref, term, language, formatToken));
		result.append(LS);

		result.append(createUsingSameComponents(kb, kbHref, term, language));
		result.append(LS);

		result.append(createBelongsToClass(kb, kbHref, term, language, parents));
		result.append(LS);

		if (!superComposites.isEmpty())
		{
			String formattedContainingComposites = formatContainingComposites(kb, kbHref, superComposites, term, language);
			if (!formattedContainingComposites.isEmpty())
			{
				result.append("<tr>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"label\">");
				result.append(LS);
				result.append("    Is Member of Composites");
				result.append(LS);
				result.append("  </td>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"title_cell\">");
				result.append(LS);
				result.append("    Composite Name");
				result.append(LS);
				result.append("  </td>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"title_cell\">");
				result.append(LS);
				result.append("    Description of Element Role");
				result.append(LS);
				result.append("  </td>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"title_cell\">");
				result.append(LS);
				result.append("    Cardinality");
				result.append(LS);
				result.append("  </td>");
				result.append(LS);
				result.append("  <td> &nbsp; </td>");
				result.append(LS);
				result.append("</tr>");
				result.append(LS);

				result.append(formattedContainingComposites);
				result.append(LS);
			}
		}

		result.append("</table>");
		result.append(LS);
		result.append(generateHtmlFooter(""));
		result.append(LS);
		result.append("  </body>");
		result.append(LS);
		result.append("</html>");
		result.append(LS);

		// result.append(createAllStatements(kb,kbHref,term,limit));
		return result.toString();
	}

	/**
	 * Returns an List of namespace delimiter Strings gathered
	 * from all loaded KBs, obtained by collecting statements formed
	 * with the predicate docGenNamespaceDelimiter.
	 *
	 * @return An List<String> of namespace delimiter tokens,
	 * which could be empty
	 */
	public List<String> getAllNamespaceDelimiters()
	{
		Set<String> reduce = new HashSet<>();
		Map<String, KB> kbs = KBManager.getMgr().kbs;
		if (!kbs.isEmpty())
		{
			for (KB kb : kbs.values())
			{
				reduce.addAll(kb.getTermsViaAsk(0, "docGenNamespaceDelimiter", 2));
			}
		}
		reduce.add(StringUtil.getW3cNamespaceDelimiter());
		reduce.add(StringUtil.getKifNamespaceDelimiter());
		return new ArrayList<>(reduce);
	}

	/**
	 * Returns a String of HTML markup for the start of a document,
	 * using title as the document title String.
	 *
	 * @param title A String to be used as the document title
	 * @return A String of HTML markup encoding the start of an HTML
	 * document
	 */
	public String generateHtmlDocStart(String title)
	{
		String css = getStyleSheet();
		css = StringUtil.removeEnclosingQuotes(css);

		String docTitle = title;
		if (docTitle == null || docTitle.isEmpty())
		{
			docTitle = getTitleText();
		}
		docTitle = StringUtil.removeEnclosingQuotes(docTitle);
		docTitle = StringUtil.removeQuoteEscapes(docTitle);

		StringBuilder sb = new StringBuilder();

		sb.append("<html>");
		sb.append(LS);
		sb.append("  <head>");
		sb.append(LS);
		sb.append("    <meta http-equiv=\"Content-Type\" ");
		sb.append("content=\"text/html; charset=utf-8\">");
		sb.append(LS);
		if (!css.isEmpty())
		{
			sb.append("    <link rel=\"stylesheet\" type=\"text/css\" href=\"");
			sb.append(css);
			sb.append("\">");
			sb.append(LS);
		}
		if (!docTitle.isEmpty())
		{
			sb.append("    <title>");
			sb.append(docTitle);
			sb.append("</title>");
			sb.append(LS);
		}
		sb.append("  </head>");
		sb.append(LS);
		sb.append("  <body>");
		sb.append(LS);
		return sb.toString();
	}

	/**
	 * Returns a String of HTML markup encoding the footer section of
	 * an HTML document, and using footerText as the text to be
	 * displayed at the bottom of the page.
	 *
	 * @param footerText The text String to be displayed at the bottom
	 *                   of an HTML document
	 * @return A String of HTML markup
	 */
	protected String generateHtmlFooter(@SuppressWarnings("SameParameterValue") String footerText)
	{
		String text = footerText;
		if (text == null || text.isEmpty())
		{
			text = getFooterText();
		}
		text = StringUtil.removeEnclosingQuotes(text);
		text = StringUtil.removeQuoteEscapes(text);
		return "<table width=\"100%\">" + LS + "  <tr class=\"title\">" + LS + "    <td>"
				// sb.append(LS);
				+ text
				// sb.append(LS);
				+ "    </td>" + LS + "  </tr>" + LS + "</table>" + LS;
	}

	/**
	 * Returns true if statements that include term and occur in the
	 * kb and ontology associated with this DocGen object may be
	 * displayed or output (at all, in any form).
	 *
	 * @return true or false
	 */
	protected boolean isLegalForDisplay(String term)
	{
		boolean result = !term.isEmpty();
		DisplayFilter df = getDisplayFilter();
		if (result && (df != null))
		{
			result = df.isLegalForDisplay(this, term);
		}
		return result;
	}

	/**
	 * Returns a List of all SUO-KIF terms denoting those namespaces
	 * containing terms that are defined in, or occur in, statements
	 * in ontology.  An association (correspondence) between a
	 * namespace and an ontology is represented by a statement formed
	 * with the SUO-KIF predicate ontologyNamespace.
	 *
	 * @param kb       The KB in which ontologyNamespace statements will be
	 *                 sought
	 * @param ontology The name of the ontology that will be checked
	 * @return An List of SUO-KIF terms that denote namespaces
	 * and occur in statements formed with the predicate
	 * ontologyNamespace
	 */
	protected List<String> getOntologyNamespaces(KB kb, String ontology)
	{
		List<String> result = new ArrayList<>();
		if (!ontology.isEmpty())
		{
			result.addAll(new HashSet<>(kb.getTermsViaAskWithRestriction(0, "ontologyNamespace", 1, ontology, 2)));
		}
		return result;
	}

	/**
	 * A List of currently known namespace prefixes.
	 */
	protected final List<String> namespacePrefixes = new ArrayList<>();

	/**
	 * Returns an List of all known namespace prefixes sorted by
	 * length, from longest to shortest.
	 *
	 * @return A List of all known namespace prefixes
	 */
	public List<String> getNamespacePrefixes()
	{
		if (namespacePrefixes.isEmpty())
		{
			synchronized (namespacePrefixes)
			{
				Set<String> delims = new HashSet<>(getAllNamespaceDelimiters());
				delims.addAll(Arrays.asList( //
						StringUtil.getKifNamespaceDelimiter(), //
						StringUtil.getW3cNamespaceDelimiter(), //
						StringUtil.getSafeNamespaceDelimiter())); //
				List<String> nsPrefs = new ArrayList<>();
				for (String delim : delims)
				{
					nsPrefs.add("ns" + delim);
				}
				String prefix;
				for (String term : getNamespaces())
				{
					prefix = term;
					for (String nsPref : nsPrefs)
					{
						if (term.startsWith(nsPref))
						{
							int idx = nsPref.length();
							if (idx < term.length())
							{
								prefix = prefix.substring(idx);
								break;
							}
						}
					}
					for (String delim : delims)
					{
						namespacePrefixes.add(prefix + delim);
					}
				}
				if (namespacePrefixes.size() > 1)
					sortByTermLength(namespacePrefixes);
			}
		}
		return namespacePrefixes;
	}

	/**
	 * A List of currently known namespaces.
	 */
	protected final List<String> namespaces = new ArrayList<>();

	/**
	 * Returns a List of all SUO-KIF terms that denote namespaces in
	 * any loaded KB, obtained by gathering statements formed with the
	 * predicates inNamespace and ontologyNamespace as well as
	 * explicit instance statements.
	 *
	 * @return A List of all known SUO-KIF terms that denote
	 * namespaces
	 */
	public List<String> getNamespaces()
	{
		synchronized (namespaces)
		{
			if (namespaces.isEmpty())
			{
				Set<String> reduce = new HashSet<>();
				for (KB kb : KBManager.getMgr().kbs.values())
				{
					reduce.addAll(kb.getTermsViaAsk(0, "inNamespace", 2));
					reduce.addAll(kb.getTermsViaAsk(0, "ontologyNamespace", 2));
					reduce.addAll(kb.getAllInstancesWithPredicateSubsumption("Namespace"));
				}
				if (!reduce.isEmpty())
					namespaces.addAll(reduce);
				if (namespaces.size() > 1)
					sortByTermLength(namespaces);
			}
		}
		return namespaces;
	}

	/**
	 * Returns a List of all SUO-KIF terms denoting namespaces in kb
	 * or in ontology, using the predicates inNamespace and
	 * ontologyNamespace.
	 *
	 * @param kb       The KB in which statements will be checked
	 * @param ontology The name of the ontology that will be checked
	 * @param force    If true, this parameter will force the List of
	 *                 namespaces to be recomputed
	 * @return A List of all the SUO-KIF terms that denote namespaces
	 * and occur in statements formed with inNamespace or
	 * ontologyNamespace
	 */
	protected List<String> getNamespaces(KB kb, String ontology, @SuppressWarnings("SameParameterValue") boolean force)
	{
		// if (!ontology.isEmpty()) {
		synchronized (namespaces)
		{
			if (namespaces.isEmpty() || force)
			{
				if (force)
				{
					namespaces.clear();
					namespacePrefixes.clear();
				}
				List<String> terms = kb.getTermsViaAsk(0, "inNamespace", 2);
				Set<String> reduce = new HashSet<>(terms);
				if (isEmpty(ontology))
				{
					ontology = getOntology();
				}
				if (!ontology.isEmpty())
				{
					reduce.addAll(getOntologyNamespaces(kb, ontology));
				}
				reduce.addAll(kb.getAllInstancesWithPredicateSubsumption("Namespace"));
				namespaces.addAll(reduce);
				if (namespaces.size() > 1)
					sortByTermLength(namespaces);
				if (!namespaces.isEmpty())
				{
					Set<String> delims = new HashSet<>(getAllNamespaceDelimiters());
					delims.addAll(Arrays.asList(StringUtil.getKifNamespaceDelimiter(), StringUtil.getW3cNamespaceDelimiter(),
							StringUtil.getSafeNamespaceDelimiter()));
					List<String> nsPrefs = new ArrayList<>();
					for (String delim : delims)
					{
						nsPrefs.add("ns" + delim);
					}
					String prefix;
					for (String term : namespaces)
					{
						prefix = term;
						for (String nsPref : nsPrefs)
						{
							if (term.startsWith(nsPref))
							{
								int idx = nsPref.length();
								if (idx < term.length())
								{
									prefix = prefix.substring(idx);
									break;
								}
							}
						}
						for (String delim : delims)
						{
							namespacePrefixes.add(prefix + delim);
						}
					}
					if (namespacePrefixes.size() > 1)
						sortByTermLength(namespacePrefixes);
				}
			}
		}
		// }
		return namespaces;
	}

	/**
	 * Returns the namespace prefix of term based on the namespaces
	 * known in kb, else returns the empty String if term appears to
	 * have no namespace prefix.
	 */
	protected String getNamespacePrefix(String term)
	{
		String result = "";
		if (!term.isEmpty())
		{
			for (String prefix : getNamespacePrefixes())
			{
				if (term.startsWith(prefix))
				{
					result = prefix;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Returns term without its namespace prefix if it appears to have
	 * one in kb, else just returns term.
	 */
	protected String stripNamespacePrefix(String term)
	{
		String result = term;
		String prefix = getNamespacePrefix(term);
		if (!prefix.isEmpty())
		{
			result = term.substring(prefix.length());
		}
		return result;
	}

	/**
	 * Returns a SUO-KIF term denoting a namespace.
	 *
	 * @param kb   The KB in which to determine if term is an namespace
	 * @param term A String denoting a namespace, perhaps in W3C format
	 * @return String A term denoting a namespace in SUO-KIF format,
	 * else just returns the input term if no syntactic transformation
	 * is warranted
	 */
	protected String toKifNamespace(KB kb, String term)
	{
		String result = term;
		if (!term.isEmpty())
		{
			String kifTerm = StringUtil.w3cToKif(term);
			String prefix = ("ns" + StringUtil.getKifNamespaceDelimiter());
			if (!kifTerm.equals("ns") && !kifTerm.startsWith(prefix))
			{
				kifTerm = prefix + kifTerm;
			}
			String ontology = getOntology();
			if (!ontology.isEmpty())
			{
				for (String ns : getNamespaces(kb, ontology, false))
				{
					if (ns.equalsIgnoreCase(kifTerm))
					{
						result = ns;
						break;
					}
				}
			}
		}
		return result;
	}

	/**
	 * Collects and returns a List of all Predicates in kb.
	 *
	 * @param kb The KB from which to gather all terms that are
	 *           instances of BinaryPredicate
	 * @return A List of BinaryPredicates (Strings)
	 */
	protected List<String> getPredicates(KB kb, boolean requireNamespace)
	{
		List<String> cached = getRelationsByKB().get(kb);
		if (cached == null)
		{
			String ontology = getOntology();
			boolean isOntology = !ontology.isEmpty();

			SortedSet<String> predSet = new TreeSet<>();
			Set<String> classNames = kb.getAllSubClassesWithPredicateSubsumption("Predicate");
			classNames.add("Predicate");
			classNames.add("BinaryPredicate");
			for (String cn : classNames)
			{
				List<String> predList = kb.getTermsViaPredicateSubsumption("instance", 2, cn, 1, true);

				for (String p1 : predList)
				{
					if (requireNamespace)
					{
						String namespace = getTermNamespace(p1);
						if (!namespace.isEmpty() && isOntology && getOntologyNamespaces(kb, ontology).contains(namespace))
						{
							// pred.contains(StringUtil.getKifNamespaceDelimiter())) {
							predSet.add(p1);
						}
					}
					else
					{
						predSet.add(p1);
					}
				}
			}
			List<String> p0List = new ArrayList<>();
			List<String> working = new ArrayList<>();
			Set<String> accumulator = new HashSet<>(Arrays.asList("subrelation", "inverse"));
			while (!accumulator.isEmpty())
			{
				working.clear();
				working.addAll(accumulator);
				accumulator.clear();
				for (String p0 : working)
				{
					if (requireNamespace)
					{
						String namespace = getTermNamespace(p0);
						if (!namespace.isEmpty() && isOntology && getOntologyNamespaces(kb, ontology).contains(namespace))
						{
							predSet.add(p0);
						}
					}
					else
					{
						predSet.add(p0);
					}
					if (!p0List.contains(p0))
					{
						p0List.add(p0);
					}
					accumulator.addAll(kb.getTermsViaPredicateSubsumption("subrelation", 2, p0, 1, true));
				}
			}
			for (String p0 : p0List)
			{
				List<Formula> formulae = kb.ask("arg", 0, p0);
				if (formulae != null)
				{
					for (Formula f : formulae)
					{
						String p1 = f.getArgument(1);
						if (requireNamespace)
						{
							String namespace = getTermNamespace(p1);
							if (!namespace.isEmpty() && isOntology && getOntologyNamespaces(kb, ontology).contains(namespace))
							{
								predSet.add(p1);
							}
						}
						else
						{
							predSet.add(p1);
						}
						String p2 = f.getArgument(2);
						if (requireNamespace)
						{
							String namespace = getTermNamespace(p2);
							if (!namespace.isEmpty() && isOntology && getOntologyNamespaces(kb, ontology).contains(namespace))
							{
								predSet.add(p2);
							}
						}
						else
						{
							predSet.add(p2);
						}
					}
				}
			}
			cached = new ArrayList<>(predSet);
			getRelationsByKB().put(kb, cached);
		}
		return cached;
	}

	/**
	 * Returns true if term has syntactic subcomponents such as XML
	 * elements or XML attributes in kb, else returns false.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String denoting a SUO-KIF constant name
	 * @return true or false
	 */
	protected boolean hasSubComponents(KB kb, String term)
	{
		boolean result = false;
		if (!term.isEmpty())
		{
			result = (getSubComponents(kb, term) != null);
		}
		return result;
	}

	/**
	 * Returns a List containing those terms that are immediate
	 * syntactic subordinates of term in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that is a SUO-KIF constant
	 * @return A List of Strings that denote SUO-KIF constants, or an
	 * empty List
	 */
	protected List<String> getSubComponents(KB kb, String term)
	{
		List<String> result = new ArrayList<>();
		if (!term.isEmpty())
		{
			result.addAll(kb.getTermsViaPredicateSubsumption("syntacticSubordinate", 2, term, 1, true));
		}
		return result;
	}

	/**
	 * Returns a List containing those terms that are immediate
	 * syntactic superiors or "containers" of term in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String, a SUO-KIF constant
	 * @return A List of Strings that denote SUO-KIF constants, or an
	 * empty List
	 */
	protected List<String> getSuperComponents(KB kb, String term)
	{
		List<String> result = new ArrayList<>();
		if (!term.isEmpty())
		{
			result.addAll(kb.getTermsViaPredicateSubsumption("syntacticSubordinate", 1, term, 2, true));
		}
		return result;
	}

	/**
	 * Returns a String that is the first termFormat value obtained
	 * for term in kb, else returns null if no termFormat value
	 * exists.
	 *
	 * @param kb       The KB in which term is defined
	 * @param term     A String that is a SUO-KIF constant
	 * @param contexts A List of namespaces or other terms that index
	 *                 context-specific termFormat statements
	 * @return A List of Strings that denote SUO-KIF constants, or an
	 * empty List
	 */
	protected String getFirstTermFormat(KB kb, String term, List<String> contexts)
	{
		String result = null;
		if (!term.isEmpty() && !StringUtil.isQuotedString(term))
		{
			List<Formula> forms = kb.askWithRestriction(2, term, 0, "headword");
			if (forms.isEmpty())
				forms = kb.askWithRestriction(2, term, 0, "termFormat");
			if (!forms.isEmpty())
			{
				for (String ctx : contexts)
				{
					for (Formula f : forms)
					{
						if (f.getArgument(1).equals(ctx))
						{
							result = f.getArgument(3);
							break;
						}
					}
					if (result != null)
					{
						break;
					}
				}
				if ((result == null) && StringUtil.isLocalTermReference(term))
				{
					String moreGeneralTerm = getFirstGeneralTerm(kb, term);
					if (!moreGeneralTerm.isEmpty())
					{
						result = getFirstTermFormat(kb, moreGeneralTerm, contexts);
					}
				}
			}
			if (result == null)
				result = term;
		}
		return result;
	}

	/**
	 * Returns the first documentation String obtained for term in kb,
	 * using the List of namespaces or other contextualizing terms in
	 * contexts.
	 *
	 * @param kb       The KB in which term is defined
	 * @param term     A String that is a SUO-KIF constant
	 * @param contexts A List of namespaces or other terms that index
	 *                 context-specific documentation or comment statements
	 * @return A documentation String, or an empty String if no
	 * documentation String can be found
	 */
	protected String getContextualDocumentation(KB kb, String term, List<String> contexts)
	{
		String result = "";
		if (!term.isEmpty())
		{
			List<Formula> forms = kb.askWithRestriction(1, term, 0, "documentation");
			if (forms != null && !forms.isEmpty())
			{
				if (StringUtil.isLocalTermReference(term) && (forms.size() == 1))
				{
					Formula f = forms.get(0);
					result = f.getArgument(3);
				}
				else
				{
					if (contexts == null)
						contexts = new ArrayList<>();
					List<String> supers = getSuperComponents(kb, term);
					contexts.addAll(supers);
					contexts.add(0, term);
					contexts.add(getDefaultNamespace());
					if (!contexts.contains("EnglishLanguage"))
						contexts.add("EnglishLanguage");

					for (String ctx : contexts)
					{
						for (Formula f : forms)
						{
							if (f.getArgument(2).equals(ctx))
							{
								result = f.getArgument(3);
								break;
							}
						}
						if (!result.isEmpty())
							break;
					}
					if (isEmpty(result))
					{
						String classOfTerm = getFirstGeneralTerm(kb, term);
						if (!classOfTerm.isEmpty())
						{
							result = getContextualDocumentation(kb, classOfTerm, null);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns the first containing Class that can be found for term
	 * in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that is a SUO-KIF constant
	 * @return A SUO-KIF term denoting a Class, or null if no Class
	 * can be found
	 */
	protected String getNearestContainingClass(KB kb, String term)
	{
		String result = null;
		List<String> predicates = new LinkedList<>();
		Set<String> accumulator = new HashSet<>();
		List<String> working = new LinkedList<>();
		accumulator.add("instance");
		while (!accumulator.isEmpty())
		{
			for (String p1 : accumulator)
			{
				if (!predicates.contains(p1))
				{
					predicates.add(0, p1);
				}
			}
			working.clear();
			working.addAll(accumulator);
			accumulator.clear();
			for (String p2 : working)
			{
				accumulator.addAll(kb.getTermsViaPredicateSubsumption("subrelation", 2, p2, 1, false));
			}
		}
		for (String p3 : predicates)
		{
			result = kb.getFirstTermViaAskWithRestriction(0, p3, 1, term, 2);
			if (result != null)
				break;
			accumulator.addAll(kb.getTermsViaPredicateSubsumption("inverse", 2, p3, 1, false));
			accumulator.addAll(kb.getTermsViaPredicateSubsumption("inverse", 1, p3, 2, false));
			for (String p4 : accumulator)
			{
				result = kb.getFirstTermViaAskWithRestriction(0, p4, 2, term, 1);
				if (result != null)
					break;
			}
			if (result != null)
				break;
			accumulator.clear();
		}
		return result;
	}

	/**
	 * Returns the first containing, subsuming, or superordinate
	 * entity that can be found for term in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that is a SUO-KIF constant
	 * @return A SUO-KIF term, or null if no more general term can be
	 * found
	 */
	protected String getFirstGeneralTerm(KB kb, String term)
	{
		String result = null;
		if (!term.isEmpty())
		{
			List<String> preds = Arrays.asList("instance", /* "subclass", */ "datatype", "syntacticExtension", "syntacticComposite", "subclass");
			for (String p : preds)
			{
				List<String> terms = kb.getTermsViaPredicateSubsumption(p, 1, term, 2, false);
				if (!terms.isEmpty())
				{
					result = terms.get(0);
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Returns a List of the first instances or syntactic subordinate
	 * entities that can be found for term in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that denotes a SUO-KIF term
	 * @return A List of SUO-KIF terms, or an empty List
	 */
	protected List<String> getFirstSpecificTerms(KB kb, String term)
	{
		List<String> result = new ArrayList<>();
		/*          */
		if (!term.isEmpty())
		{
			List<String> preds = Arrays.asList("instance",
					// "datatype",
					"syntacticExtension", "syntacticComposite"
					// "subclass"
			);
			for (String p : preds)
			{
				result.addAll(kb.getTermsViaPredicateSubsumption(p, 2, term, 1, true));
				if (!result.isEmpty())
					break;
			}
		}
		return result;
	}

	/**
	 * Returns a String consisting of HTML markup for a documentation
	 * String for term obtained from kb and indexed by language.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 documentation Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createDocs(KB kb, String kbHref, String term, String language)
	{
		String markup = "";
		if (isLegalForDisplay(term))
		{
			StringBuilder result = new StringBuilder();
			List<String> context = new ArrayList<>();
			context.add(language);
			String docString = getContextualDocumentation(kb, term, context);
			docString = processDocString(kb, kbHref, language, docString, false, true);
			result.append("<tr>");
			result.append(LS);
			result.append("  <td valign=\"top\" class=\"description\">");
			result.append(LS);
			result.append("    ");
			result.append(docString);
			result.append(LS);
			result.append("  </td>");
			result.append(LS);
			result.append("</tr>");

			markup = result.toString();
		}
		return markup;
	}

	/**
	 * Returns a String containing the HTML markup for the Comment
	 * field in a page displaying the definition of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 comment Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createComments(KB kb, String kbHref, String term, String language)
	{
		StringBuilder result = new StringBuilder();
		if (isLegalForDisplay(term))
		{
			List<Formula> formulae = kb.askWithRestriction(0, "comment", 1, term);
			if (formulae != null && !formulae.isEmpty())
			{
				List<String> docs = new ArrayList<>();
				for (Formula f : formulae)
				{
					docs.add(f.getArgument(3));
				}
				Collections.sort(docs);

				result.append("<tr>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"label\">Comments</td>");

				for (int i = 0; i < docs.size(); i++)
				{
					String docString = docs.get(i);
					docString = processDocString(kb, kbHref, language, docString, false, true);
					if (i > 0)
					{
						result.append("<tr>");
						result.append(LS);
						result.append("  <td>&nbsp;</td>");
						result.append(LS);
					}
					result.append("  <td valign=\"top\" colspan=\"2\" class=\"cell\">");
					result.append(LS);
					result.append("      ");
					result.append(docString);
					result.append("<br/>");
					result.append(LS);
					result.append("  </td>");
					result.append(LS);
					result.append("</tr>");
					result.append(LS);
				}
			}
		}
		return result.toString();
	}

	/**
	 * Returns a String containing HTML markup for the synonym field
	 * of an HTML page displaying the definition of term in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that denotes a SUO-KIF term
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createSynonyms(KB kb, String term)
	{
		String result = "";
		if (isLegalForDisplay(term))
		{
			List<Formula> alternates = new ArrayList<>();
			if (!term.isEmpty())
			{
				alternates.addAll(kb.askWithRestriction(0, "synonym", 2, term));
				alternates.addAll(kb.askWithRestriction(0, "headword", 2, term));
				if (!alternates.isEmpty())
				{
					String presentationName = getTermPresentationName(kb, term);
					String basePresentationName = stripNamespacePrefix(presentationName);
					List<String> synonyms = new ArrayList<>();
					String hwsuff = "_hw";

					for (Formula f : alternates)
					{
						String namespace = f.getArgument(1);
						String prefix = stripNamespacePrefix(namespace);
						String syn = StringUtil.removeEnclosingQuotes(f.getArgument(3));
						if (!syn.equals(basePresentationName))
						{
							if (prefix.matches("^iso\\d+.*"))
							{
								int sIdx = prefix.lastIndexOf(hwsuff);
								if (sIdx > -1)
									prefix = prefix.substring(0, sIdx);
								syn = (prefix + StringUtil.getW3cNamespaceDelimiter() + syn);
							}
							synonyms.add(syn);
						}
					}
					if (!synonyms.isEmpty())
					{
						sortByPresentationName(kb, getDefaultNamespace(), synonyms);
						StringBuilder sb = new StringBuilder();
                            /*
                              if (formatToken.equalsIgnoreCase(F_DD2)) {
                              sb.append("<tr>");
                              sb.append(getLineSeparator());
                              sb.append("  <td class=\"label\">");
                              sb.append(getLineSeparator());
                              sb.append("    Synonym");
                              sb.append((synonyms.size() > 1) ? "s" : "");
                              sb.append(getLineSeparator());
                              sb.append("  </td>");
                              sb.append(getLineSeparator());
                              sb.append("  <td class=\"syn\">");
                              sb.append(getLineSeparator());
                              sb.append("    ");
                              boolean isFirst = true;
                              for (String syn2 : synonyms) {
                              sb.append(isFirst ? "" : ", ");
                              isFirst = false;
                              sb.append(syn2);
                              }
                              sb.append(getLineSeparator());
                              sb.append("  </td>");
                              sb.append(getLineSeparator());
                              sb.append("</tr>");
                              sb.append(LS);
                              }
                              else {
                            */
						sb.append("<tr>");
						sb.append(LS);
						sb.append("  <td valign=\"top\" class=\"cell\">");
						sb.append("<strong>Synonym");
						sb.append((synonyms.size() > 1) ? "s" : "");
						sb.append("</strong>");
						boolean isFirst = true;
						for (String syn1 : synonyms)
						{
							sb.append(isFirst ? " " : ", ");
							isFirst = false;
							sb.append("<i>");
							sb.append(syn1);
							sb.append("</i>");
						}
						sb.append("</td>");
						sb.append(LS);
						sb.append("</tr>");
						sb.append(LS);
						// }
						result = sb.toString();
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns a String containing HTML markup for the Display Labels
	 * field of an HTML page displaying statements about term in kb.
	 *
	 * @param kb          The KB in which term is defined
	 * @param term        A String that denotes a SUO-KIF term
	 * @param formatToken A String token that partly determines the
	 *                    format of the output
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createDisplayNames(KB kb, String term, String formatToken)
	{
		String result = "";
		if (isLegalForDisplay(term) && !formatToken.equalsIgnoreCase(F_SI))
		{
			List<String> labels = new ArrayList<>();
			if (!term.isEmpty())
			{
				String defaultNamespace = getDefaultNamespace();
				if (!defaultNamespace.isEmpty())
				{
					labels.addAll(kb.getTermsViaAWTR(2, term, 0, "displayName", 1, defaultNamespace, 3));
				}
				if (labels.isEmpty())
				{
					labels.addAll(kb.getTermsViaAskWithRestriction(2, term, 0, "displayName", 3));
				}
				else
				{
					if (labels.size() > 1)
						labels.sort(String.CASE_INSENSITIVE_ORDER);
					StringBuilder sb = new StringBuilder();
                        /*
                          if (formatToken.equalsIgnoreCase(F_DD2)) {
                          sb.append("<tr>");
                          sb.append(getLineSeparator());
                          sb.append("  <td class=\"label\">");
                          sb.append(getLineSeparator());
                          sb.append("    Display Label");
                          sb.append((labels.size() > 1) ? "s" : "");
                          sb.append(getLineSeparator());
                          sb.append("  </td>");
                          sb.append(getLineSeparator());
                          sb.append("  <td class=\"syn\">");
                          sb.append(getLineSeparator());
                          sb.append("    ");
                          boolean isFirst = true;
                          for (String lab2 : labels) {
                          sb.append(isFirst ? "" : ", ");
                          isFirst = false;
                          sb.append(StringUtil.removeEnclosingQuotes(lab2));
                          }
                          sb.append(getLineSeparator());
                          sb.append("  </td>");
                          sb.append(getLineSeparator());
                          sb.append("</tr>");
                          sb.append(LS);
                          }
                          else {
                        */
					sb.append("<tr>");
					sb.append(LS);
					sb.append("  <td valign=\"top\" class=\"cell\">");
					sb.append("<strong>Display Label");
					sb.append((labels.size() > 1) ? "s" : "");
					sb.append("</strong>");
					boolean isFirst = true;
					for (String lab1 : labels)
					{
						sb.append(isFirst ? " " : ", ");
						isFirst = false;
						sb.append("<i>");
						sb.append(StringUtil.removeEnclosingQuotes(lab1));
						sb.append("</i>");
					}
					sb.append("</td>");
					sb.append(LS);
					sb.append("</tr>");
					sb.append(LS);
					// }
					result = sb.toString();
				}
			}
		}
		return result;
	}

	/**
	 * Returns a String containing HTML markup for the Has Same
	 * Components As field of an HTML page displaying the definition
	 * of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createHasSameComponents(KB kb, String kbHref, String term, String language)
	{
		StringBuilder result = new StringBuilder();
		if (isLegalForDisplay(term))
		{
			String suffix = (isEmpty(kbHref) ? ".html" : "");
			List<String> extensionOfs = getSyntacticExtensionTerms(kb, term, 2, true);
			if (!extensionOfs.isEmpty())
			{
				result.append("<tr>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"label\">");
				result.append(LS);
				result.append("    Has Same Components As");
				result.append(LS);
				result.append("  </td>");
				result.append(LS);
				boolean isFirst = true;
				StringBuilder hrefSB = new StringBuilder();
				for (String extended : extensionOfs)
				{
					hrefSB.setLength(0);
					hrefSB.append("<a href=\"");
					hrefSB.append(kbHref);
					hrefSB.append(StringUtil.toSafeNamespaceDelimiter(kbHref, extended));
					hrefSB.append(suffix);
					hrefSB.append("\">");
					hrefSB.append(showTermName(kb, extended, language, true));
					hrefSB.append("</a>");
					if (isFirst)
					{
						result.append("  <td valign=\"top\" class=\"cell\">");
						result.append(LS);
						isFirst = false;
					}
					result.append("    ");
					result.append(hrefSB.toString());
					result.append("<br/>");
					result.append(LS);
				}
				result.append("  </td>");
				result.append(LS);
				result.append("</tr>");
				result.append(LS);
			}
		}
		return result.toString();
	}

	/**
	 * Returns a String containing HTML markup for the Composites
	 * Using Same Components field of an HTML page displaying the
	 * definition of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createUsingSameComponents(KB kb, String kbHref, String term, String language)
	{
		StringBuilder result = new StringBuilder();
		if (!term.isEmpty())
		{
			if (isLegalForDisplay(term))
			{
				String suffix = "";
				if (isEmpty(kbHref))
					suffix = ".html";
				List<String> extensions = getSyntacticExtensionTerms(kb, term, 1, true);
                    /*
                      kb.getTransitiveClosureViaPredicateSubsumption("syntacticExtension",
                      2,
                      term,
                      1,
                      true);
                    */
				if (!extensions.isEmpty())
				{
					result.append("<tr>");
					result.append(LS);
					result.append("  <td valign=\"top\" class=\"label\">");
					result.append(LS);
					result.append("    ");
					result.append("Composites Using Same Components");
					result.append(LS);
					result.append("  </td>");
					result.append(LS);

					boolean isFirst = true;
					StringBuilder hrefSB = new StringBuilder();
					for (String extension : extensions)
					{
						hrefSB.setLength(0);
						hrefSB.append("<a href=\"");
						hrefSB.append(kbHref);
						hrefSB.append(StringUtil.toSafeNamespaceDelimiter(kbHref, extension));
						hrefSB.append(suffix);
						hrefSB.append("\">");
						hrefSB.append(showTermName(kb, extension, language, true));
						hrefSB.append("</a>");
						if (isFirst)
						{
							result.append("  <td valign=\"top\" class=\"cell\">");
							result.append(LS);
							isFirst = false;
						}
						result.append("    ");
						result.append(hrefSB.toString());
						result.append("<br/>");
						result.append(LS);
					}
					result.append("  </td>");
					result.append(LS);
					result.append("</tr>");
					result.append(LS);
				}
			}
		}
		return result.toString();
	}

	/**
	 * Returns a String containing HTML markup for the Parents field
	 * of an HTML page displaying the definition of term in kb.
	 *
	 * @param kb         The KB in which term is defined
	 * @param kbHref     A String containing the constant parts of the
	 *                   href link for term, or an empty String
	 * @param term       A String that denotes a SUO-KIF term
	 * @param language   A String denoting a SUO-KIF namespace, a
	 *                   natural language, or other type of entity that indexes
	 *                   termFormat Strings in kb
	 * @param parentsSet A Set for accumulating the parent terms of
	 *                   term
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createParents(KB kb, String kbHref, String term, String language, Set<String> parentsSet)
	{
		String result = "";
		String suffix = "";
		if (isEmpty(kbHref))
			suffix = ".html";

		List<Formula> forms = new ArrayList<>();
		Set<String> parents = new HashSet<>();
		List<String> relations = Arrays.asList("subclass", "subrelation", "subAttribute", "subentity");
		if (!term.isEmpty())
		{
			for (String pred : relations)
			{
				forms.addAll(kb.askWithPredicateSubsumption(pred, 1, term));
			}
			for (Formula f : forms)
			{
				if (!f.sourceFile.endsWith(KB._cacheFileSuffix))
				{
					String s = f.getArgument(2);
					if (isLegalForDisplay(s))
					{
						parents.add(s);
					}
				}
			}
			if (!parents.isEmpty())
			{
				parentsSet.addAll(parents);
				StringBuilder sb = new StringBuilder();
				List<String> sorted = new ArrayList<>(parents);
				sorted.sort(String.CASE_INSENSITIVE_ORDER);
				sb.append("<tr>");
				sb.append(LS);
				sb.append("  <td valign=\"top\" class=\"label\">Parents</td>");
				sb.append(LS);
				StringBuilder hrefSB = new StringBuilder();
				boolean isFirst = true;
				for (String parent : sorted)
				{
					hrefSB.setLength(0);
					hrefSB.append("<a href=\"");
					hrefSB.append(kbHref);
					hrefSB.append(StringUtil.toSafeNamespaceDelimiter(kbHref, parent));
					hrefSB.append(suffix);
					hrefSB.append("\">");
					hrefSB.append(showTermName(kb, parent, language, true));
					hrefSB.append("</a>");
					if (!isFirst)
					{
						sb.append("<tr>");
						sb.append(LS);
						sb.append("  <td>&nbsp;</td>");
						sb.append(LS);
					}
					isFirst = false;
					sb.append("  <td valign=\"top\" class=\"cell\">");
					sb.append(hrefSB.toString());
					sb.append("</td>");
					sb.append(LS);
					String docStr = getContextualDocumentation(kb, parent, null);
					sb.append("  <td valign=\"top\" class=\"cell\">");
					sb.append(LS);
					sb.append("    ");
					sb.append(processDocString(kb, kbHref, language, docStr, false, true));
					sb.append(LS);
					sb.append("  </td>");
					sb.append(LS);
				}
				sb.append("</tr>");
				sb.append(LS);
				result = sb.toString();
			}
		}
		return result;
	}

	/**
	 * Returns a String containing HTML markup for the Children field
	 * of an HTML page displaying the definition of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createChildren(KB kb, String kbHref, String term, String language)
	{
		String suffix = "";
		if (isEmpty(kbHref))
			suffix = ".html";
		StringBuilder result = new StringBuilder();
		String[] relns = { "subclass", "subrelation", "subAttribute", "subentity" };
		List<Formula> forms = new ArrayList<>();
		if (!term.isEmpty())
		{
			for (String reln : relns)
			{
				List<Formula> tmp = kb.askWithPredicateSubsumption(reln, 2, term);
				if ((tmp != null) && !tmp.isEmpty())
				{
					forms.addAll(tmp);
				}
			}
		}

		if (!forms.isEmpty())
		{
			List<String> kids = new ArrayList<>();
			for (Formula f : forms)
			{
				if (!f.sourceFile.endsWith(KB._cacheFileSuffix))
				{
					String s = f.getArgument(1);
					if (isLegalForDisplay(s) && !kids.contains(s))
					{
						kids.add(s);
					}
				}
			}
			if (!kids.isEmpty())
			{
				kids.sort(String.CASE_INSENSITIVE_ORDER);
				result.append("<tr>");
				result.append(LS);
				result.append("  <td valign=\"top\" class=\"label\">Children</td>");
				result.append(LS);
				boolean isFirst = true;
				for (String s : kids)
				{
					String termHref = ("<a href=\"" + kbHref + StringUtil.toSafeNamespaceDelimiter(kbHref, s) + suffix + "\">" + showTermName(kb, s, language,
							true) + "</a>");
					if (!isFirst)
						result.append("<tr><td>&nbsp;</td>");
					result.append("<td valign=\"top\" class=\"cell\">").append(termHref).append("</td>");
					String docString = getContextualDocumentation(kb, s, null);
					docString = processDocString(kb, kbHref, language, docString, false, true);
					result.append("<td valign=\"top\" class=\"cell\">").append(docString).append("</td>");
					isFirst = false;
				}
				result.append("</tr>").append(LS);
			}
		}
		return result.toString();
	}

	/**
	 * Returns a String containing HTML markup for the Instances
	 * section of an HTML page displaying the definition of term in
	 * kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @param excluded A List of terms to be excluded from the display
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createInstances(KB kb, String kbHref, String term, String language, List<String> excluded)
	{
		String markup = "";
		if (!term.isEmpty())
		{
			String suffix = "";
			if (isEmpty(kbHref))
				suffix = ".html";
			StringBuilder result = new StringBuilder();
			List<String> working = new ArrayList<>();
			working.add(term);
			List<String> extRelns = Arrays.asList("syntacticUnion", "syntacticExtension");
			for (String extReln : extRelns)
			{
				List<String> extendeds = kb.getTermsViaPredicateSubsumption(extReln, 1, term, 2, false);
				for (String subent : extendeds)
				{
					if (!working.contains(subent))
					{
						working.add(subent);
					}
				}
			}
			List<String> instances = new ArrayList<>();
			for (String subent : working)
			{
				List<Formula> forms = kb.askWithPredicateSubsumption("instance", 2, subent);
				for (Formula f : forms)
				{
					if (!f.sourceFile.endsWith(KB._cacheFileSuffix))
					{
						String inst = f.getArgument(1);
						if (!excluded.contains(inst) && isLegalForDisplay(inst))
						{
							instances.add(inst);
						}
					}
				}
			}
			instances.addAll(kb.getAllInstancesWithPredicateSubsumption(term, false));
			Set<String> instSet = new HashSet<>();
			for (String inst : instances)
			{
				if (!excluded.contains(inst) && isLegalForDisplay(inst))
				{
					instSet.add(inst);
				}
			}

			// Remove duplicate strings, if any.
			instances.clear();
			instances.addAll(instSet);
			if (!instances.isEmpty())
			{
				sortByPresentationName(kb, getDefaultNamespace(), instances);
				for (int j = 0; j < instances.size(); j++)
				{
					if (j == 0)
					{
						result.append("<tr><td valign=\"top\" class=\"label\">Instances</td>");
					}
					else
					{
						result.append("<tr><td>&nbsp;</td>");
					}
					String inst = instances.get(j);
					String displayName = showTermName(kb, inst, language, true);
					if (displayName.contains(inst))
					{
						String xmlName = showTermName(kb, inst, "XMLLabel", true);
						if (!isEmpty(xmlName))
							displayName = xmlName;
					}
					String termHref = ("<a href=\"" + kbHref + StringUtil.toSafeNamespaceDelimiter(kbHref, inst) + suffix + "\">" + displayName + "</a>");
					result.append("<td valign=\"top\" class=\"cell\">").append(termHref).append("</td>");
					List<String> cList = new ArrayList<>();
					cList.add(language);
					String docString = getContextualDocumentation(kb, inst, cList);
					docString = processDocString(kb, kbHref, language, docString, false, true);
					result.append("<td valign=\"top\" class=\"cell\">");
					result.append(docString);
					result.append("</td>");
					result.append("</tr>");
					result.append(LS);
				}
			}
			markup = result.toString();
		}
		return markup;
	}

	/**
	 * Returns a String containing HTML markup for a SUO-KIF Formula.
	 *
	 * @param kb           The KB in which formula occurs
	 * @param kbHref       A String containing the constant parts of the
	 *                     href link for the constants in formula, or an empty String
	 * @param indentSeq    A character sequence that will be used as the
	 *                     indentation quantum for formula
	 * @param level        The current indentation level
	 * @param previousTerm A String, the term that occurs sequentially
	 *                     before currentTerm in the same level of nesting.  The value of
	 *                     previousTerm aids in determining how a given Formula should be
	 *                     formatted, and could be null.
	 * @param currentTerm  A String denoting a SUO-KIF Formula or part
	 *                     of a Formula
	 * @param context      A String denoting a SUO-KIF namespace, a
	 *                     natural language, or other type of entity that indexes
	 *                     termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * formula cannot be processed
	 */
	protected String createFormula(KB kb, String kbHref, String indentSeq, int level, String previousTerm, String currentTerm, String context)
	{
		StringBuilder sb = new StringBuilder();
		String suffix = "";
		if (isEmpty(kbHref))
			suffix = ".html";
		if (!previousTerm.isEmpty() && previousTerm.matches(".*\\w+.*"))
			previousTerm = previousTerm.trim();
		if (currentTerm.matches(".*\\w+.*"))
			currentTerm = currentTerm.trim();
		if (Formula.listP(currentTerm))
		{
			if (Formula.empty(currentTerm))
			{
				sb.append(currentTerm);
			}
			else
			{
				Formula f = new Formula();
				f.set(currentTerm);
				List<String> tuple = f.literalToList();
				boolean isQuantifiedVarList = Formula.isQuantifier(previousTerm);
				for (String s : tuple)
				{
					if (!isQuantifiedVarList)
						break;
					isQuantifiedVarList = Formula.isVariable(s);
				}
				if (!isQuantifiedVarList && (level > 0))
				{
					sb.append("<br>");
					sb.append(LS);
					for (int i = 0; i < level; i++)
					{
						sb.append(indentSeq);
					}
				}
				sb.append("(");
				int i = 0;
				String prevTerm = null;
				for (Iterator<String> it = tuple.iterator(); it.hasNext(); i++)
				{
					String nextTerm = it.next();
					if (i > 0)
						sb.append(" ");
					sb.append(createFormula(kb, kbHref, indentSeq, (level + 1), prevTerm, nextTerm, context));
					prevTerm = nextTerm;
				}
				sb.append(")");
			}
		}
		else if (Formula.isVariable(currentTerm) || //
				Formula.isLogicalOperator(currentTerm) || //
				Formula.isFunction(currentTerm) ||  //
				StringUtil.isQuotedString(currentTerm) ||  //
				StringUtil.isDigitString(currentTerm))
		{
			sb.append(currentTerm);
		}
		else
		{
			sb.append("<a href=\"");
			sb.append(kbHref);
			sb.append(StringUtil.toSafeNamespaceDelimiter(kbHref, currentTerm));
			sb.append(suffix);
			sb.append("\">");
			sb.append(showTermName(kb, currentTerm, context, true));
			sb.append("</a>");
		}
		return sb.toString();
	}

	/**
	 * Returns a String containing HTML markup for the Relations
	 * section of an HTML page displaying the definition of term in
	 * kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createRelations(KB kb, String kbHref, String term, String language, String formatToken)
	{
		String result = "";
/*
              if (formatToken.equalsIgnoreCase(F_DD2)) {
              result = createRelations(kb, kbHref, term, language);
              }
              else {
            */
		if (isLegalForDisplay(term))
		{
			String suffix = "";
			if (isEmpty(kbHref))
				suffix = ".html";
			List<String> relations = getPredicates(kb, !formatToken.equalsIgnoreCase(F_SI));
			if (!relations.isEmpty())
			{
				StringBuilder sb = new StringBuilder();
				Set<String> avoid = getInhibitDisplayRelations();
				Map<String, List<String>> map = new HashMap<>();

				for (String relation : relations)
				{
					// boolean isFormula = relation.matches(".*(?i)kif.*");
					if (!avoid.contains(relation))
					{
						List<Formula> statements = kb.askWithPredicateSubsumption(relation, 1, term);
						if (!statements.isEmpty())
						{
							List<String> vals = new ArrayList<>();
							for (Formula f : statements)
							{
								if (!f.sourceFile.endsWith(KB._cacheFileSuffix))
								{
									vals.add(f.getArgument(2));
								}
							}
							if (!vals.isEmpty())
							{
								map.put(relation, vals);
							}
						}
					}
				}
				if (!map.isEmpty())
				{
					List<String> keys = new ArrayList<>(map.keySet());
					sortByPresentationName(kb, language, keys);
					boolean firstLine = true;
					for (String relation : keys)
					{
						List<String> vals = map.get(relation);
						if ((vals != null) && !vals.isEmpty())
						{
							String relnHref = ("<a href=\"" + kbHref + StringUtil.toSafeNamespaceDelimiter(kbHref, relation) + suffix + "\">" + showTermName(kb,
									relation, language, true) + "</a>");
							int m = 0;
							for (String s : vals)
							{
								String termHref = createFormula(kb, kbHref, "&nbsp;&nbsp;&nbsp;&nbsp;", 0, null, s, language);
								if (firstLine)
								{
									sb.append("<tr><td valign=\"top\" class=\"label\">" + "Relations" + "</td>");
									firstLine = false;
								}
								else
								{
									sb.append("<tr><td>&nbsp;</td>");
								}
								if (m == 0)
								{
									sb.append("<td valign=\"top\" class=\"cell\">").append(relnHref).append("</td>");
								}
								else
								{
									sb.append("<td valign=\"top\" class=\"cell\">&nbsp;</td>");
								}
								sb.append("<td valign=\"top\" class=\"cell\">").append(termHref).append("</td></tr>").append(LS);
								m++;
							}
						}
					}
				}
				result = sb.toString();
			}
		}
		// }
		return result;
	}

	/**
	 * Returns a String containing HTML markup for the Cardinality
	 * field of an HTML page displaying the definition of term in kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that denotes a SUO-KIF term
	 * @return A String containing HTML markup, or an empty String if
	 * no markup can be generated
	 */
	protected String showCardinalityCell(KB kb, String term)
	{
		String cardVal;
		List<Formula> cardForms = kb.askWithPredicateSubsumption("hasExactCardinality", 1, term);
		// kb.askWithRestriction(0,"exactCardinality",2,term);
		if (cardForms != null && !cardForms.isEmpty())
		{
			Formula f = cardForms.get(0);
			// if (context.equals("") || context.equals(f.getArgument(1)))
			//     return f.getArgument(3);
			cardVal = f.getArgument(2);
		}
		else
		{
			String minCard = "0";
			String maxCard = "n";
			cardForms = kb.askWithPredicateSubsumption("hasMinCardinality", 1, term);
			// kb.askWithRestriction(0,"minCardinality",2,term);
			if (cardForms != null && cardForms.size() > 0)
			{
				Formula f = cardForms.get(0);
				// if (context == "" || context.equals(f.getArgument(1)))
				//     minCard = f.getArgument(3);
				minCard = f.getArgument(2);
			}
			cardForms = kb.askWithPredicateSubsumption("hasMaxCardinality", 1, term);
			// kb.askWithRestriction(0,"maxCardinality",2,term);
			if (cardForms != null && cardForms.size() > 0)
			{
				Formula f = cardForms.get(0);
				// if (context.equals("") || context.equals(f.getArgument(1)))
				//     maxCard = f.getArgument(3);
				maxCard = f.getArgument(2);
			}
			cardVal = (minCard + "-" + maxCard);
		}
		return cardVal;
	}

	/**
	 * Returns a String containing HTML markup for a single table row
	 * in the Composite Component section of an HTML page displaying
	 * the partial definition of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * term is supposed to be suppressed for display
	 */
	protected String createCompositeComponentLine(KB kb, String kbHref, String term, int indent, String language)
	{
		StringBuilder sb = new StringBuilder();
		String suffix = "";
		if (isEmpty(kbHref))
			suffix = ".html";
		sb.append("<tr>");
		sb.append(LS);
		sb.append("  <td></td>");
		sb.append(LS);

		sb.append("  <td valign=\"top\" class=\"cell\">");
		sb.append(LS);

		String parentClass = "";
		List<Formula> instanceForms = kb.askWithPredicateSubsumption("instance", 1, term);
		if (instanceForms != null && instanceForms.size() > 0)
		{
			Formula f = instanceForms.get(0);
			parentClass = f.getArgument(2);
		}
		List<Formula> termForms = null;
		if (!term.isEmpty())
		{
			termForms = kb.askWithTwoRestrictions(0, "termFormat", 1, "XMLLabel", 2, term);
		}
		if (termForms != null)
		{
			boolean isAttribute = isXmlAttribute(kb, term);
			if (!isAttribute)
				isAttribute = isXmlAttribute(kb, parentClass);
			for (Formula f : termForms)
			{
				sb.append(indentChars("&nbsp;&nbsp;", indent));
				String termFormat = StringUtil.removeEnclosingQuotes(f.getArgument(3));
				sb.append("<a href=\"");
				sb.append(kbHref);
				sb.append(StringUtil.toSafeNamespaceDelimiter(kbHref, parentClass));
				sb.append(suffix);
				sb.append("\">");
				if (isAttribute)
					sb.append("<span class=\"attribute\">");
				sb.append(termFormat);
				if (isAttribute)
					sb.append("</span>");
				sb.append("</a>");
			}
		}
		sb.append("  </td>");
		sb.append(LS);

		sb.append("  <td valign=\"top\" class=\"cell\">");
		sb.append(LS);
		List<String> cList = new ArrayList<>();
		cList.add(language);
		String docString = getContextualDocumentation(kb, term, cList);
		docString = processDocString(kb, kbHref, language, docString, false, true);
		sb.append("    ");
		sb.append(docString);
		sb.append(LS);
		sb.append("  </td>");
		sb.append(LS);

		sb.append("  <td valign=\"top\" class=\"card\">");
		if (indent > 0)
			sb.append(showCardinalityCell(kb, term));
		sb.append("  </td>");
		sb.append(LS);

		sb.append("  <td valign=\"top\" class=\"cell\">");
		String dataTypeName = getFirstDatatype(kb, term);
		if (!dataTypeName.isEmpty())
		{
			String dtToPrint = showTermName(kb, dataTypeName, language, true);
			sb.append("<a href=\"");
			sb.append(kbHref);
			sb.append(StringUtil.toSafeNamespaceDelimiter(kbHref, dataTypeName));
			sb.append(suffix);
			sb.append("\">");
			sb.append(dtToPrint);
			sb.append("</a>");
			String xsdType = getClosestXmlDataType(kb, dataTypeName);
			if (!xsdType.isEmpty())
			{
				sb.append(" (");
				sb.append(StringUtil.kifToW3c(xsdType));
				sb.append(")");
			}
			sb.append(LS);
		}
		sb.append("  </td>");
		sb.append(LS);

		sb.append("</tr>");
		sb.append(LS);

		return sb.toString();
	}

	/**
	 * Returns the termFormat entry for term in kb and language,
	 * otherwise returns the termFormat entry for term in English,
	 * otherwise just returns the term name.
	 *
	 * @param kb           The KB in which term is defined
	 * @param term         A String that denotes a SUO-KIF term
	 * @param language     A String denoting a SUO-KIF namespace, a
	 *                     natural language, or another type of entity that contextualizes
	 *                     or indexes termFormat Strings in kb
	 * @param withSpanTags If true, the returned String is wrapped in
	 *                     HTML span tags that allow additional formatting for term via a
	 *                     style sheet
	 * @return A String providing a context-specific name for term,
	 * possibly including HTML markup, or just term if no
	 * context-specific form can be found or produced
	 */
	public String showTermName(KB kb, String term, String language, boolean withSpanTags)
	{
		String result; // = StringUtil.removeEnclosingQuotes(term);
		String termFormat = getFirstTermFormat(kb, term, Collections.singletonList(language));
		if (isEmpty(termFormat))
		{
			termFormat = kb.getTermFormatMap(language).get(term);
		}
		if (isEmpty(termFormat))
		{
			termFormat = kb.getTermFormatMap("EnglishLanguage").get(term);
		}
		if (!termFormat.isEmpty())
		{
			result = StringUtil.removeEnclosingQuotes(termFormat);
		}
		else
		{
			String namespace = getTermNamespace(term);
			if (!namespace.isEmpty() && (namespace.equals(language) || namespace.equals(getDefaultNamespace())))
			{
				result = stripNamespacePrefix(term);
			}
			else
			{
				result = StringUtil.kifToW3c(term);
			}
		}
		if (getCodedIdentifiers(kb).contains(term))
		{
			List<String> delims = Arrays.asList(StringUtil.getW3cNamespaceDelimiter(), StringUtil.getKifNamespaceDelimiter());
			for (String delim : delims)
			{
				int idx = result.indexOf(delim);
				if (idx > -1)
				{
					idx += delim.length();
					if (idx < result.length())
					{
						result = result.substring(idx);
						break;
					}
				}
			}
		}
		if (withSpanTags)
		{
			if (!result.isEmpty())
			{
				if (isXmlAttribute(kb, term))
				{
					result = ("<span class=\"attribute\">" + result + "</span>");
				}
			}
		}
		return result;
	}

	/**
	 * Returns the termFormat entry for term in kb and language,
	 * otherwise returns the termFormat entry for term in English,
	 * otherwise just returns the term name.
	 *
	 * @param kb       The KB in which term is defined
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String providing a context-specific name for term,
	 * possibly including HTML markup, or just term
	 */
	public String showTermName(KB kb, String term, String language)
	{
		return showTermName(kb, term, language, false);
	}

	/**
	 * Returns a String containing HTML markup for a hierarchy or tree
	 * display of terms that denote nested composite components.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param hier     A List containing term names and representing one
	 *                 sub-branch in a tree
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * no markup can be generated
	 */
	protected String formatCompositeHierarchy(KB kb, String kbHref, List<AVPair> hier, String language)
	{
		StringBuilder result = new StringBuilder();
		for (AVPair avp : hier)
		{
			// if (!kb.isInstanceOf(avp.attribute, "XmlSequenceElement")) {
			result.append(createCompositeComponentLine(kb, kbHref, avp.attribute, Integer.parseInt(avp.value), language));
			// }
		}
		return result.toString();
	}

	/**
	 * Recursively computes and then returns a List that constitutes
	 * the graph containing those XML elements and attributes
	 * syntactically subordinate to term in kb.
	 *
	 * @param kb          The KB in which term is defined
	 * @param term        A String that denotes a SUO-KIF term
	 * @param isAttribute If true, this parameter indicates that term
	 *                    denotes an XML attribute
	 * @return A List containing two Lists, the first of which is a
	 * List of terms that denote XML attributes, and the second of
	 * which is a list of terms that denote XML elements
	 */
	protected Tuple.Pair<List<AVPair>, List<AVPair>> createCompositeRecurse(KB kb, String term, boolean isAttribute, int indent)
	{
		Tuple.Pair<List<AVPair>, List<AVPair>> pair = new Tuple.Pair<>();
		List<AVPair> attrs = new ArrayList<>();
		List<AVPair> elems = new ArrayList<>();
		pair.first = attrs;
		pair.second = elems;

		AVPair avp = new AVPair();
		avp.attribute = term;
		avp.value = Integer.toString(indent);
		if (isAttribute)
			attrs.add(avp);
		else
			elems.add(avp);

		List<String> preds = Arrays.asList("subordinateXmlAttribute", "subordinateXmlElement");
		for (String pred : preds)
		{
			List<String> terms = kb.getTermsViaPredicateSubsumption(pred, 2, term, 1, true);
			terms.sort(String.CASE_INSENSITIVE_ORDER);
			boolean isAttributeList = pred.equals("subordinateXmlAttribute");
			for (String nextTerm : terms)
			{
				// This should return without children for subordinateXmlAttribute, since attributes don't have child elements.
				Tuple.Pair<List<AVPair>, List<AVPair>> newPair = createCompositeRecurse(kb, nextTerm, isAttributeList, (indent + 1));
				attrs.addAll(newPair.first);
				elems.addAll(newPair.second);
			}
		}
		return pair;
	}

	/**
	 * Returns a List that constitutes the graph containing those XML
	 * elements and attributes syntactically subordinate to term in
	 * kb.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that denotes a SUO-KIF term
	 * @return A String containing HTML markup, or an empty String if
	 * no markup can be generated
	 */
	protected boolean isXmlAttribute(KB kb, String term)
	{
		String kif = term;
		List<String> terms = kb.getTermsViaPredicateSubsumption("subordinateXmlAttribute", 1, kif, 2, true);
		boolean result = !terms.isEmpty();
		if (!result)
		{
			kif = getFirstGeneralTerm(kb, kif);
			if (!kif.isEmpty())
			{
				terms = kb.getTermsViaPredicateSubsumption("subordinateXmlAttribute", 1, kif, 2, true);
				result = !terms.isEmpty();
			}
		}
		return result;
	}

	/**
	 * Returns a String containing HTML markup for a row displaying a
	 * contained component in an HTML page displaying the partial
	 * definition of instance in kb.
	 *
	 * @param kb             The KB in which term is defined
	 * @param kbHref         A String containing the constant parts of the
	 *                       href link for term, or an empty String
	 * @param containingComp A String that denotes the term that
	 *                       contains, or is syntactically superordinate to, instance
	 * @param instance       A String that denotes a SUO-KIF term
	 * @param language       A String denoting a SUO-KIF namespace, a
	 *                       natural language, or other type of entity that indexes
	 *                       termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * no markup can be generated
	 */
	protected String createContainingCompositeComponentLine(KB kb, String kbHref, String containingComp, /* List containerData, */ String instance,
			@SuppressWarnings("SameParameterValue") int indent, String language)
	{
		String result = "";
		if (!instance.isEmpty())
		{
			StringBuilder sb = new StringBuilder();
			String suffix = (isEmpty(kbHref) ? ".html" : "");
			List<Formula> docForms = kb.askWithRestriction(0, "documentation", 1, instance);
			for (Formula f : docForms)
			{
				String context = f.getArgument(2);
				if (context.equals(containingComp))
				{
					sb.append("<tr>");
					sb.append(LS);
					sb.append("  <td>&nbsp;</td>");
					sb.append(LS);
					sb.append("  <td valign=\"top\" class=\"cell\">");
					sb.append(indentChars("&nbsp;&nbsp;", indent));
					sb.append("<a href=\"");
					sb.append(kbHref);
					sb.append(StringUtil.toSafeNamespaceDelimiter(kbHref, containingComp));
					sb.append(suffix);
					sb.append("\">");
					sb.append(showTermName(kb, containingComp, language, true));
					sb.append("</a></td>");
					sb.append(LS);
					sb.append("  <td valign=\"top\" class=\"cell\">");
					sb.append(LS);
					sb.append("    ");
					sb.append(processDocString(kb, kbHref, language, f.getArgument(3), false, true));
					sb.append(LS);
					sb.append("  </td>");
					sb.append(LS);
					sb.append("  <td valign=\"top\" class=\"card\">");
					sb.append(showCardinalityCell(kb, instance));
					sb.append("  </td>");
					sb.append(LS);
					sb.append("  <td>&nbsp;</td>");
					sb.append(LS);
					sb.append("</tr>");
					sb.append(LS);
				}
			}
			result = sb.toString();
		}
		return result;
	}

	/**
	 * Given the SUO-KIF statements:
	 * (hasXmlElement PartyDescriptor LocalInstance_2_459)
	 * (datatype LocalInstance_2_459 PartyId)
	 * (documentation LocalInstance_2_459
	 * PartyDescriptor "A Composite containing details...")
	 * show PartyDescriptor as one of the "containing
	 * composites" of PartyId, and show the documentation for
	 * the instance node next to the parent composite.
	 */
	protected String formatContainingComposites(KB kb, String kbHref, List<String> containing, String composite, String language)
	{
		StringBuilder sb = new StringBuilder();
		List<String> instances = getFirstSpecificTerms(kb, composite);
		for (String cComp : containing)
			for (String inst : instances)
			{
				sb.append(createContainingCompositeComponentLine(kb, kbHref, cComp, inst, 0, language));
			}

		return sb.toString();
	}

	/**
	 * Returns true if term should be skipped over during printing,
	 * else returns false.
	 */
	protected static boolean isSkipNode(KB kb, String term)
	{
		return isInstanceOf(kb, term, "XmlSequenceElement") || isInstanceOf(kb, term, "XmlChoiceElement");
	}

	/**
	 * Travels up the HasXmlElement and HasXmlAttribute relation
	 * hierarchies to collect all parents, and returns them in an
	 * List.
	 *
	 * @return An List of terms, which could be empty
	 */
	protected List<String> getContainingComposites(KB kb, String term)
	{
		List<String> result = new ArrayList<>();
		if (!term.isEmpty())
		{
			result.addAll(kb.getTermsViaPredicateSubsumption("syntacticSubordinate", 1, term, 2, true));
		}
		return result;
	}

	/**
	 * Returns a String containing HTML markup for the Belongs to Class
	 * section of an HTML page displaying the partial
	 * definition of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @param parents  A Set containing the terms displayed in the
	 *                 Parent field for term, to avoid duplication between the Parents
	 *                 field and the Belongs to Class field
	 * @return A String containing HTML markup, or an empty String if
	 * no markup can be generated
	 */
	protected String createBelongsToClass(KB kb, String kbHref, String term, String language, Set<String> parents)
	{
		String markup = "";
		String suffix = "";
		if (isEmpty(kbHref))
			suffix = ".html";
		String className = getNearestContainingClass(kb, term);
		if (!className.isEmpty() && isLegalForDisplay(className) && ((parents == null) || !parents.contains(className)))
		{
			markup = "<tr>" + LS + "  <td valign=\"top\" class=\"label\">" + LS + "    Belongs to Class" + LS + "  </td>" + LS
					+ "  <td valign=\"top\" class=\"cell\">" + LS + "<a href=\"" + kbHref + StringUtil.toSafeNamespaceDelimiter(kbHref, className) + suffix
					+ "\">" + showTermName(kb, className, language, true) + "</a>" + "  </td>" + LS + "  <td></td><td></td><td></td>" + LS + "</tr>"
					+ StringUtil.getLineSeparator();
		}
		return markup;
	}

	/**
	 * Returns a String containing HTML markup for the Belongs to Class
	 * section of an HTML page displaying the partial
	 * definition of term in kb.
	 *
	 * @param kb       The KB in which term is defined
	 * @param kbHref   A String containing the constant parts of the
	 *                 href link for term, or an empty String
	 * @param term     A String that denotes a SUO-KIF term
	 * @param language A String denoting a SUO-KIF namespace, a
	 *                 natural language, or other type of entity that indexes
	 *                 termFormat Strings in kb
	 * @return A String containing HTML markup, or an empty String if
	 * no markup can be generated
	 */
	protected String createBelongsToClass(KB kb, String kbHref, String term, String language)
	{
		return createBelongsToClass(kb, kbHref, term, language, null);
	}

	/**
	 * Returns an List holding the composite entities (Elements)
	 * that contain term, or returns an empty List.
	 *
	 * @param kb   The KB in which term is defined
	 * @param term A String that denotes a SUO-KIF term
	 * @return An List containing the names of the Elements that
	 * contain term.
	 */
	protected List<String> findContainingComposites(KB kb, String term)
	{
		List<String> result = new ArrayList<>();
		if (!term.isEmpty())
		{
			List<String> accumulator = getContainingComposites(kb, term);
			if (accumulator.isEmpty())
			{
				accumulator.addAll(getFirstSpecificTerms(kb, term));
			}
			List<String> working = new ArrayList<>();

			while (!accumulator.isEmpty())
			{
				working.clear();
				working.addAll(accumulator);
				accumulator.clear();
				for (String term2 : working)
				{
					List<String> compArr1 = getContainingComposites(kb, term2);
					for (String term3 : compArr1)
					{
						if (isSkipNode(kb, term3))
						{
							accumulator.add(term3);
						}
						if (!result.contains(term3))
						{
							result.add(term3);
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Generate an alphabetic HTML list that points to the
	 * individual index pages (which collect all terms starting
	 * with a particular letter.
	 */
	public String generateDynamicTOCHeader(String kbHref)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(generateHtmlDocStart(""));
		sb.append(LS);
		sb.append("<table width=\"100%\">");
		sb.append(LS);
		sb.append("  <tr>");
		for (char c = 65; c < 91; c++)
		{
			sb.append(LS);
			String cString = Character.toString(c);
			sb.append("    <td valign=\"top\"><a href=\"");
			sb.append(kbHref);
			if (!kbHref.isEmpty() && !kbHref.endsWith("&term="))
			{
				sb.append("&term=");
			}
			sb.append(cString);
			sb.append("*\">");
			sb.append(cString);
			sb.append("</a></td>");
			sb.append(LS);
		}
		sb.append("  </tr>");
		sb.append(LS);
		sb.append("</table>");
		sb.append(LS);
		return sb.toString();
	}

	/**
	 * Generate an alphabetic HTML list that points to the
	 * individual index pages (which collect all terms or term
	 * formats) starting with a particular letter.
	 *
	 * @param alphaList a SortedMap of SortedMaps of Lists.
	 */
	protected String generateTocHeader(KB kb, Map<String, Map<String, List<String>>> alphaList, @SuppressWarnings("SameParameterValue") String name)
	{
		if (isEmpty(getTocHeader()))
		{
			StringBuilder result = new StringBuilder();
			List<String> keyList = new ArrayList<>(alphaList.keySet());
			sortByPresentationName(kb, getDefaultNamespace(), keyList);

			String title = getTitleText();
			title = StringUtil.removeEnclosingQuotes(title);

			String imgFile = getDefaultImageFileMarkup();
			if (!imgFile.isEmpty())
			{
				imgFile = StringUtil.removeEnclosingQuotes(imgFile);
			}

			// Add the header.
			result.append(generateHtmlDocStart(""));

			// We assemble the columns first, so as to get the
			// correct value for the table's colspan attribute.
			int colNum = 0;
			StringBuilder sb2 = new StringBuilder();

			// for (char c = 48; c < 58; c++) {                // numbers
			for (String cString : keyList)
			{
				//cString = Character.toString(c);
				if (Character.isDigit(cString.charAt(0)))
				{
					colNum++;
					String fileLink = "number-" + cString + ".html";
					sb2.append("    <td><a href=\"");
					sb2.append(fileLink);
					sb2.append("\">");
					sb2.append(cString);
					sb2.append("</a></td>");
					sb2.append(LS);
				}
			}

			// for (char c = 65; c < 91; c++) {                // letters
			for (String cString : keyList)
			{
				if (!Character.isDigit(cString.charAt(0)))
				{
					colNum++;
					String fileLink = "letter-" + cString + ".html";
					sb2.append("    <td><a href=\"");
					sb2.append(fileLink);
					sb2.append("\">");
					sb2.append(cString);
					sb2.append("</a></td>");
					sb2.append(LS);
				}
			}

			// Increment once more for All.
			colNum++;

			StringBuilder sb1 = new StringBuilder();
			sb1.append("<table width=\"100%\">");
			sb1.append(LS);
			sb1.append("  <tr>");
			sb1.append(LS);
			sb1.append("    <td valign=\"top\" colspan=\"");
			sb1.append(colNum);
			sb1.append("\" class=\"title\">");
			if (!imgFile.isEmpty())
			{
				sb1.append(imgFile);
				sb1.append("&nbsp;&nbsp;");
			}
			sb1.append(title);
			sb1.append("    </td>");
			sb1.append(LS);
			sb1.append("  </tr>");
			sb1.append(LS);
			sb1.append("  <tr class=\"letter\">");
			sb1.append(LS);

			// Assemble everything in the correct order.
			result.append(sb1);
			result.append(sb2);

			result.append("    <td><a href=\"");
			result.append(name);
			result.append("\">All</a></td>");
			result.append(LS);
			result.append("  </tr>");
			result.append(LS);
			result.append("</table>");
			result.append(LS);

			setTocHeader(result.toString());
		}
		return getTocHeader();
	}

	/**
	 * Generate an HTML page that lists term name and its
	 * documentation
	 *
	 * @param alphaList a SortedMap of SortedMaps of Lists.  @see
	 *                  createAlphaList()
	 */
	protected String generateTOCPage(KB kb, String firstLetter, Map<String, Map<String, List<String>>> alphaList, String language)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("<table width=\"100%\">");
		Map<String, List<String>> map = alphaList.get(firstLetter);
		List<String> sorted = new ArrayList<>(map.keySet());
		sortByPresentationName(kb, language, sorted);

		for (String formattedTerm : sorted)
		{
			List<String> al = map.get(formattedTerm);
			sortByPresentationName(kb, language, al);
			for (String realTermName : al)
			{
				String docString = getContextualDocumentation(kb, realTermName, null);
				if (isEmpty(docString))
				{
					// continue;
					docString = "[missing definition]";
				}
				String termToPrint = showTermName(kb, realTermName, language, true);
				sb.append("  <tr>");
				sb.append(LS);

				// Term Name
				sb.append("    <td valign=\"top\" class=\"cell\">");
				sb.append(LS);
				sb.append("      <a href=\"");
				sb.append(StringUtil.toSafeNamespaceDelimiter(realTermName));
				sb.append(".html\">");
				sb.append(LS);
				sb.append("        ");
				sb.append(termToPrint);
				sb.append(LS);
				sb.append("      </a>");
				sb.append(LS);
				sb.append("    </td>");
				sb.append(LS);

				// Relevance
				sb.append("    <td valign=\"top\" class=\"cell\">");
				sb.append(LS);
				sb.append("      ");
                    /*
                    sb.append(getTermRelevance(kb, getOntology(), realTermName).equals("message")
                              ? "&nbsp;&nbsp;&nbsp;&nbsp;MT&nbsp;&nbsp;&nbsp;&nbsp;"
                              : "");
                    */
				sb.append("&nbsp;");
				sb.append("    </td>");
				sb.append(LS);

				// Documentation
				docString = processDocString(kb, "", language, docString, false, true);

				sb.append("    <td valign=\"top\" class=\"cell\">");
				sb.append(LS);
				sb.append("      ");
				sb.append(docString);
				sb.append(LS);
				sb.append("    </td>");
				sb.append(LS);
				sb.append("  </tr>");
				sb.append(LS);
			}
		}
		sb.append("</table>");
		sb.append(LS);
		return sb.toString();
	}

	/**
	 * Generate and save all the index pages that link to the
	 * individual term pages.
	 *
	 * @param dir       is the directory in which to save the pages
	 * @param alphaList a SortedMap of SortedMaps of Lists.  @see
	 *                  createAlphaList()
	 */
	protected void saveIndexPages(KB kb, Map<String, Map<String, List<String>>> alphaList, String dir, String language)
	{
		File parentDir = new File(dir);
		String tocHeader = generateTocHeader(kb, alphaList, INDEX_FILE_NAME);

		int count = 0;
		for (String letter : alphaList.keySet())
		{
			File outfile = new File(parentDir, ((letter.compareTo("A") < 0) ? "number-" : "letter-") + letter + ".html");
			try (PrintWriter pw = new PrintWriter(new FileWriter(outfile)))
			{
				String page = generateTOCPage(kb, letter, alphaList, language);
				pw.println(tocHeader);
				pw.println(page);
				pw.println(generateHtmlFooter(""));
			}
			catch (Exception e)
			{
				System.err.println("ERROR writing \"" + outfile + "\": " + e.getMessage());
				e.printStackTrace();
			}
			if ((count++ % 100) == 1)
				System.out.print(".");
		}
		System.out.print("x");
	}

	/**
	 * Save pages below the KBs directory in a directory called
	 * HTML.  If that already exists, use HTML1, HTML2 etc.
	 */
	protected void printHTMLPages(Map<String, String> pageList, String dirPath) throws IOException
	{
		File outdir = new File(dirPath);
		for (String term : pageList.keySet())
		{
			String page = pageList.get(term);
			File outfile = new File(outdir, StringUtil.toSafeNamespaceDelimiter(term) + ".html");
			try (PrintWriter pw = new PrintWriter(new FileWriter(outfile)))
			{
				pw.println(page);
			}
			catch (Exception e)
			{
				System.err.println("ERROR in DocGen.printHTMLPages(" + "[map with " + pageList.keySet().size() + " keys], " + dirPath + ")");
				System.err.println("Error writing file " + outfile + LS + ": " + e.getMessage());
				throw e;
			}
		}
	}

	/**
	 * Generate HTML pages
	 *
	 * @param alphaList a SortedMap of SortedMaps of Lists.
	 */
	protected Map<String, String> generateHTMLPages(KB kb, Map<String, Map<String, List<String>>> alphaList, /* Map<String, String> inverseHeadwordMap, */
			String language, String formatToken)
	{
		Map<String, String> pageList = new TreeMap<>();
		//SortedSet<String> rejectedTerms = new TreeSet<>();
		int count = 0;
		synchronized (kb.getTerms())
		{
			// inverseHeadwordMap.keySet().iterator();
			for (String realTermName : kb.getTerms())
			{
				// formattedTerm = realTermName;
				// termNames = inverseHeadwordMap.get(formattedTerm);
				// for (String termName : termNames) {
				// realTermName = termName;
				if (isLegalForDisplay(realTermName))
				{
					if (isComposite(kb, realTermName))
					{
						pageList.put(realTermName, createCompositePage(kb, "", realTermName, alphaList, language, formatToken));
					}
					else
					{
						pageList.put(realTermName, createPage(kb, "", realTermName, alphaList, language, formatToken));
					}
					if ((count++ % 100) == 1)
						System.out.print(".");
				}
				//else
				//{
				//	rejectedTerms.add(realTermName);
				//}
				// }
			}
			System.out.print("x");
		}
		return pageList;
	}

	/**
	 * Generate simplified HTML pages for all terms.  Output is a
	 * set of HTML files sent to the directory specified in
	 * makeOutputDir()
	 */
	public void generateHTML(KB kb, String language, String formatToken) throws IOException
	{
		String context = toKifNamespace(kb, language);
		this.defaultNamespace = context;

		// computeTermRelevance(kb, getOntology());

		// a SortedMap of SortedMaps of Lists
		Map<String, Map<String, List<String>>> alphaList = getAlphaList(kb); // headwordMap

		// Headword keys and List values (since the same headword can be found in more than one term)
		// Map inverseHeadwordMap = createInverseHeadwordMap(kb, headwordMap);
		String dir = getOutputDirectoryPath();
		saveIndexPages(kb, alphaList, dir, context);

		// Keys are formatted term names, values are HTML pages
		Map<String, String> pageList = generateHTMLPages(kb, alphaList,
				// inverseHeadwordMap,
				context, formatToken);
		printHTMLPages(pageList, dir);

		generateSingleHTML(kb, dir, alphaList, context);
	}

	/**
	 * Generate a single HTML page showing all terms.
	 *
	 * @param alphaList a SortedMap of SortedMaps of Lists.
	 *                  letter->formattedTerm1->term11,term12...term1N
	 *                  formattedTerm2->term21,term22...term2N
	 */
	public void generateSingleHTML(KB kb, String dir, Map<String, Map<String, List<String>>> alphaList, String language)
	{
		File fileDir = new File(dir);
		File outfile = new File(fileDir, INDEX_FILE_NAME);

		try (PrintWriter pw = new PrintWriter(new FileWriter(outfile)))
		{
			pw.println(generateTocHeader(kb, alphaList, INDEX_FILE_NAME));
			pw.println("<table border=\"0\">");

			for (String letter : alphaList.keySet())
			{
				Map<String, List<String>> values = alphaList.get(letter);
				List<String> sortedKeys = new ArrayList<>(values.keySet());
				sortByPresentationName(kb, language, sortedKeys);
				for (String formattedTerm : sortedKeys)
				{
					List<String> terms = values.get(formattedTerm);
					for (String term : terms)
					{
						term = StringUtil.w3cToKif(term);
						if (isLegalForDisplay(term))
						{
							String docStr = "";
							List<Formula> docs = kb.askWithRestriction(0, "documentation", 1, term);
							if ((docs != null) && !docs.isEmpty())
							{
								Formula f = docs.get(0);
								docStr = processDocString(kb, "", language, f.getArgument(3), false, true);
							}
							if (StringUtil.isLocalTermReference(term) || isEmpty(docStr))
								continue;

							pw.println("  <tr>");

							// Term
							pw.println("    <td valign=\"top\" class=\"cell\">");
							String printableTerm = (getSimplified() ? showTermName(kb, term, language, true) : term);
							pw.print("      <a href=\"");
							pw.print(StringUtil.toSafeNamespaceDelimiter(term));
							pw.print(".html\">");
							pw.print(printableTerm);
							pw.println("</a>");
							pw.println("    </td>");

							// Relevance
							pw.println("    <td valign=\"top\" class=\"cell\">");
							pw.print("      ");
                            /*
                            pw.println(getTermRelevance(kb, getOntology(), term).equals("message")
                                       ? "&nbsp;&nbsp;&nbsp;&nbsp;MT&nbsp;&nbsp;&nbsp;&nbsp;"
                                       : "");
                            */
							pw.println("&nbsp;");
							pw.println("    </td>");

							// Documentation
							pw.println("    <td valign=\"top\" class=\"description\">");
							pw.print("    ");
							pw.println(docStr);
							pw.println("    </td>");
							pw.println("  </tr>");
						}
					}
				}
			}
			pw.println("</table>");
			pw.println("");
			pw.println(generateHtmlFooter(""));
			pw.println("  </body>");
			pw.println("</html>");
		}
		catch (Exception ex)
		{
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}
	}

	/**
	 * Get syntactic extension terms
	 */
	protected List<String> getSyntacticExtensionTerms(KB kb, String term, int targetArgnum, boolean computeClosure)
	{
		List<String> result = null;
		if (!term.isEmpty())
		{
			int idxArgnum = ((targetArgnum == 2) ? 1 : 2);
			if (computeClosure)
			{
				result = kb.getTransitiveClosureViaPredicateSubsumption("syntacticExtension", idxArgnum, term, targetArgnum, true);
			}
			else
			{
				result = kb.getTermsViaPredicateSubsumption("syntacticExtension", idxArgnum, term, targetArgnum, true);
			}
			if (result.isEmpty() && StringUtil.isLocalTermReference(term))
			{
				String gt = getFirstGeneralTerm(kb, term);
				if (!gt.isEmpty())
				{
					if (computeClosure)
					{
						result = kb.getTransitiveClosureViaPredicateSubsumption("syntacticExtension", idxArgnum, gt, targetArgnum, true);
					}
					else
					{
						result = kb.getTermsViaPredicateSubsumption("syntacticExtension", idxArgnum, gt, targetArgnum, true);
					}
				}
			}
			SetUtil.removeDuplicates(result);
		}
		return result;
	}

	/**
	 * get closest Xml data type
	 */
	protected String getClosestXmlDataType(KB kb, String term)
	{
		String xmlType = null;
		if (!term.isEmpty())
		{
			xmlType = kb.getFirstTermViaPredicateSubsumption("closestXmlDataType", 1, term, 2, false);
		}
		return xmlType;
	}

	/**
	 * Supports memoization for isInstanceOf(kb, c1, c2).
	 */
	protected static final Map<String, Set<String>> isInstanceOfCache = new HashMap<>();

	/**
	 * Returns true if i is an instance of c, else returns false.
	 *
	 * @param kb   A KB object
	 * @param inst A String denoting an instance
	 * @param c    A String denoting a Class
	 * @return true or false
	 */
	protected static boolean isInstanceOf(KB kb, String inst, String c)
	{
		Set<String> classes = isInstanceOfCache.get(inst);
		if (classes == null)
		{
			classes = kb.getAllInstanceOfsWithPredicateSubsumption(inst);
			isInstanceOfCache.put(inst, classes);
		}
		return classes.contains(c);
	}

	/**
	 * Get first data type
	 */
	protected String getFirstDatatype(KB kb, String term)
	{
		if (!term.isEmpty())
		{
			List<String> types = getDatatypeTerms(kb, term, 2);
			if (!types.isEmpty())
			{
				return types.get(0);
			}
		}
		return null;
	}

	/**
	 * Get data type terms
	 */
	protected List<String> getDatatypeTerms(KB kb, String term, @SuppressWarnings("SameParameterValue") int targetArgnum)
	{
		if (!term.isEmpty())
		{
			int idxArg = targetArgnum == 2 ? 1 : 2;
			List<String> result = kb.getTermsViaPredicateSubsumption("datatype", idxArg, term, targetArgnum, true);
			SetUtil.removeDuplicates(result);
			return result;
		}
		return new ArrayList<>();
	}

	/**
	 * Get term presentation name
	 */
	public String getTermPresentationName(KB kb, String term)
	{
		if (!term.isEmpty())
		{
			String namespace = this.getDefaultNamespace();
			if (namespace == null)
				namespace = "";
			return getTermPresentationName(kb, namespace, term);
		}
		return term;
	}

	/**
	 * Get term presentation name
	 */
	public String getTermPresentationName(KB kb, String namespace, String term)
	{
		return getTermPresentationName(kb, namespace, term, false);
	}

	/**
	 * Get term presentation name
	 */
	public String getTermPresentationName(KB kb, String namespace, String term, boolean withSpanTags)
	{
		List<String> context = new ArrayList<>();
		if (!namespace.isEmpty())
		{
			context.add(namespace);
		}
		if (!context.contains("XMLLabel"))
		{
			context.add(0, "XMLLabel");
		}
		if (!context.contains("EnglishLanguage"))
		{
			context.add("EnglishLanguage");
		}
		String name = getFirstTermFormat(kb, term, context);
		if (isEmpty(name) || name.equals(term))
		{
			name = showTermName(kb, term, namespace);
		}
		if (!namespace.isEmpty())
		{
			name = stripNamespacePrefix(name);
		}
		name = StringUtil.removeEnclosingQuotes(name);
		// The for loop below is solely to handle
		// NonIsoTerritoryCode^Worldwide.
		String[] delims = { "^" };
		for (String delim : delims)
		{
			int idx = name.indexOf(delim);
			while ((idx > -1) && (idx < name.length()))
			{
				name = name.substring(idx + 1);
				idx = name.indexOf(delim);
			}
		}
		if (withSpanTags)
		{
			if (isXmlAttribute(kb, term))
			{
				name = ("<span class=\"attribute\">" + name + "</span>");
			}
		}
		return name;
	}

	/**
	 * Sorts stringList in place by the presentation name of each its
	 * terms, which could be very different from the raw term name.
	 *
	 * @param kb            The KB from which to obtain the presentation names
	 * @param namespaceTerm A KIF term denoting a namespace
	 * @param stringList    The List of Strings to be sorted
	 */
	public void sortByPresentationName(KB kb, String namespaceTerm, List<String> stringList)
	{
		try
		{
			if (!SetUtil.isEmpty(stringList) && (stringList.size() > 1))
			{
				String kifNamespace = (isEmpty(namespaceTerm) ? "" : toKifNamespace(kb, namespaceTerm));
				List<String[]> sortable = new ArrayList<>();
				for (String s : stringList)
				{
					String[] pair = new String[] { s, getTermPresentationName(kb, kifNamespace, s) };
					sortable.add(pair);
				}
				String msg = null;
				try
				{
					sortable.sort((sa1, sa2) -> String.CASE_INSENSITIVE_ORDER.compare(sa1[1], sa2[1]));
				}
				catch (Exception ex1)
				{
					msg = ex1.getMessage();
					System.err.println(msg);
					ex1.printStackTrace();
				}
				if ((msg == null) && (sortable.size() == stringList.size()))
				{
					stringList.clear();
					for (String[] sa : sortable)
					{
						stringList.add(sa[0]);
					}
				}
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * Sorts the List terms by the length of the Strings it contains,
	 * from longest to shortest.
	 */
	protected void sortByTermLength(List<String> terms)
	{
		if (!terms.isEmpty() && (terms.size() > 1))
		{
			Comparator<String> comp = (o1, o2) -> {
				int l1 = o1.length();
				int l2 = o2.length();
				int result = 0;
				if (l1 > l2)
				{
					result = -1;
				}
				else if (l1 < l2)
				{
					result = 1;
				}
				return result;
			};
			terms.sort(comp);
		}
	}

	/**
	 * Get term name space
	 */
	public String getTermNamespace(String term)
	{
		if (!term.isEmpty())
		{
			String prefix = getNamespacePrefix(term);
			if (!prefix.isEmpty())
			{
				List<String> delims = Arrays.asList(StringUtil.getW3cNamespaceDelimiter(), StringUtil.getKifNamespaceDelimiter());
				for (String delim : delims)
				{
					if (prefix.endsWith(delim))
					{
						prefix = prefix.substring(0, prefix.length() - delim.length());
						break;
					}
				}
				return prefix.equals("ns") ? prefix : ("ns" + StringUtil.getKifNamespaceDelimiter() + prefix);
			}
		}
		return "";
	}

	/**
	 * @param isXmlDoc If true, HTML character entities will be
	 *                 replaced with their ASCII equivalents, when
	 *                 possible
	 * @param addHrefs If true, HTML anchor markup will be added
	 *                 for recognized terms
	 */
	protected String processDocString(KB kb, String kbHref, String namespace, String docString, @SuppressWarnings("SameParameterValue") boolean isXmlDoc,
			@SuppressWarnings("SameParameterValue") boolean addHrefs)
	{
		if (!docString.isEmpty())
		{
			String nsTerm = toKifNamespace(kb, namespace);
			String docString2 = StringUtil.normalizeSpaceChars(docString);
			Map<String, String> srMap = getStringReplacementMap();
			if (isXmlDoc)
			{
				if (srMap != null)
				{
					for (String fromString : srMap.keySet())
					{
						String toString = srMap.get(fromString);
						if (toString != null)
						{
							docString2 = docString2.replace(fromString, toString);
						}
					}
				}
			}
			else
			{
				// The "put" immediately below is to prevent the "&" in "&%" pairs from being replaced by the corresponding HTML entity.
				srMap.put("&%", "&%");

				StringBuilder sb = new StringBuilder(docString2);
				String amp = "&";
				int ampLen = amp.length();
				String repl = "&amp;";
				int replLen = repl.length();
				int p1f = sb.indexOf(amp);
				while (p1f > -1)
				{
					int p2f = -1;
					String token = null;
					for (String key : srMap.keySet())
					{
						token = key;
						p2f = sb.indexOf(token, p1f);
						if ((p2f > -1) && (p1f == p2f))
							break;
					}
					if ((p2f > -1) && (p1f == p2f))
					{
						p2f += token.length();
						if (p2f < sb.length())
						{
							p1f = sb.indexOf(amp, p2f);
						}
					}
					else
					{
						sb.replace(p1f, p1f + ampLen, repl);
						p2f = p1f + replLen;
						p1f = sb.indexOf(amp, p2f);
					}
				}
				docString2 = sb.toString();
			}
			docString2 = StringUtil.removeEnclosingQuotes(docString2);
			docString2 = StringUtil.removeQuoteEscapes(docString2);
			if (!docString2.isEmpty())
			{
				String commentToken = " //";
				int headPos = docString2.indexOf(commentToken);
				if ((headPos > -1) && ((headPos + 4) < docString2.length()))
				{
					String head = docString2.substring(0, headPos);
					String tail = docString2.substring(headPos + commentToken.length());
					docString2 = ("<span class=\"commentHead\">" + head + "</span><br/>" + tail);
				}
				if (addHrefs)
				{
					docString2 = DocFormat.formatDocumentation(kb, kbHref, docString2, nsTerm);
				}
			}
			return docString2;
		}
		else
		{
			return "";
		}
	}

	private static boolean isEmpty(String str)
	{
		return str == null || str.isEmpty();
	}
}
