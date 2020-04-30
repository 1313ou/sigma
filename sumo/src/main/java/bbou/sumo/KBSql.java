/*
 * @author Bernard Bou
 */
package bbou.sumo;

import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extended KB (to allow for loading from SQL database)
 *
 * @author Bernard Bou 23 juin 2009
 */
public class KBSql extends KB
{
	/**
	 * Constructor
	 *
	 * @param name name of the KB
	 * @param dir  location where KBs preprocessed for Vampire should be placed.
	 */
	public KBSql(final String name, final String dir)
	{
		super(name, dir);
	}

	/**
	 * Add a new KB constituent by reading in the file, and then merging the formulas with the existing set of formulas.
	 *
	 * @param url          JDBC url
	 * @param user         database username
	 * @param password     database username password
	 * @param query        SQL query on formulas
	 * @param buildCachesP If true, forces the assertion caches to be rebuilt
	 * @param loadVampireP If true, destroys the old Vampire process and starts a new one
	 * @return log
	 */
	@SuppressWarnings({ "nls" }) public String addSqlConstituent(final String url, final String user, final String password, final String query,
			final boolean buildCachesP, final boolean loadVampireP)
	{
		final StringBuilder result = new StringBuilder();
		try
		{
			// query as path
			final String canonicalPath = url + '?' + query;
			if (this.constituents.contains(canonicalPath))
				return "ERROR: " + canonicalPath + " already loaded.";

			// kif
			final KIFSql kif = new KIFSql();
			try
			{
				kif.readSql(url, user, password, query);
				this.errors.addAll(kif.warningSet);
			}
			catch (final Exception ex1)
			{
				result.append(ex1.getMessage());
				if (ex1 instanceof ParseException)
				{
					result.append(" at line ").append(((ParseException) ex1).getErrorOffset());
				}
				result.append(" in file ").append(canonicalPath);
				return result.toString();
			}

			// Iterate through the formulas, adding them to the KB, at the appropriate key.
			int count = 0;
			for (final String key : kif.formulas.keySet())
			{
				// trace
				if (count++ % 100 == 1)
				{
					System.out.print(".");
				}

				// make sure key has (empty) value
				List<Formula> value = this.formulas.computeIfAbsent(key, k -> new ArrayList<>());

				// merge
				for (final Formula f : kif.formulas.get(key))
				{
					final String internedFormula = f.text.intern();
					if (!value.contains(f))
					{
						f.setSourceFile(canonicalPath);
						value.add(f);
						this.formulaMap.put(internedFormula, f);
					}
					else
					{
						result.append("Warning: Duplicate axiom in ");
						result.append(f.getSourceFile()).append(" at line ").append(f.startLine).append("<BR>");
						result.append(f.text).append("<P>");
						final Formula existingFormula = this.formulaMap.get(internedFormula);
						result.append("Warning: Existing formula appears in ");
						result.append(existingFormula.getSourceFile()).append(" at line ").append(existingFormula.startLine).append("<BR>");
						result.append("<P>");
					}
				}
			}

			// terms
			this.terms.addAll(kif.terms);

			// add constituent name
			if (!this.constituents.contains(canonicalPath))
			{
				this.constituents.add(canonicalPath);
			}

			// trace
			System.out.println("File " + canonicalPath);

			// clear the formatMap and termFormatMap for this KB.
			this.loadFormatMapsAttempted.clear();
			if (this.formatMap != null)
			{
				this.formatMap.clear();
			}
			if (this.termFormatMap != null)
			{
				this.termFormatMap.clear();
			}

			// follow up
			if (buildCachesP && !canonicalPath.endsWith(KB._cacheFileSuffix))
			{
				buildRelationCaches();
			}
		}
		catch (final Exception ex)
		{
			result.append(ex.getMessage());
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}
		return result.toString();
	}
}
