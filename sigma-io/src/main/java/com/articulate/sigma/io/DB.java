/* This code is copyrighted by Articulate Software (c) 2007.  It is
released under the GNU Public License &lt;http://www.gnu.org/copyleft/gpl.html&gt;."\""

Users of this code also consent, by use of this code, to credit
Articulate Software in any writings, briefings, publications,
presentations, or other representations of any software which
incorporates, builds on, or uses this code.  Please cite the following
article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment, in Working
Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico.  See also http://sigmakee.sourceforge.net.
 */

/* *********************************************************************************************/
package com.articulate.sigma.io;

import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;
import com.articulate.sigma.KBManager;
import com.articulate.sigma.StringUtil;

import java.io.*;
import java.util.*;

/**
 * A class to interface with databases and database-like formats,
 * such as spreadsheets.
 */
public class DB
{
	// A map of word keys, broken down by POS, listing whether it's a positive or negative word
	// keys are pre-defined as type, POS, stemmed, polarity
	public static final Map<String, Map<String, String>> sentiment = new HashMap<>();

	/**
	 * This procedure is called by @see generateDB().  It generates
	 * SQL statements of some of the following forms:
	 * create table [table name] (personid int(50),firstname
	 * varchar(35));
	 * alter table [table name]
	 * add column [new column name] varchar (20);
	 * drop database [database name];
	 * INSERT INTO [table name]
	 * (Host,Db,User,Select_priv,Insert_priv,Update_priv,Delete_priv,Create_priv,Drop_priv)
	 * VALUES
	 * ('%','databasename','username','Y','Y','Y','Y','Y','N');
	 */
	private void generateDBElement(PrintStream ps, KB kb, String element)
	{
		List<Formula> docs = kb.askWithRestriction(0, "localDocumentation", 3, element);
		ps.println("ALTER TABLE " + element + " add column documentation varchar(255);");
		if (docs.size() > 0)
		{
			Formula f = docs.get(0);
			String doc = f.getArgument(4);
			ps.println("INSERT INTO " + element + "(documentation) values ('" + doc + "');");
		}
		List<Formula> subs = kb.askWithRestriction(0, "HasDatabaseColumn", 1, element);
		for (Formula f : subs)
		{
			String t = f.getArgument(2);
			ps.println("ALTER TABLE " + element + " add column " + t + " varchar(255);");
		}
	}

	/**
	 * Generate an SQL database from the knowledge base
	 * Tables must be defined as instances of &%DatabaseTable and
	 * must have &%localDocumentation and &%HasDatabaseColumn
	 * relations.
	 */
	public void generateDB(PrintStream ps, KB kb)
	{
		ps.println("CREATE DATABASE " + kb.name + ";");
		List<Formula> composites = kb.askWithRestriction(0, "instance", 2, "DatabaseTable");
		for (Formula f : composites)
		{
			String element = f.getArgument(1);
			ps.println("CREATE TABLE " + element + ";");
			generateDBElement(ps, kb, element);
		}
	}

