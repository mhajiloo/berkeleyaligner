package edu.berkeley.nlp.wordAlignment.distortion;

import static edu.berkeley.nlp.wa.basic.LogInfo.dbg;
import static edu.berkeley.nlp.wa.basic.LogInfo.error;
import static edu.berkeley.nlp.wa.basic.LogInfo.logs;
import static edu.berkeley.nlp.wa.basic.LogInfo.warning;

import edu.berkeley.nlp.wa.basic.DoubleVec;
import edu.berkeley.nlp.wa.basic.Fmt;
import edu.berkeley.nlp.wa.basic.Indexer;
import edu.berkeley.nlp.wa.basic.IntVec;
import edu.berkeley.nlp.wa.basic.LogInfo;
import edu.berkeley.nlp.wa.basic.NumUtils;
import edu.berkeley.nlp.wa.mt.Alignment;
import edu.berkeley.nlp.wa.mt.SentencePair;
import edu.berkeley.nlp.wordAlignment.EMWordAligner;
import edu.berkeley.nlp.wordAlignment.ExpAlign;
import edu.berkeley.nlp.wordAlignment.Main;
import edu.berkeley.nlp.wordAlignment.Model;
import edu.berkeley.nlp.wordAlignment.SentencePairState;
import edu.berkeley.nlp.wordAlignment.distortion.FirstOrderModel.DistortionParameters;

/**
 * The HMM model (Vogel, 1996). The HMM model is a model of P(a, f | e) that
 * factors like this: a_j = (i,1) means the j-th Foreign word is aligned to the
 * i-th English word a_j = (i,0) means the j-th Foreign word is aligned to the
 * NULL and the last non-null aligned Foreign word was aligned to the i-th
 * English word
 * 
 * The parameters of the HMM model: 
 *   - P(f_j | a_j = i,1) = P(f_j | e_{a_j}) [translation probabilities]
 *   - P(f_j | a_j = i,0) = P(f_j | NULL)
 *   - P(a_j = i,1 | a_{j-1} = h,*) \propto (1-p_0) * f(i-h) 
 *   - P(a_j = i,0 | a_{j-1} = h,*) \propto p_0 * delta(i=h)
 * 
 * a = (a_0, \dots, a_{J-1}) [hidden states] 
 * f = (f_0, \dots, f_{J-1}) [observations]
 * 
 * alpha_j(i,b) = P(f_0, \dots, f_{j-1}, a_j = i,b) [forward probabilities]
 * beta_j(i,b) = P(f_j, \dots, f_{J-1} | a_j = i,b) [backward probabilities]
 * 
 * alpha_{-1}(0,1) = 1 (0 otherwise) 
 * [pretend a beginning of sentence token in Foreign is aligned to the first English word]
 * alpha_j(i,b) = \sum_{h=0..I-1, a=0,1} alpha_{j-1}(h,a) * P(f_{j-1} | a_{j-1} = h,a) 
 * * P(a_j = i,b | a_{j-1} = h,a) beta_n(*,*) 
 * = 1 beta_j(i,b) 
 * = \sum_{k=0..I-1, c=0,1} beta_{j+1}(k,c) * P(f_j | a_j = i,b) P(a_{j+1} = k,c | a_j =i,b)
 * 
 * Expected counts P(f) = \sum_{i=0..I-1, b=0,1} beta_0(i,b) P(a_j = i,b | e, f) =
 * alpha_j(i,b) * beta_j(i,b) / P(f) P(a_j = i,b, a_{j+1} = k,c | e, f) =
 * alpha_j(i,b) * beta_{j+1}(k,c) * P(a_{j+1} = k,c | a_j = i,b) * P(f_j | a_j) / P(f)
 */
public class HMMSentencePairState extends SentencePairState {

	private static final long serialVersionUID = 1L;
	private static final double UNKNOWN_WORD_PROB = 0.00001;
	WATrellis trellis;
	TrellisOutput toutput;
	HMMExpAlign expAlign; // Expected alignments (created after computeExpAlign())
	public static WAState.Factory factory = new WAState.Factory();

	public static class Factory extends SentencePairState.Factory {
		private static final long serialVersionUID = 1L;

		public SentencePairState create(SentencePair sp, EMWordAligner wa) {
			if (wa.isReversed()) sp = sp.reverse();
			return new HMMSentencePairState(sp, wa);
		}

		public String getName() {
			return "HMM";
		}

	}

