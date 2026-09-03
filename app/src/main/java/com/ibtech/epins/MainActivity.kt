package com.ibtech.epins

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.NumberFormat
import java.util.Locale

private const val API_BASE = BuildConfig.IBTECH_API_BASE_URL
private const val PREFS = "ibtech_secure_session"
private const val TOKEN = "token"
private const val PENDING_REF = "pending_payment_reference"

private val Navy = Color(0xFF101A35)
private val Green = Color(0xFF0C9B61)
private val Gold = Color(0xFFD6A62C)
private val Bg = Color(0xFFF5F8F7)
private val Networks = listOf("MTN", "GLO", "AIRTEL", "9MOBILE")
private val NetworkImages = mapOf("MTN" to R.drawable.mtn, "GLO" to R.drawable.glo, "AIRTEL" to R.drawable.airtel, "9MOBILE" to R.drawable._9mobile)
private val Denoms = listOf(100, 200, 500, 1000, 1500)

private data class User(val id: String, val name: String, val business: String?, val email: String?, val phone: String?, val wallet: Double, val role: String, val dvaStatus: String = "none", val dva: DedicatedAccount? = null)
private data class DedicatedAccount(val accountNumber: String, val bankName: String, val accountName: String)
private data class Card(val pin: String, val serial: String)
private data class Tx(val id: String, val network: String, val denom: Int, val qty: Int, val total: Double, val date: Long, val cards: List<Card> = emptyList())

class MainActivity : ComponentActivity() {
    private lateinit var api: Api
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        api = Api(this)
        setContent { App(api) }
    }
    override fun onResume() {
        super.onResume()
        // Payment verification is deliberately server-side. The app only asks
        // the backend to verify the reference it previously received.
        if (::api.isInitialized) api.verifyPendingPayment()
    }
}

private class Api(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    var token: String?
        get() = prefs.getString(TOKEN, null)
        set(value) { if (value == null) prefs.edit().remove(TOKEN).apply() else prefs.edit().putString(TOKEN, value).apply() }
    var pendingReference: String?
        get() = prefs.getString(PENDING_REF, null)
        set(value) { if (value == null) prefs.edit().remove(PENDING_REF).apply() else prefs.edit().putString(PENDING_REF, value).apply() }

    suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        try {
            val c = URL(API_BASE + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("Content-Type", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            c.disconnect()
            JSONObject(text.ifBlank { "{}" })
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", "Could not reach the IB-TECH server. Please check your internet connection.")
        }
    }

    suspend fun verifyPendingPayment(): JSONObject? {
        val ref = pendingReference ?: return null
        val r = request("POST", "/api/wallet/verify", JSONObject().put("reference", ref))
        if (r.optBoolean("ok") || r.optString("error").contains("already", true)) pendingReference = null
        return r
    }
    suspend fun logout() { request("POST", "/api/auth/logout", JSONObject()); clear() }
    fun clear() { token = null; pendingReference = null }
}

@Composable
private fun App(api: Api) {
    var user by remember { mutableStateOf<User?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        if (api.token != null) {
            val r = api.request("GET", "/api/me")
            if (r.optBoolean("ok")) user = r.optJSONObject("user")?.toUser() else api.clear()
        }
        loading = false
    }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, secondary = Gold, background = Bg, surface = Color.White)) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            user == null -> AuthScreen(api) { user = it }
            else -> HomeScreen(api, user!!) { api.clear(); user = null }
        }
    }
}

