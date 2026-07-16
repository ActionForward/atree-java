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
 * Cost of releasing subscriptions (Alg. 5): every key is unsubscribed, walking each root's
 * parent-less path down to nodes that become unreferenced. The tree is rebuilt fresh in
 * {@code @Setup(Level.Invocation)}, which JMH excludes from the measured time, so only the
 * unsubscribe batch itself is timed.
 */
@State(Scope.Thread)
public class UnsubscribeBenchmark {

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
        for (Map.Entry<Integer, Expr> entry : corpus.entrySet()) {
            tree.subscribe(entry.getKey(), entry.getValue());
        }
    }

    @Benchmark
    public void unsubscribeAll(Blackhole blackhole) {
        for (Integer key : corpus.keySet()) {
            blackhole.consume(tree.unsubscribe(key));
        }
    }
}
