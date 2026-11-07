package com.alphatrade.ai

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alphatrade.ai.phantom.PhantomDeepLinkManager
import org.json.JSONArray
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val Bg = Color(0xFF080D18)
private val Panel = Color(0xFF121B2B)
private val Accent = Color(0xFF36D6A3)
private val Muted = Color(0xFF91A0B7)
private data class Market(val symbol: String, val price: Double, val change: Double, val rsi: Double, val emaFast: Double, val emaSlow: Double, val macd: Double, val signal: String, val candles: Int)

class MainActivity : ComponentActivity() {
    private lateinit var phantomManager: PhantomDeepLinkManager
    private val phantomStatus = mutableStateOf("غير متصل بمحفظة Phantom")
    private val walletAddress = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phantomManager = PhantomDeepLinkManager(this)
        walletAddress.value = phantomManager.walletAddress()
        if (phantomManager.isConnected()) phantomStatus.value = "محفظة Phantom متصلة"
        handlePhantomIntent(intent)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                AlphaApp(
                    phantomStatus = phantomStatus.value,
                    walletAddress = walletAddress.value,
                    onConnectPhantom = { connectPhantom() },
                    onSignMessage = { signPhantomMessage() },
                    onDisconnectPhantom = { disconnectPhantom() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePhantomIntent(intent)
    }

    private fun connectPhantom() {
        try {
            if (BuildConfig.PHANTOM_APP_URL.isBlank()) {
                phantomStatus.value = "يلزم ضبط PHANTOM_APP_URL كرابط HTTPS عام لصفحة تعريف ALPHA TRADE AI قبل الاتصال."
                return
            }
            startActivity(phantomManager.beginConnect(BuildConfig.PHANTOM_APP_URL))
            phantomStatus.value = "بانتظار موافقتك داخل Phantom…"
        } catch (e: Exception) {
            phantomStatus.value = "تعذر بدء الاتصال: ${e.message ?: "خطأ غير معروف"}"
        }
    }

    private fun signPhantomMessage() {
        try {
            startActivity(phantomManager.beginSignInMessage())
            phantomStatus.value = "راجع رسالة إثبات الملكية ووافق عليها داخل Phantom."
        } catch (e: Exception) {
            phantomStatus.value = "تعذر طلب التوقيع: ${e.message ?: "خطأ غير معروف"}"
        }
    }

    private fun disconnectPhantom() {
        try {
            phantomManager.disconnect()
            walletAddress.value = null
            phantomStatus.value = "تم فصل جلسة التطبيق محلياً. إذا منحت صلاحيات أخرى سابقاً، راجع Connected Apps داخل Phantom."
        } catch (e: Exception) {
            phantomStatus.value = "تعذر فصل الجلسة: ${e.message ?: "خطأ غير معروف"}"
        }
    }

    private fun handlePhantomIntent(intent: Intent?) {
        try {
            val result = phantomManager.handleCallback(intent?.data) ?: return
            phantomStatus.value = result.message
            walletAddress.value = result.walletAddress ?: phantomManager.walletAddress()
        } catch (e: Exception) {
            phantomStatus.value = "فشل التحقق من رد Phantom: ${e.message ?: "رد غير صالح"}"
        }
    }
}

@Composable
private fun AlphaApp(
    phantomStatus: String,
    walletAddress: String?,
    onConnectPhantom: () -> Unit,
    onSignMessage: () -> Unit,
    onDisconnectPhantom: () -> Unit
) {
    var symbol by remember { mutableStateOf("BTCUSDT") }
    var input by remember { mutableStateOf("BTCUSDT") }
    var market by remember { mutableStateOf<Market?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refreshed by remember { mutableStateOf("لم يتم التحديث بعد") }
    val scope = rememberCoroutineScope()

    fun load() {
        val clean = input.trim().uppercase().filter { it.isLetterOrDigit() }
        if (clean.length !in 5..15) { error = "أدخل رمزاً صحيحاً مثل BTCUSDT"; return }
        symbol = clean
        busy = true; error = ""
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchMarket(clean) }
                market = result
                refreshed = "آخر تحديث: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            } catch (e: Exception) { error = "تعذر جلب البيانات: ${e.message ?: "تحقق من الإنترنت"}" }
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
        LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("ALPHA TRADE AI", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Black)
                        Text("مركز ذكاء الأسواق", color = Muted, fontSize = 12.sp)
                    }
                    Text("● LIVE DATA", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("محفظة Phantom · Solana", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(phantomStatus, color = Muted, fontSize = 12.sp)
                        if (walletAddress != null) Text("${walletAddress.take(6)}…${walletAddress.takeLast(6)}", color = Accent, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onConnectPhantom, modifier = Modifier.weight(1f), enabled = walletAddress == null) { Text("ربط Phantom") }
                            OutlinedButton(onClick = onSignMessage, modifier = Modifier.weight(1f), enabled = walletAddress != null) { Text("إثبات الملكية") }
                        }
                        if (walletAddress != null) {
                            TextButton(onClick = onDisconnectPhantom) { Text("فصل الجلسة من هذا التطبيق") }
                        }
                        Text("الاتصال لا يمنح صلاحية تداول تلقائي. التوقيع هنا لرسالة إثبات فقط ولا ينفذ معاملة أو ينقل أموالاً.", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("السوق الفوري · SPOT", color = Muted, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text("رمز التداول") })
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { load() }, enabled = !busy) { Text(if (busy) "..." else "تحليل") }
                        }
                        Text(refreshed, color = Muted, fontSize = 11.sp)
                        if (error.isNotEmpty()) Text(error, color = Color(0xFFFF7777), fontSize = 12.sp)
                    }
                }
            }
            market?.let { m ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(m.symbol, color = Muted, fontWeight = FontWeight.Bold)
                            Text(String.format(java.util.Locale.US, "%,.6f", m.price), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Text(String.format(java.util.Locale.US, "%+.2f%% خلال 24 ساعة", m.change), color = if (m.change >= 0) Accent else Color(0xFFFF6B78), fontWeight = FontWeight.Bold)
                            Divider(color = Color(0xFF26344A))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Metric("RSI 14", "%.1f".format(m.rsi)); Metric("EMA 9", "%.4f".format(m.emaFast)); Metric("EMA 21", "%.4f".format(m.emaSlow)) }
                            Text("MACD histogram: %.6f".format(m.macd), color = Muted, fontSize = 12.sp)
                            Text("قراءة المؤشرات: ${m.signal}", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("تم تحليل ${m.candles} شمعة · إطار 15 دقيقة", color = Muted, fontSize = 11.sp)
                        }
                    }
                }
                item { Text("وكلاء التحليل الستة", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                val agents = listOf(
                    "Scout" to "يراقب حركة السعر والتغيرات اللافتة",
                    "Filter" to "يفلتر الإشارات وفق المؤشرات المتاحة",
                    "Bear" to "يفحص إشارات الضعف والمخاطر الهبوطية",
                    "Analyst" to "يجمع RSI وEMA وMACD في قراءة واحدة",
                    "Executor" to "معطل: لا توجد أوامر حقيقية في هذه النسخة",
                    "Risk Manager" to "يفرض وضع التحليل فقط ويمنع التنفيذ"
                )
                items(agents.size) { i -> AgentCard(agents[i].first, agents[i].second, if (i == 4) "LOCKED" else "READY") }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2015)), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("وضع الحماية مفعل", color = Color(0xFFFFD28A), fontWeight = FontWeight.Bold)
                            Text("هذه النسخة تعرض بيانات السوق وتحليلاً تعليمياً فقط. لا تنفذ صفقات ولا تضمن الأرباح. لا تدخل مفاتيح API أو عبارة استرداد المحفظة هنا.", color = Color(0xFFFFE7C2), fontSize = 12.sp)
                        }
                    }
                }
            }
            item { Text("مصدر البيانات: Binance Public REST API · لا يحتاج مفتاحاً", color = Muted, fontSize = 10.sp) }
        }
    }
}

