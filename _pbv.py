import os, sys, json, base64, io, zipfile, time, urllib.request, urllib.error

TOK = os.environ["GH_TOKEN"]
REPO = "N1997-alt/travel-app"
BASE = f"https://api.github.com/repos/{REPO}"
HDR = {"Authorization": f"Bearer {TOK}", "Accept": "application/vnd.github+json", "User-Agent": "wb"}

def req(method, url, data=None, retries=3):
    last=None
    for i in range(retries):
        try:
            body = json.dumps(data).encode() if data is not None else None
            r = urllib.request.Request(url, data=body, headers=HDR, method=method)
            resp = urllib.request.urlopen(r, timeout=120)
            return resp.read().decode("utf-8","ignore")
        except urllib.error.HTTPError as e:
            loc=e.headers.get("Location")
            if loc:
                try:
                    return urllib.request.urlopen(loc, timeout=120).read().decode("utf-8","ignore")
                except Exception as ex: last=ex
            else:
                last=e
        except Exception as e:
            last=e
        time.sleep(3)
    raise last

ROOT = r"C:/Users/23790/WorkBuddy/2026-08-31-12-18-32/travel-app-repo"

base_sha = json.loads(req("GET", f"{BASE}/git/ref/heads/main"))["object"]["sha"]
print("base_sha", base_sha)

SKIP={".git"}
entries=[]
for dp,dns,fns in os.walk(ROOT):
    dns[:]=[d for d in dns if d not in SKIP]
    for fn in fns:
        full=os.path.join(dp,fn)
        rel=os.path.relpath(full,ROOT).replace("\\","/")
        if rel.startswith(".git/"): continue
        with open(full,"rb") as f: content=f.read()
        blob=json.loads(req("POST",f"{BASE}/git/blobs",{"content":base64.b64encode(content).decode(),"encoding":"base64"}))
        entries.append({"path":rel,"mode":"100644","type":"blob","sha":blob["sha"]})
        print("  blob",rel,blob["sha"][:8])

try:
    tree_sha=json.loads(req("POST",f"{BASE}/git/trees",{"base_tree":base_sha,"tree":entries}))["sha"]
except Exception:
    parent=json.loads(req("GET",f"{BASE}/git/trees/{base_sha}?recursive=1"))
    pmap={t["path"]:t["sha"] for t in parent["tree"]}
    new=[]
    for e in entries:
        e2=dict(e); e2["sha"]=pmap.get(e["path"],e["sha"]); new.append(e2)
    tree_sha=json.loads(req("POST",f"{BASE}/git/trees",{"base_tree":base_sha,"tree":new}))["sha"]
print("tree_sha",tree_sha)

new_sha=json.loads(req("POST",f"{BASE}/git/commits",{"message":"升级 cordova-android 至 13（兼容 Gradle 9/Groovy4，移除对 XmlParser 的依赖）","tree":tree_sha,"parents":[base_sha]}))["sha"]
print("new commit",new_sha)
req("PATCH",f"{BASE}/git/refs/heads/main",{"sha":new_sha,"force":True})

run_id=None
for _ in range(40):
    runs=json.loads(req("GET",f"{BASE}/actions/runs?head_sha={new_sha}&per_page=5")).get("workflow_runs",[])
    if runs:
        run_id=runs[0]["id"]; break
    time.sleep(5)
if not run_id:
    print("NO RUN"); sys.exit(1)
print("run",run_id)

conclusion=None
for _ in range(140):
    j=json.loads(req("GET",f"{BASE}/actions/runs/{run_id}"))
    conclusion=j.get("conclusion"); status=j.get("status")
    print("  status",status,"conclusion",conclusion)
    if status=="completed": break
    time.sleep(10)

if conclusion!="success":
    print("=== BUILD FAILED, tail of logs ===")
    try:
        data=req("GET",f"{BASE}/actions/runs/{run_id}/logs")
        z=zipfile.ZipFile(io.BytesIO(data))
        for n in z.namelist():
            txt=z.read(n).decode("utf-8","ignore")
            if any(k in txt for k in ["FAILED","What went wrong","error:","BUILD","XmlParser","Task :app","> "]):
                for l in txt.splitlines()[-90:]:
                    if l.strip(): print(l[:400])
    except Exception as e:
        print("log fetch err",e)
    sys.exit(2)

arts=json.loads(req("GET",f"{BASE}/actions/runs/{run_id}/artifacts")).get("artifacts",[])
if not arts: print("NO ARTIFACT"); sys.exit(3)
bdl=req("GET",f"{BASE}/actions/artifacts/{arts[0]['id']}/zip")
with open(os.path.join(ROOT,"apk_out.zip"),"wb") as f: f.write(bdl)
z=zipfile.ZipFile(io.BytesIO(bdl))
apk_name=[n for n in z.namelist() if n.endswith("app-debug.apk")][0]
out_dir=os.path.join(ROOT,"..","apk_out"); os.makedirs(out_dir,exist_ok=True)
out_path=os.path.join(out_dir,"app-debug.apk")
with open(out_path,"wb") as f: f.write(z.read(apk_name))
print("APK",os.path.abspath(out_path),os.path.getsize(out_path))

apk=zipfile.ZipFile(out_path); an=apk.namelist()
plug=[n for n in an if n.endswith("cordova_plugins.js")]
print("saveandshare files:",[n for n in an if "saveandshare" in n.lower()])
if plug:
    t=apk.read(plug[0]).decode("utf-8","ignore")
    print("plugin registered:", "cn.workbuddy.saveandshare" in t)
print("SaveAndShare.class:", any("SaveAndShare.class" in n for n in an))
print("DONE")
