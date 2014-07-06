filesetsync
===========

Java client-server based rsync-like synchronization of files. Suited for large amounts of files and unattended clients.

The server is a standard Java web application. The synchronization protocol is HTTP-based. Only complete files are synchronized (no partial updates like rsync). Unlike rsync, the file list is cached by the client so data traffic is minimized when synchronizing millions of files. Like rsync, no round-trip is required for streaming multiple small files.

## filesetsync-client

### Running
To run outside of maven:
```
cd filesetsync-client
mvn clean install
mvn dependency:copy-dependencies
java -jar target/filesetsync-client-1.0-SNAPSHOT.jar
```
Distribute "filesetsync-client-1.0-SNAPSHOT.jar" and the "dependency" directory.

To test the Dutch localization run with OS locale set to Dutch or on Linux run:
```LC_ALL=nl_NL.UTF-8 java -jar target/filesetsync-client-1.0-SNAPSHOT.jar```

### Configuration

The application looks for a file named ```filesetsync-config.xml``` in the directory the executable JAR file is in (or the directory containing the root package). Format:

```
<sync>
    <plugins>
        <plugin class="some.package.PluginClass"/>
    </plugins>

    <!-- directory to save working data relative to path of JAR file -->
    <var>./var</var>

    <!-- Set global sytem properties -->
    <globals>
        <property name="chunksize" value="10M"/>
        <property name="timeoutMillis" value="10000"/>
        
        <!-- Send HTTP request header -->
        <property name="header.x-something" value="hello"/>
        <!-- Send HTTP request header with value of property -->
        <property name="header.x-variable" value="${some.property}"/>
    </globals>
            
    <filesets>
        <fileset name="myfileset" server="https://localhost/sync/">
            <!-- only 'once', 'hourly' or 'daily' supported for now -->
            <!-- all jobs with schedule 'once' are run first (in order) -->
            <!-- then the hourly and daily jobs are scheduled to run
                 according to the last start time -->
            <schedule>once</schedule>
            <direction>download</direction>
            <remote>myfileset</remote>
            <local>/tmp/myfileset</local>
            <!--interceptor>some.package.SomeInterceptor</interceptor-->
            <properties>
                <property name="fileset.specific.property" value="true"/>
            </properties>
        </fileset>
    </filesets>              
</sync>
```

