package bbou.sumo;

import com.articulate.sigma.Formula;
import com.articulate.sigma.KB;

import java.io.IOException;
import java.util.List;

public class Main
{
	static private final boolean DUMP = false;

	static public void main(String[] args) throws IOException, ClassNotFoundException
	{
		//URL url = Main.class.getResource("logging.properties");
		//String loggingPath = url.getFile();
		String loggingPath = "logging.properties";
		System.setProperty("java.util.logging.config.file", loggingPath);

		String kbPath = "/opt/data/nlp/sumo/SumoKB";
		System.out.printf("Kb building%n");
		final KB kb = new SUMOKb().make(kbPath);
		System.out.printf("%nKb built%n");
		System.out.printf("Kb1%n");
		dumpKb(kb);
		makeClausalForms(kb);

		System.out.printf("%nKb serializing%n");
		Serializer.serializeFile("./sumokb.ser", kb);
		Serializer.serializeZip("./sumo.zip", "kb", kb);
		System.out.printf("Kb serialized%n");

		System.out.printf("%nKb de-serializing%n");
		KB kb2 = (KB) DeSerializer.deserializeZip("./sumo.zip", "kb");
		KB kb3 = (KB) DeSerializer.deserializeFile("./sumokb.ser");
		System.out.printf("Kb de-serialized%n");
		System.out.printf("Kb2%n");
		dumpKb(kb2);
		System.out.printf("Kb3%n");
		dumpKb(kb3);

		System.out.printf("%nDone");
	}

	static private void dumpKb(KB kb)
	{
		if (DUMP)
		{
			SUMOUtils.dumpFormulas(System.out, kb);
			SUMOUtils.dumpTerms(System.out, kb);
			SUMOUtils.dumpPredicates(System.out, kb);
		}
		else
		{
			System.out.printf("Formulas %d%n", kb.formulas.values().size());
			System.out.printf("Terms %d%n", kb.terms.size());
			System.out.printf("Predicates %d%n", kb.collectPredicates().size());
		}
	}

	static private void makeClausalForms(KB kb)
	{
		long count = 0;
		for (List<Formula> fs : kb.formulas.values())
		{
			for (Formula f : fs)
			{
				/* Tuple.Triplet<List<Clause>, Formula, Map<String, String>> cf = */
				f.getClausalForm();
				if ((count++ % 100L) == 0)
					System.out.println();
				System.out.print('!');
			}
		}
	}
}
