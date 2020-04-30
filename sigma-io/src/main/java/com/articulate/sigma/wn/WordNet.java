/* This code is copyright Articulate Software (c) 2003-2007.  Some portions
copyright Teknowledge (c) 2003 and reused under the terms of the GNU license.
This software is released under the GNU Public License <http://www.gnu.org/copyleft/gpl.html>.
Users of this code also consent, by use of this code, to credit Articulate Software
and Teknowledge in any writings, briefings, publications, presentations, or
other representations of any software which incorporates, builds on, or uses this
code.  Please cite the following article in any publication with references:

Pease, A., (2003). The Sigma Ontology Development Environment,
in Working Notes of the IJCAI-2003 Workshop on Ontology and Distributed Systems,
August 9, Acapulco, Mexico.  See also http://sigmakee.sourceforge.net
 */

package com.articulate.sigma.wn;

import com.articulate.sigma.AVPair;
import com.articulate.sigma.KBManager;
import com.articulate.sigma.io.DB;

import java.io.File;
import java.io.FileReader;
import java.io.LineNumberReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This program finds and displays SUMO terms that are related in meaning to the English
 * expressions that are entered as input.  Note that this program uses four WordNet data
 * files, "NOUN.EXC", "VERB.EXC" etc, as well as four WordNet to SUMO
 * mappings files called "WordNetMappings-nouns.txt", "WordNetMappings-verbs.txt" etc
 * The main part of the program prompts the user for an English term and then
 * returns associated SUMO concepts.  The two primary public methods are initOnce() and page().
 *
 * @author Ian Niles
 * @author Adam Pease
 */
public class WordNet
{
	/**
	 * This array contains all of the regular expression strings that
	 * will be compiled to Pattern objects for use in the methods in
	 * this file.
	 */
	private static final String[] regexPatternStrings = {
			// 0: WordNet.processPointers()
			"^\\s*\\d\\d\\s\\S\\s\\d\\S\\s",

			// 1: WordNet.processPointers()
			"^([a-zA-Z0-9'._\\-]\\S*)\\s([0-9a-f])\\s",

			// 2: WordNet.processPointers()
			"^...\\s",

			// 3: WordNet.processPointers()
			"^(\\S\\S?)\\s([0-9]{8})\\s(.)\\s([0-9a-f]{4})\\s?",

			// 4: WordNet.processPointers()
			"^..\\s",

			// 5: WordNet.processPointers()
			"^\\+\\s(\\d\\d)\\s(\\d\\d)\\s?",

			// 6: WordNet.readNouns()
			"^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+?)\\s(\\(?&%\\S+[\\S\\s]+)$",

			// 7: WordNet.readNouns()
			"^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)$",

			// 8: WordNet.readNouns()
			"(\\S+)\\s+(\\S+)",

			// 9: WordNet.readNouns()
			"(\\S+)\\s+(\\S+)\\s+(\\S+)",

			// 10: WordNet.readVerbs()
			"^([0-9]{8})([^|]+)\\|\\s([\\S\\s]+?)\\s(\\(?&%\\S+[\\S\\s]+)$",

			// 11: WordNet.readVerbs()
			"^([0-9]{8})([^|]+)\\|\\s([\\S\\s]+)$",

			// 12: WordNet.readVerbs()
			"(\\S+)\\s+(\\S+)",

			// 13: WordNet.readAdjectives()
			"^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+?)\\s(\\(?&%\\S+[\\S\\s]+)$",

			// 14: WordNet.readAdjectives()
			"^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)$",

			// 15: WordNet.readAdverbs()
			"^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)\\s(\\(?&%\\S+[\\S\\s]+)$",

			// 16: WordNet.readAdverbs()
			"^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)$",

			// 17: WordNet.readWordFrequencies()
			"^Word: ([^ ]+) Values: (.*)",

			// 18: WordNet.readSenseIndex()
			"([^%]+)%([^:]*):[^:]*:[^:]*:[^:]*:[^ ]* ([^ ]+) ([^ ]+) .*",

			// 19: WordNet.removePunctuation()
			"(\\w)'re",

			// 20: WordNet.removePunctuation()
			"(\\w)'m",

			// 21: WordNet.removePunctuation()
			"(\\w)n't",

			// 22: WordNet.removePunctuation()
			"(\\w)'ll",

			// 23: WordNet.removePunctuation()
			"(\\w)'s",

			// 24: WordNet.removePunctuation()
			"(\\w)'d",

			// 25: WordNet.removePunctuation()
			"(\\w)'ve" };

