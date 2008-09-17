package edu.berkeley.nlp.wa.util;

import java.util.Arrays;

public class ArrayUtil {

	public static void fill(float[][] a, float val) {
		for (int i = 0; i < a.length; i++) {
			Arrays.fill(a[i], val);
		}
	}

	public static void fill(float[][][] a, float val) {
		for (int i = 0; i < a.length; i++) {
			fill(a[i], val);
		}
	}

	public static void fill(float[][][][] a, float val) {
		for (int i = 0; i < a.length; i++) {
			fill(a[i], val);
		}
	}
	
	public static void fill(float[][][][][] a, float val) {
		for (int i = 0; i < a.length; i++) {
			fill(a[i], val);
		}
	}

	public static void fill(double[][] a, double val) {
		for (int i = 0; i < a.length; i++) {
			Arrays.fill(a[i], val);
		}
	}

	public static void fill(double[][][] a, double val) {
		for (int i = 0; i < a.length; i++) {
			fill(a[i], val);
		}
	}

	public static void fill(double[][][][] a, double val) {
		for (int i = 0; i < a.length; i++) {
			fill(a[i], val);
		}
	}
	
	public static void fill(double[][][][][] a, double val) {
		for (int i = 0; i < a.length; i++) {
			fill(a[i], val);
		}
	}

	public static String toString(float[][] a) {
		String s = "[";
		for (int i = 0; i < a.length; i++) {
			s = s.concat(Arrays.toString(a[i]) + ", ");
		}
		return s + "]";
	}

	public static String toString(float[][][] a) {
		String s = "[";
		for (int i = 0; i < a.length; i++) {
			s = s.concat(toString(a[i]) + ", ");
		}
		return s + "]";
	}

	public static String toString(double[][] a) {
		String s = "[";
		for (int i = 0; i < a.length; i++) {
			s = s.concat(Arrays.toString(a[i]) + ", ");
		}
		return s + "]";
	}

	public static String toString(double[][][] a) {
		String s = "[";
		for (int i = 0; i < a.length; i++) {
			s = s.concat(toString(a[i]) + ", ");
		}
		return s + "]";
	}

	public static String toString(boolean[][] a) {
		String s = "[";
		for (int i = 0; i < a.length; i++) {
			s = s.concat(Arrays.toString(a[i]) + ", ");
		}
		return s + "]";
	}

	public static double[] copy(double[] mat) {
		int m = mat.length;
		double[] newMat = new double[m];
		System.arraycopy(mat, 0, newMat, 0, mat.length);
		return newMat;
	}

	public static double[][] copy(double[][] mat) {
		int m = mat.length, n = mat[0].length;
		double[][] newMat = new double[m][n];
		for (int r = 0; r < m; r++)
			System.arraycopy(mat[r], 0, newMat[r], 0, mat[r].length);
		return newMat;
	}

	public static double[][][] copy(double[][][] mat) {
		int m = mat.length, n = mat[0].length, p = mat[0][0].length;
		double[][][] newMat = new double[m][n][p];
		for (int r = 0; r < m; r++)
			for (int c = 0; c < n; c++)
				System.arraycopy(mat[r][c], 0, newMat[r][c], 0, mat[r][c].length);
		return newMat;
	}

	public static double[][][][] copy(double[][][][] mat) {
		int m = mat.length, n = mat[0].length, p = mat[0][0].length, q = mat[0][0][0].length;
		double[][][][] newMat = new double[m][n][p][q];
		for (int r = 0; r < m; r++)
			for (int c = 0; c < n; c++)
				for (int i = 0; i < p; i++)
					System.arraycopy(mat[r][c][i], 0, newMat[r][c][i], 0, mat[r][c][i].length);
		return newMat;
	}

}