	/**
	 * Parse the input from a Reader for a CSV file into an List
	 * of Lists.  If lineStartTokens is a non-empty list, all
	 * lines not starting with one of the String tokens it contains
	 * will be concatenated.
	 *
	 * @param inReader        A reader for the file to be processed
	 * @param lineStartTokens If a List containing String tokens, all
	 *                        lines not starting with one of the tokens will be concatenated
	 * @param quote           signifies whether to retain quotes in elements
	 * @return An List of Lists
	 */
	public static List<List<String>> readSpreadsheet(Reader inReader, List<String> lineStartTokens, boolean quote, char delimiter)
	{
		List<List<String>> rows = new ArrayList<>();
		StringBuilder cell = new StringBuilder();
		try (LineNumberReader lr = new LineNumberReader(inReader))
		{
			List<String> textrows = new ArrayList<>();
			boolean areTokensListed = ((lineStartTokens != null) && !lineStartTokens.isEmpty());
			String line;
			while ((line = lr.readLine()) != null)
			{
				try
				{
					if (StringUtil.containsNonAsciiChars(line))
						System.err.println("\nINFO in DB.readSpreadsheet(): NonASCII char near line " + lr.getLineNumber() + ": " + line + "\n");
					line += " ";

					// Concatenate lines not starting with one of the tokens in lineStartTokens.
					boolean concat = false;
					if (areTokensListed)
					{
						String unquoted = StringUtil.unquote(line);
						for (String token : lineStartTokens)
						{
							if (unquoted.startsWith(token))
							{
								concat = true;
								break;
							}
						}
					}
					if (concat && !textrows.isEmpty())
					{
						int trLen = textrows.size();
						String previousLine = textrows.get(trLen - 1);
						line = previousLine + line;
						textrows.remove(trLen - 1);
					}
					textrows.add(line);
				}
				catch (Exception ex1)
				{
					System.err.println("ERROR in ENTER DB.readSpreadsheet(" + inReader + ", " + lineStartTokens + ")");
					System.err.println("  approx. line # == " + lr.getLineNumber());
					System.err.println("  line == " + line);
					ex1.printStackTrace();
				}
			}

			for (String line2 : textrows)
			{
				// parse comma delimited cells into an List
				int line2Len = line2.length();
				cell.setLength(0);
				List<String> row = new ArrayList<>();
				boolean inString = false;
				for (int j = 0; j < line2Len; j++)
				{
					if (line2.charAt(j) == delimiter && !inString)
					{
						String cellVal = cell.toString();
						// cellVal = cellVal.trim()
						if (cellVal.matches(".*\\w+.*"))
							cellVal = cellVal.trim();
						if (!quote)
							cellVal = StringUtil.removeEnclosingQuotes(cellVal);
						row.add(cellVal);
						cell.setLength(0);
						// cell = new StringBuilder();
					}
					else
					{
						if ((line2.charAt(j) == '"') && ((j == 0) || (line2.charAt(j - 1) != '\\')))
							inString = !inString;
						cell.append(line2.charAt(j));
					}
				}
				String cellVal = cell.toString();
				// cellVal = cellVal.trim();
				if (cellVal.matches(".*\\w+.*"))
					cellVal = cellVal.trim();
				if (!quote)
					cellVal = StringUtil.removeEnclosingQuotes(cellVal);
				row.add(cellVal);
				rows.add(row);
			}
		}
		catch (Exception e)
		{
			System.err.println("ERROR in DB.readSpreadsheet(" + inReader + ", " + lineStartTokens + ")");
			System.err.println("  cell == " + cell.toString());
			e.printStackTrace();
		}
		return rows;
	}

