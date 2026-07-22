package com.budgetmanager.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.budgetmanager.presentation.theme.ThemeModeState
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

// ===== Kawaii State =====

enum class KawaiiEventType {
    INCOME_SAVED,
    EXPENSE_SAVED,
    ALL_BUDGETS_SAFE,
    BUDGET_ALERT
}

enum class AmountTier {
    MICRO,    // 0-5
    SMALL,    // 5-30
    MEDIUM,   // 30-100
    LARGE,    // 100-300
    HUGE      // 300+
}

fun tierFor(amount: java.math.BigDecimal): AmountTier {
    val v = amount.toDouble()
    return when {
        v < 5 -> AmountTier.MICRO
        v < 30 -> AmountTier.SMALL
        v < 100 -> AmountTier.MEDIUM
        v < 300 -> AmountTier.LARGE
        else -> AmountTier.HUGE
    }
}

data class KawaiiEvent(
    val type: KawaiiEventType,
    val extraText: String = "",
    val tier: AmountTier = AmountTier.SMALL,
    val id: Long = System.currentTimeMillis()
)

object KawaiiState {
    var currentEvent by mutableStateOf<KawaiiEvent?>(null)
        private set

    fun trigger(type: KawaiiEventType, extraText: String = "", tier: AmountTier = AmountTier.SMALL) {
        if (ThemeModeState.value != "rose") return
        currentEvent = KawaiiEvent(type, extraText, tier)
    }

    fun clear() {
        currentEvent = null
    }
}

fun isRoseTheme(): Boolean = ThemeModeState.value == "rose"

// ===== Messages pools =====

val budgetSafeMessages = listOf(
    "\uD83C\uDF38 Bravo ! Tu geres comme une pro !",
    "\u2728 Parfait, continue comme ca !",
    "\uD83D\uDC85 Budget maitrise, queen !",
    "\uD83C\uDF1F Impeccable ! Rien a dire !",
    "\uD83D\uDC96 C'est clean, bien joue !",
    "\uD83E\uDD29 Le budget te dit merci !",
    "\uD83D\uDE0D T'assures grave !",
    "\uD83C\uDFC6 Pas un euro de trop, respect !"
)

val allBudgetsSafeMessages = listOf(
    "\uD83C\uDF89\uD83D\uDC96 Tu es INCROYABLE ! Tout est sous controle !",
    "\uD83C\uDF1F PERFECTION ! Pas un seul depassement !",
    "\uD83D\uDC85\u2728 Rien ne t'echappe !",
    "\uD83E\uDD73 Zero stress, tout est au TOP !",
    "\uD83D\uDC51 La reine du budget, c'est TOI !",
    "\uD83C\uDFC6\uD83C\uDF38 Maitrise TOTALE ! Standing ovation !"
)

val budgetAlertMessages = listOf(
    "\uD83D\uDE3F Oh non ! %s a deborde !",
    "\uD83D\uDE48 Aie ! %s depasse la limite !",
    "\uD83D\uDE80 Houston, on a un probleme avec %s !",
    "\uD83E\uDD7A Oups ! %s a craque !",
    "\uD83D\uDE45\u200D\u2640\uFE0F %s fait des siennes !",
    "\uD83D\uDEA8 Alerte mignonne sur %s !"
)

val incomeMessages = listOf(
    "\uD83D\uDCB0\uD83D\uDCB0\uD83D\uDCB0 CHA-CHING ! L'argent rentre !",
    "\uD83C\uDF89 PLUIE DE BILLETS !!! ",
    "\uD83E\uDD11 Le compte est TELLEMENT content !",
    "\uD83D\uDCB8\u2728 Ca fait trop plaisir !!!",
    "\uD83C\uDFB5 Money money money ~~ !",
    "\uD83C\uDFB0 JACKPOT !!!",
    "\uD83E\uDD29 La banque sourit de toutes ses dents !",
    "\uD83D\uDE80 Les sous arrivent a toute vitesse !"
)

// ===== Tiered expense messages =====

