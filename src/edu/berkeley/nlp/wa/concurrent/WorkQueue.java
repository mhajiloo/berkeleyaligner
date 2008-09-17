package edu.berkeley.nlp.wa.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import edu.berkeley.nlp.wa.basic.LogInfo;

/**
 *	A thread manager for executing many tasks safely
 *  using a fixed number of threads.
 *  
 *  Use WorkQueueReorderer to recover ordered outputs
 */
public class WorkQueue {

	private static final long WAIT_TIME = 10;
	private ExecutorService executor;
	private Semaphore sem;
	private boolean serialExecution;

	public WorkQueue(int numThreads) {
		if (numThreads == 0) {
			serialExecution = true;
		} else {
			executor = Executors.newFixedThreadPool(numThreads);
			sem = new Semaphore(numThreads);
			serialExecution = false;
		}
	}

	public void execute(final Runnable work) {
		if (serialExecution) {
			work.run();
		} else {
			sem.acquireUninterruptibly();
			executor.execute(new Runnable() {

				public void run() {
					try {
						work.run();
					} catch (AssertionError e) {
						LogInfo.error(e);
						e.printStackTrace();
					} catch (RuntimeException e) {
						LogInfo.error(e);
						e.printStackTrace();
					}
					sem.release();
				}
			});
		}
	}

	public void finishWork() {
		if (serialExecution) return;
		executor.shutdown();
		try {
			int secs = 0;
			while (!executor.awaitTermination(WAIT_TIME, TimeUnit.SECONDS)) {
				secs += WAIT_TIME;
				LogInfo.logs("Awaited executor termination for %d seconds", secs);
			}
		} catch (InterruptedException e) {
			throw new RuntimeException("Work queue interrupted");
		}
	}
}
