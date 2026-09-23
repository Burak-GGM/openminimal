package org.openminimal.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openminimal.launcher.data.LauncherRepository
import org.openminimal.launcher.model.*
import org.openminimal.launcher.platform.*
import org.openminimal.launcher.ui.OpenMinimalTheme

class FocusGateActivity : ComponentActivity() {
    private lateinit var target: GateTarget

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        target = FocusGateContract.target(intent)
        if (target.packageName.isBlank()) { finish(); return }
        enableEdgeToEdge()
        lifecycleScope.launch {
            val state = LauncherRepository(this@FocusGateActivity).state.first()
            setContent {
                OpenMinimalTheme(state.config) {
                    BackHandler { returnHome() }
                    FocusGateScreen(target, continueToApp = ::continueToApp, returnHome = ::returnHome)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }

    private fun continueToApp() {
        FocusGateSession.allowNext(target.packageName)
        val started = runCatching {
            target.shortcut?.let { shortcut ->
                getSystemService(LauncherApps::class.java).startShortcut(shortcut, null, null)
            } ?: run {
                val component = target.component?.let(ComponentName::unflattenFromString)
                val launch = if (component != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(component)
                else packageManager.getLaunchIntentForPackage(target.packageName)
                requireNotNull(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED).let(::startActivity)
            }
        }.isSuccess
        if (!started) returnHome() else finish()
    }

    private fun returnHome() {
        runCatching {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
        finish()
    }
}

@Composable
private fun FocusGateScreen(target: GateTarget, continueToApp: () -> Unit, returnHome: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(Modifier.size(64.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary) }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                if (target.reason == FocusGateReason.DAILY_LIMIT) stringResource(R.string.gate_daily_title)
                else stringResource(R.string.gate_launch_title, target.label),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    target.blocked -> stringResource(R.string.gate_blocked_body, target.label)
                    target.reason == FocusGateReason.DAILY_LIMIT -> stringResource(R.string.gate_daily_body, target.label)
                    else -> stringResource(R.string.focus_gate_intro)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Surface(Modifier.fillMaxWidth().widthIn(max = 520.dp), shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.padding(22.dp)) {
                    if (target.blocked) BlockedGate(returnHome)
                    else target.challenge?.let { ChallengeGate(it, continueToApp, returnHome) } ?: BlockedGate(returnHome)
                }
            }
        }
        }
    }
}

@Composable private fun BlockedGate(returnHome: () -> Unit) {
    Button(onClick = returnHome, Modifier.fillMaxWidth().testTag("gate_home")) { Text(stringResource(R.string.return_home)) }
}

@Composable
private fun ChallengeGate(challenge: FocusChallenge, complete: () -> Unit, returnHome: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        when (challenge.type) {
            FocusChallengeType.TEXT -> TextChallenge(challenge, complete)
            FocusChallengeType.MATH -> MathChallenge(challenge, complete)
            FocusChallengeType.MEMORY -> MemoryChallenge(challenge, complete)
            FocusChallengeType.WAIT -> WaitChallenge(challenge, complete)
        }
        TextButton(onClick = returnHome, Modifier.fillMaxWidth()) { Text(stringResource(R.string.return_home)) }
    }
}

