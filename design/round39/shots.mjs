import {chromium} from 'playwright';
import fs from 'fs';
const b=await chromium.launch({executablePath:process.env.CHROME});
const p=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:2});
const J=[];
const add=(n,a,s,pl,l,t,extra='')=>J.push([n,`a=${a}&s=${s}&p=${pl}&l=${l}&t=${t}${extra}`]);
for(const a of ['a','b','c']){add(`${a}-ios-he-day`,a,'far','ios','he','day');add(`${a}-and-he-day`,a,'far','and','he','day');add(`${a}-ios-he-night`,a,'far','ios','he','night');add(`${a}-and-en-night`,a,'far','and','en','night');}
for(const s of ['eve','today','live','late','moved','after']){add(`s-${s}-ios-he-day`,'b',s,'ios','he','day');add(`s-${s}-and-en-day`,'b',s,'and','en','day');}
add('s-far-notrip-ios-he-day','b','far','ios','he','day','&trip=0');
for(const [n,u] of J){await p.goto('file://'+process.cwd()+'/open.html?'+u);await p.waitForTimeout(250);await p.screenshot({path:`shots/${n}.png`});}
await b.close();console.log(J.length);
