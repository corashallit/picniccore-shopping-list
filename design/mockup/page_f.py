import math
from common import *
from page_b import AISLES, row, tab, open_page, fold_page, stampstrip, maru_svg, pixbee, waiting_inside, TOTAL, nextcard, eq, cassette, appbar
from page_d import fab_svg
from eggs import eggs as eggs_r9

MOTION_CSS = r"""
@keyframes sticker-twinkle{0%{transform:scale(.82) rotate(-8deg)}33%{transform:scale(1.12) rotate(6deg)}66%{transform:scale(.95) rotate(0)}100%{transform:scale(.82) rotate(-8deg)}}
@keyframes sticker-bob{0%,100%{transform:translateY(0)}50%{transform:translateY(-4px)}}
@keyframes sticker-sway{0%,100%{transform:rotate(-7deg)}50%{transform:rotate(7deg)}}
@keyframes sticker-wobble{0%{transform:rotate(0)}33%{transform:rotate(-8deg)}66%{transform:rotate(8deg)}100%{transform:rotate(0)}}
@keyframes sticker-glint{0%,35%{left:-60px}75%,100%{left:110%}}
@keyframes sticker-spin{to{transform:rotate(360deg)}}
@keyframes sticker-scan{0%{left:-40%}100%{left:130%}}
@keyframes sticker-scanline{0%,100%{top:4%}50%{top:92%}}
@keyframes sticker-caret{to{visibility:hidden}}
@keyframes sticker-reel{to{transform:rotate(360deg)}}
@keyframes sticker-eq3{0%,33.2%{opacity:1}33.3%,100%{opacity:.12}}
@keyframes sticker-eq4{0%,33.2%{opacity:.12}33.3%,66.5%{opacity:1}66.6%,100%{opacity:.12}}
@keyframes sticker-eq5{0%,66.5%{opacity:.12}66.6%,100%{opacity:1}}
@keyframes sticker-blink{to{visibility:hidden}}
@keyframes sticker-popin{0%,30%{opacity:.25;transform:scale(.9)}34%{opacity:1;transform:scale(1.06)}38%,100%{opacity:1;transform:scale(1)}}
@keyframes sticker-slam{0%{transform:scale(1.4)}50%{transform:scale(1.15)}100%{transform:scale(1)}}
@keyframes sticker-unfold{from{opacity:.2;transform:scaleY(.6);transform-origin:50% 0}to{opacity:1;transform:none}}
@keyframes sticker-burst{0%{transform:translate(0,0) scale(.4);opacity:1}70%{opacity:1}100%{transform:translate(var(--dx),var(--dy)) scale(1) rotate(var(--r,90deg));opacity:0}}
@keyframes sticker-hop{0%{transform:translate(0,0) rotate(0)}20%{transform:translate(0,-4px) rotate(-14deg) skewX(-6deg)}55%{transform:translate(calc(var(--hx)*.5),calc(var(--hy)*.5 - 46px)) rotate(-6deg) scale(.8)}100%{transform:translate(var(--hx),var(--hy)) rotate(0) scale(.5)}}
@keyframes sticker-squash{0%,100%{transform:none}40%{transform:scale(1.12,.86)}70%{transform:scale(.96,1.05)}}
@keyframes sticker-bwob{0%,100%{transform:rotate(0)}25%{transform:rotate(-6deg)}50%{transform:rotate(5deg)}75%{transform:rotate(-2deg)}}
@keyframes sticker-peelhop{0%,18%{transform:translate(0,0) rotate(0) scale(1)}24%{transform:translate(0,-3px) rotate(-10deg) skewX(-8deg)}30%{transform:translate(0,-6px) rotate(-14deg) skewX(-10deg)}52%{transform:translate(52px,-58px) rotate(8deg) scale(.82)}70%{transform:translate(104px,14px) rotate(0) scale(.62)}100%{transform:translate(104px,14px) rotate(0) scale(.62)}}
@keyframes sticker-peelfade{0%,68%{opacity:1}72%,100%{opacity:0}}
@keyframes sticker-bwob2{0%,68%{transform:rotate(0)}74%{transform:rotate(-7deg)}80%{transform:rotate(6deg)}86%{transform:rotate(-3deg)}92%,100%{transform:rotate(0)}}
@keyframes sticker-minishow{0%,69%{opacity:0;transform:translateY(8px)}72%{opacity:1;transform:translateY(-4px)}76%,96%{opacity:1;transform:none}100%{opacity:0}}
@keyframes sticker-sheet{0%,3%{transform:translateY(105%)}14%{transform:translateY(-4%)}18%,88%{transform:translateY(0)}97%,100%{transform:translateY(105%)}}
@keyframes sticker-tapeslap{0%,16%{transform:rotate(-3deg) scale(1.6);opacity:0}19%{transform:rotate(-3deg) scale(1.2);opacity:1}22%,88%{transform:rotate(-3deg) scale(1);opacity:1}97%,100%{opacity:0}}
@keyframes sticker-gacha{0%,8%{transform:rotate(0)}12%{transform:rotate(-8deg)}16%{transform:rotate(8deg)}20%{transform:rotate(0)}24%{transform:rotate(-8deg)}28%{transform:rotate(8deg)}32%,100%{transform:rotate(0)}}
@keyframes sticker-gachatop{0%,34%{transform:translateY(0) rotate(0)}40%,88%{transform:translateY(-26px) rotate(-24deg)}96%,100%{transform:translateY(0) rotate(0)}}
@keyframes sticker-fanout{0%,38%{opacity:0;transform:translate(0,20px) scale(.2)}46%,86%{opacity:1;transform:translate(var(--fx),var(--fy)) scale(1) rotate(var(--fr))}94%,100%{opacity:0;transform:translate(0,20px) scale(.2)}}
@keyframes sticker-path{to{offset-distance:100%}}
@keyframes sticker-toast{0%{transform:translateY(-30px) rotate(-9deg);opacity:0}6%{transform:translateY(4px) rotate(-1deg);opacity:1}10%,86%{transform:translateY(0) rotate(-2deg);opacity:1}96%,100%{transform:translateY(-10px) rotate(-4deg);opacity:0}}
@keyframes sticker-pull{0%,10%{transform:translateY(-34px)}40%{transform:translateY(10px)}50%,80%{transform:translateY(0)}100%{transform:translateY(-34px)}}
@keyframes sticker-pullsun{0%,40%{transform:rotate(0)}80%{transform:rotate(360deg)}100%{transform:rotate(360deg)}}
@keyframes sticker-thunk{0%,30%{transform:scale(1.6) rotate(-30deg);opacity:0}34%{transform:scale(1.3) rotate(-14deg);opacity:1}38%,90%{transform:scale(1) rotate(-10deg);opacity:1}100%{opacity:0}}
@keyframes sticker-lcda{0%,33%{opacity:1}34%,100%{opacity:0}}
@keyframes sticker-lcdb{0%,33%{opacity:0}34%,100%{opacity:1}}
@keyframes sticker-curl{0%,6%{transform:perspective(500px) rotateY(0);opacity:1}24%{transform:perspective(500px) rotateY(-80deg);opacity:.6}25%,100%{transform:perspective(500px) rotateY(-90deg);opacity:0}}
@keyframes sticker-prize{0%,14%{transform:translate(-50%,120%)}28%{transform:translate(-50%,-56%)}32%,94%{transform:translate(-50%,-50%)}100%{transform:translate(-50%,120%)}}
@keyframes sticker-fold{0%,20%{height:112px}40%,80%{height:0}100%{height:112px}}
@keyframes sticker-lid{0%,30%{transform:scaleY(0)}40%,80%{transform:scaleY(1)}90%,100%{transform:scaleY(0)}}
@keyframes sticker-roll{0%{transform:translateX(-60px) rotate(-360deg)}60%{transform:translateX(150px) rotate(0)}66%{transform:translateX(150px) translateY(-6px)}72%,100%{transform:translateX(150px) translateY(0)}}
@keyframes sticker-fall{0%{transform:translate(0,-30px) rotate(0);opacity:0}10%{opacity:1}100%{transform:translate(var(--fx,20px),170px) rotate(var(--fr,200deg));opacity:.9}}
@keyframes sticker-breathe{0%,100%{transform:scale(1)}50%{transform:scale(1.04,1.07)}}
@keyframes sticker-zz{0%{transform:translate(0,0) scale(.6);opacity:0}30%{opacity:1}100%{transform:translate(14px,-30px) scale(1.1);opacity:0}}
@keyframes sticker-holo{0%{background-position:0% 50%}100%{background-position:200% 50%}}
@keyframes sticker-rays{to{transform:translate(-50%,-50%) rotate(360deg)}}
@keyframes sticker-c-in{from{opacity:0;transform:scale(.2) rotate(-40deg)}}
@keyframes sticker-c-blanket{0%{transform:scaleY(.04);opacity:0}25%{transform:scaleY(.3);opacity:1}50%{transform:scaleY(.62)}75%{transform:scaleY(.86)}100%{transform:scaleY(1);opacity:1}}
@keyframes sticker-c-ribbon{from{stroke-dashoffset:900}to{stroke-dashoffset:0}}
@keyframes sticker-c-slam{0%{transform:scale(2.2) rotate(-30deg);opacity:0}50%{transform:scale(1.25) rotate(-14deg);opacity:1}100%{transform:scale(1) rotate(-12deg);opacity:1}}
@keyframes sticker-c-pop{0%{transform:translateY(30px) scale(.2);opacity:0}60%{transform:translateY(-6px) scale(1.1);opacity:1}100%{transform:none;opacity:1}}
@keyframes sticker-c-fade{from{opacity:0}}
@keyframes sticker-c-speed{from{transform:translate(-50%,-50%) rotate(0) scale(.6);opacity:0}30%{opacity:.9}to{transform:translate(-50%,-50%) rotate(40deg) scale(1.4);opacity:0}}
@keyframes sticker-c-title{0%{transform:translateY(-80px) scale(.6);opacity:0}60%{transform:translateY(6px) scale(1.06);opacity:1}100%{transform:none;opacity:1}}
@keyframes sticker-dusk{0%,40%{transform:translateY(0)}70%,100%{transform:translateY(34px)}}
@keyframes sticker-swipe{0%,12%{transform:translateX(0)}30%,100%{transform:translateX(-46px)}}
@keyframes sticker-tear{0%,34%{transform:translate(0,0) rotate(0);opacity:1}44%{transform:translate(4px,6px) rotate(6deg);opacity:1}70%,100%{transform:translate(30px,90px) rotate(24deg);opacity:0}}
@keyframes sticker-tearl{0%,34%{transform:none;opacity:1}44%{transform:translate(-4px,2px) rotate(-3deg)}70%,100%{transform:translate(-40px,80px) rotate(-18deg);opacity:0}}
@keyframes sticker-pix{0%,100%{transform:translateY(0)}50%{transform:translateY(-6px)}}
@keyframes sticker-orbit{to{transform:rotate(360deg)}}
@keyframes sticker-suntap{0%{transform:scale(1.25) rotate(-20deg)}50%{transform:scale(1.1) rotate(10deg)}100%{transform:none}}
@keyframes sticker-tick{0%{transform:translateY(-2px)}50%{transform:translateY(1px)}100%{transform:none}}
@keyframes sticker-lblink{0%,91%{opacity:1}92%,95%{opacity:.12}96%,100%{opacity:1}}
@keyframes sticker-lidshut{0%{transform:scaleY(.2);opacity:1}50%{transform:scaleY(.7)}100%{transform:scaleY(1)}}
@keyframes sticker-hkslam{0%{transform:scale(1.6) rotate(-14deg);opacity:0}50%{transform:scale(1.2);opacity:1}100%{transform:none;opacity:1}}
@keyframes sticker-bhop{0%,100%{transform:none}33%{transform:translateY(-5px) scale(1.04,.96)}66%{transform:translateY(-2px)}}
@keyframes sticker-peek{0%,100%{transform:none}50%{transform:translateY(-3px)}}
@keyframes sticker-rimheart{0%{transform:translateY(0) scale(.4);opacity:1}50%{transform:translateY(-14px) scale(1);opacity:1}100%{transform:translateY(-24px) scale(.9);opacity:0}}
@keyframes sticker-thunk1{0%{transform:scale(1.6) rotate(-30deg);opacity:0}50%{transform:scale(1.25) rotate(-16deg);opacity:1}100%{transform:rotate(-10deg);opacity:1}}
@keyframes sticker-ladywalk{0%{transform:translateY(0)}8%{transform:translateY(22px)}16%{transform:translateY(44px)}24%{transform:translateY(66px)}32%{transform:translateY(88px)}40%{transform:translateY(110px)}48%,60%{transform:translateY(130px)}61%{transform:translateY(130px);opacity:0}99%{transform:translateY(0);opacity:0}100%{transform:translateY(0);opacity:1}}
@keyframes sticker-waddle{0%{transform:rotate(175deg)}50%{transform:rotate(185deg)}}
@keyframes sticker-flutter{0%,88%{transform:rotate(-8deg)}90%{transform:rotate(-8deg) skewX(10deg)}94%{transform:rotate(-7deg) skewX(-6deg)}98%,100%{transform:rotate(-8deg)}}
@keyframes sticker-nextup{0%{transform:translateY(calc(100% + 120px))}22%{transform:translateY(-8px)}30%,82%{transform:none}100%{transform:translateY(calc(100% + 120px))}}
@keyframes sticker-charge{0%,79%{opacity:.85;transform:none}80%,89%{opacity:1;transform:scale(1.15)}90%,100%{opacity:.85;transform:none}}
@keyframes sticker-drag{0%,8%{transform:translate(6px,-2px) rotate(-2.4deg)}50%,58%{transform:translate(6px,-17px) rotate(2deg)}100%{transform:translate(6px,-2px) rotate(-2.4deg)}}
@keyframes sticker-shift{0%,8%{transform:none}50%,58%{transform:translateY(-4px)}100%{transform:none}}
@keyframes sticker-shift2{0%,8%{transform:translateY(4px)}50%,58%{transform:none}100%{transform:translateY(4px)}}
@keyframes sticker-ok1{0%,33%{opacity:1}34%,100%{opacity:0}}
@keyframes sticker-ok2{0%,33%{opacity:0}34%,66%{opacity:1}67%,100%{opacity:0}}
@keyframes sticker-ok3{0%,66%{opacity:0}67%,100%{opacity:1}}
@keyframes sticker-blink2{0%{visibility:visible}50%{visibility:hidden}51%{visibility:visible}100%{visibility:hidden}}
@keyframes sticker-curl2{0%{width:5px;height:5px}50%{width:9px;height:9px}100%{width:13px;height:13px}}
@keyframes sticker-hopshiver{0%{transform:translate(0,0) rotate(-1deg)}8%{transform:translate(0,0) rotate(1deg)}16%{transform:translate(0,-4px) rotate(-1deg)}55%{transform:translate(calc(var(--hx)*.5),calc(var(--hy)*.5 - 46px)) rotate(1deg) scale(.8)}100%{transform:translate(var(--hx),var(--hy)) scale(.45)}}
@keyframes sticker-hopegg{0%{transform:translate(0,0)}20%{transform:translate(0,-4px) rotate(-12deg)}55%{transform:translate(calc(var(--hx)*.5),calc(var(--hy)*.5 - 46px)) rotate(-6deg) scale(.8)}75%{transform:translate(calc(var(--hx)*.9),calc(var(--hy)*.9 - 10px)) rotate(10deg) scale(.6)}85%{transform:translate(calc(var(--hx)*.95),calc(var(--hy)*.95 - 6px)) rotate(-10deg) scale(.55)}100%{transform:translate(var(--hx),var(--hy)) scale(.45)}}
@keyframes sticker-hoppuff{0%{transform:translate(0,0)}12%{transform:translate(0,-2px) scale(1.05)}20%{transform:translate(0,-4px) rotate(-10deg)}55%{transform:translate(calc(var(--hx)*.5),calc(var(--hy)*.5 - 46px)) scale(.8)}100%{transform:translate(var(--hx),var(--hy)) scale(.45)}}
@keyframes sticker-hoproll{0%{transform:translate(0,0)}20%{transform:translate(0,-4px) rotate(-10deg)}55%{transform:translate(calc(var(--hx)*.5),calc(var(--hy)*.5 - 46px)) scale(.8)}80%{transform:translate(calc(var(--hx) - 10px),var(--hy)) rotate(0) scale(.5)}100%{transform:translate(var(--hx),var(--hy)) rotate(120deg) scale(.45)}}
@keyframes sticker-hopberry{0%{transform:translate(0,0)}20%{transform:translate(0,-4px) rotate(-10deg)}50%{transform:translate(calc(var(--hx)*.5),calc(var(--hy)*.5 - 46px)) scale(.8)}70%{transform:translate(var(--hx),var(--hy)) scale(.5)}80%{transform:translate(var(--hx),calc(var(--hy) - 10px)) scale(.5)}90%{transform:translate(var(--hx),var(--hy)) scale(.5)}95%{transform:translate(var(--hx),calc(var(--hy) - 4px)) scale(.48)}100%{transform:translate(var(--hx),var(--hy)) scale(.45)}}
@keyframes sticker-estroll{0%{transform:translate(0,0)}6%{transform:translate(28px,0)}12%{transform:translate(56px,0)}18%{transform:translate(84px,0)}24%{transform:translate(112px,0)}30%,48%{transform:translate(140px,0)}54%{transform:translate(168px,0)}60%{transform:translate(196px,0);opacity:1}61%,100%{transform:translate(196px,0);opacity:0}}
@keyframes sticker-efly{0%,60%{opacity:0;transform:none}61%{opacity:1;transform:translate(0,0)}70%{transform:translate(18px,-26px) rotate(14deg)}80%{transform:translate(40px,-60px) rotate(20deg)}90%{transform:translate(70px,-100px) rotate(24deg);opacity:1}91%,100%{opacity:0}}
@keyframes sticker-dance{0%{transform:translateY(0) rotate(0)}25%{transform:translateY(-3px) rotate(-12deg)}50%{transform:translateY(0) rotate(0)}75%{transform:translateY(-3px) rotate(12deg)}}
@keyframes sticker-c-fadein{from{opacity:0}to{opacity:1}}
@keyframes sticker-c-empty{from{opacity:1}to{opacity:0}}
@keyframes sticker-c-hop{0%{opacity:1;transform:translate(0,0) rotate(0)}15%{opacity:1;transform:translate(0,-6px) rotate(-14deg) skewX(-8deg)}55%{transform:translate(calc(var(--dx)*.5),calc(var(--dy)*.5 - 70px)) rotate(8deg) scale(1.1)}86%{transform:translate(var(--dx),var(--dy)) scale(1.18,.8)}94%{opacity:1;transform:translate(var(--dx),var(--dy)) scale(.9,1.08)}100%{opacity:0;transform:translate(var(--dx),calc(var(--dy) + 8px)) scale(.8)}}
@keyframes sticker-c-drop{0%{transform:translateY(-320px);opacity:0}10%{opacity:1}70%{transform:translateY(8px)}100%{transform:none;opacity:1}}
@keyframes sticker-c-flip{from{transform:rotateY(0)}to{transform:rotateY(180deg)}}
@keyframes sticker-c-conf{0%{transform:translate(0,0) rotate(0);opacity:1}100%{transform:translate(var(--fx,10px),720px) rotate(var(--r,200deg));opacity:1}}
@keyframes sticker-c-gpop{0%{transform:scale(1)}50%{transform:scale(1.2,.85)}100%{transform:scale(1)}}
@keyframes sticker-c-bonus{0%{transform:translateY(60px) scale(.2);opacity:0}60%{transform:translateY(-6px) scale(1.06);opacity:1}100%{transform:none;opacity:1}}
@keyframes sticker-c-skip{0%,99%{opacity:1}100%{opacity:0}}

§ .sk-twinkle{animation:sticker-twinkle .375s steps(3,end) infinite;transform-origin:50% 50%}
§ .sk-twinkle.sk-d2{animation-delay:-.125s}
§ .sk-twinkle.sk-d3{animation-delay:-.25s}
§ .sk-bob{animation:sticker-bob 2.4s ease-in-out infinite}
§ .sk-sway{animation:sticker-sway 3.2s ease-in-out infinite;transform-origin:50% 90%}
§ .sk-wobble{animation:sticker-wobble .75s steps(3,end) infinite;transform-origin:50% 80%}
§ .sk-beeloop{position:absolute;left:0;top:0;offset-path:path('M38 30C58 6 74 18 62 32C50 46 26 50 14 34C2 18 18 6 38 30Z');offset-rotate:0deg;animation:sticker-path 2.4s linear infinite}
§ .sk-beeloop svg{display:block;transform:translate(-50%,-50%)}

/* motion tiles */
§ .sk-m-pad{position:absolute;left:14px;right:14px;top:16px;background:#fff;border-radius:6px;box-shadow:var(--sk-hs);background-image:linear-gradient(90deg,transparent 22px,var(--sk-margin) 22px 24px,transparent 24px)}
§ .sk-m-pad .sk-rows{padding:0 4px 0 28px}
§ .sk-hopper{position:absolute;width:52px;height:52px;z-index:5;pointer-events:none;animation:sticker-hop .34s cubic-bezier(.3,.9,.5,1) forwards}
§ .sk-burstp{position:absolute;width:18px;height:18px;margin:-9px 0 0 -9px;z-index:6;pointer-events:none;animation:sticker-burst .5s steps(5,end) forwards}
§ .sk-bkwob{animation:sticker-bwob .36s steps(4,end)}
§ .sk-m-peel{position:absolute;left:30px;top:70px;width:60px;height:60px;animation:sticker-peelhop 2.8s cubic-bezier(.3,.9,.5,1) -1.2s infinite,sticker-peelfade 2.8s steps(1,end) -1.2s infinite;transform-origin:20% 90%}
§ .sk-m-bk{position:absolute;left:118px;top:70px;transform-origin:50% 90%;animation:sticker-bwob2 2.8s linear -1.2s infinite}
§ .sk-m-bkmini{animation:sticker-minishow 2.8s steps(1,end) -1.2s infinite}
§ .sk-m-sheet{position:absolute;left:24px;right:24px;bottom:0;height:134px;background:#fff;border-radius:16px 16px 0 0;box-shadow:0 -2px 0 var(--sk-shadow);animation:sticker-sheet 3.2s cubic-bezier(.2,.9,.3,1.1) infinite;background-image:linear-gradient(90deg,transparent 18px,var(--sk-margin) 18px 20px,transparent 20px),repeating-linear-gradient(180deg,transparent 0 21px,var(--sk-rule) 21px 22px);background-position:0 0,0 40px}
§ .sk-m-sheet .sk-handle{animation:sticker-tapeslap 3.2s steps(1,end) infinite}
§ .sk-m-gacha{position:absolute;left:50%;top:50%;width:84px;height:84px;margin:-42px 0 0 -42px}
§ .sk-m-gacha .sk-gbody{animation:sticker-gacha 2.4s linear -.7s infinite;transform-origin:50% 85%}
§ .sk-m-gacha .sk-gtop{animation:sticker-gachatop 2.4s cubic-bezier(.3,1.4,.5,1) infinite;transform-box:fill-box;transform-origin:10% 100%}
§ .sk-fanned{position:absolute;left:50%;top:50%;width:34px;height:34px;margin:-17px 0 0 -17px;transform:translate(var(--fx),var(--fy)) rotate(var(--fr));animation:sticker-fanout 2.4s cubic-bezier(.3,1.4,.5,1) infinite}
§ .sk-m-ring{position:absolute;left:50%;top:50%;width:110px;height:110px;margin:-55px 0 0 -55px;border-radius:50%;border:3px dashed var(--sk-periwinkle-m)}
§ .sk-m-lady{position:absolute;left:0;top:0;width:100%;height:100%;offset-path:circle(55px at 50% 50%);offset-rotate:auto 90deg;animation:sticker-path 3s linear infinite}
§ .sk-m-ladyw{position:absolute;left:50%;top:50%;width:0;height:0;animation:sticker-orbit 3s linear infinite}
§ .sk-m-ladyw>span{position:absolute;left:55px;top:0;transform:translate(-50%,-50%) rotate(180deg)}
§ .sk-m-lady svg{display:block;transform:translate(-50%,-50%)}
§ .sk-m-toast{position:absolute;left:50%;top:26px;margin-left:-86px;width:172px;height:110px;animation:sticker-toast 3.6s cubic-bezier(.3,1.3,.5,1) infinite}
§ .sk-m-toast .sk-toast{width:172px;min-height:110px;transform:none;font-size:13.5px;padding:20px 12px 10px}
§ .sk-m-pull{position:absolute;left:0;right:0;top:0;height:100%;animation:sticker-pull 2.8s cubic-bezier(.3,1.3,.5,1) infinite}
§ .sk-m-pullsun{animation:sticker-pullsun 2.8s cubic-bezier(.3,.9,.4,1) infinite;transform-box:fill-box;transform-origin:50% 50%}
§ .sk-m-thunk{position:absolute;animation:sticker-thunk 2.6s steps(1,end) -.4s infinite}
§ .sk-lcd .sk-la{opacity:0;animation:sticker-lcda 2.6s steps(1,end) infinite}
§ .sk-lcd .sk-lb{position:absolute;left:8px;top:7px;animation:sticker-lcdb 2.6s steps(1,end) infinite}
§ .sk-m-curl{position:absolute;left:40px;top:18px;width:110px;height:148px;background:#fff;border-radius:6px;box-shadow:var(--sk-hs);transform-origin:0 50%;animation:sticker-curl 3s ease-in infinite;background-image:repeating-linear-gradient(180deg,transparent 0 17px,var(--sk-rule) 17px 18px)}
§ .sk-m-prize{position:absolute;left:62%;top:50%;transform:translate(-50%,-50%);width:98px;height:136px;border-radius:12px;border:3px solid #fff;box-shadow:var(--sk-hs);background:var(--sk-holo);animation:sticker-prize 3s cubic-bezier(.3,1.3,.5,1) infinite;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:4px;overflow:hidden}
§ .sk-m-prize::after{content:"";position:absolute;top:-20px;bottom:-20px;width:20px;left:-40px;background:rgba(255,255,255,.4);transform:rotate(30deg);animation:sticker-glint 1.5s ease-in-out infinite}
§ .sk-m-holo{position:absolute;left:50%;top:50%;width:150px;height:96px;margin:-48px 0 0 -75px;border-radius:12px;border:3px solid #fff;box-shadow:var(--sk-hs2);background:var(--sk-holo);overflow:hidden;display:grid;place-items:center}
§ .sk-m-holo::after{content:"";position:absolute;top:-30px;bottom:-30px;width:30px;left:-60px;background:rgba(255,255,255,.35);transform:rotate(30deg);animation:sticker-glint 2.4s ease-in-out infinite}
§ .sk-m-fold{position:absolute;left:14px;right:14px;top:14px;background:#fff;border-radius:6px;box-shadow:var(--sk-hs)}
§ .sk-m-fold .sk-tabrow{position:relative;top:0;margin:0;height:52px;padding:6px 4px 0 6px;align-items:center}
§ .sk-m-fold .sk-mrows{overflow:hidden;height:112px;transition:height .24s cubic-bezier(.2,.9,.3,1)}
§ .sk-m-fold.is-shut .sk-mrows{height:0}
§ .sk-m-fold .bk-lidclosed{transform-box:fill-box;transform-origin:50% 100%;transform:scaleY(0);transition:transform .16s steps(2,end) .1s}
§ .sk-m-fold.is-shut .bk-lidclosed{transform:scaleY(1)}
§ .sk-m-fold .bk-lidopen{transition:opacity .1s steps(1,end)}
§ .sk-m-fold.is-shut .bk-lidopen{opacity:0}
§ .sk-m-fold .sk-chev>span{transition:transform .2s}
§ .sk-m-fold.is-shut .sk-chev>span{transform:rotate(180deg)}

§ .sk-m-tear{position:absolute;left:18px;right:18px;top:62px;height:60px;animation:sticker-swipe 3.2s cubic-bezier(.3,.9,.4,1) infinite}
§ .sk-m-tear>div{position:absolute;top:0;bottom:0;background:#fff;box-shadow:var(--sk-hs);display:flex;align-items:center;gap:8px}
§ .sk-m-tear .sk-tl{left:0;width:62%;border-radius:6px 0 0 6px;padding-left:10px;animation:sticker-tearl 3.2s cubic-bezier(.5,0,.7,1) infinite;transform-origin:0 100%}
§ .sk-m-tear .sk-tr{left:62%;right:0;border-radius:0 6px 6px 0;justify-content:center;animation:sticker-tear 3.2s cubic-bezier(.5,0,.7,1) infinite;transform-origin:0 100%}
§ .sk-m-tear .sk-tr::before{content:"";position:absolute;left:-1px;top:0;bottom:0;width:2px;background:radial-gradient(circle,var(--sk-bp2) 1px,transparent 1.3px) 0 0/2px 6px repeat-y}
§ .sk-cele{position:absolute;inset:0;overflow:hidden;background:var(--sk-binder)}
§ .sk-cele>*{position:absolute}
/* eggs */
§ .sk-e-rollb{position:absolute;left:0;top:76px;animation:sticker-roll 3s cubic-bezier(.5,0,.6,1) infinite}
§ .sk-petal{position:absolute;top:0;width:14px;height:10px;border-radius:12px 2px 12px 2px;background:var(--sk-sakura-m);border:1.5px solid #fff;box-shadow:1px 1.4px 0 var(--sk-shadow);animation:sticker-fall 3.2s linear infinite}
§ .sk-conf{position:absolute;top:0;width:9px;height:9px;border-radius:2px;border:1.5px solid #fff;animation:sticker-fall 2.6s linear infinite}
§ .sk-breathe{animation:sticker-breathe 2.6s ease-in-out infinite;transform-origin:50% 100%}
§ .sk-zz{position:absolute;font:700 15px/1 var(--sk-f-puff);color:var(--sk-periwinkle-t);-webkit-text-stroke:4px #fff;paint-order:stroke fill;animation:sticker-zz 2.4s ease-out infinite}
§ .sk-holotext{background:linear-gradient(90deg,#FF6F8E,#7CC6F5,#6DD3B0,#FFDA4A,#B597F2,#FF6F8E);background-size:200% 100%;-webkit-background-clip:text;background-clip:text;color:transparent;animation:sticker-holo 2.4s linear infinite}
§ .sk-estage.is-dusk{background-color:#E9DDF7;background-image:repeating-linear-gradient(90deg,#DCCBF2 0 2px,transparent 2px 10px)}
§ .sk-estage .sk-sunbtn{position:absolute;right:18px;top:16px;width:56px;height:56px;display:grid;place-items:center;transition:transform .5s cubic-bezier(.3,1.4,.5,1),opacity .4s .2s}
§ .sk-estage.is-dusk .sk-sunbtn{transform:translateY(60px) scale(.5);opacity:0;pointer-events:none}
§ .sk-estage .sk-moonbtn{position:absolute;right:26px;top:20px;opacity:0;transition:opacity .4s .2s}
§ .sk-estage.is-dusk .sk-moonbtn{opacity:1}
§ .sk-estage .sk-duskstars{opacity:0;transition:opacity .4s .3s}
§ .sk-estage.is-dusk .sk-duskstars{opacity:1}
§ .sk-tapcount{position:absolute;left:14px;bottom:10px;font:700 13px/1 var(--sk-f-body);color:var(--sk-bp2)}
§ .sk-kbtn{width:40px;height:40px;border-radius:50%;background:#fff;border:2px solid var(--sk-periwinkle-t);color:var(--sk-periwinkle-t);font:700 16px/1 var(--sk-f-puff);box-shadow:var(--sk-hs);display:grid;place-items:center}
§ .sk-kbtn:active{transform:translate(1.6px,2.2px);box-shadow:none}
§ .sk-pixel{display:none}
§ .sk-estage.is-bonus .sk-pixel{display:block;animation:sticker-pix .5s steps(2) infinite}
§ .sk-estage.is-bonus .sk-normal{display:none}
§ .sk-shinyimg{transition:transform .2s steps(2)}

@media (prefers-reduced-motion:reduce){
§ *,§ *::before,§ *::after{animation:none!important;transition-duration:.12s!important;transition-delay:0s!important}
§ .sk-m-curl{opacity:.35}
}
"""




