package ir.hamrahshare

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

private val Ink = Color(0xFF100D1C)
private val Panel = Color(0xFF211A35)
private val Purple = Color(0xFF9B75FF)
private val Lilac = Color(0xFFD5C5FF)

class MainActivity : ComponentActivity() {
    private var server: ReceiveServer? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val folder = File(getExternalFilesDir(null), "Received").apply { mkdirs() }
        server = ReceiveServer(folder).also { try { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) } catch (_: Exception) {} }
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary=Purple, background=Ink, surface=Panel)) {
            HamrahApp(folder, server?.listeningPort ?: 8765)
        } }
    }
    override fun onDestroy() { server?.stop(); super.onDestroy() }
}

private class ReceiveServer(private val folder: File) : NanoHTTPD(8765) {
    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.GET && session.uri == "/") return newFixedLengthResponse("Hamrah Share ready")
        if (session.method == Method.POST && session.uri == "/upload") {
            val files = HashMap<String, String>()
            return try {
                session.parseBody(files)
                val name = session.parameters["name"]?.firstOrNull()?.substringAfterLast('/')?.substringAfterLast('\\') ?: "received_${System.currentTimeMillis()}"
                val temp = files["postData"]?.let(::File) ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "No file")
                temp.copyTo(File(folder, name), overwrite = true)
                newFixedLengthResponse("OK")
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", e.message ?: "error")
            }
        }
        if (session.method == Method.GET && session.uri == "/list") {
            val body = folder.listFiles()?.joinToString("\n") { it.name } ?: ""
            return newFixedLengthResponse(body)
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
    }
}

@Composable
private fun HamrahApp(folder: File, port: Int) {
    val context = LocalContext.current
    val tv = (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    var page by remember { mutableStateOf("home") }
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var message by remember { mutableStateOf("آماده دریافت فایل") }
    var files by remember { mutableStateOf(folder.listFiles()?.toList() ?: emptyList()) }
    val ip = remember { localIp() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        try {
            val info = NsdServiceInfo().apply { serviceName = "Hamrah-${android.os.Build.MODEL}"; serviceType = "_http._tcp."; setPort(port) }
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            })
        } catch (_: Exception) {}
    }
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Ink, Color(0xFF25183D), Ink))).padding(if(tv) 34.dp else 18.dp)) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                Column {
                    Text("همراه‌ share", color=Color.White, fontSize=if(tv)  thirty() else 27.sp, fontWeight=FontWeight.Bold)
                    Text("انتقال آسان فایل بین گوشی و تلویزیون", color=Lilac, fontSize=13.sp)
                }
                Surface(color=Color(0xFF34264F), shape=RoundedCornerShape(50)) { Text(if(tv) "نسخه تلویزیون" else "نسخه گوشی", color=Lilac, modifier=Modifier.padding(12.dp)) }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp), modifier=Modifier.fillMaxWidth()) {
                ActionCard("ارسال فایل", "انتخاب و ارسال به دستگاه دیگر", Icons.Default.Upload, Modifier.weight(1f)) { page="send" }
                ActionCard("دریافت فایل", "آماده دریافت روی شبکه Wi-Fi", Icons.Default.Download, Modifier.weight(1f)) { page="receive" }
                ActionCard("فایل‌های من", "مدیریت فایل‌های دریافتی", Icons.Default.Folder, Modifier.weight(1f)) { files=folder.listFiles()?.toList() ?: emptyList(); page="files" }
            }
            when(page) {
                "send" -> SendPanel(context, ip, port, { page="home" }, { message=it })
                "receive" -> InfoPanel("دریافت فایل", "گوشی و تلویزیون را به یک شبکه Wi-Fi وصل کن.\nآدرس این دستگاه:", "http://$ip:$port", { page="home" })
                "files" -> FilePanel(files, { f -> openFile(context, f) }, { f -> f.delete(); files=folder.listFiles()?.toList() ?: emptyList() }, { page="home" })
                else -> {
                    Text("وضعیت اتصال", color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Bold)
                    Surface(color=Panel, shape=RoundedCornerShape(18.dp), modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text("●  آماده به کار", color=Color(0xFF8FE3B0), fontWeight=FontWeight.Bold)
                            Text("آدرس شبکه: $ip:$port", color=Color.White)
                            Text("برای انتقال، هر دو دستگاه باید به یک Wi-Fi متصل باشند.", color=Lilac)
                        }
                    }
                    Text(message, color=Lilac)
                }
            }
        }
    }
}

private fun thirty() = 34.sp

