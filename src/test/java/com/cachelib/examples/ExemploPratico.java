package com.cachelib.examples;

import com.cachelib.builder.CacheBuilder;
import com.cachelib.core.Cache;
import com.cachelib.decorator.StatsCache;
import com.cachelib.eviction.EvictionPolicyFactory;
import com.cachelib.stats.CacheStats;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

public class ExemploPratico {

    public static void main(String[] args) {

        String politicaConfigurada = "lru";

        RelogioManual relogio = new RelogioManual();
        BancoSimulado banco = new BancoSimulado();
        CacheStats<Long, Produto> estatisticas = new CacheStats<>();

        Cache<Long, Produto> cacheBase = CacheBuilder.<Long, Produto>newBuilder()
                .maxEntries(3)
                .expireAfterWrite(Duration.ofMinutes(10))
                .clock(relogio)
                .evictionPolicy(EvictionPolicyFactory.create(politicaConfigurada))
                .addListener(estatisticas)
                .addListener((id, produto, motivo) ->
                        System.out.println("    [aviso] produto " + id + " saiu do cache: " + motivo))
                .build();

        Cache<Long, Produto> cache = new StatsCache<>(cacheBase, estatisticas);
        CatalogoProdutos catalogo = new CatalogoProdutos(cache, banco);

        System.out.println("== Clientes consultando o catálogo (política: " + politicaConfigurada + ") ==");
        catalogo.precoDe(1L);
        catalogo.precoDe(2L);
        catalogo.precoDe(3L);
        catalogo.precoDe(1L);
        catalogo.precoDe(4L);
        catalogo.precoDe(2L);
        catalogo.precoDe(1L);

        System.out.println("\n== Passam 11 minutos: os preços venceram ==");
        relogio.avancar(Duration.ofMinutes(11));
        catalogo.precoDe(1L);

        cache.put(9L, new Produto(9L, "Teclado", 150.0));
        System.out.println("get(9): " + cache.get(9L).isPresent());
        cache.remove(9L);
        System.out.println("após remove: " + cache.get(9L).isPresent());

        System.out.println("\n== Resumo ==");
        System.out.println("Consultas ao banco: " + banco.consultas());
        System.out.println("Estatísticas do cache: " + estatisticas);
        System.out.println("Economia: " + banco.consultas() + " consultas ao banco para "
                + (estatisticas.hits() + estatisticas.misses()) + " pedidos de preço.");
    }

    record Produto(long id, String nome, double preco) {
    }

    static class CatalogoProdutos {
        private final Cache<Long, Produto> cache;
        private final BancoSimulado banco;

        CatalogoProdutos(Cache<Long, Produto> cache, BancoSimulado banco) {
            this.cache = cache;
            this.banco = banco;
        }

        double precoDe(long id) {
            Optional<Produto> emCache = cache.get(id);
            if (emCache.isPresent()) {
                System.out.println("  produto " + id + ": veio do cache");
                return emCache.get().preco();
            }
            System.out.println("  produto " + id + ": NÃO estava no cache, consultando o banco");
            Produto produto = banco.buscar(id);
            cache.put(id, produto);
            return produto.preco();
        }
    }

    static class BancoSimulado {
        private int consultas = 0;

        Produto buscar(long id) {
            consultas++;
            return new Produto(id, "Produto " + id, 10.0 + id * 2.5);
        }

        int consultas() {
            return consultas;
        }
    }

    static class RelogioManual extends Clock {
        private Instant agora = Instant.parse("2026-01-01T10:00:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    }
}