def tile(stage, title, code, spec, click=False, cls='', attrs=''):
    tap = '<span class="sk-tapme">tap it</span>' if click else ''
    return (f'<div class="sk-mt"><div class="sk-mstage{" is-click" if click else ""} {cls}" {attrs}>{stage}</div>'
            f'<div class="sk-mcap"><h4>{title}{tap}</h4><code>{code}</code><p class="sk-spec2">{spec}</p></div></div>')


def motion_tiles():
    T = []
    st = ''.join(deco(k, s, f'left:{x}px;top:{y}px', f'sk-stk sk-twinkle {d}') for k, s, x, y, d in [
        ('star4', 44, 30, 30, ''), ('star', 36, 112, 18, 'sk-d2'), ('star4', 30, 190, 50, 'sk-d3'), ('heart', 34, 70, 108, 'sk-d2'), ('star', 28, 160, 116, ''), ('star4', 24, 226, 120, 'sk-d2')])
    T.append(tile(st, 'Sparkle twinkle', 'sticker-twinkle · 3 poses · steps(3) · 375 ms = 8 poses/s', 'One rule for every sparkle: three hand-drawn scale poses at 8 fps, offset only by delay. It is a shape change, not a flash, so it stays under the 3 Hz limit.'))
    rows = (row(("strawberries", "Berries", "1 L basket · bounce", 0), 1) + row(("eggs", "Farm eggs", "dozen · wobble", 0), 3)
            + row(("sourdough", "Sourdough", "800 g boule · puff", 0), 2))
    rows = (rows.replace('class="sk-row" data-key="strawberries"', 'class="sk-row" data-cat="berry" data-key="strawberries"')
                .replace('class="sk-row" data-key="eggs"', 'class="sk-row" data-cat="egg" data-key="eggs"'))
    bkd = f'<span class="sk-trbk" data-sk="dbasket">{basket_svg(BBY["punnet"], ["cherries"], "open", 60, 48)}</span>'
    stage = tray('cocoa', 'flowers', 9, 'Farmers’ market', rows, cls='is-compact sk-m-tray', right=bkd, attrs=' data-sk="checkdemo"')
    T.append(tile(stage, 'Check-off, by personality', 'hanko 1.4→1.0 · 2 frames · peel at 200 ms (120 ms) · hop 300 ms (360 ms by kind) · squash + heart on landing', 'Each sticker hops by its kind into the basket, which only ever holds what is already gotten: berries bounce twice, eggs wobble before landing, bread puffs, cans roll the last 10 px, frozen stickers shiver. The basket answers with a wobble and a tiny heart. Tap again to un-check: the sticker hops back out onto its line.', click=True))
    stage = (f'<svg class="sk-m-peel" width="60" height="60" aria-hidden="true"><use href="#sticker-i-strawberries"/></svg>'
             f'<span class="sk-abs" style="left:30px;top:70px">{ghost("strawberries", 60)}</span>'
             f'<div class="sk-m-bk"><svg class="bk" viewBox="-8 -9 80 64" width="104" height="83" aria-hidden="true"><use href="#sticker-bk-picnic-back"/><g class="sk-m-bkmini">{mini("strawberries", 17, 1, 30)}</g><use href="#sticker-bk-picnic-front"/></svg></div>')
    T.append(tile(stage, 'Peel, hop &amp; land', 'peel 120 ms · hop 300 ms arc cubic-bezier(.3,.9,.5,1) · squash 2 frames', 'The corner lifts first (skew + tilt), the checked sticker arcs into its aisle basket and drops in with whatever is already gotten, its bottom tucked behind the front wall. The basket wobbles ±6° and a dotted outline stays on the line.'))
    a = dict(AISLES[0]); a['items'] = a['items'][:2]
    bk = basket_svg(BBY['picnic'], ['strawberries', 'banana'], 'open', 64, 51, live=True)
    rows = ''.join(row(it, i) for i, it in enumerate([('strawberries', 'Strawberries', '1 L basket', 1), ('banana', 'Bananas', '6 bananas', 1)]))
    stage = (f'<div class="sk-m-fold" data-sk="folddemo" style="{avars("strawberry", "gingham")}"><div class="sk-tabrow">{tab(a)}<span class="sk-tmeta"></span><span class="sk-bkw">{bk}</span>'
             f'<span class="sk-chev"><span>{tb("chev-up", 20)}</span></span></div><ul class="sk-rows sk-mrows">{rows}</ul><i class="sk-trcap" aria-hidden="true"></i></div>')
    T.append(tile(stage, 'Aisle done → lid + hanko → fold', 'lid 2 frames · hanko slam 160 ms · rows fold 240 ms ease', 'When the last sticker lands the basket is full: the lid shuts over everything gotten, the GOT IT hanko is pressed onto the lid, and the page folds into its 56 dp strip. Tap to replay.', click=True))
    stage = (f'<div class="sk-m-sheet"><i class="sk-handle"></i><div style="display:flex;gap:5px;padding:16px 0 0 26px">'
             + ''.join(f'<span class="sk-stab{" is-on" if k == "Write" else ""}" style="{avars(f)};height:{30 if k != "Write" else 34}px;font-size:12.5px;padding:0 8px">{k}</span>' for k, f in [('Write', 'lemon'), ('Snap', 'ramune'), ('Speak', 'sakura')]) +
             '</div></div>')
    T.append(tile(stage, 'Letter-set sheet entrance', 'sticker-sheet · 280 ms cubic-bezier(.2,.9,.3,1.1) · washi slaps on in 2 frames', 'The sheet slides up with a small overshoot; the gingham washi handle lands a beat later, scaled 1.6 → 1.0 in two poses.'))
    fan = ''.join(f'<svg class="sk-fanned" style="--fx:{fx}px;--fy:{fy}px;--fr:{fr}deg;animation-delay:{d - .7:.2f}s" aria-hidden="true"><use href="#sticker-i-{k}"/></svg>'
                  for k, fx, fy, fr, d in [('flour', -70, -34, -12, 0), ('eggs', -36, -62, -6, .08), ('butter', 36, -62, 6, .16), ('bakingpowder', 70, -34, 12, .24)])
    stage = (f'{fan}<div class="sk-m-gacha"><svg class="sk-gbody" viewBox="-2 -2 52 52" width="84" height="84" aria-hidden="true" style="--cap:var(--sk-sakura-m);--cap-tr:var(--sk-sakura-t);--cap-p:var(--sk-sakura-p)">'
             f'<use href="#sticker-i-capsule"/></svg></div>')
    T.append(tile(stage, 'Gashapon thinking', 'wobble ±8° on threes · pop · stickers fan out 80 ms apart', 'While the model reads, searches and sorts, the capsule rocks in three stepped poses; when results land it pops and the item stickers fan out one by one.'))
    stage = (f'<svg class="sk-abs" style="left:50%;top:50%;margin:-40px 0 0 -64px" width="128" height="80" viewBox="0 0 76 64" aria-hidden="true"><path d="M38 30C58 6 74 18 62 32C50 46 26 50 14 34C2 18 18 6 38 30Z" fill="none" stroke="#9AA8F5" stroke-width="1.6" stroke-dasharray="2 4"/></svg>'
             f'<div class="sk-abs" style="left:50%;top:50%;margin:-40px 0 0 -64px;width:128px;height:80px;transform:scale(1.68);transform-origin:0 0"><span class="sk-beeloop">{deco("bee", 22, "position:static", "")}</span></div>'
             f'<p class="sk-abs" style="left:0;right:0;bottom:12px;text-align:center;font:700 13px/1 var(--sk-f-body);color:var(--sk-bp2)">Reading your photo…</p>')
    T.append(tile(stage, 'Bee-loop loader', 'offset-path figure-8 · 2.4 s linear · loop', 'Indeterminate progress for photo and voice: a bee flies a dotted figure-8. Swapped for a static bee + “working…” under reduced motion.'))
    stage = (f'<div class="sk-m-ring"></div><div class="sk-m-ladyw"><span>{deco("ladybug", 26, "position:static", "")}</span></div>'
             f'<div class="sk-center">{lcd("68", "68 percent", star=False, p=2)}</div>')
    T.append(tile(stage, 'Ladybug progress ring', 'rotate(t) around the ring centre · 3 s linear · loop', 'A ladybug walks the dashed ring (head first) while the LCD counts up. Used for syncing, re-sorting and icon generation.'))
    stage = f'<div class="sk-m-toast"><div class="sk-toast">{tape("", "sk-t-ging")}<span>Added 5 items to Saturday shop</span><span class="sk-chip" style="--cp:#fff;min-height:26px;font-size:12.5px;align-self:flex-end">{tb("undo", 14)}Undo</span></div></div>'
    T.append(tile(stage, 'Sticky-note toast', 'drop 220 ms cubic-bezier(.3,1.3,.5,1) · −9° → −2° · held 4 s (or 1.2 s + 0.3 s/word if longer)', 'The note drops in tilted and settles at −2°; the washi strip is already on it. Leaves by lifting off upward. Live on phone 1: add something, tear a row off, save a receipt.'))
    bunt = ''.join(f'<svg class="sk-abs" style="left:{x}px;top:{y}px" width="22" height="22" aria-hidden="true"><use href="#sticker-i-{k}"/></svg>' for x, y, k in [(36, 26, 'heart'), (80, 32, 'star'), (124, 34, 'star4'), (168, 32, 'star'), (212, 26, 'heart')])
    stage = (f'<div class="sk-m-pull"><svg class="sk-abs" style="left:20px;top:10px" width="230" height="40" aria-hidden="true"><path d="M4 10Q115 42 226 10" fill="none" stroke="#4F5BA6" stroke-width="1.6" stroke-dasharray="3 3"/></svg>{bunt}'
             f'<svg class="sk-abs sk-m-pullsun" style="left:50%;top:70px;margin-left:-26px" width="52" height="52" aria-hidden="true"><use href="#sticker-i-sun"/></svg></div>'
             f'<div class="sk-m-pad" style="top:136px;height:60px"></div>')
    T.append(tile(stage, 'Pull to refresh', 'drag → bunting sags · release → sun spins 360° · 400 ms', 'A string of sticker bunting dips as you pull; on release the sun sticker spins once while prices and icons re-sync.'))
    scs = []
    for i, (fm, pg) in enumerate(zip(FAMS[:8], PICS[:8])):
        if i < 4:
            inner = f'<svg class="sk-pg" style="--st:var(--sk-{fm}-t)" aria-hidden="true"><use href="#sticker-pg-{pg}"/></svg>'
        elif i == 4:
            inner = f'<span class="sk-sn">{i + 1}</span><svg class="sk-m-thunk" style="left:-2px;top:-2px;color:var(--sk-{fm}-t)" width="32" height="32" aria-hidden="true"><use href="#sticker-pg-{pg}"/></svg>'
        else:
            inner = f'<span class="sk-sn">{i + 1}</span>'
        scs.append(f'<span class="sk-stamp">{ring28()}{inner}</span>')
    stage = (f'<div class="sk-stampstrip" style="position:absolute;left:12px;right:12px;top:40px;margin:0">{"".join(scs)}</div>'
             f'<div class="sk-abs" style="left:50%;top:110px;transform:translateX(-50%)"><span class="sk-lcd" role="img" aria-label="4 then 5 of 8 aisles">'
             f'<span class="sk-la" style="display:block">{lcd_svg("4/8", star=True)}</span><span class="sk-lb">{lcd_svg("5/8", star=True)}</span></span></div>')
    T.append(tile(stage, 'Stamp card thunk', 'aisle done → pictogram hanko 1.6→1.0 · 3 frames · LCD ticks', 'Finishing an aisle inks its pictogram into the next dashed circle with a “thunk” haptic and a tiny star burst; the LCD digits tick over in two frames.'))
    stage = f'<div class="sk-m-holo"><span class="sk-puff" style="font-size:24px;color:var(--sk-bp)">PERFECT!</span></div>'
    T.append(tile(stage, 'Holo foil glint', 'reward only · 1.2 s sweep · 30 dp band @ 35 %', 'The prism foil and its sliding glint are saved for wins: a finished list, a perfect stamp card, a shiny pull. Never on everyday UI.'))
    stage = (f'<div class="sk-m-curl"></div><div class="sk-m-prize"><span class="sk-puff" style="font-size:15px;color:var(--sk-bp);--sw:4px">NEXT</span>'
             f'<span class="sk-num" style="--at:var(--sk-grape-t)">5</span><span style="font:700 14px/1 var(--sk-f-puff);color:var(--sk-grape-t)">Breakfast</span></div>')
    T.append(tile(stage, '“Next stop” page turn', 'page curl 4 frames · 300 ms · card holds still ≥ 2.5 s (1.2 s + 0.3 s/word) · tap to skip', 'When an aisle completes, its page curls away from the corner and a holo prize card says only “NEXT ✦” and the next aisle’s tab, held long enough to read at a relaxed pace. Off switch under Surprises; TalkBack announces the change. Live on phone 1.'))
    stage = f'<div class="sk-center" style="display:flex;gap:14px;align-items:center">{cassette_svg(110)}{eq(5)}</div>'
    T.append(tile(stage, 'Cassette &amp; sticker-dot EQ', 'reels steps(8) @ 8 fps · EQ: whole dots light in 3 stepped poses', 'The reels turn in eight stepped frames; each EQ column is a stack of round puffy dots that switch on and off from the bottom up, lemon → peach → sakura. Dots never squash.'))
    stage = (f'<div class="sk-abs" style="left:50%;top:40px;margin-left:-60px">{basket_svg(BBY["eggbasket"], ["milkbag"], "open", 120, 96, live=True)}</div>'
             f'<span class="sk-badge sk-abs" style="left:calc(50% - 64px);top:112px;--at:var(--sk-peach-t);margin:0">2</span>')
    T.append(tile(stage, 'Basket wobble', 'item lands → rotate −6° / +5° / −2° / 0 · 4 frames · 360 ms', 'Every basket answers a landing sticker with a stepped wobble and a tiny vector heart off the rim; the sticker joins the ones already in it and the to-get badge ticks down. Tap: the eggs land, then the butter, and the last one shuts the lid.', click=True, attrs='data-sk="wobtile" data-items="milkbag,eggs,butter" data-got="1"'))
    tear = (f'<li class="sk-m-slot"><div class="sk-m-tear"><div class="sk-tl">{ico("cereal", 40)}<span class="sk-txt"><span class="sk-nm" style="font-size:16px">Oat cereal</span><span class="sk-dt" style="font-size:12.5px">750 g box</span></span></div>'
            f'<div class="sk-tr"><span class="sk-tspot">{spot()}</span><span class="sk-tcut">{deco("scissors", 22, "position:static", "")}</span></div></div></li>')
    stage = (tray('grape', 'stars', 5, 'Breakfast', row(("coffee", "Coffee beans", "340 g bag", 0), 4) + tear, cls='is-compact sk-m-tray')
             + f'<div class="sk-m-tundo" aria-hidden="true">{tape("", "sk-t-ging")}<span>Oat cereal torn out</span><span class="sk-chip">{tb("undo", 14)}Undo</span></div>'
             + f'<p class="sk-abs" style="left:0;right:0;bottom:12px;text-align:center;font:700 13px/1 var(--sk-f-body);color:var(--sk-bp2)">swipe left · tear off</p>')
    T.append(tile(stage, 'Tear-off delete', 'swipe left → perforation splits · halves drop 600 ms · slot closes 380 ms · Undo note held 4 s', 'Deleting tears the row out of its aisle tray along a perforated line; both halves tumble off the page, the slot closes up, and a sticky-note toast offers Undo, held long enough to read. Undo re-opens the slot and the row is back as it was. Live on phone 1: swipe a row left.'))
    T.append(book_tile())
    return ''.join(T)


