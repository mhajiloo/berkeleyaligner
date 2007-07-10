package edu.berkeley.nlp.wordAlignment.distortion;

import edu.berkeley.nlp.wordAlignment.EMWordAligner;
import edu.berkeley.nlp.wordAlignment.ExpAlign;

/**
 * Expected counts for Model 1 (and 2, when implemented) 
 */
public class Model1ExpAlign extends ExpAlign {
	double[][] table;

	Model1ExpAlign(double[][] table) {
		this.table = table;
	}

	public int J() {
		return table.length;
	}

	public int I() {
		return table[0].length - 1;
	}

	public double get(int j, int i) {
		return table[j][i];
	}

	static double combine(double a, double b) {
		return a * b;
	}

	// table[j][i] = P(a_j = i), i = I corresponds to NULL
	static double[][] merge(double[][] table, double[][] revTable) {
		int J = table.length;
		int I = table[0].length - 1;
		double[][] newTable = new double[J][I + 1];

		for (int i = 0; i < I; i++) {
			for (int j = 0; j < J; j++)
				newTable[j][i] = table[j][i] * revTable[i][j];
		}

		// For null, compute the probability that no English word i
		// picked that this
		for (int j = 0; j < J; j++) {
			double p = 1;
			for (int i = 0; i < I; i++)
				p *= 1 - revTable[i][j];
			newTable[j][I] = table[j][I] * p;
		}
		return newTable;
	}

	// Note: does not use this.
	public void merge(ExpAlign _ea1, ExpAlign _ea2) {
		Model1ExpAlign ea1 = (Model1ExpAlign) _ea1;
		Model1ExpAlign ea2 = (Model1ExpAlign) _ea2;

		if (EMWordAligner.mergeConsiderNull) {
			double[][] newTable1 = merge(ea1.table, ea2.table);
			double[][] newTable2 = merge(ea2.table, ea1.table);
			ea1.table = newTable1;
			ea2.table = newTable2;
		} else {
			int J = ea1.J();
			int I = ea1.I();
			for (int i = 0; i < I; i++) {
				for (int j = 0; j < J; j++) {
					double x1 = ea1.table[j][i];
					double x2 = ea2.table[i][j];
					ea1.table[j][i] = combine(x1, x2);
					ea2.table[i][j] = combine(x2, x1);
				}
			}
		}
	}
}
