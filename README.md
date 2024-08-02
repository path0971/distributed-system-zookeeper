First of all, to use these codes and algorithms, you should match the versions of jdk, maven-compiler-plugin, zookeeper for pom.xml.

I 've applied zookeeper 3.4.12 for the distributed system.

For notice : <For setting up the zookeeper, you should write down the path/to/your/dataDIR/ and open up the customed port and I used the port 2181 for the zookeeper server.>

Also, to rectify errors while configuring zookeeper env't, I added "flush" in the code to get the messages right away from the buffers.
