package com.actionforward.atree.jmh;

import com.actionforward.atree.ATree;
import com.actionforward.atree.Expr;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Map;

/**
 * Cost of building an index from scratch: {@link ATree#subscribe} for every expression in the
 * corpus, which drives reorganization (Alg. 2), recursive insertion (Alg. 4) and self-adjustment
 * (Alg. 3) for each one. Each invocation subscribes into a fresh, empty tree, so the reported
 * average time is for the whole batch, not a single subscribe call.
 */
@State(Scope.Thread)
public class IndexBuildBenchmark {

    @Param({"1000", "10000"})
    public int subscriptions;

    private Map<Integer, Expr> corpus;
    private ATree<Integer> tree;

    @Setup(Level.Trial)
    public void setUpCorpus() {
        corpus = Corpus.subscriptions(subscriptions, 20260716L);
    }

    @Setup(Level.Invocation)
    public void setUpTree() {
        tree = new ATree<>();
    }

    @Benchmark
    public void buildIndex(Blackhole blackhole) {
        for (Map.Entry<Integer, Expr> entry : corpus.entrySet()) {
            tree.subscribe(entry.getKey(), entry.getValue());
        }
        blackhole.consume(tree.nodeCount());
    }
}
