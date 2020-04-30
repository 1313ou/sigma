package bbou.sumo;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Serializer
 *
 * @author <a href="mailto:1313ou@gmail.com">Bernard Bou</a>
 */
public class Serializer
{
	private Serializer()
	{
	}

	/**
	 * Serialize to file
	 *
	 * @param name   name (will be the filename)
	 * @param object object to serialize
	 * @throws IOException io
	 */
	public static void serializeFile(final String name, final Serializable object) throws IOException
	{
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(name)))
		{
			oos.writeObject(object);
		}
	}

	/**
	 * Serialize to archive
	 *
	 * @param archive archive
	 * @param name    name (will be the zipFile entry)
	 * @param object  object to serialize
	 * @throws IOException io
	 */
	public static void serializeZip(final String archive, final String name, final Object object) throws IOException
	{
		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive, true)))
		{
			final ZipEntry ze = new ZipEntry(name);
			zos.putNextEntry(ze);
			try (ObjectOutputStream oos = new ObjectOutputStream(zos))
			{
				oos.writeObject(object);
				zos.closeEntry();
			}
		}
	}

	/**
	 * Serialize to archive
	 *
	 * @param archive archive
	 * @param names   names of objects to serialize
	 * @param objects series of objects to serialize
	 * @throws IOException io
	 */
	public static void serializeZip(final String archive, final String[] names, final Object... objects) throws IOException
	{
		if (names.length > objects.length)
		{
			System.err.println(String.format("Zip serializer %s with objects {%s} has length-mismatch", archive, Arrays.toString(objects)));
		}
		else if (names.length < objects.length)
		{
			System.err.println(String.format("Zip serializer %s with objects {%s} has unnamed objects", archive, Arrays.toString(objects)));
		}

		try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(archive, true)))
		{
			final int max = Math.min(names.length, objects.length);
			for (int i = 0; i < max; i++)
			{
				final ZipEntry ze = new ZipEntry(names[i]);
				zos.putNextEntry(ze);
				try (ObjectOutputStream oos = new ObjectOutputStream(zos))
				{
					oos.writeObject(objects[i]);
					zos.flush();
					zos.closeEntry();
				}
			}
		}
	}
}
