package com.waseem.smartwell

import android.app.*
import android.os.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

class MainActivity:Activity(){
 private val p by lazy{getSharedPreferences("smart_well",0)}
 private val navy=Color.rgb(8,17,31);private val blue=Color.rgb(22,119,255);private val green=Color.rgb(16,130,95);private val red=Color.rgb(196,55,55);private val bg=Color.rgb(245,248,252)
 private lateinit var body:LinearLayout;private var started=0L;private var active="";private val h=Handler(Looper.getMainLooper())

 override fun onCreate(b:Bundle?){super.onCreate(b);window.statusBarColor=navy;window.navigationBarColor=navy;screen("لوحة التحكم"){dashboard()}}

 private fun screen(t:String,f:()->Unit){
  val root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setBackgroundColor(bg);root.layoutDirection=View.LAYOUT_DIRECTION_RTL
  val head=LinearLayout(this);head.setPadding(dp(15),dp(7),dp(15),dp(7));head.setBackgroundColor(Color.WHITE)
  val title=TextView(this);title.text="البئر الذكي";title.textSize=19f;title.setTextColor(navy);title.setTypeface(null,1);head.addView(title,LinearLayout.LayoutParams(0,dp(50),1f))
  val bk=button("نسخة",Color.WHITE,navy);bk.setOnClickListener{backup()};head.addView(bk,LinearLayout.LayoutParams(dp(65),dp(40)));root.addView(head)
  val sc=ScrollView(this);body=LinearLayout(this);body.orientation=LinearLayout.VERTICAL;body.setPadding(dp(14),dp(14),dp(14),dp(8));body.layoutDirection=View.LAYOUT_DIRECTION_RTL;sc.addView(body);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
  val nav=LinearLayout(this);nav.setBackgroundColor(Color.WHITE)
  val names=listOf("الرئيسية","الحجوزات","الضخ","العملاء","المزيد");val fs=listOf<()->Unit>({screen("لوحة التحكم"){dashboard()}},{screen("الحجوزات"){bookings()}},{screen("الضخ"){pumping()}},{screen("العملاء"){customers()}},{more()})
  for(i in names.indices){val x=button(names[i],Color.WHITE,if(names[i]==t)blue else Color.DKGRAY);x.setOnClickListener{fs[i]()};nav.addView(x,LinearLayout.LayoutParams(0,dp(48),1f))}
  root.addView(nav);setContentView(root);f()
 }

 private fun dashboard(){
  add("لوحة التحكم",24,navy,true);add(date(),11,Color.GRAY,false);gap(8)
  val s=todaySales();val rev=s.sumOf{it.optDouble("total")};val pay=todayPay();val exp=todayExp();val min=s.sumOf{it.optInt("minutes")}
  row{stat("إيرادات اليوم",money(rev),blue);stat("التحصيل",money(pay),green)};row{stat("ساعات التشغيل",String.format(Locale.US,"%.1f",min/60.0),navy);stat("مصروفات اليوم",money(exp),red)}
  gap(8);add("إجراءات سريعة",16,navy,true);row{action("＋ عميل"){customerDialog()};action("＋ حجز"){bookingDialog()}};row{action("▶ بدء الضخ"){startPump()};action("﷼ تحصيل"){paymentDialog()}}
  panel("الحجوزات القادمة"){val x=bookings().filter{it.optString("date")>=day()}.take(5);if(x.isEmpty())empty("لا توجد حجوزات.");x.forEach{b->line(find(b.optString("customerId"))?.optString("name")?:"عميل",b.optString("date")+" · "+b.optString("start"),"مجدول")}}
  panel("أعلى الديون"){val x=customers().map{it to balance(it.optString("id"))}.filter{it.second>0}.sortedByDescending{it.second}.take(5);if(x.isEmpty())empty("لا توجد ديون.");x.forEach{line(it.first.optString("name"),"الرصيد المستحق",money(it.second))}}
 }

 private fun bookings(){
  title("الحجوزات والأدوار","جدولة أدوار السقي ومتابعة الحالة.");addBtn("＋ حجز جديد",blue){bookingDialog()}
  panel("الحجوزات"){val x=bookings().sortedByDescending{it.optString("date")};if(x.isEmpty())empty("لا توجد حجوزات.");x.forEach{b->val c=find(b.optString("customerId"));line(c?.optString("name")?:"عميل",b.optString("date")+" · "+b.optString("start")+" - "+b.optString("end"),b.optString("status"));if(b.optString("status")!="مكتمل")addBtn("✓ إكمال"){b.put("status","مكتمل");save("bookings",bookingsA());screen("الحجوزات"){bookings()}}}}
 }

