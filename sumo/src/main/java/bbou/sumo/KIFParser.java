/*
 * @author Adam Pease
 * @author Bernard Bou
 * Encapsulates the parser functionality of KIF
 */
package bbou.sumo;

import com.articulate.sigma.Formula;
import com.articulate.sigma.kif.StreamTokenizer_s;

import java.util.*;

/**
 * A class designed to parse a Formula's elements relations to the formula.
 *
 * @author Adam Pease
 * @author Bernard Bou (functionality factored out from KIF.java)
 */
public class KIFParser
{
	/**
	 * This class encapsulates what relates a token in a logical statement to the entire statement. The type is arg when the term is nested only within one pair
	 * of parentheses. The other possible types are "ant" for rule antecedent, "cons" for rule consequent, and "stmt" for cases where the term is nested inside
	 * multiple levels of parentheses. argumentNum is only meaningful when the type is "arg"
	 */
	static class SUMOParse
	{
		final boolean isInAntecedent;

		final boolean isInConsequent;

		final boolean isArg;

		final boolean isStatement;

		final int argumentNum;

		/**
		 * Constructor
		 *
		 * @param inAntecedent - whether the term appears in the antecedent of a rule.
		 * @param inConsequent - whether the term appears in the consequent of a rule.
		 * @param argumentNum  - the argument position in which the term appears. The predicate position is argument 0. The first argument is 1 etc.
		 * @param parenLevel   - if the paren level is > 1 then the term appears nested in a statement and the argument number is ignored.
		 */
		public SUMOParse(final boolean inAntecedent, final boolean inConsequent, final int argumentNum, final int parenLevel)
		{
			this.isInAntecedent = inAntecedent;
			this.isInConsequent = inConsequent;
			this.isArg = !inAntecedent && !inConsequent && parenLevel == 1;
			this.argumentNum = this.isArg ? argumentNum : -1;
			this.isStatement = !inAntecedent && !inConsequent && parenLevel > 1;
		}

		@SuppressWarnings("nls") @Override public String toString()
		{
			if (this.isInAntecedent)
				return "ant";
			else if (this.isInConsequent)
				return "cons";
			else if (this.isArg)
				return "arg-" + this.argumentNum;
			else if (this.isStatement)
				return "stmt";
			return "***";
		}
	}

	/**
	 * Class encapsulation of key
	 *
	 * @author Bernard Bou 23 juin 2009
	 */
	static class Key
	{
		private final String key;

		Key(final String s)
		{
			this.key = s;
		}

		Key(final Formula f)
		{
			this.key = f.toString();
		}

		@Override public String toString()
		{
			return this.key;
		}

		@SuppressWarnings("nls") static public Key make(final String sVal, final boolean inAntecedent, final boolean inConsequent, final int argumentNum,
				final int parenLevel)
		{
			String str = sVal;
			if (str == null)
			{
				str = "null";
			}
			String key = "";
			if (inAntecedent)
			{
				key = key.concat("ant-");
				key = key.concat(str);
			}
			if (inConsequent)
			{
				key = key.concat("cons-");
				key = key.concat(str);
			}
			if (!inAntecedent && !inConsequent && parenLevel == 1)
			{
				key = key.concat("arg-");
				key = key.concat(String.valueOf(argumentNum));
				key = key.concat("-");
				key = key.concat(str);
			}
			if (!inAntecedent && !inConsequent && parenLevel > 1)
			{
				key = key.concat("stmt-");
				key = key.concat(str);
			}
			return new Key(key);
		}
	}

	/**
	 * The set of all terms in the formula.
	 */
	public final SortedSet<String> terms = new TreeSet<>();

	/**
	 * A Map of ArrayLists of Formulas. @see KIF.createKey for key format.
	 */
	public final Map<Key, List<Formula>> formulas = new HashMap<>();

	/**
	 * A "raw" Set of unique Strings which are the formulas from the file without any further processing, in the order which they appear in the file.
	 */
	public final Set<String> formulaSet = new LinkedHashSet<>();

	/**
	 * Total lines for comments.
	 */
	private int totalLinesForComments = 0;

	/**
	 * This routine sets up the StreamTokenizer_s so that it parses SUO-KIF. = < > are treated as word characters, as are normal alphanumerics. ; is the line
	 * comment character and " is the quote character.
	 */
	public static void setupStreamTokenizer(final StreamTokenizer_s st)
	{
		st.whitespaceChars(0, 32);
		st.ordinaryChars(33, 44); // !"#$%&'()*+,
		st.wordChars(45, 46); // -.
		st.ordinaryChar(47); // /
		st.wordChars(48, 57); // 0-9
		st.ordinaryChars(58, 59); // :;
		st.wordChars(60, 64); // <=>?@
		st.wordChars(65, 90); // A-Z
		st.ordinaryChars(91, 94); // [\]^
		st.wordChars(95, 95); // _
		st.ordinaryChar(96); // `
		st.wordChars(97, 122); // a-z
		st.ordinaryChars(123, 255); // {|}~
		// st.parseNumbers();
		st.quoteChar('"');
		st.commentChar(';');
		st.eolIsSignificant(true);
	}

	/**
	 * Count the number of appearances of a certain character in a string.
	 *
	 * @param str - the string to be tested.
	 * @param c   - the character to be counted.
	 */
	private int countChar(final String str, final char c)
	{
		int len = 0;
		final char[] cArray = str.toCharArray();
		for (final char element : cArray)
		{
			if (element == c)
			{
				len++;
			}
		}
		return len;
	}

}
