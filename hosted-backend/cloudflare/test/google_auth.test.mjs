import test from 'node:test';
import assert from 'node:assert/strict';
import {handle} from '../src/worker.mjs';
const env={GOOGLE_CLIENT_IDS:'desktop.apps.googleusercontent.com,web.apps.googleusercontent.com',ALLOWED_GOOGLE_EMAILS:'allowed@example.com',GROQ_API_KEY:'fake'};
function req(token='test-id-token'){return new Request('https://worker.example/v1/classify',{method:'POST',headers:{authorization:`Bearer ${token}`},body:JSON.stringify({text:'sample document'})});}
test('rejects Google ID token with wrong audience',async()=>{
 const old=globalThis.fetch;
 globalThis.fetch=async()=>new Response(JSON.stringify({aud:'other.apps.googleusercontent.com',iss:'https://accounts.google.com',exp:String(Math.floor(Date.now()/1000)+3600),sub:'123',email:'allowed@example.com',email_verified:'true'}),{status:200});
 try{assert.equal((await handle(req(),env)).status,401);}finally{globalThis.fetch=old;}
});
test('rejects Google account not on allowlist',async()=>{
 const old=globalThis.fetch;
 globalThis.fetch=async()=>new Response(JSON.stringify({aud:'desktop.apps.googleusercontent.com',iss:'https://accounts.google.com',exp:String(Math.floor(Date.now()/1000)+3600),sub:'123',email:'unlisted@example.com',email_verified:'true'}),{status:200});
 try{assert.equal((await handle(req(),env)).status,401);}finally{globalThis.fetch=old;}
});
test('rejects expired Google ID token',async()=>{
 const old=globalThis.fetch;
 globalThis.fetch=async()=>new Response(JSON.stringify({aud:'desktop.apps.googleusercontent.com',iss:'https://accounts.google.com',exp:'1',sub:'123',email:'allowed@example.com',email_verified:'true'}),{status:200});
 try{assert.equal((await handle(req(),env)).status,401);}finally{globalThis.fetch=old;}
});
