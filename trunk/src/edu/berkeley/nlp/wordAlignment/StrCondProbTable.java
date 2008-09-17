package edu.berkeley.nlp.wordAlignment;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.wa.basic.BigStatFig;
import edu.berkeley.nlp.wa.basic.Fmt;
import edu.berkeley.nlp.wa.basic.FullStatFig;
import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.NumUtils;
import edu.berkeley.nlp.wa.basic.Pair;
import edu.berkeley.nlp.wa.basic.String2DoubleMap;
import edu.berkeley.nlp.wa.basic.StringDoubleMap;
import edu.berkeley.nlp.wa.mt.SentencePair;

/**
 * P(f|e) as a table in memory
 */
public class StrCondProbTable extends String2DoubleMap {
	static final long serialVersionUID = 42;

	// Only include entries that exist in sentences.
	public StrCondProbTable restrict(List<SentencePair> sentencePairs, boolean reverse) {
		Set<String> enSet = SentencePair.getWordSet(sentencePairs, reverse);
		Set<String> frSet = SentencePair.getWordSet(sentencePairs, !reverse);
		return (StrCondProbTable) restrict(enSet, frSet);
	}

	public void initUniform() {
		for (StringDoubleMap m : values())
			m.putAll(1.0 / m.size());
	}

	public void initZero() {
		for (StringDoubleMap m : values())
			m.putAll(0);
	}

	public void normalize() {
		for (Map.Entry<String, StringDoubleMap> e : entrySet()) {
			String s = e.getKey();
			StringDoubleMap m = e.getValue();

			double sum = m.sum();
			if (!NumUtils.isFinite(1.0 / sum)) {
				LogInfo.warning("normalize(): %s (with %d elements) has sum %f, using uniform",
						s, m.size(), sum);
				m.putAll(1.0 / m.size());
			} else {
				m.multAll(1.0 / sum);
			}
		}
	}

	public BigStatFig getDiff(StrCondProbTable other) {
		BigStatFig fig = new BigStatFig();

		for (Map.Entry<String, StringDoubleMap> e1 : entrySet()) {
			String en = e1.getKey();
			StringDoubleMap m = e1.getValue();
			for (StringDoubleMap.Entry e2 : m) {
				String fr = e2.getKey();
				double thisVal = e2.getValue();
				double otherVal = other.getSure(en, fr);
				fig.add(Math.abs(thisVal - otherVal));
			}
		}
		return fig;
	}

	protected String2DoubleMap newMap() {
		return new StrCondProbTable();
	}

	public void dump(PrintWriter out) {
		FullStatFig entropyFig = new FullStatFig();
		FullStatFig numTranslationsFig = new FullStatFig();
		FullStatFig sumFig = new FullStatFig();

		for (Map.Entry<String, StringDoubleMap> e : entrySet()) {
			String s = e.getKey();
			StringDoubleMap m = e.getValue();

			FullStatFig fig = new FullStatFig(m.values());
			entropyFig.add(fig.entropy());
			numTranslationsFig.add(fig.size());
			sumFig.add(fig.total());

			out.printf("%s\tentropy %s\tnTrans %d\tsum %f\n", s, Fmt.D(fig.entropy()), fig
					.size(), fig.total());

			ArrayList<StringDoubleMap.Entry> entries2 = new ArrayList<StringDoubleMap.Entry>(m
					.entrySet());
			Collections.sort(entries2, Collections.reverseOrder(m.entryValueComparator()));

			for (StringDoubleMap.Entry tv : entries2) {
				if (tv.getValue() < 1e-10) continue; // Skip zero entries
				String t = tv.getKey();
				out.printf("  %s: %f\n", t, tv.getValue());
			}
		}

		out.println("# entropy = " + entropyFig);
		out.println("# sum = " + sumFig);
		out.println("# numTranslations = " + numTranslationsFig);
	}

	public void dump(PrintWriter out, WordPairStats wpStats, boolean reverse) {
		if (wpStats == null || !wpStats.hasStats()) {
			dump(out);
			return;
		}

		FullStatFig entropyFig = new FullStatFig();
		FullStatFig numTranslationsFig = new FullStatFig();
		FullStatFig sumFig = new FullStatFig();

		// Sort English words by decreasing frequency
		ArrayList<Pair<Double, String>> entries1 = new ArrayList<Pair<Double, String>>();
		for (String s : keySet()) {
			double count = wpStats.enCount(reverse, s);
			entries1.add(new Pair<Double, String>(count, s));
		}
		Collections.sort(entries1, Collections
				.reverseOrder(new Pair.FirstComparator<Double, String>()));

		for (Pair<Double, String> vs : entries1) {
			String s = vs.getSecond();
			StringDoubleMap m = getMap(s, false);

			FullStatFig fig = new FullStatFig(m.values());
			entropyFig.add(fig.entropy());
			numTranslationsFig.add(fig.size());
			sumFig.add(fig.total());

			out.printf("%s\tentropy %s\tnTrans %d\tsum %f\tn %.0f\n", s, Fmt.D(fig.entropy()),
					fig.size(), fig.total(), vs.getFirst());

			ArrayList<StringDoubleMap.Entry> entries2 = new ArrayList<StringDoubleMap.Entry>(m
					.entrySet());
			Collections.sort(entries2, Collections.reverseOrder(m.entryValueComparator()));

			for (StringDoubleMap.Entry tv : entries2) {
				if (tv.getValue() < 1e-10) continue; // Skip zero entries
				String t = tv.getKey();
				double count = wpStats.frCount(reverse, t);
				double dice = wpStats.dice(reverse, s, t);
				out.printf("  %s: %f\tn %.0f, dice=%f\n", t, tv.getValue(), count, dice);
			}
		}

		out.println("# entropy = " + entropyFig);
		out.println("# sum = " + sumFig);
		out.println("# numTranslations = " + numTranslationsFig);
	}

	public void incrAll(StrCondProbTable other) {
		for (Map.Entry<String, StringDoubleMap> first : other) {
			for (StringDoubleMap.Entry second : first.getValue()) {
				incr(first.getKey(), second.getKey(), second.getValue());
			}
		}
	}
}