@Composable
private fun AuthScreen(api: Api, onLogin: (User) -> Unit) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var business by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, contentPadding = PaddingValues(vertical = 30.dp)) {
        item {
            Image(painterResource(R.drawable.ibtech_logo), null, Modifier.size(92.dp))
            Spacer(Modifier.height(8.dp))
            Text("IB-TECH ePINs", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(if (forgot) "Reset your password" else if (signup) "Create your reseller account" else "Secure recharge PIN reseller portal", color = Color.Gray)
            Spacer(Modifier.height(22.dp))
        }
        if (forgot) {
            item { Field("Email or phone", identifier) { identifier = it } }
            item { Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { busy = true; error = "" }) { Text(if (busy) "Please wait…" else "Send reset instructions") } }
            if (busy) item {
                LaunchedEffect(Unit) {
                    val r = api.request("POST", "/api/auth/forgot", JSONObject().put("identifier", identifier.trim()))
                    busy = false
                    error = if (r.optBoolean("ok")) r.optString("note", "If your account matches, reset instructions will be sent.") else r.optString("error", "Request failed.")
                }
            }
            if (error.isNotBlank()) item { Text(error, color = if (error.startsWith("If") || error.startsWith("A ")) Green else MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) }
            item { TextButton(onClick = { forgot = false; error = "" }) { Text("Back to login") } }
            return@LazyColumn
        }
        if (signup) {
            item { Field("Full name", name) { name = it } }
            item { Field("Business name (optional)", business) { business = it } }
        }
        item { Field("Email or phone", identifier) { identifier = it } }
        item { Field("Password", password, true) { password = it } }
        if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
        item {
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { busy = true; error = "" }) {
                Text(if (busy) "Please wait…" else if (signup) "Create account" else "Login")
            }
        }
        if (busy) item {
            LaunchedEffect(Unit) {
                val r = if (signup) {
                    val email = identifier.takeIf { it.contains("@") } ?: ""
                    val phone = identifier.takeIf { it.startsWith("0") } ?: ""
                    api.request("POST", "/api/auth/signup", JSONObject().put("name", name.trim()).put("business", business.trim()).put("phone", phone).put("email", email).put("password", password))
                } else api.request("POST", "/api/auth/login", JSONObject().put("identifier", identifier.trim()).put("password", password))
                busy = false
                if (r.optBoolean("ok")) { api.token = r.optString("token"); onLogin(r.getJSONObject("user").toUser()) }
                else error = r.optString("error", "Request failed.")
            }
        }
        item { TextButton(onClick = { signup = !signup; error = "" }) { Text(if (signup) "Already have an account? Login" else "New customer? Create account") } }
        if (!signup) item { TextButton(onClick = { forgot = true; error = "" }) { Text("Forgot password?") } }
    }
}

@Composable private fun Field(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), singleLine = true)
}

@Composable
private fun HomeScreen(api: Api, initialUser: User, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var user by remember { mutableStateOf(initialUser) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(refresh) { if (refresh > 0) api.request("GET", "/api/me").takeIf { it.optBoolean("ok") }?.optJSONObject("user")?.let { user = it.toUser() } }

    Scaffold(
        topBar = { TopAppBar(title = { Text("IB-TECH ePINs", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = { onLogout() }) { Icon(Icons.Default.Logout, "Logout") } }) },
        bottomBar = { NavigationBar { listOf("Home", "Buy PINs", "Wallet", "History").forEachIndexed { i, label ->
            NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(listOf(Icons.Default.Home, Icons.Default.ShoppingCart, Icons.Default.AccountBalanceWallet, Icons.Default.History)[i], label) }, label = { Text(label) })
        } } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                0 -> Dashboard(user) { tab = 1 }
                1 -> BuyScreen(api, user) { user = it; refresh++ }
                2 -> WalletScreen(api, user) { user = it; refresh++ }
                3 -> HistoryScreen(api)
            }
        }
    }
}

@Composable private fun Dashboard(user: User, goBuy: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Welcome, ${user.name}", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Buy genuine recharge ePINs quickly and securely.", color = Color.Gray) }
        item { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(20.dp)) { Text("Wallet balance", color = Color.Gray); Text(naira(user.wallet), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Green); Spacer(Modifier.height(10.dp)); Button(onClick = goBuy) { Text("Buy ePINs") } } } }
        item { Text("Networks", fontWeight = FontWeight.Bold, color = Navy) }
        items(Networks.chunked(2)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { net -> Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) { Image(painterResource(NetworkImages[net]!!), net, Modifier.size(42.dp)); Spacer(Modifier.height(5.dp)); Text(net, fontWeight = FontWeight.Bold, fontSize = 12.sp) } } } } }
        item { Text("Denominations: ₦100, ₦200, ₦500, ₦1,000 and ₦1,500", color = Color.DarkGray) }
        if (user.dvaStatus == "active" && user.dva != null) item { DvaCard(user.dva) }
    }
}

