"""Local UI fixture. Uses no Oracle connection or production data."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlparse
import json,time
ROOT=Path(__file__).resolve().parents[2]/"main"/"resources"/"static"
PROFILE={"id":1,"username":"prueba","role":"ADMIN","employeeId":1,"name":"Administrador de prueba","position":"Administrador","area":"Administración"}
EMPLOYEES=[{"id":i,"name":name,"role":cargo,"area":"Salón","active":True,"hasAccount":i<3,"accessRole":"ADMIN" if i==1 else "JEFE_SALON" if i==2 else None} for i,name,cargo in [(1,"Administrador de prueba","Administrador"),(2,"Jefatura de Salón","Jefe de Salón"),(3,"Trabajador de prueba","Mesero")]]
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(ROOT),**kwargs)
    def reply(self,data):
        self.send_response(200);self.send_header("Content-Type","application/json");self.send_header("Cache-Control","no-store");self.end_headers();self.wfile.write(json.dumps(data).encode())
    def do_GET(self):
        path=urlparse(self.path).path
        if not path.startswith("/api/"):return super().do_GET()
        fixtures={"/api/auth/status":{"setupRequired":False},"/api/auth/session":PROFILE,"/api/empleados":EMPLOYEES,"/api/dashboard":{"Personal":len(EMPLOYEES),"Asistencia":2,"Tareas diarias":3},"/api/dashboard/overview":{"activities":[],"alerts":[]},"/api/asistencia/reporte":{"marcas":[]}}
        self.reply(fixtures.get(path,[]))
    def do_DELETE(self):
        if self.path.startswith("/api/empleados/"):
            time.sleep(.7);key=int(self.path.rsplit("/",1)[1]);EMPLOYEES[:]=[e for e in EMPLOYEES if e["id"]!=key]
        self.send_response(204);self.end_headers()
    def do_POST(self):self.reply({})
if __name__=="__main__":ThreadingHTTPServer(("127.0.0.1",8766),Handler).serve_forever()
