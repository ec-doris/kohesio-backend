package eu.ec.doris.kohesio.services;

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

import java.io.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class SPARQLQueryService {

    private static final Logger logger = LoggerFactory.getLogger(SPARQLQueryService.class);

    @Value("${kohesio.directory}")
    String location;

    public TupleQueryResult executeAndCacheQuery(String sparqlEndpoint, String query, int timeout) throws Exception {
        return this.executeAndCacheQuery(sparqlEndpoint, query, timeout, true);
    }

    public TupleQueryResult executeAndCacheQuery(String sparqlEndpoint, String query, int timeout, boolean cache) {
        logger.info(query);
        long start = System.nanoTime();
        File dir = new File(location + "/facet/cache/");

        System.out.println("The directory of cache: "+dir.getAbsolutePath());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // check if the query is cached
        if (dir.exists() && cache == true) {
            System.out.println(query.hashCode());
            SPARQLResultsJSONParser sparqlResultsJSONParser = new SPARQLResultsJSONParser();
            TupleQueryResultBuilder tupleQueryResultHandler = new TupleQueryResultBuilder();
            sparqlResultsJSONParser.setQueryResultHandler(tupleQueryResultHandler);
            try {
                sparqlResultsJSONParser.parseQueryResult(
                        new FileInputStream(location + "/facet/cache/" + query.hashCode()));
                long end = System.nanoTime();
                logger.info("Was cached " + (end - start) / 100000);
                return tupleQueryResultHandler.getQueryResult();
            } catch (QueryResultParseException e) {
                System.out.println("Wrong in cache timeout " + timeout);
            } catch (FileNotFoundException e) {
                System.out.println("Could not find file it was probably not cached");
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
            logger.info("Was NOT cached "+(end - start)/1000000);
            return tupleQueryResultHandler.getQueryResult();
        } catch(QueryEvaluationException e){
            logger.error("Malformed query ["+query+"]");
        } catch (QueryResultParseException e){
            System.out.println("To heavy timeout "+query+" --- "+timeout);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    public boolean executeBooleanQuery(String sparqlEndpoint, String query, int timeout) {
        Map<String, String> additionalHttpHeaders = new HashMap();
        additionalHttpHeaders.put("timeout", String.valueOf(timeout));
        SPARQLRepository repo = new SPARQLRepository(sparqlEndpoint);
        repo.setAdditionalHttpHeaders(additionalHttpHeaders);

        BooleanQuery booleanQuery = repo.getConnection().prepareBooleanQuery(query);
        boolean result = booleanQuery.evaluate();
        return result;
    }
}
