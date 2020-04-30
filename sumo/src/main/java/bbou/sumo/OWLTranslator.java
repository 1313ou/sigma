/*
 * @author Bernard Bou
 * @author Adam Pease
 */
package bbou.sumo;

import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/* This code is copyright Articulate Software (c) 2004.
 This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
 Users of this code also consent, by use of this code, to credit Articulate Software
 in any writings, briefings, publications, presentations, or 
 other representations of any software which incorporates, builds on, or uses this 
 code.  Please cite the following article in any publication with references:

 Pease, A., (2003). The Sigma Ontology Development Environment, 
 in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
 August 9, Acapulco, Mexico.
 */

/**
 * Read and write OWL format from Sigma data structures.
 */
public class OWLTranslator
{
	/**
	 * <code>kb</code> is cached kb to be expressed in OWL
	 */
	public KB kb;

	/**
	 * Write this term as class
	 *
	 * @param pw           print writer
	 * @param term         term
	 * @param superClasses class's superclasses
	 */
	@SuppressWarnings("nls") private void writeClass(final PrintWriter pw, final String term, final List<String> superClasses)
	{
		pw.println("<owl:Class rdf:ID=\"" + term + "\">");
		writeDoc(pw, term);

		if (superClasses != null)
		{
			for (final String superClass : superClasses)
			{
				assert Formula.atom(superClass);
				pw.println("  <rdfs:subClassOf rdf:resource=\"#" + superClass + "\"/>");
			}
		}
		pw.println("</owl:Class>");
		pw.println();
	}

