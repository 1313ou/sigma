package bbou.sumo;

import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;
import com.articulate.sigma.io.DB;
import com.articulate.sigma.io.DocGen;
import com.articulate.sigma.io.OWLTranslator;

import java.io.*;
import java.util.List;

public class SUMOUtils
{
	public static void dumpTerms(final PrintStream ps, final KB kb)
	{
		int i = 0;
		for (final String term : kb.terms)
		{
			i++;
			ps.print("term " + i + "=" + term);
			ps.println(" doc=" + SUMOUtils.getDoc(kb, term));
			ps.println(" isQ" + KB.isQuantifier(term));
			SUMOUtils.dumpParents(ps, kb, term);
			SUMOUtils.dumpChildren(ps, kb, term);
		}
	}

	public static void dumpParents(final PrintStream ps, final KB kb, final String term)
	{
		final List<Formula> formulas = kb.askWithRestriction(0, "subclass", 1, term);
		if (formulas != null && !formulas.isEmpty())
		{
			int i = 0;
			for (final Formula formula : formulas)
			{
				i++;
				final String formulaString = formula.getArgument(2);
				ps.print("\tparent" + i + "=" + formulaString);
				ps.println(" doc=" + SUMOUtils.getDoc(kb, formulaString));
			}
		}
	}

	public static void dumpChildren(final PrintStream ps, final KB kb, final String term)
	{
		final List<Formula> formulas = kb.askWithRestriction(0, "subclass", 2, term);
		if (formulas != null && !formulas.isEmpty())
		{
			int i = 0;
			for (final Formula formula : formulas)
			{
				i++;
				final String formulaString = formula.getArgument(1);
				ps.print("\tchild" + i + "=" + formulaString);
				ps.println(" doc=" + SUMOUtils.getDoc(kb, formulaString));
			}
		}
	}

	static void dumpFormulas(@SuppressWarnings("SameParameterValue") final PrintStream ps, final KB kb)
	{
		int i = 0;
		for (final Formula formula : kb.formulaMap.values())
		{
			i++;
			ps.println(i + " " + formula);
			ps.println(i + " " + formula.text);
		}
	}

	static void dumpPredicates(@SuppressWarnings("SameParameterValue") final PrintStream ps, final KB kb)
	{
		final List<String> predicates = kb.collectPredicates();
		int i = 0;
		for (final String predicate : predicates)
		{
			i++;
			ps.println(i + " " + predicate);
		}
	}

	private static String getDoc(final KB kb, final String term)
	{
		final List<Formula> formulas = kb.askWithRestriction(0, "documentation", 1, term);
		if (formulas != null && !formulas.isEmpty())
		{
			final Formula formula = formulas.get(0);
			String doc = formula.getArgument(2); // Note this will become 3 if we add language to documentation
			// doc = kb.formatDocumentation("http://", doc);
			doc = doc.replaceAll("\\n", "");
			return doc;
		}
		return null;
	}

	public interface PrintStreamFactory
	{
		PrintStream makePrintStream() throws IOException;
	}

	public static PrintStream toFile(final String filename) throws FileNotFoundException
	{
		return new PrintStream(new FileOutputStream(new File(filename)));
	}

	public static void toOWL(final KB kb, final PrintStreamFactory psFactory) throws IOException
	{
		final OWLTranslator oWLTranslator = new OWLTranslator();
		oWLTranslator.kb = kb;
		try (PrintStream ps = psFactory.makePrintStream())
		{
			oWLTranslator.writeKB(ps);
		}
	}

	private static void toProlog(final KB kb, final PrintStreamFactory psFactory) throws IOException
	{
		try (PrintStream ps = psFactory.makePrintStream())
		{
			kb.writeProlog(ps);
		}
	}

	public static void toSQL(final KB kb, final PrintStreamFactory psFactory) throws IOException
	{
		final DB dB = new DB();
		try (PrintStream ps = psFactory.makePrintStream())
		{
			dB.generateDB(ps, kb);
		}
	}

	public static void toSpreadsheet(final KB kb, final PrintStreamFactory psFactory) throws IOException
	{
		final DB dB = new DB();
		try (PrintStream ps = psFactory.makePrintStream())
		{
			dB.exportTable(ps, kb);
		}
	}

	public static void toHtml(final KB kb, final String dirName) throws IOException
	{
		final DocGen docGen = DocGen.getInstance();
		docGen.setOutputDirectoryPath(dirName);
		docGen.generateHTML(kb, "english", "T");
	}

	/**
	 * Main entry point
	 *
	 * @param args arguments
	 * @throws Exception exception
	 */
	public static void main(final String[] args) throws Exception
	{
		final KB kb = new SUMOKb().make("/opt/nlp/SumoKB");

		System.out.println(">>>>>>>>>>");

		SUMOUtils.dumpTerms(System.out, kb);
		SUMOUtils.dumpFormulas(System.out, kb);
		SUMOUtils.dumpPredicates(System.out, kb);

		SUMOUtils.toOWL(kb, () -> toFile("sumo.owl"));
		SUMOUtils.toProlog(kb, () -> toFile("sumo.pl"));
		SUMOUtils.toHtml(kb, "./doc");

		System.out.println("<<<<<<<<<<");
	}
}
