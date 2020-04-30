/*
 * @author Bernard Bou
 * Created on 8 mai 2009
 * Filename : Main.java
 */
package bbou.sumo;

import com.articulate.sigma.KB;
import com.articulate.sigma.io.DB;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public class Converter
{
	static public void toTerms(final KB kb, final String filename) throws IOException
	{
		try (PrintStream ps = new PrintStream(new FileOutputStream(new File(filename))))
		{
			for (final String term : kb.terms)
			{
				ps.println(term);
			}
		}
	}

	static public void toSQL(final KB kb, final String filename) throws IOException
	{
		final DB dB = new DB();
		try (PrintStream ps = new PrintStream(new FileOutputStream(new File(filename))))
		{
			dB.generateDB(ps, kb);
		}
	}

	static public void toProlog(final KB kb, final String filename) throws IOException
	{
		try (PrintStream ps = new PrintStream(new FileOutputStream(new File(filename))))
		{
			kb.writeProlog(ps);
		}
	}

	static public void toSpreadsheet(final KB kb, final String filename) throws IOException
	{
		final DB dB = new DB();
		try (PrintStream ps = new PrintStream(new FileOutputStream(new File(filename))))
		{
			dB.exportTable(ps, kb);
		}
	}

	static public void toOWL(final KB kb, final String filename) throws IOException
	{
		final OWLTranslator ot = new OWLTranslator();
		ot.kb = kb;
		try (PrintStream ps = new PrintStream(new FileOutputStream(new File(filename))))
		{
			ot.write(ps);
		}
	}

	static public void toOWL2(final KB kb, final String filename) throws IOException
	{
		final com.articulate.sigma.io.OWLTranslator ot = new com.articulate.sigma.io.OWLTranslator();
		ot.kb = kb;
		try (PrintStream ps = new PrintStream(new FileOutputStream(new File(filename))))
		{
			ot.writeKB(ps);
		}
	}
}
