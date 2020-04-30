package bbou.sumo;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * DeSerializer.java
 *
 * @author <a href="mailto:1313ou@gmail.com">Bernard Bou</a>
 */
public class DeSerializer
{
	private DeSerializer()
	{
	}

	/**
	 * Deserialize from file
	 *
	 * @param name name (will be the filename)
	 * @return deserialized object
	 * @throws IOException            io
	 * @throws ClassNotFoundException class
	 */
	public static Object deserializeFile(final String name) throws IOException, ClassNotFoundException
	{
		try (ObjectInputStream oos = new ObjectInputStream(new FileInputStream(name)))
		{
			return oos.readObject();
		}
	}

	/**
	 * Deserialize from archive
	 *
	 * @param archive archive
	 * @param name    (will be the zipFile entry)
	 * @return deserialized object
	 * @throws IOException            io
	 * @throws ClassNotFoundException class
	 */
	public static Object deserializeZip(final String archive, final String name) throws IOException, ClassNotFoundException
	{
		try (final ZipFile zipFile = new ZipFile(archive))
		{
			final ZipEntry ze = zipFile.getEntry(name);
			if (ze != null)
			{
				try (ObjectInputStream ois = new ObjectInputStream(zipFile.getInputStream(ze)))
				{
					return ois.readObject();
				}
			}
		}
		throw new ClassNotFoundException(name);
	}

	/**
	 * Deserialize all from archive
	 *
	 * @param archive archive
	 * @return list of tables
	 * @throws IOException            io
	 * @throws ClassNotFoundException class
	 */
	public static List<Object[]> deserializeZip(final String archive) throws IOException, ClassNotFoundException
	{
		final List<Object[]> tables = new ArrayList<>();
		try (ZipFile zipFile = new ZipFile(archive))
		{
			final Enumeration<? extends ZipEntry> zei = zipFile.entries();
			while (zei.hasMoreElements())
			{
				final ZipEntry ze = zei.nextElement();
				try (ObjectInputStream ois = new ObjectInputStream(zipFile.getInputStream(ze)))
				{
					tables.add((Object[]) ois.readObject());
				}
			}
		}
		return tables;
	}
}
