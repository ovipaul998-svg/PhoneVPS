const $=id=>document.getElementById(id);
let currentFolder="";
let terminalHistory=["PhoneVPS terminal initialized.","Checking Rust & Python engines…","Type help to see supported commands."];
let cmdHistory=[];
let cmdHistoryIndex=-1;
function bridge(){return typeof window.PhoneVPS!=="undefined";}
function applyTheme(theme){
 const light=theme==="light";
 document.body.classList.toggle("light",light);
 const btn=$("themeToggle");
 if(btn)btn.textContent=light?"🌙 Dark":"☀ Light";
 try{localStorage.setItem("phonevps_theme",light?"light":"dark");}catch(_){}
}
applyTheme((()=>{try{return localStorage.getItem("phonevps_theme")||"dark";}catch(_){return"dark";}})());
if($("themeToggle"))$("themeToggle").onclick=()=>applyTheme(document.body.classList.contains("light")?"dark":"light");
function configuredPort(){return bridge()&&PhoneVPS.getServerPort?PhoneVPS.getServerPort():8080;}
function activePort(){return bridge()&&PhoneVPS.getActivePort?PhoneVPS.getActivePort():configuredPort();}
function configuredEngine(){return bridge()&&PhoneVPS.getEngine?PhoneVPS.getEngine():"python";}
function activeEngine(){return bridge()&&PhoneVPS.getActiveEngine?PhoneVPS.getActiveEngine():configuredEngine();}
function syncEngineUI(){
 const cfg=configuredEngine();
 const isRust=cfg==="rust";
 if($("engineRust"))$("engineRust").classList.toggle("active",isRust);
 if($("enginePython"))$("enginePython").classList.toggle("active",!isRust);
 if($("engineBadge"))$("engineBadge").textContent="Engine: "+(isRust?"Rust Native":"Python 3.11");
 if($("terminalEngineLabel"))$("terminalEngineLabel").textContent=isRust?"RUST NATIVE SHELL":"PYTHON 3.11 SHELL";
}
function syncPortUI(){
 const cfg=configuredPort();
 const act=activePort();
 if($("portDisplay"))$("portDisplay").textContent=String(act);
 if($("portInput")&&document.activeElement!==$("portInput"))$("portInput").value=String(cfg);
 if($("openServerHint"))$("openServerHint").textContent="http://127.0.0.1:"+act;
 syncEngineUI();
}
function renderTerminal(){
 const out=$("terminalOutput");
 if(!out)return;
 out.textContent=terminalHistory.slice(-150).join("\n");
 out.scrollTop=out.scrollHeight;
}
function terminalLog(value){terminalHistory.push(String(value));renderTerminal();}
window.addEventListener("error",e=>terminalLog("APP JAVASCRIPT ERROR: "+(e.message||"Unknown error")+" ("+(e.filename||"")+")"));
window.addEventListener("unhandledrejection",e=>terminalLog("APP PROMISE ERROR: "+String(e.reason)));
function setServerState(status){
 const online=status==="Online";
 const port=activePort();
 const eng=activeEngine()==="rust"?"Rust":"Python";
 syncPortUI();
 $("hostState").textContent=status;
 $("hostState").className=online?"green":status.startsWith("Error")?"red":"";
 $("hostDetail").textContent=online?"Serving /projects on port "+port+" ("+eng+")":status.startsWith("Error")?"Startup failed":"Not hosting";
 const top=$("topStatus");
 top.textContent="";
 top.appendChild(document.createElement("i"));
 top.appendChild(document.createTextNode(" "+(online?"ONLINE · "+eng.toUpperCase():status.toUpperCase())));
 $("serverMessage").textContent=online?"Server is ONLINE ("+eng+") · Local: http://127.0.0.1:"+port+" · LAN: http://"+(PhoneVPS.lanAddress?PhoneVPS.lanAddress():"your-phone-ip")+":"+port:status.startsWith("Error")?status:"Server is offline.";
 $("serverMessage").className="messageBanner "+(online?"green":status.startsWith("Error")?"red":"");
}
function refreshTokenStatus(){
 if(!bridge()||!PhoneVPS.hasBotToken)return;
 try{$("tokenStatus").textContent=PhoneVPS.hasBotToken()?"Token saved securely outside /projects.":"No token saved yet.";}
 catch(e){$("tokenStatus").textContent="Token check failed: "+String(e);}
}
function refresh(){
 try{
  if(!bridge()){$("rustState").textContent="Unavailable";$("pythonState").textContent="Unavailable";$("pythonDetail").textContent="Native app bridge missing";setServerState("Error: app bridge missing");terminalLog("ERROR: Android-to-app bridge is missing. Reinstall the latest APK.");return;}
  const rustOk=PhoneVPS.rustReady?PhoneVPS.rustReady():false;
  $("rustState").textContent=rustOk?"Ready":"Unavailable";
  $("rustState").className=rustOk?"green":"";
  $("rustDetail").textContent=rustOk&&PhoneVPS.rustVersion?PhoneVPS.rustVersion():"Rust library not loaded";
  const ok=PhoneVPS.pythonReady();
  $("pythonState").textContent=ok?"Ready":"Error";
  $("pythonState").className=ok?"green":"red";
  $("pythonDetail").textContent=ok?"Embedded Python 3.11":"Python runtime failed — check app installation";
  terminalLog("Engines: Rust="+(rustOk?"Ready":"Unavailable")+", Python="+(ok?"Ready":"Error"));
  syncPortUI();
  setServerState(PhoneVPS.hostingStatus());
  refreshTokenStatus();
 }catch(e){$("pythonState").textContent="Error";$("pythonDetail").textContent=String(e);setServerState("Error: "+String(e));terminalLog("BRIDGE CHECK ERROR: "+String(e));}
}
function showTab(tab){
 document.querySelectorAll(".view").forEach(x=>x.hidden=x.id!==tab);
 document.querySelectorAll(".nav button").forEach(x=>x.classList.toggle("active",x.dataset.tab===tab));
 if(tab==="files")loadFiles();
 if(tab==="terminal")renderTerminal();
 if(tab==="bot")refreshTokenStatus();
}
document.querySelectorAll(".nav button").forEach(b=>b.onclick=()=>showTab(b.dataset.tab));
document.querySelectorAll(".quickCmd").forEach(b=>b.onclick=()=>{$("terminalInput").value=b.dataset.cmd;runTerminal();});
function switchEngine(engine){
 if(!bridge()||!PhoneVPS.setEngine)return;
 $("serverMessage").textContent=PhoneVPS.setEngine(engine);
 syncEngineUI();
}
if($("engineRust"))$("engineRust").onclick=()=>switchEngine("rust");
if($("enginePython"))$("enginePython").onclick=()=>switchEngine("python");
$("start").onclick=()=>{try{const result=PhoneVPS.startHosting();$("serverMessage").textContent=result;setTimeout(refresh,350);setTimeout(refresh,1200);}catch(e){$("serverMessage").textContent="Start error: "+String(e);}};
$("stop").onclick=()=>{try{const result=PhoneVPS.stopHosting();$("serverMessage").textContent=result;setTimeout(refresh,500);}catch(e){$("serverMessage").textContent="Stop error: "+String(e);}};
$("openServer").onclick=()=>{try{if(PhoneVPS.hostingStatus()==="Online")window.open("http://127.0.0.1:"+activePort(),"_blank");else $("serverMessage").textContent="Start the server first.";}catch(e){$("serverMessage").textContent=String(e);}};
$("refresh").onclick=refresh;
$("savePort").onclick=()=>{
 if(!bridge()||!PhoneVPS.setServerPort)return;
 const port=parseInt($("portInput").value,10);
 if(Number.isNaN(port)||port<1024||port>65535){$("serverMessage").textContent="Enter a port between 1024 and 65535.";return;}
 $("serverMessage").textContent=PhoneVPS.setServerPort(port);
 syncPortUI();
};
$("terminalRun").onclick=runTerminal;
$("terminalInput").addEventListener("keydown",e=>{
 if(e.key==="Enter"){e.preventDefault();runTerminal();return;}
 if(e.key==="ArrowUp"){
  if(!cmdHistory.length)return;
  e.preventDefault();
  if(cmdHistoryIndex===-1)cmdHistoryIndex=cmdHistory.length;
  if(cmdHistoryIndex>0)cmdHistoryIndex--;
  $("terminalInput").value=cmdHistory[cmdHistoryIndex]||"";
 }else if(e.key==="ArrowDown"){
  if(!cmdHistory.length||cmdHistoryIndex===-1)return;
  e.preventDefault();
  if(cmdHistoryIndex<cmdHistory.length-1){cmdHistoryIndex++;$("terminalInput").value=cmdHistory[cmdHistoryIndex];}
  else{cmdHistoryIndex=-1;$("terminalInput").value="";}
 }
});
function runTerminal(){
 const command=$("terminalInput").value.trim();if(!command)return;
 if(!cmdHistory.length||cmdHistory[cmdHistory.length-1]!==command){cmdHistory.push(command);if(cmdHistory.length>50)cmdHistory.shift();}
 cmdHistoryIndex=-1;
 $("terminalInput").value="";
 terminalHistory.push("$ "+command,"Running…");
 renderTerminal();
 if(!bridge()){terminalLog("ERROR: Native PhoneVPS bridge is unavailable.");return;}
 try{
  const result=PhoneVPS.runCommand(command);
  if(result==="__CLEAR__")terminalHistory=[];
  else terminalHistory.push(result===null||result===undefined?"ERROR: PhoneVPS returned no result.":String(result));
 }catch(e){terminalHistory.push("TERMINAL ERROR: "+(e&&e.stack?e.stack:String(e)));}
 renderTerminal();
}
function loadFiles(){
 if(!bridge())return;
 let data=[];
 try{data=JSON.parse(PhoneVPS.listFiles(currentFolder)||"[]");}catch(e){$("fileMessage").textContent="Could not load files: "+e;return;}
 $("pathLabel").textContent="/projects/"+currentFolder;
 const list=$("fileList");list.innerHTML="";
 if(currentFolder){const up=document.createElement("button");up.className="file";up.textContent="⬅ Parent folder";up.onclick=()=>{currentFolder=currentFolder.includes("/")?currentFolder.slice(0,currentFolder.lastIndexOf("/")):"";loadFiles();};list.appendChild(up);}
 if(!data.length){const empty=document.createElement("p");empty.className="muted";empty.textContent="No files here yet. Tap + New file or Upload to add one.";list.appendChild(empty);}
 data.forEach(item=>{
  const path=currentFolder?currentFolder+"/"+item.name:item.name;
  const b=document.createElement("button");b.className="file";
  const label=document.createElement("span");label.textContent=(item.directory?"📁 ":"📄 ")+item.name;
  const meta=document.createElement("small");meta.textContent=item.directory?"Folder":item.size+" bytes";
  b.append(label,meta);b.onclick=()=>{if(item.directory){currentFolder=path;loadFiles();}else openFile(path);};list.appendChild(b);
 });
}
function openFile(path){
 $("fileName").value=path;
 $("fileContent").value=PhoneVPS.readFile(path);
 $("fileEditor").hidden=false;
 $("fileMessage").textContent="Editing "+path;
}
$("newFile").onclick=()=>{$("fileEditor").hidden=false;$("fileName").value=currentFolder?(currentFolder+"/main.py"):"main.py";$("fileContent").value="";$("fileMessage").textContent="Enter a filename and write your code, then save.";};
$("newFolder").onclick=()=>{const name=prompt("Enter folder name");if(!name)return;const path=currentFolder?currentFolder+"/"+name:name;$("fileMessage").textContent=PhoneVPS.createFolder(path);loadFiles();};
$("uploadFile").onclick=()=>{if(bridge()&&PhoneVPS.pickFileToUpload)PhoneVPS.pickFileToUpload(currentFolder);};
window.onNativeFileUploaded=msg=>{$("fileMessage").textContent=msg;loadFiles();};
$("reloadFiles").onclick=loadFiles;
$("saveFile").onclick=()=>{const name=$("fileName").value.trim();if(!name){$("fileMessage").textContent="Enter a file name first.";return;}const result=PhoneVPS.saveFile(name,$("fileContent").value);$("fileMessage").textContent=result;loadFiles();};
$("runFile").onclick=()=>{
 const name=$("fileName").value.trim();if(!name){$("fileMessage").textContent="Save a Python file first.";return;}
 terminalHistory.push("$ python "+name,"Starting Python file…");renderTerminal();
 try{
  const result=PhoneVPS.runCommand("python "+JSON.stringify(name));
  terminalHistory.push(result===null||result===undefined?"ERROR: No result returned":String(result));
  $("fileMessage").textContent="Execution returned. Open Terminal to see output.";
 }catch(e){terminalHistory.push("RUN ERROR: "+(e&&e.stack?e.stack:String(e)));$("fileMessage").textContent="Run failed: "+String(e);}
 renderTerminal();
};
$("deleteFile").onclick=()=>{const path=$("fileName").value;if(!path||!confirm("Delete "+path+"?"))return;$("fileMessage").textContent=PhoneVPS.deletePath(path);$("fileEditor").hidden=true;loadFiles();};
function refreshBot(){
 if(!bridge())return;
 try{
  const state=PhoneVPS.botStatus();
  $("botState").textContent="Bot status: "+state;
  $("botState").className="messageBanner "+(state==="running"?"green":state==="error"?"red":"");
  $("botLogs").textContent=PhoneVPS.botLogs();
 }catch(e){$("botState").textContent="Bot bridge error: "+String(e);}
}
$("saveToken").onclick=()=>{
 if(!bridge()||!PhoneVPS.saveBotToken)return;
 $("botState").textContent=PhoneVPS.saveBotToken($("botToken").value);
 $("botToken").value="";
 refreshTokenStatus();
};
$("startBot").onclick=()=>{
 const filename=$("botFile").value.trim()||"main.py";
 const autoRestart=Boolean($("botAutoRestart")&&$("botAutoRestart").checked);
 try{
  $("botState").textContent=PhoneVPS.startBot(filename,autoRestart);
  setTimeout(refreshBot,400);
  setTimeout(refreshBot,1400);
 }catch(e){$("botState").textContent="Start failed: "+String(e);}
};
$("stopBot").onclick=()=>{
 try{$("botState").textContent=PhoneVPS.stopBot();setTimeout(refreshBot,500);}
 catch(e){$("botState").textContent="Stop failed: "+String(e);}
};
$("refreshBot").onclick=refreshBot;
setInterval(()=>{if(!$("bot").hidden)refreshBot();},1500);
renderTerminal();
refresh();
setInterval(()=>{if(bridge())try{setServerState(PhoneVPS.hostingStatus());}catch(e){terminalLog("STATUS ERROR: "+String(e));}},2000);
