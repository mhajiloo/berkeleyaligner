package edu.berkeley.nlp.wa.basic;

import java.io.*;
import java.util.*;

/**
 * The logging output has a tree structure, where each node is a
 * line of output, and the depth of a node is its indent level.
 * A run is the sequence of children of some node.
 * A subset of the lines in the run will get printed.
 */
public class LogInfo {
  public static void track(String format, Object... args) {
    track(String.format(format, args), false);
  }
  public static void track(Object o) {
    track(o, false);
  }
  public static void track(Object o, boolean printAllLines) {
    if(indWithin()) {
      if(thisRun().shouldPrint()) {
        print(o);
        buf.append(" {\n"); // Open the block.

        childRun().init();
        childRun().printAllLines = printAllLines;
      }
      else {
        stoppedIndLevel = indLevel;
        maxIndLevel = -maxIndLevel; // Prevent children from outputting.
      }
    }

    indLevel++;
  }

  // Convenient way to end and return a value
  public static <T> T end_track(T x) { end_track(); return x; }

  public static void end_track() {
    indLevel--;

    if(stoppedIndLevel == indLevel) {
      stoppedIndLevel = -1;
      maxIndLevel = -maxIndLevel; // Restore indent level.
    }

    if(indWithin()) {
      if(thisRun().newLine()) {
        // Finish up child level.
        indLevel++;
        int n = thisRun().numOmitted();
        if(n > 0)
          print("... " + n + " lines omitted ...\n");
        indLevel--;
        childRun().finish();

        if(buf.length() > 0) // Nothing was printed, because buf hasn't been emptied.
          buf.delete(0, buf.length()); // Just pretend we didn't open the block.
        else // Something indented was printed.
          print("}"); // Close the block.

        // Print time
        StopWatch ct = childRun().watch;
        if(ct.ms > 1000) {
          rawPrint(" [" + ct);
          if(indLevel > 0) {
            StopWatch tt = thisRun().watch;
            tt.stop();
            rawPrint(", cum. " + tt);
          }
          rawPrint("]");
        }
        rawPrint("\n");
      }
    }
  }

  public static void logs(String format, Object... args) {
    logs(String.format(format, args));
  }
  public static void logs(Object o) {
    if(indWithin() && thisRun().newLine())
      printLines(o);
  }
  public static void logss(String format, Object... args) {
    logss(String.format(format, args));
  }
  public static void logss(Object o) {
    // Output something if parent outputted something.
    // Subtle note: parent must have been a track, not logs, so its run
    // information has not been updated yet until it closes.
    // Therefore, calling shouldPrint() on it is valid.
    if(indLevel == 0 || parentIndWithin() && parentRun().shouldPrint()) {
      thisRun().newLine();
      printLines(o);
    }
  }
  public static void dbg(String format, Object... args) {
    dbg(String.format(format, args));
  }
  public static void dbg(Object o) {
    logss("DBG: " + o);
  }
  public static void rant(String format, Object... args) {
    rant(String.format(format, args));
  }
  public static void rant(Object o) {
    logss("RANT: " + o);
  }
  public static void error(String format, Object... args) {
    error(String.format(format, args));
  }
  public static void error(Object o) {
    print("ERROR: " + o + "\n");
    numErrors++;
  }
  public static void warning(String format, Object... args) {
    warning(String.format(format, args));
  }
  public static void warning(Object o) {
    print("WARNING: " + o + "\n");
    numWarnings++;
  }

  /*public static void barf(Object o) throws RuntimeException {
    throw new RuntimeException(o.toString());
  }
  public static void barf(String format, Object... args) {
    barf(String.format(format, args));
  }*/

  public static void printProgStatus() {
    logs("PROG_STATUS: time = " + watch.stop() + ", memory = " + SysInfoUtils.getUsedMemoryStr());
  }
  public static <T> void printList(String s, String lines) {
    printList(s, Arrays.asList(lines.split("\n")));
  }
  public static <T> void printList(String s, List<T> items) {
    track(s, true);
    for(T x : items) logs(x);
    end_track();
  }

  // Options.
  @Option(gloss="Maximum indent level.")
    public static int maxIndLevel = 10;
  @Option(gloss="Maximum number of milliseconds between consecutive lines of output.")
    public static int msPerLine = 1000;
  @Option(gloss="File to write log.")
    public static String file = "";
  @Option(gloss="Whether to output to the console.", name="stdout")
    public static boolean writeToStdout = true;
  @Option(gloss="Dummy placeholder for a comment")
    static public String note = "";

