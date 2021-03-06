/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.benchmark;

import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.carrotsearch.junitbenchmarks.BenchmarkRule;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkHistoryChart;
import com.carrotsearch.junitbenchmarks.annotation.BenchmarkMethodChart;
import com.carrotsearch.junitbenchmarks.annotation.LabelType;
import com.google.common.collect.Iterables;
import io.crate.core.collections.Row;
import io.crate.operation.Paging;
import io.crate.operation.collect.CrateCollector;
import io.crate.operation.projectors.RowReceiver;
import io.crate.testing.CollectingRowReceiver;
import io.crate.testing.LuceneDocCollectorProvider;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.search.sort.SortBuilders;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@BenchmarkHistoryChart(filePrefix="benchmark-lucenedoccollector-history", labelWith = LabelType.CUSTOM_KEY)
@BenchmarkMethodChart(filePrefix = "benchmark-lucenedoccollector")
public class LuceneDocCollectorBenchmark extends BenchmarkBase {

    @Rule
    public TestRule benchmarkRun = RuleChain.outerRule(new BenchmarkRule()).around(super.ruleChain);

    public static boolean dataGenerated = false;
    public static final int NUMBER_OF_DOCUMENTS = 100_000;
    public static final int BENCHMARK_ROUNDS = 100;
    public static final int WARMUP_ROUNDS = 10;

    public final static ESLogger logger = Loggers.getLogger(LuceneDocCollectorBenchmark.class);
    private CollectingRowReceiver collectingRowReceiver = new CollectingRowReceiver();
    private LuceneDocCollectorProvider collectorProvider;

    public class PausingCollectingRowReceiver extends CollectingRowReceiver {

        @Override
        public boolean setNextRow(Row row) {
            upstream.pause();
            return true;
        }
    }

    @Override
    public byte[] generateRowSource() throws IOException {
        Random random = getRandom();
        byte[] buffer = new byte[32];
        random.nextBytes(buffer);
        return XContentFactory.jsonBuilder()
                .startObject()
                .field("areaInSqKm", random.nextFloat())
                .field("continent", new BytesArray(buffer, 0, 4).toUtf8())
                .field("countryCode", new BytesArray(buffer, 4, 8).toUtf8())
                .field("countryName", new BytesArray(buffer, 8, 24).toUtf8())
                .field("population", random.nextInt(Integer.MAX_VALUE))
                .endObject()
                .bytes().toBytes();
    }

