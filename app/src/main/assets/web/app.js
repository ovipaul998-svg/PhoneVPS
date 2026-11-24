const $=id=>document.getElementById(id);
const HOME="/data/data/com.termux/files/home";
function say(text){$("message").textContent=text;}
function hasBridge(){return typeof window.PhoneVPS!=="undefined";}
function send(command, background=true, cwd=HOME){
 if(!hasBridge()){say("Native Termux bridge is unavailable. Reinstall the latest PhoneVPS APK.");return;}
 say(window.PhoneVPS.runCommand(command,cwd,background));
}
function checkEngine(){
 if(!hasBridge()){$("engineStatus").textContent="Unavailable";$("engineDetail").textContent="Native bridge missing";return;}
 const installed=window.PhoneVPS.isTermuxInstalled();
 $("engineStatus").textContent=installed?"Detected":"Not installed";
 $("engineDetail").textContent=installed?"Termux package found":"Install Termux to enable hosting";
 say(installed?"Termux detected. Complete the one-time permission setup below.":"Please install and open Termux first.");
}
$("installTools").addEventListener("click",()=>send("pkg update -y && pkg install -y python nodejs-lts",true));
$("startPython").addEventListener("click",()=>send("mkdir -p ~/phonevps/www && printf '%s\\n' '<h1>PhoneVPS server is online</h1>' > ~/phonevps/www/index.html && (pgrep -f 'python -m http.server 8080' >/dev/null || (cd ~/phonevps/www && nohup python -m http.server 8080 --bind 0.0.0.0 > ~/phonevps/python-server.log 2>&1 < /dev/null & echo $! > ~/phonevps/python-server.pid)) && echo 'Python server start requested. Open http://127.0.0.1:8080'",true));
$("stopPython").addEventListener("click",()=>send("if [ -f ~/phonevps/python-server.pid ]; then kill $(cat ~/phonevps/python-server.pid) 2>/dev/null || true; rm -f ~/phonevps/python-server.pid; fi; pkill -f 'python -m http.server 8080' 2>/dev/null || true; echo 'Stop command sent'",true));
$("openTermux").addEventListener("click",()=>say(window.PhoneVPS.openTermux()));
$("runCommand").addEventListener("click",()=>{const command=$("command").value.trim();if(!command){say("Type a command first.");return;}send(command,true);});
checkEngine();