@Composable
private fun ActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier=Modifier, onClick:()->Unit) {
    Surface(color=Panel, shape=RoundedCornerShape(20.dp), modifier=modifier.height(150.dp).clickable { onClick() }.focusable()) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.SpaceBetween) {
            Icon(icon, null, tint=Purple, modifier=Modifier.size(30.dp))
            Column { Text(title, color=Color.White, fontWeight=FontWeight.Bold, fontSize=18.sp); Text(subtitle, color=Lilac, fontSize=11.sp) }
        }
    }
}

@Composable
private fun InfoPanel(title:String, description:String, address:String, back:()->Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(22.dp), verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(title, color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
        Text(description, color=Lilac)
        Text(address, color=Color.White, fontSize=18.sp, fontWeight=FontWeight.Bold)
        Button(onClick=back) { Text("بازگشت") }
    }
}

@Composable
private fun SendPanel(context:Context, ip:String, port:Int, back:()->Unit, status:(String)->Unit) {
    var selected by remember { mutableStateOf<android.net.Uri?>(null) }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { selected=it }
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(22.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("ارسال فایل", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
        Text("فایل را انتخاب کن؛ سپس آدرس دستگاه مقصد را وارد کن.", color=Lilac)
        Button(onClick={ launcher.launch("*/*") }) { Text("انتخاب فایل") }
        selected?.let { Text(it.lastPathSegment ?: "فایل انتخاب شد", color=Color.White) }
        var target by remember { mutableStateOf("http://$ip:$port") }
        OutlinedTextField(value=target, onValueChange={target=it}, label={Text("آدرس دستگاه مقصد")}, singleLine=true)
        Button(onClick={
            val uri=selected ?: return@Button
            Thread {
                try {
                    val name = queryName(context, uri)
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: byteArrayOf()
                    val boundary="----HamrahBoundary"
                    val url=java.net.URL(target.trimEnd('/')+"/upload?name="+java.net.URLEncoder.encode(name,"UTF-8"))
                    val conn=url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod="POST"; conn.doOutput=true; conn.setRequestProperty("Content-Type","application/octet-stream")
                    conn.outputStream.use { it.write(bytes) }
                    val ok=conn.responseCode in 200..299
                    (context as? Activity)?.runOnUiThread { status(if(ok) "ارسال با موفقیت انجام شد" else "ارسال ناموفق بود") }
                    conn.disconnect()
                } catch(e:Exception) { (context as? Activity)?.runOnUiThread { status("خطا در ارسال: ${e.message}") } }
            }.start()
        }) { Text("ارسال") }
        Button(onClick=back, colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF493B61))) { Text("بازگشت") }
    }
}

@Composable
private fun FilePanel(files:List<File>, open:(File)->Unit, delete:(File)->Unit, back:()->Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Text("فایل‌های دریافتی", color=Color.White, fontSize=22.sp, fontWeight=FontWeight.Bold)
            TextButton(onClick=back) { Text("بازگشت") }
        }
        if(files.isEmpty()) Text("هنوز فایلی دریافت نشده است.", color=Lilac, modifier=Modifier.padding(16.dp))
        LazyColumn { items(files, key={it.absolutePath}) { file ->
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(file.name, color=Color.White); Text("${file.length()/1024} KB", color=Lilac, fontSize=11.sp) }
                TextButton(onClick={open(file)}) { Text("باز کردن") }
                TextButton(onClick={delete(file)}) { Text("حذف", color=Color(0xFFFF8B9A)) }
            }
            HorizontalDivider(color=Color(0xFF403653))
        } }
    }
}

private fun queryName(context:Context, uri:android.net.Uri):String {
    var name=uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i=c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if(i>=0 && c.moveToFirst()) name=c.getString(i) ?: name
    }
    return name.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
}
private fun openFile(context:Context, file:File) {
    try {
        val uri=androidx.core.content.FileProvider.getUriForFile(context, context.packageName+".provider", file)
        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri, "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "باز کردن فایل"))
    } catch (_:Exception) { Toast.makeText(context, "برنامه‌ای برای باز کردن این فایل پیدا نشد", Toast.LENGTH_SHORT).show() }
}
private fun localIp():String {
    try {
        Collections.list(NetworkInterface.getNetworkInterfaces()).forEach { ni ->
            Collections.list(ni.inetAddresses).forEach { a -> if(!a.isLoopbackAddress && a is InetAddress && a.hostAddress?.contains(':')==false) return a.hostAddress ?: "0.0.0.0" }
        }
    } catch (_:Exception) {}
    return "0.0.0.0"
}
