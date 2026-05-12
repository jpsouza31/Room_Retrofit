# EV Pokedex - Offline First com Room + Retrofit

Aplicativo Android que replica os principios de uma EV Pokedex: consulta Pokemon na PokeAPI, calcula o EV yield a partir dos dados oficiais, salva tudo em cache local com Room e mantem a consulta funcionando offline. A UI trata o Room como fonte de verdade; Retrofit/OkHttp sao usados para sincronizar dados remotos para o banco local.

## Funcionalidades

- Lista paginada de Pokemon sincronizada da PokeAPI para o Room.
- Carregamento paginado de 20 Pokemon por vez, ordenado por numero da Pokedex.
- Busca por nome ou numero usando cache local primeiro; se um numero ou nome exato ainda nao estiver no cache, consulta a PokeAPI e salva o item encontrado no Room.
- Filtros por EV yield: HP, Attack, Defense, Sp. Atk, Sp. Def e Speed.
- Ordenacao sempre por numero da Pokedex, inclusive com filtros ativos.
- Tela de detalhe com sprite, tipos, EV yield e base stats.
- Cache local com Room para uso offline, incluindo os bytes do sprite.
- Badges e telas exibem dados lidos do Room assim que entram no cache.
- Tela de detalhe reage a atualizacoes do Room em tempo real, inclusive quando o WorkManager sincroniza em background.
- Pull-to-refresh para atualizar os dados da PokeAPI.
- Botao para limpar o cache local.
- Snackbar para erros e banner quando o cache offline esta sendo usado.

## Arquitetura

```text
Compose UI
    |
    | StateFlow
    v
ViewModel
    |
    | Flow / chamadas suspend
    v
Repository
    |-------------------|
    v                   v
Room DAO            Retrofit API
fonte de verdade    sincronizacao PokeAPI
```

O `PokedexRepository` e o ponto central da sincronizacao offline first:

1. A UI observa ou solicita dados do Room.
2. Se existir cache, os dados aparecem imediatamente.
3. Se o cache ainda estiver valido, o app encerra sem chamar a API.
4. Se estiver offline, lista, filtros, busca e detalhe continuam usando o que ja existe no Room.
5. Se estiver online, Retrofit busca a contagem completa e carrega paginas de 20 Pokemon conforme o usuario rola a lista.
6. A resposta da PokeAPI e convertida para entidade e salva no Room. A UI nunca recebe dados diretamente da rede: um Flow do Room esta sempre ativo no ViewModel e e a unica fonte de atualizacao da lista, inclusive durante paginacao e sincronizacao em background.
7. Pull-to-refresh nunca apaga o cache antes da rede responder; se a sincronizacao falhar, o app mantem os dados locais.

Retrofit define os endpoints da PokeAPI. OkHttp executa as chamadas HTTP, aplica timeouts e registra logs basicos de rede. Nenhum composable faz download direto de dados ou imagens; sprites exibidos na UI vem dos bytes persistidos no Room.

O cache e considerado valido por 24 horas, ja que dados de Pokemon mudam pouco.

## Estrutura

```text
app/src/main/java/com/app/room_retrofit/
├── MainActivity.kt
├── MyApplication.kt
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt
│   │   ├── dao/PokemonDao.kt
│   │   └── entity/PokemonEntity.kt
│   ├── remote/
│   │   ├── api/PokeApiService.kt
│   │   └── dto/PokemonDto.kt
│   ├── repository/PokedexRepository.kt
│   └── sync/
│       └── PokedexSyncWorker.kt
├── di/AppModule.kt
├── domain/model/Pokemon.kt
├── presentation/
│   ├── navigation/AppNavigation.kt
│   ├── ui/
│   │   ├── PokedexScreen.kt
│   │   └── PokemonDetailScreen.kt
│   └── viewmodel/
│       ├── PokedexPaginator.kt
│       ├── PokedexViewModel.kt
│       └── PokemonDetailViewModel.kt
└── util/
    └── Resource.kt
```

## Setup

Nao e necessario configurar chave de API. A PokeAPI e publica.

Compile o projeto:

```bash
./gradlew :app:compileDebugKotlin
```

Gere o APK de debug:

```bash
./gradlew :app:assembleDebug
```

## Tecnologias

- Kotlin
- Jetpack Compose
- Navigation Compose
- Material 3
- ViewModel + StateFlow
- Coroutines + Flow
- Room
- Retrofit
- OkHttp
- Hilt
- WorkManager
- KSP

## Endpoints usados

- `GET https://pokeapi.co/api/v2/pokemon?limit=20&offset={offset}`
- `GET https://pokeapi.co/api/v2/pokemon/{nameOrId}`
- URLs de sprites retornadas em `sprites.front_default` ou `sprites.other.official-artwork.front_default`

Os valores de EV yield vem do campo `stats[].effort` retornado pelo endpoint de detalhe de cada Pokemon.
O sprite e baixado durante a sincronizacao e salvo como bytes no Room. O banco local se chama `pokedex_cache.db` e o schema atual do Room esta na versao 2.

## Permissoes

Declaradas em `app/src/main/AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

## Comportamento de cache

| Condicao | Resultado |
|---|---|
| Online com cache valido | Exibe cache e nao chama a API |
| Online com cache vencido | Busca PokeAPI, atualiza Room e exibe dados relidos do cache |
| Offline com cache | Exibe cache e mostra aviso offline |
| Offline sem cache | Exibe erro de conexao |
| Falha na API com cache | Mantem cache e mostra erro |
| Pull-to-refresh | Tenta sincronizar com a API sem descartar o cache existente |
| Busca offline | Procura por nome ou numero no Room |
| Busca online por numero ou nome exato sem resultado local | Busca esse Pokemon na PokeAPI, salva no Room e relê do Room. Exemplo: pesquisar `1025` ou `pecharunt` depois de carregar poucas paginas adiciona esse Pokemon ao cache se a API responder com sucesso |
| Busca online textual com correspondencias locais | Exibe as correspondencias locais; nao usa um endpoint remoto de busca parcial para completar outros possiveis resultados |
| Filtro offline | Filtra os Pokemon salvos no Room |
| Limpar cache | Remove os Pokemon salvos no Room e zera o estado da tela |
| Sync em background (6h) | Atualiza as paginas dentro do prefixo contiguo da Pokedex ja cacheado; itens esparsos buscados individualmente podem ficar fora desse ciclo |
