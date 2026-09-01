/* ===========================================================================
   Language: Burmese or English, one at a time.
   ===========================================================================
   The page used to print both at once - Burmese as the label and English in a
   small grey span underneath it. That reads as a translation exercise rather
   than a product, and it doubled the width of every control. Here each string
   has one key and two values, and the switch in the sidebar picks which one the
   whole system shows.

   Markup declares its key and nothing else:

       <h2 data-i18n="plan.h2"></h2>
       <input data-i18n-ph="stops.placeholder">
       <button data-i18n-aria="lang.switch">

   Anything rendered from JavaScript calls t('key') instead. Whatever a view
   builds at render time has to be rebuilt when the language changes, so
   applyLang() fires a ybs:lang event and the dynamic views re-render on it.

   Data carries its own translations - stops have nameMy and nameEn - so
   stopName() and routeName() pick the right field rather than looking anything
   up in this table.
   =========================================================================== */

const LANG_KEY = 'ybs.lang';

const STRINGS = {
    my: {
        'app.title': 'YBS — ခရီးစဉ်ရှာဖွေမှု (AI Demo)',
        'brand.name': 'ခရီးစဉ်ရှာဖွေမှု',
        'hello.title': 'မင်္ဂလာပါ!',
        'hello.body': 'ဒီနေ့ ဘာများ ကူညီပေးရမလဲ။',
        'step.how': 'ဘယ်လို ရှာမလဲ ရွေးပါ',
        'step.how.info': 'အသံနဲ့ ပြောနိုင်သလို ဥပမာတစ်ခုကိုလည်း သုံးနိုင်ပါတယ်။',
        'step.stops': 'မှတ်တိုင် နှစ်ခု ရွေးပါ',
        'step.stops.info': 'ဘယ်ကနေ ဘယ်ကို သွားမလဲ ရွေးပြီး ရှာမည် ကို နှိပ်ပါ။',
        'voice.tapHint': 'မိုက်ကို နှိပ်ပြီး သွားလိုသည့် နေရာကို ပြောပါ။',
        'voice.or': 'သို့မဟုတ်',
        'stops.swap': 'အစနှင့် အဆုံး နေရာလဲရန်',
        'stops.searchRoute': 'လမ်းကြောင်း ရှာမည်',
        'profile.optional': '(မထည့်လည်း ရသည်)',
        'quick.fav': 'အနှစ်သက်ဆုံး',
        'quick.favEmpty': 'အနှစ်သက်ဆုံး စာရင်း မရှိသေးပါ — ခရီးစဉ်တစ်ခု ရှာပြီး သိမ်းနိုင်ပါသည်။',
        'chip.i.pregnant': 'ကားပြောင်းရမှု များသော ခရီးစဉ်များကို ဖယ်ထုတ်ပါမည်။',
        'chip.i.needsSeat': 'လူပြည့်နေသော ကားများကို ဖယ်ထုတ်ပါမည်။',
        'chip.i.mobility': 'ခုံးကျော်တံတား ဖြတ်ရသော ခရီးစဉ်များကို ဖယ်ထုတ်ပါမည်။',
        'chip.i.student': 'ကတ်နှင့်တွဲလျှင် ခရီးစရိတ် ထက်ဝက်။',
        'chip.i.card': 'YBS ကတ်ဖြင့် ခရီးစရိတ် သက်သာသည်။',
        'chip.i.monk': 'အခမဲ့ စီးနင်းနိုင်သည်။',
        'quick.label': 'အမြန် ရွေးရန်',
        'quick.home': 'အိမ်',
        'quick.work': 'အလုပ်',
        'quick.school': 'ကျောင်း',
        'quick.market': 'ဈေး',
        'tips.title': 'အကြံပြုချက်',
        'tips.body': 'အချက်အလက် ပိုထည့်လေ ပိုသင့်တော်တဲ့ အဖြေ ရလေပါပဲ။',
        'promise.see.t': 'မြင်ရလွယ်',
        'promise.see.s': 'စာလုံးကြီးပြီး ရုပ်ပုံ ရှင်းလင်း',
        'promise.use.t': 'သုံးရလွယ်',
        'promise.use.s': 'အဆင့်များ ရိုးရှင်း',
        'promise.safe.t': 'စိတ်ချရ',
        'promise.safe.s': 'လူတိုင်းအတွက် ဒီဇိုင်း',
        'promise.trust.t': 'ယုံကြည်ရ',
        'promise.trust.s': 'YBS စည်းမျဉ်းအတိုင်း',
        'foot.tagline': '© YBS ခရီးစဉ်ရှာဖွေမှု · အားလုံးအတွက် ခရီးလွယ်ကူစေရန်',
        'pref.light': 'အလင်း အပြင်အဆင်',
        'pref.dark': 'အမှောင် အပြင်အဆင်',
        'pref.textSize': 'စာလုံး အရွယ်အစား',
        'a11y.help': 'အကူအညီ',
        'a11y.needHelp': 'အကူအညီ လိုပါသလား။',
        'brand.sub': 'သင့်ရဲ့ ဖော်ရွေသော ခရီးလမ်းညွှန်',
        'lang.switch': 'ဘာသာစကား ပြောင်းရန်',

        'role.rider': 'ခရီးသည်',
        'role.driver': 'ယာဉ်မောင်း',
        'role.admin': 'စီမံခန့်ခွဲမှု',
        'role.switch': 'သင် ဘယ်သူလဲ။',
        'auth.h1': 'အကောင့် ဝင်ရန်',
        'auth.lede': 'ခရီးစဉ်ရှာဖွေရန် အကောင့် မလိုပါ။ ယာဉ်မောင်း၏ ကိုယ်ပိုင် တာဝန်ချိန်နှင့် စီမံခန့်ခွဲမှု စာမျက်နှာများအတွက်သာ ဝင်ရန် လိုသည်။',
        'auth.username': 'အသုံးပြုသူ အမည်',
        'auth.password': 'စကားဝှက်',
        'auth.signIn': 'ဝင်မည်',
        'auth.signOut': 'ထွက်မည်',
        'auth.signInFor': 'ဤအပိုင်းအတွက် အကောင့် ဝင်ရန် လိုသည်',
        'auth.badCredentials': 'အသုံးပြုသူ အမည် သို့ စကားဝှက် မှားနေသည်',
        'auth.failed': 'ဝင်ရောက်မှု မအောင်မြင်ပါ',
        'auth.demoAccounts': 'Demo အကောင့်များ',

        'nav.group.rider': '',
        'nav.group.evidence': 'ကိရိယာနှင့် အကူအညီ',
        'nav.group.operations': 'လုပ်ငန်း',
        'nav.group.shift': 'ကျွန်ုပ်၏ တာဝန်ချိန်',
        'nav.group.data': 'အချက်အလက်',

        'nav.plan': 'ခရီးစဉ်ရှာရန်',
        'nav.map': 'မြေပုံ',
        'nav.arrivals': 'ကားချိန်ဇယား',
        'nav.trace': 'A* ရှာဖွေမှု',
        'nav.prolog': 'Prolog',
        'nav.fleet': 'ကားစုစည်းမှု',
        'nav.about': 'AI အကြောင်း',
        'nav.shift': 'ကျွန်ုပ်၏ တာဝန်ချိန်',
        'nav.driverMap': 'ကျွန်ုပ်၏ လမ်းကြောင်း',
        'nav.admin': 'အချက်အလက် ထည့်ရန်',
        'nav.ops': 'လုပ်ငန်း ဒက်ရှ်ဘုတ်',

        'sim.speed': 'အရှိန်',

        'plan.h1': 'ခရီးစဉ် ရှာရန်',
        'plan.lede': 'အသံဖြင့် မေးပါ၊ သို့မဟုတ် မှတ်တိုင်နှစ်ခု ရွေးပါ။ A* က အဆိုပြုပြီး Prolog စည်းမျဉ်းက ဘယ်ဟာ သင့်တော်လဲ ဆုံးဖြတ်ပါတယ်။',
        'voice.h2': 'အသံဖြင့်',
        'voice.btn': 'ပြောပါ',
        'voice.hint': 'ဥပမာ ရွေးပြီး ခလုတ်ကို နှိပ်ပါ။',
        'voice.sampleLabel': 'ဗမာစာ ဥပမာ မေးခွန်း',
        'voice.listening': 'နားထောင်နေသည်…',
        'voice.notUnderstood': 'နားမလည်ပါ',
        'voice.hold': 'နှိပ်ပြီး ပြောပါ',
        'voice.recording': 'နားထောင်နေသည်… ပြီးရင် ထပ်နှိပ်ပါ',
        'voice.ready': 'ခလုတ်ကို နှိပ်ပြီး သွားလိုသည့် နေရာကို ပြောပါ။',
        'voice.thinking': 'အသံကို စာသားအဖြစ် ပြောင်းနေသည်…',
        'voice.orSample': 'ဥပမာတစ်ခု သုံးပါ',
        'voice.play': 'ဥပမာ ဖွင့်ရန်',
        'voice.micDenied': 'မိုက်ခရိုဖုန်း ခွင့်ပြုချက် မရပါ။ ဥပမာကို သုံးနိုင်ပါသည်။',
        'voice.micMissing': 'ဤစက်တွင် မိုက်ခရိုဖုန်း မတွေ့ပါ။',
        'voice.silent': 'ဘာမှ မကြားရပါ — ပိုနီးကပ်စွာ ပြောကြည့်ပါ။',
        'voice.noModel': 'အသံမှတ်သားစနစ် မရနိုင်ပါ။ ဥပမာများသာ အလုပ်လုပ်ပါမည်။',
        'voice.tooShort': 'အရမ်းတိုပါသည် — ခလုတ်ကို ဖိထားပြီး ပြောပါ။',
        'voice.from': 'မှ',
        'voice.to': 'ကို',

        'stops.h2': 'မှတ်တိုင် ရွေးရန်',
        'stops.from': 'မှ',
        'stops.to': 'ကို',
        'stops.stop': 'မှတ်တိုင်',
        'stops.search': 'ရှာမည်',
        'stops.placeholder': 'မှတ်တိုင် အမည် ရိုက်ထည့်ပါ…',
        'stops.noMatch': 'ကိုက်ညီသော မှတ်တိုင် မရှိပါ',
        'stops.more': 'နောက်ထပ် ရလဒ်များ ရှိသည် — ဆက်ရိုက်ပါ',
        'stops.clear': 'ရှင်းရန်',

        'profile.h2': 'ခရီးသည် အချက်အလက်',
        'profile.hint': 'တစ်ခုခု ရွေးလိုက်တာနဲ့ ရလဒ်တွေကို Prolog က ပြန်စစ်ပေးပါတယ်။',
        'profile.pregnant': 'ကိုယ်ဝန်ဆောင်',
        'profile.needsSeat': 'ထိုင်ခုံလို',
        'profile.mobility': 'သွားလာရခက်',
        'profile.student': 'ကျောင်းသား',
        'profile.card': 'YBS ကတ် ကိုင်ဆောင်သူ',
        'profile.monk': 'သံဃာ',

        'flood.title': 'တာမွေ ရေကြီးမှု',
        'flood.sub': 'စည်းမျဉ်းထဲသို့ flooded(s15) ထည့်ရန်',
        'flood.btn': 'ရေကြီး',

        'results.h2': 'ခရီးစဉ် ရွေးချယ်မှု',
        'results.empty': 'ခရီးစဉ် ရွေးပြီး ရှာမည် ကို နှိပ်ပါ။',
        'results.searching': 'ရှာနေသည်…',
        'results.none': 'ခရီးစဉ် မတွေ့ပါ',
        'results.pareto': 'Pareto အဆင့် ·',
        'results.kyats': 'ကျပ်',
        'results.minutes': 'မိနစ်',
        'results.transfers': 'ကြိမ် ပြောင်း',
        'results.suitable': 'အဆင်ပြေပါသည်',
        'results.pick': 'ဤခရီးစဉ် ရွေးမည်',
        'results.unavailable': 'မရနိုင်ပါ',
        'results.fareCard': 'ကတ်ဖြင့်',
        'results.fareCash': 'ငွေသား',
        'results.fullRoute': 'အစမှ အဆုံး',
        'results.fullRouteHint': 'လမ်းကြောင်းတစ်ခုလုံး စီးခြင်း — နှုန်းပိုမြင့်သည်',

        'reason.crowded': 'ဘတ်စ်ကား လူပြည့်နေသည်',
        'reason.transfers': 'ကားပြောင်းရမှု များလွန်းသည်',
        'reason.overbridge': 'ခုံးကျော်တံတား ဖြတ်ရမည်',
        'reason.flooded': 'ရေကြီးနေသော လမ်းပိုင်း',

        'map.h1': 'လမ်းကြောင်းမြေပုံ',
        'map.lede': 'YBS ကွန်ရက်အစစ်ပေါ်ရှိ ဘတ်စ်ကား ၂၃၆ စီး၊ တစ်စက္ကန့်တိုင်း အသစ်ပြင်ပြီး လမ်းကြောင်းအလိုက် အရောင်ခွဲထားသည်။ မြေပုံသည် ရှင်းလင်းစွာ စတင်သည် — အောက်တွင် လမ်းကြောင်းတစ်ခု ဖွင့်လိုက်ပါ။',
        'map.pick.h2': 'လမ်းကြောင်း ရွေးရန်',
        'map.pick.sub': 'ဘယ်လမ်းကြောင်းတွေ ဆွဲမလဲ — ရှာဖွေမှုက ၁၁၈ ခုလုံးကို လုပ်ပါသည်',
        'map.pick.placeholder': 'လမ်းကြောင်း နံပါတ် သို့ အမည် ရှာရန်',
        'map.pick.selectedOnly': 'ရွေးထားသော လမ်းများသာ',
        'map.pick.clear': 'ရှင်း',
        'map.offline': 'မြေပုံအကြောင်းအရာ မရနိုင်ပါ (offline) — လမ်းကြောင်းများသာ ပြသည်။',
        'map.tripEmpty': 'ခရီးစဉ်တစ်ခု ရွေးပါ။',
        'map.stops': 'မှတ်တိုင်',

        'arrivals.h1': 'ကား ဘယ်အချိန် ရောက်မလဲ',
        'arrivals.lede': 'ဘတ်စ်ကားများကို ရွှေ့နေသည့် မော်ဒယ်တစ်ခုတည်းက ခန့်မှန်းသည်၊ ဒါကြောင့် ဇယားနဲ့ မြေပုံ ဘယ်တော့မှ မကွဲပါ။',
        'arrivals.empty': 'မှတ်တိုင် ရွေးပါ။',
        'arrivals.hint': 'အချိန်တိုင်းကို အပိုင်းအခြားနဲ့ ပြပါတယ် — မော်ဒယ်က ±၂၅% ထက် ပိုမသိပါ။',
        'arrivals.none': 'ယခု ဘတ်စ်ကား မရှိပါ',
        'arrivals.arrived': 'ရောက်နေပြီ',
        'arrivals.bus': 'ကား',

        'trace.h1': 'A* ရှာဖွေမှု အခြေအနေ',
        'trace.lede': 'ရှာဖွေမှုက ဖြန့်ခဲ့သော node တိုင်းကို g, h, f နှင့်အတူ — heuristic က ဘာအကျိုးရှိစေလဲ ပြသည်။',
        'trace.empty': 'ရှာဖွေမှုတစ်ခု လုပ်ပါ။',
        'trace.hint': 'Dijkstra ဆိုတာ h = 0 ထားတဲ့ A* ပါပဲ — ဒါကြောင့် နှိုင်းယှဉ်မှုက မျှတပါတယ်။',
        'trace.expanded': 'A* ဖြန့်ခဲ့သော node',
        'trace.dijkstra': 'Dijkstra, h = 0',
        'trace.saved': 'heuristic က သက်သာစေသည်',
        'trace.bucket': 'ယာဉ်ကြောပိတ် အဆင့်',
        'trace.onFoot': 'ခြေလျင်',

        'prolog.h1': 'Prolog စည်းမျဉ်း ကွန်ဆိုး',
        'prolog.lede': 'ရည်မှန်းချက်တစ်ခု ရိုက်ထည့်ပါ၊ အင်ဂျင်က တိုက်ရိုက် ဖြေရှင်းပေးသည်။ အဖြေများကို ရှာယူထားခြင်း မဟုတ်ဘဲ တွက်ချက်ထားကြောင်း သက်သေဖြစ်သည်။',
        'prolog.solve': 'ဖြေရှင်းမည်',
        'prolog.empty': 'ရည်မှန်းချက်တစ်ခု ဖြေရှင်းပြီး တန်ဖိုးများကို ကြည့်ပါ။',
        'prolog.solving': 'ဖြေရှင်းနေသည်…',
        'prolog.false': 'false — အဖြေ မရှိပါ',
        'prolog.solutions': 'အဖြေ',
        'prolog.failed': 'တောင်းဆိုမှု မအောင်မြင်ပါ',
        'prolog.hint': 'ပထမ preset ကို run ပါ — အဖြေတစ်ခု၊ F = ၄၀၀၊ အဘယ်ကြောင့်ဆိုသော် m1 အကြောင်း ဘာမှ မသိသဖြင့် ယေဘုယျ clause သို့ ကျသွားသည်။ ဒုတိယကို run ပါ — အဖြေ နှစ်ခု၊ ၀ ပြီးမှ ၄၀၀။ သံဃာ clause က အရင်ရေးထားလို့ အရင်ကိုက်ပြီး၊ Prolog က နောက်ပြန်ရှာလို့ ယေဘုယျ clause ကိုပါ တွေ့သည်။',
        'prolog.preset.fare': 'ကတ်မရှိသူ → အပြည့်',
        'prolog.preset.fareFull': 'အစမှ အဆုံး စီးလျှင်',
        'prolog.preset.card': 'ကတ်ရှိသူ ဈေးသက်သာသည်',
        'prolog.preset.monk': 'fact ထည့်ပြီး အဖြေ နှစ်ခု',
        'prolog.preset.bonus': 'ယာဉ်မောင်း ဆုကြေး စည်းမျဉ်း',
        'prolog.preset.blocker': 'ဆုကြေး မရသည့် အကြောင်းရင်း',
        'prolog.preset.suitable': 'အရင် ရှာဖွေရန် လိုသည်',
        'prolog.preset.why': 'ခရီးစဉ် ပယ်ချရသည့် အကြောင်း',
        'prolog.preset.concession': 'ဘယ်နှုန်းထားက သက်ဆိုင်လဲ',
        'prolog.preset.journeyFare': 'ခရီးစဉ်တစ်ခုလုံး၏ ကျသင့်ငွေ',
        'prolog.preset.pareto': 'ကျန်ရစ်သော ရွေးချယ်စရာများ',
        'prolog.preset.dominates': 'ဘယ်ဟာက ဘယ်ဟာကို လွှမ်းမိုးလဲ',
        'prolog.preset.transferPoint': 'လမ်းကြောင်းများ ဆုံသည့် မှတ်တိုင်',
        'prolog.preset.topHeadway': 'ဤလ အကောင်းဆုံး ယာဉ်မောင်း',
        'prolog.preset.selfCheck': 'KB ကိုယ်တိုင် စစ်ဆေးမှု — အဖြေ မရှိသင့်',

        'fleet.h1': 'ကား စုစည်းမှု',
        'fleet.lede': 'simulate လုပ်ထားသော ဘတ်စ်ကားတိုင်း — ယခု ဘာလုပ်နေလဲ၊ နောက်ဘယ်ကို သွားမလဲ။',
        'fleet.hint': 'တစ်စက္ကန့်တိုင်း update လုပ်ပါတယ်။ ဘတ်စ်ကားများသည် လမ်းပိုင်းတစ်ခု မောင်းခြင်းနှင့် မှတ်တိုင်တွင် ရပ်နားခြင်း အလှည့်ကျ လုပ်သည်။',

        'about.h1': 'ဤ demo ဘယ်လို အလုပ်လုပ်လဲ',
        'about.lede': 'feature တစ်ခုစီသည် AI ခေါင်းစဉ်တစ်ခုနှင့် ကိုက်ညီသည်။',
        'about.search.title': 'Heuristic ရှာဖွေမှု',
        'about.search.body': '(မှတ်တိုင်, လမ်းကြောင်း) node များပေါ်တွင် A*။ အလေးချိန်ပေး အဖြေတစ်ခုတည်း အစား အချိန်၊ ခရီးစရိတ်၊ ကားပြောင်းရမှု သုံးခုကြားက Pareto အစုကို ပြန်ပေးသည်။ Dijkstra နှင့် ယှဉ်၍ node မည်မျှ ဖြန့်ခဲ့ကြောင်းလည်း ပြသည်။',
        'about.kr.title': 'အသိပညာ ကိုယ်စားပြုမှု',
        'about.kr.body': 'ybs.pl မှ တင်ထားသော tuProlog အင်ဂျင်။ Java သည် fact များ ထည့်ပြီး ရည်မှန်းချက် မေးရုံသာ — ဆုံးဖြတ်ချက်တိုင်းကို စည်းမျဉ်းက ချမှတ်သည်။',
        'about.csp.title': 'ကန့်သတ်ချက် ဖြေရှင်းမှု',
        'about.csp.body': 'suitable(J,R) သည် violates/2 ပေါ်တွင် negation-as-failure ဖြစ်သည်။ ယှဉ်တွဲ violation_reason/3 က အကြောင်းရင်းကို သယ်ဆောင်သဖြင့် ပယ်ချခံရသော ခရီးစဉ်က အကြောင်းပြချက် ပြောနိုင်သည်။',
        'about.uncertainty.title': 'မသေချာမှုအောက်တွင် ဆင်ခြင်ခြင်း',
        'about.uncertainty.body': 'လက်ဖြင့် စုဆောင်းထားသော ယာဉ်ကြောပိတ် prior — သင်ယူထားသော မော်ဒယ်ဟု မဟန်ဆောင်ဘဲ ယူဆချက်အဖြစ် ရိုးရိုးသားသား ဖော်ပြထားသည်။ ခန့်မှန်းချက်တိုင်းသည် အပိုင်းအခြားဖြစ်ပြီး အမှတ်တစ်ခုတည်း မဟုတ်ပါ။',
        'about.nlp.title': 'NLP / စကားသံ',
        'about.nlp.body': 'ဗမာအသံမှ စာသား၊ စာသားမှ entity — မှ နှင့် ကို ဝိဘတ်နှစ်ခုကို အခြေခံသည်။ ဘာသာစကား မော်ဒယ် မဟုတ်ဘဲ စာလုံး ကိုက်ညီမှုသာ ဖြစ်ပြီး၊ ယုံကြည်စွာ လက်မခံမိစေရန် parse ရလဒ်ကို UI တွင် ပြသည်။',
        'about.oneProcess.title': 'ရည်ရွယ်ချက်ရှိရှိ process တစ်ခုတည်း',
        'about.oneProcess.body': 'Docker မရှိ၊ microservice မရှိ၊ build step မရှိ။ demo တစ်ခုလုံးသည် စက်တွင်းရှိ MySQL နှင့် ချိတ်ဆက်ထားသော Spring Boot process တစ်ခုတည်းဖြစ်၍ command တစ်ကြောင်းနှင့် စတင်နိုင်သည်။ Leaflet ကို ထည့်သွင်းထားပြီး မြေပုံ tile သာ အင်တာနက် လိုသည်။',

        'shift.h1': 'ကျွန်ုပ်၏ တာဝန်ချိန်',
        'shift.lede': 'သင့်လမ်းကြောင်း၊ သင့်ကားများ၊ သင့်လစဉ် အမှတ် — နှင့် ဆုကြေးကို ဘယ်စည်းမျဉ်းက ဆုံးဖြတ်ခဲ့လဲ။',
        'shift.who': 'ယာဉ်မောင်း',
        'shift.route': 'တာဝန်ကျ လမ်းကြောင်း',
        'shift.month': 'လ',
        'shift.headway': 'ကားချိန်ကွာဟမှု အမှတ်',
        'shift.harsh': 'ကြမ်းတမ်းသော မောင်းနှင်မှု',
        'shift.complaints': 'အတည်ပြု တိုင်ကြားချက်',
        'shift.bonus': 'ဆုကြေး',
        'shift.eligible': 'ရထိုက်သည်',
        'shift.notEligible': 'မရထိုက်ပါ',
        'shift.becauseOk': 'သုံးချက်လုံး ပြည့်မီသည် — ကားချိန်ကွာဟမှု ၈၀ အထက်၊ ကြမ်းတမ်းမောင်းနှင်မှု ၅ အောက်၊ အတည်ပြု တိုင်ကြားချက် မရှိ။',
        'shift.blockedBy': 'ဆုကြေး မရသည့် အကြောင်းရင်း',
        'shift.myBuses': 'ကျွန်ုပ်၏ လမ်းကြောင်းပေါ်ရှိ ကားများ',
        'shift.noBuses': 'ယခု ကား မရှိပါ',
        'shift.incidents': 'ကျွန်ုပ်၏ လမ်းကြောင်းရှိ ဖြစ်စဉ်များ',
        'shift.noIncidents': 'ဖြစ်စဉ် မရှိပါ',
        'shift.bunching': 'ကား စုပုံနေမှု သတိပေးချက်',
        'shift.driverMapLede': 'သင့်လမ်းကြောင်းနှင့် ယခု ပြေးဆွဲနေသော ကားများ။',

        'blocker.headway': 'ကားချိန်ကွာဟမှု အမှတ် ၈၀ အောက်',
        'blocker.harsh': 'ကြမ်းတမ်းမောင်းနှင်မှု ၅ ကြိမ်ထက် များသည်',
        'blocker.complaint': 'အတည်ပြု တိုင်ကြားချက် ရှိသည်',

        'bus.driving': 'မောင်းနေသည်',
        'bus.dwelling': 'ရပ်နားနေသည်',
        'bus.route': 'လမ်းကြောင်း',
        'bus.next': 'နောက်မှတ်တိုင်',
        'bus.crowding': 'လူစည်ကားမှု',
        'bus.crowd.free': 'နေရာလွတ်',
        'bus.crowd.standing': 'ရပ်ရသည်',
        'bus.crowd.full': 'ပြည့်နေသည်',

        'alert.ready': 'ဆင်းရန် အဆင်သင့်ဖြစ်ပါ',
        'alert.watching': 'ကို စောင့်ကြည့်နေသည်',
        'alert.alightAt': 'တွင် ဆင်းရန်',
        'alert.until': 'အထိ',
        'alert.away': 'မီတာ ကျန်သည်',

        'ops.title': 'YBS လုပ်ငန်း ဒက်ရှ်ဘုတ်',
        'ops.sub': 'ဆုကြေး ရထိုက်မှုကို Prolog အင်ဂျင်က တိုက်ရိုက် တွက်ချက်သည်',
        'ops.bunching': 'ကား စုပုံနေမှု သတိပေးချက်',
        'ops.bunching.sub': 'လမ်းကြောင်းတစ်ခုတွင် ကားချိန်ကွာဟမှု ပြိုကျသွားသည်',
        'ops.incidents': 'ဖြစ်စဉ်များ',
        'ops.incidents.sub': 'လမ်းကြောင်းနှင့် နာရီအလိုက်',
        'ops.bonus': 'ယာဉ်မောင်း ဆုကြေး ရထိုက်မှု',
        'ops.bonus.sub': 'badge တစ်ခုစီသည် tuProlog သို့ ပို့သော bonus_eligible(D, M) ရည်မှန်းချက် တစ်ခုဖြစ်သည်',
        'ops.bonus.note': 'seed လုပ်ထားသော database ကော်လံသည် ယာဉ်မောင်းတိုင်းအတွက် false ဖြစ်သည်။ ဒါကြောင့် "ရထိုက်သည်" ပြနေတာမှန်သမျှကို ခုနက စည်းမျဉ်းက တွက်ထုတ်ခဲ့တာ — သိမ်းထားရာမှ ဖတ်ယူတာ မဟုတ်ပါ။',
        'ops.driver': 'ယာဉ်မောင်း',
        'ops.route': 'လမ်းကြောင်း',
        'ops.hour': 'နာရီ',
        'ops.type': 'အမျိုးအစား',
        'ops.count': 'အရေအတွက်',
        'ops.backToPlanner': 'ခရီးစဉ်ရှာဖွေမှု',
        'admin.link': 'အချက်အလက် ထည့်ရန် / ပြင်ရန်',
        'ops.link': 'ယာဉ်မောင်း ဆုကြေး ဒက်ရှ်ဘုတ်'
    },

    en: {
        'app.title': 'YBS — AI Journey Planner (Demo)',
        'brand.name': 'YBS Journey Planner',
        'hello.title': 'Hello!',
        'hello.body': 'How can we help you today?',
        'step.how': 'Choose how you want to plan',
        'step.how.info': 'Speak your trip out loud, or use one of the sample queries.',
        'step.stops': 'Choose two stops',
        'step.stops.info': 'Pick where you are starting and where you are going, then search.',
        'voice.tapHint': 'Tap the mic and say where you want to go.',
        'voice.or': 'or',
        'stops.swap': 'Swap start and destination',
        'stops.searchRoute': 'Search route',
        'profile.optional': '(optional)',
        'quick.fav': 'Favorites',
        'quick.favEmpty': 'No favourites saved yet — search a journey and you can keep it here.',
        'chip.i.pregnant': 'Journeys with more than one change are ruled out.',
        'chip.i.needsSeat': 'Buses predicted to be full are ruled out.',
        'chip.i.mobility': 'Journeys crossing a pedestrian overbridge are ruled out.',
        'chip.i.student': 'Half fare when paired with a YBS card.',
        'chip.i.card': 'A YBS card lowers every fare.',
        'chip.i.monk': 'Travels free.',
        'quick.label': 'Quick picks',
        'quick.home': 'Home',
        'quick.work': 'Work',
        'quick.school': 'School',
        'quick.market': 'Market',
        'tips.title': 'Tips',
        'tips.body': 'More filters = better suggestions for you.',
        'promise.see.t': 'Easy to see',
        'promise.see.s': 'Big text and clear icons',
        'promise.use.t': 'Easy to use',
        'promise.use.s': 'Simple steps, no confusion',
        'promise.safe.t': 'Safe & friendly',
        'promise.safe.s': 'Designed for everyone',
        'promise.trust.t': 'Trusted',
        'promise.trust.s': 'Based on YBS rules',
        'foot.tagline': '© YBS Journey Planner · Making travel easy for everyone',
        'pref.light': 'Light mode',
        'pref.dark': 'Dark mode',
        'pref.textSize': 'Text size',
        'a11y.help': 'Help',
        'a11y.needHelp': 'Need help?',
        'brand.sub': 'Your friendly AI travel helper',
        'lang.switch': 'Change language',

        'role.rider': 'Rider',
        'role.driver': 'Driver',
        'role.admin': 'Admin',
        'role.switch': 'Who are you?',
        'auth.h1': 'Sign in',
        'auth.lede': 'Planning a trip never needs an account. Signing in is only for a driver\u2019s own shift and the admin screens.',
        'auth.username': 'Username',
        'auth.password': 'Password',
        'auth.signIn': 'Sign in',
        'auth.signOut': 'Sign out',
        'auth.signInFor': 'Sign in to reach this',
        'auth.badCredentials': 'Wrong username or password',
        'auth.failed': 'Sign-in failed',
        'auth.demoAccounts': 'Demo accounts',

        'nav.group.rider': '',
        'nav.group.evidence': 'Tools & Help',
        'nav.group.operations': 'Operations',
        'nav.group.shift': 'My shift',
        'nav.group.data': 'Data',

        'nav.plan': 'Plan a trip',
        'nav.map': 'Live map',
        'nav.arrivals': 'Arrivals board',
        'nav.trace': 'Search trace',
        'nav.prolog': 'Rule console',
        'nav.fleet': 'Fleet',
        'nav.about': 'How it works',
        'nav.shift': 'My shift',
        'nav.driverMap': 'My route',
        'nav.admin': 'Add / edit data',
        'nav.ops': 'Ops dashboard',

        'sim.speed': 'Simulation speed',

        'plan.h1': 'Plan a trip',
        'plan.lede': 'Ask by voice or pick two stops. A* proposes; the Prolog rule base decides what suits you.',
        'voice.h2': 'By voice',
        'voice.btn': 'Speak',
        'voice.hint': 'Pick a sample and press speak.',
        'voice.sampleLabel': 'Sample Burmese query',
        'voice.listening': 'listening…',
        'voice.notUnderstood': 'not understood',
        'voice.hold': 'Tap and speak',
        'voice.recording': 'Listening… tap again when done',
        'voice.ready': 'Tap the mic and say where you want to go.',
        'voice.thinking': 'Transcribing…',
        'voice.orSample': 'Use a sample',
        'voice.play': 'Play sample',
        'voice.micDenied': 'Microphone permission denied. You can still use a sample.',
        'voice.micMissing': 'No microphone found on this device.',
        'voice.silent': 'Heard nothing — try speaking closer to the mic.',
        'voice.noModel': 'The recogniser is not running, so live speech cannot be read. Samples still work.',
        'voice.tooShort': 'That was very short — hold the button while you speak.',
        'voice.from': 'from',
        'voice.to': 'to',

        'stops.h2': 'Choose stops',
        'stops.from': 'From',
        'stops.to': 'To',
        'stops.stop': 'Stop',
        'stops.search': 'Search',
        'stops.placeholder': 'Type a stop name…',
        'stops.noMatch': 'No stop matches that',
        'stops.more': 'More matches — keep typing',
        'stops.clear': 'Clear',

        'profile.h2': 'Rider profile',
        'profile.hint': 'Toggling any of these re-filters the results through the rule base.',
        'profile.pregnant': 'pregnant',
        'profile.needsSeat': 'needs seat',
        'profile.mobility': 'mobility',
        'profile.student': 'student',
        'profile.card': 'holds a YBS card',
        'profile.monk': 'monk',

        'flood.title': 'Flood at Tamwe',
        'flood.sub': 'Assert flooded(s15) into the rule base',
        'flood.btn': 'Flood',

        'results.h2': 'Journey options',
        'results.empty': 'Choose two stops and press Search.',
        'results.searching': 'searching…',
        'results.none': 'No journey found',
        'results.pareto': 'Pareto front ·',
        'results.kyats': 'kyats',
        'results.minutes': 'min',
        'results.transfers': 'transfers',
        'results.suitable': 'suitable for this rider',
        'results.pick': 'Take this journey',
        'results.unavailable': 'unavailable',
        'results.fareCard': 'card fare',
        'results.fareCash': 'cash fare',
        'results.fullRoute': 'end to end',
        'results.fullRouteHint': 'rides the whole route — the higher rate',

        'reason.crowded': 'no seat available',
        'reason.transfers': 'too many transfers',
        'reason.overbridge': 'overbridge not accessible',
        'reason.flooded': 'route segment is flooded',

        'map.h1': 'Live route map',
        'map.lede': '236 buses across the real YBS network, updating every second and coloured by route. The map starts clean — switch a route on below to draw its road-following line and its stops.',
        'map.pick.h2': 'Which routes to draw',
        'map.pick.sub': 'Search runs over all 118 regardless',
        'map.pick.placeholder': 'Search route code or name',
        'map.pick.selectedOnly': 'buses: selected routes only',
        'map.pick.clear': 'Clear',
        'map.offline': 'Map tiles unavailable — routes, stops and buses still shown.',
        'map.tripEmpty': 'Start a trip from Plan a trip to watch the countdown here.',
        'map.stops': 'stops',

        'arrivals.h1': 'When does my bus arrive',
        'arrivals.lede': 'Predicted from the same model that moves the buses, so the board and the map can never disagree.',
        'arrivals.empty': 'Pick a stop.',
        'arrivals.hint': 'Every figure is a range, never a single number — the model does not know better than ±25%, and a point estimate would claim confidence it has not earned.',
        'arrivals.none': 'no bus inbound',
        'arrivals.arrived': 'arriving',
        'arrivals.bus': 'bus',

        'trace.h1': 'A* search trace',
        'trace.lede': 'Every node the search expanded with its g, h and f — and what the heuristic bought.',
        'trace.empty': 'Run a search to watch the frontier expand.',
        'trace.hint': 'Dijkstra is literally this same search with h = 0 — which is what makes the comparison fair rather than a comparison of two different implementations.',
        'trace.expanded': 'A* nodes expanded',
        'trace.dijkstra': 'Dijkstra, h = 0',
        'trace.saved': 'saved by the heuristic',
        'trace.bucket': 'traffic bucket',
        'trace.onFoot': 'on foot',

        'prolog.h1': 'Prolog rule console',
        'prolog.lede': 'Type a goal; the engine solves it live. This is the proof the answers are derived, not looked up.',
        'prolog.solve': 'Solve',
        'prolog.empty': 'Solve a goal to see its bindings.',
        'prolog.solving': 'solving…',
        'prolog.false': 'false — no solution',
        'prolog.solutions': 'solution(s)',
        'prolog.failed': 'request failed',
        'prolog.hint': 'Run the first preset: one solution, F = 400, because nothing is known about m1 so it falls through to the cash tariff. Now run the monk one: two solutions, 0 then 400. The monk clause matches first because it is written first, then Prolog backtracks and finds the general clause still applies. Clause ordering and backtracking, on a fact invented one second earlier.',
        'prolog.preset.fare': 'no card → cash fare',
        'prolog.preset.fareFull': 'riding it end to end',
        'prolog.preset.card': 'a card is cheaper',
        'prolog.preset.monk': 'assert a fact, then two solutions',
        'prolog.preset.bonus': 'driver bonus rule',
        'prolog.preset.blocker': 'why a bonus was withheld',
        'prolog.preset.suitable': 'needs a search first',
        'prolog.preset.why': 'why an option was withdrawn',
        'prolog.preset.concession': 'which fare clause applied',
        'prolog.preset.journeyFare': 'the KB adds up the legs',
        'prolog.preset.pareto': 'which options survive',
        'prolog.preset.dominates': 'one option beating another',
        'prolog.preset.transferPoint': 'stops where routes meet',
        'prolog.preset.topHeadway': 'best headway this month',
        'prolog.preset.selfCheck': 'a KB self-check — expect no solutions',

        'fleet.h1': 'Fleet',
        'fleet.lede': 'Every simulated bus: what it is doing right now, and where it goes next.',
        'fleet.hint': 'Updates once a second. Buses alternate between DRIVING a segment and DWELLING at the stop they reach — the waiting is real, not a fudge factor folded into driving time.',

        'about.h1': 'How this demo works',
        'about.lede': 'Each feature maps to one AI topic. This page is the map between them.',
        'about.search.title': 'Heuristic search',
        'about.search.body': 'A* over (stop, route) nodes. Returns a Pareto set over time, fare and transfers rather than one weighted answer, and reports how many nodes it expanded against plain Dijkstra.',
        'about.kr.title': 'Knowledge representation',
        'about.kr.body': 'A tuProlog engine loaded from ybs.pl. Java only asserts ground facts and asks goals — every decision is made by the rule base.',
        'about.csp.title': 'Constraint satisfaction',
        'about.csp.body': 'suitable(J,R) is negation-as-failure over violates/2. A parallel violation_reason/3 carries the cause, so a withdrawn option can say why.',
        'about.uncertainty.title': 'Reasoning under uncertainty',
        'about.uncertainty.body': 'A hand-collected traffic prior, stated as an assumption rather than dressed up as a learned model. Every estimate is an interval, never a point.',
        'about.nlp.title': 'NLP / speech',
        'about.nlp.body': 'Burmese audio to transcript to entities, keyed off the postpositions မှ (from) and ကို (to). String matching, not a language model — and the UI shows the parse so nobody takes it on trust.',
        'about.oneProcess.title': 'One process, on purpose',
        'about.oneProcess.body': 'No Docker, no microservices, no build step. The whole demo is one Spring Boot process backed by a local MySQL database, so it starts with a single command and needs no separate servers. Leaflet is vendored; only map tiles need the internet.',

        'shift.h1': 'My shift',
        'shift.lede': 'Your route, your buses, your month — and which rule decided the bonus.',
        'shift.who': 'Driver',
        'shift.route': 'Assigned route',
        'shift.month': 'Month',
        'shift.headway': 'Headway score',
        'shift.harsh': 'Harsh events',
        'shift.complaints': 'Upheld complaints',
        'shift.bonus': 'Bonus',
        'shift.eligible': 'eligible',
        'shift.notEligible': 'not eligible',
        'shift.becauseOk': 'All three conditions hold — headway at or above 80, no more than 5 harsh events, and no upheld complaint.',
        'shift.blockedBy': 'Why the bonus was withheld',
        'shift.myBuses': 'Buses on my route right now',
        'shift.noBuses': 'No buses running',
        'shift.incidents': 'Incidents on my route',
        'shift.noIncidents': 'No incidents',
        'shift.bunching': 'Bunching alert',
        'shift.driverMapLede': 'Your route and the buses running it right now.',

        'blocker.headway': 'headway score below 80',
        'blocker.harsh': 'more than 5 harsh events',
        'blocker.complaint': 'an upheld complaint',

        'bus.driving': 'driving',
        'bus.dwelling': 'at a stop',
        'bus.route': 'Route',
        'bus.next': 'Next stop',
        'bus.crowding': 'Crowding',
        'bus.crowd.free': 'seats free',
        'bus.crowd.standing': 'standing',
        'bus.crowd.full': 'full',

        'alert.ready': 'Get ready to alight',
        'alert.watching': 'watching bus',
        'alert.alightAt': 'alight at',
        'alert.until': 'until',
        'alert.away': 'm away',

        'ops.title': 'YBS Ops Dashboard',
        'ops.sub': 'Bonus eligibility is computed live by the Prolog engine — watch the server console',
        'ops.bunching': 'Bunching alert',
        'ops.bunching.sub': 'Headway has collapsed on one route',
        'ops.incidents': 'Incidents',
        'ops.incidents.sub': 'By route and hour',
        'ops.bonus': 'Driver bonus eligibility',
        'ops.bonus.sub': 'Each badge is one bonus_eligible(D, M) goal sent to tuProlog',
        'ops.bonus.note': 'The seeded database column is false for every driver, so anything showing "eligible" was derived by the rule base just now — not read from storage.',
        'ops.driver': 'Driver',
        'ops.route': 'Route',
        'ops.hour': 'Hour',
        'ops.type': 'Type',
        'ops.count': 'Count',
        'ops.backToPlanner': 'Journey planner',
        'admin.link': 'Add / edit data',
        'ops.link': 'Driver bonus dashboard'
    }
};

