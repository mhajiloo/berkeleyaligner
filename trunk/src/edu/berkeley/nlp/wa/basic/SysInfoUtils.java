package edu.berkeley.nlp.wa.basic;

import java.io.*;
import java.util.*;
import java.net.*;

public class SysInfoUtils {
  public static String getCurrentDateStr() {
    return new Date().toString();
  }
  public static String getHostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    }
    catch(UnknownHostException e) {
      return "(unknown)";
    }
  }
  public static String getShortHostName() {
    String name = getHostName();
    int i = name.indexOf('.');
    if(i == -1) return name;
    return name.substring(0, i);
  }
  public static String getcwd() { return System.getProperty("user.dir"); }

  private static int numCPUs = -1; // Cache: doesn't change
  public static int getNumCPUs() {
    // Linux
    if(numCPUs != -1) return numCPUs;
    try {
      int n = 0;
      for(String line : IOUtils.readLines("/proc/cpuinfo")) {
        if(line.startsWith("processor"))
          n++;
      }
      return numCPUs = n;
    }
    catch(IOException e) {
    }

    // MacOS
    try {
      // Output format: hw.ncpu: 1
      return numCPUs = 
        Integer.parseInt(StrUtils.split(Utils.systemGetStringOutput("sysctl hw.ncpu").trim(), " ")[1]);
    } catch(Exception e) {
    }

    return 0;
  }

  public static int getNumUsedCPUs() {
    // This command should return the percent CPU usages of
    // all processes, one on each line
    // A bit of a hack: if a process usese more than 50% of the CPU,
    // then it is considered used
    try {
      int n = 0;
      for(String line : StrUtils.split(Utils.systemGetStringOutput("ps ax -o pcpu"), "\n")) {
        double percentCPU = Utils.parseDoubleEasy(line);
        if(percentCPU > 50) n++;
      }
      return n;
    } catch(Exception e) {
      return -1;
    }
  }

  public static int getNumFreeCPUs() {
    return getNumCPUs() - getNumUsedCPUs();
  }

  // Return in MHz
  private static int cpuSpeed = -1; // Cache: doesn't change
  public static int getCPUSpeed() {
    if(cpuSpeed != -1) return cpuSpeed; 

    // Linux: take the average of the CPU speeds of all processors
    try {
      double sum = 0;
      int n = 0;
      for(String line : IOUtils.readLines("/proc/cpuinfo")) {
        if(line.startsWith("cpu MHz")) {
          sum += Double.parseDouble(ListUtils.getLast(StrUtils.split(line)));
          n++;
        }
      }
      return cpuSpeed = (int)(sum/n+0.5);
    } catch(IOException e) {
    }

    // MacOS
    try {
      // Output format: hw.cpufrequency: 1499999994
      return cpuSpeed = 
        Integer.parseInt(StrUtils.split(Utils.systemGetStringOutput("sysctl hw.cpufrequency").trim(), " ")[1])/1000000;
    } catch(Exception e) {
    }
    
    return 0;
  }
  public static String getCPUSpeedStr() {
    return getCPUSpeed() + " MHz";
  }

  public static String getMaxMemoryStr() {
    long mem = Runtime.getRuntime().maxMemory();
    return Fmt.bytesToString(mem);
  }
  public static String getUsedMemoryStr() {
    long totalMem = Runtime.getRuntime().totalMemory();
    long freeMem = Runtime.getRuntime().freeMemory();
    return Fmt.bytesToString(totalMem-freeMem);
  }
}