val expenseMessagesMicro = listOf(
    "☕ Un cafe, ca fait du bien !",
    "🍪 Petit plaisir merite !",
    "🍬 Mini depense, mini culpabilite !",
    "🧁 Tout petit, tout doux !",
    "✨ Une pichenette sur le portefeuille !",
    "💎 Si peu, si peu...",
    "🔑 Un peu de monnaie, c'est tout !"
)

val expenseMessagesSmall = listOf(
    "🙋‍♀️ Bye bye les sous, on se revoit jamais !",
    "🪦 RIP ce billet, parti trop tot...",
    "🛒 *BIIIIP* caisse enregistreuse !",
    "✅ Ca, c'est fait ! *clap clap*",
    "🥺 Le portefeuille pleure un peu...",
    "📝 Ouch... mais c'est note !",
    "🌸 Petit sacrifice pour un grand bonheur !",
    "🎈 Depenser c'est aussi vivre !",
    "✨ Tu fais les bons choix, toujours !"
)

val expenseMessagesMedium = listOf(
    "😅 Ouch, ca pique un peu mais ca vaut le coup !",
    "💸 Le portefeuille a un peu maigri...",
    "😬 Aie ! Mais bon, c'est la vie !",
    "💪 Tu maitrises ton budget, c'est l'essentiel !",
    "🌟 Investissement personnel valide !",
    "💔 Le compte a une petite douleur la !",
    "🔥 L'argent brule plus vite que prevu !",
    "😓 Ca reste raisonnable... j'espere !",
    "💰 Bisou d'adieu aux euros ~",
    "🎭 On se rattrapera le mois prochain !"
)

val expenseMessagesLarge = listOf(
    "😱 OUH la la ! Ca commence a faire !",
    "🌚 Ouch... gros achat detecte !",
    "😭 Mon Dieu, le portefeuille pleure !",
    "💸💸💸 BIG SPENDING !",
    "🙈 J'ai rien vu, j'ai rien vu !",
    "☢️ Alerte gros billet en train de s'echapper !",
    "📉 Le solde a fait un sacre plongeon !",
    "🎪 Show must go on... mais le compte fait grise mine !",
    "🔮 J'espere que c'etait un bon investissement !",
    "😬 Espece de gros achat, va !"
)

val expenseMessagesHuge = listOf(
    "😱😱😱 ENORME ACHAT DETECTE !!!",
    "💥 BOOM ! Ca, c'est un investissement !",
    "👑 Achat royal !",
    "🎉 J'espere que ca en valait la peine !",
    "😈 Le portefeuille est en deuil...",
    "💀 RIP au montant qui vient de partir !",
    "🔥🔥 Brulage massif d'euros en cours !",
    "🎬 C'est l'achat de l'annee, on dirait !",
    "🚨 ALERTE ROUGE sur le compte !",
    "🏆 Champion du monde des grosses depenses !",
    "💜 J'espere que c'est un investissement... ou un truc qui rend heureux longtemps !"
)

fun expenseMessagesForTier(tier: AmountTier): List<String> = when (tier) {
    AmountTier.MICRO -> expenseMessagesMicro
    AmountTier.SMALL -> expenseMessagesSmall
    AmountTier.MEDIUM -> expenseMessagesMedium
    AmountTier.LARGE -> expenseMessagesLarge
    AmountTier.HUGE -> expenseMessagesHuge
}

val incomeMessagesMicro = listOf(
    "✨ Un peu, mais c'est deja ca !",
    "💵 Petit petit, le tiroir-caisse !",
    "💚 Chaque centime compte !"
)

val incomeMessagesSmall = listOf(
    "💰 Cha-ching ! L'argent rentre !",
    "🎉 Pluie de billets !",
    "🤑 Le compte est content !",
    "💸✨ Ca fait plaisir !",
    "🎵 Money money money ~~ !"
)

val incomeMessagesMedium = listOf(
    "🎊 YESSSS ! Belle entree d'argent !",
    "🤩 Ca fait chaud au coeur !",
    "💰💰 Le compte sourit grandement !",
    "✨ Bravo ! Ca paie de bosser !"
)

val incomeMessagesLarge = listOf(
    "👑 GROS revenu detecte !!!",
    "🎊🎉 BELLE entree d'argent !!!",
    "💫 On est riche ! Enfin presque !",
    "😍 Le compte fait des bonds de joie !"
)

