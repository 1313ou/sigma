/* This code is copyright Articulate Software (c) 2003.  Some portions
copyright Teknowledge (c) 2003 and reused under the terms of the GNU license.
This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
Users of this code also consent, by use of this code, to credit Articulate Software
and Teknowledge in any writings, briefings, publications, presentations, or
other representations of any software which incorporates, builds on, or uses this
code.  Please cite the following article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment,
in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico.
 */

package com.articulate.sigma.wn;

/**
 * WordNet utilities
 *
 * @author Adam Pease
 */
public class WordNetUtilities
{
	/**
	 * Extract the POS from a word_POS_num sense key
	 */
	public static String getPOSFromKey(String senseKey)
	{
		int lastUS = senseKey.lastIndexOf("_");
		return senseKey.substring(lastUS - 2, lastUS);
	}

	/**
	 * Extract the POS from a word_POS_num sense key
	 */
	public static String getWordFromKey(String senseKey)
	{
		int lastUS = senseKey.lastIndexOf("_");
		return senseKey.substring(0, lastUS - 3);
	}

	/**
	 * Convert WordNet pointer
	 */
	public static String convertWordNetPointer(String ptr)
	{
		if (ptr.equals("!"))
			ptr = "antonym";
		if (ptr.equals("@"))
			ptr = "hypernym";
		if (ptr.equals("@i"))
			ptr = "instance hypernym";
		if (ptr.equals("~"))
			ptr = "hyponym";
		if (ptr.equals("~i"))
			ptr = "instance hyponym";
		if (ptr.equals("#m"))
			ptr = "member holonym";
		if (ptr.equals("#s"))
			ptr = "substance holonym";
		if (ptr.equals("#p"))
			ptr = "part holonym";
		if (ptr.equals("%m"))
			ptr = "member meronym";
		if (ptr.equals("%s"))
			ptr = "substance meronym";
		if (ptr.equals("%p"))
			ptr = "part meronym";
		if (ptr.equals("="))
			ptr = "attribute";
		if (ptr.equals("+"))
			ptr = "derivationally related";
		if (ptr.equals(";c"))
			ptr = "domain topic";
		if (ptr.equals("-c"))
			ptr = "member topic";
		if (ptr.equals(";r"))
			ptr = "domain region";
		if (ptr.equals("-r"))
			ptr = "member region";
		if (ptr.equals(";u"))
			ptr = "domain usage";
		if (ptr.equals("-u"))
			ptr = "member usage";
		if (ptr.equals("*"))
			ptr = "entailment";
		if (ptr.equals(">"))
			ptr = "cause";
		if (ptr.equals("^"))
			ptr = "also see";
		if (ptr.equals("$"))
			ptr = "verb group";
		if (ptr.equals("&"))
			ptr = "similar to";
		if (ptr.equals("<"))
			ptr = "participle";
		if (ptr.equals("\\"))
			ptr = "pertainym";
		return ptr;
	}

	/**
	 * Convert POS character to number
	 */
	public static char posLetterToNumber(char POS)
	{
		switch (POS)
		{
			case 'n':
				return '1';
			case 'v':
				return '2';
			case 'a':
				return '3';
			case 'r':
				return '4';
			case 's':
				return '5';
		}
		System.err.println("ERROR in WordNetUtilities.posLetterToNumber(): bad letter: " + POS);
		return '1';
	}

	/**
	 * Convert a part of speech number to the two letter format used by
	 * the WordNet sense index code.  Defaults to noun "NN".
	 */
	public static String posNumberToLetters(String pos)
	{
		if (pos.equalsIgnoreCase("1"))
			return "NN";
		if (pos.equalsIgnoreCase("2"))
			return "VB";
		if (pos.equalsIgnoreCase("3"))
			return "JJ";
		if (pos.equalsIgnoreCase("4"))
			return "RB";
		if (pos.equalsIgnoreCase("5"))
			return "JJ";
		System.err.println("ERROR in WordNetUtilities.posNumberToLetters(): bad number: " + pos);
		return "NN";
	}

	/**
	 * Convert a part of speech number to the two letter format used by
	 * the WordNet sense index code.  Defaults to noun "NN".
	 */
	public static String posLettersToNumber(String pos)
	{
		assert pos != null && !pos.isEmpty() : "Error in WordNetUtilities.posLettersToNumber(): empty string";
		if (pos.equalsIgnoreCase("NN"))
			return "1";
		if (pos.equalsIgnoreCase("VB"))
			return "2";
		if (pos.equalsIgnoreCase("JJ"))
			return "3";
		if (pos.equalsIgnoreCase("RB"))
			return "4";
		assert false : "Error in WordNetUtilities.posLettersToNumber(): bad letters: " + pos;
		return "1";
	}
}