	private static final String[][] wnFilenamePatterns = { { "noun_mappings", "WordNetMappings.*noun.*txt" }, { "verb_mappings", "WordNetMappings.*verb.*txt" },
			{ "adj_mappings", "WordNetMappings.*adj.*txt" }, { "adv_mappings", "WordNetMappings.*adv.*txt" }, { "noun_exceptions", "noun.exc" },
			{ "verb_exceptions", "verb.exc" }, { "adj_exceptions", "adj.exc" }, { "adv_exceptions", "adv.exc" }, { "sense_indexes", "index.sense" },
			{ "word_frequencies", "wordFrequencies.txt" }, { "stopwords", "stopwords.txt" }, { "messages", "messages.txt" } };

	public static WordNet wn = new WordNet();

	private static String baseDir = "";

	private static File baseDirFile = null;

	public static boolean initNeeded = true;

	/**
	 * This array contains all of the compiled Pattern objects that
	 * will be used by methods in this file.
	 */
	private static Pattern[] regexPatterns = null;

	private final Hashtable<String, String> nounSynsetHash = new Hashtable<>();   // Words in root form are String keys,
	private final Hashtable<String, String> verbSynsetHash = new Hashtable<>();   // String values are synset lists.
	private final Hashtable<String, String> adjectiveSynsetHash = new Hashtable<>();
	private final Hashtable<String, String> adverbSynsetHash = new Hashtable<>();

	public final Hashtable<String, String> verbDocumentationHash = new Hashtable<>();       // Keys are synset Strings, values
	public final Hashtable<String, String> adjectiveDocumentationHash = new Hashtable<>();  // are documentation strings.
	public final Hashtable<String, String> adverbDocumentationHash = new Hashtable<>();
	public final Hashtable<String, String> nounDocumentationHash = new Hashtable<>();

	public final Hashtable<String, String> nounSUMOHash = new Hashtable<>();   // Keys are synset Strings, values are SUMO
	public final Hashtable<String, String> verbSUMOHash = new Hashtable<>();   // terms with the &% prefix and =, +, @ or [ suffix.
	public final Hashtable<String, String> adjectiveSUMOHash = new Hashtable<>();
	public final Hashtable<String, String> adverbSUMOHash = new Hashtable<>();

	/**
	 * Keys are SUMO terms, values are Lists(s) of
	 * POS-prefixed synset String(s) with part of speech
	 * prepended to the synset number.
	 */
	public final Hashtable<String, List<String>> SUMOHash = new Hashtable<>();

	/**
	 * Keys are String POS-prefixed synsets.  Values
	 * are List(s) of String(s) which are words. Note
	 * that the order of words in the file is preserved.
	 */
	public final Hashtable<String, List<String>> synsetsToWords = new Hashtable<>();

	/**
	 * List of irregular plural forms where the key is the plural, singular is the value.
	 */
	public final Hashtable<String, String> exceptionNounHash = new Hashtable<>();

	/**
	 * The reverse index of the above
	 */
	public final Hashtable<String, String> exceptionNounPluralHash = new Hashtable<>();

	/**
	 * Key is past tense, value is infinitive (without "to")
	 **/
	public final Hashtable<String, String> exceptionVerbHash = new Hashtable<>();

	/**
	 * The reverse index of the above
	 */
	public final Hashtable<String, String> exceptionVerbPastHash = new Hashtable<>();

	/**
	 * Keys are POS-prefixed synsets, values are List(s) of AVPair(s)
	 * in which the attribute is a pointer type according to
	 * http://wordnet.princeton.edu/man/wninput.5WN.html#sect3 and
	 * the value is a POS-prefixed synset
	 */
	public final Hashtable<String, List<AVPair>> relations = new Hashtable<>();

	/**
	 * A Map of Maps where the key is a word sense of the
	 * form word_POS_num signifying the word, part of speech and number
	 * of the sense in WordNet.  The value is a Map of words and the
	 * number of times that word co-occurs in sentences with the word sense
	 * given in the key.
	 */
	public final Map<String, Map<String, Integer>> wordFrequencies = new HashMap<>();

