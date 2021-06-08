# netty-https-proxy

An application to accept http requests and forward them via HTTPS. You will get the response of the target server.

Build JAR with deps: mvn clean compile assembly:single

Sample usage: java -jar HttpsProxy-1.0-SNAPSHOT-jar-with-dependencies.jar 9000 testas.com 8443

Application will listen port 9000 and redirect to https://testas.com:8443

So if you request POST http://localhost:9000/as2/receive it will redirect to https://testas.com:8443/as2/receive