	/**
	 * Write this term as instance
	 *
	 * @param pw           print writer
	 * @param term         term
	 * @param classes      classes the term is instance of
	 * @param superClasses superclasses the term is subclass of (this instance is itself a class)
	 */
	@SuppressWarnings("nls") private void writeInstance(final PrintWriter pw, final String term, final List<String> classes, final List<String> superClasses)
	{
		pw.println("<owl:Thing rdf:ID=\"" + term + "\">");
		writeDoc(pw, term);

		// instance of these classes
		if (classes != null)
		{
			for (final String clazz : classes)
			{
				assert Formula.atom(clazz);
				pw.println("  <rdf:type rdf:resource=\"#" + clazz + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
				if (clazz.equals("Class"))
				{
					pw.println("  <rdf:type rdf:resource=\"http://www.w3.org/2002/07/owl#Class\"/>");
				}
			}
		}

		// subclass of these classes
		if (superClasses != null)
		{
			pw.println("  <rdf:type rdf:resource=\"http://www.w3.org/2002/07/owl#Class\"/>");
			for (final String superClass : superClasses)
			{
				assert Formula.atom(superClass);
				pw.println("  <rdfs:subClassOf rdf:resource=\"#" + superClass + "\"/>"); //$NON-NLS-1$ //$NON-NLS-2$
			}
		}
		pw.println("</owl:Thing>");
		pw.println();
	}

	/**
	 * Write this term as relation
	 *
	 * @param pw   print writer
	 * @param term term
	 */
	@SuppressWarnings("nls") private void writeRelation(final PrintWriter pw, final String term)
	{
		pw.println("<owl:ObjectProperty rdf:ID=\"" + term + "\">"); //$NON-NLS-1$//$NON-NLS-2$
		writeDoc(pw, term);

		// domain
		final List<String> domains = getRelated("domain", "1", term, 1, 2, 3);
		if (domains != null)
		{
			for (final String domain : domains)
			{
				assert Formula.atom(domain);
				pw.println("  <rdfs:domain rdf:resource=\"#" + domain + "\" />");
			}
		}

		// range
		final List<String> ranges = getRelated("domain", "2", term, 1, 2, 3);
		if (ranges != null)
		{
			for (final String range : ranges)
			{
				assert Formula.atom(range);
				pw.println("  <rdfs:range rdf:resource=\"#" + range + "\" />");
			}
		}

		// super properties
		final List<String> superProperties = getRelated("subrelation", term, 1, 2);
		if (superProperties != null)
		{
			for (final String superProperty : superProperties)
			{
				assert Formula.atom(superProperty);
				pw.println("  <owl:subPropertyOf rdf:resource=\"#" + superProperty + "\" />");
			}
		}
		pw.println("</owl:ObjectProperty>");
		pw.println();
	}

	/**
	 * Write this term's documentation
	 *
	 * @param pw   print writer
	 * @param term term
	 */
	@SuppressWarnings("nls") private void writeDoc(final PrintWriter pw, final String term)
	{
		final List<String> docs = getRelated("documentation", term, 1, 3);
		if (docs == null || docs.isEmpty())
			return;
		pw.println("  <rdfs:comment>" + OWLTranslator.processDoc(docs.get(0)) + "</rdfs:comment>");
	}

	/**
	 * Process doc string
	 *
	 * @param doc doc string
	 * @return processed doc string
	 */
	@SuppressWarnings("nls") public static String processDoc(final String doc)
	{
		String result = doc;
		result = result.replaceAll("&%", "");
		result = result.replaceAll("&", "&#38;");
		result = result.replaceAll(">", "&gt;");
		result = result.replaceAll("<", "&lt;");
		return result;
	}

	/**
	 * Get terms related to this term in formulas
	 *
	 * @param relationOp relation operator in formula
	 * @param term       term
	 * @param termPos    term's position
	 * @param xPos       target position
	 * @return list of terms
	 */
	@SuppressWarnings({}) private List<String> getRelated(final String relationOp, final String term, final int termPos, final int xPos)
	{
		final List<Formula> formulas = this.kb.askWithRestriction(0, relationOp, termPos, term);
		if (formulas == null || formulas.isEmpty())
			return null;
		final List<String> terms = new ArrayList<>();
		for (final Formula formula : formulas)
		{
			terms.add(formula.getArgument(xPos));
		}
		return terms;
	}

	/**
	 * Get terms related to this term in formulas having given argument. Same as above except the formula must have extra argument at given position.
	 *
	 * @param relationOp relation operator in formula
	 * @param arg        required argument
	 * @param term       term
	 * @param termPos    term's position
	 * @param argPos     argument position
	 * @param xPos       target position
	 * @return list of terms
	 */
	@SuppressWarnings({}) private List<String> getRelated(@SuppressWarnings("SameParameterValue") final String relationOp, final String arg, final String term,
			@SuppressWarnings("SameParameterValue") final int termPos, @SuppressWarnings("SameParameterValue") final int argPos,
			@SuppressWarnings("SameParameterValue") final int xPos)
	{
		final List<Formula> formulas = this.kb.askWithRestriction(0, relationOp, termPos, term);
		if (formulas == null || formulas.isEmpty())
			return null;
		final List<String> terms = new ArrayList<>();
		for (final Formula formula : formulas)
			if (formula.getArgument(argPos).equals(arg))
			{
				terms.add(formula.getArgument(xPos));
			}
		return terms;
	}

	/**
	 * Write OWL
	 *
	 * @param ps print stream
	 */
	public void write(final PrintStream ps)
	{
		try (PrintWriter pw = new PrintWriter(ps))
		{
			pw.println("<rdf:RDF");
			pw.println("xmlns:rdf =\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"");
			pw.println("xmlns:rdfs=\"http://www.w3.org/2000/01/rdf-schema#\"");
			pw.println("xmlns:owl =\"http://www.w3.org/2002/07/owl#\">");

			pw.println("<owl:Ontology rdf:about=\"\">");
			pw.println("<rdfs:comment xml:lang=\"en\">A provisional and necessarily lossy translation to OWL.  Please see");
			pw.println("www.ontologyportal.org for the original KIF, which is the authoritative");
			pw.println("source.  This software is released under the GNU Public License");
			pw.println("www.gnu.org.</rdfs:comment>");
			pw.println("<rdfs:comment xml:lang=\"en\">BB");
			pw.println("www.gnu.org.</rdfs:comment>");
			pw.println("</owl:Ontology>");

			for (final String term : this.kb.terms)
			{
				if (term.indexOf('>') != -1 || term.indexOf('<') != -1 || term.contains("-1"))
				{
					continue;
				}

				// attributes
				final List<String> classes = getRelated("instance", term, 1, 2); // (instance t x)
				final List<String> superclasses = getRelated("subclass", term, 1, 2); // (subclass t x)
				final List<String> subclasses = getRelated("subclass", term, 2, 1); // (subclass x t)
				final boolean isBinaryRelation = this.kb.childOf(term, "BinaryRelation");
				final boolean isClass = superclasses != null && !superclasses.isEmpty() || subclasses != null && !subclasses.isEmpty();
				final boolean isInstance = classes != null && !classes.isEmpty();
				// boolean isFunction = this.kb.childOf(term, "Function");

				// is a relation
				if (isBinaryRelation)
				{
					writeRelation(pw, term);
				}
				else if (isInstance)
				{
					writeInstance(pw, term, classes, superclasses);
				}
				else if (isClass)
				{
					writeClass(pw, term, superclasses);
				}
			}
			pw.println("</rdf:RDF>");
		}
	}
}
