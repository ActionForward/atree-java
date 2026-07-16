package com.actionforward.atree.jmh;

import com.actionforward.atree.ATree;
import com.actionforward.atree.Event;
import com.actionforward.atree.Expr;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cost of {@link ATree#match}, the hot path in production use: level-ordered bottom-to-top
 * traversal of an already-built index (paper Alg. 6). The tree is built once per trial; each
 * invocation matches the next event from a precomputed pool, so warmup/measurement iterations
 * exercise a realistic mix of hits and misses instead of the JIT specializing on one event.
 */
@State(Scope.Thread)
public class MatchBenchmark {

    @Param({"1000", "10000"})
    public int subscriptions;

    private static final int EVENT_POOL_SIZE = 256;

    private ATree<Integer> tree;
    private List<Event> eventPool;
    private int nextEvent;

    @Setup(Level.Trial)
    public void setUp() {
        tree = new ATree<>();
        Map<Integer, Expr> corpus = Corpus.subscriptions(subscriptions, 20260716L);
        for (Map.Entry<Integer, Expr> entry : corpus.entrySet()) {
            tree.subscribe(entry.getKey(), entry.getValue());
        }
        eventPool = Corpus.events(EVENT_POOL_SIZE, 42L);
    }

    @Benchmark
    public Set<Integer> match() {
        Event event = eventPool.get(nextEvent);
        nextEvent = (nextEvent + 1) % eventPool.size();
        return tree.match(event);
    }
}
