package edu.berkeley.nlp.wordAlignment.distortion;

import static edu.berkeley.nlp.wa.basic.LogInfo.error;
import static edu.berkeley.nlp.wa.basic.LogInfo.warning;

/**
 * Expected counts under a first order model.
 */
public class HMMExpAlign extends Model1ExpAlign {
	TrellisOutput output;

	HMMExpAlign(int I, int J, TrellisOutput output) {
		super(new double[J][I + 1]);
		this.output = output;
		int numStates = output.trellis.numStates();

		// Fill table
		// table[j][i] = p(a_j = i,1) if i < I
		// table[j][i] = p(a_j = *,0) if i == I
		for (int j = 0; j < J(); j++) {
			for (int state = 0; state < numStates; state++) {
				WAState stateObj = (WAState) output.trellis.states.getObject(state);

				double posterior = output.getNodePosterior(j, state);
				if (!(posterior <= 1 + 1e-5)) {
					warning("expAlign(j=%d, state=%s) = %f > 1", j, stateObj, posterior);
					posterior = 0;
				}
				if (posterior == 0) continue;
				if (stateObj.currAligned && stateObj.i == -1) {
					error("Somehow j=%d aligned to %s: %f", j, stateObj, posterior);
					continue;
				}
				//dbg("HMMExpAlign: j=%d,state=%s: %f", j, stateObj, posterior);
				if (stateObj.currAligned)
					table[j][stateObj.i] += posterior;
				else
					table[j][I()] += posterior;
			}
		}
	}
}