	@SuppressWarnings("unchecked")
	public HMMSentencePairState(SentencePair sp, EMWordAligner wa) {
		super(sp.getEnglishWords(), sp.getForeignWords(), wa);
		this.trellis = ((HMMTrainingCache) wa.getTrainingCache()).getTrellis(factory, I, wa
				.getParams(), sp);
	}

	// P(f_j | a_j = i,b)
	// P(f_j | a_j = i,1) = P(f_j | e_i)
	// P(f_j | a_j = i,0) = P(f_j | NULL)
	double emissionProb(int j, WAState state) {
		if (!state.currAligned) {
			// Generate from null
			return 1;
			//			return wa.params.transProbs.get(wa.nullWord, fr(j), UNKNOWN_WORD_PROB);
		} else {
			// Generate from English
			if (state.i >= 0 && state.i < I)
				// I would expect this to work better with a 0 default, but it works well like this.
				return wa.getParams().transProbs.get(en(state.i), fr(j), UNKNOWN_WORD_PROB);
			else
				return 0;
		}
	}

	double[][] computeEmissionWeights() {
		int numStates = trellis.numStates();
		double[][] emissionWeights = new double[J][numStates];
		for (int j = 0; j < J; j++)
			for (int state = 0; state < numStates; state++)
				emissionWeights[j][state] = emissionProb(j, (WAState) trellis.states
						.getObject(state));
		return emissionWeights;
	}

	double[][] sureEmissionWeights() {
		int numStates = trellis.numStates();
		double[][] emissionWeights = new double[J][numStates];
		for (int j = 0; j < J; j++)
			for (int state = 0; state < numStates; state++)
				emissionWeights[j][state] = 1;
		return emissionWeights;
	}

	public ExpAlign computeExpAlign() {
		if (Main.rantOutput) {
			TrellisOutput toutput = new TrellisOutput(trellis, sureEmissionWeights());
			logs("Check valid probability model: 1 >= " + toutput.likelihood);
			if (toutput.likelihood > 1) error("Likelihood > 1");
			new HMMExpAlign(I, J, toutput).dump();
		}

		toutput = new TrellisOutput(trellis, computeEmissionWeights());
		if (toutput.likelihood == 0) {
			if (Main.rantOutput) {
				String warning = "Likelihood = 0 for sentence with length (%d,%d); to prevent underflow, set to 1 (ignores the sentence)";
				LogInfo.warning(warning, enWords.size(), frWords.size());
			}
			toutput.likelihood = 1;
		}
		likelihood = toutput.likelihood;
		// logs("Likelihood = " + likelihood);

		expAlign = new HMMExpAlign(I, J, toutput);
		return expAlign;
	}

	@SuppressWarnings("unchecked")
	public void updateNewParams(ExpAlign expAlign,
			Model<DistortionModel> params) {
		updateTransProbs(expAlign, params); // Translation
		trellis.updateTransitionProbs(toutput, (Model) params); // Transition
	}

	public Alignment getViterbi(boolean reverse) {
		int[] path = trellis.computeViterbiPath(computeEmissionWeights());

		// Extract alignment from states
		Alignment alignment = new Alignment(enWords, frWords);
		for (int j = 0; j < J; j++) {
			WAState stateObj = (WAState) trellis.states.getObject(path[j]);
			if (stateObj.currAligned) {
				if (!reverse) {
					alignment.addAlignment(stateObj.i, j, true);
				} else {
					alignment.addAlignment(j, stateObj.i, true);
				}
			}
		}
		return alignment;
	}
}

class Trellis {
	Indexer<WAState> states = new Indexer<WAState>();
	int initState; // State of the position before the first position of the sequence
	int finalState; // State of the position after the last position of the sequence
	StateMapper stateMapper;

	public int numStates() {
		return states.size();
	}

	// Transition costs
	// Use parallel arrays to save space
	IntVec[][] nextStates; // jz, state -> list of next states
	DoubleVec[][] transWeights; // jz, state -> weight going to the next state

