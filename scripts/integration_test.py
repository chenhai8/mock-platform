"""Run against a disposable MockLab instance; creates and archives an isolated test system"""
import concurrent.futures
import copy
import json
import os
import time
import urllib.error
import urllib.request
import uuid

BASE=os.environ.get('MOCKLAB_TEST_URL','http://127.0.0.1:8080')
CHECKS=[]

def call(path,method='GET',value=None,raw=None,headers=None):
    content=json.dumps(value,ensure_ascii=False).encode() if value is not None else raw
    h={'X-MockLab-Request':'1',**(headers or {})}
    if value is not None:h['Content-Type']='application/json'
    request=urllib.request.Request(BASE+path,data=content,method=method,headers=h)
    try:
        with urllib.request.urlopen(request,timeout=20) as r:return r.status,dict(r.headers),r.read().decode('utf-8')
    except urllib.error.HTTPError as r:return r.code,dict(r.headers),r.read().decode('utf-8')

def api(path,method='GET',value=None,expected=200):
    status,headers,body=call('/api'+path,method,value)
    assert status==expected,(path,status,body)
    return json.loads(body)

def check(name,condition):
    assert condition,name
    CHECKS.append(name)
    print('PASS',name,flush=True)

def scene(code='{"ok":true}',**kw):
    return {'id':str(uuid.uuid4()),'name':'default','default':True,'enabled':True,'priority':0,'field':'body.orderId','operator':'eq','value':'','mode':'raw','status':200,'contentType':'application/json; charset=utf-8','headers':{},'delay':0,'code':code,**kw}

def config(path,method='POST',rules=None):
    return {'name':'Contract test','description':'integration','method':method,'path':path,'enabled':True,'rules':rules or [scene()]}

def create(cfg,publish=True):
    endpoint=api('/endpoints','POST',{'systemId':system['id'],'draft':cfg})
    if publish:endpoint=api('/endpoints/'+str(endpoint['id'])+'/publish','POST',{'revision':endpoint['revision'],'config':cfg})
    return endpoint

def publish(endpoint,cfg):return api('/endpoints/'+str(endpoint['id'])+'/publish','POST',{'revision':endpoint['revision'],'config':cfg})
def live(path,method='POST',body=None,raw=None,headers=None):return call('/m/'+system['systemKey']+path,method,body,raw,headers)