val incomeMessagesHuge = listOf(
    "🎰 JACKPOT GIGANTESQUE !!!",
    "👑👑👑 ROYAL ENRICHISSEMENT !!!",
    "🚀 Ca decolle ! Direction la lune !",
    "🌟🌟🌟 C'est Noel avant l'heure !",
    "💰💰💰 PROSPERITE INCROYABLE !!!"
)

fun incomeMessagesForTier(tier: AmountTier): List<String> = when (tier) {
    AmountTier.MICRO -> incomeMessagesMicro
    AmountTier.SMALL -> incomeMessagesSmall
    AmountTier.MEDIUM -> incomeMessagesMedium
    AmountTier.LARGE -> incomeMessagesLarge
    AmountTier.HUGE -> incomeMessagesHuge
}

val expenseMessages = listOf(
    // Drole
    "\uD83D\uDE4B\u200D\u2640\uFE0F Bye bye les sous, on se revoit jamais !",
    "\uD83E\udea6 RIP ce billet, parti trop tot...",
    "\uD83C\uDF0A L'argent c'est comme l'eau, ca COULE !",
    "\uD83D\uDED2 *BIIIIP* caisse enregistreuse !",
    "\uD83D\uDC94 Ton compte vient de perdre des HP !",
    "\uD83D\uDE0E On travaille pour ca apres tout hein !",
    "\uD83C\uDFCB\uFE0F Le portefeuille fait un GROS regime !",
    "\uD83E\uDEE3 Et HOP ! Un billet s'envole dans la nature !",
    "\u2705 Ca, c'est fait ! *clap clap*",
    "\uD83D\uDD25 L'argent brule plus vite que prevu !",
    // Mignon
    "\uD83E\uDD7A Le portefeuille pleure un peu...",
    "\uD83D\uDCDD Ouch... mais c'est note au moins !",
    "\uD83C\uDF38 Petit sacrifice pour un grand bonheur !",
    "\uD83D\uDC8B Bisou d'adieu aux euros ~",
    "\uD83D\uDC96 L'argent part mais l'amour reste !",
    "\uD83C\uDF3C Comme une fleur qui perd ses petales...",
    // Encourageant
    "\uD83C\uDF88 Depenser c'est aussi VIVRE !",
    "\u2728 Tu fais les bons choix, toujours !",
    "\uD83D\uDE07 C'est pour la bonne cause !",
    "\uD83C\uDF08 La vie c'est pas que des economies !",
    "\uD83D\uDCAA Chaque euro est bien place !",
    "\uD83D\uDCC8 Au moins c'est bien organise !",
    // Dramatique
    "\uD83C\uDFAD Adieu petit billet... tu vas me manquer...",
    "\uD83D\uDD4A\uFE0F Moment de silence pour ce montant...",
    "\u2694\uFE0F C'etait un euro courageux, parti au combat...",
    "\uD83C\uDF39 On n'oubliera JAMAIS ces euros...",
    "\uD83C\uDFAC *generique de fin triste*"
)

// ===== Kawaii Overlay =====

@Composable
fun KawaiiOverlay() {
    if (!isRoseTheme()) return

    val event = KawaiiState.currentEvent ?: return

    Box(modifier = Modifier.fillMaxSize()) {
        when (event.type) {
            KawaiiEventType.INCOME_SAVED -> MoneyRainAnimation(event.tier)
            KawaiiEventType.EXPENSE_SAVED -> ExpenseAnimation(event.tier)
            KawaiiEventType.ALL_BUDGETS_SAFE -> AllBudgetsSafeCelebration()
            KawaiiEventType.BUDGET_ALERT -> BudgetAlertAnimation(event.extraText)
        }
    }

    // Auto-clear after delay
    LaunchedEffect(event.id) {
        delay(4500)
        KawaiiState.clear()
    }
}

// ===== Money Rain EXPLOSION (for income) =====