@Composable private fun TextChallenge(challenge: FocusChallenge, complete: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    Text(stringResource(R.string.gate_text_instruction), style = MaterialTheme.typography.titleMedium)
    Text(challenge.phrase, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedTextField(input, { input = it.take(240) }, Modifier.fillMaxWidth().testTag("gate_phrase_input"), minLines = 2,
        label = { Text(stringResource(R.string.challenge_phrase)) })
    Button(onClick = complete, enabled = input == challenge.phrase, modifier = Modifier.fillMaxWidth().testTag("gate_continue")) { Text(stringResource(R.string.gate_continue)) }
}

@Composable private fun MathChallenge(challenge: FocusChallenge, complete: () -> Unit) {
    val problem = rememberSaveable { mathProblem(challenge.difficulty).let { arrayOf(it.expression, it.answer.toString()) } }
    val generated = MathProblem(problem[0], problem[1].toInt())
    var input by rememberSaveable { mutableStateOf("") }
    var wrong by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.gate_math_instruction), style = MaterialTheme.typography.titleMedium)
    Text(generated.expression, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
    OutlinedTextField(input, { input = it.filter { char -> char.isDigit() || char == '-' }.take(8); wrong = false },
        Modifier.fillMaxWidth().testTag("gate_math_input"), singleLine = true,
        label = { Text(stringResource(R.string.answer)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    if (wrong) Text(stringResource(R.string.incorrect_answer), color = MaterialTheme.colorScheme.error)
    Button(onClick = { if (input.toIntOrNull() == generated.answer) complete() else wrong = true }, enabled = input.isNotBlank(),
        modifier = Modifier.fillMaxWidth().testTag("gate_continue")) { Text(stringResource(R.string.gate_continue)) }
}

@Composable private fun WaitChallenge(challenge: FocusChallenge, complete: () -> Unit) {
    var remaining by rememberSaveable { mutableIntStateOf(challenge.waitSeconds) }
    LaunchedEffect(challenge.waitSeconds) {
        while (remaining > 0) { delay(1_000); remaining-- }
    }
    Text(stringResource(R.string.gate_wait_instruction), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(stringResource(R.string.gate_wait_message), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Text(stringResource(R.string.gate_seconds_remaining, remaining), style = MaterialTheme.typography.displaySmall)
    LinearProgressIndicator(progress = { 1f - remaining.toFloat() / challenge.waitSeconds }, modifier = Modifier.fillMaxWidth())
    Button(onClick = complete, enabled = remaining == 0, modifier = Modifier.fillMaxWidth().testTag("gate_continue")) { Text(stringResource(R.string.gate_continue)) }
}

@Composable private fun MemoryChallenge(challenge: FocusChallenge, complete: () -> Unit) {
    val rounds = memoryRounds(challenge.difficulty)
    var round by rememberSaveable { mutableIntStateOf(1) }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    var active by remember { mutableIntStateOf(-1) }
    var accepting by remember { mutableStateOf(false) }
    var inputIndex by remember { mutableIntStateOf(0) }
    var missed by remember { mutableStateOf(false) }
    val sequence = remember(round, retry) { List(round + 2) { kotlin.random.Random.nextInt(4) } }
    LaunchedEffect(round, retry) {
        accepting = false; inputIndex = 0
        delay(500)
        sequence.forEach { pad -> active = pad; delay(600); active = -1; delay(220) }
        accepting = true
    }
    Text(stringResource(R.string.gate_memory_instruction), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    Text(stringResource(R.string.memory_round, round, rounds), color = MaterialTheme.colorScheme.primary)
    val status = when {
        accepting -> stringResource(R.string.memory_repeat)
        active >= 0 -> stringResource(R.string.memory_watch_button, active + 1)
        else -> stringResource(R.string.memory_watch)
    }
    Text(status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (missed) Text(stringResource(R.string.memory_try_again), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
    val colors = listOf(Color(0xFFDF6C5B), Color(0xFFDAA520), Color(0xFF4A9B72), Color(0xFF557CC2))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(2) { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { column ->
                val index = row * 2 + column
                val animated by animateColorAsState(if (active == index) colors[index] else colors[index].copy(alpha = .28f), label = "memory_pad")
                val label = stringResource(R.string.memory_pad, index + 1)
                Box(Modifier.weight(1f).aspectRatio(1.7f).background(animated, MaterialTheme.shapes.large)
                    .semantics { contentDescription = label }
                    .clickable(enabled = accepting) {
                        if (sequence[inputIndex] != index) { missed = true; retry++ }
                        else if (inputIndex == sequence.lastIndex) {
                            missed = false
                            if (round == rounds) complete() else round++
                        } else inputIndex++
                    })
            }
        } }
    }
}
