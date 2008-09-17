package edu.berkeley.nlp.wa.basic;

import java.util.*;

/**
 * Simple class for measuring elapsed time.
 */
public class StopWatch {
  public StopWatch() { }
  public StopWatch(long ms) { startTime = 0; endTime = ms; this.ms = ms; }

  public void reset() {
    ms = 0;
  }
  public StopWatch start() {
    startTime = System.currentTimeMillis();
    return this;
  }
  public StopWatch stop() {
    endTime = System.currentTimeMillis();
    ms = endTime - startTime;
    n = 1;
    return this;
  }
  public StopWatch accumStop() { // Stop and accumulate time
    endTime = System.currentTimeMillis();
    ms += endTime - startTime;
    n++;
    return this;
  }

  public String toString() {
    long m = ms / 60000; ms %= 60000;
    long h = m / 60; m %= 60;
    long d = h / 24; h %= 24;
    long y = d / 365; d %= 365;
    long s = ms / 1000;

    StringBuilder sb = new StringBuilder();

    if(y > 0) {
      sb.append(y); sb.append('y');
      sb.append(d); sb.append('d');
    }
    if(d > 0) {
      sb.append(d); sb.append('d');
      sb.append(h); sb.append('h');
    }
    else if(h > 0) {
      sb.append(h); sb.append('h');
      sb.append(m); sb.append('m');
    }
    else if(m > 0) {
      sb.append(m); sb.append('m');
      sb.append(s); sb.append('s');
    }
    else if(s > 9) {
      sb.append(s); sb.append('s');
    }
    else if(s > 0) {
      sb.append((ms/100)/10.0); sb.append('s');
    }
    else {
      sb.append(ms/1000.0); sb.append('s');
    }
    return sb.toString();
  }

  public long startTime, endTime, ms;
  public int n;

  // For measuring time of certain types of events.
  private static Map<String, StopWatch> stopWatches = new LinkedHashMap<String, StopWatch>();
  private static StopWatch lastStopWatch = null;
  public static StopWatch getWatch(String s) {
    return MapUtils.getMut(stopWatches, s, new StopWatch());
  }
  public static StopWatch start(String s)     { return lastStopWatch = getWatch(s).start(); }
  public static StopWatch accumStop(String s) { return lastStopWatch = getWatch(s).accumStop(); }
  public static StopWatch accumStopLast()     { return lastStopWatch.accumStop(); }
  public static OrderedStringMap getStats() {
    OrderedStringMap map = new OrderedStringMap();
    for(String key : stopWatches.keySet()) {
      StopWatch watch = getWatch(key);
      map.put(key, watch + " (" + new StopWatch(watch.n == 0 ? 0 : watch.ms/watch.n) + " x " + watch.n + ")");
    }
    return map;
  }
};
