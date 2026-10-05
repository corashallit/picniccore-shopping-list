/* ===================== r9 · egg tiles: every one is a working demo ===================== */
function stageOf(t){return t.closest('.sk-estage');}
/* egg 2 · hold the basket: 440 ms peeks (wobble), 1 s sends the ladybug for a stroll */
var LB=null;
root.addEventListener('pointerdown',function(e){var b=e.target.closest('[data-sk="ladybk"]');if(!b)return;var st=stageOf(b);clearTimeout(LB&&LB.a);clearTimeout(LB&&LB.b);
 LB={a:tm(function(){wob(b.querySelector('.bk'));snd('pff');},440),b:tm(function(){if(st.classList.contains('is-go'))return;st.classList.add('is-go');buzz('tick');tm(function(){st.classList.remove('is-go');},6200);},1000)};});
['pointerup','pointerleave','pointercancel'].forEach(function(ev){root.addEventListener(ev,function(){if(LB){clearTimeout(LB.a);clearTimeout(LB.b);LB=null;}},true);});
root.addEventListener('keydown',function(e){var b=e.target.closest&&e.target.closest('[data-sk="ladybk"]');if(b&&(e.key==='Enter'||e.key===' ')){e.preventDefault();var st=stageOf(b);st.classList.add('is-go');tm(function(){st.classList.remove('is-go');},6200);}});
/* egg 15 · LCD pet tile: hold the LCD → the pixel bee moves in and the LCD reads FED; each check feeds it */
var PT=null;
root.addEventListener('pointerdown',function(e){var l=e.target.closest('[data-sk="pettile"]');if(!l)return;clearTimeout(PT);PT=tm(function(){petTile(l);},440);});
['pointerup','pointerleave','pointercancel'].forEach(function(ev){root.addEventListener(ev,function(){clearTimeout(PT);},true);});
function petTile(l){if(!l.querySelector('.sk-pet')){var src=root.querySelector('.sk-lcd .sk-pet');var p=src?src.cloneNode(true):el('span','sk-pet','');l.insertBefore(p,l.firstChild);l.classList.add('has-pet');}l.__n=l.__n||10;l.__busy=1;setLcd(l,'FED','LCD pet: fed');snd('beep');tm(function(){l.__busy=0;setLcd(l,l.__n+'/24');},1400);}
/* egg 3 · rolling produce tile */
function roll3(st,k,w){if(st.__busy)return;st.__busy=1;var r=st.querySelector('.sk-roll3'),out=st.querySelector('[data-sk="rollout3"]');var berry=k==='strawberries';r.innerHTML='<svg width="40" height="40" aria-hidden="true"><use href="#sticker-i-'+k+'"/></svg>';out.textContent='';
 r.classList.remove('is-go','is-hop');void r.offsetWidth;r.classList.add(berry?'is-hop':'is-go');snd('tk');
 tm(function(){var bk=st.querySelector('.sk-rollbk .bk');wob(bk);heartPop(st,bk);snd('pff');buzz('land');},RM?0:2600);
 tm(function(){r.classList.remove('is-go','is-hop');r.innerHTML='';out.innerHTML='<b>'+(berry?'Strawberries':'McIntosh apples')+'</b> on its own line · still to get'+(w?' (“'+w+'”)':'');st.__busy=0;},RM?0:3100);}
/* egg 4 · petal shake tile */
function shake4(st){if(RM||st.__busy)return;st.__busy=1;restart(st,'is-shake');buzz('tick');snd('kira');var W=st.clientWidth;
 for(var i=0;i<12;i++){var p=el('i','sk-lpetal sk-epetal');var x0=10+(i*37)%(W-20),tx=(i%2?W-14-(i*7)%30:4+(i*5)%26)-x0,ty=20+(i*29)%120;p.style.left=x0+'px';p.style.setProperty('--tx',tx+'px');p.style.setProperty('--ty',ty+'px');p.style.setProperty('--r',((i%2?1:-1)*(40+i*17))+'deg');p.style.animationDelay=(i*.05)+'s';st.appendChild(p);(function(x){tm(function(){x.remove();},5400);})(p);}
 tm(function(){st.__busy=0;},5000);}
