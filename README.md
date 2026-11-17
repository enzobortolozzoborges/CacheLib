# CacheLib

Biblioteca Java de cache em memória, desenvolvida para a disciplina de Design Patterns (Bacharelado em Sistemas de Informação, PUCPR, 2º semestre de 2026).

O cache roda no mesmo processo da aplicação cliente e oferece:

- expiração por tempo de vida (TTL), padrão para o cache ou próprio para cada entrada;
- limite por quantidade de entradas ou por soma de pesos;
- políticas de evicção trocáveis (LRU e FIFO prontas, e as criadas pelo cliente);
- troca da política em um cache já em uso;
- avisos de remoção com o motivo, entregues de forma síncrona ou assíncrona;
- estatísticas de acertos, erros e remoções.

## Alunos

- Davi Batista Amaral
- Enzo Bortolozzo Borges
- Henrique Fugikawa Abe

## Requisitos

- Java 17 ou superior
- Maven 3.8 ou superior

## Instalação

```bash
git clone [URL do repositório]
cd CacheLib
mvn install
```

No projeto cliente, adicione a dependência:

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>CacheLib</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Uso básico

Sem nenhuma configuração, o builder cria um cache LRU de 100 entradas, sem TTL e com notificação síncrona:

```java
import com.cachelib.builder.CacheBuilder;
import com.cachelib.core.Cache;

Cache<String, String> cache = CacheBuilder.<String, String>newBuilder().build();

cache.put("usuario:1", "Ana");
cache.get("usuario:1");        // Optional[Ana]
cache.get("usuario:2");        // Optional.empty()
cache.remove("usuario:1");     // true
cache.size();                  // 0
```

## Configuração

Todas as opções são encadeadas e opcionais:

```java
import java.time.Duration;
import com.cachelib.builder.CacheBuilder;
import com.cachelib.core.Cache;
import com.cachelib.eviction.FifoEvictionPolicy;

Cache<Long, Produto> cache = CacheBuilder.<Long, Produto>newBuilder()
        .maxEntries(500)
        .expireAfterWrite(Duration.ofMinutes(5))
        .evictionPolicy(new FifoEvictionPolicy<>())
        .addListener((id, produto, motivo) -> System.out.println(id + " saiu: " + motivo))
        .build();
```

| Opção | Padrão | Descrição |
|---|---|---|
| `maxEntries(int)` | 100 | Limite pela quantidade de entradas (cada uma tem peso 1). |
| `maxWeight(long, ToLongBiFunction)` | — | Limite pela soma dos pesos calculados por uma função, por exemplo o tamanho de um texto. |
| `expireAfterWrite(Duration)` | sem expiração | TTL padrão, contado a partir da escrita. |
| `evictionPolicy(EvictionPolicy)` | LRU | Regra de quem sai quando o cache lota. |
| `notificationStrategy(NotificationStrategy)` | síncrona | Como os avisos de remoção são entregues. |
| `addListener(CacheListener)` | — | Registra quem quer ser avisado de remoções. Pode repetir. |
| `clock(Clock)` | relógio do sistema | Relógio usado para o TTL. Útil em testes. |

Valores inválidos lançam `IllegalArgumentException`, por exemplo `maxEntries(0)` ou `expireAfterWrite(Duration.ZERO)`.

### Operações do cache

```java
cache.put("chave", valor);                       // usa o TTL padrão
cache.put("chave", valor, Duration.ofSeconds(30)); // TTL próprio desta entrada
cache.put("chave", valor, null);                 // esta entrada não expira
cache.get("chave");                              // Optional com o valor, ou vazio
cache.remove("chave");                           // true se existia
cache.size();                                    // quantidade de entradas
cache.setEvictionPolicy(new LruEvictionPolicy<>()); // troca a política em uso
```

Ao substituir o valor de uma chave já existente, nenhum aviso de remoção é enviado, porque não houve remoção.

Um item com peso maior que o limite do cache é recusado com `IllegalArgumentException`.

### Limite por peso

```java
Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
        .maxWeight(1_000_000, (chave, valor) -> valor.length())
        .build();
```

