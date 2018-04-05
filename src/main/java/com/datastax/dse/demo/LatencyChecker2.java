package com.datastax.dse.demo;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.utils.UUIDs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.UUID;

public class LatencyChecker2 {

    //Statics
    private  static String[] CONTACT_POINTS_DC1;
    private  static String[] CONTACT_POINTS_DC2;
    private  static int PORT;

    // DC #1 configs
    private  String dc1_name;

    //DC #2 configs
    private  String dc2_name;

    //Number of records to write (and then read)
    private static int numRecords;

    public static void main(String[] args){

        LatencyChecker2 client = new LatencyChecker2();

        try {
            client.loadProperties();
            client.connect(CONTACT_POINTS_DC1, CONTACT_POINTS_DC2, PORT);
            client.createSchema();
            client.loadData();
            client.computeDelta();

        } finally {
            client.close();
        }
    }

    private Cluster cluster1;
    private Session session1;
    private Cluster cluster2;
    private Session session2;
    private UUID[] uuidArray;
    private String keyspace;
    private String createTable;
    private String insertStatement;
    private String selectStatement;

    private void loadProperties() {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            input = LatencyChecker2.class.getResourceAsStream("/application.properties");

            // load a properties file
            prop.load(input);

            // get the property values
            String str = prop.getProperty("CONTACT_POINTS_DC1");

            CONTACT_POINTS_DC1 = str.split(",");

            str = prop.getProperty("CONTACT_POINTS_DC2");

            CONTACT_POINTS_DC2  = str.split(",");

            PORT = Integer.parseInt(prop.getProperty("PORT"));
            dc1_name = prop.getProperty("dc1_name");
            dc2_name = prop.getProperty("dc2_name");
            numRecords = Integer.parseInt(prop.getProperty("numRecords"));
            uuidArray = new UUID[numRecords];
            keyspace = prop.getProperty("keyspace");
            createTable = prop.getProperty("createTable");
            insertStatement = prop.getProperty("insertStatement");
            selectStatement = prop.getProperty("selectStatement");

        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
        finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Initiates a connection to the cluster
     * specified by the given contact point.
     *
     * @param contactPoints_DC1 the contact points to use for DC1
     * @param contactPoints_DC2 the contact points to use for DC2
     * @param port          the port to use.
     */
    private void connect(String[] contactPoints_DC1, String[] contactPoints_DC2, int port) {

        //For DC #1

        cluster1 = Cluster.builder()
                .addContactPoints(contactPoints_DC1).withPort(port)
                .withLoadBalancingPolicy(
                        DCAwareRoundRobinPolicy.builder()
                                .withLocalDc(dc1_name)
                                .build()
                ).withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM))
                .build();


        System.out.printf("Connected to cluster: %s%n", cluster1.getMetadata().getClusterName());

        session1 = cluster1.connect();

        //For DC #2 (create session2 for reading)

        cluster2 = Cluster.builder()
                .addContactPoints(contactPoints_DC2).withPort(port)
                .withLoadBalancingPolicy(
                        DCAwareRoundRobinPolicy.builder()
                                .withLocalDc(dc2_name)
                                .build()
                ).withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM))
                .build();


        System.out.printf("Connected to cluster: %s%n", cluster2.getMetadata().getClusterName());

        session2 = cluster2.connect();
    }

    /**
     * Creates the schema (keyspace) and tables
     * for this example.
     */
    private void createSchema() {

        //NOTE: The schema should use NetworkTopologyStrategy and RF=3 for Production testing
        session1.execute(keyspace);

        session1.execute(createTable);
    }

    /**
     * * Get current time in nanoseconds
     * @return nanosecs
     */
    private long getCurrentTime(){

        return System.nanoTime();

    }

    /**
     * Inserts data into the tables.
     * Sets the Data TTL to 3600 seconds so table doesn't grow
     * arbitrarily large
     */

    private void loadData() {

        PreparedStatement preparedStatement = session1.prepare(insertStatement);

        for (int i=0; i<numRecords; i++) {

            try {
                UUID uuid = UUIDs.random();
                uuidArray[i] = uuid;

                BoundStatement boundStatement = preparedStatement.bind(uuid, getCurrentTime());

                ResultSet rs = session1.execute(boundStatement);

                //System.out.println(rs);

                //Sleep for a random amount of time to TEST latency --- Remove Thread.sleep()
                // line below for actual monitoring use case)
                //Thread.sleep(com.github.javafaker.Faker.instance().number().numberBetween(10,20));

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }


    }

    private void computeDelta() {

        double avgLatency=0;

        for (int i=0; i<numRecords; i++) {
            long writeTime = getCreationTime(uuidArray[i]);
            long now = getCurrentTime();
            long delta = now - writeTime;
            avgLatency += delta;
            //System.out.println("Delta: " + delta);
        }

        avgLatency = avgLatency/numRecords;
        System.out.println("----\nAverage latency (msec): " + avgLatency/1000000.0);

    }

    private long getCreationTime(UUID uuid) {

        long retval=0;

        PreparedStatement preparedStatement = session2.prepare(selectStatement);

        try {
            BoundStatement boundStatement = preparedStatement.bind(uuid);
            ResultSet results = session2.execute(boundStatement);
            for (Row row : results) {
                retval = row.getLong("nanosec");
                //System.out.println("Nanosec = " + retval);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return retval;
    }

    /**
     * Closes the session and the cluster.
     */
    private void close() {
        session1.close();
        cluster1.close();
        session2.close();
        cluster2.close();
    }
}