/* egg 5 · midnight snack: a clock in 30-minute steps (starts at your real time); 23:00–04:00 = glow theme + SNACK★ */
function snackSet(w,mins){mins=((mins%1440)+1440)%1440;w.__m=mins;var h=Math.floor(mins/60),m=mins%60,t=(h<10?'0':'')+h+':'+(m<10?'0':'')+m;var c=w.querySelector('.sk-clocklcd');setLcd(c,t,t);var on=mins>=1380||mins<240;var was=w.classList.contains('is-snack');w.classList.toggle('is-snack',on);
 var l=w.querySelector('.sk-snacklcd');if(on!==was||!w.__init){setLcd(l,on?'SNACK':'10/24',on?'Snack?':'10 of 24');if(on&&w.__init){snd('kira');restart(w.querySelector('.sk-snackstar'),'is-twk');}}w.__init=1;}
$$('[data-sk="snack"]').forEach(function(w){var d=new Date(),mins=d.getHours()*60+d.getMinutes();snackSet(w,(mins>=1380||mins<240)?mins-mins%30:1350);});
/* egg 7 · magic word tile */
function kiraTile(f){var st=stageOf(f);var inp=f.querySelector('.sk-fin');var v=inp.value;var out=st.querySelector('.sk-kiraout');inp.value='';syncField(inp);
 if(/kira\s*-?\s*kira/.test(norm(v))||/キラキラ|きらきら/.test(v)){var w=st.querySelector('.sk-kiraword');restart(w,'is-kira');clearTimeout(w.__k);w.__k=tm(function(){w.classList.remove('is-kira');},5000);$$('.sk-kirastk',st).forEach(function(s,i){tm(function(){restart(s,'is-twk');},i*60);});out.textContent='';snd('kira');var c=rel(w,st);kira(st,c.x,c.y,40);}
 else if(v.trim()){out.textContent='“'+v.trim()+'” would just go on your list ♡';tm(function(){out.textContent='';},holdFor(out.textContent));}else inp.focus();}
/* egg 8 · nap time */
function napGo(st){st.classList.remove('is-stretch','is-trot');st.classList.add('is-nap');snd('pff');}
function napTap(st){if(!st.classList.contains('is-nap')||st.__busy)return;st.__busy=1;st.classList.add('is-stretch');snd('beep');
 tm(function(){wob(st.querySelector('.sk-napbk .bk'));st.classList.add('is-trot');snd('tk');},RM?0:420);tm(function(){st.classList.remove('is-nap','is-stretch','is-trot');st.__busy=0;},RM?0:2000);}
/* egg 9 · birthday tile */
function bday(st){var r=st.querySelector('.sk-bdrow');if(r.hidden){r.hidden=false;restart(r,'is-in1');}var li=r.querySelector('.sk-row');li.classList.remove('is-checked');li.querySelector('.sk-cb').setAttribute('aria-checked','false');
 if(RM)return;var K=['heart','star4','petal','star','cake'];for(var i=0;i<18;i++){var s=svgUse('sticker-i-'+K[i%5],14+(i%3)*4,0,'sk-lconf');s.style.left=(6+i*17)%(st.clientWidth-14)+'px';s.style.setProperty('--fx',((i%5)-2)*8+'px');s.style.setProperty('--r',((i%2)?1:-1)*(150+i*11)+'deg');s.style.animationDelay=((i%6)*.12)+'s';st.appendChild(s);(function(x){tm(function(){x.remove();},2300);})(s);}snd('jingle');}
/* egg 10 · LCD jingle on the bonus tile */
root.addEventListener('click',function(e){var b=e.target.closest('[data-sk="kon"]');if(!b)return;var bs=b.closest('[data-sk="bonus"]');var l=bs.querySelector('.sk-bonuslcd');clearInterval(bs.__jg);
 if(bs.classList.contains('is-bonus')){var fr=['BONUS','♪ ♪ ♪','STAGE!','♪♪ ♪♪'],i=0;setLcd(l,fr[0]);snd('jingle');bs.__jg=setInterval(function(){i++;setLcd(l,fr[i%4]);snd('beep');},600*TS);}else setLcd(l,'10/24');});