	/**
	 * Parse a CSV file into an List of Lists.  If
	 * lineStartTokens is a non-empty list, all lines not starting
	 * with one of the String tokens it contains will be concatenated.
	 *
	 * @param fileName        The pathname of the CSV file to be processed
	 * @param lineStartTokens If a List containing String tokens, all
	 *                        lines not starting with one of the tokens will be concatenated
	 * @param quote           signifies whether to retain quotes in elements
	 * @return An List of Lists
	 */
	public static List<List<String>> readSpreadsheet(String fileName, List<String> lineStartTokens, boolean quote, char delimiter)
	{
		//List<List<String>> rows = new ArrayList<>();
		try (FileReader fr = new FileReader(fileName))
		{
			return readSpreadsheet(fr, lineStartTokens, quote, delimiter);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Read spreadsheet
	 */
	public static List<List<String>> readSpreadsheet(String fileName, List<String> lineStartTokens, boolean quote)
	{
		return readSpreadsheet(fileName, lineStartTokens, quote, ',');
	}

	/**
	 * Collect relations in the knowledge base
	 *
	 * @return The set of relations in the knowledge base.
	 */
	private List<String> getRelations(KB kb)
	{
		List<String> relations = new ArrayList<>();
		synchronized (kb.getTerms())
		{
			for (String term : kb.getTerms())
			{
				if (kb.isInstanceOf(term, "Predicate"))
					relations.add(term.intern());
			}
		}
		return relations;
	}

	/**
	 * Print a comma-delimited matrix.  The values of the rows
	 * are SortedMaps, whose values in turn are Strings.  The List of
	 * relations forms the column headers, which are Strings.
	 *
	 * @param rows      - the matrix
	 * @param relations - the relations that form the column header
	 */
	public void printSpreadsheet(PrintStream ps, SortedMap<String, SortedMap<String, String>> rows, List<String> relations)
	{
		StringBuilder line = new StringBuilder();
		line.append("Domain/Range,");
		for (int i = 0; i < relations.size(); i++)
		{
			String relation = relations.get(i);
			line.append(relation);
			if (i < relations.size() - 1)
				line.append(",");
		}
		ps.println(line);
		for (String term : rows.keySet())
		{
			SortedMap<String, String> row = rows.get(term);
			ps.print(term + ",");
			for (int i = 0; i < relations.size(); i++)
			{
				String relation = relations.get(i);
				if (row.get(relation) == null)
					ps.print(",");
				else
				{
					ps.print(row.get(relation));
					if (i < relations.size() - 1)
						ps.print(",");
				}
				if (i == relations.size() - 1)
					ps.println();
			}
		}
	}

	/**
	 * Export a comma-delimited table of all the ground binary
	 * statements in the knowledge base.  Only the relations that are
	 * actually used are included in the header.
	 */
	public void exportTable(PrintStream ps, KB kb)
	{
		List<String> relations = getRelations(kb);
		List<String> usedRelations = new ArrayList<>();
		SortedMap<String, SortedMap<String, String>> rows = new TreeMap<>();
		for (String term : relations)
		{
			List<Formula> statements = kb.ask("arg", 0, term);
			if (statements != null)
			{
				for (Formula f : statements)
				{
					String arg1 = f.getArgument(1);
					if (Character.isUpperCase(arg1.charAt(0)) && !arg1.endsWith("Fn"))
					{
						if (!usedRelations.contains(term))
							usedRelations.add(term);
						SortedMap<String, String> row;
						if (rows.get(f.getArgument(1)) == null)
						{
							row = new TreeMap<>();
							rows.put(arg1, row);
						}
						else
							row = rows.get(arg1);
						if (row.get(term) == null)
							row.put(term, f.getArgument(2));
						else
						{
							String element = row.get(term);
							element = element + "/" + f.getArgument(2);
							row.put(term, element);
						}
					}
				}
			}
		}
		printSpreadsheet(ps, rows, usedRelations);
	}

	/**
	 * Word wrap
	 */
	public static String wordWrap(String input, int length)
	{
		return StringUtil.wordWrap(input, length);
	}

	/**
	 * Is empty
	 */
	public static boolean emptyString(String str)
	{
		return str == null || str.isEmpty();
	}

	/**
	 * Fill out from a CSV file a map of word keys, and values broken down by POS,
	 * listing whether it's a positive or negative word interior hash map keys are
	 * type, POS, stemmed, polarity
	 *
	 * @return void side effect on static variable "sentiment"
	 */
	public static void readSentimentArray()
	{
		if (sentiment.size() > 0)
		{
			System.err.println("Error in DB.readSentimentArray(): file previously read.");
			return;
		}
		List<List<String>> f = DB.readSpreadsheet(KBManager.getMgr().getPref("kbDir") + File.separator + "sentiment.csv", null, false);
		for (List<String> al : f)
		{
			Map<String, String> entry = new HashMap<>();
			entry.put("type", al.get(0));   // weak, strong
			entry.put("POS", al.get(2));    // noun,verb,adj,adverb,anypos
			entry.put("stemmed", al.get(3));   // y,n
			entry.put("polarity", al.get(4));  // positive, negative
			sentiment.put(al.get(1), entry);
		}
	}

}
