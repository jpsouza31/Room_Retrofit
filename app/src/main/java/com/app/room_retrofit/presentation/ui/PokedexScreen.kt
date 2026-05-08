package com.app.room_retrofit.presentation.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.room_retrofit.domain.model.DataSource
import com.app.room_retrofit.domain.model.EvStat
import com.app.room_retrofit.domain.model.Pokemon
import com.app.room_retrofit.presentation.viewmodel.PokedexViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PokedexScreen(
    onPokemonClick: (Int) -> Unit,
    viewModel: PokedexViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val shouldLoadNextPage by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                ?: return@derivedStateOf false
            lastVisibleIndex >= uiState.filteredPokemon.lastIndex - 4
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearError()
            }
        }
    }

    LaunchedEffect(shouldLoadNextPage, uiState.canLoadMore, uiState.filteredPokemon.size) {
        if (shouldLoadNextPage && uiState.canLoadMore) {
            viewModel.loadNextPage()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("EV Pokedex") },
                    actions = {
                        TextButton(onClick = viewModel::clearLocalCache) {
                            Text("Limpar cache")
                        }
                    }
                )
                if (uiState.isOffline) {
                    Surface(color = Color(0xFFD32F2F)) {
                        Text(
                            text = "Modo offline - usando cache local",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PokedexFilters(
                    query = uiState.query,
                    selectedEvStat = uiState.selectedEvStat,
                    onQueryChange = viewModel::updateQuery,
                    onEvStatSelected = viewModel::selectEvStat,
                    modifier = Modifier.padding(16.dp)
                )
                if (uiState.isRefreshing || (uiState.isLoading && uiState.pokemon.isNotEmpty())) {
                    ApiSyncBanner()
                }

                when {
                    uiState.isLoading && uiState.pokemon.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.filteredPokemon.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (uiState.isOffline && uiState.query.isBlank()) {
                                OfflineEmptyState(onRetry = { viewModel.loadInitialPage(forceRefresh = true) })
                            } else {
                                Text("Nenhum Pokemon encontrado")
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(uiState.filteredPokemon, key = { it.id }) { pokemon ->
                                PokemonListItem(
                                    pokemon = pokemon,
                                    selectedEvStat = uiState.selectedEvStat,
                                    onClick = { onPokemonClick(pokemon.id) }
                                )
                                HorizontalDivider()
                            }
                            if (uiState.isLoadingNextPage) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = "Buscando da API...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PokedexFilters(
    query: String,
    selectedEvStat: EvStat,
    onQueryChange: (String) -> Unit,
    onEvStatSelected: (EvStat) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar por nome ou numero") }
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EvStat.entries.forEach { stat ->
                FilterChip(
                    selected = selectedEvStat == stat,
                    onClick = { onEvStatSelected(stat) },
                    label = { Text(stat.label) }
                )
            }
        }
    }
}

@Composable
private fun PokemonListItem(
    pokemon: Pokemon,
    selectedEvStat: EvStat,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PokemonSprite(
                url = pokemon.spriteUrl,
                spriteBytes = pokemon.spriteBytes,
                contentDescription = pokemon.displayName(),
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "#${pokemon.id.toString().padStart(3, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = pokemon.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pokemon.types.forEach { type ->
                        TypePill(type = type)
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DataSourceBadge(source = pokemon.source)
                EvYieldBadge(
                    value = pokemon.evFor(selectedEvStat),
                    label = if (selectedEvStat == EvStat.ALL) "EVs" else selectedEvStat.label
                )
            }
        }
    }
}

@Composable
fun PokemonSprite(
    url: String?,
    spriteBytes: ByteArray? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = url, key2 = spriteBytes) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (spriteBytes != null) {
                    return@withContext BitmapFactory.decodeByteArray(spriteBytes, 0, spriteBytes.size)
                }
                url?.let { spriteUrl ->
                    java.net.URL(spriteUrl).openStream().use { BitmapFactory.decodeStream(it) }
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TypePill(type: String) {
    Surface(
        color = typeColor(type),
        contentColor = Color.White,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = type.replaceFirstChar { it.titlecase(Locale.US) },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
fun DataSourceBadge(source: DataSource) {
    val label = if (source == DataSource.NETWORK) "API" else "Cache"
    val tint = if (source == DataSource.NETWORK) Color(0xFF15803D) else Color(0xFF64748B)
    Surface(
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EvYieldBadge(value: Int, label: String) {
    AssistChip(
        onClick = {},
        label = {
            Text(
                text = "$value $label",
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

@Composable
private fun ApiSyncBanner() {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = "Atualizando dados da API...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun OfflineEmptyState(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Text(
            text = "Sem conexao com a internet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Conecte-se para carregar os Pokemon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
            Text("Tentar novamente")
        }
    }
}

fun Pokemon.displayName(): String =
    name
        .split("-")
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(Locale.US) } }

private fun typeColor(type: String): Color =
    when (type) {
        "normal" -> Color(0xFF7A7D67)
        "fire" -> Color(0xFFC2410C)
        "water" -> Color(0xFF2563EB)
        "electric" -> Color(0xFFB45309)
        "grass" -> Color(0xFF15803D)
        "ice" -> Color(0xFF0891B2)
        "fighting" -> Color(0xFF991B1B)
        "poison" -> Color(0xFF7E22CE)
        "ground" -> Color(0xFF92400E)
        "flying" -> Color(0xFF4F46E5)
        "psychic" -> Color(0xFFBE185D)
        "bug" -> Color(0xFF4D7C0F)
        "rock" -> Color(0xFF57534E)
        "ghost" -> Color(0xFF581C87)
        "dragon" -> Color(0xFF4338CA)
        "dark" -> Color(0xFF292524)
        "steel" -> Color(0xFF475569)
        "fairy" -> Color(0xFFDB2777)
        else -> Color(0xFF64748B)
    }
