package edu.berkeley.nlp.wordAlignment;

import static edu.berkeley.nlp.wa.basic.LogInfo.logs;
import static edu.berkeley.nlp.wa.basic.LogInfo.logss;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.wa.basic.BigStatFig;
import edu.berkeley.nlp.wa.basic.Fmt;
import edu.berkeley.nlp.wa.basic.IOUtils;
import edu.berkeley.nlp.wa.basic.Option;
import edu.berkeley.nlp.wa.basic.Pair;
import edu.berkeley.nlp.wa.basic.String2DoubleMap;
import edu.berkeley.nlp.wa.exec.Execution;
import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.AlignmentRenderer;
import edu.berkeley.nlp.wa.mt.SentencePair;

/**
 * A repository for induced alignments and their gold counterparts.
 * This object gets serialized for post-processing.
 */
public class AlignmentsInfo implements Serializable {
	static final long serialVersionUID = 42;
	private String2DoubleMap dictionary;

	private AlignmentsInfo() {
		this.sentencePairs = new ArrayList<SentencePair>();
		this.proposedAlignments = new HashMap<Integer, Alignment>();
	}

	public AlignmentsInfo(String name, List<SentencePair> sentencePairs,
			Map<Integer, Alignment> proposedAlignments) {
		this(name, sentencePairs, proposedAlignments, null);
	}

	public AlignmentsInfo(String name, List<SentencePair> sentencePairs,
			Map<Integer, Alignment> proposedAlignments, String2DoubleMap dictionary) {
		this.name = name;
		this.sentencePairs = sentencePairs;
		this.proposedAlignments = proposedAlignments;
		this.dictionary = dictionary;
	}

	public void writeBinary(String file) {
		IOUtils.writeObjFileEasy(file, this);
	}

	public void writeText(String file) {
		PrintWriter txtOut = IOUtils.openOutEasy(file);
		for (SentencePair sentencePair : sentencePairs) {
			Alignment proposedAlignment = proposedAlignments.get(sentencePair.getSentenceID());
			Alignment referenceAlignment = sentencePair.getAlignment();

			txtOut.println("Alignment " + sentencePair.getSentenceID() + ":");
			txtOut.println(Alignment.render(referenceAlignment, proposedAlignment, dictionary));
		}
		txtOut.close();
	}

	public void writeGIZA(String file) {
		PrintWriter txtOut = IOUtils.openOutHard(file);
		for (SentencePair sentencePair : sentencePairs) {
			Alignment proposedAlignment = proposedAlignments.get(sentencePair.getSentenceID());
			proposedAlignment.writeGIZA(txtOut, sentencePair.getSentenceID());
		}
		txtOut.close();
	}

	public void writePharaoh(String file) {
		PrintWriter txtOut = IOUtils.openOutHard(file);
		for (SentencePair sentencePair : sentencePairs) {
			Alignment proposedAlignment = proposedAlignments.get(sentencePair.getSentenceID());
			txtOut.println(proposedAlignment.outputHard());
		}
		txtOut.close();
	}

	public void logStats() {
		logs("Name: " + name);
		logs("%d sentences (%d with alignments)", sentencePairs.size(), proposedAlignments
				.size());

		BigStatFig numAlignments = new BigStatFig();
		for (Alignment a : proposedAlignments.values()) {
			numAlignments.add(a.getSureAlignments().size());
		}
		logs("Num alignments: " + numAlignments);
	}

	public AlignmentsInfo reverse() {
		AlignmentsInfo newInfo = new AlignmentsInfo();
		newInfo.name = String.format("reverse(%s)", name);
		for (SentencePair sp : sentencePairs)
			newInfo.sentencePairs.add(sp.reverse());
		for (int sid : referenceAlignments.keySet())
			newInfo.referenceAlignments.put(sid, referenceAlignments.get(sid).reverse());
		for (int sid : proposedAlignments.keySet())
			newInfo.proposedAlignments.put(sid, proposedAlignments.get(sid).reverse());
		return newInfo;
	}

	public String toString() {
		return name;
	}

	public String name;
	public List<SentencePair> sentencePairs;
	public Map<Integer, Alignment> referenceAlignments;
	public Map<Integer, Alignment> proposedAlignments;

	@Option(gloss = "Operation to perform.")
	public static Operation operation = Operation.DUMP;
	@Option(gloss = "Input alignments input file.", required = true)
	public static String inFile;
	@Option(gloss = "Input alignments input file (if we want to combine).")
	public static String inFile2;
	@Option(gloss = "Output alignments (after thresholding or combination) or pstricks.")
	public static String outFile;
	@Option(gloss = "Method of combination (juxtapose is using first as reference).")
	public static Combine combine = Combine.INTERSECT;
	@Option(gloss = "Reverse second before combination")
	public static boolean reverse = false;
	@Option(gloss = "Show strength in text mode?")
	public static boolean showStrength = false;
	@Option(gloss = "Threshold for posterior decoding (when thresholding).")
	public static double threshold = 0.5;
	@Option(gloss = "Sentence ID to render with pstricks.")
	public static int sid = 0;
	@Option(gloss = "English start index.")
	public static int i1 = 0;
	@Option(gloss = "English end index.")
	public static int i2 = Integer.MAX_VALUE;
	@Option(gloss = "French start index.")
	public static int j1 = 0;
	@Option(gloss = "French end index.")
	public static int j2 = Integer.MAX_VALUE;

	public enum Operation {
		STATS, DUMP, THRESHOLD, COMBINE, PSTRICKS, TEXT
	};

