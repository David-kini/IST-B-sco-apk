# Lance un vrai PEGSCO (base neuve), prépare un cours et des codes, puis exécute
# le cœur Kotlin de l'application contre ce serveur.
# Variables : SCO_EXE, PG_ADMIN, PG_ADMIN_MDP, KOTLIN_CP (classpath compilé).
import os, subprocess, sys, time, json, urllib.parse, urllib.request
SCO, ADMIN, MDP, CP = os.environ["SCO_EXE"], os.environ["PG_ADMIN"], os.environ["PG_ADMIN_MDP"], os.environ["KOTLIN_CP"]
DOSSIER = sys.argv[1]; os.makedirs(DOSSIER, exist_ok=True)
BASE, PORT = "pegsco_integration_apk", 8104
URL = f"http://127.0.0.1:{PORT}"
def psql(sql): subprocess.run(["psql", "-h", "localhost", "-U", ADMIN, "-d", "postgres", "-Atqc", sql], env={**os.environ, "PGPASSWORD": MDP}, check=True, capture_output=True)
def http(m, p, c=None, t=""):
    rq = urllib.request.Request(URL + p, data=json.dumps(c).encode() if c is not None else None, method=m, headers={"Content-Type": "application/json", **({"Authorization": "Bearer " + t} if t else {})})
    try:
        with urllib.request.urlopen(rq, timeout=10) as r: return json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e: raise RuntimeError(f"{m} {p} : {e.code} {e.read()}")
psql(f"DROP DATABASE IF EXISTS {BASE}"); psql(f"CREATE DATABASE {BASE} OWNER {ADMIN}")
srv = subprocess.Popen([SCO], env={**os.environ, "PEGSCO_DATABASE_URL": f"postgres://{ADMIN}:{urllib.parse.quote(MDP, safe='')}@localhost:5432/{BASE}?sslmode=disable", "PEGSCO_ADDR": f"127.0.0.1:{PORT}"},
                       stdout=open(os.path.join(DOSSIER, "sco.log"), "w"), stderr=subprocess.STDOUT)
try:
    for _ in range(80):
        try:
            urllib.request.urlopen(URL + "/health", timeout=2); break
        except Exception: time.sleep(0.25)
    http("POST", "/auth/amorcer", {"nom": "Secrétariat", "email": "sec@ist.cd", "mot_de_passe": "Sco-Admin-2026x"})
    tok = http("POST", "/auth/connexion", {"email": "sec@ist.cd", "mot_de_passe": "Sco-Admin-2026x"})["jeton"]
    A = lambda m, p, c=None: http(m, p, c, tok)
    an = A("POST", "/annees", {"code": "2025-2026", "libelle": "2025-2026"})["id"]; A("POST", f"/annees/{an}/courante")
    l1 = A("POST", "/classes", {"code": "L1", "libelle": "Licence 1", "ordre": 1})["id"]
    sg = A("POST", "/options", {"code": "SG", "libelle": "Soins généraux"})["id"]; sf = A("POST", "/options", {"code": "SF", "libelle": "Sage-femme"})["id"]
    E = {n: A("POST", "/etudiants", {"nom": n, "postnom": "MAKAYA", "annee_id": an, "classe_id": l1, "option_id": o})["id"] for n, o in [("AMBA", sg), ("BOLA", sg), ("CIKU", sg), ("DIMA", sf), ("EKO", sf)]}
    iv = A("POST", "/intervenants", {"nom": "MVUEZOLO", "postnom": "KIESE"})["id"]
    co = A("POST", "/cours", {"annee_id": an, "classe_id": l1, "intitule": "Anatomie", "option_ids": [sg, sf]})["id"]
    code_prof = A("POST", f"/cours/{co}/attributions", {"intervenant_id": iv, "role": "TITULAIRE"})["code"]
    code_cp = A("PUT", "/cps", {"annee_id": an, "classe_id": l1, "option_id": sg, "etudiant_id": E["BOLA"]})["code"]
    r = subprocess.run(["java", "-cp", CP, "IntegrationPEGSCOKt"], env={**os.environ, "PEGSCO_URL": URL, "CODE_PROF": code_prof, "CODE_CP": code_cp, "DOSSIER": DOSSIER}, capture_output=True, text=True)
    sortie = r.stdout; print(sortie.strip()); print(r.stderr.replace("Picked up JAVA_TOOL_OPTIONS", "").strip()[:2000]) if r.returncode else None
    ok = r.returncode == 0
    if ok:
        # Le secrétariat importe la sauvegarde du CP : 1 appel déjà reçu, 1 nouveau (le TP).
        f = json.load(open(os.path.join(DOSSIER, "sauvegarde.istsco")))
        b = A("POST", "/presences/sauvegardes?nom=sauvegarde.istsco", f)
        uid_tp = [l.split("=")[1] for l in sortie.splitlines() if l.startswith("UID_TP=")][0]
        appels = A("GET", "/presences/appels")
        tp = [a for a in appels if a["uid"] == uid_tp]
        ok2 = b["appels"] == {"RECU": 1, "DEJA_RECU": 1, "REFUSE": 0} and len(tp) == 1 and tp[0]["via_sauvegarde"] and tp[0]["absents"] == 1
        print(("OK    " if ok2 else "ECHEC ") + "sauvegarde importée par le secrétariat : 1 nouveau (TP), 1 déjà reçu", "" if ok2 else json.dumps(b))
        ec = A("GET", "/presences/ecarts")
        ok3 = len(ec) == 1 and ec[0]["nom"].startswith("CIKU")
        print(("OK    " if ok3 else "ECHEC ") + "côté secrétariat : 1 écart (CIKU)")
        sig = A("GET", "/presences/signalements")
        ok4 = len(sig) == 1 and sig[0]["motif"] == "FRAUDE"
        print(("OK    " if ok4 else "ECHEC ") + "côté secrétariat : signalement FRAUDE reçu")
        ok = ok2 and ok3 and ok4
    print("\nINTEGRATION : " + ("RÉUSSIE" if ok else "ÉCHEC")); sys.exit(0 if ok else 1)
finally:
    srv.terminate(); srv.wait(timeout=8)
