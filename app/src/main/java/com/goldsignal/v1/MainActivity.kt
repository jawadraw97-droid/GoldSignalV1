package com.goldsignal.v1

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class Bar(val t:Long,val o:Double,val h:Double,val l:Double,val c:Double)
data class Signal(
    val side:String,val score:Int,val entry:Double,val sl:Double,
    val tp1:Double,val tp2:Double,val rsi:Double,val atr:Double,
    val ema20:Double,val ema50:Double,val ema200:Double,
    val macd:Double,val htf:String,val reason:String
)

class MainActivity : Activity() {
    private val executor=Executors.newSingleThreadExecutor()
    private lateinit var price:TextView
    private lateinit var change:TextView
    private lateinit var badge:TextView
    private lateinit var score:TextView
    private lateinit var levels:TextView
    private lateinit var market:TextView
    private lateinit var updated:TextView
    private lateinit var refresh:Button
    private lateinit var history:Button
    private lateinit var progress:ProgressBar
    private val prefs by lazy { getSharedPreferences("gold_signal",MODE_PRIVATE) }

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        buildUi()
        refreshData()
    }

    private fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()

    private fun tv(text:String,size:Float,color:Int=Color.WHITE,bold:Boolean=false)=TextView(this).apply{
        this.text=text
        textSize=size
        setTextColor(color)
        if(bold) typeface=Typeface.DEFAULT_BOLD
    }

    private fun card():LinearLayout=LinearLayout(this).apply{
        orientation=LinearLayout.VERTICAL
        setPadding(dp(16),dp(14),dp(16),dp(14))
        background=GradientDrawable().apply{
            setColor(Color.rgb(18,23,34))
            cornerRadius=dp(18).toFloat()
            setStroke(dp(1),Color.rgb(39,47,63))
        }
    }

    private fun add(root:LinearLayout,v:View,top:Int=8){
        root.addView(v,LinearLayout.LayoutParams(-1,-2).apply{topMargin=dp(top)})
    }

    private fun buildUi(){
        val scroll=ScrollView(this)
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setPadding(dp(18),dp(16),dp(18),dp(28))
            setBackgroundColor(Color.rgb(9,11,16))
        }

        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        val logo=ImageView(this).apply{
            setImageResource(com.goldsignal.v1.R.drawable.app_icon)
            layoutParams=LinearLayout.LayoutParams(dp(54),dp(54))
        }
        header.addView(logo)
        val ht=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,0,0)}
        ht.addView(tv("GOLD SIGNAL",22f,Color.rgb(243,209,122),true))
        ht.addView(tv("تحليل الذهب • XAU/USD",13f,Color.rgb(156,167,184)))
        header.addView(ht,LinearLayout.LayoutParams(0,-2,1f))
        add(root,header,0)

        val pcard=card()
        pcard.addView(tv("السعر الحالي",12f,Color.rgb(156,167,184)))
        price=tv("—",34f,Color.WHITE,true)
        pcard.addView(price)
        change=tv("—",13f,Color.rgb(156,167,184))
        pcard.addView(change)
        updated=tv("جارٍ الاتصال بالسوق…",11f,Color.rgb(110,120,135))
        pcard.addView(updated)
        add(root,pcard)

        val scard=card()
        badge=tv("⚪  NO TRADE",25f,Color.LTGRAY,true).apply{gravity=Gravity.CENTER}
        scard.addView(badge)
        score=tv("قوة الإشارة  — / 100",14f,Color.rgb(216,174,72),true).apply{gravity=Gravity.CENTER}
        scard.addView(score)
        progress=ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal).apply{
            max=100;progress=0
        }
        scard.addView(progress,LinearLayout.LayoutParams(-1,dp(8)).apply{topMargin=dp(10)})
        add(root,scard)

        val lcard=card()
        lcard.addView(tv("مستويات الصفقة",15f,Color.rgb(243,209,122),true))
        levels=tv("الدخول     —\nوقف الخسارة —\nالهدف 1    —\nالهدف 2    —",16f)
        lcard.addView(levels)
        add(root,lcard)

        val mcard=card()
        mcard.addView(tv("تحليل السوق",15f,Color.rgb(243,209,122),true))
        market=tv("EMA 20/50/200: —\nRSI: —\nMACD: —\nATR: —\nالاتجاه الأعلى: —",14f,Color.rgb(220,225,232))
        mcard.addView(market)
        add(root,mcard)

        val note=card()
        note.addView(tv("كيف تُبنى الإشارة؟",14f,Color.rgb(243,209,122),true))
        note.addView(tv("يتم فحص الاتجاه، EMA 20/50/200، RSI، MACD، ATR وفريم أعلى. عند عدم توافق الشروط يعرض التطبيق NO TRADE بدل اختلاق صفقة.",12f,Color.rgb(156,167,184)))
        add(root,note)

        val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        refresh=Button(this).apply{
            text="تحديث الآن"
            setOnClickListener{refreshData()}
        }
        history=Button(this).apply{
            text="سجل الإشارات"
            setOnClickListener{showHistory()}
        }
        row.addView(refresh,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(history,LinearLayout.LayoutParams(0,-2,1f).apply{leftMargin=dp(8)})
        add(root,row,12)

        val foot=tv("Gold Signal • V1.2  |  توصيات تحليلية وليست ضمانًا للربح",11f,Color.rgb(100,110,125)).apply{
            gravity=Gravity.CENTER
        }
        add(root,foot,14)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun refreshData(){
        refresh.isEnabled=false
        updated.text="جارٍ تحديث بيانات الذهب…"
        executor.execute{
            try{
                val b15=parseYahoo(get("https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=15m&range=5d"))
                val b60=parseYahoo(get("https://query1.finance.yahoo.com/v8/finance/chart/GC=F?interval=1h&range=1mo"))
                if(b15.size<220) throw Exception("بيانات 15 دقيقة غير كافية: ${b15.size}")
                val s=analyze(b15,b60)
                val last=b15.last().c
                val prev=b15.getOrNull(b15.size-2)?.c?:last
                val pct=if(prev==0.0)0.0 else (last-prev)/prev*100.0
                runOnUiThread{
                    price.text=fmt(last)
                    change.text=String.format(Locale.US,"آخر حركة: %+.2f%%",pct)
                    change.setTextColor(if(pct>=0)Color.rgb(55,213,138) else Color.rgb(255,100,124))
                    updated.text="متصل • آخر تحديث: الآن • GC=F (Gold Futures)"
                    render(s)
                    refresh.isEnabled=true
                }
            }catch(e:Exception){
                runOnUiThread{
                    updated.text="تعذر تحديث السوق: ${e.message?: "خطأ غير معروف"}"
                    badge.text="⚪  NO TRADE"
                    badge.setTextColor(Color.LTGRAY)
                    score.text="قوة الإشارة  — / 100"
                    levels.text="الدخول     —\nوقف الخسارة —\nالهدف 1    —\nالهدف 2    —"
                    refresh.isEnabled=true
                }
            }
        }
    }

    private fun get(url:String):String{
        val c=URL(url).openConnection() as HttpURLConnection
        c.requestMethod="GET"
        c.connectTimeout=12000
        c.readTimeout=18000
        c.setRequestProperty("User-Agent","GoldSignal/1.2 Android")
        try{
            if(c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use{it.readText()}
        }finally{c.disconnect()}
    }

    private fun parseYahoo(s:String):List<Bar>{
        val out=ArrayList<Bar>()
        val result=JSONObject(s).getJSONObject("chart").getJSONArray("result").getJSONObject(0)
        val ts=result.getJSONArray("timestamp")
        val q=result.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
        val o=q.getJSONArray("open");val h=q.getJSONArray("high")
        val l=q.getJSONArray("low");val c=q.getJSONArray("close")
        for(i in 0 until ts.length()){
            if(ts.isNull(i)||o.isNull(i)||h.isNull(i)||l.isNull(i)||c.isNull(i))continue
            val cc=c.getDouble(i);val oo=o.getDouble(i);val hh=h.getDouble(i);val ll=l.getDouble(i)
            if(cc.isFinite()&&oo.isFinite()&&hh.isFinite()&&ll.isFinite())
                out.add(Bar(ts.getLong(i)*1000,oo,hh,ll,cc))
        }
        return out.distinctBy{it.t}.sortedBy{it.t}
    }

    private fun ema(x:List<Double>,n:Int):Double{
        if(x.isEmpty())return 0.0
        if(x.size<n)return x.average()
        val k=2.0/(n+1);var e=x.take(n).average()
        for(i in n until x.size)e=x[i]*k+e*(1-k)
        return e
    }

    private fun rsi(x:List<Double>,n:Int=14):Double{
        if(x.size<=n)return 50.0
        var g=0.0;var l=0.0
        for(i in 1..n){val d=x[i]-x[i-1];if(d>=0)g+=d else l-=d}
        var ag=g/n;var al=l/n
        for(i in n+1 until x.size){
            val d=x[i]-x[i-1]
            ag=(ag*(n-1)+max(0.0,d))/n
            al=(al*(n-1)+max(0.0,-d))/n
        }
        return if(al==0.0)100.0 else 100.0-100.0/(1+ag/al)
    }

    private fun emaSeries(x:List<Double>,n:Int):List<Double>{
        if(x.size<n)return emptyList()
        val out=ArrayList<Double>();val k=2.0/(n+1);var e=x.take(n).average();out.add(e)
        for(i in n until x.size){e=x[i]*k+e*(1-k);out.add(e)}
        return out
    }

    private fun macd(x:List<Double>):Double{
        val a=emaSeries(x,12);val b=emaSeries(x,26)
        if(a.isEmpty()||b.isEmpty())return 0.0
        return a.takeLast(min(a.size,b.size)).last()-b.last()
    }

    private fun atr(b:List<Bar>,n:Int=14):Double{
        if(b.size<=n)return 0.0
        val tr=ArrayList<Double>()
        for(i in 1 until b.size){
            tr.add(max(b[i].h-b[i].l,max(abs(b[i].h-b[i-1].c),abs(b[i].l-b[i-1].c))))
        }
        return tr.takeLast(n).average()
    }

    private fun analyze(b15:List<Bar>,b60:List<Bar>):Signal{
        val x=b15.map{it.c};val last=x.last()
        val e20=ema(x,20);val e50=ema(x,50);val e200=ema(x,200)
        val r=rsi(x);val m=macd(x);val a=atr(b15)
        var htf=0
        if(b60.size>=50){
            val z=b60.map{it.c};val a20=ema(z,20);val a50=ema(z,50)
            htf=when{a20>a50->1;a20<a50->-1;else->0}
        }
        var buy=0;var sell=0
        if(last>e20)buy++ else sell++
        if(e20>e50)buy++ else sell++
        if(e50>e200)buy++ else sell++
        if(r in 52.0..68.0)buy++ else if(r in 32.0..48.0)sell++
        if(m>0)buy++ else if(m<0)sell++
        if(htf>0)buy+=2 else if(htf<0)sell+=2
        val total=8
        val side=when{
            buy>=6 && buy>sell+1 -> "BUY"
            sell>=6 && sell>buy+1 -> "SELL"
            else -> "NO TRADE"
        }
        val rawScore=max(buy,sell)*100/total
        val score=rawScore.coerceIn(0,100)
        val risk=max(a*1.6,last*0.0012)
        val sl=when(side){
            "BUY"->last-risk
            "SELL"->last+risk
            else->last
        }
        val d=abs(last-sl)
        val tp1=when(side){"BUY"->last+d*1.25;"SELL"->last-d*1.25;else->last}
        val tp2=when(side){"BUY"->last+d*2.0;"SELL"->last-d*2.0;else->last}
        val reason="اتفاق الشروط: شراء $buy/8 • بيع $sell/8"
        return Signal(side,score,last,sl,tp1,tp2,r,a,e20,e50,e200,m,
            when(htf>0){"صاعد"}else if(htf<0){"هابط"}else{"محايد"},reason)
    }

    private fun render(s:Signal){
        badge.text=when(s.side){"BUY"->"🟢  BUY — شراء";"SELL"->"🔴  SELL — بيع";else->"⚪  NO TRADE — انتظار"}
        badge.setTextColor(when(s.side){"BUY"->Color.rgb(55,213,138);"SELL"->Color.rgb(255,100,124);else->Color.LTGRAY})
        score.text="قوة الإشارة  ${s.score} / 100"
        progress.progress=s.score
        levels.text="الدخول     ${fmt(s.entry)}\nوقف الخسارة ${fmt(s.sl)}\nالهدف 1    ${fmt(s.tp1)}\nالهدف 2    ${fmt(s.tp2)}"
        market.text="EMA 20     ${fmt(s.ema20)}\nEMA 50     ${fmt(s.ema50)}\nEMA 200    ${fmt(s.ema200)}\nRSI        ${fmt(s.rsi)}\nMACD      ${fmt(s.macd)}\nATR        ${fmt(s.atr)}\nالاتجاه الأعلى  ${s.htf}\n${s.reason}"
        if(s.side!="NO TRADE")saveSignal(s)
    }

    private fun saveSignal(s:Signal){
        val count=prefs.getInt("count",0)+1
        prefs.edit().putInt("count",count)
            .putString("last","${s.side} | ${fmt(s.entry)} | ${s.score}/100")
            .apply()
    }

    private fun showHistory(){
        AlertDialog.Builder(this)
            .setTitle("سجل الإشارات")
            .setMessage("عدد الإشارات المسجلة: ${prefs.getInt("count",0)}\n\nآخر إشارة:\n${prefs.getString("last","لا توجد")}\n\nملاحظة: هذه النسخة تسجل ظهور الإشارة فقط. لا تعرض نسبة نجاح إلا بعد تسجيل نتائج فعلية.")
            .setPositiveButton("إغلاق",null)
            .show()
    }

    private fun fmt(x:Double)=String.format(Locale.US,"%.2f",x)
}
