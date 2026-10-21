// 출처: 친구 아티팩트 "League of Legends Team Maker"의 app.js (사용자가 친구에게 사용 허락을 받음)
// app.js SHA-256: f7194a4543a63ba01dbea92f9e41c4369c46f71c55aa5e2c87ffabfaf057dd94
// 옮긴 줄: 1(LANES), 22(pts), 23(byId), 25(laneName), 79(rank), 261~336(PERMS ... recalcResult)
// 아래 "원본" 구간의 함수 본문은 한 글자도 고치지 않았다. 바깥 감싸개만 우리가 붙였다.
// 감싸개: 전역(data, team, ui)을 인자로 받고(team.fix 기본 {}), Math.random을 0.99로 고정해 좌우 교체가 없게 한다.
'use strict';

function createReference({ data, team, ui }) {
  const Math = Object.create(globalThis.Math, { random: { value: () => 0.99 } });
  if (!team.fix) team.fix = {};

  // ---- 원본 시작 ----
const LANES=[['top','탑'],['jg','정글'],['mid','미드'],['adc','원딜'],['sup','서폿']];
const pts=(v,k)=>(+data.score.base[v.t]||0)+(+data.score.sub[v.s]||0)+(+(data.score.lane||{})[k]||0);
const byId=id=>data.players.find(p=>p.id===id);
const laneName=k=>LANES.find(l=>l[0]===k)[1];
const rank=v=>(7-v.t)*3+({'상':2,'중':1,'하':0}[v.s]||0);
const PERMS=(()=>{const out=[];const go=(a,rest)=>{if(!rest.length){out.push(a);return}rest.forEach((x,i)=>go([...a,x],rest.filter((_,j)=>j!==i)))};go([],[0,1,2,3,4]);return out})();
function laneAssignments(teamPs){ // 5명을 5라인에 배치하는 모든 경우
  const out=[];
  for(const perm of PERMS){
    const slots=[];let sum=0,ok=true;
    for(let i=0;i<5;i++){const k=LANES[perm[i]][0],p=teamPs[i],fx=team.mode==='auto'&&team.fix[p.id];
      if(fx&&fx!==k){ok=false;break}
      const v=p.lanes[k];if(!v&&!fx){ok=false;break}
      const s=v?pts(v,k):pts({t:6,s:'하'},k);slots[perm[i]]={p,k,v:v||null,s};sum+=s}
    if(ok)out.push({slots,sum});
  }
  return out;
}
const cost=(diff,gap)=>diff*3+gap;
function evaluate(A,B){
  let best=null;
  for(const a of A)for(const b of B){
    const diff=Math.abs(a.sum-b.sum);let gap=0;
    for(let l=0;l<5;l++){const g=Math.abs(a.slots[l].s-b.slots[l].s);if(g>gap)gap=g}
    const c=cost(diff,gap),tot=a.sum+b.sum;
    if(!best||c<best.c||(c===best.c&&tot>best.tot))best={c,tot,diff,gap,blue:a.slots,red:b.slots,bs:a.sum,rs:b.sum};
  }
  return best;
}
function okTogether(blueIds){
  return team.together.every(([a,b])=>!byId(a)||!byId(b)||blueIds.has(a)===blueIds.has(b));
}
function makeTeams(){
  const res=[];
  if(team.mode==='auto'){
    const ids=team.chosen.filter(byId);
    if(ids.length!==10)return `참가자를 정확히 10명 골라 주세요. 지금 ${ids.length}명이에요.`;
    const P=ids.map(byId);
    const fx={};ids.forEach(id=>{const k=team.fix[id];if(k)(fx[k]=fx[k]||[]).push(byId(id).name)});
    const over=Object.entries(fx).find(([,ns])=>ns.length>2);
    if(over)return `${laneName(over[0])}에 ${over[1].length}명이 고정돼 있어요. 한 라인은 양 팀 합쳐 2명까지만 고정할 수 있어요.`;
    const cache=new Map();
    const assign=idx=>{const key=idx.join(',');if(!cache.has(key))cache.set(key,laneAssignments(idx.map(i=>P[i])));return cache.get(key)};
    for(let m=0;m<1024;m++){
      if(!(m&1))continue;
      let c=0;for(let i=0;i<10;i++)if(m>>i&1)c++;if(c!==5)continue;
      const bi=[],ri=[];for(let i=0;i<10;i++)(m>>i&1?bi:ri).push(i);
      if(!okTogether(new Set(bi.map(i=>ids[i]))))continue;
      const A=assign(bi),B=assign(ri);if(!A.length||!B.length)continue;
      res.push(evaluate(A,B));
    }
    if(!res.length){ui.results=null;return Object.keys(fx).length?'라인 고정 때문에 가능한 조합이 없어요. 고정한 라인 때문에 다른 사람들이 갈 라인이 모자라요. 고정을 하나씩 풀어 보세요.':'가능한 조합이 없어요. 고른 사람들의 라인이 겹쳐서 양 팀 모두 5개 라인을 채울 수 없거나, 같은 팀 조건 때문에 막혀요.'}
  }else{
    for(const [k,n] of LANES){const pr=team.pick[k]||[];if(!pr[0]||!pr[1]||!byId(pr[0])||!byId(pr[1]))return `${n} 라인에 2명을 모두 골라 주세요.`}
    const ids=LANES.flatMap(([k])=>team.pick[k]);
    const dup=ids.find((id,i)=>ids.indexOf(id)!==i);
    if(dup)return `${byId(dup).name}님이 두 번 선택됐어요.`;
    for(let c=1;c<32;c+=2){
      const blue=[],red=[];
      LANES.forEach(([k],i)=>{const [a,b]=team.pick[k].map(byId);
        const x={p:a,k,v:a.lanes[k],s:pts(a.lanes[k],k)},y={p:b,k,v:b.lanes[k],s:pts(b.lanes[k],k)};
        if(c>>i&1){blue.push(x);red.push(y)}else{blue.push(y);red.push(x)}});
      if(!okTogether(new Set(blue.map(x=>x.p.id))))continue;
      const bs=blue.reduce((s,x)=>s+x.s,0),rs=red.reduce((s,x)=>s+x.s,0);
      const diff=Math.abs(bs-rs);const gap=Math.max(...blue.map((x,i)=>Math.abs(x.s-red[i].s)));
      res.push({c:cost(diff,gap),tot:bs+rs,diff,gap,blue,red,bs,rs});
    }
    if(!res.length)return '같은 팀 조건을 만족하는 조합이 없어요. 같은 라인의 두 사람은 같은 팀이 될 수 없어요.';
  }
  res.sort((a,b)=>a.c-b.c||b.tot-a.tot);
  ui.results=res.slice(0,4).map(r=>{if(Math.random()<.5){[r.blue,r.red]=[r.red,r.blue];[r.bs,r.rs]=[r.rs,r.bs]}return r});
  ui.sel=0;ui.resSel=null;ui.coin=null;return '';
}

function slotFor(p,k){ // 라인 티어가 없으면 6티어 하 점수로 계산
  const v=p.lanes[k]||null;return {p,k,v,s:v?pts(v,k):pts({t:6,s:'하'},k)};
}
function recalcResult(r){
  r.bs=r.blue.reduce((s,x)=>s+x.s,0);r.rs=r.red.reduce((s,x)=>s+x.s,0);
  r.diff=Math.abs(r.bs-r.rs);r.gap=Math.max(...r.blue.map((x,i)=>Math.abs(x.s-r.red[i].s)));r.c=cost(r.diff,r.gap);
}
  // ---- 원본 끝 ----

  return { LANES, pts, byId, laneName, rank, PERMS, laneAssignments, cost, evaluate, okTogether, makeTeams, slotFor, recalcResult };
}

module.exports = { createReference };