let LANG = 'my';
try {
    const saved = localStorage.getItem(LANG_KEY);
    if (saved === 'my' || saved === 'en') LANG = saved;
} catch (e) {
    // Private windows and blocked site data throw on read. The default is fine.
}

/** The current language's string for a key, falling back to Burmese then the key itself. */
function t(key) {
    // Presence, not truthiness. A string deliberately set to '' - the blank nav group
    // heading, for one - is an answer, and || treated it as a missing key and rendered
    // the key itself on screen.
    const table = STRINGS[LANG] || {};
    if (key in table) return table[key];
    if (key in STRINGS.my) return STRINGS.my[key];
    return key;
}

function lang() { return LANG; }

/** Stop and route names ship in both languages already, so pick, do not translate. */
function stopName(stop) {
    if (!stop) return '';
    return LANG === 'en' ? (stop.nameEn || stop.nameMy || '') : (stop.nameMy || stop.nameEn || '');
}

/** Route names exist in Burmese only, so English falls back to the code plus the name. */
function routeName(route) {
    if (!route) return '';
    return route.nameMy || route.code || '';
}

/**
 * Rewrites every declared string on the page, then tells the dynamic views to
 * re-render. Static markup is handled here; anything built by JavaScript listens
 * for ybs:lang, because its text was baked in at render time.
 */
