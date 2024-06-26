package eu.ec.doris.kohesio.services;

import com.opencsv.CSVWriter;

import org.eclipse.rdf4j.query.*;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryResults;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.impl.TupleQueryResultBuilder;
import org.eclipse.rdf4j.query.resultio.QueryResultParseException;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParser;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.*;
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
        logger.info("Executing given query: {}", query);
        long start = System.nanoTime();

        File dir = new File(location + "/facet/cache/");

        if (!dir.exists()) {
            dir.mkdirs();
        }
        // check if the query is cached
        if (dir.exists() && cache == true) {
            logger.debug("Query hashcode: " + String.valueOf(query.hashCode()));
            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);
            try {
                sparqlResultsJSONParser.parseQueryResult(
                        new FileInputStream(location + "/facet/cache/" + query.hashCode()));
                long end = System.nanoTime();
                logger.debug("Was cached " + (end - start) / 100000);
                return tupleQueryResultHandler.getQueryResult();
            } catch (QueryResultParseException e) {
                logger.debug("Wrong in cache timeout " + timeout);
            } catch (FileNotFoundException e) {
                logger.debug("Could not find file it was probably not cached");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        // execute and cache the query if not found before
        Map<String, String> additionalHttpHeaders = new HashMap();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));

        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);

        try {
            TupleQueryResult resultSet =
                    repo.getConnection().prepareTupleQuery(query).evaluate();
            FileOutputStream out = new FileOutputStream(location + "/facet/cache/" + query.hashCode());
            TupleQueryResultHandler writer = new SPARQLResultsJSONWriter(out);
            QueryResults.report(resultSet, writer);


            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);

            sparqlResultsJSONParser.parseQueryResult(
                    new FileInputStream(location + "/facet/cache/" + query.hashCode()));
            long end = System.nanoTime();
            logger.info("Was NOT cached " + query);
            logger.info("Time " + (end - start) / 1000000);


//            try (CSVWriter writer2 = new CSVWriter(new FileWriter("/Users/Dennis/Downloads/queries.csv", true))) {
//                String[] entry = {query, type, Long.toString((end - start)/ 1000000)};
//                writer2.writeNext(entry);
//            }


            return tupleQueryResultHandler.getQueryResult();
        } catch (QueryEvaluationException e) {
            logger.error("Query Evaluation Exception: [" + e.getMessage() + "]");
        } catch (QueryResultParseException e) {
            logger.error("To heavy timeout " + query + " --- " + timeout);
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return null;
    }

    public boolean executeBooleanQuery(String sparqlEndpoint, String query, int timeout) {
        Map<String, String> additionalHttpHeaders = new HashMap();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));
        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);

        BooleanQuery booleanQuery = repo.getConnection().prepareBooleanQuery(query);
        return booleanQuery.evaluate();
    }

    public void executeUpdateQuery(String sparqlEndpoint, String query, int timeout) {
        Map<String, String> additionalHttpHeaders = new HashMap();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));
        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint, sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);
        repo.getConnection()
                .prepareUpdate(query)
                .execute();
    }
}
