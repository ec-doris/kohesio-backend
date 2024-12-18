package eu.ec.doris.kohesio.services;

import com.opencsv.CSVWriter;

import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.impl.TupleQueryResultBuilder;
import org.eclipse.rdf4j.query.resultio.BooleanQueryResultParser;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLBooleanJSONParser;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLBooleanJSONWriter;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParser;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SPARQLQueryService {

    private static final Logger logger = LoggerFactory.getLogger(SPARQLQueryService.class);
    private static final Pattern spaceCleaner = Pattern.compile("[ \\t\\n\\x0B\\f\\r]+");

    @Value("${kohesio.directory}")
    String location;

    public TupleQueryResult executeAndCacheQuery(String sparqlEndpoint, String query, int timeout, String type) throws Exception {
        return this.executeAndCacheQuery(sparqlEndpoint, query, timeout, true, type);
    }

    public TupleQueryResult executeAndCacheQuery(String sparqlEndpoint, String query, int timeout, boolean cache, String type) {
        query = spaceCleaner.matcher(query).replaceAll(" ");
        logger.info("Executing given of hash {}, query: {}", query.hashCode(), query);
        long start = System.nanoTime();

        File dir = new File(location + "/facet/cache/");

        if (!dir.exists()) {
            dir.mkdirs();
        }
        // check if the query is cached
        if (dir.exists() && cache) {
            logger.debug("Query hashcode: {}", query.hashCode());
            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);
            try {
                sparqlResultsJSONParser.parseQueryResult(
                        new FileInputStream(location + "/facet/cache/" + query.hashCode()));
                long end = System.nanoTime();
                logger.debug("Was cached {} in file {}", (end - start) / 100000, query.hashCode());
                return tupleQueryResultHandler.getQueryResult();
            } catch (QueryResultParseException e) {
                logger.debug("Wrong in cache timeout {}", timeout);
            } catch (FileNotFoundException e) {
                logger.debug("Could not find file it was probably not cached");
            } catch (IOException e) {
//                e.printStackTrace();
                logger.error("Error: {}", e.getMessage());
            }
        }
        // execute and cache the query if not found before
        Map<String, String> additionalHttpHeaders = new HashMap<>();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));

        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);

        try {
            TupleQueryResult resultSet = repo.getConnection().prepareTupleQuery(query).evaluate();
            logger.info("tupleQueryResult: {}", resultSet);
            FileOutputStream out = new FileOutputStream(location + "/facet/cache/" + query.hashCode());
            TupleQueryResultHandler writer = new SPARQLResultsJSONWriter(out);
            QueryResults.report(resultSet, writer);


            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);

            sparqlResultsJSONParser.parseQueryResult(
                    Files.newInputStream(Paths.get(location + "/facet/cache/" + query.hashCode()))
            );
            long end = System.nanoTime();
            logger.info("Was NOT cached {}", query);
            logger.info("Time {}", (end - start) / 1000000);


//            try (CSVWriter writer2 = new CSVWriter(new FileWriter("/Users/Dennis/Downloads/queries.csv", true))) {
//                String[] entry = {query, type, Long.toString((end - start)/ 1000000)};
//                writer2.writeNext(entry);
//            }


            return tupleQueryResultHandler.getQueryResult();
        } catch (QueryEvaluationException e) {
            logger.error("Query Evaluation Exception: [{}]", e.getMessage());
        } catch (QueryResultParseException e) {
            logger.error("To heavy timeout {} --- {}", query, timeout);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public boolean executeBooleanQuery(String sparqlEndpoint, String query, int timeout) {
        return executeBooleanQuery(sparqlEndpoint, query, true, timeout);
    }

    public boolean executeBooleanQuery(String sparqlEndpoint, String query, boolean cache, int timeout) {

        query = spaceCleaner.matcher(query).replaceAll(" ");
        logger.info("Executing given of hash {}, query: {}", query.hashCode(), query);
        File dir = new File(location + "/facet/cache/");
        dir.mkdirs();

        Map<String, String> additionalHttpHeaders = new HashMap<>();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));
        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);

        String cachePathString = dir.getPath() + "/" + query.hashCode();
        Path cachePath = Paths.get(cachePathString);
        if (cachePath.toFile().exists() && cache) {
            try {
                SPARQLBooleanJSONParser booleanQueryResultParser = new SPARQLBooleanJSONParser();
                QueryResultCollector result = new QueryResultCollector();
                booleanQueryResultParser.parseQueryResult(
                        Files.newInputStream(cachePath)
                );
                booleanQueryResultParser.setQueryResultHandler(result);
                return result.getHandledBoolean();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // execute and cache the query if not found before
        try {
            OutputStream out = Files.newOutputStream(cachePath);
            SPARQLBooleanJSONWriter writer = new SPARQLBooleanJSONWriter(out);
            BooleanQueryResultParser booleanQueryResultParser = new SPARQLBooleanJSONParser();
            QueryResultCollector result = new QueryResultCollector();
            writer.write(repo.getConnection().prepareBooleanQuery(query).evaluate());
            booleanQueryResultParser.setQueryResultHandler(result);
            booleanQueryResultParser.parseQueryResult(
                    Files.newInputStream(cachePath)
            );
            return result.getBoolean();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public void executeUpdateQuery(String sparqlEndpoint, String query, int timeout) {
        Map<String, String> additionalHttpHeaders = new HashMap<>();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));
        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint, sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);
        repo.getConnection()
                .prepareUpdate(query)
                .execute();
    }
}
