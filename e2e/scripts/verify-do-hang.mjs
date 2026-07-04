import { chromium } from "playwright";
const BASE="https://shepard.nuclide.systems", KC="https://shepard-auth.nuclide.systems";
const LUMEN="019eb019-d49b-7131-b2d2-3f3107d36a4f", TR004="019eb019-d8e9-7991-96e7-75ca1ab3d6be";
async function login(page){
  await page.goto(BASE+"/auth/signIn",{waitUntil:"domcontentloaded"});
  const btn=page.getByRole("button",{name:/sign in|login/i}).first();
  if(await btn.isVisible().catch(()=>false))await btn.click().catch(()=>{});
  try{await page.waitForURL(`${KC}/realms/shepard-demo/**`,{timeout:8000});
    await page.fill("#username","flo");await page.fill("#password","flo-demo");await page.click('[type="submit"]');}catch{}
  await page.waitForSelector("text=SIGN OUT",{timeout:30000});
}
const ctx=await(await chromium.launch()).newContext({viewport:{width:1920,height:1080}});
const page=await ctx.newPage();await login(page);
const t0=Date.now();
await page.goto(`${BASE}/collections/${LUMEN}/dataobjects/${TR004}`,{waitUntil:"domcontentloaded"});
let rendered=false;
for(let i=0;i<25;i++){
  await page.waitForTimeout(1000);
  const hasDesc=await page.getByText("Description",{exact:true}).first().isVisible().catch(()=>false);
  const hasSpinner=await page.locator(".v-progress-circular").first().isVisible().catch(()=>false);
  if(hasDesc&&!hasSpinner){rendered=true;break;}
}
console.log(`TR004 (200, no numeric id): rendered=${rendered} after ${((Date.now()-t0)/1000).toFixed(1)}s`);
await page.screenshot({path:"/tmp/do-hang-LIVE-before-TR004.png",fullPage:false});
await ctx.close();process.exit(0);
