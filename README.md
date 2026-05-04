# EV Pokedex - PokeAPI + Room + Retrofit

Aplicativo Android que replica os principios de uma EV Pokedex: consulta Pokemon na PokeAPI, calcula o EV yield a partir dos dados oficiais, salva tudo em cache local com Room e mantem a consulta funcionando offline.

## Funcionalidades

- Lista completa de Pokemon retornada pela PokeAPI.
- Carregamento paginado de 20 Pokemon por vez, ordenado por numero da Pokedex.
- Busca por nome ou numero usando cache local primeiro e PokeAPI como sincronizacao.
- Filtros por EV yield: HP, Attack, Defense, Sp. Atk, Sp. Def e Speed.
- Ordenacao sempre por numero da Pokedex, inclusive com filtros ativos.
- Tela de detalhe com sprite, tipos, EV yield e base stats.
- Cache local com Room para uso offline, incluindo os bytes do sprite.
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
    | Flow<Resource<T>>
    v
Repository
    |-------------------|
    v                   v
Room DAO            Retrofit API
cache local         PokeAPI
```

O `PokedexRepository` e o ponto central da sincronizacao:

1. Emite `Loading`.
2. Le o cache salvo no Room.
3. Se existir cache, entrega os dados imediatamente.
4. Se o cache ainda estiver valido, encerra sem chamar a API.
5. Se estiver offline, lista, filtros, busca e detalhe continuam usando o que ja existe no Room.
6. Se estiver online, busca a contagem completa e carrega paginas de 20 Pokemon conforme o usuario rola a lista.
7. Pull-to-refresh nunca apaga o cache antes da rede responder; se a sincronizacao falhar, o app mantem os dados locais.

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
│   └── repository/PokedexRepository.kt
├── di/AppModule.kt
├── domain/model/Pokemon.kt
├── presentation/
│   ├── navigation/AppNavigation.kt
│   ├── ui/
│   │   ├── PokedexScreen.kt
│   │   └── PokemonDetailScreen.kt
│   └── viewmodel/
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
- KSP

## Endpoints usados

- `GET https://pokeapi.co/api/v2/pokemon?limit=20&offset={offset}`
- `GET https://pokeapi.co/api/v2/pokemon/{nameOrId}`
- URLs de sprites retornadas em `sprites.front_default`

Os valores de EV yield vem do campo `stats[].effort` retornado pelo endpoint de detalhe de cada Pokemon.
O banco local se chama `pokedex_cache.db` e o schema atual do Room esta na versao 2.

## Permissoes

Declaradas em `app/src/main/AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

## Comportamento de cache

| Condicao | Resultado |
|---|---|
| Online com cache valido | Exibe cache e nao chama a API |
| Online com cache vencido | Busca PokeAPI, atualiza Room e exibe dados novos |
| Offline com cache | Exibe cache e mostra aviso offline |
| Offline sem cache | Exibe erro de conexao |
| Falha na API com cache | Mantem cache e mostra erro |
| Pull-to-refresh | Tenta sincronizar com a API sem descartar o cache existente |
| Busca offline | Procura por nome ou numero no Room |
| Filtro offline | Filtra os Pokemon salvos no Room |
| Limpar cache | Remove os Pokemon salvos no Room e zera o estado da tela |