	// alpha[j][state] = P(obs_0, ..., obs_{j-1}, state_j)
	// emissionWeights[j][state] = P(objs_j | state_j = state)
	public double[][] computeForwardProbs(double[][] emissionWeights) {
		int J = emissionWeights.length;
		int numStates = states.size();
		double[][] alpha = new double[J][numStates];

		for (int j = -1; j < J - 1; j++) {
			// FOR TRANS
			int jz = stateMapper.getjz(j, J);
			for (int state = 0; state < numStates; state++) {
				IntVec nextStatesJS = nextStates[jz][state];
				DoubleVec transWeightsJS = transWeights[jz][state];
				for (int q = 0; q < nextStatesJS.size(); q++) {
					int state2 = nextStatesJS.get(q);
					double transWeight = transWeightsJS.get(q);

					alpha[j + 1][state2] += (j == -1 ? (state == initState ? 1 : 0)
							: alpha[j][state] * emissionWeights[j][state])
							* transWeight;

					if (alpha[j + 1][state2] > 1) {
						warning("alpha(j=%d,state=%s) = %f > 1", j + 1, states.getObject(state2),
								alpha[j + 1][state2]);
						alpha[j + 1][state2] = 1;
					}
				}
			}
		}

		if (Main.rantOutput) for (int j = 0; j < J; j++)
			for (int state = 0; state < numStates; state++)
				dbg("alpha(j=%d; %s) = %f", j, states.getObject(state), alpha[j][state]);

		return alpha;
	}

	// beta[j][state] = P(obs_j, ..., obs_{J-1} | state_j)
	// emissionWeights[j][state] = P(objs_j | state_j = state)
	public double[][] computeBackwardProbs(double[][] emissionWeights) {
		int J = emissionWeights.length;
		int numStates = states.size();
		double[][] beta = new double[J][numStates];

		// Compute backward probabilities
		for (int j = J - 1; j >= 0; j--) {
			// FOR TRANS
			int jz = stateMapper.getjz(j, J);
			for (int state = 0; state < numStates; state++) {
				IntVec nextStatesJS = nextStates[jz][state];
				DoubleVec transWeightsJS = transWeights[jz][state];
				for (int q = 0; q < nextStatesJS.size(); q++) {
					int state2 = nextStatesJS.get(q);
					double transWeight = transWeightsJS.get(q);

					beta[j][state] += (j == J - 1 ? (state2 == finalState ? 1 : 0)
							: beta[j + 1][state2])
							* emissionWeights[j][state] * transWeight;

					if (beta[j][state] > 1) {
						warning("beta(j=%d,state=%s) = %f > 1", j, states.getObject(state),
								beta[j][state]);
						beta[j][state] = 1;
					}
				}

				// dbg("beta(%d,%s) = %f", j, stateObj, beta[j][state]);
			}
		}

		if (Main.rantOutput) for (int j = 0; j < J; j++)
			for (int state = 0; state < numStates; state++)
				dbg("beta(%d,%s) = %f", j, states.getObject(state), beta[j][state]);

		return beta;
	}

	public double computeLikelihood(double[][] alpha, double[][] beta) {
		int anyIndex = 0;
		double likelihood = 0;
		for (int state = 0; state < states.size(); state++)
			likelihood += alpha[anyIndex][state] * beta[anyIndex][state];

		/*
		 * if(likelihood > 0) { for(int j = 0; j < alpha.length; j++) { double
		 * jlikelihood = 0; for(int state = 0; state < states.size(); state++)
		 * jlikelihood += alpha[j][state] * beta[j][state]; dbg("like(j=%d) = %f",
		 * j, jlikelihood/likelihood); //if(Math.abs(jlikelihood/likelihood-1) <
		 * 1e-5 } }
		 */

		if (!NumUtils.isFinite(likelihood)) {
			error("Bad likelihood: " + likelihood);
			return 0;
		}
		return likelihood;
	}

	// For Viterbi decoding
	static class Rec {
		public Rec() {
			p = Double.MIN_VALUE;
			state = -1;
		}

		public Rec(double p, int state) {
			this.p = p;
			this.state = state;
		}

		public void improve(Rec r) {
			if (r.p > p) {
				p = r.p;
				state = r.state;
			}
		}

		public String toString() {
			return "Rec(" + Fmt.D(p) + "," + state + ")";
		}

		public String toString(Indexer states) {
			if (state == -1) return "NullRec";
			return "Rec(" + Fmt.D(p) + "," + states.getObject(state) + ")";
		}

		public double p;
		public int state;
	}

