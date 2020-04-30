package bbou.sumo;

import com.articulate.sigma.KB;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SUMOKb extends KB implements Serializable
{
	private static final String[] CORE_FILES = new String[] { "Merge.kif", "Mid-level-ontology.kif", "english_format.kif" };

	public static final boolean full = false;

	public static SUMOKb kB;

	private String[] filenames;

	public SUMOKb()
	{
		super("SUMO", "");
	}

	public SUMOKb make(final String dirName)
	{
		this.filenames = SUMOKb.getFiles(dirName);
		final String[] filePaths = new String[this.filenames.length];
		for (int i = 0; i < filePaths.length; i++)
		{
			filePaths[i] = dirName + File.separatorChar + this.filenames[i];
		}
		SUMOKb.makeKB(this, filePaths);
		return this;
	}

	private static void makeKB(final KB kb, final String[] filePaths)
	{
		for (final String filePath : filePaths)
		{
			System.out.println("\n" + filePath);
			kb.addConstituent(filePath);
		}
	}

	protected static String[] getFiles(final String dirName)
	{
		if (SUMOKb.full)
		{
			final List<String> list = new ArrayList<>(Arrays.asList(SUMOKb.CORE_FILES));
			for (final String filename : SUMOKb.getKifs(dirName))
			{
				if (list.contains(filename))
				{
					continue;
				}
				list.add(filename);
			}
			return list.toArray(new String[0]);
		}
		return SUMOKb.CORE_FILES;
	}

	private static String[] getKifs(final String dirName)
	{
		final File file = new File(dirName);
		if (file.exists() && file.isDirectory())
			return file.list((dir, name) -> name.endsWith(".kif"));
		return new String[] {};
	}

	public String[] getFilenames()
	{
		return this.filenames;
	}
}
