"""Capacity acceptance for a disposable instance; uses and archives its own test namespace"""
import concurrent.futures,json,os,threading,time,urllib.error,urllib.request,uuid
BASE=os.environ.get('MOCKLAB_TEST_URL','http://127.0.0.1:8080')
def call(path,body=None):
    data=json.dumps(body).encode() if body is not None else None
    request=urllib.request.Request(BASE+path,data=data,headers={'Content-Type':'application/json','X-MockLab-Request':'1'},method='POST' if body is not None else 'GET')
    try:
        with urllib.request.urlopen(request,timeout=15) as response:return response.status,response.read().decode()
    except urllib.error.HTTPError as response:return response.code,response.read().decode()
def api(path,body):
    status,result=call('/api'+path,body);assert status==200,(status,result);return json.loads(result)
system=api('/systems',{'name':'Capacity acceptance','systemKey':'capacity-'+uuid.uuid4().hex[:10]})
try:
    rule={'id':'default','name':'default','default':True,'enabled':True,'priority':0,'mode':'script','status':200,'contentType':'text/plain','headers':{},'delay':0,'code':'function main(){ const end=Date.now()+400; while(Date.now()<end){} return {body:"ok"}; }'}
    config={'name':'Capacity test','method':'GET','path':'/capacity','enabled':True,'rules':[rule]}
    endpoint=api('/endpoints',{'systemId':system['id'],'draft':config})
    api('/endpoints/'+str(endpoint['id'])+'/publish',{'revision':endpoint['revision'],'config':config})
    ready=threading.Event()
    def request():ready.wait();return call('/m/'+system['systemKey']+'/capacity')[0]
    started=time.monotonic()
    with concurrent.futures.ThreadPoolExecutor(max_workers=140) as pool:
        futures=[pool.submit(request) for _ in range(140)]
        ready.set();statuses=[future.result() for future in futures]
    counts={code:statuses.count(code) for code in sorted(set(statuses))}
    assert counts.get(503,0)>0,counts
    assert all(code in [200,503,504] for code in statuses),counts
    print('PASS bounded script capacity under 140 concurrent HTTP requests:',counts)
    print('Elapsed seconds:',round(time.monotonic()-started,2))
finally:
    system['archived']=True;system['enabled']=False;api('/systems',system)