@Composable
private fun MoneyRainAnimation(tier: AmountTier = AmountTier.SMALL) {
    val particleCount = when (tier) {
        AmountTier.MICRO -> 6
        AmountTier.SMALL -> 15
        AmountTier.MEDIUM -> 25
        AmountTier.LARGE -> 40
        AmountTier.HUGE -> 60
    }
    val emojis = remember { List(particleCount) { MoneyParticle() } }

    Box(modifier = Modifier.fillMaxSize()) {
        emojis.forEach { particle ->
            FallingEmoji(particle)
        }

        // Big center emoji that pops (huge for big tiers)
        BigPopEmoji(
            emoji = if (tier == AmountTier.HUGE) "\uD83C\uDFB0" else "\uD83D\uDCB0",
            delayMs = 0
        )

        // Second pop for celebration on big amounts
        if (tier == AmountTier.LARGE || tier == AmountTier.HUGE) {
            BigPopEmoji(emoji = "\uD83C\uDF89", delayMs = 400)
        }

        // Toast message with gradient bg
        KawaiiToast(
            message = incomeMessagesForTier(tier).random(),
            gradient = Brush.horizontalGradient(
                listOf(Color(0xFF00E676), Color(0xFF69F0AE), Color(0xFF00E676))
            ),
            textColor = Color(0xFF1B5E20),
            fontSize = if (tier == AmountTier.HUGE) 22 else 20
        )
    }
}

private data class MoneyParticle(
    val emoji: String = listOf(
        "\uD83D\uDCB8", "\uD83D\uDCB0", "\u2728", "\uD83D\uDCB5", "\uD83C\uDF1F",
        "\uD83D\uDCB6", "\uD83E\uDE99", "\uD83D\uDCB3", "\uD83D\uDCB2", "\u2B50"
    ).random(),
    val startX: Float = Random.nextFloat(),
    val delay: Long = Random.nextLong(0, 1500),
    val duration: Int = Random.nextInt(1800, 3500),
    val size: Int = Random.nextInt(22, 44),
    val wobbleAmount: Float = Random.nextFloat() * 60f - 30f,
    val spinSpeed: Float = Random.nextFloat() * 720f - 360f
)

@Composable
private fun FallingEmoji(particle: MoneyParticle) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(particle.delay)
        started = true
    }

    if (!started) return

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = particle.duration, easing = LinearOutSlowInEasing)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val maxW = maxWidth
        val wobble = sin(progress * 3.14f * 2f) * particle.wobbleAmount
        Text(
            text = particle.emoji,
            fontSize = particle.size.sp,
            modifier = Modifier
                .offset(
                    x = maxW * particle.startX + wobble.dp,
                    y = maxH * progress - 60.dp
                )
                .alpha((1f - progress * 0.7f).coerceIn(0f, 1f))
                .graphicsLayer {
                    rotationZ = particle.spinSpeed * progress
                    scaleX = 1f + sin(progress * 6.28f) * 0.3f
                    scaleY = 1f + sin(progress * 6.28f) * 0.3f
                }
        )
    }
}

// ===== Big Pop Emoji (center explosion) =====

@Composable
private fun BigPopEmoji(emoji: String, delayMs: Long = 0) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(delayMs)
        started = true
    }

    val scale by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    val alpha by animateFloatAsState(
        targetValue = if (started) 0f else 1f,
        animationSpec = tween(durationMillis = 2000, delayMillis = 800)
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 90.sp,
            modifier = Modifier
                .scale(scale * 1.5f)
                .alpha(alpha)
                .graphicsLayer {
                    rotationZ = (1f - scale) * 30f
                }
        )
    }
}

// ===== Expense Animation (ghost flying away) =====

