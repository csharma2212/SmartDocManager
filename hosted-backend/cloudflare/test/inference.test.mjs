import test from 'node:test';
import assert from 'node:assert/strict';
import {handle} from '../src/worker.mjs';
const env={SMARTDOC_CLIENT_TOKEN:'test-secret',GROQ_API_KEY:'fake'};
const request=()=>new Request('https://example.workers.dev/v1/classify',{method:'POST',headers:{authorization:'Bearer test-secret'},body:JSON.stringify({text:'Form 16 salary TDS'})});
test('valid Groq response normalized',async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async(_url,opts)=>{
    assert.equal(JSON.parse(opts.body).messages[1].content,'Form 16 salary TDS');
    return new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'Tax Document',specifics:'Form 16'})}}]}),{status:200});
  };
  try {const res=await handle(request(),env);assert.equal(res.status,200);assert.deepEqual(await res.json(),{category:'Tax Document',specifics:'Form 16'});} finally {globalThis.fetch=original;}
});
test('invalid model category rejected',async()=>{
  const original=globalThis.fetch;
  globalThis.fetch=async()=>new Response(JSON.stringify({choices:[{message:{content:JSON.stringify({category:'Made-up',specifics:'x'})}}]}),{status:200});
  try {assert.equal((await handle(request(),env)).status,502);} finally {globalThis.fetch=original;}
});