def book_tile():
    from book import slot as bslot
    slots = ''.join(bslot(i, k) for i, k in enumerate(['banana', 'carrots'])) + bslot(2).replace('sk-bk-slot', 'sk-bk-slot is-next', 1) + ''.join(bslot(i) for i in range(3, 6))
    mb = (f'<div class="sk-minibook sk-book sk-mbtile" aria-hidden="true"><div class="sk-bk-r"><ol class="sk-bk-page">{slots}</ol><i class="sk-bk-foil"></i></div></div>'
          f'<span class="sk-abs sk-mbtlab" aria-hidden="true">{heart_svg(11)}Sat 3 Oct</span>')
    rows = row(("strawberries", "Strawberries", "1 L basket · bounce", 0), 1).replace('class="sk-row" data-key="strawberries"', 'class="sk-row" data-cat="berry" data-key="strawberries"') + row(("apple", "McIntosh apples", "1.36 kg bag", 0), 2)
    bkd = f'<span class="sk-trbk">{basket_svg(BBY["picnic"], ["banana", "carrots"], "open", 60, 48, live=True)}</span>'
    stage = mb + tray('strawberry', 'gingham', 1, 'Produce', rows, cls='is-compact sk-m-tray sk-mbtray', right=bkd, attrs=' data-sk="booktile" data-base="banana,carrots"')
    return tile(stage, 'Sticker book · magic appear', 'hop lands → +320 ms · kira bloom 400 ms (4 frames) · scale-in .2 → 1.3 → .86 → pressed .95 → 1 · 320 ms · page lift 2 dp',
                'The only flight is row → basket. A beat after the sticker lands, its twin appears out of nowhere in the next spot of the trip’s sticker book: a kira star blooms, the sticker scales in on three hand-drawn poses, presses flat for one frame as if stuck on, and the page lifts 2 dp. Tap again: it peels back off with the sparks running in reverse.', click=True)


