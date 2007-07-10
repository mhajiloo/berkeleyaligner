package edu.berkeley.nlp.wordAlignment.distortion;

import java.io.Serializable;

/**
 * Maps tag types to indices to collapse types into classes.
 */
public interface TagMapper extends Serializable {
	public int getGroup(String tag);

	public int numGroups();
}
