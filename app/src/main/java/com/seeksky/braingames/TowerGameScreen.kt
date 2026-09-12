package com.seeksky.braingames

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

private enum class TowerScreenStatus { Ready, Playing, Victory }

@Composable
fun TowerGameApp(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val saveStore = remember(context) { TowerSaveStore(context.applicationContext) }
    var status by remember { mutableStateOf(TowerScreenStatus.Ready) }
    var gameState by remember { mutableStateOf(TowerGameState()) }
    var hasSave by remember { mutableStateOf(saveStore.hasSave()) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var notice by remember { mutableStateOf<String?>(null) }
    var showQuitConfirmation by remember { mutableStateOf(false) }
    var showNewGameConfirmation by remember { mutableStateOf(false) }
    var showShop by remember { mutableStateOf(false) }
    var showMonsterBook by remember { mutableStateOf(false) }

    fun beginGame(savedGame: TowerSavedGame? = null) {
        val restoredElapsed = savedGame?.elapsedMillis ?: 0L
        gameState = savedGame?.state ?: TowerGameState()
        elapsedMillis = restoredElapsed
        startedAt = SystemClock.elapsedRealtime() - restoredElapsed
        notice = if (savedGame == null) "勇者，寻找通往塔顶的道路吧" else "存档读取成功"
        status = TowerScreenStatus.Playing
    }

    fun saveGame(message: String? = null) {
        val currentElapsed = if (status == TowerScreenStatus.Playing) {
            SystemClock.elapsedRealtime() - startedAt
        } else {
            elapsedMillis
        }
        elapsedMillis = currentElapsed
        saveStore.save(gameState, currentElapsed)
        hasSave = true
        if (message != null) notice = message
    }

    fun returnToReady() {
        status = TowerScreenStatus.Ready
        hasSave = saveStore.hasSave()
        notice = null
    }

    fun handleMove(direction: TowerDirection) {
        if (status != TowerScreenStatus.Playing) return
        val result = attemptTowerMove(gameState, direction)
        gameState = result.state
        when (val event = result.event) {
            is TowerEvent.Message -> notice = event.text
            is TowerEvent.Battle -> notice =
                "击败${event.monster.title} · 损失 ${event.damage} 生命 · 金币 +${event.monster.gold}"
            is TowerEvent.FloorChanged -> notice = "进入第 ${event.floor} 层"
            TowerEvent.OpenShop -> showShop = true
            TowerEvent.Victory -> {
                elapsedMillis = SystemClock.elapsedRealtime() - startedAt
                saveStore.clear()
                hasSave = false
                status = TowerScreenStatus.Victory
            }
            TowerEvent.None -> Unit
        }
        if (result.changed && status == TowerScreenStatus.Playing) saveGame()
    }

    LaunchedEffect(status, startedAt) {
        while (status == TowerScreenStatus.Playing) {
            elapsedMillis = SystemClock.elapsedRealtime() - startedAt
            delay(250L)
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2_400L)
            notice = null
        }
    }

    BackHandler(enabled = status == TowerScreenStatus.Playing) {
        showQuitConfirmation = true
    }

    Scaffold(
        containerColor = Color(0xFF17151D),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TowerHeader(
                isPlaying = status == TowerScreenStatus.Playing,
                onBack = {
                    if (status == TowerScreenStatus.Playing) showQuitConfirmation = true
                    else onNavigateBack()
                },
                onSave = { saveGame("进度已保存") },
            )
        },
    ) { contentPadding ->
        when (status) {
            TowerScreenStatus.Ready -> TowerReadyContent(
                hasSave = hasSave,
                notice = notice,
                onContinue = {
                    val savedGame = saveStore.load()
                    if (savedGame == null) {
                        saveStore.clear()
                        hasSave = false
                        notice = "存档已损坏，请开始新游戏"
                    } else {
                        beginGame(savedGame)
                    }
                },
                onNewGame = {
                    if (hasSave) showNewGameConfirmation = true else beginGame()
                },
                modifier = Modifier.padding(contentPadding),
            )

            TowerScreenStatus.Playing -> TowerPlayingContent(
                state = gameState,
                elapsedMillis = elapsedMillis,
                notice = notice,
                onMove = ::handleMove,
                onOpenBook = { showMonsterBook = true },
                modifier = Modifier.padding(contentPadding),
            )

            TowerScreenStatus.Victory -> TowerVictoryContent(
                state = gameState,
                elapsedMillis = elapsedMillis,
                onPlayAgain = { beginGame() },
                onHome = ::returnToReady,
                modifier = Modifier.padding(contentPadding),
            )
        }
    }

    if (showQuitConfirmation) {
        AlertDialog(
            onDismissRequest = { showQuitConfirmation = false },
            title = { Text("返回游戏大厅？") },
            text = { Text("当前进度会自动保存，下次可从本层继续。") },
            confirmButton = {
                TextButton(onClick = {
                    saveGame()
                    showQuitConfirmation = false
                    returnToReady()
                }) { Text("保存并返回") }
            },
            dismissButton = {
                TextButton(onClick = { showQuitConfirmation = false }) { Text("继续冒险") }
            },
        )
    }

    if (showNewGameConfirmation) {
        AlertDialog(
            onDismissRequest = { showNewGameConfirmation = false },
            title = { Text("开始新游戏？") },
            text = { Text("已有存档将被覆盖。") },
            confirmButton = {
                TextButton(onClick = {
                    saveStore.clear()
                    hasSave = false
                    showNewGameConfirmation = false
                    beginGame()
                }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showNewGameConfirmation = false }) { Text("取消") }
            },
        )
    }

    if (showShop) {
        TowerShopDialog(
            state = gameState,
            onBuy = { upgrade ->
                val result = buyTowerUpgrade(gameState, upgrade)
                gameState = result.state
                notice = result.message
                if (result.purchased) saveGame()
            },
            onDismiss = { showShop = false },
        )
    }

    if (showMonsterBook) {
        TowerMonsterBookDialog(
            state = gameState,
            onDismiss = { showMonsterBook = false },
        )
    }
}