	/**
	 * English "stop words" such as "a", "at", "them", which have no or little
	 * inherent meaning when taken alone.
	 */
	public final List<String> stopWords = new ArrayList<>();

	/**
	 * A Map where the keys are of the form word_POS_sensenum, and values are 8 digit
	 * WordNet synset byte offsets.
	 */
	public final Map<String, String> senseIndex = new HashMap<>();

	/**
	 * A Map where keys are 8 digit
	 * WordNet synset byte offsets or synsets appended with a dash and a specific
	 * word such as "12345678-foo".  Values are List(s) of String
	 * verb frame numbers.
	 */
	public final Map<String, List<String>> verbFrames = new HashMap<>();

	/**
	 * A Map with words as keys and List as values.  The
	 * List contains word senses which are Strings of the form
	 * word_POS_num signifying the word, part of speech and number of
	 * the sense in WordNet.
	 */
	public final Map<String, List<String>> wordsToSenses = new HashMap<>();

	/**
	 * A Map of String keys and String values.
	 * The String key is the first word of a multi-word WordNet "word", such as "table_tennis",
	 * where words are separated by underscores.  The values are an List of Strings which
	 * contain the whole multi-word. The same head word can appear in many multi-words.
	 */
	public final Map<String, List<String>> multiWord = new HashMap<>();

	/**
	 * This method compiles all of the regular expression pattern
	 * strings in regexPatternStrings and puts the resulting compiled
	 * Pattern objects in the Pattern[] regexPatterns.
	 */
	private void compileRegexPatterns()
	{
		regexPatterns = new Pattern[regexPatternStrings.length];
		for (int i = 0; i < regexPatternStrings.length; i++)
		{
			regexPatterns[i] = Pattern.compile(regexPatternStrings[i]);
			if (regexPatterns[i] == null)
				System.err.println("ERROR in WordNet.compileRegexPatterns(): could not compile \"" + regexPatternStrings[i] + "\"");
		}
	}

