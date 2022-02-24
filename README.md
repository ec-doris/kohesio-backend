# kohesio-backend

The kohesio backend contains a set offers the APIs to serve (<https://kohesio.ec.europa.eu>). It offers APIs to:

- search over projects
- search over beneficiaries
- explore a Map
- serve the facets of the search
- find projects nearby based on the IP

All REST API requests are transformed into SPARQL queries serving the requested content. 

To run from cli:

- mvn clean package
- java -jar target/kohesio-backend-1.0-SNAPSHOT.jar

To run using docker:

- Build the image: `docker build -t kohesio-backend .`
- Run the container: `docker run -p 5678:5678 --name kohesio-api kohesio-backend`
- Stop: `docker stop kohesio-api` - Start: `docker start kohesio-api` (kohesio-api is 
the name of the container)
