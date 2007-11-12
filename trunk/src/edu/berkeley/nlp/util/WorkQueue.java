package edu.berkeley.nlp.util;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import fig.basic.LogInfo;

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

	public WorkQueue(int numThreads) {
		executor = Executors.newFixedThreadPool(numThreads);
		sem = new Semaphore(numThreads);
	}

	public void submit(Runnable work) {
		executor.execute(work);
	}

	public void execute(final Runnable work) {
		try {
			sem.acquire();
		} catch (InterruptedException e) {
			sem.release();
			throw new RuntimeException(e);
		}
		executor.execute(new Runnable() {

			public void run() {
				work.run();
				sem.release();
			}
		});

	}

	public void finishWork() {
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
