# java-dse-latency-checker2
Cassandra inter-DC replication latency check

Use Cases:

1. Measure the Overall Latency from a Client to write a small payload to a Table in Cassandra
2. Also measure Write Latency for the same operation in 1.
3. Also measure the Read Latency for the same operation in 1.
4. Enable the calculation of Replication and any other latency between the Read and Write operations.
5. Provide at least the following items in a Property File so it can be changed easily:
	- IP Addresses of hosts in DC1 and DC2
	- DC1 and DC2 Names
	- PORT
	- CONSISTENCY
	-numTasks
	-numRecords
	- Create KEYSPACE
	- Create TABLE
	- INSERT statement
	- SELECT statement

Design:

This project is about calculating Cassandra latencies between two Data Centers in the same cluster. 
We create a keyspace (“latency_check”) and table (“kip”) (if they do not exist already).
We create Write requests with a Timestamp value stored as part of the INSERTs
We read the data we wrote immediately and report on:
1. Write Latency
2. Read Latency
3. Overall Latency from the time a Write was initiated to the time the same data is read back into the same client.

Execution:

java -cp uber-java-dse-latency-checker2-1.0-SNAPSHOT.jar -DPROP_FILE=$PATH/app2.properties com.datastax.dse.demo.ExecutorsWrapper
