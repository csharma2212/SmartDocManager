import test from 'node:test';
import assert from 'node:assert/strict';
import {handle} from '../src/worker.mjs';
const env={SMARTDOC_CLIENT_TOKEN:'test-secret',ALLOW_LEGACY_TOKEN:'true',GROQ_API_KEY:'fake'};
const request=()=>new Request('https://example.workers.dev/v1/classify',{method:'POST',headers:{authorization:'Bearer test-secret'},body:JSON.stringify({text:'Form 16 salary TDS'})});
test('valid Groq response normalized',async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async(_url,opts)=>{
    assert.ok(JSON.parse(opts.body).messages[1].content.includes('Form 16 salary TDS'));
    return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'Tax Document',specifics:'Form 16'})}}]}),{status:200});
  };
  try {
    const res=await handle(request(),env);
    assert.equal(res.status,200);
    const body=await res.json();
    assert.equal(body.category,'Tax Document');
    assert.equal(body.specifics,'Form 16');
    assert.equal(body.provider,'groq');
  } finally {globalThis.fetch=original;}
});
test('invalid model category rejected',async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async()=>new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'',specifics:'x'})}}]}),{status:200});
  try {assert.equal((await handle(request(),env)).status,502);} finally {globalThis.fetch=original;}
});
test('Gemini quota failure falls back to Groq',async()=>{
 const original=globalThis.fetch;const called=[];
 globalThis.fetch=async(url)=>{called.push(String(url));if(String(url).includes('generativelanguage'))return new Response('{}',{status:429});return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'Bank Statement',specifics:'Bank period',suggested_name:'HDFC_Oct26'})}}]}),{status:200});};
 try{
   const res=await handle(request(),{...env,GEMINI_API_KEY:'fake-gemini',USE_GROQ_ONLY:'false'});
   assert.equal(res.status,200);
   const body=await res.json();
   assert.equal(body.provider,'groq');
   assert.equal(called.length,2);
 }finally{globalThis.fetch=original;}
});
test('by default USE_GROQ_ONLY routes directly to Groq without calling Gemini',async()=>{
 const original=globalThis.fetch;const called=[];
 globalThis.fetch=async(url)=>{called.push(String(url));return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'Bank Statement',specifics:'Bank period',suggested_name:'HDFC_Oct26'})}}]}),{status:200});};
 try{
   const res=await handle(request(),{...env,GEMINI_API_KEY:'fake-gemini'});
   assert.equal(res.status,200);
   const body=await res.json();
   assert.equal(body.provider,'groq');
   assert.equal(called.length,1);
   assert.ok(!called[0].includes('generativelanguage'));
 }finally{globalThis.fetch=original;}
});
test('Both providers exhausted returns 429',async()=>{
 const original=globalThis.fetch;globalThis.fetch=async()=>new Response('{}',{status:429,headers:{'retry-after':'60'}});
 try{const res=await handle(request(),{...env,GEMINI_API_KEY:'fake-gemini'});assert.equal(res.status,429);assert.equal(res.headers.get('retry-after'),'60');}finally{globalThis.fetch=original;}
});

test('client runtime choice provider gemini makes Gemini primary API',async()=>{
 const original=globalThis.fetch;const called=[];
 globalThis.fetch=async(url)=>{called.push(String(url));return new Response(JSON.stringify({candidates:[{content:{parts:[{text:JSON.stringify({category:'Medical Report',specifics:'Blood test',proposed_name:'Lab_2026-11-15'})}]}}]}),{status:200});};
 const geminiReq=new Request('https://example.workers.dev/v1/classify',{method:'POST',headers:{authorization:'Bearer test-secret','content-type':'application/json'},body:JSON.stringify({text:'Lab CBC report',provider:'gemini'})});
 try{
   const res=await handle(geminiReq,{...env,GEMINI_API_KEY:'fake-gemini',GROQ_API_KEY:'fake-groq',USE_GROQ_ONLY:'true'});
   assert.equal(res.status,200);
   const body=await res.json();
   assert.ok(body.provider.includes('gemini'));
   assert.ok(called[0].includes('generativelanguage'));
 }finally{globalThis.fetch=original;}
});

test('client runtime choice provider groq makes Groq primary API',async()=>{
 const original=globalThis.fetch;const called=[];
 globalThis.fetch=async(url)=>{called.push(String(url));return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'Invoice',specifics:'Tax invoice',proposed_name:'Invoice_123'})}}]}),{status:200});};
 const groqReq=new Request('https://example.workers.dev/v1/classify',{method:'POST',headers:{authorization:'Bearer test-secret','content-type':'application/json','x-provider':'groq'},body:JSON.stringify({text:'Invoice bill'})});
 try{
   const res=await handle(groqReq,{...env,GEMINI_API_KEY:'fake-gemini',GROQ_API_KEY:'fake-groq',USE_GROQ_ONLY:'false'});
   assert.equal(res.status,200);
   const body=await res.json();
   assert.ok(body.provider.includes('groq'));
   assert.ok(called[0].includes('api.groq.com'));
 }finally{globalThis.fetch=original;}
});