    @Override
    public boolean generateData() {
        return true;
    }


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        collectorProvider = new LuceneDocCollectorProvider(CLUSTER);
    }

    @After
    public void cleanup() throws Exception {
        collectorProvider.close();
    }

    @Override
    protected void createTable() {
        execute("create table \"" + INDEX_NAME + "\" (" +
                " \"areaInSqKm\" float," +
                " capital string," +
                " continent string," +
                " \"continentName\" string," +
                " \"countryCode\" string," +
                " \"countryName\" string," +
                " north float," +
                " east float," +
                " south float," +
                " west float," +
                " \"fipsCode\" string," +
                " \"currencyCode\" string," +
                " languages string," +
                " \"isoAlpha3\" string," +
                " \"isoNumeric\" string," +
                " population integer" +
                ") clustered into 1 shards with (number_of_replicas=0)", new Object[0], true);
        client().admin().cluster().prepareHealth(INDEX_NAME).setWaitForGreenStatus().execute().actionGet();
    }

    @Override
    protected void doGenerateData() throws Exception {
        if (!dataGenerated) {

            logger.info("generating {} documents...", NUMBER_OF_DOCUMENTS);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (int i=0; i<4; i++) {
                executor.submit(new Runnable() {
                    @Override
                    public void run() {
                        int numDocsToCreate = NUMBER_OF_DOCUMENTS/4;
                        logger.info("Generating {} Documents in Thread {}", numDocsToCreate, Thread.currentThread().getName());
                        Client client = getClient(false);
                        BulkRequest bulkRequest = new BulkRequest();

                        for (int i=0; i < numDocsToCreate; i+=1000) {
                            bulkRequest.requests().clear();
                            try {
                                byte[] source = generateRowSource();
                                for (int j=0; j<1000;j++) {
                                    IndexRequest indexRequest = new IndexRequest(INDEX_NAME, "default", String.valueOf(i+j) + String.valueOf(Thread.currentThread().getId()));
                                    indexRequest.source(source);
                                    bulkRequest.add(indexRequest);
                                }
                                BulkResponse response = client.bulk(bulkRequest).actionGet();
                                assertFalse(response.hasFailures());
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(2L, TimeUnit.MINUTES);
            executor.shutdownNow();
            getClient(true).admin().indices().prepareFlush(INDEX_NAME).execute().actionGet();
            refresh(client());
            dataGenerated = true;
            logger.info("{} documents generated.", NUMBER_OF_DOCUMENTS);
        }
    }


    private CrateCollector createCollector(String stmt, RowReceiver downstream, Integer pageSizeHint, Object ... args) {
        return Iterables.getOnlyElement(collectorProvider.createCollectors(stmt, downstream, pageSizeHint, args));
    }


    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testLuceneDocCollectorOrderedWithScrollingPerformance() throws Exception{
        collectingRowReceiver.rows.clear();
        CrateCollector docCollector = createCollector(
                "SELECT continent FROM countries ORDER by continent",
                collectingRowReceiver,
                NUMBER_OF_DOCUMENTS / 2
        );
        docCollector.doCollect();
        collectingRowReceiver.result(); // call result to make sure there were no errors
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testLuceneDocCollectorOrderedWithScrollingStartStopPerformance() throws Exception{
        PausingCollectingRowReceiver rowReceiver = new PausingCollectingRowReceiver();
        CrateCollector docCollector = createCollector(
                "SELECT continent FROM countries ORDER BY continent",
                rowReceiver,
                NUMBER_OF_DOCUMENTS / 2
        );
        docCollector.doCollect();
        while (!rowReceiver.isFinished()) {
            rowReceiver.resumeUpstream(false);
        }
        rowReceiver.result();
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testLuceneDocCollectorOrderedWithoutScrollingPerformance() throws Exception{
        CollectingRowReceiver rowReceiver = new CollectingRowReceiver();
        CrateCollector docCollector = createCollector(
                "select continent from countries order by continent limit ?",
                rowReceiver,
                NUMBER_OF_DOCUMENTS);
        docCollector.doCollect();
        rowReceiver.result(); // call result to make sure there were no errors
    }


    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testLuceneDocCollectorOrderedWithoutScrollingStartStopPerformance() throws Exception{
        PausingCollectingRowReceiver rowReceiver = new PausingCollectingRowReceiver();
        CrateCollector docCollector = createCollector(
                "select continent from countries order by continent limit ?",
                rowReceiver,
                NUMBER_OF_DOCUMENTS);
        docCollector.doCollect();
        while (!rowReceiver.isFinished()) {
            rowReceiver.resumeUpstream(false);
        }
        rowReceiver.result();
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testLuceneDocCollectorUnorderedPerformance() throws Exception{
        CrateCollector docCollector = createCollector("SELECT continent FROM countries", collectingRowReceiver, NUMBER_OF_DOCUMENTS);
        docCollector.doCollect();
        collectingRowReceiver.result(); // call result to make sure there were no errors
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testLuceneDocCollectorUnorderedStartStopPerformance() throws Exception{
        PausingCollectingRowReceiver rowReceiver = new PausingCollectingRowReceiver();
        CrateCollector docCollector = createCollector("SELECT continent FROM countries", rowReceiver, NUMBER_OF_DOCUMENTS);
        docCollector.doCollect();
        while (!rowReceiver.isFinished()) {
            rowReceiver.resumeUpstream(false);
        }
        rowReceiver.result();
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testElasticsearchOrderedWithScrollingPerformance() throws Exception{
        int totalHits = 0;
        SearchResponse response = getClient(true).prepareSearch(INDEX_NAME).setTypes("default")
                                    .addField("continent")
                                    .addSort(SortBuilders.fieldSort("continent").missing("_last"))
                                    .setScroll("1m")
                                    .setSize(Paging.PAGE_SIZE)
                                    .execute().actionGet();
        totalHits += response.getHits().hits().length;
        while ( totalHits < NUMBER_OF_DOCUMENTS) {
            response = getClient(true).prepareSearchScroll(response.getScrollId()).setScroll("1m").execute().actionGet();
            totalHits += response.getHits().hits().length;
        }
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testElasticsearchOrderedWithoutScrollingPerformance() throws Exception{
        getClient(true).prepareSearch(INDEX_NAME).setTypes("default")
                .addField("continent")
                .addSort(SortBuilders.fieldSort("continent").missing("_last"))
                .setSize(NUMBER_OF_DOCUMENTS)
                .execute().actionGet();
    }

    @BenchmarkOptions(benchmarkRounds = BENCHMARK_ROUNDS, warmupRounds = WARMUP_ROUNDS)
    @Test
    public void testElasticsearchUnorderedWithoutScrollingPerformance() throws Exception{
        getClient(true).prepareSearch(INDEX_NAME).setTypes("default")
                .addField("continent")
                .setSize(NUMBER_OF_DOCUMENTS)
                .execute().actionGet();
    }
}