  static { updateStdStreams(); }
  public static void updateStdStreams() {
    try {
      stdin  = CharEncUtils.getReader(System.in);
      stdout = CharEncUtils.getWriter(System.out);
      stderr = CharEncUtils.getWriter(System.err);
    } catch(IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void init() {
    buf = new StringBuilder();
    indLevel = 0;
    stoppedIndLevel = -1;

    runs = new ArrayList<LogRun>(128);
    for(int i = 0; i < 128; i++)
      runs.add(new LogRun());
    watch = new StopWatch();
    watch.start();

    // Write to file, stdout?
    if(!file.equals("")) {
      fout = IOUtils.openOutHard(file);
    }
    if(writeToStdout) out = stdout;
  }

  static LogRun parentRun() { return runs.get(indLevel-1); }
  static LogRun thisRun()   { return runs.get(indLevel); }
  static LogRun childRun()  { return runs.get(indLevel+1); }

  // If we were to print a new line, should we print?
  static boolean indWithin()       { return indLevel   <= maxIndLevel; }
  static boolean parentIndWithin() { return indLevel-1 <= maxIndLevel; }

  static void rawPrint(Object o) {
    if(out != null) { out.print(o); out.flush(); }
    if(fout != null) { fout.print(o); fout.flush(); }
  }

  // Print with indent; flush the buffer as necessary
  static void print(Object o) {
    rawPrint(buf);
    buf.delete(0, buf.length());
    for(int i = 0; i < indLevel; i++) rawPrint("  ");
    rawPrint(o);
  }
  // If there are new lines, put indents before them
  static void printLines(Object o) {
    if(o == null) o = "null";
    String s = StrUtils.toString(o);
    if(s.indexOf('\n') == -1)
      print(s+"\n");
    else
      for(String t : StrUtils.split(s, "\n")) print(t+"\n");
  }

  public static StopWatch getWatch() { return watch; }
  public static int getNumErrors() { return numErrors; }
  public static int getNumWarnings() { return numWarnings; }

  public static BufferedReader stdin;
  public static PrintWriter stdout, stderr;

  // Private state.
  static PrintWriter out, fout;
  static int indLevel;           // Current indent level.
  static int stoppedIndLevel;    // The number of levels that have had output stopped
  static StringBuilder buf;      // The buffer to be flushed out the next time _logs is called.
  static ArrayList<LogRun> runs; // Indent level -> state
  static StopWatch watch;        // StopWatch that starts at the beginning of the program
  static int numErrors;          // Number of errors made
  static int numWarnings;        // Number of warnings
}

/**
 * A run is a sequence of lines of text, some of which are printed.
 * Stores the state associated with a run.
 */
class LogRun {
  public LogRun() {
    watch = new StopWatch();
    init();
  }
  void init() {
    numLines = 0;
    numLinesPrinted = 0;
    nextLineToPrint = 0;
    printAllLines = false;
    watch.start();
  }
  void finish() {
    // Make it clear that this run is not printed.
    // Otherwise, logss might think its
    // parent was printed when it really wasn't.
    nextLineToPrint = -1;
    watch.stop();
  }

  boolean shouldPrint() { return numLines == nextLineToPrint; }
  int numOmitted() { return numLines - numLinesPrinted; }

  /**
   * Decide whether to print the next line.  If yes,
   * then you must print it.
   * @return Whether the next line should be printed.
   */
  boolean newLine() {
    boolean p = shouldPrint();
    numLines++;
    if(!p) return false;
    
    // Ok, we're going to print this line.
    numLinesPrinted++;

    // Decide next line to print.
    int msPerLine = LogInfo.msPerLine;
    if(numLines <= 2  || // Print first few lines anyway.
       msPerLine == 0 || // Print everything.
       printAllLines)    // Print every line in this run (by fiat).
      nextLineToPrint++;
    else {
      watch.stop();
      if(watch.ms == 0) // No time has elapsed.  
        nextLineToPrint *= 2; // Exponentially increase time between lines.
      else // Try to maintain the number of lines per second.
        nextLineToPrint += (int)Math.max((double)numLines * msPerLine / watch.ms, 1);
    }

    return true;
  }

  int numLines;           // Number of lines that we've gone through so far in this run.
  int numLinesPrinted;    // Number of lines actually printed.
  int nextLineToPrint;    // Next line to be printed (lines are 0-based).
  StopWatch watch;        // Keeps track of time spent on this run.
  boolean printAllLines;  // Whether or not to force the printing of each line.
}