	// Return a list of states
	public int[] computeViterbiPath(double[][] emissionWeights) {
		// bestRecs[j][state] = (p, state2),
		// where p is the maximum probability of a state sequence that begins with
		// state at position j, state2 is the next state that we used to achieve p
		int J = emissionWeights.length;
		int numStates = states.size();
		Rec[][] bestRecs = new Rec[J][numStates];
		Rec bestInitRec = new Rec(); // Best from initState

		for (int j = J - 1; j >= -1; j--) {
			// FOR TRANS
			int jz = stateMapper.getjz(j, J);
			for (int state = 0; state < numStates; state++) {
				IntVec nextStatesJS = nextStates[jz][state];
				DoubleVec transWeightsJS = transWeights[jz][state];
				Rec bestRec = new Rec();
				for (int q = 0; q < nextStatesJS.size(); q++) {
					int state2 = nextStatesJS.get(q);
					double transWeight = transWeightsJS.get(q);

					// dbg("jz=%d, %s => %s: %f", jz, states.get(state),
					// states.get(state2), transWeight);

					double p = (j == J - 1 ? (state2 == finalState ? 1 : 0)
							: bestRecs[j + 1][state2].p)
							* (j == -1 ? (state == initState ? 1 : 0) : emissionWeights[j][state])
							* transWeight;
					bestRec.improve(new Rec(p, state2));
				}
				if (j >= 0)
					bestRecs[j][state] = bestRec;
				else if (state == initState) bestInitRec = bestRec;

				if (Main.rantOutput)
					dbg("bestRec(j=%d,state=%s) = %s", j, states.getObject(state), bestRec
							.toString(states));
			}
		}

		if (Main.rantOutput) dbg("SCORE " + bestInitRec.p);

		// Trace out best path
		int[] path = new int[J];
		Rec rec = bestInitRec;
		for (int j = 0; j < J; j++) {
			if (rec.state == -1) {
				error("bestRec at j=%d was %s, setting state to 0", j, rec);
				rec.state = 0;
			}
			path[j] = rec.state;
			rec = bestRecs[j][rec.state];
		}
		assert rec.state == finalState : rec;
		return path;
	}
}

// //////////////////////////////////////////////////////////

class TrellisOutput {
	Trellis trellis;
	double[][] emissionWeights;
	double[][] alpha, beta;
	double likelihood;

	public TrellisOutput(Trellis trellis, double[][] emissionWeights) {
		this.trellis = trellis;
		this.emissionWeights = emissionWeights;
		this.alpha = trellis.computeForwardProbs(emissionWeights);
		this.beta = trellis.computeBackwardProbs(emissionWeights);
		this.likelihood = trellis.computeLikelihood(alpha, beta);
	}

	public double getNodePosterior(int j, int state) {
		return alpha[j][state] * beta[j][state] / likelihood;
	}

	// We have already computed transitionProb(jz, state1, state2)
	public double getEdgePosterior(int j, int state1, int state2, double transitionProb,
			int J) {
		// Assume must end in finalState
		return (j == -1 ? (state1 == trellis.initState ? 1 : 0) : alpha[j][state1])
				* (j == J - 1 ? (state2 == trellis.finalState ? 1 : 0) : beta[j + 1][state2])
				* transitionProb * (j == -1 || j == J - 1 ? 1 : emissionWeights[j][state1])
				/ likelihood;
	}
}

// //////////////////////////////////////////////////////////

// Convention: emit symbol at position j and then transition.
class WATrellis extends Trellis {
	int I; // Length of English sentence
	Model<FirstOrderModel> params;
	double nullProb; // Specific to this sentence length
	WAState.Factory factory;
	SentencePair pair;
	DistortionParameters distParams;

	public WATrellis(WAState.Factory factory, int I, Model<FirstOrderModel> model,
			SentencePair pair) {
		this.factory = factory;
		this.I = I;
		this.params = model;
		this.pair = pair;
		this.distParams = model.distortionModel.getDistortionParameters(pair);
		this.nullProb = (EMWordAligner.nullProb == 1 ? 1.0 / (I + 1) : EMWordAligner.nullProb);

		createStates();
		initState = states.indexOf(factory.getInitState(I));
		finalState = states.indexOf(factory.getFinalState(I));
		stateMapper = model.getStateMapper();

		// Allocate memory
		int numjz = stateMapper.numjz();
		nextStates = new IntVec[numjz][states.size()];
		transWeights = new DoubleVec[numjz][states.size()];
		for (int jz = 0; jz < numjz; jz++) {
			for (int i = 0; i < nextStates[jz].length; i++)
				nextStates[jz][i] = new IntVec();
			for (int i = 0; i < transWeights[jz].length; i++)
				transWeights[jz][i] = new DoubleVec();
		}

		createTransitions();
		//		logs("Create trellis for length I=%d with %d states", I, numStates());
	}

