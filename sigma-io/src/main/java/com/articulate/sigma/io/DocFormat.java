package com.articulate.sigma.io;

import com.articulate.sigma.KB;
import com.articulate.sigma.StringUtil;

import java.util.Arrays;
import java.util.logging.Logger;

public class DocFormat
{
	private static final Logger logger = Logger.getLogger(DocFormat.class.getName());

	/**
	 * Hyperlink terms identified with '&%' to the URL that brings up
	 * that term in the browser.  Handle (and ignore) suffixes on the
	 * term.  For example "&%Processes" would get properly linked to
	 * the term "Process", if present in the knowledge base.
	 */
	public static String formatDocumentation(KB kb, String href, String documentation, String language)
	{
		String formatted = documentation;
		try
		{
			if (!formatted.isEmpty())
			{
				boolean isStaticFile = false;
				StringBuilder sb = new StringBuilder(formatted);
				String suffix = "";
				if (href == null || href.isEmpty())
				{
					href = "";
					suffix = ".html";
					isStaticFile = true;
				}
				else if (!href.endsWith("&term="))
				{
					href += "&term=";
				}
				int i;
				int j;
				int start = 0;
				String term = "";
				String formToPrint;
				while ((start < sb.length()) && ((i = sb.indexOf("&%", start)) != -1))
				{
					sb.delete(i, (i + 2));
					j = i;
					while ((j < sb.length()) && !Character.isWhitespace(sb.charAt(j)) && sb.charAt(j) != '"')
						j++;
					while (j > i)
					{
						term = sb.substring(i, j);
						if (kb.containsTerm(term))
							break;
						j--;
					}
					if (j > i)
					{
						formToPrint = DocGen.getInstance(kb.name).showTermName(kb, term, language);
						StringBuilder hsb = new StringBuilder("<a href=\"");
						hsb.append(href);
						hsb.append(isStaticFile ? StringUtil.toSafeNamespaceDelimiter(term) : term);
						hsb.append(suffix);
						hsb.append("\">");
						hsb.append(formToPrint);
						hsb.append("</a>");
						sb.replace(i, j, hsb.toString());
						start = (i + hsb.length());
					}
				}
				formatted = sb.toString();
			}
		}
		catch (Exception ex)
		{
			logger.warning(Arrays.toString(ex.getStackTrace()));
			ex.printStackTrace();
		}
		return formatted;
	}
}
