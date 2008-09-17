package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.end_track;
import static edu.berkeley.nlp.wa.basic.LogInfo.logs;
import static edu.berkeley.nlp.wa.basic.LogInfo.track;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;

/**
 * WordAligners have one method: alignSentencePair, which takes a sentence
 * pair and produces an alignment which specifies an english source for each
 * french word which is not aligned to "null".  Explicit alignment to
 * position -1 is equivalent to alignment to "null".
 */
public abstract class WordAligner implements Serializable {
	protected String modelPrefix; // Identifies the model (for writing any files)

	public abstract String getName();

	// IMPORTANT: At least one of the following two methods should be overridden.
	public Alignment alignSentencePair(SentencePair sp) {
		ArrayList<SentencePair> spList = new ArrayList<SentencePair>();
		spList.add(sp);
		Map<Integer, Alignment> alignments = alignSentencePairs(spList);
		return alignments.get(sp.getSentenceID());
	}

	public Map<Integer, Alignment> alignSentencePairs(List<SentencePair> sentencePairs) {
		track("alignSentencePairs(%d sentences)", sentencePairs.size());
		Map<Integer, Alignment> alignments = new HashMap<Integer, Alignment>();
		int idx = 0;

		for (SentencePair sp : sentencePairs) {
			logs("Sentence %d/%d", idx++, sentencePairs.size());
			Alignment alignment = alignSentencePair(sp);
			alignments.put(sp.getSentenceID(), alignment);
		}
		end_track();
		return alignments;
	}

	public Alignment thresholdAlignment(Alignment al, double threshold) {
		return al.thresholdPosteriors(al.getPosteriors(), threshold);
	}

	public String getModelPrefix() {
		return modelPrefix;
	}
}