@Composable
private fun ExpenseAnimation(tier: AmountTier = AmountTier.SMALL) {
    val particleCount = when (tier) {
        AmountTier.MICRO -> 3
        AmountTier.SMALL -> 8
        AmountTier.MEDIUM -> 14
        AmountTier.LARGE -> 22
        AmountTier.HUGE -> 32
    }
    // Center emoji + intensity scaled to tier
    val centerEmoji = when (tier) {
        AmountTier.MICRO -> "\uD83C\uDF6A"
        AmountTier.SMALL -> "\uD83D\uDC7B"
        AmountTier.MEDIUM -> "\uD83D\uDE05"
        AmountTier.LARGE -> "\uD83D\uDE31"
        AmountTier.HUGE -> "\uD83D\uDCA5"
    }
    val emojiPool = when (tier) {
        AmountTier.MICRO -> listOf("\u2728", "\uD83C\uDF43", "\uD83D\uDCA8")
        AmountTier.SMALL -> listOf("\uD83D\uDC7B", "\uD83D\uDCB8", "\uD83D\uDCA8", "\u2728", "\uD83C\uDF43")
        AmountTier.MEDIUM -> listOf("\uD83D\uDCB8", "\uD83D\uDC94", "\uD83D\uDE2C", "\uD83D\uDD25", "\uD83D\uDCA8")
        AmountTier.LARGE -> listOf("\uD83D\uDCB8", "\uD83D\uDD25", "\uD83D\uDCC9", "\uD83D\uDE31", "\uD83D\uDCA5", "\u2622\uFE0F")
        AmountTier.HUGE -> listOf("\uD83D\uDCA5", "\uD83D\uDD25", "\uD83D\uDC80", "\uD83D\uDEA8", "\uD83D\uDCB8", "\uD83D\uDCC9", "\uD83D\uDE31")
    }
    val ghosts = remember {
        List(particleCount) {
            ExpenseParticle(
                emoji = emojiPool.random(),
                startX = 0.3f + Random.nextFloat() * 0.4f,
                startY = 0.4f + Random.nextFloat() * 0.2f,
                endX = Random.nextFloat(),
                endY = -0.2f,
                delay = Random.nextLong(0, 600),
                duration = Random.nextInt(1200, 2200),
                size = Random.nextInt(
                    if (tier == AmountTier.HUGE) 30 else 24,
                    if (tier == AmountTier.HUGE) 50 else 42
                ),
                spin = Random.nextFloat() * 540f - 270f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ghosts.forEach { particle ->
            FlyingAwayEmoji(particle)
        }

        // Big center pop
        BigPopEmoji(emoji = centerEmoji, delayMs = 100)

        // Toast with tier-specific message
        KawaiiToast(
            message = expenseMessagesForTier(tier).random(),
            gradient = Brush.horizontalGradient(
                listOf(Color(0xFFFFCDD2), Color(0xFFF8BBD0), Color(0xFFFFCDD2))
            ),
            textColor = Color(0xFF880E4F),
            fontSize = if (tier == AmountTier.HUGE) 20 else 18
        )
    }
}

private data class ExpenseParticle(
    val emoji: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val delay: Long,
    val duration: Int,
    val size: Int,
    val spin: Float
)

@Composable
private fun FlyingAwayEmoji(particle: ExpenseParticle) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(particle.delay)
        started = true
    }

    if (!started) return

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = particle.duration, easing = FastOutSlowInEasing)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val maxW = maxWidth
        val curX = particle.startX + (particle.endX - particle.startX) * progress
        val curY = particle.startY + (particle.endY - particle.startY) * progress

        Text(
            text = particle.emoji,
            fontSize = particle.size.sp,
            modifier = Modifier
                .offset(x = maxW * curX, y = maxH * curY)
                .alpha((1f - progress).coerceIn(0f, 1f))
                .graphicsLayer {
                    rotationZ = particle.spin * progress
                    scaleX = 1f + progress * 0.5f
                    scaleY = 1f + progress * 0.5f
                }
        )
    }
}

// ===== All Budgets Safe MEGA Celebration =====

@Composable
private fun AllBudgetsSafeCelebration() {
    val hearts = remember { List(25) { CelebrationParticle() } }

    Box(modifier = Modifier.fillMaxSize()) {
        // Confetti / hearts / stars explosion from center
        hearts.forEach { particle ->
            ExplodingParticle(particle)
        }

        // Big crown pop
        BigPopEmoji(emoji = "\uD83D\uDC51", delayMs = 100)

        // Second wave
        BigPopEmoji(emoji = "\uD83C\uDF89", delayMs = 600)

        // Banner slide-in
        CelebrationBanner(allBudgetsSafeMessages.random())
    }
}