Quando um item novo não cabe, o cache remove itens (começando pela vítima indicada pela política) até liberar espaço. Itens já vencidos são removidos antes de qualquer item válido.

### Políticas de evicção

| Classe | Regra |
|---|---|
| `LruEvictionPolicy` (padrão) | Remove a entrada usada há mais tempo. |
| `FifoEvictionPolicy` | Remove a entrada inserida há mais tempo, sem considerar leituras. |

Ao trocar a política com `setEvictionPolicy`, as entradas atuais entram na nova política na ordem em que foram inseridas. O histórico de uso anterior não é preservado.

### Motivos de remoção

| `RemovalCause` | Quando ocorre |
|---|---|
| `EXPIRED` | O TTL da entrada terminou. |
| `EVICTED` | A política escolheu a entrada para liberar espaço. |
| `EXPLICIT` | O cliente chamou `remove`. |

Entradas vencidas só saem do cache quando são lidas ou quando o cache precisa de espaço. Este comportamento é de expiração preguiçosa.

### Estratégias de notificação

```java
import com.cachelib.listener.Notifications;
import java.util.concurrent.Executors;

// Síncrona (padrão): os listeners rodam na mesma thread, em ordem.
.notificationStrategy(Notifications.synchronous())

// Assíncrona: os avisos são entregues em outra thread e o cache não espera.
.notificationStrategy(Notifications.asynchronous(Executors.newSingleThreadExecutor()))
```

Nas duas, um listener que lança exceção não impede os demais: o erro é registrado no log.

As notificações são entregues fora da região protegida pelo lock, então um listener pode usar o próprio cache sem risco de travamento.

## Estatísticas

```java
import com.cachelib.decorator.StatsCache;
import com.cachelib.stats.CacheStats;

CacheStats<String, String> stats = new CacheStats<>();

Cache<String, String> base = CacheBuilder.<String, String>newBuilder()
        .maxEntries(3)
        .addListener(stats)          // conta evicções, expirações e remoções explícitas
        .build();

Cache<String, String> cache = new StatsCache<>(base, stats);   // conta acertos e erros

stats.hits();
stats.misses();
stats.hitRate();          // entre 0.0 e 1.0
stats.evictions();
stats.expirations();
stats.explicitRemovals();
```

O `CacheStats` precisa ser registrado tanto como listener (para remoções) quanto envolvendo o cache com `StatsCache` (para acertos e erros).

## Extensão: criando as próprias classes

A biblioteca pode ser estendida sem alterar o código dela. São três pontos de extensão.

### Política de evicção própria

```java
import com.cachelib.eviction.EvictionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class RandomEvictionPolicy<K> implements EvictionPolicy<K> {

    private final List<K> keys = new ArrayList<>();
    private final Random random = new Random();

    @Override
    public void onInsert(K key) {
        if (!keys.contains(key)) {
            keys.add(key);
        }
    }

    @Override
    public void onAccess(K key) {
    }

    @Override
    public void onRemove(K key) {
        keys.remove(key);
    }

    @Override
    public Optional<K> selectVictim() {
        if (keys.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(keys.get(random.nextInt(keys.size())));
    }
}

Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
        .maxEntries(100)
        .evictionPolicy(new RandomEvictionPolicy<>())
        .build();
```

### Política registrada pelo nome

A política pode ser criada a partir de um nome, útil quando a escolha vem de um arquivo de configuração:

```java
import com.cachelib.eviction.EvictionPolicyFactory;

EvictionPolicyFactory.register("random", RandomEvictionPolicy::new);

Cache<String, String> cache = CacheBuilder.<String, String>newBuilder()
        .evictionPolicy(EvictionPolicyFactory.create("random"))
        .build();
```

As políticas `lru` e `fifo` já vêm registradas. Um nome desconhecido lança `IllegalArgumentException`.

### Listener próprio

```java
import com.cachelib.listener.CacheListener;
import com.cachelib.listener.RemovalCause;

public class ContadorDeEvicoes implements CacheListener<String, String> {

    private int evicoes = 0;

    @Override
    public void onRemoval(String chave, String valor, RemovalCause motivo) {
        if (motivo == RemovalCause.EVICTED) {
            evicoes++;
        }
    }

    public int evicoes() {
        return evicoes;
    }
}
```