@Composable
private fun TowerHeader(
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(color = Color(0xFF17151D)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text(if (isPlaying) "返回" else "大厅") }
            Text(
                "魔塔",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFD166),
                fontFamily = FontFamily.Monospace,
                fontSize = 23.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            if (isPlaying) TextButton(onClick = onSave) { Text("存档") }
            else Spacer(Modifier.size(64.dp))
        }
    }
}

@Composable
private fun TowerReadyContent(
    hasSave: Boolean,
    notice: String?,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelSprite(R.drawable.tower_player, "勇者", Modifier.size(112.dp))
        Text(
            "勇者与五层魔塔",
            color = Color(0xFFFFD166),
            fontFamily = FontFamily.Monospace,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "钥匙、宝石与血量都不可浪费。战斗没有随机数，每一次选择都会决定你能否登上塔顶。",
            color = Color(0xFFD7D2E2),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(28.dp))
        if (hasSave) {
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B5DE5)),
            ) { Text("继续冒险", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
        }
        Button(
            onClick = onNewGame,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC7A29)),
        ) { Text(if (hasSave) "重新开始" else "开始冒险", fontWeight = FontWeight.Bold) }
        notice?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Color(0xFFFFD166), textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(26.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF282330)),
            border = BorderStroke(1.dp, Color(0xFF51465F)),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("冒险规则", color = Color.White, fontWeight = FontWeight.Bold)
                Text("• 用方向键移动，靠近怪物即自动战斗", color = Color(0xFFD7D2E2))
                Text("• 伤害由双方攻防确定，可提前精确计算", color = Color(0xFFD7D2E2))
                Text("• 找到怪物图鉴后，可查看本层战斗损伤", color = Color(0xFFD7D2E2))
                Text("• 经过楼梯自动切换楼层，进度自动保存", color = Color(0xFFD7D2E2))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("像素素材：Kenney Tiny Dungeon · CC0", color = Color(0xFF9992A6), fontSize = 12.sp)
    }
}

