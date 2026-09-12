package com.seeksky.braingames

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class AppDestination { Home, Schulte, Math, Reaction, Sudoku, SlidingPuzzle, Tower, Sokoban }

@Composable
fun BrainGamesApp() {
    var destination by rememberSaveable { mutableStateOf(AppDestination.Home) }

    BackHandler(enabled = destination != AppDestination.Home) {
        destination = AppDestination.Home
    }

    when (destination) {
        AppDestination.Home -> GameLibraryScreen(
            onOpenSchulte = { destination = AppDestination.Schulte },
            onOpenMath = { destination = AppDestination.Math },
            onOpenReaction = { destination = AppDestination.Reaction },
            onOpenSudoku = { destination = AppDestination.Sudoku },
            onOpenSlidingPuzzle = { destination = AppDestination.SlidingPuzzle },
            onOpenTower = { destination = AppDestination.Tower },
            onOpenSokoban = { destination = AppDestination.Sokoban },
        )

        AppDestination.Schulte -> SchulteGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )

        AppDestination.Math -> MathGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )

        AppDestination.Reaction -> ReactionGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )

        AppDestination.Sudoku -> SudokuGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )

        AppDestination.SlidingPuzzle -> SlidingPuzzleGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )

        AppDestination.Tower -> TowerGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )

        AppDestination.Sokoban -> SokobanGameApp(
            onNavigateBack = { destination = AppDestination.Home },
        )
    }
}

@Composable
private fun GameLibraryScreen(
    onOpenSchulte: () -> Unit,
    onOpenMath: () -> Unit,
    onOpenReaction: () -> Unit,
    onOpenSudoku: () -> Unit,
    onOpenSlidingPuzzle: () -> Unit,
    onOpenTower: () -> Unit,
    onOpenSokoban: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "选择今天要训练的能力",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GameCard(
                symbol = "S",
                title = "舒尔特表",
                ability = "专注力 · 视觉搜索",
                description = "从 1 开始依次找出数字，在准确的前提下不断提速。",
                symbolColor = MaterialTheme.colorScheme.primary,
                symbolContentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onOpenSchulte,
            )
            GameCard(
                symbol = "∑",
                title = "逻辑运算",
                ability = "逻辑性 · 心算",
                description = "在连续的四则运算中识别结构，快速找出正确结果。",
                symbolColor = MaterialTheme.colorScheme.tertiary,
                symbolContentColor = MaterialTheme.colorScheme.onTertiary,
                onClick = onOpenMath,
            )
            GameCard(
                symbol = "7A",
                title = "双重判断",
                ability = "反应力 · 规则切换",
                description = "盯住数字与字母，根据当前问题迅速作出是非判断。",
                symbolColor = MaterialTheme.colorScheme.secondary,
                symbolContentColor = MaterialTheme.colorScheme.onSecondary,
                onClick = onOpenReaction,
            )
            GameCard(
                symbol = "9",
                title = "数独",
                ability = "逻辑力 · 空间推理",
                description = "在行、列和九宫格的约束中逐步排除，找出唯一答案。",
                symbolColor = MaterialTheme.colorScheme.primary,
                symbolContentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onOpenSudoku,
            )
            GameCard(
                symbol = "8",
                title = "数字华容道",
                ability = "规划力 · 空间推理",
                description = "移动空位旁的数字，用尽可能少的步数将 1 至 8 依次归位。",
                symbolColor = MaterialTheme.colorScheme.tertiary,
                symbolContentColor = MaterialTheme.colorScheme.onTertiary,
                onClick = onOpenSlidingPuzzle,
            )
            GameCard(
                symbol = "塔",
                title = "魔塔",
                ability = "策略力 · 资源规划",
                description = "计算战斗损伤，收集钥匙与装备，在五层地牢中规划通往魔王的路线。",
                symbolColor = MaterialTheme.colorScheme.secondary,
                symbolContentColor = MaterialTheme.colorScheme.onSecondary,
                onClick = onOpenTower,
            )
            GameCard(
                symbol = "箱",
                title = "推箱子",
                ability = "规划力 · 空间推理",
                description = "规划搬运路线，把所有木箱推到标记位置，小心别让箱子困在墙角。",
                symbolColor = MaterialTheme.colorScheme.primary,
                symbolContentColor = MaterialTheme.colorScheme.onPrimary,
                onClick = onOpenSokoban,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun GameCard(
    symbol: String,
    title: String,
    ability: String,
    description: String,
    symbolColor: Color,
    symbolContentColor: Color,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = symbolColor,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            symbol,
                            color = symbolContentColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(ability, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("进入训练", fontWeight = FontWeight.Bold)
            }
        }
    }
}
