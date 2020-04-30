/*
 * @author Bernard Bou
 */
package bbou.sumo;

import com.articulate.sigma.KBManager;
import com.articulate.sigma.kif.KIF;

import java.io.Reader;
import java.io.StringReader;
import java.sql.*;

/**
 * KIF, extended to read from SQL database
 *
 * @author Bernard Bou 23 juin 2009
 */
public class KIFSql extends KIF
{
	/**
	 * Constructor
	 */
	public KIFSql()
	{
		super();
	}

	/**
	 * Read a KIF query.
	 *
	 * @param url      JDBC url
	 * @param user     database username
	 * @param password database username password
	 * @param query    SQL query on formulas
	 */
	@SuppressWarnings("nls") public void readSql(final String url, final String user, final String password, final String query) throws Exception
	{
		Exception exThr = null;
		try (Reader r = getReader(url, user, password, query))
		{
			parse(r);
		}
		catch (final Exception ex)
		{
			exThr = ex;
			final String er = ex.getMessage();
			System.err.println("ERROR in KIF.readSql(\"" + query + "\")");
			System.err.println("  " + er);
			KBManager.getMgr().setError(KBManager.getMgr().getError() + "\n<br/>" + er + " in file " + query + "\n<br/>");
		}
		// do nothing
		if (exThr != null)
			throw exThr;
	}

	/**
	 * Get reader
	 *
	 * @param url      JDBC url
	 * @param user     database username
	 * @param password database username password
	 * @param query    SQL query on formulas
	 * @return Reader
	 * @throws SQLException SQL exception
	 */
	private Reader getReader(final String url, final String user, final String password, final String query) throws SQLException
	{
		final StringBuilder buffer = new StringBuilder();
		final Connection connection = DriverManager.getConnection(url, user, password);
		final Statement statement = connection.createStatement();
		final ResultSet resultSet = statement.executeQuery(query);
		while (resultSet.next())
		{
			buffer.append(resultSet.getString("formula")); //$NON-NLS-1$
		}
		resultSet.close();
		connection.close();
		return new StringReader(buffer.toString());
	}
}
