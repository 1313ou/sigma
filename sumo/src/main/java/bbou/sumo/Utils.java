/*
 * @author Bernard Bou
 * Created on 8 mai 2009
 * Filename : Main.java
 */
package bbou.sumo;

import com.articulate.sigma.KB;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Utils
{
	// KB factory

	/**
	 * Make KB
	 *
	 * @param kbName    KB name
	 * @param filePaths paths of KIF files to add as constituents
	 * @return KB
	 */
	static public KB toKB(final String kbName, final String[] filePaths)
	{
		final KB kb = new KB(kbName, null);
		for (final String filePath : filePaths)
		{
			kb.addConstituent(filePath);
		}
		return kb;
	}

	/**
	 * Scan dir for KIF files
	 *
	 * @param dirName path of dir containing KIF files
	 * @return KB
	 */
	static private String[] getKifs(final String dirName)
	{
		final File file = new File(dirName);
		if (file.exists() && file.isDirectory())
			return file.list((dir, name) -> {
				//$NON-NLS-1$
				return name.endsWith(".kif");
			});
		return new String[] {};
	}

	// CVS

	/**
	 * Encapsulates CVS info (version, date)
	 *
	 * @author Bernard Bou 23 juin 2009
	 */
	static class CVSEntry
	{
		String version;

		Date date;
	}

	/**
	 * Get CVS info (version, date)
	 *
	 * @param dirName dir name
	 * @return map filename->file CVS info
	 * @throws IOException io exception
	 */
	static public Map<String, CVSEntry> getCVS(final String dirName) throws IOException
	{
		final File file = new File(dirName + File.separatorChar + "CVS" + File.separatorChar + "Entries"); //$NON-NLS-1$ //$NON-NLS-2$
		if (file.exists())
		{
			final SimpleDateFormat format = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US); //$NON-NLS-1$
			final Map<String, CVSEntry> map = new HashMap<>();
			try (BufferedReader reader = new BufferedReader(new FileReader(file)))
			{
				String line;
				while ((line = reader.readLine()) != null)
				{
					if (line.startsWith("D"))
					{
						continue;
					}

					// /Merge.kif/1.53/Thu Apr 30 00:55:03 2009//
					final String[] fields = line.split("/"); //$NON-NLS-1$
					final String filename = fields[1];
					final CVSEntry entry = new CVSEntry();
					entry.version = fields[2];
					try
					{
						entry.date = format.parse(fields[3]);
					}
					catch (final ParseException e)
					{
						entry.date = new Date();
					}
					map.put(filename, entry);
				}
				return map;
			}
		}
		return null;
	}
}