@Composable
private fun TowerPlayingContent(
    state: TowerGameState,
    elapsedMillis: Long,
    notice: String?,
    onMove: (TowerDirection) -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val floor = TOWER_FLOORS[state.floor - 1]
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.floor}F  ${floor.name}",
                modifier = Modifier.weight(1f),
                color = Color(0xFFFFD166),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text(formatTowerDuration(elapsedMillis), color = Color(0xFFC9C2D5), fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(6.dp))
        TowerStatsPanel(state.stats)
        Spacer(Modifier.height(8.dp))
        TowerMap(state = state, onMove = onMove, modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            notice?.let {
                Text(it, color = Color(0xFFFFD166), fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onOpenBook, enabled = state.hasMonsterBook) {
                Text(if (state.hasMonsterBook) "怪物图鉴" else "尚未获得图鉴")
            }
            TowerDirectionPad(onMove)
            Text(
                "钥匙\n黄 ${state.stats.yellowKeys}  蓝 ${state.stats.blueKeys}  红 ${state.stats.redKeys}",
                color = Color(0xFFD7D2E2),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TowerStatsPanel(stats: TowerStats) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF282330)),
        border = BorderStroke(1.dp, Color(0xFF51465F)),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Row(Modifier.fillMaxWidth()) {
                TowerStat("生命", stats.hp.toString(), Color(0xFFFF6B6B), Modifier.weight(1f))
                TowerStat("攻击", stats.attack.toString(), Color(0xFFFF9F43), Modifier.weight(1f))
                TowerStat("防御", stats.defense.toString(), Color(0xFF54A0FF), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                TowerStat("金币", stats.gold.toString(), Color(0xFFFFD166), Modifier.weight(1f))
                TowerStat("经验", stats.experience.toString(), Color(0xFF76D7C4), Modifier.weight(1f))
                TowerStat("战力", (stats.attack + stats.defense).toString(), Color(0xFFD7D2E2), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TowerStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFFAAA3B7), fontSize = 11.sp)
        Text(value, color = color, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TowerMap(
    state: TowerGameState,
    onMove: (TowerDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        color = Color(0xFF743B38),
        border = BorderStroke(2.dp, Color(0xFF8E7A9F)),
    ) {
        Column {
            repeat(TOWER_GRID_SIZE) { row ->
                Row(Modifier.weight(1f)) {
                    repeat(TOWER_GRID_SIZE) { column ->
                        val position = TowerPosition(row, column)
                        val adjacent = abs(row - state.position.row) + abs(column - state.position.column) == 1
                        val direction = if (adjacent) directionBetween(state.position, position) else null
                        TowerMapCell(
                            tile = towerTileAt(state, position),
                            hasPlayer = state.position == position,
                            onClick = { direction?.let(onMove) },
                            enabled = adjacent,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TowerMapCell(
    tile: TowerTile,
    hasPlayer: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.clickable(enabled = enabled, onClick = onClick)) {
        PixelSprite(R.drawable.tower_floor, null, Modifier.fillMaxSize())
        if (tile != TowerTile.Floor) {
            PixelSprite(towerTileDrawable(tile), tile.name, Modifier.fillMaxSize())
        }
        if (hasPlayer) PixelSprite(R.drawable.tower_player, "勇者", Modifier.fillMaxSize())
    }
}

@Composable
private fun TowerDirectionPad(onMove: (TowerDirection) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TowerDirectionButton("▲") { onMove(TowerDirection.Up) }
        Row {
            TowerDirectionButton("◀") { onMove(TowerDirection.Left) }
            Spacer(Modifier.size(38.dp))
            TowerDirectionButton("▶") { onMove(TowerDirection.Right) }
        }
        TowerDirectionButton("▼") { onMove(TowerDirection.Down) }
    }
}

@Composable
private fun TowerDirectionButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(38.dp),
        shape = RoundedCornerShape(7.dp),
        color = Color(0xFF51465F),
        border = BorderStroke(1.dp, Color(0xFF8E7A9F)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TowerShopDialog(
    state: TowerGameState,
    onBuy: (TowerUpgrade) -> Unit,
    onDismiss: () -> Unit,
) {
    val cost = towerShopCost(state)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("勇者商店") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本次强化需要 $cost 金币，你有 ${state.stats.gold} 金币。")
                TowerUpgrade.entries.forEach { upgrade ->
                    Button(
                        onClick = { onBuy(upgrade) },
                        enabled = state.stats.gold >= cost,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(upgrade.title) }
                }
                Text("每次购买后价格增加 10 金币。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("离开") } },
    )
}

@Composable
private fun TowerMonsterBookDialog(
    state: TowerGameState,
    onDismiss: () -> Unit,
) {
    val floorMonsterTiles = TOWER_FLOORS[state.floor - 1].rows
        .flatMap { it.toList() }
        .map(TowerTile::fromSymbol)
        .mapNotNull(TowerMonster::fromTile)
        .distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("怪物图鉴 · ${state.floor}F") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()),
            ) {
                floorMonsterTiles.forEachIndexed { index, monster ->
                    val damage = calculateTowerBattleDamage(state.stats, monster)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PixelSprite(towerMonsterDrawable(monster), monster.title, Modifier.size(44.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(monster.title, fontWeight = FontWeight.Bold)
                            Text(
                                "生命 ${monster.hp}  攻 ${monster.attack}  防 ${monster.defense}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text("金币 ${monster.gold}  经验 ${monster.experience}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            when {
                                damage == null -> "无法破防"
                                damage >= state.stats.hp -> "无法战胜"
                                else -> "损伤 $damage"
                            },
                            color = when {
                                damage == null || damage >= state.stats.hp -> MaterialTheme.colorScheme.error
                                damage == 0 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (index != floorMonsterTiles.lastIndex) HorizontalDivider()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TowerVictoryContent(
    state: TowerGameState,
    elapsedMillis: Long,
    onPlayAgain: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PixelSprite(R.drawable.tower_exit, "胜利", Modifier.size(112.dp))
        Text("魔塔已被征服", color = Color(0xFFFFD166), fontSize = 28.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text("通关用时 ${formatTowerDuration(elapsedMillis)}", color = Color.White, fontSize = 20.sp)
        Text(
            "剩余生命 ${state.stats.hp} · 攻击 ${state.stats.attack} · 防御 ${state.stats.defense}",
            color = Color(0xFFD7D2E2),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth()) { Text("再次挑战") }
        TextButton(onClick = onHome) { Text("返回首页") }
    }
}

@Composable
private fun PixelSprite(
    @DrawableRes drawable: Int,
    description: String?,
    modifier: Modifier = Modifier,
) {
    Image(
        bitmap = ImageBitmap.imageResource(drawable),
        contentDescription = description,
        modifier = modifier,
        contentScale = ContentScale.FillBounds,
        filterQuality = FilterQuality.None,
    )
}

private fun directionBetween(from: TowerPosition, to: TowerPosition): TowerDirection? = when {
    to.row == from.row - 1 && to.column == from.column -> TowerDirection.Up
    to.row == from.row + 1 && to.column == from.column -> TowerDirection.Down
    to.row == from.row && to.column == from.column - 1 -> TowerDirection.Left
    to.row == from.row && to.column == from.column + 1 -> TowerDirection.Right
    else -> null
}

@DrawableRes
private fun towerTileDrawable(tile: TowerTile): Int = when (tile) {
    TowerTile.Floor -> R.drawable.tower_floor
    TowerTile.Wall -> R.drawable.tower_wall
    TowerTile.YellowDoor -> R.drawable.tower_yellow_door
    TowerTile.BlueDoor -> R.drawable.tower_blue_door
    TowerTile.RedDoor -> R.drawable.tower_red_door
    TowerTile.UpStairs -> R.drawable.tower_stairs_up
    TowerTile.DownStairs -> R.drawable.tower_stairs_down
    TowerTile.YellowKey -> R.drawable.tower_yellow_key
    TowerTile.BlueKey -> R.drawable.tower_blue_key
    TowerTile.RedKey -> R.drawable.tower_red_key
    TowerTile.RedPotion -> R.drawable.tower_red_potion
    TowerTile.BluePotion -> R.drawable.tower_blue_potion
    TowerTile.AttackGem -> R.drawable.tower_attack_gem
    TowerTile.DefenseGem -> R.drawable.tower_defense_gem
    TowerTile.Sword -> R.drawable.tower_sword
    TowerTile.Shield -> R.drawable.tower_shield
    TowerTile.MonsterBook -> R.drawable.tower_book
    TowerTile.Shop -> R.drawable.tower_shop
    TowerTile.Npc -> R.drawable.tower_npc
    TowerTile.Exit -> R.drawable.tower_exit
    TowerTile.Slime -> R.drawable.tower_slime
    TowerTile.Bat -> R.drawable.tower_bat
    TowerTile.Skeleton -> R.drawable.tower_skeleton
    TowerTile.Knight -> R.drawable.tower_knight
    TowerTile.Wizard -> R.drawable.tower_wizard
    TowerTile.Boss -> R.drawable.tower_boss
}

@DrawableRes
private fun towerMonsterDrawable(monster: TowerMonster): Int = towerTileDrawable(monster.tile)

fun formatTowerDuration(elapsedMillis: Long): String {
    val safeMillis = elapsedMillis.coerceAtLeast(0L)
    val hours = safeMillis / 3_600_000
    val minutes = (safeMillis / 60_000) % 60
    val seconds = (safeMillis / 1_000) % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