def cassette_svg(w=132):
    h = w * 88 / 132
    return (f'<svg class="sk-cassette" viewBox="-3 -3 136 92" width="{w}" height="{h:.0f}" aria-hidden="true">'
            f'<rect x="0" y="0" width="130" height="86" rx="12" fill="#6E7BD0" transform="translate(2.4 3.4)"/>'
            f'<rect x="0" y="0" width="130" height="86" rx="12" fill="#FFD6E8" stroke="#fff" stroke-width="4"/>'
            f'<rect x="2" y="2" width="126" height="82" rx="10" fill="none" stroke="#B02E6B" stroke-width="2"/>'
            f'<rect x="14" y="12" width="102" height="40" rx="8" fill="#fff" stroke="#B02E6B" stroke-width="2"/>'
            f'<rect x="38" y="22" width="54" height="20" rx="4" fill="#FFF3A6" stroke="#B02E6B" stroke-width="1.6"/>'
            + ''.join(f'<g class="sk-reel"><circle cx="{cx}" cy="32" r="11" fill="#FF9FC8" stroke="#B02E6B" stroke-width="2"/>'
                      f'<path d="M{cx} 24V40M{cx - 8} 32H{cx + 8}" stroke="#fff" stroke-width="2.2" stroke-linecap="round"/><circle cx="{cx}" cy="32" r="3" fill="#fff" stroke="#B02E6B" stroke-width="1.4"/></g>' for cx in (30, 100)) +
            f'<path d="M30 86L36 68H94L100 86" fill="#FFD6E8" stroke="#B02E6B" stroke-width="2" stroke-linejoin="round"/></svg>')


