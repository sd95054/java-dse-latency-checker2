package com.datastax.dse.demo;

import com.datastax.driver.core.*;
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy;
import com.datastax.driver.core.utils.UUIDs;
import com.datastax.driver.dse.auth.DseGSSAPIAuthProvider;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Properties;
import java.util.UUID;

public class LatencyChecker2 {

    //Statics
    private  static String[] CONTACT_POINTS_DC1;
    private  static String[] CONTACT_POINTS_DC2;
    private  static int PORT;
    private static ConsistencyLevel CONSISTENCY;

    // DC #1 configs
    private  String dc1_name;

    //DC #2 configs
    private  String dc2_name;

    //Parallel Threads/Tasks
    public int numTasks;

    //Number of records to write (and then read)
    private static int numRecords;

    private Cluster cluster1;
    private Session session1;
    private Cluster cluster2;
    private Session session2;
    private String keyspace;
    private String createTable;
    private String insertStatement;
    private String selectStatement;
    private PreparedStatement preparedStatement_loadData;
    private PreparedStatement preparedStatement_readData;
    private String authMethod;
    private String username;
    private String password;

    public void loadProperties() {
        Properties prop = new Properties();
        InputStream input = null;

        try {
            //Check if a Property File is passed in command line
            String propFile = System.getProperty("PROP_FILE");

            System.out.println("Property File Specified: " + propFile);

            if (propFile != null) {
                prop.load(new FileInputStream(propFile));
            }
            else {
                //Else load from default maven project location
                System.out.println("Trying to use default bundled property file...");
                input = LatencyChecker2.class.getResourceAsStream("/application.properties");

                // load a properties file
                prop.load(input);
            }

            // get the property values
            String str = prop.getProperty("CONTACT_POINTS_DC1");

            CONTACT_POINTS_DC1 = str.split(",");

            str = prop.getProperty("CONTACT_POINTS_DC2");

            CONTACT_POINTS_DC2  = str.split(",");

            PORT = Integer.parseInt(prop.getProperty("PORT"));
            CONSISTENCY = ConsistencyLevel.valueOf(prop.getProperty("CONSISTENCY"));
            dc1_name = prop.getProperty("dc1_name");
            dc2_name = prop.getProperty("dc2_name");
            numTasks = Integer.parseInt(prop.getProperty("numTasks"));
            numRecords = Integer.parseInt(prop.getProperty("numRecords"));
            keyspace = prop.getProperty("keyspace");
            createTable = prop.getProperty("createTable");
            insertStatement = prop.getProperty("insertStatement");
            selectStatement = prop.getProperty("selectStatement");
            authMethod = prop.getProperty("authMethod");
            if (authMethod.compareTo("username_password") == 0) {
                username = prop.getProperty("username");
                password = prop.getProperty("password");
            }

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
     */
    public void connect() {


        if (authMethod.compareTo("username_password") == 0) {

            //For DC #1
            cluster1 = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS_DC1).withPort(PORT)
                    .withCredentials(username.trim(), password.trim())
                    .withLoadBalancingPolicy(
                            DCAwareRoundRobinPolicy.builder()
                                    .withLocalDc(dc1_name)
                                    .build()
                    ).withQueryOptions(new QueryOptions().setConsistencyLevel(CONSISTENCY))
                    .build();

            //For DC #2
            cluster2 = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS_DC2).withPort(PORT)
                    .withCredentials(username.trim(), password.trim())
                    .withLoadBalancingPolicy(
                            DCAwareRoundRobinPolicy.builder()
                                    .withLocalDc(dc2_name)
                                    .build()
                    ).withQueryOptions(new QueryOptions().setConsistencyLevel(CONSISTENCY))
                    .build();

        }
        else if (authMethod.compareTo("kerberos") == 0) {

            cluster1 = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS_DC1).withPort(PORT)
                    .withAuthProvider(DseGSSAPIAuthProvider.builder().build())
                    .withLoadBalancingPolicy(
                            DCAwareRoundRobinPolicy.builder()
                                    .withLocalDc(dc1_name)
                                    .build()
                    ).withQueryOptions(new QueryOptions().setConsistencyLevel(CONSISTENCY))
                    .build();

            cluster2 = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS_DC2).withPort(PORT)
                    .withAuthProvider(DseGSSAPIAuthProvider.builder().build())
                    .withLoadBalancingPolicy(
                            DCAwareRoundRobinPolicy.builder()
                                    .withLocalDc(dc2_name)
                                    .build()
                    ).withQueryOptions(new QueryOptions().setConsistencyLevel(CONSISTENCY))
                    .build();
        }
        else { // No authentication in cluster

            //For DC #1
            cluster1 = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS_DC1).withPort(PORT)
                    .withLoadBalancingPolicy(
                            DCAwareRoundRobinPolicy.builder()
                                    .withLocalDc(dc1_name)
                                    .build()
                    ).withQueryOptions(new QueryOptions().setConsistencyLevel(CONSISTENCY))
                    .build();

            //For DC #2
            cluster2 = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS_DC2).withPort(PORT)
                    .withLoadBalancingPolicy(
                            DCAwareRoundRobinPolicy.builder()
                                    .withLocalDc(dc2_name)
                                    .build()
                    ).withQueryOptions(new QueryOptions().setConsistencyLevel(CONSISTENCY))
                    .build();

        }


        System.out.printf("Connected to cluster: %s%n", cluster1.getMetadata().getClusterName());

        session1 = cluster1.connect();

        System.out.printf("Connected to cluster: %s%n", cluster2.getMetadata().getClusterName());

        session2 = cluster2.connect();

    }

    public void createPreparedStatements() {
        preparedStatement_loadData = session1.prepare(insertStatement);
        preparedStatement_readData = session2.prepare(selectStatement);
        preparedStatement_loadData.enableTracing();
        preparedStatement_readData.enableTracing();
    }

    /**
     * Creates the schema (keyspace) and tables
     * for this example.
     */
    public void createSchema() {

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
     * LDH (Load/Write Data holder) multiple return values
     */
    public class loadData_holder {
        UUID[] uuidArray;
        double avgWriteLatency;
        String traceCoordinator;
        double traceDuration;

        loadData_holder(UUID[] uuidArray, double avgWriteLatency, String trCood, double trDur){
            this.uuidArray = uuidArray;
            this.avgWriteLatency = avgWriteLatency;
            this.traceCoordinator = trCood;
            this.traceDuration = trDur;
        }
    }


    /**
     * Inserts data into the tables.
     * Sets the Data TTL to 3600 seconds so table doesn't grow
     * arbitrarily large
     */

    public loadData_holder loadData() {

        long startTime=0, endTime=0;
        double avgWriteLatency=0;
        UUID[] uuidArray = new UUID[numRecords];
        loadData_holder loadResults;
        QueryTrace trace = null;

        for (int i=0; i<numRecords; i++) {

            try {
                UUID uuid = UUIDs.random();
                uuidArray[i] = uuid;

                startTime = getCurrentTime();

                BoundStatement boundStatement = preparedStatement_loadData.bind(uuid, startTime);

                ResultSet rs = session1.execute(boundStatement);


                //Without Prepared statement
                //String query1 = "INSERT INTO latency_check.kvp (id,nanosec) VALUES(" + uuid + ", " + startTime + ");";
                //
                //session1.execute(query1);


                endTime = getCurrentTime();

                avgWriteLatency = avgWriteLatency + (endTime - startTime)/1000000.0;

                //Tracing output
                ExecutionInfo executionInfo = rs.getExecutionInfo();
                trace = executionInfo.getQueryTrace();

               //Sleep for a random amount of time to TEST latency --- Remove Thread.sleep()
                // line below for actual monitoring use case)
                //Thread.sleep(com.github.javafaker.Faker.instance().number().numberBetween(10,20));

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        loadResults = new loadData_holder(uuidArray,
                            avgWriteLatency/numRecords,
                                trace.getCoordinator().toString(),
                                trace.getDurationMicros());

        //System.out.println("Average Write latency (msec): " + avgWriteLatency/numRecords);
        return loadResults;
    }

    //CDH (computeData_holder) holds LDH (Load/Write Data Holder) as well as Read information.
    public class computeDelta_holder {
        private loadData_holder ldh;
        private double avgReadLatency;
        private double avgTotalLatency;
        private String traceCoordinator;
        private double traceDuration;

        computeDelta_holder(loadData_holder ldh,double avgReadLatency,double avgTotalLatency, String trCood, double trDur){
            this.ldh = ldh;
            this.avgReadLatency = avgReadLatency;
            this.avgTotalLatency = avgTotalLatency;
            this.traceCoordinator = trCood;
            this.traceDuration = trDur;
        }

        public void printResults (int iteration, String threadName) {
            //Format (CSV)
            // Iteration,ThreadId,AvgWriteLatency,AvgReadLatency,AvgTotalLatency,AvgOverhead
            // ... WriteCoordinator, Write duration, Read Coordinator, Read Duration

            System.out.printf("%d, %s, %.2f, %.2f, %.2f, %.2f, %s, %.2f, %s, %.2f\n", iteration, threadName, ldh.avgWriteLatency,
                avgReadLatency, avgTotalLatency, (avgTotalLatency - ldh.avgWriteLatency - avgReadLatency),
                    ldh.traceCoordinator, ldh.traceDuration/1000., traceCoordinator, traceDuration/1000.);
        }
    }

    public computeDelta_holder computeDelta(loadData_holder inputValue) {

        long startTime=0, endTime=0;
        double avgReadLatency=0.0;
        double avgTotalLatency=0.0;
        UUID[] uuidArray = inputValue.uuidArray;
        QueryTrace trace = null;

        for (int i=0; i<numRecords; i++) {

            startTime = getCurrentTime();

            readData_holder rdh = getCreationTime(uuidArray[i]);

            //For Read Latency
            endTime = getCurrentTime();
            avgReadLatency = avgReadLatency + (endTime - startTime)/1000000.0;

            //For Write + Replication + Read delays
            long currentTime = getCurrentTime();
            avgTotalLatency = avgTotalLatency + (currentTime-rdh.writetime)/1000000.0;

            //System.out.printf("AvgTotalLatency:%.3f, currentTime:%d, writeTime:%d\n", avgTotalLatency, currentTime, writeTime);

            //Tracing output
            ExecutionInfo executionInfo = rdh.rs.getExecutionInfo();
            trace = executionInfo.getQueryTrace();
        }

        return new computeDelta_holder(inputValue,
                avgReadLatency/numRecords,
                avgTotalLatency/numRecords,
                 trace.getCoordinator().toString(),
                 trace.getDurationMicros());
    }

    private class readData_holder {
        long writetime;
        ResultSet rs;

        readData_holder(long writeTime, ResultSet rs){
            this.writetime = writeTime;
            this.rs = rs;
        }
    }

    private readData_holder getCreationTime(UUID uuid) {

        long time=0;
        ResultSet rs=null;

        try {
            for (int i=0; i<10; i++) {
                BoundStatement boundStatement = preparedStatement_readData.bind(uuid);
                rs= session2.execute(boundStatement);

                Iterator<Row> iter = rs.iterator();

                while (iter.hasNext()) {
                    Row row = iter.next();
                    time = row.getLong("nanosec");
                    //System.out.println("Nanosec = " + retval);
                }
                if (time != 0){
                    break;
                }
                else {
                    System.out.println("Need to iterate again until data arrives...");
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return new readData_holder(time, rs);
    }

    /**
     * Closes the session and the cluster.
     */
    public void close() {
        session1.close();
        cluster1.close();
        session2.close();
        cluster2.close();
    }
}