 private fun pumping(){
  title("الضخ والتعبئة","مؤقت دقيق وتسجيل عمليات التشغيل.")
  if(started>0){val v=TextView(this);v.textSize=38f;v.gravity=Gravity.CENTER;v.setTextColor(Color.WHITE);val c=LinearLayout(this);c.orientation=LinearLayout.VERTICAL;c.setPadding(dp(15),dp(15),dp(15),dp(15));c.background=round(navy,Color.TRANSPARENT,18);c.addView(v,LinearLayout.LayoutParams(-1,dp(75)));val stop=button("⏹ إيقاف وتسجيل",Color.rgb(255,230,230),red);stop.setOnClickListener{stopPump()};c.addView(stop);body.addView(c);clock(v)}else addBtn("▶ بدء عملية ضخ",blue){startPump()}
  panel("عمليات اليوم"){val x=todaySales().reversed();if(x.isEmpty())empty("لا توجد عمليات.");x.forEach{line(find(it.optString("customerId"))?.optString("name")?:"عميل",it.optString("time")+" · "+it.optInt("minutes")+" دقيقة",money(it.optDouble("total")))}}
 }

 private fun customers(){
  title("العملاء والمزارعون","ملفات العملاء والأرصدة.");addBtn("＋ إضافة عميل",blue){customerDialog()};gap(6)
  val q=EditText(this);q.hint="بحث بالاسم أو الهاتف";q.setSingleLine();q.background=round(Color.WHITE,Color.LTGRAY,10);body.addView(q,LinearLayout.LayoutParams(-1,dp(46)));gap(6)
  val list=LinearLayout(this);list.orientation=LinearLayout.VERTICAL;body.addView(list)
  fun refresh(){list.removeAllViews();com.waseem.smartwell.list(customersA()).filter{(it.optString("name")+" "+it.optString("phone")).contains(q.text.toString(),true)}.forEach{c->val x=LinearLayout(this);x.orientation=LinearLayout.VERTICAL;x.setPadding(dp(10),dp(9),dp(10),dp(9));x.background=round(Color.WHITE,Color.LTGRAY,13);txt(x,c.optString("name"),15,navy,true);txt(x,c.optString("phone")+" · "+c.optString("area"),10,Color.GRAY,false);txt(x,"الرصيد: "+money(balance(c.optString("id"))),12,red,true);val a=LinearLayout(this);val p=button("تحصيل",Color.rgb(225,248,237),green);p.setOnClickListener{paymentDialog(c.optString("id"))};val l=button("كشف الحساب",Color.rgb(232,242,255),blue);l.setOnClickListener{ledger(c.optString("id"))};a.addView(p,LinearLayout.LayoutParams(0,dp(40),1f));a.addView(l,LinearLayout.LayoutParams(0,dp(40),1f));x.addView(a);list.addView(x);space(list,5)}}
  q.addTextChangedListener(object:android.text.TextWatcher{public override fun beforeTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){};public override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int){refresh()};public override fun afterTextChanged(e:android.text.Editable?){} });refresh()
 }

 private fun more(){
  screen("المزيد"){title("المزيد","أدوات الإدارة والتقارير.");menu("﷼ التحصيل والديون","الأرصدة والدفعات"){screen("المزيد"){payments()}};menu("▥ المصروفات والديزل","التكاليف التشغيلية"){screen("المزيد"){expenses()}};menu("🔧 الصيانة","المهام الدورية"){screen("المزيد"){maintenance()}};menu("▦ التقارير","الإيرادات والتكاليف"){screen("المزيد"){reports()}};menu("⚙ الإعدادات","بيانات البئر والأسعار"){screen("المزيد"){settings()}};menu("⇩ نسخة احتياطية","تصدير البيانات"){backup()}}
 }

 private fun payments(){title("التحصيل والديون","متابعة المبالغ المستحقة.");addBtn("＋ تسجيل تحصيل",green){paymentDialog()};panel("الأرصدة"){val x=customers().map{it to balance(it.optString("id"))}.filter{it.second>0};if(x.isEmpty())empty("لا توجد ديون.");x.forEach{line(it.first.optString("name"),it.first.optString("phone"),money(it.second))}}}
 private fun expenses(){title("المصروفات والديزل","التكاليف التشغيلية.");row{action("＋ مصروف"){expenseDialog(false)};action("＋ ديزل"){expenseDialog(true)}};panel("الحركات"){expenses().reversed().forEach{line(it.optString("desc"),it.optString("date"),money(it.optDouble("amount")))}}}
 private fun maintenance(){title("الصيانة","جدولة المهام الدورية.");addBtn("＋ مهمة صيانة",blue){maintenanceDialog()};panel("المهام"){maint().forEach{line(it.optString("title"),it.optString("due"),it.optString("status"))}}}
 private fun reports(){title("التقارير","ملخص الشهر الحالي.");val r=monthSales().sumOf{it.optDouble("total")};val p=monthPays().sumOf{it.optDouble("amount")};val e=monthExp().sumOf{it.optDouble("amount")};row{stat("الإيرادات",money(r),blue);stat("التحصيل",money(p),green)};row{stat("التكاليف",money(e),red);stat("الصافي",money(r-e),navy)};panel("ملخص"){add("عدد العملاء: "+customers().size,13,navy,false);add("عدد العمليات: "+monthSales().size,13,navy,false);add("الديون: "+money(customers().sumOf{max(0.0,balance(it.optString("id")))}),13,red,true)}}}
 private fun settings(){title("الإعدادات","بيانات البئر والأسعار.");val n=field("اسم البئر",p.getString("name","البئر الارتوازي الذكي")!!);val h=field("سعر الساعة",p.getString("hour","3000")!!,true);val t=field("سعر النقلة",p.getString("trip","0")!!,true);listOf(n,h,t).forEach{body.addView(it,LinearLayout.LayoutParams(-1,dp(48)));gap(6)};addBtn("حفظ الإعدادات",blue){p.edit().putString("name",n.text.toString()).putString("hour",h.text.toString()).putString("trip",t.text.toString()).apply();toast("تم الحفظ")};addBtn("⇩ تصدير نسخة",Color.DKGRAY){backup()}}
 private fun ledger(id:String){screen("العملاء"){title("كشف حساب","تفاصيل العميل.");add("الرصيد الحالي: "+money(balance(id)),19,red,true);panel("الحركات"){list(salesA()).filter{it.optString("customerId")==id}.forEach{line("عملية ضخ",it.optString("date"),money(it.optDouble("total")))};list(paysA()).filter{it.optString("customerId")==id}.forEach{line("سداد",it.optString("date"),money(-it.optDouble("amount")))}}}}

 private fun customerDialog(){val n=field("اسم العميل");val ph=field("رقم الهاتف");val ar=field("المنطقة");val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(dp(8),0,dp(8),0);listOf(n,ph,ar).forEach{box.addView(it);space(box,5)};AlertDialog.Builder(this).setTitle("إضافة عميل").setView(box).setPositiveButton("حفظ"){_,_->val a=customersA();a.put(JSONObject().put("id",uid()).put("name",n.text.toString()).put("phone",ph.text.toString()).put("area",ar.text.toString()));save("customers",a);screen("العملاء"){customers()};toast("تم حفظ العميل")}.setNegativeButton("إلغاء",null).show()}
 private fun bookingDialog(){if(customers().isEmpty()){toast("أضف عميلًا أولًا");return};val cs=customers();val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,cs.map{it.optString("name")});val d=field("التاريخ",day());val s=field("البداية","07:00");val e=field("النهاية","08:00");val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(dp(8),0,dp(8),0);listOf(sp,d,s,e).forEach{box.addView(it);space(box,4)};AlertDialog.Builder(this).setTitle("حجز جديد").setView(box).setPositiveButton("حفظ"){_,_->val a=bookingsA();a.put(JSONObject().put("id",uid()).put("customerId",cs[sp.selectedItemPosition].optString("id")).put("date",d.text.toString()).put("start",s.text.toString()).put("end",e.text.toString()).put("status","مجدول"));save("bookings",a);screen("الحجوزات"){bookings()};toast("تم حفظ الحجز")}.setNegativeButton("إلغاء",null).show()}
 private fun startPump(){if(customers().isEmpty()){toast("أضف عميلًا أولًا");return};val cs=customers();val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,cs.map{it.optString("name")});AlertDialog.Builder(this).setTitle("اختيار العميل").setView(sp).setPositiveButton("بدء"){_,_->active=cs[sp.selectedItemPosition].optString("id");started=System.currentTimeMillis();screen("الضخ"){pumping()}}.setNegativeButton("إلغاء",null).show()}
 private fun stopPump(){val m=max(1,((System.currentTimeMillis()-started)/60000).toInt());started=0;val hour=p.getString("hour","3000")!!.toDoubleOrNull()?:0.0;val a=salesA();a.put(JSONObject().put("id",uid()).put("customerId",active).put("date",day()).put("time",clockTime()).put("minutes",m).put("total",hour*m/60));save("sales",a);screen("الضخ"){pumping()};toast("تم تسجيل العملية")}
 private fun paymentDialog(cid:String?=null){if(customers().isEmpty()){toast("أضف عميلًا أولًا");return};val cs=customers();val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,cs.map{it.optString("name")});if(cid!=null){val i=cs.indexOfFirst{it.optString("id")==cid};if(i>=0)sp.setSelection(i)};val am=field("المبلغ","",true);val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(dp(8),0,dp(8),0);box.addView(sp);box.addView(am);AlertDialog.Builder(this).setTitle("تسجيل تحصيل").setView(box).setPositiveButton("حفظ"){_,_->val id=cs[sp.selectedItemPosition].optString("id");val v=am.text.toString().toDoubleOrNull()?:0.0;if(v<=0||v>balance(id)){toast("المبلغ غير صالح");return@setPositiveButton};val a=paysA();a.put(JSONObject().put("id",uid()).put("customerId",id).put("amount",v).put("date",day()));save("payments",a);payments();toast("تم التحصيل")}.setNegativeButton("إلغاء",null).show()}
 private fun expenseDialog(fuel:Boolean){val d=field(if(fuel)"بيان شراء الديزل" else "بيان المصروف");val a=field("القيمة","",true);val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(dp(8),0,dp(8),0);box.addView(d);box.addView(a);AlertDialog.Builder(this).setTitle(if(fuel)"تسجيل ديزل" else "تسجيل مصروف").setView(box).setPositiveButton("حفظ"){_,_->val x=expensesA();x.put(JSONObject().put("id",uid()).put("date",day()).put("desc",d.text.toString()).put("amount",a.text.toString().toDoubleOrNull()?:0.0));save("expenses",x);expenses();toast("تم الحفظ")}.setNegativeButton("إلغاء",null).show()}
 private fun maintenanceDialog(){val t=field("اسم المهمة","تغيير زيت المولد");val d=field("تاريخ الاستحقاق",day());val box=LinearLayout(this);box.orientation=LinearLayout.VERTICAL;box.setPadding(dp(8),0,dp(8),0);box.addView(t);box.addView(d);AlertDialog.Builder(this).setTitle("مهمة صيانة").setView(box).setPositiveButton("حفظ"){_,_->val a=maintA();a.put(JSONObject().put("id",uid()).put("title",t.text.toString()).put("due",d.text.toString()).put("status","معلقة"));save("maintenance",a);maintenance();toast("تمت الإضافة")}.setNegativeButton("إلغاء",null).show()}

 private fun panel(t:String,f:()->Unit){val x=LinearLayout(this);x.orientation=LinearLayout.VERTICAL;x.setPadding(dp(12),dp(12),dp(12),dp(12));x.background=round(Color.WHITE,Color.LTGRAY,15);txt(x,t,15,navy,true);space(x,6);val old=body;body=x;f();body=old;old.addView(x);gap(7)}
 private fun line(a:String,b:String,c:String){val x=LinearLayout(this);x.setPadding(dp(9),dp(7),dp(9),dp(7));x.background=round(Color.WHITE,Color.LTGRAY,10);val l=LinearLayout(this);l.orientation=LinearLayout.VERTICAL;txt(l,a,13,navy,true);txt(l,b,10,Color.GRAY,false);x.addView(l,LinearLayout.LayoutParams(0,-2,1f));txt(x,c,11,blue,true);body.addView(x);gap(4)}
 private fun title(a:String,b:String){add(a,22,navy,true);add(b,11,Color.GRAY,false);gap(7)}
 private fun addBtn(s:String,c:Int,f:()->Unit){val b=button(s,Color.WHITE,c);b.setOnClickListener{f()};body.addView(b,LinearLayout.LayoutParams(-1,dp(48)))}
 private fun action(s:String,f:()->Unit){val b=button(s,Color.WHITE,navy);b.setOnClickListener{f()};body.addView(b,LinearLayout.LayoutParams(0,dp(50),1f))}
 private fun stat(a:String,b:String,c:Int){val x=LinearLayout(this);x.orientation=LinearLayout.VERTICAL;x.setPadding(dp(10),dp(9),dp(10),dp(9));x.background=round(Color.WHITE,Color.LTGRAY,14);txt(x,a,10,Color.GRAY,false);txt(x,b,18,c,true);body.addView(x,LinearLayout.LayoutParams(0,dp(84),1f))}
 private fun row(f:()->Unit){val old=body;val x=LinearLayout(this);x.orientation=LinearLayout.HORIZONTAL;body=x;f();body=old;old.addView(x)}
 private fun menu(a:String,b:String,f:()->Unit){val x=button(a+"\n"+b,Color.WHITE,navy);x.gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL;x.setOnClickListener{f()};body.addView(x,LinearLayout.LayoutParams(-1,dp(58)));gap(5)}
 private fun add(s:String,z:Int,c:Int,b:Boolean){val x=TextView(this);x.text=s;x.textSize=z.toFloat();x.setTextColor(c);if(b)x.setTypeface(null,1);body.addView(x)}
 private fun txt(p:LinearLayout,s:String,z:Int,c:Int,b:Boolean){val x=TextView(this);x.text=s;x.textSize=z.toFloat();x.setTextColor(c);if(b)x.setTypeface(null,1);p.addView(x)}
 private fun empty(s:String){add(s,12,Color.GRAY,false)}
 private fun gap(n:Int){space(body,n)};private fun space(v:LinearLayout,n:Int){v.addView(Space(this),LinearLayout.LayoutParams(1,dp(n)))}
 private fun button(s:String,b:Int,t:Int)=Button(this).apply{text=s;textSize=11f;isAllCaps=false;setTextColor(t);background=round(b,Color.LTGRAY,10);minHeight=0;minimumHeight=0}
 private fun field(h:String,v:String="",num:Boolean=false)=EditText(this).apply{hint=h;setText(v);setSingleLine();inputType=if(num)2 else 1;background=round(Color.WHITE,Color.LTGRAY,10);setPadding(dp(12),0,dp(12),0)}
 private fun round(f:Int,s:Int,r:Int)=GradientDrawable().apply{setColor(f);cornerRadius=dp(r).toFloat();if(s!=Color.TRANSPARENT)setStroke(dp(1),s)}
 private fun dp(x:Int)=(x*resources.displayMetrics.density).toInt()
 private fun day()=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())
 private fun date()=SimpleDateFormat("EEEE، d MMMM yyyy",Locale("ar","YE")).format(Date())
 private fun clockTime()=SimpleDateFormat("HH:mm",Locale.US).format(Date())
 private fun uid()=UUID.randomUUID().toString()
 private fun money(v:Double)=NumberFormat.getIntegerInstance(Locale("ar","YE")).format(v)+" ريال"
 private fun arr(k:String)=try{JSONArray(p.getString(k,"[]"))}catch(_:Exception){JSONArray()}
 private fun save(k:String,a:JSONArray){p.edit().putString(k,a.toString()).apply()}
 private fun list(a:JSONArray)=List(a.length()){a.getJSONObject(it)}
 private fun customersA()=arr("customers");private fun bookingsA()=arr("bookings");private fun salesA()=arr("sales");private fun paysA()=arr("payments");private fun expensesA()=arr("expenses");private fun maintA()=arr("maintenance")
 private fun customers()=list(customersA());private fun bookings()=list(bookingsA());private fun expenses()=list(expensesA());private fun maint()=list(maintA())
 private fun todaySales()=list(salesA()).filter{it.optString("date")==day()};private fun todayPay()=list(paysA()).filter{it.optString("date")==day()}.sumOf{it.optDouble("amount")};private fun todayExp()=expenses().filter{it.optString("date")==day()}.sumOf{it.optDouble("amount")}
 private fun monthSales()=list(salesA()).filter{it.optString("date").startsWith(day().substring(0,7))};private fun monthPays()=list(paysA()).filter{it.optString("date").startsWith(day().substring(0,7))};private fun monthExp()=expenses().filter{it.optString("date").startsWith(day().substring(0,7))}
 private fun find(id:String)=customers().firstOrNull{it.optString("id")==id};private fun balance(id:String)=list(salesA()).filter{it.optString("customerId")==id}.sumOf{it.optDouble("total")}-list(paysA()).filter{it.optString("customerId")==id}.sumOf{it.optDouble("amount")}
 private fun clock(v:TextView){val r=object:Runnable{override fun run(){if(started==0L)return;val s=(System.currentTimeMillis()-started)/1000;v.text=String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60);h.postDelayed(this,500)}};h.post(r)}
 private fun backup(){val o=JSONObject();listOf("customers","bookings","sales","payments","expenses","maintenance").forEach{o.put(it,arr(it))};val i=Intent(Intent.ACTION_SEND);i.type="application/json";i.putExtra(Intent.EXTRA_TEXT,o.toString(2));startActivity(Intent.createChooser(i,"حفظ النسخة"))}
 private fun toast(s:String){Toast.makeText(this,s,Toast.LENGTH_SHORT).show()}
}
