package edu.berkeley.nlp.wa.basic;

import java.text.*;

/**
 * Formatting class.  I'm really lazy.
 * D() is a family of default functions for formatting various types of objects.
 */
public class Fmt {
  public static String D(double x) {
    if(Math.abs(x - (int)x) < 1e-40) // An integer (probably)
      return ""+(int)x;
    if(Math.abs(x) < 1e-3) // Scientific notation (close to 0)
      return String.format("%.2e", x);
    return String.format("%.3f", x);
  }
  public static String D(int[] x) { return StrUtils.join(x); }
  public static String D(double[] x) { return D(x, " "); }
  public static String D(double[] xs, String delim) {
    StringBuilder sb = new StringBuilder();
    for(double x : xs) {
      if(sb.length() > 0) sb.append(delim);
      sb.append(Fmt.D(x));
    }
    return sb.toString();
  }
  // Print out only first N
  public static String D(double[] x, int firstN) {
    if(firstN >= x.length) return D(x);
    return D(ListUtils.subArray(x, 0, firstN)) + " ...("+(x.length-firstN) + " more)";
  }

  public static String D(TDoubleMap map) { return D(map, 20); }
  public static String D(TDoubleMap map, int numTop) {
    return MapUtils.topNToString(map, numTop);
  }

  public static String bytesToString(long b) {
    int mb = (int)(b / (1024*1024));
    int kb = (int)(b / 1024);
    if(mb > 0) return mb+"M";
    if(kb > 0) return kb+"K";
    return b+"";
  }
  public static String formatEasyDateTime(long t) {
    return new SimpleDateFormat("MM/dd HH:mm").format(t);
  }
}