private data class CelebrationParticle(
    val emoji: String = listOf(
        "\uD83D\uDC96", "\uD83D\uDC97", "\uD83D\uDC95", "\u2728", "\uD83C\uDF38",
        "\uD83C\uDF1F", "\uD83C\uDF89", "\uD83C\uDF8A", "\u2B50", "\uD83C\uDF3C",
        "\uD83E\uDD8B", "\uD83C\uDF80", "\u2764\uFE0F", "\uD83D\uDC9D", "\uD83D\uDCAB"
    ).random(),
    val angle: Float = Random.nextFloat() * 360f,
    val distance: Float = 0.2f + Random.nextFloat() * 0.4f,
    val delay: Long = Random.nextLong(0, 800),
    val duration: Int = Random.nextInt(1500, 3000),
    val size: Int = Random.nextInt(20, 40),
    val spin: Float = Random.nextFloat() * 720f - 360f
)

@Composable
private fun ExplodingParticle(particle: CelebrationParticle) {
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(particle.delay)
        started = true
    }

    if (!started) return

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = particle.duration, easing = FastOutSlowInEasing)
    )

    val angleRad = Math.toRadians(particle.angle.toDouble())
    val dx = (kotlin.math.cos(angleRad) * particle.distance).toFloat()
    val dy = (kotlin.math.sin(angleRad) * particle.distance).toFloat()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxH = maxHeight
        val maxW = maxWidth
        val centerX = maxW * 0.5f
        val centerY = maxH * 0.4f

        Text(
            text = particle.emoji,
            fontSize = particle.size.sp,
            modifier = Modifier
                .offset(
                    x = centerX + (maxW * dx * progress) - 20.dp,
                    y = centerY + (maxH * dy * progress) - 20.dp
                )
                .alpha((1f - progress * 0.8f).coerceIn(0f, 1f))
                .graphicsLayer {
                    rotationZ = particle.spin * progress
                    val bounceScale = 1f + sin(progress * 3.14f) * 0.5f
                    scaleX = bounceScale
                    scaleY = bounceScale
                }
        )
    }
}

@Composable
private fun CelebrationBanner(message: String) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    // Pulsing effect
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { -it * 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        ) + fadeIn() + scaleIn(initialScale = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 12.dp)
                .scale(pulseScale)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFFF8A9D),
                            Color(0xFFFFB3C6),
                            Color(0xFFFF8A9D),
                            Color(0xFFFF6B9D)
                        )
                    )
                )
                .padding(vertical = 20.dp, horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 22.sp
            )
        }
    }
}

// ===== Budget Alert Animation =====

@Composable
private fun BudgetAlertAnimation(categoryName: String) {
    // Shake emoji
    val infiniteTransition = rememberInfiniteTransition()
    val shakeOffset by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(80, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Big shaking emoji
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = scaleIn(initialScale = 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy))
            ) {
                Text(
                    text = "\uD83D\uDE3F",
                    fontSize = 80.sp,
                    modifier = Modifier.offset(x = shakeOffset.dp)
                )
            }
        }

        // Toast
        KawaiiToast(
            message = budgetAlertMessages.random().format(categoryName),
            gradient = Brush.horizontalGradient(
                listOf(Color(0xFFFFCDD2), Color(0xFFEF9A9A), Color(0xFFFFCDD2))
            ),
            textColor = Color(0xFFC62828),
            fontSize = 18
        )
    }
}

// ===== Kawaii Toast =====

@Composable
private fun KawaiiToast(
    message: String,
    gradient: Brush,
    textColor: Color,
    fontSize: Int = 16
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        visible = true
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it * 2 },
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn() + scaleIn(initialScale = 0.5f)
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(gradient)
                    .padding(horizontal = 32.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    fontSize = fontSize.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ===== Budget safe message (inline, under card) =====

@Composable
fun KawaiiBudgetSafeMessage(modifier: Modifier = Modifier) {
    if (!isRoseTheme()) return

    val message = remember { budgetSafeMessages.random() }

    // Subtle entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(500)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFF6B9D),
            fontWeight = FontWeight.SemiBold,
            modifier = modifier.padding(top = 6.dp, start = 4.dp),
            fontSize = 13.sp
        )
    }
}