# ---------------- "List complete!" ----------------
SHELF = [(14, 160), (98, 160), (182, 160), (266, 160), (14, 232), (98, 232), (182, 232), (266, 232)]
HAMPER = (105, 420, 150, 120)


HOP_PATHS = []


def spiral(cx=180, cy=452, r0=168, turns=1.3, a0=200, squash=.5, n=56, rise=-180):
    """sakura ribbon spiral: winds in from the shelf to the hamper mouth"""
    pts = []
    for i in range(n + 1):
        t = i / n
        a = math.radians(a0 + 360 * turns * t)
        r = r0 * (1 - t) ** 1.15 + 4
        pts.append((cx + r * math.cos(a), cy + rise * (1 - t) ** 1.6 + r * squash * math.sin(a)))
    return pts


def _pd(pts):
    return 'M' + 'L'.join(f'{x:.0f} {y:.0f}' for x, y in pts)


HEART_MASK = ("url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'%3E%3Cpath d='M50 92C30 76 4 58 4 32C4 16 16 6 29 6C38 6 45 11 50 19C55 11 62 6 71 6C84 6 96 16 96 32C96 58 70 76 50 92Z'/%3E%3C/svg%3E\")")


def cele_under():
    """r4 start frame: the finished list the flash + heart iris open over (every aisle folded into its basket, lid shut,
    GOT IT hanko on the lid, all 8 stamps in). Static and inert: screen 1 hides it and shows its own live list instead."""
    pages = []
    for a in AISLES:
        a2 = dict(a)
        a2['items'] = [tuple(it[:3]) + (1,) + tuple(it[4:]) for it in a['items']]
        pages.append(f'<div class="sk-aw is-complete">{fold_page(a2)}</div>')
    from book import book as _book, got_in_walking_order as _gw
    full = [dict(a, items=[tuple(it[:3]) + (1,) + tuple(it[4:]) for it in a['items']]) for a in AISLES]
    inner = (f'<div style="height:28px"></div>{appbar(lcdtext=f"{TOTAL}/{TOTAL}", egg=None)}{_book(full, _gw(full), done=tuple(a["n"] for a in AISLES), now=None, live=False)}'
             f'<div class="sk-scroll">{"".join(pages)}<p class="sk-endnote">{heart_svg(14)} end of the list · {TOTAL} stickers</p></div>')
    inner = re.sub(r'<button\b', '<span', inner).replace('</button>', '</span>')
    inner = re.sub(r'\s(?:data-sk|aria-[a-z]+|tabindex|type|role)="[^"]*"', '', inner)
    return f'<div class="c-under" aria-hidden="true">{inner}</div>'