system=api('/systems','POST',{'name':'自动验收 '+time.strftime('%H:%M:%S'),'systemKey':'accept-'+uuid.uuid4().hex[:12],'description':'自动生成的验收系统，测试完成后归档','enabled':True})
try:
    check('create system',system['id']>0)
    check('duplicate system rejected',api('/systems','POST',{'name':'duplicate','systemKey':system['systemKey']},409)['error']['code'] in ['CONFLICT','SYSTEM_KEY_EXISTS'])
    text=' {\n  "ok": true, "中文": "原样"\n}\n'
    e=create(config('/raw',rules=[scene(text,headers={'X-Scene':'raw'})]),False)
    check('unpublished route 404',live('/raw')[0]==404)
    e=publish(e,e['draft'])
    status,headers,body=live('/raw')
    check('raw body preserved byte-for-byte',status==200 and body==text)
    check('custom response header',headers.get('x-scene',headers.get('X-Scene'))=='raw')
    old_revision=e['revision'];cfg=copy.deepcopy(e['draft']);cfg['rules'][0]['code']='new draft'
    e=api('/endpoints/'+str(e['id'])+'/draft','PUT',{'revision':old_revision,'config':cfg})
    check('draft isolated from live response',live('/raw')[2]==text)
    check('stale save returns 409',api('/endpoints/'+str(e['id'])+'/draft','PUT',{'revision':old_revision,'config':cfg},409)['error']['code']=='REVISION_CONFLICT')
    debug={'source':'draft','revision':e['revision'],'config':cfg,'method':'POST','path':'/raw','query':'','headers':{},'rawBody':''}
    check('unsaved debug uses provided config',api('/endpoints/'+str(e['id'])+'/debug','POST',debug)['body']=='new draft')
    debug['source']='published';debug['config']=None
    check('published debug uses server snapshot',api('/endpoints/'+str(e['id'])+'/debug','POST',debug)['body']==text)
    e=publish(e,cfg);check('publish changes live body',live('/raw')[2]=='new draft')
    e=api('/endpoints/'+str(e['id'])+'/rollback','POST',{'revision':e['revision'],'version':1,'expectedVersion':e['publishedVersion']})
    check('rollback creates new version and preserves draft',e['publishedVersion']==3 and live('/raw')[2]==text and e['draft']['rules'][0]['code']=='new draft')
    rules=[scene('fallback'),scene('success',**{'default':False,'name':'success','priority':100,'value':'1001'}),scene('failure',**{'default':False,'name':'failure','priority':90,'value':'1002'})]
    create(config('/orders',rules=rules))
    check('condition success',live('/orders',body={'orderId':1001})[2]=='success')
    check('condition failure',live('/orders',body={'orderId':'1002'})[2]=='failure')
    check('default fallback',live('/orders',body={'orderId':'9999'})[2]=='fallback')
    check('invalid JSON 400',live('/orders',raw=b'{',headers={'Content-Type':'application/json'})[0]==400)
    check('unsupported binary 415',live('/orders',raw=b'xx',headers={'Content-Type':'application/octet-stream'})[0]==415)
    create(config('/materials/{id}','GET',[scene('function main(request,mock){return {body:{id:request.pathParams.id,query:request.query,requestId:mock.uuid(),now:mock.now()}}}',mode='script')]))
    status,_,body=live('/materials/A01?tag=a&tag=b','GET')
    value=json.loads(body)
    check('script dynamic path parameters',status==200 and value.get('id')=='A01')
    check('repeated Query parameters',value.get('query',{}).get('tag')==['a','b'])
    check('UUID v4 helper',uuid.UUID(value['requestId']).version==4)
    check('method mismatch 405',live('/materials/A01','POST')[0]==405)
    create(config('/materials/latest','POST',[scene('latest')]))
    status,headers,_=live('/materials/latest','GET')
    check('static route wins before method matching',status==405 and headers.get('Allow')=='POST')
    check('trailing slash distinct',live('/materials/A01/','GET')[0]==404)
    create(config('/head','HEAD',[scene('suppressed')]))
    check('HEAD body omitted',live('/head','HEAD')[0]==200 and live('/head','HEAD')[2]=='')
    create(config('/options','OPTIONS',[scene('options')]))
    check('explicit OPTIONS reaches mock',live('/options','OPTIONS')[2]=='options')
    for suffix,code in [('throw','function main(){throw Error("test failure")}'),('missing','function main(){return {status:200}}'),('bigint','function main(){return {body:1n}}'),('cycle','function main(){const a={};a.a=a;return {body:a}}'),('async','async function main(){return {body:"async"}}')]:
        create(config('/'+suffix,rules=[scene(code,mode='script')]))
        check('script contract '+suffix,live('/'+suffix)[0]==500)
    create(config('/timeout',rules=[scene('function main(){while(true){}}',mode='script')]))
    status,headers,_=live('/timeout')
    check('infinite loop terminated',status==504)
    check('raw still works after script timeout',live('/raw')[0]==200)
    check('workers recover for next script',live('/materials/A02','GET')[0]==200)
    for name,code in [('file','function main(){return {body:Java.type("java.nio.file.Files")}}'),('network','function main(){return {body:fetch("https://example.com")}}'),('process','function main(){return {body:process.env}}')]:
        create(config('/deny-'+name,rules=[scene(code,mode='script')]))
        check('deny '+name,live('/deny-'+name)[0]==500)
    malformed=config('/syntax',rules=[scene('function main( {',mode='script')]);e2=create(malformed,False)
    check('invalid script rejected at publication',api('/endpoints/'+str(e2['id'])+'/publish','POST',{'revision':e2['revision'],'config':malformed},400)['error']['code']=='INVALID_SCRIPT')
    create(config('/form',rules=[scene('function main(request){return {body:request.body}}',mode='script')]))
    check('form body parsing',json.loads(live('/form',raw=b'item=a&item=b',headers={'Content-Type':'application/x-www-form-urlencoded'})[2])=={'item':['a','b']})
    create(config('/null',rules=[scene('function main(){return {body:null}}',mode='script')]))
    check('null body is JSON null',live('/null')[2]=='null')
    create(config('/empty',rules=[scene('',status=204)]))
    check('204 has no body',live('/empty')[0]==204 and live('/empty')[2]=='')
    create(config('/slow',rules=[scene('slow',delay=1000)]))
    start=time.monotonic()
    with concurrent.futures.ThreadPoolExecutor() as pool:
        slow=pool.submit(live,'/slow');time.sleep(.05);fast=live('/raw');fast_duration=time.monotonic()-start;slow_response=slow.result()
    check('delay does not block raw requests',fast[0]==200 and fast_duration<.8 and time.monotonic()-start>=1)
    check('oversized request rejected',live('/orders',raw=b'x'*(1048576+1),headers={'Content-Type':'text/plain'})[0]==413)
    check('parameter names do not avoid route conflict',api('/endpoints','POST',{'systemId':system['id'],'draft':config('/materials/{other}','GET')},409)['error']['code']=='ROUTE_CONFLICT')
    duplicate_default=config('/invalid-default',rules=[scene(),scene()])
    check('duplicate defaults rejected',api('/endpoints','POST',{'systemId':system['id'],'draft':duplicate_default},400)['error']['code']=='INVALID_CONFIG')
    invalid_header=config('/invalid-header',rules=[scene(headers={'Content-Length':'20'})])
    check('protocol header injection rejected',api('/endpoints','POST',{'systemId':system['id'],'draft':invalid_header},400)['error']['code']=='INVALID_CONFIG')
    invalid_newline=config('/invalid-newline',rules=[scene(headers={'X-Scene':'x\r\nInjected: yes'})])
    check('response header CRLF rejected',api('/endpoints','POST',{'systemId':system['id'],'draft':invalid_newline},400)['error']['code']=='INVALID_CONFIG')
    create(config('/large-output',rules=[scene('function main(){return {body:"x".repeat(2097153)}}',mode='script')]))
    check('oversized response controlled',live('/large-output')[0]==500)
    create(config('/console-output',rules=[scene('function main(){console.log("not protocol");return {body:"ok"}}',mode='script')]))
    check('script console cannot corrupt protocol',live('/console-output')[2]=='ok')
    create(config('/memory',rules=[scene('function main(){return {body:new Array(100000000).fill(1)}}',mode='script')]))
    check('worker memory exhaustion controlled',live('/memory')[0] in [500,504])
    check('raw unaffected by memory exhaustion',live('/orders',body={'orderId':'1001'})[2]=='success')
    rollback_endpoint=create(config('/old-path',rules=[scene('old')]))
    changed=copy.deepcopy(rollback_endpoint['draft']);changed['path']='/new-path'
    rollback_endpoint=publish(rollback_endpoint,changed)
    check('path publication removes old route',live('/old-path')[0]==404 and live('/new-path')[2]=='old')
    create(config('/old-path',rules=[scene('occupied')]))
    check('rollback conflicting route rejected',api('/endpoints/'+str(rollback_endpoint['id'])+'/rollback','POST',{'revision':rollback_endpoint['revision'],'version':1,'expectedVersion':rollback_endpoint['publishedVersion']},409)['error']['code']=='ROUTE_CONFLICT')
    check('failed rollback leaves active version untouched',live('/new-path')[2]=='old' and live('/old-path')[2]=='occupied')
    e=api('/endpoints/'+str(e['id'])+'/lifecycle','POST',{'revision':e['revision'],'archived':True})
    check('archive immediately closes route',live('/raw')[0]==404)
    e=api('/endpoints/'+str(e['id'])+'/lifecycle','POST',{'revision':e['revision'],'archived':False})
    check('restore requires publication',e.get('published') is None and live('/raw')[0]==404)
    time.sleep(1)
    page=api('/logs?current=1&size=100&systemId='+str(system['id']))
    check('server-side call logs persisted',page['total']>20 and any(v['errorCode']=='SCRIPT_TIMEOUT' for v in page['records']))
    check('logs omit bodies by default',all(v.get('requestDetail') is None for v in page['records']))
    system['enabled']=False;system=api('/systems','POST',system)
    check('system disable is immediate',live('/orders',body={'orderId':'1001'})[0]==404)
    print('\nRESULT:',len(CHECKS),'checks passed',flush=True)
finally:
    try:
        system['archived']=True;system['enabled']=False;api('/systems','POST',system)
    except Exception as error:print('Unable to archive test system:',error)