@Composable private fun DvaCard(account: DedicatedAccount) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Your dedicated bank account", fontWeight = FontWeight.Bold, color = Navy); Text(account.bankName); Text(account.accountNumber, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Green); Text(account.accountName, color = Color.Gray) } }
}

@Composable
private fun BuyScreen(api: Api, user: User, onDone: (User) -> Unit) {
    var network by remember { mutableStateOf("MTN") }
    var denom by remember { mutableIntStateOf(100) }
    var qty by remember { mutableIntStateOf(1) }
    var result by remember { mutableStateOf("") }
    var tx by remember { mutableStateOf<Tx?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showPins by remember { mutableStateOf(true) }
    val total = Math.round(denom * 0.97) * qty
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Buy ePINs", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy) }
        item { Text("Network", fontWeight = FontWeight.SemiBold) }
        item { FlowRowCompat(Networks) { selected -> network = selected } }
        item { Text("Denomination", fontWeight = FontWeight.SemiBold) }
        item { FlowRowCompat(Denoms.map { "₦$it" }) { selected -> denom = selected.removePrefix("₦").replace(",", "").toInt() } }
        item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Quantity"); Spacer(Modifier.weight(1f)); OutlinedButton(onClick = { if (qty > 1) qty-- }) { Text("−") }; Text("  $qty  "); OutlinedButton(onClick = { if (qty < 100) qty++ }) { Text("+") } } }
        item { Text("Total: ${naira(total.toDouble())}", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item { Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { busy = true; result = "" }) { Text(if (busy) "Processing…" else "Purchase PINs") } }
        if (busy) item { LaunchedEffect(Unit) { val r = api.request("POST", "/api/print", JSONObject().put("network", network).put("denom", denom).put("qty", qty)); busy = false; if (r.optBoolean("ok")) { tx = r.getJSONObject("tx").toTx(); onDone(user.copy(wallet = r.optDouble("wallet", user.wallet))); result = "Purchase successful" } else result = r.optString("error", "Purchase failed") } }
        if (result.isNotBlank()) item { Text(result, color = if (tx != null) Green else MaterialTheme.colorScheme.error) }
        tx?.let { purchase -> item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("Purchased PINs", fontWeight = FontWeight.Bold, color = Navy); Spacer(Modifier.weight(1f)); TextButton(onClick = { showPins = !showPins }) { Text(if (showPins) "Hide" else "Show") } }; purchase.cards.forEachIndexed { i, card -> PinRow(i + 1, card, showPins) } } } } }
    }
}

@Composable private fun FlowRowCompat(values: List<String>, onSelect: (String) -> Unit) {
    Column { values.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) { row.forEach { value -> FilterChip(selected = false, onClick = { onSelect(value) }, label = { Text(value) }) } } } }
}

@Composable private fun PinRow(index: Int, card: Card, visible: Boolean) {
    val context = LocalContext.current
    val display = if (visible) card.pin else "••••••••••"
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) { Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Text("$index.  PIN: $display", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); IconButton(onClick = { context.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("IB-TECH ePIN", card.pin)) }) { Icon(Icons.Default.ContentCopy, "Copy PIN") } }; Text("Serial: ${card.serial}", color = Color.Gray, fontSize = 13.sp) } }
}

