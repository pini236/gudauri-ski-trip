import {chromium} from 'playwright';
const b=await chromium.launch({executablePath:process.env.CHROME});
const p=await b.newPage({viewport:{width:390,height:844},deviceScaleFactor:2});
const J=[];const add=(n,u)=>J.push([n,u]);
for(const v of ['a','b','c']){add(`${v}-ios-he-day`,`v=${v}&p=ios&l=he&t=day`);add(`${v}-and-he-day`,`v=${v}&p=and&l=he&t=day`);add(`${v}-ios-he-night`,`v=${v}&p=ios&l=he&t=night`);add(`${v}-and-en-night`,`v=${v}&p=and&l=en&t=night`);}
add('s-d1-ios-he-day','v=b&p=ios&l=he&t=day&d=1&s=free');add('s-d7-and-en-day','v=b&p=and&l=en&t=day&d=7&s=free');add('s-d8-ios-he-day','v=b&p=ios&l=he&t=day&d=8&s=free');add('s-kid-and-en-day','v=b&p=and&l=en&t=day&d=4&kid=1');add('s-stale-ios-he-day','v=b&p=ios&l=he&t=day&s=stale');
for(const [n,u] of J){await p.goto('file://'+process.cwd()+'/price.html?'+u);await p.waitForTimeout(200);await p.screenshot({path:`shots/${n}.png`});}
await b.close();console.log(J.length);
