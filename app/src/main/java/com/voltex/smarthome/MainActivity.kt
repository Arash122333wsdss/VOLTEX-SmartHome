package com.voltex.smarthome

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private object C {
    const val BG = 0xFF08090D.toInt(); const val CARD = 0xFF17151A.toInt(); const val INNER = 0xFF211B1B.toInt()
    const val RED = 0xFFFF343B.toInt(); const val YELLOW = 0xFFFFC928.toInt(); const val WHITE = 0xFFFFF9F0.toInt()
    const val MUTED = 0xFFAAA3A0.toInt(); const val GREEN = 0xFF55D6A0.toInt(); const val BORDER = 0xFF593027.toInt()
}
private data class Lamp(val on: Boolean, val src: String, val ago: Long)

private class Vault(private val a: Activity) {
    private val prefs = a.getSharedPreferences("vault", 0)
    private val alias = "voltex_aes_key_v3"
    private fun key(): SecretKey {
        val ks = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }
    fun save(value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        val data = c.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("iv", Base64.encodeToString(c.iv, Base64.NO_WRAP)).putString("data", Base64.encodeToString(data, Base64.NO_WRAP)).apply()
    }
    fun read(): String? {
        return try {
            val iv = prefs.getString("iv", null) ?: return null
            val data = prefs.getString("data", null) ?: return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)), StandardCharsets.UTF_8)
        } catch (_: Exception) { null }
    }
    fun clear() { prefs.edit().clear().apply() }
}

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor(); private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("voltex_settings", 0) }; private val vault by lazy { Vault(this) }
    private var ip = "192.168.1.100"; private var pass = ""; private var connected = false; private var busy = false
    private var lamps: List<Lamp>? = null; private var status: TextView? = null; private var page: LinearLayout? = null
    private val poll = object : Runnable { override fun run() { if (connected && !isFinishing) fetchState(); main.postDelayed(this, 3000) } }

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=C.BG; window.navigationBarColor=C.BG
        ip = prefs.getString("ip", "192.168.1.100") ?: "192.168.1.100"
        if (prefs.getBoolean("remember", false)) { val saved=vault.read(); if (!saved.isNullOrEmpty()) { pass=saved; login(ip, pass, true); } else showLogin() } else showLogin()
        main.postDelayed(poll, 3000)
    }
    override fun onDestroy() { main.removeCallbacks(poll); io.shutdownNow(); super.onDestroy() }
    private fun d(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun bg(color:Int, radius:Int=16, stroke:Int=0)=GradientDrawable().apply { setColor(color); cornerRadius=d(radius).toFloat(); if(stroke!=0)setStroke(d(1),stroke) }
    private fun txt(s:String,size:Float,color:Int=C.WHITE,bold:Boolean=false)=TextView(this).apply { text=s;textSize=size;setTextColor(color); if(bold)setTypeface(typeface,Typeface.BOLD); gravity=Gravity.CENTER_VERTICAL; textDirection=View.TEXT_DIRECTION_RTL }
    private fun btn(s:String,primary:Boolean=false)=Button(this).apply { text=s;textSize=13f;isAllCaps=false;setTextColor(if(primary) 0xFF21120B.toInt() else C.WHITE);background=bg(if(primary) C.YELLOW else 0xFF251A1A.toInt(),15,if(primary)0 else C.BORDER);minHeight=d(48);minimumHeight=d(48);setPadding(d(10),0,d(10),0) }
    private fun edit(hint:String,value:String="",secret:Boolean=false)=EditText(this).apply { setSingleLine(true);textSize=15f;setTextColor(C.WHITE);setHintTextColor(C.MUTED);setHint(hint);setText(value);background=bg(C.INNER,12,C.BORDER);setPadding(d(13),d(8),d(13),d(8)); if(secret) inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD; layoutParams=LinearLayout.LayoutParams(-1,d(52)).apply { bottomMargin=d(10) } }
    private fun screen():LinearLayout { val outer=LinearLayout(this).apply{orientation=1;background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(0xFF09090D.toInt(),0xFF1A0D0E.toInt(),0xFF09090D.toInt()))};val sc=ScrollView(this).apply{fillViewport=true};val inner=LinearLayout(this).apply{orientation=1;setPadding(d(20),d(22),d(20),d(28))};sc.addView(inner);outer.addView(sc,LinearLayout.LayoutParams(-1,0,1f));setContentView(outer);page=inner;return inner }
    private fun brand(p:LinearLayout, sub:String) {
        val header=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
        val logo=ImageView(this).apply{setImageResource(com.voltex.smarthome.R.mipmap.ic_launcher);scaleType=ImageView.ScaleType.CENTER_CROP}
        header.addView(logo,LinearLayout.LayoutParams(d(58),d(58)).apply{marginEnd=d(13)})
        val words=LinearLayout(this).apply{orientation=1}
        words.addView(txt("VOLTEX",29f,C.WHITE,true));words.addView(txt(sub,10f,C.YELLOW,true));header.addView(words)
        p.addView(header)
        p.addView(View(this).apply{background=GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,intArrayOf(C.RED,C.YELLOW,0x00333333))},LinearLayout.LayoutParams(-1,d(2)).apply{topMargin=d(16);bottomMargin=d(18)})
    }
    private fun showLogin(error:String?=null) {
        connected=false; val p=screen();p.gravity=Gravity.CENTER_VERTICAL;brand(p,"SMART HOME  •  CONTROL YOUR WORLD")
        val card=LinearLayout(this).apply{orientation=1;background=bg(0xF0181417.toInt(),24,0xFF78402A.toInt());elevation=d(10).toFloat();setPadding(d(20),d(22),d(20),d(20))};p.addView(card)
        card.addView(txt("خوش آمدی به خانه هوشمندت",22f,C.WHITE,true));card.addView(txt("همه چیز، تحت کنترل توست",12f,C.YELLOW,true).apply{setPadding(0,d(7),0,d(18))})
        val username=edit("نام کاربری", "admin");username.isEnabled=false;username.alpha=.8f
        val password=edit("رمز فعلی پنل ESP32", "", true);card.addView(txt("نام کاربری",11f,C.MUTED,true));card.addView(username);card.addView(txt("رمز عبور پنل",11f,C.MUTED,true));card.addView(password)
        val remember=CheckBox(this).apply{text="مرا به خاطر بسپار";textSize=13f;setTextColor(C.MUTED);isChecked=prefs.getBoolean("remember",true);buttonTintList=android.content.res.ColorStateList.valueOf(C.YELLOW)};card.addView(remember)
        if(error!=null)card.addView(txt(error,12f,C.RED).apply{setPadding(0,d(8),0,d(8))})
        val go=btn("اتصال امن به خانه  →",true);card.addView(go,LinearLayout.LayoutParams(-1,d(54)).apply{topMargin=d(15)})
        go.setOnClickListener { val secret=password.text.toString();if(secret.isEmpty()){Toast.makeText(this,"رمز را وارد کن",Toast.LENGTH_SHORT).show();return@setOnClickListener};go.isEnabled=false;go.text="در حال بررسی...";login(ip,secret,remember.isChecked){ok,msg->if(!ok)showLogin(msg)} }
        p.addView(txt("اتصال محلی و امن  •  VOLTEX SMART HOME",10f,C.MUTED).apply{gravity=Gravity.CENTER;setPadding(0,d(18),0,0)})
    }
    private data class Reply(val code:Int,val body:String)
    private fun request(host:String,secret:String,method:String,path:String,body:String?=null):Reply {
        var c:HttpURLConnection?=null
        return try {
            c=URL("http://$host$path").openConnection() as HttpURLConnection
            c.requestMethod=method;c.connectTimeout=5000;c.readTimeout=5000;c.useCaches=false
            c.setRequestProperty("Accept","application/json");c.setRequestProperty("Cache-Control","no-cache")
            // ESP32 WebServer.authenticate(PANEL_USER, panelPass): HTTP Basic, username is exactly admin.
            val token=Base64.encodeToString("admin:$secret".toByteArray(StandardCharsets.UTF_8),Base64.NO_WRAP)
            c.setRequestProperty("Authorization","Basic $token")
            if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/x-www-form-urlencoded; charset=UTF-8");c.outputStream.use{it.write(body.toByteArray(StandardCharsets.UTF_8))}}
            val code=c.responseCode;val stream=if(code in 200..399)c.inputStream else c.errorStream
            Reply(code,stream?.bufferedReader(StandardCharsets.UTF_8)?.use{it.readText()}?:"")
        } catch(e:Exception){Reply(-1,e.javaClass.simpleName)} finally{c?.disconnect()}
    }
    private fun parse(body:String):Boolean=try { val a=JSONObject(body).getJSONArray("lamps");if(a.length()!=4)false else {lamps=(0..3).map{val x=a.getJSONObject(it);Lamp(x.getBoolean("on"),x.optString("src","boot"),x.optLong("ago",0))};true} } catch(_:Exception){false}
    private fun login(host:String,secret:String,remember:Boolean,done:((Boolean,String?)->Unit)?=null) { io.execute { val r=request(host,secret,"GET","/api/state");val valid=r.code in 200..299 && parse(r.body);main.post {
        if(valid){ip=host;pass=secret;connected=true;prefs.edit().putString("ip",ip).putBoolean("remember",remember).apply();if(remember)vault.save(secret) else vault.clear();showDashboard();done?.invoke(true,null)}
        else {connected=false;val msg=when(r.code){401->"رمز عبور اشتباه است (HTTP 401). نام کاربری ثابت admin است.";-1->"به ESP32 وصل نشدم؛ Wi-Fi و IP را بررسی کن.";else->"پاسخ نامعتبر از ESP32 (HTTP ${r.code}). ${r.body.take(70)}"};done?.invoke(false,msg);if(done==null)showLogin(msg)}
    } } }
    private fun fetchState(){if(busy)return;io.execute{val r=request(ip,pass,"GET","/api/state");main.post{if(r.code==200&&parse(r.body)){status?.apply{text="● متصل به کنترلر";setTextColor(C.GREEN)};drawRooms()}else if(r.code==401){connected=false;vault.clear();prefs.edit().putBoolean("remember",false).apply();showLogin("رمز پنل تغییر کرده؛ با رمز جدید وارد شو.")}else{status?.apply{text="● ارتباط قطع است؛ آخرین وضعیت حفظ شده";setTextColor(C.RED)}}}}}
    private fun showDashboard(){val p=screen();val top=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL};val col=LinearLayout(this).apply{orientation=1};col.addView(txt("VOLTEX",27f,C.WHITE,true));col.addView(txt("خانه هوشمند تو، هوشمندتر",10f,C.YELLOW,true));top.addView(col,LinearLayout.LayoutParams(0,-2,1f));val settings=btn("⚙ تنظیمات");top.addView(settings);p.addView(top)
        status=txt("●  کنترلر متصل است  |  شبکه محلی",12f,C.GREEN,true).apply{background=bg(0xFF14221D.toInt(),14,0xFF285640.toInt());setPadding(d(14),d(14),d(14),d(14))};p.addView(status,LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(18);bottomMargin=d(14)})
        val row=LinearLayout(this).apply{orientation=0};val on=btn("☀  روشن کردن همه",true);val off=btn("◐  خاموش کردن همه");row.addView(on,LinearLayout.LayoutParams(0,d(50),1f).apply{marginEnd=d(5)});row.addView(off,LinearLayout.LayoutParams(0,d(50),1f).apply{marginStart=d(5)});p.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(16)});on.setOnClickListener{command("/api/all","state=1")};off.setOnClickListener{command("/api/all","state=0")};settings.setOnClickListener{settingsDialog()};drawRooms();p.addView(txt("VOLTEX  •  PRIVATE LOCAL NETWORK",10f,C.MUTED).apply{gravity=Gravity.CENTER;setPadding(0,d(18),0,0)})
    }
    private fun roomName(i:Int)=prefs.getString("room$i",if(i==0)"اتاق مامان" else "اتاق خودمون")!!
    private fun lampName(i:Int)=prefs.getString("lamp$i",if(i%2==0)"لامپ دیواری" else "لامپ آویز")!!
    private fun rename(title:String,initial:String,save:(String)->Unit){val input=edit("نام جدید",initial);AlertDialog.Builder(this).setTitle(title).setView(input).setNegativeButton("لغو",null).setPositiveButton("ذخیره"){_,_->val s=input.text.toString().trim();if(s.isNotEmpty()&&s.length<=30)save(s)else Toast.makeText(this,"نام معتبر نیست",Toast.LENGTH_SHORT).show()}.show()}
    private fun drawRooms(){val p=page?:return;if(!connected)return;while(p.childCount>3)p.removeViewAt(3);for(room in 0..1){val card=LinearLayout(this).apply{orientation=1;background=bg(0xFF171418.toInt(),20,0xFF553026.toInt());elevation=d(3).toFloat();setPadding(d(15),d(14),d(15),d(16))};val head=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL};head.addView(txt("⌂  ${roomName(room)}",17f,C.WHITE,true),LinearLayout.LayoutParams(0,d(40),1f));val ren=btn("ویرایش");head.addView(ren);ren.setOnClickListener{rename("نام اتاق",roomName(room)){prefs.edit().putString("room$room",it).apply();drawRooms()}};card.addView(head)
        for(i in room*2..room*2+1){val l=lamps?.getOrNull(i);val line=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL;background=bg(0xFF211B1B.toInt(),15,0xFF3D2A27.toInt());setPadding(d(11),d(10),d(9),d(10))};val info=LinearLayout(this).apply{orientation=1};info.addView(txt("💡  ${lampName(i)}",14f,C.WHITE,true));info.addView(txt(if(l==null)"در انتظار وضعیت" else if(l.on)"روشن • ${l.src}" else "خاموش • ${l.src}",10f,if(l?.on==true)C.YELLOW else C.MUTED));line.addView(info,LinearLayout.LayoutParams(0,-2,1f));val rn=btn("✎");line.addView(rn);rn.setOnClickListener{rename("نام کلید",lampName(i)){prefs.edit().putString("lamp$i",it).apply();drawRooms()}};val sw=btn(if(l?.on==true)"خاموش" else "روشن",l?.on==true);sw.isEnabled=l!=null&&!busy;line.addView(sw,LinearLayout.LayoutParams(d(78),d(44)));sw.setOnClickListener{command("/api/set","id=$i&state=${if(l?.on==true)0 else 1}")};card.addView(line,LinearLayout.LayoutParams(-1,-2).apply{topMargin=d(9)})};p.addView(card,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=d(13)})}}
    private fun command(path:String,body:String){if(busy)return;busy=true;io.execute{val r=request(ip,pass,"POST",path,body);main.post{busy=false;if(r.code==200&&parse(r.body)){status?.apply{text="● فرمان اجرا شد";setTextColor(C.GREEN)};drawRooms()}else{status?.apply{text="● فرمان ناموفق؛ HTTP ${r.code}";setTextColor(C.RED)};if(r.code==401)showLogin("رمز پنل تغییر کرده؛ دوباره وارد شو")}}}}
    private fun settingsDialog(){val box=LinearLayout(this).apply{orientation=1;setPadding(d(18),d(8),d(18),0)};val host=edit("IP کنترلر",ip);val newPass=edit("رمز جدید پنل (خالی = بدون تغییر)","",true);box.addView(host);box.addView(newPass);box.addView(txt("نام کاربری روی Firmware ثابت و برابر admin است. تغییر رمز باعث ری‌استارت ESP32 می‌شود.",11f,C.MUTED));AlertDialog.Builder(this).setTitle("تنظیمات VOLTEX").setView(box).setNegativeButton("بستن",null).setNeutralButton("خروج از حساب"){_,_->connected=false;vault.clear();prefs.edit().putBoolean("remember",false).apply();showLogin()}.setPositiveButton("ذخیره"){_,_->val h=host.text.toString().trim().removePrefix("http://").removePrefix("https://").trimEnd('/');val np=newPass.text.toString();if(h.isBlank()||h.contains('/')||h.contains(' ')){Toast.makeText(this,"IP معتبر نیست",Toast.LENGTH_SHORT).show();return@setPositiveButton};if(np.isNotEmpty()&&np.length !in 6..32){Toast.makeText(this,"رمز باید ۶ تا ۳۲ نویسه باشد",Toast.LENGTH_SHORT).show();return@setPositiveButton};if(np.isEmpty()){ip=h;prefs.edit().putString("ip",ip).apply();fetchState()}else{val oldHost=ip;io.execute{val body="panel="+URLEncoder.encode(np,"UTF-8");val r=request(oldHost,pass,"POST","/api/settings",body);main.post{if(r.code==200){ip=h;pass=np;prefs.edit().putString("ip",ip).apply();if(prefs.getBoolean("remember",false))vault.save(np);Toast.makeText(this,"رمز تغییر کرد؛ در حال اتصال مجدد...",Toast.LENGTH_LONG).show();main.postDelayed({login(ip,np,prefs.getBoolean("remember",false))},5000)}else Toast.makeText(this,"تغییر رمز ناموفق بود؛ HTTP ${r.code}",Toast.LENGTH_LONG).show()}}}}}.show()}
}
