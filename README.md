# NewsCache - Room + Retrofit + Compose

Aplicativo Android de exemplo para consumo de noticias com cache local. O app busca manchetes na NewsAPI, salva os artigos no Room e continua exibindo dados cacheados quando o dispositivo esta offline ou quando a API falha.

## Funcionalidades

- Lista de noticias com Jetpack Compose.
- Pull-to-refresh para forcar nova busca na API.
- Cache local com Room.
- Leitura offline de dados cacheados.
- Tela de detalhe com WebView quando ha conexao.
- Fallback offline na tela de detalhe exibindo o resumo salvo.
- Avisos de erro via Snackbar e banner de modo offline.

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
cache local         NewsAPI
```

O `NewsRepository` centraliza a regra de cache:

1. Emite `Loading`.
2. Carrega o cache local via Room.
3. Se existir cache, emite os dados imediatamente.
4. Se o cache ainda estiver valido, encerra sem chamar a API.
5. Se estiver offline, mantem os dados cacheados ou emite erro.
6. Se estiver online, busca dados novos na API, limpa o cache antigo e salva os artigos atualizados.

O cache e considerado valido por 5 minutos.

## Estrutura

```text
app/src/main/java/com/app/room_retrofit/
├── MainActivity.kt                         # Entry point Compose
├── MyApplication.kt                        # Hilt application
├── data/
│   ├── local/
│   │   ├── AppDatabase.kt                  # Configuracao Room
│   │   ├── dao/ArticleDao.kt               # Queries do cache
│   │   └── entity/ArticleEntity.kt         # Entidade Room
│   ├── remote/
│   │   ├── api/NewsApiService.kt           # Endpoints Retrofit
│   │   └── dto/NewsResponse.kt             # DTOs da NewsAPI
│   └── repository/NewsRepository.kt        # Regra de cache e sincronizacao
├── di/AppModule.kt                         # Providers Hilt
├── domain/model/Article.kt                 # Modelo de dominio e mappers
├── presentation/
│   ├── navigation/AppNavigation.kt         # Rotas Compose
│   ├── ui/
│   │   ├── NewsScreen.kt                   # Lista de noticias
│   │   └── ArticleDetailScreen.kt          # Detalhe/WebView/offline
│   └── viewmodel/
│       ├── NewsViewModel.kt                # Estado da lista
│       └── ArticleDetailViewModel.kt       # Estado do detalhe
└── util/
    ├── NetworkUtil.kt                      # Verificacao de conectividade
    └── Resource.kt                         # Loading/Success/Error
```

## Setup

### 1. Configure a API key

Crie uma chave em [newsapi.org](https://newsapi.org) e adicione ao arquivo `local.properties` na raiz do projeto:

```properties
NEWS_API_KEY=sua_chave_aqui
```

O valor e lido no `app/build.gradle.kts` e exposto como `BuildConfig.NEWS_API_KEY`.

### 2. Compile o projeto

```bash
./gradlew :app:compileDebugKotlin
```

Para gerar o APK de debug:

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

## Permissoes

Declaradas em `app/src/main/AndroidManifest.xml`:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`

## Comportamento de cache

| Condicao | Resultado |
|---|---|
| Online com cache valido | Exibe cache e nao chama a API |
| Online com cache vencido | Busca API, atualiza Room e exibe dados novos |
| Offline com cache | Exibe cache e mostra aviso offline |
| Offline sem cache | Exibe erro de conexao |
| Falha na API com cache | Mantem cache e mostra erro |
| Pull-to-refresh | Forca tentativa de atualizacao pela API |

## Observacoes

- A API usada e `https://newsapi.org/v2/top-headlines`.
- A consulta atual usa `country = "us"` e `pageSize = 20`.
- O banco local chama `news_cache.db`.
- O schema do Room esta com `exportSchema = false`.
- `local.properties` nao deve ser versionado porque contem configuracoes locais e a chave da API.
