package com.datastax.dse.demo;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ExecutorsWrapper {
    public static void main(String[] args) {

        System.out.println("Starting ExecutorsWrapper..");

        System.out.println("Creating Executor Service with a thread pool of Size 2");
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        LatencyChecker2 client = new LatencyChecker2();

        try {
            client.loadProperties();
            client.connect();
            client.createSchema();
            client.createPreparedStatements();

        } finally {

        }


        Runnable task1 = () -> {
            //System.out.println("Executing Task1 inside : " + Thread.currentThread().getName());
            try {
                TimeUnit.SECONDS.sleep(1);
                UUID[] uuidArray = client.loadData();
                client.computeDelta(uuidArray);
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        };

        Runnable task2 = () -> {
            //System.out.println("Executing Task2 inside : " + Thread.currentThread().getName());
            try {
                TimeUnit.SECONDS.sleep(2);
                UUID[] uuidArray = client.loadData();
                client.computeDelta(uuidArray);
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
        };


        System.out.println("Submitting the tasks for execution...");

        for (int i=0; i<2; i++) {
            executorService.submit(task1);
            executorService.submit(task2);
        }

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