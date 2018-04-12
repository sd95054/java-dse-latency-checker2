package com.datastax.dse.demo;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorsWrapper {

    private static int numTasks=1;

    // Define a static logger variable so that it references the
    // root Logger
    static Logger logger = Logger.getRootLogger();

    public static void main(String[] args) {

        // Set up a simple configuration that logs on the console.
        BasicConfigurator.configure();

        logger.setLevel(Level.ERROR);

        LatencyChecker2 client = new LatencyChecker2();

        try {
            client.loadProperties();
            numTasks = client.numTasks;
            client.connect();
            client.createSchema();
            client.createPreparedStatements();

        } finally {

        }

        Runnable[] taskArray = new Runnable[numTasks];

        System.out.println("Creating Executor Service with a thread pool of Size:" + numTasks);
        ExecutorService executorService = Executors.newFixedThreadPool(numTasks);


        for (int i = 0; i < numTasks; i++) {
            int j = i;
            taskArray[i] = () -> {
                //System.out.println("Executing Task #" + j + ": " + Thread.currentThread().getName());

                LatencyChecker2.loadData_holder loadResults = client.loadData();
                LatencyChecker2.computeDelta_holder cdh = client.computeDelta(loadResults);
                cdh.printResults(j, Thread.currentThread().getName());

            };

        }

        System.out.println("Submitting the tasks for execution...");

        for (int i = 0; i < numTasks; i++) {
            executorService.submit(taskArray[i]);
        }

        System.out.println("\nIteration(I), WriteLatency(WL), ReadLatency(RL), TotalLatency(TL)\n" +
                "Additional Latency(AL), Write Coordinator(WC), Server-side Write Latency (sWL)\n" +
                "Read Coordinator(RC), Server-side Read Latency(sRL)\n");

        System.out.println("All time is reported in milli-seconds\n");

        System.out.println("\nI ThreadName \t    WL\t   RL \t TL  \tAL \tWC \t sWL \t RC \t   sRL\n");


        executorService.shutdown();

        try {
            // Wait a while for existing tasks to terminate
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("Pool did not terminate");
            }
            else {
                System.out.println("Shutdown...");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            executorService.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }

        client.close();
    }

}