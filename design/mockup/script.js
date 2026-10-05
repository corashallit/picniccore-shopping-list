(function(){
var root=document.getElementById('sticker');if(!root||root.__skInit)return;root.__skInit=1;
var RM=window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches;
var NS='http://www.w3.org/2000/svg';
function $$(s,el){return Array.prototype.slice.call((el||root).querySelectorAll(s));}
/* every timer goes through tm(): a test harness can slow the whole prototype (root.__ts = 10 with CSS playback at 0.1) to film exact frames */
var TS=1;try{Object.defineProperty(root,'__ts',{set:function(v){TS=+v||1;},get:function(){return TS;}});}catch(e){}
function tm(fn,ms){return setTimeout(fn,(ms||0)*TS);}
function later(fn,ms){return tm(fn,RM?0:ms);}
function el(tag,cls,html){var e=document.createElement(tag);if(cls)e.className=cls;if(html!=null)e.innerHTML=html;return e;}
function svgUse(id,w,h,cls){var s=document.createElementNS(NS,'svg');s.setAttribute('width',w);s.setAttribute('height',h||w);s.setAttribute('aria-hidden','true');if(cls)s.setAttribute('class',cls);s.innerHTML='<use href="#'+id+'"/>';return s;}
function restart(e,c){if(!e)return;e.classList.remove(c);void e.getBoundingClientRect();e.classList.add(c);}
/* ---------- timing (ms): one table drives the live prototype, the tiles and the storyboards on the page ---------- */
var T={peel:200,hop:320,hopDur:300,hopCat:360,book:320,lid:300,stamp:500,curl:800,fold:1100,next:200,finale:250,nextHoldMin:2600,base:1200,perWord:300,celeHide:13800,perfect:450};
/* ---------- Surprises (phone 4 settings): eggs, sounds, haptics, next stop, celebration ---------- */
var S={eggs:true,sounds:false,haptics:true,next:true,cele:true};
var AC=null;
function tone(f,d,type,vol,when,slide){if(!S.sounds)return;try{AC=AC||new (window.AudioContext||window.webkitAudioContext)();var t=AC.currentTime+(when||0);var o=AC.createOscillator(),g=AC.createGain();o.type=type||'square';o.frequency.setValueAtTime(f,t);if(slide)o.frequency.exponentialRampToValueAtTime(slide,t+d);g.gain.setValueAtTime(vol||.06,t);g.gain.exponentialRampToValueAtTime(.0005,t+d);o.connect(g);g.connect(AC.destination);o.start(t);o.stop(t+d+.02);}catch(e){}}
var SND={thunk:function(){tone(150,.12,'triangle',.18,0,60);},tk:function(){tone(1800,.03,'square',.03);},pff:function(){tone(420,.09,'sine',.07,0,220);},clunk:function(){tone(110,.08,'square',.08);tone(70,.08,'square',.06,.06);},
 beep:function(){tone(1320,.05,'square',.035);},pon:function(){tone(660,.08,'sine',.1);tone(990,.12,'sine',.08,.07);},
 kira:function(){tone(1568,.07,'sine',.05);tone(2093,.1,'sine',.04,.06);},peel:function(){tone(900,.05,'sawtooth',.02,0,500);},
 jingle:function(){[523,659,784,1047,784,1047].forEach(function(f,i){tone(f,.1,'square',.04,i*.11);});},print:function(){for(var i=0;i<10;i++)tone(2400+((i%2)*300),.025,'square',.018,i*.12);}};
function snd(k){if(S.sounds&&SND[k])SND[k]();}
var HAP={confirm:[18],tick:[6],land:[4],tap:[8],thunk:[22,40,12]};
function buzz(k){if(S.haptics&&navigator.vibrate&&HAP[k])try{navigator.vibrate(HAP[k]);}catch(e){}}
/* ---------- LCD dot matrix (glyphs shared with the generator) ---------- */
var G=/*G57*/{};
function starPath(cx,cy,R,r){var s='';for(var i=0;i<10;i++){var a=(-90+i*36)*Math.PI/180,q=i%2?r:R;s+=(i?'L':'M')+(cx+q*Math.cos(a)).toFixed(2)+' '+(cy+q*Math.sin(a)).toFixed(2);}return s+'Z';}
function setLcd(e,text,label){if(!e)return;var svg=e.querySelector('svg.sk-lcdsvg');if(!svg)return;var p=parseFloat(e.getAttribute('data-p')||'2.2');var on='',x=0;text=String(text).toUpperCase();
 for(var i=0;i<text.length;i++){var g=G[text[i]];if(!g)continue;var rows=g.split(' '),w=rows[0].length;for(var r=0;r<rows.length;r++)for(var c=0;c<w;c++)if(rows[r][c]==='1')on+='M'+(x+c*p).toFixed(2)+' '+(r*p).toFixed(2)+'h'+(p*.8).toFixed(2)+'v'+(p*.8).toFixed(2)+'h-'+(p*.8).toFixed(2)+'z';x+=(w+1)*p;}
 var off=svg.querySelector('.sk-loff'),lon=svg.querySelector('.sk-lon'),st=svg.querySelector('.sk-lstar');if(off)off.setAttribute('width',Math.max(0,x-p*1.2).toFixed(2));if(lon)lon.setAttribute('d',on);
 var W=x-p*.2;if(st&&(e.classList.contains('has-pet')||e.classList.contains('no-star'))){st.setAttribute('d','');st=null;}if(st){var R=p*3,cx=x+p*3,cy=p*3.4;st.setAttribute('d',e.closest('.is-dusk')?'M'+(cx-R).toFixed(2)+' '+cy.toFixed(2)+'a'+R+' '+R+' 0 1 0 '+(2*R).toFixed(2)+' 0a'+R+' '+R+' 0 1 0 '+(-2*R).toFixed(2)+' 0z':starPath(cx,cy,p*3.6,p*1.6));W=x+p*7.4-p*.2;}
 svg.setAttribute('viewBox','-.4 -.4 '+(W+.8).toFixed(2)+' '+(p*7+.6).toFixed(2));svg.setAttribute('width',(W+.8).toFixed(2));e.setAttribute('aria-label',label||(/^\d+\/\d+$/.test(text)?text.replace('/',' of ')+' in the basket':text.toLowerCase()));
 e.classList.remove('is-tick');void e.offsetWidth;if(!RM)e.classList.add('is-tick');}
/* ---------- bursts, wobbles, sparkles ---------- */
function burst(host,x,y,kinds,n,col,cls,dist){if(RM||!host)return;kinds=kinds||['heart','star','star4','flower'];n=n||8;dist=dist||30;for(var i=0;i<n;i++){var a=i/n*Math.PI*2+.3,d=dist+(i%3)*(dist/3);var s=document.createElementNS(NS,'svg');s.setAttribute('class','sk-burstp'+(cls?' '+cls:''));s.setAttribute('aria-hidden','true');
 var kk=kinds[i%kinds.length];s.innerHTML='<use href="#'+(kk.charAt(0)==='#'?kk.slice(1):'sticker-i-'+kk)+'"/>';if(col)s.style.color=col;s.style.left=x+'px';s.style.top=y+'px';s.style.setProperty('--dx',(Math.cos(a)*d).toFixed(1)+'px');s.style.setProperty('--dy',(Math.sin(a)*d).toFixed(1)+'px');s.style.setProperty('--r',(i%2?120:-120)+'deg');host.appendChild(s);tm(function(nn){return function(){nn.remove();};}(s),640);}}
function kira(host,x,y,size){if(RM||!host)return;var s=svgUse('sticker-i-kira',size||30,size||30,'sk-bk-kira');s.style.left=x+'px';s.style.top=y+'px';host.appendChild(s);tm(function(){s.remove();},460);}
function zf(host){var ph=host&&host.closest&&host.closest('.sk-phone');if(!ph)return 1;var z=parseFloat(getComputedStyle(ph).zoom);return z&&z>0?z:1;}
function rel(e,host){var a=e.getBoundingClientRect(),b=host.getBoundingClientRect(),z=zf(host);return {x:(a.left-b.left+a.width/2)/z,y:(a.top-b.top+a.height/2)/z,l:(a.left-b.left)/z,t:(a.top-b.top)/z,w:a.width/z,h:a.height/z};}
function wob(e){if(!e||RM)return;restart(e,'sk-bkwob');}
function heartPop(host,bk){if(RM||!host||!bk)return;var r=rel(bk,host);var s=svgUse('sticker-i-heart',14,14,'sk-rimheart');s.style.left=(r.x-7)+'px';s.style.top=(r.t+r.h*.3)+'px';host.appendChild(s);tm(function(){s.remove();},700);}
function words(t){return String(t).replace(/[·♡★✦…!?,.]/g,' ').trim().split(/\s+/).filter(Boolean).length;}
function holdFor(t,min){return Math.max(min||0,T.base+T.perWord*words(t));}
/* ---------- toasts: a sticky note bottom-left above the add bar, held ≥ 1.2 s + 0.3 s/word (4 s for the spec's toasts) ---------- */
function toast(scr,text,undo,opts){if(!scr)return;opts=opts||{};var old=scr.querySelector('.sk-livetoast');if(old)old.remove();var t=el('div','sk-toast sk-livetoast'+(opts.cls?' '+opts.cls:''));t.setAttribute('role','status');
 t.innerHTML='<i class="sk-tape sk-t-ging" aria-hidden="true"></i><span class="sk-tmsg"></span>';t.querySelector('.sk-tmsg').textContent=text;
 if(undo){var b=el('button','sk-chip sk-undo','<svg width="16" height="16" aria-hidden="true"><use href="#sticker-tb-undo"/></svg>Undo');b.type='button';b.addEventListener('click',function(ev){ev.stopPropagation();undo();hide();});t.appendChild(b);}
 scr.appendChild(t);var ms=opts.ms||Math.max(4000,holdFor(text));function hide(){t.classList.add('is-out');tm(function(){t.remove();},RM?0:260);}clearTimeout(scr.__toastT);scr.__toastT=tm(hide,ms);return t;}
/* ---------- baskets: one rule — a basket holds only what has already been gotten ---------- */
var FILL={1:[[32,0,0]],2:[[22,0,-7],[42,0,7]],3:[[13,1.5,-9],[32,0,0],[51,1.5,9]],4:[[13,1.5,-9],[51,1.5,9],[23,-6,-4],[41,-6,4]],5:[[13,1.5,-9],[32,0,0],[51,1.5,9],[23,-7,-4],[41,-7,4]]},SI=30,LIFT=.7,MAXIN=5,GOTSEQ=0;
function mk(key,x,y,s,r,cls){var u=document.createElementNS(NS,'use');u.setAttribute('href','#'+symOf(key));u.setAttribute('x',x.toFixed(2));u.setAttribute('y',y.toFixed(2));u.setAttribute('width',s);u.setAttribute('height',s);u.setAttribute('class',cls);u.setAttribute('data-k',key);if(!r)return u;
 var w=document.createElementNS(NS,'g');w.setAttribute('transform','rotate('+r+' '+(x+s/2).toFixed(2)+' '+(y+s).toFixed(2)+')');w.appendChild(u);return w;}
function relayout(bk,got,fresh){if(!bk)return;var g=bk.querySelector('.bk-minis');if(!g)return;var rim=parseFloat(bk.getAttribute('data-rim')||'20');g.innerHTML='';
 var ins=got.slice(-MAXIN),L=FILL[ins.length]||[],sl=[];for(var i=0;i<ins.length;i++)sl.push([ins[i],L[i][0],L[i][1],L[i][2]]);
 if(!ins.length){var c=document.createElementNS(NS,'circle');c.setAttribute('class','bk-wait');c.setAttribute('cx','32');c.setAttribute('cy',(rim-1.5).toFixed(2));c.setAttribute('r','7');g.appendChild(c);}
 sl.sort(function(a,b){return (a[2]>=0)-(b[2]>=0);});sl.forEach(function(x){g.appendChild(mk(x[0],x[1]-SI/2,rim-SI*LIFT+x[2],SI,x[3],'bk-in'+(x[2]<0?' bk-back':'')+(fresh&&x[0]===fresh&&!RM?' is-land':'')));});
 var tg=bk.querySelector('.bk-tag');if(tg){var extra=got.length-MAXIN;tg.style.display=extra>0?'':'none';var t=tg.querySelector('text');if(t)t.textContent='+'+Math.max(extra,1);}}
function rowKey(r){return r.getAttribute('data-mini')||r.getAttribute('data-key');}
/* gotten = landed in the basket, in landing order (rows checked before the page loaded keep row order) */
function gotOrder(rows){var a=[];rows.forEach(function(r,i){if(r.classList.contains('is-inbk')||(r.classList.contains('is-checked')&&!r.__live))a.push({k:rowKey(r),t:parseInt(r.getAttribute('data-got')||'0',10),i:i});});
 a.sort(function(x,y){return x.t-y.t||x.i-y.i;});return a.map(function(x){return x.k;});}
function setBadge(b,left){if(!b)return;if(left===0){b.hidden=true;}else{b.hidden=false;b.textContent=left;}}
function aisleRows(aw){return $$('.sk-opened .sk-row',aw);}
function aisleState(aw){var rs=aisleRows(aw),w=[];rs.forEach(function(r){if(!r.classList.contains('is-inbk'))w.push(rowKey(r));});return {w:w,i:gotOrder(rs),n:rs.length};}
function syncAisle(aw,fresh){var s=aisleState(aw);var obk=aw.querySelector('.sk-opened .bk'),fbk=aw.querySelector('.sk-folded .bk');relayout(obk,s.i,fresh);relayout(fbk,s.i);
 setBadge(aw.querySelector('.sk-opened .sk-badge'),s.w.length);setBadge(aw.querySelector('.sk-folded .sk-badge'),s.w.length);
 var m=aw.querySelector('.sk-fmeta');if(m)m.innerHTML=s.w.length?('<b>'+s.w.length+' left</b> of '+s.n):('<b>'+s.n+' of '+s.n+'</b> in the basket');
 var fb=aw.querySelector('.sk-fold');if(fb)fb.setAttribute('aria-label','Aisle '+aw.getAttribute('data-n')+', '+aw.getAttribute('data-name')+': '+(s.w.length?s.w.length+' left of '+s.n:s.n+' of '+s.n+' in the basket')+'. Expand');
 if(s.w.length&&aw.classList.contains('is-complete')){aw.classList.remove('is-complete');[obk,fbk].forEach(function(b){if(b)b.classList.remove('is-done','is-anim');});stampSet(aw,false);}
 return s;}
function stampSet(aw,on){var scr=aw.closest('.sk-screen');if(!scr)return null;var st=scr.querySelector('.sk-book .sk-stamp[data-n="'+aw.getAttribute('data-n')+'"]');if(!st)return null;
 var inner=st.querySelector('.sk-pg,.sk-sn');if(inner)inner.remove();
 if(on){var s=document.createElementNS(NS,'svg');s.setAttribute('class','sk-pg'+(RM?'':' sk-thunk'));s.setAttribute('aria-hidden','true');s.style.setProperty('--st','var(--sk-'+st.getAttribute('data-fam')+'-t)');s.innerHTML='<use href="#sticker-pg-'+(scr.__pix&&scr.__pix[st.getAttribute('data-n')]||st.getAttribute('data-pic'))+'"/>';st.appendChild(s);st.classList.add('is-done');st.setAttribute('aria-label',aw.getAttribute('data-name')+': stamped');}
 else{var n=document.createElement('span');n.className='sk-sn';n.textContent=st.getAttribute('data-n');st.appendChild(n);st.classList.remove('is-done');st.setAttribute('aria-label',aw.getAttribute('data-name')+': not yet');}
 return st;}
function setNow(scr,aw){$$('.sk-book .sk-stamp.is-now',scr).forEach(function(s){s.classList.remove('is-now');});if(!aw)return;var st=scr.querySelector('.sk-book .sk-stamp[data-n="'+aw.getAttribute('data-n')+'"]');if(st&&!st.classList.contains('is-done'))st.classList.add('is-now');
 $$('.sk-page.is-current',scr).forEach(function(p){p.classList.remove('is-current');});var op=aw.querySelector('.sk-opened');if(op)op.classList.add('is-current');
 var bee=scr.querySelector('.sk-beefly');if(bee&&op&&bee.parentNode!==op.querySelector('.sk-tabrow')){var tr=op.querySelector('.sk-tabrow');if(tr)tr.appendChild(bee);}}
/* ---------- the trip sticker book ---------- */
var PER=24;
function bookOf(scr){return scr&&scr.querySelector('.sk-book[data-sk="book"]');}
function bookInit(scr){var bk=bookOf(scr);if(!bk||bk.__init)return bk;bk.__init=1;var rows=$$('.sk-aw .sk-opened .sk-row',scr);bk.__slots=[];bk.__page=parseInt(bk.getAttribute('data-page')||'0',10);
 rows.forEach(function(r){if(r.classList.contains('is-checked')){r.__slot=bk.__slots.length;bk.__slots.push(rowKey(r));}});while(bk.__slots.length<rows.length)bk.__slots.push(null);return bk;}
function bookCount(bk){return bk.__slots.filter(function(x){return x;}).length;}
function bookRender(bk,p){var ol=bk.querySelector('.sk-bk-page');var html='';var a=p*PER,b=Math.min(bk.__slots.length,a+PER);var R=[-6,4,-2,7,-5,3,6,-4,2,-7,5,-3];
 for(var i=a;i<b;i++){var k=bk.__slots[i];html+=k?'<li class="sk-bk-slot is-on" data-key="'+k+'" style="--r:'+R[i%12]+'deg"><svg class="sk-bk-st" width="26" height="26" aria-hidden="true"><use href="#'+symOf(k)+'"/></svg></li>':'<li class="sk-bk-slot" style="--r:'+R[i%12]+'deg"></li>';}
 ol.innerHTML=html;bk.__page=p;bk.setAttribute('data-page',p);var pages=Math.max(1,Math.ceil(bk.__slots.length/PER)),pn=bk.querySelector('.sk-bk-pno');if(pn){pn.hidden=pages<2;pn.textContent=(p+1)+'/'+pages;}bookNext(bk);}
function bookNext(bk){$$('.sk-bk-slot.is-next',bk).forEach(function(s){s.classList.remove('is-next');});var i=bk.__slots.indexOf(null);if(i<0||Math.floor(i/PER)!==bk.__page)return;var li=bk.querySelectorAll('.sk-bk-slot')[i%PER];if(li)li.classList.add('is-next');}
function bookShow(bk,p,cb){if(bk.__page===p||RM){if(bk.__page!==p)bookRender(bk,p);cb&&cb();return;}restart(bk,'is-turn');snd('peel');tm(function(){bookRender(bk,p);},120);tm(function(){bk.classList.remove('is-turn');cb&&cb();},250);}
function bookSlotEl(bk,i){return bk.querySelectorAll('.sk-bk-slot')[i%PER];}
function bookLabel(bk,say){var n=bookCount(bk),N=bk.__slots.length,scr=bk.closest('.sk-screen');var st=$$('.sk-stamp.is-done',bk).length,ns=$$('.sk-stamp',bk).length;
 bk.setAttribute('aria-label','Trip sticker book, '+(bk.querySelector('.sk-bk-date').textContent||'')+': '+n+' of '+N+' stickers, '+st+' of '+ns+' aisles stamped');var lv=bk.querySelector('[data-sk="booklive"]');if(lv&&say)lv.textContent=say;
 bk.classList.toggle('is-full',n>=N&&N>0);}
function nameOf(row){var n=row&&row.querySelector('.sk-nm');return n?n.textContent:'';}
function bookPut(scr,row,quiet){var bk=bookInit(scr);if(!bk||row.__slot!=null)return;var k=rowKey(row);var i=bk.__slots.indexOf(null);if(i<0){i=bk.__slots.length;bk.__slots.push(null);}bk.__slots[i]=k;row.__slot=i;
 var wasFull=bk.classList.contains('is-full');
 bookShow(bk,Math.floor(i/PER),function(){var li=bookSlotEl(bk,i);if(!li)return;li.classList.add('is-on');li.classList.remove('is-next','is-peel');li.setAttribute('data-key',k);li.innerHTML='<svg class="sk-bk-st" width="26" height="26" aria-hidden="true"><use href="#'+symOf(k)+'"/></svg>';
  bookNext(bk);bookLabel(bk,nameOf(row)+' added to today’s sticker book');if(quiet||RM)return;
  restart(li,'is-magic');var c=rel(li,scr);kira(scr,c.x,c.y);burst(scr,c.x,c.y,['star4','heart','star','star4','flower','star'],6,null,'is-mini',13);restart(bk,'is-lift');snd('kira');
  tm(function(){li.classList.remove('is-magic');},420);
  if(!wasFull&&bk.classList.contains('is-full')){tm(function(){restart(bk,'is-fullnow');},360);}});}
function bookTake(scr,row){var bk=bookInit(scr);if(!bk||row.__slot==null)return;var i=row.__slot;row.__slot=null;bk.__slots[i]=null;bk.classList.remove('is-fullnow');
 bookShow(bk,Math.floor(i/PER),function(){var li=bookSlotEl(bk,i);if(!li)return;bookLabel(bk,nameOf(row)+' peeled out of today’s sticker book');
  function clear(){li.classList.remove('is-on','is-peel','is-magic');li.removeAttribute('data-key');li.innerHTML='';bookNext(bk);}
  if(RM){clear();return;}restart(li,'is-peel');var c=rel(li,scr);kira(scr,c.x,c.y,22);burst(scr,c.x,c.y,['star4','heart','star'],6,null,'is-mini is-in',13);snd('peel');tm(clear,330);});}
function bookResize(scr){var bk=bookInit(scr);if(!bk)return;var N=$$('.sk-aw .sk-opened .sk-row',scr).length;while(bk.__slots.length<N)bk.__slots.push(null);
 while(bk.__slots.length>N){var j=bk.__slots.lastIndexOf(null);if(j<0)break;bk.__slots.splice(j,1);$$('.sk-aw .sk-opened .sk-row',scr).forEach(function(r){if(r.__slot!=null&&r.__slot>j)r.__slot--;});}
 var p=Math.min(bk.__page,Math.max(0,Math.ceil(bk.__slots.length/PER)-1));var i=bk.__slots.indexOf(null);if(i>=0)p=Math.floor(i/PER);bookRender(bk,p);bookLabel(bk);}
function bookPerfect(scr,on){var bk=bookOf(scr);if(!bk)return;if(!on){bk.classList.remove('is-perfect');return;}if(bk.classList.contains('is-perfect'))return;bk.classList.add('is-perfect');if(RM)return;
 var hk=bk.querySelector('.sk-bk-perfect');restart(hk,'is-slam');var c=rel(hk,scr);burst(scr,c.x,c.y,['star4','heart','star'],8,null,'is-mini',22);snd('thunk');buzz('thunk');restart(bk,'is-fullnow');}
/* ---------- the check-off chain: hanko → peel (200) → hop (320, 300 ms) → land + squash → +320 the twin appears in the book ---------- */
function pend(row,t){(row.__pend=row.__pend||[]).push(t);return t;}
function clearPend(row){(row.__pend||[]).forEach(clearTimeout);row.__pend=[];if(row.__hopper){row.__hopper.remove();row.__hopper=null;}}
function basketTarget(basket,host){var b=basket.getBoundingClientRect(),hb=host.getBoundingClientRect(),z=zf(host),rim=parseFloat(basket.getAttribute('data-rim')||'20');return {x:(b.left-hb.left+b.width/2)/z,y:(b.top-hb.top+(rim+9)/64*b.height)/z-6};}
function checkRow(row,ctx){var host=ctx.host,basket=ctx.basket,cb=row.querySelector('.sk-cb');clearPend(row);row.__live=1;row.setAttribute('data-got',++GOTSEQ);
 row.classList.remove('is-inbk','is-landing');row.classList.add('is-checked','is-slam');pend(row,tm(function(){row.classList.remove('is-slam');},300));
 var hk=row.querySelector('.sk-cb .sk-hanko use');if(hk&&row.getAttribute('data-hanko'))hk.setAttribute('href','#'+row.getAttribute('data-hanko'));
 if(host&&cb){var c=rel(cb,host);burst(host,c.x,c.y);}snd('thunk');buzz('confirm');if(cb)cb.setAttribute('aria-checked','true');
 var icon=row.querySelector('.sk-real');
 if(RM||!host||!basket||!icon||!basket.getClientRects().length){land(row,ctx,true);return;}
 row.classList.add('is-peeling');pend(row,tm(function(){snd('tk');buzz('tick');},T.peel));
 pend(row,tm(function(){row.classList.remove('is-peeling');if(!basket.getClientRects().length){land(row,ctx,true);return;}
  var p=rel(icon.closest('.sk-ico')||icon,host),q=basketTarget(basket,host);var h=icon.cloneNode(true);var cat=row.getAttribute('data-cat');
  h.setAttribute('class','sk-hopper sk-hop2'+(cat?' h-'+cat:''));h.removeAttribute('role');h.removeAttribute('aria-label');h.style.left=p.l+'px';h.style.top=p.t+'px';h.style.setProperty('--hx',(q.x-p.x)+'px');h.style.setProperty('--hy',(q.y-p.y)+'px');
  host.appendChild(h);row.__hopper=h;pend(row,tm(function(){h.remove();row.__hopper=null;land(row,ctx);},cat?T.hopCat:T.hopDur));},T.hop));}
function land(row,ctx,instant){row.classList.add('is-inbk');var k=rowKey(row);if(ctx.onLand)ctx.onLand(row,k,instant);if(!instant){wob(ctx.basket);heartPop(ctx.host,ctx.basket);snd('pff');buzz('land');}
 if(ctx.book)pend(row,later(function(){ctx.book(row);},instant?0:T.book));}
function uncheckRow(row,ctx){var host=ctx.host,basket=ctx.basket,wasIn=row.classList.contains('is-inbk');clearPend(row);row.__live=1;
 row.removeAttribute('data-got');row.classList.remove('is-checked','is-inbk','is-peeling','is-slam');var cb=row.querySelector('.sk-cb');if(cb)cb.setAttribute('aria-checked','false');
 var ic=row.querySelector('.sk-real');
 if(wasIn&&host&&ic&&basket&&!RM&&basket.getClientRects().length){row.classList.add('is-landing');var p=rel(ic.closest('.sk-ico')||ic,host),q=basketTarget(basket,host);var h=ic.cloneNode(true);h.setAttribute('class','sk-hopper sk-hopout');h.removeAttribute('role');h.removeAttribute('aria-label');
  h.style.left=p.l+'px';h.style.top=p.t+'px';h.style.setProperty('--hx',(q.x-p.x)+'px');h.style.setProperty('--hy',(q.y-p.y)+'px');host.appendChild(h);row.__hopper=h;wob(basket);snd('peel');
  pend(row,tm(function(){h.remove();row.__hopper=null;row.classList.remove('is-landing');},360));}
 if(ctx.onUnland)ctx.onUnland(row,wasIn);if(ctx.unbook)ctx.unbook(row);}
/* ---------- the live list phones (1 + 6) ---------- */
function countRows(scr){var all=$$('.sk-aw .sk-opened .sk-row',scr),n=0,m=0;all.forEach(function(r){if(r.classList.contains('is-checked'))n++;if(r.classList.contains('is-inbk'))m++;});return [m,all.length,n];}
function lcdCount(scr){var c=countRows(scr);var l=scr.querySelector('.sk-appbar .sk-lcdmain');if(l&&!l.__busy)setLcd(l,c[0]+'/'+c[1]);}
function listCtx(scr,row){var aw=row.closest('.sk-aw');var bk=aw?aw.querySelector('.sk-opened .bk'):null;if(bk&&!bk.getClientRects().length)bk=aw.querySelector('.sk-folded .bk');
 return {host:scr,basket:bk,aw:aw,
  onLand:function(r,k,instant){var s=syncAisle(aw,instant?null:k);lcdCount(scr);petFeed(scr);if(!s.w.length)complete(scr,aw);},
  book:function(r){bookPut(scr,r);},
  onUnland:function(r){syncAisle(aw);lcdCount(scr);if(scr.__tripDone){scr.__tripDone=false;bookPerfect(scr,false);}},
  unbook:function(r){bookTake(scr,r);}};}
function toggleListRow(row){var scr=row.closest('.sk-screen');if(!scr)return;bookInit(scr);var cbx=row.querySelector('.sk-cb')||row;scr.__lastXY=rel(cbx,scr);var ctx=listCtx(scr,row);
 if(row.classList.contains('is-checked'))uncheckRow(row,ctx);else checkRow(row,ctx);}
function nextAisle(scr,aw){var aws=$$('.sk-aw',scr),i=aws.indexOf(aw);for(var k=1;k<=aws.length;k++){var a=aws[(i+k)%aws.length];if(aisleState(a).w.length)return a;}return null;}
function openAisle(scr,aw,opts){opts=opts||{};$$('.sk-aw.is-open',scr).forEach(function(o){if(o!==aw){o.classList.remove('is-open','is-anim','is-curl');void o.offsetWidth;if(!RM)o.classList.add('is-anim');}});
 aw.classList.add('is-open');aw.classList.remove('is-anim');void aw.offsetWidth;if(!RM)aw.classList.add('is-anim');setNow(scr,aw);
 if(opts.scroll!==false){var sc=scr.querySelector('.sk-scroll');scr.__prog=Date.now();if(sc){var top=aw.offsetTop-4;try{sc.scrollTo({top:top,behavior:RM?'auto':'smooth'});}catch(e){sc.scrollTop=top;}}}
 if(opts.focus){var b=aw.querySelector('.sk-opened .sk-chev');if(b)b.focus({preventScroll:true});}}
function foldAisle(scr,aw,focus){aw.classList.remove('is-open','is-anim','is-curl');void aw.offsetWidth;if(!RM)aw.classList.add('is-anim');if(focus){var b=aw.querySelector('.sk-fold');if(b)b.focus({preventScroll:true});}}
function showNext(scr,a){if(!S.next)return;var c=scr.querySelector('.sk-nextcard');if(!c)return;var t=c.querySelector('.sk-nxtab');var f=a.getAttribute('data-fam'),pt=a.getAttribute('data-pat');
 t.style.cssText='--ap:var(--sk-'+f+'-p);--am:var(--sk-'+f+'-m);--at:var(--sk-'+f+'-t);--pat:var(--sk-pat-'+pt+');--pats:10px';t.querySelector('.sk-num').textContent=a.getAttribute('data-n');t.querySelector('.sk-tabname').textContent=a.getAttribute('data-name');
 c.setAttribute('aria-label','Next: aisle '+a.getAttribute('data-n')+', '+a.getAttribute('data-name'));var hold=holdFor('Next '+a.getAttribute('data-n')+' '+a.getAttribute('data-name'),T.nextHoldMin);c.__hold=hold;
 clearTimeout(c.__t);clearTimeout(c.__t2);c.hidden=false;c.classList.remove('is-in','is-out');void c.offsetWidth;c.classList.add('is-in');snd('pon');
 c.__t=tm(function(){hideNext(c);},hold+(RM?120:400));}
function hideNext(c){if(!c||c.hidden)return;clearTimeout(c.__t);c.classList.remove('is-in');c.classList.add('is-out');c.__t2=tm(function(){c.hidden=true;c.classList.remove('is-out');},RM?120:320);}
function complete(scr,aw){if(aw.classList.contains('is-complete'))return;aw.classList.add('is-complete');var obk=aw.querySelector('.sk-opened .bk'),fbk=aw.querySelector('.sk-folded .bk');var wasOpen=aw.classList.contains('is-open');var seq=aw.__seq=(aw.__seq||0)+1;
 function ok(){return aw.__seq===seq&&aw.classList.contains('is-complete');}
 later(function(){if(!ok())return;if(obk){obk.classList.add('is-done');if(!RM){obk.classList.add('is-anim','bk-hop');}}if(fbk)fbk.classList.add('is-done');snd('thunk');buzz('confirm');},T.lid);
 later(function(){if(!ok())return;var st=stampSet(aw,true);if(st){var c=rel(st,scr);burst(scr,c.x,c.y,['#sticker-pg-'+st.getAttribute('data-pic')],4,'var(--sk-'+st.getAttribute('data-fam')+'-t)');st.classList.remove('is-now');snd('thunk');buzz('thunk');}bookLabel(bookOf(scr)||root);},T.stamp);
 if(!wasOpen){later(function(){if(ok())afterAisle(scr,aw);},T.stamp+200);return;}
 later(function(){if(!ok())return;if(!RM)aw.classList.add('is-curl');snd('peel');},T.curl);
 later(function(){if(!ok())return;foldAisle(scr,aw);afterAisle(scr,aw);},T.fold);}
function afterAisle(scr,aw){var c=countRows(scr);if(c[0]===c[1]&&c[1]>0){later(function(){finale(scr);},T.finale);return;}if(scr.__fast)return;var nx=nextAisle(scr,aw);if(nx)later(function(){showNext(scr,nx);openAisle(scr,nx);},T.next);}
/* ---------- finale: the celebration plays over the phone; afterwards the book is the trip's keepsake (stamp page holo + PERFECT!) ---------- */
function finale(scr){scr.__tripDone=true;var host=scr.querySelector('.sk-celehost');var tpl=root.querySelector('.sk-celewrap [data-sk="cele"]');
 function after(){later(function(){bookPerfect(scr,true);},T.perfect);var sc=scr.querySelector('.sk-scroll');var hint=scr.querySelector('.sk-lcdmain');if(hint)restart(hint,'is-ready');}
 if(!S.cele||!host||!tpl){after();return;}host.innerHTML='';var cl=tpl.cloneNode(true);cl.classList.remove('is-play','is-skip');cl.removeAttribute('data-sk');cl.setAttribute('data-sk','celeinner');var un=cl.querySelector('.c-under');if(un)un.remove();
 var sb=scr.getBoundingClientRect(),sz=zf(scr),SW=sb.width/sz,SH=sb.height/sz,o=scr.__lastXY;if(!o||!isFinite(o.x)||!isFinite(o.y)||o.x<0||o.y<0||o.x>SW||o.y>SH)o={x:SW/2,y:SH/2};cl.style.setProperty('--ix',o.x.toFixed(0)+'px');cl.style.setProperty('--iy',o.y.toFixed(0)+'px');host.appendChild(cl);
 host.classList.add('is-on');host.__after=after;void cl.offsetWidth;cl.classList.add('is-play');snd('jingle');clearTimeout(host.__t);host.__t=tm(function(){closeCele(host);},RM?6000:T.celeHide);}
function closeCele(h){if(!h||!h.classList.contains('is-on'))return;clearTimeout(h.__t);h.classList.remove('is-on');h.innerHTML='';var f=h.__after;h.__after=null;if(f)f();}
/* ---------- egg 15 · LCD pet, egg 18 · LCD receipt ---------- */
function petFeed(scr){var l=scr.querySelector('.sk-appbar .sk-lcdmain');if(!l||!l.classList.contains('has-pet')||RM)return;var p=l.querySelector('.sk-pet');restart(p,'is-munch');tm(function(){p.classList.remove('is-munch');},520);}
function lcdPet(lcd){var scr=lcd.closest('.sk-screen');if(!lcd.querySelector('.sk-pet')){var src=root.querySelector('.sk-lcd .sk-pet');if(src){var p=src.cloneNode(true);lcd.insertBefore(p,lcd.firstChild);lcd.classList.add('has-pet');}}
 lcd.__busy=1;setLcd(lcd,'FED','LCD pet: fed');snd('beep');tm(function(){lcd.__busy=0;lcdCount(scr);},1400);}
function receiptText(scr){var bk=bookOf(scr);var n=bk?bookCount(bk):24,shiny=bk?bk.__slots.filter(function(k){return k&&/-holo$/.test(k);}).length:1;var ba=$$('.sk-aw',scr).length;
 function dots(a,b){var w=19-a.length-String(b).length;return a+' '+new Array(Math.max(2,w)).join('.')+' '+b;}
 return ['KIRAKIRA ★ BASKET','SAT 3 OCT · HILLCREST','—',dots('STICKERS',n),dots('BASKETS',ba),dots('TIME','41 MIN'),dots('SHINY',shiny),'—','THANK YOU ♪'];}
function printReceipt(scr,lcd){if(scr.querySelector('.sk-livereceipt'))return;var lines=receiptText(scr);var r=rel(lcd,scr);var w=el('div','sk-printslot');w.style.left=(r.x-80)+'px';w.style.top=(r.t+r.h-3)+'px';
 var paper=el('div','sk-receipt sk-livereceipt');paper.setAttribute('role','button');paper.setAttribute('tabindex','0');paper.setAttribute('aria-label','Trip receipt: '+lines.join(', ')+'. Tap to save it to the sticker book');
 paper.innerHTML=lines.map(function(t,i){return t==='—'?'<i></i>':(i===0||i===lines.length-1?'<b>'+t+'</b>':'<span>'+t+'</span>');}).join('');w.appendChild(paper);scr.appendChild(w);
 lcd.__busy=1;setLcd(lcd,'PRINT','LCD printing the trip receipt');snd('print');buzz('tick');
 if(RM){w.classList.add('is-done');}else{w.classList.add('is-printing');tm(function(){w.classList.remove('is-printing');w.classList.add('is-torn');snd('tk');},1700);tm(function(){w.classList.add('is-done');},1960);}
 tm(function(){lcd.__busy=0;lcdCount(scr);},RM?600:2000);
 paper.addEventListener('click',function(ev){ev.stopPropagation();saveReceipt(scr,w);});paper.addEventListener('keydown',function(ev){if(ev.key==='Enter'||ev.key===' '){ev.preventDefault();saveReceipt(scr,w);}});}
function saveReceipt(scr,w){if(w.__saved)return;w.__saved=1;var c=rel(w.querySelector('.sk-receipt'),scr);if(!RM){w.classList.add('is-poof');kira(scr,c.x,c.y,40);burst(scr,c.x,c.y,['star4','heart','star'],8,null,'is-mini',30);}
 tm(function(){w.remove();var bk=bookOf(scr);if(!bk)return;var tk=bk.querySelector('.sk-bk-receipt');if(!tk){tk=el('span','sk-bk-receipt');tk.setAttribute('aria-hidden','true');tk.innerHTML='<i></i><i></i><i></i>';bk.appendChild(tk);}
  if(!RM){restart(tk,'is-magic');var c2=rel(tk,scr);kira(scr,c2.x,c2.y,26);snd('kira');}bk.classList.add('has-receipt');bookLabel(bk,'Receipt saved to today’s sticker book');
  var al=root.querySelector('[data-sk="tripbooks"]');if(al)al.classList.add('has-receipt');},RM?0:420);
 toast(scr,'Receipt saved to your sticker book ♡');}
/* ---------- clicks ---------- */
root.addEventListener('click',function(e){var t=e.target.closest('[data-sk]');if(!t||!root.contains(t))return;var k=t.getAttribute('data-sk');
 if(k==='fold'){var aw=t.closest('.sk-aw');if(!aw)return;var scr0=aw.closest('.sk-screen');if(aw.classList.contains('is-open'))foldAisle(scr0,aw,true);else openAisle(scr0,aw,{scroll:false,focus:true});}
 else if(k==='row'){toggleListRow(t);}
 else if(k==='skipnext'){hideNext(t);}
 else if(k==='celeinner'){closeCele(t.closest('.sk-celehost'));}
 else if(k==='checkdemo'){var r=e.target.closest('.sk-row');if(!r)return;var stage=t.closest('.sk-mstage');var bk2=stage.querySelector('[data-sk="dbasket"] .bk');
  var ctx={host:stage,basket:bk2,onLand:function(row,key,inst){relayout(bk2,['cherries'].concat(gotOrder($$('.sk-row',t))),inst?null:key);},onUnland:function(){relayout(bk2,['cherries'].concat(gotOrder($$('.sk-row',t))));}};
  if(r.classList.contains('is-checked'))uncheckRow(r,ctx);else checkRow(r,ctx);}
 else if(k==='booktile'){var r2=e.target.closest('.sk-row');if(!r2)return;bookTileToggle(t,r2);}
 else if(k==='folddemo'){var bk3=t.querySelector('.bk');if(t.classList.contains('is-shut')){t.classList.remove('is-shut');bk3.classList.remove('is-done','is-anim');}else{bk3.classList.add('is-done');if(!RM)bk3.classList.add('is-anim');later(function(){t.classList.add('is-shut');},450);}}
 else if(k==='wobtile'){var bk4=t.querySelector('.bk');var its=(t.getAttribute('data-items')||'').split(','),n4=parseInt(t.getAttribute('data-got')||'1',10),bd=t.querySelector('.sk-badge');
  if(n4>=its.length){n4=1;bk4.classList.remove('is-done','is-anim');relayout(bk4,its.slice(0,n4));}else{n4++;wob(bk4);heartPop(t,bk4);relayout(bk4,its.slice(0,n4),its[n4-1]);if(n4>=its.length){bk4.classList.add('is-done');if(!RM)bk4.classList.add('is-anim');}}
  t.setAttribute('data-got',n4);if(bd){bd.textContent=String(its.length-n4);bd.hidden=n4>=its.length;}}
 else if(k==='replay'){var ce=t.parentNode.querySelector('[data-sk="cele"]');play(ce);}
 else if(k==='cele'){t.classList.add('is-skip');}
 else if(k==='sunegg'){var sc=t.closest('.sk-screen');var lc=sc.querySelector('.sk-appbar .sk-lcd');
  if(sc.classList.contains('is-dusk')){sc.classList.remove('is-dusk');sc.__sun=0;lcdCount(sc);return;}var n=(sc.__sun||0)+1;sc.__sun=n;restart(t,'is-tap');snd('beep');
  if(n>=7){sc.classList.add('is-dusk');sc.__sun=0;later(function(){lcdCount(sc);},600);later(function(){var m=t.querySelector('.sk-moonegg');if(m){var o=rel(m,sc);burst(sc,o.x,o.y,['star4'],6);}snd('kira');},1200);}}
 else if(k==='sun'){var st2=t.closest('[data-sk="dusk"]');var n2=(parseInt(st2.getAttribute('data-n')||'0',10)+1);st2.setAttribute('data-n',n2);var o=st2.querySelector('[data-sk="suncount"]');if(n2>=7){st2.classList.add('is-dusk');o.textContent='dusk! tap the moon to undo';}else o.textContent='tap the sun · '+n2+' / 7';}
 else if(k==='roll'){var eg=t.closest('.sk-egg');var n3=(parseInt(t.getAttribute('data-n')||'0',10)+1);t.setAttribute('data-n',n3);var im=eg.querySelector('[data-sk="shinyimg"]');var out=eg.querySelector('[data-sk="rollout"]');var u=im.querySelector('use');
  if(n3%3===0){u.setAttribute('href','#sticker-i-apple-holo');im.classList.add('is-shiny');out.innerHTML='<svg width="14" height="14" aria-hidden="true"><use href="#sticker-i-star4"/></svg> SHINY! saved to album';burst(eg.querySelector('.sk-estage'),80,75);}
  else{var pool=['apple','lemon','banana','avocado'];u.setAttribute('href','#sticker-i-'+pool[n3%4]);im.classList.remove('is-shiny');out.textContent='added #'+n3+' · not shiny';}
  im.style.transform='scale(1.12) rotate(-6deg)';tm(function(){im.style.transform='';},160);}
 else if(k==='kon'){var bs=t.closest('[data-sk="bonus"]');var seq=(bs.getAttribute('data-seq')||'')+t.getAttribute('data-n');var want='11223434';if(want.indexOf(seq)!==0)seq=t.getAttribute('data-n')==='1'?'1':'';bs.setAttribute('data-seq',seq);var ko=bs.querySelector('[data-sk="konout"]');
  if(seq===want){bs.classList.add('is-bonus');ko.textContent='BONUS STAGE!';bs.setAttribute('data-seq','');}else{bs.classList.remove('is-bonus');ko.textContent=seq?seq.split('').join(' '):'1 1 2 2 3 4 3 4';}}
 else if(k==='peel'){if(t.__lp){t.__lp=0;return;}t.classList.toggle('is-lift');}
});
root.addEventListener('click',function(e){var h=e.target.closest('.sk-celehost.is-on');if(h){closeCele(h);}var m=e.target.closest('.sk-moonbtn');if(m){var st=m.closest('[data-sk="dusk"]');st.classList.remove('is-dusk');st.setAttribute('data-n','0');st.querySelector('[data-sk="suncount"]').textContent='tap the sun · 0 / 7';}});
root.addEventListener('keydown',function(e){if(e.key!==' '&&e.key!=='Enter')return;var cb=e.target.closest&&e.target.closest('.sk-cb');if(cb){var r=cb.closest('[data-sk="row"],.sk-row');if(r){e.preventDefault();r.click();}}var ic=e.target.closest&&e.target.closest('.sk-ic');if(ic){e.preventDefault();ic.classList.toggle('is-lift');}});
/* ---------- the sticker-book motion tile: tap a row → hop into the basket → +320 ms its twin appears in the mini book ---------- */
function bookTileToggle(t,row){var stage=t.closest('.sk-mstage');var bk=stage.querySelector('.bk');var mb=stage.querySelector('.sk-minibook');var base=(t.getAttribute('data-base')||'').split(',').filter(Boolean);
 var ctx={host:stage,basket:bk,onLand:function(r,key,inst){relayout(bk,base.concat(gotOrder($$('.sk-row',t))),inst?null:key);},onUnland:function(){relayout(bk,base.concat(gotOrder($$('.sk-row',t))));},
  book:function(r){var slots=$$('.sk-bk-slot',mb);var li=slots.filter(function(s){return !s.classList.contains('is-on');})[0];if(!li)return;var k=rowKey(r);r.__mslot=li;li.classList.add('is-on');li.innerHTML='<svg class="sk-bk-st" width="26" height="26" aria-hidden="true"><use href="#'+symOf(k)+'"/></svg>';
   if(RM)return;restart(li,'is-magic');var c=rel(li,stage);kira(stage,c.x,c.y);burst(stage,c.x,c.y,['star4','heart','star','star4','flower','star'],6,null,'is-mini',13);restart(mb,'is-lift');snd('kira');tm(function(){li.classList.remove('is-magic');},420);},
  unbook:function(r){var li=r.__mslot;if(!li)return;r.__mslot=null;function clr(){li.classList.remove('is-on','is-peel');li.innerHTML='';}if(RM){clr();return;}restart(li,'is-peel');var c=rel(li,stage);kira(stage,c.x,c.y,22);burst(stage,c.x,c.y,['star4','heart','star'],6,null,'is-mini is-in',13);tm(clr,330);}};
 if(row.classList.contains('is-checked'))uncheckRow(row,ctx);else checkRow(row,ctx);}
/* ---------- long-press: holo pull on the sticker sheet, blink in the album, basket peek / ladybug, LCD pet / receipt ---------- */
var lpT=null,lpT2=null,peeked=null;
function peek(aw){var fp=aw.querySelector('.sk-folded');if(!fp)return;var names=$$('.sk-opened .sk-row',aw).map(function(r){return {n:r.querySelector('.sk-nm').textContent,c:r.classList.contains('is-checked')};});
 var note=fp.querySelector('.sk-peeknote');if(!note){note=document.createElement('div');note.className='sk-peeknote';note.setAttribute('role','status');fp.appendChild(note);}
 var sc=aw.closest('.sk-scroll');if(sc){var r1=fp.getBoundingClientRect(),r2=sc.getBoundingClientRect();note.classList.toggle('is-up',r1.top-r2.top>r2.height*.45);}
 note.innerHTML='<b>'+aw.getAttribute('data-name')+'</b>'+names.map(function(x){return '<span'+(x.c?' class="is-in"':'')+'>'+x.n+'</span>';}).join('');aw.classList.add('is-peek');peeked=aw;}
function unpeek(){if(peeked){peeked.classList.remove('is-peek');peeked=null;}}
function ladyStroll(aw){if(RM)return;var fp=aw.querySelector('.sk-folded');if(!fp)return;var l=document.createElement('span');l.className='sk-ladywalk sk-ladyegg';l.setAttribute('aria-hidden','true');l.innerHTML='<svg width="15" height="15"><use href="#sticker-i-ladybug"/></svg>';fp.appendChild(l);tm(function(){l.remove();},5200);}
root.addEventListener('pointerdown',function(e){var ic=e.target.closest('.sk-ic,.sk-pocket.is-face,.sk-folded .bk,.sk-s-list .sk-appbar .sk-lcd,[data-sk="rcptlcd"]');if(!ic)return;clearTimeout(lpT);clearTimeout(lpT2);
 lpT=tm(function(){if(ic.classList.contains('sk-ic')){ic.classList.toggle('is-holo');ic.__lp=1;}
  else if(ic.classList.contains('sk-pocket')){restart(ic,'is-blink');}
  else if(ic.classList.contains('bk')){var aw=ic.closest('.sk-aw');var fb=ic.closest('.sk-fold');if(fb)fb.__lp=1;peek(aw);lpT2=tm(function(){ladyStroll(aw);},560);}
  else if(ic.getAttribute('data-sk')==='rcptlcd'){receiptTile(ic);}
  else{var scr=ic.closest('.sk-screen');if(scr&&scr.__tripDone)printReceipt(scr,ic);else lcdPet(ic);}},440);});
['pointerup','pointerleave','pointercancel'].forEach(function(ev){root.addEventListener(ev,function(){clearTimeout(lpT);clearTimeout(lpT2);unpeek();},true);});
root.addEventListener('click',function(e){var fb=e.target.closest('.sk-fold');if(fb&&fb.__lp){fb.__lp=0;e.stopImmediatePropagation();e.preventDefault();}},true);
function receiptTile(lcd){var st=lcd.closest('.sk-estage');if(!st||st.querySelector('.sk-printslot'))return;var w=el('div','sk-printslot');var r=rel(lcd,st);w.style.left=(r.x-80)+'px';w.style.top=(r.t+r.h-3)+'px';
 var lines=['KIRAKIRA ★ BASKET','SAT 3 OCT · HILLCREST','—','STICKERS ...... 24','BASKETS ........ 8','TIME ...... 41 MIN','SHINY .......... 1','—','THANK YOU ♪'];
 var paper=el('div','sk-receipt sk-livereceipt',lines.map(function(t,i){return t==='—'?'<i></i>':(i===0||i===lines.length-1?'<b>'+t+'</b>':'<span>'+t+'</span>');}).join(''));paper.setAttribute('role','button');paper.setAttribute('tabindex','0');paper.setAttribute('aria-label','Trip receipt. Tap to save it to the sticker book');
 w.appendChild(paper);st.appendChild(w);setLcd(lcd,'PRINT');snd('print');var out=st.querySelector('[data-sk="rcptout"]');if(out)out.textContent='printing…';
 if(RM)w.classList.add('is-done');else{w.classList.add('is-printing');tm(function(){w.classList.remove('is-printing');w.classList.add('is-torn');},1700);tm(function(){w.classList.add('is-done');if(out)out.textContent='tap the receipt to save it';},1960);}
 tm(function(){setLcd(lcd,'24/24');},2000);
 paper.addEventListener('click',function(ev){ev.stopPropagation();if(w.__saved)return;w.__saved=1;var c=rel(paper,st);if(!RM){w.classList.add('is-poof');kira(st,c.x,c.y,40);burst(st,c.x,c.y,['star4','heart','star'],8,null,'is-mini',30);}
  tm(function(){w.remove();var mb=st.querySelector('.sk-minibook');if(mb){mb.classList.add('has-receipt');var tk=mb.querySelector('.sk-bk-receipt');if(tk&&!RM){restart(tk,'is-magic');var c2=rel(tk,st);kira(st,c2.x,c2.y,26);}}if(out)out.textContent='saved to the sticker book ♡ · hold the LCD again';},RM?0:420);});}
/* phones keep a 360 dp screen and scale as a whole on narrow viewports */
function fitPhones(){var wr=root.querySelector('.sk-wrap');if(!wr)return;var cs=getComputedStyle(wr);var avail=wr.clientWidth-parseFloat(cs.paddingLeft)-parseFloat(cs.paddingRight);var z=Math.min(1,avail/392);
 $$('.sk-phone').forEach(function(p){if(z<1)p.style.setProperty('--skz',z.toFixed(4));else p.style.removeProperty('--skz');});}
fitPhones();window.addEventListener('resize',fitPhones);
/*@LIVE@*/
/* ---------- init the live lists: rows already checked are in their baskets and in the book ---------- */
$$('.sk-s-list').forEach(function(scr){$$('.sk-aw .sk-opened .sk-row.is-checked',scr).forEach(function(r){r.classList.add('is-inbk');});bookInit(scr);var bk=bookOf(scr);if(bk){bookNext(bk);bookLabel(bk);}});
/* ---------- celebration: play when seen, replay every 14 s while visible ---------- */
function play(ce){if(!ce)return;ce.classList.remove('is-play','is-skip');void ce.offsetWidth;ce.classList.add('is-play');}
var cele=root.querySelector('.sk-celewrap [data-sk="cele"]');
if(cele&&'IntersectionObserver' in window){var iv=null;function loop(){clearTimeout(iv);iv=tm(function(){play(cele);loop();},15000);}new IntersectionObserver(function(en){en.forEach(function(x){if(x.isIntersecting){play(cele);if(!RM)loop();}else{clearTimeout(iv);iv=null;}});},{threshold:.4}).observe(cele);
 root.addEventListener('click',function(e){if(e.target.closest('.sk-celewrap .sk-replay')&&!RM)loop();});}
else if(cele)cele.classList.add('is-play');
/* ---------- album: every 6 s a '?' pocket wobbles and a kira star visits a holo pocket ---------- */
if(!RM)$$('.sk-s-album').forEach(function(scr){var vis=false;if('IntersectionObserver' in window)new IntersectionObserver(function(en){vis=en[0].isIntersecting;},{threshold:.1}).observe(scr);else vis=true;
 setInterval(function(){if(!vis)return;var q=$$('.sk-pocket.is-empty',scr);if(q.length){var p=q[Math.floor(Math.random()*q.length)];restart(p,'is-wob');}
  var h=$$('.sk-pocket.is-holo',scr);if(h.length){var o=h[Math.floor(Math.random()*h.length)];var s=svgUse('sticker-i-kira',18,18,'sk-pkira');o.appendChild(s);tm(function(){s.remove();},1200);}},6000);});
/* ---------- ambient loops in the list phones (12 fps poses; text never moves) ---------- */
if(!RM)$$('.sk-s-list').forEach(function(scr){var vis=false;
 if('IntersectionObserver' in window)new IntersectionObserver(function(en){vis=en[0].isIntersecting;},{threshold:.1}).observe(scr);else vis=true;
 setInterval(function(){if(!vis)return;var bks=$$('.sk-aw:not(.is-open) .sk-folded .bk:not(.is-done)',scr);if(!bks.length)return;var b=bks[Math.floor(Math.random()*bks.length)];wob(b);var ms=b.querySelectorAll('.bk-minis .bk-in');if(ms.length){var m=ms[Math.floor(Math.random()*ms.length)];restart(m,'is-peek');tm(function(){m.classList.remove('is-peek');},700);}},5000);
 setInterval(function(){if(!vis)return;var sc=scr.querySelector('.sk-scroll');var top=sc.getBoundingClientRect().top,bot=top+sc.clientHeight-90;var ics=$$('.sk-row:not(.is-checked) .sk-ico:not(.is-shiny)',scr).filter(function(i){var r=i.getBoundingClientRect();return r.top>top&&r.bottom<bot;});if(!ics.length)return;var ic=ics[Math.floor(Math.random()*ics.length)];restart(ic,'is-glint');tm(function(){ic.classList.remove('is-glint');},1000);},7000);
});
})();