/* egg 11 · seasonal sheets: the tile picks a season for phone 1's add bar, its sticker book and the album */
var SEAS={maple:['Canada Day','strawberry'],pumpkin:['Thanksgiving','peach'],halloween:['Halloween','grape'],snow:['First snow','ramune'],hearts:['Valentine’s','sakura'],redenv:['Lunar New Year','strawberry']};
function season(btn){var k=btn.getAttribute('data-k'),pg=btn.getAttribute('data-pg');$$('[data-sk="season"]',btn.parentNode).forEach(function(b){var on=b===btn;b.classList.toggle('is-on',on);b.setAttribute('aria-pressed',on?'true':'false');});restart(btn,'is-twk');snd('kira');
 var s1=root.querySelector('#sticker-p1 .sk-screen');if(!s1)return;var tp=s1.querySelector('.sk-addbar .sk-tape');if(tp){tp.className=tp.className.replace(/sk-t-\w+/,'sk-t-'+k);restart(tp,'is-twk');}
 var d=s1.querySelector('.sk-bk-date');if(d){var old=d.querySelector('.sk-bk-season');if(old)old.remove();var s=svgUse('sticker-pg-'+pg,14,14,'sk-bk-season');s.style.color='var(--sk-'+SEAS[k][1]+'-t)';d.appendChild(s);if(!RM)restart(s,'is-magic');}
 var al=root.querySelector('[data-sk="seasonname"]');if(al)al.textContent=SEAS[k][0]+' sheet';toast(s1,'Seasonal sheet: '+SEAS[k][0]+' ♡');}
/* egg 12 · golden hour: within 30 minutes of sunset the binder warms and shadows lengthen; the sun sinks minute by minute */
function golden(inp){var w=inp.closest('[data-sk="golden"]');var m=+inp.value;w.querySelector('[data-sk="goldmin"]').textContent=m+' min';w.style.setProperty('--sink',((60-m)*.75).toFixed(1)+'px');w.classList.toggle('is-golden',m<=30);}
/* egg 13 · gashapon jackpot (every 2nd mystery item is gold in this demo) */
function jack(st){var w=st.querySelector('[data-sk="jackstage"]');if(w.__busy)return;w.__busy=1;var n=(w.__n||0)+1;w.__n=n;var gold=n%2===0;var it=w.querySelector('.sk-jitem use'),out=w.querySelector('.sk-jout');
 it.setAttribute('href',gold?'#sticker-ai-dragonfruit':['#sticker-ai-bokchoy','#sticker-ai-kimchi','#sticker-ai-bagel'][(n>>1)%3]);w.classList.remove('is-gold','is-pop','is-hk');w.classList.toggle('is-gold',gold);out.textContent='';restart(w,'is-wob');snd('beep');
 tm(function(){w.classList.add('is-pop');snd('pon');buzz('confirm');var c=rel(w.querySelector('.sk-jitem'),st);kira(st,c.x,c.y,36);},RM?0:650);
 if(gold)tm(function(){w.classList.add('is-hk');snd('thunk');buzz('thunk');out.textContent='holo · saved to the album';},RM?0:950);
 else tm(function(){out.textContent='a new live-drawn sticker';},RM?0:700);
 tm(function(){w.__busy=0;},RM?0:(gold?3200:1400));}
/* egg 14 · perfect week */
function pweek(w){var n=(+w.getAttribute('data-n')||0)+1;if(n>11)n=0;w.setAttribute('data-n',n);var lab=w.querySelector('[data-sk="pwn"]');
 $$('.sk-stamp',w).forEach(function(s,i){var on=i<Math.min(n,4);if(on&&!s.classList.contains('is-done')){s.classList.add('is-done');if(!RM)restart(s,'is-thunk');snd('thunk');}if(!on)s.classList.remove('is-done');});
 w.classList.toggle('is-perfect',n>=4);w.classList.toggle('is-pocket',n>=10);if(n===4){snd('jingle');buzz('thunk');}if(n===10)snd('peel');
 lab.textContent=n===0?'week 0 of 4':n<=4?'week '+n+' of 4':n<10?'trip '+n+' of 10':n===10?'trip 10 of 10 · pocket!':'tap again to start over';}
