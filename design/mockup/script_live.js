/* ===================== r9 · live prototype (phones 1 + 6): add field + smart sort, magic words, toasts with Undo,
   the Write / Snap / Speak letter-set sheet, swipe to check / tear off, pull to refresh, collapsed FAB, menu, demo controls ===================== */
var D=/*DATA*/{};var CATL=D.cat||[];
function symOf(k){return /^ai-/.test(k)?'sticker-'+k:'sticker-i-'+k;}
function esc(t){return String(t).replace(/[&<>"]/g,function(c){return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c];});}
function norm(t){var s=String(t).toLowerCase();try{s=s.normalize('NFD').replace(/[̀-ͯ]/g,'');}catch(e){}return s.replace(/[’']/g,"'").replace(/\s+/g,' ').trim();}
function cap1(t){t=String(t).trim();return t.charAt(0).toUpperCase()+t.slice(1);}
function findItem(t){var q=norm(t).replace(/^(?:a |an |some |the |more |\d+\s*(?:x|×)?\s*)+/,'').replace(/[.!?]+$/,'');if(!q)return null;var best=null,bs=0;
 CATL.forEach(function(c){(c.t||[]).forEach(function(term){var tt=norm(term),sc=0;if(q===tt)sc=100+tt.length;else if(q===tt+'s'||q+'s'===tt||q===tt+'es')sc=90+tt.length;else if((' '+q+' ').indexOf(' '+tt+' ')>=0)sc=50+tt.length;else if(tt.length>4&&(' '+tt+' ').indexOf(' '+q+' ')>=0)sc=30+q.length;if(sc>bs){bs=sc;best=c;}});});
 return best?Object.assign({},best):null;}
function guessAisle(q){q=norm(q);return /bread|bun|roll|bagel|cake|pie|tart/.test(q)?2:/ham|chicken|beef|pork|fish|tofu|salami|turkey/.test(q)?3:/flour|sugar|spice|oil|sauce|rice|pasta|can|beans/.test(q)?4:/cereal|coffee|tea|jam|syrup|snack|cookie|chip|candy/.test(q)?5:/milk|cheese|yog|butter|cream|egg|juice|drink|soda/.test(q)?6:/frozen|ice/.test(q)?7:/soap|paper|clean|towel|bag|shampoo|pet|cat|dog|baby/.test(q)?8:1;}
function unknownItem(t){var q=String(t).trim().replace(/^(?:a |an |some |the )/i,'');return {k:'capsule',n:cap1(q),q:'each · new sticker',a:guessAisle(q),c:'',unk:1};}
function aisleEl(scr,n){return scr.querySelector('.sk-aw[data-n="'+n+'"]');}
function hkMarkup(){var h=root.querySelector('.sk-s-list .sk-row .sk-cb .sk-hanko');return h?h.outerHTML:'';}
function rowHTML(it,o){var sym=symOf(it.k),nm=esc(it.n);var badge=o.badge?'<svg class="sk-badgeico" aria-hidden="true"><use href="#sticker-i-'+(o.badge==='fav'?'heart':'star')+'"/></svg>':'';
 return '<span class="sk-ico'+(o.shiny?' is-holoring':'')+'"><svg class="sk-real" width="52" height="52" role="img" aria-label="'+nm+', '+esc(it.q)+'"><use href="#'+sym+'"/></svg>'
  +'<svg class="sk-ghost" viewBox="0 0 52 52" width="52" height="52" aria-hidden="true"><circle class="sk-g2" cx="26" cy="27" r="21"/><use href="#'+sym+'" width="52" height="52" opacity=".16"/></svg>'+badge+'</span>'
  +'<span class="sk-txt"><span class="sk-nm">'+nm+'</span><span class="sk-dt">'+esc(it.q)+(o.chip?' <span class="sk-shinychip">'+o.chip+'</span>':'')+'</span></span>'
  +'<span class="sk-cb" role="checkbox" tabindex="0" aria-checked="false" aria-label="'+nm+'"><svg class="sk-spot" viewBox="0 0 30 30" width="30" height="30" aria-hidden="true"><use href="#sticker-spotg"/></svg>'+hkMarkup()+'</span>';}
function tripChanged(scr){if(scr.__tripDone){scr.__tripDone=false;bookPerfect(scr,false);}}
function addRow(scr,it,o){o=o||{};var aw=aisleEl(scr,it.a)||aisleEl(scr,8);if(!aw)return null;var ul=aw.querySelector('.sk-opened .sk-rows');
 var li=document.createElement('li');li.className='sk-row is-new';li.setAttribute('data-key',it.k);li.setAttribute('data-sk','row');li.__live=1;if(it.c)li.setAttribute('data-cat',it.c);
 if(o.shiny&&/^(apple|maple|croissant)$/.test(it.k))li.setAttribute('data-mini',it.k+'-holo');if(it.bday&&D.bday)li.setAttribute('data-hanko',D.bday);
 li.innerHTML=rowHTML(o.shiny&&/^(apple|maple|croissant)$/.test(it.k)?Object.assign({},it,{k:it.k+'-holo'}):it,o);if(o.before&&o.before.parentNode===ul)ul.insertBefore(li,o.before);else ul.appendChild(li);
 tm(function(){li.classList.remove('is-new');},700);syncAisle(aw);bookResize(scr);lcdCount(scr);tripChanged(scr);
 var fb=aw.querySelector('.sk-folded .sk-badge');if(fb&&!RM)restart(fb,'is-pop');if(!aw.classList.contains('is-open'))wob(aw.querySelector('.sk-folded .bk'));
 return li;}
function removeRow(scr,li){var aw=li.closest('.sk-aw');var wasChecked=li.classList.contains('is-checked');if(wasChecked)bookTake(scr,li);clearPend(li);var next=li.nextSibling,ul=li.parentNode;li.remove();
 if(aw)syncAisle(aw);bookResize(scr);lcdCount(scr);return {li:li,ul:ul,next:next,aw:aw,checked:wasChecked};}
function restoreRow(scr,r){if(r.next&&r.next.parentNode===r.ul)r.ul.insertBefore(r.li,r.next);else r.ul.appendChild(r.li);r.li.classList.remove('is-tear','is-swiping','is-swl','is-swr');r.li.style.removeProperty('--sx');
 bookResize(scr);if(r.checked){r.li.classList.add('is-checked','is-inbk');r.li.__slot=null;bookPut(scr,r.li);}syncAisle(r.aw);lcdCount(scr);}
/* -------- the add pipeline: smart sort → (capsule pop for live-drawn stickers) → the row pops in on its own line; nothing flies -------- */
var ADDN=0;
function scrollToAisle(scr,aw,cb){var sc=scr.querySelector('.sk-scroll');if(!sc||!aw){cb&&cb();return;}var r=aw.getBoundingClientRect(),sr=sc.getBoundingClientRect(),z=zf(scr);if(r.top>=sr.top&&r.bottom<=sr.bottom-90){cb&&cb();return;}
 scr.__prog=Date.now();try{sc.scrollTo({top:Math.max(0,sc.scrollTop+(r.top-sr.top)/z-60),behavior:RM?'auto':'smooth'});}catch(e){sc.scrollTop+=(r.top-sr.top)/z-60;}tm(function(){cb&&cb();},RM?0:420);}
function addItems(scr,items,opts){opts=opts||{};if(!items.length)return;var added=[],delay=0;
 items.forEach(function(it,ix){var o={badge:'new'};ADDN++;if(ADDN%4===0&&!it.ai&&!it.unk){o.shiny=1;o.chip='shiny!';}
  var dup=null;$$('.sk-aw .sk-opened .sk-row[data-key="'+it.k+'"]',scr).forEach(function(r){if(!r.classList.contains('is-checked'))dup=r;});
  var go=function(){if(it.jack){o.shiny=1;o.chip='jackpot!';dup=null;}if(dup){var dt=dup.querySelector('.sk-dt');var m=/ ×(\d+)$/.exec(dt.textContent);dup.__qty=(dup.__qty||1)+1;dt.lastChild&&dt.lastChild.nodeType===3&&/ ×\d+$/.test(dt.lastChild.nodeValue)?dt.lastChild.nodeValue=' ×'+dup.__qty:dt.appendChild(document.createTextNode(' ×'+dup.__qty));restart(dup,'is-bump');added.push({dup:dup});return;}
   var li=addRow(scr,it,o);if(li)added.push({li:li});if(o.shiny&&li){var c=rel(li.querySelector('.sk-ico'),scr);burst(scr,c.x,c.y,['star4','star','heart'],8);snd('kira');}};
  if(it.roll){(function(itx,g){tm(function(){rollEgg(scr,itx,g);},delay);})(it,go);delay+=RM?0:3300;}
  else if(it.ai||it.unk){(function(itx,g){tm(function(){capsulePop(scr,itx,g);},delay);})(it,go);delay+=RM?0:(scr.__jack?1500:2900);if(!scr.__jack)scr.__jack=1;}
  else{tm(go,delay);delay+=RM?0:90;}
  if(it.bday)tm(function(){confetti(scr);},0);});
 tm(function(){var n=added.length;if(!n)return;var one=items.length===1?items[0]:null;var aw=one?aisleEl(scr,one.a):null;
  var msg=one?(added[0].dup?one.n+' ×'+added[0].dup.__qty+' on your list ♡':'Added '+one.n+' to '+(aw?aw.getAttribute('data-name'):'your list')+' ♡'):'Added '+n+' items to Saturday shop ♡';
  toast(scr,msg,function(){added.forEach(function(a){if(a.li&&a.li.parentNode)removeRow(scr,a.li);if(a.dup){a.dup.__qty=Math.max(1,(a.dup.__qty||2)-1);var dt=a.dup.querySelector('.sk-dt');if(dt.lastChild&&dt.lastChild.nodeType===3)dt.lastChild.nodeValue=a.dup.__qty>1?' ×'+a.dup.__qty:'';}});});
  picnicCheck(scr);},delay+(RM?0:120));}
function submitText(scr,text,from){var t=String(text||'').trim();if(!t)return false;var n=norm(t);
 if(/kira\s*-?\s*kira/.test(n)||/キラキラ|きらきら/.test(t)){if(S.eggs)kirakira(scr);else toast(scr,'“kirakira” ♡');return true;}
 if(/pancake|from scratch|recipe|ingredients for/.test(n)){openSheet(scr,'write');runRecipe(scr);return true;}
 var parts=t.split(/\s*(?:,|\n|;|\band\b|\bet\b|&|\+)\s*/i).map(function(x){return x.trim();}).filter(Boolean);
 var items=parts.map(function(p){var it=findItem(p)||unknownItem(p);var q=norm(p);if(S.eggs&&(it.k==='strawberries'||it.k==='apple'))it.roll=1;return it;});
 addItems(scr,items,{from:from});return true;}
function fieldOf(scr){return scr.querySelector('.sk-addbar .sk-fin');}
function sendField(scr){var f=fieldOf(scr);if(!f)return;var v=f.value;if(!v.trim()){f.focus();restart(f.closest('.sk-field'),'is-nudge');return;}f.value='';syncField(f);submitText(scr,v,'bar');snd('pon');}
function syncField(f){var w=f.closest('.sk-field');if(w)w.classList.toggle('has-text',!!f.value);}
root.addEventListener('input',function(e){if(e.target.classList&&e.target.classList.contains('sk-fin'))syncField(e.target);});
root.addEventListener('focusin',function(e){var f=e.target.classList&&e.target.classList.contains('sk-fin')?e.target.closest('.sk-field'):null;if(f)f.classList.add('is-focus');});
root.addEventListener('focusout',function(e){var f=e.target.classList&&e.target.classList.contains('sk-fin')?e.target.closest('.sk-field'):null;if(f&&!f.classList.contains('sk-wfield')&&!f.classList.contains('sk-keepfocus'))f.classList.remove('is-focus');});
root.addEventListener('keydown',function(e){if(e.key!=='Enter'||!e.target.classList||!e.target.classList.contains('sk-fin'))return;var scr=e.target.closest('.sk-screen');if(!scr)return;e.preventDefault();
 if(e.target.closest('.sk-addbar'))sendField(scr);else if(e.target.closest('.sk-lsheet'))writeSubmit(scr);else{var eg=e.target.closest('[data-sk="kiratile"]');if(eg)kiraTile(eg);}});
/* -------- egg 7 · kirakira: nothing is added; every sticker twinkles once and the title turns holo for 5 s -------- */
function kirakira(scr){var h=scr.querySelector('.sk-appbar h3');if(h){restart(h,'is-kira');clearTimeout(h.__k);h.__k=tm(function(){h.classList.remove('is-kira');},5000);}
 var wm=root.querySelector('.sk-wordmark .sk-wm');if(wm){restart(wm,'is-kira');tm(function(){wm.classList.remove('is-kira');},5000);}
 if(RM)return;$$('.sk-ico,.sk-bk-slot.is-on,.sk-bkw,.sk-sunegg',scr).forEach(function(e,i){tm(function(){restart(e,'is-twk');tm(function(){e.classList.remove('is-twk');},420);},i*22);});
 if(h){var c=rel(h,scr);kira(scr,c.x,c.y,40);burst(scr,c.x,c.y,['star4','heart','star'],10,null,'',40);}snd('kira');buzz('tick');}
/* -------- egg 9 · birthday: confetti stickers rain over the phone for 2 s -------- */
function confetti(scr){if(RM)return;var K=['heart','star4','petal','star','cake','heart','petal','star4'];for(var i=0;i<26;i++){var s=svgUse('sticker-i-'+K[i%K.length],16+(i%4)*3,0,'sk-lconf');s.style.left=(4+i*13.4)%352+'px';s.style.setProperty('--fx',((i%5)-2)*12+'px');s.style.setProperty('--r',((i%2)?1:-1)*(160+i*9)+'deg');s.style.animationDelay=((i%7)*.11)+'s';s.style.animationDuration=(1.3+(i%3)*.25)+'s';scr.appendChild(s);(function(x){tm(function(){x.remove();},2400);})(s);}snd('jingle');}
/* -------- egg 3 · rolling produce: the round sticker rolls down the page and bumps the Produce basket (a berry punnet hops instead) -------- */
function rollEgg(scr,it,then){var aw=aisleEl(scr,it.a);if(RM||!aw){then();return;}scrollToAisle(scr,aw,function(){var bk=aw.querySelector(aw.classList.contains('is-open')?'.sk-opened .bk':'.sk-folded .bk');if(!bk){then();return;}
  var q=rel(bk,scr);var berry=it.k==='strawberries';var s=svgUse(symOf(it.k),44,44,'sk-roller'+(berry?' is-hop':''));var x0=berry?q.x-150:24,y0=176;s.style.left=x0+'px';s.style.top=y0+'px';s.style.setProperty('--rx',(q.x-22-x0)+'px');s.style.setProperty('--ry',(q.t-20-y0)+'px');scr.appendChild(s);
  tm(function(){wob(bk);heartPop(scr,bk);snd('pff');buzz('land');},2600);tm(function(){s.remove();then();},3050);});}
/* -------- eggs 13 + live AI stickers: the gashapon capsule wobbles twice and pops; 1st one is a gold JACKPOT (demo cheat for 1 in 100) -------- */
function capsulePop(scr,it,then){if(RM){then();return;}var jack=!scr.__jack&&S.eggs;var w=el('div','sk-capsulepop'+(jack?' is-jack':''));
 w.innerHTML='<svg class="sk-cpb" width="58" height="58" aria-hidden="true"><use href="#sticker-i-capsule"/></svg><svg class="sk-cpi" width="52" height="52" aria-hidden="true"><use href="#'+symOf(it.k)+'"/></svg>'
  +(jack&&D.jack?'<span class="sk-cphk"><svg class="sk-hanko" viewBox="0 0 40 40" width="60" height="60" aria-hidden="true" style="color:#C8321F"><use href="#'+D.jack+'" transform="rotate(-10 20 20)"/></svg></span>':'')+'<span class="sk-cpcap">'+(jack?'':'drawing a sticker…')+'</span>';
 scr.appendChild(w);snd('beep');tm(function(){snd('pon');buzz('confirm');var c=rel(w.querySelector('.sk-cpi'),scr);kira(scr,c.x,c.y,34);if(jack){burst(scr,c.x,c.y,['star4','star','heart'],10,null,'',40);}},jack?700:650);
 if(jack){tm(function(){snd('thunk');buzz('thunk');},1000);it.q=it.q+'';}
 tm(function(){w.classList.add('is-out');},jack?2500:1250);tm(function(){w.remove();if(jack){it.jack=1;}then();},jack?2800:1400);}
/* -------- egg 17 · ant parade: once per session when the list holds 3+ picnic things -------- */
function picnicCheck(scr){if(scr.__ants||!S.eggs)return;var keys={};$$('.sk-aw .sk-opened .sk-row',scr).forEach(function(r){keys[r.getAttribute('data-key')]=1;});var n=(D.picnic||[]).filter(function(k){return keys[k];}).length;if(n<3)return;scr.__ants=1;ants(scr);}
function ants(scr){if(RM)return;var bar=scr.querySelector('.sk-addbar');if(!bar)return;var y=rel(bar,scr).t-21;for(var i=0;i<5;i++){var a=el('span','sk-ant sk-liveant');a.innerHTML='<svg width="'+(i===2?30:22)+'" height="'+(i===2?30:22)+'" aria-hidden="true"><use href="#sticker-i-ant"/></svg>';a.style.top=(y-(i===2?8:0))+'px';a.style.animationDelay=(-i*.42*0+i*.42)+'s';scr.appendChild(a);(function(x){tm(function(){x.remove();},6200);})(a);}}
/* -------- the letter-set sheet on phones 1 + 6: Write (recipes), Snap (paper list), Speak (voice note) -------- */
function sheetOf(scr){return scr.querySelector('.sk-lsheetw');}
function openSheet(scr,tab){var w=sheetOf(scr);if(!w)return;closeMenu(scr);if(w.hidden){w.hidden=false;restart(w,'is-open');snd('peel');}setTab(scr,tab);}
function closeSheet(scr){var w=sheetOf(scr);if(!w||w.hidden)return;stopAll(scr);w.classList.remove('is-open');w.classList.add('is-closing');tm(function(){w.hidden=true;w.classList.remove('is-closing');},RM?0:260);}
function stopAll(scr){var w=sheetOf(scr);if(!w)return;(w.__t||[]).forEach(clearTimeout);w.__t=[];w.classList.remove('is-rec','is-scan');}
function st(w,fn,ms){(w.__t=w.__t||[]).push(tm(fn,ms));}
function setTab(scr,tab){var w=sheetOf(scr);stopAll(scr);$$('.sk-stab',w).forEach(function(b){var on=b.getAttribute('data-tab')===tab;b.classList.toggle('is-on',on);b.setAttribute('aria-selected',on?'true':'false');});
 $$('.sk-lpanel',w).forEach(function(p){p.hidden=p.getAttribute('data-tab')!==tab;});w.__tab=tab;var btn=w.querySelector('.sk-lstick');
 if(tab==='write'){var r=w.querySelector('.sk-lrecipe');stickBtn(w,r&&!r.hidden?keptCount(w):0,'write');var f=w.querySelector('.sk-lpanel[data-tab="write"] .sk-fin');if(f&&r&&r.hidden)tm(function(){f.focus({preventScroll:true});},60);}
 if(tab==='snap')runSnap(scr);if(tab==='speak')runSpeak(scr);}
function stickBtn(w,n,mode,busy){var b=w.querySelector('.sk-lstick');if(!b)return;var l=b.querySelector('span');b.disabled=!!busy||!n;b.classList.toggle('is-disabled',b.disabled);
 l.textContent=busy?busy:(n?'Stick '+(n===1?'it':'all '+n)+' on my list':(mode==='write'?'Type something to stick':'Nothing new yet'));b.__n=n;}
function writeSubmit(scr){var w=sheetOf(scr);var f=w.querySelector('.sk-lpanel[data-tab="write"] .sk-fin');var v=(f.value||f.placeholder||'').trim();var n=norm(v);
 if(/pancake|from scratch|recipe|ingredients for/.test(n)||!f.value){runRecipe(scr);return;}f.value='';closeSheet(scr);submitText(scr,v,'sheet');}
function runRecipe(scr){var w=sheetOf(scr);if(!w)return;stopAll(scr);var th=w.querySelector('.sk-lthink'),rc=w.querySelector('.sk-lrecipe'),hint=w.querySelector('.sk-whint');var f=w.querySelector('.sk-lpanel[data-tab="write"] .sk-fin');if(f&&!f.value)f.value='pancakes from scratch';
 if(hint)hint.hidden=true;rc.hidden=true;th.hidden=false;var lis=$$('li',th);lis.forEach(function(l){l.classList.remove('is-on');});stickBtn(w,0,'write','Looking it up…');
 lis.forEach(function(l,i){st(w,function(){l.classList.add('is-on');snd('tk');},RM?0:500+i*650);});
 st(w,function(){th.hidden=true;rc.hidden=false;$$('.sk-irow',rc).forEach(function(r,i){r.classList.remove('is-in1');if(!RM){r.style.animationDelay=(i*.08)+'s';restart(r,'is-in1');}});stickBtn(w,keptCount(w),'write');snd('pon');buzz('confirm');},RM?0:2600);}
function keptCount(w){var n=$$('.sk-lrecipe .sk-keep[aria-checked="true"]',w).length;var k=w.querySelector('[data-sk="rkept"]');if(k)k.textContent=n+' of '+$$('.sk-lrecipe .sk-keep',w).length+' kept';return n;}
var SNAPF=[['lemons','lemon'],['crackers','crackers'],['oat milk','ai-oatmilk'],['tortillas','tortillas'],['dish soap','dishsoap']];
function irowHTML(it,note){return '<li class="sk-irow is-in1"><span class="sk-ico"><svg width="36" height="36" role="img" aria-label="'+esc(it.n)+'"><use href="#'+symOf(it.k)+'"/></svg></span><span class="sk-txt"><span class="sk-nm">'+esc(it.n)+'</span><span class="sk-dt">'+esc(note||it.q)+'</span></span></li>';}
function onList(scr,k){return !!scr.querySelector('.sk-aw .sk-opened .sk-row[data-key="'+k+'"]');}
function runSnap(scr){var w=sheetOf(scr);var lis=$$('.sk-ltrans li',w),tray=w.querySelector('.sk-lfound'),cnt=w.querySelector('[data-sk="snapn"]');tray.innerHTML='';lis.forEach(function(l){l.classList.remove('is-done','is-now');});w.__found=[];
 w.classList.add('is-scan');stickBtn(w,0,'snap','Reading… 0 of '+lis.length);
 lis.forEach(function(l,i){st(w,function(){l.classList.add('is-now');},RM?0:400+i*700);st(w,function(){l.classList.remove('is-now');l.classList.add('is-done');var f=SNAPF[i];var it=findItem(f[0])||unknownItem(f[0]);var dup=onList(scr,it.k);
   tray.insertAdjacentHTML('beforeend',irowHTML(it,dup?'already on your list':it.q));if(!dup)w.__found.push(it);cnt.textContent=(i+1)+' of '+lis.length;snd('tk');
   if(i===lis.length-1){w.classList.remove('is-scan');stickBtn(w,w.__found.length,'snap');snd('pon');}else stickBtn(w,0,'snap','Reading… '+(i+1)+' of '+lis.length);},RM?0:800+i*700);});}
var SPEAK=['…oh','and','frozen','pierogi,','a','bunch','of','bok','choy,','and','kimchi','for','the','fried','rice'];var HEARD={'pierogi,':'pierogi','choy,':'bok choy','kimchi':'kimchi'};
function runSpeak(scr){var w=sheetOf(scr);var tx=w.querySelector('.sk-lvoice'),tray=w.querySelector('.sk-lheard'),cnt=w.querySelector('[data-sk="heardn"]'),lcd=w.querySelector('.sk-ltimer');tx.innerHTML='';tray.innerHTML='';w.__found=[];
 w.classList.add('is-rec');stickBtn(w,0,'speak','Listening…');snd('clunk');buzz('tap');var sec=0;setLcd(lcd,'0:00','Recording, 0 seconds');
 for(var s2=1;s2<=9;s2++)(function(s3){st(w,function(){setLcd(lcd,'0:0'+s3,'Recording, '+s3+' seconds');},RM?0:s3*1000);})(s2);
 SPEAK.forEach(function(word,i){st(w,function(){var hw=HEARD[word];tx.insertAdjacentHTML('beforeend',(hw?'<b>'+esc(word.replace(',',''))+'</b>'+(/,$/.test(word)?',':''):esc(word))+' ');
   if(hw){var it=findItem(hw);if(it){tray.insertAdjacentHTML('beforeend',irowHTML(it));w.__found.push(it);cnt.textContent=w.__found.length+' sticker'+(w.__found.length>1?'s':'');snd('tk');}}
   if(i===SPEAK.length-1)st(w,function(){stopRec(scr);},RM?0:900);},RM?0:600+i*330);});}
function stopRec(scr){var w=sheetOf(scr);if(!w.classList.contains('is-rec'))return;(w.__t||[]).forEach(clearTimeout);w.__t=[];w.classList.remove('is-rec');snd('clunk');buzz('tap');stickBtn(w,(w.__found||[]).length,'speak');}
function stickSheet(scr){var w=sheetOf(scr);var tab=w.__tab,items=[];
 if(tab==='write'){$$('.sk-lrecipe .sk-irow',w).forEach(function(r){if(r.querySelector('.sk-keep[aria-checked="true"]')){var d=(r.getAttribute('data-add')||'').split('|');var it=d[0]?{k:r.getAttribute('data-key'),n:d[0],q:d[1],a:+d[2],c:d[3]||''}:findItem(r.querySelector('.sk-nm').textContent);if(it)items.push(it);}});}
 else items=(w.__found||[]).slice();
 closeSheet(scr);if(items.length)tm(function(){addItems(scr,items,{from:tab});},RM?0:280);}
/* -------- swipe a row right to check it, left to tear it off (perforation tear + Undo); press = highlighter band -------- */
var SW=null;
function swipeBg(r){var b=r.querySelector('.sk-swipebg');if(b)return b;b=el('i','sk-swipebg','<span class="sk-swr">'+(hkMarkup().replace('sk-hanko','sk-hanko sk-swhk'))+'</span><span class="sk-swl"><svg width="22" height="22" aria-hidden="true"><use href="#sticker-i-scissors"/></svg>tear off</span>');b.setAttribute('aria-hidden','true');r.insertBefore(b,r.firstChild);return b;}
root.addEventListener('pointerdown',function(e){var r=e.target.closest('.sk-s-list .sk-aw .sk-opened .sk-row');var sc=e.target.closest('.sk-s-list .sk-scroll');if(e.button>0)return;
 if(r){SW={r:r,x:e.clientX,y:e.clientY,dx:0,on:false,t:Date.now(),sc:sc};r.classList.add('is-pressed');}else if(sc)SW={r:null,x:e.clientX,y:e.clientY,dx:0,on:false,t:Date.now(),sc:sc};});
root.addEventListener('pointermove',function(e){if(!SW)return;var z=zf(SW.sc||root),dx=(e.clientX-SW.x)/z,dy=(e.clientY-SW.y)/z;
 if(!SW.on&&!SW.pull){if(SW.r&&Math.abs(dx)>10&&Math.abs(dx)>Math.abs(dy)*1.3){SW.on=true;SW.r.classList.add('is-swiping');swipeBg(SW.r);}
  else if(dy>10&&Math.abs(dy)>Math.abs(dx)&&SW.sc&&SW.sc.scrollTop<=0&&e.pointerType!=='touch'){SW.pull=true;pullStart(SW.sc.closest('.sk-screen'));}
  else if(Math.abs(dy)>10){if(SW.r)SW.r.classList.remove('is-pressed');SW=null;return;}}
 if(SW.on){var x=Math.max(-112,Math.min(112,dx));SW.dx=x;SW.r.style.setProperty('--sx',x+'px');SW.r.classList.toggle('is-swr',x>56);SW.r.classList.toggle('is-swl',x<-56);SW.r.classList.toggle('is-swpos',x>0);SW.r.classList.toggle('is-swneg',x<0);SW.r.classList.remove('is-pressed');}
 if(SW.pull)pullMove(SW.sc.closest('.sk-screen'),dy);});
function swipeEnd(){if(!SW)return;var s=SW;SW=null;if(s.r)s.r.classList.remove('is-pressed');if(s.pull){pullEnd(s.sc.closest('.sk-screen'));return;}if(!s.on)return;var r=s.r;r.__swiped=Date.now();
 var scr=r.closest('.sk-screen');r.classList.remove('is-swr','is-swl');r.classList.add('is-swback');r.style.setProperty('--sx','0px');tm(function(){r.classList.remove('is-swiping','is-swback','is-swpos','is-swneg');},220);
 if(s.dx>56&&!r.classList.contains('is-checked'))toggleListRow(r);else if(s.dx<-56)tearRow(scr,r);}
root.addEventListener('pointerup',swipeEnd);root.addEventListener('pointercancel',function(){if(SW&&SW.r)SW.r.classList.remove('is-pressed');if(SW&&SW.pull)pullEnd(SW.sc.closest('.sk-screen'),true);SW=null;});
root.addEventListener('click',function(e){var r=e.target.closest('.sk-row');if(r&&r.__swiped&&Date.now()-r.__swiped<400){e.stopImmediatePropagation();e.preventDefault();}},true);
function tearRow(scr,r){var name=nameOf(r);if(RM){var rec0=removeRow(scr,r);toast(scr,name+' torn out',function(){restoreRow(scr,rec0);});return;}
 r.classList.add('is-tear');snd('peel');buzz('tick');tm(function(){r.classList.add('is-close');},600);
 tm(function(){var rec=removeRow(scr,r);r.classList.remove('is-close');toast(scr,name+' torn out',function(){restoreRow(scr,rec);snd('pff');});},980);}
/* -------- pull to refresh: sticker bunting sags as you pull; let go and the sun spins once while prices + icons re-sync -------- */
function bunting(scr){var b=scr.querySelector('.sk-bunting');if(b)return b;b=el('div','sk-bunting');b.setAttribute('aria-hidden','true');
 b.innerHTML='<svg viewBox="0 0 340 60" width="340" height="60"><path class="sk-bcord" d="M4 6Q170 6 336 6" fill="none" stroke="#4F5BA6" stroke-width="1.6" stroke-dasharray="3 3"/></svg>'+['heart','star','star4','star','heart','star4','star'].map(function(k,i){return '<svg class="sk-bflag" style="--i:'+i+'" width="22" height="22"><use href="#sticker-i-'+k+'"/></svg>';}).join('');
 scr.appendChild(b);return b;}
function pullStart(scr){var b=bunting(scr);b.classList.add('is-on');b.classList.remove('is-back');scr.classList.add('is-pulling');scr.classList.remove('is-pullback');}
function pullMove(scr,dy){var b=bunting(scr);var p=Math.max(0,Math.min(70,dy*.6));b.style.setProperty('--pull',p+'px');scr.style.setProperty('--pull',p+'px');var sag=6+p*.55;b.querySelector('.sk-bcord').setAttribute('d','M4 6Q170 '+(6+sag*2).toFixed(1)+' 336 6');b.__p=p;}
function pullEnd(scr,cancel){var b=bunting(scr);var go=!cancel&&b.__p>40;b.classList.add('is-back');b.style.setProperty('--pull','0px');b.querySelector('.sk-bcord').setAttribute('d','M4 6Q170 6 336 6');b.__p=0;scr.classList.add('is-pullback');scr.style.setProperty('--pull','0px');tm(function(){b.classList.remove('is-on','is-back');scr.classList.remove('is-pulling','is-pullback');},RM?0:320);
 if(go){var sun=scr.querySelector('.sk-sunegg>svg');if(sun)restart(sun,'is-spin');var l=scr.querySelector('.sk-lcdmain');if(l){l.__busy=1;setLcd(l,'SYNC','Re-syncing prices and stickers');tm(function(){l.__busy=0;lcdCount(scr);},900);}snd('pon');buzz('tick');}}
(function(){var ts=null;root.addEventListener('touchstart',function(e){var sc=e.target.closest('.sk-s-list .sk-scroll');if(!sc||sc.scrollTop>0)return;ts={y:e.touches[0].clientY,sc:sc,on:false};},{passive:true});
 root.addEventListener('touchmove',function(e){if(!ts)return;var dy=e.touches[0].clientY-ts.y;if(dy>8&&ts.sc.scrollTop<=0){if(!ts.on){ts.on=true;pullStart(ts.sc.closest('.sk-screen'));}e.preventDefault();pullMove(ts.sc.closest('.sk-screen'),dy/zf(ts.sc));}},{passive:false});
 root.addEventListener('touchend',function(){if(ts&&ts.on)pullEnd(ts.sc.closest('.sk-screen'));ts=null;});})();
/* -------- collapsed FAB: scrolling down a long way folds the add bar into the strawberry FAB; scrolling up or a tap brings it back -------- */
$$('.sk-s-list').forEach(function(scr){var sc=scr.querySelector('.sk-scroll');if(!sc)return;var last=sc.scrollTop,acc=0;
 sc.addEventListener('scroll',function(){var y=sc.scrollTop,d=y-last;last=y;if(scr.__prog&&Date.now()-scr.__prog<1200){acc=0;return;}acc=(d>0)===(acc>0)?acc+d:d;
  if(acc>70&&y>140)fabMode(scr,true);else if(acc<-40||y<30)fabMode(scr,false);},{passive:true});});
function fabMode(scr,on){if(!!scr.__fab===on)return;if(on&&(scr.querySelector('.sk-addbar .sk-fin')===document.activeElement||!sheetOf(scr)||!sheetOf(scr).hidden))return;scr.__fab=on;scr.classList.toggle('is-fab',on);var f=scr.querySelector('.sk-livefab');if(f){f.hidden=!on;if(on&&!RM)restart(f,'is-in');}}
/* -------- more options menu -------- */
function closeMenu(scr){var m=scr&&scr.querySelector('.sk-menu');if(m&&!m.hidden){m.hidden=true;var b=scr.querySelector('[data-sk="more"]');if(b)b.setAttribute('aria-expanded','false');}}
function pulse(e){if(!e)return;e.scrollIntoView({behavior:RM?'auto':'smooth',block:'center'});tm(function(){restart(e,'is-pulse');},RM?0:500);}
function go(where,scr){var p4=root.querySelector('.sk-s-aisles'),p5=root.querySelector('.sk-s-album'),p1=root.querySelector('#sticker-p1 .sk-phone');
 if(where==='list')pulse(p1);else if(where==='newtrip')newTrip(scr);else if(where==='store')pulse(p4&&p4.closest('.sk-phone'));else if(where==='album')pulse(p5&&p5.closest('.sk-phone'));
 else if(where==='surprises'&&p4){pulse(p4.closest('.sk-phone'));var card=p4.querySelector('.sk-surprises'),s4=p4.querySelector('.sk-scroll');if(card&&s4)tm(function(){s4.scrollTo({top:card.offsetTop-20,behavior:RM?'auto':'smooth'});restart(card,'is-pulse');},RM?0:700);}}
/* -------- one book per trip: "New trip" files today's book in the album and starts a fresh one -------- */
var NEXTSAT=['Sat 10 Oct','Sat 17 Oct','Sat 24 Oct','Sat 31 Oct','Sat 7 Nov'];
function newTrip(scr){var bk=bookOf(scr);if(!bk)return;var date=bk.querySelector('.sk-bk-date');var oldDate=date.textContent,n=bookCount(bk),N=bk.__slots.length,perfect=bk.classList.contains('is-perfect'),receipt=bk.classList.contains('has-receipt');
 fileBook(oldDate,bk.__slots.slice(),n,N,perfect,receipt);
 $$('.sk-aw .sk-opened .sk-row',scr).forEach(function(r){clearPend(r);r.classList.remove('is-checked','is-inbk','is-slam','is-peeling','is-landing');r.removeAttribute('data-got');r.__slot=null;var cb=r.querySelector('.sk-cb');if(cb)cb.setAttribute('aria-checked','false');});
 $$('.sk-aw',scr).forEach(function(aw){aw.classList.remove('is-complete','is-anim','is-curl','is-open');aw.__seq=(aw.__seq||0)+1;$$('.bk',aw).forEach(function(b){b.classList.remove('is-done','is-anim','bk-hop');});stampSet(aw,false);syncAisle(aw);});
 scr.__trip=(scr.__trip||0)+1;date.lastChild.nodeValue=NEXTSAT[(scr.__trip-1)%NEXTSAT.length];bk.classList.remove('is-full','is-fullnow','is-perfect','has-receipt');var tk=bk.querySelector('.sk-bk-receipt');if(tk)tk.remove();
 bk.__slots=bk.__slots.map(function(){return null;});bookRender(bk,0);scr.__tripDone=false;scr.__ants=0;var first=scr.querySelector('.sk-aw');if(first)openAisle(scr,first);lcdCount(scr);bookLabel(bk);
 if(!RM){restart(bk,'is-turn');}toast(scr,'New trip · a fresh sticker book ♡');}
function fileBook(date,slots,n,N,perfect,receipt){var al=root.querySelector('[data-sk="tripbooks"]');if(!al)return;var li=document.createElement('li');li.className='sk-tbk is-new'+(perfect?' is-perfect':'');
 li.innerHTML='<span class="sk-tbkst">'+slots.filter(Boolean).slice(0,6).map(function(k){return '<svg width="20" height="20" aria-hidden="true"><use href="#'+symOf(k)+'"/></svg>';}).join('')+'</span><b>'+esc(date)+'</b><span>'+n+' of '+N+' stickers'+(perfect?' · perfect':'')+(receipt?' · receipt':'')+'</span>';
 al.insertBefore(li,al.firstChild);tm(function(){li.classList.remove('is-new');},800);}
/* -------- demo controls under phone 1 -------- */
function firstUnchecked(scr){var rows=$$('.sk-aw .sk-opened .sk-row',scr);for(var i=0;i<rows.length;i++)if(!rows[i].classList.contains('is-checked'))return rows[i];return null;}
function demo(scr,what){if(what==='newtrip'){newTrip(scr);return;}if(what==='next'){var r=firstUnchecked(scr);if(!r)return;var aw=r.closest('.sk-aw');if(!aw.classList.contains('is-open')){openAisle(scr,aw);tm(function(){toggleListRow(r);},RM?0:450);}else{var sc=scr.querySelector('.sk-scroll');toggleListRow(r);}return;}
 if(what==='finish'){var rows=$$('.sk-aw .sk-opened .sk-row',scr).filter(function(x){return !x.classList.contains('is-checked');});scr.__fast=true;rows.forEach(function(x,i){tm(function(){toggleListRow(x);if(i===rows.length-1)tm(function(){scr.__fast=false;},1500);},i*160);});}}
/* -------- clicks for the live pieces -------- */
root.addEventListener('click',function(e){var t=e.target.closest('[data-sk]');var scr=e.target.closest('.sk-screen');
 if(scr&&!e.target.closest('.sk-menu,[data-sk="more"]'))closeMenu(scr);if(!t||!root.contains(t))return;var k=t.getAttribute('data-sk');
 if(k==='send'){sendField(scr);}
 else if(k==='snap'||k==='speak'){openSheet(scr,k);}
 else if(k==='ltab'){setTab(scr,t.getAttribute('data-tab'));}
 else if(k==='lclose'){closeSheet(scr);}
 else if(k==='lstop'){stopRec(scr);}
 else if(k==='lstick'){stickSheet(scr);}
 else if(k==='keep'){var on=t.getAttribute('aria-checked')!=='true';t.setAttribute('aria-checked',on?'true':'false');var ir=t.closest('.sk-irow');if(ir)ir.classList.toggle('is-off',!on);if(on&&!RM){restart(t,'is-stick');}snd(on?'tk':'peel');
  var w=t.closest('.sk-lsheetw');if(w)stickBtn(w,keptCount(w),'write');else keepStatic(t);}
 else if(k==='fab'){fabMode(scr,false);var f=fieldOf(scr);if(f)tm(function(){f.focus();},RM?0:200);}
 else if(k==='more'){var m=scr.querySelector('.sk-menu');if(m){m.hidden=!m.hidden;t.setAttribute('aria-expanded',m.hidden?'false':'true');if(!m.hidden&&!RM)restart(m,'is-in');}}
 else if(k==='menu'){closeMenu(scr);go(t.getAttribute('data-go'),scr);}
 else if(k==='demo'){var s1=root.querySelector('#sticker-p1 .sk-screen');if(s1)demo(s1,t.getAttribute('data-do'));}
 else if(k==='book'){var stp=e.target.closest('.sk-stamp');if(stp)bonusTap(scr,stp.getAttribute('data-n'));}
});
root.addEventListener('submit',function(e){e.preventDefault();});
/* -------- egg 10 live · bonus stage: tap the book's stamps 1 1 2 2 3 4 3 4 → 8-bit stickers + LCD jingle until you leave the screen -------- */
function bonusTap(scr,n){if(!S.eggs)return;if(scr.classList.contains('is-8bit')){bonusEnd(scr);return;}var want='11223434';var seq=(scr.__kon||'')+n;if(want.indexOf(seq)!==0)seq=n==='1'?'1':'';scr.__kon=seq;snd('beep');
 if(seq===want){scr.__kon='';scr.classList.add('is-8bit');var l=scr.querySelector('.sk-lcdmain');if(l){l.__busy=1;var fr=['BONUS','♪ ♪ ♪','STAGE!','♪♪ ♪♪'],i=0;setLcd(l,fr[0],'Bonus stage');clearInterval(scr.__jg);scr.__jg=setInterval(function(){i++;setLcd(l,fr[i%fr.length],'Bonus stage');snd('beep');},600*TS);}
  snd('jingle');buzz('thunk');toast(scr,'BONUS STAGE! ♪ tap a stamp to leave',null,{ms:4500});
  if('IntersectionObserver' in window&&!scr.__bio){scr.__bio=new IntersectionObserver(function(en){if(!en[0].isIntersecting&&scr.classList.contains('is-8bit'))bonusEnd(scr);},{threshold:0});scr.__bio.observe(scr);}}}
function bonusEnd(scr){scr.classList.remove('is-8bit');clearInterval(scr.__jg);var l=scr.querySelector('.sk-lcdmain');if(l){l.__busy=0;lcdCount(scr);}}
/* -------- egg 4 live · petal shake: a real shake (DeviceMotion), or grab a phone's frame and wiggle it left-right -------- */
function petals(scr){if(RM||!S.eggs||!scr)return;if(scr.__petal&&Date.now()-scr.__petal<5200)return;scr.__petal=Date.now();buzz('tick');snd('kira');
 var spots=[[2,190],[352,230],[1,320],[353,400],[2,470],[352,540],[118,8],[150,14],[204,10],[2,610],[353,640],[232,16],[1,260],[352,330]];
 spots.forEach(function(p,i){var e2=el('i','sk-lpetal');var x0=30+((i*53)%300);e2.style.left=x0+'px';e2.style.setProperty('--tx',(p[0]-x0)+'px');e2.style.setProperty('--ty',p[1]+'px');e2.style.setProperty('--r',((i%2?1:-1)*(40+i*17))+'deg');e2.style.animationDelay=(i*.05)+'s';scr.appendChild(e2);(function(x){tm(function(){x.remove();},5600);})(e2);});}
var WG=null;
root.addEventListener('pointerdown',function(e){var ph=e.target.closest('.sk-phone');if(!ph||e.target.closest('.sk-screen'))return;WG={ph:ph,x:e.clientX,dir:0,rev:0,t:Date.now()};});
root.addEventListener('pointermove',function(e){if(!WG)return;var dx=e.clientX-WG.x;if(Math.abs(dx)<12)return;var d=dx>0?1:-1;if(WG.dir&&d!==WG.dir)WG.rev++;WG.dir=d;WG.x=e.clientX;
 if(!RM)WG.ph.style.transform='translateX('+(d*3)+'px) rotate('+(d*1.2)+'deg)';if(WG.rev>=4&&Date.now()-WG.t<1400){var scr=WG.ph.querySelector('.sk-s-list');WG.ph.style.transform='';WG=null;petals(scr);}});
['pointerup','pointercancel'].forEach(function(ev){root.addEventListener(ev,function(){if(WG){WG.ph.style.transform='';WG=null;}});});
if(window.DeviceMotionEvent){var lastShake=0;window.addEventListener('devicemotion',function(e){var a=e.accelerationIncludingGravity||e.acceleration;if(!a)return;var m=Math.abs(a.x||0)+Math.abs(a.y||0);if(m>34&&Date.now()-lastShake>3000){lastShake=Date.now();$$('.sk-s-list').forEach(function(s){var r=s.getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)petals(s);});}});}
/* -------- static story phones: keep switches + "Stick all" on screen 2, stop + reading on screen 3 -------- */
function keepStatic(t){var sh=t.closest('.sk-sheet');if(!sh)return;var n=$$('.sk-keep[aria-checked="true"]',sh).length,N=$$('.sk-keep',sh).length;var b=sh.querySelector('.sk-sheetfoot .sk-btn');if(b){var sp=b.lastChild;if(sp&&sp.nodeType===3)sp.nodeValue='Stick all '+n+' on my list';b.disabled=!n;b.classList.toggle('is-disabled',!n);}
 var m=sh.querySelector('.sk-rmeta span:last-child');if(m)m.textContent='3 recipes · '+n+' of '+N+' kept';$$('.sk-tray',sh).forEach(function(tr){var k=$$('.sk-keep[aria-checked="true"]',tr).length,K=$$('.sk-keep',tr).length;var mm=tr.querySelector('.sk-trm');if(mm)mm.textContent=k+' of '+K+' kept';});}
/* ===================== r9 · phones 2–5 ===================== */
function phoneN(n){var ps=$$('.sk-phones>.sk-pblock');var map={1:0,6:1,2:2,3:3,4:4,5:5};return ps[map[n]]?ps[map[n]].querySelector('.sk-screen'):null;}
function storySheet(scr){var sh=scr.querySelector('.sk-sheet');if(!sh.__orig)sh.__orig=sh.innerHTML;return sh;}
function storyReset(scr,ms){var sh=storySheet(scr);tm(function(){sh.classList.remove('is-down');sh.innerHTML=sh.__orig;var sc=scr.querySelector('.sk-scrim');if(sc)sc.classList.remove('is-down');snd('peel');},ms);}
function storyStick(scr,n,label){var sh=storySheet(scr);sh.classList.add('is-down');var sc=scr.querySelector('.sk-scrim');if(sc)sc.classList.add('is-down');snd('pon');buzz('confirm');
 toast(scr,'Added '+n+' items to Saturday shop ♡',function(){},{ms:4500});var l=scr.querySelector('.sk-appbar .sk-lcd');storyReset(scr,5200);}
function stop3(scr){var sh=storySheet(scr);if(sh.__stopped)return;sh.__stopped=1;sh.classList.add('is-stopped');snd('clunk');buzz('tap');var lis=$$('.sk-trans li',sh),tray=sh.querySelector('.sk-found'),btn=sh.querySelector('[data-sk="stick3"]');
 var rest=[['old cheddar','cheese','Old cheddar','400 g block'],['tomatoes 796','tomatocan','Diced tomatoes','796 mL can'],['dish soap','dishsoap','Dish soap','740 mL']];var n=6;
 rest.forEach(function(r,i){tm(function(){var li=lis[4+i];if(!li){li=document.createElement('li');lis[0].parentNode.appendChild(li);}li.className='is-done';li.innerHTML='<svg class="sk-tb" width="14" height="14" aria-hidden="true"><use href="#sticker-tb-check"/></svg>'+r[0];
   tray.insertAdjacentHTML('beforeend','<li class="sk-irow is-in1"><span class="sk-ico"><svg width="36" height="36" role="img" aria-label="'+r[2]+'"><use href="#sticker-i-'+r[1]+'"/></svg></span><span class="sk-txt"><span class="sk-nm">'+r[2]+'</span><span class="sk-dt">'+r[3]+'</span></span></li>');
   var m=sh.querySelector('.sk-found').closest('.sk-tray').querySelector('.sk-trm');if(m)m.textContent=(4+i+1)+' of 7';n++;snd('tk');
   if(i===rest.length-1){btn.disabled=false;btn.classList.remove('is-disabled');btn.lastChild.nodeValue='Stick all 9 on my list';var sp=btn.querySelector('.sk-spin');if(sp)sp.remove();btn.setAttribute('aria-label','Stick all 9 on my list');snd('pon');}
   else{btn.lastChild.nodeValue='Reading… '+(n)+' of 9';}},RM?0:500+i*700);});}
/* aisle editor */
function aeditOff(ul){$$('.sk-alabel.is-edit',ul).forEach(function(l){l.classList.remove('is-edit');var p=l.querySelector('.sk-aedit');if(p)p.remove();var b=l.querySelector('[data-sk="aedit"]');if(b){b.setAttribute('aria-expanded','false');b.querySelector('use').setAttribute('href','#sticker-tb-pencil');}var sm=l.querySelector('.sk-an small');if(sm&&/editing/.test(sm.textContent))sm.textContent=sm.textContent.replace(' · editing','');});}
function quiet(ul){$$('.is-drag,.is-shift,.is-shift2',ul).forEach(function(l){l.classList.remove('is-drag','is-shift','is-shift2');});}
var PANEL=null;
function aedit(l){var ul=l.parentNode;quiet(ul);var was=l.classList.contains('is-edit');if(!PANEL){var pp=ul.querySelector('.sk-aedit');if(pp)PANEL=pp.cloneNode(true);}aeditOff(ul);if(was){snd('tk');return;}
 l.classList.add('is-edit');var p=PANEL.cloneNode(true);var nm=l.querySelector('.sk-an').firstChild;p.querySelector('.sk-aname').value=(nm&&nm.nodeType===3?nm.nodeValue:'').trim();
 $$('[data-sk="swcol"]',p).forEach(function(b){var on=b.getAttribute('data-f')===l.getAttribute('data-fam');b.classList.toggle('is-on',on);b.setAttribute('aria-pressed',on?'true':'false');});
 $$('[data-sk="swpat"]',p).forEach(function(b){var on=b.getAttribute('data-p')===l.getAttribute('data-pat');b.classList.toggle('is-on',on);b.setAttribute('aria-pressed',on?'true':'false');});
 l.appendChild(p);var b=l.querySelector('[data-sk="aedit"]');b.setAttribute('aria-expanded','true');b.querySelector('use').setAttribute('href','#sticker-tb-check');tm(function(){p.querySelector('.sk-aname').focus({preventScroll:true});},50);snd('tk');}
function renumber(ul){$$('.sk-alabel',ul).forEach(function(l,i){var n=l.querySelector('.sk-num');if(n)n.textContent=i+1;});}
var AD=null;
root.addEventListener('pointerdown',function(e){var h=e.target.closest('[data-sk="adrag"]');if(!h)return;var l=h.closest('.sk-alabel'),ul=l.parentNode;quiet(ul);e.preventDefault();AD={l:l,ul:ul,y:e.clientY,z:zf(ul)};l.classList.add('is-lift');snd('tk');buzz('tick');});
root.addEventListener('pointermove',function(e){if(!AD)return;var dy=(e.clientY-AD.y)/AD.z;AD.l.style.transform='translateY('+dy+'px) rotate(-2deg)';var sibs=$$('.sk-alabel',AD.ul);var r=AD.l.getBoundingClientRect(),mid=(r.top+r.bottom)/2;
 sibs.forEach(function(s){if(s===AD.l)return;var q=s.getBoundingClientRect(),m2=(q.top+q.bottom)/2;if(AD.l.compareDocumentPosition(s)&Node.DOCUMENT_POSITION_FOLLOWING){if(mid>m2){AD.ul.insertBefore(s,AD.l);AD.y+=q.height*AD.z+4;AD.l.style.transform='translateY('+((e.clientY-AD.y)/AD.z)+'px) rotate(-2deg)';snd('tk');}}else{if(mid<m2){AD.ul.insertBefore(AD.l,s);AD.y-=q.height*AD.z+4;AD.l.style.transform='translateY('+((e.clientY-AD.y)/AD.z)+'px) rotate(-2deg)';snd('tk');}}});});
['pointerup','pointercancel'].forEach(function(ev){root.addEventListener(ev,function(){if(!AD)return;AD.l.classList.remove('is-lift');AD.l.style.transform='';renumber(AD.ul);AD=null;snd('pff');});});
root.addEventListener('keydown',function(e){var h=e.target.closest&&e.target.closest('[data-sk="adrag"]');if(!h||(e.key!=='ArrowUp'&&e.key!=='ArrowDown'))return;e.preventDefault();var l=h.closest('.sk-alabel'),ul=l.parentNode;quiet(ul);
 if(e.key==='ArrowUp'&&l.previousElementSibling)ul.insertBefore(l,l.previousElementSibling);else if(e.key==='ArrowDown'&&l.nextElementSibling)ul.insertBefore(l.nextElementSibling,l);renumber(ul);h.focus();});
root.addEventListener('input',function(e){if(e.target.getAttribute&&e.target.getAttribute('data-sk')==='aname'){var l=e.target.closest('.sk-alabel');var an=l.querySelector('.sk-an');var sm=an.querySelector('small');an.firstChild&&an.firstChild.nodeType===3?an.firstChild.nodeValue=e.target.value:an.insertBefore(document.createTextNode(e.target.value),an.firstChild);}});
root.addEventListener('click',function(e){var t=e.target.closest('[data-sk]');if(!t||!root.contains(t))return;var k=t.getAttribute('data-sk'),scr=t.closest('.sk-screen');
 if(k==='tog'){var on=t.getAttribute('aria-checked')!=='true';t.setAttribute('aria-checked',on?'true':'false');t.classList.toggle('is-off',!on);var key=t.getAttribute('data-k');if(key in S){S[key]=on;}if(key==='sounds'&&on){snd('pon');}snd('tk');buzz('tap');
  if(scr)toast(scr,(t.getAttribute('aria-label')||'')+(on?' on':' off')+' ♡',null,{ms:2600});}
 else if(k==='reveal'){var pill=scr.querySelector('.sk-keypill');var on2=t.getAttribute('aria-pressed')!=='true';t.setAttribute('aria-pressed',on2?'true':'false');pill.textContent=on2?'sk-proj-…kira…3f9a':'sk-•••••••••• 3f9a';t.setAttribute('aria-label',on2?'Hide key':'Show key');snd('tk');}
 else if(k==='paste'){var pill2=scr.querySelector('.sk-keypill');pill2.textContent='checking…';snd('tk');tm(function(){pill2.textContent='sk-•••••••••• 7c2e';var ok=scr.querySelector('.sk-oklcd');if(ok)restart(ok,'is-ready');toast(scr,'Key saved on this phone · connected ♡');snd('pon');},900);}
 else if(k==='saved'){toast(scr,'Saved · My store ♡',null,{ms:2600});snd('pon');}
 else if(k==='aedit'){aedit(t.closest('.sk-alabel'));}
 else if(k==='swcol'||k==='swpat'){var l=t.closest('.sk-alabel');var isc=k==='swcol';$$('[data-sk="'+k+'"]',t.parentNode).forEach(function(b){b.classList.toggle('is-on',b===t);b.setAttribute('aria-pressed',b===t?'true':'false');});
  if(isc){var f=t.getAttribute('data-f');l.setAttribute('data-fam',f);l.style.setProperty('--ap','var(--sk-'+f+'-p)');l.style.setProperty('--am','var(--sk-'+f+'-m)');l.style.setProperty('--at','var(--sk-'+f+'-t)');}
  else{var pt=t.getAttribute('data-p');l.setAttribute('data-pat',pt);l.style.setProperty('--pat','var(--sk-pat-'+pt+')');}snd('tk');}
 else if(k==='addaisle'){var ul=scr.querySelector('.sk-alabels');quiet(ul);var src=ul.querySelector('.sk-alabel:not(.is-edit)');var nl=src.cloneNode(true);nl.className='sk-alabel is-new';nl.setAttribute('data-fam','periwinkle');nl.setAttribute('data-pat','plain');
  nl.style.cssText='--ap:var(--sk-periwinkle-p);--am:var(--sk-periwinkle-m);--at:var(--sk-periwinkle-t);--pat:var(--sk-pat-plain);--pats:4px';nl.querySelector('.sk-an').innerHTML='New aisle';var pp2=nl.querySelector('.sk-aedit');if(pp2)pp2.remove();ul.appendChild(nl);renumber(ul);aedit(nl);snd('pon');}
 else if(k==='resort'){var w=t.closest('.sk-resortw');if(w.classList.contains('is-run'))return;w.classList.add('is-run');t.disabled=true;snd('beep');tm(function(){w.classList.remove('is-run');t.disabled=false;toast(scr,'Re-sorted 24 stickers into 8 aisles ♡');snd('pon');},2400);}
 else if(k==='atab'){var tab=t.getAttribute('data-tab');$$('[data-sk="atab"]',scr).forEach(function(b){var on3=b===t;b.classList.toggle('is-on',on3);b.setAttribute('aria-selected',on3?'true':'false');});$$('.sk-pockets',scr).forEach(function(u){u.hidden=u.getAttribute('data-tab')!==tab;if(!u.hidden&&!RM)restart(u,'is-in1');});snd('peel');}
 else if(k==='stab2'){var tab2=t.getAttribute('data-tab');if(scr.classList.contains('sk-s-add')&&tab2!=='write'){toast(scr,'Snap + Speak are on the next phone (09:41) and live on phone 1 ♡',null,{ms:4200});pulse(phoneN(3)&&phoneN(3).closest('.sk-phone'));}
  else if(scr.classList.contains('sk-s-snap')&&tab2==='write'){toast(scr,'Write is on the previous phone (10:12) and live on phone 1 ♡',null,{ms:4200});pulse(phoneN(2)&&phoneN(2).closest('.sk-phone'));}}
 else if(k==='stick2'){var n2=$$('.sk-keep[aria-checked="true"]',scr).length;if(n2)storyStick(scr,n2);}
 else if(k==='stop3'){stop3(scr);}
 else if(k==='stick3'){if(!t.disabled)storyStick(scr,9);}
});
/* ===================== r9 · flourish kit: chips, rows, fields, FAB / Start my list ===================== */
root.addEventListener('click',function(e){var t=e.target.closest('[data-sk]');var kr=e.target.closest('.sk-kit .sk-row');
 if(kr&&!(t&&t.getAttribute('data-sk')==='row')){var on=!kr.classList.contains('is-checked');kr.classList.toggle('is-checked',on);kr.classList.remove('is-pressed');var cb=kr.querySelector('.sk-cb');if(cb)cb.setAttribute('aria-checked',on?'true':'false');if(on){restart(kr,'is-slam');snd('thunk');}else snd('peel');return;}
 if(!t||!root.contains(t))return;var k=t.getAttribute('data-sk');
 if(k==='kchip'){var on2=!t.classList.contains('is-on');t.classList.toggle('is-on',on2);t.setAttribute('aria-pressed',on2?'true':'false');var star=t.querySelector('svg:not(.sk-cnum svg)');if(on2&&!RM)restart(t,'is-twk');snd('tk');}
 else if(k==='startlist'){var s1=root.querySelector('#sticker-p1 .sk-screen');if(!s1)return;s1.closest('.sk-phone').scrollIntoView({behavior:RM?'auto':'smooth',block:'center'});fabMode(s1,false);tm(function(){var f=fieldOf(s1);if(f)f.focus({preventScroll:true});restart(s1.querySelector('.sk-addbar .sk-field'),'is-nudge');},RM?0:600);}
});
root.addEventListener('input',function(e){if(e.target.getAttribute&&e.target.getAttribute('data-sk')==='keyfield'){var ok=/^sk-[A-Za-z0-9_-]{20,}$/.test(e.target.value.trim());var f=e.target.closest('.sk-field');f.classList.toggle('is-error',!ok);var er=root.querySelector('[data-sk="keyerr"]');if(er)er.hidden=ok;}});
