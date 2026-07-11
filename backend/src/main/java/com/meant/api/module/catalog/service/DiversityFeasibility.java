package com.meant.api.module.catalog.service;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Small deterministic max-flow solver for correlated source/merchant capacity constraints. */
@Component
class DiversityFeasibility {

    int maximum(
            List<ProductDiversityPolicy.Attribution> candidates,
            int sourceCap,
            int merchantCap,
            Map<String, Integer> sourceUsed,
            Map<String, Integer> merchantUsed
    ) {
        List<String> sources = candidates.stream().map(ProductDiversityPolicy.Attribution::source).distinct().sorted().toList();
        List<String> merchants = candidates.stream().map(ProductDiversityPolicy.Attribution::merchant).distinct().sorted().toList();
        int sourceOffset = 1;
        int merchantOffset = sourceOffset + sources.size();
        int sink = merchantOffset + merchants.size();
        int[][] capacity = new int[sink + 1][sink + 1];
        Map<String, Integer> sourceNode = nodes(sources, sourceOffset);
        Map<String, Integer> merchantNode = nodes(merchants, merchantOffset);
        sources.forEach(source -> capacity[0][sourceNode.get(source)] = Math.max(0, sourceCap - sourceUsed.getOrDefault(source, 0)));
        merchants.forEach(merchant -> capacity[merchantNode.get(merchant)][sink] = Math.max(0, merchantCap - merchantUsed.getOrDefault(merchant, 0)));
        candidates.forEach(candidate -> capacity[sourceNode.get(candidate.source())][merchantNode.get(candidate.merchant())]++);
        return maximumFlow(capacity, 0, sink);
    }

    private Map<String, Integer> nodes(List<String> keys, int offset) {
        Map<String, Integer> nodes = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) nodes.put(keys.get(index), offset + index);
        return nodes;
    }

    private int maximumFlow(int[][] capacity, int source, int sink) {
        int flow = 0;
        int[] parent = new int[capacity.length];
        while (path(capacity, source, sink, parent)) {
            int increment = Integer.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) increment = Math.min(increment, capacity[parent[node]][node]);
            for (int node = sink; node != source; node = parent[node]) {
                capacity[parent[node]][node] -= increment;
                capacity[node][parent[node]] += increment;
            }
            flow += increment;
        }
        return flow;
    }

    private boolean path(int[][] capacity, int source, int sink, int[] parent) {
        java.util.Arrays.fill(parent, -1);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        parent[source] = source;
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next = 0; next < capacity.length; next++) {
                if (parent[next] == -1 && capacity[current][next] > 0) {
                    parent[next] = current;
                    if (next == sink) return true;
                    queue.addLast(next);
                }
            }
        }
        return false;
    }
}