/* egg 16 · rainy day: the tile and phone 6 */
function rain(st){var w=st.querySelector('[data-sk="rain"]');var on=!w.classList.contains('is-rain');w.classList.toggle('is-rain',on);var b=w.querySelector('[data-sk="raingo"]');b.textContent=on?'☀ Clear skies':'☂ Make it rain';
 var tp=w.querySelector('.sk-eaddbar .sk-tape');if(tp)tp.className=tp.className.replace(/sk-t-\w+/,on?'sk-t-rain':'sk-t-ging');snd(on?'pff':'kira');
 var s6=root.querySelector('.sk-phones>.sk-pblock:nth-child(2) .sk-s-list');if(!s6)return;s6.classList.toggle('is-rain',on);var bar=s6.querySelector('.sk-addbar');var u=bar.querySelector('.sk-umbrella');
 if(on&&!u){u=el('span','sk-umbrella sk-liveumb');u.setAttribute('aria-hidden','true');u.innerHTML=w.querySelector('.sk-umbrella').innerHTML;bar.appendChild(u);}if(u&&!on)u.remove();if(u&&on&&!RM)restart(u,'is-magic');
 var t6=bar.querySelector('.sk-tape');if(t6){if(on){t6.__was=(/sk-t-\w+/.exec(t6.className)||['sk-t-snow'])[0];t6.className=t6.className.replace(/sk-t-\w+/,'sk-t-rain');}else t6.className=t6.className.replace(/sk-t-\w+/,t6.__was||'sk-t-snow');}
 if(on){toast(s6,'Rain on the way · bring the tote! ☂',null,{ms:4500});var tt=w.querySelector('.sk-raintoast');restart(tt,'is-show');}}
/* egg 17 · ant parade tile */
function antAdd(st,k){var w=st.querySelector('[data-sk="antstage"]');var l=w.querySelector('[data-sk="antlist"]');if(w.classList.contains('is-march'))return;if(l.querySelector('[data-k="'+k+'"]'))return;
 var s=svgUse('sticker-i-'+k,34,34,'');s.setAttribute('data-k',k);l.appendChild(s);if(!RM)restart(s,'is-twk');snd('tk');var b=st.querySelector('[data-sk="antadd"][data-k="'+k+'"]');if(b)b.disabled=true;
 if(l.children.length>=3){tm(function(){w.classList.add('is-march');snd('kira');},RM?0:400);tm(function(){w.classList.remove('is-march');l.innerHTML='';$$('[data-sk="antadd"]',st).forEach(function(x){x.disabled=false;});},RM?600:5400);}}
root.addEventListener('click',function(e){var t=e.target.closest('[data-sk]');if(!t||!root.contains(t))return;var k=t.getAttribute('data-sk'),st=stageOf(t);
 if(k==='roll3')roll3(st,t.getAttribute('data-k'),t.getAttribute('data-w'));
 else if(k==='shake4')shake4(st);
 else if(k==='clock'){var w=t.closest('[data-sk="snack"]');snackSet(w,(w.__m||1350)+(+t.getAttribute('data-d')));snd('beep');}
 else if(k==='kirasend')kiraTile(t.closest('[data-sk="kiratile"]'));
 else if(k==='napgo')napGo(st);
 else if(k==='nap')napTap(st);
 else if(k==='bday')bday(st);
 else if(k==='bdrow'){var cb=t.querySelector('.sk-cb');var on=!t.classList.contains('is-checked');t.classList.toggle('is-checked',on);cb.setAttribute('aria-checked',on?'true':'false');if(on){restart(t,'is-slam');var c=rel(cb,st);burst(st,c.x,c.y,['heart','star','cake','star4'],8);snd('thunk');buzz('confirm');}}
 else if(k==='season')season(t);
 else if(k==='jack')jack(st);
 else if(k==='pweek')pweek(t.closest('[data-sk="pweekw"]'));
 else if(k==='petfeed'){var l=st.querySelector('[data-sk="pettile"]');l.__n=Math.min(24,(l.__n||10)+1);if(!l.__busy)setLcd(l,l.__n+'/24');var p=l.querySelector('.sk-pet');if(p&&!RM){restart(p,l.__n>=24?'is-dance':'is-munch');}snd('beep');if(l.__n>=24)tm(function(){l.__n=10;setLcd(l,'10/24');if(p)p.classList.remove('is-dance');},2600);}
 else if(k==='raingo')rain(st);
 else if(k==='antadd')antAdd(st,t.getAttribute('data-k'));
});
root.addEventListener('input',function(e){if(e.target.getAttribute&&e.target.getAttribute('data-sk')==='goldrange')golden(e.target);});
if(window.DeviceMotionEvent)window.addEventListener('devicemotion',function(e){var a=e.accelerationIncludingGravity||e.acceleration;if(!a)return;if(Math.abs(a.x||0)+Math.abs(a.y||0)>34){$$('[data-sk="petalstage"]').forEach(function(st){var r=st.getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)shake4(st);});}});