@Composable private fun Metric(label: String, value: String) { Column { Text(label, color = Muted, fontSize = 10.sp); Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) } }
@Composable private fun AgentCard(name: String, detail: String, status: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(name, color = Color.White, fontWeight = FontWeight.Bold); Text(detail, color = Muted, fontSize = 11.sp) }
            Text(status, color = if (status == "LOCKED") Color(0xFFFFC66D) else Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun fetchMarket(symbol: String): Market {
    val encoded = URLEncoder.encode(symbol, "UTF-8")
    val ticker = URL("https://api.binance.com/api/v3/ticker/24hr?symbol=$encoded").readText()
    val t = org.json.JSONObject(ticker)
    val raw = URL("https://api.binance.com/api/v3/klines?symbol=$encoded&interval=15m&limit=100").readText()
    val arr = JSONArray(raw)
    val closes = (0 until arr.length()).map { arr.getJSONArray(it).getString(4).toDouble() }
    if (closes.size < 30) error("بيانات تاريخية غير كافية")
    val ema9 = ema(closes, 9); val ema21 = ema(closes, 21)
    val ema12 = ema(closes, 12); val ema26 = ema(closes, 26)
    val macdLine = ema12 - ema26
    val rsi = rsi(closes, 14)
    val signal = when {
        rsi >= 70 && ema9 < ema21 -> "زخم مرتفع مع تقاطع ضعيف؛ الحذر من التصحيح"
        rsi >= 70 -> "تشبع شرائي محتمل؛ تجنب مطاردة السعر"
        rsi <= 30 && ema9 > ema21 -> "ارتداد محتمل؛ يحتاج تأكيداً إضافياً"
        rsi <= 30 -> "ضغط بيع مرتفع؛ لا تعتبره إشارة شراء وحدها"
        ema9 > ema21 && macdLine > 0 -> "ميل صاعد وفق المؤشرات، وليس توصية شراء"
        ema9 < ema21 && macdLine < 0 -> "ميل هابط وفق المؤشرات، وليس توصية بيع"
        else -> "اتجاه غير حاسم؛ انتظار تأكيد أفضل"
    }
    return Market(symbol, t.getString("lastPrice").toDouble(), t.getString("priceChangePercent").toDouble(), rsi, ema9, ema21, macdLine, signal, closes.size)
}
private fun ema(values: List<Double>, period: Int): Double {
    val k = 2.0 / (period + 1.0)
    var result = values.take(period).average()
    for (v in values.drop(period)) result = v * k + result * (1 - k)
    return result
}
private fun rsi(values: List<Double>, period: Int): Double {
    val changes = values.zipWithNext { a, b -> b - a }.takeLast(period)
    val gain = changes.filter { it > 0 }.sum() / period
    val loss = -changes.filter { it < 0 }.sum() / period
    if (loss == 0.0) return 100.0
    val rs = gain / loss
    return 100.0 - (100.0 / (1.0 + rs))
}