def celebration_markup():
    shelf, hops = [], []
    hx, hy = HAMPER[0] + HAMPER[2] / 2, HAMPER[1] + 34
    SP = spiral(hx, hy)
    k = 0
    for a, (x, y) in zip(AISLES, SHELF):
        keys = [it[0] for it in a['items']]
        shelf.append(f'<div class="c-shelf" style="left:{x}px;top:{y}px">{basket_svg(BBY[a["basket"]], keys, "open", 80, 64)}</div>')
        sx, sy = x + 40, y + 24
        j = min(range(len(SP) // 2), key=lambda i: math.dist(SP[i], (sx, sy)))         # join the ribbon where it passes this basket
        mx, my = (sx + SP[j][0]) / 2, min(sy, SP[j][1]) - 26
        path = f'M{sx} {sy}Q{mx:.0f} {my:.0f} {SP[j][0]:.0f} {SP[j][1]:.0f}' + _pd(SP[j:]).replace('M', 'L', 1)
        HOP_PATHS.append(path)
        for key in keys:
            hops.append(f'<svg class="c-hop c-r{len(HOP_PATHS)}" style="animation-delay:{.45 + k * .055:.3f}s" width="30" height="30" aria-hidden="true"><use href="#sticker-i-{key}"/></svg>')
            k += 1
    ribbon = (f'<svg class="c-ribbon" viewBox="0 0 360 760" width="360" height="760" aria-hidden="true"><path d="{_pd(SP)}" pathLength="100" class="c-rdraw" fill="none" stroke="#fff" stroke-width="13" stroke-linecap="round" stroke-linejoin="round"/>'
              f'<path d="{_pd(SP)}" pathLength="100" class="c-rdraw" fill="none" stroke="#FF9FC8" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>'
              f'<path d="{_pd(SP)}" pathLength="100" class="c-rstitch" fill="none" stroke="#FFD6E8" stroke-width="2.4" stroke-dasharray="1.4 2.2" stroke-linecap="round"/></svg>')
    # 3 waves x 12 die-cut stickers across the full width: hearts, stars, petals and mini groceries
    conf = []
    import random as _r
    g = _r.Random(4)
    kinds = ['heart', 'star4', 'petal', 'star', 'strawberries', 'heart', 'petal', 'apple', 'star4', 'croissant', 'lemon', 'cherries']
    for wave, d0 in enumerate((4.9, 5.2, 5.5)):
        for j in range(12):
            kind = kinds[(j * 5 + wave * 3) % len(kinds)]
            x = 4 + j * 29.5 + g.randint(-6, 6)
            r = g.choice([-1, 1]) * g.randint(140, 320)
            dur = 1.3 + g.random() * .5
            sz = g.choice([18, 20, 22, 24, 26])
            conf.append(f'<svg class="c-conf" style="left:{x:.0f}px;--r:{r}deg;--fx:{g.randint(-30, 30)}px;animation-delay:{d0 + j * .035 + g.random() * .08:.2f}s;animation-duration:{dur:.2f}s" width="{sz}" height="{sz}" aria-hidden="true"><use href="#sticker-i-{kind}"/></svg>')
    burst = ('<svg viewBox="-100 -100 200 200" aria-hidden="true"><path d="' + star_path(0, 0, 96, 58, 12) + '" fill="#FFF3A6" stroke="#fff" stroke-width="6" stroke-linejoin="round"/>'
             '<path d="' + star_path(0, 0, 70, 46, 12, -75) + '" fill="#FFD6E8" stroke="#fff" stroke-width="4" stroke-linejoin="round"/></svg>')
    stamps = ''.join(f'<span class="sk-stamp is-done">{ring28()}<svg class="sk-pg" style="--st:var(--sk-{a["fam"]}-t)" aria-hidden="true"><use href="#sticker-pg-{a["pic"]}"/></svg></span>' for a in AISLES)
    card = (f'<div class="c-card"><div class="c-cardin"><div class="c-front"><div class="sk-stampstrip">{stamps}</div></div>'
            f'<div class="c-back"><div class="sk-stampstrip sk-holo-card">{stamps}</div></div></div>'
            f'<span class="c-perfect">{hanko(-12, 58, ("PER-", "FECT!"), label="Perfect")}<i class="c-ink"></i></span></div>')
    pile = basket_svg(BBY['picnic'], ['strawberries', 'maple', 'croissant'], 'open', HAMPER[2], HAMPER[3], cls='c-hamper')
    spread = ''.join(f'<span class="c-spread" style="left:{x}px;top:{y}px;animation-delay:{d}s">{ico(k, s)}</span>' for k, x, y, s, d in
                     [('blanket', 28, 560, 58, 2.4), ('strawberries', 40, 618, 52, 2.5), ('croissant', 98, 600, 48, 2.55), ('milkbag', 150, 612, 50, 2.6), ('eggs', 206, 604, 48, 2.65)])
    capsule = (f'<div class="c-gacha"><svg class="c-gbody" viewBox="-2 -2 52 52" width="58" height="58" aria-hidden="true" style="--cap:#FFDA4A;--cap-tr:#8A6800;--cap-p:#FFF3A6"><use href="#sticker-i-capsule"/></svg>'
               f'<div class="c-bonus"><span class="c-bshine"></span><span class="c-bico">{ico("maple-holo", 40)}</span><b>bonus sticker!</b></div></div>')
    kira = ''.join(f'<svg class="c-kira" style="left:{x}px;top:{y}px;--kd:{d}s" width="48" height="48" aria-hidden="true"><use href="#sticker-i-kira"/></svg>'
                   for x, y, d in [(2, 36, .25), (310, 36, .3), (20, 120, .35), (292, 128, .4)])
    title = (f'<div class="c-title"><b data-t="List complete!">List complete!</b><span class="sk-chip" style="--cp:#fff">{heart_svg(14)} Saturday shop</span>'
             f'<span class="c-lcd sk-lcd" role="img" aria-label="{TOTAL} of {TOTAL}"><span class="c-pet">{pixbee()}</span>{lcd_svg(f"{TOTAL}/{TOTAL}", p=2, star=True)}</span></div>')
    banner = (f'<div class="c-banner"><svg viewBox="0 0 324 46" width="324" height="46" aria-hidden="true">'
              f'<path d="M2 12H34V42H2L12 27Z" fill="#E67BAA" stroke="#fff" stroke-width="3" stroke-linejoin="round"/><path d="M322 12H290V42H322L312 27Z" fill="#E67BAA" stroke="#fff" stroke-width="3" stroke-linejoin="round"/>'
              f'<path d="M24 36L34 42V36Z M300 36L290 42V36Z" fill="#B02E6B"/>'
              f'<rect x="24" y="4" width="276" height="32" rx="6" fill="#6E7BD0" transform="translate(1.6 2.2)"/><rect x="24" y="4" width="276" height="32" rx="6" fill="#FF9FC8" stroke="#fff" stroke-width="3"/>'
              f'<path d="M30 9H294" stroke="#fff" stroke-width="2" stroke-dasharray="3 3" opacity=".8"/><path d="M30 31H294" stroke="#fff" stroke-width="2" stroke-dasharray="3 3" opacity=".8"/></svg>'
              f'<span>{TOTAL} stickers · 41 min</span></div>')
    album = (f'<div class="c-album" aria-hidden="true"><span class="c-pocket"></span><span class="c-pocket c-p2"></span>'
             f'<svg class="c-pocketstk" width="44" height="44"><use href="#sticker-i-maple-holo"/></svg><svg class="c-pocketkira" width="40" height="40"><use href="#sticker-i-kira"/></svg></div>')
    scene = (f'<div class="c-scene"><div class="c-rays"></div><div class="c-burst">{burst}</div>{title}{kira}{"".join(shelf)}'
             f'<div class="c-blanket"></div>{pile}{spread}{capsule}{card}{banner}{ribbon}{"".join(hops)}{"".join(conf)}</div>')
    return (f'<div class="sk-cele" data-sk="cele" style="--ix:300px;--iy:600px" role="img" aria-label="List complete celebration: a white flash and a heart wipe open from the last row, every sticker hops along a ribbon into the picnic hamper, the stamp card turns holo with a PERFECT hanko, confetti stickers rain, and the page peels back to drop a bonus sticker into the album">'
            f'{cele_under()}{album}{scene}<i class="c-flap"></i><i class="c-flash"></i>'
            f'<span class="c-skip">tap to skip</span></div>')


def celebration():
    screen = (f'<div class="sk-screen" style="background:var(--sk-binder)">{celebration_markup()}{status("11:02")}'
              f'<button class="sk-btn sk-replay" type="button" data-sk="replay">{star_svg(18, "#FFF3A6", "#C3264F")}Replay</button></div>')
    steps = [
        ('0.0 s', 'Last item stamped, its aisle folded into its basket: a 2-frame white flash over the finished list (8 baskets shut, the sticker book full, 24/24), then a heart-shaped iris opens from that row onto pastel rays, the blanket and the open hamper.'),
        ('0.1 s', 'Text beat 1 · “List complete!” drops in with a “♡ Saturday shop” chip (4 words, read by 3.1 s); the LCD reads 24/24 with the pixel-bee pet. Nothing else with words arrives until 3.25 s.'),
        ('0.25 s', 'Four 48 dp holo kira stars pop around the title (0 → 1.4 → 1 in 3 steps), then twinkle at 8 fps.'),
        ('0.45 s', f'A sakura ribbon spiral draws itself (12 steps) from the shelf down into the hamper; all {TOTAL} stickers peel off their 8 baskets and ride it, 55 ms apart, landing by 2.3 s.'),
        ('2.3 s', 'The stamp card drops in, flips (rotateY) and turns holo. Text beat 2 · the PER-FECT! hanko slams onto its corner at 3.25 s with a 2-frame 3 px shake and an ink ring (1 word, 1.5 s).'),
        ('4.9 s', f'Text beat 3 · a ribbon banner unfurls: “{TOTAL} stickers · 41 min” (4 words, read by 7.6 s), while 36 die-cut confetti stickers fall behind the words in 3 waves.'),
        ('6.8 s', 'The gold gashapon capsule wobbles twice and pops. Text beat 4 · a holo “bonus sticker!” card rises out of it at 7.3 s (2 words, read by 9.4 s).'),
        ('9.5 s', 'The screen peels from the bottom-right corner (4-frame page curl) onto the album page; at 10.1 s the bonus sticker’s twin appears in its pocket by magic (kira bloom, stepped scale-in). The card keeps its sticker; nothing flies.'),
        ('10.4 → 13.8 s', 'Everything holds still as a summary for 3.4 s, then the overlay closes (on phone 1 the book’s stamp page turns holo with PER-FECT! a beat later). One tap skips at any point.'),
        ('replay', 'This phone plays when it scrolls into view (40 % visible) and again every 15 s while it stays there; ★ Replay restarts it.'),
        ('reduced motion', 'No flash, wipe, shake or curl: the final frame fades in over 120 ms and stays.'),
    ]
    st = ''.join(f'<li style="display:flex;gap:10px;align-items:baseline"><code style="flex:none;min-width:94px;text-align:center">{t}</code><span>{d}</span></li>' for t, d in steps)
    return (f'<div class="sk-celewrap"><div class="sk-pcol"><span class="sk-plabel"><span class="sk-cnum">{star_svg(16)}</span>List complete!</span><div class="sk-phone">{screen}</div></div>'
            f'<div class="sk-padcard"><h3 class="sk-h3" style="margin-bottom:6px">Transformation sequence</h3>'
            f'<p class="sk-small" style="margin-bottom:12px">An early-90s transformation, retold as a picnic: flash, heart wipe, the whole list rides a ribbon into the hamper, the stamp card turns to foil, confetti rains, the capsule pays out and the page peels back into the album. Retimed for a relaxed read: one short text beat at a time, each held for at least 1.2 s + 0.3 s per word before the next one arrives, then a 3 s still summary. About 14 s, skippable with one tap; on phone 1 the wipe opens from the row you just stamped.</p>'
            f'<ol style="display:flex;flex-direction:column;gap:10px;font-size:15px">{st}</ol></div></div>')


def timing_tables():
    chain = [
        ('0', 'Hanko slams 1.4 → 1.15 → 1 (2 frames, 160 ms), 8-sticker burst, CONFIRM “thunk”. Name strikes through.'),
        ('160', 'Ink-spread ring + the ruled line thunks (2 frames, 170 ms).'),
        ('200', 'Peel: the sticker’s corner lifts in 2 frames (−6° → −12°, skew −8°), 120 ms. Soft “tk”, CLOCK_TICK.'),
        ('320', 'Hop: a 300 ms arc (360 ms with personality: berries bounce, eggs wobble, bread puffs, cans roll, frozen shivers) into the aisle’s basket. The only flight.'),
        ('620', 'Lands: the new mini squashes in 2 frames (1.12/.86 → .96/1.05 → 1), basket wobbles (4 frames, 360 ms) + rim heart; badge −1, LCD ticks, the pixel pet chomps.'),
        ('940', 'Magic: its twin appears in the next spot of the trip’s sticker book: kira bloom (4 frames, 400 ms) + 6 sparks, stepped scale-in .2 → 1.3 → .86 → pressed .95 → 1 (320 ms), the page lifts 2 dp (160 ms). TalkBack: “Strawberries added to today’s sticker book”.'),
        ('1260', 'Settled. If the book’s page is full it turns (3 frames, 240 ms) before the next sticker appears; a full trip book gets one holo glint (1.2 s).'),
        ('un-check', 'At once: the twin peels off its spot (lift, 1.12, .55, gone · 320 ms) with the sparks running inward; the sticker hops back out of the basket onto its line (340 ms); badge +1 and LCD tick immediately. The empty spot becomes the holo “next” spot.'),
        ('aisle done', '+300 lid shuts (2 frames) with GOT IT on the lid · +500 the aisle’s pictogram thunks into the book’s stamp page · +800 the page curls away (4 frames, 300 ms) · +1100 it folds to its strip · +1300 the NEXT card rises and the next aisle opens behind it.'),
        ('last item', '+1350 the celebration (13.8 s, tap to skip) · +450 after it closes the stamp page turns holo and PER-FECT! is pressed across the spine. Hold the LCD now to print the receipt.'),
    ]
    rows = ''.join(f'<tr><td><code>{t}</code></td><td>{d}</td></tr>' for t, d in chain)
    rule = [
        ('“Next stop” card', 'NEXT ✦ · 5 Breakfast', '3', '2.5 s (floor)', '2.6–2.9 s still, 3.3 s on screen; tap skips', '0.78 s for ~6 words'),
        ('Celebration · title', 'List complete! · ♡ Saturday shop', '4', '2.4 s', '2.6 s alone, then stays to the end', 'joined by 4 more beats inside 3 s'),
        ('Celebration · PERFECT!', 'PER-FECT!', '1', '1.5 s', '1.7 s before the next words', 'overlapped'),
        ('Celebration · ribbon', '24 stickers · 41 min', '4', '2.4 s', '2.6 s before the bonus; on screen 8.6 s', '6 words at 2.0 s'),
        ('Celebration · bonus', 'bonus sticker!', '2', '1.8 s', '1.9 s before the curl; stays', '5 words, curl at 3.3 s'),
        ('Celebration · summary', 'all of the above', '—', '≥ 3 s still', '3.4 s still, closes at 13.8 s', '1.9 s'),
        ('Sticky-note toast', 'Added 5 items to Saturday shop ♡ · Undo', '7', '3.3 s', '4 s', '2.7 s (tile)'),
        ('Tear-off Undo note', 'Oat cereal torn out · Undo', '5', '2.7 s', '4 s live · 2.9 s in the tile loop', '0.19 s'),
        ('Jackpot', 'JACK / POT!', '2', '1.8 s', '1.8 s with the capsule, then the row', '—'),
        ('Rainy-day toast', 'Rain on the way · bring the tote! ☂', '6', '3.0 s', '4.5 s', '—'),
        ('Bonus stage', 'BONUS STAGE! ♪', '2', '1.8 s', 'until you tap a stamp or leave', '—'),
        ('LCD receipt', '9 short lines', '~16', 'prints 1.7 s', 'stays until tapped', 'did not print'),
    ]
    rrows = ''.join(f'<tr><td>{a}</td><td data-l="Copy">{b}</td><td data-l="Words">{c}</td><td data-l="Rule">{d}</td><td data-l="Now">{e}</td><td data-l="Before">{f}</td></tr>' for a, b, c, d, e, f in rule)
    return (f'<div class="sk-card sk-cues sk-timing"><h3 class="sk-h3">Check-off → basket → sticker book <span class="sk-chip" style="--cp:var(--sk-melon-p)">ms from the tap · live on phone 1</span></h3>'
            f'<div class="sk-tablewrap"><table class="sk-ttab"><thead><tr><th>ms</th><th>What happens</th></tr></thead><tbody>{rows}</tbody></table></div></div>'
            f'<div class="sk-card sk-cues sk-timing"><h3 class="sk-h3">Full-screen + text moments: readable at a relaxed pace <span class="sk-chip" style="--cp:var(--sk-lemon-p)">hold ≥ 1.2 s + 0.3 s per word · interstitials ≥ 2.5 s</span></h3>'
            f'<div class="sk-tablewrap"><table><thead><tr><th>Moment</th><th>Copy shown</th><th>Words</th><th>Rule</th><th>Now</th><th>Before (r8)</th></tr></thead><tbody>{rrows}</tbody></table></div>'
            f'<p class="sk-small" style="margin-top:8px">Fewer words first (the next card lost its duplicate pill; the celebration lost “Hillcrest Market”, “8 baskets” and “for your album”), then longer holds. Every moment stays skippable with one tap and can be switched off under Surprises.</p></div>')


def cues():
    rows = [('Hanko lands', '“thunk”', 'CONFIRM', 'check-off, aisle done, PERFECT!'), ('Sticker peels', 'soft “tk”', 'CLOCK_TICK', 'corner lift before the hop'),
            ('Lands in basket', 'paper “pff”', 'TEXT_HANDLE_MOVE', 'basket wobble + heart'), ('Cassette record', 'clunk', 'KEYBOARD_TAP', 'start / stop'),
            ('LCD digit ticks', 'beep (8-bit)', 'none', 'only with sound on'), ('Capsule pops', '“pon!”', 'CONFIRM', 'AI results, jackpot')]
    body = ''.join(f'<tr><td>{a}</td><td data-l="Sound">{b}</td><td data-l="Haptic"><code>{c}</code></td><td data-l="Where">{d}</td></tr>' for a, b, c, d in rows)
    return (f'<div class="sk-card sk-cues"><h3 class="sk-h3">Haptics &amp; sound cues <span class="sk-chip" style="--cp:var(--sk-lemon-p)">all optional · under “Surprises”</span></h3>'
            f'<p class="sk-small" style="margin:-4px 0 8px">Try them: switch <b>Sounds</b> on under Surprises in phone 4 and the live phones play small WebAudio stand-ins for each cue; on an Android browser the haptics buzz too.</p>'
            f'<div class="sk-tablewrap"><table><thead><tr><th>Moment</th><th>Sound</th><th>Haptic</th><th>Where</th></tr></thead><tbody>{body}</tbody></table></div></div>')


def eggs():
    E = []

    def egg(n, title, stage, trig, eff, attrs='', cls='', where=''):
        w = f'<span class="sk-where">{where}</span>' if where else ''
        E.append(f'<div class="sk-egg"><div class="sk-estage {cls}" {attrs}>{stage}</div><div class="sk-ecap"><h4><span class="sk-enum">{n}</span>{title}{w}</h4>'
                 f'<dl><dt>Trigger</dt><dd>{trig}</dd><dt>Effect</dt><dd>{eff}</dd></dl></div></div>')
    stars = ''.join(f'<svg class="sk-abs sk-duskstars sk-twinkle" style="left:{x}px;top:{y}px" width="{s}" height="{s}" aria-hidden="true"><use href="#sticker-i-star4"/></svg>' for x, y, s in [(30, 24, 22), (90, 50, 16), (150, 20, 20)])
    st = (f'{stars}<span class="sk-puff sk-abs" style="left:16px;top:84px;font-size:22px;color:var(--sk-bp)">Saturday shop</span>'
          f'<svg class="sk-moonbtn" width="44" height="44" aria-hidden="true"><use href="#sticker-i-moon"/></svg>'
          f'<button class="sk-sunbtn" type="button" data-sk="sun" aria-label="Sun sticker, tap 7 times">{ico("sun", 52)}</button><span class="sk-tapcount" data-sk="suncount">tap the sun · 0 / 7</span>')
    egg(1, 'Dusk binder', st, 'Tap the sun sticker beside the title 7 times.', 'Over 1.2 s the sun sinks behind the title in 3 poses and a full moon rises with a sparkle burst; three posterised dusk bands (peach → lavender → periwinkle) wash the top of the binder, five sparkles twinkle in the gutters and the LCD star becomes a little full moon. Tap the moon to undo.', 'data-sk="dusk"', where='in screen 1')
    lrows = row(("eggs", "Eggs, large", "dozen", 0), 0) + row(("butter", "Butter, salted", "454 g brick", 0), 1)
    st = (tray('peach', 'clouds', 6, 'Dairy &amp; eggs', lrows, cls='is-compact sk-e-tray')
          + f'<span class="sk-e-lady"><span class="sk-e-walk"><svg width="18" height="18" aria-hidden="true"><use href="#sticker-i-ladybug"/></svg></span><span class="sk-e-fly"><svg width="28" height="28" aria-hidden="true"><use href="#sticker-i-ladybugfly"/></svg></span></span>')
    egg(2, 'Ladybug stroll', st, 'Press and hold a folded basket for one second (a shorter hold just peeks inside).', 'A ladybug walks along the aisle tray’s end cap in six stepped hops with a two-frame waddle, pauses by an item, then opens its wings and flies off the edge. It never crosses item text.', where='in screen 1')
    st = (f'<span class="sk-e-rollb"><svg width="44" height="44" aria-hidden="true" style="display:block"><use href="#sticker-i-apple"/></svg></span>'
          f'<div class="sk-abs" style="right:6px;top:58px">{basket_svg(BBY["picnic"], ["banana", "carrots"], "open", 96, 77)}</div>'
          f'<p class="sk-abs" style="left:12px;top:12px;font:600 15px/1.2 var(--sk-f-pencil);color:var(--sk-bp2)">+ apples</p>')
    egg(3, 'Rolling produce', st, 'Add “strawberries”, “apples” or “fraises”, in any language.', 'The round fruit sticker rolls down the page and bumps the Produce basket, which wobbles hello, then settles on its new line: it is still to get, so it only hops into the basket once you check it off. A berry basket hops instead.')
    pet = ''.join(f'<i class="sk-petal" style="left:{x}%;animation-delay:-{d}s;--fx:{fx}px;--fr:{fr}deg"></i>' for x, d, fx, fr in
                  [(8, 0, 30, 220), (22, 1.1, -20, 160), (36, 2.2, 26, 300), (50, .6, -30, 200), (64, 1.7, 20, 260), (78, 2.6, -16, 180), (90, 1.3, 24, 240), (14, 2.9, 10, 280)])
    egg(4, 'Petal shake', pet + f'<div class="sk-center">{ico("flower", 54)}</div>', 'Shake the phone (with reduced motion off).', 'Sakura petals scatter over the binder, settle as tiny stickers for 3 s, then blow away. Haptic: one soft tick.')
    st = (f'<div class="sk-glow sk-dark" style="position:absolute;inset:0;border-radius:0;padding:10px">'
          + ''.join(f'<svg class="sk-glowstar" style="left:{x}px;top:{y}px;animation-delay:-{d}s" width="{s}" height="{s}" aria-hidden="true"><use href="#sticker-i-glowstar"/></svg>' for x, y, s, d in [(252, 132, 14, 0), (150, 140, 10, 2)]) +
          f'<svg class="sk-abs" style="right:14px;top:10px" width="40" height="40" aria-hidden="true"><use href="#sticker-i-moon"/></svg>'
          f'<div class="sk-abs" style="left:10px;top:12px">{lcd("SNACK", "Snack?", star=True, p=1.8)}</div>'
          f'<div class="sk-abs sk-snackrow"><span class="sk-tab" style="{avars("mint", "check")};height:32px"><span class="sk-num">7</span><span class="sk-tabname" style="font-size:17px">Frozen</span></span>'
          f'<span class="sk-snackstar">{ico("star", 22)}</span><span class="sk-maru sk-snackdoodle" aria-hidden="true">snack?</span></div>'
          f'<div class="sk-abs" style="left:14px;top:104px;font:500 12.5px/1.3 var(--sk-f-body);color:#C9CFF5">walking order unchanged · aisle 7</div></div>')
    egg(5, 'Midnight snack mode', st, 'Open the app between 23:00 and 04:00.', 'Glow-in-the-dark theme, glow stars, the moon by the title and an LCD that asks “SNACK?”. A snack-time star and a “snack?” doodle sit beside the Frozen tab; the walking order never changes.')
    st = (f'<div class="sk-center" style="display:flex;gap:16px;align-items:center"><span data-sk="shinyimg" class="sk-shinyimg sk-ico">{ico("apple", 80, extra=glint_svg("apple"))}</span>'
          f'<div style="display:flex;flex-direction:column;gap:6px;align-items:flex-start"><button class="sk-btn2" type="button" data-sk="roll" style="height:40px;font-size:14px;padding:0 14px">{tb("sparkle", 16)}Add an item</button>'
          f'<span class="sk-small" data-sk="rollout" style="font-weight:700;display:inline-flex;align-items:center;gap:4px">odds 1 in 200</span></div></div>')
    egg(6, 'Shiny sticker', st, 'Any newly added item, 1 in 200 chance.', 'The sticker arrives in holo foil clipped to its body with a glint every few seconds and a lemon star, and is saved to the album. (This demo cheats: the 3rd add is always shiny. Screen 1’s maple tin is one.)', where='in screen 1')
    st = (f'<div class="sk-center" style="text-align:center"><span class="sk-wm sk-holotext" style="font-size:36px;-webkit-text-stroke:0;filter:none;display:block">Kirakira</span>'
          f'<span class="sk-small" style="font:600 15px/1.2 var(--sk-f-pencil)">“kirakira”</span></div>'
          + ''.join(deco(k, s, f'left:{x}px;top:{y}px', f'sk-stk sk-twinkle {d}') for k, s, x, y, d in [('star4', 24, 22, 18, ''), ('star', 20, 230, 24, 'sk-d2'), ('star4', 18, 40, 110, 'sk-d3'), ('heart', 18, 220, 108, '')]))
    egg(7, 'Magic word', st, 'Type “kirakira” (or キラキラ) in the add box and send.', 'Nothing is added. Instead every sticker on the page twinkles once and the wordmark turns holo for 5 s.')
    st = (f'<div class="sk-abs" style="left:50%;top:26px;margin-left:-60px"><svg class="bk" viewBox="-8 -9 80 64" width="120" height="96" aria-hidden="true"><use href="#sticker-bk-picnic-back"/>'
          f'<g class="sk-breathe"><use href="#sticker-i-cat" x="8" y="-6" width="48" height="48"/></g><use href="#sticker-bk-picnic-front"/></svg></div>'
          f'<span class="sk-zz" style="left:64%;top:22px">z</span><span class="sk-zz" style="left:68%;top:18px;animation-delay:-1.2s">Z</span>')
    egg(8, 'Nap time', st, 'An aisle basket stays empty for a whole trip.', 'A ginger tabby curls up in it and snoozes. Tap it: it stretches, the basket wobbles, and it trots off the page. Plain pastel face; its gloss sits on its back.')
    conf = ''.join(f'<i class="sk-conf" style="left:{x}%;background:{c};animation-delay:-{d}s;--fx:{fx}px;--fr:{fr}deg"></i>' for x, c, d, fx, fr in
                   [(10, '#FF9FC8', 0, 14, 300), (24, '#7CC6F5', 1, -10, 200), (38, '#FFDA4A', 2, 18, 260), (60, '#8EDB76', .5, -14, 320), (74, '#B597F2', 1.5, 12, 220), (88, '#FFA477', 2.2, -8, 280)])
    st = conf + f'<div class="sk-center" style="display:flex;align-items:center;gap:6px">{ico("cake", 76)}{hanko(-12, 64, ("HAPPY", "B-DAY!"), label="Happy birthday")}</div>'
    egg(9, 'Birthday cake', st, 'Add “birthday cake”, “candles” or “gâteau”.', 'Confetti stickers rain for 2 s and the check-off hanko for that item reads “HAPPY B-DAY!”.')
    pix = '<svg class="sk-pixel sk-abs" style="left:50%;top:14px;margin-left:-32px" width="64" height="64" viewBox="0 0 16 16" shape-rendering="crispEdges" aria-hidden="true">' + ''.join(
        f'<rect x="{x}" y="{y}" width="1" height="1" fill="{c}"/>' for y, row_ in enumerate([
            '......GG.GG.....', '.......GGG......', '....RRRGRRRR....', '...RRRRRRRRRR...', '..RRYRRRRRYRRR..', '..RRRRRYRRRRRR..', '..RRRYRRRRYRRR..',
            '...RRRRRRRRRR...', '...RRYRRRYRRR...', '....RRRRRRRR....', '.....RRYRRR.....', '......RRRR......', '.......RR.......']) for x, ch in enumerate(row_) if ch != '.'
        for c in [{'G': '#2E8239', 'R': '#FF6F8E', 'Y': '#FFF3A6'}[ch]]) + '</svg>'
    st = (f'{pix}<svg class="sk-normal sk-abs" style="left:50%;top:14px;margin-left:-32px" width="64" height="64" aria-hidden="true"><use href="#sticker-i-strawberries"/></svg>'
          f'<div class="sk-abs" style="left:0;right:0;bottom:12px;display:flex;justify-content:center;gap:8px">'
          + ''.join(f'<button class="sk-kbtn" type="button" data-sk="kon" data-n="{i}" aria-label="Aisle {i}">{i}</button>' for i in range(1, 5)) +
          f'</div><span class="sk-abs sk-small" data-sk="konout" style="right:10px;top:10px;font-weight:700">1 1 2 2 3 4 3 4</span>')
    egg(10, 'Bonus stage', st, 'Tap the stamp-card numbers 1 1 2 2 3 4 3 4.', 'Every sticker turns into an 8-bit sprite and the LCD plays a jingle until you leave the screen. Try it on the buttons.', 'data-sk="bonus"')
    tapes = ''.join(f'<span class="sk-seastape"><i class="sk-tape sk-t-{k}"></i><b>{t}</b><small>{dt}</small></span>' for k, t, dt in
                    [('maple', 'Canada Day', 'Jul 1'), ('pumpkin', 'Thanksgiving', 'Oct 12 · next!'), ('halloween', 'Halloween', 'Oct 31'), ('snow', 'First snow', 'by forecast'), ('hearts', 'Valentine’s', 'Feb 14'), ('redenv', 'Lunar New Year', 'Feb 17')])
    st = f'<div class="sk-season">{tapes}</div>'
    egg(11, 'Seasonal sticker sheets', st, 'The calendar (Ontario dates), or the first snowy forecast.', 'A themed washi tape on the add bar (screen 1 wears the Thanksgiving pumpkin), a seasonal sticker sheet in the album and a matching stamp-card pictogram.', where='in screens 1 + 5')
    st = (f'<div style="position:absolute;inset:0;background:linear-gradient(180deg,#FFE7C2,#FFD6E8);"></div>'
          f'<svg class="sk-abs" style="left:50%;top:14px;margin-left:-30px;animation:sticker-dusk 4s ease-in-out infinite alternate" width="60" height="60" aria-hidden="true"><use href="#sticker-i-sun"/></svg>'
          f'<div class="sk-abs" style="left:0;right:0;top:104px;height:46px;background:repeating-linear-gradient(90deg,#FFDCC6 0 2px,transparent 2px 10px),#FFF1E6;border-top:3px solid #fff"></div>'
          f'<div class="sk-abs" style="left:20px;top:84px">{ico("lemonade", 50)}</div><div class="sk-abs" style="right:24px;top:90px;filter:drop-shadow(3px 4px 0 #E59A7A)">{ico("sandwich", 44)}</div>')
    egg(12, 'Golden hour', st, 'Within 30 minutes of local sunset.', 'The binder warms to apricot, sticker shadows lengthen to +3 / +4 dp in a rosy tone, and the sun sticker sinks a little each minute.')
    st = (f'<div class="sk-center" style="display:flex;align-items:center;gap:6px"><svg class="sk-wobble" width="76" height="76" aria-hidden="true" style="--cap:#FFDA4A;--cap-tr:#8A6800;--cap-p:#FFF3A6"><use href="#sticker-i-capsule"/></svg>'
          f'{hanko(-10, 60, ("JACK", "POT!"), label="Jackpot")}</div>')
    egg(13, 'Gashapon jackpot', st, 'The AI adds an item it has no icon for, 1 in 100.', 'The mystery capsule comes out gold, wobbles twice and stamps “JACKPOT!”. Its live-generated icon gets the holo treatment.')
    sc = ''.join(f'<span class="sk-stamp">{ring28()}<svg class="sk-pg" style="--st:var(--sk-{fm}-t)" aria-hidden="true"><use href="#sticker-pg-{pg}"/></svg></span>' for fm, pg in zip(FAMS[:6], PICS[:6]))
    st = (f'<div class="sk-stampstrip sk-holo-card" style="position:absolute;left:16px;right:16px;top:52px;margin:0;background:var(--sk-holo);overflow:visible">{sc}'
          f'<span style="position:absolute;right:-6px;top:-30px">{hanko(-12, 58, ("PER-", "FECT!"), label="Perfect")}</span></div>'
          f'<p class="sk-abs" style="left:16px;top:18px;font:700 13px/1 var(--sk-f-body);color:var(--sk-bp2)">week 4 of 4</p>')
    egg(14, 'Perfect week', st, 'Finish four lists in a row, every item checked.', 'The stamp card turns holo for a week and a new sticker sheet (sakura picnic) unlocks in the album. After ten finished trips the binder’s back pocket opens with a page curl: “sticker sheet 2 of 2”, closing the loop the hero sheet starts.', where='in screen 5')
    st = (f'<div class="sk-center" style="display:flex;flex-direction:column;align-items:center;gap:8px"><span class="sk-lcd sk-petlcd" style="padding:10px 14px" role="img" aria-label="A pixel bee pet on the LCD">'
          f'<span class="sk-petdance">{pixbee()}</span>&nbsp;&nbsp;{lcd_svg("FED", p=2)}</span>'
          f'<span class="sk-small" style="font-weight:700">press and hold the LCD</span></div>')
    egg(15, 'LCD pet', st, 'Press and hold the LCD counter.', 'A pixel bee moves into the LCD for this trip and the digits read FED for a beat. Each checked item feeds it; finish the list and it does a 4-frame dance.', where='in screens 1, 2 + finale')
    drops = ''.join(f'<i class="sk-conf" style="left:{x}%;width:5px;height:11px;border-radius:3px;background:#7CC6F5;animation-duration:1.4s;animation-delay:-{d}s;--fx:-6px;--fr:0deg"></i>' for x, d in [(8, 0), (22, .5), (36, .9), (52, .2), (68, 1.1), (84, .7), (94, .3)])
    st = (drops + f'<div class="sk-center" style="display:flex;align-items:flex-end;gap:4px"><svg width="70" height="70" viewBox="0 0 48 48" aria-hidden="true">'
          f'<path d="M6 24C6 12 16 6 24 6S42 12 42 24C39 21 35 21 33 24C31 21 27 21 24 24C21 21 17 21 15 24C13 21 9 21 6 24Z" fill="#6E7BD0" transform="translate(1.6 2.2)"/>'
          f'<path d="M6 24C6 12 16 6 24 6S42 12 42 24C39 21 35 21 33 24C31 21 27 21 24 24C21 21 17 21 15 24C13 21 9 21 6 24Z" fill="#FF9FC8" stroke="#fff" stroke-width="5" stroke-linejoin="round" paint-order="stroke"/>'
          f'<path d="M6 24C6 12 16 6 24 6S42 12 42 24C39 21 35 21 33 24C31 21 27 21 24 24C21 21 17 21 15 24C13 21 9 21 6 24Z" fill="#FF9FC8" stroke="#B02E6B" stroke-width="2" stroke-linejoin="round"/>'
          f'<path d="M24 23V38Q24 42 20 42Q17 42 17 39" fill="none" stroke="#B02E6B" stroke-width="2.4" stroke-linecap="round"/><path d="M12 16Q14 11 19 9" stroke="#fff" stroke-width="2.4" fill="none" stroke-linecap="round"/></svg>'
          f'{ico("envelope", 46)}</div>')
    egg(16, 'Rainy day', st, 'Rain in the forecast for the store’s postcode.', 'A sakura umbrella sticker shelters the add bar, raindrop washi tape appears, and the toast says “bring the tote!”.')
    ants = ''.join(f'<span class="sk-ant" style="animation-delay:{-i * .42:.2f}s"><svg width="{30 if i == 2 else 22}" height="{30 if i == 2 else 22}" aria-hidden="true"><use href="#sticker-i-ant"/></svg></span>' for i in range(5))
    st = (f'<div class="sk-abs" style="left:10px;right:10px;top:70px;height:58px;background:#fff;border-radius:6px 6px 0 0;box-shadow:var(--sk-hs)">'
          f'<i class="sk-perfrow"></i></div>{ants}'
          f'<div class="sk-abs" style="left:14px;top:14px;display:flex;gap:6px">{ico("watermelon", 40)}{ico("loaf", 40)}{ico("lemonade", 40)}</div>')
    egg(17, 'Ant parade', st, 'The list holds three or more picnic things (watermelon, sandwich bread, lemonade…).', 'Once per session five ants march along the add bar’s perforation, the middle one carrying a strawberry, in 2-frame steps. They never cross the text field.')
    receipt = ('<div class="sk-receipt"><b>KIRAKIRA ★ BASKET</b><span>SAT · HILLCREST MKT</span><i></i><span>STICKERS ... 24</span><span>BASKETS .... 8</span><span>TIME ... 41 MIN</span><span>SHINY ...... 3</span><i></i><b>THANK YOU ♪</b></div>')
    st = (f'<div class="sk-abs" style="left:16px;top:26px">{lcd("24/24", "24 of 24", star=True, p=1.8)}</div>{receipt}')
    egg(18, 'LCD receipt', st, 'After the finale, press and hold the LCD.', 'The LCD prints a pastel thermal-paper receipt sticker in DotGothic16 with the trip’s stats (24 stickers · 8 baskets · 41 min · 3 shiny), and it is saved to the album.')
    return '<div class="sk-eggs">' + ''.join(E) + '</div>'


def section_f():
    head = (f'<div class="sk-bhead"><span class="sk-btab" style="--tp:var(--sk-peach-p);--tm:var(--sk-peach-m);--tt:var(--sk-peach-t);--pat:var(--sk-pat-clouds);--pats:13px"><span class="sk-bnum">F</span><h2>Motion &amp; easter eggs</h2></span>'
            f'<p class="sk-bsub">Tactile and stepped: stickers move in 2–3 hand-drawn poses (12 fps feel), layout moves smoothly. Item text never moves. Reduced motion pauses every loop and swaps peels, wobbles and glints for 120 ms fades.</p></div>')
    sub1 = f'<h3 class="sk-h3" style="margin:0 0 14px">Motion system</h3><div class="sk-mgrid">{motion_tiles()}</div>{timing_tables()}{cues()}'
    sub2 = f'<h3 class="sk-h3" style="margin:44px 0 14px">“List complete!” celebration</h3>{celebration()}'
    sub3 = (f'<h3 class="sk-h3" style="margin:44px 0 6px">Easter egg catalogue</h3>'
            f'<p class="sk-small" style="margin-bottom:16px;max-width:90ch">Eighteen secrets, and every one can be tried right here: each tile is a working demo with a <b>Try it</b> line, and most also work live in the phones above (in both themes). All of them can be switched off together under “Surprises” in phone 4, and none hides information or changes the walking order.</p>{eggs_r9()}')
    return f'<div class="sk-block" id="sticker-motion">{head}{sub1}{sub2}{sub3}</div>'


def hop_css():
    return ''.join(f".mk-sticker .sk-cele .c-r{i + 1}{{offset-path:path('{p}')}}" for i, p in enumerate(HOP_PATHS))


import json as _json
from deco import G57 as _G57
from live import script_data as _sd
SCRIPT = '<script>\n' + open('script.js').read().replace('/*G57*/{}', _json.dumps(_G57, separators=(',', ':'))).replace('/*@LIVE@*/', open('script_live.js').read() + '\n' + open('script_eggs.js').read()).replace('/*DATA*/{}', _sd()) + '\n</script>\n'