@Composable
private fun WalletScreen(api: Api, user: User, onUpdate: (User) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var dvaLoading by remember { mutableStateOf(false) }
    var dvaStatus by remember { mutableStateOf(user.dvaStatus) }
    var dva by remember { mutableStateOf(user.dva) }
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Wallet", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy); Text(naira(user.wallet), fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Green) }
        item { Field("Amount to fund (₦)", amount) { amount = it } }
        item { Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { busy = true; msg = "" }) { Text(if (busy) "Starting payment…" else "Fund with Paystack") } }
        if (busy) item { LaunchedEffect(Unit) { val a = amount.toDoubleOrNull(); if (a == null || a <= 0) { msg = "Enter a valid amount."; busy = false } else { val r = api.request("POST", "/api/wallet/initiate", JSONObject().put("amount", a)); busy = false; if (r.optBoolean("ok")) { val ref = r.optString("reference"); api.pendingReference = ref; val url = r.optString("authorization_url"); if (url.startsWith("https://")) CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url)); msg = "After payment, return to the app and tap Verify payment." } else msg = r.optString("error", "Could not start payment.") } } }
        item { OutlinedButton(enabled = api.pendingReference != null, modifier = Modifier.fillMaxWidth(), onClick = { busy = true }) { Text("Verify payment") } }
        if (busy && api.pendingReference != null && amount.isNotBlank()) item { LaunchedEffect("verify-${api.pendingReference}") { val r = api.verifyPendingPayment(); busy = false; msg = if (r?.optBoolean("ok") == true) { val newWallet = r.optDouble("wallet", user.wallet); onUpdate(user.copy(wallet = newWallet)); "Payment verified. Wallet credited: ${naira(r.optDouble("credited", 0.0))}" } else r?.optString("error", "Payment is not yet confirmed. Try again shortly.") ?: "No pending payment." } }
        if (msg.isNotBlank()) item { Text(msg, color = if (msg.contains("verified", true) || msg.contains("credited", true)) Green else Color.DarkGray) }
        item { Divider() }
        item { Text("Bank transfer", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy); Text("Use your dedicated Paystack account for direct transfers when it is active.", color = Color.Gray) }
        item {
            when (dvaStatus) {
                "active" -> if (dva != null) DvaCard(dva!!)
                "pending" -> Text("Your dedicated account is being created. Please check again later.", color = Color.Gray)
                else -> Button(enabled = !dvaLoading, modifier = Modifier.fillMaxWidth(), onClick = { dvaLoading = true }) { Text(if (dvaLoading) "Creating account…" else "Create dedicated bank account") }
            }
        }
        if (dvaLoading) item { LaunchedEffect(Unit) { val r = api.request("POST", "/api/wallet/dedicated-account", JSONObject()); dvaLoading = false; if (r.optBoolean("ok")) { dvaStatus = r.optString("status", "none"); val a = r.optJSONObject("account"); dva = a?.toDva() } else msg = r.optString("error", "Could not create the account.") } }
    }
}

@Composable private fun HistoryScreen(api: Api) {
    var txs by remember { mutableStateOf<List<Tx>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { val r = api.request("GET", "/api/transactions"); if (r.optBoolean("ok")) { val a = r.optJSONArray("transactions") ?: JSONArray(); txs = List(a.length()) { a.getJSONObject(it).toTx() } }; busy = false }
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { Text("Transaction history", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy) }; if (busy) item { CircularProgressIndicator() }; if (!busy && txs.isEmpty()) item { Text("No transactions yet.", color = Color.Gray) }; items(txs) { t -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text("${t.network} ₦${t.denom} × ${t.qty}", fontWeight = FontWeight.Bold); Text(naira(t.total)); Text("${t.cards.size} PIN(s) available", color = Green); if (t.cards.isNotEmpty()) t.cards.take(3).forEachIndexed { i, c -> Text("${i + 1}. ${c.pin}", fontSize = 12.sp, color = Color.Gray) } } } } }
}

private fun JSONObject.toUser(): User = User(optString("id"), optString("name"), optString("business").takeIf { it.isNotBlank() }, optString("email").takeIf { it.isNotBlank() }, optString("phone").takeIf { it.isNotBlank() }, optDouble("wallet"), optString("role"), optString("dedicatedAccountStatus", "none"), optJSONObject("dedicatedAccount")?.toDva())
private fun JSONObject.toDva() = DedicatedAccount(optString("accountNumber"), optString("bankName"), optString("accountName"))
private fun JSONObject.toTx(): Tx { val a = optJSONArray("cards") ?: JSONArray(); return Tx(optString("id"), optString("network"), optInt("denom"), optInt("qty"), optDouble("total"), optLong("date"), List(a.length()) { val c = a.getJSONObject(it); Card(c.optString("pin"), c.optString("serial")) }) }
private fun naira(n: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "NG")).format(n).replace("NGN", "₦")