	protected void createStates() {
		// Create states
		for (int i = -1; i <= I; i++) {
			for (int b = 0; b < 2; b++) {
				WAState stateObj = factory.createState(i, b == 1, I);
				if (stateObj != null) states.getIndex(stateObj);
			}
		}
	}

	protected void createTransitions() {
		// Create transitions
		for (int jz = 0; jz < stateMapper.numjz(); jz++) { // From state
			for (int i = -1; i <= I; i++) {
				for (int b = 0; b < 2; b++) {
					WAState stateObj = factory.createState(i, b == 1, I);
					if (stateObj == null) continue;
					int state = states.getIndex(stateObj);

					double transSum = 0;
					for (int i2 = -1; i2 <= I; i2++) { // To state2
						for (int b2 = 0; b2 < 2; b2++) {
							WAState stateObj2 = factory.createState(i2, b2 == 1, I);
							if (stateObj2 == null) continue;
							int state2 = states.getIndex(stateObj2);

							// Add transition if valid
							if (stateMapper.isValidTransition(stateObj, stateObj2)) {
								double transWeight = transitionProb(jz, stateObj, stateObj2);
								// John: transweights are no longer normalized.
								nextStates[jz][state].add(state2);
								transWeights[jz][state].add(transWeight);
								transSum += transWeight;

								if (Main.rantOutput)
									dbg("ADD jz=%d, %d:%s -> %d:%s = %f", jz, state, stateObj, state2,
											stateObj2, transWeight);
							}
						}
					}
					transWeights[jz][state].multAll(1.0 / transSum);

					// Effect: giving too little weight to short sentences?
					// Can't normalize anyway, argh!
					// Explicitly normalize transition weights (just to make us feel good)
					//					if (transSum != 0 && Math.abs(transSum - 1) > 1e-10)
					//						error("transSum for jz=%d,i=%d,b=%d is %f", jz, i, b, transSum);
					/*
					 * if(transSum > 0) { //assert transSum > 0 :
					 * String.format("jz=%d,i=%d,b=%d has 0 sum", jz, i, b);
					 * //transWeights[jz][state].multAll(1.0/transSum); }
					 */
				}
			}
		}
	}

	public void updateTransitionProbs(TrellisOutput toutput,
			Model<FirstOrderModel> newParams) {
		DistortionParameters newDistParams = newParams.distortionModel
				.getDistortionParameters(pair);
		int J = toutput.emissionWeights.length;
		int numStates = numStates();

		// Update distortion probabilities
		for (int j = -1; j < J; j++) { // For each (j,j+1) adjacent pair...
			// FOR TRANS
			int jz = stateMapper.getjz(j, J);
			for (int state = 0; state < numStates; state++) {
				WAState stateObj = (WAState) states.getObject(state);
				IntVec nextStatesJS = nextStates[jz][state];
				DoubleVec transWeightsJS = transWeights[jz][state];
				for (int q = 0; q < nextStatesJS.size(); q++) {
					int state2 = nextStatesJS.get(q);
					double transWeight = transWeightsJS.get(q);

					// Distortion probability used when have transition into a state with
					// currAligned=true
					WAState stateObj2 = (WAState) states.getObject(state2);
					if (stateObj2.currAligned) {
						double posterior = toutput.getEdgePosterior(j, state, state2, transWeight, J);
						// if(posterior > 1)
						if (!NumUtils.isFinite(posterior)) {
							error("Edge posterior for j=%d/%d, %s -> %s: %f", j, J, stateObj,
									stateObj2, posterior);
							posterior = 1;
						}
						try {
							newDistParams.add(jz, stateObj.i, stateObj2.i, I, posterior);
						} catch (ArrayIndexOutOfBoundsException e) {
							LogInfo.warning("Distortion model update concurrency error: skipping sentence");
						}
					}
				}
			}
		}
	}

	// Assume these states have already been passed through isValidTransition
	// state1(h,a) -> state2(i,b)
	// P(a_j = i,1 | a_{j-1} = h,*) \propto (1-p_0) * P(i-h|I)
	// P(a_j = i,0 | a_{j-1} = h,*) \propto p_0 * delta(i=h)
	public double transitionProb(int jz, WAState state1, WAState state2) {
		if (!state2.currAligned) {
			// There better be only one state for which this holds
			assert state1.i == state2.i;
			return nullProb;
		} else {
			return (1 - nullProb) * distParams.get(jz, state1.i, state2.i, I);
		}
	}
}