function applyLang(root) {
    const scope = root || document;
    scope.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n); });
    scope.querySelectorAll('[data-i18n-ph]').forEach(el => { el.placeholder = t(el.dataset.i18nPh); });
    scope.querySelectorAll('[data-i18n-aria]').forEach(el => {
        el.setAttribute('aria-label', t(el.dataset.i18nAria));
    });
    scope.querySelectorAll('[data-i18n-title]').forEach(el => {
        el.title = t(el.dataset.i18nTitle);
    });

    if (!root) {
        document.documentElement.lang = LANG;
        document.title = t('app.title');
        document.querySelectorAll('[data-lang-to]').forEach(b => {
            initLangSwitch(b.parentElement);
        });
        document.dispatchEvent(new CustomEvent('ybs:lang', { detail: { lang: LANG } }));
    }
}

function setLang(next) {
    if (next !== 'my' && next !== 'en') return;
    LANG = next;
    try { localStorage.setItem(LANG_KEY, next); } catch (e) { /* not worth failing over */ }
    applyLang();
}

/**
 * The language control: one pill, naming the language you are reading.
 *
 * <p>It used to be two buttons side by side, which asked the reader to compare two
 * words and work out which was already true. A pill that says "EN" and opens
 * Burmese is one fact instead of a comparison - and it is the pattern every phone
 * on the network already uses.
 */
function langSwitchHtml() {
    const other = LANG === 'my' ? 'en' : 'my';
    return `<button type="button" class="lang-pill" data-lang-to="${other}"
                    aria-label="${t('lang.switch')}" title="${t('lang.switch')}">
        <span aria-hidden="true">\u{1F310}</span>
        <span>${LANG === 'my' ? 'မြန်မာ' : 'EN'}</span>
        <span class="caret" aria-hidden="true">\u25BE</span>
    </button>`;
}

function initLangSwitch(el) {
    if (!el) return;
    el.innerHTML = langSwitchHtml();
    el.querySelectorAll('[data-lang-to]').forEach(b => {
        b.addEventListener('click', () => setLang(b.dataset.langTo));
    });
}
