import { chromium } from '@playwright/test';
import { readFile } from 'node:fs/promises';
const origin='http://127.0.0.1:18081';
const credentials=JSON.parse((await readFile(new URL('../../college-events/target/security-review-2026-10-19/session-credentials.json',import.meta.url),'utf8')).replace(/^\uFEFF/,''));
if(new URL(credentials.url).origin!==origin) throw new Error('WRONG_SYNTHETIC_ORIGIN');
const browser=await chromium.launch({channel:'msedge',headless:true});
let stage='navigate'; const paths=[]; const statuses=[]; const violations=[];
try {
 const context=await browser.newContext({baseURL:origin}); const page=await context.newPage(); page.setDefaultTimeout(15000);
 page.on('framenavigated',frame=>{if(frame===page.mainFrame()) paths.push(new URL(frame.url()).pathname);});
 page.on('response',r=>{if(new URL(r.url()).origin===origin && ['/login','/app/'].includes(new URL(r.url()).pathname)) statuses.push({path:new URL(r.url()).pathname,status:r.status()});});
 await page.addInitScript(()=>document.addEventListener('securitypolicyviolation',e=>{(window.__violations??=[]).push(e.violatedDirective)}));
 await page.goto('/app/sign-in',{waitUntil:'domcontentloaded',timeout:20000});
 stage='fill';await page.locator('[name=username]').fill('student@example.invalid');await page.locator('[name=password]').fill(credentials.password);
 stage='submit'; await page.locator('form[action="/login"] button[type=submit]').click({noWaitAfter:true});
 stage='settle';await page.waitForURL(url=>url.pathname==='/app/',{waitUntil:'domcontentloaded',timeout:20000});
 stage='session';const response=await page.request.get('/api/v1/session');
 violations.push(...await page.evaluate(()=>window.__violations??[]));
 console.log(JSON.stringify({stage,paths,statuses,sessionStatus:response.status(),violations}));
} catch {console.log(JSON.stringify({failedAt:stage,paths,statuses}));process.exitCode=1;}
finally {credentials.password='';await browser.close();}
