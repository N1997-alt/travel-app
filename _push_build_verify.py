import os, sys, json, base64, io, zipfile, time, urllib.request, urllib.error, tempfile

TOK = os.environ["GH_TOKEN"]
REPO = "N1997-alt/travel-app"
BASE = f"https://api.github.com/repos/{REPO}"
HDR = {"Authorization": f"Bearer {TOK}", "Accept": "application/vnd.github+json", "User-Agent": "wb"}

def req(method, url, data=None, extra=None):
    headers = dict(HDR)
    if extra: headers.update(extra)
    body = json.dumps(data).encode() if data is not None else None
    r = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        resp = urllib.request.urlopen(r, timeout=120)
        return resp, resp.read().decode("utf-8", "ignore")
    except urllib.error.HTTPError as e:
        return None, e.read().decode("utf-8", "ignore")

ROOT = r"C:/Users/23790/WorkBuddy/2026-08-31-12-18-32/travel-app-repo"

# 1) get base sha
_, b = req("GET", f"{BASE}/git/ref/heads/main")
base_sha = json.loads(b)["object"]["sha"]
print("base_sha =", base_sha)

# 2) build tree from working tree files (skip .git)
SKIP_DIRS = {".git"}
entries = []
for dp, dns, fns in os.walk(ROOT):
    dns[:] = [d for d in dns if d not in SKIP_DIRS]
    for fn in fns:
        full = os.path.join(dp, fn)
        rel = os.path.relpath(full, ROOT).replace("\\", "/")
        if rel.startswith(".git/"): continue
        sz = os.path.getsize(full)
        with open(full, "rb") as f:
            content = f.read()
        if sz > 4_000_000:
            print(f"  LARGE {rel} {sz} bytes -> reuse parent blob if needed")
        # create blob
        _, bb = req("POST", f"{BASE}/git/blobs", {"content": base64.b64encode(content).decode(), "encoding": "base64"})
        blob = json.loads(bb)
        entries.append({"path": rel, "mode": "100644", "type": "blob", "sha": blob["sha"]})
        print("  blob", rel, blob["sha"][:8])

# 3) create tree (retry parent blobs for large files on failure)
def create_tree(ents):
    _, bt = req("POST", f"{BASE}/git/trees", {"base_tree": base_sha, "tree": ents})
    return bt
bt = create_tree(entries)
try:
    tree_sha = json.loads(bt)["sha"]
except Exception:
    # large file likely failed; reuse parent blob sha
    print("  tree create failed, reusing parent blobs for large files...")
    parent, _ = req("GET", f"{BASE}/git/trees/{base_sha}?recursive=1")
    pmap = {t["path"]: t["sha"] for t in json.loads(parent)["tree"]}
    new = []
    for e in entries:
        if e["path"] in pmap:
            e2 = dict(e); e2["sha"] = pmap[e["path"]]; new.append(e2)
        else:
            new.append(e)
    bt = create_tree(new)
    tree_sha = json.loads(bt)["sha"]
print("tree_sha =", tree_sha)

# 4) commit
msg = "升级 cordova-android 至 12.0.1 修复 Gradle 8/Groovy4 下 XmlParser 编译失败（cordova.gradle 已知 bug）"
_, bc = req("POST", f"{BASE}/git/commits", {"message": msg, "tree": tree_sha, "parents": [base_sha]})
commit = json.loads(bc)
new_sha = commit["sha"]
print("new commit =", new_sha)

# 5) update ref
_, br = req("PATCH", f"{BASE}/git/refs/heads/main", {"sha": new_sha, "force": True})
print("ref update:", "ok" if br else "fail")

# 6) wait for run matching head_sha
run_id = None
for _ in range(60):
    _, bj = req("GET", f"{BASE}/actions/runs?head_sha={new_sha}&per_page=5")
    runs = json.loads(bj).get("workflow_runs", [])
    if runs:
        run_id = runs[0]["id"]
        break
    time.sleep(5)
if not run_id:
    print("ERROR: no run triggered")
    sys.exit(1)
print("run id =", run_id)

conclusion = None
for _ in range(120):
    _, bj = req("GET", f"{BASE}/actions/runs/{run_id}")
    j = json.loads(bj)
    conclusion = j.get("conclusion")
    status = j.get("status")
    print(f"  status={status} conclusion={conclusion}")
    if status == "completed":
        break
    time.sleep(10)

if conclusion != "success":
    print("BUILD FAILED. Fetching logs...")
    data = None
    r, raw = req("GET", f"{BASE}/actions/runs/{run_id}/logs")
    if raw:
        try:
            data = raw
        except: pass
    if data:
        z = zipfile.ZipFile(io.BytesIO(data))
        for n in z.namelist():
            txt = z.read(n).decode("utf-8", "ignore")
            for l in txt.splitlines():
                if "XmlParser" in l or "What went wrong" in l or "BUILD FAILED" in l or "error:" in l.lower():
                    print("  ", l[:300])
    sys.exit(2)

# 7) download artifact
_, bj = req("GET", f"{BASE}/actions/runs/{run_id}/artifacts")
arts = json.loads(bj).get("artifacts", [])
if not arts:
    print("ERROR no artifact"); sys.exit(3)
art_id = arts[0]["id"]
_, bdl = req("GET", f"{BASE}/actions/artifacts/{art_id}/zip")
with open(os.path.join(ROOT, "apk_out.zip"), "wb") as f:
    f.write(bdl)
z = zipfile.ZipFile(io.BytesIO(bdl))
names = z.namelist()
apk_name = [n for n in names if n.endswith("app-debug.apk")][0]
out_dir = os.path.join(ROOT, "..", "apk_out")
os.makedirs(out_dir, exist_ok=True)
out_path = os.path.join(out_dir, "app-debug.apk")
with open(out_path, "wb") as f:
    f.write(z.read(apk_name))
print("APK written:", os.path.abspath(out_path), os.path.getsize(out_path), "bytes")

# 8) verify plugin in apk
apk = zipfile.ZipFile(out_path)
an = apk.namelist()
sas = [n for n in an if "saveandshare" in n.lower()]
plug = [n for n in an if n.endswith("cordova_plugins.js")]
print("saveandshare files in apk:", sas)
if plug:
    txt = apk.read(plug[0]).decode("utf-8", "ignore")
    print("plugin cn.workbuddy.saveandshare registered:", "cn.workbuddy.saveandshare" in txt)
print("SaveAndShare.java class in apk:", any("SaveAndShare.class" in n for n in an))
print("saveandshare_paths.xml in apk:", any("saveandshare_paths" in n for n in an))
print("DONE")