### Estratégia de notificação própria

```java
import com.cachelib.listener.NotificationStrategy;
import com.cachelib.listener.RemovalEvent;
import com.cachelib.listener.CacheListener;
import java.util.List;

NotificationStrategy<String, String> registrarAuditoria = (eventos, listeners) -> {
    for (RemovalEvent<String, String> evento : eventos) {
        auditoria.gravar(evento.key(), evento.cause());
    }
};
```

## Exemplo completo

O arquivo `src/test/java/com/cachelib/examples/ExemploPratico.java` simula o catálogo de uma loja online, em que cada consulta ao banco custa tempo. Ele usa a Factory para escolher a política a partir de uma configuração, o Builder para montar o cache, o Decorator para contar acessos, e o Observer para avisar cada produto que sai. Para executar, rode o método `main` pelo IntelliJ.

## Organização do código

```
com.cachelib
├── core         Cache (contrato público), MemoryCache (implementação), CacheConfig
├── builder      CacheBuilder (montagem e valores padrão)
├── eviction     EvictionPolicy, LruEvictionPolicy, FifoEvictionPolicy, EvictionPolicyFactory
├── listener     CacheListener, RemovalCause, RemovalEvent, NotificationStrategy, Notifications
├── decorator    StatsCache
└── stats        CacheStats
```

O cliente depende apenas de `Cache`, `CacheBuilder`, `EvictionPolicy`, `CacheListener`, `NotificationStrategy` e `CacheStats`. As classes internas do `core` podem mudar sem afetar o código do cliente.

## Padrões de projeto utilizados

| Padrão | Aluno | Onde está | Problema que resolve |
|---|---|---|---|
| Strategy | 1 | `eviction/EvictionPolicy` e implementações; `MemoryCache.setEvictionPolicy` | Trocar a regra de evicção sem alterar o cache, inclusive em um cache em uso. |
| Decorator | 1 | `decorator/StatsCache`, `stats/CacheStats` | Medir acertos e erros sem alterar o cache. Quem não precisa de estatísticas não paga o custo. |
| Observer | 2 | `listener/CacheListener`, `listener/RemovalCause`, `listener/NotificationStrategy`, `MemoryCache.notifier` | Reagir às remoções sem o cache conhecer cada reação. |
| Template Method | 2 | `MemoryCache.Operation` | Garantir que toda operação siga o mesmo roteiro (lock, passo, unlock, notificação) sem repetir o código. |
| Builder | 3 | `builder/CacheBuilder`, `core/CacheConfig` | Configurar o cache com opções opcionais e validação, sem construtor com muitos parâmetros. |
| Factory | 3 | `eviction/EvictionPolicyFactory` | Criar políticas pelo nome, inclusive as registradas pelo cliente. |

## Concorrência

Todo o estado do cache é protegido por um lock interno privado. Dentro dele ficam apenas as alterações e consultas à estrutura de dados. Os avisos de remoção são coletados dentro do lock e entregues depois que ele é liberado.

Com um único lock, as operações do cache se revezam. Isso garante consistência, mas limita o paralelismo. Uma melhoria futura seria dividir os dados em partes com locks separados, como faz o `ConcurrentHashMap`.

## Testes

```bash
mvn test
```

Os testes estão em `src/test/java` e cobrem:

- leitura e escrita, políticas LRU e FIFO, expiração por TTL e prioridade das entradas vencidas;
- TTL próprio por entrada, limite por peso e recusa de itens maiores que o limite;
- troca de política em um cache em uso;
- avisos de remoção, inclusive a ausência de aviso na substituição de valor;
- listeners com erro, que não bloqueiam os demais;
- notificação assíncrona;
- estatísticas, a fábrica de políticas e o uso concorrente por várias threads.

## Limitações conhecidas

- Trocar a política em um cache em uso não preserva o histórico de uso.
- Na notificação assíncrona, a ordem entre avisos de threads diferentes não é garantida.
- A expiração é preguiçosa: entradas vencidas só saem quando são lidas ou quando o cache precisa de espaço.
- A Factory usa um registro estático, então um nome registrado vale para toda a aplicação.