	public enum Combine {
		INTERSECT, UNION, JUXTAPOSE
	};

	public static SentencePair findSentencePair(List<SentencePair> sps, int sid) {
		for (SentencePair sp : sps)
			if (sp.getSentenceID() == sid) return sp;
		return null;
	}

	public static void main(String[] args) {
		Execution.init(args, AlignmentsInfo.class);
		doMain();
		Execution.finish();
	}

	public static void doMain() {
		AlignmentsInfo info = (AlignmentsInfo) IOUtils.readObjFileHard(inFile);

		if (operation == Operation.STATS) {
			info.logStats();
		} else if (operation == Operation.DUMP) {
			PrintWriter out = IOUtils.openOutHard(outFile);
			for (SentencePair sp : info.sentencePairs) {
				int sid = sp.getSentenceID();
				Alignment ref = info.referenceAlignments.get(sid);
				Alignment prop = info.proposedAlignments.get(sid);
				out.println("Alignment " + sid + ":");
				out.println(Alignment.render(ref, prop));

				for (Pair<Integer, Integer> pair : prop.getStrengths().keySet())
					out.printf("strength(i=%d,j=%d) = %f\n", pair.getFirst(), pair.getSecond(),
							prop.getStrengths().get(pair));
				out.println("");
			}
			out.close();
		} else if (operation == Operation.TEXT) {
			// Print the alignments in a text format (<alignment> <i> <j> per line)
			PrintWriter out = IOUtils.openOutHard(outFile);
			for (SentencePair sp : info.sentencePairs) {
				int sid = sp.getSentenceID();
				Alignment prop = info.proposedAlignments.get(sid);
				if (showStrength) {
					for (Map.Entry<Pair<Integer, Integer>, Double> e : prop.getStrengths()
							.entrySet()) {
						Pair<Integer, Integer> pair = e.getKey();
						double strength = e.getValue();
						//if(strength < 1e-10) continue;
						out.printf("%d %d %d %f %f\n", sid, pair.getFirst(), pair.getSecond(),
								strength, Math.log(strength));
					}
				} else {
					for (Pair<Integer, Integer> pair : prop.getSureAlignments())
						out.printf("%d %d %d S\n", sid, pair.getFirst(), pair.getSecond());
					/*for(Pair<Integer, Integer> pair : prop.getPossibleAlignments())
					 out.printf("%d %d %d P\n", sid, pair.getFirst(), pair.getSecond());*/
				}
			}
			out.close();
		} else if (operation == Operation.THRESHOLD) {
			// Try to evaluate the alignments with the given threshold
			// Compute thresholded versions of the provided alignments.
			info.proposedAlignments = Alignment.thresholdAlignmentsByStrength(
					info.proposedAlignments, threshold);
			Performance perf = Evaluator.eval(info.sentencePairs, info.proposedAlignments);
			logss("Performance: " + perf);

			info.name += ",posteriorThreshold=" + Fmt.D(threshold);
			IOUtils.writeObjFileHard(outFile, info);
		} else if (operation == Operation.PSTRICKS) {
			SentencePair sp = findSentencePair(info.sentencePairs, sid);
			Alignment ref = info.referenceAlignments.get(sid);
			Alignment prop = info.proposedAlignments.get(sid);
			if (sp == null)
				logs("Sentence " + sid + " doesn't exist");
			//else if(ref == null)  logs("Reference alignment for sentence " + sid + " doesn't exist");
			else if (prop == null)
				logs("Proposed alignment for sentence " + sid + " doesn't exist");
			else {
				if (ref == null) ref = prop;
				i1 = Math.max(i1, 0);
				j1 = Math.max(j1, 0);
				i2 = Math.min(i2, sp.I());
				j2 = Math.min(j2, sp.J());
				logs("Reading sentence %d: length %d,%d", sid, sp.I(), sp.J());
				logs("Chopping using range %d:%d, %d:%d", i1, i2, j1, j2);
				PrintWriter out = IOUtils.openOutHard(outFile);
				out.print(AlignmentRenderer.renderPSTricks(ref.chop(i1, i2, j1, j2), prop.chop(
						i1, i2, j1, j2), sp.chop(i1, i2, j1, j2)));
				out.close();
			}
		} else if (operation == Operation.COMBINE) {
			AlignmentsInfo info2 = (AlignmentsInfo) IOUtils.readObjFileHard(inFile2);
			logs("Read %d,%d sentences", info.sentencePairs.size(), info2.sentencePairs.size());

			if (reverse) info2 = info2.reverse();

			String newName = String.format("%s(%s, %s)", combine, info.name, info2.name);
			AlignmentsInfo combInfo;

			if (combine == Combine.JUXTAPOSE) {
				combInfo = new AlignmentsInfo(newName, info.sentencePairs,
						info2.proposedAlignments);
			} else {
				Map<Integer, Alignment> combAlignments = new HashMap<Integer, Alignment>();
				for (SentencePair sp : info.sentencePairs) {
					int sid = sp.getSentenceID();
					Alignment a1 = info.proposedAlignments.get(sid);
					Alignment a2 = info2.proposedAlignments.get(sid);
					Alignment newAlignment = null;
					if (combine == Combine.INTERSECT)
						newAlignment = a1.intersect(a2);
					else if (combine == Combine.UNION) newAlignment = a1.union(a2);
					combAlignments.put(sp.getSentenceID(), newAlignment);
				}

				combInfo = new AlignmentsInfo(newName, info.sentencePairs, combAlignments);
			}

			IOUtils.writeObjFileHard(outFile, combInfo);
		}
	}

}
