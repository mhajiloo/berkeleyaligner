package edu.berkeley.nlp.wa.concurrent;

import java.util.Set;
import java.util.concurrent.Semaphore;

import edu.berkeley.nlp.wa.util.PriorityQueue;

/**
 *  A reorderer that processes inputs in the order in which
 *  they were entered into a work queue.
 *  
 *  Note that this implementation uses existing threads to do all the work.
 */
public abstract class WorkQueueReorderer<T> {

	private PriorityQueue<T> pq = new PriorityQueue<T>();
	private Semaphore sem = new Semaphore(1);
	int nextToOutput = 0;

	/**
	 * What to do with output, with order guarantees.
	 * 
	 * @param queueOutput Something created by a WorkQueue task
	 */
	public abstract void process(T queueOutput);

	public void addToProcessQueue(int orderIndex, T queueOutput) {
		sem.acquireUninterruptibly();

		if (orderIndex == nextToOutput) {
			nextToOutput++;
			process(queueOutput);
			drainQueue();
			try {
			} catch (Exception e) {
				System.err.println("WorkQueueReorderer: " + e.getLocalizedMessage());
				e.printStackTrace();
			}
		} else {
			pq.add(queueOutput, -1.0 * orderIndex);
		}
		sem.release();
	}

	private void drainQueue() {
		if (pq.isEmpty()) return;
		while (nextToOutput == -1.0 * pq.getPriority()) {
			process(pq.next());
			nextToOutput++;
			if(pq.isEmpty()) return;
		}
	}

	public boolean hasStrandedOutputs() {
		return !getStrandedOutputs().isEmpty();
	}

	public Set<T> getStrandedOutputs() {
		return pq.asCounter().keySet();
	}

}