	/**
	 * Returns the WordNet File object corresponding to key.  The
	 * purpose of this accessor is to make it easier to deal with
	 * possible changes to these file names, since the descriptive
	 * key, ideally, need not change.  Each key maps to a regular
	 * expression that is used to match against filenames found in the
	 * directory denoted by WordNet.baseDir.  If multiple filenames
	 * match the pattern for one key, then the file that was most
	 * recently changed (presumably, saved) is chosen.
	 *
	 * @param key A descriptive literal String that maps to a regular
	 *            expression pattern used to obtain a WordNet file.
	 * @return A File object
	 */
	public File getWnFile(String key)
	{
		File result = null;
		try
		{
			String pattern = null;
			for (String[] wnFilenamePattern : wnFilenamePatterns)
			{
				if ((wnFilenamePattern[0]).equalsIgnoreCase(key))
				{
					pattern = wnFilenamePattern[1];
					break;
				}
			}
			if ((pattern != null) && (baseDirFile != null))
			{
				File[] wnFiles = baseDirFile.listFiles();
				if (wnFiles != null)
				{
					for (File wnFile : wnFiles)
					{
						if (wnFile.getName().matches(pattern) && wnFile.exists())
						{
							if (result != null)
							{
								if (wnFile.lastModified() > result.lastModified())
									result = wnFile;
							}
							else
								result = wnFile;
						}
					}
				}
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return result;
	}

	/**
	 * Add a synset (with part of speech number prefix) and the SUMO
	 * term that maps to it.
	 */
	private void addSUMOHash(String term, String synset)
	{
		term = term.substring(2, term.length() - 1);
		List<String> synsets = SUMOHash.computeIfAbsent(term, k -> new ArrayList<>());
		synsets.add(synset);
	}

	/**
	 * Add a multi-word string to the multiWord member variable.
	 */
	private void addMultiWord(String word)
	{
		if (word == null || word.isEmpty())
		{
			System.err.println("ERROR in WordNet.addMultiWord(): word is null");
			return;
		}
		if (word.indexOf('_') >= 0)
		{
			String firstWord = word.substring(0, word.indexOf('_'));
			if (!multiWord.containsKey(firstWord))
			{
				List<String> al = new ArrayList<>();
				al.add(word);
				multiWord.put(firstWord, al);
			}
			else
				multiWord.get(firstWord).add(word);
		}
		else
			System.err.println("ERROR in WordNet.addMultiWord(): Not a multi-word: " + word);
	}

	/**
	 * Add a synset and its corresponding word to the synsetsToWords
	 * variable.  Prefix the synset with its part of speech before adding.
	 */
	private void addToSynsetsToWords(String word, String synsetStr, String POS)
	{
		if (word.indexOf('_') >= 0)
			addMultiWord(word);
		List<String> al = synsetsToWords.computeIfAbsent(POS + synsetStr, k -> new ArrayList<>());
		al.add(word);

		switch (POS.charAt(0))
		{
			case '1':
				String synsets = nounSynsetHash.get(word);
				if (synsets == null)
					synsets = "";
				if (!synsets.contains(synsetStr))
				{
					if (synsets.length() > 0)
						synsets = synsets + " ";
					synsets = synsets + synsetStr;
					nounSynsetHash.put(word, synsets);
				}
				break;
			case '2':
				synsets = verbSynsetHash.get(word);
				if (synsets == null)
					synsets = "";
				if (!synsets.contains(synsetStr))
				{
					if (synsets.length() > 0)
						synsets = synsets + " ";
					synsets = synsets + synsetStr;
					verbSynsetHash.put(word, synsets);
				}
				break;
			case '3':
				synsets = adjectiveSynsetHash.get(word);
				if (synsets == null)
					synsets = "";
				if (!synsets.contains(synsetStr))
				{
					if (synsets.length() > 0)
						synsets = synsets + " ";
					synsets = synsets + synsetStr;
					adjectiveSynsetHash.put(word, synsets);
				}
				break;
			case '4':
				synsets = adverbSynsetHash.get(word);
				if (synsets == null)
					synsets = "";
				if (!synsets.contains(synsetStr))
				{
					if (synsets.length() > 0)
						synsets = synsets + " ";
					synsets = synsets + synsetStr;
					adverbSynsetHash.put(word, synsets);
				}
				break;
		}
	}

	/**
	 * Process some of the fields in a WordNet .DAT file as described at
	 * http://wordnet.princeton.edu/man/wndb.5WN . synset must include
	 * the POS-prefix.  Input should be of the form
	 * lex_filenum  ss_type  w_cnt  word  lex_id  [word  lex_id...]  p_cnt  [ptr...]  [frames...]
	 */
	private void processPointers(String synset, String pointers)
	{
		Matcher m;

		// 0: p = Pattern.compile("^\\s*\\d\\d\\s\\S\\s\\d\\S\\s");
		m = regexPatterns[0].matcher(pointers);
		pointers = m.replaceFirst("");

		// Should be left with:
		// word  lex_id  [word  lex_id...]  p_cnt  [ptr...]  [frames...]
		// 1: p = Pattern.compile("^([a-zA-Z0-9'._\\-]\\S*)\\s([0-9a-f])\\s");
		m = regexPatterns[1].matcher(pointers);
		while (m.lookingAt())
		{
			String word = m.group(1);
			if (word.length() > 3 && (word.substring(word.length() - 3).equals("(a)") || word.substring(word.length() - 3).equals("(p)")))
				word = word.substring(0, word.length() - 3);
			if (word.length() > 4 && word.substring(word.length() - 4).equals("(ip)"))
				word = word.substring(0, word.length() - 4);
			addToSynsetsToWords(word, synset.substring(1), synset.substring(0, 1));
			pointers = m.replaceFirst("");
			m = regexPatterns[1].matcher(pointers);
		}

		// Should be left with:
		// p_cnt  [ptr...]  [frames...]
		// 2: p = Pattern.compile("^...\\s");
		m = regexPatterns[2].matcher(pointers);
		pointers = m.replaceFirst("");

		// Should be left with:
		// [ptr...]  [frames...]
		// where ptr is
		// pointer_symbol  synset_offset  pos  source/target
		// 3: p = Pattern.compile("^(\\S\\S?)\\s([0-9]{8})\\s(.)\\s([0-9a-f]{4})\\s?");
		m = regexPatterns[3].matcher(pointers);
		while (m.lookingAt())
		{
			String ptr = m.group(1);
			String targetSynset = m.group(2);
			String targetPOS = m.group(3);
			targetPOS = Character.toString(WordNetUtilities.posLetterToNumber(targetPOS.charAt(0)));
			pointers = m.replaceFirst("");
			m = regexPatterns[3].matcher(pointers);
			ptr = WordNetUtilities.convertWordNetPointer(ptr);
			AVPair avp = new AVPair();
			avp.attribute = ptr;
			avp.value = targetPOS + targetSynset;
			List<AVPair> al = new ArrayList<>();
			if (relations.containsKey(synset))
				al = relations.get(synset);
			else
			{
				relations.put(synset, al);
			}
			al.add(avp);
		}
		if (!pointers.isEmpty() && !" ".equals(pointers))
		{
			// Only for verbs may we have the following leftover
			// f_cnt + f_num  w_num  [ +  f_num  w_num...]
			if (synset.charAt(0) == '2')
			{
				// 4: p = Pattern.compile("^..\\s");
				m = regexPatterns[4].matcher(pointers);
				pointers = m.replaceFirst("");
				// 5: p = Pattern.compile("^\\+\\s(\\d\\d)\\s(\\d\\d)\\s?");
				m = regexPatterns[5].matcher(pointers);
				while (m.lookingAt())
				{
					String frameNum = m.group(1);
					String wordNum = m.group(2);
					String key;
					if (wordNum.equals("00"))
						key = synset.substring(1);
					else
					{
						int num = Integer.parseInt(wordNum);
						List<String> al = synsetsToWords.get(synset);
						if (al == null)
						{
							System.err.println("ERROR in WordNet.processPointers(): " + synset + " has no words for pointers: \"" + pointers + "\"");
							continue;
						}
						String word = al.get(num - 1);
						key = synset.substring(1) + "-" + word;
					}
					List<String> frames = new ArrayList<>();
					if (!verbFrames.containsKey(key))
						verbFrames.put(key, frames);
					else
						frames = verbFrames.get(key);
					frames.add(frameNum);
					pointers = m.replaceFirst("");
					m = regexPatterns[5].matcher(pointers);
				}
			}
			else
			{
				System.err.println("ERROR in WordNet.processPointers(): " + synset.charAt(0) + " leftover pointers: \"" + pointers + "\"");
			}
		}
	}

	/**
	 *
	 */
	private void addSUMOMapping(String SUMO, String synset)
	{
		SUMO = SUMO.trim();
		switch (synset.charAt(0))
		{
			case '1':
				nounSUMOHash.put(synset.substring(1), SUMO);
				break;
			case '2':
				verbSUMOHash.put(synset.substring(1), SUMO);
				break;
			case '3':
				adjectiveSUMOHash.put(synset.substring(1), SUMO);
				break;
			case '4':
				adverbSUMOHash.put(synset.substring(1), SUMO);
				break;
		}
		addSUMOHash(SUMO, synset);
	}

	/**
	 * Create the hashtables nounSynsetHash, nounDocumentationHash,
	 * nounSUMOHash and exceptionNounHash that contain the WordNet
	 * noun synsets, word definitions, mappings to SUMO, and plural
	 * exception forms, respectively.
	 * Throws an IOException if the files are not found.
	 */
	private void readNouns()
	{
		File nounFile = getWnFile("noun_mappings");
		if (nounFile == null)
		{
			System.err.println("ERROR in WordNet.readNouns(): The noun mappings file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(nounFile)))
		{
			// synset_offset  lex_filenum  ss_type  w_cnt  word  lex_id  [word  lex_id...]  p_cnt  [ptr...]  [frames...]  |   gloss
			String line;
			while ((line = lr.readLine()) != null)
			{
				if (lr.getLineNumber() % 1000 == 0)
					System.out.print('.');
				line = line.trim();

				// 6: p = Pattern.compile("^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+?)\\s(\\(?\\&\\%\\S+[\\S\\s]+)$");
				Matcher m = regexPatterns[6].matcher(line);
				boolean anyAreNull = false;
				if (m.matches())
				{
					for (int i = 1; i < 5; i++)
					{
						anyAreNull = (m.group(i) == null);
						if (anyAreNull)
						{
							break;
						}
					}
					if (!anyAreNull)
					{
						addSUMOMapping(m.group(4), "1" + m.group(1));
						nounDocumentationHash.put(m.group(1), m.group(3)); // 1-synset, 2-pointers, 3-docu, 4-SUMO term
						processPointers("1" + m.group(1), m.group(2));
					}
				}
				else
				{
					// 7: p = Pattern.compile("^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)$");  // no SUMO mapping
					m = regexPatterns[7].matcher(line);
					if (m.matches())
					{
						nounDocumentationHash.put(m.group(1), m.group(3));
						processPointers("1" + m.group(1), m.group(2));
					}
					else
					{
						if (line.length() > 0 && line.charAt(0) != ';')
						{
							System.err.println();
							System.err.println("ERROR in WordNet.readNouns(): No match in " + nounFile + " for line " + line);
						}
					}
				}
			}
			System.out.print("x");
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}

		nounFile = getWnFile("noun_exceptions");
		if (nounFile == null)
		{
			System.err.println("ERROR in WordNet.readNouns(): " + "The noun mapping exceptions file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(nounFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				// 8: p = Pattern.compile("(\\S+)\\s+(\\S+)");
				Matcher m = regexPatterns[8].matcher(line);
				if (m.matches())
				{
					exceptionNounHash.put(m.group(1), m.group(2));      // 1-plural, 2-singular
					exceptionNounPluralHash.put(m.group(2), m.group(1));
				}
				else
				{
					// 9: p = Pattern.compile("(\\S+)\\s+(\\S+)\\s+(\\S+)");
					m = regexPatterns[9].matcher(line);
					if (m.matches())
					{
						exceptionNounHash.put(m.group(1), m.group(2));      // 1-plural, 2-singular 3-alternate singular
						exceptionNounPluralHash.put(m.group(2), m.group(1));
						exceptionNounPluralHash.put(m.group(3), m.group(1));
					}
					else if (line.length() > 0 && line.charAt(0) != ';')
					{
						System.err.println("ERROR in WordNet.readNouns(): No match in " + nounFile + " for line " + line);
					}
				}
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * Create the hashtables verbSynsetHash, verbDocumentationHash,
	 * verbSUMOHash and exceptionVerbHash that contain the WordNet
	 * verb synsets, word definitions, mappings to SUMO, and plural
	 * exception forms, respectively.
	 * Throws an IOException if the files are not found.
	 */
	private void readVerbs()
	{
		File verbFile = getWnFile("verb_mappings");
		if (verbFile == null)
		{
			System.err.println("ERROR in WordNet.readVerbs(): The verb mappings file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(verbFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				if (lr.getLineNumber() % 1000 == 0)
					System.out.print('.');
				line = line.trim();
				// 10: p = Pattern.compile("^([0-9]{8})([^\\|]+)\\|\\s([\\S\\s]+?)\\s(\\(?\\&\\%\\S+[\\S\\s]+)$");
				Matcher m = regexPatterns[10].matcher(line);
				if (m.matches())
				{
					verbDocumentationHash.put(m.group(1), m.group(3));
					addSUMOMapping(m.group(4), "2" + m.group(1));
					processPointers("2" + m.group(1), m.group(2));
				}
				else
				{
					// 11: p = Pattern.compile("^([0-9]{8})([^\\|]+)\\|\\s([\\S\\s]+)$");   // no SUMO mapping
					m = regexPatterns[11].matcher(line);
					if (m.matches())
					{
						verbDocumentationHash.put(m.group(1), m.group(3));
						processPointers("2" + m.group(1), m.group(2));
					}
					else
					{
						if (line.length() > 0 && line.charAt(0) != ';')
						{
							System.err.println();
							System.err.println("ERROR in WordNet.readVerbs(): No match in " + verbFile + " for line " + line);
						}
					}
				}
			}
			System.out.print("x");
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}

		verbFile = getWnFile("verb_exceptions");
		if (verbFile == null)
		{
			System.err.println("ERROR in WordNet.readVerbs(): The verb mapping exceptions file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(verbFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				// 12: p = Pattern.compile("(\\S+)\\s+(\\S+)");
				Matcher m = regexPatterns[12].matcher(line);
				if (m.matches())
				{
					exceptionVerbHash.put(m.group(1), m.group(2));          // 1-past, 2-infinitive
					exceptionVerbPastHash.put(m.group(2), m.group(1));
				}
				else if (line.length() > 0 && line.charAt(0) != ';')
					System.err.println("ERROR in WordNet.readVerbs(): No match in " + verbFile.getCanonicalPath() + " for line " + line);
			}
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * Create the hashtables adjectiveSynsetHash, adjectiveDocumentationHash,
	 * and adjectiveSUMOHash that contain the WordNet
	 * adjective synsets, word definitions, and mappings to SUMO, respectively.
	 * Throws an IOException if the files are not found.
	 */
	private void readAdjectives()
	{
		File adjFile = getWnFile("adj_mappings");
		if (adjFile == null)
		{
			System.err.println("ERROR in WordNet.readAdjectives(): The adjective mappings file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(adjFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				if (lr.getLineNumber() % 1000 == 0)
					System.out.print('.');
				line = line.trim();

				// 13: p = Pattern.compile("^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+?)\\s(\\(?\\&\\%\\S+[\\S\\s]+)$");
				Matcher m = regexPatterns[13].matcher(line);
				if (m.matches())
				{
					adjectiveDocumentationHash.put(m.group(1), m.group(3));
					addSUMOMapping(m.group(4), "3" + m.group(1));
					processPointers("3" + m.group(1), m.group(2));
				}
				else
				{
					// 14: p = Pattern.compile("^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)$");     // no SUMO mapping
					m = regexPatterns[14].matcher(line);
					if (m.matches())
					{
						adjectiveDocumentationHash.put(m.group(1), m.group(3));
						processPointers("3" + m.group(1), m.group(2));
					}
					else
					{
						if (line.length() > 0 && line.charAt(0) != ';')
						{
							System.err.println();
							System.err.println("ERROR in WordNet.readAdjectives(): No match in " + adjFile.getCanonicalPath() + " for line " + line);
						}
					}
				}
			}
			System.out.print("x");
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * Create the hashtables adverbSynsetHash, adverbDocumentationHash,
	 * and adverbSUMOHash that contain the WordNet
	 * adverb synsets, word definitions, and mappings to SUMO, respectively.
	 * Throws an IOException if the files are not found.
	 */
	private void readAdverbs()
	{
		File advFile = getWnFile("adv_mappings");
		if (advFile == null)
		{
			System.err.println("ERROR in WordNet.readAdverbs(): The adverb mappings file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(advFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				if (lr.getLineNumber() % 1000 == 0)
					System.out.print('.');
				line = line.trim();

				// 15: p = Pattern.compile("^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)\\s(\\(?\\&\\%\\S+[\\S\\s]+)$");
				Matcher m = regexPatterns[15].matcher(line);
				if (m.matches())
				{
					adverbDocumentationHash.put(m.group(1), m.group(3));
					addSUMOMapping(m.group(4), "4" + m.group(1));
					processPointers("4" + m.group(1), m.group(2));
				}
				else
				{
					// 16: p = Pattern.compile("^([0-9]{8})([\\S\\s]+)\\|\\s([\\S\\s]+)$");   // no SUMO mapping
					m = regexPatterns[16].matcher(line);
					if (m.matches())
					{
						adverbDocumentationHash.put(m.group(1), m.group(3));
						processPointers("4" + m.group(1), m.group(2));
					}
					else
					{
						if (line.length() > 0 && line.charAt(0) != ';')
						{
							System.err.println();
							System.err.println("ERROR in WordNet.readAdverbs(): No match in " + advFile.getCanonicalPath() + " for line " + line);
						}
					}
				}
			}
			System.out.print("x");
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}

	/**
	 * Return a Map of Maps where the key is a word sense of the
	 * form word_POS_num signifying the word, part of speech and number
	 * of the sense in WordNet.  The value is a Map of words and the
	 * number of times that word co-occurs in sentences with the word sense
	 * given in the key.
	 */
	public void readWordFrequencies()
	{
		File wfFile = getWnFile("word_frequencies");
		if (wfFile == null)
		{
			System.err.println("ERROR in WordNet.readWordFrequencies(): The word frequencies file does not exist in " + baseDir);
			return;
		}
		int counter = 0;
		try (LineNumberReader lr = new LineNumberReader(new FileReader(wfFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				line = line.trim();
				// 17: Pattern p = Pattern.compile("^Word: ([^ ]+) Values: (.*)");
				Matcher m = regexPatterns[17].matcher(line);
				if (m.matches())
				{
					String key = m.group(1);
					String values = m.group(2);
					String[] words = values.split(" ");
					Map<String, Integer> frequencies = new HashMap<>();
					for (int i = 0; i < words.length - 3; i++)
					{
						if (words[i].equals("SUMOterm:"))
						{
							i = words.length;
						}
						else
						{
							if (words[i].contains("_"))
							{
								String word = words[i].substring(0, words[i].indexOf("_"));
								String freq = words[i].substring(words[i].lastIndexOf("_") + 1);
								frequencies.put(word.intern(), Integer.decode(freq));
							}
						}
					}
					wordFrequencies.put(key.intern(), frequencies);
					counter++;
					if (counter == 1000)
					{
						System.out.print(".");
						counter = 0;
					}
				}
			}
			System.out.print("x");
		}
		catch (Exception i)
		{
			System.err.println();
			System.err.println("ERROR in WordNet.readWordFrequencies() reading file " + wfFile + ": " + i.getMessage());
			i.printStackTrace();
		}
	}

	/**
	 *
	 */
	public void readStopWords()
	{
		File swFile = getWnFile("stopwords");
		if (swFile == null)
		{
			System.err.println("ERROR in WordNet.readStopWords(): The stopwords file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(swFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
				stopWords.add(line.intern());
		}
		catch (Exception i)
		{
			System.err.println("ERROR in WordNet.readStopWords() reading file " + swFile + ": " + i.getMessage());
			i.printStackTrace();
		}
	}

	/**
	 *
	 */
	public void readSenseIndex()
	{
		int counter = 0;
		File siFile = getWnFile("sense_indexes");
		if (siFile == null)
		{
			System.err.println("ERROR in WordNet.readSenseIndex(): The sense indexes file does not exist in " + baseDir);
			return;
		}
		try (LineNumberReader lr = new LineNumberReader(new FileReader(siFile)))
		{
			String line;
			while ((line = lr.readLine()) != null)
			{
				// 18: Pattern p = Pattern.compile("([^%]+)%([^:]*):[^:]*:[^:]*:[^:]*:[^ ]* ([^ ]+) ([^ ]+) .*");
				Matcher m = regexPatterns[18].matcher(line);
				if (m.matches())
				{
					String word = m.group(1);
					String pos = m.group(2);
					String synset = m.group(3);
					String sensenum = m.group(4);
					String posString = WordNetUtilities.posNumberToLetters(pos);
					String key = word + "_" + posString + "_" + sensenum;
					word = word.intern();
					List<String> al = wordsToSenses.computeIfAbsent(word, k -> new ArrayList<>());
					al.add(key);
					senseIndex.put(key, synset);
					counter++;
					if (counter == 1000)
					{
						System.out.print('.');
						counter = 0;
					}
				}
			}
			System.out.print('x');
		}
		catch (Exception e)
		{
			System.err.println();
			System.err.println("ERROR in WordNet.readSenseIndex() reading file " + siFile + ": " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Read the WordNet files only on initialization of the class.
	 */
	public static void initOnce()
	{
		try
		{
			if (initNeeded)
			{
				if (WordNet.baseDir == null || WordNet.baseDir.isEmpty())
					WordNet.baseDir = KBManager.getMgr().getPref("kbDir");
				baseDirFile = new File(WordNet.baseDir);
				wn = new WordNet();
				wn.compileRegexPatterns();
				wn.readNouns();
				wn.readVerbs();
				wn.readAdjectives();
				wn.readAdverbs();
				wn.readWordFrequencies();
				wn.readStopWords();
				wn.readSenseIndex();
				initNeeded = false;
				DB.readSentimentArray();
			}
		}
		catch (Exception ex)
		{
			System.err.println(ex.getMessage());
			ex.printStackTrace();
		}
	}
}
